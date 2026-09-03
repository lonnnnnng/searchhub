package com.searchhub.app.ui.search

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("搜索聚合") },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "设置")
                    }
                },
            )
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = kw,
                    onValueChange = { kw = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("输入片名关键字") },
                    singleLine = true,
                )
                Button(onClick = { if (kw.isNotBlank()) vm.search(kw) }) {
                    Icon(Icons.Default.Search, contentDescription = null)
                }
            }
            Spacer(Modifier.height(8.dp))
            when (val s = ui) {
                is SearchUiState.Idle -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("输入关键字,从多个站点汇总结果", style = MaterialTheme.typography.bodyMedium)
                }
                is SearchUiState.Loading -> if (s.results.isEmpty()) {
                    // 首站结果还没到: 转圈 + 进度
                    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Spacer(Modifier.height(120.dp))
                        CircularProgressIndicator()
                        Spacer(Modifier.height(12.dp))
                        Text("正在搜索 ${s.done}/${s.total} 站…", style = MaterialTheme.typography.bodyMedium)
                    }
                } else {
                    // 流式: 已有部分结果, 实时展示 + 顶部提示剩余
                    Column(Modifier.fillMaxSize()) {
                        LoadingProgressBar(s.done, s.total)
                        SiteTabs(
                            results = s.results,
                            onDetail = onOpenDetail,
                            showMore = false,
                            onLoadMore = {},
                        )
                    }
                }
                is SearchUiState.Error -> Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text(s.msg, color = MaterialTheme.colorScheme.error)
                }
                is SearchUiState.Loaded -> SiteTabs(
                    results = s.results,
                    onDetail = onOpenDetail,
                    showMore = s.results.isNotEmpty() && s.results.size % 20 == 0,
                    onLoadMore = { vm.loadMore(s.page + 1, s.kw) },
                )
            }
        }
    }
}

@Composable
private fun LoadingProgressBar(done: Int, total: Int) {
    val fraction = if (total > 0) done.toFloat() / total else 0f
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("加载中…", style = MaterialTheme.typography.labelSmall)
            Text("${done}/${total} 站", style = MaterialTheme.typography.labelSmall)
        }
        androidx.compose.material3.LinearProgressIndicator(
            progress = { fraction },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun SiteTabs(
    results: List<SearchResult>,
    onDetail: (SearchResult) -> Unit,
    showMore: Boolean,
    onLoadMore: () -> Unit,
) {
    // 按站源分组,保持出现顺序
    val bySite = remember(results) { results.groupBy { it.sourceSite } }
    val sites = remember(bySite) { listOf("全部") + bySite.keys.toList() }
    var selected by remember(sites) { mutableStateOf(0) }
    if (selected >= sites.size) selected = 0

    Column(Modifier.fillMaxSize()) {
        ScrollableTabRow(
            selectedTabIndex = selected,
            edgePadding = 8.dp,
        ) {
            sites.forEachIndexed { index, name ->
                val count = if (index == 0) results.size else bySite[name]?.size ?: 0
                Tab(
                    selected = selected == index,
                    onClick = { selected = index },
                    text = { Text("$name($count)") },
                )
            }
        }
        val shown = if (selected == 0) results else bySite[sites[selected]] ?: emptyList()
        val pageShowMore = if (selected == 0) showMore else false
        ResultList(
            results = shown,
            onDetail = onDetail,
            showMore = pageShowMore,
            onLoadMore = onLoadMore,
            showSite = selected == 0,
        )
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
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
    ) {
        item {
            Text("共 ${results.size} 条", style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.height(4.dp))
        }
        itemsIndexed(results, key = { i, _ -> i }) { _, item ->
            SearchResultCard(item, onClick = { onDetail(item) }, showSite = showSite)
        }
        if (showMore) {
            item {
                OutlinedButton(onClick = onLoadMore, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Text("加载更多")
                }
            }
        }
    }
}

@Composable
private fun SearchResultCard(item: SearchResult, onClick: () -> Unit, showSite: Boolean = true) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(Modifier.padding(12.dp)) {
            Text(item.title, style = MaterialTheme.typography.titleMedium, maxLines = 2)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (item.year.isNotBlank()) Tag(item.year)
                if (item.quality.isNotBlank()) Tag(item.quality)
                if (item.type.isNotBlank()) Tag(item.type)
            }
            if (showSite && item.sourceSite.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(item.sourceSite, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun Tag(text: String) {
    Surface(
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = MaterialTheme.shapes.small,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}