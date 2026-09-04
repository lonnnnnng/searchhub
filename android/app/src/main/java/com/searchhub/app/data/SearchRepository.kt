package com.searchhub.app.data

import com.searchhub.app.model.DetailInfo
import com.searchhub.app.model.ResourceItem
import com.searchhub.app.model.SearchResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

private val MAYBE = 20000L

/**
 * 聚合仓库:调用各站点适配器,合并搜索结果
 */
class SearchRepository(
    private val engine: HttpEngine,
    private val captchaFlow: CaptchaFlow,
) {
    private var adapters: List<SiteAdapter> = emptyList()

    /** 根据配置重建适配器(保存站点配置后调用) */
    fun rebuild(sites: List<SiteConfig>) {
        adapters = sites.filter { it.enabled }.map { cfg ->
            when (cfg.id) {
                "btbtlb" -> BtbtlbAdapter(cfg, engine)
                "foxjun" -> FoxjunAdapter(cfg, engine)
                "xlys" -> XlysAdapter(cfg, engine, captchaFlow)
                "seedhub" -> SeedHubAdapter(cfg, engine)
                "binhd" -> BinhdAdapter(cfg, engine)
                "sixv520" -> SixV520Adapter(cfg, engine)
                "dygang" -> DygangAdapter(cfg, engine)
                "dytt8899" -> Dytt8899Adapter(cfg, engine)
                "451024" -> Seven451024Adapter(cfg, engine)
                "duanjugou" -> DuanjugouAdapter(cfg, engine)
                "showpaw" -> ShowpawAdapter(cfg, engine)
                "btdx8" -> Btdx8Adapter(cfg, engine)
                "xb6v" -> Xb6vAdapter(cfg, engine)
                else -> BtbtlbAdapter(cfg, engine)
            }
        }
    }

    fun activeCount(): Int = adapters.size

    /** 并发搜索所有启用站点;单站失败不影响他站,返回所有成功结果 */
    suspend fun searchAll(kw: String, page: Int = 1): List<SearchResult> = coroutineScope {
        adapters.map { a ->
            async(Dispatchers.IO) {
                try {
                    withTimeoutOrNull(MAYBE) {
                        a.search(kw, page)
                    } ?: emptyList()
                } catch (e: Exception) {
                    emptyList()
                }
            }
        }.awaitAll().flatten()
    }

    /**
     * 流式搜索: 各站并发执行,每站完成后立即把结果与进度回调出去,无需等待全部完成。
     * 快站的结果会先返回(先完成先回调),UI 可实时累积展示。
     * @param onBatch 每站结果到达时回调 (results, doneCount)
     */
    suspend fun searchAllStream(kw: String, page: Int = 1, onBatch: (List<SearchResult>, Int) -> Unit) {
        val total = adapters.size
        if (total == 0) { onBatch(emptyList(), 0); return }
        val done = java.util.concurrent.atomic.AtomicInteger(0)
        coroutineScope {
            adapters.map { a ->
                async(Dispatchers.IO) {
                    val r = try {
                        withTimeoutOrNull(MAYBE) { a.search(kw, page) } ?: emptyList()
                    } catch (e: Exception) {
                        emptyList()
                    }
                    onBatch(r, done.incrementAndGet())
                }
            }.awaitAll()
        }
    }

    /** 解析某站详情(适配器定位由 detailUrl 推断哪站) */
    suspend fun detail(item: SearchResult): DetailInfo = withContext(Dispatchers.IO) {
        val a = adapters.firstOrNull { it.displayName == item.sourceSite }
            ?: return@withContext DetailInfo(item.title, resources = emptyList(), sourceSite = item.sourceSite)
        a.detail(item.detailUrl)
    }

    /** 解析二级跳转资源: 通过 fetchUrl 换取真实链接(tdown → magnet, seedhub → magnet, binhd → POST 网盘) */
    suspend fun resolveResource(item: ResourceItem): ResourceItem = withContext(Dispatchers.IO) {
        if (item.url.isNotBlank() || item.fetchUrl.isBlank()) return@withContext item
        when (item.sourceSite) {
            "BT影视" -> {
                val (magnet, torrent) = resolveTdown(engine, item.fetchUrl)
                if (magnet.isNotBlank()) item.copy(url = magnet, type = "magnet")
                else if (torrent != null) item.copy(url = torrent, type = "torrent")
                else item
            }
            "SeedHub" -> {
                val magnet = resolveSeedHubLink(engine, item.fetchUrl)
                if (magnet.isNotBlank()) item.copy(url = magnet, type = "magnet")
                else item
            }
            "云集" -> {
                // POST 下载端点(带 csrf) → 303 → 真实网盘链接
                val csrf = item.postBody.removePrefix("csrfmiddlewaretoken=")
                val real = if (csrf.isNotBlank()) resolveBinhdLink(engine, item.fetchUrl, csrf) else ""
                if (real.isNotBlank()) item.copy(url = real, type = "netdisk")
                else item
            }
            else -> item
        }
    }
}