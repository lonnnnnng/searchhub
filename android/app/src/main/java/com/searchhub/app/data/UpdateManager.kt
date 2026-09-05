package com.searchhub.app.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.searchhub.app.model.CaptchaAnswer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject
import java.io.File

/** 应用内自更新: 检测 GitHub 最新 Release → 下载 APK → 拉起系统安装器 */
class UpdateManager(private val engine: HttpEngine) {

    companion object {
        private const val LATEST_API = "https://api.github.com/repos/lonnnnnng/searchhub/releases/latest"

        /** 版本号比较: 逐段数值比较, "v1.8.4" 与 "1.8.4" 等价; remote 更新返回 true */
        fun isNewer(remote: String, current: String): Boolean {
            fun parts(v: String) = v.trim().removePrefix("v").removePrefix("V").split('.').map { it.trim().toIntOrNull() ?: 0 }
            val a = parts(remote)
            val b = parts(current)
            for (i in 0 until maxOf(a.size, b.size)) {
                val x = a.getOrElse(i) { 0 }
                val y = b.getOrElse(i) { 0 }
                if (x != y) return x > y
            }
            return false
        }

        fun currentVersion(context: Context): String =
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: ""
    }

    data class LatestRelease(
        val versionName: String,   // 不带 v 前缀, 如 "1.8.4"
        val apkUrl: String,
        val apkSize: Long,
        val notes: String,
    )

    /** 拉取最新 Release; 网络失败或无 APK 资产返回 null */
    suspend fun fetchLatest(): LatestRelease? = withContext(Dispatchers.IO) {
        try {
            engine.get(LATEST_API).use { resp ->
                if (!resp.isSuccessful) return@use null
                val root = JSONObject(resp.body?.string() ?: return@use null)
                if (root.optBoolean("prerelease", false) || root.optBoolean("draft", true)) return@use null
                val tag = root.optString("tag_name")
                val assets = root.optJSONArray("assets") ?: return@use null
                var apkUrl = ""
                var apkSize = 0L
                for (i in 0 until assets.length()) {
                    val a = assets.optJSONObject(i) ?: continue
                    if (a.optString("name").endsWith(".apk")) {
                        apkUrl = a.optString("browser_download_url")
                        apkSize = a.optLong("size")
                        break
                    }
                }
                if (tag.isBlank() || apkUrl.isBlank()) return@use null
                LatestRelease(tag.removePrefix("v").removePrefix("V"), apkUrl, apkSize, root.optString("body"))
            }
        } catch (e: Exception) {
            null
        }
    }

    /** 下载 APK 到应用私有 update 目录(内部存储, 无需外部存储权限); onProgress 回调 0..1; 返回下载完成的文件 */
    suspend fun downloadApk(release: LatestRelease, context: Context, onProgress: (Float) -> Unit): File? =
        withContext(Dispatchers.IO) {
            try {
                val dir = File(context.filesDir, "update").apply { if (!exists()) mkdirs() }
                val target = File(dir, "app-${release.versionName}.apk")
                val tmp = File(dir, "app-download.tmp")
                engine.get(release.apkUrl).use { resp ->
                    if (!resp.isSuccessful) return@withContext null
                    val body = resp.body ?: return@withContext null
                    val total = body.contentLength().takeIf { it > 0 } ?: release.apkSize
                    body.byteStream().use { input ->
                        tmp.outputStream().use { output ->
                            val buf = ByteArray(64 * 1024)
                            var read = 0L
                            var last = 0f
                            while (true) {
                                val n = input.read(buf)
                                if (n < 0) break
                                output.write(buf, 0, n)
                                read += n
                                if (total > 0) {
                                    val p = (read.toFloat() / total).coerceIn(0f, 1f)
                                    if (p - last >= 0.01f || p >= 1f) {
                                        last = p
                                        onProgress(p)
                                    }
                                }
                            }
                        }
                    }
                }
                if (tmp.length() <= 0) return@withContext null
                tmp.renameTo(target)
                target
            } catch (e: Exception) {
                null
            }
        }

    /** 拉起系统安装器; 未授予"安装未知应用"权限时跳转授权页并返回 false */
    fun installApk(context: Context, file: File): Boolean {
        if (!context.packageManager.canRequestPackageInstalls()) {
            val intent = Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                .setData(Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            return false
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        return true
    }
}
