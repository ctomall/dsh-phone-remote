package com.ila3.dshnative.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import com.ila3.dshnative.BuildConfig
import android.provider.OpenableColumns
import java.io.File
import java.util.Base64
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ila3.dshnative.protocol.ChatItem
import com.ila3.dshnative.protocol.ChatParser
import com.ila3.dshnative.protocol.DshClient
import com.ila3.dshnative.protocol.DshException
import com.ila3.dshnative.protocol.NativeStore
import com.ila3.dshnative.protocol.ServerProfile
import com.ila3.dshnative.protocol.SessionSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.WebSocket
import org.json.JSONObject
import java.util.UUID

sealed interface Screen {
    data object Servers : Screen
    data object Sessions : Screen
    data object Chat : Screen
    data object Files : Screen
    data object Terminal : Screen
    data object Settings : Screen
}

class NativeViewModel(app: Application) : AndroidViewModel(app) {
    private val store = NativeStore(app)

    var profiles by mutableStateOf(store.profiles())
        private set
    var currentId by mutableStateOf(store.currentId())
        private set
    var screen by mutableStateOf<Screen>(Screen.Servers)
        private set
    var sessions by mutableStateOf<List<SessionSummary>>(emptyList())
        private set
    var messages = mutableStateListOf<ChatItem>()
    var pendingAttachments = mutableStateListOf<PendingAttachment>()
    val queue = mutableStateListOf<QueuedMessage>()
    var activeSessionId by mutableStateOf("")
        private set
    var activeSessionTitle by mutableStateOf("")
        private set
    var activeCwd by mutableStateOf<String?>(null)
        private set
    var showChatActions by mutableStateOf(false)
        private set
    var showRenameDialog by mutableStateOf(false)
        private set
    var updateInfo by mutableStateOf<com.ila3.dshnative.protocol.AppUpdate?>(null)
        private set
    var updateChecking by mutableStateOf(false)
        private set
    var updateDownloading by mutableStateOf(false)
        private set
    var updateProgress by mutableStateOf(0)
        private set
    private var pendingApk: File? = null
    private var permissionRequested = false
    private val updateClient = com.ila3.dshnative.protocol.UpdateClient()
    var input by mutableStateOf("")
    var busy by mutableStateOf(false)
        private set
    var error by mutableStateOf("")
        private set
    var connection by mutableStateOf("未连接")
        private set
    var files by mutableStateOf<List<DshClient.FileEntry>>(emptyList())
        private set
    var filePath by mutableStateOf("")
        private set
    var filePreview by mutableStateOf<Pair<String, String>?>(null)
        private set
    var terminalTitle by mutableStateOf("终端")
        private set
    var modelGroups by mutableStateOf<List<DshClient.ModelGroup>>(emptyList())
        private set
    var currentModel by mutableStateOf<DshClient.ModelChoice?>(null)
        private set
    var showModelPicker by mutableStateOf(false)
        private set
    val terminalChunks = mutableStateListOf<String>()
    private var terminalHandle: DshClient.StreamHandle? = null
    private var terminalSequence = 0L

    data class PendingAttachment(val id: String, val name: String, val attachment: DshClient.Attachment)

    data class QueuedMessage(val id: String, val text: String)

    private var client: DshClient? = null
    private var socket: WebSocket? = null
    private var followRetryJob: Job? = null
    private var following = false
    private var followAttempt = 0
    private var followGeneration = 0
    private var lastSeq = 0L

    val currentProfile: ServerProfile?
        get() = profiles.firstOrNull { it.id == currentId }

    init {
        val current = currentProfile
        if (current != null) {
            openClient(current)
        }
    }

    fun dismissError() {
        error = ""
    }

    fun showServers() {
        screen = Screen.Servers
    }

    fun showSessions() {
        if (currentProfile != null) screen = Screen.Sessions
    }

    fun pair(url: String, code: String) {
        val normalized = normalizeBase(url)
        val cleanCode = code.uppercase().replace(Regex("[^A-Z0-9]"), "")
        if (normalized.isEmpty()) {
            error = "请填写 DSH 地址"
            return
        }
        if (cleanCode.length != 8) {
            error = "配对码应为 8 位"
            return
        }
        viewModelScope.launch {
            busy = true
            error = ""
            try {
                val existing = profiles.firstOrNull { it.url == normalized }
                val id = existing?.id ?: UUID.randomUUID().toString()
                val candidate = DshClient(getApplication(), normalized, id)
                candidate.pair(cleanCode)
                if (existing == null) {
                    val host = android.net.Uri.parse(normalized).host ?: "DSH"
                    var name = host.removePrefix("www.")
                    var suffix = 2
                    val base = name
                    while (profiles.any { it.name == name }) name = "$base ${suffix++}"
                    val profile = ServerProfile(id, name, normalized)
                    profiles = profiles + profile
                    store.saveProfiles(profiles)
                }
                currentId = id
                store.setCurrent(id)
                client?.close()
                client = candidate
                screen = Screen.Sessions
                loadSessions()
            } catch (e: Exception) {
                error = e.message ?: "配对失败"
            } finally {
                busy = false
            }
        }
    }

    fun switchTo(profile: ServerProfile) {
        currentId = profile.id
        store.setCurrent(profile.id)
        screen = Screen.Sessions
        openClient(profile)
    }

    fun deleteProfile(profile: ServerProfile) {
        store.deleteProfile(profile.id)
        profiles = store.profiles()
        if (currentId == profile.id) {
            currentId = store.currentId()
            val next = currentProfile
            if (next == null) {
                client?.close()
                client = null
                screen = Screen.Servers
            } else {
                openClient(next)
            }
        }
    }

    private fun openClient(profile: ServerProfile) {
        client?.close()
        stopFollowing()
        client = DshClient(getApplication(), profile.url, profile.id)
        sessions = emptyList()
        screen = Screen.Sessions
        loadSessions()
    }

    fun refreshSessions() {
        loadSessions()
    }

    private fun loadSessions() {
        val active = client ?: run {
            screen = Screen.Servers
            return
        }
        viewModelScope.launch {
            busy = true
            error = ""
            connection = "连接中"
            try {
                val list = withContext(Dispatchers.IO) { active.sessions() }
                sessions = list.sortedByDescending { it.updatedAt }
                connection = "已连接"
            } catch (e: DshException) {
                error = e.message ?: "加载会话失败"
                connection = "连接失败"
                if (e.status == 401 || e.status == 403) screen = Screen.Servers
            } catch (e: Exception) {
                error = e.message ?: "加载会话失败"
                connection = "连接失败"
            } finally {
                busy = false
            }
        }
    }

    fun openSession(session: SessionSummary) {
        val active = client ?: return
        stopFollowing()
        activeSessionId = session.id
        activeSessionTitle = session.title?.takeIf { it.isNotBlank() }
            ?: if (session.blank) "新会话" else "会话 ${session.id.take(8)}"
        activeCwd = session.cwd
        messages.clear()
        queue.clear()
        lastSeq = 0L
        screen = Screen.Chat
        connection = "连接中"
        error = ""
        following = true
        followAttempt = 0
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val catalog = active.modelCatalog()
                    val selected = active.currentModel(session.id) ?: catalog.default
                    catalog.groups to selected
                }
            }.onSuccess { (groups, selected) ->
                modelGroups = groups
                currentModel = selected
            }
        }
        startFollow()
    }

    /** Open one follow generation; failures schedule an exponential retry. */
    private fun startFollow() {
        val active = client ?: return
        if (!following || activeSessionId.isEmpty()) return
        val address = JSONObject().put("kind", "session").put("sessionId", activeSessionId)
        val request = JSONObject()
            .put("address", address)
            .put("assistantStream", true)
            .put("maxMessages", 500)
            .put("turnWindow", JSONObject().put("minMessages", 50).put("minTurns", 2))
        socket?.cancel()
        val generation = ++followGeneration
        socket = active.openStream(
            "session/follow",
            JSONObject().put("request", request),
            onValue = { value ->
                if (generation == followGeneration) handleFollowValue(value)
            },
            onFailure = { failure ->
                if (generation == followGeneration) scheduleFollowRetry(failure)
            },
        )
    }

    private fun scheduleFollowRetry(failure: Throwable) {
        if (!following || activeSessionId.isEmpty()) return
        if (failure is DshException && (failure.status == 401 || failure.status == 403)) {
            following = false
            connection = "登录已失效"
            error = "登录已失效，请重新配对"
            screen = Screen.Servers
            return
        }
        if (followRetryJob?.isActive == true) return
        connection = "连接中断，重连中…"
        val waitMs = (1000L shl followAttempt.coerceAtMost(5)).coerceAtMost(30_000L)
        followAttempt += 1
        val targetSession = activeSessionId
        followRetryJob = viewModelScope.launch {
            delay(waitMs)
            if (following && activeSessionId == targetSession) {
                connection = "重连中…"
                startFollow()
            }
        }
    }

    private fun stopFollowing() {
        following = false
        followGeneration += 1
        followRetryJob?.cancel()
        followRetryJob = null
        socket?.cancel()
        socket = null
    }

    fun checkUpdate() {
        val profile = currentProfile
        if (profile == null) {
            error = "请先绑定 DSH"
            return
        }
        viewModelScope.launch {
            updateChecking = true
            try {
                val found = withContext(Dispatchers.IO) {
                    runCatching { updateClient.check(com.ila3.dshnative.protocol.UpdateClient.GIT_MANIFEST) }
                        .getOrElse {
                            updateClient.check(if (profile.url.endsWith("/")) "${profile.url}updates/android.json" else "${profile.url}/updates/android.json")
                        }
                }
                if (found != null && found.versionCode > BuildConfig.VERSION_CODE) {
                    updateInfo = found
                } else {
                    error = "已是最新版本 ${BuildConfig.VERSION_NAME}"
                }
            } catch (e: Exception) {
                error = e.message ?: "检查更新失败"
            } finally {
                updateChecking = false
            }
        }
    }

    fun dismissUpdate() {
        updateInfo = null
    }

    fun downloadUpdate(onReady: (File) -> Unit) {
        val update = updateInfo ?: return
        if (updateDownloading) return
        val context = getApplication<Application>()
        viewModelScope.launch {
            updateDownloading = true
            try {
                val target = File(context.cacheDir, "updates/DSHNative-${update.versionCode}.apk")
                updateProgress = 0
                val file = withContext(Dispatchers.IO) {
                    updateClient.download(update, target) { progress -> updateProgress = progress }
                }
                pendingApk = file
                updateInfo = null
                onReady(file)
            } catch (e: Exception) {
                error = e.message ?: "下载安装包失败"
            } finally {
                updateDownloading = false
            }
        }
    }

    fun installApk(context: Context, file: File) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
            pendingApk = file
            permissionRequested = true
            runCatching {
                context.startActivity(
                    Intent(
                        android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:${context.packageName}"),
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }.onFailure { error = "请允许安装未知来源应用" }
            return
        }
        runCatching {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            context.startActivity(
                Intent(Intent.ACTION_VIEW)
                    .setDataAndType(uri, "application/vnd.android.package-archive")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            pendingApk = null
            permissionRequested = false
        }.onFailure { error = it.message ?: "无法启动安装器" }
    }

    /** Called from Activity.onResume after the unknown-sources settings page closes. */
    fun resumePendingInstall(context: Context) {
        val file = pendingApk ?: return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()) {
            installApk(context, file)
        } else if (permissionRequested) {
            permissionRequested = false
            pendingApk = null
            error = "未授予安装未知应用权限"
        }
    }

    fun openSettings() {
        screen = Screen.Settings
    }

    fun closeSettings() {
        screen = Screen.Sessions
    }

    fun showChatMenu() {
        showChatActions = true
    }

    fun dismissChatMenu() {
        showChatActions = false
    }

    fun showRename() {
        showChatActions = false
        showRenameDialog = true
    }

    fun dismissRename() {
        showRenameDialog = false
    }

    fun renameActive(title: String) {
        val clean = title.trim()
        val active = client ?: return
        if (clean.isEmpty() || activeSessionId.isEmpty()) {
            error = "标题不能为空"
            return
        }
        showRenameDialog = false
        viewModelScope.launch {
            try {
                val committed = withContext(Dispatchers.IO) { active.renameSession(activeSessionId, clean) }
                activeSessionTitle = committed
                sessions = sessions.map { if (it.id == activeSessionId) it.copy(title = committed) else it }
            } catch (e: Exception) {
                error = e.message ?: "重命名失败"
            }
        }
    }

    fun stopGeneration() {
        showChatActions = false
        val active = client ?: return
        if (activeSessionId.isEmpty()) return
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { active.cancelSession(activeSessionId) }
                connection = "已停止"
            } catch (e: Exception) {
                error = e.message ?: "停止失败"
            }
        }
    }

    fun reloadActiveSession() {
        showChatActions = false
        if (activeSessionId.isEmpty()) return
        openSession(
            SessionSummary(
                id = activeSessionId,
                updatedAt = System.currentTimeMillis(),
                running = false,
                blank = false,
                cwd = activeCwd,
                title = activeSessionTitle,
            ),
        )
    }

    fun openModelPicker() {
        if (modelGroups.isEmpty()) {
            error = "模型列表尚未加载"
            return
        }
        showModelPicker = true
    }

    fun dismissModelPicker() {
        showModelPicker = false
    }

    fun chooseModel(choice: DshClient.ModelChoice) {
        val active = client ?: return
        if (activeSessionId.isEmpty()) return
        showModelPicker = false
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { active.selectModel(activeSessionId, choice) }
                currentModel = choice
            } catch (e: Exception) {
                error = e.message ?: "切换模型失败"
            }
        }
    }

    fun openTerminal() {
        if (activeSessionId.isEmpty()) return
        val active = client ?: return
        screen = Screen.Terminal
        terminalChunks.clear()
        terminalTitle = "终端"
        viewModelScope.launch {
            busy = true
            error = ""
            try {
                val terminals = withContext(Dispatchers.IO) { active.listTerminals(activeSessionId) }
                val terminal = terminals.firstOrNull() ?: withContext(Dispatchers.IO) {
                    active.createTerminal(activeSessionId)
                }
                terminalTitle = terminal.title
                terminalSequence = 0L
                terminalHandle = active.attachTerminal(
                    activeSessionId,
                    terminal.id,
                    onFrame = { frame ->
                        when (frame.optString("type")) {
                            "snapshot" -> {
                                terminalChunks.clear()
                                terminalChunks.add(frame.optString("screen"))
                            }
                            "output" -> terminalChunks.add(frame.optString("data"))
                        }
                    },
                    onFailure = { failure -> error = failure.message ?: "终端连接失败" },
                )
            } catch (e: Exception) {
                error = e.message ?: "打开终端失败"
            } finally {
                busy = false
            }
        }
    }

    fun sendTerminalInput(data: String) {
        val handle = terminalHandle ?: return
        terminalSequence += 1
        handle.sendItem(
            JSONObject()
                .put("type", "write")
                .put("sequence", terminalSequence)
                .put("data", data),
        )
    }

    fun resizeTerminal(cols: Int, rows: Int) {
        val handle = terminalHandle ?: return
        terminalSequence += 1
        handle.sendItem(
            JSONObject()
                .put("type", "resize")
                .put("sequence", terminalSequence)
                .put("cols", cols)
                .put("rows", rows),
        )
    }

    fun closeTerminal() {
        terminalHandle?.cancel()
        terminalHandle = null
        screen = Screen.Chat
    }

    fun removeQueued(item: QueuedMessage) {
        val active = client ?: return
        queue.removeAll { it.id == item.id }
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { active.removeQueued(activeSessionId, item.id) }
            } catch (e: Exception) {
                error = e.message ?: "移除排队消息失败"
            }
        }
    }

    fun steerQueued(item: QueuedMessage) {
        val active = client ?: return
        queue.removeAll { it.id == item.id }
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { active.steerQueued(activeSessionId, item.id) }
            } catch (e: Exception) {
                error = e.message ?: "转向失败"
            }
        }
    }

    private fun applyTitle(event: JSONObject) {
        if (event.optString("type") != "session/title") return
        val title = event.optJSONObject("data")?.optString("title").orEmpty()
        if (title.isNotBlank()) activeSessionTitle = title
    }

    private fun applyInbox(event: JSONObject) {
        if (event.optString("type") != "agent/inbox/spliced") return
        val data = event.optJSONObject("data") ?: return
        if (data.optString("target") != "next-turn") return
        val start = data.optInt("start", queue.size).coerceIn(0, queue.size)
        val removed = data.optInt("removedCount", 0).coerceAtMost(queue.size - start)
        repeat(removed) { if (start < queue.size) queue.removeAt(start) }
        val inserted = data.optJSONArray("inserted") ?: return
        for (i in 0 until inserted.length()) {
            val message = inserted.optJSONObject(i) ?: continue
            val id = message.optString("id")
            if (id.isEmpty()) continue
            var text = ChatParser.userMessageText(message)
            if (text.isBlank()) {
                val content = message.optJSONArray("content")
                text = if (content != null && content.length() > 0) "[附件]" else "…"
            }
            queue.add((start + i).coerceAtMost(queue.size), QueuedMessage(id, text))
        }
    }

    fun openFiles() {
        if (activeSessionId.isEmpty()) return
        filePath = ""
        files = emptyList()
        screen = Screen.Files
        loadFiles("")
    }

    fun loadFiles(path: String) {
        val active = client ?: return
        if (activeSessionId.isEmpty()) return
        viewModelScope.launch {
            busy = true
            error = ""
            try {
                files = withContext(Dispatchers.IO) { active.listFiles(activeSessionId, path) }
                filePath = path
            } catch (e: Exception) {
                error = e.message ?: "读取目录失败"
            } finally {
                busy = false
            }
        }
    }

    fun openFile(entry: DshClient.FileEntry) {
        val active = client ?: return
        if (entry.type == "directory") {
            loadFiles(if (filePath.isEmpty()) entry.name else "$filePath/${entry.name}")
            return
        }
        viewModelScope.launch {
            busy = true
            try {
                val text = withContext(Dispatchers.IO) { active.readFileText(activeSessionId, if (filePath.isEmpty()) entry.name else "$filePath/${entry.name}") }
                filePreview = entry.name to text
            } catch (e: Exception) {
                error = e.message ?: "读取文件失败"
            } finally {
                busy = false
            }
        }
    }

    fun dismissFilePreview() {
        filePreview = null
    }

    fun filesUp() {
        if (filePath.isEmpty()) {
            screen = Screen.Chat
        } else {
            loadFiles(filePath.substringBeforeLast('/', ""))
        }
    }

    fun closeSession() {
        stopFollowing()
        activeSessionId = ""
        activeSessionTitle = ""
        activeCwd = null
        showChatActions = false
        showRenameDialog = false
        messages.clear()
        pendingAttachments.clear()
        queue.clear()
        modelGroups = emptyList()
        currentModel = null
        showModelPicker = false
        screen = Screen.Sessions
        loadSessions()
    }

    private fun handleFollowValue(value: JSONObject) {
        when (value.optString("type")) {
            "snapshot" -> {
                followAttempt = 0
                connection = "已连接"
                val records = value.optJSONArray("records")
                messages.clear()
                queue.clear()
                ChatParser.parseRecords(records).forEach { appendItem(it) }
                for (i in 0 until (records?.length() ?: 0)) {
                    records?.optJSONObject(i)?.optJSONObject("event")?.let {
                        applyInbox(it)
                        applyTitle(it)
                    }
                }
                lastSeq = maxSeq(records, lastSeq)
            }
            "event" -> {
                connection = "已连接"
                val event = value.optJSONObject("event") ?: return
                val seq = event.optLong("seq", 0L)
                if (seq in 1..lastSeq) return
                if (seq > lastSeq) lastSeq = seq
                applyInbox(event)
                applyTitle(event)
                ChatParser.parseEvent(event)?.let { appendItem(it) }
            }
            "assistant-stream" -> {
                val frame = value.optJSONObject("frame") ?: return
                when (frame.optString("type")) {
                    "chunk" -> {
                        val chunk = frame.optJSONObject("chunk") ?: return
                        if (chunk.optString("type") == "text-delta") {
                            appendStreamText(chunk.optString("text"))
                        }
                    }
                    "end" -> finishStreaming()
                }
            }
        }
    }

    private fun appendItem(item: ChatItem) {
        when (item) {
            is ChatItem.ToolResult -> {
                val index = messages.indexOfFirst { it is ChatItem.Tool && it.callId == item.callId }
                if (index >= 0) {
                    val tool = messages[index] as ChatItem.Tool
                    messages[index] = tool.copy(result = item.text, isError = item.isError)
                } else {
                    messages.add(ChatItem.Notice(item.key, item.text))
                }
            }
            is ChatItem.Assistant -> {
                // Replace the live streaming bubble once the durable message arrives.
                val index = messages.indexOfLast { it is ChatItem.Assistant && it.streaming }
                if (index >= 0) messages.removeAt(index)
                messages.add(item)
            }
            is ChatItem.User -> {
                // The composer shows an optimistic row; replace it with the durable one.
                val local = messages.indexOfLast { it is ChatItem.User && it.key.startsWith("local-") }
                if (local >= 0) messages.removeAt(local)
                messages.add(item)
            }
            else -> messages.add(item)
        }
    }

    private fun appendStreamText(delta: String) {
        if (delta.isEmpty()) return
        val index = messages.indexOfLast { it is ChatItem.Assistant && it.streaming }
        if (index >= 0) {
            val item = messages[index] as ChatItem.Assistant
            messages[index] = item.copy(text = item.text + delta)
        } else {
            messages.add(ChatItem.Assistant("stream", delta, streaming = true))
        }
    }

    private fun finishStreaming() {
        val index = messages.indexOfLast { it is ChatItem.Assistant && it.streaming }
        if (index >= 0) {
            val item = messages[index] as ChatItem.Assistant
            messages[index] = item.copy(streaming = false)
        }
    }

    fun attach(uri: Uri) {
        val active = client ?: return
        if (activeSessionId.isEmpty()) return
        viewModelScope.launch {
            busy = true
            error = ""
            try {
                val resolver = getApplication<Application>().contentResolver
                var name = "附件"
                resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (index >= 0) name = cursor.getString(index) ?: name
                    }
                }
                val bytes = withContext(Dispatchers.IO) {
                    resolver.openInputStream(uri)?.use { it.readBytes() }
                } ?: throw DshException("无法读取文件")
                if (bytes.size > 20 * 1024 * 1024) throw DshException("附件不能超过 20 MB")
                val mime = resolver.getType(uri) ?: "application/octet-stream"
                val attachment = if (mime in setOf("image/png", "image/jpeg", "image/webp", "image/gif")) {
                    DshClient.Attachment.Image(
                        mediaType = mime,
                        data = Base64.getEncoder().encodeToString(bytes),
                        name = name,
                    )
                } else {
                    val receipt = withContext(Dispatchers.IO) { active.uploadFile(activeSessionId, bytes, name) }
                    DshClient.Attachment.File(receipt.receiptId, name)
                }
                pendingAttachments.add(PendingAttachment(UUID.randomUUID().toString(), name, attachment))
            } catch (e: Exception) {
                error = e.message ?: "添加附件失败"
            } finally {
                busy = false
            }
        }
    }

    fun removeAttachment(id: String) {
        pendingAttachments.removeAll { it.id == id }
    }

    fun send() {
        val text = input.trim()
        val active = client ?: return
        if ((text.isEmpty() && pendingAttachments.isEmpty()) || activeSessionId.isEmpty()) return
        val attachments = pendingAttachments.map { it.attachment }
        val names = pendingAttachments.joinToString("、") { it.name }
        input = ""
        pendingAttachments.clear()
        messages.add(
            ChatItem.User(
                "local-${System.currentTimeMillis()}",
                if (names.isEmpty()) text else "$text\n📎 $names".trim(),
            ),
        )
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { active.prompt(activeSessionId, text, attachments) }
            } catch (e: Exception) {
                error = e.message ?: "发送失败"
            }
        }
    }

    fun newSession() {
        val active = client ?: return
        viewModelScope.launch {
            busy = true
            try {
                val id = withContext(Dispatchers.IO) { active.createSession(null) }
                loadSessions()
                openSession(SessionSummary(id, System.currentTimeMillis(), false, true, null))
            } catch (e: Exception) {
                error = e.message ?: "创建会话失败"
            } finally {
                busy = false
            }
        }
    }

    fun clearAll() {
        socket?.cancel()
        socket = null
        client?.close()
        client = null
        store.clear()
        profiles = emptyList()
        currentId = ""
        sessions = emptyList()
        messages.clear()
        screen = Screen.Servers
        connection = "未连接"
    }

    override fun onCleared() {
        stopFollowing()
        client?.close()
        super.onCleared()
    }

    private fun normalizeBase(input: String): String {
        var value = input.trim()
        if (value.isEmpty()) return ""
        if (!value.startsWith("http://") && !value.startsWith("https://")) value = "https://$value"
        if (!value.endsWith("/")) value += "/"
        return value
    }

    private fun maxSeq(records: org.json.JSONArray?, fallback: Long): Long {
        records ?: return fallback
        var max = fallback
        for (i in 0 until records.length()) {
            val event = records.optJSONObject(i)?.optJSONObject("event") ?: continue
            max = maxOf(max, event.optLong("seq", 0L))
        }
        return max
    }
}
