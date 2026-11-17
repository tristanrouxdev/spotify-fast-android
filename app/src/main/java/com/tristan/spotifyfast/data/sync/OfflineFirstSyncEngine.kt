package com.tristan.spotifyfast.data.sync

import com.tristan.spotifyfast.auth.EncryptedTokenStore
import com.tristan.spotifyfast.data.local.AlbumEntity
import com.tristan.spotifyfast.data.local.ArtistEntity
import com.tristan.spotifyfast.data.local.AudiobookChapterEntity
import com.tristan.spotifyfast.data.local.AudiobookEntity
import com.tristan.spotifyfast.data.local.DEFAULT_DOMINANT_COLOR
import com.tristan.spotifyfast.data.local.HomeShortcutEntity
import com.tristan.spotifyfast.data.local.LIKED_TRACKS_CONTEXT_URI
import com.tristan.spotifyfast.data.local.PlaybackSnapshotEntity
import com.tristan.spotifyfast.data.local.PlaylistEntity
import com.tristan.spotifyfast.data.local.ShowEntity
import com.tristan.spotifyfast.data.local.ShowEpisodeEntity
import com.tristan.spotifyfast.data.local.SpotifyDao
import com.tristan.spotifyfast.data.local.TrackEntity
import com.tristan.spotifyfast.data.remote.CreatePlaylistRequest
import com.tristan.spotifyfast.data.remote.PlayableItemDto
import com.tristan.spotifyfast.data.remote.SearchResponseDto
import com.tristan.spotifyfast.data.remote.SpotifyWebApi
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Stale-While-Revalidate synchronization engine.
 * Reads always come directly from Room `StateFlow`; network updates run strictly post-Frame 1
 * on `Dispatchers.IO`, isolating each endpoint in `runCatching` to tolerate Spotify Developer Mode 403s.
 */
class OfflineFirstSyncEngine(
    private val dao: SpotifyDao,
    private val api: SpotifyWebApi,
    private val tokenStore: EncryptedTokenStore,
    private val paletteExtractor: ArtworkPaletteExtractor? = null,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val syncMutex = Mutex()

    suspend fun syncAll(): Result<Unit> = withContext(ioDispatcher) {
        syncMutex.withLock {
            val bundle = tokenStore.loadTokenBundle()
            if (bundle?.isDemoMode == true) {
                seedDemoDataIfNeeded()
                return@withContext Result.success(Unit)
            }
            if (bundle == null || bundle.accessToken.isBlank()) {
                return@withContext Result.success(Unit)
            }

            runCatching {
                runCatching { api.getCurrentProfile() }.getOrNull()?.let { profile ->
                    tokenStore.saveUserProfile(
                        name = profile.displayName?.takeIf { it.isNotBlank() } ?: profile.id,
                        product = profile.product ?: "premium",
                    )
                }

                val existingPlaylists = dao.getAllPlaylistsOnce().associateBy { it.id }

                val playlists = runCatching { api.getUserPlaylists(limit = 50).items }.getOrNull()
                if (playlists != null) {
                    val playlistEntities = playlists.mapIndexed { index, dto ->
                        val existing = existingPlaylists[dto.id]
                        val art = dto.images.firstOrNull()?.url
                        val color = existing?.dominantColorArgb
                            ?: paletteExtractor?.extractDominantColor(art)
                            ?: DEFAULT_DOMINANT_COLOR
                        PlaylistEntity(
                            id = dto.id,
                            uri = dto.uri,
                            name = dto.name,
                            description = dto.description,
                            ownerName = dto.owner?.displayName ?: "Spotify",
                            artworkUrl = art,
                            snapshotId = dto.snapshotId,
                            trackCount = dto.itemCount,
                            dominantColorArgb = color,
                            isPinned = existing?.isPinned ?: (index == 0),
                            addedAtEpochMs = existing?.addedAtEpochMs ?: System.currentTimeMillis(),
                            updatedAtEpochMs = System.currentTimeMillis(),
                        )
                    }
                    dao.upsertPlaylists(playlistEntities)

                    // Differential item sync: only refetch playlists whose snapshot_id changed
                    playlists.take(8).forEach { dto ->
                        val prev = existingPlaylists[dto.id]
                        if (prev == null || prev.snapshotId != dto.snapshotId) {
                            runCatching { syncPlaylistItems(dto.id, dto.uri) }
                        }
                    }
                }

                // Saved tracks (Liked Songs)
                runCatching {
                    val savedTracks = api.getSavedTracks(limit = 50).items
                    val entities = savedTracks.mapIndexed { idx, wrapper ->
                        wrapper.track.toTrackEntity(
                            contextUri = LIKED_TRACKS_CONTEXT_URI,
                            position = idx,
                            isLiked = true,
                        )
                    }
                    dao.deleteTracksForContext(LIKED_TRACKS_CONTEXT_URI)
                    dao.upsertTracks(entities)
                }

                // Saved albums
                runCatching {
                    val albums = api.getSavedAlbums(limit = 50).items
                    val entities = albums.map { saved ->
                        val art = saved.album.images.firstOrNull()?.url
                        AlbumEntity(
                            id = saved.album.id,
                            uri = saved.album.uri,
                            name = saved.album.name,
                            artistName = saved.album.artists.joinToString(", ") { it.name },
                            artworkUrl = art,
                            releaseYear = saved.album.releaseDate?.take(4).orEmpty(),
                            totalTracks = saved.album.totalTracks,
                            dominantColorArgb = paletteExtractor?.extractDominantColor(art)
                                ?: DEFAULT_DOMINANT_COLOR,
                        )
                    }
                    dao.upsertAlbums(entities)
                }

                // Saved podcasts / shows & latest episodes
                runCatching {
                    val shows = api.getSavedShows(limit = 30).items
                    val showEntities = shows.map { saved ->
                        val art = saved.show.images.firstOrNull()?.url
                        ShowEntity(
                            id = saved.show.id,
                            uri = saved.show.uri,
                            name = saved.show.name,
                            publisher = saved.show.publisher.orEmpty(),
                            description = saved.show.description.orEmpty(),
                            artworkUrl = art,
                            totalEpisodes = saved.show.totalEpisodes,
                            dominantColorArgb = paletteExtractor?.extractDominantColor(art)
                                ?: DEFAULT_DOMINANT_COLOR,
                        )
                    }
                    dao.upsertShows(showEntities)
                    shows.take(5).forEach { saved ->
                        runCatching { syncShowEpisodes(saved.show.id, saved.show.uri, saved.show.name) }
                    }
                }

                // Saved audiobooks
                runCatching {
                    val audiobooks = api.getSavedAudiobooks(limit = 20).items
                    val entities = audiobooks.map { book ->
                        val art = book.images.firstOrNull()?.url
                        AudiobookEntity(
                            id = book.id,
                            uri = book.uri,
                            name = book.name,
                            authorName = book.authors.joinToString(", ") { it.name },
                            description = book.description.orEmpty(),
                            artworkUrl = art,
                            totalChapters = book.totalChapters,
                            dominantColorArgb = paletteExtractor?.extractDominantColor(art)
                                ?: DEFAULT_DOMINANT_COLOR,
                        )
                    }
                    dao.upsertAudiobooks(entities)
                }

                // Recently played tracks & shortcuts construction
                runCatching {
                    val recent = api.getRecentlyPlayed(limit = 20).items
                    val recentEntities = recent.mapIndexed { idx, history ->
                        history.track.toTrackEntity(
                            contextUri = "spotify:recent",
                            position = idx,
                            isLiked = false,
                            isRecentPlay = true,
                            playedAtEpochMs = System.currentTimeMillis() - idx * 60_000L,
                        )
                    }
                    dao.deleteTracksForContext("spotify:recent")
                    dao.upsertTracks(recentEntities)

                    // Check liked state via unified Feb 2026 `/v1/me/library/contains` in chunks of 40
                    val uris = recentEntities.map { it.uri }.distinct()
                    uris.chunked(40).forEach { batch ->
                        runCatching {
                            val flags = api.checkLibraryContains(batch.joinToString(","))
                            batch.zip(flags).forEach { (uri, liked) ->
                                dao.updateTrackLikedFlag(uri, liked)
                            }
                        }
                    }
                }

                rebuildHomeShortcuts()
            }
        }
    }

    suspend fun syncPlaylistItems(playlistId: String, playlistUri: String) = withContext(ioDispatcher) {
        val page = api.getPlaylistItems(playlistId, limit = 50)
        val tracks = page.items.mapIndexedNotNull { idx, wrapper ->
            val item = wrapper.resolvedItem ?: return@mapIndexedNotNull null
            if (item.uri.isBlank()) return@mapIndexedNotNull null
            item.toTrackEntity(
                contextUri = playlistUri,
                position = idx,
                isLiked = false,
            )
        }
        dao.deleteTracksForContext(playlistUri)
        dao.upsertTracks(tracks)
    }

    suspend fun syncAlbumTracks(albumId: String, albumUri: String) = withContext(ioDispatcher) {
        val album = api.getAlbum(albumId)
        val art = album.images.firstOrNull()?.url
        val color = paletteExtractor?.extractDominantColor(art) ?: DEFAULT_DOMINANT_COLOR
        dao.upsertAlbums(
            listOf(
                AlbumEntity(
                    id = album.id,
                    uri = album.uri,
                    name = album.name,
                    artistName = album.artists.joinToString(", ") { it.name },
                    artworkUrl = art,
                    releaseYear = album.releaseDate?.take(4).orEmpty(),
                    totalTracks = album.tracks.total,
                    dominantColorArgb = color,
                ),
            ),
        )
        val tracks = album.tracks.items.mapIndexed { idx, dto ->
            TrackEntity(
                uri = dto.uri,
                id = dto.id ?: dto.uri,
                contextUri = albumUri,
                positionInContext = idx,
                title = dto.name,
                artistName = dto.primarySubtitle.ifBlank {
                    album.artists.joinToString(", ") { it.name }
                },
                albumName = album.name,
                artworkUrl = art,
                durationMs = dto.durationMs,
                dominantColorArgb = color,
            )
        }
        dao.deleteTracksForContext(albumUri)
        dao.upsertTracks(tracks)
    }

    suspend fun syncArtistTopTracks(artistId: String, artistUri: String) = withContext(ioDispatcher) {
        val top = api.getArtistTopTracks(artistId)
        val tracks = top.tracks.mapIndexed { idx, dto ->
            dto.toTrackEntity(contextUri = artistUri, position = idx)
        }
        dao.deleteTracksForContext(artistUri)
        dao.upsertTracks(tracks)
    }

    suspend fun syncShowEpisodes(showId: String, showUri: String, showName: String) = withContext(ioDispatcher) {
        val episodes = api.getShowEpisodes(showId, limit = 50).items
        val entities = episodes.map { ep ->
            val existing = dao.getEpisode(ep.uri)
            val remotePos = ep.resumePoint?.resumePositionMs ?: 0L
            ShowEpisodeEntity(
                uri = ep.uri,
                id = ep.id,
                showUri = showUri,
                showName = showName,
                title = ep.name,
                description = ep.description.orEmpty(),
                artworkUrl = ep.images.firstOrNull()?.url,
                durationMs = ep.durationMs,
                resumePositionMs = maxOf(existing?.resumePositionMs ?: 0L, remotePos),
                isFullyPlayed = ep.resumePoint?.fullyPlayed ?: existing?.isFullyPlayed ?: false,
                releaseDate = ep.releaseDate.orEmpty(),
            )
        }
        dao.upsertEpisodes(entities)
    }

    suspend fun syncAudiobookChapters(audiobookId: String, audiobookUri: String) = withContext(ioDispatcher) {
        val chapters = api.getAudiobookChapters(audiobookId, limit = 50).items
        val entities = chapters.map { ch ->
            AudiobookChapterEntity(
                uri = ch.uri,
                id = ch.id,
                audiobookUri = audiobookUri,
                title = ch.name,
                chapterNumber = ch.chapterNumber,
                artworkUrl = ch.images.firstOrNull()?.url,
                durationMs = ch.durationMs,
                resumePositionMs = ch.resumePoint?.resumePositionMs ?: 0L,
                isFullyPlayed = ch.resumePoint?.fullyPlayed ?: false,
            )
        }
        dao.upsertChapters(entities)
    }

    suspend fun search(query: String, limit: Int = 10): Result<SearchResponseDto> = withContext(ioDispatcher) {
        runCatching {
            val bundle = tokenStore.loadTokenBundle()
            if (bundle?.isDemoMode == true) {
                return@runCatching buildDemoSearchResponse(query)
            }
            api.search(query = query, limit = limit)
        }
    }

    suspend fun toggleLibraryItem(uri: String, currentlySaved: Boolean): Result<Boolean> = withContext(ioDispatcher) {
        runCatching {
            val targetSaved = !currentlySaved
            val bundle = tokenStore.loadTokenBundle()
            if (bundle?.isDemoMode != true) {
                if (targetSaved) {
                    api.saveToLibrary(uri)
                } else {
                    api.removeFromLibrary(uri)
                }
            }
            dao.updateTrackLikedFlag(uri, targetSaved)
            targetSaved
        }
    }

    suspend fun checkLibraryContainsBatch(uris: List<String>): Map<String, Boolean> = withContext(ioDispatcher) {
        if (uris.isEmpty()) return@withContext emptyMap()
        val result = mutableMapOf<String, Boolean>()
        uris.chunked(40).forEach { chunk ->
            runCatching {
                val flags = api.checkLibraryContains(chunk.joinToString(","))
                chunk.zip(flags).forEach { (uri, saved) ->
                    result[uri] = saved
                }
            }
        }
        result
    }

    suspend fun createPlaylist(name: String, description: String = ""): Result<PlaylistEntity> =
        withContext(ioDispatcher) {
            runCatching {
                val cleanName = name.trim()
                require(cleanName.isNotEmpty()) { "Nom de playlist requis" }
                val bundle = tokenStore.loadTokenBundle()
                val entity = if (bundle?.isDemoMode == true) {
                    val id = "local_${System.currentTimeMillis()}"
                    PlaylistEntity(
                        id = id,
                        uri = "spotify:playlist:$id",
                        name = cleanName,
                        description = description,
                        ownerName = "Vous",
                    )
                } else {
                    val userId = api.getCurrentProfile().id
                    val created = api.createPlaylist(
                        userId = userId,
                        body = CreatePlaylistRequest(name = cleanName, description = description, public = false),
                    )
                    PlaylistEntity(
                        id = created.id,
                        uri = created.uri,
                        name = created.name,
                        description = created.description,
                        ownerName = created.owner?.displayName ?: userId,
                        artworkUrl = created.images.firstOrNull()?.url,
                        snapshotId = created.snapshotId,
                        trackCount = created.itemCount,
                    )
                }
                dao.upsertPlaylists(listOf(entity))
                rebuildHomeShortcuts()
                entity
            }
        }

    private suspend fun rebuildHomeShortcuts() {
        val shortcuts = mutableListOf<HomeShortcutEntity>()
        shortcuts += HomeShortcutEntity(
            uri = LIKED_TRACKS_CONTEXT_URI,
            title = "Titres likés",
            subtitle = "Playlist",
            artworkUrl = null,
            category = "MUSIC",
            dominantColorArgb = 0xFF450AF5.toInt(),
            rank = 0,
        )
        var rank = 1
        dao.getAllPlaylistsOnce().take(3).forEach { p ->
            shortcuts += HomeShortcutEntity(
                uri = p.uri,
                title = p.name,
                subtitle = "Playlist • ${p.ownerName}",
                artworkUrl = p.artworkUrl,
                category = "MUSIC",
                dominantColorArgb = p.dominantColorArgb,
                rank = rank++,
            )
        }
        dao.observeAlbums().first().take(2).forEach { a ->
            if (shortcuts.size < 6) {
                shortcuts += HomeShortcutEntity(
                    uri = a.uri,
                    title = a.name,
                    subtitle = "Album • ${a.artistName}",
                    artworkUrl = a.artworkUrl,
                    category = "MUSIC",
                    dominantColorArgb = a.dominantColorArgb,
                    rank = rank++,
                )
            }
        }
        dao.observeShows().first().take(2).forEach { s ->
            if (shortcuts.size < 6) {
                shortcuts += HomeShortcutEntity(
                    uri = s.uri,
                    title = s.name,
                    subtitle = "Podcast • ${s.publisher}",
                    artworkUrl = s.artworkUrl,
                    category = "PODCAST",
                    dominantColorArgb = s.dominantColorArgb,
                    rank = rank++,
                )
            }
        }
        dao.clearHomeShortcuts()
        dao.upsertHomeShortcuts(shortcuts.take(6))
    }

    suspend fun seedDemoDataIfNeeded() = withContext(ioDispatcher) {
        if (dao.getAllPlaylistsOnce().isNotEmpty()) return@withContext
        val demoShortcuts = listOf(
            HomeShortcutEntity(LIKED_TRACKS_CONTEXT_URI, "Titres likés", "142 titres", null, "MUSIC", 0xFF450AF5.toInt(), 0),
            HomeShortcutEntity("spotify:playlist:demo_synth", "Night Drive Synth", "Playlist", null, "MUSIC", 0xFF1E3264.toInt(), 1),
            HomeShortcutEntity("spotify:playlist:demo_focus", "Deep Focus Flow", "Playlist", null, "MUSIC", 0xFF1DB954.toInt(), 2),
            HomeShortcutEntity("spotify:show:demo_tech", "Tech & Architecture", "Podcast", null, "PODCAST", 0xFFE13300.toInt(), 3),
            HomeShortcutEntity("spotify:album:demo_midnight", "Midnight Horizon", "Album", null, "MUSIC", 0xFF8D67AB.toInt(), 4),
            HomeShortcutEntity("spotify:audiobook:demo_clean", "Clean Code Audio", "Livre audio", null, "PODCAST", 0xFF503750.toInt(), 5),
        )
        dao.upsertHomeShortcuts(demoShortcuts)
        dao.upsertPlaylists(
            listOf(
                PlaylistEntity("demo_synth", "spotify:playlist:demo_synth", "Night Drive Synth", "Sélection électronique nocturne", "Tristan", null, "snap1", 24, 0xFF1E3264.toInt(), true),
                PlaylistEntity("demo_focus", "spotify:playlist:demo_focus", "Deep Focus Flow", "Instrumentaux & Ambient", "Tristan", null, "snap2", 40, 0xFF1DB954.toInt(), false),
            ),
        )
        val demoTracks = listOf(
            TrackEntity("spotify:track:demo1", "demo1", LIKED_TRACKS_CONTEXT_URI, 0, "Midnight City", "M83", "Hurry Up, We're Dreaming", null, 243_000L, isLiked = true, isRecentPlay = true, playedAtEpochMs = System.currentTimeMillis(), dominantColorArgb = 0xFF1E3264.toInt()),
            TrackEntity("spotify:track:demo2", "demo2", LIKED_TRACKS_CONTEXT_URI, 1, "Resonance", "HOME", "Odyssey", null, 212_000L, isLiked = true, isRecentPlay = true, playedAtEpochMs = System.currentTimeMillis() - 60_000L, dominantColorArgb = 0xFF8D67AB.toInt()),
            TrackEntity("spotify:track:demo1", "demo1", "spotify:playlist:demo_synth", 0, "Midnight City", "M83", "Hurry Up, We're Dreaming", null, 243_000L, isLiked = true),
            TrackEntity("spotify:track:demo2", "demo2", "spotify:playlist:demo_synth", 1, "Resonance", "HOME", "Odyssey", null, 212_000L, isLiked = true),
        )
        dao.upsertTracks(demoTracks)
        dao.upsertAlbums(
            listOf(
                AlbumEntity("demo_midnight", "spotify:album:demo_midnight", "Midnight Horizon", "Nova Echo", null, "2025", 10, 0xFF8D67AB.toInt()),
            ),
        )
        dao.upsertArtists(
            listOf(
                ArtistEntity("demo_m83", "spotify:artist:demo_m83", "M83", null, "Synthwave, Electronic", 0xFF1E3264.toInt()),
            ),
        )
        dao.upsertShows(
            listOf(
                ShowEntity("demo_tech", "spotify:show:demo_tech", "Tech & Architecture", "Studio Dev", "Ingénierie Android & systèmes distribués", null, 18, 0xFFE13300.toInt()),
            ),
        )
        dao.upsertEpisodes(
            listOf(
                ShowEpisodeEntity("spotify:episode:demo_ep1", "demo_ep1", "spotify:show:demo_tech", "Tech & Architecture", "Offline-First Mobile Architecture", "Room + Baseline Profiles < 300ms", null, 2_400_000L, 640_000L, false, "2026-11-10"),
                ShowEpisodeEntity("spotify:episode:demo_ep2", "demo_ep2", "spotify:show:demo_tech", "Tech & Architecture", "Kotlin Coroutines & IPC Binder", "Low-latency audio pipelines", null, 1_950_000L, 0L, false, "2026-11-02"),
            ),
        )
        dao.upsertAudiobooks(
            listOf(
                AudiobookEntity("demo_clean", "spotify:audiobook:demo_clean", "Clean Code Audio", "Robert C. Martin", "Principes d'architecture logicielle", null, 12, 0xFF503750.toInt()),
            ),
        )
        dao.upsertChapters(
            listOf(
                AudiobookChapterEntity("spotify:chapter:demo_ch1", "demo_ch1", "spotify:audiobook:demo_clean", "Chapitre 1 : Fonctions propres", 1, null, 1_200_000L, 320_000L, false),
            ),
        )
        dao.upsertPlaybackSnapshot(
            PlaybackSnapshotEntity(
                itemUri = "spotify:track:demo1",
                title = "Midnight City",
                subtitle = "M83",
                albumName = "Hurry Up, We're Dreaming",
                artworkUrl = null,
                contextUri = "spotify:playlist:demo_synth",
                isEpisodeOrChapter = false,
                isPlaying = false,
                positionMs = 42_000L,
                durationMs = 243_000L,
                isSavedInLibrary = true,
                dominantColorArgb = 0xFF1E3264.toInt(),
            ),
        )
    }

    private fun buildDemoSearchResponse(query: String): SearchResponseDto {
        val q = query.lowercase()
        val demoTracks = listOf(
            PlayableItemDto("demo1", "spotify:track:demo1", "Midnight City", "track", 243_000L),
            PlayableItemDto("demo2", "spotify:track:demo2", "Resonance", "track", 212_000L),
        ).filter { it.name.lowercase().contains(q) }
        return SearchResponseDto(
            tracks = com.tristan.spotifyfast.data.remote.PagingDto(items = demoTracks, total = demoTracks.size),
        )
    }

    private fun PlayableItemDto.toTrackEntity(
        contextUri: String,
        position: Int,
        isLiked: Boolean = false,
        isRecentPlay: Boolean = false,
        playedAtEpochMs: Long = 0L,
    ): TrackEntity = TrackEntity(
        uri = uri,
        id = id ?: uri,
        contextUri = contextUri,
        positionInContext = position,
        title = name,
        artistName = primarySubtitle,
        albumName = album?.name ?: show?.name.orEmpty(),
        artworkUrl = artworkUrl,
        durationMs = durationMs,
        isLiked = isLiked,
        isRecentPlay = isRecentPlay,
        playedAtEpochMs = playedAtEpochMs,
    )
}
