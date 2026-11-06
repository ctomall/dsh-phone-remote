package com.ila3.dshnative.ui

import android.content.Intent
import android.net.Uri
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
                Text("搜索 · 技能 · 工具 · 浏览器 · SSH · 文件", color = DshMuted, fontSize = 9.sp, fontFamily = DshMono)
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
            "browser" -> BrowserSection()
            "ssh" -> SshSection(vm)
            "attachments" -> AttachmentsSection(vm)
            else -> FilesSection(vm)
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
private fun BrowserSection() {
    var address by remember { mutableStateOf("https://example.com/") }
    var current by remember { mutableStateOf(address) }
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
            Text("添加", color = DshTeal, fontSize = 11.sp, fontFamily = DshMono, modifier = Modifier.clickable { showAdd = true })
        }
        Text(
            "保存主机，点「终端」在会话里执行 ssh；密码与密钥交互在终端内完成",
            color = DshMuted,
            fontSize = 10.sp,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
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
    var preview by remember { mutableStateOf<Bitmap?>(null) }
    Column(Modifier.fillMaxSize()) {
        Text("当前会话附件", color = DshMuted, fontSize = 9.sp, fontFamily = DshMono, modifier = Modifier.padding(20.dp))
        if (attachments.isEmpty()) {
            Text("还没有附件", color = DshMuted, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 20.dp))
        }
        LazyColumn(Modifier.fillMaxSize()) {
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
