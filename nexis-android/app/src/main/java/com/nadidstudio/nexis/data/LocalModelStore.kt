package com.nadidstudio.nexis.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns

/**
 * Remembers WHERE the user's local GGUF model lives (a persisted SAF URI).
 * The file is never copied into the app and never uploaded anywhere.
 */
object LocalModelStore {
    const val DOWNLOAD_URL =
        "https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF/resolve/main/qwen2.5-0.5b-instruct-q4_k_m.gguf"

    data class Info(val uri: Uri, val name: String, val size: Long)

    private fun prefs(c: Context) = c.getSharedPreferences("nexis_local_model", Context.MODE_PRIVATE)

    fun current(c: Context): Info? {
        val p = prefs(c)
        val uri = p.getString("uri", null) ?: return null
        return Info(Uri.parse(uri), p.getString("name", "") ?: "", p.getLong("size", 0L))
    }

    /** Returns null if OK, otherwise an Arabic error message. */
    fun save(c: Context, uri: Uri): String? {
        val cr = c.contentResolver
        // Must look like a GGUF file (magic bytes "GGUF").
        val magic = try {
            cr.openInputStream(uri)?.use { s -> ByteArray(4).also { if (s.read(it) != 4) return "الملف غير صالح" } }
        } catch (e: Exception) { return "تعذّر قراءة الملف" } ?: return "تعذّر قراءة الملف"
        if (String(magic, Charsets.US_ASCII) != "GGUF") return "الملف ليس بصيغة GGUF"

        try {
            cr.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: Exception) { /* some providers don't support persistence */ }

        var name = "model.gguf"; var size = 0L
        cr.query(uri, null, null, null, null)?.use { cur ->
            if (cur.moveToFirst()) {
                val n = cur.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val s = cur.getColumnIndex(OpenableColumns.SIZE)
                if (n >= 0) name = cur.getString(n) ?: name
                if (s >= 0) size = cur.getLong(s)
            }
        }
        prefs(c).edit().putString("uri", uri.toString()).putString("name", name).putLong("size", size).apply()
        return null
    }

    fun clear(c: Context) {
        current(c)?.let {
            try { c.contentResolver.releasePersistableUriPermission(it.uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) {}
        }
        prefs(c).edit().clear().apply()
    }

    /** True if the stored URI can still be opened (file not moved/deleted). */
    fun isReadable(c: Context, info: Info): Boolean = try {
        c.contentResolver.openInputStream(info.uri)?.use { true } ?: false
    } catch (_: Exception) { false }
}
