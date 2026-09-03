package com.searchhub.app.data

import com.searchhub.app.model.DetailInfo
import com.searchhub.app.model.SearchResult

/** 站点适配器接口:每个站实现搜索与详情解析 */
interface SiteAdapter {
    val id: String
    val displayName: String
    val config: SiteConfig

    /** 搜索结果第 page 页;超出总页数返回空列表 */
    suspend fun search(kw: String, page: Int = 1): List<SearchResult>

    /** 解析详情页资源 */
    suspend fun detail(detailUrl: String): DetailInfo
}