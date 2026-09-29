// JNI bridge around llama.cpp for fully on-device inference.
// The model file is opened by Kotlin via Android's document picker (SAF) and
// never copied — we load it straight from "/proc/self/fd/<fd>".
#include <jni.h>
#include <string>
#include <vector>
#include <atomic>
#include <chrono>
#include <android/log.h>
#include "llama.h"

#define TAG "NexisLlama"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

static const int kCtx = 2048;
static std::atomic<bool> g_abort{false};

struct NexisLlamaSession {
    llama_model* model = nullptr;
    llama_context* ctx = nullptr;
};

static llama_context* newContext(llama_model* model, int nCtx) {
    llama_context_params cparams = llama_context_default_params();
    cparams.n_ctx = nCtx > 0 ? (uint32_t) nCtx : kCtx;
    cparams.n_batch = cparams.n_ctx;
    cparams.n_threads = 4;
    cparams.n_threads_batch = 4;
    return llama_new_context_with_model(model, cparams);
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_nadidstudio_nexis_engine_LocalLlamaEngine_nativeLoad(
        JNIEnv* env, jobject, jint fd, jint nCtx) {
    static bool backendInit = false;
    if (!backendInit) {
        llama_backend_init();
        backendInit = true;
    }
    std::string path = "/proc/self/fd/" + std::to_string(fd);
    llama_model_params mparams = llama_model_default_params();
    llama_model* model = llama_load_model_from_file(path.c_str(), mparams);
    if (!model) {
        LOGE("failed to load model from %s", path.c_str());
        return 0;
    }
    llama_context* ctx = newContext(model, nCtx);
    if (!ctx) {
        LOGE("failed to create context");
        llama_free_model(model);
        return 0;
    }
    return reinterpret_cast<jlong>(new NexisLlamaSession{model, ctx});
}

// Length of the longest prefix of [s] that does not end in the middle of a UTF-8 character.
static size_t validUtf8Prefix(const std::string& s) {
    size_t n = s.size();
    if (n == 0) return 0;
    size_t back = 0;
    while (back < 3 && back < n && (((unsigned char) s[n - 1 - back]) & 0xC0) == 0x80) back++;
    if (back == n) return n;
    unsigned char lead = (unsigned char) s[n - 1 - back];
    size_t need = lead >= 0xF0 ? 4 : lead >= 0xE0 ? 3 : lead >= 0xC0 ? 2 : 1;
    return (back + 1 < need) ? n - 1 - back : n;
}

static std::string jstr(JNIEnv* env, jstring s) {
    const char* c = env->GetStringUTFChars(s, nullptr);
    std::string out(c ? c : "");
    if (c) env->ReleaseStringUTFChars(s, c);
    return out;
}

static std::vector<llama_token> tokenize(const llama_vocab* vocab, const std::string& text) {
    int n = (int) text.size() + 32;
    std::vector<llama_token> tokens(n);
    int got = llama_tokenize(vocab, text.c_str(), (int32_t) text.size(), tokens.data(), n, false, true);
    if (got < 0) {
        tokens.resize(-got);
        got = llama_tokenize(vocab, text.c_str(), (int32_t) text.size(), tokens.data(), (int32_t) tokens.size(), false, true);
    }
    tokens.resize(got > 0 ? got : 0);
    return tokens;
}

// Formats the conversation with the model's OWN chat template (ChatML for
// Qwen, Llama-3 style for Llama, etc.). Without this an instruct model gets a
// raw string, never sees an assistant turn start, and rambles / never stops.
static std::string applyTemplate(llama_model* model, const std::vector<std::pair<std::string, std::string>>& msgs) {
    std::vector<llama_chat_message> chat;
    chat.reserve(msgs.size());
    for (auto& m : msgs) chat.push_back({m.first.c_str(), m.second.c_str()});
    const char* tmpl = llama_model_chat_template(model, nullptr);
    std::vector<char> buf(8192);
    int n = llama_chat_apply_template(tmpl ? tmpl : "chatml", chat.data(), chat.size(), true, buf.data(), (int32_t) buf.size());
    if (n > (int) buf.size()) {
        buf.resize(n);
        n = llama_chat_apply_template(tmpl ? tmpl : "chatml", chat.data(), chat.size(), true, buf.data(), (int32_t) buf.size());
    }
    if (n < 0) {
        // Unknown template: fall back to ChatML.
        n = llama_chat_apply_template("chatml", chat.data(), chat.size(), true, buf.data(), (int32_t) buf.size());
        if (n > (int) buf.size()) {
            buf.resize(n);
            n = llama_chat_apply_template("chatml", chat.data(), chat.size(), true, buf.data(), (int32_t) buf.size());
        }
    }
    return n > 0 ? std::string(buf.data(), n) : std::string();
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_nadidstudio_nexis_engine_LocalLlamaEngine_nativeChat(
        JNIEnv* env, jobject self, jlong handle, jobjectArray jRoles, jobjectArray jContents, jint maxTokens) {
    auto* session = reinterpret_cast<NexisLlamaSession*>(handle);
    auto empty = [&]() { return env->NewByteArray(0); };
    if (!session) return empty();
    g_abort = false;

    std::vector<std::pair<std::string, std::string>> msgs;
    jsize count = env->GetArrayLength(jRoles);
    for (jsize i = 0; i < count; i++) {
        auto role = (jstring) env->GetObjectArrayElement(jRoles, i);
        auto content = (jstring) env->GetObjectArrayElement(jContents, i);
        msgs.emplace_back(jstr(env, role), jstr(env, content));
        env->DeleteLocalRef(role);
        env->DeleteLocalRef(content);
    }

    const llama_vocab* vocab = llama_model_get_vocab(session->model);
    int limit = maxTokens > 0 ? maxTokens : 384;
    int budget = kCtx - limit - 16;

    // Drop the oldest turns (keep the system message + the latest user turn)
    // until the prompt fits in the context window.
    std::vector<llama_token> tokens;
    while (true) {
        tokens = tokenize(vocab, applyTemplate(session->model, msgs));
        if ((int) tokens.size() <= budget || msgs.size() <= 2) break;
        msgs.erase(msgs.begin() + 1);
    }
    if ((int) tokens.size() > budget) tokens.erase(tokens.begin(), tokens.end() - budget);
    if (tokens.empty()) return empty();

    // Fresh context every call: the old code kept appending to the same KV
    // cache, so follow-up messages eventually overflowed and came back empty.
    llama_free(session->ctx);
    session->ctx = newContext(session->model, kCtx);
    if (!session->ctx) { LOGE("context recreate failed"); return empty(); }

    llama_sampler* smpl = llama_sampler_chain_init(llama_sampler_chain_default_params());
    llama_sampler_chain_add(smpl, llama_sampler_init_penalties(llama_vocab_n_tokens(llama_model_get_vocab(session->model)), 64, 1.1f, 0.0f, 0.0f));
    llama_sampler_chain_add(smpl, llama_sampler_init_top_k(40));
    llama_sampler_chain_add(smpl, llama_sampler_init_top_p(0.9f, 1));
    llama_sampler_chain_add(smpl, llama_sampler_init_temp(0.7f));
    llama_sampler_chain_add(smpl, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));

    llama_batch batch = llama_batch_get_one(tokens.data(), (int32_t) tokens.size());
    if (llama_decode(session->ctx, batch) != 0) {
        LOGE("initial decode failed");
        llama_sampler_free(smpl);
        return empty();
    }

    // Live token streaming back to Kotlin (cumulative valid-UTF-8 bytes).
    jclass selfCls = env->GetObjectClass(self);
    jmethodID onBytes = env->GetMethodID(selfCls, "onNativeBytes", "([B)V");
    if (env->ExceptionCheck()) { env->ExceptionClear(); onBytes = nullptr; }
    auto emit = [&](const std::string& text) {
        if (!onBytes) return;
        size_t n = validUtf8Prefix(text);
        jbyteArray arr = env->NewByteArray((jsize) n);
        env->SetByteArrayRegion(arr, 0, (jsize) n, reinterpret_cast<const jbyte*>(text.data()));
        env->CallVoidMethod(self, onBytes, arr);
        if (env->ExceptionCheck()) env->ExceptionClear();
        env->DeleteLocalRef(arr);
    };

    std::string result;
    auto start = std::chrono::steady_clock::now();
    for (int i = 0; i < limit; i++) {
        if (g_abort.load()) break;
        // Hard wall-clock cap so a slow phone can never look "stuck" forever.
        if (std::chrono::steady_clock::now() - start > std::chrono::seconds(150)) break;

        llama_token tok = llama_sampler_sample(smpl, session->ctx, -1);
        if (llama_vocab_is_eog(vocab, tok)) break;

        char buf[256];
        int n = llama_token_to_piece(vocab, tok, buf, sizeof(buf), 0, true);
        if (n > 0) result.append(buf, n);
        if (i % 2 == 0) emit(result);

        llama_batch next = llama_batch_get_one(&tok, 1);
        if (llama_decode(session->ctx, next) != 0) {
            LOGE("decode step failed");
            break;
        }
    }
    llama_sampler_free(smpl);
    emit(result);

    // Raw bytes -> Kotlin decodes as UTF-8. (A token can end mid-character for
    // Arabic; passing that through NewStringUTF can crash the JVM.)
    jbyteArray out = env->NewByteArray((jsize) result.size());
    env->SetByteArrayRegion(out, 0, (jsize) result.size(), reinterpret_cast<const jbyte*>(result.data()));
    return out;
}

extern "C" JNIEXPORT void JNICALL
Java_com_nadidstudio_nexis_engine_LocalLlamaEngine_nativeAbort(JNIEnv*, jobject) {
    g_abort = true;
}

extern "C" JNIEXPORT void JNICALL
Java_com_nadidstudio_nexis_engine_LocalLlamaEngine_nativeFree(
        JNIEnv* env, jobject, jlong handle) {
    auto* session = reinterpret_cast<NexisLlamaSession*>(handle);
    if (!session) return;
    if (session->ctx) llama_free(session->ctx);
    if (session->model) llama_free_model(session->model);
    delete session;
}
