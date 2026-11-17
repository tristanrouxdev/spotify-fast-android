package com.tristan.spotifyfast.player

import com.tristan.spotifyfast.data.remote.PlaybackOffsetDto
import com.tristan.spotifyfast.data.remote.PlaybackStateDto
import com.tristan.spotifyfast.data.remote.SpotifyWebApi
import com.tristan.spotifyfast.data.remote.StartPlaybackRequest
import com.tristan.spotifyfast.data.remote.TransferPlaybackRequest
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Response

/**
 * Manages Spotify Connect REST state, active devices, session transfers, and remote volume.
 * Properly checks HTTP status codes and recovers from 404 NO_ACTIVE_DEVICE when devices exist.
 */
class SpotifyConnectManager(
    private val api: SpotifyWebApi,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private fun Response<Unit>.requireSuccess(action: String) {
        if (!isSuccessful) {
            throw IllegalStateException("Spotify Connect $action failed (HTTP ${code()})")
        }
    }

    suspend fun fetchDevices(): Result<List<ConnectDevice>> = withContext(ioDispatcher) {
        runCatching {
            api.getAvailableDevices().devices.mapNotNull { dto ->
                val id = dto.id ?: return@mapNotNull null
                ConnectDevice(
                    id = id,
                    name = dto.name,
                    type = dto.type,
                    isActive = dto.isActive,
                    volumePercent = dto.volumePercent ?: 50,
                    isLocalPhone = dto.type.equals("Smartphone", ignoreCase = true) && dto.isActive,
                )
            }
        }
    }

    suspend fun fetchCurrentPlaybackState(): Result<PlaybackStateDto?> = withContext(ioDispatcher) {
        runCatching {
            val response = api.getPlaybackState()
            if (response.code() == 204 || !response.isSuccessful) null else response.body()
        }
    }

    suspend fun fetchQueue(): Result<List<QueueTrack>> = withContext(ioDispatcher) {
        runCatching {
            api.getQueue().queue.mapNotNull { item ->
                if (item.uri.isBlank()) return@mapNotNull null
                QueueTrack(
                    uri = item.uri,
                    title = item.name,
                    subtitle = item.primarySubtitle,
                    albumName = item.album?.name ?: item.show?.name.orEmpty(),
                    artworkUrl = item.artworkUrl,
                    durationMs = item.durationMs,
                )
            }
        }
    }

    suspend fun transferPlayback(deviceId: String, playImmediately: Boolean = true): Result<Unit> =
        withContext(ioDispatcher) {
            runCatching {
                api.transferPlayback(TransferPlaybackRequest(listOf(deviceId), playImmediately))
                    .requireSuccess("transfer")
            }
        }

    suspend fun playOnConnect(
        deviceId: String?,
        uri: String?,
        contextUri: String?,
        offsetIndex: Int?,
        fallbackTrackUris: List<String> = emptyList(),
    ): Result<Unit> = withContext(ioDispatcher) {
        runCatching {
            val isValidSpotifyContext = !contextUri.isNullOrBlank() &&
                (contextUri.startsWith("spotify:playlist:") ||
                    contextUri.startsWith("spotify:album:") ||
                    contextUri.startsWith("spotify:show:") ||
                    contextUri.startsWith("spotify:artist:"))

            val body = when {
                isValidSpotifyContext && uri == contextUri -> StartPlaybackRequest(contextUri = contextUri)
                isValidSpotifyContext && offsetIndex != null && !contextUri!!.startsWith("spotify:artist:") ->
                    StartPlaybackRequest(
                        contextUri = contextUri,
                        offset = PlaybackOffsetDto(position = offsetIndex),
                    )
                isValidSpotifyContext && !uri.isNullOrBlank() && !contextUri!!.startsWith("spotify:artist:") ->
                    StartPlaybackRequest(
                        contextUri = contextUri,
                        offset = PlaybackOffsetDto(uri = uri),
                    )
                isValidSpotifyContext -> StartPlaybackRequest(contextUri = contextUri)
                fallbackTrackUris.isNotEmpty() -> {
                    val capped = fallbackTrackUris.take(50)
                    val offsetUri = uri?.takeIf { it in capped }
                    StartPlaybackRequest(
                        uris = capped,
                        offset = offsetUri?.let { PlaybackOffsetDto(uri = it) },
                    )
                }
                !uri.isNullOrBlank() -> StartPlaybackRequest(uris = listOf(uri))
                else -> null
            }

            val resp = api.startOrResumePlayback(deviceId = deviceId, body = body)
            if (resp.code() == 404 && deviceId == null) {
                val devices = fetchDevices().getOrDefault(emptyList())
                val candidate = devices.firstOrNull { it.isActive }
                    ?: devices.firstOrNull { it.type.equals("Smartphone", ignoreCase = true) }
                    ?: devices.firstOrNull()
                if (candidate != null) {
                    api.transferPlayback(TransferPlaybackRequest(listOf(candidate.id), play = false))
                    val retry = api.startOrResumePlayback(deviceId = candidate.id, body = body)
                    retry.requireSuccess("play(retry)")
                    return@runCatching
                }
            }
            resp.requireSuccess("play")
        }
    }

    suspend fun pause(deviceId: String?): Result<Unit> = withContext(ioDispatcher) {
        runCatching { api.pausePlayback(deviceId).requireSuccess("pause") }
    }

    suspend fun next(deviceId: String?): Result<Unit> = withContext(ioDispatcher) {
        runCatching { api.skipToNext(deviceId).requireSuccess("next") }
    }

    suspend fun previous(deviceId: String?): Result<Unit> = withContext(ioDispatcher) {
        runCatching { api.skipToPrevious(deviceId).requireSuccess("previous") }
    }

    suspend fun seekTo(positionMs: Long, deviceId: String?): Result<Unit> = withContext(ioDispatcher) {
        runCatching { api.seekToPosition(positionMs.coerceAtLeast(0L), deviceId).requireSuccess("seek") }
    }

    suspend fun setShuffle(enabled: Boolean, deviceId: String?): Result<Unit> = withContext(ioDispatcher) {
        runCatching { api.setShuffle(enabled, deviceId).requireSuccess("shuffle") }
    }

    suspend fun setRepeat(mode: RepeatMode, deviceId: String?): Result<Unit> = withContext(ioDispatcher) {
        val state = when (mode) {
            RepeatMode.OFF -> "off"
            RepeatMode.CONTEXT -> "context"
            RepeatMode.TRACK -> "track"
        }
        runCatching { api.setRepeatMode(state, deviceId).requireSuccess("repeat") }
    }

    suspend fun setVolume(volumePercent: Int, deviceId: String?): Result<Unit> = withContext(ioDispatcher) {
        runCatching { api.setVolume(volumePercent.coerceIn(0, 100), deviceId).requireSuccess("volume") }
    }

    suspend fun addToQueue(uri: String): Result<Unit> = withContext(ioDispatcher) {
        runCatching { api.addToQueue(uri).requireSuccess("queue") }
    }
}
