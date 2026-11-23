package com.jarvis.ai.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.jarvis.ai.Mode

@Composable
fun Visualizer(mode: Mode, modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "v")
    val spin by t.animateFloat(0f, 360f, infiniteRepeatable(tween(9000, easing = LinearEasing)), label = "spin")
    val spin2 by t.animateFloat(360f, 0f, infiniteRepeatable(tween(14000, easing = LinearEasing)), label = "spin2")
    val pulse by t.animateFloat(0.85f, 1.15f,
        infiniteRepeatable(tween(if (mode == Mode.LISTENING || mode == Mode.WAKE_LISTENING) 500 else 1600, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "p")
    val strength = when (mode) {
        Mode.LISTENING, Mode.WAKE_LISTENING -> 1f
        Mode.SPEAKING -> 0.8f
        Mode.THINKING -> 0.6f
        Mode.IDLE -> 0.35f
    }

    Canvas(modifier.size(210.dp)) {
        val c = center
        val r = size.minDimension / 2
        drawCircle(Brush.radialGradient(listOf(Cyan.copy(alpha = 0.35f * strength), Color.Transparent), c, r), r)
        drawCircle(Cyan.copy(alpha = 0.9f), r * 0.22f * pulse)
        drawCircle(Cyan.copy(alpha = 0.5f), r * 0.55f, c, style = Stroke(2.dp.toPx()))
        for (i in 0 until 4) drawArc(Cyan, spin + i * 90f, 55f, false,
            Offset(c.x - r * 0.8f, c.y - r * 0.8f), Size(r * 1.6f, r * 1.6f), style = Stroke(4.dp.toPx(), cap = StrokeCap.Round))
        for (i in 0 until 6) drawArc(Cyan.copy(alpha = 0.6f), spin2 + i * 60f, 30f, false,
            Offset(c.x - r * 0.95f, c.y - r * 0.95f), Size(r * 1.9f, r * 1.9f), style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
    }
}
