package com.searchhub.app.ui.search

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LocalMovies
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.searchhub.app.model.SearchResult
import com.searchhub.app.ui.AppViewModel

// 参考"追剧"清爽绿白风
private val TitaGreen = Color(0xFF1E9C5A)
private val TitaGray = Color(0xFF949494)
private val Line = Color(0xFFF0F0F0)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    model: AppViewModel,
    onOpenSettings: () -> Unit,
    onOpenDetail: (SearchResult) -> Unit,
) {
    val appContext = androidx.compose.ui.platform.LocalContext.current.applicationContext as android.app.Application
    val vm: SearchViewModel = viewModel { SearchViewModel(model.repository, appContext) }
    var kw by remember { mutableStateOf("") }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val submit: () -> Unit = {
        kw = kw.trim()
        if (kw.isNotBlank()) {
            // long: 提交后把阅读空间交给结果列表，避免软键盘遮住首屏反馈。
            keyboardController?.hide()
            focusManager.clearFocus()
            vm.search(kw)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        // 设置: 右下角悬浮按钮
        floatingActionButton = {
            val fabShape = RoundedCornerShape(26.dp)
            Surface(
                modifier = Modifier
                    .size(width = 52.dp, height = 52.dp)
                    .clickable(onClick = onOpenSettings),
                color = TitaGreen,
                shape = fabShape,
                shadowElevation = 6.dp,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Settings, contentDescription = "打开设置", tint = Color.White, modifier = Modifier.size(24.dp))
                }
            }
        },
        floatingActionButtonPosition = androidx.compose.material3.FabPosition.End,
    ) { pad ->
        Column(Modifier.padding(pad).imePadding().fillMaxSize()) {
            SearchHeader(
                query = kw,
                onQueryChange = { kw = it },
                onSubmit = submit,
            )
            Spacer(Modifier.height(10.dp))
            when (val state = ui) {
                SearchUiState.Idle -> HistoryState(
                    history = vm.history.collectAsStateWithLifecycle().value,
                    onSelect = { h -> kw = h; submit() },
                    onRemove = { vm.removeHistory(it) },
                    onClear = { vm.clearHistory() },
                )
                is SearchUiState.Loading -> if (state.results.isEmpty()) {
                    LoadingState(done = state.done, total = state.total)
                } else {
                    Column(Modifier.fillMaxSize()) {
                        LoadingProgressBar(state.done, state.total)
                        SiteTabs(results = state.results, onDetail = onOpenDetail, showMore = false, onLoadMore = {})
                    }
                }
                is SearchUiState.Error -> ErrorState(message = state.msg, onRetry = submit)
                is SearchUiState.Loaded -> SiteTabs(
                    results = state.results,
                    onDetail = onOpenDetail,
                    showMore = state.results.isNotEmpty() && state.results.size % 20 == 0,
                    onLoadMore = { vm.loadMore(state.page + 1, state.kw) },
                )
            }
        }
    }
}

@Composable
private fun SearchHeader(
    query: String,
    onQueryChange: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(10.dp))
        // 胶囊搜索框: 浅灰圆角 + 图标 + 搜索按钮
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Surface(
                modifier = Modifier.weight(1f).height(44.dp),
                color = Color(0xFFF3F3F3),
                shape = RoundedCornerShape(22.dp),
            ) {
                Row(
                    Modifier.fillMaxSize().clickable { /* 聚焦交给内部 OutlinedTextField */ },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Spacer(Modifier.width(14.dp))
                    Icon(Icons.Default.Search, contentDescription = null, tint = Color(0xFFBDBDBD), modifier = Modifier.size(18.dp))
                    Box(Modifier.weight(1f).padding(start = 4.dp, end = 8.dp)) {
                        androidx.compose.foundation.text.BasicTextField(
                            value = query,
                            onValueChange = onQueryChange,
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyMedium,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
                            decorationBox = { inner ->
                                if (query.isEmpty()) {
                                    Text("输入片名或关键词", color = Color(0xFFBDBDBD), style = MaterialTheme.typography.bodyMedium)
                                }
                                inner()
                            },
                        )
                    }
                }
            }
            // 绿色搜索按钮
            Surface(
                modifier = Modifier.size(width = 44.dp, height = 44.dp).clickable(enabled = query.isNotBlank(), onClick = onSubmit),
                color = if (query.isNotBlank()) TitaGreen else Color(0xFFCCCCCC),
                shape = RoundedCornerShape(22.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Search, contentDescription = "搜索", tint = Color.White, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

@Composable
private fun HistoryState(
    history: List<String>,
    onSelect: (String) -> Unit,
    onRemove: (String) -> Unit,
    onClear: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 14.dp),
    ) {
        if (history.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.History, contentDescription = null, tint = Color(0xFFC0C0C0), modifier = Modifier.size(40.dp))
                    Spacer(Modifier.height(10.dp))
                    Text("暂无搜索历史", style = MaterialTheme.typography.bodyMedium, color = Color(0xFF999999))
                }
            }
        } else {
            Row(Modifier.fillMaxWidth().padding(horizontal = 3.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("搜索历史", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color(0xFF232323))
                Spacer(Modifier.weight(1f))
                Text("清空", style = MaterialTheme.typography.labelMedium, color = TitaGreen, modifier = Modifier.clickable(onClick = onClear))
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
            history.forEach { h ->
                Row(
                    Modifier.fillMaxWidth().clickable { onSelect(h) }.padding(horizontal = 3.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.History, contentDescription = null, tint = Color(0xFFB0B0B0), modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(h, style = MaterialTheme.typography.bodyMedium, color = Color(0xFF333333), modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "删除 $h",
                        tint = Color(0xFFB0B0B0),
                        modifier = Modifier.size(18.dp).clickable { onRemove(h) },
                    )
                }
                Box(Modifier.fillMaxWidth().padding(start = 30.dp).height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
            }
        }
    }
}

@Composable
private fun LoadingState(done: Int, total: Int) {
    Column(Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 52.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(18.dp))
        Text("正在扫描公开索引", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        Text("$done / $total 个站点已响应", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(16.dp))
        LinearProgressIndicator(progress = { if (total > 0) done.toFloat() / total else 0f }, modifier = Modifier.fillMaxWidth(0.72f))
    }
}

@Composable
private fun ErrorState(message: String, onRetry: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 52.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(40.dp))
        Spacer(Modifier.height(14.dp))
        Text("这次搜索没有完成", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(18.dp))
        OutlinedButton(onClick = onRetry) {
            Icon(Icons.Default.Refresh, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text("重新搜索")
        }
    }
}

@Composable
private fun LoadingProgressBar(done: Int, total: Int) {
    val fraction = if (total > 0) done.toFloat() / total else 0f
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(5.dp))
                Text("结果持续抵达", style = MaterialTheme.typography.labelMedium)
            }
            Text("$done / $total 站", style = MaterialTheme.typography.labelSmall)
        }
        Spacer(Modifier.height(6.dp))
        LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun SiteTabs(
    results: List<SearchResult>,
    onDetail: (SearchResult) -> Unit,
    showMore: Boolean,
    onLoadMore: () -> Unit,
) {
    val bySite = remember(results) { results.groupBy { it.sourceSite } }
    val sites = remember(bySite) { listOf("全部") + bySite.keys.toList() }
    var selected by remember(sites) { mutableIntStateOf(0) }
    if (selected >= sites.size) selected = 0
    Column(Modifier.fillMaxSize()) {
        // 清爽风格: 横向滚动文字 tab, 选中绿色加粗 + 底部 2dp 绿条
        Surface(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surface) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp)) {
                sites.forEachIndexed { index, name ->
                    val count = if (index == 0) results.size else bySite[name]?.size ?: 0
                    val isSel = selected == index
                    Column(
                        Modifier.padding(horizontal = 10.dp, vertical = 8.dp).clickable { selected = index },
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            if (index == 0) "全部  $count" else "$name  $count",
                            color = if (isSel) TitaGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = if (isSel) 15.sp else 14.sp,
                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 1,
                        )
                        Spacer(Modifier.height(4.dp))
                        Box(Modifier.size(width = if (isSel) 22.dp else 0.dp, height = 2.dp).background(TitaGreen))
                    }
                }
            }
        }
        // 分隔线
        Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
        val shown = if (selected == 0) results else bySite[sites[selected]] ?: emptyList()
        ResultList(results = shown, onDetail = onDetail, showMore = if (selected == 0) showMore else false, onLoadMore = onLoadMore, showSite = selected == 0)
    }
}

@Composable
private fun ResultList(
    results: List<SearchResult>,
    onDetail: (SearchResult) -> Unit,
    showMore: Boolean,
    onLoadMore: () -> Unit,
    showSite: Boolean = true,
) {
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("搜索结果", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.weight(1f))
                Text("${results.size} 条", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (results.isEmpty()) {
            item { EmptyResultState() }
        } else {
            items(results, key = { it.detailUrl }) { item -> SearchResultCard(item, onClick = { onDetail(item) }, showSite = showSite) }
        }
        if (showMore) {
            item { OutlinedButton(onClick = onLoadMore, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) { Text("加载更多结果") } }
        }
    }
}

@Composable
private fun EmptyResultState() {
    Column(Modifier.fillMaxWidth().padding(vertical = 52.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(36.dp))
        Spacer(Modifier.height(12.dp))
        Text("站点已响应，但没有匹配结果", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(5.dp))
        Text("换一个片名或更短的关键词试试", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SearchResultCard(item: SearchResult, onClick: () -> Unit, showSite: Boolean = true) {
    // 清爽无边框简约行, 左站名色块 + 信息 + 跳转; 背景跟随主题(浅色白/深色深灰)
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(6.dp), modifier = Modifier.size(38.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Text(item.sourceSite.take(1).uppercase().ifBlank { "•" }, style = MaterialTheme.typography.titleMedium, color = TitaGreen, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(item.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(7.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (item.year.isNotBlank()) Tag(item.year)
                    if (item.quality.isNotBlank()) Tag(item.quality)
                    if (item.type.isNotBlank()) Tag(item.type)
                }
                if (showSite && item.sourceSite.isNotBlank()) {
                    Spacer(Modifier.height(7.dp))
                    Text(item.sourceSite, style = MaterialTheme.typography.labelSmall, color = TitaGreen)
                }
            }
            Spacer(Modifier.width(8.dp))
            Icon(Icons.Default.Search, contentDescription = "查看详情", tint = Color(0xFFD0D0D0), modifier = Modifier.size(18.dp))
        }
    }
    Box(Modifier.fillMaxWidth().padding(start = 64.dp).height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
}

@Composable
private fun Tag(text: String) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(4.dp)) {
        Text(text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp))
    }
}
