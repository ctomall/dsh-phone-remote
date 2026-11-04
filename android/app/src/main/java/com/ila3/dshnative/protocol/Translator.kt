package com.ila3.dshnative.protocol

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import java.util.concurrent.TimeUnit

/**
 * Anonymous machine translation for reasoning / assistant text.
 *
 * Mirrors the Web "Reasoning translation" providers: Google's gtx endpoint is
 * tried first, Bing's edge endpoint is the fallback. Long input is split into
 * paragraph-sized chunks so a single request stays small.
 */
object Translator {
    private val http = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .build()

    suspend fun translate(text: String, target: String = "zh-CN"): String = withContext(Dispatchers.IO) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return@withContext ""
        val chunks = split(trimmed, 1500)
        chunks.map { chunk ->
            // Bing first: the Web plugin defaults to it and it is reachable from
            // mainland networks where translate.googleapis.com is blocked.
            runCatching { bing(chunk, bingLanguage(target)) }.getOrElse { google(chunk, target) }
        }.joinToString("\n")
    }

    private fun split(text: String, limit: Int): List<String> {
        if (text.length <= limit) return listOf(text)
        val out = mutableListOf<String>()
        val current = StringBuilder()
        fun flush() {
            if (current.isNotEmpty()) {
                out.add(current.toString())
                current.clear()
            }
        }
        for (paragraph in text.split('\n')) {
            if (paragraph.length > limit) {
                flush()
                out.addAll(paragraph.chunked(limit))
                continue
            }
            if (current.length + paragraph.length + 1 > limit) flush()
            if (current.isNotEmpty()) current.append('\n')
            current.append(paragraph)
        }
        flush()
        return out
    }

    private fun google(text: String, target: String): String {
        val body = FormBody.Builder()
            .add("client", "gtx")
            .add("sl", "auto")
            .add("tl", target)
            .add("dt", "t")
            .add("q", text)
            .build()
        val request = Request.Builder()
            .url("https://translate.googleapis.com/translate_a/single")
            .post(body)
            .header("user-agent", "DSH-Native")
            .build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw DshException("翻译失败：HTTP ${response.code}")
            val payload = runCatching { JSONArray(response.body?.string().orEmpty()) }.getOrElse {
                throw DshException("翻译结果格式异常")
            }
            val segments = payload.optJSONArray(0) ?: throw DshException("翻译结果格式异常")
            val builder = StringBuilder()
            for (index in 0 until segments.length()) {
                builder.append(segments.optJSONArray(index)?.optString(0).orEmpty())
            }
            return builder.toString().ifBlank { text }
        }
    }

    private fun bing(text: String, target: String): String {
        val url = "https://edge.microsoft.com/translate/translatetext?to=$target&isEnterpriseClient=false"
        val request = Request.Builder()
            .url(url)
            .post(JSONArray().put(text).toString().toRequestBody("application/json".toMediaType()))
            .header("user-agent", "DSH-Native")
            .build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw DshException("翻译失败：HTTP ${response.code}")
            val payload = runCatching { JSONArray(response.body?.string().orEmpty()) }.getOrElse {
                throw DshException("翻译结果格式异常")
            }
            val first = payload.optJSONObject(0) ?: throw DshException("翻译结果格式异常")
            val translated = first.optJSONArray("translations")?.optJSONObject(0)?.optString("text")
                ?: throw DshException("翻译结果格式异常")
            return translated.ifBlank { text }
        }
    }

    private fun bingLanguage(target: String): String = when (target.lowercase()) {
        "zh-cn", "zh" -> "zh-Hans"
        "zh-tw", "zh-hk" -> "zh-Hant"
        else -> target
    }
}
