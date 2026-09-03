package com.searchhub.app.data

import com.searchhub.app.model.CaptchaRequest
import com.searchhub.app.model.DetailInfo
import com.searchhub.app.model.ResourceItem
import com.searchhub.app.model.SearchResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Response
import org.jsoup.Jsoup

/**
 * 雪落影视 https://www.xlys02.com
 * 搜索: GET /search/{kw}  SSR;首次需验证码(30min窗口)
 * 验证码: 页面含 <img id=verifyCode src=/search/verifyCode?t=…>
 *   → 手动弹图 → 用户填答案 → GET /search/{kw}?code={答案}(同一 cookie 会话)
 *   → 成功返回结果页(结果 a.card-img href=/{cat}/{id}.htm,title 带年份)
 * 详情: /{cat}/{id}.htm
 */
class XlysAdapter(
    override val config: SiteConfig,
    private val engine: HttpEngine,
    private val captchaFlow: CaptchaFlow,
) : SiteAdapter {
    override val id = "xlys"
    override val displayName = "雪落影视"

    private val baseUrl = config.baseUrl.trimEnd('/')

    /*** 搜索带验证码处理 */
    override suspend fun search(kw: String, page: Int): List<SearchResult> = withContext(Dispatchers.IO) {
        val kEnc = java.net.URLEncoder.encode(kw.trim(), "UTF-8")
        var url = if (page <= 1) "$baseUrl/search/$kEnc" else "$baseUrl/search/$kEnc?page=$page"
        var html = try { engine.getText(url, referer = "$baseUrl/") } catch (e: Exception) { return@withContext emptyList() }
        // ===== 验证码流程: 最多重试 3 次 =====
        repeat(3) {
            if (!needsCaptcha(html)) {
                val parsed = parseResults(html)
                android.util.Log.d("XlysAdapter", "no-captcha, parsed=${parsed.size}, htmlLen=${html.length}")
                return@withContext parsed
            }
            val req = buildCaptchaRequest(html, kEnc, page)
            val answer = try { captchaFlow.ask(req) } catch (e: CaptchaCancelledException) { return@withContext emptyList() }
            // 同一会话提交 code(答案绑定当前 JSESSIONID)
            val submit = req.submitUrl.replace("{code}", answer)
            html = try { engine.getText(submit, referer = url) } catch (e: Exception) {
                android.util.Log.d("XlysAdapter", "submit failed: ${e.message}")
                return@withContext emptyList()
            }
            android.util.Log.d("XlysAdapter", "after submit: len=${html.length} needsCaptcha=${needsCaptcha(html)} hasItem=${html.contains("xl-result-item")} hasName=${html.contains("xl-result-name")} hasCard=${html.contains("card-img")} snippet=${html.take(150).replace('\n',' ')}")
            if (!needsCaptcha(html)) {
                val parsed = parseResults(html)
                android.util.Log.d("XlysAdapter", "submit ok parsed=${parsed.size}")
                return@withContext parsed
            }
        }
        parseResults(html)
    }

    private fun needsCaptcha(html: String): Boolean = html.contains("verifyCode") || html.contains("需要输入验证码")

    private fun buildCaptchaRequest(html: String, kEnc: String, page: Int): CaptchaRequest {
        val doc = Jsoup.parse(html)
        val img = doc.selectFirst("#verifyCode, img[src*=/search/verifyCode]")
        val imgSrc = img?.attr("src") ?: "$baseUrl/search/verifyCode?t=0"
        val imgAbs = if (imgSrc.startsWith("http")) imgSrc else baseUrl + imgSrc
        val submit = if (page <= 1) "$baseUrl/search/$kEnc?code={code}" else "$baseUrl/search/$kEnc?page=$page&code={code}"
        return CaptchaRequest(siteId = id, imageUrl = imgAbs, submitUrl = submit, message = "雪落影视需要验证码(算术题),请看图作答")
    }

    private fun parseResults(html: String): List<SearchResult> {
        val out = mutableListOf<SearchResult>()
        // 方法1: jsoup 结构化解析
        try {
            val doc = Jsoup.parse(html)
            for (card in doc.select(".xl-result-item")) {
                // 优先取 .xl-result-name(标题链接);不要匹配 a[href*=.htm],否则会选中只含图片、无标题的 .xl-result-poster
                val a = card.selectFirst("a.xl-result-name") ?: continue
                val href = a.attr("href")
                if (!href.contains(".htm")) continue
                val title = a.text().trim()
                if (title.isBlank()) continue
                out += buildResult(href, title)
            }
        } catch (_: Exception) {}

        // 方法2: 正则兜底(若 jsoup 异常或类名解析失败)
        if (out.isEmpty()) {
            try {
                val re = Regex("class=\"xl-result-name\"[^>]*href=\"([^\"]+\\.htm)\"[^>]*>([\\s\\S]*?)</a>")
                for (m in re.findAll(html)) {
                    val href = m.groupValues[1]
                    val title = Jsoup.parse(m.groupValues[2]).text().trim()
                    if (title.isBlank()) continue
                    out += buildResult(href, title)
                }
            } catch (_: Exception) {}
        }

        android.util.Log.d("XlysAdapter", "parse done: ${out.size} items")
        return out
    }

    private fun buildResult(href: String, title: String): SearchResult {
        val year = Regex("(19\\d{2}|20\\d{2})").find(title)?.groupValues?.get(1) ?: ""
        val quality = Regex("(4K|1080P|720P|720p|蓝光|HD)", RegexOption.IGNORE_CASE).find(title)?.groupValues?.get(1) ?: ""
        val type = Regex("(国剧|美剧|韩剧|日剧|泰剧|电影|动漫|剧集)").find(title)?.groupValues?.get(1) ?: ""
        return SearchResult(
            title = title,
            year = year,
            type = type,
            quality = quality.ifBlank { "HD" },
            sourceSite = displayName,
            detailUrl = if (href.startsWith("http")) href else baseUrl + href,
        )
    }

    override suspend fun detail(detailUrl: String): DetailInfo = withContext(Dispatchers.IO) {
        val html = try { engine.getText(detailUrl) } catch (e: Exception) {
            return@withContext DetailInfo(title = "", resources = emptyList(), sourceSite = displayName)
        }
        val doc = Jsoup.parse(html)
        val title = doc.select("h1").firstOrNull()?.text() ?: ""
        val resources = mutableListOf<ResourceItem>()
        // 尽量用表格行解析: <tr> 内 <td>类型</td><td><a href=.../></td><td>备注</td>
        for (tr in doc.select("table tbody tr")) {
            val tds = tr.select("td")
            if (tds.size < 2) continue
            val typeLabel = tds[0].text().trim()          // 磁力链接 / 第33集 / 百度网盘…
            val a = tds[1].selectFirst("a") ?: continue
            val href = a.attr("href")
            val t = a.text().trim().ifBlank { typeLabel }
            if (t.isBlank()) continue
            val note = if (tds.size >= 3) tds[2].text().trim() else ""
            val full = if (note.isNotBlank()) "$t [$note]" else t
            val kind = when {
                href.startsWith("magnet:") -> "magnet"
                href.startsWith("thunder:") -> "thunder"
                href.startsWith("ed2k:") -> "ed2k"
                Regex("(pan\\.(baidu|quark|xunlei)\\.[a-z]+|aliyundrive|cloud\\.189|uc\\.cn)").containsMatchIn(href) -> "netdisk"
                href.endsWith(".torrent") || href.contains("torrent") -> "torrent"
                href.startsWith("http") -> "http"
                else -> "other"
            }
            resources += ResourceItem(kind, full, href, sourceSite = displayName)
        }
        // 表格外兜底: 直接 a[href*=magnet/netdisk]
        if (resources.isEmpty()) {
            for (a in doc.select("a")) {
                val href = a.attr("href")
                val t = a.text().trim()
                if (t.isBlank()) continue
                when {
                    href.startsWith("magnet:") -> resources += ResourceItem("magnet", t, href, sourceSite = displayName)
                    href.startsWith("thunder:") || href.contains("thunder") -> resources += ResourceItem("thunder", t, href, sourceSite = displayName)
                    Regex("(pan\\.(baidu|quark|xunlei)\\.[a-z]+|aliyundrive|cloud\\.189|uc\\.cn)").containsMatchIn(href) ->
                        resources += ResourceItem("netdisk", t, href, sourceSite = displayName)
                    href.endsWith(".torrent") || href.contains("torrent") -> resources += ResourceItem("torrent", t, href, sourceSite = displayName)
                }
            }
        }
        DetailInfo(title = title, resources = resources, sourceSite = displayName)
    }
}