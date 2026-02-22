package com.projectalice.overlay

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun AliceOverlayContent(viewModel: OverlayViewModel) {
    val state by viewModel.uiState.collectAsState()
    if (!state.isOverlayTriggered) return

    val pulse = rememberInfiniteTransition(label = "siriOrbPulse")
    val orbScale by pulse.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 950, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "siriOrbScale"
    )
    val orbAlpha by pulse.animateFloat(
        initialValue = 0.8f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "siriOrbAlpha"
    )

    Box(
        modifier = Modifier
            .size(75.dp)
            .scale(orbScale)
            .alpha(orbAlpha)
            .background(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFFFFFFFF),
                        Color(0xFF5CE1FF),
                        Color(0xFF2F6BFF),
                        Color(0xFFD24DFF),
                        Color(0xFF7D4DFF)
                    )
                ),
                shape = CircleShape
            )
    )
}
