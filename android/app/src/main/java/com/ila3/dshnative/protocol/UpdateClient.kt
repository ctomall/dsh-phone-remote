package com.ila3.dshnative.protocol

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import java.io.File
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

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
        // Raw GitHub caches for several minutes; a per-check query makes the
        // manifest refresh immediately and does not affect signed fields.
        val fresh = url.newBuilder().addQueryParameter("_", System.currentTimeMillis().toString()).build()
        val request = Request.Builder().url(fresh).header("cache-control", "no-cache").build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw DshException("检查更新失败：HTTP ${response.code}")
            val json = org.json.JSONObject(response.body?.string().orEmpty())
            val versionCode = json.optInt("versionCode", 0)
            if (versionCode <= 0) return@use null
            verifyManifest(json, versionCode)
            val apk = json.optString("apkUrl")
            val resolved = if (apk.startsWith("http")) {
                apk
            } else {
                // Accept both "DSHNative.apk" and the historical "updates/DSHNative.apk"
                // while the manifest itself lives under .../updates/.
                val relative = if (apk.startsWith("updates/") && url.encodedPath.endsWith("/updates/android.json")) {
                    apk.removePrefix("updates/")
                } else {
                    apk
                }
                url.resolve(relative)?.toString() ?: "$url/$relative"
            }
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

        /** ECDSA P-256 public key (X.509 DER, base64) for signed manifests. */
        private const val PUBLIC_KEY_B64 = "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEzcsZnRGxY3+4KdnLgCH9cHu/joD7VKxpaZfI45fDh4YSY3IBepXf4QHKKXZRP+UtI3YX5Mvs/sLjpDLKiVY3Cw=="
    }

    private fun verifyManifest(json: org.json.JSONObject, versionCode: Int) {
        val signature = json.optString("signature")
        if (signature.isBlank()) throw DshException("更新清单缺少签名")
        val canonical = buildString {
            append(versionCode).append('\n')
            append(json.optString("versionName")).append('\n')
            append(json.optString("apkUrl")).append('\n')
            append(json.optString("sha256").lowercase()).append('\n')
            append(json.optString("notes"))
        }
        val keyBytes = Base64.getDecoder().decode(PUBLIC_KEY_B64)
        val key = KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(keyBytes))
        val verifier = Signature.getInstance("SHA256withECDSA")
        verifier.initVerify(key)
        verifier.update(canonical.toByteArray(Charsets.UTF_8))
        if (!verifier.verify(Base64.getDecoder().decode(signature))) {
            throw DshException("更新清单签名无效")
        }
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
