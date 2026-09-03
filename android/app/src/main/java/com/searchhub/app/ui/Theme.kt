package com.searchhub.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight

private val Ink = Color(0xFF1B1D1F)
private val Canvas = Color(0xFFF7F4EE)
private val CanvasDark = Color(0xFF17191A)
private val Coral = Color(0xFFE76A47)
private val Teal = Color(0xFF197F82)
private val Mint = Color(0xFFD8EEEA)
private val Sand = Color(0xFFF0E2C7)

private val LightColors = lightColorScheme(
    primary = Coral,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDBCE),
    onPrimaryContainer = Color(0xFF3B0B02),
    secondary = Teal,
    onSecondary = Color.White,
    secondaryContainer = Mint,
    onSecondaryContainer = Color(0xFF002021),
    tertiary = Color(0xFFB47718),
    onTertiary = Color.White,
    tertiaryContainer = Sand,
    onTertiaryContainer = Color(0xFF291800),
    background = Canvas,
    onBackground = Ink,
    surface = Color(0xFFFFFCF8),
    onSurface = Ink,
    surfaceVariant = Color(0xFFEDE9E2),
    onSurfaceVariant = Color(0xFF5E5A55),
    outline = Color(0xFF8A837A),
    outlineVariant = Color(0xFFD8D1C8),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFF9B7D),
    onPrimary = Color(0xFF571B0B),
    primaryContainer = Color(0xFF7A2E1A),
    onPrimaryContainer = Color(0xFFFFDBCE),
    secondary = Color(0xFF79D4D1),
    onSecondary = Color(0xFF003738),
    secondaryContainer = Color(0xFF155557),
    onSecondaryContainer = Color(0xFFB4ECE8),
    tertiary = Color(0xFFE3B95E),
    onTertiary = Color(0xFF3F2D00),
    tertiaryContainer = Color(0xFF5B4300),
    onTertiaryContainer = Color(0xFFFFDEA0),
    background = CanvasDark,
    onBackground = Color(0xFFE8E2DA),
    surface = Color(0xFF202223),
    onSurface = Color(0xFFE8E2DA),
    surfaceVariant = Color(0xFF45413D),
    onSurfaceVariant = Color(0xFFD1C6BC),
    outline = Color(0xFFA79A8F),
    outlineVariant = Color(0xFF5C554F),
)

private val SearchHubTypography = Typography().copy(
    displaySmall = Typography().displaySmall.copy(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Bold,
    ),
    headlineMedium = Typography().headlineMedium.copy(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Bold,
    ),
    headlineSmall = Typography().headlineSmall.copy(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Bold,
    ),
    titleLarge = Typography().titleLarge.copy(fontWeight = FontWeight.Bold),
    titleMedium = Typography().titleMedium.copy(fontWeight = FontWeight.SemiBold),
)

@Composable
fun SearchHubTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        typography = SearchHubTypography,
        content = content,
    )
}
