package com.searchhub.app.data

/** 单个站点配置(域名可自定义) */

data class SiteConfig(
    val id: String,             // 稳定标识: btbtlb / foxjun / xlys / seedhub
    val name: String,           // 显示名
    val baseUrl: String,        // 根域名,如 https://www.foxjun.com
    val enabled: Boolean = true,
    val searchPath: String = "",// 搜索路径模板,${kw} 为关键词占位
    val matchUrl: String = "",  // 搜索 / 详情跳转的基路径,便于按 href 分组
)

object SiteDefaults {
    val DEFAULT_SITES = listOf(
        SiteConfig(
            id = "btbtlb", name = "BT影视",
            baseUrl = "https://www.btbtlb.com",
            searchPath = "/search/\${kw}",
            matchUrl = "/detail/",
        ),
        SiteConfig(
            id = "foxjun", name = "狐狸君",
            baseUrl = "https://www.foxjun.com",
            searchPath = "/search?q=\${kw}",
            matchUrl = "/archives/",
        ),
        SiteConfig(
            id = "xlys", name = "雪落影视",
            baseUrl = "https://www.xlys02.com",
            searchPath = "/search/\${kw}",
            matchUrl = "/v5/",
        ),
        SiteConfig(
            id = "seedhub", name = "SeedHub",
            baseUrl = "https://www.seedhub.cc",
            searchPath = "/s/\${kw}/",
            matchUrl = "/movies/",
        ),
        SiteConfig(
            id = "binhd", name = "云集",
            baseUrl = "https://binhd.com",
            searchPath = "/resources/?q=\${kw}",
            matchUrl = "/resources/",
        ),
        SiteConfig(
            id = "sixv520", name = "6v520",
            baseUrl = "https://www.6v520.com",
            searchPath = "/e/search/index.php",
            matchUrl = "/dy/",
        ),
        SiteConfig(
            id = "dygang", name = "电影港",
            baseUrl = "https://www.dygang.tv",
            searchPath = "/e/search/index.php",
            matchUrl = "/",
        ),
        SiteConfig(
            id = "dytt8899", name = "电影天堂",
            baseUrl = "https://www.dytt8899.com",
            searchPath = "/e/search/index.php",
            matchUrl = "/i/",
        ),
        SiteConfig(
            id = "451024", name = "451024",
            baseUrl = "https://video.451024.xyz",
            searchPath = "/",
            matchUrl = "/",
        ),
        SiteConfig(
            id = "showpaw", name = "Showpaw",
            baseUrl = "https://www.showpaw.xyz",
            searchPath = "/",
            matchUrl = "/",
        ),
        SiteConfig(
            id = "btdx8", name = "比特大雄",
            baseUrl = "https://www.btdx8.net",
            searchPath = "/?s=\${kw}",
            matchUrl = "/torrent/",
        ),
        SiteConfig(
            id = "xb6v", name = "新版6v",
            baseUrl = "https://www.xb6v.com",
            searchPath = "/e/search/11index.php",
            matchUrl = "/",
        ),
    )
}
