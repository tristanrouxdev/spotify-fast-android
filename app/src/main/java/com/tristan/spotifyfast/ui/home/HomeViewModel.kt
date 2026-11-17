package com.tristan.spotifyfast.ui.home

import com.tristan.spotifyfast.data.local.AlbumEntity
import com.tristan.spotifyfast.data.local.HomeShortcutEntity
import com.tristan.spotifyfast.data.local.PlaylistEntity
import com.tristan.spotifyfast.data.local.ShowEpisodeEntity
import com.tristan.spotifyfast.data.local.SpotifyDao
import com.tristan.spotifyfast.data.local.TrackEntity
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

enum class HomeFilter {
    ALL,
    MUSIC,
    PODCASTS,
}

data class HomeUiState(
    val filter: HomeFilter = HomeFilter.ALL,
    val shortcuts: List<HomeShortcutEntity> = emptyList(),
    val playlists: List<PlaylistEntity> = emptyList(),
    val recentTracks: List<TrackEntity> = emptyList(),
    val inProgressEpisodes: List<ShowEpisodeEntity> = emptyList(),
    val albums: List<AlbumEntity> = emptyList(),
)

/**
 * Emits cached Room data immediately for Frame 1 rendering without awaiting network sync.
 */
class HomeViewModel(
    dao: SpotifyDao,
    mainDispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
) {
    private val scope = CoroutineScope(SupervisorJob() + mainDispatcher)
    private val selectedFilter = MutableStateFlow(HomeFilter.ALL)

    private val coreStreams = combine(
        selectedFilter,
        dao.observeHomeShortcuts(),
        dao.observePlaylists(),
        dao.observeRecentTracks(),
    ) { filter, shortcuts, playlists, recentTracks ->
        Quadruple(filter, shortcuts, playlists, recentTracks)
    }

    val uiState: StateFlow<HomeUiState> = combine(
        coreStreams,
        dao.observeInProgressEpisodes(),
        dao.observeAlbums(),
    ) { (filter, shortcuts, playlists, recentTracks), episodes, albums ->
        val filteredShortcuts = when (filter) {
            HomeFilter.ALL -> shortcuts
            HomeFilter.MUSIC -> shortcuts.filter { it.category == "MUSIC" }
            HomeFilter.PODCASTS -> shortcuts.filter { it.category == "PODCAST" }
        }
        HomeUiState(
            filter = filter,
            shortcuts = filteredShortcuts.take(6),
            playlists = if (filter == HomeFilter.PODCASTS) emptyList() else playlists,
            recentTracks = if (filter == HomeFilter.PODCASTS) emptyList() else recentTracks,
            inProgressEpisodes = if (filter == HomeFilter.MUSIC) emptyList() else episodes,
            albums = if (filter == HomeFilter.PODCASTS) emptyList() else albums,
        )
    }.stateIn(
        scope = scope,
        started = SharingStarted.Eagerly,
        initialValue = HomeUiState(),
    )

    fun setFilter(filter: HomeFilter) {
        selectedFilter.value = filter
    }
}

private data class Quadruple<A, B, C, D>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D,
)
