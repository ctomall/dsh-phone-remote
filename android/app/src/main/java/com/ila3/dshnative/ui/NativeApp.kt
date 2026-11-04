package com.ila3.dshnative.ui

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
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
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
            Screen.Sessions -> SessionsScreen(vm)
            Screen.Chat -> ChatScreen(vm)
            Screen.Files -> FilesScreen(vm)
            Screen.Terminal -> TerminalScreen(vm)
            Screen.Settings -> SettingsScreen(vm)
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
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { vm.attach(it) }
    }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { vm.attach(it) }
    }
    LaunchedEffect(vm.activeSessionId, vm.messages.size) {
        if (vm.messages.isEmpty()) return@LaunchedEffect
        val lastIndex = vm.messages.lastIndex
        if (!initialScrollDone) {
            // Opening a long conversation must land on the newest message
            // immediately. animateScrollToItem would visibly travel from the
            // top through hundreds of records.
            listState.scrollToItem(lastIndex)
            initialScrollDone = true
        } else {
            // Follow live output only while the reader is already near the
            // bottom; scrolling up to read history must not be interrupted.
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            if (lastVisible >= vm.messages.size - 3) {
                listState.animateScrollToItem(lastIndex)
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
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(vm.messages.size) { index ->
                MessageRow(vm.messages[index])
            }
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
        if (vm.pendingAttachments.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                vm.pendingAttachments.forEach { pending ->
                    Row(
                        Modifier.clip(RoundedCornerShape(10.dp)).background(DshSurface).padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(pending.name, color = DshText, fontSize = 10.sp, maxLines = 1)
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = "移除",
                            tint = DshMuted,
                            modifier = Modifier.size(14.dp).clickable { vm.removeAttachment(pending.id) },
                        )
                    }
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().imePadding().padding(10.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            IconButton(
                onClick = { filePicker.launch("*/*") },
                modifier = Modifier.size(42.dp).clip(CircleShape).background(DshSurface2),
            ) {
                Icon(Icons.Filled.Add, contentDescription = "附件", tint = DshTeal)
            }
            Spacer(Modifier.width(8.dp))
            OutlinedTextField(
                value = vm.input,
                onValueChange = { vm.input = it },
                placeholder = { Text("跟进…", color = DshMuted, fontSize = 13.sp) },
                maxLines = 5,
                colors = fieldColors(),
                shape = RoundedCornerShape(22.dp),
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            IconButton(
                onClick = { vm.send() },
                modifier = Modifier.clip(CircleShape).background(DshTeal).size(46.dp),
            ) {
                Icon(Icons.Filled.ArrowUpward, contentDescription = "发送", tint = DshOnAccent)
            }
        }
    }
    if (vm.showModelPicker) ModelPickerDialog(vm)
    if (vm.showChatActions) {
        ModalBottomSheet(
            onDismissRequest = { vm.dismissChatMenu() },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = DshSurface,
        ) {
            ChatActionSheet(vm, imagePicker = { imagePicker.launch("image/*") }, filePicker = { filePicker.launch("*/*") })
        }
    }
    if (vm.showRenameDialog) RenameDialog(vm)
}

@Composable
private fun ChatActionSheet(vm: NativeViewModel, imagePicker: () -> Unit, filePicker: () -> Unit) {
    Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text("会话操作", color = DshText, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(8.dp))
        ChatAction(Icons.Filled.AddPhotoAlternate, "上传图片") { vm.dismissChatMenu(); imagePicker() }
        ChatAction(Icons.Filled.UploadFile, "上传文件") { vm.dismissChatMenu(); filePicker() }
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
private fun MessageRow(item: ChatItem) {
    when (item) {
        is ChatItem.User -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Text(
                item.text,
                color = DshText,
                fontSize = 14.sp,
                modifier = Modifier
                    .widthIn(max = 300.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(DshUserBubble)
                    .padding(12.dp),
            )
        }
        is ChatItem.Assistant -> {
            val clipboard = LocalClipboardManager.current
            var copied by remember { mutableStateOf(false) }
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(DshSurface).padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(20.dp).clip(CircleShape).background(DshSelected), contentAlignment = Alignment.Center) {
                        Text("D", color = DshTeal, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.width(6.dp))
                    Text("DSH", color = DshTealSoft, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.weight(1f))
                    IconButton(
                        onClick = {
                            clipboard.setText(AnnotatedString(item.text))
                            copied = true
                        },
                        modifier = Modifier.size(26.dp),
                    ) {
                        Icon(
                            if (copied) Icons.Filled.Check else Icons.Filled.ContentCopy,
                            contentDescription = "复制",
                            tint = DshMuted,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
                MarkdownText(item.text.ifBlank { "…" })
                if (item.streaming) Text("正在生成…", color = DshMuted, fontSize = 10.sp, modifier = Modifier.padding(top = 4.dp))
            }
        }
        is ChatItem.Tool -> ToolCard(item)
        is ChatItem.ToolResult -> Unit
        is ChatItem.Notice -> Text(item.text, color = DshMuted, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 4.dp))
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
            .clip(RoundedCornerShape(16.dp))
            .background(DshSurface2)
            .border(1.dp, tint.copy(alpha = 0.32f), RoundedCornerShape(16.dp))
            .clickable { expanded = !expanded }
            .padding(14.dp),
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
        Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) { Icon(backIcon, contentDescription = "返回", tint = DshMuted) }
        Column(Modifier.weight(1f)) {
            Text(title, color = DshText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text(subtitle, color = DshMuted, fontSize = 10.sp)
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
    vm.filePreview?.let { (name, text) ->
        AlertDialog(
            onDismissRequest = { vm.dismissFilePreview() },
            title = { Text(name, color = DshText, fontSize = 15.sp) },
            text = {
                Text(
                    text.ifBlank { "（空文件）" },
                    color = DshText,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    modifier = Modifier.verticalScroll(rememberScrollState()).height(400.dp),
                )
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

/** Screenshot-only chat composition with deterministic messages and tool card. */
@Composable
internal fun ChatPreviewContent() {
    Column(Modifier.fillMaxSize().background(DshBackgroundBrush)) {
        TopBar("检查未提交改动", "已连接 · DeepSeek V4", {}, Icons.AutoMirrored.Filled.ArrowBack) {}
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(
                listOf(
                    ChatItem.User("u1", "帮我检查一下工作区里有没有未提交的改动"),
                    ChatItem.Assistant("a1", "我先看一下 git 状态和最近的 diff：\n```\ngit status --short\n```"),
                    ChatItem.Tool(
                        "t1",
                        "call_1",
                        "bash",
                        "{\n  \"command\": \"git status --short\"\n}",
                        " M app/src/main/java/com/ila3/dshnative/ui/NativeApp.kt\n?? docs/",
                        false,
                    ),
                    ChatItem.Assistant("a2", "有两个改动：NativeApp.kt 已修改，docs/ 是新目录。需要我继续生成 diff 吗？"),
                ),
            ) { item -> MessageRow(item) }
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
        Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.clip(RoundedCornerShape(10.dp)).background(DshSurface).padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("截图.png", color = DshText, fontSize = 10.sp)
                Spacer(Modifier.width(4.dp))
                Icon(Icons.Filled.Close, contentDescription = null, tint = DshMuted, modifier = Modifier.size(14.dp))
            }
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
            Box(Modifier.clip(CircleShape).background(DshTeal).size(46.dp), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.ArrowUpward, contentDescription = null, tint = DshOnAccent)
            }
        }
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
    )
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
        version = "0.12.0",
        updateLabel = "当前 0.12.0",
        onCheckUpdate = {},
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
) {
    Column(Modifier.fillMaxSize()) {
        TopBar("设置", profileName, onBack, Icons.AutoMirrored.Filled.ArrowBack)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SectionLabel("连接")
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(DshSurface)
                    .border(1.dp, DshBorder, RoundedCornerShape(22.dp)).padding(16.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(DshSelected), contentAlignment = Alignment.Center) {
                        Text("DSH", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(profileName, color = DshText, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        Text(connected, color = DshTeal, fontSize = 11.sp)
                    }
                    Text("当前", color = DshTeal, fontSize = 10.sp)
                }
                Text(profileUrl, color = DshMuted, fontSize = 10.sp, modifier = Modifier.padding(top = 10.dp))
                Button(
                    onClick = onServers,
                    colors = ButtonDefaults.buttonColors(containerColor = DshSurface2, contentColor = DshTeal),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                ) { Text("切换 / 添加 DSH") }
            }
            SectionLabel("外观")
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(DshSurface)
                    .border(1.dp, DshBorder, RoundedCornerShape(22.dp)),
            ) {
                ThemeOption("云白", "清爽蓝紫", DshThemeMode.PAPER, mode, onMode)
                HorizontalDivider(color = DshBorder, modifier = Modifier.padding(horizontal = 16.dp))
                ThemeOption("深海", "暗色青绿", DshThemeMode.AURORA, mode, onMode)
            }
            SectionLabel("设备")
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(DshSurface)
                    .border(1.dp, DshBorder, RoundedCornerShape(22.dp)),
            ) {
                SettingRow("当前 DSH", profileName)
                HorizontalDivider(color = DshBorder, modifier = Modifier.padding(horizontal = 16.dp))
                SettingRow("添加电脑", "扫码或输入配对码", onClick = onServers)
            }
            SectionLabel("关于")
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(DshSurface)
                    .border(1.dp, DshBorder, RoundedCornerShape(22.dp)),
            ) {
                SettingRow("版本", version)
                HorizontalDivider(color = DshBorder, modifier = Modifier.padding(horizontal = 16.dp))
                SettingRow("检查更新", updateLabel, onClick = onCheckUpdate)
                HorizontalDivider(color = DshBorder, modifier = Modifier.padding(horizontal = 16.dp))
                SettingRow("协议", "DSH Remote / Typert")
            }
            Spacer(Modifier.height(30.dp))
        }
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
