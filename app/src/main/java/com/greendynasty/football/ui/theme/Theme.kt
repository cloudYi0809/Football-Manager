package com.greendynasty.football.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.view.WindowCompat

// 绿茵王朝主题色
val GreenPrimary = Color(0xFF1B5E20)
val GreenLight = Color(0xFF4C8C4A)
val GreenDark = Color(0xFF003300)
val GoldAccent = Color(0xFFFFD700)
val PitchGreen = Color(0xFF2E7D32)

private val DarkColorScheme = darkColorScheme(
    primary = GreenLight,
    secondary = GoldAccent,
    tertiary = PitchGreen,
    background = Color(0xFF0A1F0A),
    surface = Color(0xFF1A2E1A),
)

private val LightColorScheme = lightColorScheme(
    primary = GreenPrimary,
    secondary = GoldAccent,
    tertiary = PitchGreen,
    background = Color(0xFFF5F5F0),
    surface = Color(0xFFFFFFFF),
)

@Composable
fun GreenDynasty2002Theme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
