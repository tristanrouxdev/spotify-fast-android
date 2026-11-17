package com.tristan.spotifyfast.ui.library

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tristan.spotifyfast.data.local.DEFAULT_DOMINANT_COLOR
import com.tristan.spotifyfast.player.UnifiedPlaybackState
import com.tristan.spotifyfast.ui.components.MediaArtwork
import com.tristan.spotifyfast.ui.components.SpotifyFilterChipsRow
import com.tristan.spotifyfast.ui.components.formatDurationMs
import com.tristan.spotifyfast.ui.icons.SpotifyIcons
import com.tristan.spotifyfast.ui.theme.SpotifyTokens

private enum class EpisodeFilter { ALL, UNPLAYED }

@Composable
fun ShowDetailScreen(
    showUri: String,
    title: String,
    viewModel: LibraryViewModel,
    playbackState: UnifiedPlaybackState,
    onBack: () -> Unit,
    onPlayEpisode: (episodeUri: String, showUri: String) -> Unit,
    bottomPadding: PaddingValues,
) {
    val episodes by viewModel.observeShowEpisodes(showUri)
        .collectAsStateWithLifecycle(initialValue = emptyList())
    var dominantColor by remember(showUri) { mutableIntStateOf(DEFAULT_DOMINANT_COLOR) }
    var filter by remember { mutableStateOf(EpisodeFilter.ALL) }
    var isSaved by remember { mutableStateOf(true) }

    LaunchedEffect(showUri) {
        dominantColor = viewModel.getDominantColorForUri(showUri)
        viewModel.refreshCollectionInBackground(showUri)
    }

    val filteredEpisodes = remember(episodes, filter) {
        when (filter) {
            EpisodeFilter.ALL -> episodes
            EpisodeFilter.UNPLAYED -> episodes.filter { !it.isFullyPlayed }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(SpotifyTokens.Background),
        contentPadding = PaddingValues(bottom = bottomPadding.calculateBottomPadding() + 24.dp),
    ) {
        item(key = "show_header") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color(dominantColor).copy(alpha = 0.72f), SpotifyTokens.Background),
                        ),
                    )
                    .statusBarsPadding()
                    .padding(horizontal = SpotifyTokens.ScreenHorizontalPadding, vertical = 12.dp),
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = SpotifyIcons.Back,
                        contentDescription = "Retour",
                        tint = SpotifyTokens.TextPrimary,
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MediaArtwork(
                        url = episodes.firstOrNull()?.artworkUrl,
                        contentDescription = title,
                        size = 112.dp,
                        fallbackColorArgb = dominantColor,
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = title, style = MaterialTheme.typography.headlineMedium)
                        Text(
                            text = "${episodes.size} épisodes",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (isSaved) SpotifyTokens.AccentGreen else SpotifyTokens.SurfaceElevated)
                                .clickable {
                                    viewModel.toggleLibraryMembership(showUri, isSaved) { isSaved = it }
                                }
                                .padding(horizontal = 14.dp, vertical = 6.dp),
                        ) {
                            Text(
                                text = if (isSaved) "Abonné" else "S'abonner",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = if (isSaved) Color.Black else SpotifyTokens.TextPrimary,
                            )
                        }
                    }
                }
            }
        }

        item(key = "episode_filter") {
            SpotifyFilterChipsRow(
                items = listOf(
                    EpisodeFilter.ALL to "Tous les épisodes",
                    EpisodeFilter.UNPLAYED to "Non lus / En cours",
                ),
                selected = filter,
                onSelected = { filter = it },
                modifier = Modifier.padding(vertical = 8.dp),
            )
        }

        items(filteredEpisodes, key = { it.uri }) { ep ->
            val ratio = if (ep.durationMs > 0L) {
                (ep.resumePositionMs.toFloat() / ep.durationMs.toFloat()).coerceIn(0f, 1f)
            } else {
                0f
            }
            val isActive = playbackState.itemUri == ep.uri
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onPlayEpisode(ep.uri, showUri) }
                    .padding(horizontal = SpotifyTokens.ScreenHorizontalPadding, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = ep.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isActive) SpotifyTokens.AccentGreen else SpotifyTokens.TextPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (ep.description.isNotBlank()) {
                    Text(
                        text = ep.description,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    val remainingMs = (ep.durationMs - ep.resumePositionMs).coerceAtLeast(0L)
                    val label = when {
                        ep.isFullyPlayed -> "Lu • ${formatDurationMs(ep.durationMs)}"
                        ep.resumePositionMs > 0L -> "${formatDurationMs(remainingMs)} restantes"
                        else -> "${ep.releaseDate} • ${formatDurationMs(ep.durationMs)}"
                    }
                    Text(text = label, style = MaterialTheme.typography.bodySmall)

                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(Color.White),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = if (isActive && playbackState.isPlaying) SpotifyIcons.Pause else SpotifyIcons.Play,
                            contentDescription = "Lire épisode",
                            tint = Color.Black,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
                if (ratio > 0f && !ep.isFullyPlayed) {
                    LinearProgressIndicator(
                        progress = { ratio },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = SpotifyTokens.AccentGreen,
                        trackColor = SpotifyTokens.SurfaceHighlight,
                    )
                }
            }
        }
    }
}
