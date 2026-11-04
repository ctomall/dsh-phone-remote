package com.ila3.dshnative.protocol

import android.content.Context
import org.json.JSONObject
import java.io.File

/**
 * Durable cache for machine translations.
 *
 * Keys are "<sessionId>:<messageKey>:<reason|body>"; entries are capped and the
 * oldest ones are dropped first so the file cannot grow without bound.
 */
class TranslationStore(context: Context) {
    private val file = File(context.filesDir, "translations.json")
    private val maxEntries = 600

    @Synchronized
    fun load(): Map<String, String> {
        if (!file.exists()) return emptyMap()
        return runCatching {
            val entries = JSONObject(file.readText()).optJSONObject("entries") ?: return emptyMap()
            buildMap {
                for (key in entries.keys()) {
                    val text = entries.optJSONObject(key)?.optString("t").orEmpty()
                    if (text.isNotEmpty()) put(key, text)
                }
            }
        }.getOrElse { emptyMap() }
    }

    @Synchronized
    fun put(key: String, text: String) {
        update { entries ->
            entries.put(key, JSONObject().put("t", text).put("at", System.currentTimeMillis()))
        }
    }

    @Synchronized
    fun remove(key: String) {
        update { entries -> entries.remove(key) }
    }

    private fun update(block: (JSONObject) -> Unit) {
        val root = runCatching {
            if (file.exists()) JSONObject(file.readText()) else JSONObject()
        }.getOrElse { JSONObject() }
        val entries = root.optJSONObject("entries") ?: JSONObject().also { root.put("entries", it) }
        block(entries)
        if (entries.length() > maxEntries) {
            val oldest = entries.keys().asSequence()
                .sortedBy { entries.optJSONObject(it)?.optLong("at", 0) ?: 0 }
                .take(entries.length() - maxEntries)
                .toList()
            oldest.forEach { entries.remove(it) }
        }
        runCatching { file.writeText(root.toString()) }
    }
}
