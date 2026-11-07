package com.ila3.dshnative.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import com.ila3.dshnative.BuildConfig
import android.provider.OpenableColumns
import java.io.File
import java.util.Base64
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ila3.dshnative.protocol.ChatItem
import com.ila3.dshnative.protocol.ChatParser
import com.ila3.dshnative.protocol.DshClient
import com.ila3.dshnative.protocol.DshException
import com.ila3.dshnative.protocol.NativeNotifications
import com.ila3.dshnative.protocol.NativeStore
import com.ila3.dshnative.protocol.ServerProfile
import com.ila3.dshnative.protocol.SessionSummary
import com.ila3.dshnative.protocol.SshHost
import com.ila3.dshnative.protocol.TranslationStore
import com.ila3.dshnative.protocol.Translator
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
    data object Tasks : Screen
    data object NavSettings : Screen
    data object TaskDetail : Screen
    data object Toolbox : Screen
}

class NativeViewModel(app: Application) : AndroidViewModel(app) {
    private val store = NativeStore(app)
    private val notifications = NativeNotifications(app)
    var notifyEnabled by mutableStateOf(store.notifyEnabled())
        private set
    data class SessionQuestion(val sessionId: String, val sessionTitle: String, val pending: DshClient.PendingQuestion)

    var inbox by mutableStateOf<List<SessionQuestion>>(emptyList())
        private set
    var navSelection by mutableStateOf("now")
        private set
    var navOrder by mutableStateOf(store.navOrder())
        private set
    var navHidden by mutableStateOf(store.navHidden())
        private set
    var keepAliveEnabled by mutableStateOf(store.keepAlive())
        private set
    private var foreground = true
    private var lastTurnNotified = -1L
    private var lastQuestionNotified = ""
    private var pendingOpenSessionId = ""

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
    var showAttachSheet by mutableStateOf(false)
        private set
    val queue = mutableStateListOf<QueuedMessage>()
    var pendingQuestions by mutableStateOf<List<DshClient.PendingQuestion>>(emptyList())
        private set
    var hasMoreHistory by mutableStateOf(false)
        private set
    var loadingHistory by mutableStateOf(false)
        private set
    var historyVersion by mutableStateOf(0)
        private set
    var historyAdded by mutableStateOf(0)
        private set
    private var historyCursor = 0L
    private var oldestSeq = Long.MAX_VALUE
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
    data class FilePreviewData(
        val name: String,
        val text: String?,
        val bitmap: Bitmap?,
        val bytes: Long = 0L,
        val width: Int = 0,
        val height: Int = 0,
        val path: String? = null,
        val hasMore: Boolean = false,
        val lineCount: Int = 0,
    )
    var filePreview by mutableStateOf<FilePreviewData?>(null)
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

    enum class AttachmentStatus { UPLOADING, READY, FAILED }

    data class PendingAttachment(
        val id: String,
        val name: String,
        val mime: String,
        val size: Long,
        val uri: Uri,
        val preview: Bitmap? = null,
        val status: AttachmentStatus = AttachmentStatus.UPLOADING,
        val progress: Int = 0,
        val attachment: DshClient.Attachment? = null,
        val error: String? = null,
    )

    data class QueuedMessage(val id: String, val text: String)

    data class Translation(val loading: Boolean = false, val text: String? = null, val error: String? = null)

    private val translationStore = TranslationStore(app)

    /** Machine translations keyed by "<session>:<message>:reason|body", persisted on disk. */
    val translations = mutableStateMapOf<String, Translation>()

    var thinkExpanded by mutableStateOf(store.thinkExpanded())
        private set
    var showTime by mutableStateOf(store.showTime())
        private set
    var toolExpanded by mutableStateOf(store.toolExpanded())
        private set
    var autoScroll by mutableStateOf(store.autoScroll())
        private set
    data class DeviceState(
        val id: String?,
        val name: String,
        val ok: Boolean,
        val sessions: Int = 0,
        val running: Int = 0,
        val error: String? = null,
    )

    var sshHosts by mutableStateOf(store.sshHosts())
    var deviceStates by mutableStateOf<List<DeviceState>>(emptyList())
    var feedback by mutableStateOf<Map<String, String>>(emptyMap())
    var toolboxSection by mutableStateOf("search")
    var searchQuery by mutableStateOf("")
    var searchResults by mutableStateOf<List<DshClient.SearchHit>>(emptyList())
    var searchBusy by mutableStateOf(false)
    var searchFallback by mutableStateOf(false)
    var skills by mutableStateOf<List<DshClient.SkillItem>>(emptyList())
    var skillsBusy by mutableStateOf(false)
    var fileKindFilter by mutableStateOf("all")
    var browserUrl by mutableStateOf("https://example.com/")
    data class GalleryItem(val path: String, val name: String, val size: Long?)
    var gallery by mutableStateOf<List<GalleryItem>>(emptyList())
    var galleryBusy by mutableStateOf(false)
    var thumbnails by mutableStateOf<Map<String, Bitmap>>(emptyMap())
    var galleryPreviewIndex by mutableIntStateOf(-1)
    var galleryPreviewBase by mutableStateOf<List<GalleryItem>>(emptyList())
    var galleryFolder by mutableStateOf("all")
    var optimizerVisible by mutableStateOf(false)
    var optimizerMode by mutableStateOf("general")
    var optimizerText by mutableStateOf("")
    var optimizerBusy by mutableStateOf(false)
    var optimizerError by mutableStateOf("")
    var optimizerUndo by mutableStateOf<String?>(null)
    var optimizerEnabled by mutableStateOf(true)
    var optimizerMaxChars by mutableStateOf(8000)
    var optimizerShowModes by mutableStateOf(true)
    var optimizerSynced by mutableStateOf(false)
    var serverSnapshot by mutableStateOf<JSONObject?>(null)
    var serverBusy by mutableStateOf(false)
    var serverError by mutableStateOf("")
    var sshImportMessage by mutableStateOf("")
    var showCommandSheet by mutableStateOf(false)
    var slashCommands by mutableStateOf<List<DshClient.SlashCommand>>(emptyList())
    var commandsBusy by mutableStateOf(false)
    var commandsError by mutableStateOf("")
    var agencyRaw by mutableStateOf<JSONObject?>(null)
    var agencyBusy by mutableStateOf(false)
    var agencyError by mutableStateOf("")
    var selectedAgencyTeam by mutableStateOf<JSONObject?>(null)
    private var optimizerModeTouched = false
    val optimizerModes = listOf(
        "general" to "通用", "structure" to "结构化", "concise" to "精简", "detail" to "补充细节",
        "technical" to "技术", "frontend" to "前端", "backend" to "后端", "review" to "审查", "dsh" to "DSH",
    )
    var pairPrefillUrl by mutableStateOf("")
    var multiDevice by mutableStateOf(store.multiDevice())
        private set
    var showDeviceRunning by mutableStateOf(store.showDeviceRunning())
        private set
    var usage by mutableStateOf<DshClient.SessionMeta?>(null)
    var turnDurations by mutableStateOf<Map<Int, Long>>(emptyMap())
    private var liveTurn = 0
    private var liveTurnStart = 0L
    var permissionOptions by mutableStateOf<List<DshClient.PermissionOption>>(emptyList())
    var presetOptions by mutableStateOf<List<DshClient.PresetOption>>(emptyList())

    init {
        runCatching { translationStore.load() }.getOrElse { emptyMap() }
            .forEach { (key, text) -> translations[key] = Translation(text = text) }
    }

    fun updateShowTime(value: Boolean) {
        showTime = value
        store.setShowTime(value)
    }

    fun updateToolExpanded(value: Boolean) {
        toolExpanded = value
        store.setToolExpanded(value)
    }

    fun closeToolbox() {
        screen = Screen.Sessions
    }

    fun loadGallery() {
        val active = client ?: return
        val sid = activeSessionId.ifBlank { sessions.firstOrNull()?.id.orEmpty() }
        if (sid.isEmpty()) return
        viewModelScope.launch {
            galleryBusy = true
            runCatching {
                withContext(Dispatchers.IO) {
                    val images = mutableListOf<GalleryItem>()
                    val imageExt = setOf("png", "jpg", "jpeg", "webp", "gif", "bmp")
                    suspend fun walk(path: String, depth: Int) {
                        val entries = runCatching { active.listFiles(sid, path) }.getOrDefault(emptyList())
                        entries.forEach { entry ->
                            val child = if (path.isEmpty()) entry.name else "$path/${entry.name}"
                            if (entry.type == "directory") {
                                if (depth > 0 && !entry.name.startsWith(".") &&
                                    entry.name !in setOf("node_modules", "build", "dist", ".git")
                                ) walk(child, depth - 1)
                            } else if (entry.name.substringAfterLast('.', "").lowercase() in imageExt) {
                                images.add(GalleryItem(child, entry.name, entry.size))
                            }
                        }
                    }
                    walk("", 1)
                    images.take(300)
                }
            }.onSuccess { gallery = it }
                .onFailure { error = it.message ?: "加载图片失败" }
            galleryBusy = false
        }
    }

    fun refreshGallery() {
        thumbnails = emptyMap()
        gallery = emptyList()
        loadGallery()
    }

    fun loadThumbnail(item: GalleryItem) {
        if (thumbnails.containsKey(item.path)) return
        val active = client ?: return
        val sid = activeSessionId.ifBlank { sessions.firstOrNull()?.id.orEmpty() }
        if (sid.isEmpty()) return
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val bytes = active.readFileBytes(sid, item.path, 400_000)
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                    var sample = 1
                    while (bounds.outWidth / sample > 480 || bounds.outHeight / sample > 480) sample *= 2
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
                }
            }.onSuccess { bitmap -> if (bitmap != null) thumbnails = thumbnails + (item.path to bitmap) }
        }
    }

    fun openBrowser(url: String) {
        browserUrl = url
        toolboxSection = "browser"
        screen = Screen.Toolbox
    }

    fun openToolbox(section: String = "search") {
        toolboxSection = section
        screen = Screen.Toolbox
        if (section == "skills") loadSkills()
    }

    fun openAgencyTeam(team: JSONObject) {
        selectedAgencyTeam = team
    }

    fun closeAgencyTeam() {
        selectedAgencyTeam = null
    }

    fun useAgencyTeam(team: JSONObject) {
        val name = team.optString("name").ifBlank { "专家团队" }
        val members = team.optJSONArray("members")
        val slugs = buildList {
            if (members != null) {
                for (i in 0 until members.length()) {
                    val slug = members.optJSONObject(i)?.optString("expertSlug").orEmpty()
                    if (slug.isNotBlank()) add(slug)
                }
            }
        }
        if (activeSessionId.isBlank()) {
            error = "先打开一个会话，再使用专家团队"
            return
        }
        input = "使用专家团队「$name」（${slugs.joinToString("、")}）完成下面的任务：\n"
        selectedAgencyTeam = null
        closeToolbox()
        screen = Screen.Chat
    }

    fun loadAgency() {
        val active = client ?: return
        viewModelScope.launch {
            agencyBusy = true
            agencyError = ""
            runCatching { withContext(Dispatchers.IO) { active.callRaw("agencyAgents/getTeams", JSONObject()) } }
                .onSuccess { agencyRaw = it }
                .onFailure { agencyError = it.message ?: "读取专家团队失败" }
            agencyBusy = false
        }
    }

    fun openCommandSheet() {
        showCommandSheet = true
        val active = client ?: return
        val sid = activeSessionId.ifBlank { sessions.firstOrNull()?.id.orEmpty() }
        if (sid.isEmpty()) {
            commandsError = "还没有会话"
            return
        }
        viewModelScope.launch {
            commandsBusy = true
            commandsError = ""
            runCatching { withContext(Dispatchers.IO) { active.commands(sid) } }
                .onSuccess { slashCommands = it }
                .onFailure { commandsError = it.message ?: "读取命令失败" }
            commandsBusy = false
        }
    }

    fun insertCommand(name: String) {
        input = "/$name "
        showCommandSheet = false
    }

    fun loadServerSnapshot() {
        val active = client ?: return
        viewModelScope.launch {
            serverBusy = true
            serverError = ""
            runCatching { withContext(Dispatchers.IO) { active.serverMonitor() } }
                .onSuccess { serverSnapshot = it }
                .onFailure { serverError = it.message ?: "读取服务器状态失败" }
            serverBusy = false
        }
    }

    fun loadOptimizerSettings() {
        val active = client ?: return
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { active.optimizerSettings() } }
                .onSuccess { settings ->
                    optimizerEnabled = settings.enabled
                    optimizerMaxChars = settings.maxChars
                    optimizerShowModes = settings.showModes
                    if (!optimizerModeTouched) optimizerMode = settings.defaultMode
                    optimizerSynced = true
                }
        }
    }

    fun openOptimizer() {
        if (input.isBlank()) {
            error = "先写一段草稿，再优化"
            return
        }
        if (input.length > optimizerMaxChars) {
            error = "草稿超过 ${optimizerMaxChars} 字上限"
            return
        }
        optimizerVisible = true
        optimizerError = ""
        optimizerText = ""
        runOptimizer()
    }

    fun closeOptimizer() {
        optimizerVisible = false
        optimizerBusy = false
        optimizerText = ""
        optimizerError = ""
    }

    fun updateOptimizerMode(mode: String) {
        if (optimizerMode == mode) return
        optimizerModeTouched = true
        optimizerMode = mode
        if (optimizerVisible) {
            optimizerText = ""
            runOptimizer()
        }
    }

    fun runOptimizer() {
        val active = client ?: return
        val draft = input.trim()
        if (draft.isEmpty()) return
        viewModelScope.launch {
            optimizerBusy = true
            optimizerError = ""
            runCatching {
                withContext(Dispatchers.IO) {
                    active.optimizePrompt(draft, optimizerMode, activeSessionId) { delta ->
                        optimizerText += delta
                    }
                }
            }.onFailure { optimizerError = it.message ?: "提示词增强失败" }
            optimizerBusy = false
        }
    }

    fun applyOptimizer() {
        if (optimizerText.isNotBlank()) {
            optimizerUndo = input
            input = optimizerText.trim()
        }
        closeOptimizer()
    }

    fun undoOptimizer() {
        optimizerUndo?.let { input = it }
        optimizerUndo = null
    }

    fun searchSessions(query: String) {
        searchQuery = query
        val active = client ?: return
        if (query.isBlank()) {
            searchResults = emptyList()
            return
        }
        viewModelScope.launch {
            searchBusy = true
            runCatching { withContext(Dispatchers.IO) { active.searchSessions(query.trim()) } }
                .onSuccess {
                    searchFallback = false
                    searchResults = it
                }
                .onFailure { failure ->
                    searchFallback = true
                    val q = query.trim()
                    searchResults = sessions
                        .filter {
                            (it.title ?: "").contains(q, true) || (it.cwd ?: "").contains(q, true) || it.id.contains(q, true)
                        }
                        .map { DshClient.SearchHit(it.id, listOfNotNull(it.title, it.cwd).joinToString(" · ")) }
                    error = failure.message ?: "搜索失败"
                }
            searchBusy = false
        }
    }

    fun loadSkills() {
        val active = client ?: return
        val sid = activeSessionId.ifBlank { sessions.firstOrNull()?.id.orEmpty() }
        if (sid.isEmpty()) return
        viewModelScope.launch {
            skillsBusy = true
            runCatching { withContext(Dispatchers.IO) { active.skills(sid) } }
                .onSuccess { skills = it }
                .onFailure { error = it.message ?: "加载技能失败" }
            skillsBusy = false
        }
    }

    fun openServersFor(profile: ServerProfile) {
        pairPrefillUrl = profile.url
        screen = Screen.Servers
        error = ""
    }

    fun updateMultiDevice(value: Boolean) {
        multiDevice = value
        store.setMultiDevice(value)
        loadSessions()
    }

    fun updateShowDeviceRunning(value: Boolean) {
        showDeviceRunning = value
        store.setShowDeviceRunning(value)
    }

    fun updateAutoScroll(value: Boolean) {
        autoScroll = value
        store.setAutoScroll(value)
    }

    fun clearTranslations() {
        translations.clear()
        translationStore.clear()
    }

    fun updateThinkExpanded(value: Boolean) {
        thinkExpanded = value
        store.setThinkExpanded(value)
    }

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
                val localName = currentProfile?.name
                val loaded = withContext(Dispatchers.IO) {
                    val local = active.sessions().map { it.copy(device = localName) }
                    val states = mutableListOf(
                        DeviceState(currentProfile?.id, localName ?: "本机", true, local.size, local.count { it.running }),
                    )
                    if (!multiDevice) {
                        local to states
                    } else {
                        val remote = profiles
                            .filter { it.id != currentProfile?.id }
                            .flatMap { profile ->
                                try {
                                    val extra = DshClient(getApplication(), profile.url, profile.id)
                                    try {
                                        val list = extra.sessions()
                                        states.add(
                                            DeviceState(profile.id, profile.name, true, list.size, list.count { it.running }),
                                        )
                                        list.map { it.copy(device = profile.name) }
                                    } finally {
                                        extra.close()
                                    }
                                } catch (e: Exception) {
                                    states.add(
                                        DeviceState(
                                            profile.id,
                                            profile.name,
                                            false,
                                            error = e.message ?: "连接失败",
                                        ),
                                    )
                                    emptyList()
                                }
                            }
                        val filteredRemote = if (showDeviceRunning) remote else remote.filterNot { it.running }
                        (local + filteredRemote) to states
                    }
                }
                sessions = loaded.first.sortedByDescending { it.updatedAt }
                deviceStates = loaded.second
                connection = "已连接"
                refreshInbox()
                if (pendingOpenSessionId.isNotEmpty()) {
                    val target = sessions.firstOrNull { it.id == pendingOpenSessionId }
                    if (target != null) {
                        pendingOpenSessionId = ""
                        openSession(target)
                    }
                }
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
        pendingQuestions = emptyList()
        hasMoreHistory = false
        loadingHistory = false
        historyCursor = 0L
        oldestSeq = Long.MAX_VALUE
        lastSeq = 0L
        screen = Screen.Chat
        connection = "连接中"
        error = ""
        refreshSessionMeta()
        loadOptimizerSettings()
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
        loadQuestions()
    }

    private fun loadQuestions() {
        val active = client ?: return
        val session = activeSessionId
        if (session.isEmpty()) return
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { active.pendingQuestions(session) } }
                .onSuccess {
                    if (activeSessionId == session) {
                        pendingQuestions = it
                        maybeNotifyQuestion()
                    }
                }
        }
    }

    private fun refreshInbox() {
        val active = client ?: return
        val running = sessions.filter { it.running }.take(6)
        viewModelScope.launch {
            val collected = mutableListOf<SessionQuestion>()
            for (session in running) {
                val pending = runCatching {
                    withContext(Dispatchers.IO) { active.pendingQuestions(session.id) }
                }.getOrDefault(emptyList())
                pending.forEach { question ->
                    collected.add(
                        SessionQuestion(
                            sessionId = session.id,
                            sessionTitle = session.title?.takeIf { it.isNotBlank() } ?: session.id.take(12),
                            pending = question,
                        ),
                    )
                }
            }
            inbox = collected
        }
    }

    fun answerSessionQuestion(question: SessionQuestion, answers: org.json.JSONArray) {
        val active = client ?: return
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    active.answerQuestions(question.sessionId, question.pending.callId, answers)
                }
            }.onSuccess {
                inbox = inbox.filterNot {
                    it.sessionId == question.sessionId && it.pending.callId == question.pending.callId
                }
                if (question.sessionId == activeSessionId) {
                    pendingQuestions = pendingQuestions.filterNot { it.callId == question.pending.callId }
                }
            }.onFailure { error = it.message ?: "回答失败" }
        }
    }

    fun answerQuestion(callId: String, answers: org.json.JSONArray) {
        val active = client ?: return
        val session = activeSessionId
        if (session.isEmpty()) return
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { active.answerQuestions(session, callId, answers) }
                pendingQuestions = pendingQuestions.filterNot { it.callId == callId }
            } catch (e: Exception) {
                error = e.message ?: "回答失败"
            }
        }
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

    fun renameSession(id: String, title: String) {
        val clean = title.trim()
        val client = client ?: return
        if (clean.isEmpty() || id.isEmpty()) return
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { client.renameSession(id, clean) } }
                .onSuccess { committed ->
                    sessions = sessions.map { if (it.id == id) it.copy(title = committed) else it }
                    if (id == activeSessionId) activeSessionTitle = committed
                }
                .onFailure { error = it.message ?: "重命名失败" }
        }
    }

    fun refreshSessionMeta() {
        val active = client ?: return
        val sid = activeSessionId
        if (sid.isEmpty()) return
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { active.sessionMeta(sid) } }
                .onSuccess { usage = it }
            if (permissionOptions.isEmpty()) {
                runCatching { withContext(Dispatchers.IO) { active.permissionCatalog() } }
                    .onSuccess { permissionOptions = it }
            }
            if (presetOptions.isEmpty()) {
                runCatching { withContext(Dispatchers.IO) { active.presets() } }
                    .onSuccess { presetOptions = it }
            }
        }
    }

    fun sendFeedback(messageId: String, rating: String) {
        val active = client ?: return
        val sid = activeSessionId
        feedback = feedback + (messageId to rating)
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { active.putFeedback(sid, messageId, rating) } }
                .onFailure { error = it.message ?: "反馈失败" }
        }
    }

    fun setPermission(value: String) {
        val active = client ?: return
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { active.setPermission(activeSessionId, value) } }
                .onFailure { error = it.message ?: "切换权限失败" }
            refreshSessionMeta()
        }
    }

    fun setDefaultPreset(id: String) {
        val active = client ?: return
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { active.setDefaultPreset(id) } }
                .onFailure { error = it.message ?: "设置预设失败" }
            refreshSessionMeta()
        }
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
        val active = client ?: return
        val candidates = (listOf(activeSessionId) + sessions.map { it.id }).filter { it.isNotBlank() }.distinct()
        if (candidates.isEmpty()) return
        screen = Screen.Terminal
        terminalChunks.clear()
        terminalTitle = "终端"
        viewModelScope.launch {
            busy = true
            error = ""
            var lastError: String? = null
            for (sid in candidates) {
                try {
                    val terminals = withContext(Dispatchers.IO) { active.listTerminals(sid) }
                    val terminal = terminals.firstOrNull() ?: withContext(Dispatchers.IO) {
                        active.createTerminal(sid)
                    }
                    terminalTitle = terminal.title
                    terminalSequence = 0L
                    terminalHandle = active.attachTerminal(
                        sid,
                        terminal.id,
                        onFrame = { frame ->
                            when (frame.optString("type")) {
                                "snapshot" -> {
                                    terminalChunks.clear()
                                    terminalChunks.add(frame.optString("screen"))
                                    pendingTerminalCommand?.let { command ->
                                        pendingTerminalCommand = null
                                        viewModelScope.launch {
                                            kotlinx.coroutines.delay(400)
                                            sendTerminalInput(command + "\r")
                                        }
                                    }
                                }
                                "output" -> terminalChunks.add(frame.optString("data"))
                            }
                        },
                        onFailure = { failure -> error = failure.message ?: "终端连接失败" },
                    )
                    lastError = null
                    break
                } catch (e: Exception) {
                    lastError = e.message ?: "打开终端失败"
                    // A session routed to a subagent cannot host a terminal; try the next one.
                    if (!lastError.contains("owned by subagent", ignoreCase = true)) break
                }
            }
            if (lastError != null) error = lastError
            busy = false
        }
    }

    private var pendingTerminalCommand: String? = null

    fun addSshHost(name: String, host: String, user: String, port: Int) {
        val entry = SshHost(java.util.UUID.randomUUID().toString(), name.ifBlank { host }, host, user, port)
        sshHosts = sshHosts + entry
        store.setSshHosts(sshHosts)
    }

    fun importWebSshHosts() {
        val active = client ?: return
        viewModelScope.launch {
            sshImportMessage = "读取中…"
            runCatching { withContext(Dispatchers.IO) { active.sshServers() } }
                .onSuccess { remote ->
                    var added = 0
                    var skipped = 0
                    remote.forEach { server ->
                        val exists = sshHosts.any { it.host == server.host && it.port == server.port && it.user == server.user }
                        if (exists) {
                            skipped++
                        } else {
                            sshHosts = sshHosts + SshHost(
                                id = java.util.UUID.randomUUID().toString(),
                                name = server.name,
                                host = server.host,
                                user = server.user,
                                port = server.port,
                            )
                            added++
                        }
                    }
                    if (added > 0) store.setSshHosts(sshHosts)
                    sshImportMessage = "导入 $added 台，跳过 $skipped 台"
                }
                .onFailure { sshImportMessage = it.message ?: "导入失败" }
        }
    }

    fun removeSshHost(id: String) {
        sshHosts = sshHosts.filterNot { it.id == id }
        store.setSshHosts(sshHosts)
    }

    fun openSshTerminal(host: String, user: String, port: Int) {
        if (activeSessionId.isEmpty() && sessions.isEmpty()) {
            error = "还没有会话，无法启动终端"
            return
        }
        pendingTerminalCommand = "ssh -p $port $user@$host"
        openTerminal()
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

    fun loadOlderMessages() {
        val active = client ?: return
        val session = activeSessionId
        if (session.isEmpty() || loadingHistory || !hasMoreHistory || historyCursor <= 0 || oldestSeq == Long.MAX_VALUE) return
        viewModelScope.launch {
            loadingHistory = true
            try {
                val page = withContext(Dispatchers.IO) {
                    active.historyPage(session, historyCursor, oldestSeq)
                }
                if (activeSessionId != session) return@launch
                if (page.items.isEmpty()) {
                    hasMoreHistory = false
                } else {
                    messages.addAll(0, page.items)
                    turnDurations = turnDurations + page.turnDurations
                    historyAdded = page.items.size
                    historyVersion += 1
                    if (page.oldestSeq != Long.MAX_VALUE) oldestSeq = page.oldestSeq
                    hasMoreHistory = page.hasMore
                }
            } catch (e: Exception) {
                error = e.message ?: "加载更早消息失败"
            } finally {
                loadingHistory = false
            }
        }
    }

    fun openFiles() {
        if (activeSessionId.isEmpty()) return
        filePath = ""
        files = emptyList()
        screen = Screen.Files
        loadFiles("")
    }

    /** Open the Files tab for one task's workspace. */
    fun openWorkspace(session: SessionSummary) {
        if (session.id != activeSessionId) openSession(session)
        navSelection = "files"
        screen = Screen.Sessions
        filePath = ""
        files = emptyList()
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
        val sid = activeSessionId.ifBlank { sessions.firstOrNull()?.id.orEmpty() }
        if (sid.isEmpty()) return
        viewModelScope.launch {
            busy = true
            try {
                val path = if (filePath.isEmpty()) entry.name else "$filePath/${entry.name}"
                val image = entry.name.substringAfterLast('.', "").lowercase() in setOf("png", "jpg", "jpeg", "webp", "gif")
                if (image && (entry.size ?: 0) < 2_000_000) {
                    val bytes = withContext(Dispatchers.IO) { active.readFileBytes(sid, path) }
                    val bitmap = withContext(Dispatchers.IO) { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }
                    if (bitmap != null) {
                        filePreview = FilePreviewData(entry.name, null, bitmap, bytes.size.toLong(), bitmap.width, bitmap.height)
                        return@launch
                    }
                }
                val text = withContext(Dispatchers.IO) { active.readFileText(sid, path) }
                val lines = text.count { it == '\n' } + if (text.isEmpty()) 0 else 1
                filePreview = FilePreviewData(entry.name, text, null, path = path, hasMore = lines >= 500, lineCount = lines)
            } catch (e: Exception) {
                error = e.message ?: "读取文件失败"
            } finally {
                busy = false
            }
        }
    }

    fun openGalleryAt(index: Int, items: List<GalleryItem>) {
        if (index !in items.indices) return
        galleryPreviewBase = items
        galleryPreviewIndex = index
        openArtifact(items[index].path)
    }

    fun galleryStep(delta: Int) {
        val next = galleryPreviewIndex + delta
        if (next !in galleryPreviewBase.indices) return
        galleryPreviewIndex = next
        openArtifact(galleryPreviewBase[next].path)
    }

    fun loadMorePreview() {
        val active = client ?: return
        val preview = filePreview ?: return
        val path = preview.path ?: return
        val sid = activeSessionId.ifBlank { sessions.firstOrNull()?.id.orEmpty() }
        if (sid.isEmpty()) return
        viewModelScope.launch {
            busy = true
            runCatching {
                withContext(Dispatchers.IO) {
                    val offset = preview.lineCount + 1
                    val text = active.readFileText(sid, path, offset, 500)
                    val lines = text.count { it == '\n' } + if (text.isEmpty()) 0 else 1
                    Triple(text, lines, lines >= 500)
                }
            }.onSuccess { (text, lines, more) ->
                val joined = if (preview.text.isNullOrEmpty()) text else preview.text + "\n" + text
                filePreview = preview.copy(
                    text = joined,
                    hasMore = more,
                    lineCount = preview.lineCount + lines,
                )
            }.onFailure { error = it.message ?: "加载更多失败" }
            busy = false
        }
    }

    fun openArtifact(path: String) {
        val active = client ?: return
        val session = activeSessionId.ifBlank { sessions.firstOrNull()?.id.orEmpty() }
        if (session.isEmpty()) return
        viewModelScope.launch {
            busy = true
            try {
                val cwd = activeCwd.orEmpty().trimEnd('/')
                val relative = if (cwd.isNotEmpty() && path.startsWith("$cwd/")) path.removePrefix("$cwd/") else path
                val name = relative.substringAfterLast('/')
                val image = name.substringAfterLast('.', "").lowercase() in setOf("png", "jpg", "jpeg", "webp", "gif")
                if (image) {
                    val bytes = withContext(Dispatchers.IO) { active.readFileBytes(session, relative) }
                    val bitmap = withContext(Dispatchers.IO) { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }
                    filePreview = FilePreviewData(name, null, bitmap, bytes.size.toLong(), bitmap?.width ?: 0, bitmap?.height ?: 0)
                } else {
                    val text = withContext(Dispatchers.IO) { active.readFileText(session, relative) }
                    val lines = text.count { it == '\n' } + if (text.isEmpty()) 0 else 1
                    filePreview = FilePreviewData(name, text, null, path = relative, hasMore = lines >= 500, lineCount = lines)
                }
            } catch (e: Exception) {
                error = e.message ?: "读取产物失败"
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
        pendingQuestions = emptyList()
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
                loadQuestions()
                val records = value.optJSONArray("records")
                messages.clear()
                queue.clear()
                val parsed = ChatParser.parsePage(records)
                turnDurations = parsed.turnDurations
                liveTurn = parsed.items.filterIsInstance<ChatItem.Assistant>().maxOfOrNull { it.turn } ?: 0
                liveTurnStart = 0L
                for (i in 0 until (records?.length() ?: 0)) {
                    val ev = records?.optJSONObject(i)?.optJSONObject("event") ?: continue
                    if (ev.optString("type") == "turn/start") {
                        liveTurnStart = ev.optLong("time", 0L)
                        liveTurn = ev.optJSONObject("data")?.optInt("turn", liveTurn)?.takeIf { it > 0 } ?: liveTurn
                    }
                }
                parsed.items.forEach { appendItem(it) }
                for (i in 0 until (records?.length() ?: 0)) {
                    records?.optJSONObject(i)?.optJSONObject("event")?.let {
                        applyInbox(it)
                        applyTitle(it)
                    }
                }
                lastSeq = maxSeq(records, lastSeq)
                historyCursor = value.optLong("cursor", 0L)
                hasMoreHistory = value.optBoolean("hasMore", false)
                oldestSeq = minSeq(records)
            }
            "event" -> {
                connection = "已连接"
                val event = value.optJSONObject("event") ?: return
                val seq = event.optLong("seq", 0L)
                if (seq in 1..lastSeq) return
                if (seq > lastSeq) lastSeq = seq
                applyInbox(event)
                applyTitle(event)
                if (event.optString("type") == "tool/call" && event.optJSONObject("data")?.optString("name") == "ask_user_question") {
                    loadQuestions()
                }
                when (event.optString("type")) {
                    "turn/start" -> {
                        val data = event.optJSONObject("data")
                        liveTurn = data?.optInt("turn", liveTurn + 1)?.takeIf { it > 0 } ?: (liveTurn + 1)
                        liveTurnStart = event.optLong("time", 0L)
                    }
                    "turn/end" -> {
                        val end = event.optLong("time", 0L)
                        if (liveTurn > 0 && liveTurnStart > 0L && end >= liveTurnStart) {
                            turnDurations = turnDurations + (liveTurn to (end - liveTurnStart))
                        }
                        maybeNotifyTurnDone()
                    }
                }
                val parsedEvent = ChatParser.parseEvent(event)
                if (parsedEvent is ChatItem.Assistant && liveTurn > 0) appendItem(parsedEvent.copy(turn = liveTurn))
                else parsedEvent?.let { appendItem(it) }
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
                    messages[index] = tool.copy(result = item.text, isError = item.isError, endedAt = item.at)
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
            messages.add(ChatItem.Assistant("stream", delta, streaming = true, at = System.currentTimeMillis()))
        }
    }

    private fun finishStreaming() {
        val index = messages.indexOfLast { it is ChatItem.Assistant && it.streaming }
        if (index >= 0) {
            val item = messages[index] as ChatItem.Assistant
            messages[index] = item.copy(streaming = false)
        }
    }

    private val attachmentLimit = 5
    private val attachmentMaxBytes = 20 * 1024 * 1024

    fun openAttachSheet() {
        showAttachSheet = true
    }

    fun dismissAttachSheet() {
        showAttachSheet = false
    }

    fun attach(uri: Uri) {
        if (client == null) {
            error = "尚未连接服务器"
            return
        }
        if (activeSessionId.isEmpty()) {
            error = "请先打开一个会话"
            return
        }
        if (pendingAttachments.size >= attachmentLimit) {
            error = "最多添加 $attachmentLimit 个附件"
            return
        }
        viewModelScope.launch {
            error = ""
            val resolver = getApplication<Application>().contentResolver
            var name = "附件"
            var size = 0L
            runCatching {
                resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (nameIndex >= 0) name = cursor.getString(nameIndex) ?: name
                        val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                        if (sizeIndex >= 0) size = cursor.getLong(sizeIndex)
                    }
                }
            }
            if (size > attachmentMaxBytes) {
                error = "「$name」超过 20 MB"
                return@launch
            }
            val mime = resolver.getType(uri) ?: "application/octet-stream"
            val item = PendingAttachment(
                id = UUID.randomUUID().toString(),
                name = name,
                mime = mime,
                size = size,
                uri = uri,
            )
            pendingAttachments.add(item)
            uploadAttachment(item.id)
        }
    }

    fun retryAttachment(id: String) {
        val index = pendingAttachments.indexOfFirst { it.id == id }
        if (index < 0) return
        pendingAttachments[index] = pendingAttachments[index].copy(
            status = AttachmentStatus.UPLOADING,
            progress = 0,
            error = null,
            attachment = null,
        )
        uploadAttachment(id)
    }

    fun removeAttachment(id: String) {
        pendingAttachments.removeAll { it.id == id }
        error = ""
    }

    private fun updateAttachment(id: String, update: (PendingAttachment) -> PendingAttachment) {
        val index = pendingAttachments.indexOfFirst { it.id == id }
        if (index >= 0) pendingAttachments[index] = update(pendingAttachments[index])
    }

    private fun uploadAttachment(id: String) {
        val active = client ?: return
        val session = activeSessionId
        viewModelScope.launch {
            val item = pendingAttachments.firstOrNull { it.id == id } ?: return@launch
            try {
                val resolver = getApplication<Application>().contentResolver
                val raw = withContext(Dispatchers.IO) {
                    resolver.openInputStream(item.uri)?.use { it.readBytes() }
                } ?: throw DshException("无法读取文件")
                if (raw.size > attachmentMaxBytes) throw DshException("单个附件不能超过 20 MB")
                if (item.mime.startsWith("image/")) {
                    val (encoded, preview) = withContext(Dispatchers.IO) { compressImage(raw) }
                    updateAttachment(id) { it.copy(preview = preview, size = encoded.size.toLong(), progress = 70) }
                    val data = Base64.getEncoder().encodeToString(encoded)
                    updateAttachment(id) {
                        it.copy(
                            status = AttachmentStatus.READY,
                            progress = 100,
                            attachment = DshClient.Attachment.Image(
                                mediaType = "image/jpeg",
                                data = data,
                                name = it.name,
                            ),
                        )
                    }
                } else {
                    val receipt = active.uploadFile(session, raw, item.name) { percent ->
                        viewModelScope.launch(Dispatchers.Main) {
                            updateAttachment(id) { it.copy(progress = percent) }
                        }
                    }
                    updateAttachment(id) {
                        it.copy(
                            status = AttachmentStatus.READY,
                            progress = 100,
                            attachment = DshClient.Attachment.File(receipt.receiptId, it.name),
                            size = receipt.bytes,
                        )
                    }
                }
            } catch (e: Exception) {
                updateAttachment(id) {
                    it.copy(status = AttachmentStatus.FAILED, error = e.message ?: "上传失败")
                }
            }
        }
    }

    private fun compressImage(bytes: ByteArray): Pair<ByteArray, Bitmap?> {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return bytes to null
        var sample = 1
        while (bounds.outWidth / sample > 1600 || bounds.outHeight / sample > 1600) sample *= 2
        val bitmap = BitmapFactory.decodeByteArray(
            bytes,
            0,
            bytes.size,
            BitmapFactory.Options().apply { inSampleSize = sample },
        ) ?: return bytes to null
        val out = java.io.ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 82, out)
        val encoded = out.toByteArray()
        val preview = runCatching {
            var previewSample = 1
            while (bounds.outWidth / previewSample > 360 || bounds.outHeight / previewSample > 360) previewSample *= 2
            BitmapFactory.decodeByteArray(
                encoded,
                0,
                encoded.size,
                BitmapFactory.Options().apply { inSampleSize = previewSample },
            )
        }.getOrNull()
        return (if (encoded.size in 1 until bytes.size) encoded else bytes) to preview
    }

    fun send() {
        val text = input.trim()
        val active = client ?: return
        if (activeSessionId.isEmpty()) return
        if (text.isEmpty() && pendingAttachments.isEmpty()) return
        if (pendingAttachments.any { it.status == AttachmentStatus.UPLOADING }) {
            error = "附件还在上传，请稍候"
            return
        }
        val failed = pendingAttachments.firstOrNull { it.status == AttachmentStatus.FAILED }
        if (failed != null) {
            error = "「${failed.name}」上传失败，请重试或移除"
            return
        }
        val ready = pendingAttachments.filter { it.status == AttachmentStatus.READY }
        val attachments = ready.mapNotNull { it.attachment }
        val localAttachments = ready.map { pending ->
            ChatItem.ChatAttachment(
                name = pending.name,
                mime = pending.mime,
                receiptId = (pending.attachment as? DshClient.Attachment.File)?.receiptId,
                bytes = pending.size,
                preview = pending.preview,
            )
        }
        input = ""
        pendingAttachments.clear()
        messages.add(
            ChatItem.User(
                "local-${System.currentTimeMillis()}",
                text,
                localAttachments,
                System.currentTimeMillis(),
            ),
        )
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { active.prompt(activeSessionId, text, attachments) }
            } catch (e: Exception) {
                error = e.message ?: "发送失败"
                input = text
                pendingAttachments.addAll(ready)
            }
        }
    }

    fun translate(key: String, text: String) {
        if (text.isBlank()) return
        val existing = translations[key]
        if (existing?.loading == true || existing?.text != null) return
        translations[key] = Translation(loading = true)
        viewModelScope.launch {
            try {
                val result = Translator.translate(text)
                translations[key] = Translation(text = result)
                withContext(Dispatchers.IO) { translationStore.put(key, result) }
            } catch (e: Exception) {
                translations[key] = Translation(error = e.message ?: "翻译失败")
            }
        }
    }

    fun retryTranslation(key: String, text: String) {
        translations.remove(key)
        viewModelScope.launch(Dispatchers.IO) { translationStore.remove(key) }
        translate(key, text)
    }

    fun clearTranslation(key: String) {
        translations.remove(key)
        viewModelScope.launch(Dispatchers.IO) { translationStore.remove(key) }
    }

    fun setForeground(value: Boolean) {
        foreground = value
    }

    fun updateNotifyEnabled(value: Boolean) {
        notifyEnabled = value
        store.setNotifyEnabled(value)
    }

    fun updateKeepAlive(value: Boolean) {
        keepAliveEnabled = value
        store.setKeepAlive(value)
    }

    fun showError(message: String) {
        error = message
    }

    fun selectNav(id: String) {
        navSelection = id
    }

    fun visibleNavIds(): List<String> {
        val order = navOrder.ifEmpty { listOf("now", "tasks", "files", "settings") }
        val visible = order.filter { it == "now" || it !in navHidden }
        return if (hiddenNavIds().isNotEmpty()) visible.take(3) else visible.take(4)
    }

    fun hiddenNavIds(): List<String> =
        navOrder.filter { it != "now" && it in navHidden }

    fun toggleNav(id: String) {
        if (id == "now") return
        if (id in navHidden) {
            val visible = navOrder.count { it == "now" || it !in navHidden }
            if (visible >= 4) return
            navHidden = navHidden - id
        } else {
            val visible = navOrder.count { it == "now" || it !in navHidden }
            if (visible <= 2) return
            navHidden = navHidden + id
        }
        store.setNavHidden(navHidden)
    }

    fun moveNav(id: String, delta: Int) {
        if (id == "now") return
        val list = navOrder.toMutableList()
        val index = list.indexOf(id)
        if (index < 0) return
        val target = (index + delta).coerceIn(1, list.size - 1)
        if (target == index) return
        list.removeAt(index)
        list.add(target, id)
        navOrder = list
        store.setNavOrder(list)
    }

    fun resetNav() {
        navOrder = DefaultNavItems.map { it.id }
        navHidden = emptySet()
        store.setNavOrder(navOrder)
        store.setNavHidden(navHidden)
        if (navSelection !in navOrder) navSelection = "now"
    }

    fun openNavSettings() {
        screen = Screen.NavSettings
    }

    fun closeNavSettings() {
        screen = Screen.Settings
    }

    /** Jump to the inbox tab (notification action). */
    fun openInbox() {
        navSelection = "now"
        if (screen != Screen.Chat) screen = Screen.Sessions
    }

    var taskTitle by mutableStateOf("")
        private set
    var taskRunning by mutableStateOf(false)
        private set

    /** Open one task's detail; the underlying chat is loaded in parallel. */
    fun openTask(session: SessionSummary) {
        taskTitle = session.title?.takeIf { it.isNotBlank() } ?: session.id.take(18)
        taskRunning = session.running
        if (session.id != activeSessionId) {
            openSession(session)
        }
        screen = Screen.TaskDetail
    }

    fun closeTask() {
        screen = if (activeSessionId.isNotEmpty()) Screen.Chat else Screen.Sessions
    }

    fun reloadTask() {
        val session = sessions.firstOrNull { it.id == activeSessionId } ?: return
        taskRunning = session.running
        openSession(session)
        screen = Screen.TaskDetail
    }

    fun openTasks() {
        screen = Screen.Tasks
        loadSessions()
    }

    fun closeTasks() {
        screen = Screen.Sessions
    }

    /** Deep-link from a notification: open the session once the list is loaded. */
    fun openSessionFromNotification(sessionId: String) {
        if (sessionId.isEmpty()) return
        pendingOpenSessionId = sessionId
        val match = sessions.firstOrNull { it.id == sessionId }
        if (match != null) {
            pendingOpenSessionId = ""
            openSession(match)
        } else {
            loadSessions()
        }
    }

    private fun maybeNotifyTurnDone() {
        if (foreground || !notifyEnabled || activeSessionId.isEmpty()) return
        if (lastTurnNotified == lastSeq) return
        lastTurnNotified = lastSeq
        val last = messages.lastOrNull { it is ChatItem.Assistant } as? ChatItem.Assistant
        val text = last?.text?.takeIf { it.isNotBlank() } ?: "任务已完成，点击查看结果"
        notifications.turnDone(activeSessionId, activeSessionTitle, text)
    }

    private fun maybeNotifyQuestion() {
        if (foreground || !notifyEnabled) return
        val pending = pendingQuestions.firstOrNull() ?: return
        if (pending.callId == lastQuestionNotified) return
        lastQuestionNotified = pending.callId
        val text = pending.questions.firstOrNull()?.question ?: "Agent 需要你的回答"
        notifications.question(activeSessionId, activeSessionTitle, text)
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

    private fun minSeq(records: org.json.JSONArray?): Long {
        records ?: return Long.MAX_VALUE
        var min = Long.MAX_VALUE
        for (i in 0 until records.length()) {
            val seq = records.optJSONObject(i)?.optJSONObject("event")?.optLong("seq", 0L) ?: 0L
            if (seq in 1 until min) min = seq
        }
        return min
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
