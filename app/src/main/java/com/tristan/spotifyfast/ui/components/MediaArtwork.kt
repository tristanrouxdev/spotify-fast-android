package com.tristan.spotifyfast.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.tristan.spotifyfast.data.local.LIKED_TRACKS_CONTEXT_URI
import com.tristan.spotifyfast.ui.icons.SpotifyIcons
import com.tristan.spotifyfast.ui.theme.SpotifyTokens

@Composable
fun MediaArtwork(
    url: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: Dp? = null,
    cornerRadius: Dp = SpotifyTokens.CardCornerRadius,
    isLikedCollection: Boolean = false,
    fallbackColorArgb: Int = 0xFF282828.toInt(),
) {
    val sizedModifier = if (size != null) modifier.size(size) else modifier
    val clippedModifier = sizedModifier.clip(RoundedCornerShape(cornerRadius))

    if (isLikedCollection) {
        Box(
            modifier = clippedModifier.background(
                Brush.linearGradient(
                    colors = listOf(Color(0xFF450AF5), Color(0xFF8E8EE5)),
                ),
            ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = SpotifyIcons.Heart,
                contentDescription = contentDescription,
                tint = Color.White,
                modifier = Modifier.size(if (size != null && size < 60.dp) 20.dp else 44.dp),
            )
        }
        return
    }

    if (url.isNullOrBlank()) {
        Box(
            modifier = clippedModifier.background(Color(fallbackColorArgb)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = SpotifyIcons.Lyrics,
                contentDescription = contentDescription,
                tint = SpotifyTokens.TextSecondary,
                modifier = Modifier.size(22.dp),
            )
        }
    } else {
        AsyncImage(
            model = url,
            contentDescription = contentDescription,
            contentScale = ContentScale.Crop,
            modifier = clippedModifier.background(Color(fallbackColorArgb)),
        )
    }
}

fun isLikedContext(uri: String?): Boolean = uri == LIKED_TRACKS_CONTEXT_URI
