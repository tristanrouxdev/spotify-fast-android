package com.tristan.spotifyfast.ui.home

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tristan.spotifyfast.data.local.HomeShortcutEntity
import com.tristan.spotifyfast.player.UnifiedPlaybackState
import com.tristan.spotifyfast.ui.components.EqualizerIndicator
import com.tristan.spotifyfast.ui.components.MediaArtwork
import com.tristan.spotifyfast.ui.components.SpotifyFilterChipsRow
import com.tristan.spotifyfast.ui.components.isLikedContext
import com.tristan.spotifyfast.ui.theme.SpotifyTokens

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    playbackState: UnifiedPlaybackState,
    userInitial: String,
    onOpenUri: (uri: String, title: String) -> Unit,
    onPlayTrack: (uri: String, contextUri: String?) -> Unit,
    onOpenProfile: () -> Unit,
    bottomPadding: PaddingValues,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(SpotifyTokens.Background)
            .statusBarsPadding(),
        contentPadding = PaddingValues(
            top = 12.dp,
            bottom = bottomPadding.calculateBottomPadding() + 20.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item(key = "header_chips") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = SpotifyTokens.ScreenHorizontalPadding),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(SpotifyTokens.AccentGreen)
                        .clickable(onClick = onOpenProfile),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = userInitial,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color.Black,
                    )
                }

                SpotifyFilterChipsRow(
                    items = listOf(
                        HomeFilter.ALL to "Tout",
                        HomeFilter.MUSIC to "Musique",
                        HomeFilter.PODCASTS to "Podcasts",
                    ),
                    selected = state.filter,
                    onSelected = viewModel::setFilter,
                )
            }
        }

        if (state.shortcuts.isNotEmpty()) {
            item(key = "shortcuts_grid") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = SpotifyTokens.ScreenHorizontalPadding),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    state.shortcuts.chunked(2).forEach { rowItems ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            rowItems.forEach { shortcut ->
                                ShortcutGridTile(
                                    shortcut = shortcut,
                                    isActiveContext = playbackState.contextUri == shortcut.uri,
                                    isPlaying = playbackState.isPlaying,
                                    onClick = { onOpenUri(shortcut.uri, shortcut.title) },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            if (rowItems.size == 1) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }

        if (state.recentTracks.isNotEmpty()) {
            item(key = "recent_tracks_section") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Écoutes récentes",
                        style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier.padding(horizontal = SpotifyTokens.ScreenHorizontalPadding),
                    )
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = SpotifyTokens.ScreenHorizontalPadding),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(state.recentTracks, key = { "recent_${it.uri}_${it.positionInContext}" }) { track ->
                            Column(
                                modifier = Modifier
                                    .width(136.dp)
                                    .clickable { onPlayTrack(track.uri, null) },
                            ) {
                                MediaArtwork(
                                    url = track.artworkUrl,
                                    contentDescription = track.title,
                                    size = 136.dp,
                                    fallbackColorArgb = track.dominantColorArgb,
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = track.title,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = track.artistName,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }

        if (state.inProgressEpisodes.isNotEmpty()) {
            item(key = "resume_episodes_section") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Reprendre vos épisodes",
                        style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier.padding(horizontal = SpotifyTokens.ScreenHorizontalPadding),
                    )
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = SpotifyTokens.ScreenHorizontalPadding),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(state.inProgressEpisodes, key = { it.uri }) { episode ->
                            val ratio = if (episode.durationMs > 0L) {
                                (episode.resumePositionMs.toFloat() / episode.durationMs.toFloat()).coerceIn(0f, 1f)
                            } else {
                                0f
                            }
                            Column(
                                modifier = Modifier
                                    .width(220.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(SpotifyTokens.SurfaceCard)
                                    .clickable { onPlayTrack(episode.uri, episode.showUri) }
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    MediaArtwork(
                                        url = episode.artworkUrl,
                                        contentDescription = episode.title,
                                        size = 48.dp,
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = episode.showName,
                                            style = MaterialTheme.typography.bodySmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Text(
                                            text = episode.title,
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
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
        }

        if (state.playlists.isNotEmpty()) {
            item(key = "playlists_section") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Vos playlists",
                        style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier.padding(horizontal = SpotifyTokens.ScreenHorizontalPadding),
                    )
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = SpotifyTokens.ScreenHorizontalPadding),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(state.playlists, key = { "home_pl_${it.id}" }) { playlist ->
                            Column(
                                modifier = Modifier
                                    .width(142.dp)
                                    .clickable { onOpenUri(playlist.uri, playlist.name) },
                            ) {
                                MediaArtwork(
                                    url = playlist.artworkUrl,
                                    contentDescription = playlist.name,
                                    size = 142.dp,
                                    fallbackColorArgb = playlist.dominantColorArgb,
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = playlist.name,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = "${playlist.trackCount} titres • ${playlist.ownerName}",
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }

        if (state.albums.isNotEmpty()) {
            item(key = "albums_section") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Vos albums favoris",
                        style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier.padding(horizontal = SpotifyTokens.ScreenHorizontalPadding),
                    )
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = SpotifyTokens.ScreenHorizontalPadding),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(state.albums, key = { it.id }) { album ->
                            Column(
                                modifier = Modifier
                                    .width(142.dp)
                                    .clickable { onOpenUri(album.uri, album.name) },
                            ) {
                                MediaArtwork(
                                    url = album.artworkUrl,
                                    contentDescription = album.name,
                                    size = 142.dp,
                                    fallbackColorArgb = album.dominantColorArgb,
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = album.name,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = album.artistName,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ShortcutGridTile(
    shortcut: HomeShortcutEntity,
    isActiveContext: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .height(56.dp)
            .clip(RoundedCornerShape(SpotifyTokens.CardCornerRadius))
            .background(SpotifyTokens.SurfaceElevated)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MediaArtwork(
            url = shortcut.artworkUrl,
            contentDescription = shortcut.title,
            size = 56.dp,
            cornerRadius = 0.dp,
            isLikedCollection = isLikedContext(shortcut.uri),
            fallbackColorArgb = shortcut.dominantColorArgb,
        )
        Text(
            text = shortcut.title,
            style = MaterialTheme.typography.labelMedium,
            color = if (isActiveContext) SpotifyTokens.AccentGreen else SpotifyTokens.TextPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp),
        )
        if (isActiveContext) {
            EqualizerIndicator(
                isPlaying = isPlaying,
                size = 14.dp,
                modifier = Modifier.padding(end = 8.dp),
            )
        }
    }
}
