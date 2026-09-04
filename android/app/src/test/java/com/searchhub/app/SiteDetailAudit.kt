package com.searchhub.app

import com.searchhub.app.data.CaptchaFlow
import com.searchhub.app.data.HttpEngine
import com.searchhub.app.data.SearchRepository
import com.searchhub.app.data.SiteDefaults
import com.searchhub.app.model.CaptchaAnswer
import kotlinx.coroutines.runBlocking
import org.junit.Test

/**
 * 全站详情资源审计: 搜索 → 每站取前 N 条 → 解析详情 → 统计资源数。
 * 用于发现"详情页 0 资源"的站点/条目(适配器改版、站点结构变化)。
 * 本地真跑网络, 仅手动触发: ./gradlew :app:testDebugUnitTest --tests *SiteDetailAudit*
 */
class SiteDetailAudit {

    @Test
    fun auditChineseKeyword() = runBlocking {
        auditKeyword("蜘蛛侠")
    }

    @Test
    fun btdx8DownPageDebug() = runBlocking {
        val downUrl = "http://m.btdx8.net/down-m.php?afa1xdAqK9jRrLdSBhNeAGWMSGyIS3XXLaP/1KCtoqvfr8Mw1fGs3fngAK60TkloczC9/0AjaTtjwDX6mZA57QlFdKIQZ805UMLq85iRPYbmx7a2NnVF6unvNf7oCfVdGbCczqz3GZk9dsDioG885aWtQn0tMEFq6e4UNbzYjDr8kJ05LGB/x6cvY7I"
        val ua = HttpEngine.UA
        // 头部矩阵: 找出哪些头导致站点返回"无 calldown"的页面
        val variants: List<Pair<String, List<Pair<String, String>>>> = listOf(
            "baseline(UA only)" to listOf("User-Agent" to ua),
            "+AcceptLang" to listOf("User-Agent" to ua, "Accept-Language" to "zh-CN,zh;q=0.9,en;q=0.8"),
            "+AcceptHTML" to listOf("User-Agent" to ua, "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8"),
            "+Referer" to listOf("User-Agent" to ua, "Referer" to downUrl),
            "curl-like" to listOf("User-Agent" to ua, "Accept" to "*/*"),
        )
        for ((name, headers) in variants) {
            val b = okhttp3.Request.Builder().url(downUrl)
            headers.forEach { (k, v) -> b.header(k, v) }
            val resp = okhttp3.OkHttpClient.Builder()
                .followRedirects(true).followSslRedirects(true)
                .connectTimeout(12, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                .build().newCall(b.build()).execute()
            val body = resp.body?.string().orEmpty()
            println("[$name] code=${resp.code} len=${body.length} hasFileId=${body.contains("file_id")} hasCalldown=${body.contains("calldown")} hasTemplateVar=${body.contains("TEMPLATE_DIRECTORY")}")
            resp.close()
        }
    }

    @Test
    fun btdx8NewEntryDebug() = runBlocking {
        val engine = HttpEngine()
        // 蜘蛛侠 res=0 的新条目: 从搜索拿到真实 detailUrl 再抓原始 HTML
        val repo = SearchRepository(engine, CaptchaFlow { CaptchaAnswer("0") })
        repo.rebuild(SiteDefaults.DEFAULT_SITES.filter { it.id == "btdx8" })
        val items = repo.searchAll("蜘蛛侠").filter { it.sourceSite == "比特大雄" }
        println("btdx8 items=${items.size}")
        items.take(2).forEach { item ->
            println("URL ${item.detailUrl}")
            val html = engine.getText(item.detailUrl, referer = item.detailUrl)
            println("len=${html.length} title=${Regex("<title>.*?</title>").find(html)?.value}")
            val i = html.indexOf("magnet")
            println("magnet at=$i ctx=${if (i >= 0) html.substring(i, minOf(html.length, i + 200)) else "NONE"}")
            val j = html.indexOf("zdownload")
            println("zdownload at=$j ctx=${if (j >= 0) html.substring(j, minOf(html.length, j + 200)) else "NONE"}")
        }
    }

    @Test
    fun btdx8ResolveChain() = runBlocking {
        // 比特大雄老条目: 详情 down-m.php → 带详情页 Referer 取 file_id/fc → calldown → 真链
        val engine = HttpEngine()
        val repo = SearchRepository(engine, CaptchaFlow { CaptchaAnswer("0") })
        repo.rebuild(SiteDefaults.DEFAULT_SITES.filter { it.id == "btdx8" })
        // 直接构造老条目详情(rzbfx_2018 = 忍者蝙蝠侠 2018, 无直链磁力)
        val d = repo.detail(com.searchhub.app.model.SearchResult(
            title = "", sourceSite = "比特大雄", detailUrl = "https://www.btdx8.net/torrent/rzbfx_2018.html",
        ))
        println("res=${d.resources.size} 需解析=${d.resources.count { it.fetchUrl.isNotBlank() }}")
        d.resources.filter { it.fetchUrl.isNotBlank() }.forEach { r ->
            val resolved = repo.resolveResource(r)
            println("resolve [${r.title.take(20)}] → ${resolved.url.take(70).ifBlank { "EMPTY" }}")
        }
    }

    @Test
    fun auditSilentSites() = runBlocking {
        // searchAll 会吞异常, 单独跑无结果的站并打印原因
        val engine = HttpEngine()
        val repo = SearchRepository(engine, CaptchaFlow { CaptchaAnswer("0") })
        repo.rebuild(SiteDefaults.DEFAULT_SITES.filter { it.id in setOf("foxjun", "451024", "duanjugou", "showpaw", "binhd") })
        val kw = "batman"
        val results = repo.searchAll(kw)
        println("总数=${results.size} 按站=${results.groupBy { it.sourceSite }.mapValues { it.value.size }}")
        // binhd 0 资源条目比例: 取 4 条看详情
        val binhd = results.filter { it.sourceSite == "云集" }
        binhd.take(4).forEachIndexed { i, item ->
            val d = runCatching { repo.detail(item) }.getOrNull()
            println("云集[$i] res=${d?.resources?.size} | ${item.title.take(30)} | ${item.detailUrl.take(60)}")
        }
    }

    @Test
    fun auditDuandjuAndSeedHub() = runBlocking {
        val engine = HttpEngine()
        val repo = SearchRepository(engine, CaptchaFlow { CaptchaAnswer("0") })
        repo.rebuild(SiteDefaults.DEFAULT_SITES.filter { it.id in setOf("duanjugou", "seedhub") })
        // 短剧狗是短剧站, 用短剧关键词测
        val r1 = repo.searchAll("豪门")
        println("短剧狗豪门: ${r1.filter { it.sourceSite == "短剧狗" }.size} 条")
        val d = r1.filter { it.sourceSite == "短剧狗" }.take(1).map { runCatching { repo.detail(it) }.getOrNull() }
        println("短剧狗详情: ${d.map { "res=${it?.resources?.size}" }}")
        // SeedHub: 用 JVM Base64 验证解析页逻辑(避开 android.util.Base64 stub)
        val r2 = repo.searchAll("蜘蛛侠")
        val sh = r2.filter { it.sourceSite == "SeedHub" }
        println("SeedHub 蜘蛛侠: ${sh.size} 条")
        sh.take(1).forEach { item ->
            val info = runCatching { repo.detail(item) }.getOrNull()
            info?.resources?.take(2)?.forEach { r ->
                println("SeedHub fetchUrl=${r.fetchUrl.take(70)}")
                val html = runCatching { engine.getText(r.fetchUrl) }.getOrNull().orEmpty()
                println("  page len=${html.length} hasConstData=${html.contains("const")} title=${Regex("<title>.*?</title>").find(html)?.value?.take(50)}")
            }
        }
    }

    private suspend fun auditKeyword(kw: String) {
        val engine = HttpEngine()
        val repo = SearchRepository(engine, CaptchaFlow { CaptchaAnswer("0") })
        repo.rebuild(SiteDefaults.DEFAULT_SITES)
        val results = repo.searchAll(kw)
        val bySite = results.groupBy { it.sourceSite }

        val out = StringBuilder("\n========== AUDIT kw=$kw ==========\n")
        for ((site, items) in bySite) {
            out.appendLine("$site — 搜索 ${items.size} 条")
            items.take(2).forEachIndexed { i, item ->
                val line = try {
                    val d = repo.detail(item)
                    val types = d.resources.groupingBy { it.type }.eachCount()
                    val needsResolve = d.resources.count { it.fetchUrl.isNotBlank() }
                    "  [$i] res=${d.resources.size} $types 需二次解析=$needsResolve | ${item.title.take(36)}"
                } catch (e: Exception) {
                    "  [$i] 详情异常: ${e.message?.take(80)} | ${item.title.take(36)}"
                }
                out.appendLine(line)
                // 顺带验证二次解析(比特大雄 calldown 等)
                val d = runCatching { repo.detail(item) }.getOrNull() ?: return@forEachIndexed
                d.resources.firstOrNull { it.fetchUrl.isNotBlank() }?.let { r ->
                    val resolved = runCatching { repo.resolveResource(r) }.getOrNull()
                    out.appendLine("      resolve→ ${if (resolved?.url?.isNotBlank() == true) "OK ${resolved.url.take(60)}" else "EMPTY"}")
                }
            }
        }
        val missing = SiteDefaults.DEFAULT_SITES.map { it.name } - bySite.keys
        out.appendLine("无结果站点: $missing")
        out.appendLine("========== END ==========")
        println(out)
    }
}
