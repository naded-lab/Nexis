package com.nadidstudio.nexis.orchestration

import com.nadidstudio.nexis.assistants.AssistantRole
import com.nadidstudio.nexis.head.ModelHealthTracker
import com.nadidstudio.nexis.head.NetworkMonitor
import com.nadidstudio.nexis.models.AiCallResult
import com.nadidstudio.nexis.security.SecureKeyStore
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.ensureActive

/** What the caller (an assistant) ultimately gets back. */
sealed class OrchestratedResult {
    data class Success(val text: String, val providerId: String) : OrchestratedResult()
    data class Offline(val message: String = "No internet connection") : OrchestratedResult()

    /** Every key on every model in the chain was exhausted. */
    data class AllModelsFailed(val attempts: List<String>) : OrchestratedResult()
}

/**
 * Implements the confirmed fallback design:
 * - try each key of the current model before moving to the next model
 * - only after ALL of a model's keys are exhausted does it move to the
 *   next model in that assistant's chain (max 5 models)
 * - the "head" layer decides usability (connectivity + known-bad keys),
 *   this class just drives the loop and records outcomes back into it
 */
class FallbackOrchestrator(
    private val keyStore: SecureKeyStore,
    private val networkMonitor: NetworkMonitor,
    private val healthTracker: ModelHealthTracker
) {

    suspend fun sendWithFallback(
        prompt: String,
        role: AssistantRole,
        chain: List<String> = ModelRegistry.defaultChainFor(role)
    ): OrchestratedResult {
        // The local on-device model needs no network and no API key, so it
        // must not be blocked by an offline check the way remote providers are.
        val online = networkMonitor.isOnline()
        if (!online && chain.none { it == "local" }) {
            return OrchestratedResult.Offline()
        }

        val attemptLog = mutableListOf<String>()
        // Keys paused by a cooldown: skipped while anything else can answer, tried once at the end.
        val paused = mutableListOf<Pair<com.nadidstudio.nexis.models.AiModelAdapter, com.nadidstudio.nexis.models.ApiKeyEntry>>()

        for (providerId in chain.take(5)) {
            if (providerId != "local" && !online) continue
            val adapter = ModelRegistry.adapterFor(providerId) ?: continue
            val keys = if (providerId == "local") {
                listOf(com.nadidstudio.nexis.models.ApiKeyEntry("local", ""))
            } else {
                val usable = keyStore.getKeys(providerId).filter { healthTracker.isUsable(providerId, it.id) }
                usable.filterNot { healthTracker.isReadyNow(providerId, it.id) }
                    .filter { healthTracker.remainingMs(providerId, it.id) <= ModelHealthTracker.LAST_RESORT_MAX_MS }
                    .forEach { paused.add(adapter to it) }
                usable.filter { healthTracker.isReadyNow(providerId, it.id) }
            }

            for (key in keys) {
                coroutineContext.ensureActive() // Stop button: never start another attempt once cancelled
                attempt(adapter, key, prompt, attemptLog)?.let { return it }
            }
            // all keys for this provider are exhausted -> next provider in chain
        }

        // Nothing else worked: give the paused keys one last try (never worse than before the cooldown existed).
        for ((adapter, key) in paused) {
            coroutineContext.ensureActive()
            attempt(adapter, key, prompt, attemptLog)?.let { return it }
        }

        return OrchestratedResult.AllModelsFailed(attemptLog)
    }

    /** One call with one key; returns a result only on success, otherwise records the outcome and returns null. */
    private suspend fun attempt(
        adapter: com.nadidstudio.nexis.models.AiModelAdapter,
        key: com.nadidstudio.nexis.models.ApiKeyEntry,
        prompt: String,
        attemptLog: MutableList<String>
    ): OrchestratedResult? {
        val providerId = adapter.providerId
        when (val result = adapter.send(prompt, key)) {
            is AiCallResult.Success -> {
                healthTracker.markWorking(providerId, key.id)
                return OrchestratedResult.Success(result.text, providerId)
            }
            is AiCallResult.QuotaExceeded -> {
                attemptLog.add("${adapter.displayName} ← ${scrub(result.raw.orEmpty().ifBlank { "تم تجاوز حصة الاستخدام" }, key)}")
                healthTracker.markQuotaExceeded(providerId, key.id, result.retryAfterMs)
            }
            is AiCallResult.TransientError -> {
                attemptLog.add("${adapter.displayName} ← ${scrub(result.raw.orEmpty(), key)}")
                healthTracker.markServerDown(providerId, key.id)
            }
            is AiCallResult.PermanentError -> {
                attemptLog.add("${adapter.displayName} ← ${scrub(result.raw.orEmpty(), key)}")
                healthTracker.markInvalid(providerId, key.id) // bad key — never retried automatically
            }
        }
        return null
    }

    /** Provider error text must never echo the API key back into the chat or logs. */
    private fun scrub(text: String, key: com.nadidstudio.nexis.models.ApiKeyEntry): String {
        var t = text
        if (key.keyValue.length >= 6) t = t.replace(key.keyValue, "***")
        return t.take(140)
    }
}
