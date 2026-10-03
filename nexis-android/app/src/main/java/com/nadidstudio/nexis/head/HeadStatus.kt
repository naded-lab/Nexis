package com.nadidstudio.nexis.head

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

/**
 * The "head" is explicitly NOT an AI model — it's the status/health layer
 * that (1) confirms real internet connectivity and (2) tracks each key's
 * health so the fallback orchestrator knows what to skip.
 */
enum class KeyHealth {
    UNKNOWN,        // never tried yet
    WORKING,        // last call succeeded
    QUOTA_EXCEEDED, // try again later / move to next key
    INVALID,        // bad key — never retry automatically
    SERVER_DOWN     // provider-side issue — safe to retry later
}

/** Checks whether the device actually has a working internet path right now. */
class NetworkMonitor(private val context: Context) {

    fun isOnline(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val network = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}

/** Summary used by the quick model switcher (status dot next to each model). */
enum class ProviderStatus { READY, LIMITED, INVALID, NO_KEY }

/**
 * Health table: (providerId, keyId) -> KeyHealth.
 *
 * A quota hit pauses that key until the time the provider itself reported (Retry-After /
 * rate-limit reset headers); when the provider gave none, a per-provider default is used —
 * Claude's usage window is 5 hours, everyone else recovers within minutes. Pauses are saved
 * in [prefs] so they survive closing the app. A paused key is skipped while anything else
 * can answer; see FallbackOrchestrator.
 */
class ModelHealthTracker(
    private val prefs: android.content.SharedPreferences? = null,
    private val clock: () -> Long = { System.currentTimeMillis() }
) {

    private val table = mutableMapOf<String, KeyHealth>()
    private val blockedUntil = mutableMapOf<String, Long>()
    private val errorStreak = mutableMapOf<String, Int>()

    init {
        val now = clock()
        prefs?.all?.forEach { (k, v) ->
            if (k.startsWith(PREF_PREFIX) && v is Long && v > now) {
                val key = k.removePrefix(PREF_PREFIX)
                blockedUntil[key] = v
                table[key] = KeyHealth.QUOTA_EXCEEDED
            }
        }
    }

    @Synchronized
    fun healthOf(providerId: String, keyId: String): KeyHealth =
        table[tableKey(providerId, keyId)] ?: KeyHealth.UNKNOWN

    @Synchronized
    fun markWorking(providerId: String, keyId: String) {
        val k = tableKey(providerId, keyId)
        table[k] = KeyHealth.WORKING
        clearBlock(k)
        errorStreak.remove(k)
    }

    /** [retryAfterMs] = what the provider said (null -> provider default). */
    @Synchronized
    fun markQuotaExceeded(providerId: String, keyId: String, retryAfterMs: Long? = null) {
        val k = tableKey(providerId, keyId)
        table[k] = KeyHealth.QUOTA_EXCEEDED
        setBlock(k, clock() + (retryAfterMs ?: defaultCooldownMs(providerId)))
    }

    @Synchronized
    fun markInvalid(providerId: String, keyId: String) {
        table[tableKey(providerId, keyId)] = KeyHealth.INVALID
    }

    @Synchronized
    fun markServerDown(providerId: String, keyId: String) {
        val k = tableKey(providerId, keyId)
        val n = (errorStreak[k] ?: 0) + 1
        errorStreak[k] = n
        table[k] = KeyHealth.SERVER_DOWN
        if (n >= SERVER_ERRORS_BEFORE_PAUSE) setBlock(k, clock() + SERVER_COOLDOWN_MS)
    }

    /** A key is worth trying unless it is permanently bad. */
    @Synchronized
    fun isUsable(providerId: String, keyId: String): Boolean =
        healthOf(providerId, keyId) != KeyHealth.INVALID

    /** Usable AND not paused by a cooldown right now. */
    @Synchronized
    fun isReadyNow(providerId: String, keyId: String): Boolean =
        isUsable(providerId, keyId) && remainingMs(providerId, keyId) == 0L

    /** Milliseconds left on this key's pause (0 = not paused). */
    @Synchronized
    fun remainingMs(providerId: String, keyId: String): Long =
        ((blockedUntil[tableKey(providerId, keyId)] ?: 0L) - clock()).coerceAtLeast(0L)

    @Synchronized
    fun providerStatus(providerId: String, keyIds: List<String>): ProviderStatus = when {
        keyIds.isEmpty() -> ProviderStatus.NO_KEY
        keyIds.any { isReadyNow(providerId, it) } -> ProviderStatus.READY
        keyIds.all { healthOf(providerId, it) == KeyHealth.INVALID } -> ProviderStatus.INVALID
        else -> ProviderStatus.LIMITED
    }

    /** When the soonest paused key comes back (epoch ms), or null if nothing is paused. */
    @Synchronized
    fun limitedUntil(providerId: String, keyIds: List<String>): Long? =
        keyIds.filter { isUsable(providerId, it) }
            .mapNotNull { blockedUntil[tableKey(providerId, it)] }
            .filter { it > clock() }.minOrNull()

    /** Manual "the limit is over" — clears pauses and bad-key marks for these keys. */
    @Synchronized
    fun resetProvider(providerId: String, keyIds: List<String>) {
        keyIds.forEach { id ->
            val k = tableKey(providerId, id)
            table.remove(k); clearBlock(k); errorStreak.remove(k)
        }
    }

    private fun setBlock(k: String, until: Long) {
        blockedUntil[k] = until
        prefs?.edit()?.putLong(PREF_PREFIX + k, until)?.apply()
    }

    private fun clearBlock(k: String) {
        if (blockedUntil.remove(k) != null) prefs?.edit()?.remove(PREF_PREFIX + k)?.apply()
    }

    private fun tableKey(providerId: String, keyId: String) = "$providerId:$keyId"

    companion object {
        private const val PREF_PREFIX = "blocked_"
        private const val SERVER_COOLDOWN_MS = 60_000L
        private const val SERVER_ERRORS_BEFORE_PAUSE = 3
        /** Only used when the provider's response gave no retry time. */
        fun defaultCooldownMs(providerId: String): Long =
            if (providerId == "claude") 5L * 3600_000L else 120_000L
        /** Pauses longer than this are not worth a last-resort retry. */
        const val LAST_RESORT_MAX_MS = 10L * 60_000L
    }
}
