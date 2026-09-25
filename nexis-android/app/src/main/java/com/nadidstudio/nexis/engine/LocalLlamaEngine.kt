package com.nadidstudio.nexis.engine

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
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

    private external fun nativeLoad(fd: Int, nCtx: Int): Long
    private external fun nativeGenerate(handle: Long, prompt: String, maxTokens: Int): String
    private external fun nativeFree(handle: Long)

    /** Loads [uri] if it isn't already the currently-loaded model. Returns an error message, or null on success. */
    suspend fun ensureLoaded(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (handle != 0L && loadedUri == uri) return@withContext null
            try {
                ensureLibrary()
            } catch (e: UnsatisfiedLinkError) {
                return@withContext "تعذّر تحميل مكتبة النموذج المحلي: ${e.message}"
            }
            if (handle != 0L) {
                nativeFree(handle)
                handle = 0
                loadedUri = null
            }
            val pfd = context.contentResolver.openFileDescriptor(uri, "r")
                ?: return@withContext "تعذّر فتح ملف النموذج"
            pfd.use {
                val h = nativeLoad(it.fd, 2048)
                if (h == 0L) return@withContext "فشل تحميل النموذج — تأكد أن الملف GGUF صالح"
                handle = h
                loadedUri = uri
            }
            null
        }
    }

    suspend fun generate(prompt: String, maxTokens: Int = 256): String = withContext(Dispatchers.IO) {
        mutex.withLock {
            val h = handle
            if (h == 0L) return@withContext ""
            nativeGenerate(h, prompt, maxTokens)
        }
    }
}
