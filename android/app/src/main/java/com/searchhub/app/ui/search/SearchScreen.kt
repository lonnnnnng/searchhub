package com.searchhub.app.ui.search

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.LocalMovies
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.searchhub.app.model.SearchResult
import com.searchhub.app.ui.AppViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    model: AppViewModel,
    onOpenSettings: () -> Unit,
    onOpenDetail: (SearchResult) -> Unit,
    vm: SearchViewModel = viewModel { SearchViewModel(model.repository) },
) {
    var kw by remember { mutableStateOf("") }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val sites by model.sites.collectAsStateWithLifecycle()
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val activeSiteCount = sites.count { it.enabled }
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
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("SEARCHHUB", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        Text("发现下一部", style = MaterialTheme.typography.titleLarge)
                    }
                },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "打开设置")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
    ) { pad ->
        Column(Modifier.padding(pad).imePadding().fillMaxSize()) {
            SearchHeader(
                query = kw,
                activeSiteCount = activeSiteCount,
                onQueryChange = { kw = it },
                onSubmit = submit,
            )
            Spacer(Modifier.height(10.dp))
            when (val state = ui) {
                SearchUiState.Idle -> EmptySearchState(onExample = { example -> kw = example; submit() })
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
    activeSiteCount: Int,
    onQueryChange: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.large,
        tonalElevation = 2.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("多站点检索", style = MaterialTheme.typography.titleMedium)
                    Text("一次搜索，汇总公开索引", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.small) {
                    Text("$activeSiteCount 个站点", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("输入片名或关键词") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
                )
                Button(
                    onClick = onSubmit,
                    enabled = query.isNotBlank(),
                    modifier = Modifier.height(56.dp),
                    shape = MaterialTheme.shapes.medium,
                    contentPadding = ButtonDefaults.ContentPadding,
                ) {
                    Icon(Icons.Default.Search, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("搜索")
                }
            }
        }
    }
}

@Composable
private fun EmptySearchState(onExample: (String) -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.large, modifier = Modifier.size(64.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Default.LocalMovies, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(32.dp))
            }
        }
        Spacer(Modifier.height(18.dp))
        Text("把想看的片名交给这里", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text("搜索会同时询问多个公开站点，结果会随着响应逐步出现。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth(0.86f))
        Spacer(Modifier.height(22.dp))
        Text("试试这些关键词", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(10.dp))
        Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("蝙蝠侠", "黑镜", "庆余年").forEach { example ->
                FilterChip(selected = false, onClick = { onExample(example) }, label = { Text(example) })
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
        ScrollableTabRow(selectedTabIndex = selected, edgePadding = 16.dp, containerColor = Color.Transparent) {
            sites.forEachIndexed { index, name ->
                val count = if (index == 0) results.size else bySite[name]?.size ?: 0
                Tab(selected = selected == index, onClick = { selected = index }, text = { Text(if (index == 0) "全部  $count" else "$name  $count") })
            }
        }
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
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(color = MaterialTheme.colorScheme.tertiaryContainer, shape = MaterialTheme.shapes.small, modifier = Modifier.size(38.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Text(item.sourceSite.take(1).uppercase().ifBlank { "•" }, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onTertiaryContainer)
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(item.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(7.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (item.year.isNotBlank()) Tag(item.year)
                    if (item.quality.isNotBlank()) Tag(item.quality)
                    if (item.type.isNotBlank()) Tag(item.type)
                }
                if (showSite && item.sourceSite.isNotBlank()) {
                    Spacer(Modifier.height(7.dp))
                    Text(item.sourceSite, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
            }
            Spacer(Modifier.width(8.dp))
            Icon(Icons.Default.Search, contentDescription = "查看详情", tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun Tag(text: String) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.extraSmall) {
        Text(text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp))
    }
}
