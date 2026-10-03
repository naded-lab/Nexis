package com.nadidstudio.nexis.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.nadidstudio.nexis.assistants.Project
import java.io.File

/**
 * Files uploaded into a project. Stored privately under filesDir/projects/<id>/
 * and available to every conversation of that project (no re-upload).
 * v1 supports text-based files only (code, md, txt, json, csv…).
 */
object ProjectFiles {
    private const val MAX_BYTES = 1_000_000
    private const val MAX_CONTEXT_CHARS = 40_000

    fun displayName(path: String): String = File(path).name

    /** Returns an Arabic error message, or null on success. */
    fun import(context: Context, project: Project, uri: Uri): String? {
        return try {
            val resolver = context.contentResolver
            var name = "file"
            resolver.query(uri, null, null, null, null)?.use { c ->
                val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (i >= 0 && c.moveToFirst()) name = c.getString(i) ?: name
            }
            name = name.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().ifEmpty { "file" }

            val bytes = resolver.openInputStream(uri)?.use { input ->
                val out = java.io.ByteArrayOutputStream()
                val buf = ByteArray(8192)
                while (out.size() <= MAX_BYTES) {
                    val n = input.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                }
                out.toByteArray()
            } ?: return "تعذّر فتح الملف"
            if (bytes.size > MAX_BYTES) return "الملف كبير جدًا (الحد الأقصى 1 ميجابايت)"
            if (bytes.any { it.toInt() == 0 }) return "نوع الملف غير مدعوم حاليًا (نصوص وأكواد فقط)"

            val dir = File(context.filesDir, "projects/${project.id}").apply { mkdirs() }
            val target = File(dir, name)
            target.writeBytes(bytes)
            if (!project.uploadedFilePaths.contains(target.absolutePath)) {
                project.uploadedFilePaths.add(target.absolutePath)
            }
            InMemoryAppStore.persist()
            null
        } catch (e: Exception) {
            "فشل رفع الملف"
        }
    }

    fun remove(project: Project, path: String) {
        project.uploadedFilePaths.remove(path)
        runCatching { File(path).delete() }
        InMemoryAppStore.persist()
    }

    /** Text block prepended to the prompt so every conversation in the project sees the files. */
    private class Chunk(val file: String, val idx: Int, val text: String, val score: Int)

    /**
     * [query] == null → whole files (up to [maxChars]).
     * [query] != null → only the chunks most relevant to the question (for small on-device models).
     */
    fun buildContext(project: Project?, query: String? = null, maxChars: Int = MAX_CONTEXT_CHARS): String {
        if (project == null || project.uploadedFilePaths.isEmpty()) return ""
        if (query != null) {
            val words = query.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).filter { it.length >= 3 }.toSet()
            val chunks = mutableListOf<Chunk>()
            for (path in project.uploadedFilePaths.toList()) {
                val text = runCatching { File(path).readText() }.getOrNull() ?: continue
                val name = displayName(path)
                var i = 0; var n = 0
                while (i < text.length) {
                    val end = minOf(i + 700, text.length)
                    val piece = text.substring(i, end)
                    val low = piece.lowercase()
                    chunks.add(Chunk(name, n++, piece, words.count { low.contains(it) }))
                    i = end
                }
            }
            var picked = chunks.filter { it.score > 0 }.sortedByDescending { it.score }
            if (picked.isEmpty()) picked = chunks.filter { it.idx == 0 }
            val out = mutableListOf<Chunk>()
            var used = 0
            for (c in picked) { if (used + c.text.length > maxChars) continue; out.add(c); used += c.text.length }
            if (out.isEmpty()) return ""
            val sb = StringBuilder("Relevant excerpts from the project files:\n")
            out.sortedWith(compareBy({ it.file }, { it.idx })).forEach {
                sb.append("\n--- ").append(it.file).append(" (part ").append(it.idx + 1).append(") ---\n").append(it.text).append('\n')
            }
            return sb.toString()
        }
        val sb = StringBuilder("Project files (shared context for this project):\n")
        var remaining = maxChars
        for (path in project.uploadedFilePaths.toList()) {
            if (remaining <= 0) break
            val text = runCatching { File(path).readText() }.getOrNull() ?: continue
            val part = if (text.length > remaining) text.take(remaining) + "\n…[truncated]" else text
            sb.append("\n--- ").append(displayName(path)).append(" ---\n").append(part).append('\n')
            remaining -= part.length
        }
        return sb.toString()
    }
}
