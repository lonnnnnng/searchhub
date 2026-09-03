package com.searchhub.app.data

import com.searchhub.app.model.DetailInfo
import com.searchhub.app.model.ResourceItem
import com.searchhub.app.model.SearchResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * 狐狸君 www.foxjun.com  /  api.foxjun.com
 * 搜索: GET https://api.{domain}/v1/search?q={kw}&page={n}  JSON,data.list[]:
 *       title,type_badge,category,year,rate,quality,poster,detail_url
 * 详情: GET https://api.{domain}/v1/detail?eid={id}  JSON,data.resources.list[]
 *       type(magnet/netdisk…),url,display,quality,size
 */
class FoxjunAdapter(override val config: SiteConfig, private val engine: HttpEngine) : SiteAdapter {
    override val id = "foxjun"
    override val displayName = "狐狸君"

    private val baseUrl = config.baseUrl.trimEnd('/')

    /** 推断 API 根主机: www.x.com → api.x.com; x.com → api.x.com */
    private val apiBase: String by lazy {
        val host = baseUrl.substringAfter("//").substringBefore("/")
        val scheme = baseUrl.substringBefore("://") + "://"
        val apiHost = if (host.startsWith("www.")) "api." + host.removePrefix("www.")
        else "api." + host
        scheme + apiHost
    }

    override suspend fun search(kw: String, page: Int): List<SearchResult> = withContext(Dispatchers.IO) {
        val url = "$apiBase/v1/search?q=${java.net.URLEncoder.encode(kw.trim(), "UTF-8")}&page=$page"
        val raw = try { engine.getText(url, referer = "$baseUrl/") } catch (e: Exception) { return@withContext emptyList() }
        val out = mutableListOf<SearchResult>()
        try {
            val root = JSONObject(raw)
            if (root.optInt("code") != 0) return@withContext emptyList()
            val data = root.optJSONObject("data") ?: return@withContext emptyList()
            val list = data.optJSONArray("list") ?: return@withContext emptyList()
            for (i in 0 until list.length()) {
                val it = list.optJSONObject(i)
                val title = it.optString("title")
                if (title.isBlank()) continue
                val type = it.optString("type_badge").ifBlank { it.optString("category") }
                val detailUrl = it.optString("detail_url")
                out += SearchResult(
                    title = title,
                    year = it.optString("year"),
                    type = type,
                    quality = it.optString("quality"),
                    rate = it.optString("rate"),
                    sourceSite = displayName,
                    detailUrl = if (detailUrl.startsWith("http")) detailUrl else baseUrl + detailUrl,
                    poster = it.optString("poster"),
                )
            }
        } catch (_: Exception) {
        }
        out
    }

    override suspend fun detail(detailUrl: String): DetailInfo = withContext(Dispatchers.IO) {
        val eid = extractEid(detailUrl)
        android.util.Log.d("FoxjunAdapter", "detail detailUrl=$detailUrl eid=$eid")
        val raw = try {
            engine.getText("$apiBase/v1/detail?eid=$eid", referer = detailUrl)
        } catch (e: Exception) {
            android.util.Log.d("FoxjunAdapter", "detail get failed: ${e.message}, apiBase=$apiBase")
            return@withContext DetailInfo(title = "", resources = emptyList(), sourceSite = displayName)
        }
        val resources = mutableListOf<ResourceItem>()
        var title = ""
        var overview = ""
        var year = ""
        var rate = ""
        var category = ""
        var poster = ""
        try {
            val root = JSONObject(raw)
            if (root.optInt("code") != 0) return@withContext DetailInfo(title = "", resources = emptyList(), sourceSite = displayName)
            val d = root.optJSONObject("data") ?: return@withContext DetailInfo(title = "", resources = emptyList(), sourceSite = displayName)
            title = d.optString("title")
            overview = d.optString("overview")
            year = d.optString("year")
            rate = d.optString("rate")
            category = d.optString("category")
            poster = d.optString("poster")
            val r = d.optJSONObject("resources")
            val list = r?.optJSONArray("list") ?: return@withContext DetailInfo(
                title = title, overview = overview, year = year, rate = rate,
                category = category, poster = poster, resources = emptyList(), sourceSite = displayName,
            )
            for (i in 0 until list.length()) {
                val it = list.optJSONObject(i)
                val type = it.optString("type")
                val u = it.optString("url")
                val disp = it.optString("display").ifBlank { it.optString("title") }
                val q = it.optString("quality")
                val s = it.optString("size")
                resources += ResourceItem(
                    type = type,
                    title = disp,
                    url = u,
                    quality = q,
                    size = s,
                    sourceSite = displayName,
                )
            }
        } catch (_: Exception) {
        }
        DetailInfo(
            title = title, originalTitle = "", year = year, rate = rate,
            category = category, overview = overview, poster = poster,
            resources = resources, sourceSite = displayName,
        )
    }

    companion object {
        fun extractEid(detailUrl: String): String {
            // eid 就是详情 URL 最后一段文件名(去掉扩展名), 如 /archives/donghua/AVqe3mmXZz.html → AVqe3mmXZz
            return detailUrl
                .substringAfterLast('/')
                .substringBeforeLast('.')
                .ifBlank { detailUrl.substringAfterLast('/') }
        }
    }
}