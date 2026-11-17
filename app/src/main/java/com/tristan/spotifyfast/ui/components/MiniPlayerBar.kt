package com.tristan.spotifyfast.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tristan.spotifyfast.player.UnifiedPlaybackState
import com.tristan.spotifyfast.ui.icons.SpotifyIcons
import com.tristan.spotifyfast.ui.theme.SpotifyTokens

@Composable
fun MiniPlayerBar(
    state: UnifiedPlaybackState,
    onExpandNowPlaying: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onToggleLike: () -> Unit,
    onOpenDevices: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state.itemUri.isBlank()) return

    var dragAccumulated by remember { mutableFloatStateOf(0f) }
    val tintedSurface = lerp(SpotifyTokens.SurfaceElevated, Color(state.dominantColorArgb), 0.35f)
    val progress = if (state.durationMs > 0L) {
        (state.positionMs.toFloat() / state.durationMs.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(tintedSurface)
            .pointerInput(state.itemUri) {
                detectHorizontalDragGestures(
                    onDragStart = { dragAccumulated = 0f },
                    onHorizontalDrag = { _, dragAmount -> dragAccumulated += dragAmount },
                    onDragEnd = {
                        if (dragAccumulated < -90f) {
                            onSkipNext()
                        } else if (dragAccumulated > 90f) {
                            onSkipPrevious()
                        }
                        dragAccumulated = 0f
                    },
                )
            }
            .clickable(onClick = onExpandNowPlaying),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(SpotifyTokens.MiniPlayerHeight)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MediaArtwork(
                url = state.artworkUrl,
                contentDescription = state.title,
                size = 42.dp,
                cornerRadius = 4.dp,
                fallbackColorArgb = state.dominantColorArgb,
            )

            Spacer(modifier = Modifier.width(10.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = state.title,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val subtitleText = if (state.isRemoteConnect && state.activeDevice != null) {
                    "${state.subtitle} • ${state.activeDevice.name}"
                } else {
                    state.subtitle
                }
                Text(
                    text = subtitleText,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (state.isRemoteConnect) SpotifyTokens.AccentGreen else SpotifyTokens.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            IconButton(onClick = onOpenDevices, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = SpotifyIcons.Devices,
                    contentDescription = "Appareils Connect",
                    tint = if (state.isRemoteConnect) SpotifyTokens.AccentGreen else SpotifyTokens.TextSecondary,
                    modifier = Modifier.size(20.dp),
                )
            }

            IconButton(onClick = onToggleLike, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = if (state.isSavedInLibrary) SpotifyIcons.Heart else SpotifyIcons.HeartOutline,
                    contentDescription = "Aimer",
                    tint = if (state.isSavedInLibrary) SpotifyTokens.AccentGreen else SpotifyTokens.TextPrimary,
                    modifier = Modifier.size(20.dp),
                )
            }

            IconButton(onClick = onTogglePlayPause, modifier = Modifier.size(40.dp)) {
                Icon(
                    imageVector = if (state.isPlaying) SpotifyIcons.Pause else SpotifyIcons.Play,
                    contentDescription = if (state.isPlaying) "Pause" else "Lecture",
                    tint = SpotifyTokens.TextPrimary,
                    modifier = Modifier.size(24.dp),
                )
            }
        }

        // Thin playback progress gauge
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
                .height(2.dp)
                .background(Color.White.copy(alpha = 0.18f)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress)
                    .height(2.dp)
                    .background(Color.White),
            )
        }
    }
}
