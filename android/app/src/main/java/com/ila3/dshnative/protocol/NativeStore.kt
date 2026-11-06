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
private val DefaultNavOrder = listOf("now", "tasks", "files", "settings", "automation", "team")

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

    fun notifyEnabled(): Boolean = prefs.getBoolean("notify_enabled", true)

    fun setNotifyEnabled(value: Boolean) {
        prefs.edit().putBoolean("notify_enabled", value).apply()
    }

    fun keepAlive(): Boolean = prefs.getBoolean("keep_alive", false)

    fun setKeepAlive(value: Boolean) {
        prefs.edit().putBoolean("keep_alive", value).apply()
    }

    fun navOrder(): List<String> = prefs.getString("nav_order", "")
        ?.split(',')?.filter { it.isNotBlank() }?.takeIf { it.isNotEmpty() }
        ?: DefaultNavOrder

    fun setNavOrder(order: List<String>) {
        prefs.edit().putString("nav_order", order.joinToString(",")).apply()
    }

    fun navHidden(): Set<String> = prefs.getString("nav_hidden", "")
        ?.split(',')?.filter { it.isNotBlank() }?.toSet()
        ?.takeIf { it.isNotEmpty() }
        ?: setOf("automation", "team")

    fun setNavHidden(hidden: Set<String>) {
        prefs.edit().putString("nav_hidden", hidden.joinToString(",")).apply()
    }

    fun thinkExpanded(): Boolean = prefs.getBoolean("think_expanded", false)

    fun setThinkExpanded(value: Boolean) {
        prefs.edit().putBoolean("think_expanded", value).apply()
    }

    fun multiDevice(): Boolean = prefs.getBoolean("multi_device", false)

    fun setMultiDevice(value: Boolean) {
        prefs.edit().putBoolean("multi_device", value).apply()
    }

    fun showDeviceRunning(): Boolean = prefs.getBoolean("show_device_running", true)

    fun setShowDeviceRunning(value: Boolean) {
        prefs.edit().putBoolean("show_device_running", value).apply()
    }

    fun showTime(): Boolean = prefs.getBoolean("show_time", true)

    fun setShowTime(value: Boolean) {
        prefs.edit().putBoolean("show_time", value).apply()
    }

    fun toolExpanded(): Boolean = prefs.getBoolean("tool_expanded", false)

    fun setToolExpanded(value: Boolean) {
        prefs.edit().putBoolean("tool_expanded", value).apply()
    }

    fun autoScroll(): Boolean = prefs.getBoolean("auto_scroll", true)

    fun setAutoScroll(value: Boolean) {
        prefs.edit().putBoolean("auto_scroll", value).apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }
}
