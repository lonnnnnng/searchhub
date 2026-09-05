package com.searchhub.app.data

import com.searchhub.app.model.DetailInfo
import com.searchhub.app.model.ResourceItem
import com.searchhub.app.model.SearchResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup

/**
 * 4K指南 4kzn.cc (WordPress)
 * 搜索: GET /?post_type=book&s={kw};结果 article.posts-item → /book/{id}.html
 * 详情: .site-go a.btn 网盘直链(夸克/光鸭云等, 无需登录), 部分条目含磁力
 */
class FourKznAdapter(override val config: SiteConfig, private val engine: HttpEngine) : SiteAdapter {
    override val id = "4kzn"
    override val displayName = "4K指南"

    private val baseUrl = config.baseUrl.trimEnd('/')

    override suspend fun search(kw: String, page: Int): List<SearchResult> = withContext(Dispatchers.IO) {
        if (page > 1) return@withContext emptyList()
        val url = "$baseUrl/?post_type=book&s=${java.net.URLEncoder.encode(kw.trim(), "UTF-8")}"
        val html = try { engine.getText(url, referer = "$baseUrl/") } catch (e: Exception) { return@withContext emptyList() }
        val doc = Jsoup.parse(html)
        val out = mutableListOf<SearchResult>()
        for (item in doc.select("article.posts-item")) {
            // 条目内有多个 /book/ 链接(缩略图a无文本 + 标题a), 取第一个标题非空的
            val a = item.select("a[href*=/book/]").firstOrNull {
                it.attr("title").isNotBlank() || it.text().isNotBlank()
            } ?: continue
            val href = a.attr("href")
            val title = (a.attr("title").ifBlank { a.text() }).trim()
            if (title.isBlank() || title.length < 6) continue
            val year = Regex("(19\\d{2}|20\\d{2})").find(title)?.groupValues?.get(1) ?: ""
            val quality = Regex("(4K|1080P|1080p|720P|720p|蓝光|HD|BD)", RegexOption.IGNORE_CASE).find(title)?.groupValues?.get(1) ?: ""
            out += SearchResult(
                title = title,
                year = year,
                quality = quality,
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
        val title = doc.select("h1").firstOrNull()?.text()?.trim() ?: doc.title()
        val year = Regex("(19\\d{2}|20\\d{2})").find(title)?.groupValues?.get(1) ?: ""
        val resources = mutableListOf<ResourceItem>()
        // 网盘直链按钮(.site-go), 兜底全页网盘域名匹配
        val seen = mutableSetOf<String>()
        val buttons = doc.select(".site-go a[href]").ifEmpty { doc.select("a[href]") }
        for (a in buttons) {
            val href = a.attr("href").trim()
            if (href.isBlank() || !isNetdiskUrl(href) || !seen.add(href)) continue
            val label = a.attr("title").ifBlank { a.text() }.trim().ifBlank {
                netdiskLabel(href)
            }
            resources += ResourceItem("netdisk", label, href, sourceSite = displayName)
        }
        // 磁力
        val magnet = Regex("magnet:\\?xt=urn:btih:[A-Za-z0-9]{32,40}[^\"'\\s<]*").find(html)?.value
        if (magnet != null) resources += ResourceItem("magnet", "磁力链接", magnet, sourceSite = displayName)
        DetailInfo(title = title, year = year, resources = resources, sourceSite = displayName)
    }

    private fun isNetdiskUrl(url: String): Boolean = url.contains("pan.baidu") ||
        url.contains("pan.quark") || url.contains("pan.xunlei") || url.contains("aliyundrive") ||
        url.contains("alipan") || url.contains("lanzou") || url.contains("123pan") ||
        url.contains("guangyapan") || url.contains("cloud.189") || url.contains("feijian")

    private fun netdiskLabel(url: String): String = when {
        url.contains("pan.baidu") -> "百度网盘"
        url.contains("pan.quark") -> "夸克网盘"
        url.contains("pan.xunlei") -> "迅雷网盘"
        url.contains("aliyundrive") || url.contains("alipan") -> "阿里云盘"
        url.contains("guangyapan") -> "光鸭云盘"
        url.contains("123pan") -> "123云盘"
        url.contains("cloud.189") -> "天翼云盘"
        else -> "网盘"
    }
}
