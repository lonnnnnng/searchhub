package com.searchhub.app.ui.captcha

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.searchhub.app.model.CaptchaAnswer
import com.searchhub.app.model.CaptchaRequest
import com.searchhub.app.ui.AppViewModel

private val TitaGreen = Color(0xFF1E9C5A)

/**
 * 验证码处理宿主:观察全局验证码事件,弹出图片+输入框,把应答送回。
 * 验证码图片通过 HttpEngine 下载(共享 cookie/UA),保证与搜索是同一会话。
 */
@Composable
fun CaptchaHost(
    model: AppViewModel = viewModel(),
    content: @Composable () -> Unit,
) {
    var activeRequest by remember { mutableStateOf<CaptchaRequest?>(null) }
    var answer by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        model.captchaRequests.collect { req ->
            activeRequest = req
            answer = ""
        }
    }

    activeRequest?.let { req ->
        var imgKey by remember(req.imageUrl) { mutableIntStateOf(0) }
        var bitmap by remember(req.imageUrl, imgKey) { mutableStateOf<Bitmap?>(null) }
        var loading by remember(req.imageUrl, imgKey) { mutableStateOf(true) }
        var failed by remember(req.imageUrl, imgKey) { mutableStateOf(false) }

        // 含时间戳的验证码 URL(点击刷新时追加新的 t 参数避免缓存)
        val effUrl = remember(req.imageUrl, imgKey) {
            if (req.imageUrl.contains('?')) req.imageUrl + "&c=" + imgKey
            else req.imageUrl + "?c=" + imgKey
        }

        // 用共享 HttpEngine 下载验证码图片(带同一会话 cookie)
        LaunchedEffect(effUrl) {
            loading = true
            failed = false
            bitmap = null
            try {
                val bytes = withContext(Dispatchers.IO) {
                    model.engine.getBytes(effUrl, referer = req.submitUrl)
                }
                val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                if (bmp != null) {
                    android.util.Log.d("CaptchaHost", "orig size=${bmp.width}x${bmp.height}")
                    // 放大2倍像素,算式清晰可辨且不溢出
                    bitmap = scaleBitmap(bmp, 2f)
                    android.util.Log.d("CaptchaHost", "scaled size=${bitmap!!.width}x${bitmap!!.height}")
                } else {
                    failed = true
                }
            } catch (e: Exception) {
                failed = true
            } finally {
                loading = false
            }
        }

        AlertDialog(
            onDismissRequest = {},
            title = { Text("验证码") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // 图片区(3倍像素放大 + 宽度占满,算式清晰且不越界)
                    androidx.compose.foundation.layout.Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center,
                    ) {
                        when {
                            loading -> CircularProgressIndicator()
                            failed -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("图片加载失败", color = MaterialTheme.colorScheme.error)
                                OutlinedButton(onClick = { imgKey++ }) {
                                    Icon(Icons.Default.Refresh, contentDescription = null)
                                    Spacer(Modifier.width(6.dp))
                                    Text("重新加载")
                                }
                            }
                            bitmap != null -> {
                                // 以原始像素按 dp 展示(不铺满): 原图130x28, 放大2倍后260x56像素
                                // 每个像素=1dp显示(260dp宽), 屏幕密度高会自然放大, 尺寸适中不铺满
                                val bmp = bitmap!!
                                val ratio = bmp.width.toFloat() / bmp.height.toFloat()
                                Image(
                                    bitmap = bmp.asImageBitmap(),
                                    contentDescription = "验证码图片",
                                    modifier = Modifier.fillMaxWidth(0.7f).aspectRatio(ratio),
                                    contentScale = ContentScale.Fit,
                                )
                            }
                        }
                    }
                    OutlinedTextField(
                        value = answer,
                        onValueChange = { answer = it },
                        label = { Text("答案") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            model.submitCaptchaAnswer(CaptchaAnswer(answer.trim()))
                            activeRequest = null
                        },
                        enabled = answer.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = TitaGreen),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(22.dp),
                    ) { Text("提交验证码") }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        OutlinedButton(onClick = { imgKey++ }) { Text("换一张") }
                        Spacer(Modifier.width(8.dp))
                        OutlinedButton(onClick = {
                            model.submitCaptchaAnswer(CaptchaAnswer(CaptchaAnswer.CANCEL))
                            activeRequest = null
                        }) { Text("取消") }
                    }
                }
            },
        )
    }

    content()
}

/** 将位图等比放大 factor 倍(用于验证码小图放大显示) */
private fun scaleBitmap(src: Bitmap, factor: Float): Bitmap {
    val m = Matrix().apply { postScale(factor, factor) }
    return Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
}
