package com.jarvis.ai.v4.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

internal object JarvisPalette {
    val background = Color(0xFF030A16)
    val surface = Color(0xD9081B31)
    val cyan = Color(0xFF20DFFF)
    val blue = Color(0xFF168DFF)
    val muted = Color(0xFF91B7D7)
    val green = Color(0xFF52F29B)
    val red = Color(0xFFFF657A)
}

internal enum class CoreState(val label: String) {
    IDLE("Idle"),
    LISTENING("Listening"),
    THINKING("Thinking"),
    SPEAKING("Speaking"),
    ERROR("Error"),
}

@Composable
internal fun JarvisOrb(state: CoreState, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "jarvis-orb")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (state == CoreState.IDLE) 18_000 else 7_000,
                easing = LinearEasing,
            ),
            repeatMode = RepeatMode.Restart,
        ),
        label = "orb-rotation",
    )
    val pulse by transition.animateFloat(
        initialValue = 0.965f,
        targetValue = 1.035f,
        animationSpec = infiniteRepeatable(
            animation = tween(2_400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "orb-pulse",
    )
    val accent = when (state) {
        CoreState.ERROR -> JarvisPalette.red
        CoreState.SPEAKING -> JarvisPalette.green
        else -> JarvisPalette.cyan
    }

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .semantics { contentDescription = "JARVIS AI core, ${state.label}" },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val outerRadius = size.minDimension * 0.43f * pulse
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(accent.copy(alpha = 0.34f), JarvisPalette.blue.copy(alpha = 0.14f), Color.Transparent),
                    center = center,
                    radius = outerRadius * 1.48f,
                ),
                radius = outerRadius * 1.48f,
                center = center,
            )
            drawCircle(accent.copy(alpha = 0.28f), outerRadius * 1.14f, center, style = Stroke(1.dp.toPx()))
            drawCircle(JarvisPalette.blue.copy(alpha = 0.52f), outerRadius, center, style = Stroke(1.6.dp.toPx()))
            drawCircle(accent.copy(alpha = 0.72f), outerRadius * 0.9f, center, style = Stroke(2.4.dp.toPx()))
            drawCircle(JarvisPalette.blue.copy(alpha = 0.65f), outerRadius * 0.78f, center, style = Stroke(1.dp.toPx()))
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(Color(0xFF0B2745).copy(alpha = 0.94f), Color(0xFF050D1A).copy(alpha = 0.98f)),
                    center = center,
                    radius = outerRadius * 0.78f,
                ),
                radius = outerRadius * 0.76f,
                center = center,
            )
            drawArc(
                color = accent.copy(alpha = 0.95f),
                startAngle = rotation,
                sweepAngle = 72f,
                useCenter = false,
                topLeft = Offset(center.x - outerRadius, center.y - outerRadius),
                size = Size(outerRadius * 2f, outerRadius * 2f),
                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round),
            )
            drawArc(
                color = JarvisPalette.blue.copy(alpha = 0.92f),
                startAngle = -rotation * 0.72f + 150f,
                sweepAngle = 36f,
                useCenter = false,
                topLeft = Offset(center.x - outerRadius * 0.9f, center.y - outerRadius * 0.9f),
                size = Size(outerRadius * 1.8f, outerRadius * 1.8f),
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round),
            )
            for (index in 0 until 16) {
                val angle = Math.toRadians((index * 22.5 + rotation * 0.12).toDouble())
                val radius = outerRadius * 1.08f
                val point = Offset(
                    center.x + kotlin.math.cos(angle).toFloat() * radius,
                    center.y + kotlin.math.sin(angle).toFloat() * radius,
                )
                drawCircle(accent.copy(alpha = if (index % 4 == 0) 0.9f else 0.35f), if (index % 4 == 0) 2.dp.toPx() else 1.dp.toPx(), point)
            }
        }
        androidx.compose.foundation.layout.Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(12.dp),
        ) {
            Text(
                text = "JARVIS",
                color = JarvisPalette.cyan,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Light,
                textAlign = TextAlign.Center,
            )
            Text(
                text = "AI PERSONAL ASSISTANT",
                color = JarvisPalette.cyan.copy(alpha = 0.86f),
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
            )
            Text(
                text = state.label.uppercase(),
                color = accent,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
internal fun VoiceWaveform(active: Boolean, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "voice-waveform")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (Math.PI * 2).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(if (active) 900 else 2_800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "wave-phase",
    )
    Canvas(modifier.semantics { contentDescription = if (active) "Voice waveform active" else "Voice waveform idle" }) {
        val bars = 56
        val midY = size.height / 2f
        val step = size.width / (bars - 1)
        for (index in 0 until bars) {
            val normalized = index.toFloat() / (bars - 1)
            val envelope = (1f - kotlin.math.abs(normalized - 0.5f) * 1.72f).coerceAtLeast(0.12f)
            val pattern = (0.25f + kotlin.math.abs(kotlin.math.sin(index * 1.73f + phase)) * 0.75f)
            val height = size.height * envelope * pattern * if (active) 0.92f else 0.55f
            val x = index * step
            drawLine(
                color = JarvisPalette.cyan.copy(alpha = 0.2f),
                start = Offset(x = x, y = midY - height / 2f),
                end = Offset(x = x, y = midY + height / 2f),
                strokeWidth = 5.dp.toPx(),
                cap = StrokeCap.Round,
            )
            drawLine(
                color = JarvisPalette.cyan.copy(alpha = 0.9f),
                start = Offset(x = x, y = midY - height / 2f),
                end = Offset(x = x, y = midY + height / 2f),
                strokeWidth = 1.7.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }
    }
}

@Composable
internal fun NeonCircuitLine(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "circuit-line")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2_600, easing = LinearEasing), RepeatMode.Restart),
        label = "circuit-pulse",
    )
    Canvas(modifier.semantics { contentDescription = "Animated cyan holographic circuit line" }) {
        val centerY = size.height * 0.52f
        val middle = size.width * 0.5f
        val path = Path().apply {
            moveTo(0f, centerY - size.height * 0.28f)
            lineTo(size.width * 0.025f, centerY)
            lineTo(size.width * 0.22f, centerY)
            lineTo(size.width * 0.27f, centerY + size.height * 0.26f)
            lineTo(size.width * 0.43f, centerY + size.height * 0.26f)
            lineTo(size.width * 0.47f, centerY + size.height * 0.04f)
            lineTo(middle, centerY + size.height * 0.04f)
            lineTo(size.width * 0.53f, centerY - size.height * 0.2f)
            lineTo(size.width * 0.7f, centerY - size.height * 0.2f)
            lineTo(size.width * 0.75f, centerY)
            lineTo(size.width * 0.975f, centerY)
            lineTo(size.width, centerY - size.height * 0.24f)
        }
        drawPath(path, JarvisPalette.blue.copy(alpha = 0.2f), style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round))
        drawPath(path, JarvisPalette.cyan.copy(alpha = 0.84f), style = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round))
        val pulseX = size.width * phase
        drawCircle(JarvisPalette.cyan.copy(alpha = 0.24f), 8.dp.toPx(), Offset(pulseX, centerY), style = Stroke(width = 2.dp.toPx()))
        drawCircle(JarvisPalette.cyan, 2.2.dp.toPx(), Offset(pulseX, centerY))
        drawCircle(Color.White.copy(alpha = 0.75f), 0.8.dp.toPx(), Offset(middle, centerY))
        for (index in 0..5) {
            val x = size.width * (index / 5f)
            drawCircle(JarvisPalette.blue.copy(alpha = 0.8f), 1.2.dp.toPx(), Offset(x, centerY + if (index % 2 == 0) size.height * 0.36f else -size.height * 0.36f))
        }
    }
}
