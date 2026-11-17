package com.tristan.spotifyfast.player

import android.os.SystemClock
import com.tristan.spotifyfast.auth.EncryptedTokenStore
import com.tristan.spotifyfast.data.local.DEFAULT_DOMINANT_COLOR
import com.tristan.spotifyfast.data.local.PlaybackSnapshotEntity
import com.tristan.spotifyfast.data.local.SpotifyDao
import com.tristan.spotifyfast.data.sync.ArtworkPaletteExtractor
import com.tristan.spotifyfast.data.sync.LyricsRepository
import com.tristan.spotifyfast.data.sync.OfflineFirstSyncEngine
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Unified playback controller routing seamlessly between `AppRemoteBridge` (low-latency local IPC),
 * `SpotifyConnectManager` (remote speakers/PC/TV via REST + 50 ms interpolation ticker),
 * and `DemoAudioSynthesizer` (audible feedback in offline Demo Mode).
 */
class PlaybackRouter(
    private val dao: SpotifyDao,
    private val localTransport: LocalPlaybackTransport,
    private val connectManager: SpotifyConnectManager,
    private val syncEngine: OfflineFirstSyncEngine,
    private val tokenStore: EncryptedTokenStore? = null,
    private val demoSynthesizer: DemoAudioSynthesizer? = null,
    private val lyricsRepository: LyricsRepository? = null,
    private val paletteExtractor: ArtworkPaletteExtractor? = null,
    private val prefetchArtwork: suspend (String) -> Unit = {},
    private val onRemoteSessionChanged: (Boolean) -> Unit = {},
    private val elapsedRealtimeMs: () -> Long = { SystemClock.elapsedRealtime() },
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val enableBackgroundPolling: Boolean = true,
) : PlaybackController {

    private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)

    private val _playbackState = MutableStateFlow(UnifiedPlaybackState())
    override val playbackState: StateFlow<UnifiedPlaybackState> = _playbackState.asStateFlow()

    private val _connectedDevices = MutableStateFlow<List<ConnectDevice>>(emptyList())
    override val connectedDevices: StateFlow<List<ConnectDevice>> = _connectedDevices.asStateFlow()

    private val _queue = MutableStateFlow<List<QueueTrack>>(emptyList())
    override val queue: StateFlow<List<QueueTrack>> = _queue.asStateFlow()

    override val isAppRemoteConnected: StateFlow<Boolean> = localTransport.isConnected

    private val _lastErrorMessage = MutableStateFlow<String?>(null)
    override val lastErrorMessage: StateFlow<String?> = _lastErrorMessage.asStateFlow()

    private var tickerJob: Job? = null
    private var pollJob: Job? = null
    private var anchorPositionMs: Long = 0L
    private var anchorElapsedMs: Long = 0L
    private var lastRemoteSessionFlag: Boolean = false

    val preloadCoordinator = PreloadCoordinator(
        scope = scope,
        onRefreshQueue = {
            refreshQueue()
            _queue.value
        },
        onPrefetchArtwork = prefetchArtwork,
        onPrefetchLyrics = { nextTrack ->
            lyricsRepository?.getLyrics(
                trackUri = nextTrack.uri,
                trackTitle = nextTrack.title,
                artistName = nextTrack.subtitle,
                albumName = nextTrack.albumName,
                durationMs = nextTrack.durationMs,
            )
        },
    )

    init {
        scope.launch {
            dao.observePlaybackSnapshot().firstOrNull()?.let { snap ->
                val activeDevice = if (!snap.activeDeviceId.isNullOrBlank()) {
                    ConnectDevice(
                        id = snap.activeDeviceId,
                        name = snap.activeDeviceName ?: "Connect",
                        type = snap.activeDeviceType ?: "Speaker",
                        isActive = true,
                        volumePercent = 50,
                        isLocalPhone = snap.activeDeviceType.equals("Smartphone", ignoreCase = true),
                    )
                } else {
                    null
                }
                _playbackState.value = UnifiedPlaybackState(
                    itemUri = snap.itemUri,
                    title = snap.title,
                    subtitle = snap.subtitle,
                    albumName = snap.albumName,
                    artworkUrl = snap.artworkUrl,
                    contextUri = snap.contextUri,
                    isEpisodeOrChapter = snap.isEpisodeOrChapter,
                    isPlaying = false,
                    positionMs = snap.positionMs,
                    durationMs = snap.durationMs,
                    playbackSpeed = snap.playbackSpeed,
                    shuffleEnabled = snap.shuffleEnabled,
                    repeatMode = runCatching { RepeatMode.valueOf(snap.repeatMode) }.getOrDefault(RepeatMode.OFF),
                    isSavedInLibrary = snap.isSavedInLibrary,
                    activeDevice = activeDevice,
                    dominantColorArgb = snap.dominantColorArgb,
                )
            }
        }

        scope.launch {
            localTransport.localState.collect { local ->
                if (local == null) return@collect
                if (!_playbackState.value.isRemoteConnect) {
                    applyUpdatedState(
                        local.copy(
                            contextUri = local.contextUri ?: _playbackState.value.contextUri,
                            activeDevice = _playbackState.value.activeDevice,
                        ),
                    )
                }
            }
        }

        if (enableBackgroundPolling) {
            startTickerLoop()
            startRemotePollLoop()
        }
    }

    override fun clearError() {
        _lastErrorMessage.value = null
    }

    fun startPostFrameWarmup(clientId: String) {
        if (clientId.isNotBlank() && localTransport.isSpotifyInstalled()) {
            localTransport.prewarm(clientId)
        }
        scope.launch {
            refreshDevices()
            syncPlaybackFromConnectOnce()
        }
    }

    override suspend fun reconnectSpotifyApp(clientId: String): Result<Unit> {
        val result = localTransport.reconnectNow(clientId)
        result.onFailure { err ->
            _lastErrorMessage.value = err.message ?: "Connexion à l'application Spotify impossible"
        }.onSuccess {
            _lastErrorMessage.value = null
        }
        return result
    }

    private fun startTickerLoop() {
        tickerJob?.cancel()
        tickerJob = scope.launch {
            while (isActive) {
                delay(50L)
                val current = _playbackState.value
                if (current.isPlaying && current.durationMs > 0L) {
                    val elapsed = (elapsedRealtimeMs() - anchorElapsedMs).coerceAtLeast(0L)
                    val interpolated = (anchorPositionMs + (elapsed * current.playbackSpeed).toLong())
                        .coerceIn(0L, current.durationMs)
                    if (interpolated != current.positionMs) {
                        val updated = current.copy(positionMs = interpolated)
                        _playbackState.value = updated
                        preloadCoordinator.onPlaybackProgress(updated, _queue.value)
                    }
                }
            }
        }
    }

    private fun startRemotePollLoop() {
        pollJob?.cancel()
        pollJob = scope.launch {
            while (isActive) {
                delay(5_000L)
                syncPlaybackFromConnectOnce()
            }
        }
    }

    suspend fun syncPlaybackFromConnectOnce() {
        val bundle = tokenStore?.loadTokenBundle()
        if (bundle?.isDemoMode == true) return

        val dto = connectManager.fetchCurrentPlaybackState().getOrNull() ?: return
        val device = dto.device?.let { d ->
            ConnectDevice(
                id = d.id ?: "remote",
                name = d.name,
                type = d.type,
                isActive = d.isActive,
                volumePercent = d.volumePercent ?: 50,
                isLocalPhone = d.type.equals("Smartphone", ignoreCase = true),
            )
        }
        val item = dto.item ?: return
        val isLocal = device == null || device.isLocalPhone
        if (isLocal && localTransport.isConnected.value) {
            if (_playbackState.value.activeDevice != device) {
                applyUpdatedState(_playbackState.value.copy(activeDevice = device))
            }
            return
        }

        val repeat = when (dto.repeatState) {
            "track" -> RepeatMode.TRACK
            "context" -> RepeatMode.CONTEXT
            else -> RepeatMode.OFF
        }
        applyUpdatedState(
            UnifiedPlaybackState(
                itemUri = item.uri,
                title = item.name,
                subtitle = item.primarySubtitle,
                albumName = item.album?.name ?: item.show?.name.orEmpty(),
                artworkUrl = item.artworkUrl,
                contextUri = dto.context?.uri ?: _playbackState.value.contextUri,
                isEpisodeOrChapter = item.isEpisodeOrChapter,
                isPlaying = dto.isPlaying,
                positionMs = dto.progressMs,
                durationMs = item.durationMs,
                playbackSpeed = _playbackState.value.playbackSpeed,
                shuffleEnabled = dto.shuffleState,
                repeatMode = repeat,
                isSavedInLibrary = dao.isTrackLiked(item.uri),
                activeDevice = device,
                dominantColorArgb = _playbackState.value.dominantColorArgb,
            ),
        )
    }

    fun advanceConnectInterpolationForTick() {
        val current = _playbackState.value
        if (!current.isPlaying || current.durationMs <= 0L) return
        val elapsed = (elapsedRealtimeMs() - anchorElapsedMs).coerceAtLeast(0L)
        val interpolated = (anchorPositionMs + (elapsed * current.playbackSpeed).toLong())
            .coerceIn(0L, current.durationMs)
        val updated = current.copy(positionMs = interpolated)
        _playbackState.value = updated
        preloadCoordinator.onPlaybackProgress(updated, _queue.value)
    }

    private suspend fun applyUpdatedState(newState: UnifiedPlaybackState) {
        anchorPositionMs = newState.positionMs
        anchorElapsedMs = elapsedRealtimeMs()

        val color = if (newState.artworkUrl != _playbackState.value.artworkUrl && newState.artworkUrl != null) {
            paletteExtractor?.extractDominantColor(newState.artworkUrl) ?: DEFAULT_DOMINANT_COLOR
        } else {
            newState.dominantColorArgb
        }
        val liked = if (newState.itemUri.isNotBlank() && newState.itemUri != _playbackState.value.itemUri) {
            dao.isTrackLiked(newState.itemUri)
        } else {
            newState.isSavedInLibrary
        }

        val enriched = newState.copy(dominantColorArgb = color, isSavedInLibrary = liked)
        _playbackState.value = enriched

        if (enriched.isRemoteConnect != lastRemoteSessionFlag) {
            lastRemoteSessionFlag = enriched.isRemoteConnect
            onRemoteSessionChanged(lastRemoteSessionFlag)
        }

        preloadCoordinator.onPlaybackProgress(enriched, _queue.value)
        persistSnapshot(enriched)
    }

    private suspend fun persistSnapshot(state: UnifiedPlaybackState) {
        if (state.itemUri.isBlank()) return
        dao.upsertPlaybackSnapshot(
            PlaybackSnapshotEntity(
                itemUri = state.itemUri,
                title = state.title,
                subtitle = state.subtitle,
                albumName = state.albumName,
                artworkUrl = state.artworkUrl,
                contextUri = state.contextUri,
                isEpisodeOrChapter = state.isEpisodeOrChapter,
                isPlaying = state.isPlaying,
                positionMs = state.positionMs,
                durationMs = state.durationMs,
                playbackSpeed = state.playbackSpeed,
                shuffleEnabled = state.shuffleEnabled,
                repeatMode = state.repeatMode.name,
                isSavedInLibrary = state.isSavedInLibrary,
                activeDeviceId = state.activeDevice?.id,
                activeDeviceName = state.activeDevice?.name,
                activeDeviceType = state.activeDevice?.type,
                dominantColorArgb = state.dominantColorArgb,
            ),
        )
        if (state.isEpisodeOrChapter) {
            val finished = state.durationMs > 0L && state.positionMs >= state.durationMs - 5_000L
            if (state.itemUri.startsWith("spotify:episode:")) {
                dao.updateEpisodeProgress(state.itemUri, state.positionMs, finished)
            } else if (state.itemUri.startsWith("spotify:chapter:")) {
                dao.updateChapterProgress(state.itemUri, state.positionMs, finished)
            }
        }
    }

    private suspend fun isDemoMode(): Boolean = tokenStore?.loadTokenBundle()?.isDemoMode == true

    private suspend fun ensureClientIdLoaded() {
        tokenStore?.loadTokenBundle()?.clientId?.takeIf { it.isNotBlank() }?.let { cid ->
            localTransport.setClientId(cid)
        }
    }

    private suspend fun buildOptimisticState(
        uri: String,
        contextUri: String?,
        playing: Boolean,
    ): UnifiedPlaybackState {
        val current = _playbackState.value
        val track = contextUri?.let { ctx -> dao.getTracksForContextOnce(ctx).firstOrNull { it.uri == uri } }
            ?: dao.findTrackByUriOnce(uri)
        if (track != null) {
            return current.copy(
                itemUri = track.uri,
                title = track.title,
                subtitle = track.artistName,
                albumName = track.albumName,
                artworkUrl = track.artworkUrl,
                contextUri = contextUri ?: current.contextUri,
                isEpisodeOrChapter = track.uri.startsWith("spotify:episode:") || track.uri.startsWith("spotify:chapter:"),
                isPlaying = playing,
                positionMs = 0L,
                durationMs = track.durationMs,
                isSavedInLibrary = track.isLiked,
                dominantColorArgb = track.dominantColorArgb,
            )
        }

        val episode = dao.getEpisode(uri)
        if (episode != null) {
            return current.copy(
                itemUri = episode.uri,
                title = episode.title,
                subtitle = episode.showName,
                albumName = episode.showName,
                artworkUrl = episode.artworkUrl,
                contextUri = contextUri ?: episode.showUri,
                isEpisodeOrChapter = true,
                isPlaying = playing,
                positionMs = episode.resumePositionMs,
                durationMs = episode.durationMs,
            )
        }

        val chapter = dao.getChapter(uri)
        if (chapter != null) {
            return current.copy(
                itemUri = chapter.uri,
                title = chapter.title,
                subtitle = "Chapitre ${chapter.chapterNumber}",
                artworkUrl = chapter.artworkUrl,
                contextUri = contextUri ?: chapter.audiobookUri,
                isEpisodeOrChapter = true,
                isPlaying = playing,
                positionMs = chapter.resumePositionMs,
                durationMs = chapter.durationMs,
            )
        }

        val playlist = contextUri?.let { dao.getPlaylist(it) }
        val album = contextUri?.let { dao.getAlbum(it) }
        return current.copy(
            itemUri = uri,
            title = playlist?.name ?: album?.name ?: current.title.ifBlank { "Lecture en cours" },
            subtitle = playlist?.ownerName ?: album?.artistName ?: current.subtitle,
            artworkUrl = playlist?.artworkUrl ?: album?.artworkUrl ?: current.artworkUrl,
            contextUri = contextUri ?: current.contextUri,
            isEpisodeOrChapter = uri.startsWith("spotify:episode:") || uri.startsWith("spotify:chapter:"),
            isPlaying = playing,
            positionMs = 0L,
            dominantColorArgb = playlist?.dominantColorArgb ?: album?.dominantColorArgb ?: current.dominantColorArgb,
        )
    }

    override suspend fun playUri(uri: String, contextUri: String?, offsetIndex: Int?) {
        _lastErrorMessage.value = null
        if (isDemoMode()) {
            val optimistic = buildOptimisticState(uri, contextUri, playing = true)
            applyUpdatedState(optimistic)
            demoSynthesizer?.start(uri)
            return
        }

        ensureClientIdLoaded()
        val current = _playbackState.value
        val contextTracks = contextUri?.let { dao.getTracksForContextOnce(it) }.orEmpty()
        val fallbackTrackUris = contextTracks.map { it.uri }

        var succeeded = false
        if (current.isRemoteConnect) {
            val remoteRes = connectManager.playOnConnect(
                deviceId = current.activeDevice?.id,
                uri = uri,
                contextUri = contextUri,
                offsetIndex = offsetIndex,
                fallbackTrackUris = fallbackTrackUris,
            )
            succeeded = remoteRes.isSuccess
            if (!succeeded) {
                _lastErrorMessage.value = remoteRes.exceptionOrNull()?.message
                    ?: "Lecture impossible sur l'appareil Connect"
            } else {
                syncPlaybackFromConnectOnce()
            }
        } else {
            val resolvedIndex = if (contextUri != null && offsetIndex == null) {
                contextTracks.indexOfFirst { it.uri == uri }.takeIf { it >= 0 }
            } else {
                offsetIndex
            }
            val localRes = localTransport.playUri(uri, contextUri, resolvedIndex)
            if (localRes.isSuccess) {
                succeeded = true
            } else {
                val restRes = connectManager.playOnConnect(
                    deviceId = null,
                    uri = uri,
                    contextUri = contextUri,
                    offsetIndex = resolvedIndex,
                    fallbackTrackUris = fallbackTrackUris,
                )
                succeeded = restRes.isSuccess
                if (!succeeded) {
                    _lastErrorMessage.value =
                        "Spotify n'a pas pu démarrer la lecture. Ouvrez Spotify ou touchez votre profil → Connecter Spotify."
                }
            }
        }

        if (succeeded) {
            val optimistic = buildOptimisticState(uri, contextUri, playing = true)
            applyUpdatedState(optimistic)
        }
    }

    override suspend fun toggleContextPlayback(contextUri: String) {
        val current = _playbackState.value
        val tracks = dao.getTracksForContextOnce(contextUri)
        val isSameContext = current.contextUri == contextUri ||
            (tracks.isNotEmpty() && tracks.any { it.uri == current.itemUri })

        if (isSameContext && current.itemUri.isNotBlank()) {
            togglePlayPause()
        } else {
            val firstTrack = tracks.firstOrNull()
            if (firstTrack != null) {
                playUri(firstTrack.uri, contextUri, 0)
            } else {
                playUri(contextUri, contextUri, null)
            }
        }
    }

    override suspend fun shufflePlayContext(contextUri: String) {
        setShuffle(true)
        val tracks = dao.getTracksForContextOnce(contextUri)
        if (tracks.isNotEmpty()) {
            val idx = (0 until tracks.size).random()
            playUri(tracks[idx].uri, contextUri, idx)
        } else {
            playUri(contextUri, contextUri, null)
        }
    }

    override suspend fun togglePlayPause() {
        val current = _playbackState.value
        val nextPlaying = !current.isPlaying
        if (isDemoMode()) {
            if (nextPlaying) demoSynthesizer?.start(current.itemUri) else demoSynthesizer?.stop()
            applyUpdatedState(current.copy(isPlaying = nextPlaying))
            return
        }

        ensureClientIdLoaded()
        val result = if (current.isRemoteConnect) {
            val deviceId = current.activeDevice?.id
            if (nextPlaying) connectManager.playOnConnect(deviceId, null, null, null)
            else connectManager.pause(deviceId)
        } else {
            val localRes = if (nextPlaying) localTransport.resume() else localTransport.pause()
            if (localRes.isFailure) {
                if (nextPlaying) connectManager.playOnConnect(null, null, null, null)
                else connectManager.pause(null)
            } else {
                localRes
            }
        }

        if (result.isSuccess) {
            _lastErrorMessage.value = null
            applyUpdatedState(current.copy(isPlaying = nextPlaying))
        } else {
            _lastErrorMessage.value = "Impossible de basculer la lecture Spotify."
        }
    }

    override suspend fun skipNext() {
        val current = _playbackState.value
        if (isDemoMode()) {
            val ctx = current.contextUri ?: return
            val tracks = dao.getTracksForContextOnce(ctx)
            if (tracks.isNotEmpty()) {
                val curIdx = tracks.indexOfFirst { it.uri == current.itemUri }
                val nextIdx = (curIdx + 1) % tracks.size
                playUri(tracks[nextIdx].uri, ctx, nextIdx)
            }
            return
        }
        if (current.isRemoteConnect) {
            connectManager.next(current.activeDevice?.id)
            syncPlaybackFromConnectOnce()
        } else {
            val r = localTransport.skipNext()
            if (r.isFailure) connectManager.next(null)
        }
    }

    override suspend fun skipPrevious() {
        val current = _playbackState.value
        if (isDemoMode()) {
            val ctx = current.contextUri ?: return
            val tracks = dao.getTracksForContextOnce(ctx)
            if (tracks.isNotEmpty()) {
                val curIdx = tracks.indexOfFirst { it.uri == current.itemUri }
                val prevIdx = if (curIdx <= 0) tracks.lastIndex else curIdx - 1
                playUri(tracks[prevIdx].uri, ctx, prevIdx)
            }
            return
        }
        if (current.isRemoteConnect) {
            connectManager.previous(current.activeDevice?.id)
            syncPlaybackFromConnectOnce()
        } else {
            val r = localTransport.skipPrevious()
            if (r.isFailure) connectManager.previous(null)
        }
    }

    override suspend fun seekTo(positionMs: Long) {
        val current = _playbackState.value
        val clamped = positionMs.coerceIn(0L, current.durationMs.coerceAtLeast(positionMs))
        if (!isDemoMode()) {
            if (current.isRemoteConnect) {
                connectManager.seekTo(clamped, current.activeDevice?.id)
            } else {
                val r = localTransport.seekTo(clamped)
                if (r.isFailure) connectManager.seekTo(clamped, null)
            }
        }
        applyUpdatedState(current.copy(positionMs = clamped))
    }

    override suspend fun setShuffle(enabled: Boolean) {
        val current = _playbackState.value
        if (!isDemoMode()) {
            if (current.isRemoteConnect) {
                connectManager.setShuffle(enabled, current.activeDevice?.id)
            } else {
                val r = localTransport.setShuffle(enabled)
                if (r.isFailure) connectManager.setShuffle(enabled, null)
            }
        }
        applyUpdatedState(current.copy(shuffleEnabled = enabled))
    }

    override suspend fun setRepeatMode(mode: RepeatMode) {
        val current = _playbackState.value
        if (!isDemoMode()) {
            if (current.isRemoteConnect) {
                connectManager.setRepeat(mode, current.activeDevice?.id)
            } else {
                val r = localTransport.setRepeat(mode)
                if (r.isFailure) connectManager.setRepeat(mode, null)
            }
        }
        applyUpdatedState(current.copy(repeatMode = mode))
    }

    override suspend fun setPodcastSpeed(multiplier: Float) {
        val current = _playbackState.value
        if (!isDemoMode() && !current.isRemoteConnect) {
            localTransport.setPodcastSpeed(multiplier)
        }
        applyUpdatedState(current.copy(playbackSpeed = multiplier))
    }

    override suspend fun enqueue(uri: String) {
        val current = _playbackState.value
        if (!isDemoMode()) {
            if (current.isRemoteConnect) {
                connectManager.addToQueue(uri)
            } else {
                val r = localTransport.enqueue(uri)
                if (r.isFailure) connectManager.addToQueue(uri)
            }
            refreshQueue()
        }
    }

    override suspend fun transferPlayback(deviceId: String, playImmediately: Boolean) {
        val result = connectManager.transferPlayback(deviceId, playImmediately)
        if (result.isSuccess) {
            val targetDevice = _connectedDevices.value.firstOrNull { it.id == deviceId }?.copy(isActive = true)
            _connectedDevices.value = _connectedDevices.value.map {
                it.copy(isActive = it.id == deviceId)
            }
            if (targetDevice != null) {
                applyUpdatedState(_playbackState.value.copy(activeDevice = targetDevice, isPlaying = playImmediately))
            }
            syncPlaybackFromConnectOnce()
        }
    }

    override suspend fun setRemoteVolume(volumePercent: Int, deviceId: String?) {
        val targetId = deviceId ?: _playbackState.value.activeDevice?.id
        val clamped = volumePercent.coerceIn(0, 100)
        connectManager.setVolume(clamped, targetId)
        _connectedDevices.value = _connectedDevices.value.map {
            if (it.id == targetId || it.isActive) it.copy(volumePercent = clamped) else it
        }
        _playbackState.value.activeDevice?.let { active ->
            if (targetId == null || active.id == targetId) {
                _playbackState.value = _playbackState.value.copy(
                    activeDevice = active.copy(volumePercent = clamped),
                )
            }
        }
    }

    override suspend fun refreshDevices() {
        connectManager.fetchDevices().onSuccess { devices ->
            _connectedDevices.value = devices
            val active = devices.firstOrNull { it.isActive }
            if (active != null && active != _playbackState.value.activeDevice) {
                applyUpdatedState(_playbackState.value.copy(activeDevice = active))
            }
        }
    }

    override suspend fun refreshQueue() {
        connectManager.fetchQueue().onSuccess { items ->
            _queue.value = items
        }
    }

    override suspend fun toggleLikeCurrentTrack() {
        val current = _playbackState.value
        if (current.itemUri.isBlank()) return
        syncEngine.toggleLibraryItem(current.itemUri, current.isSavedInLibrary).onSuccess { newSaved ->
            applyUpdatedState(current.copy(isSavedInLibrary = newSaved))
        }
    }
}
