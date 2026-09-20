package com.nadidstudio.nexis.models

/**
 * One unified error type every provider adapter must translate its own
 * errors into. This is what the fallback logic (key-switch / model-switch)
 * decides on — see the model-fallback-orchestration design.
 */
sealed class AiCallResult {
    data class Success(val text: String) : AiCallResult()

    /** Quota/rate limit hit on this key — try the next key for the same model. */
    data class QuotaExceeded(val raw: String? = null) : AiCallResult()

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
 * Placeholder Claude adapter — wiring in the real Anthropic API call is the
 * next concrete step once this skeleton builds and runs.
 */
class ClaudeAdapter : AiModelAdapter {
    override val providerId = "claude"
    override val displayName = "Claude"

    override suspend fun send(prompt: String, key: ApiKeyEntry): AiCallResult {
        TODO("Wire real Anthropic API call here")
    }
}
