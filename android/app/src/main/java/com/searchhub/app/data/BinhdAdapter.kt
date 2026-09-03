package com.searchhub.app.data

import com.searchhub.app.model.DetailInfo
import com.searchhub.app.model.ResourceItem
import com.searchhub.app.model.SearchResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import org.jsoup.Jsoup

/**
 * 云集 binhd https://binhd.com
 * 搜索: GET /resources/?q={kw}  SSR;结果 article.resource-other-row(h2 a + meta)
 * 详情: /resources/legacy-post-{id}/
 * 下载: 卡片 .resource-download-card 内 form action=/…/links/{id}/download/ + csrf
 *       POST(带 csrf) → 303 Location → 真实网盘链接(pan.xunlei.com?pwd=提取码)
 */
class BinhdAdapter(override val config: SiteConfig, private val engine: HttpEngine) : SiteAdapter {
    override val id = "binhd"
    override val displayName = "云集"

    private val baseUrl = config.baseUrl.trimEnd('/')

    override suspend fun search(kw: String, page: Int): List<SearchResult> = withContext(Dispatchers.IO) {
        if (page > 1) return@withContext emptyList()
        val url = "$baseUrl/resources/?q=${java.net.URLEncoder.encode(kw.trim(), "UTF-8")}"
        val html = try { engine.getText(url, referer = "$baseUrl/") } catch (e: Exception) { return@withContext emptyList() }
        val doc = Jsoup.parse(html)
        val out = mutableListOf<SearchResult>()
        for (item in doc.select("article.resource-other-row")) {
            val a = item.selectFirst("h2 a[href]") ?: continue
            val href = a.attr("href")
            if (!href.contains("legacy-post")) continue
            val title = a.text().trim()
            if (title.isBlank()) continue
            val meta = item.selectFirst(".resource-other-row__meta")?.text() ?: ""
            val year = Regex("(19\\d{2}|20\\d{2})").find(meta)?.groupValues?.get(1) ?: ""
            val kind = item.attr("data-resource-kind")
            val type = when (kind) {
                "movie" -> "电影"
                "tv" -> "剧集"
                "anime" -> "动漫"
                "documentary" -> "纪录片"
                else -> ""
            }
            out += SearchResult(
                title = title,
                year = year,
                type = type,
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
        val title = doc.select("h1").firstOrNull()?.text()
            ?: doc.title().substringBefore(" - ") ?: ""
        val resources = mutableListOf<ResourceItem>()

        // 从所有 form 中收集 csrf token(整个页面统一一个)
        val globalCsrf = doc.selectFirst("input[name=csrfmiddlewaretoken]")?.attr("value") ?: ""

        for (card in doc.select("article.resource-download-card")) {
            val provider = card.selectFirst(".resource-download-card__provider")?.text() ?: "网盘"
            val pill = card.selectFirst(".resource-download-card__pill")?.text() ?: ""
            val code = Regex("提取码[:：]?(\\S+)").find(pill)?.groupValues?.get(1) ?: ""
            val form = card.selectFirst("form[action*=/links/]") ?: continue
            val action = form.attr("action")
            if (action.isBlank()) continue
            val csrf = form.selectFirst("input[name=csrfmiddlewaretoken]")?.attr("value") ?: globalCsrf
            val fetchUrl = if (action.startsWith("http")) action else baseUrl + action
            val label = if (code.isNotBlank()) "$provider (提取码:$code)" else provider
            resources += ResourceItem(
                type = "netdisk",
                title = label,
                fetchUrl = fetchUrl,
                sourceSite = displayName,
                postBody = if (csrf.isNotBlank()) "csrfmiddlewaretoken=" + csrf else "",
            )
        }
        DetailInfo(title = title, resources = resources, sourceSite = displayName)
    }
}

/** 解析 binhd 下载 POST → 真实网盘链接 */
suspend fun resolveBinhdLink(engine: HttpEngine, fetchUrl: String, csrf: String): String {
    val body = FormBody.Builder().add("csrfmiddlewaretoken", csrf).build()
    return try {
        engine.post(fetchUrl, body, referer = fetchUrl)
    } catch (e: Exception) {
        ""
    }
}