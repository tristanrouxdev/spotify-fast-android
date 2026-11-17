package com.tristan.spotifyfast

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.tristan.spotifyfast.data.local.SpotifyFastDatabase
import com.tristan.spotifyfast.data.remote.EmptyBodyConverterFactory
import com.tristan.spotifyfast.data.remote.LrcLibApi
import com.tristan.spotifyfast.data.sync.LrcParser
import com.tristan.spotifyfast.data.sync.LyricsRepository
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
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
class LyricsRepositoryAndParserTest {

    private lateinit var server: MockWebServer
    private lateinit var lrcApi: LrcLibApi
    private lateinit var db: SpotifyFastDatabase

    @Before
    fun setup() {
        server = MockWebServer()
        server.start()
        val json = Json { ignoreUnknownKeys = true }
        lrcApi = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(EmptyBodyConverterFactory())
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(LrcLibApi::class.java)

        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, SpotifyFastDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
        server.shutdown()
    }

    @Test
    fun `lrc parser handles multi-timestamps, normalizes title, and binary searches active line`() {
        assertEquals("Midnight City", LrcParser.normalizeTrackTitle("Midnight City - Remastered 2011"))
        assertEquals("Get Lucky", LrcParser.normalizeTrackTitle("Get Lucky (feat. Pharrell Williams)"))

        val rawLrc = """
            [00:10.00]Waiting in a car
            [00:15.50][01:02.00]Waiting for a ride in the dark
            [00:22.00]The night city grows
        """.trimIndent()

        val lines = LrcParser.parse(rawLrc)
        assertEquals(4, lines.size)
        assertEquals(10_000L, lines[0].timestampMs)
        assertEquals(15_500L, lines[1].timestampMs)
        assertEquals(22_000L, lines[2].timestampMs)
        assertEquals(62_000L, lines[3].timestampMs)

        assertEquals(-1, LrcParser.activeLineIndexAt(lines, 5_000L))
        assertEquals(0, LrcParser.activeLineIndexAt(lines, 12_000L))
        assertEquals(1, LrcParser.activeLineIndexAt(lines, 16_000L))
        assertEquals(3, LrcParser.activeLineIndexAt(lines, 75_000L))
    }

    @Test
    fun `falls back from 404 api get to search within 3s duration and caches result in Room`() = runTest {
        val networkHits = AtomicInteger(0)
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                networkHits.incrementAndGet()
                val p = request.path.orEmpty()
                return when {
                    p.startsWith("/api/get") -> MockResponse().setResponseCode(404)
                    p.startsWith("/api/search") -> MockResponse().setResponseCode(200).setBody(
                        """
                        [
                          {"id":1,"trackName":"Resonance","artistName":"HOME","duration":180.0,"syncedLyrics":"[00:05.00]Wrong duration"},
                          {"id":2,"trackName":"Resonance","artistName":"HOME","duration":211.5,"syncedLyrics":"[00:08.00]Matched line"}
                        ]
                        """.trimIndent(),
                    )
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }

        val repo = LyricsRepository(db.spotifyDao(), lrcApi)
        val first = repo.getLyrics(
            trackUri = "spotify:track:res1",
            trackTitle = "Resonance - Remastered 2020",
            artistName = "HOME, Guest",
            albumName = "Odyssey",
            durationMs = 212_000L,
        )

        assertFalse(first.isNotFound)
        assertEquals(1, first.syncedLines.size)
        assertEquals("Matched line", first.syncedLines[0].text)
        assertEquals(2, networkHits.get())

        // Second query hits Room cache with zero network calls
        val second = repo.getLyrics("spotify:track:res1", "Resonance", "HOME", "Odyssey", 212_000L)
        assertEquals("Matched line", second.syncedLines[0].text)
        assertEquals(2, networkHits.get())
    }

    @Test
    fun `stores negative cache entry when lyrics are unavailable`() = runTest {
        val hits = AtomicInteger(0)
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                hits.incrementAndGet()
                return if (request.path?.startsWith("/api/search") == true) {
                    MockResponse().setResponseCode(200).setBody("[]")
                } else {
                    MockResponse().setResponseCode(404)
                }
            }
        }

        val repo = LyricsRepository(db.spotifyDao(), lrcApi)
        val first = repo.getLyrics("spotify:track:inst", "Instrumental", "Artist", "Album", 120_000L)
        assertTrue(first.isNotFound)
        assertEquals(2, hits.get())

        val second = repo.getLyrics("spotify:track:inst", "Instrumental", "Artist", "Album", 120_000L)
        assertTrue(second.isNotFound)
        assertEquals(2, hits.get())
    }
}
