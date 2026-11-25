package com.jarvis.ai.v4.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val AivoDarkColors = darkColorScheme(
    primary = Color(0xFF61E6FF),
    onPrimary = Color(0xFF07111F),
    secondary = Color(0xFF9A8CFF),
    background = Color(0xFF07111F),
    onBackground = Color(0xFFEAFBFF),
    surface = Color(0xFF101D2D),
    onSurface = Color(0xFFEAFBFF),
)

private val AivoLightColors = lightColorScheme(
    primary = Color(0xFF087A94),
    onPrimary = Color(0xFFFFFFFF),
    secondary = Color(0xFF5B50A6),
    background = Color(0xFFF3FAFC),
    onBackground = Color(0xFF10212C),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF10212C),
)

@Composable
fun AivoTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AivoDarkColors,
        content = content,
    )
}
