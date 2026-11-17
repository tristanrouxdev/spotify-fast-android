package com.tristan.spotifyfast.player

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Looper
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Media3 MediaSessionService activated strictly for remote Spotify Connect sessions.
 * Remains stopped during local `AppRemoteBridge` playback so `com.spotify.music` keeps
 * the sole Android system media notification and Bluetooth AVRCP ownership (zero duplicates).
 */
@UnstableApi
class PlaybackSessionService : MediaSessionService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var mediaSession: MediaSession? = null
    private var remotePlayer: RemoteConnectMirrorPlayer? = null
    private var collectJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        val controller = sessionController ?: return
        val player = RemoteConnectMirrorPlayer(controller, serviceScope)
        remotePlayer = player
        mediaSession = MediaSession.Builder(this, player)
            .setId("spotify_fast_connect_session")
            .build()

        collectJob = serviceScope.launch {
            controller.playbackState.collect { unified ->
                if (!unified.isRemoteConnect) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                } else {
                    player.updateFromUnifiedState(unified)
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_DEACTIVATE) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onDestroy() {
        collectJob?.cancel()
        mediaSession?.release()
        mediaSession = null
        remotePlayer = null
        serviceScope.cancel()
        super.onDestroy()
    }

    private class RemoteConnectMirrorPlayer(
        private val controller: PlaybackController,
        private val scope: CoroutineScope,
    ) : SimpleBasePlayer(Looper.getMainLooper()) {

        private var latestState: UnifiedPlaybackState = UnifiedPlaybackState()

        fun updateFromUnifiedState(newState: UnifiedPlaybackState) {
            latestState = newState
            invalidateState()
        }

        override fun getState(): State {
            val item = MediaItem.Builder()
                .setMediaId(latestState.itemUri.ifBlank { "connect_idle" })
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(latestState.title.ifBlank { "Spotify Connect" })
                        .setArtist(latestState.subtitle)
                        .setAlbumTitle(latestState.albumName)
                        .setArtworkUri(latestState.artworkUrl?.let(Uri::parse))
                        .build(),
                )
                .build()

            val itemData = MediaItemData.Builder(latestState.itemUri.ifBlank { "connect_idle" })
                .setMediaItem(item)
                .setDurationUs(latestState.durationMs.coerceAtLeast(0L) * 1000L)
                .build()

            val commands = Player.Commands.Builder()
                .addAll(
                    Player.COMMAND_PLAY_PAUSE,
                    Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,
                    Player.COMMAND_SEEK_TO_NEXT,
                    Player.COMMAND_SEEK_TO_PREVIOUS,
                    Player.COMMAND_GET_CURRENT_MEDIA_ITEM,
                    Player.COMMAND_GET_METADATA,
                )
                .build()

            return State.Builder()
                .setAvailableCommands(commands)
                .setPlayWhenReady(latestState.isPlaying, PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
                .setPlaybackState(if (latestState.itemUri.isBlank()) STATE_IDLE else STATE_READY)
                .setPlaylist(listOf(itemData))
                .setCurrentMediaItemIndex(0)
                .setContentPositionMs(latestState.positionMs.coerceAtLeast(0L))
                .build()
        }

        override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
            scope.launch {
                if (playWhenReady != latestState.isPlaying) {
                    controller.togglePlayPause()
                }
            }
            return Futures.immediateVoidFuture()
        }

        override fun handleSeek(
            mediaItemIndex: Int,
            positionMs: Long,
            seekCommand: Int,
        ): ListenableFuture<*> {
            scope.launch {
                when (seekCommand) {
                    COMMAND_SEEK_TO_NEXT -> controller.skipNext()
                    COMMAND_SEEK_TO_PREVIOUS -> controller.skipPrevious()
                    else -> controller.seekTo(positionMs)
                }
            }
            return Futures.immediateVoidFuture()
        }
    }

    companion object {
        private const val ACTION_DEACTIVATE = "com.tristan.spotifyfast.DEACTIVATE_REMOTE_SESSION"

        @Volatile
        var sessionController: PlaybackController? = null

        fun syncRemoteSession(context: Context, isRemoteActive: Boolean) {
            val intent = Intent(context, PlaybackSessionService::class.java)
            if (isRemoteActive) {
                ContextCompat.startForegroundService(context, intent)
            } else {
                intent.action = ACTION_DEACTIVATE
                runCatching { context.stopService(Intent(context, PlaybackSessionService::class.java)) }
            }
        }
    }
}
