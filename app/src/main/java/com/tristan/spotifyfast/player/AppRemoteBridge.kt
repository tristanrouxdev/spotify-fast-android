package com.tristan.spotifyfast.player

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import android.view.KeyEvent
import com.spotify.android.appremote.api.ConnectionParams
import com.spotify.android.appremote.api.Connector
import com.spotify.android.appremote.api.SpotifyAppRemote
import com.spotify.android.appremote.api.error.AuthenticationFailedException
import com.spotify.android.appremote.api.error.SpotifyDisconnectedException
import com.spotify.android.appremote.api.error.UserNotAuthorizedException
import com.spotify.protocol.client.CallResult
import com.spotify.protocol.client.Subscription
import com.spotify.protocol.types.PlaybackSpeed
import com.spotify.protocol.types.PlayerContext
import com.spotify.protocol.types.PlayerState
import com.spotify.protocol.types.Repeat
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Pre-warmed Binder IPC bridge to `com.spotify.music` via Spotify App Remote SDK 0.8.0.
 * Never opens the heavy official Spotify UI; supports automatic exponential reconnection
 * and replays a failed command on `SpotifyDisconnectedException`.
 */
class AppRemoteBridge(
    private val context: Context,
    private val redirectUri: String = "http://127.0.0.1:8888/callback",
    private val mainDispatcher: CoroutineDispatcher = Dispatchers.Main,
) : LocalPlaybackTransport {

    private val scope = CoroutineScope(SupervisorJob() + mainDispatcher)
    private val connectMutex = Mutex()

    private var currentClientId: String? = null
    private var appRemote: SpotifyAppRemote? = null
    private var playerStateSub: Subscription<PlayerState>? = null
    private var playerContextSub: Subscription<PlayerContext>? = null
    private var currentContextUri: String? = null
    private var reconnectAttempt = 0

    private val _isConnected = MutableStateFlow(false)
    override val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _localState = MutableStateFlow<UnifiedPlaybackState?>(null)
    override val localState: StateFlow<UnifiedPlaybackState?> = _localState.asStateFlow()

    private val _lastConnectionError = MutableStateFlow<String?>(null)
    override val lastConnectionError: StateFlow<String?> = _lastConnectionError.asStateFlow()

    override fun isSpotifyInstalled(): Boolean =
        runCatching { SpotifyAppRemote.isSpotifyInstalled(context) }.getOrDefault(false)

    override fun setClientId(clientId: String) {
        if (clientId.isNotBlank()) {
            currentClientId = clientId.trim()
        }
    }

    override fun prewarm(clientId: String) {
        if (clientId.isBlank() || !isSpotifyInstalled()) return
        currentClientId = clientId.trim()
        scope.launch {
            ensureConnected()
        }
    }

    private fun logInfo(msg: String) {
        runCatching { Log.i(LOG_TAG, msg) }
    }

    private fun logError(msg: String, err: Throwable? = null) {
        runCatching {
            if (err != null) Log.e(LOG_TAG, msg, err) else Log.e(LOG_TAG, msg)
        }
    }

    override fun wakeSpotifyApp() {
        wakeSpotifyMediaService()
    }

    override fun sendMediaKeyToSpotify(keyCode: Int) {
        logInfo("Sending MEDIA_BUTTON keyCode=$keyCode to $SPOTIFY_PACKAGE")
        val target = ComponentName(SPOTIFY_PACKAGE, SPOTIFY_MEDIA_BUTTON_RECEIVER)
        runCatching {
            val down = Intent(Intent.ACTION_MEDIA_BUTTON).apply {
                component = target
                putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
            }
            val up = Intent(Intent.ACTION_MEDIA_BUTTON).apply {
                component = target
                putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(KeyEvent.ACTION_UP, keyCode))
            }
            context.sendBroadcast(down)
            context.sendBroadcast(up)
        }.onFailure { err ->
            logError("sendMediaKeyToSpotify($keyCode) failed", err)
        }
    }

    override suspend fun reconnectNow(clientId: String): Result<Unit> {
        setClientId(clientId)
        logInfo("Manual AppRemote reconnect requested")
        wakeSpotifyMediaService()
        delay(300L)
        val remote = ensureConnected()
        return if (remote != null) {
            logInfo("Manual AppRemote reconnect succeeded")
            Result.success(Unit)
        } else {
            val errMsg = _lastConnectionError.value
                ?: "Impossible de se connecter à Spotify. Vérifiez l'empreinte SHA-1 dans votre Dashboard Spotify Developer."
            logError("Manual AppRemote reconnect failed: $errMsg")
            Result.failure(IllegalStateException(errMsg))
        }
    }

    private fun wakeSpotifyMediaService() {
        logInfo("Waking $SPOTIFY_PACKAGE receivers & protocol service")
        runCatching {
            val prepareIntent = Intent("com.spotify.music.ACTION_PREPARE").apply {
                component = ComponentName(SPOTIFY_PACKAGE, SPOTIFY_ALARM_RECEIVER)
            }
            context.sendBroadcast(prepareIntent)
        }.onFailure { logError("ACTION_PREPARE broadcast failed", it) }

        runCatching {
            val initIntent = Intent("com.waze.sdk.audio.ACTION_INIT").apply {
                component = ComponentName(SPOTIFY_PACKAGE, SPOTIFY_WAZE_WAKEUP_RECEIVER)
            }
            context.sendBroadcast(initIntent)
        }.onFailure { logError("ACTION_INIT broadcast failed", it) }

        runCatching {
            val svcIntent = Intent("com.spotify.mobile.appprotocol.action.START_APP_PROTOCOL_SERVICE").apply {
                setPackage(SPOTIFY_PACKAGE)
            }
            val conn = object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                    runCatching { context.applicationContext.unbindService(this) }
                }

                override fun onServiceDisconnected(name: ComponentName?) {}
            }
            val bound = context.applicationContext.bindService(svcIntent, conn, Context.BIND_AUTO_CREATE)
            if (bound) {
                scope.launch {
                    delay(1_500L)
                    runCatching { context.applicationContext.unbindService(conn) }
                }
            }
        }.onFailure { logError("Brief START_APP_PROTOCOL_SERVICE bind failed", it) }
    }

    private suspend fun ensureConnected(): SpotifyAppRemote? = connectMutex.withLock {
        val existing = appRemote
        if (existing != null && existing.isConnected) {
            return@withLock existing
        }
        val clientId = currentClientId?.takeIf { it.isNotBlank() } ?: return@withLock null
        if (!isSpotifyInstalled()) {
            val msg = "Application Spotify officielle introuvable sur cet appareil."
            _lastConnectionError.value = msg
            logError(msg)
            return@withLock null
        }

        wakeSpotifyMediaService()

        val params = ConnectionParams.Builder(clientId)
            .setRedirectUri(redirectUri)
            .showAuthView(true)
            .build()

        return@withLock runCatching {
            val remote = suspendCancellableCoroutine<SpotifyAppRemote> { cont ->
                SpotifyAppRemote.connect(
                    context,
                    params,
                    object : Connector.ConnectionListener {
                        override fun onConnected(spotifyAppRemote: SpotifyAppRemote) {
                            if (cont.isActive) cont.resume(spotifyAppRemote)
                        }

                        override fun onFailure(throwable: Throwable) {
                            if (cont.isActive) cont.resumeWithException(throwable)
                        }
                    },
                )
            }
            appRemote = remote
            _isConnected.value = true
            _lastConnectionError.value = null
            reconnectAttempt = 0
            logInfo("AppRemote connected successfully")
            subscribeToStreams(remote)
            remote
        }.onFailure { err ->
            _isConnected.value = false
            val formatted = "${err.javaClass.simpleName}: ${err.message ?: err.toString()}"
            _lastConnectionError.value = formatted
            logError("AppRemote connect failed: $formatted", err)
            if (err !is UserNotAuthorizedException && err !is AuthenticationFailedException) {
                scheduleExponentialReconnect()
            } else {
                logInfo("Stopping auto-reconnect loop after auth error (${err.javaClass.simpleName})")
            }
        }.getOrNull()
    }

    private fun subscribeToStreams(remote: SpotifyAppRemote) {
        playerStateSub?.cancel()
        playerContextSub?.cancel()

        val contextSub = remote.playerApi.subscribeToPlayerContext()
        contextSub.setEventCallback { ctx ->
            currentContextUri = ctx.uri
            _localState.value = _localState.value?.copy(contextUri = ctx.uri)
        }
        playerContextSub = contextSub

        val stateSub = remote.playerApi.subscribeToPlayerState()
        stateSub.setEventCallback { ps ->
            val track = ps.track ?: return@setEventCallback
            val artworkHttps = spotifyImageUriToHttps(track.imageUri?.raw)
            val repeat = when (ps.playbackOptions?.repeatMode) {
                Repeat.ONE -> RepeatMode.TRACK
                Repeat.ALL -> RepeatMode.CONTEXT
                else -> RepeatMode.OFF
            }
            val isEp = track.isEpisode || track.isPodcast ||
                track.uri.startsWith("spotify:episode:") ||
                track.uri.startsWith("spotify:chapter:")
            val subtitle = track.artists?.joinToString(", ") { it.name }
                ?.takeIf { it.isNotBlank() }
                ?: track.artist?.name
                ?: track.album?.name
                ?: ""

            _localState.value = UnifiedPlaybackState(
                itemUri = track.uri,
                title = track.name.orEmpty(),
                subtitle = subtitle,
                albumName = track.album?.name.orEmpty(),
                artworkUrl = artworkHttps,
                contextUri = currentContextUri,
                isEpisodeOrChapter = isEp,
                isPlaying = !ps.isPaused,
                positionMs = ps.playbackPosition.coerceAtLeast(0L),
                durationMs = track.duration.coerceAtLeast(0L),
                playbackSpeed = ps.playbackSpeed.takeIf { it > 0f } ?: 1.0f,
                shuffleEnabled = ps.playbackOptions?.isShuffling ?: false,
                repeatMode = repeat,
            )
        }.setErrorCallback { err ->
            logError("AppRemote playerState subscription error", err)
            if (err is SpotifyDisconnectedException) {
                _isConnected.value = false
                appRemote = null
                scheduleExponentialReconnect()
            }
        }
        playerStateSub = stateSub
    }

    private fun scheduleExponentialReconnect() {
        val delayMs = (500L shl reconnectAttempt.coerceAtMost(5)).coerceAtMost(16_000L)
        reconnectAttempt++
        scope.launch {
            delay(delayMs)
            ensureConnected()
        }
    }

    private suspend fun <T> executeWithReconnect(block: (SpotifyAppRemote) -> CallResult<T>): Result<Unit> {
        val firstRemote = ensureConnected()
            ?: return Result.failure(IllegalStateException("Spotify App Remote non connecté"))

        val firstAttempt = runCatching { awaitCallResult(block(firstRemote)) }
        if (firstAttempt.isSuccess) return Result.success(Unit)

        val err = firstAttempt.exceptionOrNull()
        logError("AppRemote command failed on first attempt", err)
        if (err is SpotifyDisconnectedException || !firstRemote.isConnected) {
            appRemote = null
            _isConnected.value = false
            val reconnected = ensureConnected()
                ?: return Result.failure(err ?: IllegalStateException("Reconnexion impossible"))
            return runCatching {
                awaitCallResult(block(reconnected))
                Unit
            }.onFailure { retryErr ->
                logError("AppRemote command retry failed", retryErr)
            }
        }
        return Result.failure(err ?: IllegalStateException("Erreur App Remote"))
    }

    private suspend fun <T> awaitCallResult(callResult: CallResult<T>): T =
        suspendCancellableCoroutine { cont ->
            callResult.setResultCallback { value ->
                if (cont.isActive) cont.resume(value)
            }.setErrorCallback { error ->
                if (cont.isActive) cont.resumeWithException(error)
            }
        }

    override suspend fun playUri(uri: String, contextUri: String?, offsetIndex: Int?): Result<Unit> {
        val isPlayableContext = !contextUri.isNullOrBlank() &&
            (contextUri.startsWith("spotify:playlist:") ||
                contextUri.startsWith("spotify:album:") ||
                contextUri.startsWith("spotify:show:"))

        return executeWithReconnect { remote ->
            when {
                uri == contextUri && isPlayableContext -> remote.playerApi.play(contextUri)
                isPlayableContext && offsetIndex != null -> remote.playerApi.skipToIndex(contextUri!!, offsetIndex)
                else -> remote.playerApi.play(uri)
            }
        }
    }

    override suspend fun resume(): Result<Unit> = executeWithReconnect { it.playerApi.resume() }

    override suspend fun pause(): Result<Unit> = executeWithReconnect { it.playerApi.pause() }

    override suspend fun skipNext(): Result<Unit> = executeWithReconnect { it.playerApi.skipNext() }

    override suspend fun skipPrevious(): Result<Unit> = executeWithReconnect { it.playerApi.skipPrevious() }

    override suspend fun seekTo(positionMs: Long): Result<Unit> =
        executeWithReconnect { it.playerApi.seekTo(positionMs.coerceAtLeast(0L)) }

    override suspend fun setShuffle(enabled: Boolean): Result<Unit> =
        executeWithReconnect { it.playerApi.setShuffle(enabled) }

    override suspend fun setRepeat(mode: RepeatMode): Result<Unit> {
        val sdkMode = when (mode) {
            RepeatMode.OFF -> Repeat.OFF
            RepeatMode.CONTEXT -> Repeat.ALL
            RepeatMode.TRACK -> Repeat.ONE
        }
        return executeWithReconnect { it.playerApi.setRepeat(sdkMode) }
    }

    override suspend fun setPodcastSpeed(multiplier: Float): Result<Unit> {
        val speed = mapPodcastSpeed(multiplier)
        return executeWithReconnect { it.playerApi.setPodcastPlaybackSpeed(speed) }
    }

    override suspend fun enqueue(uri: String): Result<Unit> =
        executeWithReconnect { it.playerApi.queue(uri) }

    override fun disconnect() {
        playerStateSub?.cancel()
        playerContextSub?.cancel()
        playerStateSub = null
        playerContextSub = null
        appRemote?.let { SpotifyAppRemote.disconnect(it) }
        appRemote = null
        _isConnected.value = false
    }

    companion object {
        private const val LOG_TAG = "SpotifyFast"
        private const val SPOTIFY_PACKAGE = "com.spotify.music"
        private const val SPOTIFY_MEDIA_BUTTON_RECEIVER =
            "com.spotify.mediasession.mediasession.receiver.MediaButtonReceiver"
        private const val SPOTIFY_ALARM_RECEIVER =
            "com.spotify.music.alarmlauncher.SpotifyAlarmLauncherReceiver"
        private const val SPOTIFY_WAZE_WAKEUP_RECEIVER =
            "com.spotify.carmobile.wazesdk.navigation.WazeWakeUpReceiver"

        fun buildNativeSsoAuthIntent(
            context: Context,
            clientId: String,
            redirectUri: String = "http://127.0.0.1:8888/callback",
        ): Intent? {
            val cleanClientId = clientId.trim()
            if (cleanClientId.isBlank()) return null
            val intent = Intent("com.spotify.sso.action.START_AUTH_FLOW").apply {
                setPackage(SPOTIFY_PACKAGE)
                putExtra("VERSION", 1)
                putExtra("CLIENT_ID", cleanClientId)
                putExtra("REDIRECT_URI", redirectUri)
                putExtra("RESPONSE_TYPE", "token")
                putExtra(
                    "SCOPES",
                    arrayOf(
                        "app-remote-control",
                        "streaming",
                        "user-read-playback-state",
                        "user-modify-playback-state",
                        "user-read-currently-playing",
                    ),
                )
                putExtra("UTM_SOURCE", "spotify-sdk")
                putExtra("UTM_MEDIUM", "android-sdk")
                putExtra("UTM_CAMPAIGN", "android-sdk")
            }
            return if (intent.resolveActivity(context.packageManager) != null) intent else null
        }

        fun spotifyImageUriToHttps(raw: String?): String? {
            if (raw.isNullOrBlank()) return null
            return if (raw.startsWith("spotify:image:")) {
                "https://i.scdn.co/image/${raw.removePrefix("spotify:image:")}"
            } else {
                raw
            }
        }

        fun mapPodcastSpeed(multiplier: Float): PlaybackSpeed.PodcastPlaybackSpeed {
            return when {
                multiplier <= 0.65f -> PlaybackSpeed.PodcastPlaybackSpeed.PLAYBACK_SPEED_50
                multiplier <= 0.90f -> PlaybackSpeed.PodcastPlaybackSpeed.PLAYBACK_SPEED_80
                multiplier <= 1.10f -> PlaybackSpeed.PodcastPlaybackSpeed.PLAYBACK_SPEED_100
                multiplier <= 1.35f -> PlaybackSpeed.PodcastPlaybackSpeed.PLAYBACK_SPEED_120
                multiplier <= 1.75f -> PlaybackSpeed.PodcastPlaybackSpeed.PLAYBACK_SPEED_150
                multiplier <= 2.50f -> PlaybackSpeed.PodcastPlaybackSpeed.PLAYBACK_SPEED_200
                else -> PlaybackSpeed.PodcastPlaybackSpeed.PLAYBACK_SPEED_300
            }
        }
    }
}
