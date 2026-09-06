package com.searchhub.app.data

import com.searchhub.app.model.DetailInfo
import com.searchhub.app.model.ResourceItem
import com.searchhub.app.model.SearchResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import org.jsoup.Jsoup

/**
 * 磁力熊 cilixiong.xyz (帝国CMS, 域名 .cc/.uk/.xyz)
 * 搜索: POST /e/search/index.php  classid=1,2&show=title&tempid=1&keyboard={kw}
 *       → 302 /e/search/result/?searchid= ;结果卡片 div.card, 卡内 a[h2 标题 / .rank 评分 / 年份]
 * 详情: /movie|drama/{id}.html → .mv_down 内 magnet 直链, 锚文本含文件名与体积[3.09G]
 */
class CilixiongAdapter(override val config: SiteConfig, private val engine: HttpEngine) : SiteAdapter {
    override val id = "cilixiong"
    override val displayName = "磁力熊"

    private val baseUrl = config.baseUrl.trimEnd('/')

    override suspend fun search(kw: String, page: Int): List<SearchResult> = withContext(Dispatchers.IO) {
        if (page > 1) return@withContext emptyList()
        val body = FormBody.Builder()
            .add("classid", "1,2")
            .add("show", "title")
            .add("tempid", "1")
            .add("keyboard", kw.trim())
            .build()
        val html = try { engine.postText("$baseUrl/e/search/index.php", body, referer = "$baseUrl/") } catch (e: Exception) { return@withContext emptyList() }
        val doc = Jsoup.parse(html)
        val out = mutableListOf<SearchResult>()
        for (card in doc.select("div.card")) {
            val a = card.selectFirst("a[href*=.html]") ?: continue
            val href = a.attr("href")
            val title = card.selectFirst("h2")?.text()?.trim() ?: continue
            if (title.isBlank()) continue
            val rate = card.selectFirst(".rank")?.text()?.trim() ?: ""
            val year = Regex("(19\\d{2}|20\\d{2})").find(card.selectFirst("ul")?.text() ?: "")?.groupValues?.get(1) ?: ""
            out += SearchResult(
                title = title,
                year = year,
                rate = rate,
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
        // 页面 title 形如 "标题(2023) 美国电影1080P下载...", 年份从这里取
        val year = Regex("(19\\d{2}|20\\d{2})").find(doc.title())?.groupValues?.get(1) ?: ""
        val resources = mutableListOf<ResourceItem>()
        val seen = mutableSetOf<String>()
        for (a in doc.select(".mv_down a[href^=magnet:]")) {
            val href = a.attr("href").trim()
            if (href.isBlank() || !seen.add(href)) continue
            val label = a.text().trim().ifBlank { "磁力链接" }
            val size = Regex("(\\d+(?:\\.\\d+)?\\s*[GMT]B)", RegexOption.IGNORE_CASE).find(label)?.groupValues?.get(1) ?: ""
            resources += ResourceItem("magnet", label, href, size = size, sourceSite = displayName)
        }
        DetailInfo(title = title, year = year, resources = resources, sourceSite = displayName)
    }
}
