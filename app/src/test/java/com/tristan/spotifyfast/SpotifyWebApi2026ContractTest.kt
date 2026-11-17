package com.tristan.spotifyfast

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.tristan.spotifyfast.auth.AuthTokenBundle
import com.tristan.spotifyfast.auth.EncryptedTokenStore
import com.tristan.spotifyfast.data.local.PlaylistEntity
import com.tristan.spotifyfast.data.local.SpotifyFastDatabase
import com.tristan.spotifyfast.data.remote.EmptyBodyConverterFactory
import com.tristan.spotifyfast.data.remote.SpotifyWebApi
import com.tristan.spotifyfast.data.sync.OfflineFirstSyncEngine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
class SpotifyWebApi2026ContractTest {

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
            File(context.cacheDir, "contract_test_${System.nanoTime()}.preferences_pb")
        }
        tokenStore = EncryptedTokenStore(ds)
    }

    @After
    fun tearDown() {
        db.close()
        server.shutdown()
    }

    @Test
    fun `handles polymorphic playlist items 2026 format and 204 playback response`() = runTest {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                return when {
                    request.path?.startsWith("/v1/playlists/pl1/items") == true -> MockResponse()
                        .setResponseCode(200)
                        .setBody(
                            """
                            {
                              "items": [
                                {"item": {"id":"t1","uri":"spotify:track:t1","name":"Track New Field","type":"track","duration_ms":200000,"artists":[{"id":"a1","name":"Artist 1"}]}},
                                {"track": {"id":"t2","uri":"spotify:track:t2","name":"Track Legacy Field","type":"track","duration_ms":180000,"artists":[{"id":"a2","name":"Artist 2"}]}}
                              ]
                            }
                            """.trimIndent(),
                        )
                    request.path?.startsWith("/v1/me/player?") == true -> MockResponse().setResponseCode(204)
                    request.path?.startsWith("/v1/me/library/contains") == true -> MockResponse()
                        .setResponseCode(200)
                        .setBody("[true,false]")
                    request.path?.startsWith("/v1/search") == true -> MockResponse()
                        .setResponseCode(200)
                        .setBody("""{"tracks":{"items":[{"id":"s1","uri":"spotify:track:s1","name":"Search Hit","duration_ms":150000}]}}""")
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }

        val page = api.getPlaylistItems("pl1")
        assertEquals(2, page.items.size)
        assertEquals("Track New Field", page.items[0].resolvedItem?.name)
        assertEquals("Track Legacy Field", page.items[1].resolvedItem?.name)

        val playerResp = api.getPlaybackState()
        assertEquals(204, playerResp.code())
        assertNull(playerResp.body())

        val contains = api.checkLibraryContains("spotify:track:t1,spotify:track:t2")
        assertEquals(listOf(true, false), contains)

        val searchRes = api.search("Hit")
        assertEquals(1, searchRes.tracks?.items?.size)
        val searchReq = (1..4).map { server.takeRequest() }.first { it.path?.startsWith("/v1/search") == true }
        assertTrue(searchReq.path!!.contains("limit=10"))
    }

    @Test
    fun `sync engine skips unchanged snapshot_id and tolerates isolated 403 errors`() = runTest {
        tokenStore.saveTokenBundle(
            AuthTokenBundle("cid", "tok", "ref", System.currentTimeMillis() + 3600_000L),
        )
        // Pre-seed playlist with snapshot "snap_unchanged"
        db.spotifyDao().upsertPlaylists(
            listOf(
                PlaylistEntity(
                    id = "pl_cached",
                    uri = "spotify:playlist:pl_cached",
                    name = "Cached PL",
                    snapshotId = "snap_unchanged",
                ),
            ),
        )

        val plItemsCalls = AtomicInteger(0)
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val p = request.path.orEmpty()
                return when {
                    p.startsWith("/v1/me/playlists") -> MockResponse().setResponseCode(200).setBody(
                        """{"items":[{"id":"pl_cached","uri":"spotify:playlist:pl_cached","name":"Cached PL","snapshot_id":"snap_unchanged"}]}""",
                    )
                    p.startsWith("/v1/playlists/pl_cached/items") -> {
                        plItemsCalls.incrementAndGet()
                        MockResponse().setResponseCode(200).setBody("""{"items":[]}""")
                    }
                    p.startsWith("/v1/me/tracks") -> MockResponse().setResponseCode(403)
                    p.startsWith("/v1/me/albums") -> MockResponse().setResponseCode(200).setBody("""{"items":[]}""")
                    p.startsWith("/v1/me/shows") -> MockResponse().setResponseCode(200).setBody("""{"items":[]}""")
                    p.startsWith("/v1/me/audiobooks") -> MockResponse().setResponseCode(403)
                    p.startsWith("/v1/me/player/recently-played") -> MockResponse().setResponseCode(200).setBody(
                        """{"items":[{"track":{"id":"r1","uri":"spotify:track:r1","name":"Recent Song","duration_ms":190000,"artists":[{"id":"a1","name":"M83"}]}}]}""",
                    )
                    p.startsWith("/v1/me/library/contains") -> MockResponse().setResponseCode(200).setBody("[true]")
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }

        val engine = OfflineFirstSyncEngine(db.spotifyDao(), api, tokenStore)
        val result = engine.syncAll()
        assertTrue(result.isSuccess)
        assertEquals(0, plItemsCalls.get())

        val recent = db.spotifyDao().observeRecentTracks().first()
        assertEquals(1, recent.size)
        assertEquals("Recent Song", recent[0].title)
        assertTrue(recent[0].isLiked)
    }

    @Test
    fun `github app update manager parses release and compares semver cleanly`() = runTest {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                return if (request.path == "/repos/tristanroux/spotify-fast-android/releases/latest") {
                    MockResponse().setResponseCode(200).setBody(
                        """
                        {
                          "tag_name": "v1.2.0",
                          "name": "v1.2.0",
                          "body": "Améliorations audio",
                          "assets": [
                            {"name": "app-release.apk", "browser_download_url": "${server.url("/apk/app-release.apk")}", "size": 2600000}
                          ]
                        }
                        """.trimIndent(),
                    )
                } else {
                    MockResponse().setResponseCode(404)
                }
            }
        }

        val context = ApplicationProvider.getApplicationContext<Context>()
        val updater = com.tristan.spotifyfast.update.AppUpdateManager(
            context = context,
            apiBaseUrl = server.url("/").toString(),
            currentVersionName = "1.1.0",
        )
        val release = updater.fetchLatestRelease("tristanroux/spotify-fast-android").getOrThrow()
        assertEquals("v1.2.0", release.tagName)
        assertEquals("app-release.apk", release.assets.first().name)
        assertTrue(com.tristan.spotifyfast.update.AppUpdateManager.isNewerVersion("v1.2.0", "1.1.0"))
        assertTrue(!com.tristan.spotifyfast.update.AppUpdateManager.isNewerVersion("v1.1.0", "1.1.0"))
    }
}
