package com.searchhub.app.data

import com.searchhub.app.model.DetailInfo
import com.searchhub.app.model.ResourceItem
import com.searchhub.app.model.SearchResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import org.jsoup.Jsoup

/**
 * 电影港 dygang.tv (帝国CMS)
 * 搜索: POST /e/search/index.php  tempid=1&tbname=article&keyboard={kw}&show=title,smalltext
 * 结果: a[href$=.htm] 内 img alt=标题  (或 a href 带 target=_blank, 图片列)
 * 详情: /{cat}/yyyymmdd/{id}.htm
 */
class DygangAdapter(override val config: SiteConfig, private val engine: HttpEngine) : SiteAdapter {
    override val id = "dygang"
    override val displayName = "电影港"

    private val baseUrl = config.baseUrl.trimEnd('/')

    override suspend fun search(kw: String, page: Int): List<SearchResult> = withContext(Dispatchers.IO) {
        if (page > 1) return@withContext emptyList()
        val body = FormBody.Builder()
            .add("tempid", "1")
            .add("tbname", "article")
            .add("keyboard", kw.trim())
            .add("show", "title,smalltext")
            .build()
        val html = try { engine.postTextGbk("$baseUrl/e/search/index.php", body, referer = "$baseUrl/") } catch (e: Exception) { return@withContext emptyList() }
        val doc = Jsoup.parse(html)
        val out = mutableListOf<SearchResult>()
        // 结果卡片: a[href$=.htm] + img alt 提供标题
        for (a in doc.select("a[href$=.htm]")) {
            val href = a.attr("href")
            val img = a.selectFirst("img")
            var title = a.attr("title")
            if (title.isBlank()) title = img?.attr("alt") ?: ""
            if (title.isBlank()) title = a.text().trim()
            if (title.isBlank()) continue
            val year = Regex("(19\\d{2}|20\\d{2})").find(title)?.groupValues?.get(1) ?: ""
            val quality = Regex("(4K|1080P|1080p|720P|720p|蓝光|BD|DVDRip)", RegexOption.IGNORE_CASE).find(title)?.groupValues?.get(1) ?: ""
            val type = Regex("(电影|电视剧|动漫|综艺|记录片|纪录片)").find(title)?.groupValues?.get(1) ?: ""
            out += SearchResult(
                title = title,
                year = year,
                type = type,
                quality = quality,
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
        val title = doc.select("h1").firstOrNull()?.text() ?: doc.title()
        val overview = doc.select("p, td").firstOrNull()?.text() ?: ""
        val resources = mutableListOf<ResourceItem>()
        for (a in doc.select("a")) {
            val href = a.attr("href")
            val t = a.text().trim()
            if (t.isBlank()) continue
            when {
                href.startsWith("magnet:") -> resources += ResourceItem("magnet", t, href, sourceSite = displayName)
                href.startsWith("thunder:") -> resources += ResourceItem("thunder", t, href, sourceSite = displayName)
                href.startsWith("ed2k:") -> resources += ResourceItem("ed2k", t, href, sourceSite = displayName)
                href.contains("pan.baidu") || href.contains("pan.quark") || href.contains("pan.xunlei") || href.contains("aliyundrive") ->
                    resources += ResourceItem("netdisk", t, href, sourceSite = displayName)
            }
        }
        DetailInfo(title = title, overview = overview, resources = resources, sourceSite = displayName)
    }
}