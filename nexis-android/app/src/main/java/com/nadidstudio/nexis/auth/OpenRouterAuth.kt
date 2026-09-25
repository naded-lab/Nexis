package com.nadidstudio.nexis.auth

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import org.json.JSONObject

/**
 * OpenRouter PKCE login WITHOUT a callback URL: the browser page shows a code,
 * the user pastes it into the app, and we exchange it for an API key.
 * No client ID / secret needed.
 */
object OpenRouterAuth {
    private var verifier: String? = null
    private val client = OkHttpClient()

    private fun b64(bytes: ByteArray) = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

    /** Creates a fresh verifier and returns the URL to open in the browser. */
    fun startUrl(): String {
        val v = b64(ByteArray(32).also { SecureRandom().nextBytes(it) })
        verifier = v
        val challenge = b64(MessageDigest.getInstance("SHA-256").digest(v.toByteArray(Charsets.US_ASCII)))
        return "https://openrouter.ai/auth?code_challenge=$challenge&code_challenge_method=S256&key_label=Nexis"
    }

    /** Exchanges the pasted code for an API key. */
    suspend fun exchange(code: String): Result<String> = withContext(Dispatchers.IO) {
        val v = verifier ?: return@withContext Result.failure(IllegalStateException("اضغط «ربط حساب OpenRouter» أولًا"))
        try {
            val body = JSONObject().put("code", code.trim()).put("code_verifier", v).put("code_challenge_method", "S256")
            val req = Request.Builder()
                .url("https://openrouter.ai/api/v1/auth/keys")
                .post(RequestBody.create("application/json; charset=utf-8".toMediaTypeOrNull(), body.toString()))
                .build()
            client.newCall(req).execute().use { r ->
                val raw = r.body?.string().orEmpty()
                if (!r.isSuccessful) return@withContext Result.failure(IllegalStateException("فشل التبادل (${r.code})"))
                val key = JSONObject(raw).optString("key")
                if (key.isBlank()) Result.failure(IllegalStateException("لم يرجع مفتاح")) else Result.success(key)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
