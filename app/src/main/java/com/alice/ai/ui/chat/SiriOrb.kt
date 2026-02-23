package com.alice.ai.ui.chat

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun SiriOrb(
    modifier: Modifier = Modifier,
    size: Dp = 32.dp
) {
    val transition = rememberInfiniteTransition(label = "siri-orb")
    val morphPhase = transition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "morph-phase"
    )
    val pulse = transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 980),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )
    val drift = transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 10000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "drift"
    )
    val blobPath = remember { Path() }

    Canvas(
        modifier = modifier
            .size(size)
            .scale(pulse.value)
    ) {
        val center = Offset(x = this.size.width / 2f, y = this.size.height / 2f)
        val baseRadius = this.size.minDimension / 2.7f

        drawCircle(
            color = Color(0x557F5AF0),
            radius = baseRadius * 1.95f,
            center = center,
            blendMode = BlendMode.Plus
        )
        drawCircle(
            color = Color(0x442CB1FF),
            radius = baseRadius * 1.55f,
            center = center,
            blendMode = BlendMode.Plus
        )

        blobPath.reset()
        val points = 42
        for (index in 0..points) {
            val t = index.toFloat() / points.toFloat()
            val angle = t * (2f * PI).toFloat()
            val waveA = sin((angle * 3.1f) + morphPhase.value) * (baseRadius * 0.18f)
            val waveB = cos((angle * 2.4f) - (morphPhase.value * 0.85f)) * (baseRadius * 0.12f)
            val radius = baseRadius + waveA + waveB
            val x = center.x + cos(angle.toDouble()).toFloat() * radius
            val y = center.y + sin(angle.toDouble()).toFloat() * radius
            if (index == 0) {
                blobPath.moveTo(x, y)
            } else {
                blobPath.lineTo(x, y)
            }
        }
        blobPath.close()

        drawPath(
            path = blobPath,
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFF7F5AF0),
                    Color(0xFF2CB1FF),
                    Color(0xFF1A1B3A)
                ),
                center = center,
                radius = baseRadius * 2.1f
            ),
            alpha = 0.96f
        )

        drawPath(
            path = blobPath,
            color = Color(0x33FFFFFF),
            style = Stroke(
                width = baseRadius * 0.18f,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )

        rotate(
            degrees = drift.value,
            pivot = center
        ) {
            drawCircle(
                color = Color(0x3D2CB1FF),
                radius = baseRadius * 0.45f,
                center = Offset(center.x + baseRadius * 0.75f, center.y),
                blendMode = BlendMode.Plus
            )
        }
    }
}
