package com.searchhub.app.data

import com.searchhub.app.model.DetailInfo
import com.searchhub.app.model.ResourceItem
import com.searchhub.app.model.SearchResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import org.jsoup.Jsoup

/**
 * 新版6v(旧版66影视) xb6v.com (帝国CMS)
 * 搜索: POST /e/search/11index.php  show=title,smalltext&tempid=1&keyboard={kw}&tbname=article&mid=1&dopost=search&catid=0
 *       → 302 /e/search/result/?searchid=  ;结果 a[href*=".html"] 标题含年份
 * 详情: /{cat}/{id}.html
 */
class Xb6vAdapter(override val config: SiteConfig, private val engine: HttpEngine) : SiteAdapter {
    override val id = "xb6v"
    override val displayName = "新版6v"

    private val baseUrl = config.baseUrl.trimEnd('/')

    override suspend fun search(kw: String, page: Int): List<SearchResult> = withContext(Dispatchers.IO) {
        if (page > 1) return@withContext emptyList()
        val body = FormBody.Builder()
            .add("show", "title,smalltext")
            .add("tempid", "1")
            .add("keyboard", kw.trim())
            .add("tbname", "article")
            .add("mid", "1")
            .add("dopost", "search")
            .add("catid", "0")
            .build()
        val html = try { engine.postText("$baseUrl/e/search/11index.php", body, referer = "$baseUrl/") } catch (e: Exception) { return@withContext emptyList() }
        val doc = Jsoup.parse(html)
        val out = mutableListOf<SearchResult>()
        for (a in doc.select("a[href*=.html]")) {
            val href = a.attr("href")
            val title = a.text().trim()
            if (title.isBlank() || title.length < 6) continue
            // 过滤栏目导航链接
            if (!Regex("(19\\d{2}|20\\d{2})").containsMatchIn(title) && title.startsWith("首页")) continue
            val year = Regex("(19\\d{2}|20\\d{2})").find(title)?.groupValues?.get(1) ?: ""
            val quality = Regex("(4K|1080P|1080p|720P|720p|蓝光|HD|BD)", RegexOption.IGNORE_CASE).find(title)?.groupValues?.get(1) ?: ""
            val type = Regex("(电影|电视剧|动漫|综艺|纪录片)").find(title)?.groupValues?.get(1) ?: ""
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
        val html = try { engine.getText(detailUrl, referer = detailUrl) } catch (e: Exception) {
            return@withContext DetailInfo(title = "", resources = emptyList(), sourceSite = displayName)
        }
        val doc = Jsoup.parse(html)
        val title = doc.select("h1").firstOrNull()?.text() ?: doc.title()
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
        DetailInfo(title = title, resources = resources, sourceSite = displayName)
    }
}