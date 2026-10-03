package com.nadidstudio.nexis.models

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull

/**
 * One unified error type every provider adapter must translate its own
 * errors into. This is what the fallback logic (key-switch / model-switch)
 * decides on — see the model-fallback-orchestration design.
 */
sealed class AiCallResult {
    data class Success(val text: String) : AiCallResult()

    /** Quota/rate limit hit on this key — try the next key for the same model. */
    data class QuotaExceeded(val raw: String? = null, val retryAfterMs: Long? = null) : AiCallResult()

    /** Network blip / server hiccup — safe to retry the same key shortly. */
    data class TransientError(val raw: String? = null) : AiCallResult()

    /** Bad key, malformed request, etc — do not retry this key again. */
    data class PermanentError(val raw: String? = null) : AiCallResult()
}

/**
 * One API key belonging to a provider (a provider/model can have several,
 * per the multi-key failover design).
 */
data class ApiKeyEntry(
    val id: String,
    val keyValue: String // read from EncryptedSharedPreferences, never hardcoded
)

/**
 * Every AI provider (Claude, GPT, Gemini, KiMi, NotebookLM, a manually
 * added custom one, ...) implements this same interface so the
 * orchestration layer never needs to know provider-specific details.
 */
interface AiModelAdapter {
    val providerId: String
    val displayName: String

    /**
     * Send a single request using the given key. Implementations translate
     * their own SDK/HTTP error into one of the AiCallResult cases above —
     * this is the one place provider-specific error handling is allowed to live.
     */
    suspend fun send(prompt: String, key: ApiKeyEntry): AiCallResult
}

/**
 * Claude adapter — calls the real Anthropic Messages API.
 */
class ClaudeAdapter(
    private val client: okhttp3.OkHttpClient = NexisHttp
) : AiModelAdapter {
    override val providerId = "claude"
    override val displayName = "Claude"

    override suspend fun send(prompt: String, key: ApiKeyEntry): AiCallResult =
        withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val body = org.json.JSONObject().apply {
                    put("model", "claude-sonnet-4-5")
                    put("max_tokens", 1024)
                    put(
                        "messages",
                        org.json.JSONArray().put(
                            org.json.JSONObject().apply {
                                put("role", "user")
                                put("content", prompt)
                            }
                        )
                    )
                }

                val request = okhttp3.Request.Builder()
                    .url("https://api.anthropic.com/v1/messages")
                    .addHeader("x-api-key", key.keyValue)
                    .addHeader("anthropic-version", "2023-06-01")
                    .addHeader("content-type", "application/json")
                    .post(
                        okhttp3.RequestBody.create(
                            "application/json; charset=utf-8".toMediaTypeOrNull(),
                            body.toString()
                        )
                    )
                    .build()

                client.await(request).let { (code, raw, retry) ->
                    parseHttpOutcome(code, raw, retry) { json ->
                        json.getJSONArray("content").getJSONObject(0).getString("text")
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: java.io.IOException) {
                AiCallResult.TransientError("تعذّر الاتصال بالمزوّد")
            } catch (e: Exception) {
                AiCallResult.PermanentError(e.message)
            }
        }
}

/**
 * ChatGPT adapter — calls the OpenAI chat completions API. Same
 * key/fallback contract as every other adapter.
 */
class ChatGptAdapter(
    private val client: okhttp3.OkHttpClient = NexisHttp
) : AiModelAdapter {
    override val providerId = "chatgpt"
    override val displayName = "ChatGPT"

    override suspend fun send(prompt: String, key: ApiKeyEntry): AiCallResult =
        withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val body = org.json.JSONObject().apply {
                    put("model", "gpt-4o-mini")
                    put(
                        "messages",
                        org.json.JSONArray().put(
                            org.json.JSONObject().apply {
                                put("role", "user")
                                put("content", prompt)
                            }
                        )
                    )
                }

                val request = okhttp3.Request.Builder()
                    .url("https://api.openai.com/v1/chat/completions")
                    .addHeader("Authorization", "Bearer ${key.keyValue}")
                    .addHeader("content-type", "application/json")
                    .post(
                        okhttp3.RequestBody.create(
                            "application/json; charset=utf-8".toMediaTypeOrNull(),
                            body.toString()
                        )
                    )
                    .build()

                client.await(request).let { (code, raw, retry) ->
                    parseHttpOutcome(code, raw, retry) { json ->
                        json.getJSONArray("choices")
                            .getJSONObject(0)
                            .getJSONObject("message")
                            .getString("content")
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: java.io.IOException) {
                AiCallResult.TransientError("تعذّر الاتصال بالمزوّد")
            } catch (e: Exception) {
                AiCallResult.PermanentError(e.message)
            }
        }
}

/**
 * Gemini adapter — calls the Google AI Studio generateContent endpoint
 * (the free-tier API the user is starting with, per current plan).
 *
 * Model IDs are tried in order; if Google reports one as retired / not
 * available, the next one is used automatically so a renamed model never
 * breaks the assistant again.
 */
/** Shared HTTP client: generous read timeout so slow "thinking" models don't fail with a connection error. */
internal val NexisHttp: okhttp3.OkHttpClient = okhttp3.OkHttpClient.Builder()
    .connectTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
    .readTimeout(90, java.util.concurrent.TimeUnit.SECONDS)
    .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
    .build()

class GeminiAdapter(
    private val client: okhttp3.OkHttpClient = NexisHttp
) : AiModelAdapter {
    override val providerId = "gemini"
    override val displayName = "Gemini"

    private val modelIds = listOf("gemini-3.5-flash", "gemini-3.1-flash-lite", "gemini-2.5-flash")

    override suspend fun send(prompt: String, key: ApiKeyEntry): AiCallResult {
        var last: AiCallResult = AiCallResult.PermanentError("النموذج غير متاح حاليًا لدى المزوّد")
        for (model in modelIds) {
            val r = callModel(model, prompt, key)
            if (r !is AiCallResult.PermanentError || !r.raw.orEmpty().contains("غير متاح")) return r
            last = r
        }
        return last
    }

    private suspend fun callModel(model: String, prompt: String, key: ApiKeyEntry): AiCallResult =
        withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val body = org.json.JSONObject().apply {
                    put(
                        "contents",
                        org.json.JSONArray().put(
                            org.json.JSONObject().apply {
                                put(
                                    "parts",
                                    org.json.JSONArray().put(
                                        org.json.JSONObject().apply { put("text", prompt) }
                                    )
                                )
                            }
                        )
                    )
                }

                val request = okhttp3.Request.Builder()
                    .url("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent")
                    .addHeader("content-type", "application/json")
                    .addHeader("x-goog-api-key", key.keyValue.trim())
                    .post(
                        okhttp3.RequestBody.create(
                            "application/json; charset=utf-8".toMediaTypeOrNull(),
                            body.toString()
                        )
                    )
                    .build()

                client.await(request).let { (code, raw, retry) ->
                    parseHttpOutcome(code, raw, retry) { json ->
                        val parts = json.getJSONArray("candidates")
                            .getJSONObject(0)
                            .getJSONObject("content")
                            .getJSONArray("parts")
                        buildString {
                            for (i in 0 until parts.length()) {
                                val part = parts.getJSONObject(i)
                                if (!part.optBoolean("thought", false)) append(part.optString("text"))
                            }
                        }.ifBlank { throw IllegalStateException("empty") }
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: java.io.IOException) {
                AiCallResult.TransientError("تعذّر الاتصال بالمزوّد")
            } catch (e: Exception) {
                AiCallResult.PermanentError(e.message)
            }
        }
}

/**
 * Runs an OkHttp call so that cancelling the coroutine (the Stop button)
 * really cancels the underlying HTTP request instead of letting it run on.
 */
private suspend fun okhttp3.OkHttpClient.await(request: okhttp3.Request): HttpReply =
    suspendCancellableCoroutine { cont ->
        val call = newCall(request)
        cont.invokeOnCancellation { runCatching { call.cancel() } }
        call.enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                if (cont.isActive) cont.resumeWith(Result.failure(e))
            }
            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                response.use {
                    val out = try {
                        val body = it.body?.string()
                        // Only a 429 carries a meaningful "try again in" hint.
                        HttpReply(it.code, body, if (it.code == 429) retryAfterMs(it, body) else null)
                    } catch (e: java.io.IOException) {
                        if (cont.isActive) cont.resumeWith(Result.failure(e)); return
                    }
                    if (cont.isActive) cont.resumeWith(Result.success(out))
                }
            }
        })
    }

/** code + body + (for 429) how long the provider says to wait. Destructures like the old Pair. */
internal data class HttpReply(val code: Int, val raw: String?, val retryAfterMs: Long?)

/**
 * Reads the provider's own "when can I try again" from a 429: the standard Retry-After header,
 * OpenAI's x-ratelimit-reset-* ("6m0s"), Anthropic's anthropic-ratelimit-*-reset (RFC 3339 time),
 * or Gemini's retryDelay in the JSON body. Returns null when the provider gave no hint.
 */
internal fun retryAfterMs(resp: okhttp3.Response, body: String?): Long? {
    val now = System.currentTimeMillis()
    val found = mutableListOf<Long>()

    resp.header("retry-after")?.trim()?.let { v ->
        v.toDoubleOrNull()?.let { found.add((it * 1000).toLong()) }
            ?: runCatching {
                val fmt = java.text.SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", java.util.Locale.US)
                found.add(fmt.parse(v)!!.time - now)
            }
    }
    if (found.isEmpty()) {
        for (name in resp.headers.names()) {
            val low = name.lowercase()
            val v = resp.header(name) ?: continue
            if (low.startsWith("x-ratelimit-reset")) parseDurationMs(v)?.let { found.add(it) }
            else if (low.startsWith("anthropic-ratelimit-") && low.endsWith("-reset")) {
                runCatching {
                    val t = java.time.Instant.parse(v.trim()).toEpochMilli() - now
                    found.add(t)
                }
            }
        }
    }
    if (found.isEmpty() && body != null) {
        Regex("\"retryDelay\"\\s*:\\s*\"([0-9.]+)s\"").find(body)?.groupValues?.get(1)?.toDoubleOrNull()
            ?.let { found.add((it * 1000).toLong()) }
    }
    val ms = found.filter { it > 0 }.maxOrNull() ?: return null
    return ms.coerceIn(1_000L, 24L * 3600_000L)
}

/** "1h2m3.5s", "6m0s", "20ms", "45s" -> milliseconds. */
private fun parseDurationMs(text: String): Long? {
    val m = Regex("^(?:(\\d+)h)?(?:(\\d+)m(?!s))?(?:([0-9.]+)s)?(?:(\\d+)ms)?$").find(text.trim()) ?: return null
    val (h, min, sec, ms) = m.destructured
    if (h.isEmpty() && min.isEmpty() && sec.isEmpty() && ms.isEmpty()) return null
    return (h.toLongOrNull() ?: 0L) * 3_600_000L + (min.toLongOrNull() ?: 0L) * 60_000L +
        ((sec.toDoubleOrNull() ?: 0.0) * 1000).toLong() + (ms.toLongOrNull() ?: 0L)
}

/** Pulls a short human message out of a provider's JSON error body (never returns raw JSON). */
internal fun friendlyApiError(code: Int, raw: String?): String {
    val fromJson = runCatching {
        val j = org.json.JSONObject(raw ?: "")
        when (val e = j.opt("error")) {
            is org.json.JSONObject -> e.optString("message").ifBlank { e.optString("status") }
            is String -> e
            else -> j.optString("message")
        }
    }.getOrNull().orEmpty().replace(Regex("\\s+"), " ").trim()
    val low = fromJson.lowercase()
    return when {
        code == 404 || "no longer available" in low || "not found" in low || "deprecated" in low ->
            "النموذج غير متاح حاليًا لدى المزوّد (HTTP $code)"
        code == 400 && ("api key" in low || "api_key" in low) -> "مفتاح API غير صالح"
        code == 401 || code == 403 -> "المفتاح غير صالح أو لا يملك صلاحية (HTTP $code)"
        code == 429 -> "تم تجاوز حصة الاستخدام (HTTP 429)"
        code in 500..599 -> "خطأ مؤقت من خادم المزوّد (HTTP $code)"
        fromJson.isNotBlank() -> fromJson.take(140)
        else -> "خطأ غير متوقع (HTTP $code)"
    }
}

/**
 * Shared HTTP-status -> AiCallResult mapping so every adapter treats
 * quota/auth/server errors the same way for the fallback logic upstream.
 * [extractText] pulls the actual reply text out of that provider's own
 * response shape once we know the call succeeded.
 */
private inline fun parseHttpOutcome(
    code: Int,
    raw: String?,
    retryAfterMs: Long?,
    extractText: (org.json.JSONObject) -> String
): AiCallResult {
    return when {
        code == 200 && raw != null -> {
            try {
                AiCallResult.Success(extractText(org.json.JSONObject(raw)))
            } catch (e: Exception) {
                AiCallResult.PermanentError("رد غير متوقع من المزوّد")
            }
        }
        code == 401 || code == 403 -> AiCallResult.PermanentError(friendlyApiError(code, raw))
        code == 429 -> AiCallResult.QuotaExceeded(friendlyApiError(code, raw), retryAfterMs)
        code in 500..599 -> AiCallResult.TransientError(friendlyApiError(code, raw))
        else -> AiCallResult.PermanentError(friendlyApiError(code, raw))
    }
}

/**
 * Generic adapter for any OpenAI-compatible chat-completions endpoint.
 * Used for KiMi (Moonshot) and for user-added custom models.
 */
open class OpenAiCompatibleAdapter(
    override val providerId: String,
    override val displayName: String,
    private val baseUrl: String,
    private val model: String,
    private val client: okhttp3.OkHttpClient = NexisHttp
) : AiModelAdapter {

    override suspend fun send(prompt: String, key: ApiKeyEntry): AiCallResult =
        withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val body = org.json.JSONObject().apply {
                    put("model", model)
                    put(
                        "messages",
                        org.json.JSONArray().put(
                            org.json.JSONObject().apply {
                                put("role", "user")
                                put("content", prompt)
                            }
                        )
                    )
                }
                val request = okhttp3.Request.Builder()
                    .url(baseUrl)
                    .addHeader("Authorization", "Bearer ${key.keyValue}")
                    .addHeader("content-type", "application/json")
                    .post(
                        okhttp3.RequestBody.create(
                            "application/json; charset=utf-8".toMediaTypeOrNull(),
                            body.toString()
                        )
                    )
                    .build()
                client.await(request).let { (code, raw, retry) ->
                    parseHttpOutcome(code, raw, retry) { json ->
                        json.getJSONArray("choices").getJSONObject(0)
                            .getJSONObject("message").getString("content")
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: java.io.IOException) {
                AiCallResult.TransientError("تعذّر الاتصال بالمزوّد")
            } catch (e: Exception) {
                AiCallResult.PermanentError(e.message)
            }
        }
}

class KimiAdapter : OpenAiCompatibleAdapter(
    providerId = "kimi",
    displayName = "KiMi",
    baseUrl = "https://api.moonshot.ai/v1/chat/completions",
    model = "moonshot-v1-8k"
)

/**
 * Local, fully on-device model (GGUF via llama.cpp). No API key, no network.
 */
class LocalModelAdapter(private val context: android.content.Context) : AiModelAdapter {
    override val providerId = "local"
    override val displayName = "Qwen (محلي)"

    override suspend fun send(prompt: String, key: ApiKeyEntry): AiCallResult {
        val info = com.nadidstudio.nexis.data.LocalModelStore.current(context)
            ?: return AiCallResult.PermanentError("لم يتم اختيار ملف النموذج المحلي بعد")
        val err = com.nadidstudio.nexis.engine.LocalLlamaEngine.ensureLoaded(context, info.uri)
        if (err != null) return AiCallResult.PermanentError(err)
        val text = com.nadidstudio.nexis.engine.LocalLlamaEngine.chat(promptToMessages(prompt))
        return if (text.isBlank() || text.matches(Regex("[\\s.\u2026]*"))) AiCallResult.TransientError("رد فارغ من النموذج المحلي — جرّب رسالة أقصر أو نموذجًا أكبر") else AiCallResult.Success(text)
    }

    /** Splits the assistant's flat "system\n\nuser: ..\nassistant: .." prompt back into chat turns. */
    private fun promptToMessages(prompt: String): List<Pair<String, String>> {
        val turn = Regex("(?m)^(?=(?:user|assistant): )")
        val first = Regex("(?m)^(?:user|assistant): ").find(prompt)?.range?.first ?: prompt.length
        val system = prompt.substring(0, first).trim().take(2000) + "\nReply in the same language the user writes in."
        val out = mutableListOf("system" to system)
        prompt.substring(first).split(turn).filter { it.isNotBlank() }.forEach { seg ->
            val role = if (seg.startsWith("assistant: ")) "assistant" else "user"
            out.add(role to seg.substringAfter(": ").trim())
        }
        // Small on-device context (2048 tokens): keep only the most recent turns.
        val system0 = out.first()
        val turns = out.drop(1).takeLast(4).mapIndexed { i, m -> m.first to m.second.take(if (i == 3) 1000 else 500) }
        return listOf(system0) + turns
    }
}
