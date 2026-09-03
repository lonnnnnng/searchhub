package com.searchhub.app.ui.search

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.searchhub.app.model.DetailInfo
import com.searchhub.app.model.ResourceItem
import com.searchhub.app.ui.AppViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    url: String,
    site: String,
    model: AppViewModel,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val vm: SearchViewModel = viewModel { SearchViewModel(model.repository) }
    val detail by vm.detail.collectAsStateWithLifecycle()
    val resolving by vm.resolving.collectAsStateWithLifecycle()

    androidx.compose.runtime.LaunchedEffect(url, site) {
        vm.openDetail(com.searchhub.app.model.SearchResult(
            title = "", sourceSite = site, detailUrl = url,
        ))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(site) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }
                },
            )
        },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when (val d = detail) {
                is DetailUiState.Idle, is DetailUiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                is DetailUiState.Error -> Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text(d.msg, color = MaterialTheme.colorScheme.error)
                }
                is DetailUiState.Loaded -> DetailContent(
                    info = d.info,
                    resolving = resolving,
                    onResolve = { idx, item ->
                        if (item.url.isBlank() && item.fetchUrl.isNotBlank()) vm.resolveResource(item, idx)
                    },
                    onCopy = { copyText(context, it) },
                    onOpen = { openUrl(context, it) },
                )
            }
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
        contentPadding = PaddingValues(16.dp),
    ) {
        item {
            Text(info.title, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            Text("${info.category} ${info.year} ${info.rate}", style = MaterialTheme.typography.bodyMedium)
            if (info.overview.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(info.overview, style = MaterialTheme.typography.bodySmall, maxLines = 5)
            }
            Spacer(Modifier.height(12.dp))
            Text("资源 (${info.resources.size})", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
        }
        itemsIndexed(info.resources, key = { i, _ -> i }) { i, item ->
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

@Composable
private fun ResourceCard(
    item: ResourceItem,
    resolving: Boolean,
    onResolve: () -> Unit,
    onCopy: () -> Unit,
    onOpen: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(typeLabel(item.type), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                if (item.quality.isNotBlank()) Text(item.quality, style = MaterialTheme.typography.labelSmall)
            }
            Spacer(Modifier.height(4.dp))
            Text(item.title, style = MaterialTheme.typography.bodyMedium, maxLines = 3)
            if (item.size.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(item.size, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (item.url.isNotBlank()) {
                    OutlinedButton(onClick = onCopy, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.ContentCopy, null, Modifier.padding(end = 4.dp))
                        Text("复制链接")
                    }
                    OutlinedButton(onClick = onOpen, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.OpenInNew, null, Modifier.padding(end = 4.dp))
                        Text("打开")
                    }
                } else if (item.fetchUrl.isNotBlank()) {
                    Button(onClick = onResolve, modifier = Modifier.weight(1f), enabled = !resolving) {
                        if (resolving) CircularProgressIndicator(Modifier.padding(end = 4.dp), strokeWidth = 2.dp) else Icon(Icons.Default.Refresh, null, Modifier.padding(end = 4.dp))
                        Text(if (resolving) "解析中…" else "解析链接")
                    }
                } else {
                    Text("需在源站查看", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
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
    Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
}

private fun openUrl(context: Context, url: String) {
    if (url.isBlank()) return
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (e: Exception) {
        Toast.makeText(context, "无法打开链接", Toast.LENGTH_SHORT).show()
    }
}