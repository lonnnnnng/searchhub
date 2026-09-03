package com.searchhub.app.data

import com.searchhub.app.model.DetailInfo
import com.searchhub.app.model.ResourceItem
import com.searchhub.app.model.SearchResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.net.URLDecoder
import java.net.URLEncoder

/**
 * 451024 影视搜索 video.451024.xyz
 * 搜索: POST https://apis.451024.xyz/api/media/search  body {"title":"kw","page":1,"size":10}
 * 响应 data[]: id/title/link(网盘直链)/link_type(quark|xunlei|baidu)/content/tags
 * 资源即网盘直链, 无需二次跳转。
 * 设计: detailUrl 编码 title/link/linkType, detail() 还原为网盘资源。
 */
class Seven451024Adapter(override val config: SiteConfig, private val engine: HttpEngine) : SiteAdapter {
    override val id = "451024"
    override val displayName = "451024"

    private val baseUrl = config.baseUrl.trimEnd('/')
    private val apiBase = "https://apis.451024.xyz"
    private val jsonType = "application/json; charset=utf-8".toMediaType()

    override suspend fun search(kw: String, page: Int): List<SearchResult> = withContext(Dispatchers.IO) {
        val payload = JSONObject()
            .put("title", kw.trim())
            .put("page", page)
            .put("size", 10)
            .toString()
            .toRequestBody(jsonType)
        val raw = try { engine.postJson("$apiBase/api/media/search", payload, referer = "$baseUrl/") } catch (e: Exception) { return@withContext emptyList() }
        val out = mutableListOf<SearchResult>()
        try {
            val root = JSONObject(raw)
            val data = root.optJSONArray("data") ?: return@withContext emptyList()
            for (i in 0 until data.length()) {
                val it = data.optJSONObject(i)
                val title = it.optString("title")
                if (title.isBlank()) continue
                val link = it.optString("link")
                val linkType = it.optString("link_type")
                val tags = it.optString("tags")
                val year = Regex("(19\\d{2}|20\\d{2})").find(title)?.groupValues?.get(1)
                    ?: Regex("(19\\d{2}|20\\d{2})").find(tags)?.groupValues?.get(1) ?: ""
                val quality = Regex("(4K|1080P|1080p|720P|720p|蓝光|HD|BD)", RegexOption.IGNORE_CASE).find(title)?.groupValues?.get(1) ?: ""
                val detailUrl = "451024://" + URLEncoder.encode(
                    listOf(
                        "t=${encode(title)}",
                        "link=${encode(link)}",
                        "type=${encode(linkType)}",
                    ).joinToString("&"),
                    "UTF-8",
                )
                out += SearchResult(
                    title = title,
                    year = year,
                    quality = quality,
                    sourceSite = displayName,
                    detailUrl = detailUrl,
                )
            }
        } catch (_: Exception) {
        }
        out
    }

    override suspend fun detail(detailUrl: String): DetailInfo = withContext(Dispatchers.IO) {
        val p = parseDetailUrl(detailUrl)
        val resources = mutableListOf<ResourceItem>()
        val title = p["t"] ?: ""
        val link = p["link"] ?: ""
        if (link.isNotBlank()) {
            val lt = p["type"] ?: ""
            val name = when (lt) {
                "quark" -> "夸克网盘"
                "baidu" -> "百度网盘"
                "xunlei" -> "迅雷云盘"
                "ali" -> "阿里云盘"
                else -> "网盘"
            }
            resources += ResourceItem("netdisk", name, link, sourceSite = displayName)
        }
        DetailInfo(title = title, resources = resources, sourceSite = displayName)
    }

    private fun encode(s: String): String = URLEncoder.encode(s, "UTF-8")

    private fun parseDetailUrl(detailUrl: String): Map<String, String> {
        val decoded = URLDecoder.decode(detailUrl.removePrefix("451024://"), "UTF-8")
        val m = HashMap<String, String>()
        decoded.split("&").forEach { kv ->
            val p = kv.split("=", limit = 2)
            if (p.size == 2) m[p[0]] = p[1]
        }
        return m
    }
}