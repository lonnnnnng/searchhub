package com.searchhub.app.data

import com.searchhub.app.model.DetailInfo
import com.searchhub.app.model.ResourceItem
import com.searchhub.app.model.SearchResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup

/**
 * 短剧狗 duanjugou.top (Z-BlogPHP)
 * 搜索: GET /search.php?q={kw}  SSR
 * 结果: article.post-item-row → h2.post-title a(title+href /{id}.html)
 * 详情: /{id}.html  → h1 + 网盘直链(pan.quark.cn 等)
 */
class DuanjugouAdapter(override val config: SiteConfig, private val engine: HttpEngine) : SiteAdapter {
    override val id = "duanjugou"
    override val displayName = "短剧狗"

    private val baseUrl = config.baseUrl.trimEnd('/')

    override suspend fun search(kw: String, page: Int): List<SearchResult> = withContext(Dispatchers.IO) {
        if (page > 1) return@withContext emptyList()
        val url = "$baseUrl/search.php?q=${java.net.URLEncoder.encode(kw.trim(), "UTF-8")}"
        val html = try { engine.getText(url, referer = "$baseUrl/") } catch (e: Exception) { return@withContext emptyList() }
        val doc = Jsoup.parse(html)
        val out = mutableListOf<SearchResult>()
        for (item in doc.select("article.post-item-row")) {
            val a = item.selectFirst("h2.post-title a[href]") ?: continue
            val href = a.attr("href")
            if (!href.contains(".html")) continue
            val title = a.attr("title").ifBlank { a.text() }.trim()
            if (title.isBlank()) continue
            val cate = item.selectFirst(".post-cate-badge a")?.text() ?: ""
            val year = Regex("(19\\d{2}|20\\d{2})").find(title)?.groupValues?.get(1) ?: ""
            out += SearchResult(
                title = title.replace("<strong>", "").replace("</strong>", ""),
                year = year,
                type = if (cate.isNotBlank()) cate else "短剧",
                sourceSite = displayName,
                detailUrl = if (href.startsWith("http")) href else baseUrl + href,
            )
        }
        out
    }

    override suspend fun detail(detailUrl: String): DetailInfo = withContext(Dispatchers.IO) {
        val html = try { engine.getText(detailUrl, referer = detailUrl) } catch (e: Exception) {
            return@withContext DetailInfo(title = "", resources = emptyList(), sourceSite = displayName)
        }
        val doc = Jsoup.parse(html)
        val title = doc.select("h1").firstOrNull()?.text() ?: doc.title()
        val resources = mutableListOf<ResourceItem>()
        // 网盘直链: pan.quark.cn / pan.baidu.com / pan.xunlei / alipan 等
        for (a in doc.select("a[href]")) {
            val href = a.attr("href").trim()
            if (href.isEmpty()) continue
            val kind = when {
                href.contains("quark.cn") -> "夸克网盘"
                href.contains("pan.baidu.com") || href.contains("yun.baidu.com") -> "百度网盘"
                href.contains("xunlei.com") || href.contains("pan.xunlei") -> "迅雷云盘"
                href.contains("alipan") || href.contains("aliyundrive") -> "阿里云盘"
                href.startsWith("magnet:") -> ""
                else -> ""
            }
            if (kind.isNotBlank()) {
                resources += ResourceItem("netdisk", kind, href, sourceSite = displayName)
            }
        }
        // 去重
        val dedup = resources.distinctBy { it.url }
        DetailInfo(title = title, resources = dedup, sourceSite = displayName)
    }
}