package com.nadidstudio.nexis.data

import android.content.Context
import com.nadidstudio.nexis.models.OpenAiCompatibleAdapter
import com.nadidstudio.nexis.orchestration.ModelRegistry
import org.json.JSONArray
import org.json.JSONObject

/** User-added OpenAI-compatible models (name + endpoint URL + model id). API keys live in SecureKeyStore. */
object CustomModelStore {
    private fun prefs(c: Context) = c.getSharedPreferences("nexis_custom_models", Context.MODE_PRIVATE)

    private fun readAll(c: Context): JSONArray =
        try { JSONArray(prefs(c).getString("list", "[]")) } catch (_: Exception) { JSONArray() }

    /** Registers every saved custom model into [ModelRegistry]. Call once at startup. */
    fun registerAll(c: Context) {
        val arr = readAll(c)
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            ModelRegistry.registerCustomAdapter(
                OpenAiCompatibleAdapter(o.getString("id"), o.getString("name"), o.getString("url"), o.getString("model"))
            )
        }
    }

    /** Returns the new provider id, or null with [error] set to an Arabic message. */
    fun add(c: Context, name: String, url: String, model: String, error: (String) -> Unit): String? {
        val n = name.trim(); val u = url.trim(); val m = model.trim()
        if (n.isEmpty() || m.isEmpty()) { error("الاسم واسم النموذج مطلوبان"); return null }
        if (!u.startsWith("https://")) { error("الرابط يجب أن يبدأ بـ https://"); return null }
        val id = "custom_" + n.lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_').ifEmpty { System.currentTimeMillis().toString() }
        if (ModelRegistry.adapterFor(id) != null) { error("يوجد نموذج بنفس الاسم"); return null }
        val arr = readAll(c)
        arr.put(JSONObject().put("id", id).put("name", n).put("url", u).put("model", m))
        prefs(c).edit().putString("list", arr.toString()).apply()
        ModelRegistry.registerCustomAdapter(OpenAiCompatibleAdapter(id, n, u, m))
        return id
    }
}
