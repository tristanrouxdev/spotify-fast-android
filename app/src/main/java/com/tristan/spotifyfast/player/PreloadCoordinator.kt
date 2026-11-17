package com.tristan.spotifyfast.player

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Triggers single-shot proactive preloading once playback reaches 80% of the current track's duration:
 * - Refreshes the upcoming Spotify queue
 * - Prefetches high-resolution artwork into Coil's disk cache
 * - Prefetches and caches synced LRCLIB lyrics for the next queued track
 */
class PreloadCoordinator(
    private val scope: CoroutineScope,
    private val onRefreshQueue: suspend () -> List<QueueTrack>,
    private val onPrefetchArtwork: suspend (String) -> Unit,
    private val onPrefetchLyrics: suspend (QueueTrack) -> Unit,
    private val thresholdRatio: Float = 0.80f,
) {
    @Volatile
    private var lastTriggeredTrackUri: String? = null

    fun onPlaybackProgress(state: UnifiedPlaybackState, currentQueue: List<QueueTrack>) {
        if (state.itemUri.isBlank() || state.durationMs <= 0L) return
        if (lastTriggeredTrackUri == state.itemUri) return

        val thresholdMs = (state.durationMs * thresholdRatio).toLong()
        if (state.positionMs < thresholdMs) return

        lastTriggeredTrackUri = state.itemUri
        scope.launch {
            val refreshedQueue = runCatching { onRefreshQueue() }.getOrDefault(currentQueue)
            val nextTrack = refreshedQueue.firstOrNull { it.uri != state.itemUri } ?: return@launch
            nextTrack.artworkUrl?.takeIf { it.isNotBlank() }?.let { url ->
                runCatching { onPrefetchArtwork(url) }
            }
            if (!nextTrack.uri.startsWith("spotify:episode:") && !nextTrack.uri.startsWith("spotify:chapter:")) {
                runCatching { onPrefetchLyrics(nextTrack) }
            }
        }
    }

    fun resetForTest() {
        lastTriggeredTrackUri = null
    }
}
