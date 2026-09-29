package com.nadidstudio.nexis.data

import android.content.Context
import android.content.SharedPreferences

/** User-level assistant behavior: personal instructions. Added to every assistant's system prompt. */
object AssistantPrefs {
    const val MAX_INSTRUCTIONS = 600
    private var prefs: SharedPreferences? = null

    fun attach(context: Context) {
        if (prefs == null) prefs = context.applicationContext.getSharedPreferences("nexis_assistant", Context.MODE_PRIVATE)
    }

    var instructions: String
        get() = prefs?.getString("instructions", "") ?: ""
        set(v) { prefs?.edit()?.putString("instructions", v.trim().take(MAX_INSTRUCTIONS))?.apply() }

    var notificationsOn: Boolean
        get() = prefs?.getBoolean("notifications", true) ?: true
        set(v) { prefs?.edit()?.putBoolean("notifications", v)?.apply() }

    /** Extra system-prompt text (empty when nothing is configured). */
    fun promptExtra(): String {
        return if (instructions.isBlank()) "" else "User's personal instructions: ${instructions.trim()}"
    }
}
