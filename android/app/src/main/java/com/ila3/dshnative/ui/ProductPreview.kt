package com.ila3.dshnative.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
