package com.jarvis.ai.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import com.jarvis.ai.Mode
import kotlin.math.abs
import kotlin.math.sin

@Composable
fun Waveform(mode: Mode, level: Float, modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "w")
    val phase by t.animateFloat(0f, 6.2832f, infiniteRepeatable(tween(1200, easing = LinearEasing)), label = "ph")
    val amp = when (mode) { Mode.LISTENING -> 0.15f + level * 0.85f; Mode.SPEAKING -> 0.55f; Mode.THINKING -> 0.25f; Mode.IDLE -> 0.08f }
    Canvas(modifier.fillMaxWidth().height(40.dp)) {
        val n = 28; val gap = size.width / n; val bw = gap * 0.5f
        for (i in 0 until n) {
            val h = (size.height * 0.15f + size.height * 0.85f * amp * abs(sin(phase + i * 0.55f))).coerceAtMost(size.height)
            drawRoundRect(Cyan.copy(alpha = 0.4f + 0.6f * amp), Offset(i * gap + (gap - bw) / 2, (size.height - h) / 2), Size(bw, h), CornerRadius(bw / 2))
        }
    }
}
