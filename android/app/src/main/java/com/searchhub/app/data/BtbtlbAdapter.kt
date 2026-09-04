package com.searchhub.app.data

import com.searchhub.app.model.DetailInfo
import com.searchhub.app.model.ResourceItem
import com.searchhub.app.model.SearchResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup

/**
 * BT影视 https://www.btbtlb.com
 * 搜索: GET /search/{kw}, 分页 /search/{kw}/{page}
 * 详情: /detail/{id}.html
 * 资源: 资源项 a[href*=/tdown/] → /tdown/{id}.html 页内含磁力 magnet: 与种子直链 /dlt/…
 */
class BtbtlbAdapter(override val config: SiteConfig, private val engine: HttpEngine) : SiteAdapter {
    override val id = "btbtlb"
    override val displayName = "BT影视"

    private val baseUrl = config.baseUrl.trimEnd('/')

    override suspend fun search(kw: String, page: Int): List<SearchResult> = withContext(Dispatchers.IO) {
        val url = if (page <= 1) "$baseUrl/search/${kw.trim()}"
        else "$baseUrl/search/${kw.trim()}/$page"
        val html = try { engine.getText(url, referer = "$baseUrl/") } catch (e: Exception) { return@withContext emptyList() }
        val doc = Jsoup.parse(html)
        val out = mutableListOf<SearchResult>()
        // 结果条目容器(每个 module 含一个详情链接)
        for (m in doc.select(".module-item, .module, [class*=module-item]")) {
            val a = m.selectFirst("a[href*=/detail/]") ?: continue
            val href = a.attr("href")
            val title = a.attr("title").ifBlank { a.text() }
            if (title.isBlank()) continue
            val year = m.wholeText().trim().split(Regex("\\s+")).firstOrNull { it.matches(Regex("19\\d{2}|20\\d{2}")) } ?: ""
            val poster = m.selectFirst("img")?.let { it.attr("data-src").ifEmpty { it.attr("src") } } ?: ""
            out += SearchResult(
                title = title,
                year = year,
                sourceSite = displayName,
                detailUrl = if (href.startsWith("http")) href else baseUrl + href,
                poster = poster,
            )
        }
        out
    }

    override suspend fun detail(detailUrl: String): DetailInfo = withContext(Dispatchers.IO) {
        val html = try { engine.getText(detailUrl) } catch (e: Exception) {
            return@withContext DetailInfo(title = "", resources = emptyList(), sourceSite = displayName)
        }
        val doc = Jsoup.parse(html)
        val title = doc.select("h1").firstOrNull()?.text()
            ?: doc.select(".module-info-heading").firstOrNull()?.text() ?: ""
        val resources = mutableListOf<ResourceItem>()
        // 同一片名被大量重复转贴(同名行可达 50+), 按标题去重
        val seenTitles = mutableSetOf<String>()
        for (a in doc.select("a[href*=/tdown/]")) {
            val href = a.attr("href")
            val t = a.attr("title").ifBlank { a.text() }
            if (t.isBlank()) continue
            if (!seenTitles.add(t.trim())) continue
            resources += ResourceItem(
                type = "torrent",
                title = t,
                fetchUrl = if (href.startsWith("http")) href else baseUrl + href,
                sourceSite = displayName,
            )
        }
        DetailInfo(title = title, resources = resources, sourceSite = displayName)
    }
}

/** 抓取 /tdown/ 页换取真实 magnet / 种子直链 */
suspend fun resolveTdown(engine: HttpEngine, fetchUrl: String): Pair<String, String?> {
    // 返回 (magnet 链接, 种子文件链接)
    val html = try { engine.getText(fetchUrl) } catch (e: Exception) { return "" to null }
    val doc = Jsoup.parse(html)
    val magnet = doc.select("a[href^=magnet:]").firstOrNull()?.attr("href")
    var torrent = doc.select("a[href*=/dlt/]").firstOrNull()?.attr("href")
    // 种子直链是相对路径(如 /dlt/xxx), 补全为绝对 URL 才能复制/打开
    if (torrent != null && !torrent.startsWith("http")) {
        val origin = Regex("^(https?://[^/]+)").find(fetchUrl)?.groupValues?.get(1)
        if (origin != null) torrent = origin + torrent
    }
    return (magnet ?: "") to torrent
}