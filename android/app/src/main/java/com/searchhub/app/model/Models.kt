package com.searchhub.app.model

/** 单个搜索结果条目 */
data class SearchResult(
    val title: String,
    val year: String = "",
    val type: String = "",          // 电影/剧集/动漫
    val quality: String = "",
    val rate: String = "",
    val sourceSite: String,          // 站点 display 名
    val detailUrl: String,           // 源站详情页 URL
    val poster: String = "",         // 海报 URL(可选)
)

/** 详情页中的一个资源(磁力/网盘/种子…) */
data class ResourceItem(
    val type: String,                // magnet / netdisk / torrent / thunder / ed2k / http / other
    val title: String,
    val url: String = "",            // 最终可直接复制的链接(magnet:/网盘URL…);为空则需 fetchUrl 二次跳转获取
    val fetchUrl: String = "",       // 若 url 为空,访问该页可换取真实链接(如 /tdown/xxx.html / download POST 端点)
    val quality: String = "",
    val size: String = "",
    val sourceSite: String = "",
    val postBody: String = "",       // 若需 POST,携带的表单体(如 csrf=xxx)
)

/** 详情解析结果 */
data class DetailInfo(
    val title: String,
    val originalTitle: String = "",
    val year: String = "",
    val rate: String = "",
    val category: String = "",
    val overview: String = "",
    val poster: String = "",
    val resources: List<ResourceItem>,
    val sourceSite: String = "",
)

/** 验证码请求 */
data class CaptchaRequest(
    val siteId: String,
    val imageUrl: String,            // 验证码图片 URL(绝对)
    val submitUrl: String,           // 提交验证码后跳转的 URL(一般即原搜索 URL + code=)
    val message: String = "请输入验证码",
)

/** 验证码应答: result=null 表示取消 */
data class CaptchaAnswer(
    val result: String?,
) {
    companion object {
        const val CANCEL = "__cancel__"
    }
}