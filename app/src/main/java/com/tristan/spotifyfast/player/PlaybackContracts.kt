package com.tristan.spotifyfast.player

import com.tristan.spotifyfast.data.local.DEFAULT_DOMINANT_COLOR
import kotlinx.coroutines.flow.StateFlow

enum class RepeatMode {
    OFF,
    CONTEXT,
    TRACK,
}

data class ConnectDevice(
    val id: String,
    val name: String,
    val type: String,
    val isActive: Boolean,
    val volumePercent: Int,
    val isLocalPhone: Boolean = false,
)

data class QueueTrack(
    val uri: String,
    val title: String,
    val subtitle: String,
    val albumName: String,
    val artworkUrl: String?,
    val durationMs: Long,
)

data class UnifiedPlaybackState(
    val itemUri: String = "",
    val title: String = "",
    val subtitle: String = "",
    val albumName: String = "",
    val artworkUrl: String? = null,
    val contextUri: String? = null,
    val isEpisodeOrChapter: Boolean = false,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val playbackSpeed: Float = 1.0f,
    val shuffleEnabled: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.OFF,
    val isSavedInLibrary: Boolean = false,
    val activeDevice: ConnectDevice? = null,
    val dominantColorArgb: Int = DEFAULT_DOMINANT_COLOR,
) {
    val isRemoteConnect: Boolean
        get() = activeDevice != null && !activeDevice.isLocalPhone
}

interface PlaybackController {
    val playbackState: StateFlow<UnifiedPlaybackState>
    val connectedDevices: StateFlow<List<ConnectDevice>>
    val queue: StateFlow<List<QueueTrack>>
    val isAppRemoteConnected: StateFlow<Boolean>
    val lastErrorMessage: StateFlow<String?>

    suspend fun playUri(uri: String, contextUri: String? = null, offsetIndex: Int? = null)
    suspend fun toggleContextPlayback(contextUri: String)
    suspend fun shufflePlayContext(contextUri: String)
    suspend fun togglePlayPause()
    suspend fun skipNext()
    suspend fun skipPrevious()
    suspend fun seekTo(positionMs: Long)
    suspend fun setShuffle(enabled: Boolean)
    suspend fun setRepeatMode(mode: RepeatMode)
    suspend fun setPodcastSpeed(multiplier: Float)
    suspend fun enqueue(uri: String)
    suspend fun transferPlayback(deviceId: String, playImmediately: Boolean = true)
    suspend fun setRemoteVolume(volumePercent: Int, deviceId: String? = null)
    suspend fun refreshDevices()
    suspend fun refreshQueue()
    suspend fun toggleLikeCurrentTrack()
    suspend fun reconnectSpotifyApp(clientId: String): Result<Unit>
    fun clearError()
}

/**
 * Abstraction over local Spotify App Remote IPC (`com.spotify.music`) to allow deterministic unit testing.
 */
interface LocalPlaybackTransport {
    val isConnected: StateFlow<Boolean>
    val localState: StateFlow<UnifiedPlaybackState?>
    val lastConnectionError: StateFlow<String?>

    fun setClientId(clientId: String)
    fun prewarm(clientId: String)
    fun isSpotifyInstalled(): Boolean
    suspend fun reconnectNow(clientId: String): Result<Unit>
    suspend fun playUri(uri: String, contextUri: String? = null, offsetIndex: Int? = null): Result<Unit>
    suspend fun resume(): Result<Unit>
    suspend fun pause(): Result<Unit>
    suspend fun skipNext(): Result<Unit>
    suspend fun skipPrevious(): Result<Unit>
    suspend fun seekTo(positionMs: Long): Result<Unit>
    suspend fun setShuffle(enabled: Boolean): Result<Unit>
    suspend fun setRepeat(mode: RepeatMode): Result<Unit>
    suspend fun setPodcastSpeed(multiplier: Float): Result<Unit>
    suspend fun enqueue(uri: String): Result<Unit>
    fun disconnect()
}
