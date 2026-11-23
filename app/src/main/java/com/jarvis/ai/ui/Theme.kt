package com.jarvis.ai.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Cyan = Color(0xFF2EE6FF)
val DeepBlue = Color(0xFF020B1A)
val Navy = Color(0xFF062845)

@Composable
fun JarvisTheme(content: @Composable () -> Unit) = MaterialTheme(
    colorScheme = darkColorScheme(
        primary = Cyan, onPrimary = DeepBlue, background = DeepBlue, surface = Navy,
        onBackground = Color(0xFFD8F6FF), onSurface = Color(0xFFD8F6FF), secondary = Color(0xFF1AA7D9)
    ),
    content = content
)
