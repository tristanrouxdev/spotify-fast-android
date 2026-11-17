package com.tristan.spotifyfast.ui.nowplaying

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tristan.spotifyfast.data.sync.LrcParser
import com.tristan.spotifyfast.data.sync.TrackLyrics
import com.tristan.spotifyfast.ui.icons.SpotifyIcons
import com.tristan.spotifyfast.ui.theme.SpotifyTokens

@Composable
fun LyricsPreviewCard(
    lyrics: TrackLyrics?,
    positionMs: Long,
    dominantColorArgb: Int,
    onExpand: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cardColor = lerp(SpotifyTokens.AccentGreen, Color(dominantColorArgb), 0.35f).copy(alpha = 0.88f)
    val synced = lyrics?.syncedLines.orEmpty()
    val activeIdx = remember(synced, positionMs) {
        LrcParser.activeLineIndexAt(synced, positionMs).coerceAtLeast(0)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(cardColor)
            .clickable(onClick = onExpand)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Paroles",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = Color.White,
            )
            Text(
                text = "Agrandir",
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.85f),
            )
        }

        when {
            lyrics == null -> {
                Text(
                    text = "Chargement des paroles synchronisées...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.85f),
                )
            }
            synced.isNotEmpty() -> {
                val previewSlice = synced.drop(activeIdx).take(3)
                previewSlice.forEachIndexed { idx, line ->
                    Text(
                        text = line.text,
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                        color = if (idx == 0) Color.White else Color.Black.copy(alpha = 0.72f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            !lyrics.plainLyrics.isNullOrBlank() -> {
                Text(
                    text = lyrics.plainLyrics.lines().take(3).joinToString("\n"),
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = Color.White,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            else -> {
                Text(
                    text = "Paroles synchronisées indisponibles pour ce titre.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.85f),
                )
            }
        }
    }
}

@Composable
fun SyncedLyricsFullScreen(
    title: String,
    subtitle: String,
    lyrics: TrackLyrics?,
    positionMs: Long,
    dominantColorArgb: Int,
    onSeekTo: (Long) -> Unit,
    onClose: () -> Unit,
) {
    val bgColor = lerp(SpotifyTokens.AccentGreen, Color(dominantColorArgb), 0.45f)
    val synced = lyrics?.syncedLines.orEmpty()
    val activeIndex = remember(synced, positionMs) {
        LrcParser.activeLineIndexAt(synced, positionMs)
    }
    val listState = rememberLazyListState()

    LaunchedEffect(activeIndex) {
        if (activeIndex >= 0 && synced.isNotEmpty()) {
            val target = (activeIndex - 2).coerceAtLeast(0)
            listState.animateScrollToItem(target)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
            .statusBarsPadding()
            .padding(horizontal = 20.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onClose) {
                Icon(
                    imageVector = SpotifyIcons.Close,
                    contentDescription = "Fermer paroles",
                    tint = Color.White,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        when {
            synced.isNotEmpty() -> {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 28.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    itemsIndexed(synced, key = { idx, line -> "${line.timestampMs}_$idx" }) { index, line ->
                        val isActive = index == activeIndex
                        val isPast = activeIndex >= 0 && index < activeIndex
                        Text(
                            text = line.text,
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontSize = if (isActive) 26.sp else 22.sp,
                                lineHeight = if (isActive) 34.sp else 30.sp,
                                fontWeight = FontWeight.Bold,
                            ),
                            color = when {
                                isActive -> Color.White
                                isPast -> Color.White.copy(alpha = 0.45f)
                                else -> Color.Black.copy(alpha = 0.65f)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { onSeekTo(line.timestampMs) }
                                .padding(vertical = 4.dp),
                        )
                    }
                }
            }
            !lyrics?.plainLyrics.isNullOrBlank() -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 24.dp),
                ) {
                    item {
                        Text(
                            text = lyrics?.plainLyrics.orEmpty(),
                            style = MaterialTheme.typography.titleLarge.copy(lineHeight = 28.sp),
                            color = Color.White,
                        )
                    }
                }
            }
            else -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "Aucune parole disponible pour ce morceau.",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                    )
                }
            }
        }
    }
}
