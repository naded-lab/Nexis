// JNI bridge around llama.cpp for fully on-device inference.
// The model file is opened by Kotlin via Android's document picker (SAF) and
// never copied — we load it straight from "/proc/self/fd/<fd>", which the
// Linux kernel resolves back to the original file, so llama.cpp's normal
// mmap-based loader works unmodified.
#include <jni.h>
#include <string>
#include <android/log.h>
#include "llama.h"

#define TAG "NexisLlama"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

struct NexisLlamaSession {
    llama_model* model = nullptr;
    llama_context* ctx = nullptr;
};

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

    llama_context_params cparams = llama_context_default_params();
    cparams.n_ctx = nCtx > 0 ? (uint32_t) nCtx : 2048;
    cparams.n_threads = 4;
    cparams.n_threads_batch = 4;

    llama_context* ctx = llama_new_context_with_model(model, cparams);
    if (!ctx) {
        LOGE("failed to create context");
        llama_free_model(model);
        return 0;
    }

    auto* session = new NexisLlamaSession{model, ctx};
    return reinterpret_cast<jlong>(session);
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_nadidstudio_nexis_engine_LocalLlamaEngine_nativeGenerate(
        JNIEnv* env, jobject, jlong handle, jstring jPrompt, jint maxTokens) {
    auto* session = reinterpret_cast<NexisLlamaSession*>(handle);
    if (!session) return env->NewStringUTF("");

    const char* cPrompt = env->GetStringUTFChars(jPrompt, nullptr);
    std::string prompt(cPrompt);
    env->ReleaseStringUTFChars(jPrompt, cPrompt);

    const llama_vocab* vocab = llama_model_get_vocab(session->model);

    const int nPromptMax = (int) prompt.size() + 32;
    std::vector<llama_token> tokens(nPromptMax);
    int nTokens = llama_tokenize(vocab, prompt.c_str(), (int32_t) prompt.size(),
                                  tokens.data(), nPromptMax, true, true);
    if (nTokens < 0) {
        tokens.resize(-nTokens);
        nTokens = llama_tokenize(vocab, prompt.c_str(), (int32_t) prompt.size(),
                                  tokens.data(), (int32_t) tokens.size(), true, true);
    }
    tokens.resize(nTokens);

    llama_batch batch = llama_batch_get_one(tokens.data(), (int32_t) tokens.size());
    if (llama_decode(session->ctx, batch) != 0) {
        LOGE("initial decode failed");
        return env->NewStringUTF("");
    }

    std::string result;
    int nCur = nTokens;
    int limit = maxTokens > 0 ? maxTokens : 512;

    for (int i = 0; i < limit; i++) {
        auto* logits = llama_get_logits_ith(session->ctx, -1);
        int nVocab = llama_vocab_n_tokens(vocab);

        llama_token bestToken = 0;
        float bestLogit = logits[0];
        for (int t = 1; t < nVocab; t++) {
            if (logits[t] > bestLogit) {
                bestLogit = logits[t];
                bestToken = t;
            }
        }

        if (llama_vocab_is_eog(vocab, bestToken)) break;

        char buf[256];
        int n = llama_token_to_piece(vocab, bestToken, buf, sizeof(buf), 0, true);
        if (n > 0) result.append(buf, n);

        llama_token nextTok = bestToken;
        llama_batch nextBatch = llama_batch_get_one(&nextTok, 1);
        if (llama_decode(session->ctx, nextBatch) != 0) {
            LOGE("decode step failed at token %d", nCur);
            break;
        }
        nCur++;
    }

    return env->NewStringUTF(result.c_str());
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
