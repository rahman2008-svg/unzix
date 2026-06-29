package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = EmeraldGreen,
    secondary = MintAccent,
    tertiary = GlowingGreenText,
    background = CharcoalBackground,
    surface = SlateSurface,
    onPrimary = Color.Black,
    onSecondary = Color.Black,
    onTertiary = Color.Black,
    onBackground = Color(0xFFECEFF1),
    onSurface = Color(0xFFECEFF1),
    surfaceVariant = SlateBorder,
    onSurfaceVariant = Color(0xFFB0BEC5),
    outline = SlateBorder
)

private val LightColorScheme = lightColorScheme(
    primary = DeepEmerald,
    secondary = MintAccent,
    tertiary = DarkForestText,
    background = CleanWhiteBg,
    surface = LightSurface,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = DarkForestText,
    onSurface = DarkForestText,
    surfaceVariant = LightBorder,
    onSurfaceVariant = Color(0xFF556B5F),
    outline = LightBorder
)

@Composable
fun UnzixTheme(
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
