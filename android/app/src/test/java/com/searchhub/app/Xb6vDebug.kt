package com.searchhub.app

import com.searchhub.app.data.HttpEngine
import kotlinx.coroutines.runBlocking
import okhttp3.FormBody
import org.jsoup.Jsoup
import org.junit.Test

class Xb6vDebug {
    @Test
    fun xb6vSearchDebug() = runBlocking {
        val engine = HttpEngine()
        val body = FormBody.Builder()
            .add("show", "title,smalltext")
            .add("tempid", "1")
            .add("keyboard", "batman")
            .add("tbname", "article")
            .add("mid", "1")
            .add("dopost", "search")
            .add("catid", "0")
            .build()
        val html = engine.postText("https://www.xb6v.com/e/search/11index.php", body, referer = "https://www.xb6v.com/")
        val doc = Jsoup.parse(html)
        val zooms = doc.select("#post_container a.zoom")
        println("a.zoom=${zooms.size}")
        zooms.forEach { println("  ${it.attr("title").ifBlank { it.text() }.take(34)}") }
    }
}
