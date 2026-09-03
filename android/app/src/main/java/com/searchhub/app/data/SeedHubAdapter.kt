package com.searchhub.app.data

import android.util.Base64
import com.searchhub.app.model.DetailInfo
import com.searchhub.app.model.ResourceItem
import com.searchhub.app.model.SearchResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup

/**
 * SeedHub https://www.seedhub.cc
 * 搜索: GET /s/{kw}/  SSR,结果 .cover 块 a[href=/movies/{id}/]
 * 详情: /movies/{id}/ 资源列表:
 *   - 磁力: /link_start/?seed_id={n}… 进入页面后内联脚本 const data="<base64>" → atob = magnet
 *   - 网盘: /link_start/?redirect_to=pan_id_{n}… 二维码转存(展示 url 即可)
 */
class SeedHubAdapter(override val config: SiteConfig, private val engine: HttpEngine) : SiteAdapter {
    override val id = "seedhub"
    override val displayName = "SeedHub"

    private val baseUrl = config.baseUrl.trimEnd('/')

    override suspend fun search(kw: String, page: Int): List<SearchResult> = withContext(Dispatchers.IO) {
        val kwEnc = java.net.URLEncoder.encode(kw.trim(), "UTF-8")
        val url = if (page <= 1) "$baseUrl/s/$kwEnc/" else "$baseUrl/s/$kwEnc/?page=$page"
        val html = try { engine.getText(url, referer = "$baseUrl/") } catch (e: Exception) { return@withContext emptyList() }
        // Cloudflare 挑战拦截
        if (html.contains("Just a moment") || html.contains("cf-challenge") || html.contains("challenge-platform")) {
            return@withContext emptyList()
        }
        val doc = Jsoup.parse(html)
        val out = mutableListOf<SearchResult>()
        for (cover in doc.select(".cover, .movie-item, [class*=item]")) {
            val a = cover.selectFirst("a[href*=/movies/]") ?: continue
            val href = a.attr("href")
            // 标题优先在 a.title 属性或 h2 a
            val h2 = cover.selectFirst("h2 a, h3 a, .title a")
            val title = a.attr("title").ifBlank { h2?.text() ?: "" }
            if (title.isBlank()) continue
            val meta = Regex("\\d{4}").find(cover.text())?.groupValues?.get(0) ?: ""
            out += SearchResult(
                title = title,
                year = meta,
                sourceSite = displayName,
                detailUrl = if (href.startsWith("http")) href else baseUrl + href,
                poster = "", // 图片走静态域名,可后续补
            )
        }
        out
    }

    override suspend fun detail(detailUrl: String): DetailInfo = withContext(Dispatchers.IO) {
        val html = try { engine.getText(detailUrl) } catch (e: Exception) {
            return@withContext DetailInfo(title = "", resources = emptyList(), sourceSite = displayName)
        }
        if (html.contains("Just a moment") || html.contains("challenge-platform")) {
            return@withContext DetailInfo(title = "", resources = emptyList(), sourceSite = displayName)
        }
        val doc = Jsoup.parse(html)
        val title = doc.select("h1").firstOrNull()?.text() ?: doc.title()
        val resources = mutableListOf<ResourceItem>()
        // 磁力 +网盘资源链接都形如 /link_start/?
        for (a in doc.select("a[href*=/link_start/]")) {
            val href = a.attr("href")
            val t = a.text().trim()
            if (t.isBlank()) continue
            val kind = when {
                href.contains("seed_id") -> "magnet"
                href.contains("pan_id") -> "netdisk"
                else -> continue
            }
            resources += ResourceItem(
                type = kind,
                title = t,
                fetchUrl = if (href.startsWith("http")) href else baseUrl + href,
                sourceSite = displayName,
            )
        }
        DetailInfo(title = title, resources = resources, sourceSite = displayName)
    }
}

/** 访问 /link_start/ 页面,解析内联 base64 → 真实磁力链接 */
suspend fun resolveSeedHubLink(engine: HttpEngine, fetchUrl: String): String {
    val html = try { engine.getText(fetchUrl) } catch (e: Exception) { return "" }
    // 页面内 <script> const data = "base64";  window.atob(data)
    val m = Regex("const\\s+data\\s*=\\s*\"([A-Za-z0-9+/=]+)\"").find(html) ?: return ""
    val b64 = m.groupValues[1]
    val bytes = try { Base64.decode(b64, Base64.DEFAULT) } catch (e: Exception) { return "" }
    val decoded = String(bytes)
    if (decoded.startsWith("magnet:")) return decoded
    return ""
}