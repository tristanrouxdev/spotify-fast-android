package com.tristan.spotifyfast.ui.nowplaying

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.unit.sp
import com.tristan.spotifyfast.data.sync.LyricsRepository
import com.tristan.spotifyfast.data.sync.TrackLyrics
import com.tristan.spotifyfast.player.QueueTrack
import com.tristan.spotifyfast.player.RepeatMode
import com.tristan.spotifyfast.player.UnifiedPlaybackState
import com.tristan.spotifyfast.ui.components.MediaArtwork
import com.tristan.spotifyfast.ui.components.formatDurationMs
import com.tristan.spotifyfast.ui.icons.SpotifyIcons
import com.tristan.spotifyfast.ui.theme.SpotifyTokens

private val PODCAST_SPEEDS = listOf(0.8f, 1.0f, 1.2f, 1.5f, 1.8f, 2.0f, 3.0f)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingSheet(
    state: UnifiedPlaybackState,
    queue: List<QueueTrack>,
    lyricsRepository: LyricsRepository,
    onDismiss: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onSetPodcastSpeed: (Float) -> Unit,
    onToggleLike: () -> Unit,
    onOpenDevices: () -> Unit,
    onPlayQueuedUri: (String) -> Unit,
    onRefreshQueue: () -> Unit,
) {
    var showFullLyrics by remember { mutableStateOf(false) }
    var showQueueSheet by remember { mutableStateOf(false) }
    var showSpeedPicker by remember { mutableStateOf(false) }
    var lyrics by remember(state.itemUri) { mutableStateOf<TrackLyrics?>(null) }

    LaunchedEffect(state.itemUri) {
        if (state.itemUri.isNotBlank() && !state.isEpisodeOrChapter) {
            lyrics = lyricsRepository.getLyrics(
                trackUri = state.itemUri,
                trackTitle = state.title,
                artistName = state.subtitle,
                albumName = state.albumName,
                durationMs = state.durationMs,
            )
        } else {
            lyrics = null
        }
    }

    if (showFullLyrics) {
        SyncedLyricsFullScreen(
            title = state.title,
            subtitle = state.subtitle,
            lyrics = lyrics,
            positionMs = state.positionMs,
            dominantColorArgb = state.dominantColorArgb,
            onSeekTo = onSeekTo,
            onClose = { showFullLyrics = false },
        )
        return
    }

    val bgGradient = Brush.verticalGradient(
        colors = listOf(
            Color(state.dominantColorArgb).copy(alpha = 0.78f),
            SpotifyTokens.Background,
            SpotifyTokens.Background,
        ),
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bgGradient)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Top header bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = SpotifyIcons.Back,
                    contentDescription = "Réduire",
                    tint = Color.White,
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = if (state.isEpisodeOrChapter) "PODCAST / LIVRE AUDIO" else "EN LECTURE",
                    style = MaterialTheme.typography.bodySmall.copy(letterSpacing = 1.sp),
                    color = Color.White.copy(alpha = 0.8f),
                )
                if (state.albumName.isNotBlank()) {
                    Text(
                        text = state.albumName,
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            IconButton(
                onClick = {
                    onRefreshQueue()
                    showQueueSheet = true
                },
            ) {
                Icon(
                    imageVector = SpotifyIcons.Queue,
                    contentDescription = "File d'attente",
                    tint = Color.White,
                )
            }
        }

        // Artwork
        MediaArtwork(
            url = state.artworkUrl,
            contentDescription = state.title,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
            cornerRadius = 10.dp,
            fallbackColorArgb = state.dominantColorArgb,
        )

        // Title + Like button
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = state.title,
                    style = MaterialTheme.typography.headlineMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = state.subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = SpotifyTokens.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onToggleLike) {
                Icon(
                    imageVector = if (state.isSavedInLibrary) SpotifyIcons.Heart else SpotifyIcons.HeartOutline,
                    contentDescription = "Aimer",
                    tint = if (state.isSavedInLibrary) SpotifyTokens.AccentGreen else Color.White,
                    modifier = Modifier.size(26.dp),
                )
            }
        }

        // Smooth 60fps Seekbar
        var isDragging by remember { mutableStateOf(false) }
        var dragPosMs by remember { mutableFloatStateOf(0f) }
        val durationFloat = state.durationMs.coerceAtLeast(1L).toFloat()
        val displayPosMs = if (isDragging) dragPosMs.toLong() else state.positionMs.coerceIn(0L, state.durationMs)

        Column(modifier = Modifier.fillMaxWidth()) {
            Slider(
                value = displayPosMs.toFloat().coerceIn(0f, durationFloat),
                onValueChange = {
                    isDragging = true
                    dragPosMs = it
                },
                onValueChangeFinished = {
                    onSeekTo(dragPosMs.toLong())
                    isDragging = false
                },
                valueRange = 0f..durationFloat,
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = Color.White,
                    inactiveTrackColor = Color.White.copy(alpha = 0.25f),
                ),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(formatDurationMs(displayPosMs), style = MaterialTheme.typography.bodySmall)
                Text(formatDurationMs(state.durationMs), style = MaterialTheme.typography.bodySmall)
            }
        }

        // Transport controls (Adaptive Music vs Podcast/Audiobook)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            if (state.isEpisodeOrChapter) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(SpotifyTokens.SurfaceElevated)
                        .clickable { showSpeedPicker = true }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                ) {
                    Text(
                        text = "${state.playbackSpeed}x",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = SpotifyTokens.AccentGreen,
                    )
                }

                IconButton(onClick = { onSeekTo((state.positionMs - 15_000L).coerceAtLeast(0L)) }) {
                    Icon(
                        imageVector = SpotifyIcons.Replay15,
                        contentDescription = "-15 secondes",
                        tint = Color.White,
                        modifier = Modifier.size(30.dp),
                    )
                }
            } else {
                IconButton(onClick = onToggleShuffle) {
                    Icon(
                        imageVector = SpotifyIcons.Shuffle,
                        contentDescription = "Aléatoire",
                        tint = if (state.shuffleEnabled) SpotifyTokens.AccentGreen else Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(22.dp),
                    )
                }

                IconButton(onClick = onSkipPrevious) {
                    Icon(
                        imageVector = SpotifyIcons.SkipPrevious,
                        contentDescription = "Précédent",
                        tint = Color.White,
                        modifier = Modifier.size(34.dp),
                    )
                }
            }

            Box(
                modifier = Modifier
                    .size(66.dp)
                    .clip(CircleShape)
                    .background(Color.White)
                    .clickable(onClick = onTogglePlayPause),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (state.isPlaying) SpotifyIcons.Pause else SpotifyIcons.Play,
                    contentDescription = if (state.isPlaying) "Pause" else "Lecture",
                    tint = Color.Black,
                    modifier = Modifier.size(32.dp),
                )
            }

            if (state.isEpisodeOrChapter) {
                IconButton(onClick = { onSeekTo((state.positionMs + 15_000L).coerceAtMost(state.durationMs)) }) {
                    Icon(
                        imageVector = SpotifyIcons.Forward15,
                        contentDescription = "+15 secondes",
                        tint = Color.White,
                        modifier = Modifier.size(30.dp),
                    )
                }

                IconButton(
                    onClick = {
                        onRefreshQueue()
                        showQueueSheet = true
                    },
                ) {
                    Icon(
                        imageVector = SpotifyIcons.Queue,
                        contentDescription = "File d'attente",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp),
                    )
                }
            } else {
                IconButton(onClick = onSkipNext) {
                    Icon(
                        imageVector = SpotifyIcons.SkipNext,
                        contentDescription = "Suivant",
                        tint = Color.White,
                        modifier = Modifier.size(34.dp),
                    )
                }

                IconButton(onClick = onCycleRepeat) {
                    val tint = if (state.repeatMode == RepeatMode.OFF) Color.White.copy(alpha = 0.7f) else SpotifyTokens.AccentGreen
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = SpotifyIcons.Repeat,
                            contentDescription = "Répéter",
                            tint = tint,
                            modifier = Modifier.size(22.dp),
                        )
                        if (state.repeatMode == RepeatMode.TRACK) {
                            Text(
                                text = "1",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold),
                                color = SpotifyTokens.AccentGreen,
                            )
                        }
                    }
                }
            }
        }

        // Bottom row: Connect Devices + Queue
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .clickable(onClick = onOpenDevices)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = SpotifyIcons.Devices,
                    contentDescription = "Spotify Connect",
                    tint = if (state.isRemoteConnect) SpotifyTokens.AccentGreen else Color.White,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = state.activeDevice?.name ?: "Cet appareil",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (state.isRemoteConnect) SpotifyTokens.AccentGreen else SpotifyTokens.TextSecondary,
                )
            }

            IconButton(
                onClick = {
                    onRefreshQueue()
                    showQueueSheet = true
                },
            ) {
                Icon(
                    imageVector = SpotifyIcons.Queue,
                    contentDescription = "File d'attente",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        if (!state.isEpisodeOrChapter) {
            LyricsPreviewCard(
                lyrics = lyrics,
                positionMs = state.positionMs,
                dominantColorArgb = state.dominantColorArgb,
                onExpand = { showFullLyrics = true },
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
    }

    if (showSpeedPicker) {
        ModalBottomSheet(
            onDismissRequest = { showSpeedPicker = false },
            containerColor = SpotifyTokens.SurfaceCard,
            contentColor = SpotifyTokens.TextPrimary,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Vitesse de lecture", style = MaterialTheme.typography.headlineMedium)
                PODCAST_SPEEDS.forEach { speed ->
                    val selected = state.playbackSpeed == speed
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                onSetPodcastSpeed(speed)
                                showSpeedPicker = false
                            }
                            .padding(vertical = 12.dp, horizontal = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = "${speed}x",
                            style = MaterialTheme.typography.titleMedium,
                            color = if (selected) SpotifyTokens.AccentGreen else SpotifyTokens.TextPrimary,
                        )
                        if (selected) {
                            Icon(
                                imageVector = SpotifyIcons.Check,
                                contentDescription = null,
                                tint = SpotifyTokens.AccentGreen,
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    if (showQueueSheet) {
        ModalBottomSheet(
            onDismissRequest = { showQueueSheet = false },
            containerColor = SpotifyTokens.SurfaceCard,
            contentColor = SpotifyTokens.TextPrimary,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("File d'attente", style = MaterialTheme.typography.headlineMedium)
                if (queue.isEmpty()) {
                    Text(
                        text = "Aucun titre suivant dans la file d'attente.",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(vertical = 12.dp),
                    )
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        items(queue, key = { it.uri }) { qTrack ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onPlayQueuedUri(qTrack.uri)
                                        showQueueSheet = false
                                    }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                MediaArtwork(url = qTrack.artworkUrl, contentDescription = qTrack.title, size = 44.dp)
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = qTrack.title,
                                        style = MaterialTheme.typography.titleMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = qTrack.subtitle,
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }
}
