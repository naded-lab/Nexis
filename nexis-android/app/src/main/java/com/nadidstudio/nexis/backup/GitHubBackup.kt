package com.nadidstudio.nexis.backup

import android.content.Context
import android.util.Base64
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.nadidstudio.nexis.head.NetworkMonitor
import com.nadidstudio.nexis.security.SecureKeyStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Observable status shown in Settings. */
object BackupState {
    var status by mutableStateOf("لم يتم النسخ بعد")
    var running by mutableStateOf(false)
}

/**
 * Backs up Nexis data to a GitHub repo through the Contents API, using the
 * user's own token (stored in SecureKeyStore under "github").
 * Uploaded: projects/conversations JSON + files uploaded to projects.
 * NEVER uploaded: API keys or the GitHub token itself.
 */
object GitHubBackup {
    private const val ROOT = "nexis-backup"
    private const val DEBOUNCE_MS = 45_000L
    private val client = OkHttpClient()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    private var appContext: Context? = null
    private var keyStore: SecureKeyStore? = null

    fun attach(context: Context, store: SecureKeyStore) {
        appContext = context.applicationContext
        keyStore = store
    }

    private fun prefs(context: Context) = context.getSharedPreferences("nexis_backup", Context.MODE_PRIVATE)
    fun repo(context: Context): String = prefs(context).getString("repo", "") ?: ""
    fun setRepo(context: Context, value: String) = prefs(context).edit().putString("repo", value.trim()).apply()
    fun autoEnabled(context: Context): Boolean = prefs(context).getBoolean("auto", false)
    fun setAuto(context: Context, value: Boolean) = prefs(context).edit().putBoolean("auto", value).apply()

    fun isValidRepo(value: String) = Regex("^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$").matches(value.trim())

    /** Called after any data change; schedules one debounced backup if auto-backup is on. */
    fun onChanged() {
        val ctx = appContext ?: return
        if (!autoEnabled(ctx)) return
        job?.cancel()
        job = scope.launch {
            delay(DEBOUNCE_MS)
            backupNow(ctx)
        }
    }

    /** Runs a backup now; returns the human-readable result (also stored in [BackupState.status]). */
    suspend fun backupNow(context: Context): String = withContext(Dispatchers.IO) {
        val ctx = context.applicationContext
        val store = keyStore
        val repo = repo(ctx)
        val token = store?.getKeys("github")?.firstOrNull()?.keyValue
        val result = when {
            token == null -> "أضف توكن GitHub أولًا"
            !isValidRepo(repo) -> "اسم المستودع غير صالح (owner/repo)"
            !NetworkMonitor(ctx).isOnline() -> "لا يوجد اتصال بالإنترنت"
            else -> {
                BackupState.running = true
                try { upload(ctx, token, repo.trim()) } catch (e: BackupException) { e.message ?: "فشل النسخ" } catch (e: Exception) { "فشل النسخ: تحقق من الاتصال" }
                finally { BackupState.running = false }
            }
        }
        BackupState.status = result
        result
    }

    /** Replaces local data with the backup in the repo. Local data is kept once as nexis_data.pre_restore.json. */
    suspend fun restore(context: Context): String = withContext(Dispatchers.IO) {
        val ctx = context.applicationContext
        val repo = repo(ctx).trim()
        val token = keyStore?.getKeys("github")?.firstOrNull()?.keyValue
        val result = when {
            token == null -> "أضف توكن GitHub أولًا"
            !isValidRepo(repo) -> "اسم المستودع غير صالح (owner/repo)"
            !NetworkMonitor(ctx).isOnline() -> "لا يوجد اتصال بالإنترنت"
            else -> {
                BackupState.running = true
                try { doRestore(ctx, token, repo) } catch (e: BackupException) { e.message ?: "فشلت الاستعادة" } catch (e: Exception) { "فشلت الاستعادة: تحقق من الاتصال" }
                finally { BackupState.running = false }
            }
        }
        BackupState.status = result
        result
    }

    private fun doRestore(ctx: Context, token: String, repo: String): String {
        val dataBytes = download(token, repo, "data.json") ?: throw BackupException("لا توجد نسخة احتياطية في هذا المستودع")
        val json = String(dataBytes, Charsets.UTF_8)
        if (!com.nadidstudio.nexis.data.InMemoryAppStore.isValidBackup(json)) throw BackupException("ملف النسخة الاحتياطية تالف")

        var count = 0
        for ((dirName, dirType) in listDir(token, repo, "files")) {
            if (dirType != "dir" || !safeName(dirName)) continue
            for ((fileName, fileType) in listDir(token, repo, "files/$dirName")) {
                if (fileType != "file" || !safeName(fileName)) continue
                val bytes = download(token, repo, "files/$dirName/$fileName") ?: continue
                val dir = File(ctx.filesDir, "projects/$dirName").apply { mkdirs() }
                File(dir, fileName).writeBytes(bytes)
                count++
            }
        }
        com.nadidstudio.nexis.data.InMemoryAppStore.restoreFromJson(json)
        com.nadidstudio.nexis.ui.session.NexisSessionStore.reloadAfterRestore()
        return "تمت الاستعادة ($count ملف)"
    }

    private fun safeName(name: String) = name.isNotEmpty() && name != "." && name != ".." && !name.contains('/') && !name.contains('\\')

    private fun download(token: String, repo: String, path: String): ByteArray? {
        val req = request(token, url(repo, path)).header("Accept", "application/vnd.github.raw+json").get().build()
        client.newCall(req).execute().use { r ->
            return when (r.code) {
                200 -> r.body?.bytes()
                404 -> null
                else -> throw BackupException(errorFor(r.code))
            }
        }
    }

    /** Returns (name, type) pairs of a repo directory; empty if it doesn't exist. */
    private fun listDir(token: String, repo: String, path: String): List<Pair<String, String>> {
        client.newCall(request(token, url(repo, path)).get().build()).execute().use { r ->
            if (r.code == 404) return emptyList()
            if (r.code != 200) throw BackupException(errorFor(r.code))
            val arr = org.json.JSONArray(r.body?.string() ?: "[]")
            return List(arr.length()) { i -> arr.getJSONObject(i).let { it.getString("name") to it.getString("type") } }
        }
    }

    private class BackupException(message: String) : Exception(message)

    private fun upload(ctx: Context, token: String, repo: String): String {
        val files = mutableListOf<Pair<String, File>>()
        File(ctx.filesDir, "nexis_data.json").takeIf { it.exists() }?.let { files.add("data.json" to it) }
        File(ctx.filesDir, "projects").listFiles()?.forEach { dir ->
            dir.listFiles()?.filter { it.isFile }?.forEach { f -> files.add("files/${dir.name}/${f.name}" to f) }
        }
        if (files.isEmpty()) return "لا توجد بيانات للنسخ بعد"

        var uploaded = 0
        var skipped = 0
        for ((path, file) in files) {
            val bytes = file.readBytes()
            val remoteSha = getSha(token, repo, path)
            if (remoteSha != null && remoteSha == gitBlobSha(bytes)) { skipped++; continue }
            put(token, repo, path, bytes, remoteSha)
            uploaded++
        }
        val time = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())
        return "آخر نسخ: $time ($uploaded مرفوع، $skipped دون تغيير)"
    }

    private fun url(repo: String, path: String): HttpUrl =
        HttpUrl.Builder().scheme("https").host("api.github.com")
            .addPathSegments("repos/$repo/contents/$ROOT/$path").build()

    private fun request(token: String, url: HttpUrl) = Request.Builder().url(url)
        .header("Authorization", "Bearer $token")
        .header("Accept", "application/vnd.github+json")
        .header("X-GitHub-Api-Version", "2022-11-28")

    private fun getSha(token: String, repo: String, path: String): String? {
        client.newCall(request(token, url(repo, path)).get().build()).execute().use { r ->
            return when {
                r.code == 200 -> JSONObject(r.body?.string() ?: "{}").optString("sha").ifEmpty { null }
                r.code == 404 -> {
                    if (path == "data.json" && !repoExists(token, repo)) throw BackupException("المستودع غير موجود أو لا توجد صلاحية")
                    null
                }
                else -> throw BackupException(errorFor(r.code))
            }
        }
    }

    private fun repoExists(token: String, repo: String): Boolean {
        val u = HttpUrl.Builder().scheme("https").host("api.github.com").addPathSegments("repos/$repo").build()
        client.newCall(request(token, u).get().build()).execute().use { return it.code == 200 }
    }

    private fun put(token: String, repo: String, path: String, bytes: ByteArray, sha: String?) {
        val body = JSONObject().apply {
            put("message", "Nexis backup: $path")
            put("content", Base64.encodeToString(bytes, Base64.NO_WRAP))
            if (sha != null) put("sha", sha)
        }.toString().toRequestBody("application/json".toMediaType())
        client.newCall(request(token, url(repo, path)).put(body).build()).execute().use { r ->
            if (r.code != 200 && r.code != 201) throw BackupException(errorFor(r.code))
        }
    }

    private fun errorFor(code: Int) = when (code) {
        401 -> "التوكن غير صالح أو منتهي"
        403 -> "لا توجد صلاحية كتابة (اجعل التوكن يملك Contents: Read and write) أو تم بلوغ الحد"
        404 -> "المستودع غير موجود أو التوكن لا يصل إليه"
        422 -> "GitHub رفض الملف (تعارض)، أعد المحاولة"
        else -> "خطأ من GitHub ($code)"
    }

    /** Git blob SHA-1, so unchanged files can be skipped without uploading. */
    private fun gitBlobSha(bytes: ByteArray): String {
        val md = MessageDigest.getInstance("SHA-1")
        md.update("blob ${bytes.size}\u0000".toByteArray())
        md.update(bytes)
        return md.digest().joinToString("") { "%02x".format(it) }
    }
}
