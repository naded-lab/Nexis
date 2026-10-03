package com.nadidstudio.nexis.engine
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Thin Kotlin wrapper around the native llama.cpp session (nexis_llama.cpp).
 * The model file is opened via SAF and read from its original location —
 * never copied into the app — using the "/proc/self/fd/<fd>" trick so
 * llama.cpp's normal file loader (mmap) works unchanged.
 */
object LocalLlamaEngine {
    /** Human-readable load state shown in the UI. */
    var status by androidx.compose.runtime.mutableStateOf("لم يُحمَّل بعد")

    private var libraryLoaded = false
    private var handle: Long = 0
    private var loadedUri: Uri? = null
    private val mutex = Mutex()

    private fun ensureLibrary() {
        if (!libraryLoaded) {
            System.loadLibrary("nexis_llama")
            libraryLoaded = true
        }
    }

    /** Set by the session store while a local reply is being generated; receives the text so far. */
    @Volatile var partialListener: ((String) -> Unit)? = null

    /** Called from native code for every couple of tokens (cumulative, valid UTF-8). */
    @androidx.annotation.Keep
    fun onNativeBytes(bytes: ByteArray) { partialListener?.invoke(String(bytes, Charsets.UTF_8)) }

    private external fun nativeLoad(fd: Int, nCtx: Int): Long
    private external fun nativeChat(handle: Long, roles: Array<String>, contents: Array<String>, maxTokens: Int): ByteArray
    private external fun nativeAbort()
    private external fun nativeFree(handle: Long)

    /** Loads [uri] if it isn't already the currently-loaded model. Returns an error message, or null on success. */
    suspend fun ensureLoaded(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (handle != 0L && loadedUri == uri) { status = "جاهز"; return@withContext null }
            status = "جارٍ التحميل…"
            try {
                ensureLibrary()
            } catch (e: UnsatisfiedLinkError) {
                status = "فشل التحميل"; return@withContext "تعذّر تحميل مكتبة النموذج المحلي: ${e.message}"
            }
            if (handle != 0L) {
                nativeFree(handle)
                handle = 0
                loadedUri = null
            }
            val pfd = context.contentResolver.openFileDescriptor(uri, "r")
                ?: run { status = "فشل التحميل"; return@withContext "تعذّر فتح ملف النموذج" }
            pfd.use {
                val h = nativeLoad(it.fd, 3072)
                if (h == 0L) { status = "فشل التحميل"; return@withContext "فشل تحميل النموذج — تأكد أن الملف GGUF صالح" }
                handle = h
                loadedUri = uri
                status = "جاهز"
            }
            null
        }
    }

    /** Stops a running generation (returns whatever was produced so far). */
    fun abort() { if (libraryLoaded) nativeAbort() }

    /** [messages] = (role, text) pairs, role in system/user/assistant; formatted natively with the model's chat template. */
    suspend fun chat(messages: List<Pair<String, String>>, maxTokens: Int = 384): String = withContext(Dispatchers.IO) {
        mutex.withLock {
            // Stop pressed while waiting for the lock / model load: don't start generating at all.
            ensureActive()
            val h = handle
            if (h == 0L) return@withContext ""
            val bytes = nativeChat(h, messages.map { it.first }.toTypedArray(), messages.map { it.second }.toTypedArray(), maxTokens)
            String(bytes, Charsets.UTF_8).trim()
        }
    }
}
