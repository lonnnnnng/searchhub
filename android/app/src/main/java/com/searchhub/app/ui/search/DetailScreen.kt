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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
    val vm: SearchViewModel = viewModel { SearchViewModel(model.repository) }
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
                is DetailUiState.Loaded -> DetailContent(
                    info = d.info,
                    resolving = resolving,
                    onResolve = { idx, item -> if (item.url.isBlank() && item.fetchUrl.isNotBlank()) vm.resolveResource(item, idx) },
                    onCopy = { copyText(context, it) },
                    onOpen = { openUrl(context, it) },
                )
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
        Spacer(Modifier.height(14.dp))
        Text("详情暂时无法打开", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(18.dp))
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
    resolving: Set<Int>,
    onResolve: (Int, ResourceItem) -> Unit,
    onCopy: (String) -> Unit,
    onOpen: (String) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
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
                    Spacer(Modifier.height(12.dp))
                    Text(info.overview, style = MaterialTheme.typography.bodyMedium, color = Color(0xFF888888), maxLines = 8, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(color = Color(0xFFE8F7EF), shape = RoundedCornerShape(4.dp), modifier = Modifier.size(26.dp)) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Link, contentDescription = null, tint = TitaGreen, modifier = Modifier.size(15.dp))
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(info.sourceSite.ifBlank { "公开索引" }, style = MaterialTheme.typography.labelMedium, color = TitaGreen)
                }
            }
            Box(Modifier.fillMaxWidth().padding(top = 14.dp).height(1.dp).background(Line))
        }
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 3.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("可用资源", style = MaterialTheme.typography.titleLarge, color = Color(0xFF232323))
                    Text("${info.resources.size} 个来源，可按需复制或打开", style = MaterialTheme.typography.bodySmall, color = Color(0xFF888888))
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
) {
    Column(Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(8.dp)).padding(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(color = Color(0xFFE8F7EF), shape = RoundedCornerShape(4.dp)) {
                Text(typeLabel(item.type), style = MaterialTheme.typography.labelMedium, color = TitaGreen, modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp))
            }
            if (item.quality.isNotBlank()) {
                Spacer(Modifier.width(7.dp))
                Text(item.quality, style = MaterialTheme.typography.labelSmall, color = Color(0xFF888888))
            }
            Spacer(Modifier.weight(1f))
            if (item.size.isNotBlank()) Text(item.size, style = MaterialTheme.typography.labelSmall, color = Color(0xFF949494))
        }
        Spacer(Modifier.height(9.dp))
        Text(item.title, style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis, color = Color(0xFF333333))
        Spacer(Modifier.height(12.dp))
        when {
            item.url.isNotBlank() -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Surface(
                    modifier = Modifier.weight(1f).height(40.dp).clickable(onClick = onCopy),
                    color = Color.White,
                    shape = RoundedCornerShape(20.dp),
                ) {
                    Box(Modifier.fillMaxSize().border(1.dp, Color(0xFFE0E0E0), RoundedCornerShape(20.dp)), contentAlignment = Alignment.Center) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, tint = TitaGreen, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("复制", color = TitaGreen, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
                Surface(
                    modifier = Modifier.weight(1f).height(40.dp).clickable(onClick = onOpen),
                    color = TitaGreen,
                    shape = RoundedCornerShape(20.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("打开", color = Color.White, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
            item.fetchUrl.isNotBlank() -> Surface(
                modifier = Modifier.fillMaxWidth().height(40.dp).clickable(enabled = !resolving, onClick = onResolve),
                color = TitaGreen,
                shape = RoundedCornerShape(20.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (resolving) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        }
                        Spacer(Modifier.width(7.dp))
                        Text(if (resolving) "正在解析真实链接" else "解析真实链接", color = Color.White, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
            else -> Text("需在源站查看", style = MaterialTheme.typography.labelMedium, color = Color(0xFF949494))
        }
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(Line))
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

private fun copyText(context: Context, text: String) {
    if (text.isBlank()) {
        Toast.makeText(context, "无可用链接", Toast.LENGTH_SHORT).show()
        return
    }
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("link", text))
    Toast.makeText(context, "已复制链接", Toast.LENGTH_SHORT).show()
}

private fun openUrl(context: Context, url: String) {
    if (url.isBlank()) return
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
    } catch (e: Exception) {
        Toast.makeText(context, "无法打开链接", Toast.LENGTH_SHORT).show()
    }
}
