package com.ila3.dshnative.ui

import android.content.Intent
import android.net.Uri
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.ui.viewinterop.AndroidView
import com.ila3.dshnative.protocol.ChatItem
import com.ila3.dshnative.protocol.SshHost
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket

private val ToolboxTabs = listOf(
    "search" to "搜索",
    "skills" to "技能",
    "tools" to "工具",
    "browser" to "浏览器",
    "ssh" to "SSH",
    "attachments" to "附件",
    "server" to "服务器",
    "agency" to "专家",
    "files" to "文件",
)

@Composable
internal fun ToolboxScreen(vm: NativeViewModel) {
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 8.dp, end = 16.dp, top = 6.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { vm.closeToolbox() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = DshText, modifier = Modifier.size(20.dp))
            }
            Column(Modifier.weight(1f)) {
                Text("工具箱", color = DshText, fontSize = 18.sp, fontWeight = FontWeight.Medium)
                Text("搜索 · 技能 · 工具 · 浏览器 · SSH · 服务器 · 文件", color = DshMuted, fontSize = 9.sp, fontFamily = DshMono)
            }
        }
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            ToolboxTabs.forEach { (id, label) ->
                val active = vm.toolboxSection == id
                Text(
                    label,
                    color = if (active) DshText else DshMuted,
                    fontSize = 11.sp,
                    fontFamily = DshMono,
                    modifier = Modifier
                        .background(if (active) DshSurface else Color.Transparent, RoundedCornerShape(6.dp))
                        .border(1.dp, if (active) DshBorder else Color.Transparent, RoundedCornerShape(6.dp))
                        .clickable {
                            vm.toolboxSection = id
                            if (id == "skills") vm.loadSkills()
                        }
                        .padding(horizontal = 9.dp, vertical = 6.dp),
                )
            }
        }
        HorizontalDivider(color = DshBorder, thickness = 1.dp, modifier = Modifier.padding(top = 6.dp))
        when (vm.toolboxSection) {
            "search" -> SearchSection(vm)
            "skills" -> SkillsSection(vm)
            "tools" -> ToolsSection(vm)
            "browser" -> BrowserSection(vm)
            "ssh" -> SshSection(vm)
            "attachments" -> AttachmentsSection(vm)
            "server" -> ServerSection(vm)
            "agency" -> AgencySection(vm)
            else -> FilesSection(vm)
        }
    }
}

/* --------------------------------- 专家 --------------------------------- */
@Composable
private fun AgencySection(vm: NativeViewModel) {
    LaunchedEffect(Unit) { if (vm.agencyRaw == null) vm.loadAgency() }
    val root = vm.agencyRaw
    val value = root?.optJSONObject("result")?.optJSONObject("value")
        ?: root?.optJSONObject("value")
        ?: root?.optJSONObject("result")
    val teams = value?.optJSONArray("teams")
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (vm.agencyBusy) "读取中…" else "专家团队",
                color = DshText,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
            )
            Text("刷新", color = DshTeal, fontSize = 10.sp, fontFamily = DshMono, modifier = Modifier.clickable { vm.loadAgency() })
        }
        if (vm.agencyError.isNotBlank()) {
            Text(vm.agencyError, color = DshDanger, fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp))
        }
        if (teams == null) {
            val preview = (value ?: root)?.toString(2).orEmpty().take(1200)
            Text(
                if (preview.isBlank()) "暂无数据" else preview,
                color = DshMuted,
                fontSize = 10.sp,
                fontFamily = DshMono,
                modifier = Modifier.padding(top = 10.dp),
            )
        } else {
            for (i in 0 until teams.length()) {
                val team = teams.optJSONObject(i) ?: continue
                val name = team.optString("name").ifBlank { team.optString("title") }
                val description = team.optString("description")
                val members = team.optJSONArray("members")
                Column(
                    Modifier.fillMaxWidth().clickable { vm.openAgencyTeam(team) }.padding(vertical = 8.dp),
                ) {
                    Text(name, color = DshText, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    if (description.isNotBlank()) {
                        Text(description, color = DshMuted, fontSize = 10.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    if (members != null) {
                        val slugs = buildList {
                            for (m in 0 until members.length()) {
                                val slug = members.optJSONObject(m)?.optString("expertSlug").orEmpty()
                                if (slug.isNotBlank()) add(slug)
                            }
                        }
                        Text(
                            "${members.length()} 位成员 · " + slugs.joinToString(" · "),
                            color = DshMuted,
                            fontSize = 9.sp,
                            fontFamily = DshMono,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                HorizontalDivider(color = DshBorder, thickness = 1.dp)
            }
        }
    }
    vm.selectedAgencyTeam?.let { team ->
        val members = team.optJSONArray("members")
        AlertDialog(
            onDismissRequest = { vm.closeAgencyTeam() },
            title = { Text(team.optString("name"), color = DshText, fontSize = 15.sp) },
            text = {
                Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                    val description = team.optString("description")
                    if (description.isNotBlank()) {
                        Text(description, color = DshText, fontSize = 12.sp, lineHeight = 18.sp)
                        Spacer(Modifier.height(10.dp))
                    }
                    if (members != null) {
                        for (i in 0 until members.length()) {
                            val member = members.optJSONObject(i) ?: continue
                            Text(
                                member.optString("expertSlug"),
                                color = DshText,
                                fontSize = 12.sp,
                                fontFamily = DshMono,
                                modifier = Modifier.padding(top = 6.dp),
                            )
                            Text(
                                member.optString("duty"),
                                color = DshMuted,
                                fontSize = 10.sp,
                                lineHeight = 15.sp,
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { vm.useAgencyTeam(team) }) {
                    Text("填入输入框", color = DshTeal)
                }
            },
            dismissButton = {
                TextButton(onClick = { vm.closeAgencyTeam() }) { Text("关闭", color = DshMuted) }
            },
            containerColor = DshSurface,
            shape = RoundedCornerShape(10.dp),
        )
    }
}

/* -------------------------------- 服务器 -------------------------------- */
private fun jsonNum(obj: JSONObject?, key: String): Double? {
    if (obj == null || !obj.has(key) || obj.isNull(key)) return null
    val value = obj.optDouble(key, Double.NaN)
    return if (value.isNaN()) null else value
}

@Composable
private fun ServerSection(vm: NativeViewModel) {
    LaunchedEffect(Unit) {
        if (vm.serverSnapshot == null) vm.loadServerSnapshot()
    }
    val snapshot = vm.serverSnapshot
    val sample = snapshot?.optJSONObject("sample")
    val cpu = sample?.optJSONObject("cpu")
    val memory = sample?.optJSONObject("memory")
    val processes = sample?.optJSONObject("processes")
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                val hostValue = sample?.opt("host")
                val hostText = when (hostValue) {
                    is JSONObject -> hostValue.optString("hostname").ifBlank { "本机" }
                    is String -> hostValue.ifBlank { "本机" }
                    else -> "本机"
                }
                Text(hostText, color = DshText, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                val detail = when (hostValue) {
                    is JSONObject -> listOf(
                        hostValue.optString("platform"),
                        hostValue.optString("release"),
                        if (hostValue.has("cores")) "${hostValue.optInt("cores")} 核" else "",
                    ).filter { it.isNotBlank() }.joinToString(" · ")
                    else -> ""
                }
                Text(
                    if (vm.serverBusy) "读取中…" else detail.ifBlank { "server-monitor · snapshot" },
                    color = DshMuted,
                    fontSize = 9.sp,
                    fontFamily = DshMono,
                )
            }
            Text(
                "刷新",
                color = DshTeal,
                fontSize = 10.sp,
                fontFamily = DshMono,
                modifier = Modifier.clickable { vm.loadServerSnapshot() },
            )
        }
        if (vm.serverError.isNotBlank()) {
            Spacer(Modifier.height(10.dp))
            Text(vm.serverError, color = DshDanger, fontSize = 11.sp)
        }
        Spacer(Modifier.height(14.dp))
        val metrics = listOfNotNull(
            jsonNum(cpu, "usage")?.let { "CPU" to it },
            jsonNum(memory, "usagePct")?.let { "内存" to it },
        )
        metrics.forEach { (label, value) ->
            Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(label, color = DshMuted, fontSize = 10.sp, fontFamily = DshMono, modifier = Modifier.width(40.dp))
                Box(Modifier.weight(1f).height(4.dp).background(DshBorder, RoundedCornerShape(2.dp))) {
                    Box(
                        Modifier.fillMaxWidth((value / 100.0).toFloat().coerceIn(0f, 1f))
                            .height(4.dp).background(DshTeal, RoundedCornerShape(2.dp)),
                    )
                }
                Spacer(Modifier.width(10.dp))
                Text("${"%.0f".format(value)}%", color = DshText, fontSize = 11.sp, fontFamily = DshMono, modifier = Modifier.width(44.dp))
            }
        }
        val memTotal = jsonNum(memory, "total")
        val memUsed = jsonNum(memory, "used")
        if (memTotal != null && memUsed != null) {
            Text(
                "内存 ${toolboxBytes(memUsed.toLong())} / ${toolboxBytes(memTotal.toLong())}",
                color = DshMuted,
                fontSize = 9.sp,
                fontFamily = DshMono,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        val load = cpu?.optJSONArray("loadAvg")
        if (load != null && load.length() >= 3) {
            val text = (0 until minOf(3, load.length())).joinToString(" · ") {
                if (load.isNull(it)) "—" else "%.2f".format(load.optDouble(it))
            }
            Text("负载 $text", color = DshMuted, fontSize = 9.sp, fontFamily = DshMono, modifier = Modifier.padding(top = 2.dp))
        }
        Spacer(Modifier.height(14.dp))
        val disks = sample?.optJSONArray("disks")
        if (disks != null && disks.length() > 0) {
            Text("磁盘", color = DshMuted, fontSize = 9.sp, fontFamily = DshMono)
            Spacer(Modifier.height(4.dp))
            for (i in 0 until disks.length()) {
                val disk = disks.optJSONObject(i) ?: continue
                val mount = disk.optString("mount")
                val pct = jsonNum(disk, "usagePct") ?: 0.0
                val used = jsonNum(disk, "used") ?: 0.0
                val total = jsonNum(disk, "total") ?: 0.0
                Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(mount, color = DshText, fontSize = 11.sp, fontFamily = DshMono, modifier = Modifier.width(90.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Box(Modifier.weight(1f).height(4.dp).background(DshBorder, RoundedCornerShape(2.dp))) {
                        Box(Modifier.fillMaxWidth((pct / 100.0).toFloat().coerceIn(0f, 1f)).height(4.dp).background(DshTeal, RoundedCornerShape(2.dp)))
                    }
                    Spacer(Modifier.width(8.dp))
                    Text("${"%.0f".format(pct)}%", color = DshMuted, fontSize = 9.sp, fontFamily = DshMono)
                }
                Text(
                    "${toolboxBytes(used.toLong())} / ${toolboxBytes(total.toLong())}",
                    color = DshMuted,
                    fontSize = 8.sp,
                    fontFamily = DshMono,
                    modifier = Modifier.padding(start = 90.dp, bottom = 2.dp),
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        val total = jsonNum(processes, "total")
        val running = jsonNum(processes, "running")
        if (total != null) {
            Text(
                "进程 ${total.toInt()} · 运行 ${running?.toInt() ?: 0}",
                color = DshMuted,
                fontSize = 9.sp,
                fontFamily = DshMono,
            )
        }
    }
}

/* --------------------------------- 搜索 --------------------------------- */

@Composable
private fun SearchSection(vm: NativeViewModel) {
    val keyboard = LocalSoftwareKeyboardController.current
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)
                .border(1.dp, DshBorder, RoundedCornerShape(8.dp))
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Search, null, tint = DshMuted, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(8.dp))
            BasicTextField(
                value = vm.searchQuery,
                onValueChange = { vm.searchQuery = it },
                singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(color = DshText, fontSize = 13.sp),
                decorationBox = { inner ->
                    if (vm.searchQuery.isEmpty()) Text("搜索所有会话", color = DshMuted, fontSize = 13.sp)
                    inner()
                },
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = {
                vm.searchSessions(vm.searchQuery)
                keyboard?.hide()
            }, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Filled.Send, "搜索", tint = DshTeal, modifier = Modifier.size(15.dp))
            }
        }
        if (vm.searchFallback) {
            Text(
                "服务端检索索引未启用，已回退为标题/目录匹配",
                color = DshMuted,
                fontSize = 9.sp,
                fontFamily = DshMono,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )
        }
        if (vm.searchBusy) Text("搜索中", color = DshMuted, fontSize = 10.sp, fontFamily = DshMono, modifier = Modifier.padding(horizontal = 20.dp))
        if (vm.searchResults.isEmpty() && vm.searchQuery.isNotBlank() && !vm.searchBusy) {
            Text("没有匹配的会话", color = DshMuted, fontSize = 12.sp, modifier = Modifier.padding(20.dp))
        }
        LazyColumn(Modifier.fillMaxSize()) {
            items(vm.searchResults, key = { it.sessionId + it.snippet.take(12) }) { hit ->
                val session = vm.sessions.firstOrNull { it.id == hit.sessionId }
                Column(
                    Modifier.fillMaxWidth().clickable { session?.let { vm.openSession(it) } }
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                ) {
                    Text(
                        session?.title?.takeIf { it.isNotBlank() } ?: hit.sessionId.take(18),
                        color = DshText,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        hit.snippet.replace('\n', ' '),
                        color = DshMuted,
                        fontSize = 10.sp,
                        fontFamily = DshMono,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 3.dp),
                    )
                }
                HorizontalDivider(color = DshBorder, thickness = 1.dp, modifier = Modifier.padding(start = 20.dp))
            }
        }
    }
}

/* --------------------------------- 技能 --------------------------------- */

@Composable
private fun SkillsSection(vm: NativeViewModel) {
    var detail by remember { mutableStateOf<com.ila3.dshnative.protocol.DshClient.SkillItem?>(null) }
    val clipboard = LocalClipboardManager.current
    Column(Modifier.fillMaxSize()) {
        if (vm.skillsBusy) Text("加载中", color = DshMuted, fontSize = 10.sp, modifier = Modifier.padding(20.dp))
        if (vm.skills.isEmpty() && !vm.skillsBusy) {
            Text("当前会话没有可用技能", color = DshMuted, fontSize = 12.sp, modifier = Modifier.padding(20.dp))
        }
        LazyColumn(Modifier.fillMaxSize()) {
            items(vm.skills, key = { it.name }) { skill ->
                Column(
                    Modifier.fillMaxWidth().clickable { detail = skill }.padding(horizontal = 20.dp, vertical = 12.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("/${skill.name}", color = DshText, fontSize = 13.sp, fontFamily = DshMono)
                        Spacer(Modifier.weight(1f))
                        if (skill.modelInvocable) Text("模型可用", color = DshMuted, fontSize = 9.sp, fontFamily = DshMono)
                    }
                    Text(
                        skill.description,
                        color = DshMuted,
                        fontSize = 11.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 3.dp),
                    )
                }
                HorizontalDivider(color = DshBorder, thickness = 1.dp, modifier = Modifier.padding(start = 20.dp))
            }
        }
    }
    detail?.let { skill ->
        AlertDialog(
            onDismissRequest = { detail = null },
            title = { Text("/${skill.name}", color = DshText, fontSize = 15.sp) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(skill.description, color = DshText, fontSize = 13.sp, lineHeight = 19.sp)
                    skill.whenToUse?.let { Text("适用：$it", color = DshMuted, fontSize = 11.sp) }
                    skill.path?.let { Text(it, color = DshMuted, fontSize = 9.sp, fontFamily = DshMono, maxLines = 2) }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    clipboard.setText(AnnotatedString("/${skill.name}"))
                    detail = null
                }) { Text("复制调用", color = DshTeal) }
            },
            dismissButton = { TextButton(onClick = { detail = null }) { Text("关闭", color = DshMuted) } },
            containerColor = DshSurface,
            shape = RoundedCornerShape(10.dp),
        )
    }
}

/* --------------------------------- 工具 --------------------------------- */

@Composable
private fun ToolsSection(vm: NativeViewModel) {
    val tools = remember(vm.messages.size) {
        vm.messages.filterIsInstance<ChatItem.Tool>()
            .groupBy { it.name }
            .map { (name, calls) -> Triple(name, calls.size, calls.maxOfOrNull { it.at } ?: 0L) }
            .sortedByDescending { it.second }
    }
    Column(Modifier.fillMaxSize()) {
        Text(
            if (vm.activeSessionId.isBlank()) "打开一个会话后显示工具调用统计" else "当前会话 · 工具调用",
            color = DshMuted,
            fontSize = 9.sp,
            fontFamily = DshMono,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
        )
        if (tools.isEmpty()) {
            Text("还没有工具调用", color = DshMuted, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 20.dp))
        }
        val maxCount = (tools.maxOfOrNull { it.second } ?: 1).coerceAtLeast(1)
        LazyColumn(Modifier.fillMaxSize()) {
            items(tools, key = { it.first }) { (name, count, lastAt) ->
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(name, color = DshText, fontSize = 13.sp, fontFamily = DshMono, modifier = Modifier.weight(1f))
                    Box(
                        Modifier.width((60 * count / maxCount).dp).height(4.dp).background(DshTeal),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("$count 次", color = DshMuted, fontSize = 10.sp, fontFamily = DshMono)
                    Spacer(Modifier.width(12.dp))
                    Text(relativeTime(lastAt), color = DshMuted, fontSize = 9.sp, fontFamily = DshMono)
                }
                HorizontalDivider(color = DshBorder, thickness = 1.dp, modifier = Modifier.padding(start = 20.dp))
            }
        }
    }
}

/* --------------------------------- 浏览器 --------------------------------- */

@Composable
private fun BrowserSection(vm: NativeViewModel) {
    var address by remember(vm.browserUrl) { mutableStateOf(vm.browserUrl) }
    var current by remember(vm.browserUrl) { mutableStateOf(vm.browserUrl) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                .border(1.dp, DshBorder, RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicTextField(
                value = address,
                onValueChange = { address = it },
                singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(color = DshText, fontSize = 12.sp, fontFamily = DshMono),
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = {
                val url = if (address.startsWith("http")) address else "https://$address"
                webView?.loadUrl(url)
                current = url
            }, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Filled.Send, "打开", tint = DshTeal, modifier = Modifier.size(15.dp))
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("‹", color = DshMuted, fontSize = 18.sp, modifier = Modifier.clickable { webView?.goBack() }.padding(8.dp))
            Text("›", color = DshMuted, fontSize = 18.sp, modifier = Modifier.clickable { webView?.goForward() }.padding(8.dp))
            Text("刷新", color = DshMuted, fontSize = 10.sp, fontFamily = DshMono, modifier = Modifier.clickable { webView?.reload() }.padding(8.dp))
            Spacer(Modifier.weight(1f))
            Text(current, color = DshMuted, fontSize = 9.sp, fontFamily = DshMono, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        AndroidView(
            factory = { context ->
                WebView(context).apply {
                    webViewClient = WebViewClient()
                    settings.javaScriptEnabled = true
                    loadUrl(current)
                    webView = this
                }
            },
            modifier = Modifier.weight(1f).fillMaxWidth(),
        )
    }
}

/* --------------------------------- SSH --------------------------------- */

@Composable
private fun SshSection(vm: NativeViewModel) {
    var showAdd by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var name by remember { mutableStateOf("本机") }
    var host by remember { mutableStateOf("127.0.0.1") }
    var user by remember { mutableStateOf("root") }
    var port by remember { mutableStateOf("22") }
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("SSH 服务器", color = DshDanger, fontSize = 9.sp, fontFamily = DshMono, modifier = Modifier.weight(1f))
            Text("从 Web 导入", color = DshTeal, fontSize = 11.sp, fontFamily = DshMono, modifier = Modifier.clickable { vm.importWebSshHosts() }.padding(end = 12.dp))
            Text("添加", color = DshTeal, fontSize = 11.sp, fontFamily = DshMono, modifier = Modifier.clickable { showAdd = true })
        }
        Text(
            "保存主机，点「终端」在会话里执行 ssh；密码与密钥交互在终端内完成",
            color = DshMuted,
            fontSize = 10.sp,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        if (vm.sshImportMessage.isNotBlank()) {
            Text(vm.sshImportMessage, color = DshTeal, fontSize = 10.sp, fontFamily = DshMono, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
        }
        if (vm.sshHosts.isEmpty()) {
            Text("还没有服务器", color = DshMuted, fontSize = 12.sp, modifier = Modifier.padding(20.dp))
        }
        LazyColumn(Modifier.fillMaxSize()) {
            items(vm.sshHosts, key = { it.id }) { item ->
                Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(item.name, color = DshText, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            Text("${item.user}@${item.host}:${item.port}", color = DshMuted, fontSize = 9.sp, fontFamily = DshMono)
                        }
                        Text(
                            status[item.id] ?: "测试",
                            color = if (status[item.id] == "可连接") DshTeal else DshMuted,
                            fontSize = 10.sp,
                            fontFamily = DshMono,
                            modifier = Modifier.clickable {
                                status = status + (item.id to "测试中")
                                scope.launch {
                                    val ok = withContext(Dispatchers.IO) {
                                        runCatching {
                                            Socket().use { it.connect(InetSocketAddress(item.host, item.port), 4000) }
                                        }.isSuccess
                                    }
                                    status = status + (item.id to if (ok) "可连接" else "失败")
                                }
                            }.padding(8.dp),
                        )
                        Text("终端", color = DshTeal, fontSize = 10.sp, fontFamily = DshMono, modifier = Modifier.clickable {
                            vm.openSshTerminal(item.host, item.user, item.port)
                        }.padding(8.dp))
                        Text("复制", color = DshTeal, fontSize = 10.sp, fontFamily = DshMono, modifier = Modifier.clickable {
                            clipboard.setText(AnnotatedString("ssh ${item.user}@${item.host} -p ${item.port}"))
                        }.padding(8.dp))
                        Icon(
                            Icons.Filled.Delete,
                            "删除",
                            tint = DshMuted,
                            modifier = Modifier.size(15.dp).clickable { vm.removeSshHost(item.id) }.padding(4.dp),
                        )
                    }
                }
                HorizontalDivider(color = DshBorder, thickness = 1.dp, modifier = Modifier.padding(start = 20.dp))
            }
        }
    }
    if (showAdd) {
        AlertDialog(
            onDismissRequest = { showAdd = false },
            title = { Text("添加服务器", color = DshText, fontSize = 15.sp) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    field("名称", name) { name = it }
                    field("主机", host) { host = it }
                    field("用户", user) { user = it }
                    field("端口", port) { port = it.filter { c -> c.isDigit() }.take(5) }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val p = port.toIntOrNull() ?: 22
                    if (host.isNotBlank()) {
                        vm.addSshHost(name.trim(), host.trim(), user.ifBlank { "root" }, p)
                    }
                    showAdd = false
                    name = "本机"; host = "127.0.0.1"; user = "root"; port = "22"
                }) { Text("保存", color = DshTeal) }
            },
            dismissButton = { TextButton(onClick = { showAdd = false }) { Text("取消", color = DshMuted) } },
            containerColor = DshSurface,
            shape = RoundedCornerShape(10.dp),
        )
    }
}

@Composable
private fun field(label: String, value: String, onChange: (String) -> Unit) {
    BasicTextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        textStyle = androidx.compose.ui.text.TextStyle(color = DshText, fontSize = 13.sp),
        decorationBox = { inner ->
            Box(Modifier.fillMaxWidth().border(1.dp, DshBorder, RoundedCornerShape(6.dp)).padding(10.dp)) {
                if (value.isEmpty()) Text(label, color = DshMuted, fontSize = 12.sp)
                inner()
            }
        },
    )
}

/* --------------------------------- 附件 --------------------------------- */

private fun attachmentPreview(data: String?): Bitmap? {
    data ?: return null
    return runCatching {
        val bytes = Base64.decode(data, Base64.NO_WRAP)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }.getOrNull()
}

@Composable
private fun AttachmentsSection(vm: NativeViewModel) {
    val attachments = remember(vm.messages.size) {
        vm.messages.filterIsInstance<ChatItem.User>().flatMap { it.attachments }
    }
    var shown by remember(vm.gallery.size) { mutableStateOf(40) }
    var preview by remember { mutableStateOf<Bitmap?>(null) }
    Column(Modifier.fillMaxSize()) {
        LaunchedEffect(Unit) {
            if (vm.gallery.isEmpty()) vm.loadGallery()
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "工作区图片 · ${vm.gallery.size}",
                color = DshMuted,
                fontSize = 9.sp,
                fontFamily = DshMono,
                modifier = Modifier.weight(1f),
            )
            Text("刷新", color = DshTeal, fontSize = 10.sp, fontFamily = DshMono, modifier = Modifier.clickable { vm.refreshGallery() })
        }
        val folders = remember(vm.gallery) {
            vm.gallery.mapNotNull { item ->
                item.path.substringBefore('/', "").takeIf { item.path.contains('/') && it.isNotBlank() }
            }.distinct().take(8)
        }
        if (folders.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                (listOf("all") + folders).forEach { folder ->
                    val active = vm.galleryFolder == folder
                    Text(
                        if (folder == "all") "全部" else folder,
                        color = if (active) DshText else DshMuted,
                        fontSize = 9.sp,
                        fontFamily = DshMono,
                        modifier = Modifier
                            .background(if (active) DshSurface else Color.Transparent, RoundedCornerShape(6.dp))
                            .border(1.dp, if (active) DshBorder else Color.Transparent, RoundedCornerShape(6.dp))
                            .clickable { vm.galleryFolder = folder }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }
        }
        Text("当前会话附件", color = DshMuted, fontSize = 9.sp, fontFamily = DshMono, modifier = Modifier.padding(horizontal = 20.dp))
        if (attachments.isEmpty()) {
            Text("没有上传附件", color = DshMuted, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 20.dp))
        }
        LazyColumn(Modifier.fillMaxSize()) {
            if (vm.galleryBusy) {
                item { Text("加载图片中", color = DshMuted, fontSize = 9.sp, fontFamily = DshMono, modifier = Modifier.padding(20.dp)) }
            }
            if (vm.gallery.isNotEmpty()) {
                val visible = vm.gallery.filter { item ->
                    vm.galleryFolder == "all" || item.path.substringBefore('/', "") == vm.galleryFolder
                }
                itemsIndexed(visible.take(shown), key = { _, item -> "gallery-" + item.path }) { index, item ->
                    val thumb = vm.thumbnails[item.path]
                    LaunchedEffect(item.path) { vm.loadThumbnail(item) }
                    Row(
                        Modifier.fillMaxWidth().clickable { vm.openGalleryAt(index, visible) }
                            .padding(horizontal = 20.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (thumb != null) {
                            Image(
                                thumb.asImageBitmap(),
                                contentDescription = item.name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(56.dp).clip(RoundedCornerShape(6.dp)),
                            )
                        } else {
                            Box(Modifier.size(56.dp).background(DshSurface2, RoundedCornerShape(6.dp)))
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(item.name, color = DshText, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(item.path, color = DshMuted, fontSize = 9.sp, fontFamily = DshMono, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Text("查看", color = DshTeal, fontSize = 10.sp, fontFamily = DshMono)
                    }
                    HorizontalDivider(color = DshBorder, thickness = 1.dp, modifier = Modifier.padding(start = 20.dp))
                }
                if (shown < visible.size) {
                    item(key = "gallery-more") {
                        Text(
                            "加载更多（还有 ${visible.size - shown} 张）",
                            color = DshTeal,
                            fontSize = 11.sp,
                            fontFamily = DshMono,
                            modifier = Modifier.fillMaxWidth()
                                .clickable { shown += 40 }
                                .padding(horizontal = 20.dp, vertical = 14.dp),
                        )
                    }
                }
            }
            items(attachments, key = { it.name + it.bytes }) { item ->
                val bitmap = remember(item) { item.preview ?: attachmentPreview(item.data) }
                Row(
                    Modifier.fillMaxWidth().clickable { bitmap?.let { preview = it } }
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (bitmap != null) {
                        Image(
                            bitmap.asImageBitmap(),
                            contentDescription = item.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(44.dp).clip(RoundedCornerShape(6.dp)),
                        )
                        Spacer(Modifier.width(10.dp))
                    }
                    Column(Modifier.weight(1f)) {
                        Text(item.name, color = DshText, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            item.bytes?.let { toolboxBytes(it) } ?: (item.mime ?: "附件"),
                            color = DshMuted,
                            fontSize = 9.sp,
                            fontFamily = DshMono,
                        )
                    }
                    if (bitmap != null) Text("查看", color = DshTeal, fontSize = 10.sp, fontFamily = DshMono)
                }
                HorizontalDivider(color = DshBorder, thickness = 1.dp, modifier = Modifier.padding(start = 20.dp))
            }
        }
    }
    preview?.let { bitmap ->
        Dialog(onDismissRequest = { preview = null }) {
            Column(Modifier.fillMaxWidth().background(DshSurface).padding(12.dp)) {
                Image(bitmap.asImageBitmap(), contentDescription = "附件", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxWidth())
                Text("点击外部关闭", color = DshMuted, fontSize = 9.sp, fontFamily = DshMono, modifier = Modifier.padding(top = 8.dp))
            }
        }
    }
}

/* -------------------------------- 文件入口 -------------------------------- */

@Composable
private fun FilesSection(vm: NativeViewModel) {
    val entries = listOf(
        "资料目录" to "浏览当前工作区目录",
        "知识库" to "只看文档与 Markdown",
        "代码工作台" to "只看代码文件",
        "图片" to "只看图片并预览",
        "全部文件" to "所有文件与目录",
    )
    Column(Modifier.fillMaxSize()) {
        Text("文件能力复用「文件」页，筛选已应用", color = DshMuted, fontSize = 10.sp, modifier = Modifier.padding(20.dp))
        entries.forEachIndexed { index, (title, hint) ->
            Row(
                Modifier.fillMaxWidth().clickable {
                    vm.fileKindFilter = when (index) {
                        1 -> "docs"
                        2 -> "code"
                        3 -> "image"
                        else -> "all"
                    }
                    vm.openFiles()
                }.padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(title, color = DshText, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Text(hint, color = DshMuted, fontSize = 10.sp, modifier = Modifier.padding(top = 2.dp))
                }
                Icon(Icons.Outlined.ChevronRight, null, tint = DshMuted, modifier = Modifier.size(16.dp))
            }
            HorizontalDivider(color = DshBorder, thickness = 1.dp, modifier = Modifier.padding(start = 20.dp))
        }
    }
}


private fun toolboxBytes(bytes: Long): String = when {
    bytes >= 1024 * 1024 -> String.format(java.util.Locale.US, "%.1f MB", bytes / 1024.0 / 1024.0)
    bytes >= 1024 -> "${bytes / 1024} KB"
    else -> "$bytes B"
}
