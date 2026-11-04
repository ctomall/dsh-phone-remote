package com.ila3.dshnative.protocol

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class ServerProfile(
    val id: String,
    val name: String,
    val url: String,
)

/** Small SharedPreferences-backed profile catalog; cookies live in CookieStore. */
class NativeStore(context: Context) {
    private val prefs = context.getSharedPreferences("dsh_native", Context.MODE_PRIVATE)

    fun profiles(): List<ServerProfile> {
        val raw = prefs.getString("profiles", "") ?: ""
        if (raw.isEmpty()) return emptyList()
        return try {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    val id = item.optString("id")
                    val url = item.optString("url")
                    if (id.isNotEmpty() && url.isNotEmpty()) {
                        add(ServerProfile(id, item.optString("name", "DSH"), url))
                    }
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveProfiles(profiles: List<ServerProfile>) {
        val array = JSONArray()
        profiles.forEach {
            array.put(JSONObject().put("id", it.id).put("name", it.name).put("url", it.url))
        }
        prefs.edit().putString("profiles", array.toString()).apply()
    }

    fun currentId(): String = prefs.getString("currentId", "") ?: ""

    fun setCurrent(id: String) {
        prefs.edit().putString("currentId", id).apply()
    }

    fun addProfile(url: String): ServerProfile {
        val all = profiles().toMutableList()
        val host = runCatching { android.net.Uri.parse(url).host }.getOrNull() ?: "DSH"
        var name = host.removePrefix("www.")
        var suffix = 2
        val base = name
        while (all.any { it.name == name }) {
            name = "$base ${suffix++}"
        }
        val profile = ServerProfile(UUID.randomUUID().toString(), name, url)
        all.add(profile)
        saveProfiles(all)
        setCurrent(profile.id)
        return profile
    }

    fun deleteProfile(id: String) {
        val all = profiles().filterNot { it.id == id }
        saveProfiles(all)
        if (currentId() == id) setCurrent(all.firstOrNull()?.id ?: "")
    }

    fun clear() {
        prefs.edit().clear().apply()
    }
}
