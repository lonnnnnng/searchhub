package com.searchhub.app.data

import com.searchhub.app.model.DetailInfo
import com.searchhub.app.model.ResourceItem
import com.searchhub.app.model.SearchResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.net.URLEncoder

/**
 * Showpaw 海外影视网盘资源搜索 www.showpaw.xyz
 * 搜索: POST https://www.showpaw.xyz/api/search  body {"query":"kw"}
 * 响应: tmdb(元数据) + resources[](panType/panUrl/passcode/quality) + magnetResources[]
 * 网盘直链直接可用。
 */
class ShowpawAdapter(override val config: SiteConfig, private val engine: HttpEngine) : SiteAdapter {
    override val id = "showpaw"
    override val displayName = "Showpaw"

    private val baseUrl = config.baseUrl.trimEnd('/')
    private val jsonType = "application/json; charset=utf-8".toMediaType()

    override suspend fun search(kw: String, page: Int): List<SearchResult> = withContext(Dispatchers.IO) {
        if (page > 1) return@withContext emptyList()
        val payload = JSONObject().put("query", kw.trim()).toString().toRequestBody(jsonType)
        val raw = try { engine.postJson("$baseUrl/api/search", payload, referer = "$baseUrl/") } catch (e: Exception) { return@withContext emptyList() }
        val out = mutableListOf<SearchResult>()
        try {
            val root = JSONObject(raw)
            val resources = root.optJSONArray("resources") ?: return@withContext emptyList()
            val tmdb = root.optJSONObject("tmdb")
            // 单个资源即可作为一条搜索结果(同名不同版本)
            for (i in 0 until resources.length()) {
                val it = resources.optJSONObject(i)
                val title = it.optString("title")
                if (title.isBlank()) continue
                val panUrl = it.optString("panUrl")
                if (panUrl.isBlank()) continue
                val panType = it.optString("panType")
                val passcode = it.optString("passcode")
                val quality = it.optString("quality")
                val year = tmdb?.optString("firstAirDate")?.take(4)
                    ?: tmdb?.optString("releaseDate")?.take(4)
                    ?: Regex("(19\\d{2}|20\\d{2})").find(title)?.groupValues?.get(1) ?: ""
                val type = when (tmdb?.optString("type")) {
                    "tv" -> "剧集"
                    "movie" -> "电影"
                    else -> ""
                }
                val detailUrl = "showpaw://" + URLEncoder.encode(
                    listOf(
                        "title=${URLEncoder.encode(title, "UTF-8")}",
                        "link=${URLEncoder.encode(panUrl, "UTF-8")}",
                        "type=${URLEncoder.encode(panType, "UTF-8")}",
                        "pass=${URLEncoder.encode(passcode, "UTF-8")}",
                        "quality=${URLEncoder.encode(quality, "UTF-8")}",
                    ).joinToString("&"),
                    "UTF-8",
                )
                out += SearchResult(
                    title = title,
                    year = year,
                    type = type,
                    quality = quality,
                    sourceSite = displayName,
                    detailUrl = detailUrl,
                )
            }
        } catch (_: Exception) {
        }
        out.take(15)
    }

    override suspend fun detail(detailUrl: String): DetailInfo = withContext(Dispatchers.IO) {
        val dec = java.net.URLDecoder.decode(detailUrl.removePrefix("showpaw://"), "UTF-8")
        val m = HashMap<String, String>()
        dec.split("&").forEach { kv ->
            val p = kv.split("=", limit = 2)
            if (p.size == 2) m[p[0]] = java.net.URLDecoder.decode(p[1], "UTF-8")
        }
        val title = m["title"] ?: ""
        val link = m["link"] ?: ""
        val panType = m["type"] ?: ""
        val pass = m["pass"] ?: ""
        val quality = m["quality"] ?: ""
        val resources = mutableListOf<ResourceItem>()
        if (link.isNotBlank()) {
            val name = when (panType) {
                "baidu" -> "百度网盘"
                "quark" -> "夸克网盘"
                "xunlei" -> "迅雷云盘"
                "ali" -> "阿里云盘"
                "115" -> "115网盘"
                else -> "网盘"
            }
            val label = if (pass.isNotBlank()) "$name (提取码:$pass)" else name
            resources += ResourceItem("netdisk", label, link, quality = quality, sourceSite = displayName)
        }
        DetailInfo(title = title, resources = resources, sourceSite = displayName)
    }
}