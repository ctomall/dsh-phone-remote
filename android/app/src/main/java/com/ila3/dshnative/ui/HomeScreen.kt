package com.ila3.dshnative.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
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
                subtitle = "${vm.pendingQuestions.size} 项需要你 · ${running.size} 个任务运行中",
                actions = {
                    IconButtonBubble(Icons.Filled.Refresh, "刷新") { vm.refreshSessions() }
                },
            )
        }
        item(key = "need-title") { SectionCaption("需要你") }
        if (vm.inbox.isEmpty()) {
            item(key = "need-empty") { HintRow("目前没有待处理事项；Agent 需要你时会出现在这里并推送通知。") }
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
                HomeSessionRow(session, active = session.id == vm.activeSessionId) { vm.openTask(session) }
            }
        }
        item(key = "recent-title") { SectionCaption("最近完成") }
        if (recent.isEmpty()) {
            item(key = "recent-empty") { HintRow("还没有已完成的会话。") }
        } else {
            items(recent, key = { "done-${it.id}" }) { session ->
                HomeSessionRow(session, active = false) { vm.openTask(session) }
            }
        }
    }
}

/* ------------------------------- 任务 ------------------------------- */

@Composable
private fun TasksTab(vm: NativeViewModel) {
    val ordered = vm.sessions.sortedByDescending { it.running }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { HomeHeader("任务", "${vm.sessions.size} 个 · ${vm.sessions.count { it.running }} 个运行中") }
        when {
            vm.busy && vm.sessions.isEmpty() -> item {
                Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = DshTeal)
                }
            }
            ordered.isEmpty() -> item { HintRow("还没有任务。新建会话后，任务会出现在这里。") }
            else -> items(ordered, key = { it.id }) { session ->
                HomeSessionRow(session, active = session.id == vm.activeSessionId) { vm.openTask(session) }
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
            hidden.forEach { id ->
                val item = NavById[id] ?: return@forEach
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp)
                        .background(DshSurface, CardShape)
                        .clickable { vm.selectNav(id) }
                        .padding(horizontal = 14.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(navIcon(id), contentDescription = item.label, tint = DshMuted, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(item.label, color = DshText, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                    Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = DshMuted, modifier = Modifier.size(17.dp))
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)
                    .background(DshSurface2, RoundedCornerShape(14.dp))
                    .clickable { vm.openNavSettings() }
                    .padding(horizontal = 14.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Settings, contentDescription = null, tint = DshTeal, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(12.dp))
                Text("编辑底部导航", color = DshText, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = DshMuted, modifier = Modifier.size(17.dp))
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
                if (vm.files.isEmpty() && !vm.busy) {
                    item { HintRow("目录为空，或还没有加载文件。") }
                }
                items(vm.files, key = { it.name }) { entry ->
                    Row(
                        Modifier.fillMaxWidth().background(DshSurface, RoundedCornerShape(14.dp))
                            .clickable { vm.openFile(entry) }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            if (entry.type == "directory") Icons.Filled.Folder else Icons.Filled.Description,
                            contentDescription = null,
                            tint = if (entry.type == "directory") DshTeal else DshMuted,
                            modifier = Modifier.size(17.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(entry.name, color = DshText, fontSize = 13.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        if (entry.type != "directory") {
                            Text(entry.size?.let { sizeLabel(it) } ?: "", color = DshMuted, fontSize = 10.5.sp)
                        }
                        Spacer(Modifier.width(6.dp))
                        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = DshMuted, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
    FilePreviewSheet(vm)
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
        }
    }
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
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { HomeHeader("文件", "${groups.size} 个目录 · ${vm.sessions.size} 个任务") }
        if (groups.isEmpty()) {
            item { HintRow("还没有工作区。会话建立后会按目录出现在这里。") }
        }
        items(groups, key = { "ws-${it.first}" }) { (cwd, list) ->
            val latest = list.maxByOrNull { it.updatedAt }
            val name = cwd.substringAfterLast('/').ifBlank { cwd }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp)
                    .background(DshSurface, CardShape)
                    .clickable { latest?.let { vm.openWorkspace(it) } }
                    .padding(horizontal = 14.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(40.dp).background(DshSurface2, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Folder, contentDescription = null, tint = DshTeal, modifier = Modifier.size(19.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(name, color = DshText, fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${list.size} 个会话 · 最近 ${formatTime(latest?.updatedAt ?: 0L)}", color = DshMuted, fontSize = 11.5.sp, modifier = Modifier.padding(top = 2.dp))
                }
                Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = DshMuted, modifier = Modifier.size(17.dp))
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
                Text("底部导航", color = DshText, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Text("拖动排序 · 控制显示 · 最多 4 个", color = DshMuted, fontSize = 10.5.sp)
            }
        }
        Text(
            "隐藏的入口会收进「更多」，不会丢失。",
            color = DshMuted,
            fontSize = 11.sp,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
        )
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)
                    .background(DshSurface, RoundedCornerShape(18.dp)),
            ) {
                DefaultNavItems.forEachIndexed { index, item ->
                    val visible = item.id !in vm.navHidden
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.width(30.dp)) {
                            if (!item.locked) {
                                Text("↑", color = DshMuted, fontSize = 14.sp, modifier = Modifier.clickable { vm.moveNav(item.id, -1) }.padding(2.dp))
                                Text("↓", color = DshMuted, fontSize = 14.sp, modifier = Modifier.clickable { vm.moveNav(item.id, 1) }.padding(2.dp))
                            }
                        }
                        Box(Modifier.size(30.dp).background(DshSurface2, RoundedCornerShape(9.dp)), contentAlignment = Alignment.Center) {
                            Icon(navIcon(item.id), contentDescription = null, tint = if (visible) DshTeal else DshMuted, modifier = Modifier.size(16.dp))
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(item.label, color = if (visible) DshText else DshMuted, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            if (item.locked) Text("固定显示", color = DshMuted, fontSize = 10.sp)
                        }
                        Switch(
                            checked = visible,
                            onCheckedChange = { vm.toggleNav(item.id) },
                            enabled = !item.locked,
                            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = DshTeal),
                        )
                    }
                    if (index != DefaultNavItems.lastIndex) {
                        HorizontalDivider(color = DshBorder, modifier = Modifier.padding(start = 74.dp))
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("恢复默认", color = DshTeal, fontSize = 12.sp, fontWeight = FontWeight.Medium, modifier = Modifier.clickable { vm.resetNav() })
                Spacer(Modifier.weight(1f))
                Text("规则：至少 2 个；「现在」固定", color = DshMuted, fontSize = 10.5.sp)
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
        Box(Modifier.fillMaxWidth().height(1.dp).background(DshBorder))
        Row(
            Modifier.fillMaxWidth().background(DshSurface).padding(horizontal = 12.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.SpaceAround,
        ) {
            shown.forEach { id ->
                val label = NavById[id]?.label ?: "更多"
                val active = vm.navSelection == id
                Column(
                    Modifier.widthIn(min = 64.dp).clickable { vm.selectNav(id) }.padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        navIcon(id),
                        contentDescription = label,
                        tint = if (active) DshTeal else DshMuted.copy(alpha = 0.55f),
                        modifier = Modifier.size(22.dp),
                    )
                    Text(
                        label,
                        color = if (active) DshTeal else DshMuted.copy(alpha = 0.75f),
                        fontSize = 10.sp,
                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
        }
    }
}

private fun navIcon(id: String): ImageVector = when (id) {
    "now" -> Icons.Filled.Home
    "tasks" -> Icons.Filled.Checklist
    "files" -> Icons.Filled.Folder
    "settings" -> Icons.Filled.Settings
    "automation" -> Icons.Filled.Inbox
    "team" -> Icons.Filled.MoreHoriz
    else -> Icons.Filled.MoreHoriz
}

/* ------------------------------- 通用 ------------------------------- */

@Composable
private fun HomeHeader(title: String, subtitle: String, actions: @Composable () -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = DshText, fontSize = 26.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.6).sp)
            if (subtitle.isNotBlank()) {
                Text(subtitle, color = DshMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
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
    Text(
        text,
        color = DshMuted.copy(alpha = 0.85f),
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.4.sp,
        modifier = Modifier.padding(start = 22.dp, top = 12.dp, bottom = 2.dp),
    )
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

@Composable
internal fun InboxQuestionContent(
    pending: DshClient.PendingQuestion,
    sessionTitle: String = "",
    onAnswer: (JSONArray) -> Unit,
    onOpen: () -> Unit,
) {
    val question = pending.questions.firstOrNull() ?: return
    val quickAnswer = pending.questions.size == 1 && !question.multiSelect && question.options.isNotEmpty()
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp)
            .background(DshSurface, CardShape)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(4.dp).height(34.dp).background(Color(0xFFF59E0B), RoundedCornerShape(4.dp)))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("需要你处理", color = DshText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    listOf(sessionTitle, question.header ?: "Agent 正在等待你的回答").filter { it.isNotBlank() }.joinToString(" · "),
                    color = DshMuted,
                    fontSize = 11.sp,
                )
            }
        }
        Text(question.question, color = DshText, fontSize = 13.sp, lineHeight = 19.sp)
        if (quickAnswer) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                question.options.take(3).forEach { option ->
                    Text(
                        option.label,
                        color = DshOnAccent,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(DshTeal)
                            .clickable {
                                onAnswer(
                                    JSONArray().put(
                                        JSONObject()
                                            .put("id", question.id)
                                            .put("selected", JSONArray().put(option.label)),
                                    ),
                                )
                            }
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                    )
                }
            }
        } else {
            Text(
                "打开会话处理",
                color = DshTeal,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.clickable { onOpen() },
            )
        }
    }
}

@Composable
private fun HomeSessionRow(session: SessionSummary, active: Boolean, onClick: () -> Unit) {
    val title = session.title?.takeIf { it.isNotBlank() } ?: session.id.take(18)
    val initial = title.firstOrNull()?.toString()?.uppercase() ?: "D"
    val tint = when ((title.hashCode() % 4 + 4) % 4) {
        0 -> DshTeal
        1 -> Color(0xFF8B5CF6)
        2 -> Color(0xFF14B8A6)
        else -> Color(0xFFF59E0B)
    }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp)
            .background(if (active) DshUserBubble else DshSurface, CardShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp).background(tint.copy(alpha = 0.12f), CircleShape), contentAlignment = Alignment.Center) {
            Text(initial, color = tint, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title,
                    color = DshText,
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (session.running) {
                    Spacer(Modifier.width(6.dp))
                    Box(Modifier.size(6.dp).background(Color(0xFF10B981), CircleShape))
                }
            }
            Text(
                session.cwd?.takeIf { it.isNotBlank() } ?: "默认工作区",
                color = DshMuted,
                fontSize = 11.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Text(formatTime(session.updatedAt), color = DshMuted.copy(alpha = 0.8f), fontSize = 10.5.sp)
    }
}

private fun formatTime(time: Long): String =
    if (time <= 0L) "--:--" else SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(time))
