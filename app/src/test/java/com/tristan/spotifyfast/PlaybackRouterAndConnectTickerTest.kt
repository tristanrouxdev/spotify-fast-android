package com.tristan.spotifyfast

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.tristan.spotifyfast.auth.EncryptedTokenStore
import com.tristan.spotifyfast.data.local.SpotifyFastDatabase
import com.tristan.spotifyfast.data.remote.EmptyBodyConverterFactory
import com.tristan.spotifyfast.data.remote.SpotifyWebApi
import com.tristan.spotifyfast.data.sync.OfflineFirstSyncEngine
import com.tristan.spotifyfast.player.ConnectDevice
import com.tristan.spotifyfast.player.LocalPlaybackTransport
import com.tristan.spotifyfast.player.PlaybackRouter
import com.tristan.spotifyfast.player.PreloadCoordinator
import com.tristan.spotifyfast.player.QueueTrack
import com.tristan.spotifyfast.player.RepeatMode
import com.tristan.spotifyfast.player.SpotifyConnectManager
import com.tristan.spotifyfast.player.UnifiedPlaybackState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

private class FakeLocalTransport : LocalPlaybackTransport {
    private val _isConnected = MutableStateFlow(true)
    override val isConnected: StateFlow<Boolean> = _isConnected
    private val _localState = MutableStateFlow<UnifiedPlaybackState?>(null)
    override val localState: StateFlow<UnifiedPlaybackState?> = _localState
    private val _lastConnectionError = MutableStateFlow<String?>(null)
    override val lastConnectionError: StateFlow<String?> = _lastConnectionError

    val playedCalls = mutableListOf<Triple<String, String?, Int?>>()
    var speedCalls = mutableListOf<Float>()
    var resumeCount = 0
    var pauseCount = 0
    var shouldFailPlay = false
    var wakeCount = 0
    val mediaKeys = mutableListOf<Int>()

    fun emitConnectionError(msg: String?) {
        _lastConnectionError.value = msg
    }

    override fun setClientId(clientId: String) {}
    override fun prewarm(clientId: String) {}
    override fun isSpotifyInstalled(): Boolean = true
    override fun wakeSpotifyApp() { wakeCount++ }
    override fun sendMediaKeyToSpotify(keyCode: Int) { mediaKeys += keyCode }
    override suspend fun reconnectNow(clientId: String): Result<Unit> = Result.success(Unit)

    override suspend fun playUri(uri: String, contextUri: String?, offsetIndex: Int?): Result<Unit> {
        if (shouldFailPlay) {
            return Result.failure(IllegalStateException("AppRemote unauthorized"))
        }
        playedCalls += Triple(uri, contextUri, offsetIndex)
        return Result.success(Unit)
    }

    override suspend fun resume(): Result<Unit> {
        resumeCount++
        return Result.success(Unit)
    }

    override suspend fun pause(): Result<Unit> {
        pauseCount++
        return Result.success(Unit)
    }
    override suspend fun skipNext(): Result<Unit> = Result.success(Unit)
    override suspend fun skipPrevious(): Result<Unit> = Result.success(Unit)
    override suspend fun seekTo(positionMs: Long): Result<Unit> = Result.success(Unit)
    override suspend fun setShuffle(enabled: Boolean): Result<Unit> = Result.success(Unit)
    override suspend fun setRepeat(mode: RepeatMode): Result<Unit> = Result.success(Unit)

    override suspend fun setPodcastSpeed(multiplier: Float): Result<Unit> {
        speedCalls += multiplier
        return Result.success(Unit)
    }

    override suspend fun enqueue(uri: String): Result<Unit> = Result.success(Unit)
    override fun disconnect() { _isConnected.value = false }
}

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class PlaybackRouterAndConnectTickerTest {

    private lateinit var server: MockWebServer
    private lateinit var api: SpotifyWebApi
    private lateinit var db: SpotifyFastDatabase
    private lateinit var tokenStore: EncryptedTokenStore

    @Before
    fun setup() {
        server = MockWebServer()
        server.start()
        val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }
        api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(EmptyBodyConverterFactory())
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(SpotifyWebApi::class.java)

        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, SpotifyFastDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val ds = PreferenceDataStoreFactory.create {
            File(context.cacheDir, "router_test_${System.nanoTime()}.preferences_pb")
        }
        tokenStore = EncryptedTokenStore(ds)
    }

    @After
    fun tearDown() {
        db.close()
        server.shutdown()
    }

    @Test
    fun `routes local skipToIndex, switches to Connect with Media3 flag, and interpolates position`() = runTest {
        val remotePlayCount = AtomicInteger(0)
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val p = request.path.orEmpty()
                return when {
                    p.startsWith("/v1/me/player/devices") -> MockResponse().setResponseCode(200).setBody(
                        """{"devices":[{"id":"pc_1","name":"Desktop Linux","type":"Computer","is_active":false,"volume_percent":70}]}""",
                    )
                    p == "/v1/me/player" -> MockResponse().setResponseCode(204)
                    p.startsWith("/v1/me/player?") -> MockResponse().setResponseCode(200).setBody(
                        """
                        {
                          "device":{"id":"pc_1","name":"Desktop Linux","type":"Computer","is_active":true,"volume_percent":70},
                          "is_playing":true,
                          "progress_ms":10000,
                          "repeat_state":"off",
                          "shuffle_state":false,
                          "item":{"id":"t_connect","uri":"spotify:track:t_connect","name":"Connect Track","duration_ms":100000,"artists":[{"id":"a1","name":"M83"}]}
                        }
                        """.trimIndent(),
                    )
                    p.startsWith("/v1/me/player/play") -> {
                        remotePlayCount.incrementAndGet()
                        MockResponse().setResponseCode(204)
                    }
                    else -> MockResponse().setResponseCode(204)
                }
            }
        }

        val fakeLocal = FakeLocalTransport()
        val clockMs = AtomicLong(1_000L)
        val remoteSessionStates = mutableListOf<Boolean>()
        val ioDispatcher = StandardTestDispatcher(testScheduler)

        val router = PlaybackRouter(
            dao = db.spotifyDao(),
            localTransport = fakeLocal,
            connectManager = SpotifyConnectManager(api, ioDispatcher),
            syncEngine = OfflineFirstSyncEngine(db.spotifyDao(), api, tokenStore, ioDispatcher = ioDispatcher),
            onRemoteSessionChanged = { remoteSessionStates += it },
            elapsedRealtimeMs = { clockMs.get() },
            ioDispatcher = ioDispatcher,
            enableBackgroundPolling = false,
        )

        // 1) Local playback calls AppRemote skipToIndex and keeps Media3 inactive
        router.playUri("spotify:track:abc", "spotify:playlist:pl1", 2)
        assertEquals(Triple("spotify:track:abc", "spotify:playlist:pl1", 2), fakeLocal.playedCalls.single())
        assertTrue(remoteSessionStates.isEmpty())

        // 2) Transfer to remote PC activates Media3 remote session flag and polls Connect state
        router.refreshDevices()
        advanceUntilIdle()
        router.transferPlayback("pc_1", playImmediately = true)
        advanceUntilIdle()

        assertTrue(router.playbackState.value.isRemoteConnect)
        assertEquals(listOf(true), remoteSessionStates)
        assertEquals(10_000L, router.playbackState.value.positionMs)

        // 3) Advance clock by 2500ms -> Connect ticker interpolates position smoothly
        clockMs.set(3_500L)
        router.advanceConnectInterpolationForTick()
        assertEquals(12_500L, router.playbackState.value.positionMs)

        // 4) Play command while on remote routes via Spotify Connect REST
        router.playUri("spotify:track:other", "spotify:playlist:pl2", 0)
        advanceUntilIdle()
        assertEquals(1, remotePlayCount.get())
    }

    @Test
    fun `preload coordinator triggers strictly once when track passes 80 percent`() = runTest {
        val artworkHits = mutableListOf<String>()
        val lyricsHits = mutableListOf<String>()
        val queueHits = AtomicInteger(0)

        val nextTrack = QueueTrack(
            uri = "spotify:track:next_2",
            title = "Next Song",
            subtitle = "Artist",
            albumName = "Album",
            artworkUrl = "https://img.example/next.jpg",
            durationMs = 200_000L,
        )

        val coordinator = PreloadCoordinator(
            scope = this,
            onRefreshQueue = {
                queueHits.incrementAndGet()
                listOf(nextTrack)
            },
            onPrefetchArtwork = { artworkHits += it },
            onPrefetchLyrics = { lyricsHits += it.uri },
        )

        val playing = UnifiedPlaybackState(
            itemUri = "spotify:track:curr_1",
            durationMs = 100_000L,
            positionMs = 79_000L,
            isPlaying = true,
        )

        // Below 80%: nothing triggers
        coordinator.onPlaybackProgress(playing, listOf(nextTrack))
        advanceUntilIdle()
        assertEquals(0, queueHits.get())

        // At 80% (80,000 ms): triggers once
        coordinator.onPlaybackProgress(playing.copy(positionMs = 80_000L), listOf(nextTrack))
        advanceUntilIdle()
        assertEquals(1, queueHits.get())
        assertEquals(listOf("https://img.example/next.jpg"), artworkHits)
        assertEquals(listOf("spotify:track:next_2"), lyricsHits)

        // At 92% for same track: does NOT re-trigger
        coordinator.onPlaybackProgress(playing.copy(positionMs = 92_000L), listOf(nextTrack))
        advanceUntilIdle()
        assertEquals(1, queueHits.get())
    }

    @Test
    fun `toggleContextPlayback switches between play and pause and handles liked songs fallback`() = runTest {
        val restPlayBodies = mutableListOf<String>()
        var firstPlayAttempt = true
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val p = request.path.orEmpty()
                return when {
                    p.startsWith("/v1/me/player/play") -> {
                        restPlayBodies += request.body.readUtf8()
                        if (firstPlayAttempt) {
                            firstPlayAttempt = false
                            MockResponse().setResponseCode(404)
                        } else {
                            MockResponse().setResponseCode(204)
                        }
                    }
                    p.startsWith("/v1/me/player/devices") -> MockResponse().setResponseCode(200).setBody(
                        """{"devices":[{"id":"phone_1","name":"Xiaomi 11T","type":"Smartphone","is_active":false,"volume_percent":80}]}""",
                    )
                    else -> MockResponse().setResponseCode(204)
                }
            }
        }

        db.spotifyDao().upsertTracks(
            listOf(
                com.tristan.spotifyfast.data.local.TrackEntity(
                    uri = "spotify:track:pl_1",
                    id = "pl_1",
                    contextUri = "spotify:playlist:test_pl",
                    positionInContext = 0,
                    title = "Track One",
                    artistName = "Artist",
                    albumName = "Album",
                    artworkUrl = null,
                    durationMs = 180_000L,
                ),
            ),
        )

        val fakeLocal = FakeLocalTransport()
        val ioDispatcher = StandardTestDispatcher(testScheduler)
        val router = PlaybackRouter(
            dao = db.spotifyDao(),
            localTransport = fakeLocal,
            connectManager = SpotifyConnectManager(api, ioDispatcher),
            syncEngine = OfflineFirstSyncEngine(db.spotifyDao(), api, tokenStore, ioDispatcher = ioDispatcher),
            tokenStore = tokenStore,
            ioDispatcher = ioDispatcher,
            enableBackgroundPolling = false,
        )

        // First toggle starts playlist
        router.toggleContextPlayback("spotify:playlist:test_pl")
        advanceUntilIdle()
        assertTrue(router.playbackState.value.isPlaying)
        assertEquals("spotify:playlist:test_pl", router.playbackState.value.contextUri)

        // Second toggle pauses active playlist instead of restarting from index 0
        router.toggleContextPlayback("spotify:playlist:test_pl")
        advanceUntilIdle()
        assertFalse(router.playbackState.value.isPlaying)
        assertEquals(1, fakeLocal.pauseCount)

        // Third toggle resumes
        router.toggleContextPlayback("spotify:playlist:test_pl")
        advanceUntilIdle()
        assertTrue(router.playbackState.value.isPlaying)
        assertEquals(1, fakeLocal.resumeCount)

        // Connect 404 recovery & Liked Songs non-playlist URI array payload
        val connectMgr = SpotifyConnectManager(api, ioDispatcher)
        val res = connectMgr.playOnConnect(
            deviceId = null,
            uri = "spotify:track:liked_1",
            contextUri = "spotify:collection:tracks",
            offsetIndex = 0,
            fallbackTrackUris = listOf("spotify:track:liked_1", "spotify:track:liked_2"),
        )
        advanceUntilIdle()
        assertTrue(res.isSuccess)
        assertTrue(restPlayBodies.last().contains("spotify:track:liked_1"))
        assertFalse(restPlayBodies.last().contains("spotify:collection:tracks"))
    }

    @Test
    fun `local play failure wakes spotify media session, polls connect devices, and flags sso authorization`() = runTest {
        val devicePollAttempts = AtomicInteger(0)
        val targetedPhonePlay = AtomicInteger(0)
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val p = request.path.orEmpty()
                return when {
                    p.startsWith("/v1/me/player/play") -> {
                        val query = request.requestUrl?.queryParameter("device_id")
                        if (query == "phone_xiaomi") {
                            targetedPhonePlay.incrementAndGet()
                            MockResponse().setResponseCode(204)
                        } else {
                            MockResponse().setResponseCode(404)
                        }
                    }
                    p.startsWith("/v1/me/player/devices") -> {
                        val count = devicePollAttempts.incrementAndGet()
                        if (count < 2) {
                            MockResponse().setResponseCode(200).setBody("""{"devices":[]}""")
                        } else {
                            MockResponse().setResponseCode(200).setBody(
                                """{"devices":[{"id":"phone_xiaomi","name":"Xiaomi 11T","type":"Smartphone","is_active":false,"volume_percent":85}]}""",
                            )
                        }
                    }
                    else -> MockResponse().setResponseCode(204)
                }
            }
        }

        val fakeLocal = FakeLocalTransport().apply { shouldFailPlay = true }
        val ioDispatcher = StandardTestDispatcher(testScheduler)
        val router = PlaybackRouter(
            dao = db.spotifyDao(),
            localTransport = fakeLocal,
            connectManager = SpotifyConnectManager(api, ioDispatcher),
            syncEngine = OfflineFirstSyncEngine(db.spotifyDao(), api, tokenStore, ioDispatcher = ioDispatcher),
            tokenStore = tokenStore,
            ioDispatcher = ioDispatcher,
            enableBackgroundPolling = false,
        )

        fakeLocal.emitConnectionError("""UserNotAuthorizedException: {"message":"Explicit user authorization is required"}""")
        advanceUntilIdle()
        assertTrue(router.needsSsoAuthorization.value)

        router.playUri("spotify:track:wake_1", "spotify:playlist:wake_pl", 0)
        advanceUntilIdle()

        assertTrue(fakeLocal.wakeCount >= 2)
        assertTrue(fakeLocal.mediaKeys.contains(android.view.KeyEvent.KEYCODE_MEDIA_PLAY))
        assertTrue(devicePollAttempts.get() >= 2)
        assertEquals(1, targetedPhonePlay.get())
        assertTrue(router.playbackState.value.isPlaying)
    }
}
