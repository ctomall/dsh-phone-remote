package com.ila3.dshnative.ui

import android.content.ContentValues
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ila3.dshnative.protocol.DshClient
import com.ila3.dshnative.protocol.SessionSummary
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.io.File
import java.util.Locale

private val CardShape = RoundedCornerShape(18.dp)

@Composable
internal fun HomeScreen(vm: NativeViewModel) {
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (vm.navSelection) {
                "tasks" -> TasksTab(vm)
                "files" -> FilesTab(vm)
                "settings" -> SettingsTab(vm)
                "more" -> MoreTab(vm)
                "automation" -> PlaceholderTab("自动化", "定时任务将在 P2 接入：列表、启停、运行记录。")
                "team" -> PlaceholderTab("团队", "子代理与团队树将在 P3 接入。")
                else -> NowTab(vm)
            }
        }
        BottomNavigation(vm)
    }
}

/* ------------------------------- 现在 ------------------------------- */

@Composable
private fun NowTab(vm: NativeViewModel) {
    val running = vm.sessions.filter { it.running }
    val recent = vm.sessions.filter { !it.running }.take(5)
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "header") {
            HomeHeader(
                title = "现在",
                subtitle = "${vm.inbox.size} 待处理 · ${running.size} 运行中",
                actions = {
                    IconButtonBubble(Icons.Filled.Refresh, "刷新") { vm.refreshSessions() }
                },
            )
        }
        item(key = "need-title") { SectionCaption("需要你") }
        if (vm.inbox.isEmpty()) {
            item(key = "need-empty") { HintRow("没有待处理事项。需要你时会在这里提醒。") }
        } else {
            items(vm.inbox, key = { "q-${it.sessionId}-${it.pending.callId}" }) { question ->
                InboxQuestionCard(vm, question)
            }
        }
        item(key = "running-title") { SectionCaption("运行中") }
        if (running.isEmpty()) {
            item(key = "running-empty") { HintRow("没有运行中的任务。") }
        } else {
            items(running, key = { "run-${it.id}" }) { session ->
                HomeSessionRow(session, active = session.id == vm.activeSessionId, onClick = { vm.openTask(session) }, onRename = vm::renameSession)
            }
        }
        item(key = "recent-title") { SectionCaption("最近完成") }
        if (recent.isEmpty()) {
            item(key = "recent-empty") { HintRow("还没有已完成的会话。") }
        } else {
            items(recent, key = { "done-${it.id}" }) { session ->
                HomeSessionRow(session, active = false, onClick = { vm.openTask(session) }, onRename = vm::renameSession)
            }
        }
    }
}

/* ------------------------------- 任务 ------------------------------- */

@Composable
private fun TasksTab(vm: NativeViewModel) {
    var filter by remember { mutableStateOf(0) }
    var query by remember { mutableStateOf("") }
    val running = vm.sessions.count { it.running }
    val ordered = vm.sessions.sortedByDescending { it.running }
    val searched = ordered.filter {
        query.isBlank() ||
            it.title?.contains(query, ignoreCase = true) == true ||
            it.cwd?.contains(query, ignoreCase = true) == true ||
            it.id.contains(query, ignoreCase = true)
    }
    val shown = when (filter) {
        1 -> searched.filter { it.running }
        2 -> searched.filter { !it.running }
        else -> searched
    }
    Column(Modifier.fillMaxSize()) {
        HomeHeader("任务", "${vm.sessions.size} 个 · $running 个运行中")
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            listOf("全部" to vm.sessions.size, "运行中" to running, "已完成" to (vm.sessions.size - running)).forEachIndexed { index, pair ->
                Column(
                    Modifier.clickable { filter = index }.padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        pair.first,
                        color = if (filter == index) DshText else DshMuted,
                        fontSize = 13.sp,
                        fontWeight = if (filter == index) FontWeight.Medium else FontWeight.Normal,
                    )
                    Text(
                        pair.second.toString().padStart(2, '0'),
                        color = if (filter == index) DshText else DshMuted,
                        fontSize = 9.sp,
                        fontFamily = DshMono,
                        modifier = Modifier.padding(top = 1.dp),
                    )
                    Box(
                        Modifier.padding(top = 6.dp).width(18.dp).height(2.dp)
                            .background(if (filter == index) DshTeal else Color.Transparent),
                    )
                }
            }
        }
        HorizontalDivider(color = DshBorder, thickness = 1.dp)
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp)
                .background(DshSurface, RoundedCornerShape(8.dp))
                .border(1.dp, DshBorder, RoundedCornerShape(8.dp))
                .padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Search, contentDescription = null, tint = DshMuted, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Box(Modifier.weight(1f)) {
                if (query.isEmpty()) Text("搜索任务标题、目录或 ID", color = DshMuted, fontSize = 12.sp)
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    textStyle = TextStyle(color = DshText, fontSize = 12.5.sp),
                    cursorBrush = SolidColor(DshTeal),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (query.isNotEmpty()) {
                Text("×", color = DshMuted, fontSize = 14.sp, modifier = Modifier.clickable { query = "" }.padding(horizontal = 4.dp))
            }
        }
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 8.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            when {
                vm.busy && vm.sessions.isEmpty() -> item {
                    Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = DshTeal)
                    }
                }
                shown.isEmpty() -> item { HintRow(if (filter == 1) "没有运行中的任务。" else "没有已完成的任务。") }
                else -> items(shown, key = { it.id }) { session ->
                    HomeSessionRow(session, active = session.id == vm.activeSessionId, onClick = { vm.openTask(session) }, onRename = vm::renameSession)
                }
            }
        }
    }
}

/* ------------------------------- 更多 ------------------------------- */

@Composable
private fun MoreTab(vm: NativeViewModel) {
    val hidden = vm.hiddenNavIds()
    Column(Modifier.fillMaxSize()) {
        HomeHeader("更多", "隐藏的入口 · ${hidden.size} 个")
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                Modifier.fillMaxWidth().clickable { vm.openToolbox("search") }.padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Build, contentDescription = null, tint = DshTeal, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("工具箱", color = DshText, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Text("搜索 · 技能 · 工具 · 浏览器 · SSH · 文件", color = DshMuted, fontSize = 10.sp, fontFamily = DshMono)
                }
                Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = DshMuted, modifier = Modifier.size(16.dp))
            }
            HorizontalDivider(color = DshBorder, thickness = 1.dp)
            hidden.forEach { id ->
                val item = NavById[id] ?: return@forEach
                Row(
                    Modifier.fillMaxWidth()
                        .clickable { vm.selectNav(id) }
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(navIcon(id), contentDescription = item.label, tint = DshMuted, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(item.label, color = DshText, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                    Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = DshMuted, modifier = Modifier.size(16.dp))
                }
                HorizontalDivider(color = DshBorder, thickness = 1.dp)
            }
            Row(
                Modifier.fillMaxWidth().clickable { vm.openNavSettings() }.padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("编辑底部导航", color = DshTeal, fontSize = 12.sp, fontFamily = DshMono)
            }
        }
    }
}

@Composable
private fun PlaceholderTab(title: String, hint: String) {
    Column(Modifier.fillMaxSize()) {
        HomeHeader(title, "")
        HintRow(hint)
    }
}

/* ------------------------------- 文件 ------------------------------- */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilesTab(vm: NativeViewModel) {
    var sortBySize by remember { mutableStateOf(false) }
    var showHidden by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    if (vm.activeSessionId.isEmpty()) {
        WorkspaceTab(vm)
    } else {
        val title = vm.activeSessionTitle.ifBlank { "文件" }
        Column(Modifier.fillMaxSize()) {
            HomeHeader(
                title = "文件",
                subtitle = listOf(vm.activeCwd ?: "", vm.filePath).filter { it.isNotBlank() }.joinToString(" / "),
                actions = {
                    IconButtonBubble(Icons.Filled.Refresh, "刷新") { vm.loadFiles(vm.filePath) }
                },
            )
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (vm.filePath.isNotEmpty()) {
                    Text(
                        "‹ 上一级",
                        color = DshTeal,
                        fontSize = 12.sp,
                        modifier = Modifier.clickable { vm.filesUp() }.padding(vertical = 4.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                }
                Text(vm.filePath.ifEmpty { "." }, color = DshMuted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.weight(1f))
                Text(
                    "大小",
                    color = if (sortBySize) DshTeal else DshMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { sortBySize = !sortBySize }
                        .padding(horizontal = 6.dp, vertical = 3.dp),
                )
                Text(
                    "隐藏",
                    color = if (showHidden) DshTeal else DshMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { showHidden = !showHidden }
                        .padding(horizontal = 6.dp, vertical = 3.dp),
                )
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp)
                    .background(DshSurface, RoundedCornerShape(8.dp))
                    .border(1.dp, DshBorder, RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.Search, contentDescription = null, tint = DshMuted, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(8.dp))
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) Text("搜索当前目录", color = DshMuted, fontSize = 12.sp)
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        textStyle = TextStyle(color = DshText, fontSize = 12.5.sp),
                        cursorBrush = SolidColor(DshTeal),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (query.isNotEmpty()) {
                    Text("×", color = DshMuted, fontSize = 14.sp, modifier = Modifier.clickable { query = "" }.padding(horizontal = 4.dp))
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                listOf("all" to "全部", "docs" to "文档", "code" to "代码", "image" to "图片").forEach { (id, label) ->
                    val active = vm.fileKindFilter == id
                    Text(
                        label,
                        color = if (active) DshText else DshMuted,
                        fontSize = 10.sp,
                        fontFamily = DshMono,
                        modifier = Modifier
                            .background(if (active) DshSurface else Color.Transparent, RoundedCornerShape(6.dp))
                            .border(1.dp, if (active) DshBorder else Color.Transparent, RoundedCornerShape(6.dp))
                            .clickable { vm.fileKindFilter = id }
                            .padding(horizontal = 9.dp, vertical = 5.dp),
                    )
                }
            }
            if (vm.busy && vm.files.isEmpty()) {
                Box(Modifier.fillMaxWidth().padding(30.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = DshTeal)
                }
            }
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                val displayed = vm.files
                    .filter { showHidden || !it.name.startsWith(".") }
                    .filter { entry -> entry.type == "directory" || matchesKind(entry.name, vm.fileKindFilter) }
                    .filter { query.isBlank() || it.name.contains(query, ignoreCase = true) }
                    .sortedWith(
                        compareBy<com.ila3.dshnative.protocol.DshClient.FileEntry> { it.type != "directory" }.thenBy {
                            if (sortBySize) -(it.size ?: 0L) else 0L
                        }.thenBy { if (sortBySize) "" else it.name.lowercase() },
                    )
                if (displayed.isEmpty() && !vm.busy) {
                    item { HintRow("目录为空。") }
                }
                items(displayed, key = { it.name }) { entry ->
                    Column(Modifier.fillMaxWidth().clickable { vm.openFile(entry) }) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            if (entry.type == "directory") Icons.Outlined.Folder else Icons.Outlined.Description,
                            contentDescription = null,
                            tint = if (entry.type == "directory") DshTeal else DshMuted,
                            modifier = Modifier.size(17.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(entry.name, color = DshText, fontSize = 13.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        if (entry.type != "directory") {
                            Text(
                                entry.size?.let { sizeLabel(it) } ?: "",
                                color = DshMuted,
                                fontSize = 10.sp,
                                fontFamily = DshMono,
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = DshMuted, modifier = Modifier.size(16.dp))
                    }
                    HorizontalDivider(color = DshBorder, thickness = 1.dp)
                    }
                }
            }
        }
        FilePreviewSheet(vm)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FilePreviewSheet(vm: NativeViewModel) {
    val preview = vm.filePreview ?: return
    ModalBottomSheet(
        onDismissRequest = { vm.dismissFilePreview() },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = DshSurface,
    ) {
        Column(Modifier.fillMaxWidth().padding(20.dp)) {
            Text(preview.name, color = DshText, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(10.dp))
            when {
                preview.bitmap != null -> Image(
                    preview.bitmap.asImageBitmap(),
                    contentDescription = preview.name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth().height(360.dp),
                )
                preview.text != null -> Text(
                    preview.text,
                    color = DshText,
                    fontSize = 11.sp,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    modifier = Modifier.verticalScroll(rememberScrollState()).height(420.dp),
                )
                else -> Text("无法预览这个文件", color = DshMuted, fontSize = 12.sp)
            }
            Spacer(Modifier.height(14.dp))
            val context = LocalContext.current
            var saved by remember(preview.name) { mutableStateOf("") }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "分享",
                    color = DshOnAccent,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(DshTeal)
                        .clickable { sharePreview(context, preview) }
                        .padding(horizontal = 16.dp, vertical = 9.dp),
                )
                Text(
                    "复制",
                    color = DshText,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(DshSurface2)
                        .clickable { copyPreview(context, preview) }
                        .padding(horizontal = 16.dp, vertical = 9.dp),
                )
                Text(
                    "保存",
                    color = DshText,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(DshSurface2)
                        .clickable { saved = savePreview(context, preview) }
                        .padding(horizontal = 16.dp, vertical = 9.dp),
                )
            }
            if (saved.isNotEmpty()) {
                Text(saved, color = DshTeal, fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp))
            }
        }
    }
}

private fun sharePreview(context: Context, preview: NativeViewModel.FilePreviewData) {
    val send = Intent(Intent.ACTION_SEND)
    if (preview.bitmap != null) {
        val dir = File(context.cacheDir, "share").apply { mkdirs() }
        val file = File(dir, preview.name.ifBlank { "preview.png" })
        runCatching {
            file.outputStream().use { preview.bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            send.type = "image/png"
            send.putExtra(Intent.EXTRA_STREAM, uri)
            send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    } else {
        send.type = "text/plain"
        send.putExtra(Intent.EXTRA_TEXT, preview.text.orEmpty())
    }
    runCatching { context.startActivity(Intent.createChooser(send, "分享")) }
}

private fun savePreview(context: Context, preview: NativeViewModel.FilePreviewData): String {
    return runCatching {
        val resolver = context.contentResolver
        if (preview.bitmap != null) {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, preview.name.ifBlank { "dsh-preview.png" })
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/DSH")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: return "保存失败：无法创建媒体条目"
            resolver.openOutputStream(uri)?.use { out ->
                preview.bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            }
            "已保存到相册（Pictures/DSH）"
        } else {
            val bytes = preview.text.orEmpty().toByteArray()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, preview.name.ifBlank { "dsh-file.txt" })
                    put(MediaStore.Downloads.MIME_TYPE, "text/plain")
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/DSH")
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: return "保存失败：无法创建下载条目"
                resolver.openOutputStream(uri)?.use { it.write(bytes) }
                values.clear()
                values.put(MediaStore.Downloads.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                "已保存到下载（Download/DSH）"
            } else {
                val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "DSH")
                dir.mkdirs()
                val file = File(dir, preview.name.ifBlank { "dsh-file.txt" })
                file.writeBytes(bytes)
                "已保存到 ${file.absolutePath}"
            }
        }
    }.getOrElse { "保存失败：${it.message}" }
}

private fun copyPreview(context: Context, preview: NativeViewModel.FilePreviewData) {
    val text = preview.text ?: preview.name
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
    clipboard.setPrimaryClip(android.content.ClipData.newPlainText("DSH", text))
}

private fun sizeLabel(bytes: Long): String = when {
    bytes >= 1024 * 1024 -> String.format("%.1f MB", bytes / 1024.0 / 1024.0)
    bytes >= 1024 -> String.format("%.0f KB", bytes / 1024.0)
    else -> "$bytes B"
}

/* ------------------------------- 工作区 ------------------------------- */

@Composable
private fun WorkspaceTab(vm: NativeViewModel) {
    val groups = vm.sessions
        .groupBy { it.cwd?.takeIf { path -> path.isNotBlank() } ?: "默认工作区" }
        .toList()
        .sortedByDescending { (_, list) -> list.maxOf { it.updatedAt } }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 16.dp),
    ) {
        item { HomeHeader("文件", "${groups.size} 个目录 · ${vm.sessions.size} 个任务") }
        if (groups.isEmpty()) {
            item { HintRow("还没有工作区。会话建立后会按目录出现在这里。") }
        }
        items(groups, key = { "ws-${it.first}" }) { (cwd, list) ->
            val latest = list.maxByOrNull { it.updatedAt }
            val name = cwd.substringAfterLast('/').ifBlank { cwd }
            Column(Modifier.fillMaxWidth().clickable { latest?.let { vm.openWorkspace(it) } }) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Outlined.Folder,
                        contentDescription = null,
                        tint = DshMuted,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            name,
                            color = DshText,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            "${list.size} 个会话 · 最近 ${formatTime(latest?.updatedAt ?: 0L)}",
                            color = DshMuted,
                            fontSize = 10.sp,
                            fontFamily = DshMono,
                            modifier = Modifier.padding(top = 3.dp),
                        )
                    }
                    Icon(
                        Icons.Outlined.ChevronRight,
                        contentDescription = null,
                        tint = DshMuted,
                        modifier = Modifier.size(16.dp),
                    )
                }
                HorizontalDivider(color = DshBorder, thickness = 1.dp)
            }
        }
    }
}

/* ------------------------------- 导航自定义 ------------------------------- */

@Composable
internal fun NavSettingsScreen(vm: NativeViewModel) {
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "‹",
                color = DshText,
                fontSize = 26.sp,
                modifier = Modifier.clickable { vm.closeNavSettings() }.padding(horizontal = 8.dp),
            )
            Column(Modifier.weight(1f)) {
                Text("底部导航", color = DshText, fontSize = 18.sp, fontWeight = FontWeight.Medium)
                Text("排序 · 显示 · 最多 4 个", color = DshMuted, fontSize = 10.sp, fontFamily = DshMono)
            }
        }
        Text(
            "隐藏的入口会收进「更多」，不会丢失。",
            color = DshMuted,
            fontSize = 11.sp,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
        )
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
                DefaultNavItems.forEachIndexed { index, item ->
                    val visible = item.id !in vm.navHidden
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.width(26.dp)) {
                            if (!item.locked) {
                                Text("↑", color = DshMuted, fontSize = 12.sp, fontFamily = DshMono, modifier = Modifier.clickable { vm.moveNav(item.id, -1) }.padding(1.dp))
                                Text("↓", color = DshMuted, fontSize = 12.sp, fontFamily = DshMono, modifier = Modifier.clickable { vm.moveNav(item.id, 1) }.padding(1.dp))
                            }
                        }
                        Icon(navIcon(item.id), contentDescription = null, tint = if (visible) DshTeal else DshMuted, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(item.label, color = if (visible) DshText else DshMuted, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        }
                        if (item.locked) {
                            Text("固定", color = DshMuted, fontSize = 9.sp, fontFamily = DshMono)
                        } else {
                            Switch(
                                checked = visible,
                                onCheckedChange = { vm.toggleNav(item.id) },
                                colors = SwitchDefaults.colors(checkedThumbColor = DshOnAccent, checkedTrackColor = DshTeal),
                            )
                        }
                    }
                    HorizontalDivider(color = DshBorder, thickness = 1.dp, modifier = Modifier.padding(start = 54.dp))
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("恢复默认", color = DshTeal, fontSize = 11.sp, fontFamily = DshMono, modifier = Modifier.clickable { vm.resetNav() })
                Spacer(Modifier.weight(1f))
                Text("至少 2 个 · 现在固定", color = DshMuted, fontSize = 9.sp, fontFamily = DshMono)
            }
        }
        PreviewNavBar(vm)
    }
}

@Composable
private fun PreviewNavBar(vm: NativeViewModel) {
    val visible = vm.visibleNavIds()
    val hidden = vm.hiddenNavIds()
    val shown = visible + if (hidden.isNotEmpty()) listOf("more") else emptyList()
    Column(Modifier.fillMaxWidth().background(DshSurface).padding(vertical = 8.dp)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(DshBorder))
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), horizontalArrangement = Arrangement.SpaceAround) {
            shown.forEach { id ->
                val label = NavById[id]?.label ?: "更多"
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(navIcon(id), contentDescription = label, tint = if (id == "now") DshTeal else DshMuted, modifier = Modifier.size(20.dp))
                    Text(label, color = if (id == "now") DshTeal else DshMuted, fontSize = 9.5.sp, modifier = Modifier.padding(top = 1.dp))
                }
            }
        }
    }
}

/* ------------------------------- 底部导航 ------------------------------- */

@Composable
private fun BottomNavigation(vm: NativeViewModel) {
    val visible = vm.visibleNavIds()
    val hidden = vm.hiddenNavIds()
    val shown = visible + if (hidden.isNotEmpty()) listOf("more") else emptyList()
    Column(Modifier.navigationBarsPadding()) {
        HorizontalDivider(color = DshBorder, thickness = 1.dp)
        Row(
            Modifier.fillMaxWidth().background(DshBg).padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceAround,
        ) {
            shown.forEach { id ->
                val label = NavById[id]?.label ?: "更多"
                val active = vm.navSelection == id
                Column(
                    Modifier.weight(1f).clickable { vm.selectNav(id) }.padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        navIcon(id),
                        contentDescription = label,
                        tint = if (active) DshTeal else DshMuted,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        label,
                        color = if (active) DshTeal else DshMuted,
                        fontSize = 10.sp,
                        fontWeight = if (active) FontWeight.Medium else FontWeight.Normal,
                        modifier = Modifier.padding(top = 3.dp),
                    )
                }
            }
        }
    }
}

private fun navIcon(id: String): ImageVector = when (id) {
    "now" -> Icons.Outlined.Home
    "tasks" -> Icons.Outlined.Checklist
    "files" -> Icons.Outlined.Folder
    "settings" -> Icons.Outlined.Settings
    "automation" -> Icons.Outlined.Inbox
    "team" -> Icons.Outlined.MoreHoriz
    else -> Icons.Outlined.MoreHoriz
}

/* ------------------------------- 通用 ------------------------------- */

@Composable
private fun HomeHeader(title: String, subtitle: String, actions: @Composable () -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = DshText, fontSize = 24.sp, fontWeight = FontWeight.Medium)
            if (subtitle.isNotBlank()) {
                Text(
                    subtitle,
                    color = DshMuted,
                    fontSize = 10.sp,
                    fontFamily = DshMono,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
        actions()
    }
}

@Composable
private fun IconButtonBubble(icon: ImageVector, description: String, onClick: () -> Unit) {
    Box(
        Modifier.size(34.dp).background(DshSurface, CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, contentDescription = description, tint = DshText.copy(alpha = 0.72f), modifier = Modifier.size(17.dp)) }
}

@Composable
private fun SectionCaption(text: String) {
    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, color = DshMuted, fontSize = 11.sp, fontFamily = DshMono)
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f).height(1.dp).background(DshBorder))
    }
}

@Composable
private fun HintRow(text: String) {
    Text(
        text,
        color = DshMuted,
        fontSize = 11.5.sp,
        lineHeight = 17.sp,
        modifier = Modifier.padding(horizontal = 22.dp, vertical = 6.dp),
    )
}

@Composable
private fun InboxQuestionCard(vm: NativeViewModel, question: NativeViewModel.SessionQuestion) {
    InboxQuestionContent(
        pending = question.pending,
        sessionTitle = question.sessionTitle,
        onAnswer = { answers -> vm.answerSessionQuestion(question, answers) },
        onOpen = { vm.sessions.firstOrNull { it.id == question.sessionId }?.let { vm.openTask(it) } },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun InboxQuestionContent(
    pending: DshClient.PendingQuestion,
    sessionTitle: String = "",
    onAnswer: (JSONArray) -> Unit,
    onOpen: () -> Unit,
) {
    val question = pending.questions.firstOrNull() ?: return
    val single = pending.questions.size == 1 && !question.multiSelect && question.options.isNotEmpty()
    val multi = pending.questions.size == 1 && question.multiSelect && question.options.isNotEmpty()
    val freeText = pending.questions.size == 1 && question.options.isEmpty()
    val selected = remember(pending.callId) { mutableStateMapOf<String, Boolean>() }
    var custom by remember(pending.callId) { mutableStateOf("") }

    fun submit() {
        val labels = selected.filterValues { it }.keys.toList()
        val answers = JSONArray()
        if (labels.isNotEmpty()) {
            answers.put(JSONObject().put("id", question.id).put("selected", JSONArray(labels)))
        }
        if (custom.isNotBlank()) {
            answers.put(JSONObject().put("id", question.id).put("selected", JSONArray()).put("custom", custom.trim()))
        }
        if (answers.length() > 0) onAnswer(answers)
    }

    Column(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp)
            .background(DshSurface)
            .border(1.dp, DshBorder)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(6.dp).background(DshTeal))
            Spacer(Modifier.width(8.dp))
            Text("需要你", color = DshTeal, fontSize = 10.sp, fontFamily = DshMono)
            Spacer(Modifier.width(8.dp))
            Text(
                listOf(sessionTitle, question.header.orEmpty()).filter { it.isNotBlank() }.joinToString(" · "),
                color = DshMuted,
                fontSize = 10.sp,
                fontFamily = DshMono,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(question.question, color = DshText, fontSize = 14.sp, lineHeight = 21.sp)
        when {
            single -> {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    question.options.take(3).forEachIndexed { index, option ->
                        Text(
                            option.label,
                            color = if (index == 0) DshOnAccent else DshText,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier
                                .background(if (index == 0) DshTeal else Color.Transparent)
                                .border(1.dp, if (index == 0) DshTeal else DshBorder)
                                .clickable {
                                    onAnswer(
                                        JSONArray().put(
                                            JSONObject().put("id", question.id).put("selected", JSONArray().put(option.label)),
                                        ),
                                    )
                                }
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                        )
                    }
                }
            }
            multi -> {
                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    question.options.forEach { option ->
                        val checked = selected[option.label] == true
                        Text(
                            option.label,
                            color = if (checked) DshOnAccent else DshText,
                            fontSize = 12.sp,
                            modifier = Modifier
                                .background(if (checked) DshTeal else Color.Transparent)
                                .border(1.dp, if (checked) DshTeal else DshBorder)
                                .clickable { selected[option.label] = !checked }
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                        )
                    }
                }
                Text(
                    "提交",
                    color = DshOnAccent,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .background(if (selected.any { it.value }) DshTeal else DshMuted.copy(alpha = 0.4f))
                        .clickable(enabled = selected.any { it.value }) { submit() }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            freeText -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.foundation.text.BasicTextField(
                        value = custom,
                        onValueChange = { custom = it },
                        textStyle = androidx.compose.ui.text.TextStyle(color = DshText, fontSize = 13.sp),
                        cursorBrush = androidx.compose.ui.graphics.SolidColor(DshTeal),
                        modifier = Modifier.weight(1f)
                            .background(DshBg)
                            .border(1.dp, DshBorder)
                            .padding(horizontal = 10.dp, vertical = 9.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "提交",
                        color = DshOnAccent,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .background(if (custom.isNotBlank()) DshTeal else DshMuted.copy(alpha = 0.4f))
                            .clickable(enabled = custom.isNotBlank()) { submit() }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                    )
                }
            }
            else -> {
                Text(
                    "打开会话",
                    color = DshTeal,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.clickable { onOpen() },
                )
            }
        }
    }
}

@Composable
private fun HomeSessionRow(session: SessionSummary, active: Boolean, onClick: () -> Unit, onRename: (String, String) -> Unit) {
    val title = session.title?.takeIf { it.isNotBlank() } ?: session.id.take(18)
    val clipboard = LocalClipboardManager.current
    var menuOpen by remember { mutableStateOf(false) }
    var renameOpen by remember { mutableStateOf(false) }
    var draft by remember(session.id) { mutableStateOf(title) }
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth()
                .background(if (active) DshSelected else Color.Transparent)
                .clickable(onClick = onClick)
                .padding(start = 20.dp, end = 12.dp, top = 11.dp, bottom = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.width(46.dp)) {
                Text(
                    relativeTime(session.updatedAt),
                    color = DshMuted,
                    fontSize = 10.sp,
                    fontFamily = DshMono,
                )
                Text(
                    if (session.running) "RUN" else "DONE",
                    color = if (session.running) DshTeal else DshMuted,
                    fontSize = 9.sp,
                    fontFamily = DshMono,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (session.running) {
                        Box(Modifier.size(6.dp).background(DshTeal, CircleShape))
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(
                        title,
                        color = DshText,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    listOfNotNull(session.device, session.cwd?.takeIf { it.isNotBlank() } ?: "默认工作区").joinToString(" · "),
                    color = DshMuted,
                    fontSize = 10.sp,
                    fontFamily = DshMono,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
            Box {
                Icon(
                    Icons.Outlined.MoreHoriz,
                    contentDescription = "会话操作",
                    tint = DshMuted,
                    modifier = Modifier.size(18.dp).clickable { menuOpen = true }.padding(4.dp),
                )
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(text = { Text("重命名", fontSize = 12.sp) }, onClick = {
                        menuOpen = false
                        draft = title
                        renameOpen = true
                    })
                    DropdownMenuItem(text = { Text("复制标题", fontSize = 12.sp) }, onClick = {
                        menuOpen = false
                        clipboard.setText(AnnotatedString(title))
                    })
                }
            }
        }
        HorizontalDivider(color = DshBorder, thickness = 1.dp, modifier = Modifier.padding(start = 20.dp))
    }
    if (renameOpen) {
        AlertDialog(
            onDismissRequest = { renameOpen = false },
            title = { Text("重命名会话", fontSize = 15.sp) },
            text = {
                BasicTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(color = DshText, fontSize = 14.sp),
                    modifier = Modifier.fillMaxWidth().border(1.dp, DshBorder, RoundedCornerShape(6.dp)).padding(10.dp),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    renameOpen = false
                    if (draft.isNotBlank()) onRename(session.id, draft.trim())
                }) { Text("保存", color = DshTeal) }
            },
            dismissButton = { TextButton(onClick = { renameOpen = false }) { Text("取消", color = DshMuted) } },
            containerColor = DshSurface,
            shape = RoundedCornerShape(10.dp),
        )
    }
}

private fun formatTime(time: Long): String =
    if (time <= 0L) "--:--" else SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(time))

internal fun relativeTime(time: Long): String {
    if (time <= 0L) return "--:--"
    val now = java.util.Calendar.getInstance()
    val then = java.util.Calendar.getInstance().apply { timeInMillis = time }
    val sameYear = now.get(java.util.Calendar.YEAR) == then.get(java.util.Calendar.YEAR)
    val dayDiff = now.get(java.util.Calendar.DAY_OF_YEAR) - then.get(java.util.Calendar.DAY_OF_YEAR)
    return when {
        sameYear && dayDiff == 0 -> SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(time))
        sameYear && dayDiff == 1 -> "昨天"
        else -> SimpleDateFormat("M/d", Locale.getDefault()).format(Date(time))
    }
}


private fun matchesKind(name: String, kind: String): Boolean {
    val ext = name.substringAfterLast('.', "").lowercase()
    return when (kind) {
        "docs" -> ext in setOf("md", "markdown", "txt", "pdf", "doc", "docx", "rst")
        "code" -> ext in setOf(
            "kt", "java", "ts", "tsx", "js", "jsx", "py", "go", "rs", "c", "cc", "cpp", "h", "hpp",
            "json", "yml", "yaml", "toml", "sh", "css", "html", "xml", "sql",
        )
        "image" -> ext in setOf("png", "jpg", "jpeg", "webp", "gif", "svg", "bmp")
        else -> true
    }
}
