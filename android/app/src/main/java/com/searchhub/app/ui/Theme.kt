package com.searchhub.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp

// 参考"追剧"项目的清爽风格: 绿主色 + 白底 + 中性灰, 高信息密度, 圆角胶囊
private val TitaGreen = Color(0xFF1E9C5A)
private val TitaGreenDark = Color(0xFF63D199)
private val TitaGray = Color(0xFF949494)
private val TitaLine = Color(0xFFF0F0F0)

// 浅色(白天): 白底为主, 绿色强调
private val LightColors = lightColorScheme(
    primary = TitaGreen,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE6F5EC),
    onPrimaryContainer = Color(0xFF0B5B2E),
    secondary = TitaGreen,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE8F7EF),
    onSecondaryContainer = Color(0xFF0B5B2E),
    tertiary = Color(0xFF555555),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFF3F3F3),
    onTertiaryContainer = Color(0xFF333333),
    background = Color.White,
    onBackground = Color(0xFF232323),
    surface = Color.White,
    onSurface = Color(0xFF232323),
    surfaceVariant = Color(0xFFF3F3F3),
    onSurfaceVariant = Color(0xFF888888),
    surfaceContainer = Color.White,
    surfaceContainerHigh = Color.White,
    surfaceContainerHighest = Color(0xFFF7F7F7),
    outline = Color(0xFFE0E0E0),
    outlineVariant = Color(0xFFF0F0F0),
    error = Color(0xFFB3261E),
)

// 深色(夜间): 深灰底, 绿色保持强调
private val DarkColors = darkColorScheme(
    primary = TitaGreenDark,
    onPrimary = Color(0xFF0B3D1E),
    primaryContainer = Color(0xFF145B32),
    onPrimaryContainer = Color(0xFFB8F0D0),
    secondary = TitaGreenDark,
    onSecondary = Color(0xFF0B3D1E),
    secondaryContainer = Color(0xFF145B32),
    onSecondaryContainer = Color(0xFFB8F0D0),
    tertiary = Color(0xFFBDBDBD),
    onTertiary = Color(0xFF111111),
    tertiaryContainer = Color(0xFF333333),
    onTertiaryContainer = Color(0xFFDDDDDD),
    background = Color(0xFF121212),
    onBackground = Color(0xFFE5E5E5),
    surface = Color(0xFF1B1B1B),
    onSurface = Color(0xFFE5E5E5),
    surfaceVariant = Color(0xFF2A2A2A),
    onSurfaceVariant = Color(0xFF9A9A9A),
    surfaceContainer = Color(0xFF1B1B1B),
    surfaceContainerHigh = Color(0xFF242424),
    surfaceContainerHighest = Color(0xFF2E2E2E),
    outline = Color(0xFF3A3A3A),
    outlineVariant = Color(0xFF333333),
    error = Color(0xFFEF9A9A),
)

private val SearchHubTypography = Typography(
    // 与"追剧"一致: 系统默认非衬线, 朴实为主, 标题加粗
    headlineSmall = TextStyle(fontSize = 19.sp),
    titleLarge = TextStyle(fontSize = 17.sp, letterSpacing = 0.4.sp),
    titleMedium = TextStyle(fontSize = 15.sp),
    titleSmall = TextStyle(fontSize = 13.sp),
    bodyMedium = TextStyle(fontSize = 14.sp),
    bodySmall = TextStyle(fontSize = 12.sp),
    labelLarge = TextStyle(fontSize = 14.sp),
    labelMedium = TextStyle(fontSize = 12.sp),
    labelSmall = TextStyle(fontSize = 10.sp),
)

@Composable
fun SearchHubTheme(content: @Composable () -> Unit) {
    // 固定浅色模式: 白底清爽风格, 不跟随系统深色
    MaterialTheme(
        colorScheme = LightColors,
        typography = SearchHubTypography,
        content = content,
    )
}