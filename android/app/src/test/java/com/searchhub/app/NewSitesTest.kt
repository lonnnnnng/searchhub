package com.searchhub.app

import com.searchhub.app.data.CaptchaFlow
import com.searchhub.app.data.HttpEngine
import com.searchhub.app.data.SearchRepository
import com.searchhub.app.data.SiteDefaults
import com.searchhub.app.model.CaptchaAnswer
import kotlinx.coroutines.runBlocking
import org.junit.Test

/** 新接入站点(4K指南/悠悠MP4)搜索+详情链路验证 */
class NewSitesTest {
    @Test
    fun newSitesAudit() = runBlocking {
        val engine = HttpEngine()
        val repo = SearchRepository(engine, CaptchaFlow { CaptchaAnswer("0") })
        repo.rebuild(SiteDefaults.DEFAULT_SITES.filter { it.id in setOf("4kzn", "uump4") })
        for (kw in listOf("蜘蛛侠", "batman")) {
            println("==== kw=$kw ====")
            val results = repo.searchAll(kw)
            for ((site, items) in results.groupBy { it.sourceSite }) {
                println("$site — ${items.size} 条")
                items.take(2).forEachIndexed { i, item ->
                    val d = runCatching { repo.detail(item) }.getOrNull()
                    val types = d?.resources?.groupingBy { it.type }?.eachCount()
                    println("  [$i] res=${d?.resources?.size} $types | ${item.title.take(34)}")
                    d?.resources?.take(2)?.forEach { r -> println("      ${r.type}: ${r.url.take(60)}") }
                }
            }
        }
    }
}
