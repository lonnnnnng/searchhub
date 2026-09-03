package com.searchhub.app

import android.os.Bundle
import android.view.Window
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.searchhub.app.ui.AppRoot

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // 启动屏: 纯白背景淡化图标, 立即进入主界面(不额外停留)
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // 应用固定浅色风格: 强制状态栏/导航栏图标为深色(黑色)
        forceDarkStatusBarIcons(window)
        setContent {
            AppRoot()
        }
    }

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