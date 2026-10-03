package com.nadidstudio.nexis.data

import android.content.Context
import com.nadidstudio.nexis.assistants.AssistantRole
import com.nadidstudio.nexis.ui.session.NexisSessionStore
import org.json.JSONObject

data class FreeModel(val id: String, val name: String, val context: String, val limit: String)

data class FreeProvider(
    val id: String,
    val name: String,
    val endpoint: String,
    val keyUrl: String,
    val note: String,
    val trainsOnData: Boolean,
    val models: List<FreeModel>
)

/**
 * Ready-made free-tier providers (all OpenAI-compatible), bundled from assets/free_providers.json.
 * Adding one registers it as a normal custom model, so keys, failover, limits and the model
 * picker all work exactly as for any other provider.
 */
object FreeProviderCatalog {

    fun load(c: Context): List<FreeProvider> = try {
        val root = JSONObject(c.assets.open("free_providers.json").bufferedReader().use { it.readText() })
        val arr = root.getJSONArray("providers")
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val ms = o.getJSONArray("models")
            FreeProvider(
                id = o.getString("id"), name = o.getString("name"), endpoint = o.getString("endpoint"),
                keyUrl = o.getString("keyUrl"), note = o.optString("note"), trainsOnData = o.optBoolean("trainsOnData"),
                models = (0 until ms.length()).map { j ->
                    val m = ms.getJSONObject(j)
                    FreeModel(m.getString("id"), m.optString("name", m.getString("id")), m.optString("context"), m.optString("limit"))
                }
            )
        }
    } catch (_: Exception) { emptyList() }

    /** Display name used when a model of this provider is added, e.g. "Groq · gpt-oss-120b". */
    fun entryName(p: FreeProvider, m: FreeModel): String = p.name + " · " + m.id.substringAfterLast('/').removeSuffix(":free")

    fun isAdded(c: Context, p: FreeProvider, m: FreeModel): Boolean =
        CustomModelStore.all(c).any { it.url == p.endpoint && it.model == m.id }

    /**
     * Adds [m] as a provider of its own, enables it for every assistant like the manual "+" does,
     * and — because the key belongs to the service, not to the model — copies the keys already
     * saved for another model of the same service. Returns the new provider id, or null.
     */
    fun add(c: Context, p: FreeProvider, m: FreeModel, error: (String) -> Unit): String? {
        val sibling = CustomModelStore.all(c).filter { it.url == p.endpoint }
        val id = CustomModelStore.add(c, entryName(p, m), p.endpoint, m.id, error) ?: return null
        AssistantRole.entries.forEach { NexisSessionStore.toggleProvider(it, id, true) }
        val store = NexisSessionStore.keyStoreForSettings
        sibling.firstNotNullOfOrNull { s -> store.getKeys(s.id).takeIf { it.isNotEmpty() } }
            ?.forEach { store.addKey(id, it.keyValue) }
        return id
    }
}
