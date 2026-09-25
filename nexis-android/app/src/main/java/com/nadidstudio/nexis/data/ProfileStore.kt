package com.nadidstudio.nexis.data

import android.content.Context

/** Local profile (display name only for now). */
object ProfileStore {
    private fun prefs(c: Context) = c.getSharedPreferences("nexis_profile", Context.MODE_PRIVATE)
    fun name(c: Context): String = prefs(c).getString("name", "") ?: ""
    fun setName(c: Context, v: String) { prefs(c).edit().putString("name", v.trim()).apply() }
    fun email(c: Context): String = prefs(c).getString("email", "") ?: ""
    fun isSignedIn(c: Context): Boolean = prefs(c).getBoolean("signed_in", false)
    fun signIn(c: Context, name: String, email: String) {
        val e = prefs(c).edit().putBoolean("signed_in", true).putString("email", email)
        if (name(c).isBlank() && name.isNotBlank()) e.putString("name", name.trim())
        e.apply()
    }
    fun signOut(c: Context) { prefs(c).edit().putBoolean("signed_in", false).apply() }
}
