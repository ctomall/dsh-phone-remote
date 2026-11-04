package com.ila3.dshnative.protocol

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.TimeUnit

class DshException(
    message: String,
    val code: String = "client/error",
    val status: Int = 0,
) : Exception(message)

data class SessionSummary(
    val id: String,
    val updatedAt: Long,
    val running: Boolean,
    val blank: Boolean,
    val cwd: String?,
    val title: String? = null,
    val lastPromptAt: Long = 0L,
)

/**
 * Native DSH transport: pairing, typed unary `/api/<endpoint>` envelopes, and
 * the `/api/remote.mux` WebSocket stream carrier. All requests share one
 * per-profile cookie jar, so the device cookie and DSH browser session are
 * reused exactly like the WebView client.
 */
class DshClient private constructor(
    val base: HttpUrl,
    val cookies: CookieJar,
) {
    constructor(context: Context, baseUrl: String, cookieKey: String) : this(
        parseBase(baseUrl),
        CookieStore(context, cookieKey),
    )

    constructor(baseUrl: String, cookieJar: CookieJar) : this(parseBase(baseUrl), cookieJar)

    private val http: OkHttpClient = OkHttpClient.Builder()
        .cookieJar(cookies)
        .followRedirects(false)
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .pingInterval(25, TimeUnit.SECONDS)
        .build()

    suspend fun pair(code: String, deviceName: String = "Android Native") = withContext(Dispatchers.IO) {
        val form = FormBody.Builder()
            .add("code", code)
            .add("name", deviceName)
            .add("next", "")
            .build()
        var response = http.newCall(
            Request.Builder().url(base.resolve("pair")!!).post(form).build(),
        ).execute()
        var hops = 0
        while (response.isRedirect && hops < 8) {
            val location = response.header("Location") ?: break
            response.close()
            val next = base.resolve(location) ?: location.toHttpUrlOrNull() ?: break
            response = http.newCall(Request.Builder().url(next).get().build()).execute()
            hops++
        }
        response.use {
            if (!it.isSuccessful) {
                throw DshException("配对失败：HTTP ${it.code}", "pair/http", it.code)
            }
        }
    }

    suspend fun call(endpoint: String, args: JSONObject): JSONObject = withContext(Dispatchers.IO) {
        val envelope = JSONObject()
            .put("type", "client-request")
            .put("rpcId", UUID.randomUUID().toString())
            .put("method", endpoint)
            .put("payload", JSONObject().put("args", args))
        val body = envelope.toString().toRequestBody(JSON)
        val request = Request.Builder()
            .url(base.resolve("api/$endpoint")!!)
            .post(body)
            .build()
        http.newCall(request).execute().use { response ->
            if (response.code == 401 || response.code == 403) {
                throw DshException("登录已失效，请重新配对", "auth/required", response.code)
            }
            if (!response.isSuccessful) {
                throw DshException("$endpoint：HTTP ${response.code}", "rpc/http", response.code)
            }
            val text = response.body?.string().orEmpty()
            val json = runCatching { JSONObject(text) }.getOrElse {
                throw DshException("$endpoint：响应不是 JSON")
            }
            if (json.optString("type") != "server-response") {
                throw DshException("$endpoint：响应信封无效")
            }
            val result = json.optJSONObject("result") ?: throw DshException("$endpoint：缺少 result")
            if (!result.optBoolean("ok")) {
                val error = result.optJSONObject("error")
                throw DshException(
                    error?.optString("message") ?: "$endpoint 调用失败",
                    error?.optString("code") ?: "rpc/failed",
                )
            }
            result.optJSONObject("value") ?: JSONObject()
        }
    }

    suspend fun sessions(): List<SessionSummary> {
        val value = call("session/list", JSONObject().put("_request", JSONObject()))
        val items = value.optJSONArray("items") ?: JSONArray()
        return buildList {
            for (i in 0 until items.length()) {
                val item = items.optJSONObject(i) ?: continue
                val id = item.optString("sessionId")
                if (id.isEmpty()) continue
                val projections = item.optJSONObject("projections")?.optJSONObject("values")
                val title = projections?.optString("title")?.takeIf { it.isNotEmpty() && it != "null" }
                val metadata = projections?.optJSONObject("sessionListMetadata")
                add(
                    SessionSummary(
                        id = id,
                        updatedAt = item.optLong("updatedAt", 0L),
                        running = item.optBoolean("running", false),
                        blank = item.optBoolean("blank", false),
                        cwd = item.optString("cwd").takeIf { it.isNotEmpty() },
                        title = title,
                        lastPromptAt = metadata?.optLong("lastPromptAt", 0L) ?: 0L,
                    ),
                )
            }
        }
    }

    suspend fun createSession(cwd: String?): String {
        val request = JSONObject()
        if (!cwd.isNullOrBlank()) request.put("cwd", cwd)
        val value = call("session/create", JSONObject().put("request", request))
        return value.optString("sessionId").ifEmpty { throw DshException("创建会话失败") }
    }

    sealed class Attachment {
        data class Image(val mediaType: String, val data: String, val name: String) : Attachment()
        data class File(val receiptId: String, val name: String) : Attachment()
    }

    data class UploadReceipt(val receiptId: String, val name: String, val bytes: Long)




    suspend fun renameSession(sessionId: String, title: String): String {
        val value = call(
            "session/rename",
            JSONObject().put("request", JSONObject().put("sessionId", sessionId).put("title", title)),
        )
        return value.optString("title", title)
    }

    suspend fun cancelSession(sessionId: String) {
        call("session/cancel", JSONObject().put("request", JSONObject().put("sessionId", sessionId)))
    }

    suspend fun archiveSession(sessionId: String) {
        runCatching {
            call(
                "workspace/archiveSession",
                JSONObject().put("request", JSONObject().put("sessionId", sessionId).put("stopActivity", true)),
            )
        }
    }

    suspend fun removeQueued(sessionId: String, itemId: String) {
        updateQueue(sessionId, itemId, JSONObject().put("kind", "remove"))
    }

    suspend fun steerQueued(sessionId: String, itemId: String) {
        updateQueue(sessionId, itemId, JSONObject().put("kind", "steer"))
    }

    private suspend fun updateQueue(sessionId: String, itemId: String, action: JSONObject) {
        val request = JSONObject()
            .put("sessionId", sessionId)
            .put("itemId", itemId)
            .put("action", action)
        call("session/updateQueue", JSONObject().put("request", request))
    }

    suspend fun uploadFile(sessionId: String, bytes: ByteArray, name: String): UploadReceipt = withContext(Dispatchers.IO) {
        val url = base.resolve("api/session/uploadFileBinary")!!.newBuilder()
            .addQueryParameter("sessionId", sessionId)
            .addQueryParameter("name", name)
            .build()
        val body = bytes.toRequestBody("application/octet-stream".toMediaType())
        http.newCall(Request.Builder().url(url).post(body).build()).execute().use { response ->
            if (response.code == 401 || response.code == 403) {
                throw DshException("登录已失效，请重新配对", "auth/required", response.code)
            }
            val text = response.body?.string().orEmpty()
            val json = runCatching { JSONObject(text) }.getOrElse {
                throw DshException("上传失败：响应不是 JSON")
            }
            if (!json.optBoolean("ok")) {
                val error = json.optJSONObject("error")
                throw DshException(error?.optString("message") ?: "上传失败", error?.optString("code") ?: "upload/failed", response.code)
            }
            val value = json.optJSONObject("value") ?: throw DshException("上传失败：缺少回执")
            val receipt = value.optString("receiptId")
            val file = value.optJSONObject("file")
            if (receipt.isEmpty()) throw DshException("上传失败：缺少回执 id")
            UploadReceipt(receipt, file?.optString("name") ?: name, file?.optLong("bytes") ?: bytes.size.toLong())
        }
    }

    suspend fun prompt(sessionId: String, text: String, attachments: List<Attachment> = emptyList()) {
        val content = JSONArray()
        if (text.isNotBlank()) content.put(JSONObject().put("type", "text").put("text", text))
        attachments.forEach { attachment ->
            when (attachment) {
                is Attachment.Image -> content.put(
                    JSONObject()
                        .put("type", "image")
                        .put("mediaType", attachment.mediaType)
                        .put("data", attachment.data)
                        .put("name", attachment.name),
                )
                is Attachment.File -> content.put(
                    JSONObject()
                        .put("type", "file")
                        .put("receiptId", attachment.receiptId),
                )
            }
        }
        if (content.length() == 0) throw DshException("消息内容为空")
        val request = JSONObject()
            .put("requestId", UUID.randomUUID().toString())
            .put("sessionId", sessionId)
            .put("mode", "queue")
            .put("content", content)
            .put("clientTimeZone", java.util.TimeZone.getDefault().id)
        call("session/prompt", JSONObject().put("request", request))
    }

    /**
     * Open one logical stream on the mux socket.
     * @param endpoint e.g. `session/follow`.
     * @param args request arguments.
     * @param onValue one `item.value`.
     * @param onFailure transport or remote failure.
     * @return the WebSocket; cancel it to close the stream.
     */
    fun openStream(
        endpoint: String,
        args: JSONObject,
        onValue: (JSONObject) -> Unit,
        onFailure: (Throwable) -> Unit = {},
    ): WebSocket = openStreamHandle(endpoint, args, onValue, onFailure).socket

    class StreamHandle(val socket: WebSocket, val streamId: String) {
        fun sendItem(value: JSONObject) {
            socket.send(JSONObject().put("type", "item").put("streamId", streamId).put("value", value).toString())
        }

        fun cancel() {
            socket.cancel()
        }
    }

    fun openStreamHandle(
        endpoint: String,
        args: JSONObject,
        onValue: (JSONObject) -> Unit,
        onFailure: (Throwable) -> Unit = {},
    ): StreamHandle {
        val wsUrl = base.resolve("api/remote.mux")!!
        val request = Request.Builder()
            .url(wsUrl)
            .header("Cookie", cookies.loadForRequest(base).joinToString("; ") { "${it.name}=${it.value}" })
            .build()
        val streamId = UUID.randomUUID().toString()
        val socket = http.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                val message = JSONObject()
                    .put("type", "open")
                    .put("streamId", streamId)
                    .put("endpoint", endpoint)
                    .put("payload", JSONObject().put("args", args))
                webSocket.send(message.toString())
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                val message = runCatching { JSONObject(text) }.getOrNull() ?: return
                if (message.optString("streamId") != streamId) return
                when (message.optString("type")) {
                    "item" -> message.optJSONObject("value")?.let(onValue)
                    "error" -> onFailure(
                        DshException(
                            message.optJSONObject("error")?.optString("message") ?: "流式调用失败",
                            message.optJSONObject("error")?.optString("code") ?: "stream/failed",
                        ),
                    )
                    "end" -> Unit
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                onFailure(t)
            }
        })
        return StreamHandle(socket, streamId)
    }






    data class HistoryPage(val items: List<ChatItem>, val hasMore: Boolean, val oldestSeq: Long)

    suspend fun historyPage(sessionId: String, throughSeq: Long, beforeSeq: Long, maxMessages: Int = 200): HistoryPage {
        val request = JSONObject()
            .put("address", JSONObject().put("kind", "session").put("sessionId", sessionId))
            .put("throughSeq", throughSeq)
            .put("beforeSeq", beforeSeq)
            .put("maxMessages", maxMessages)
            .put("turnWindow", JSONObject().put("minMessages", 20).put("minTurns", 1))
        val value = call("session/page", JSONObject().put("request", request))
        val records = value.optJSONArray("records") ?: org.json.JSONArray()
        var oldest = Long.MAX_VALUE
        for (i in 0 until records.length()) {
            val seq = records.optJSONObject(i)?.optJSONObject("event")?.optLong("seq", 0L) ?: 0L
            if (seq in 1 until oldest) oldest = seq
        }
        return HistoryPage(
            items = ChatParser.parseRecords(records),
            hasMore = value.optBoolean("hasMore", false),
            oldestSeq = oldest,
        )
    }

    data class QuestionOption(val label: String, val description: String?)
    data class QuestionItem(
        val id: String,
        val question: String,
        val header: String?,
        val detail: String?,
        val options: List<QuestionOption>,
        val multiSelect: Boolean,
    )
    data class PendingQuestion(
        val callId: String,
        val state: String,
        val questions: List<QuestionItem>,
    )

    suspend fun pendingQuestions(sessionId: String): List<PendingQuestion> {
        val value = call("session/projections", JSONObject().put("request", JSONObject().put("sessionId", sessionId)))
        val questions = value.optJSONObject("values")?.optJSONObject("userQuestions") ?: return emptyList()
        val active = questions.optJSONArray("active") ?: return emptyList()
        return buildList {
            for (i in 0 until active.length()) {
                val pending = active.optJSONObject(i) ?: continue
                val callId = pending.optString("callId")
                if (callId.isEmpty()) continue
                val items = pending.optJSONArray("questions") ?: continue
                val parsed = buildList {
                    for (j in 0 until items.length()) {
                        val item = items.optJSONObject(j) ?: continue
                        val id = item.optString("id")
                        if (id.isEmpty()) continue
                        val options = buildList {
                            val array = item.optJSONArray("options") ?: JSONArray()
                            for (k in 0 until array.length()) {
                                val option = array.optJSONObject(k) ?: continue
                                val label = option.optString("label")
                                if (label.isNotEmpty()) add(QuestionOption(label, option.optString("description").takeIf { it.isNotEmpty() }))
                            }
                        }
                        add(
                            QuestionItem(
                                id = id,
                                question = item.optString("question"),
                                header = item.optString("header").takeIf { it.isNotEmpty() },
                                detail = item.optString("detail").takeIf { it.isNotEmpty() },
                                options = options,
                                multiSelect = item.optBoolean("multiSelect", false),
                            ),
                        )
                    }
                }
                if (parsed.isNotEmpty()) add(PendingQuestion(callId, pending.optString("state", "open"), parsed))
            }
        }
    }

    suspend fun answerQuestions(sessionId: String, callId: String, answers: JSONArray) {
        call(
            "userQuestions/answer",
            JSONObject()
                .put("agentId", sessionId)
                .put("callId", callId)
                .put("answer", JSONObject().put("answers", answers)),
        )
    }

    data class ModelChoice(val provider: String, val model: String, val name: String)
    data class ModelGroup(val id: String, val name: String, val models: List<ModelChoice>)
    data class ModelCatalogData(val default: ModelChoice?, val groups: List<ModelGroup>)

    suspend fun modelCatalog(): ModelCatalogData {
        val value = call("session/modelCatalog", JSONObject())
        val default = parseModel(value.optJSONObject("default"), null)
        val groupsArray = value.optJSONArray("groups") ?: JSONArray()
        val groups = buildList {
            for (i in 0 until groupsArray.length()) {
                val group = groupsArray.optJSONObject(i) ?: continue
                val id = group.optString("id")
                val name = group.optString("name", id)
                val modelsArray = group.optJSONArray("models") ?: JSONArray()
                val models = buildList {
                    for (j in 0 until modelsArray.length()) {
                        val model = modelsArray.optJSONObject(j) ?: continue
                        parseModel(model, id)?.let { add(it) }
                    }
                }
                if (models.isNotEmpty()) add(ModelGroup(id, name, models))
            }
        }
        return ModelCatalogData(default, groups)
    }

    suspend fun selectModel(sessionId: String, choice: ModelChoice, reasoningEffort: String? = null) {
        val args = JSONObject()
            .put("sessionId", sessionId)
            .put("provider", choice.provider)
            .put("model", choice.model)
        if (!reasoningEffort.isNullOrEmpty()) args.put("reasoningEffort", reasoningEffort)
        call("session/selectModel", JSONObject().put("request", args))
    }

    suspend fun currentModel(sessionId: String): ModelChoice? {
        val value = call("session/projections", JSONObject().put("request", JSONObject().put("sessionId", sessionId)))
        val values = value.optJSONObject("values") ?: return null
        val selection = values.optJSONObject("modelSelection") ?: return null
        val next = selection.optJSONObject("next") ?: selection.optJSONObject("lastUsed") ?: return null
        return parseModel(next, null)
    }

    private fun parseModel(model: JSONObject?, providerName: String?): ModelChoice? {
        model ?: return null
        val id = model.optString("id").ifEmpty { model.optString("model") }
        if (id.isEmpty()) return null
        val provider = model.optString("provider").ifEmpty { providerName ?: "" }
        return ModelChoice(provider, id, model.optString("name").ifEmpty { id })
    }

    data class TerminalInfo(val id: String, val title: String, val state: String)

    suspend fun listTerminals(sessionId: String): List<TerminalInfo> {
        val value = call("terminal/list", JSONObject().put("sessionId", sessionId))
        val items = value.optJSONArray("items") ?: JSONArray()
        return buildList {
            for (i in 0 until items.length()) {
                val item = items.optJSONObject(i) ?: continue
                val id = item.optString("id")
                if (id.isNotEmpty()) add(TerminalInfo(id, item.optString("title", "终端"), item.optString("state", "running")))
            }
        }
    }

    suspend fun createTerminal(sessionId: String, cols: Int = 80, rows: Int = 24): TerminalInfo {
        val request = JSONObject()
            .put("id", UUID.randomUUID().toString())
            .put("cols", cols)
            .put("rows", rows)
        val value = call("terminal/create", JSONObject().put("agentId", sessionId).put("request", request))
        return TerminalInfo(
            value.optString("id", request.optString("id")),
            value.optString("title", "终端"),
            value.optString("state", "running"),
        )
    }

    suspend fun closeTerminal(sessionId: String, terminalId: String) {
        call("terminal/close", JSONObject().put("agentId", sessionId).put("id", terminalId))
    }

    fun attachTerminal(
        sessionId: String,
        terminalId: String,
        onFrame: (JSONObject) -> Unit,
        onFailure: (Throwable) -> Unit = {},
    ): StreamHandle = openStreamHandle(
        "terminal/attach",
        JSONObject().put("agentId", sessionId).put("id", terminalId),
        onFrame,
        onFailure,
    )

    data class FileEntry(val name: String, val type: String, val size: Long?)

    suspend fun listFiles(sessionId: String, path: String): List<FileEntry> {
        val args = JSONObject()
            .put("workspaceFileScopeId", sessionId)
            .put("path", path.ifBlank { "." })
        val value = call("workspaceFiles/list", args)
        val entries = value.optJSONArray("entries") ?: JSONArray()
        return buildList {
            for (i in 0 until entries.length()) {
                val item = entries.optJSONObject(i) ?: continue
                val name = item.optString("name")
                if (name.isEmpty()) continue
                add(FileEntry(name, item.optString("type", "file"), if (item.has("size")) item.optLong("size") else null))
            }
        }
    }

    suspend fun readFileText(sessionId: String, path: String): String {
        val args = JSONObject()
            .put("workspaceFileScopeId", sessionId)
            .put("path", path.ifBlank { "." })
            .put("range", JSONObject().put("offset", 1).put("limit", 500))
        return call("workspaceFiles/read", args).optString("text")
    }

    fun close() {
        http.dispatcher.executorService.shutdown()
        http.connectionPool.evictAll()
    }

    private companion object {
        val JSON = "application/json; charset=utf-8".toMediaType()

        fun parseBase(baseUrl: String): HttpUrl {
            val normalized = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
            return normalized.toHttpUrlOrNull() ?: throw IllegalArgumentException("无效地址：$baseUrl")
        }
    }
}
