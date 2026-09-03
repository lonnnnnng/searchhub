package com.searchhub.app.ui.search

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
) : ViewModel() {

    val query = MutableStateFlow("")

    private val _ui = MutableStateFlow<SearchUiState>(SearchUiState.Idle)
    val ui: StateFlow<SearchUiState> = _ui.asStateFlow()

    private val _detail = MutableStateFlow<DetailUiState>(DetailUiState.Idle)
    val detail: StateFlow<DetailUiState> = _detail.asStateFlow()

    private val _resolving = MutableStateFlow<Set<Int>>(emptySet())
    val resolving: StateFlow<Set<Int>> = _resolving.asStateFlow()

    fun search(kw: String, page: Int = 1) {
        query.value = kw
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