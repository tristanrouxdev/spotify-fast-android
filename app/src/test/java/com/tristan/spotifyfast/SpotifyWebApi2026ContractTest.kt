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
                return if (request.path == "/repos/tristanrouxdev/spotify-fast-android/releases/latest") {
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
        val release = updater.fetchLatestRelease("tristanrouxdev/spotify-fast-android").getOrThrow()
        assertEquals("v1.2.0", release.tagName)
        assertEquals("app-release.apk", release.assets.first().name)
        assertTrue(com.tristan.spotifyfast.update.AppUpdateManager.isNewerVersion("v1.2.0", "1.1.0"))
        assertTrue(!com.tristan.spotifyfast.update.AppUpdateManager.isNewerVersion("v1.1.0", "1.1.0"))
    }

    @Test
    fun `sync engine filters null playlist items, uses tracks fallback, syncs artists, and emits SyncStatus`() = runTest {
        tokenStore.saveTokenBundle(
            AuthTokenBundle("cid", "tok", "ref", System.currentTimeMillis() + 3600_000L),
        )
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val p = request.path.orEmpty()
                return when {
                    p.startsWith("/v1/me/playlists") -> MockResponse().setResponseCode(200).setBody(
                        """{"items":[null,{"id":"pl_mix","uri":"spotify:playlist:pl_mix","name":"Mix 1","tracks":{"total":1}}]}""",
                    )
                    p.startsWith("/v1/playlists/pl_mix/items") -> MockResponse().setResponseCode(404)
                    p.startsWith("/v1/playlists/pl_mix/tracks") -> MockResponse().setResponseCode(200).setBody(
                        """{"items":[null,{"track":{"id":"tr_mix","uri":"spotify:track:tr_mix","name":"Mix Song","duration_ms":210000,"artists":[{"id":"ar1","name":"Artist"}]}}]}""",
                    )
                    p.startsWith("/v1/me/following") -> MockResponse().setResponseCode(200).setBody(
                        """{"artists":{"items":[{"id":"ar1","uri":"spotify:artist:ar1","name":"Followed Artist","genres":["synthwave"]}]}}""",
                    )
                    else -> MockResponse().setResponseCode(200).setBody("""{"items":[]}""")
                }
            }
        }

        val engine = OfflineFirstSyncEngine(db.spotifyDao(), api, tokenStore)
        val res = engine.syncAll()
        assertTrue(res.isSuccess)
        val tracks = db.spotifyDao().getTracksForContextOnce("spotify:playlist:pl_mix")
        assertEquals(1, tracks.size)
        assertEquals("Mix Song", tracks[0].title)
        val artists = db.spotifyDao().observeArtists().first()
        assertEquals(1, artists.size)
        assertEquals("Followed Artist", artists[0].name)
        val status = engine.syncStatus.value as com.tristan.spotifyfast.data.sync.SyncStatus.Success
        assertEquals(1, status.playlists)
        assertEquals(1, status.artists)
    }

    @Test
    fun `sync engine paginates all playlists, playlist tracks beyond 50, and liked tracks beyond 50`() = runTest {
        tokenStore.saveTokenBundle(
            AuthTokenBundle("cid", "tok", "ref", System.currentTimeMillis() + 3600_000L),
        )
        // Pre-seed truncated v1.3.0 cache (50 tracks stored even though API has 130 tracks and same snapshot)
        db.spotifyDao().upsertPlaylists(
            listOf(
                PlaylistEntity(
                    id = "big_pl",
                    uri = "spotify:playlist:big_pl",
                    name = "Big Playlist",
                    snapshotId = "snap_big",
                    trackCount = 130,
                ),
            ),
        )
        db.spotifyDao().upsertTracks(
            (0 until 50).map { idx ->
                com.tristan.spotifyfast.data.local.TrackEntity(
                    uri = "spotify:track:old_$idx",
                    id = "old_$idx",
                    contextUri = "spotify:playlist:big_pl",
                    positionInContext = idx,
                    title = "Old $idx",
                    artistName = "Artist",
                    albumName = "Album",
                    artworkUrl = null,
                    durationMs = 180_000L,
                )
            },
        )

        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val p = request.path.orEmpty()
                val offset = Regex("[?&]offset=(\\d+)").find(p)?.groupValues?.get(1)?.toIntOrNull() ?: 0
                val limit = Regex("[?&]limit=(\\d+)").find(p)?.groupValues?.get(1)?.toIntOrNull() ?: 50
                return when {
                    p.startsWith("/v1/me/playlists") -> {
                        val totalPlaylists = 55
                        val end = (offset + limit).coerceAtMost(totalPlaylists)
                        val jsonItems = (offset until end).joinToString(",") { i ->
                            if (i == 25) {
                                """{"id":"big_pl","uri":"spotify:playlist:big_pl","name":"Big Playlist","snapshot_id":"snap_big","items":{"total":130}}"""
                            } else {
                                """{"id":"pl_$i","uri":"spotify:playlist:pl_$i","name":"Playlist $i","snapshot_id":"snap_$i","items":{"total":1}}"""
                            }
                        }
                        val nextField = if (end < totalPlaylists) """"${server.url("/v1/me/playlists?limit=50&offset=$end")}"""" else "null"
                        MockResponse().setResponseCode(200).setBody(
                            """{"items":[$jsonItems],"limit":$limit,"offset":$offset,"total":$totalPlaylists,"next":$nextField}""",
                        )
                    }
                    p.startsWith("/v1/playlists/big_pl/items") -> {
                        val totalTracks = 130
                        val end = (offset + limit).coerceAtMost(totalTracks)
                        val jsonItems = (offset until end).joinToString(",") { i ->
                            """{"item":{"id":"bt_$i","uri":"spotify:track:bt_$i","name":"Big Track $i","type":"track","duration_ms":200000,"artists":[{"id":"a1","name":"Artist"}]}}"""
                        }
                        val nextField = if (end < totalTracks) """"${server.url("/v1/playlists/big_pl/items?limit=50&offset=$end")}"""" else "null"
                        MockResponse().setResponseCode(200).setBody(
                            """{"items":[$jsonItems],"limit":$limit,"offset":$offset,"total":$totalTracks,"next":$nextField}""",
                        )
                    }
                    p.startsWith("/v1/playlists/pl_") && p.contains("/items") -> {
                        val id = p.substringAfter("/v1/playlists/").substringBefore("/items")
                        MockResponse().setResponseCode(200).setBody(
                            """{"items":[{"item":{"id":"t_$id","uri":"spotify:track:t_$id","name":"Song $id","type":"track","duration_ms":180000}}],"total":1,"next":null}""",
                        )
                    }
                    p.startsWith("/v1/me/tracks") -> {
                        val totalLiked = 125
                        val end = (offset + limit).coerceAtMost(totalLiked)
                        val jsonItems = (offset until end).joinToString(",") { i ->
                            """{"track":{"id":"lk_$i","uri":"spotify:track:lk_$i","name":"Liked $i","type":"track","duration_ms":210000,"artists":[{"id":"a1","name":"Artist"}]}}"""
                        }
                        val nextField = if (end < totalLiked) """"${server.url("/v1/me/tracks?limit=50&offset=$end")}"""" else "null"
                        MockResponse().setResponseCode(200).setBody(
                            """{"items":[$jsonItems],"limit":$limit,"offset":$offset,"total":$totalLiked,"next":$nextField}""",
                        )
                    }
                    p.startsWith("/v1/me/albums") -> {
                        val totalAlbums = 55
                        val end = (offset + limit).coerceAtMost(totalAlbums)
                        val jsonItems = (offset until end).joinToString(",") { i ->
                            """{"album":{"id":"alb_$i","uri":"spotify:album:alb_$i","name":"Album $i","total_tracks":10,"artists":[{"id":"ar1","name":"Artist"}]}}"""
                        }
                        val nextField = if (end < totalAlbums) """"${server.url("/v1/me/albums?limit=50&offset=$end")}"""" else "null"
                        MockResponse().setResponseCode(200).setBody(
                            """{"items":[$jsonItems],"limit":$limit,"offset":$offset,"total":$totalAlbums,"next":$nextField}""",
                        )
                    }
                    else -> MockResponse().setResponseCode(200).setBody("""{"items":[],"total":0,"next":null}""")
                }
            }
        }

        val engine = OfflineFirstSyncEngine(db.spotifyDao(), api, tokenStore)
        val result = engine.syncAll()
        assertTrue(result.isSuccess)

        // All 55 playlists synced Metadata + items (beyond 20 and 50 caps!)
        assertEquals(55, db.spotifyDao().getAllPlaylistsOnce().size)
        assertEquals(1, db.spotifyDao().getTracksForContextOnce("spotify:playlist:pl_54").size)

        // Pre-seeded truncated big_pl (was 50) re-synced in full to 130 tracks
        val bigTracks = db.spotifyDao().getTracksForContextOnce("spotify:playlist:big_pl")
        assertEquals(130, bigTracks.size)
        assertEquals("Big Track 0", bigTracks.first().title)
        assertEquals("Big Track 129", bigTracks.last().title)

        // All 125 liked tracks synced (beyond 50 cap!)
        val likedTracks = db.spotifyDao().getTracksForContextOnce(com.tristan.spotifyfast.data.local.LIKED_TRACKS_CONTEXT_URI)
        assertEquals(125, likedTracks.size)
        assertEquals("Liked 0", likedTracks.first().title)
        assertEquals("Liked 124", likedTracks.last().title)

        // All 55 saved albums synced (beyond 50 cap!)
        assertEquals(55, db.spotifyDao().observeAlbums().first().size)
    }

    @Test
    fun `sync engine falls back to full playlist container with pagination and tracks 403 restricted playlists`() = runTest {
        tokenStore.saveTokenBundle(
            AuthTokenBundle("cid", "tok", "ref", System.currentTimeMillis() + 3600_000L),
        )
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val p = request.path.orEmpty()
                return when {
                    p.startsWith("/v1/me/playlists") -> MockResponse().setResponseCode(200).setBody(
                        """{"items":[{"id":"pl_obj","uri":"spotify:playlist:pl_obj","name":"Obj Playlist","items":{"total":2}},{"id":"pl_forbidden","uri":"spotify:playlist:pl_forbidden","name":"Spotify Mix","items":{"total":40}}],"total":2,"next":null}""",
                    )
                    p.startsWith("/v1/playlists/pl_obj/items") && !p.contains("page=2") -> MockResponse().setResponseCode(404)
                    p.startsWith("/v1/playlists/pl_obj/tracks") -> MockResponse().setResponseCode(404)
                    p.startsWith("/v1/playlists/pl_obj?") || p == "/v1/playlists/pl_obj" -> MockResponse().setResponseCode(200).setBody(
                        """{"id":"pl_obj","uri":"spotify:playlist:pl_obj","name":"Obj Playlist","items":{"items":[{"item":{"id":"o1","uri":"spotify:track:o1","name":"Obj Track 1","duration_ms":180000}}],"total":2,"next":"${server.url("/v1/playlists/pl_obj/items?page=2")}"}}""",
                    )
                    p.startsWith("/v1/playlists/pl_obj/items?page=2") -> MockResponse().setResponseCode(200).setBody(
                        """{"items":[{"item":{"id":"o2","uri":"spotify:track:o2","name":"Obj Track 2","duration_ms":190000}}],"total":2,"next":null}""",
                    )
                    p.startsWith("/v1/playlists/pl_forbidden") -> MockResponse().setResponseCode(403).setBody("""{"error":{"status":403,"message":"Forbidden"}}""")
                    p.startsWith("/v1/me/following") -> MockResponse().setResponseCode(403)
                    else -> MockResponse().setResponseCode(200).setBody("""{"items":[],"total":0,"next":null}""")
                }
            }
        }

        val engine = OfflineFirstSyncEngine(db.spotifyDao(), api, tokenStore)
        val res = engine.syncAll()
        assertTrue(res.isSuccess)

        val objTracks = db.spotifyDao().getTracksForContextOnce("spotify:playlist:pl_obj")
        assertEquals(2, objTracks.size)
        assertEquals("Obj Track 2", objTracks[1].title)

        assertTrue(engine.restrictedPlaylistUris.value.contains("spotify:playlist:pl_forbidden"))
        assertTrue(engine.followedArtistsPermissionMissing.value)
    }
}
