package com.tristan.spotifyfast.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tristan.spotifyfast.ui.theme.SpotifyTokens
import kotlin.math.PI
import kotlin.math.sin

/**
 * Zero-allocation native Canvas 4-bar equalizer matching `design/animations/equalizer.anicraft`.
 * Runs smoothly at 60/120 fps without bitmap frame decoding overhead.
 */
@Composable
fun EqualizerIndicator(
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 16.dp,
    barColor: Color = SpotifyTokens.AccentGreen,
) {
    val transition = rememberInfiniteTransition(label = "equalizer")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "equalizer_phase",
    )

    Canvas(modifier = modifier.size(size)) {
        val barCount = 4
        val gap = this.size.width * 0.12f
        val barWidth = (this.size.width - gap * (barCount - 1)) / barCount
        val phases = floatArrayOf(0.0f, 1.5f, 3.0f, 4.4f)
        val staticHeights = floatArrayOf(0.38f, 0.72f, 0.50f, 0.28f)

        for (i in 0 until barCount) {
            val fraction = if (isPlaying) {
                0.24f + 0.72f * ((sin(phase + phases[i]) + 1f) * 0.5f)
            } else {
                staticHeights[i]
            }
            val barHeight = this.size.height * fraction
            val left = i * (barWidth + gap)
            val top = this.size.height - barHeight
            drawRoundRect(
                color = barColor,
                topLeft = Offset(left, top),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f),
            )
        }
    }
}
