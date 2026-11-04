package com.ila3.dshnative.protocol

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import java.io.File
import java.security.MessageDigest

data class AppUpdate(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
    val sha256: String,
    val notes: String,
)

/** Public update feed, served from {base}updates/android.json. */
class UpdateClient {
    private val http = OkHttpClient()

    suspend fun check(manifestUrl: String): AppUpdate? = withContext(Dispatchers.IO) {
        val url = manifestUrl.toHttpUrlOrNull() ?: throw DshException("更新地址无效")
        val request = Request.Builder().url(url).header("cache-control", "no-cache").build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw DshException("检查更新失败：HTTP ${response.code}")
            val json = org.json.JSONObject(response.body?.string().orEmpty())
            val versionCode = json.optInt("versionCode", 0)
            if (versionCode <= 0) return@use null
            val apk = json.optString("apkUrl")
            val resolved = if (apk.startsWith("http")) apk else url.resolve(apk)?.toString() ?: "$url/$apk"
            AppUpdate(
                versionCode = versionCode,
                versionName = json.optString("versionName", versionCode.toString()),
                apkUrl = resolved,
                sha256 = json.optString("sha256").lowercase(),
                notes = json.optString("notes", ""),
            )
        }
    }

    companion object {
        /** Public Git-hosted manifest; used before the gateway fallback. */
        const val GIT_MANIFEST = "https://raw.githubusercontent.com/ctomall/dsh-phone-remote/main/updates/android.json"
    }

    suspend fun download(update: AppUpdate, target: File, onProgress: (Int) -> Unit = {}): File = withContext(Dispatchers.IO) {
        target.parentFile?.mkdirs()
        val request = Request.Builder().url(update.apkUrl).build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw DshException("下载安装包失败：HTTP ${response.code}")
            val body = response.body ?: throw DshException("安装包为空")
            val total = body.contentLength()
            body.byteStream().use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(16 * 1024)
                    var copied = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        output.write(buffer, 0, read)
                        copied += read
                        if (total > 0) onProgress(((copied * 100) / total).toInt().coerceIn(0, 100))
                    }
                }
            }
            onProgress(100)
        }
        if (update.sha256.isNotEmpty()) {
            val digest = MessageDigest.getInstance("SHA-256")
            target.inputStream().use { input ->
                val buffer = ByteArray(8192)
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    digest.update(buffer, 0, read)
                }
            }
            val actual = digest.digest().joinToString("") { "%02x".format(it) }
            if (actual != update.sha256) {
                target.delete()
                throw DshException("安装包校验失败")
            }
        }
        target
    }
}
