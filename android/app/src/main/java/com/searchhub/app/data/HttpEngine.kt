package com.searchhub.app.data

import com.searchhub.app.model.CaptchaAnswer
import com.searchhub.app.model.CaptchaRequest
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * 统一 HTTP 引擎。
 * - 携带模拟浏览器 UA
 * - 按站点持久化 cookie(验证码通过后需要保持 session)
 * - 提供同步挂起方法
 */
class HttpEngine {

    companion object {
        const val UA = "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
    }

    private val cookieJar = PersistentCookieJar()

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .cookieJar(cookieJar)
        .build()

    fun clearSitesCookies(host: String) {
        cookieJar.clear(host)
    }

    /** 发起 GET 并返回 Response;调用方需 close */
    suspend fun get(url: String, referer: String? = null): Response {
        val b = Request.Builder().url(url).header("User-Agent", UA)
        if (referer != null) b.header("Referer", referer)
        b.header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
        return execute(b.build())
    }

    /** 发起 GET 返回页面文本 */
    suspend fun getText(url: String, referer: String? = null): String {
        get(url, referer).use { r ->
            if (!r.isSuccessful) throw IOException("HTTP ${r.code} for $url")
            return r.body?.string() ?: ""
        }
    }

    /** 发起 GET 并按 GBK 解码(帝国CMS / 老电影站页面) */
    suspend fun getTextGbk(url: String, referer: String? = null): String {
        get(url, referer).use { r ->
            if (!r.isSuccessful) throw IOException("HTTP ${r.code} for $url")
            val bytes = r.body?.bytes() ?: return ""
            return String(bytes, charset("GBK"))
        }
    }

    /** 发起 POST JSON 返回文本 */
    suspend fun postJson(url: String, body: RequestBody, referer: String? = null): String {
        val b = Request.Builder()
            .url(url)
            .post(body)
            .header("User-Agent", UA)
            .header("Accept", "application/json")
            .header("Content-Type", "application/json; charset=utf-8")
        if (referer != null) b.header("Referer", referer)
        return execute(b.build()).use { r ->
            if (!r.isSuccessful) throw IOException("HTTP ${r.code} for $url")
            r.body?.string() ?: ""
        }
    }

    /** 发起 POST 表单返回文本(帝国CMS 搜索等) */
    suspend fun postText(url: String, body: FormBody, referer: String? = null): String {
        postRaw(url, body, referer).use { r ->
            if (!r.isSuccessful) throw IOException("HTTP ${r.code} for $url")
            return r.body?.string() ?: ""
        }
    }

    /** 发起 POST 表单返回文本,按 GBK 解码 */
    suspend fun postTextGbk(url: String, body: FormBody, referer: String? = null): String {
        postRaw(url, body, referer).use { r ->
            if (!r.isSuccessful) throw IOException("HTTP ${r.code} for $url")
            val bytes = r.body?.bytes() ?: return ""
            return String(bytes, charset("GBK"))
        }
    }

    /** 发起 POST 表单返回 Response;调用方需 close */
    suspend fun postRaw(url: String, body: FormBody, referer: String? = null): Response {
        val b = Request.Builder().url(url).post(body).header("User-Agent", UA)
        b.header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
        if (referer != null) b.header("Referer", referer)
        return execute(b.build())
    }

    /** 发起 GET 返回原始字节(带 cookie/UA,用于验证码图片等) */
    suspend fun getBytes(url: String, referer: String? = null): ByteArray {
        get(url, referer).use { r ->
            if (!r.isSuccessful) throw IOException("HTTP ${r.code} for $url")
            return r.body?.bytes() ?: ByteArray(0)
        }
    }

    /** 发起 POST 表单(用于需 csrf 的端点),跟随重定向返回最终 URL */
    suspend fun post(url: String, form: okhttp3.FormBody, referer: String? = null): String {
        val b = Request.Builder()
            .url(url)
            .post(form)
            .header("User-Agent", UA)
        b.header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
        if (referer != null) b.header("Referer", referer)
        return execute(b.build()).use { r ->
            r.request.url.toString()
        }
    }

    private suspend fun execute(request: Request): Response =
        suspendCancellableCoroutine { cont ->
            val call = client.newCall(request)
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (cont.isCancelled) return
                    cont.resumeWithException(e)
                }

                override fun onResponse(call: Call, response: Response) {
                    cont.resume(response)
                }
            })
            cont.invokeOnCancellation { call.cancel() }
        }

    private class PersistentCookieJar : CookieJar {
        private val cache = ConcurrentHashMap<String, List<Cookie>>()

        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            cache[url.host] = cookies
        }

        override fun loadForRequest(url: HttpUrl): List<Cookie> {
            return cache[url.host] ?: emptyList()
        }

        fun clear(host: String) {
            cache.remove(host)
        }
    }
}

/** 验证码需要通过信号量回到 UI 线程输入,返回 null 表示用户取消或超时 */
class CaptchaFlow(
    private val onCaptcha: suspend (CaptchaRequest) -> CaptchaAnswer,
) {
    /** 请求验证码,返回答案;取消或超时抛 CancelledException */
    suspend fun ask(req: CaptchaRequest): String {
        val a = withTimeoutOrNull(60000L) { onCaptcha(req) } ?: throw CaptchaCancelledException()
        val result = a.result ?: throw CaptchaCancelledException()
        if (result == CaptchaAnswer.CANCEL) throw CaptchaCancelledException()
        return result
    }
}

class CaptchaCancelledException : Exception("用户取消验证码输入")
class CaptchaRequiredException(val request: CaptchaRequest) : Exception("需要验证码")