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
    fun buildContext(project: Project?): String {
        if (project == null || project.uploadedFilePaths.isEmpty()) return ""
        val sb = StringBuilder("Project files (shared context for this project):\n")
        var remaining = MAX_CONTEXT_CHARS
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
