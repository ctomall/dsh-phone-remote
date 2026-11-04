package com.ila3.dshnative.protocol

import android.content.Context
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

/**
 * Per-server cookie jar persisted as tab-separated name/value/path/expiry rows.
 * DSH's device cookie and browser-session cookie are host-scoped, so one jar is
 * kept for each configured server profile.
 */
class CookieStore(context: Context, private val key: String) : CookieJar {
    private val prefs = context.getSharedPreferences("dsh_native", Context.MODE_PRIVATE)
    private val storeKey = "cookies_$key"

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        if (cookies.isEmpty()) return
        val merged = loadAll().associateBy { it.name }.toMutableMap()
        cookies.forEach { merged[it.name] = it }
        val lines = merged.values.joinToString("\n") {
            listOf(it.name, it.value, it.path, if (it.secure) "1" else "0", it.expiresAt.toString(), it.domain).joinToString("\t")
        }
        prefs.edit().putString(storeKey, lines).apply()
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        return loadAll().filter { cookie ->
            cookie.expiresAt > System.currentTimeMillis() &&
                (cookie.domain == url.host || cookie.domain == ".${url.host}" || url.host.endsWith(cookie.domain.removePrefix(".")))
        }
    }

    fun header(): String = loadAll()
        .filter { it.expiresAt > System.currentTimeMillis() }
        .joinToString("; ") { "${it.name}=${it.value}" }

    fun clear() {
        prefs.edit().remove(storeKey).apply()
    }

    private fun loadAll(): List<Cookie> {
        val raw = prefs.getString(storeKey, "") ?: return emptyList()
        return raw.lines().mapNotNull { line ->
            val parts = line.split('\t')
            if (parts.size < 6) return@mapNotNull null
            try {
                Cookie.Builder()
                    .name(parts[0])
                    .value(parts[1])
                    .path(parts[2].ifEmpty { "/" })
                    .domain(parts[5])
                    .expiresAt(parts[4].toLong())
                    .apply { if (parts[3] == "1") secure() }
                    .build()
            } catch (_: Exception) {
                null
            }
        }
    }
}
