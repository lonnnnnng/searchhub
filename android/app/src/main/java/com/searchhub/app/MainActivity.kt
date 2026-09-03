package com.searchhub.app

import android.os.Bundle
import android.view.Window
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.searchhub.app.ui.AppRoot
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // 启动屏: 纯色背景淡化(主题已配透明图标+纯绿背景)
        val splash = installSplashScreen()
        splash.setKeepOnScreenCondition { keepOnScreen }
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // 应用固定浅色风格(splash 后生效): 强制状态栏/导航栏图标为深色(黑色)
        forceDarkStatusBarIcons(window)
        setContent {
            AppRoot()
        }
        // 保持纯色启动画面约 600ms, 平滑过渡到主界面
        keepOnScreen = true
        lifecycleScope.launch {
            delay(600)
            keepOnScreen = false
        }
    }

    private var keepOnScreen = true

    private fun forceDarkStatusBarIcons(window: Window) {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.isAppearanceLightStatusBars = true  // 深色图标
        controller.isAppearanceLightNavigationBars = true
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        // 渲染透明系统栏, 由 Compose 内容接管背景
        WindowCompat.setDecorFitsSystemWindows(window, false)
    }
}