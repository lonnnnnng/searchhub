package com.searchhub.app.ui.search

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.searchhub.app.model.DetailInfo
import com.searchhub.app.model.ResourceItem
import com.searchhub.app.model.SearchResult
import com.searchhub.app.ui.AppViewModel

// 参考"追剧"清爽绿白风
private val TitaGreen = Color(0xFF1E9C5A)
private val Line = Color(0xFFF0F0F0)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    url: String,
    site: String,
    model: AppViewModel,
    onBack: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val detailApp = androidx.compose.ui.platform.LocalContext.current.applicationContext as android.app.Application
    val vm: SearchViewModel = viewModel { SearchViewModel(model.repository, detailApp) }
    val detail by vm.detail.collectAsStateWithLifecycle()
    val resolving by vm.resolving.collectAsStateWithLifecycle()

    LaunchedEffect(url, site) {
        vm.openDetail(SearchResult(title = "", sourceSite = site, detailUrl = url))
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("资源详情", style = MaterialTheme.typography.titleLarge)
                        Text(site, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when (val d = detail) {
                is DetailUiState.Idle, is DetailUiState.Loading -> DetailLoadingState()
                is DetailUiState.Error -> DetailErrorState(
                    message = d.msg,
                    onRetry = { vm.openDetail(SearchResult(title = "", sourceSite = site, detailUrl = url)) },
                )
                is DetailUiState.Loaded -> {
                    // 统一规范化资源链接: 相对路径(/dlt/xxx 等)补全域名, 避免复制/打开不可用
                    val fixed = d.info.copy(
                        resources = d.info.resources.map { it.copy(url = absolutizeUrl(it.url, url)) },
                    )
                    DetailContent(
                        info = fixed,
                        sourceUrl = url,
                        resolving = resolving,
                        onResolve = { idx, item -> if (item.url.isBlank() && item.fetchUrl.isNotBlank()) vm.resolveResource(item, idx) },
                        onCopy = { copyText(context, it) },
                        onCopyCode = { copyText(context, it, "已复制提取码") },
                        onOpen = { openUrl(context, it) },
                    )
                }
            }
        }
    }
}

@Composable
private fun DetailLoadingState() {
    Column(Modifier.fillMaxSize().padding(vertical = 80.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(16.dp))
        Text("正在读取片名与资源", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(5.dp))
        Text("源站响应可能需要一点时间", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun DetailErrorState(message: String, onRetry: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 76.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(40.dp))
        Spacer(Modifier.height(8.dp))
        Text("详情暂时无法打开", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(10.dp))
        OutlinedButton(onClick = onRetry) {
            Icon(Icons.Default.Refresh, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text("重新加载")
        }
    }
}

@Composable
private fun DetailContent(
    info: DetailInfo,
    sourceUrl: String,
    resolving: Set<Int>,
    onResolve: (Int, ResourceItem) -> Unit,
    onCopy: (String) -> Unit,
    onCopyCode: (String) -> Unit,
    onOpen: (String) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        item {
            // zhuiju 风格: 无边框白顶信息块
            Column {
                Text(info.title.ifBlank { "未命名条目" }, style = MaterialTheme.typography.headlineSmall, color = Color(0xFF232323))
                Spacer(Modifier.height(8.dp))
                Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    if (info.category.isNotBlank()) DetailTag(info.category)
                    if (info.year.isNotBlank()) DetailTag(info.year)
                    if (info.rate.isNotBlank()) DetailTag("评分 ${info.rate}", accent = true)
                }
                if (info.overview.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(info.overview, style = MaterialTheme.typography.bodyMedium, color = Color(0xFF888888), maxLines = 8, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(color = Color(0xFFE8F7EF), shape = RoundedCornerShape(4.dp), modifier = Modifier.size(26.dp)) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Link, contentDescription = null, tint = TitaGreen, modifier = Modifier.size(15.dp))
                        }
                    }
                    Spacer(Modifier.width(5.dp))
                    Text(info.sourceSite.ifBlank { "公开索引" }, style = MaterialTheme.typography.labelMedium, color = TitaGreen)
                }
                // 溯源: 源站详情页地址, 点击复制, 右侧按钮浏览器打开
                if (sourceUrl.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().clickable { onCopy(sourceUrl) },
                    ) {
                        Text(
                            sourceUrl,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF999999),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(4.dp))
                        Icon(Icons.Default.ContentCopy, contentDescription = "复制源地址", tint = Color(0xFFB0B0B0), modifier = Modifier.size(13.dp))
                        Spacer(Modifier.width(8.dp))
                        Icon(
                            Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = "浏览器打开源页",
                            tint = TitaGreen,
                            modifier = Modifier.size(15.dp).clickable { onOpen(sourceUrl) },
                        )
                    }
                }
            }
            Box(Modifier.fillMaxWidth().padding(top = 14.dp).height(1.dp).background(Line))
        }
        item {
            // 可用资源: 标题 + 总数, 下方一行资源类型汇总(磁力 x / 网盘 x / 种子 x)
            Row(Modifier.fillMaxWidth().padding(horizontal = 3.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("可用资源", style = MaterialTheme.typography.titleLarge, color = Color(0xFF232323))
                Spacer(Modifier.weight(1f))
                Text("共 ${info.resources.size} 个", style = MaterialTheme.typography.labelMedium, color = Color(0xFF888888))
            }
            val typeCounts = remember(info.resources) {
                info.resources.groupingBy { typeLabel(it.type) }.eachCount().toList().sortedByDescending { it.second }
            }
            if (typeCounts.isNotEmpty()) {
                Row(modifier = Modifier.padding(horizontal = 3.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    typeCounts.forEach { (label, n) ->
                        Surface(color = Color(0xFFE8F7EF), shape = RoundedCornerShape(4.dp)) {
                            Text(
                                "$label $n",
                                style = MaterialTheme.typography.labelMedium,
                                color = TitaGreen,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            )
                        }
                    }
                }
            }
        }
        if (info.resources.isEmpty()) {
            item { Text("该条目暂未解析出资源，请前往源站查看。", style = MaterialTheme.typography.bodyMedium, color = Color(0xFF888888), modifier = Modifier.padding(vertical = 30.dp)) }
        } else {
            itemsIndexed(info.resources, key = { index, item -> "${item.type}:${item.title}:$index" }) { i, item ->
                ResourceCard(
                    item = item,
                    resolving = i in resolving,
                    onResolve = { onResolve(i, item) },
                    onCopy = { onCopy(item.url) },
                    onOpen = { onOpen(item.url) },
                    onCopyCode = onCopyCode,
                )
            }
        }
    }
}

@Composable
private fun DetailTag(text: String, accent: Boolean = false) {
    Surface(
        color = if (accent) Color(0xFFE8F7EF) else Color(0xFFF3F3F3),
        shape = RoundedCornerShape(4.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelMedium, color = if (accent) TitaGreen else Color(0xFF888888), modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp))
    }
}

@Composable
private fun ResourceCard(
    item: ResourceItem,
    resolving: Boolean,
    onResolve: () -> Unit,
    onCopy: () -> Unit,
    onOpen: () -> Unit,
    onCopyCode: (String) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
        // 第一行: 类型 + 标题 + 操作按钮
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(color = Color(0xFFE8F7EF), shape = RoundedCornerShape(4.dp)) {
                Text(typeLabel(item.type), style = MaterialTheme.typography.labelSmall, color = TitaGreen, modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp))
            }
            Spacer(Modifier.width(6.dp))
            Text(item.title, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Color(0xFF333333), modifier = Modifier.weight(1f))
            if (item.quality.isNotBlank()) {
                Spacer(Modifier.width(4.dp))
                Text(item.quality, style = MaterialTheme.typography.labelSmall, color = Color(0xFF888888))
            }
            if (item.size.isNotBlank()) {
                Spacer(Modifier.width(4.dp))
                Text(item.size, style = MaterialTheme.typography.labelSmall, color = Color(0xFF949494))
            }
            Spacer(Modifier.width(4.dp))
            when {
                item.url.isNotBlank() -> Row {
                    IconButton(onClick = onCopy, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "复制", tint = TitaGreen, modifier = Modifier.size(16.dp))
                    }
                    IconButton(onClick = onOpen, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = "打开", tint = TitaGreen, modifier = Modifier.size(16.dp))
                    }
                }
                item.fetchUrl.isNotBlank() -> IconButton(onClick = onResolve, modifier = Modifier.size(28.dp), enabled = !resolving) {
                    Icon(
                        if (resolving) Icons.Default.Refresh else Icons.Default.Link,
                        contentDescription = if (resolving) "解析中" else "解析",
                        tint = TitaGreen, modifier = Modifier.size(16.dp),
                    )
                }
                else -> Text("需源站", style = MaterialTheme.typography.labelSmall, color = Color(0xFFB0B0B0))
            }
        }
        // 第二行(仅网盘等带提取码时): 点击单独复制提取码
        extractAccessCode(item.title)?.let { code ->
            Spacer(Modifier.height(3.dp))
            Surface(
                color = Color(0xFFE8F7EF),
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier.clickable { onCopyCode(code) },
            ) {
                Row(Modifier.padding(horizontal = 6.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("提取码 $code", style = MaterialTheme.typography.labelSmall, color = TitaGreen, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.width(3.dp))
                    Icon(Icons.Default.ContentCopy, contentDescription = "复制提取码", tint = TitaGreen, modifier = Modifier.size(11.dp))
                }
            }
        }
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(Line))
}

private val AccessCodeRegex = Regex("(?:提取码|提取碼|访问码|訪問碼|密碼|密码)\\s*[:：=]?\\s*([a-zA-Z0-9]{4,8})")

/** 从资源标题中识别网盘提取码, 如 "百度网盘 提取码:4f8k" → "4f8k"; 无则返回 null */
private fun extractAccessCode(title: String): String? {
    val m = AccessCodeRegex.find(title) ?: return null
    return m.groupValues[1]
}

private fun typeLabel(type: String): String = when (type) {
    "magnet" -> "磁力"
    "netdisk" -> "网盘"
    "torrent" -> "种子"
    "thunder" -> "迅雷"
    "ed2k" -> "电驴"
    "ftp" -> "FTP"
    "http" -> "直链"
    else -> type
}

private fun copyText(context: Context, text: String, toast: String = "已复制链接") {
    if (text.isBlank()) {
        Toast.makeText(context, "无可用链接", Toast.LENGTH_SHORT).show()
        return
    }
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("link", text))
    Toast.makeText(context, toast, Toast.LENGTH_SHORT).show()
}

private fun openUrl(context: Context, url: String) {
    if (url.isBlank()) return
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
    } catch (e: Exception) {
        Toast.makeText(context, "无法打开链接", Toast.LENGTH_SHORT).show()
    }
}

/** 相对路径资源链接(如 /dlt/xxx.torrent)补全为绝对 URL; magnet:/thunder:/ed2k: 等协议链接原样返回 */
private fun absolutizeUrl(url: String, detailUrl: String): String {
    val u = url.trim()
    if (u.isBlank()) return u
    if (u.startsWith("http://") || u.startsWith("https://")) return u
    if (u.contains("://")) return u  // magnet:/thunder:/ed2k: 等
    if (u.startsWith("/")) {
        val origin = Regex("^(https?://[^/]+)").find(detailUrl)?.groupValues?.get(1)
        if (origin != null) return origin + u
    }
    return u
}
