package com.ila3.dshnative.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Lock
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * High-fidelity product design mockups (not wired to the ViewModel yet).
 * Rendered by NativeScreenshotTest for design review.
 */
private object Ink {
    val canvas = Color(0xFFF6F7FB)
    val surface = Color(0xFFFFFFFF)
    val ink = Color(0xFF0B1220)
    val ink2 = Color(0xFF334155)
    val muted = Color(0xFF64748B)
    val faint = Color(0xFF94A3B8)
    val line = Color(0xFFE9EEF8)
    val surface2 = Color(0xFFF1F4FB)
    val brand = Color(0xFF4D6BFE)
    val brandSoft = Color(0xFFEEF2FF)
    val mint = Color(0xFF14B8A6)
    val success = Color(0xFF10B981)
    val danger = Color(0xFFEF4444)
    val warn = Color(0xFFF59E0B)
}

private val CardShape = RoundedCornerShape(18.dp)

@Composable
private fun ProductCanvas(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().background(Ink.canvas).statusBarsPadding()) { content() }
}

@Composable
private fun ProductTopBar(title: String, subtitle: String? = null, actions: @Composable () -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Ink.ink, fontSize = 26.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.6).sp)
            subtitle?.let {
                Text(it, color = Ink.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
            }
        }
        actions()
    }
}

@Composable
private fun BottomNav(active: Int) {
    val items = listOf(
        Triple("会话", Icons.Filled.Chat, 0),
        Triple("收件箱", Icons.Filled.Inbox, 1),
        Triple("工作区", Icons.Filled.Folder, 2),
        Triple("设置", Icons.Filled.Settings, 3),
    )
    Column {
        Box(Modifier.fillMaxWidth().height(1.dp).background(Ink.line))
        Row(
            Modifier.fillMaxWidth().background(Ink.surface).padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceAround,
        ) {
            items.forEach { (label, icon, index) ->
                Column(
                    Modifier.widthIn(min = 64.dp).clickable { }.padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        icon,
                        contentDescription = label,
                        tint = if (active == index) Ink.brand else Ink.faint,
                        modifier = Modifier.size(22.dp),
                    )
                    Text(
                        label,
                        color = if (active == index) Ink.brand else Ink.faint,
                        fontSize = 10.sp,
                        fontWeight = if (active == index) FontWeight.SemiBold else FontWeight.Normal,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun Segmented(active: Int) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp)
            .background(Ink.surface, RoundedCornerShape(14.dp)).padding(4.dp),
    ) {
        listOf("会话", "收件箱 · 3").forEachIndexed { index, label ->
            Box(
                Modifier.weight(1f)
                    .background(if (active == index) Ink.ink else Color.Transparent, RoundedCornerShape(11.dp))
                    .clickable { }
                    .padding(vertical = 9.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    color = if (active == index) Color.White else Ink.muted,
                    fontSize = 13.sp,
                    fontWeight = if (active == index) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
        }
    }
}

@Composable
private fun RunningCard() {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp)
            .background(Brush.linearGradient(listOf(Color(0xFF3347D8), Color(0xFF5B78FF))), RoundedCornerShape(20.dp))
            .clickable { }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("2 个任务正在运行", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text("插件升级与手机端功能设计 · 已运行 12 分钟", color = Color.White.copy(alpha = 0.78f), fontSize = 11.sp, modifier = Modifier.padding(top = 3.dp))
        }
        Box(Modifier.size(30.dp).background(Color.White.copy(alpha = 0.16f), CircleShape), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun ApprovalCard() {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp)
            .background(Ink.surface, CardShape)
            .border(1.dp, Ink.line, CardShape)
            .clickable { }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(4.dp).height(38.dp).background(Ink.warn, RoundedCornerShape(4.dp)))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("需要你批准", color = Ink.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text("bash 删除 build/ 目录 · 修改系统配置", color = Ink.muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
        }
        Text("去处理", color = Ink.brand, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun SessionItem(initial: String, title: String, snippet: String, meta: String, running: Boolean, tint: Color) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp)
            .background(Ink.surface, CardShape)
            .clickable { }
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(40.dp).background(tint.copy(alpha = 0.12f), CircleShape),
            contentAlignment = Alignment.Center,
        ) { Text(initial, color = tint, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, color = Ink.ink, fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (running) {
                    Spacer(Modifier.width(6.dp))
                    Box(Modifier.size(6.dp).background(Ink.success, CircleShape))
                }
            }
            Text(snippet, color = Ink.muted, fontSize = 11.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
        }
        Text(meta, color = Ink.faint, fontSize = 10.5.sp)
    }
}

@Composable
private fun SectionCaption(text: String) {
    Text(
        text,
        color = Ink.faint,
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.4.sp,
        modifier = Modifier.padding(start = 22.dp, top = 12.dp, bottom = 2.dp),
    )
}

@Composable
internal fun ProductHomePreview() {
    ProductCanvas {
        ProductTopBar("会话", "2 个运行中 · 27 个会话") {
            Box(Modifier.size(34.dp).background(Ink.surface, CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Search, contentDescription = null, tint = Ink.ink2, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(8.dp))
            Box(Modifier.size(34.dp).background(Ink.surface, CircleShape), contentAlignment = Alignment.Center) {
                Text("D", color = Ink.brand, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
        Segmented(active = 0)
        Spacer(Modifier.height(12.dp))
        RunningCard()
        Spacer(Modifier.height(10.dp))
        ApprovalCard()
        SectionCaption("今天")
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SessionItem("插", "插件升级与手机端功能设计", "正在生成：通知与任务进度页", "12:36", true, Ink.brand)
            SessionItem("D", "dsh-im-omni 安装与手机配对", "已完成 0.4.2 发布，等待验证", "12:19", false, Ink.mint)
            SessionItem("为", "为 stream-polish 增加配置项", "摘要已更新，等你确认", "11:48", false, Color(0xFF8B5CF6))
        }
        SectionCaption("昨天")
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SessionItem("询", "询问安卓模拟器插件", "模拟器已启动，面板验证完成", "21:30", false, Ink.warn)
        }
        Spacer(Modifier.weight(1f))
        BottomNav(active = 0)
    }
}

@Composable
private fun MockAssistantCard() {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.size(28.dp).background(Ink.brand, CircleShape), contentAlignment = Alignment.Center) {
            Text("D", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Column(
            Modifier.weight(1f).background(Ink.surface, RoundedCornerShape(20.dp)).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("DSH", color = Ink.brand, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Icon(Icons.Filled.Translate, contentDescription = null, tint = Ink.faint, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(12.dp))
                Icon(Icons.Filled.MoreVert, contentDescription = null, tint = Ink.faint, modifier = Modifier.size(15.dp))
            }
            Row(
                Modifier.fillMaxWidth().background(Ink.canvas, RoundedCornerShape(12.dp)).padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("思考", color = Ink.muted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                Text("展开", color = Ink.faint, fontSize = 10.sp)
            }
            Text(
                "通知与任务进度页已经接好，现在把「收件箱」的审批动作补上，再统一视觉。",
                color = Ink.ink2,
                fontSize = 14.sp,
                lineHeight = 21.sp,
            )
            Box(
                Modifier.fillMaxWidth().background(Color(0xFF0F172A), RoundedCornerShape(12.dp)).padding(12.dp),
            ) {
                Text(
                    "./gradlew assembleRelease\nadb install -r app-release.apk",
                    color = Color(0xFF7DD3FC),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                )
            }
        }
    }
}

@Composable
private fun MockToolRow() {
    Row(
        Modifier.fillMaxWidth().background(Ink.surface, RoundedCornerShape(14.dp)).padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(6.dp).background(Ink.success, CircleShape))
        Spacer(Modifier.width(8.dp))
        Text("bash", color = Ink.ink, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
        Text("gradlew assembleRelease", color = Ink.faint, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(start = 8.dp))
        Text("完成", color = Ink.faint, fontSize = 10.sp)
    }
}

@Composable
internal fun ProductChatPreview() {
    ProductCanvas {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = Ink.ink, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text("插件升级与手机端功能设计", color = Ink.ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("已连接 · deepseek-flash", color = Ink.faint, fontSize = 10.5.sp, modifier = Modifier.padding(top = 1.dp))
            }
            Icon(Icons.Filled.MoreVert, contentDescription = null, tint = Ink.ink, modifier = Modifier.size(20.dp))
        }
        Column(Modifier.weight(1f).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("今天 20:45", color = Ink.faint, fontSize = 10.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Text(
                    "把通知和任务进度页再做精致一点，像产品而不是 demo",
                    color = Ink.ink,
                    fontSize = 14.sp,
                    lineHeight = 21.sp,
                    modifier = Modifier.widthIn(max = 280.dp)
                        .background(Ink.brandSoft, RoundedCornerShape(20.dp))
                        .padding(horizontal = 14.dp, vertical = 11.dp),
                )
            }
            MockAssistantCard()
            MockToolRow()
        }
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp)
                .background(Ink.surface, RoundedCornerShape(26.dp))
                .border(1.dp, Ink.line, RoundedCornerShape(26.dp))
                .padding(horizontal = 8.dp, vertical = 8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Add, contentDescription = null, tint = Ink.muted, modifier = Modifier.size(20.dp).padding(start = 4.dp))
                Spacer(Modifier.width(10.dp))
                Text("跟进…", color = Ink.faint, fontSize = 14.sp, modifier = Modifier.weight(1f))
                Icon(Icons.Filled.Mic, contentDescription = null, tint = Ink.muted, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Box(Modifier.size(36.dp).background(Ink.brand, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.ArrowUpward, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
private fun SettingsRow(icon: ImageVector, label: String, value: String? = null, switch: Boolean? = null, danger: Boolean = false) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = if (danger) Ink.danger else Ink.muted, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(12.dp))
        Text(label, color = if (danger) Ink.danger else Ink.ink, fontSize = 14.sp, modifier = Modifier.weight(1f))
        when {
            switch != null -> Switch(
                checked = switch,
                onCheckedChange = {},
                colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = Ink.brand),
            )
            value != null -> Text(value, color = Ink.faint, fontSize = 12.sp)
        }
        if (switch == null && !danger) {
            Spacer(Modifier.width(4.dp))
            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = Ink.faint, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun SettingsGroup(title: String, content: @Composable () -> Unit) {
    Column(Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) {
        Text(title, color = Ink.faint, fontSize = 11.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.4.sp, modifier = Modifier.padding(start = 4.dp, bottom = 6.dp))
        Column(Modifier.fillMaxWidth().background(Ink.surface, RoundedCornerShape(18.dp))) { content() }
    }
}

@Composable
internal fun ProductSettingsPreview() {
    ProductCanvas {
        ProductTopBar("设置", "10.0.2.2 · 已连接")
        SettingsGroup("连接") {
            SettingsRow(Icons.Filled.Chat, "当前 DSH", "10.0.2.2")
            Row(Modifier.padding(horizontal = 16.dp).height(1.dp).fillMaxWidth().background(Ink.line)) {}
            SettingsRow(Icons.Filled.Notifications, "通知提醒", switch = true)
        }
        SettingsGroup("外观") {
            SettingsRow(Icons.Filled.Settings, "主题", "云白")
            Row(Modifier.padding(horizontal = 16.dp).height(1.dp).fillMaxWidth().background(Ink.line)) {}
            SettingsRow(Icons.Filled.Mic, "语音", "系统识别")
        }
        SettingsGroup("阅读") {
            SettingsRow(Icons.Filled.Translate, "思考默认展开", switch = true)
            Row(Modifier.padding(horizontal = 16.dp).height(1.dp).fillMaxWidth().background(Ink.line)) {}
            SettingsRow(Icons.Filled.Chat, "长消息折叠", switch = true)
        }
        SettingsGroup("关于") {
            SettingsRow(Icons.Filled.Settings, "版本", "0.19.5")
        }
        Spacer(Modifier.weight(1f))
        BottomNav(active = 3)
    }
}

@Composable
internal fun ProductInboxPreview() {
    val pending = com.ila3.dshnative.protocol.DshClient.PendingQuestion(
        callId = "preview-question",
        state = "open",
        questions = listOf(
            com.ila3.dshnative.protocol.DshClient.QuestionItem(
                id = "q1",
                question = "是否继续生成 diff？",
                header = "确认",
                detail = "将读取最近一次修改并生成补丁。",
                options = listOf(
                    com.ila3.dshnative.protocol.DshClient.QuestionOption("继续", "生成并展示 diff"),
                    com.ila3.dshnative.protocol.DshClient.QuestionOption("暂不", "保留当前改动"),
                ),
                multiSelect = false,
            ),
        ),
    )
    Column(
        Modifier.fillMaxSize().background(Ink.canvas).padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text("收件箱", color = Ink.ink, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Text("需要你处理的 1 项", color = Ink.muted, fontSize = 12.sp)
        Spacer(Modifier.height(18.dp))
        InboxQuestionContent(pending, sessionTitle = "dsh-phone-remote", onAnswer = {}, onOpen = {})
        Spacer(Modifier.height(14.dp))
        Text("点击选项即可直接回传给 Agent，无需进入会话。", color = Ink.muted, fontSize = 11.sp)
    }
}

/* ============================ 完整手机端 IA 设计稿 ============================ */

@Composable
private fun FullBottomNav(active: Int) {
    val items = listOf(
        Triple("收件箱", Icons.Filled.Inbox, 0),
        Triple("任务", Icons.Filled.Checklist, 1),
        Triple("工作区", Icons.Filled.Folder, 2),
        Triple("设置", Icons.Filled.Settings, 3),
    )
    Column {
        Box(Modifier.fillMaxWidth().height(1.dp).background(Ink.line))
        Row(
            Modifier.fillMaxWidth().background(Ink.surface).padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceAround,
        ) {
            items.forEach { (label, icon, index) ->
                Column(
                    Modifier.widthIn(min = 64.dp).padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        icon,
                        contentDescription = label,
                        tint = if (active == index) Ink.brand else Ink.faint,
                        modifier = Modifier.size(22.dp),
                    )
                    Text(
                        label,
                        color = if (active == index) Ink.brand else Ink.faint,
                        fontSize = 10.sp,
                        fontWeight = if (active == index) FontWeight.SemiBold else FontWeight.Normal,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun Pill(text: String, background: Color, foreground: Color, border: Color? = null) {
    Box(
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(background)
            .then(if (border != null) Modifier.border(1.dp, border, RoundedCornerShape(10.dp)) else Modifier)
            .padding(horizontal = 12.dp, vertical = 7.dp),
    ) {
        Text(text, color = foreground, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun FullApprovalCard() {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp)
            .background(Ink.surface, RoundedCornerShape(18.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(4.dp).height(34.dp).background(Ink.warn, RoundedCornerShape(4.dp)))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("需要批准", color = Ink.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text("bash · 删除构建目录", color = Ink.muted, fontSize = 11.sp)
            }
            Pill("高风险", Color(0xFFFEF2F2), Ink.danger, Color(0xFFFECACA))
        }
        Box(
            Modifier.fillMaxWidth().background(Ink.canvas, RoundedCornerShape(10.dp)).padding(10.dp),
        ) {
            Text("rm -rf build/", color = Ink.ink2, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill("拒绝", Ink.canvas, Ink.ink2, Ink.line)
            Pill("批准", Ink.brand, Color.White)
            Spacer(Modifier.weight(1f))
            Text("查看影响", color = Ink.muted, fontSize = 11.sp)
        }
    }
}

@Composable
private fun FullQuestionCard() {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp)
            .background(Ink.surface, RoundedCornerShape(18.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(4.dp).height(28.dp).background(Ink.brand, RoundedCornerShape(4.dp)))
            Spacer(Modifier.width(10.dp))
            Text("需要你的回答", color = Ink.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
        Text("是否继续生成 diff？", color = Ink.ink2, fontSize = 13.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill("继续", Ink.brand, Color.White)
            Pill("暂不", Ink.canvas, Ink.ink2, Ink.line)
        }
    }
}

@Composable
private fun FullFailureCard() {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp)
            .background(Ink.surface, RoundedCornerShape(18.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.ErrorOutline, contentDescription = null, tint = Ink.danger, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text("构建失败", color = Ink.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text("assembleRelease 退出码 1 · 12:06", color = Ink.muted, fontSize = 11.sp)
        }
        Text("查看日志", color = Ink.brand, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun RunningTaskRow(title: String, progress: Float, meta: String) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp)
            .background(Ink.surface, RoundedCornerShape(18.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(26.dp).background(Ink.brandSoft, CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Ink.brand, modifier = Modifier.size(14.dp))
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = Ink.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text(meta, color = Ink.muted, fontSize = 11.sp)
            }
            Text("${(progress * 100).toInt()}%", color = Ink.brand, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
        Box(Modifier.fillMaxWidth().height(4.dp).background(Ink.line, CircleShape)) {
            Box(Modifier.fillMaxWidth(progress).height(4.dp).background(Ink.brand, CircleShape))
        }
    }
}

@Composable
internal fun ProductInboxFirstPreview() {
    ProductCanvas {
        ProductTopBar("收件箱", "3 项需要你处理 · 2 个任务运行中") {
            Box(Modifier.size(34.dp).background(Ink.surface, CircleShape), contentAlignment = Alignment.Center) {
                Text("D", color = Ink.brand, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp)
                .background(Ink.surface, RoundedCornerShape(14.dp)).padding(4.dp),
        ) {
            listOf("待处理 · 3", "运行中 · 2", "已完成").forEachIndexed { index, label ->
                Box(
                    Modifier.weight(1f)
                        .background(if (index == 0) Ink.ink else Color.Transparent, RoundedCornerShape(11.dp))
                        .padding(vertical = 9.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label,
                        color = if (index == 0) Color.White else Ink.muted,
                        fontSize = 13.sp,
                        fontWeight = if (index == 0) FontWeight.SemiBold else FontWeight.Normal,
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        FullApprovalCard()
        Spacer(Modifier.height(10.dp))
        FullQuestionCard()
        Spacer(Modifier.height(10.dp))
        FullFailureCard()
        SectionCaption("运行中")
        RunningTaskRow("修复构建失败", 0.45f, "bash · 已运行 12 分钟")
        Spacer(Modifier.height(8.dp))
        RunningTaskRow("给插件补审批接口", 0.2f, "edit · 已运行 4 分钟")
        Spacer(Modifier.weight(1f))
        FullBottomNav(0)
    }
}

@Composable
private fun FullGoalCard(goal: String) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp)
            .background(Ink.surface, RoundedCornerShape(18.dp))
            .padding(14.dp),
    ) {
        Text("目标", color = Ink.brand, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
        Text(goal, color = Ink.ink, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun TodoLine(text: String, status: String) {
    val icon = when (status) {
        "done" -> Icons.Filled.CheckCircle
        "doing" -> Icons.Filled.PlayArrow
        else -> Icons.Filled.RadioButtonUnchecked
    }
    val tint = when (status) {
        "done" -> Ink.success
        "doing" -> Ink.brand
        else -> Ink.faint
    }
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(17.dp))
        Spacer(Modifier.width(10.dp))
        Text(
            text,
            color = if (status == "done") Ink.muted else Ink.ink2,
            fontSize = 13.sp,
            textDecoration = if (status == "done") androidx.compose.ui.text.style.TextDecoration.LineThrough else null,
        )
    }
}

@Composable
private fun FullTodoCard() {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp)
            .background(Ink.surface, RoundedCornerShape(18.dp))
            .padding(14.dp),
    ) {
        Text("待办", color = Ink.muted, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
        TodoLine("定位 assembleRelease 失败原因", "done")
        TodoLine("修改 signingConfig 并验证", "done")
        TodoLine("生成 diff 并等待批准", "doing")
        TodoLine("发布 0.19.10 并推送", "todo")
    }
}

@Composable
private fun FullApprovalBanner() {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp)
            .background(Color(0xFFFFFBEB), RoundedCornerShape(16.dp))
            .border(1.dp, Color(0xFFFDE68A), RoundedCornerShape(16.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.ErrorOutline, contentDescription = null, tint = Ink.warn, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text("等待你批准", color = Ink.ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text("修改 gradle 签名配置", color = Ink.muted, fontSize = 11.sp)
        }
        Text("批准", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.background(Ink.brand, RoundedCornerShape(9.dp)).padding(horizontal = 12.dp, vertical = 6.dp))
    }
}

@Composable
private fun TimelineRow(time: String, title: String, meta: String, state: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Text(time, color = Ink.faint, fontSize = 10.sp, modifier = Modifier.width(42.dp))
        Box(Modifier.padding(top = 4.dp).size(7.dp).background(
            if (state == "done") Ink.success else if (state == "doing") Ink.brand else Ink.warn,
            CircleShape,
        ))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f).padding(bottom = 10.dp)) {
            Text(title, color = Ink.ink2, fontSize = 13.sp)
            Text(meta, color = Ink.muted, fontSize = 10.5.sp, modifier = Modifier.padding(top = 1.dp))
        }
    }
}

@Composable
internal fun ProductTaskDetailPreview() {
    ProductCanvas {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = Ink.ink, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text("修复构建失败", color = Ink.ink, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                Text("deepseek-flash · 12 分钟", color = Ink.faint, fontSize = 10.5.sp)
            }
            Pill("运行中", Ink.brandSoft, Ink.brand)
        }
        FullGoalCard("让 assembleRelease 通过，并发布 0.19.10")
        Spacer(Modifier.height(10.dp))
        FullTodoCard()
        Spacer(Modifier.height(10.dp))
        FullApprovalBanner()
        SectionCaption("时间线")
        Column(Modifier.padding(horizontal = 20.dp)) {
            TimelineRow("12:01", "生成计划 · 4 个步骤", "Plan", "done")
            TimelineRow("12:02", "bash · ./gradlew assembleRelease", "3m 12s", "done")
            TimelineRow("12:05", "请求批准：修改 gradle 签名配置", "等待中", "doing")
            TimelineRow("12:06", "生成 diff.patch", "12 KB", "done")
        }
        SectionCaption("产物")
        Row(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("report.md", "diff.patch", "build.log").forEach { name ->
                Row(
                    Modifier.background(Ink.surface, RoundedCornerShape(12.dp)).padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Description, contentDescription = null, tint = Ink.brand, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(name, color = Ink.ink2, fontSize = 11.sp)
                }
            }
        }
        Spacer(Modifier.weight(1f))
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                Modifier.background(Ink.canvas, RoundedCornerShape(14.dp)).padding(horizontal = 16.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Stop, contentDescription = null, tint = Ink.danger, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("停止", color = Ink.danger, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            }
            Row(
                Modifier.weight(1f).background(Ink.brand, RoundedCornerShape(14.dp)).padding(vertical = 11.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                Text("输入引导", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun DirRow(name: String, meta: String) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp)
            .background(Ink.surface, RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(36.dp).background(Ink.line, RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.FolderOpen, contentDescription = null, tint = Ink.brand, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(name, color = Ink.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(meta, color = Ink.muted, fontSize = 11.sp)
        }
        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = Ink.faint, modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun ArtifactRow(name: String, meta: String, time: String) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp)
            .background(Ink.surface, RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Description, contentDescription = null, tint = Ink.brand, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(10.dp))
        Text(name, color = Ink.ink2, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Text(meta, color = Ink.muted, fontSize = 10.5.sp)
        Spacer(Modifier.width(10.dp))
        Text(time, color = Ink.faint, fontSize = 10.sp)
    }
}

@Composable
internal fun ProductWorkspacePreview() {
    ProductCanvas {
        ProductTopBar("工作区", "dsh-phone-remote · 2 个任务") {
            Box(Modifier.size(34.dp).background(Ink.surface, CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Search, contentDescription = null, tint = Ink.ink2, modifier = Modifier.size(17.dp))
            }
        }
        Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("dsh-phone-remote", color = Ink.faint, fontSize = 11.sp)
            Text("  /  ", color = Ink.faint, fontSize = 11.sp)
            Text("android", color = Ink.ink2, fontSize = 11.sp, fontWeight = FontWeight.Medium)
        }
        Spacer(Modifier.height(10.dp))
        DirRow("android/", "42 个文件 · 最近修改 12:06")
        Spacer(Modifier.height(8.dp))
        DirRow("design/", "3 个文件 · 12 分钟前")
        Spacer(Modifier.height(8.dp))
        DirRow("updates/", "8 个文件 · 昨天")
        SectionCaption("最近产物")
        ArtifactRow("report.md", "12 KB", "12:06")
        Spacer(Modifier.height(8.dp))
        ArtifactRow("diff.patch", "4 KB", "12:06")
        Spacer(Modifier.height(8.dp))
        ArtifactRow("screenshot.png", "220 KB", "12:07")
        Spacer(Modifier.weight(1f))
        FullBottomNav(2)
    }
}

/* ============================ 包 1：Now / 任务 / 文件 / 设置 ============================ */

@Composable
private fun NowBottomNav(active: Int) {
    val items = listOf(
        Triple("现在", Icons.Filled.Home, 0),
        Triple("任务", Icons.Filled.Checklist, 1),
        Triple("文件", Icons.Filled.Folder, 2),
        Triple("设置", Icons.Filled.Settings, 3),
    )
    Column {
        Box(Modifier.fillMaxWidth().height(1.dp).background(Ink.line))
        Row(
            Modifier.fillMaxWidth().background(Ink.surface).padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceAround,
        ) {
            items.forEach { (label, icon, index) ->
                Column(
                    Modifier.widthIn(min = 64.dp).padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        icon,
                        contentDescription = label,
                        tint = if (active == index) Ink.brand else Ink.faint,
                        modifier = Modifier.size(22.dp),
                    )
                    Text(
                        label,
                        color = if (active == index) Ink.brand else Ink.faint,
                        fontSize = 10.sp,
                        fontWeight = if (active == index) FontWeight.SemiBold else FontWeight.Normal,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun NowSection(title: String, caption: String? = null) {
    Row(
        Modifier.fillMaxWidth().padding(start = 22.dp, end = 20.dp, top = 14.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, color = Ink.muted, fontSize = 11.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.4.sp)
        if (caption != null) {
            Spacer(Modifier.weight(1f))
            Text(caption, color = Ink.faint, fontSize = 10.sp)
        }
    }
}

@Composable
private fun DoneTaskRow(title: String, meta: String, time: String) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp)
            .background(Ink.surface, RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = Ink.success, modifier = Modifier.size(17.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Ink.ink, fontSize = 13.5.sp, fontWeight = FontWeight.Medium)
            Text(meta, color = Ink.muted, fontSize = 10.5.sp)
        }
        Text(time, color = Ink.faint, fontSize = 10.sp)
    }
}

@Composable
internal fun ProductNowPreview() {
    ProductCanvas {
        ProductTopBar("现在", "2 项需要你 · 2 个任务运行中") {
            Box(Modifier.size(34.dp).background(Ink.surface, CircleShape), contentAlignment = Alignment.Center) {
                Text("D", color = Ink.brand, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
        NowSection("需要你", "按风险排序")
        FullApprovalCard()
        Spacer(Modifier.height(10.dp))
        FullQuestionCard()
        NowSection("运行中", "2 个")
        RunningTaskRow("修复构建失败", 0.45f, "bash · 已运行 12 分钟")
        Spacer(Modifier.height(8.dp))
        RunningTaskRow("给插件补审批接口", 0.2f, "edit · 已运行 4 分钟")
        NowSection("最近完成", "今天")
        DoneTaskRow("发布 0.19.9", "收件箱快捷回答 + 日期分隔", "14:52")
        Spacer(Modifier.height(8.dp))
        DoneTaskRow("设置页图标行", "Ink 设计系统落地", "14:30")
        Spacer(Modifier.height(12.dp))
        }
        NowBottomNav(0)
    }
}

/* ---------------------------- 导航自定义 ---------------------------- */

@Composable
private fun NavToggleRow(
    icon: ImageVector,
    label: String,
    visible: Boolean,
    draggable: Boolean = true,
    locked: Boolean = false,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Filled.DragHandle,
            contentDescription = "拖动排序",
            tint = if (draggable) Ink.faint else Ink.line,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(10.dp))
        Box(Modifier.size(30.dp).background(Ink.surface2, RoundedCornerShape(9.dp)), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = if (visible) Ink.brand else Ink.faint, modifier = Modifier.size(16.dp))
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(label, color = if (visible) Ink.ink else Ink.muted, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            if (locked) Text("固定显示", color = Ink.faint, fontSize = 10.sp)
        }
        if (locked) {
            Icon(Icons.Filled.Lock, contentDescription = "固定", tint = Ink.faint, modifier = Modifier.size(16.dp))
        } else {
            Icon(
                if (visible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                contentDescription = if (visible) "隐藏" else "显示",
                tint = if (visible) Ink.brand else Ink.faint,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun NavDivider() {
    Box(Modifier.fillMaxWidth().padding(start = 74.dp).height(1.dp).background(Ink.line))
}

@Composable
internal fun ProductNavCustomPreview() {
    ProductCanvas {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = Ink.ink, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text("底部导航", color = Ink.ink, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                Text("拖动排序 · 控制显示 · 最多 4 个", color = Ink.faint, fontSize = 10.5.sp)
            }
        }
        Text(
            "隐藏的入口会收进「更多」，不会丢失。",
            color = Ink.muted,
            fontSize = 11.sp,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
        )
        Spacer(Modifier.height(8.dp))
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp)
                .background(Ink.surface, RoundedCornerShape(18.dp)),
        ) {
            NavToggleRow(Icons.Filled.Home, "现在", true, locked = true)
            NavDivider()
            NavToggleRow(Icons.Filled.Checklist, "任务", true)
            NavDivider()
            NavToggleRow(Icons.Filled.Folder, "文件", true)
            NavDivider()
            NavToggleRow(Icons.Filled.Settings, "设置", true)
            NavDivider()
            NavToggleRow(Icons.Filled.MoreHoriz, "自动化", false)
            NavDivider()
            NavToggleRow(Icons.Filled.People, "团队", false)
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("预览", color = Ink.faint, fontSize = 10.5.sp, modifier = Modifier.weight(1f))
            Text("恢复默认", color = Ink.brand, fontSize = 11.sp, fontWeight = FontWeight.Medium)
        }
        Spacer(Modifier.height(6.dp))
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp)
                .background(Ink.surface, RoundedCornerShape(16.dp))
                .padding(vertical = 8.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                listOf("现在" to Icons.Filled.Home, "任务" to Icons.Filled.Checklist, "文件" to Icons.Filled.Folder, "设置" to Icons.Filled.Settings).forEachIndexed { index, pair ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(pair.second, contentDescription = pair.first, tint = if (index == 0) Ink.brand else Ink.faint, modifier = Modifier.size(22.dp))
                        Text(pair.first, color = if (index == 0) Ink.brand else Ink.faint, fontSize = 10.sp, modifier = Modifier.padding(top = 2.dp))
                    }
                }
            }
        }
        Spacer(Modifier.weight(1f))
        Box(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp)
                .background(Ink.canvas, RoundedCornerShape(14.dp)).padding(12.dp),
        ) {
            Text(
                "规则：至少保留 2 个；「现在」固定；超出 4 个自动收进「更多」。",
                color = Ink.muted,
                fontSize = 10.5.sp,
                lineHeight = 15.sp,
            )
        }
    }
}
