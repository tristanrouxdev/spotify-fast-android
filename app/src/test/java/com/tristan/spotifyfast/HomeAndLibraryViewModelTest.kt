package com.tristan.spotifyfast

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.tristan.spotifyfast.auth.EncryptedTokenStore
import com.tristan.spotifyfast.data.local.HomeShortcutEntity
import com.tristan.spotifyfast.data.local.PlaylistEntity
import com.tristan.spotifyfast.data.local.ShowEntity
import com.tristan.spotifyfast.data.local.SpotifyFastDatabase
import com.tristan.spotifyfast.data.remote.EmptyBodyConverterFactory
import com.tristan.spotifyfast.data.remote.SpotifyWebApi
import com.tristan.spotifyfast.data.sync.OfflineFirstSyncEngine
import com.tristan.spotifyfast.ui.home.HomeFilter
import com.tristan.spotifyfast.ui.home.HomeViewModel
import com.tristan.spotifyfast.ui.library.LibraryFilter
import com.tristan.spotifyfast.ui.library.LibrarySort
import com.tristan.spotifyfast.ui.library.LibraryViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class HomeAndLibraryViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var db: SpotifyFastDatabase
    private lateinit var server: MockWebServer
    private lateinit var syncEngine: OfflineFirstSyncEngine

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, SpotifyFastDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryExecutor(Runnable::run)
            .setTransactionExecutor(Runnable::run)
            .build()

        server = MockWebServer()
        server.start()
        val api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(EmptyBodyConverterFactory())
            .addConverterFactory(Json { ignoreUnknownKeys = true }.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(SpotifyWebApi::class.java)
        val ds = PreferenceDataStoreFactory.create {
            File(context.cacheDir, "vm_test_${System.nanoTime()}.preferences_pb")
        }
        syncEngine = OfflineFirstSyncEngine(db.spotifyDao(), api, EncryptedTokenStore(ds), ioDispatcher = testDispatcher)
    }

    @After
    fun tearDown() {
        db.close()
        server.shutdown()
        Dispatchers.resetMain()
    }

    @Test
    fun `home and library viewmodels hydrate immediately from Room and support filters and sorting`() = runTest {
        db.spotifyDao().upsertHomeShortcuts(
            listOf(
                HomeShortcutEntity("spotify:playlist:1", "Synthwave", "Playlist", null, "MUSIC", 0xFF1DB954.toInt(), 0),
                HomeShortcutEntity("spotify:show:1", "Android Podcast", "Podcast", null, "PODCAST", 0xFFE13300.toInt(), 1),
            ),
        )
        db.spotifyDao().upsertPlaylists(
            listOf(
                PlaylistEntity("1", "spotify:playlist:1", "Zebra Mix", null, "Tristan", null, "s1", 10, 0xFF1DB954.toInt(), false),
                PlaylistEntity("2", "spotify:playlist:2", "Alpha Beats", null, "Tristan", null, "s2", 12, 0xFF1E3264.toInt(), false),
            ),
        )
        db.spotifyDao().upsertShows(
            listOf(ShowEntity("s1", "spotify:show:1", "Android Podcast", "Dev", "", null, 15)),
        )

        val homeVm = HomeViewModel(db.spotifyDao(), testDispatcher)
        val libVm = LibraryViewModel(db.spotifyDao(), syncEngine, testDispatcher, testDispatcher)
        advanceUntilIdle()

        assertEquals(2, homeVm.uiState.value.shortcuts.size)
        homeVm.setFilter(HomeFilter.PODCASTS)
        advanceUntilIdle()
        assertEquals(1, homeVm.uiState.value.shortcuts.size)
        assertEquals("Android Podcast", homeVm.uiState.value.shortcuts[0].title)

        // Library includes Liked Songs + 2 playlists + 1 show = 4 items
        assertEquals(4, libVm.uiState.value.items.size)

        libVm.setFilter(LibraryFilter.PLAYLISTS)
        advanceUntilIdle()
        assertEquals(3, libVm.uiState.value.items.size)

        // Cycle RECENTS -> RECENTLY_ADDED -> ALPHABETICAL
        libVm.cycleSort()
        libVm.cycleSort()
        advanceUntilIdle()
        assertEquals(LibrarySort.ALPHABETICAL, libVm.uiState.value.sort)
        assertEquals("Titres likés", libVm.uiState.value.items[0].title)
        assertEquals("Alpha Beats", libVm.uiState.value.items[1].title)

        libVm.setSearchQuery("zebra")
        advanceUntilIdle()
        assertTrue(libVm.uiState.value.items.any { it.title == "Zebra Mix" })
        assertEquals(1, libVm.uiState.value.items.size)
    }

    @Test
    fun `search viewmodel debounces input and app remote maps podcast speed and image uris`() = kotlinx.coroutines.runBlocking {
        server.enqueue(
            okhttp3.mockwebserver.MockResponse()
                .setResponseCode(200)
                .setBody("""{"tracks":{"items":[{"id":"t9","uri":"spotify:track:t9","name":"Daft Track","duration_ms":200000}]}}"""),
        )
        val realEngine = OfflineFirstSyncEngine(
            db.spotifyDao(),
            Retrofit.Builder()
                .baseUrl(server.url("/"))
                .addConverterFactory(EmptyBodyConverterFactory())
                .addConverterFactory(Json { ignoreUnknownKeys = true }.asConverterFactory("application/json".toMediaType()))
                .build()
                .create(SpotifyWebApi::class.java),
            EncryptedTokenStore(
                PreferenceDataStoreFactory.create {
                    File(ApplicationProvider.getApplicationContext<Context>().cacheDir, "search_vm_${System.nanoTime()}.preferences_pb")
                },
            ),
            ioDispatcher = Dispatchers.IO,
        )
        val searchVm = com.tristan.spotifyfast.ui.search.SearchViewModel(db.spotifyDao(), realEngine, Dispatchers.IO)
        searchVm.onQueryChange("daft")
        val resolved = withTimeout(5_000L) {
            searchVm.uiState.first { it.results != null }
        }
        assertEquals(1, resolved.results?.tracks?.items?.size)
        assertEquals(
            "https://i.scdn.co/image/ab67616d0000b273",
            com.tristan.spotifyfast.player.AppRemoteBridge.spotifyImageUriToHttps("spotify:image:ab67616d0000b273"),
        )
        assertEquals(
            com.spotify.protocol.types.PlaybackSpeed.PodcastPlaybackSpeed.PLAYBACK_SPEED_150,
            com.tristan.spotifyfast.player.AppRemoteBridge.mapPodcastSpeed(1.5f),
        )
    }
}
