package com.searchhub.app.data

import com.searchhub.app.model.DetailInfo
import com.searchhub.app.model.ResourceItem
import com.searchhub.app.model.SearchResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import org.jsoup.Jsoup

/**
 * 比特大雄 btdx8.net
 * 搜索: GET /?s={kw}  SSR;结果 a[href*="/torrent/"] 标题含年份/清晰度
 * 详情: /torrent/{slug}.html
 *   - 新条目: 磁力直链 a[href^=magnet:]
 *   - 老条目: a[href*=down-m.php] 下载页 → 页内 JS(file_id+fc) → POST /calldown/calldown-m.php → JSON.down 真链
 */
class Btdx8Adapter(override val config: SiteConfig, private val engine: HttpEngine) : SiteAdapter {
    override val id = "btdx8"
    override val displayName = "比特大雄"

    private val baseUrl = config.baseUrl.trimEnd('/')

    override suspend fun search(kw: String, page: Int): List<SearchResult> = withContext(Dispatchers.IO) {
        if (page > 1) return@withContext emptyList()
        val url = "$baseUrl/?s=${java.net.URLEncoder.encode(kw.trim(), "UTF-8")}"
        val html = try { engine.getText(url, referer = "$baseUrl/") } catch (e: Exception) { return@withContext emptyList() }
        val doc = Jsoup.parse(html)
        val out = mutableListOf<SearchResult>()
        for (a in doc.select("a[href*=/torrent/]")) {
            val href = a.attr("href")
            val title = a.text().trim()
            // 过滤无意义的短链接
            if (title.isBlank() || title.length < 8) continue
            if (a.select(".movie-name, .title, h4, h3").isEmpty() && !Regex("\\d{4}").containsMatchIn(title)) continue
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
        for (a in doc.select("a[href^=magnet:]")) {
            val href = a.attr("href").trim()
            if (href.isBlank()) continue
            val label = a.text().ifBlank { "磁力链接" }.trim()
            resources += ResourceItem("magnet", label, href, sourceSite = displayName)
        }
        for (a in doc.select("a[href$=.torrent]")) {
            val href = a.attr("href")
            if (href.isBlank()) continue
            resources += ResourceItem("torrent", a.text().ifBlank { "种子文件" }, href, sourceSite = displayName)
        }
        // 老条目无直链磁力, 给二次跳转下载页, 点"解析"后经 calldown 换真链
        // 注意: down-m.php 页面必须带 Referer=详情页 URL, 否则返回没有 calldown 脚本的残缺页
        val seenFetch = resources.map { it.fetchUrl }.toMutableSet()
        val detailOrigin = Regex("^(https?://[^/]+)").find(detailUrl)?.groupValues?.get(1) ?: baseUrl
        for (a in doc.select("a[href*=down-m.php]")) {
            val href = a.attr("href").trim()
            if (href.isBlank() || !seenFetch.add(href)) continue
            val label = a.text().trim().ifBlank { "种子下载" }
            resources += ResourceItem(
                type = "torrent",
                title = label,
                url = "",
                fetchUrl = if (href.startsWith("http")) href else detailOrigin + "/" + href.removePrefix("/"),
                sourceSite = displayName,
                referer = detailUrl,
            )
        }
        DetailInfo(title = title, resources = resources, sourceSite = displayName)
    }
}

/**
 * 解析比特大雄二次下载页: down-m.php 必须带 Referer=详情页, 页内 JS 有 file_id + fc,
 * POST /calldown/calldown-m.php 返回 JSON {"error":0,"down":"https://m.btdx8.com/downfile.php?..."}
 */
suspend fun resolveBtdx8(engine: HttpEngine, fetchUrl: String, referer: String): String {
    val html = engine.getText(fetchUrl, referer = referer.ifBlank { fetchUrl })
    val fileId = Regex("file_id:\\s*\"(\\d+)\"").find(html)?.groupValues?.get(1) ?: return ""
    val fc = Regex("fc:\\s*\"([^\"]+)\"").find(html)?.groupValues?.get(1) ?: return ""
    val origin = Regex("^(https?://[^/]+)").find(fetchUrl)?.groupValues?.get(1) ?: return ""
    val body = FormBody.Builder().add("file_id", fileId).add("fc", fc).build()
    val json = engine.postText("$origin/calldown/calldown-m.php", body, referer = fetchUrl)
    val err = Regex("\"error\"\\s*:\\s*(\\d+)").find(json)?.groupValues?.get(1)
    val down = Regex("\"down\"\\s*:\\s*\"([^\"]+)\"").find(json)?.groupValues?.get(1)?.replace("\\/", "/")
    return if (err == "0" && !down.isNullOrBlank()) down else ""
}