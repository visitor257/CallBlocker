package com.callblocker.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = Blue700,
    onPrimary = Color.White,
    primaryContainer = Blue100,
    secondary = Blue500,
    background = SurfaceLight,
    surface = Color.White,
    surfaceVariant = Gray100,
    onBackground = Gray900,
    onSurface = Gray900,
    error = Red500,
    onError = Color.White,
    outline = Gray400
)

@Composable
fun CallBlockerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        typography = Typography,
        content = content
    )
}
