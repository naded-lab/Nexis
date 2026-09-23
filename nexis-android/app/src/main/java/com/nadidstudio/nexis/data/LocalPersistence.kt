package com.nadidstudio.nexis.data

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import com.nadidstudio.nexis.assistants.AssistantRole
import com.nadidstudio.nexis.assistants.ChatMessage
import com.nadidstudio.nexis.assistants.Conversation
import com.nadidstudio.nexis.assistants.Project
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors

/** Snapshot of everything that must survive process death. API keys are NOT here (see SecureKeyStore). */
data class PersistedState(
    val projects: List<Project>,
    val chains: Map<String, List<String>>
)

/**
 * Local-first JSON persistence (org.json, no extra dependency).
 * Writes are atomic (temp file + rename) and happen on one background thread;
 * the JSON string is built on the caller's thread so no list is read concurrently.
 */
class LocalPersistence(context: Context) {
    private val file = File(context.filesDir, "nexis_data.json")
    private val tmp = File(context.filesDir, "nexis_data.json.tmp")
    private val io = Executors.newSingleThreadExecutor()

    fun load(): PersistedState? = try {
        if (!file.exists()) null else parse(file.readText(), file.parentFile!!)
    } catch (e: Exception) {
        // Corrupt file: keep a copy for inspection and start clean instead of crashing.
        runCatching { file.copyTo(File(file.parentFile, "nexis_data.corrupt.json"), overwrite = true) }
        null
    }

    fun save(state: PersistedState) {
        val json = serialize(state)
        io.execute {
            runCatching {
                tmp.writeText(json)
                if (!tmp.renameTo(file)) { file.delete(); tmp.renameTo(file) }
            }
        }
    }

    private fun serialize(state: PersistedState): String {
        val root = JSONObject()
        root.put("projects", JSONArray().apply {
            state.projects.forEach { p ->
                put(JSONObject().apply {
                    put("id", p.id); put("name", p.name); put("role", p.assistantRole.name)
                    put("files", JSONArray(p.uploadedFilePaths.toList()))
                    put("conversations", JSONArray().apply {
                        p.conversations.toList().forEach { c ->
                            put(JSONObject().apply {
                                put("id", c.id); put("projectId", c.projectId)
                                put("messages", JSONArray().apply {
                                    c.messages.toList().forEach { m ->
                                        put(JSONObject().apply { put("role", m.role); put("text", m.text); put("t", m.timestampMillis) })
                                    }
                                })
                            })
                        }
                    })
                })
            }
        })
        root.put("chains", JSONObject().apply { state.chains.forEach { (k, v) -> put(k, JSONArray(v)) } })
        return root.toString()
    }

    /** True if [text] is a well-formed Nexis data file. */
    fun isValid(text: String): Boolean = runCatching { parse(text, file.parentFile!!) }.isSuccess

    /** Replaces the data file (used by restore). Keeps a copy of the previous file; waits for queued saves first. */
    fun replaceRaw(text: String) {
        io.submit {
            runCatching {
                if (file.exists()) file.copyTo(File(file.parentFile, "nexis_data.pre_restore.json"), overwrite = true)
                tmp.writeText(text)
                if (!tmp.renameTo(file)) { file.delete(); tmp.renameTo(file) }
            }
        }.get()
    }

    private fun parse(text: String, baseDir: File): PersistedState {
        val root = JSONObject(text)
        val projects = mutableListOf<Project>()
        val pa = root.optJSONArray("projects") ?: JSONArray()
        for (i in 0 until pa.length()) {
            val po = pa.getJSONObject(i)
            val convs = mutableListOf<Conversation>()
            val ca = po.optJSONArray("conversations") ?: JSONArray()
            for (j in 0 until ca.length()) {
                val co = ca.getJSONObject(j)
                val msgs = mutableListOf<ChatMessage>()
                val ma = co.optJSONArray("messages") ?: JSONArray()
                for (k in 0 until ma.length()) {
                    val mo = ma.getJSONObject(k)
                    msgs.add(ChatMessage(mo.getString("role"), mo.getString("text"), mo.optLong("t", 0L)))
                }
                convs.add(Conversation(co.getString("id"), co.getString("projectId"), msgs))
            }
            val files = mutableStateListOf<String>()
            val projectId = po.getString("id")
            // Rebase to this device's storage dir: a backup may come from another device/install.
            po.optJSONArray("files")?.let { fa -> for (f in 0 until fa.length()) files.add(File(baseDir, "projects/$projectId/${File(fa.getString(f)).name}").absolutePath) }
            projects.add(
                Project(po.getString("id"), po.getString("name"), AssistantRole.valueOf(po.getString("role")), files, convs)
            )
        }
        val chains = mutableMapOf<String, List<String>>()
        root.optJSONObject("chains")?.let { co ->
            co.keys().forEach { key ->
                val arr = co.getJSONArray(key)
                chains[key] = List(arr.length()) { arr.getString(it) }
            }
        }
        return PersistedState(projects, chains)
    }
}
