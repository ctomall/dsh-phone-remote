package com.ila3.dshnative.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ila3.dshnative.protocol.DshClient
import com.ila3.dshnative.protocol.SessionSummary
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val CardShape = RoundedCornerShape(18.dp)

@Composable
internal fun HomeScreen(vm: NativeViewModel) {
    var tab by remember { mutableIntStateOf(0) }
    var query by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (tab) {
                0 -> SessionsTab(vm, query) { query = it }
                1 -> InboxTab(vm)
                2 -> WorkspaceTab(vm)
                else -> SettingsTab(vm)
            }
        }
        BottomNavigation(active = tab) { index -> tab = index }
    }
}

/* ------------------------------- 会话 ------------------------------- */

@Composable
private fun SessionsTab(vm: NativeViewModel, query: String, onQuery: (String) -> Unit) {
    val running = vm.sessions.filter { it.running }
    val filtered = vm.sessions.filter { session ->
        query.isBlank() ||
            session.title?.contains(query, ignoreCase = true) == true ||
            session.id.contains(query, ignoreCase = true) ||
            session.cwd?.contains(query, ignoreCase = true) == true
    }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "header") {
            HomeHeader(
                title = "会话",
                subtitle = "${vm.connection} · ${vm.sessions.size} 个会话",
                actions = {
                    IconButtonBubble(Icons.Filled.Refresh, "刷新") { vm.refreshSessions() }
                    Spacer(Modifier.width(8.dp))
                    IconButtonBubble(Icons.Filled.Add, "新建会话") { vm.newSession() }
                },
            )
        }
        item(key = "search") {
            SearchField(query, onQuery)
        }
        if (running.isNotEmpty()) {
            item(key = "running") { RunningCard(running) { vm.openTasks() } }
        }
        if (vm.pendingQuestions.isNotEmpty()) {
            item(key = "approval") {
                ApprovalCard(vm.pendingQuestions.first()) {
                    vm.sessions.firstOrNull { it.id == vm.activeSessionId }?.let { vm.openSession(it) }
                }
            }
        }
        when {
            vm.busy && vm.sessions.isEmpty() -> item(key = "loading") {
                Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = DshTeal)
                }
            }
            filtered.isEmpty() -> item(key = "empty") {
                EmptyState(
                    title = if (vm.sessions.isEmpty()) "还没有会话" else "没有匹配的会话",
                    hint = if (vm.sessions.isEmpty()) "新建一个会话，或先在网页端开始" else "换个关键词试试",
                )
            }
            else -> {
                val groups = filtered.groupBy { dateGroup(it.updatedAt) }
                groups.forEach { (label, items) ->
                    item(key = "group-$label") { SectionCaption(label) }
                    items(items, key = { it.id }) { session ->
                        HomeSessionRow(session, active = session.id == vm.activeSessionId) { vm.openSession(session) }
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeHeader(title: String, subtitle: String, actions: @Composable () -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = DshText, fontSize = 26.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.6).sp)
            Text(subtitle, color = DshMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
        }
        actions()
    }
}

@Composable
private fun IconButtonBubble(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, onClick: () -> Unit) {
    Box(
        Modifier.size(34.dp).background(DshSurface, CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, contentDescription = description, tint = DshText.copy(alpha = 0.72f), modifier = Modifier.size(17.dp)) }
}

@Composable
private fun SearchField(query: String, onQuery: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp)
            .background(DshSurface, RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Search, contentDescription = null, tint = DshMuted, modifier = Modifier.size(17.dp))
        Spacer(Modifier.width(8.dp))
        Box(Modifier.weight(1f)) {
            if (query.isEmpty()) {
                Text("搜索标题、ID 或目录", color = DshMuted, fontSize = 12.5.sp)
            }
            BasicTextField(
                value = query,
                onValueChange = onQuery,
                singleLine = true,
                textStyle = TextStyle(color = DshText, fontSize = 12.5.sp),
                cursorBrush = SolidColor(DshTeal),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun RunningCard(running: List<SessionSummary>, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp)
            .background(
                Brush.linearGradient(listOf(Color(0xFF3347D8), Color(0xFF5B78FF))),
                RoundedCornerShape(20.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("${running.size} 个任务正在运行", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(
                running.first().title?.takeIf { it.isNotBlank() } ?: running.first().id.take(18),
                color = Color.White.copy(alpha = 0.78f),
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 3.dp),
            )
        }
        Box(
            Modifier.size(30.dp).background(Color.White.copy(alpha = 0.16f), CircleShape),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Filled.Checklist, contentDescription = "查看任务", tint = Color.White, modifier = Modifier.size(17.dp)) }
    }
}

@Composable
private fun ApprovalCard(pending: DshClient.PendingQuestion, onOpen: () -> Unit) {
    val question = pending.questions.firstOrNull()?.question ?: "Agent 需要你的回答"
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp)
            .background(DshSurface, CardShape)
            .clickable(onClick = onOpen)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(4.dp).height(38.dp).background(Color(0xFFF59E0B), RoundedCornerShape(4.dp)))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("需要你处理", color = DshText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(question, color = DshMuted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
        }
        Text("去处理", color = DshTeal, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
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

/* ------------------------------- 收件箱 ------------------------------- */

@Composable
private fun InboxTab(vm: NativeViewModel) {
    val running = vm.sessions.filter { it.running }
    val recent = vm.sessions.filter { !it.running }.take(8)
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { HomeHeader("收件箱", "需要你处理的 ${running.size + vm.pendingQuestions.size} 项") }
        item { SectionCaption("待处理") }
        if (vm.pendingQuestions.isEmpty() && running.isEmpty()) {
            item { EmptyState("没有待处理事项", "Agent 需要你时会出现在这里，并推送通知") }
        }
        if (vm.pendingQuestions.isNotEmpty()) {
            item {
                ApprovalCard(vm.pendingQuestions.first()) {
                    vm.sessions.firstOrNull { it.id == vm.activeSessionId }?.let { vm.openSession(it) }
                }
            }
        }
        items(running, key = { "inbox-${it.id}" }) { session ->
            HomeSessionRow(session, active = session.id == vm.activeSessionId) { vm.openSession(session) }
        }
        item { SectionCaption("最近完成") }
        if (recent.isEmpty()) {
            item { EmptyState("暂无已完成会话", "") }
        } else {
            items(recent, key = { "recent-${it.id}" }) { session ->
                HomeSessionRow(session, active = false) { vm.openSession(session) }
            }
        }
    }
}

/* ------------------------------- 工作区 ------------------------------- */

@Composable
private fun WorkspaceTab(vm: NativeViewModel) {
    val groups = vm.sessions
        .groupBy { it.cwd?.takeIf { path -> path.isNotBlank() } ?: "默认工作区" }
        .toList()
        .sortedByDescending { (_, list) -> list.maxOf { it.updatedAt } }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { HomeHeader("工作区", "${groups.size} 个目录 · ${vm.sessions.size} 个会话") }
        if (groups.isEmpty()) {
            item { EmptyState("还没有工作区", "会话建立后会按目录出现在这里") }
        }
        items(groups, key = { "ws-${it.first}" }) { (cwd, list) ->
            val latest = list.maxByOrNull { it.updatedAt }
            val name = cwd.substringAfterLast('/').ifBlank { cwd }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp)
                    .background(DshSurface, CardShape)
                    .clickable { latest?.let { vm.openSession(it) } }
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

@Composable
private fun EmptyState(title: String, hint: String) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(title, color = DshText, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        if (hint.isNotBlank()) Text(hint, color = DshMuted, fontSize = 12.sp)
    }
}

/* ------------------------------- 底部导航 ------------------------------- */

@Composable
private fun BottomNavigation(active: Int, onSelect: (Int) -> Unit) {
    val items = listOf(
        Triple("会话", Icons.Filled.Chat, 0),
        Triple("收件箱", Icons.Filled.Inbox, 1),
        Triple("工作区", Icons.Filled.Folder, 2),
        Triple("设置", Icons.Filled.Settings, 3),
    )
    Column(Modifier.navigationBarsPadding()) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(DshBorder))
        Row(
            Modifier.fillMaxWidth().background(DshSurface).padding(horizontal = 12.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.SpaceAround,
        ) {
            items.forEach { (label, icon, index) ->
                Column(
                    Modifier.widthIn(min = 64.dp).clickable { onSelect(index) }.padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        icon,
                        contentDescription = label,
                        tint = if (active == index) DshTeal else DshMuted.copy(alpha = 0.55f),
                        modifier = Modifier.size(22.dp),
                    )
                    Text(
                        label,
                        color = if (active == index) DshTeal else DshMuted.copy(alpha = 0.75f),
                        fontSize = 10.sp,
                        fontWeight = if (active == index) FontWeight.SemiBold else FontWeight.Normal,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
        }
    }
}

private fun dateGroup(time: Long): String {
    val now = Calendar.getInstance()
    val then = Calendar.getInstance().apply { timeInMillis = time }
    val sameDay = now.get(Calendar.YEAR) == then.get(Calendar.YEAR) &&
        now.get(Calendar.DAY_OF_YEAR) == then.get(Calendar.DAY_OF_YEAR)
    if (sameDay) return "今天"
    now.add(Calendar.DAY_OF_YEAR, -1)
    val yesterday = now.get(Calendar.YEAR) == then.get(Calendar.YEAR) &&
        now.get(Calendar.DAY_OF_YEAR) == then.get(Calendar.DAY_OF_YEAR)
    if (yesterday) return "昨天"
    return SimpleDateFormat("MM-dd", Locale.getDefault()).format(Date(time))
}

private fun formatTime(time: Long): String =
    if (time <= 0L) "--:--" else SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(time))
