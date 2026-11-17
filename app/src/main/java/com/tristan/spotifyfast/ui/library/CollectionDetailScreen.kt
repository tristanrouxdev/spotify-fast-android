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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tristan.spotifyfast.data.local.DEFAULT_DOMINANT_COLOR
import com.tristan.spotifyfast.player.UnifiedPlaybackState
import com.tristan.spotifyfast.ui.components.EqualizerIndicator
import com.tristan.spotifyfast.ui.components.MediaArtwork
import com.tristan.spotifyfast.ui.components.formatDurationMs
import com.tristan.spotifyfast.ui.components.isLikedContext
import com.tristan.spotifyfast.ui.icons.SpotifyIcons
import com.tristan.spotifyfast.ui.theme.SpotifyTokens

@Composable
fun CollectionDetailScreen(
    contextUri: String,
    title: String,
    viewModel: LibraryViewModel,
    playbackState: UnifiedPlaybackState,
    onBack: () -> Unit,
    onPlayTrack: (uri: String, contextUri: String, index: Int) -> Unit,
    onToggleContextPlayPause: () -> Unit,
    onShufflePlay: () -> Unit,
    onToggleSaveTrack: (uri: String, currentlyLiked: Boolean) -> Unit,
    onEnqueueTrack: (uri: String) -> Unit,
    bottomPadding: PaddingValues,
) {
    val tracks by viewModel.observeTracksForContext(contextUri)
        .collectAsStateWithLifecycle(initialValue = emptyList())
    var dominantColor by remember(contextUri) { mutableIntStateOf(DEFAULT_DOMINANT_COLOR) }
    var isSavedInLibrary by remember(contextUri) { mutableStateOf(true) }

    LaunchedEffect(contextUri) {
        dominantColor = viewModel.getDominantColorForUri(contextUri)
        viewModel.refreshCollectionInBackground(contextUri)
    }

    val isContextActive = remember(playbackState.contextUri, playbackState.itemUri, tracks, contextUri) {
        playbackState.contextUri == contextUri ||
            (tracks.isNotEmpty() && tracks.any { it.uri == playbackState.itemUri })
    }
    val isContextPlaying = isContextActive && playbackState.isPlaying

    val headerGradient = Brush.verticalGradient(
        colors = listOf(Color(dominantColor).copy(alpha = 0.72f), SpotifyTokens.Background),
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(SpotifyTokens.Background),
        contentPadding = PaddingValues(bottom = bottomPadding.calculateBottomPadding() + 24.dp),
    ) {
        item(key = "header") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(headerGradient)
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

                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    MediaArtwork(
                        url = tracks.firstOrNull()?.artworkUrl,
                        contentDescription = title,
                        size = 188.dp,
                        isLikedCollection = isLikedContext(contextUri),
                        fallbackColorArgb = dominantColor,
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineLarge,
                )
                Text(
                    text = "${tracks.size} titres",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp),
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (!isLikedContext(contextUri)) {
                        IconButton(
                            onClick = {
                                viewModel.toggleLibraryMembership(contextUri, isSavedInLibrary) { saved ->
                                    isSavedInLibrary = saved
                                }
                            },
                        ) {
                            Icon(
                                imageVector = if (isSavedInLibrary) SpotifyIcons.Check else SpotifyIcons.Add,
                                contentDescription = "Bibliothèque",
                                tint = if (isSavedInLibrary) SpotifyTokens.AccentGreen else SpotifyTokens.TextPrimary,
                            )
                        }
                    } else {
                        Spacer(modifier = Modifier.width(48.dp))
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        IconButton(onClick = onShufflePlay) {
                            Icon(
                                imageVector = SpotifyIcons.Shuffle,
                                contentDescription = "Lecture aléatoire",
                                tint = if (playbackState.shuffleEnabled) SpotifyTokens.AccentGreen else SpotifyTokens.TextSecondary,
                                modifier = Modifier.size(24.dp),
                            )
                        }

                        Box(
                            modifier = Modifier
                                .size(54.dp)
                                .clip(CircleShape)
                                .background(SpotifyTokens.AccentGreen)
                                .clickable(onClick = onToggleContextPlayPause),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = if (isContextPlaying) SpotifyIcons.Pause else SpotifyIcons.Play,
                                contentDescription = if (isContextPlaying) "Pause" else "Lire",
                                tint = Color.Black,
                                modifier = Modifier.size(26.dp),
                            )
                        }
                    }
                }
            }
        }

        itemsIndexed(tracks, key = { idx, t -> "${t.uri}_$idx" }) { index, track ->
            val isActive = playbackState.itemUri == track.uri
            var menuExpanded by remember { mutableStateOf(false) }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onPlayTrack(track.uri, contextUri, index) }
                    .padding(horizontal = SpotifyTokens.ScreenHorizontalPadding, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MediaArtwork(
                    url = track.artworkUrl,
                    contentDescription = track.title,
                    size = 48.dp,
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isActive) {
                            EqualizerIndicator(
                                isPlaying = playbackState.isPlaying,
                                size = 14.dp,
                                modifier = Modifier.padding(end = 6.dp),
                            )
                        }
                        Text(
                            text = track.title,
                            style = MaterialTheme.typography.titleMedium,
                            color = if (isActive) SpotifyTokens.AccentGreen else SpotifyTokens.TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text(
                        text = "${track.artistName} • ${formatDurationMs(track.durationMs)}",
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                IconButton(onClick = { onToggleSaveTrack(track.uri, track.isLiked) }) {
                    Icon(
                        imageVector = if (track.isLiked) SpotifyIcons.Heart else SpotifyIcons.HeartOutline,
                        contentDescription = "Aimer",
                        tint = if (track.isLiked) SpotifyTokens.AccentGreen else SpotifyTokens.TextSecondary,
                        modifier = Modifier.size(18.dp),
                    )
                }

                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(
                            imageVector = SpotifyIcons.MoreVert,
                            contentDescription = "Options du titre",
                            tint = SpotifyTokens.TextSecondary,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                        modifier = Modifier.background(SpotifyTokens.SurfaceElevated),
                    ) {
                        DropdownMenuItem(
                            text = { Text("Lire maintenant", color = SpotifyTokens.TextPrimary) },
                            onClick = {
                                menuExpanded = false
                                onPlayTrack(track.uri, contextUri, index)
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Ajouter à la file d'attente", color = SpotifyTokens.TextPrimary) },
                            onClick = {
                                menuExpanded = false
                                onEnqueueTrack(track.uri)
                            },
                        )
                        DropdownMenuItem(
                            text = {
                                Text(
                                    if (track.isLiked) "Retirer des Titres likés" else "Ajouter aux Titres likés",
                                    color = SpotifyTokens.TextPrimary,
                                )
                            },
                            onClick = {
                                menuExpanded = false
                                onToggleSaveTrack(track.uri, track.isLiked)
                            },
                        )
                    }
                }
            }
        }
    }
}
