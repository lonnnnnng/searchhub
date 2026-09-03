package com.searchhub.app.data

import com.searchhub.app.model.DetailInfo
import com.searchhub.app.model.ResourceItem
import com.searchhub.app.model.SearchResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import org.jsoup.Jsoup

/**
 * 6v520.com (帝国CMS)
 * 搜索: POST /e/search/index.php  show=title,smalltext&tempid=1&keyboard={kw}&tbname=article  → 302 → /e/search/result/?searchid=
 * 结果: span.blue14 a[href*=".html"] (标题含年份/类型/质量)
 * 详情: /dy/YYYY-MM-DD/{id}.html 或 /{cat}/YYYY-MM-DD/{id}.html
 */
class SixV520Adapter(override val config: SiteConfig, private val engine: HttpEngine) : SiteAdapter {
    override val id = "sixv520"
    override val displayName = "6v520"

    private val baseUrl = config.baseUrl.trimEnd('/')

    override suspend fun search(kw: String, page: Int): List<SearchResult> = withContext(Dispatchers.IO) {
        if (page > 1) return@withContext emptyList()
        val body = FormBody.Builder()
            .add("show", "title,smalltext")
            .add("tempid", "1")
            .add("keyboard", kw.trim())
            .add("tbname", "article")
            .build()
        val html = try { engine.postTextGbk("$baseUrl/e/search/index.php", body, referer = "$baseUrl/") } catch (e: Exception) { return@withContext emptyList() }
        val doc = Jsoup.parse(html)
        val out = mutableListOf<SearchResult>()
        // 结果: span.blue14 a(标题) / 通用 a[href*=".html"]
        for (a in doc.select("span.blue14 a[href*=.html]")) {
            val href = a.attr("href")
            val title = a.text().trim()
            if (title.isBlank() || title.length < 4) continue
            val year = Regex("(19\\d{2}|20\\d{2})").find(title)?.groupValues?.get(1) ?: ""
            val quality = Regex("(4K|1080p|720p|蓝光|HD|BD)", RegexOption.IGNORE_CASE).find(title)?.groupValues?.get(1) ?: ""
            val type = Regex("(电影|电视剧|动漫|综艺|纪录片)").find(title)?.groupValues?.get(1) ?: ""
            out += SearchResult(
                title = title,
                year = year,
                type = type,
                quality = quality.ifBlank { "" },
                sourceSite = displayName,
                detailUrl = if (href.startsWith("http")) href else baseUrl + href,
            )
        }
        out
    }

    override suspend fun detail(detailUrl: String): DetailInfo = withContext(Dispatchers.IO) {
        val html = try { engine.getTextGbk(detailUrl, referer = detailUrl) } catch (e: Exception) {
            return@withContext DetailInfo(title = "", resources = emptyList(), sourceSite = displayName)
        }
        val doc = Jsoup.parse(html)
        val title = doc.select("h1, .title, [class*=tittle]").firstOrNull()?.text() ?: doc.title()
        val data = doc.select("td, .bot").firstOrNull()?.text() ?: ""
        val overview = doc.select("p, td").firstOrNull()?.text() ?: ""
        val resources = mutableListOf<ResourceItem>()
        // 迅雷/磁力下载
        for (a in doc.select("a")) {
            val href = a.attr("href")
            val t = a.text().trim()
            if (t.isBlank()) continue
            when {
                href.startsWith("magnet:") -> resources += ResourceItem("magnet", t, href, sourceSite = displayName)
                href.startsWith("thunder:") -> resources += ResourceItem("thunder", t, href, sourceSite = displayName)
                href.startsWith("ed2k:") -> resources += ResourceItem("ed2k", t, href, sourceSite = displayName)
                href.contains("pan.") || href.contains("yunpan") || href.contains("pan.baidu") || href.contains("pan.quark") ->
                    resources += ResourceItem("netdisk", t, href, sourceSite = displayName)
            }
        }
        DetailInfo(title = title, overview = overview, resources = resources, sourceSite = displayName)
    }
}