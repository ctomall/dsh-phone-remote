package com.ila3.dshnative.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.viewinterop.AndroidView
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.OutlinedButton
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import android.annotation.SuppressLint
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.RadioButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import com.ila3.dshnative.protocol.DshClient
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ila3.dshnative.protocol.ChatItem
import com.ila3.dshnative.protocol.SessionSummary
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject
import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.speech.RecognizerIntent
import android.util.Base64
import androidx.activity.result.PickVisualMediaRequest
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch
import androidx.core.content.FileProvider
import java.io.File

@Composable
fun NativeApp(vm: NativeViewModel = viewModel()) {
    Box(
        Modifier
            .fillMaxSize()
            .background(DshBackgroundBrush)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        when (val screen = vm.screen) {
            Screen.Servers -> ServersScreen(vm)
            Screen.Sessions -> HomeScreen(vm)
            Screen.Chat -> ChatScreen(vm)
            Screen.Files -> FilesScreen(vm)
            Screen.Terminal -> TerminalScreen(vm)
            Screen.Settings -> SettingsScreen(vm)
            Screen.Tasks -> TasksScreen(vm)
            Screen.NavSettings -> NavSettingsScreen(vm)
            Screen.TaskDetail -> TaskDetailScreen(vm)
        }
        if (vm.error.isNotEmpty()) {
            ErrorBar(vm.error, vm::dismissError, Modifier.align(Alignment.BottomCenter).padding(16.dp))
        }
    }
}

@Composable
internal fun ThemeToggle() {
    val context = LocalContext.current
    val isPaper = DshTheme.mode == DshThemeMode.PAPER
    IconButton(
        onClick = {
            DshTheme.mode = if (isPaper) DshThemeMode.AURORA else DshThemeMode.PAPER
            context.getSharedPreferences("dsh_native", android.content.Context.MODE_PRIVATE)
                .edit()
                .putString("theme_mode", if (isPaper) "aurora" else "paper")
                .apply()
        },
    ) {
        Icon(
            if (isPaper) Icons.Filled.DarkMode else Icons.Filled.LightMode,
            contentDescription = if (isPaper) "切换深海主题" else "切换纸白主题",
            tint = DshMuted,
        )
    }
}

@Composable
private fun ServersScreen(vm: NativeViewModel) {
    val context = LocalContext.current
    var url by remember { mutableStateOf(vm.currentProfile?.url ?: "https://harness.ila3.net/phone/") }
    var code by remember { mutableStateOf("") }
    var scanHint by remember { mutableStateOf("") }
    val scanLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        val raw = result.contents
        if (raw.isNullOrBlank()) {
            scanHint = "未读取到二维码"
        } else {
            val parsed = parsePairPayload(raw)
            if (parsed == null) {
                scanHint = "这不是 DSH 配对二维码"
            } else {
                if (parsed.first.isNotEmpty()) url = parsed.first
                code = parsed.second
                scanHint = "已识别，点「连接」完成配对"
            }
        }
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            scanLauncher.launch(
                ScanOptions()
                    .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                    .setPrompt("扫描电脑端配对二维码")
                    .setBeepEnabled(false)
                    .setOrientationLocked(false),
            )
        } else {
            scanHint = "需要相机权限才能扫码"
        }
    }
    val startScan = {
        val context = context
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            scanLauncher.launch(
                ScanOptions()
                    .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                    .setPrompt("扫描电脑端配对二维码")
                    .setBeepEnabled(false)
                    .setOrientationLocked(false),
            )
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 22.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text("DSH 原生", color = DshText, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "原生会话、流式消息与工具卡片；对外入口仍是 https://…/phone/",
                        color = DshMuted,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
                ThemeToggle()
            }
        }
        if (vm.profiles.isNotEmpty()) {
            item {
                Text("已绑定 DSH", color = DshMuted, fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp))
            }
            items(vm.profiles, key = { it.id }) { profile ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(22.dp))
                        .background(DshSurface)
                        .border(1.dp, DshBorder, RoundedCornerShape(22.dp))
                        .clickable { vm.switchTo(profile) }
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(profile.name, color = DshText, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                        Text(profile.url, color = DshMuted, fontSize = 11.sp, maxLines = 1)
                    }
                    if (profile.id == vm.currentId) {
                        Text("当前", color = DshTealSoft, fontSize = 10.sp)
                    }
                    TextButton(onClick = { vm.deleteProfile(profile) }) {
                        Text("删除", color = DshDanger, fontSize = 11.sp)
                    }
                }
            }
        }
        item {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(DshSurface)
                    .border(1.dp, DshBorder, RoundedCornerShape(22.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    if (vm.profiles.isEmpty()) "连接第一台 DSH" else "添加 DSH",
                    color = DshText,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("服务器地址") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
                    colors = fieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it.uppercase().take(9) },
                    label = { Text("配对码") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    colors = fieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedButton(
                    onClick = startScan,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                ) {
                    Text("扫一扫配对二维码", color = DshTealSoft, fontSize = 13.sp)
                }
                if (scanHint.isNotEmpty()) {
                    Text(scanHint, color = DshMuted, fontSize = 10.sp)
                }
                Button(
                    onClick = { vm.pair(url, code) },
                    enabled = !vm.busy,
                    colors = ButtonDefaults.buttonColors(containerColor = DshTeal, contentColor = DshOnAccent),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().height(46.dp),
                ) {
                    if (vm.busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = DshOnAccent)
                    else Text("连接", fontWeight = FontWeight.SemiBold)
                }
                Text(
                    "在电脑端「设置 → 手机远程」选择公网/内网/隧道入口生成配对码。",
                    color = DshMuted,
                    fontSize = 10.sp,
                )
            }
        }
    }
}

@Composable
private fun TasksScreen(vm: NativeViewModel) {
    val running = vm.sessions.filter { it.running }
    val taskState = remember(vm.messages.size, vm.activeSessionId) { parseTaskState(vm.messages) }
    val tools = remember(vm.messages.size) { vm.messages.filterIsInstance<ChatItem.Tool>().takeLast(8).reversed() }
    Column(Modifier.fillMaxSize()) {
        TopBar(
            "任务",
            "${running.size} 个运行中 · ${vm.sessions.size} 个会话",
            vm::closeTasks,
            Icons.AutoMirrored.Filled.ArrowBack,
        )
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { SectionLabel("运行中的会话") }
            if (running.isEmpty()) {
                item { Text("当前没有运行中的会话", color = DshMuted, fontSize = 12.sp) }
            } else {
                items(running, key = { "running-${it.id}" }) { session ->
                    SessionRow(session, active = session.id == vm.activeSessionId) { vm.openSession(session) }
                }
            }
            item { SectionLabel("当前会话任务") }
            if (vm.activeSessionId.isEmpty()) {
                item { Text("先打开一个会话，这里会显示它的目标与 todo", color = DshMuted, fontSize = 12.sp) }
            } else {
                taskState.first?.let { goal -> item(key = "goal") { GoalCard(goal) } }
                if (taskState.second.isEmpty()) {
                    item { Text("这个会话还没有 todo（Agent 调用 todo_write 后会出现在这里）", color = DshMuted, fontSize = 12.sp) }
                } else {
                    items(taskState.second.size, key = { "todo-$it" }) { index -> TodoRow(taskState.second[index]) }
                }
            }
            item { SectionLabel("最近工具调用") }
            if (tools.isEmpty()) {
                item { Text("暂无工具调用", color = DshMuted, fontSize = 12.sp) }
            } else {
                items(tools.size, key = { "recent-${tools[it].key}" }) { index -> ToolCard(tools[index]) }
            }
        }
    }
}

private fun parseTaskState(messages: List<ChatItem>): Pair<String?, List<Pair<String, String>>> {
    var goal: String? = null
    messages.lastOrNull { it is ChatItem.Tool && (it.name == "create_goal" || it.name == "update_goal") }?.let { raw ->
        val tool = raw as ChatItem.Tool
        goal = runCatching { JSONObject(tool.arguments).optString("objective").ifBlank { null } }.getOrNull()
    }
    val todos = mutableListOf<Pair<String, String>>()
    messages.lastOrNull { it is ChatItem.Tool && it.name == "todo_write" }?.let { raw ->
        val tool = raw as ChatItem.Tool
        runCatching {
            val array = JSONObject(tool.arguments).optJSONArray("todos") ?: JSONArray()
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val content = item.optString("content")
                if (content.isNotBlank()) todos.add(content to item.optString("status"))
            }
        }
    }
    return goal to todos
}

@Composable
private fun GoalCard(goal: String) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(DshSurface)
            .border(1.dp, DshBorder, RoundedCornerShape(16.dp)).padding(14.dp),
    ) {
        Text("目标", color = DshTeal, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
        Text(goal, color = DshText, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun TodoRow(todo: Pair<String, String>) {
    val (content, status) = todo
    val icon = when (status) {
        "completed" -> Icons.Filled.CheckCircle
        "in_progress" -> Icons.Filled.PlayCircleOutline
        else -> Icons.Filled.RadioButtonUnchecked
    }
    val tint = when (status) {
        "completed" -> DshTeal
        "in_progress" -> DshTealSoft
        else -> DshMuted
    }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(DshSurface)
            .border(1.dp, DshBorder, RoundedCornerShape(14.dp)).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(
            content,
            color = if (status == "completed") DshMuted else DshText,
            fontSize = 13.sp,
            textDecoration = if (status == "completed") TextDecoration.LineThrough else null,
        )
    }
}

@Composable
private fun SessionsScreen(vm: NativeViewModel) {
    val profile = vm.currentProfile
    var query by remember { mutableStateOf("") }
    val filtered = vm.sessions.filter { session ->
        query.isBlank() ||
            session.title?.contains(query, ignoreCase = true) == true ||
            session.id.contains(query, ignoreCase = true) ||
            session.cwd?.contains(query, ignoreCase = true) == true
    }
    Column(Modifier.fillMaxSize()) {
        TopBar(
            title = profile?.name ?: "DSH",
            subtitle = "${vm.connection} · ${vm.sessions.size} 个会话",
            onBack = { vm.showServers() },
            backIcon = Icons.Filled.Menu,
            actions = {
                ThemeToggle()
                IconButton(onClick = { vm.newSession() }) {
                    Icon(Icons.Filled.Add, contentDescription = "新建会话", tint = DshMuted)
                }
                IconButton(onClick = { vm.refreshSessions() }) {
                    Icon(Icons.Filled.Refresh, contentDescription = "刷新", tint = DshMuted)
                }
                IconButton(onClick = { vm.openSettings() }) {
                    Icon(Icons.Filled.Settings, contentDescription = "设置", tint = DshMuted)
                }
            },
        )
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("搜索标题、ID 或目录", color = DshMuted, fontSize = 12.sp) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = DshMuted, modifier = Modifier.size(18.dp)) },
            singleLine = true,
            colors = fieldColors(),
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        )
        val runningCount = vm.sessions.count { it.running }
        if (runningCount > 0) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp)
                    .clip(RoundedCornerShape(14.dp)).background(DshSurface)
                    .border(1.dp, DshBorder, RoundedCornerShape(14.dp))
                    .clickable { vm.openTasks() }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(7.dp).clip(CircleShape).background(DshTeal))
                Spacer(Modifier.width(8.dp))
                Text("$runningCount 个会话运行中", color = DshText, fontSize = 12.sp, modifier = Modifier.weight(1f))
                Text("查看任务 ›", color = DshTeal, fontSize = 11.sp)
            }
        }
        when {
            vm.busy && vm.sessions.isEmpty() -> Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = DshTeal)
            }
            vm.sessions.isEmpty() && !vm.busy -> Column(
                Modifier.fillMaxSize().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text("还没有会话", color = DshText, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Text("新建一个会话，或先在网页端开始", color = DshMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                Spacer(Modifier.height(18.dp))
                Button(
                    onClick = { vm.newSession() },
                    colors = ButtonDefaults.buttonColors(containerColor = DshTeal, contentColor = DshOnAccent),
                    shape = RoundedCornerShape(16.dp),
                ) { Text("新建会话") }
            }
            filtered.isEmpty() -> Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text("没有匹配的会话", color = DshMuted, fontSize = 13.sp)
            }
            else -> {
                val groups = filtered.groupBy { dateGroup(it.updatedAt) }
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    groups.forEach { (label, items) ->
                        item(key = "header-$label") {
                            Text(label, color = DshMuted, fontSize = 10.sp, letterSpacing = 1.sp, modifier = Modifier.padding(top = 10.dp, bottom = 2.dp))
                        }
                        items(items, key = { it.id }) { session ->
                            SessionRow(session, active = session.id == vm.activeSessionId) { vm.openSession(session) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SessionRow(session: SessionSummary, active: Boolean = false, onClick: () -> Unit) {
    val title = session.title?.takeIf { it.isNotBlank() } ?: session.id.take(18)
    val initial = title.firstOrNull()?.toString()?.uppercase() ?: "D"
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(if (active) DshSelected else DshSurface)
            .border(1.dp, if (active) DshSelected else DshBorder, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(38.dp).clip(CircleShape).background(if (active) Color.White.copy(alpha = 0.16f) else if (session.running) DshSelected.copy(alpha = 0.12f) else DshSurface2),
            contentAlignment = Alignment.Center,
        ) {
            Text(initial, color = if (active) Color.White else if (session.running) DshTeal else DshMuted, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title,
                    color = if (active) Color.White else DshText,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (session.running) {
                    Spacer(Modifier.width(6.dp))
                    Box(Modifier.size(6.dp).clip(CircleShape).background(if (active) Color.White else DshTeal))
                }
            }
            Text(
                buildString {
                    append(formatTime(session.updatedAt))
                    session.cwd?.let { append(" · ").append(it.substringAfterLast('/')) }
                },
                color = if (active) Color.White.copy(alpha = 0.72f) else DshMuted,
                fontSize = 10.5.sp,
                maxLines = 1,
            )
        }
        Icon(Icons.Filled.KeyboardArrowRight, contentDescription = null, tint = if (active) Color.White.copy(alpha = 0.8f) else DshMuted, modifier = Modifier.size(18.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatScreen(vm: NativeViewModel) {
    val listState = rememberLazyListState()
    var initialScrollDone by remember(vm.activeSessionId) { mutableStateOf(false) }
    var historyAnchorIndex by remember { mutableStateOf(-1) }
    var historyAnchorOffset by remember { mutableStateOf(0) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val speaker = rememberSpeaker()
    val speechLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull().orEmpty()
            if (spoken.isNotBlank()) vm.input = (vm.input.trim() + " " + spoken).trim()
        }
    }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { vm.attach(it) }
    }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { vm.attach(it) }
    }
    var cameraTarget by remember { mutableStateOf<Uri?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        if (saved) cameraTarget?.let { vm.attach(it) }
        cameraTarget = null
    }
    LaunchedEffect(vm.activeSessionId, vm.messages.size) {
        if (vm.messages.isEmpty()) return@LaunchedEffect
        val lastIndex = vm.messages.lastIndex
        if (!initialScrollDone) {
            // Opening a long conversation must land on the newest message
            // immediately. animateScrollToItem would visibly travel from the
            // top through hundreds of records.
            runCatching { listState.scrollToItem(lastIndex + 1) }
            initialScrollDone = true
        } else {
            // Follow live output only while the reader is already near the
            // bottom; scrolling up to read history must not be interrupted.
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            if (lastVisible >= vm.messages.size - 3) {
                runCatching { listState.animateScrollToItem(lastIndex + 1) }
            }
        }
    }
    LaunchedEffect(vm.historyVersion) {
        if (historyAnchorIndex >= 0) {
            runCatching {
                listState.scrollToItem((historyAnchorIndex + vm.historyAdded).coerceAtLeast(0), historyAnchorOffset)
            }
            historyAnchorIndex = -1
        }
    }
    LaunchedEffect(listState, initialScrollDone, vm.activeSessionId) {
        if (!initialScrollDone) return@LaunchedEffect
        snapshotFlow { listState.firstVisibleItemIndex }.collect { index ->
            if (index <= 1 && vm.hasMoreHistory && !vm.loadingHistory && vm.messages.isNotEmpty()) {
                historyAnchorIndex = listState.firstVisibleItemIndex
                historyAnchorOffset = listState.firstVisibleItemScrollOffset
                vm.loadOlderMessages()
            }
        }
    }
    Column(Modifier.fillMaxSize()) {
        TopBar(
            title = vm.activeSessionTitle.ifBlank { "新会话" },
            subtitle = "${vm.connection} · ${vm.currentModel?.name ?: "默认模型"}",
            onBack = { vm.closeSession() },
            backIcon = Icons.AutoMirrored.Filled.ArrowBack,
            actions = {
                IconButton(onClick = { vm.showChatMenu() }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "会话操作", tint = DshMuted)
                }
            },
        )
        var imagePreview by remember { mutableStateOf<Bitmap?>(null) }
        Box(Modifier.weight(1f).fillMaxWidth()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (vm.messages.isEmpty()) {
                item(key = "empty-chat") {
                    if (vm.connection == "连接中") ChatSkeleton() else EmptyChatState()
                }
            }
            item(key = "history-loader") {
                when {
                    vm.loadingHistory -> Row(
                        Modifier.fillMaxWidth().padding(10.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(Modifier.size(16.dp), color = DshTeal, strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("加载更早消息…", color = DshMuted, fontSize = 11.sp)
                    }
                    vm.hasMoreHistory -> Row(
                        Modifier.fillMaxWidth().padding(4.dp),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        TextButton(onClick = {
                            historyAnchorIndex = listState.firstVisibleItemIndex
                            historyAnchorOffset = listState.firstVisibleItemScrollOffset
                            vm.loadOlderMessages()
                        }) { Text("加载更早消息", color = DshTeal, fontSize = 11.sp) }
                    }
                }
            }
            items(vm.messages.size) { index ->
                val message = vm.messages[index]
                val previous = vm.messages.getOrNull(index - 1)
                val freshDay = message.at > 0L && (previous == null || previous.at <= 0L || !sameDay(previous.at, message.at))
                Column {
                    if (freshDay) DayDivider(dayLabel(message.at))
                    MessageRow(message, vm, speaker) { imagePreview = it }
                }
            }
        }
        val atBottom by remember {
            derivedStateOf {
                val info = listState.layoutInfo
                val last = info.visibleItemsInfo.lastOrNull()
                last == null || last.index >= info.totalItemsCount - 1
            }
        }
        if (!atBottom && vm.messages.isNotEmpty()) {
            SmallFloatingActionButton(
                onClick = { scope.launch { runCatching { listState.animateScrollToItem(vm.messages.size) } } },
                modifier = Modifier.align(Alignment.BottomEnd).padding(14.dp),
                containerColor = DshSurface,
                contentColor = DshTeal,
            ) { Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "回到最新") }
        }
        }
        imagePreview?.let { bitmap -> ImagePreviewDialog(bitmap) { imagePreview = null } }
        vm.pendingQuestions.firstOrNull()?.let { pending ->
            QuestionCard(pending, onSubmit = { answers -> vm.answerQuestion(pending.callId, answers) })
        }
        if (vm.queue.isNotEmpty()) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(DshSurface2)
                    .border(1.dp, DshBorder, RoundedCornerShape(16.dp))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text("${vm.queue.size} 条排队消息", color = DshMuted, fontSize = 9.sp)
                vm.queue.forEach { item ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(item.text, color = DshText, fontSize = 11.sp, maxLines = 1, modifier = Modifier.weight(1f))
                        TextButton(onClick = { vm.steerQueued(item) }) {
                            Text("转向", color = DshTealSoft, fontSize = 10.sp)
                        }
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = "移除排队消息",
                            tint = DshMuted,
                            modifier = Modifier.size(16.dp).clickable { vm.removeQueued(item) },
                        )
                    }
                }
            }
        }
        if (vm.pendingAttachments.isNotEmpty()) PendingAttachmentTray(vm)
        val attachmentUploading = vm.pendingAttachments.any { it.status == NativeViewModel.AttachmentStatus.UPLOADING }
        val attachmentFailed = vm.pendingAttachments.any { it.status == NativeViewModel.AttachmentStatus.FAILED }
        val sendEnabled = !attachmentUploading && !attachmentFailed &&
            (vm.input.isNotBlank() || vm.pendingAttachments.isNotEmpty())
        Row(
            Modifier.fillMaxWidth().imePadding().padding(horizontal = 12.dp, vertical = 8.dp)
                .clip(RoundedCornerShape(26.dp))
                .background(DshSurface)
                .border(1.dp, DshBorder, RoundedCornerShape(26.dp))
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = { vm.openAttachSheet() },
                modifier = Modifier.size(40.dp),
            ) {
                Icon(Icons.Filled.Add, contentDescription = "添加附件", tint = DshMuted, modifier = Modifier.size(20.dp))
            }
            IconButton(
                onClick = {
                    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
                        putExtra(RecognizerIntent.EXTRA_PROMPT, "说出你的指令")
                    }
                    runCatching { speechLauncher.launch(intent) }
                        .onFailure { vm.showError("设备没有可用的语音识别服务") }
                },
                modifier = Modifier.size(40.dp),
            ) {
                Icon(Icons.Filled.Mic, contentDescription = "语音输入", tint = DshMuted, modifier = Modifier.size(19.dp))
            }
            Box(Modifier.weight(1f)) {
                if (vm.input.isEmpty()) {
                    Text("跟进…", color = DshMuted, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 6.dp))
                }
                BasicTextField(
                    value = vm.input,
                    onValueChange = { vm.input = it },
                    textStyle = TextStyle(color = DshText, fontSize = 14.sp, lineHeight = 20.sp),
                    cursorBrush = SolidColor(DshTeal),
                    maxLines = 5,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 10.dp),
                )
            }
            Box(
                Modifier.size(38.dp).clip(CircleShape)
                    .background(if (sendEnabled) DshTeal else DshMuted.copy(alpha = 0.3f))
                    .clickable(enabled = sendEnabled) { vm.send() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.ArrowUpward, contentDescription = "发送", tint = DshOnAccent, modifier = Modifier.size(18.dp))
            }
        }
    }
    if (vm.showAttachSheet) {
        ModalBottomSheet(
            onDismissRequest = { vm.dismissAttachSheet() },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = DshSurface,
        ) {
            AttachSheet(
                onImage = {
                    vm.dismissAttachSheet()
                    imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
                onCamera = {
                    vm.dismissAttachSheet()
                    val directory = File(context.cacheDir, "captures").apply { mkdirs() }
                    val file = File(directory, "photo-${System.currentTimeMillis()}.jpg")
                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                    cameraTarget = uri
                    cameraLauncher.launch(uri)
                },
                onFile = {
                    vm.dismissAttachSheet()
                    filePicker.launch(arrayOf("*/*"))
                },
                onClose = { vm.dismissAttachSheet() },
            )
        }
    }
    if (vm.showModelPicker) ModelPickerDialog(vm)
    if (vm.showChatActions) {
        ModalBottomSheet(
            onDismissRequest = { vm.dismissChatMenu() },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = DshSurface,
        ) {
            ChatActionSheet(vm)
        }
    }
    if (vm.showRenameDialog) RenameDialog(vm)
}

@Composable
private fun ChatActionSheet(vm: NativeViewModel) {
    Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text("会话操作", color = DshText, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(8.dp))
        ChatAction(Icons.Filled.Checklist, "任务与进度") { vm.dismissChatMenu(); vm.openTasks() }
        ChatAction(Icons.Filled.Tune, "更换模型", vm.currentModel?.name ?: "默认") { vm.dismissChatMenu(); vm.openModelPicker() }
        ChatAction(Icons.Filled.Edit, "重命名会话") { vm.showRename() }
        ChatAction(Icons.Filled.Refresh, "刷新会话") { vm.reloadActiveSession() }
        ChatAction(Icons.Filled.Folder, "文件") { vm.dismissChatMenu(); vm.openFiles() }
        ChatAction(Icons.Filled.Terminal, "终端") { vm.dismissChatMenu(); vm.openTerminal() }
        HorizontalDivider(color = DshBorder, modifier = Modifier.padding(vertical = 6.dp))
        ChatAction(Icons.Filled.StopCircle, "停止生成", danger = true) { vm.stopGeneration() }
    }
}

@Composable
private fun ChatAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String? = null, danger: Boolean = false, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = if (danger) DshDanger else DshMuted, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(label, color = if (danger) DshDanger else DshText, fontSize = 14.sp, modifier = Modifier.weight(1f))
        value?.let { Text(it, color = DshMuted, fontSize = 11.sp) }
    }
}

@Composable
private fun RenameDialog(vm: NativeViewModel) {
    var title by remember(vm.activeSessionTitle) { mutableStateOf(vm.activeSessionTitle) }
    AlertDialog(
        onDismissRequest = { vm.dismissRename() },
        title = { Text("重命名会话", color = DshText, fontSize = 16.sp) },
        text = {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                singleLine = true,
                colors = fieldColors(),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = { TextButton(onClick = { vm.renameActive(title) }) { Text("保存", color = DshTeal) } },
        dismissButton = { TextButton(onClick = { vm.dismissRename() }) { Text("取消", color = DshMuted) } },
        containerColor = DshSurface,
    )
}

@Composable
internal fun QuestionCard(pending: DshClient.PendingQuestion, onSubmit: (org.json.JSONArray) -> Unit) {
    val selections = remember(pending.callId) {
        mutableStateMapOf<String, MutableSet<String>>().apply {
            pending.questions.forEach { put(it.id, mutableSetOf()) }
        }
    }
    val customs = remember(pending.callId) { mutableStateMapOf<String, String>() }
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(18.dp)).background(DshSurface)
            .border(1.dp, DshTeal.copy(alpha = 0.3f), RoundedCornerShape(18.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("DSH 需要你的确认", color = DshTeal, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
        pending.questions.forEach { question ->
            question.header?.let { Text(it, color = DshMuted, fontSize = 10.sp) }
            Text(question.question, color = DshText, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            question.detail?.let { Text(it, color = DshMuted, fontSize = 11.sp) }
            if (question.options.isEmpty()) {
                OutlinedTextField(
                    value = customs[question.id].orEmpty(),
                    onValueChange = { customs[question.id] = it },
                    placeholder = { Text("输入回答", color = DshMuted, fontSize = 12.sp) },
                    colors = fieldColors(),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                question.options.forEach { option ->
                    val selected = selections[question.id]?.contains(option.label) == true
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                            .clickable {
                                val set = selections.getOrPut(question.id) { mutableSetOf() }
                                if (question.multiSelect) {
                                    if (selected) set.remove(option.label) else set.add(option.label)
                                } else {
                                    set.clear(); set.add(option.label)
                                }
                            }
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (question.multiSelect) {
                            Checkbox(checked = selected, onCheckedChange = null)
                        } else {
                            RadioButton(selected = selected, onClick = null)
                        }
                        Column {
                            Text(option.label, color = DshText, fontSize = 13.sp)
                            option.description?.let { Text(it, color = DshMuted, fontSize = 10.sp) }
                        }
                    }
                }
            }
        }
        val ready = pending.questions.all { question ->
            selections[question.id]?.isNotEmpty() == true || customs[question.id]?.isNotBlank() == true
        }
        Button(
            enabled = ready,
            onClick = {
                val answers = org.json.JSONArray()
                pending.questions.forEach { question ->
                    val selected = selections[question.id]?.toList().orEmpty()
                    val custom = customs[question.id]?.trim().orEmpty()
                    if (selected.isNotEmpty() || custom.isNotEmpty()) {
                        answers.put(
                            org.json.JSONObject().put("id", question.id)
                                .put("selected", org.json.JSONArray(selected))
                                .apply { if (custom.isNotEmpty()) put("custom", custom) },
                        )
                    }
                }
                if (answers.length() > 0) onSubmit(answers)
            },
            colors = ButtonDefaults.buttonColors(containerColor = DshTeal, contentColor = DshOnAccent),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth(),
        ) { Text("提交回答") }
    }
}

@Composable
private fun ModelPickerDialog(vm: NativeViewModel) {
    AlertDialog(
        onDismissRequest = { vm.dismissModelPicker() },
        title = { Text("选择模型", color = DshText, fontSize = 16.sp) },
        text = {
            LazyColumn(Modifier.heightIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                vm.modelGroups.forEach { group ->
                    item(key = "group-${group.id}") {
                        Text(group.name, color = DshMuted, fontSize = 10.sp, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
                    }
                    items(group.models, key = { "${group.id}-${it.model}" }) { model ->
                        val selected = vm.currentModel?.provider == group.id && vm.currentModel?.model == model.model
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (selected) DshSelected else Color.Transparent)
                                .clickable { vm.chooseModel(model) }
                                .padding(horizontal = 10.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(model.name, color = DshText, fontSize = 13.sp)
                                Text(model.provider, color = DshMuted, fontSize = 9.sp)
                            }
                            if (selected) Text("当前", color = DshTealSoft, fontSize = 10.sp)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { vm.dismissModelPicker() }) { Text("关闭", color = DshTealSoft) }
        },
        containerColor = DshSurface,
    )
}

@Composable
private fun MessageRow(item: ChatItem, vm: NativeViewModel? = null, speaker: Speaker? = null, onImage: (Bitmap) -> Unit = {}) {
    when (item) {
        is ChatItem.User -> UserMessageRow(item, onImage)
        is ChatItem.Assistant -> AssistantMessageRow(item, vm, speaker)
        is ChatItem.Tool -> ToolCard(item)
        is ChatItem.ToolResult -> Unit
        is ChatItem.Notice -> Text(item.text, color = DshMuted, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 4.dp))
    }
}

@Composable
private fun UserMessageRow(item: ChatItem.User, onImage: (Bitmap) -> Unit) {
    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (item.text.isNotBlank()) {
            Text(
                item.text,
                color = DshText,
                fontSize = 14.sp,
                modifier = Modifier
                    .widthIn(max = 300.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(DshUserBubble)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            )
        }
        if (item.attachments.isNotEmpty()) MessageAttachments(item.attachments, onImage)
    }
}

@Composable
private fun MessageAttachments(attachments: List<ChatItem.ChatAttachment>, onImage: (Bitmap) -> Unit) {
    val (images, files) = attachments.partition { it.preview != null || it.data != null }
    if (images.isNotEmpty()) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.widthIn(max = 280.dp)) {
            images.take(3).forEach { attachment ->
                val bitmap = remember(attachment) { attachmentBitmap(attachment) }
                if (bitmap != null) {
                    Image(
                        bitmap.asImageBitmap(),
                        contentDescription = attachment.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(96.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .border(1.dp, DshBorder, RoundedCornerShape(16.dp))
                            .clickable { onImage(bitmap) },
                    )
                } else {
                    FileChip(attachment, Modifier.widthIn(max = 240.dp))
                }
            }
            if (images.size > 3) {
                Box(
                    Modifier.size(96.dp).clip(RoundedCornerShape(16.dp)).background(DshSurface2)
                        .border(1.dp, DshBorder, RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center,
                ) { Text("+${images.size - 3}", color = DshMuted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
            }
        }
    }
    files.forEach { attachment -> FileChip(attachment, Modifier.widthIn(max = 260.dp)) }
}

@Composable
private fun FileChip(attachment: ChatItem.ChatAttachment, modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(DshSurface)
            .border(1.dp, DshBorder, RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.InsertDriveFile, contentDescription = null, tint = DshTeal, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Column {
            Text(attachment.name, color = DshText, fontSize = 11.sp, maxLines = 1)
            attachment.bytes?.let { Text(formatBytes(it), color = DshMuted, fontSize = 9.sp) }
        }
    }
}

private fun attachmentBitmap(attachment: ChatItem.ChatAttachment): Bitmap? {
    attachment.preview?.let { return it }
    val data = attachment.data ?: return null
    return runCatching {
        val bytes = Base64.decode(data, Base64.NO_WRAP)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / sample > 640 || bounds.outHeight / sample > 640) sample *= 2
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
    }.getOrNull()
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1024 * 1024 -> String.format(Locale.US, "%.1f MB", bytes / 1024.0 / 1024.0)
    bytes >= 1024 -> "${bytes / 1024} KB"
    else -> "$bytes B"
}

@Composable
private fun AssistantMessageRow(item: ChatItem.Assistant, vm: NativeViewModel?, speaker: Speaker?) {
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    val baseKey = if (vm != null) "${vm.activeSessionId}:${item.key}" else item.key
    val bodyKey = "$baseKey:body"
    val bodyTranslation = vm?.translations?.get(bodyKey)
    val bodyText = bodyTranslation?.text ?: item.text
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            Modifier.size(28.dp).clip(CircleShape).background(DshSelected),
            contentAlignment = Alignment.Center,
        ) { Text("D", color = DshTeal, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
        Column(
            Modifier
                .weight(1f)
                .clip(RoundedCornerShape(18.dp))
                .background(DshSurface)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("DSH", color = DshTealSoft, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                if (item.streaming) {
                    Spacer(Modifier.width(8.dp))
                    StreamingDots()
                }
                if (bodyTranslation?.text != null) {
                    Spacer(Modifier.width(6.dp))
                    Text("已翻译", color = DshTeal, fontSize = 9.sp)
                }
                Spacer(Modifier.weight(1f))
                if (speaker != null && item.text.isNotBlank()) {
                    IconButton(
                        onClick = { speaker.toggle(baseKey, bodyText) },
                        modifier = Modifier.size(24.dp),
                    ) {
                        Icon(
                            if (speaker.speakingKey == baseKey) Icons.Filled.VolumeOff else Icons.Filled.VolumeUp,
                            contentDescription = if (speaker.speakingKey == baseKey) "停止朗读" else "朗读这条消息",
                            tint = if (speaker.speakingKey == baseKey) DshTeal else DshMuted,
                            modifier = Modifier.size(13.dp),
                        )
                    }
                }
                if (vm != null && item.text.isNotBlank()) {
                    IconButton(
                        onClick = {
                            if (bodyTranslation?.text != null) vm.clearTranslation(bodyKey) else vm.translate(bodyKey, item.text)
                        },
                        modifier = Modifier.size(24.dp),
                    ) {
                        Icon(
                            Icons.Filled.Translate,
                            contentDescription = if (bodyTranslation?.text != null) "查看原文" else "翻译这条消息",
                            tint = if (bodyTranslation?.text != null) DshTeal else DshMuted,
                            modifier = Modifier.size(13.dp),
                        )
                    }
                }
                IconButton(
                    onClick = {
                        clipboard.setText(AnnotatedString(bodyText))
                        copied = true
                    },
                    modifier = Modifier.size(24.dp),
                ) {
                    Icon(
                        if (copied) Icons.Filled.Check else Icons.Filled.ContentCopy,
                        contentDescription = "复制",
                        tint = DshMuted,
                        modifier = Modifier.size(13.dp),
                    )
                }
            }
            item.reasoning?.let { reasoning -> ThinkSection(baseKey, reasoning, vm) }
            if (bodyTranslation?.loading == true) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(12.dp), color = DshTeal, strokeWidth = 1.5.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("翻译中…", color = DshMuted, fontSize = 11.sp)
                }
            }
            if (bodyTranslation?.error != null) {
                Text("翻译失败：${bodyTranslation.error}", color = DshDanger, fontSize = 10.sp)
                TextButton(onClick = { vm?.retryTranslation(bodyKey, item.text) }) {
                    Text("重试", color = DshTeal, fontSize = 10.sp)
                }
            }
            if (bodyText.isNotBlank()) CollapsibleMarkdown(bodyText)
        }
    }
}

/**
 * Collapsed reasoning row. Expanding it requests a machine translation of the
 * reasoning text; the original stays one tap away.
 */
@Composable
private fun ThinkSection(key: String, reasoning: String, vm: NativeViewModel?) {
    var expanded by remember(key) { mutableStateOf(vm?.thinkExpanded ?: false) }
    var showOriginal by remember(key) { mutableStateOf(false) }
    LaunchedEffect(vm?.thinkExpanded) {
        vm?.let { expanded = it.thinkExpanded }
    }
    val translationKey = "$key:reason"
    val state = vm?.translations?.get(translationKey)
    LaunchedEffect(expanded) {
        if (expanded && vm != null) vm.translate(translationKey, reasoning)
    }
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(DshSurface2)
            .border(1.dp, DshBorder, RoundedCornerShape(12.dp)),
    ) {
        Row(
            Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Lightbulb, contentDescription = null, tint = DshMuted, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
            Text("思考", color = DshMuted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            Text(if (expanded) "收起" else "展开", color = DshMuted, fontSize = 10.sp)
        }
        if (expanded) {
            Column(Modifier.padding(start = 10.dp, end = 10.dp, bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                when {
                    state?.loading == true -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(12.dp), color = DshTeal, strokeWidth = 1.5.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("翻译中…", color = DshMuted, fontSize = 11.sp)
                    }
                    state?.text != null -> Text(if (showOriginal) reasoning else state.text, color = DshMuted, fontSize = 12.sp, lineHeight = 18.sp)
                    else -> Text(reasoning, color = DshMuted, fontSize = 12.sp, lineHeight = 18.sp)
                }
                if (state?.error != null) {
                    Text("翻译失败：${state.error}", color = DshDanger, fontSize = 10.sp)
                    TextButton(onClick = { vm?.retryTranslation(translationKey, reasoning) }) {
                        Text("重试", color = DshTeal, fontSize = 10.sp)
                    }
                }
                if (state?.text != null) {
                    TextButton(onClick = { showOriginal = !showOriginal }) {
                        Text(if (showOriginal) "查看翻译" else "查看原文", color = DshTeal, fontSize = 10.sp)
                    }
                }
            }
        }
    }
}

/** Very long assistant output is clipped until the reader asks for more. */
@Composable
private fun CollapsibleMarkdown(text: String) {
    var expanded by remember(text) { mutableStateOf(false) }
    val long = text.length > 1200
    Column {
        Box(
            Modifier.then(
                if (long && !expanded) Modifier.heightIn(max = 420.dp).clipToBounds() else Modifier.fillMaxWidth(),
            ),
        ) {
            MarkdownText(text)
        }
        if (long) {
            TextButton(onClick = { expanded = !expanded }) {
                Text(if (expanded) "收起" else "展开全文", color = DshTeal, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun StreamingDots() {
    val transition = rememberInfiniteTransition(label = "dots")
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(3) { index ->
            val alpha by transition.animateFloat(
                initialValue = 0.25f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(600, delayMillis = index * 160),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "dot$index",
            )
            Box(Modifier.size(5.dp).clip(CircleShape).background(DshTeal.copy(alpha = alpha)))
        }
    }
}

@Composable
private fun EmptyChatState() {
    Column(
        Modifier.fillMaxWidth().padding(top = 120.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            Modifier.size(54.dp).clip(CircleShape).background(DshSurface2),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Filled.Terminal, contentDescription = null, tint = DshTeal, modifier = Modifier.size(24.dp)) }
        Text("开始新的对话", color = DshText, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Text("输入消息，或用 + 添加图片 / 文件 / 拍照", color = DshMuted, fontSize = 11.sp)
    }
}

@Composable
private fun TaskDetailScreen(vm: NativeViewModel) {
    val state = remember(vm.messages.size, vm.activeSessionId) { parseTaskState(vm.messages) }
    val tools = remember(vm.messages.size) { vm.messages.filterIsInstance<ChatItem.Tool>().takeLast(6) }
    val artifacts = remember(vm.messages.size) {
        vm.messages.filterIsInstance<ChatItem.Tool>()
            .filter { it.name in setOf("write", "edit", "apply_patch", "notebook_edit") }
            .mapNotNull { tool ->
                runCatching {
                    val args = org.json.JSONObject(tool.arguments)
                    args.optString("path").ifBlank { args.optString("file_path") }
                }.getOrNull()?.takeIf { it.isNotBlank() }
            }
            .distinct()
            .takeLast(6)
    }
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { vm.closeTask() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = DshText.copy(alpha = 0.8f))
            }
            Column(Modifier.weight(1f)) {
                Text(vm.taskTitle.ifBlank { "任务" }, color = DshText, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                Text(vm.activeCwd?.takeIf { it.isNotBlank() } ?: (if (vm.taskRunning) "运行中" else "已结束"), color = DshMuted, fontSize = 10.5.sp, maxLines = 1)
            }
            if (vm.taskRunning) {
                Text(
                    "运行中",
                    color = DshTeal,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.background(DshSurface2, RoundedCornerShape(9.dp)).padding(horizontal = 10.dp, vertical = 5.dp),
                )
            }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            state.first?.let { goal ->
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(DshSurface).padding(14.dp)) {
                    Text("目标", color = DshTeal, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                    Text(goal, color = DshText, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.padding(top = 4.dp))
                }
            }
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(DshSurface).padding(14.dp)) {
                Text("待办", color = DshMuted, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                if (state.second.isEmpty()) {
                    Text("这个任务还没有 todo（Agent 调用 todo_write 后会出现）", color = DshMuted, fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp))
                } else {
                    state.second.forEach { (content, status) ->
                        val done = status == "completed"
                        val doing = status == "in_progress"
                        Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                when {
                                    done -> Icons.Filled.CheckCircle
                                    doing -> Icons.Filled.PlayArrow
                                    else -> Icons.Filled.RadioButtonUnchecked
                                },
                                contentDescription = null,
                                tint = when {
                                    done -> DshTeal
                                    doing -> DshTeal
                                    else -> DshMuted
                                },
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(content, color = if (done) DshMuted else DshText, fontSize = 13.sp)
                        }
                    }
                }
            }
            if (vm.pendingQuestions.isNotEmpty()) {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(DshErrorBg)
                        .border(1.dp, DshErrorBorder, RoundedCornerShape(16.dp))
                        .clickable { vm.openSession(vm.sessions.first { it.id == vm.activeSessionId }) }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.ErrorOutline, contentDescription = null, tint = DshDanger, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("等待你处理", color = DshText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text("Agent 正在等待回答或批准", color = DshMuted, fontSize = 11.sp)
                    }
                    Text("去处理", color = DshTeal, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }
            }
            Text("时间线", color = DshMuted, fontSize = 11.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 6.dp))
            if (tools.isEmpty()) {
                Text("暂无工具调用", color = DshMuted, fontSize = 11.sp)
            } else {
                tools.forEach { tool ->
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(DshSurface).padding(horizontal = 14.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(6.dp).clip(CircleShape).background(if (tool.isError) DshDanger else DshTeal))
                        Spacer(Modifier.width(8.dp))
                        Text(tool.name, color = DshText, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            (if (tool.at > 0L) "${formatTime(tool.at)} · " else "") + toolSummary(tool.arguments),
                            color = DshMuted,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f).padding(start = 8.dp),
                        )
                        Text(if (tool.result != null) "完成" else "执行中", color = DshMuted, fontSize = 10.sp)
                    }
                }
            }
            Text("产物", color = DshMuted, fontSize = 11.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 6.dp))
            if (artifacts.isEmpty()) {
                Text("还没有写入产物（write / edit 后会出现）", color = DshMuted, fontSize = 11.sp)
            } else {
                artifacts.forEach { path ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(DshSurface).padding(horizontal = 14.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.Edit, contentDescription = null, tint = DshTeal, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(path, color = DshText, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                "进入会话",
                color = DshTeal,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.clip(RoundedCornerShape(14.dp)).background(DshSurface).clickable {
                    vm.sessions.firstOrNull { it.id == vm.activeSessionId }?.let { vm.openSession(it) }
                }.padding(horizontal = 18.dp, vertical = 11.dp),
            )
            Text(
                "刷新",
                color = DshOnAccent,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(DshTeal).clickable {
                    vm.reloadTask()
                }.padding(vertical = 11.dp),
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun DayDivider(label: String) {
    Text(
        label,
        color = DshMuted,
        fontSize = 10.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
    )
}

private fun toolSummary(arguments: String): String {
    val parsed = runCatching { org.json.JSONObject(arguments) }.getOrNull() ?: return arguments.lineSequence().first().take(80)
    for (key in listOf("command", "path", "file_path", "query", "pattern", "url", "description")) {
        val value = parsed.optString(key)
        if (value.isNotBlank()) return value.lineSequence().first().take(80)
    }
    return arguments.lineSequence().first().take(80)
}

private fun sameDay(first: Long, second: Long): Boolean {
    if (first <= 0L || second <= 0L) return false
    val a = Calendar.getInstance().apply { timeInMillis = first }
    val b = Calendar.getInstance().apply { timeInMillis = second }
    return a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
}

private fun dayLabel(time: Long): String {
    if (time <= 0L) return ""
    val now = Calendar.getInstance()
    val then = Calendar.getInstance().apply { timeInMillis = time }
    if (now.get(Calendar.YEAR) == then.get(Calendar.YEAR) && now.get(Calendar.DAY_OF_YEAR) == then.get(Calendar.DAY_OF_YEAR)) {
        return "今天"
    }
    val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
    if (yesterday.get(Calendar.YEAR) == then.get(Calendar.YEAR) && yesterday.get(Calendar.DAY_OF_YEAR) == then.get(Calendar.DAY_OF_YEAR)) {
        return "昨天"
    }
    return java.text.SimpleDateFormat("M月d日", java.util.Locale.getDefault()).format(java.util.Date(time))
}

@Composable
private fun ChatSkeleton() {
    Column(Modifier.fillMaxWidth().padding(top = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        repeat(3) { index ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = if (index % 2 == 0) Arrangement.Start else Arrangement.End,
            ) {
                Box(
                    Modifier.height(if (index == 1) 96.dp else 60.dp)
                        .fillMaxWidth(if (index == 1) 0.9f else 0.62f)
                        .clip(RoundedCornerShape(18.dp))
                        .background(DshSurface2),
                )
            }
        }
        Text(
            "正在加载会话…",
            color = DshMuted,
            fontSize = 11.sp,
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 6.dp),
        )
    }
}

@Composable
private fun ImagePreviewDialog(bitmap: Bitmap, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.92f)).clickable { onDismiss() },
            contentAlignment = Alignment.Center,
        ) {
            Image(
                bitmap.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun PendingAttachmentTray(vm: NativeViewModel) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 10.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        vm.pendingAttachments.forEach { pending ->
            PendingAttachmentCard(
                pending = pending,
                onRemove = { vm.removeAttachment(pending.id) },
                onRetry = { vm.retryAttachment(pending.id) },
            )
        }
    }
}

@Composable
private fun PendingAttachmentCard(
    pending: NativeViewModel.PendingAttachment,
    onRemove: () -> Unit,
    onRetry: () -> Unit,
) {
    val failed = pending.status == NativeViewModel.AttachmentStatus.FAILED
    val uploading = pending.status == NativeViewModel.AttachmentStatus.UPLOADING
    Column(
        Modifier
            .width(176.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(DshSurface)
            .border(1.dp, if (failed) DshDanger.copy(alpha = 0.55f) else DshBorder, RoundedCornerShape(14.dp))
            .padding(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(DshSurface2),
                contentAlignment = Alignment.Center,
            ) {
                val preview = pending.preview
                if (preview != null) {
                    Image(preview.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                } else {
                    Icon(Icons.Filled.InsertDriveFile, contentDescription = null, tint = DshTeal, modifier = Modifier.size(18.dp))
                }
            }
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(pending.name, color = DshText, fontSize = 11.sp, maxLines = 1)
                Text(
                    when {
                        failed -> pending.error ?: "上传失败"
                        uploading -> "上传中 ${pending.progress}%"
                        else -> formatBytes(pending.size)
                    },
                    color = if (failed) DshDanger else DshMuted,
                    fontSize = 9.5.sp,
                    maxLines = 1,
                )
            }
            IconButton(onClick = onRemove, modifier = Modifier.size(20.dp)) {
                Icon(Icons.Filled.Close, contentDescription = "移除", tint = DshMuted, modifier = Modifier.size(13.dp))
            }
        }
        if (uploading) {
            LinearProgressIndicator(
                progress = { pending.progress / 100f },
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp).height(3.dp),
                color = DshTeal,
                trackColor = DshSurface2,
            )
        }
        if (failed) {
            TextButton(onClick = onRetry, modifier = Modifier.align(Alignment.End).padding(top = 2.dp)) {
                Text("重试", color = DshTeal, fontSize = 10.sp)
            }
        }
    }
}

@Composable
private fun AttachSheet(onImage: () -> Unit, onCamera: () -> Unit, onFile: () -> Unit, onClose: () -> Unit) {
    Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("添加附件", color = DshText, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(8.dp))
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onClose) { Icon(Icons.Filled.Close, contentDescription = "关闭", tint = DshMuted) }
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AttachTile("相册", Icons.Filled.AddPhotoAlternate, onImage, Modifier.weight(1f))
            AttachTile("拍照", Icons.Filled.PhotoCamera, onCamera, Modifier.weight(1f))
            AttachTile("文件", Icons.Filled.InsertDriveFile, onFile, Modifier.weight(1f))
        }
        Text(
            "图片自动压缩到长边 1600px · 单个文件 ≤ 20 MB · 最多 5 个",
            color = DshMuted,
            fontSize = 10.sp,
            modifier = Modifier.padding(top = 14.dp, bottom = 6.dp),
        )
    }
}

@Composable
private fun AttachTile(label: String, icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(DshSurface2)
            .border(1.dp, DshBorder, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(DshSelected),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, contentDescription = null, tint = DshTeal, modifier = Modifier.size(20.dp)) }
        Text(label, color = DshText, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

private data class MarkdownBlock(val code: Boolean, val text: String)

private fun markdownBlocks(source: String): List<MarkdownBlock> {
    val blocks = mutableListOf<MarkdownBlock>()
    val prose = StringBuilder()
    val code = StringBuilder()
    var inCode = false
    fun flushProse() {
        if (prose.isNotBlank()) blocks.add(MarkdownBlock(false, prose.toString().trim()))
        prose.clear()
    }
    fun flushCode() {
        if (code.isNotBlank()) blocks.add(MarkdownBlock(true, code.toString().trimEnd()))
        code.clear()
    }
    source.lines().forEach { line ->
        if (line.trimStart().startsWith("```")) {
            if (inCode) { flushCode(); inCode = false } else { flushProse(); inCode = true }
        } else if (inCode) {
            code.appendLine(line)
        } else {
            prose.appendLine(line)
        }
    }
    if (inCode) flushCode() else flushProse()
    return blocks
}

@Composable
private fun MarkdownText(text: String) {
    val blocks = remember(text) { markdownBlocks(text) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 6.dp)) {
        blocks.forEach { block ->
            if (block.code) {
                Box(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(DshSurface2)
                        .border(1.dp, DshBorder, RoundedCornerShape(12.dp)).padding(10.dp),
                ) {
                    Text(
                        block.text,
                        color = DshText,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                    )
                }
            } else {
                Text(
                    buildAnnotatedString {
                        block.text.lines().forEachIndexed { index, raw ->
                            val line = raw.trimEnd()
                            val heading = line.startsWith("# ")
                            val subheading = line.startsWith("## ") || line.startsWith("### ")
                            val bullet = line.startsWith("- ") || line.startsWith("* ")
                            val value = when {
                                heading -> line.removePrefix("# ")
                                subheading -> line.removePrefix("## ").removePrefix("### ")
                                bullet -> "• ${line.drop(2)}"
                                else -> line
                            }
                            if (heading) pushStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = 17.sp))
                            else if (subheading) pushStyle(SpanStyle(fontWeight = FontWeight.SemiBold, fontSize = 15.sp))
                            append(value)
                            if (heading || subheading) pop()
                            if (index != block.text.lines().lastIndex) append("\n")
                        }
                    },
                    color = DshText,
                    fontSize = 14.sp,
                )
            }
        }
    }
}

@Composable
private fun ToolCard(tool: ChatItem.Tool) {
    var expanded by remember { mutableStateOf(false) }
    val tint = toolTint(tool.name)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(DshSurface)
            .clickable { expanded = !expanded }
            .padding(horizontal = 14.dp, vertical = 11.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(6.dp).clip(CircleShape).background(tint))
            Spacer(Modifier.width(6.dp))
            Text("工具", color = tint, fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.width(6.dp))
            Text(tool.name, color = DshText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            Text(if (tool.result == null) "执行中…" else if (tool.isError) "失败" else "完成", color = if (tool.isError) DshDanger else DshMuted, fontSize = 9.sp)
        }
        toolDetail(tool)?.let { detail ->
            Text(detail, color = DshMuted, fontFamily = FontFamily.Monospace, fontSize = 10.sp, maxLines = 1, modifier = Modifier.padding(top = 4.dp))
        }
        if (expanded) {
            if (tool.arguments.isNotBlank()) {
                Text("参数", color = DshMuted, fontSize = 9.sp, modifier = Modifier.padding(top = 8.dp))
                Text(tool.arguments, color = DshText, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
            }
            tool.result?.let {
                Text("结果", color = DshMuted, fontSize = 9.sp, modifier = Modifier.padding(top = 8.dp))
                Text(it, color = DshText, fontFamily = FontFamily.Monospace, fontSize = 10.sp, maxLines = 20)
            }
        }
    }
}

private fun toolTint(name: String): Color {
    val light = DshTheme.colors.light
    return when (name) {
        "bash" -> if (light) Color(0xFF4F46E5) else Color(0xFF5EEAD4)
        "read", "write", "edit" -> if (light) Color(0xFFB45309) else Color(0xFFFBBF24)
        "grep", "glob", "search" -> if (light) Color(0xFFC2410C) else Color(0xFFFB923C)
        "subagent" -> if (light) Color(0xFF7C3AED) else Color(0xFFC084FC)
        else -> DshTeal
    }
}

private fun toolDetail(tool: ChatItem.Tool): String? {
    val args = runCatching { org.json.JSONObject(tool.arguments) }.getOrNull() ?: return null
    val detail = when (tool.name) {
        "bash" -> args.optString("command")
        "read", "write", "edit" -> args.optString("path")
        "grep", "glob" -> args.optString("pattern")
        "subagent" -> args.optString("description").ifEmpty { args.optString("prompt") }
        else -> ""
    }
    if (detail.isBlank()) return null
    return detail.lineSequence().firstOrNull()?.take(120)
}

@Composable
private fun TopBar(
    title: String,
    subtitle: String,
    onBack: () -> Unit,
    backIcon: androidx.compose.ui.graphics.vector.ImageVector,
    actions: @Composable () -> Unit = {},
) {
    Row(
        Modifier.fillMaxWidth().height(58.dp).padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) { Icon(backIcon, contentDescription = "返回", tint = DshText.copy(alpha = 0.8f)) }
        Column(Modifier.weight(1f)) {
            Text(title, color = DshText, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text(subtitle, color = DshMuted, fontSize = 10.5.sp)
        }
        actions()
    }
}

@Composable
private fun ErrorBar(message: String, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(DshErrorBg)
            .border(1.dp, DshErrorBorder, RoundedCornerShape(16.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(message, color = DshDanger, fontSize = 11.sp, modifier = Modifier.weight(1f))
        TextButton(onClick = onDismiss) { Text("关闭", color = DshMuted, fontSize = 11.sp) }
    }
}

private fun parsePairPayload(raw: String): Pair<String, String>? {
    val uri = android.net.Uri.parse(raw.trim())
    val scheme = uri.scheme
    val authority = uri.authority
    val queryCode = uri.getQueryParameter("code")
    if (scheme != null && authority != null && !queryCode.isNullOrBlank()) {
        val path = uri.path ?: ""
        var base = "$scheme://$authority/"
        when {
            path.endsWith("/pair") -> base = "$scheme://$authority${path.dropLast(4)}"
            path.endsWith("/pair/") -> base = "$scheme://$authority${path.dropLast(5)}"
            path.contains('/') -> base = "$scheme://$authority${path.substringBeforeLast('/')}/"
        }
        if (!base.endsWith("/")) base += "/"
        val code = queryCode.uppercase().replace(Regex("[^A-Z0-9]"), "")
        if (code.length == 8) return base to code
    }
    val typed = raw.trim().uppercase().replace(Regex("[^A-Z0-9]"), "")
    return if (typed.length == 8) "" to typed else null
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = DshTeal,
    unfocusedBorderColor = DshBorder,
    focusedTextColor = DshText,
    unfocusedTextColor = DshText,
    focusedLabelColor = DshTealSoft,
    unfocusedLabelColor = DshMuted,
    cursorColor = DshTeal,
    focusedContainerColor = DshSurface2,
    unfocusedContainerColor = DshSurface2,
)

private fun dateGroup(epochMs: Long): String {
    val now = java.util.Calendar.getInstance()
    val then = java.util.Calendar.getInstance().apply { timeInMillis = epochMs }
    val sameDay = now.get(Calendar.YEAR) == then.get(Calendar.YEAR) &&
        now.get(Calendar.DAY_OF_YEAR) == then.get(Calendar.DAY_OF_YEAR)
    if (sameDay) return "今天"
    val yesterday = java.util.Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
    if (yesterday.get(Calendar.YEAR) == then.get(Calendar.YEAR) &&
        yesterday.get(Calendar.DAY_OF_YEAR) == then.get(Calendar.DAY_OF_YEAR)
    ) return "昨天"
    return "更早"
}

private fun formatTime(epochMs: Long): String {
    if (epochMs <= 0) return ""
    val format = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
    return format.format(Date(epochMs))
}

@Composable
private fun FilesScreen(vm: NativeViewModel) {
    Column(Modifier.fillMaxSize()) {
        TopBar(
            title = if (vm.filePath.isEmpty()) "文件" else vm.filePath,
            subtitle = "当前会话 · ${vm.files.size} 项",
            onBack = { vm.filesUp() },
            backIcon = Icons.AutoMirrored.Filled.ArrowBack,
        )
        if (vm.busy) {
            Box(Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = DshTeal)
            }
        }
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(vm.files, key = { it.name }) { entry ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(DshSurface)
                        .clickable { vm.openFile(entry) }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        if (entry.type == "directory") Icons.Filled.Folder else Icons.Filled.InsertDriveFile,
                        contentDescription = null,
                        tint = if (entry.type == "directory") DshTealSoft else DshMuted,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(entry.name, color = DshText, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    entry.size?.let { Text("$it B", color = DshMuted, fontSize = 9.sp) }
                }
            }
        }
    }
    vm.filePreview?.let { preview ->
        AlertDialog(
            onDismissRequest = { vm.dismissFilePreview() },
            title = { Text(preview.name, color = DshText, fontSize = 15.sp) },
            text = {
                if (preview.bitmap != null) {
                    Image(
                        preview.bitmap.asImageBitmap(),
                        contentDescription = preview.name,
                        modifier = Modifier.fillMaxWidth().heightIn(max = 480.dp),
                        contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                    )
                } else {
                    Text(
                        preview.text.orEmpty().ifBlank { "（空文件）" },
                        color = DshText,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        modifier = Modifier.verticalScroll(rememberScrollState()).height(400.dp),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { vm.dismissFilePreview() }) { Text("关闭", color = DshTealSoft) }
            },
            containerColor = DshSurface,
        )
    }
}

@SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
@Composable
private fun TerminalScreen(vm: NativeViewModel) {
    var ready by remember { mutableStateOf(false) }
    var written by remember { mutableIntStateOf(0) }
    var webView by remember { mutableStateOf<WebView?>(null) }

    Column(Modifier.fillMaxSize()) {
        TopBar(
            title = vm.terminalTitle,
            subtitle = "WebView · xterm",
            onBack = { vm.closeTerminal() },
            backIcon = Icons.AutoMirrored.Filled.ArrowBack,
        )
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                WebView(context).apply {
                    setBackgroundColor(android.graphics.Color.parseColor("#070D18"))
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.allowFileAccess = true
                    settings.allowContentAccess = true
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView, url: String) {
                            ready = true
                            written = 0
                        }
                    }
                    addJavascriptInterface(object {
                        @JavascriptInterface
                        fun send(data: String) {
                            vm.sendTerminalInput(data)
                        }

                        @JavascriptInterface
                        fun resize(cols: Int, rows: Int) {
                            vm.resizeTerminal(cols, rows)
                        }
                    }, "AndroidTerminal")
                    loadUrl("file:///android_asset/terminal.html")
                    webView = this
                }
            },
        )
    }

    LaunchedEffect(ready, vm.terminalChunks.size) {
        val view = webView ?: return@LaunchedEffect
        if (!ready) return@LaunchedEffect
        if (vm.terminalChunks.size < written) {
            written = 0
            view.evaluateJavascript("window.termReset && window.termReset()", null)
        }
        while (written < vm.terminalChunks.size) {
            val text = vm.terminalChunks[written]
            written++
            view.evaluateJavascript("window.termWrite && window.termWrite(${org.json.JSONObject.quote(text)})", null)
        }
    }
}


/** Screenshot-only sessions composition with deterministic data. */
@Composable
internal fun SessionsPreviewContent() {
    Column(Modifier.fillMaxSize().background(DshBackgroundBrush)) {
        TopBar("公司 DSH", "已连接 · 4 个会话", {}, Icons.Filled.Menu) {}
        OutlinedTextField(
            value = "",
            onValueChange = {},
            placeholder = { Text("搜索标题、ID 或目录", color = DshMuted, fontSize = 12.sp) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = DshMuted, modifier = Modifier.size(18.dp)) },
            singleLine = true,
            colors = fieldColors(),
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        )
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { Text("今天", color = DshMuted, fontSize = 10.sp, letterSpacing = 1.sp, modifier = Modifier.padding(top = 10.dp, bottom = 2.dp)) }
            items(
                listOf(
                    SessionSummary("session-4f8a2c1d-phone", System.currentTimeMillis() - 120_000, true, false, "/home/ubuntu/workspace", "检查未提交改动"),
                    SessionSummary("session-a91b73e2-api", System.currentTimeMillis() - 3_600_000, false, false, "/home/ubuntu/workspace/api", "API 鉴权重构"),
                ),
            ) { session -> SessionRow(session, active = session.title == "检查未提交改动") {} }
            item { Text("昨天", color = DshMuted, fontSize = 10.sp, letterSpacing = 1.sp, modifier = Modifier.padding(top = 10.dp, bottom = 2.dp)) }
            items(
                listOf(
                    SessionSummary("session-77c0d5aa-mobile", System.currentTimeMillis() - 86_400_000, false, false, null, "原生客户端联调"),
                    SessionSummary("session-1e6b90ff-docs", System.currentTimeMillis() - 3 * 86_400_000, false, true, null, "插件文档整理"),
                ),
            ) { session -> SessionRow(session, active = session.title == "检查未提交改动") {} }
        }
    }
}

/** Screenshot-only chat composition with deterministic messages, attachments and tool card. */
@Composable
internal fun ChatPreviewContent() {
    val now = System.currentTimeMillis()
    val previewItems = listOf(
        ChatItem.User(
            "u1",
            "帮我看下这张截图和日志，顺便确认改动范围",
            listOf(
                ChatItem.ChatAttachment(
                    "screenshot.png",
                    "image/png",
                    preview = sampleImage(0xFF4F46E5.toInt(), 0xFF22D3EE.toInt()),
                ),
                ChatItem.ChatAttachment("app.log", receiptId = "r1", bytes = 182_432),
            ),
            now - 86_400_000L,
        ),
        ChatItem.Assistant(
            "a1",
            "我先看一下 git 状态和最近的 diff：\n```\ngit status --short\n```",
            reasoning = "We need to inspect the working tree before answering. Run git status --short, then summarize staged and unstaged changes for the user.",
            streaming = true,
            at = now - 86_340_000L,
        ),
        ChatItem.Tool(
            "t1",
            "call_1",
            "bash",
            "{\n  \"command\": \"git status --short\"\n}",
            " M app/src/main/java/com/ila3/dshnative/ui/NativeApp.kt\n?? docs/",
            false,
            at = now - 3_600_000L,
        ),
        ChatItem.Assistant("a2", "有两个改动：NativeApp.kt 已修改，docs/ 是新目录。需要我继续生成 diff 吗？", at = now - 3_540_000L),
    )
    Column(Modifier.fillMaxSize().background(DshBackgroundBrush)) {
        TopBar("检查未提交改动", "已连接 · DeepSeek V4", {}, Icons.AutoMirrored.Filled.ArrowBack) {}
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            itemsIndexed(previewItems) { index, item ->
                val previous = previewItems.getOrNull(index - 1)
                val freshDay = item.at > 0L && (previous == null || previous.at <= 0L || !sameDay(previous.at, item.at))
                Column {
                    if (freshDay) DayDivider(dayLabel(item.at))
                    MessageRow(item)
                }
            }
        }
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp)
                .clip(RoundedCornerShape(16.dp)).background(DshSurface2)
                .border(1.dp, DshBorder, RoundedCornerShape(16.dp))
                .padding(horizontal = 10.dp, vertical = 8.dp),
        ) {
            Text("1 条排队消息", color = DshMuted, fontSize = 9.sp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("继续生成 diff", color = DshText, fontSize = 11.sp, modifier = Modifier.weight(1f))
                Text("转向", color = DshTealSoft, fontSize = 10.sp)
                Spacer(Modifier.width(10.dp))
                Icon(Icons.Filled.Close, contentDescription = null, tint = DshMuted, modifier = Modifier.size(16.dp))
            }
        }
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 10.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PreviewAttachmentCard("screenshot.png", "上传中 62%", progress = 62, failed = false)
            PreviewAttachmentCard("design.pdf", "1.4 MB", progress = null, failed = false)
            PreviewAttachmentCard("build.log", "上传失败 · 网络错误", progress = null, failed = true)
        }
        Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.Bottom) {
            Box(Modifier.size(42.dp).clip(CircleShape).background(DshSurface2), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Add, contentDescription = null, tint = DshTeal, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(8.dp))
            OutlinedTextField(
                value = "",
                onValueChange = {},
                placeholder = { Text("跟进…", color = DshMuted, fontSize = 13.sp) },
                colors = fieldColors(),
                shape = RoundedCornerShape(22.dp),
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            Box(Modifier.clip(CircleShape).background(DshMuted.copy(alpha = 0.32f)).size(46.dp), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.ArrowUpward, contentDescription = null, tint = DshOnAccent)
            }
        }
    }
}

/** Screenshot-only attach sheet composition. */
@Composable
internal fun AttachSheetPreviewContent() {
    Column(Modifier.fillMaxSize().background(DshBackgroundBrush)) {
        Spacer(Modifier.weight(1f))
        Column(
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .background(DshSurface),
        ) {
            AttachSheet({}, {}, {}, {})
        }
    }
}

private fun sampleImage(start: Int, end: Int): Bitmap {
    val width = 480
    val height = 320
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)
    val paint = android.graphics.Paint()
    paint.shader = android.graphics.LinearGradient(
        0f,
        0f,
        width.toFloat(),
        height.toFloat(),
        start,
        end,
        android.graphics.Shader.TileMode.CLAMP,
    )
    canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
    return bitmap
}

@Composable
private fun PreviewAttachmentCard(name: String, detail: String, progress: Int?, failed: Boolean) {
    Column(
        Modifier.width(176.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(DshSurface)
            .border(1.dp, if (failed) DshDanger.copy(alpha = 0.55f) else DshBorder, RoundedCornerShape(14.dp))
            .padding(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(DshSurface2), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.InsertDriveFile, contentDescription = null, tint = DshTeal, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(name, color = DshText, fontSize = 11.sp, maxLines = 1)
                Text(detail, color = if (failed) DshDanger else DshMuted, fontSize = 9.5.sp, maxLines = 1)
            }
            Icon(Icons.Filled.Close, contentDescription = null, tint = DshMuted, modifier = Modifier.size(13.dp))
        }
        if (progress != null) {
            LinearProgressIndicator(
                progress = { progress / 100f },
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp).height(3.dp),
                color = DshTeal,
                trackColor = DshSurface2,
            )
        }
        if (failed) Text("重试", color = DshTeal, fontSize = 10.sp, modifier = Modifier.align(Alignment.End).padding(top = 2.dp))
    }
}

@Composable
private fun SettingsScreen(vm: NativeViewModel) {
    val context = LocalContext.current
    SettingsContent(
        profileName = vm.currentProfile?.name ?: "未绑定",
        profileUrl = vm.currentProfile?.url ?: "",
        connected = vm.connection,
        mode = DshTheme.mode,
        onMode = { mode ->
            DshTheme.mode = mode
            context.getSharedPreferences("dsh_native", android.content.Context.MODE_PRIVATE)
                .edit().putString("theme_mode", if (mode == DshThemeMode.PAPER) "paper" else "aurora").apply()
        },
        onServers = { vm.showServers() },
        onBack = { vm.closeSettings() },
        version = com.ila3.dshnative.BuildConfig.VERSION_NAME,
        updateLabel = if (vm.updateChecking) "检查中…" else "当前 ${com.ila3.dshnative.BuildConfig.VERSION_NAME}",
        onCheckUpdate = { vm.checkUpdate() },
        thinkExpanded = vm.thinkExpanded,
        onThinkExpanded = { vm.updateThinkExpanded(it) },
        notifyEnabled = vm.notifyEnabled,
        onNotify = { vm.updateNotifyEnabled(it) },
        keepAlive = vm.keepAliveEnabled,
        onKeepAlive = { vm.updateKeepAlive(it) },
        onNavSettings = { vm.openNavSettings() },
    )
    vm.updateInfo?.let { UpdateDialog(vm, context) }
}

@Composable
internal fun SettingsTab(vm: NativeViewModel) {
    val context = LocalContext.current
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp)) {
            Column {
                Text(
                    "设置",
                    color = DshText,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.6).sp,
                )
                Text(
                    "${vm.currentProfile?.name ?: "未绑定"} · ${vm.connection}",
                    color = DshMuted,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        SettingsContent(
            profileName = vm.currentProfile?.name ?: "未绑定",
            profileUrl = vm.currentProfile?.url ?: "",
            connected = vm.connection,
            mode = DshTheme.mode,
            onMode = { mode ->
                DshTheme.mode = mode
                context.getSharedPreferences("dsh_native", android.content.Context.MODE_PRIVATE)
                    .edit().putString("theme_mode", if (mode == DshThemeMode.PAPER) "paper" else "aurora").apply()
            },
            onServers = { vm.showServers() },
            onBack = {},
            version = com.ila3.dshnative.BuildConfig.VERSION_NAME,
            updateLabel = if (vm.updateChecking) "检查中…" else "当前 ${com.ila3.dshnative.BuildConfig.VERSION_NAME}",
            onCheckUpdate = { vm.checkUpdate() },
            thinkExpanded = vm.thinkExpanded,
            onThinkExpanded = { vm.updateThinkExpanded(it) },
            notifyEnabled = vm.notifyEnabled,
            onNotify = { vm.updateNotifyEnabled(it) },
            keepAlive = vm.keepAliveEnabled,
            onKeepAlive = { vm.updateKeepAlive(it) },
            onNavSettings = { vm.openNavSettings() },
            showTopBar = false,
        )
    }
    vm.updateInfo?.let { UpdateDialog(vm, context) }
}

@Composable
private fun UpdateDialog(vm: NativeViewModel, context: android.content.Context) {
    val update = vm.updateInfo ?: return
    AlertDialog(
        onDismissRequest = { vm.dismissUpdate() },
        title = { Text("发现新版本 ${update.versionName}", color = DshText, fontSize = 17.sp) },
        text = {
            Column {
                Text(update.notes.ifBlank { "包含体验改进与问题修复。" }, color = DshText, fontSize = 13.sp)
                Text("当前 ${com.ila3.dshnative.BuildConfig.VERSION_NAME} → ${update.versionName}", color = DshMuted, fontSize = 10.sp, modifier = Modifier.padding(top = 8.dp))
                if (vm.updateDownloading) {
                    LinearProgressIndicator(
                        progress = vm.updateProgress / 100f,
                        color = DshTeal,
                        trackColor = DshSurface2,
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    )
                    Text("${vm.updateProgress}%", color = DshMuted, fontSize = 10.sp, modifier = Modifier.padding(top = 4.dp))
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !vm.updateDownloading,
                onClick = { vm.downloadUpdate { file -> vm.installApk(context, file) } },
            ) {
                Text(if (vm.updateDownloading) "下载中…" else "下载并安装", color = DshTeal)
            }
        },
        dismissButton = { TextButton(onClick = { vm.dismissUpdate() }) { Text("稍后", color = DshMuted) } },
        containerColor = DshSurface,
    )
}

@Composable
internal fun SettingsPreviewContent() {
    SettingsContent(
        profileName = "tony",
        profileUrl = "https://harness.ila3.net/phone/",
        connected = "已连接 · 局域网 17ms",
        mode = DshThemeMode.PAPER,
        onMode = {},
        onServers = {},
        onBack = {},
        version = "0.19.2",
        updateLabel = "当前 0.19.2",
        onCheckUpdate = {},
        thinkExpanded = true,
        onThinkExpanded = {},
        notifyEnabled = true,
        onNotify = {},
        keepAlive = false,
        onKeepAlive = {},
        onNavSettings = {},
    )
}

@Composable
private fun SettingsContent(
    profileName: String,
    profileUrl: String,
    connected: String,
    mode: DshThemeMode,
    onMode: (DshThemeMode) -> Unit,
    onServers: () -> Unit,
    onBack: () -> Unit,
    version: String,
    updateLabel: String,
    onCheckUpdate: () -> Unit,
    thinkExpanded: Boolean,
    onThinkExpanded: (Boolean) -> Unit,
    notifyEnabled: Boolean,
    onNotify: (Boolean) -> Unit,
    keepAlive: Boolean,
    onKeepAlive: (Boolean) -> Unit,
    onNavSettings: () -> Unit = {},
    showTopBar: Boolean = true,
) {
    Column(Modifier.fillMaxSize()) {
        if (showTopBar) TopBar("设置", profileName, onBack, Icons.AutoMirrored.Filled.ArrowBack)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            SettingsGroup("连接") {
                SettingsActionRow(Icons.Filled.Chat, profileName, profileUrl, onClick = onServers)
                SettingsDivider()
                SettingsActionRow(Icons.Filled.Add, "切换 / 添加 DSH", connected, onClick = onServers)
            }
            SettingsGroup("外观") {
                SettingsActionRow(
                    Icons.Filled.LightMode,
                    "云白",
                    "清爽蓝紫",
                    selected = mode == DshThemeMode.PAPER,
                    onClick = { onMode(DshThemeMode.PAPER) },
                )
                SettingsDivider()
                SettingsActionRow(
                    Icons.Filled.DarkMode,
                    "深海",
                    "暗色青绿",
                    selected = mode == DshThemeMode.AURORA,
                    onClick = { onMode(DshThemeMode.AURORA) },
                )
            }
            SettingsGroup("导航") {
                SettingsActionRow(Icons.Filled.Checklist, "底部导航", "排序与显示", onClick = onNavSettings)
            }
            SettingsGroup("阅读") {
                SettingsSwitchRow(
                    Icons.Filled.Translate,
                    "思考默认展开",
                    "打开后思考块直接展开并自动翻译",
                    thinkExpanded,
                    onThinkExpanded,
                )
            }
            SettingsGroup("通知与后台") {
                SettingsSwitchRow(
                    Icons.Filled.Notifications,
                    "通知提醒",
                    "需要回答、任务完成或出错时推送",
                    notifyEnabled,
                    onNotify,
                )
                SettingsDivider()
                SettingsSwitchRow(
                    Icons.Filled.Refresh,
                    "后台保持连接",
                    "退出 App 后保留连接（常驻通知，略耗电）",
                    keepAlive,
                    onKeepAlive,
                )
            }
            SettingsGroup("设备") {
                SettingsActionRow(Icons.Filled.Add, "添加电脑", "扫码或输入配对码", onClick = onServers)
            }
            SettingsGroup("关于") {
                SettingsActionRow(Icons.Filled.Settings, "版本", version)
                SettingsDivider()
                SettingsActionRow(Icons.Filled.Refresh, "检查更新", updateLabel, onClick = onCheckUpdate)
            }
            Spacer(Modifier.height(28.dp))
        }
    }
}

@Composable
private fun SettingsGroup(title: String, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Text(
        title,
        color = DshMuted,
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.4.sp,
        modifier = Modifier.padding(start = 20.dp, top = 14.dp, bottom = 6.dp),
    )
    Column(
        Modifier.padding(horizontal = 20.dp).fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(DshSurface),
        content = content,
    )
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(color = DshBorder, modifier = Modifier.padding(horizontal = 14.dp))
}

@Composable
private fun SettingsActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    value: String? = null,
    selected: Boolean? = null,
    onClick: (() -> Unit)? = null,
) {
    Row(
        Modifier.fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = DshMuted, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = DshText, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            subtitle?.let {
                Text(it, color = DshMuted, fontSize = 10.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 1.dp))
            }
        }
        when {
            selected == true -> Icon(Icons.Filled.Check, contentDescription = "已选", tint = DshTeal, modifier = Modifier.size(16.dp))
            selected == false -> Box(Modifier.size(16.dp).clip(CircleShape).border(1.5.dp, DshBorder, CircleShape))
            value != null -> Text(value, color = DshMuted, fontSize = 11.sp)
            onClick != null -> Icon(
                Icons.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = DshMuted,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
private fun SettingsSwitchRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = DshMuted, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = DshText, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(subtitle, color = DshMuted, fontSize = 10.5.sp, maxLines = 2)
        }
        Spacer(Modifier.width(10.dp))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = DshTeal),
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, color = DshMuted, fontSize = 10.sp, letterSpacing = 1.sp, modifier = Modifier.padding(top = 10.dp, start = 4.dp))
}

@Composable
private fun ThemeOption(label: String, caption: String, value: DshThemeMode, selected: DshThemeMode, onSelect: (DshThemeMode) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onSelect(value) }.padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, color = DshText, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(caption, color = DshMuted, fontSize = 10.sp)
        }
        Box(
            Modifier.size(22.dp).clip(CircleShape).border(2.dp, if (selected == value) DshTeal else DshBorder, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (selected == value) Box(Modifier.size(10.dp).clip(CircleShape).background(DshTeal))
        }
    }
}

@Composable
private fun SettingRow(label: String, value: String, onClick: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = DshText, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Text(value, color = DshMuted, fontSize = 11.sp)
        if (onClick != null) Icon(Icons.Filled.KeyboardArrowRight, contentDescription = null, tint = DshMuted, modifier = Modifier.size(16.dp))
    }
}

@Composable
internal fun QuestionPreviewContent() {
    Column(Modifier.fillMaxSize().background(DshBackgroundBrush)) {
        TopBar("检查未提交改动", "DSH 需要你的确认", {}, Icons.AutoMirrored.Filled.ArrowBack) {}
        Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                "DSH 在继续执行前需要你确认一个选择。",
                color = DshMuted,
                fontSize = 12.sp,
            )
            QuestionCard(
                DshClient.PendingQuestion(
                    callId = "preview-question",
                    state = "open",
                    questions = listOf(
                        DshClient.QuestionItem(
                            id = "q1",
                            question = "是否继续生成 diff？",
                            header = "确认",
                            detail = "将读取最近一次修改并生成补丁。",
                            options = listOf(
                                DshClient.QuestionOption("继续", "生成并展示 diff"),
                                DshClient.QuestionOption("暂不", "保留当前改动"),
                            ),
                            multiSelect = false,
                        ),
                    ),
                ),
            ) {}
        }
    }
}

