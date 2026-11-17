package com.tristan.spotifyfast.ui.library

import com.tristan.spotifyfast.data.local.AudiobookChapterEntity
import com.tristan.spotifyfast.data.local.DEFAULT_DOMINANT_COLOR
import com.tristan.spotifyfast.data.local.LIKED_TRACKS_CONTEXT_URI
import com.tristan.spotifyfast.data.local.ShowEpisodeEntity
import com.tristan.spotifyfast.data.local.SpotifyDao
import com.tristan.spotifyfast.data.local.TrackEntity
import com.tristan.spotifyfast.data.sync.OfflineFirstSyncEngine
import com.tristan.spotifyfast.data.sync.SyncStatus
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class LibraryFilter {
    ALL,
    PLAYLISTS,
    ARTISTS,
    ALBUMS,
    PODCASTS_AUDIOBOOKS,
}

enum class LibrarySort(val label: String) {
    RECENTS("Récents"),
    RECENTLY_ADDED("Ajoutés récemment"),
    ALPHABETICAL("Alphabétique"),
}

enum class LibraryItemKind {
    LIKED_TRACKS,
    PLAYLIST,
    ALBUM,
    ARTIST,
    SHOW,
    AUDIOBOOK,
}

data class LibraryItemUi(
    val id: String,
    val uri: String,
    val title: String,
    val subtitle: String,
    val artworkUrl: String?,
    val kind: LibraryItemKind,
    val dominantColorArgb: Int,
    val isPinned: Boolean = false,
    val addedAtEpochMs: Long = 0L,
    val updatedAtEpochMs: Long = 0L,
)

data class LibraryUiState(
    val filter: LibraryFilter = LibraryFilter.ALL,
    val sort: LibrarySort = LibrarySort.RECENTS,
    val searchQuery: String = "",
    val isGridView: Boolean = false,
    val items: List<LibraryItemUi> = emptyList(),
)

data class CollectionHeaderMeta(
    val dominantColorArgb: Int = DEFAULT_DOMINANT_COLOR,
    val artworkUrl: String? = null,
    val reportedTrackCount: Int = 0,
)

class LibraryViewModel(
    private val dao: SpotifyDao,
    private val syncEngine: OfflineFirstSyncEngine,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    mainDispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
) {
    private val scope = CoroutineScope(SupervisorJob() + mainDispatcher)
    val syncStatus: StateFlow<SyncStatus> = syncEngine.syncStatus
    val restrictedPlaylistUris: StateFlow<Set<String>> = syncEngine.restrictedPlaylistUris

    private val filterFlow = MutableStateFlow(LibraryFilter.ALL)
    private val sortFlow = MutableStateFlow(LibrarySort.RECENTS)
    private val queryFlow = MutableStateFlow("")
    private val gridFlow = MutableStateFlow(false)

    private val sourceItemsFlow = combine(
        dao.observeLikedTracksCount(),
        dao.observePlaylists(),
        dao.observeAlbums(),
        dao.observeArtists(),
        dao.observeShows(),
    ) { likedCount, playlists, albums, artists, shows ->
        val list = mutableListOf<LibraryItemUi>()
        list += LibraryItemUi(
            id = "liked_tracks",
            uri = LIKED_TRACKS_CONTEXT_URI,
            title = "Titres likés",
            subtitle = "Playlist • $likedCount titres",
            artworkUrl = null,
            kind = LibraryItemKind.LIKED_TRACKS,
            dominantColorArgb = 0xFF450AF5.toInt(),
            isPinned = true,
            addedAtEpochMs = Long.MAX_VALUE,
            updatedAtEpochMs = Long.MAX_VALUE,
        )
        playlists.forEach { p ->
            val sub = if (p.trackCount > 0) {
                "Playlist • ${p.trackCount} titres • ${p.ownerName}"
            } else {
                "Playlist • ${p.ownerName}"
            }
            list += LibraryItemUi(
                id = p.id,
                uri = p.uri,
                title = p.name,
                subtitle = sub,
                artworkUrl = p.artworkUrl,
                kind = LibraryItemKind.PLAYLIST,
                dominantColorArgb = p.dominantColorArgb,
                isPinned = p.isPinned,
                addedAtEpochMs = p.addedAtEpochMs,
                updatedAtEpochMs = p.updatedAtEpochMs,
            )
        }
        albums.forEach { a ->
            list += LibraryItemUi(
                id = a.id,
                uri = a.uri,
                title = a.name,
                subtitle = "Album • ${a.artistName}",
                artworkUrl = a.artworkUrl,
                kind = LibraryItemKind.ALBUM,
                dominantColorArgb = a.dominantColorArgb,
                addedAtEpochMs = a.addedAtEpochMs,
                updatedAtEpochMs = a.addedAtEpochMs,
            )
        }
        artists.forEach { ar ->
            list += LibraryItemUi(
                id = ar.id,
                uri = ar.uri,
                title = ar.name,
                subtitle = "Artiste",
                artworkUrl = ar.artworkUrl,
                kind = LibraryItemKind.ARTIST,
                dominantColorArgb = ar.dominantColorArgb,
            )
        }
        shows.forEach { s ->
            list += LibraryItemUi(
                id = s.id,
                uri = s.uri,
                title = s.name,
                subtitle = "Podcast • ${s.publisher}",
                artworkUrl = s.artworkUrl,
                kind = LibraryItemKind.SHOW,
                dominantColorArgb = s.dominantColorArgb,
                addedAtEpochMs = s.addedAtEpochMs,
                updatedAtEpochMs = s.addedAtEpochMs,
            )
        }
        list
    }.combine(dao.observeAudiobooks()) { base, audiobooks ->
        val books = audiobooks.map { b ->
            LibraryItemUi(
                id = b.id,
                uri = b.uri,
                title = b.name,
                subtitle = "Livre audio • ${b.authorName}",
                artworkUrl = b.artworkUrl,
                kind = LibraryItemKind.AUDIOBOOK,
                dominantColorArgb = b.dominantColorArgb,
                addedAtEpochMs = b.addedAtEpochMs,
                updatedAtEpochMs = b.addedAtEpochMs,
            )
        }
        base + books
    }

    val uiState: StateFlow<LibraryUiState> = combine(
        sourceItemsFlow,
        filterFlow,
        sortFlow,
        queryFlow,
        gridFlow,
    ) { items, filter, sort, query, isGrid ->
        val byType = when (filter) {
            LibraryFilter.ALL -> items
            LibraryFilter.PLAYLISTS -> items.filter {
                it.kind == LibraryItemKind.LIKED_TRACKS || it.kind == LibraryItemKind.PLAYLIST
            }
            LibraryFilter.ARTISTS -> items.filter { it.kind == LibraryItemKind.ARTIST }
            LibraryFilter.ALBUMS -> items.filter { it.kind == LibraryItemKind.ALBUM }
            LibraryFilter.PODCASTS_AUDIOBOOKS -> items.filter {
                it.kind == LibraryItemKind.SHOW || it.kind == LibraryItemKind.AUDIOBOOK
            }
        }
        val byQuery = if (query.isBlank()) {
            byType
        } else {
            val q = query.trim().lowercase()
            byType.filter { it.title.lowercase().contains(q) || it.subtitle.lowercase().contains(q) }
        }
        val sorted = when (sort) {
            LibrarySort.RECENTS -> byQuery.sortedWith(
                compareByDescending<LibraryItemUi> { it.isPinned }.thenByDescending { it.updatedAtEpochMs },
            )
            LibrarySort.RECENTLY_ADDED -> byQuery.sortedWith(
                compareByDescending<LibraryItemUi> { it.isPinned }.thenByDescending { it.addedAtEpochMs },
            )
            LibrarySort.ALPHABETICAL -> byQuery.sortedWith(
                compareByDescending<LibraryItemUi> { it.kind == LibraryItemKind.LIKED_TRACKS }
                    .thenBy { it.title.lowercase() },
            )
        }
        LibraryUiState(
            filter = filter,
            sort = sort,
            searchQuery = query,
            isGridView = isGrid,
            items = sorted,
        )
    }.stateIn(scope, SharingStarted.Eagerly, LibraryUiState())

    fun setFilter(filter: LibraryFilter) {
        filterFlow.value = filter
    }

    fun cycleSort() {
        val next = when (sortFlow.value) {
            LibrarySort.RECENTS -> LibrarySort.RECENTLY_ADDED
            LibrarySort.RECENTLY_ADDED -> LibrarySort.ALPHABETICAL
            LibrarySort.ALPHABETICAL -> LibrarySort.RECENTS
        }
        sortFlow.value = next
    }

    fun setSearchQuery(q: String) {
        queryFlow.value = q
    }

    fun toggleGridView() {
        gridFlow.value = !gridFlow.value
    }

    fun togglePin(item: LibraryItemUi) {
        if (item.kind != LibraryItemKind.PLAYLIST) return
        scope.launch(ioDispatcher) {
            dao.setPlaylistPinned(item.id, !item.isPinned)
        }
    }

    fun observeTracksForContext(contextUri: String): Flow<List<TrackEntity>> =
        dao.observeTracksForContext(contextUri)

    fun observeShowEpisodes(showUri: String): Flow<List<ShowEpisodeEntity>> =
        dao.observeEpisodesForShow(showUri)

    fun observeAudiobookChapters(audiobookUri: String): Flow<List<AudiobookChapterEntity>> =
        dao.observeChaptersForAudiobook(audiobookUri)

    suspend fun getCollectionMeta(uri: String): CollectionHeaderMeta {
        if (uri == LIKED_TRACKS_CONTEXT_URI) {
            return CollectionHeaderMeta(dominantColorArgb = 0xFF450AF5.toInt())
        }
        dao.getPlaylist(uri)?.let { p ->
            return CollectionHeaderMeta(
                dominantColorArgb = p.dominantColorArgb,
                artworkUrl = p.artworkUrl,
                reportedTrackCount = p.trackCount,
            )
        }
        dao.getAlbum(uri)?.let { a ->
            return CollectionHeaderMeta(
                dominantColorArgb = a.dominantColorArgb,
                artworkUrl = a.artworkUrl,
                reportedTrackCount = a.totalTracks,
            )
        }
        dao.getShow(uri)?.let { s ->
            return CollectionHeaderMeta(dominantColorArgb = s.dominantColorArgb, artworkUrl = s.artworkUrl)
        }
        dao.getAudiobook(uri)?.let { b ->
            return CollectionHeaderMeta(dominantColorArgb = b.dominantColorArgb, artworkUrl = b.artworkUrl)
        }
        return CollectionHeaderMeta()
    }

    suspend fun getDominantColorForUri(uri: String): Int = getCollectionMeta(uri).dominantColorArgb

    fun refreshCollectionInBackground(uri: String) {
        scope.launch(ioDispatcher) {
            val id = uri.substringAfterLast(":")
            when {
                uri == LIKED_TRACKS_CONTEXT_URI -> runCatching { syncEngine.syncSavedTracks() }
                uri.startsWith("spotify:playlist:") -> runCatching { syncEngine.syncPlaylistItems(id, uri) }
                uri.startsWith("spotify:album:") -> runCatching { syncEngine.syncAlbumTracks(id, uri) }
                uri.startsWith("spotify:artist:") -> runCatching { syncEngine.syncArtistTopTracks(id, uri) }
                uri.startsWith("spotify:show:") -> {
                    val showName = dao.getShow(uri)?.name ?: "Podcast"
                    runCatching { syncEngine.syncShowEpisodes(id, uri, showName) }
                }
                uri.startsWith("spotify:audiobook:") -> runCatching { syncEngine.syncAudiobookChapters(id, uri) }
            }
        }
    }

    fun toggleLibraryMembership(uri: String, currentlySaved: Boolean, onDone: (Boolean) -> Unit = {}) {
        scope.launch(ioDispatcher) {
            syncEngine.toggleLibraryItem(uri, currentlySaved).onSuccess(onDone)
        }
    }

    fun createPlaylist(name: String, description: String = "", onCreated: () -> Unit = {}) {
        scope.launch(ioDispatcher) {
            syncEngine.createPlaylist(name, description).onSuccess {
                onCreated()
            }
        }
    }
}
