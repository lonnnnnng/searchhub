package com.searchhub.app.ui.search

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.searchhub.app.data.SearchRepository
import com.searchhub.app.model.DetailInfo
import com.searchhub.app.model.ResourceItem
import com.searchhub.app.model.SearchResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface SearchUiState {
    data object Idle : SearchUiState
    data class Loading(
        val kw: String = "",
        val results: List<SearchResult> = emptyList(),
        val done: Int = 0,
        val total: Int = 0,
    ) : SearchUiState
    data class Loaded(
        val kw: String,
        val results: List<SearchResult>,
        val page: Int,
    ) : SearchUiState
    data class Error(val msg: String) : SearchUiState
}

sealed interface DetailUiState {
    data object Idle : DetailUiState
    data object Loading : DetailUiState
    data class Loaded(val info: DetailInfo) : DetailUiState
    data class Error(val msg: String) : DetailUiState
}

class SearchViewModel(
    private val repository: SearchRepository,
    app: android.app.Application,
) : ViewModel() {

    val query = MutableStateFlow("")

    private val prefs = app.getSharedPreferences("search_history", android.content.Context.MODE_PRIVATE)

    private val _history = MutableStateFlow<List<String>>(loadHistory())
    val history: StateFlow<List<String>> = _history.asStateFlow()

    private fun loadHistory(): List<String> {
        return prefs.getStringSet("keywords", emptySet())?.toList() ?: emptyList()
    }

    private fun persist(list: List<String>) {
        prefs.edit().putStringSet("keywords", list.toSet()).apply()
    }

    /** 记录一条搜索历史(去重置顶, 上限 20 条) */
    fun addHistory(kw: String) {
        val t = kw.trim()
        if (t.isBlank()) return
        val cur = _history.value.toMutableList()
        cur.remove(t)
        cur.add(0, t)
        val capped = cur.take(20)
        _history.value = capped
        persist(capped)
    }

    /** 删除一条历史 */
    fun removeHistory(kw: String) {
        val cur = _history.value.toMutableList()
        cur.remove(kw)
        _history.value = cur
        persist(cur)
    }

    /** 清空历史 */
    fun clearHistory() {
        _history.value = emptyList()
        persist(emptyList())
    }

    private val _ui = MutableStateFlow<SearchUiState>(SearchUiState.Idle)
    val ui: StateFlow<SearchUiState> = _ui.asStateFlow()

    /** 当前选中的站点 tab("全部"或站点名); 存 VM 里跨详情往返/流式更新保持选中 */
    var selectedTab by mutableStateOf("全部")
        private set

    fun selectTab(name: String) {
        selectedTab = name
    }

    private val _detail = MutableStateFlow<DetailUiState>(DetailUiState.Idle)
    val detail: StateFlow<DetailUiState> = _detail.asStateFlow()

    private val _resolving = MutableStateFlow<Set<Int>>(emptySet())
    val resolving: StateFlow<Set<Int>> = _resolving.asStateFlow()

    fun search(kw: String, page: Int = 1) {
        query.value = kw
        if (page == 1) addHistory(kw)
        val total = repository.activeCount()
        _ui.value = SearchUiState.Loading(kw = kw, done = 0, total = total)
        viewModelScope.launch {
            try {
                val seen = hashMapOf<String, SearchResult>()
                var doneCount = 0
                repository.searchAllStream(kw, page) { batch, done ->
                    doneCount = done
                    batch.forEach { seen.putIfAbsent(it.detailUrl, it) }
                    // 每站到达立即更新 UI(流式展示)
                    if (doneCount >= total) {
                        _ui.value = SearchUiState.Loaded(kw, seen.values.toList(), page)
                    } else {
                        _ui.value = SearchUiState.Loading(
                            kw = kw,
                            results = seen.values.toList(),
                            done = doneCount,
                            total = total,
                        )
                    }
                }
                // 兜底: 所有站都完成了但计数异常时确保转 Loaded
                val cur = _ui.value
                if (cur !is SearchUiState.Loaded) {
                    _ui.value = SearchUiState.Loaded(kw, seen.values.toList(), page)
                }
            } catch (e: Exception) {
                _ui.value = SearchUiState.Error(e.message ?: "搜索失败")
            }
        }
    }

    fun loadMore(page: Int, kw: String) {
        val total = repository.activeCount()
        val cur = (_ui.value as? SearchUiState.Loaded)?.results ?: emptyList()
        _ui.value = SearchUiState.Loading(kw = kw, results = cur, done = 0, total = total)
        viewModelScope.launch {
            try {
                val seen = cur.associateBy { it.detailUrl }.toMutableMap()
                var doneCount = 0
                repository.searchAllStream(kw, page) { batch, done ->
                    doneCount = done
                    batch.forEach { seen.putIfAbsent(it.detailUrl, it) }
                    if (doneCount >= total) {
                        _ui.value = SearchUiState.Loaded(kw, seen.values.toList(), page)
                    } else {
                        _ui.value = SearchUiState.Loading(
                            kw = kw,
                            results = seen.values.toList(),
                            done = doneCount,
                            total = total,
                        )
                    }
                }
                val st = _ui.value
                if (st !is SearchUiState.Loaded) {
                    _ui.value = SearchUiState.Loaded(kw, seen.values.toList(), page)
                }
            } catch (e: Exception) {
                _ui.value = SearchUiState.Error(e.message ?: "加载更多失败")
            }
        }
    }

    fun openDetail(item: SearchResult) {
        _detail.value = DetailUiState.Loading
        viewModelScope.launch {
            try {
                val info = repository.detail(item)
                if (info.resources.isEmpty()) {
                    // 无资源则展示元数据
                }
                _detail.value = DetailUiState.Loaded(info)
            } catch (e: Exception) {
                _detail.value = DetailUiState.Error(e.message ?: "详情加载失败")
            }
        }
    }

    fun clearDetail() {
        _detail.value = DetailUiState.Idle
        _resolving.value = emptySet()
    }

    /** 解析单个资源(二次跳转,magnet/种子) */
    fun resolveResource(item: ResourceItem, index: Int) {
        viewModelScope.launch {
            _resolving.value = _resolving.value + index
            try {
                val resolved = repository.resolveResource(item)
                val info = (_detail.value as? DetailUiState.Loaded)?.info ?: return@launch
                val list = info.resources.toMutableList()
                list[index] = resolved
                _detail.value = DetailUiState.Loaded(info.copy(resources = list))
            } finally {
                _resolving.value = _resolving.value - index
            }
        }
    }
}