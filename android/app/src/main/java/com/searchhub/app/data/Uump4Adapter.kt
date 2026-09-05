package com.searchhub.app.data

import com.searchhub.app.model.DetailInfo
import com.searchhub.app.model.ResourceItem
import com.searchhub.app.model.SearchResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup

/**
 * 悠悠MP4 uump4.cc (论坛)
 * 搜索: GET /search.htm?keyword={kw};结果 a[href^=thread-], 标题含 [画质-格式/体积]
 * 详情: /thread-{id}.htm → fieldset fieldset > ul.attachlist 附件直链(.torrent 种子 / 磁力 / 网盘)
 */
class Uump4Adapter(override val config: SiteConfig, private val engine: HttpEngine) : SiteAdapter {
    override val id = "uump4"
    override val displayName = "悠悠MP4"

    private val baseUrl = config.baseUrl.trimEnd('/')

    override suspend fun search(kw: String, page: Int): List<SearchResult> = withContext(Dispatchers.IO) {
        if (page > 1) return@withContext emptyList()
        val url = "$baseUrl/search.htm?keyword=${java.net.URLEncoder.encode(kw.trim(), "UTF-8")}"
        val html = try { engine.getText(url, referer = "$baseUrl/") } catch (e: Exception) { return@withContext emptyList() }
        val doc = Jsoup.parse(html)
        val out = mutableListOf<SearchResult>()
        val seen = mutableSetOf<String>()
        // 论坛高亮 <span class="text-danger"> 会被 .text() 拿成纯文本
        for (a in doc.select("a[href^=thread-]")) {
            val href = a.attr("href").trim()
            val title = a.text().trim()
            // 同一条目有 标题a + 摘要a 多个锚点; 只收带画质/体积标记的标题行
            if (href.isBlank() || title.length < 10 || !title.startsWith("[")) continue
            if (!seen.add(href.substringBefore("?"))) continue
            val quality = Regex("(4K|1080P|1080p|720P|720p|蓝光|HD|BD|RMVB)", RegexOption.IGNORE_CASE).find(title)?.groupValues?.get(1) ?: ""
            val size = Regex("(\\d+(?:\\.\\d+)?\\s*[GM]B)", RegexOption.IGNORE_CASE).find(title)?.groupValues?.get(1) ?: ""
            val year = Regex("(19\\d{2}|20\\d{2})").find(title)?.groupValues?.get(1) ?: ""
            out += SearchResult(
                title = title,
                year = year,
                quality = quality,
                sourceSite = displayName,
                detailUrl = if (href.startsWith("http")) href else "$baseUrl/$href",
            )
        }
        out
    }

    override suspend fun detail(detailUrl: String): DetailInfo = withContext(Dispatchers.IO) {
        val html = try { engine.getText(detailUrl, referer = detailUrl) } catch (e: Exception) {
            return@withContext DetailInfo(title = "", resources = emptyList(), sourceSite = displayName)
        }
        val doc = Jsoup.parse(html)
        // 页面 title 形如 "[标题]-电影-悠悠MP4...", h1 更干净
        val h1 = doc.select("h1").firstOrNull()?.text()?.trim().orEmpty()
        val title = h1.substringBeforeLast("-").ifBlank { doc.title() }
        val size = Regex("(\\d+(?:\\.\\d+)?\\s*[GM]B)", RegexOption.IGNORE_CASE).find(title)?.groupValues?.get(1) ?: ""
        val resources = mutableListOf<ResourceItem>()
        val seen = mutableSetOf<String>()
        for (a in doc.select(".attachlist a[href], fieldset a[href]")) {
            val href = a.attr("href").trim()
            if (href.isBlank() || !seen.add(href)) continue
            val label = a.text().trim().ifBlank { "下载附件" }
            val type = when {
                href.startsWith("magnet:") -> "magnet"
                href.endsWith(".torrent") || href.contains(".torrent?") -> "torrent"
                href.contains("pan.") || href.contains("lanzou") || href.contains("alipan") || href.contains("123pan") -> "netdisk"
                href.startsWith("thunder:") -> "thunder"
                href.startsWith("ed2k:") -> "ed2k"
                else -> "http"
            }
            resources += ResourceItem(type, label, href, size = size, sourceSite = displayName)
        }
        DetailInfo(title = title, resources = resources, sourceSite = displayName)
    }
}
