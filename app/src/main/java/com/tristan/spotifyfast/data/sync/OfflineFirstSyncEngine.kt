package com.tristan.spotifyfast.data.sync

import android.util.Log
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
import com.tristan.spotifyfast.data.remote.CursorPagingDto
import com.tristan.spotifyfast.data.remote.PagingDto
import com.tristan.spotifyfast.data.remote.PlayableItemDto
import com.tristan.spotifyfast.data.remote.PlaylistItemWrapperDto
import com.tristan.spotifyfast.data.remote.SearchResponseDto
import com.tristan.spotifyfast.data.remote.SpotifyWebApi
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import retrofit2.HttpException

sealed interface SyncStatus {
    data object Idle : SyncStatus
    data class Syncing(val message: String = "Synchronisation en cours...") : SyncStatus
    data class Success(
        val playlists: Int,
        val likedTracks: Int,
        val albums: Int,
        val shows: Int,
        val artists: Int,
    ) : SyncStatus
    data class Error(val message: String) : SyncStatus
}

class PlaylistRestrictedException(
    val playlistId: String,
    val detail: String,
    cause: Throwable? = null,
) : IllegalStateException("Playlist $playlistId restreinte ($detail)", cause)

internal data class NextPageParams(
    val offset: Int?,
    val limit: Int?,
)

/**
 * Stale-While-Revalidate synchronization engine with full offset & cursor pagination across
 * all collections (700+ liked tracks, large playlists, albums, artists, shows, audiobooks).
 * Reads always come directly from Room `StateFlow`; network updates run off Frame 1 on `Dispatchers.IO`.
 */
class OfflineFirstSyncEngine(
    private val dao: SpotifyDao,
    private val api: SpotifyWebApi,
    private val tokenStore: EncryptedTokenStore,
    private val paletteExtractor: ArtworkPaletteExtractor? = null,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val syncMutex = Mutex()
    private val _syncStatus = MutableStateFlow<SyncStatus>(SyncStatus.Idle)
    val syncStatus: StateFlow<SyncStatus> = _syncStatus.asStateFlow()

    private val _restrictedPlaylistUris = MutableStateFlow<Set<String>>(emptySet())
    val restrictedPlaylistUris: StateFlow<Set<String>> = _restrictedPlaylistUris.asStateFlow()

    private val _followedArtistsPermissionMissing = MutableStateFlow(false)
    val followedArtistsPermissionMissing: StateFlow<Boolean> = _followedArtistsPermissionMissing.asStateFlow()

    private fun logInfo(msg: String) {
        runCatching { Log.i(LOG_TAG, msg) }
    }

    private fun logWarn(msg: String) {
        runCatching { Log.w(LOG_TAG, msg) }
    }

    private fun logError(msg: String, err: Throwable? = null) {
        runCatching {
            if (err != null) Log.e(LOG_TAG, msg, err) else Log.e(LOG_TAG, msg)
        }
    }

    private fun describeHttpFailure(err: Throwable): String {
        return if (err is HttpException) {
            val code = err.code()
            val body = runCatching { err.response()?.errorBody()?.string() }.getOrNull().orEmpty()
            "HTTP $code ${body.take(240)}".trim()
        } else {
            err.message ?: err.javaClass.simpleName
        }
    }

    private fun isForbiddenOrUnauthorized(err: Throwable?): Boolean {
        if (err is HttpException) {
            val code = err.code()
            return code == 401 || code == 403
        }
        return false
    }

    internal fun parseNextPageParams(nextUrl: String): NextPageParams {
        val query = nextUrl.substringAfter('?', "")
        if (query.isBlank()) return NextPageParams(offset = null, limit = null)
        var offset: Int? = null
        var limit: Int? = null
        query.split('&').forEach { param ->
            val eqIdx = param.indexOf('=')
            if (eqIdx > 0) {
                val key = param.substring(0, eqIdx)
                val value = param.substring(eqIdx + 1)
                when (key) {
                    "offset" -> offset = value.toIntOrNull()
                    "limit" -> limit = value.toIntOrNull()
                }
            }
        }
        return NextPageParams(offset = offset, limit = limit)
    }

    private suspend fun <T> collectAllPages(
        initialLimit: Int = PAGE_LIMIT,
        maxPages: Int = MAX_PAGINATION_PAGES,
        onPageFetched: (fetchedCount: Int, totalCount: Int) -> Unit = { _, _ -> },
        fetchPage: suspend (limit: Int, offset: Int) -> PagingDto<T>,
    ): List<T> {
        val collected = mutableListOf<T>()
        var offset = 0
        var limit = initialLimit
        var pagesFetched = 0
        while (pagesFetched < maxPages) {
            val page = fetchPage(limit, offset)
            pagesFetched++
            collected += page.items
            val reportedTotal = maxOf(page.total, collected.size)
            onPageFetched(collected.size, reportedTotal)

            val rawCount = page.rawItems.size
            if (rawCount == 0) break

            val nextUrl = page.next
            val hasMoreByNext = !nextUrl.isNullOrBlank()
            val hasMoreByTotal = page.total > offset + rawCount
            if (!hasMoreByNext && !hasMoreByTotal) break

            val nextParams = nextUrl?.let(::parseNextPageParams)
            val nextOffset = nextParams?.offset ?: (offset + rawCount)
            if (nextOffset <= offset) break
            offset = nextOffset
            limit = (nextParams?.limit ?: page.limit.takeIf { it > 0 } ?: limit).coerceIn(1, 100)
        }
        return collected
    }

    private suspend fun <T> collectCursorPages(
        initialLimit: Int = PAGE_LIMIT,
        maxPages: Int = MAX_PAGINATION_PAGES,
        fetchPage: suspend (limit: Int, after: String?) -> CursorPagingDto<T>,
    ): List<T> {
        val collected = mutableListOf<T>()
        var after: String? = null
        var pagesFetched = 0
        while (pagesFetched < maxPages) {
            val page = fetchPage(initialLimit, after)
            pagesFetched++
            collected += page.items
            val nextAfter = page.cursors?.after?.takeIf { it.isNotBlank() }
                ?: page.next?.let { nextUrl ->
                    nextUrl.substringAfter('?', "")
                        .split('&')
                        .firstOrNull { it.startsWith("after=") }
                        ?.substringAfter("after=")
                        ?.takeIf { it.isNotBlank() }
                }
            if (nextAfter.isNullOrBlank() || nextAfter == after || page.rawItems.isEmpty()) break
            after = nextAfter
        }
        return collected
    }

    suspend fun syncAll(): Result<Unit> = withContext(ioDispatcher) {
        syncMutex.withLock {
            val bundle = tokenStore.loadTokenBundle()
            if (bundle?.isDemoMode == true) {
                seedDemoDataIfNeeded()
                _syncStatus.value = SyncStatus.Success(
                    playlists = dao.getAllPlaylistsOnce().size,
                    likedTracks = dao.observeLikedTracksCount().first(),
                    albums = dao.observeAlbums().first().size,
                    shows = dao.observeShows().first().size,
                    artists = dao.observeArtists().first().size,
                )
                return@withContext Result.success(Unit)
            }
            if (bundle == null || bundle.accessToken.isBlank()) {
                return@withContext Result.success(Unit)
            }

            _syncStatus.value = SyncStatus.Syncing("Connexion à Spotify...")
            logInfo("Starting full paginated syncAll() for user library & playlists")

            runCatching {
                runCatching { api.getCurrentProfile() }
                    .onFailure { logError("Sync getCurrentProfile failed: ${describeHttpFailure(it)}", it) }
                    .getOrNull()
                    ?.let { profile ->
                        tokenStore.saveUserProfile(
                            name = profile.displayName?.takeIf { it.isNotBlank() } ?: profile.id,
                            product = profile.product ?: "premium",
                        )
                    }

                // 1. Saved tracks (Liked Songs) synced first with full pagination so playlists reuse liked flags in O(1)
                val likedUris = syncSavedTracksInternal()

                // 2. All User Playlists (full pagination, no 50-item or 20-playlist cap)
                _syncStatus.value = SyncStatus.Syncing("Synchronisation des playlists...")
                val existingPlaylists = dao.getAllPlaylistsOnce().associateBy { it.id }
                val playlists = runCatching {
                    collectAllPages(
                        onPageFetched = { count, total ->
                            val label = if (total > count) "$count / $total" else "$count"
                            _syncStatus.value = SyncStatus.Syncing("Playlists : $label...")
                        },
                    ) { limit, offset ->
                        api.getUserPlaylists(limit = limit, offset = offset)
                    }
                }
                    .onFailure { logError("Sync getUserPlaylists failed: ${describeHttpFailure(it)}", it) }
                    .getOrNull()

                if (playlists != null) {
                    val validPlaylists = playlists
                        .filter { it.id.isNotBlank() && it.uri.isNotBlank() }
                        .distinctBy { it.id }
                    logInfo("Synced ${validPlaylists.size} playlists metadata from API")

                    val playlistEntities = validPlaylists.mapIndexed { index, dto ->
                        val existing = existingPlaylists[dto.id]
                        val art = dto.images.firstOrNull()?.url?.takeIf { it.isNotBlank() }
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
                            trackCount = maxOf(dto.itemCount, existing?.trackCount ?: 0),
                            dominantColorArgb = color,
                            isPinned = existing?.isPinned ?: (index == 0),
                            addedAtEpochMs = existing?.addedAtEpochMs ?: System.currentTimeMillis(),
                            updatedAtEpochMs = System.currentTimeMillis(),
                        )
                    }
                    dao.upsertPlaylists(playlistEntities)

                    val forceFullSchemaResync = tokenStore.getSyncSchemaVersion() < SYNC_SCHEMA_VERSION
                    var allAccessiblePlaylistsSyncedCleanly = true

                    validPlaylists.forEachIndexed { idx, dto ->
                        val prev = existingPlaylists[dto.id]
                        val cachedCount = dao.getTracksCountForContext(dto.uri)
                        val expectedTotal = dto.itemCount
                        val missingAllTracks = expectedTotal > 0 && cachedCount == 0
                        val incompleteCache = expectedTotal > cachedCount &&
                            (forceFullSchemaResync || cachedCount <= PAGE_LIMIT || prev?.trackCount != expectedTotal)
                        val legacyCapHitOnUpgrade = forceFullSchemaResync && cachedCount == PAGE_LIMIT
                        val needsSync = prev == null ||
                            prev.snapshotId != dto.snapshotId ||
                            missingAllTracks ||
                            incompleteCache ||
                            legacyCapHitOnUpgrade

                        if (needsSync) {
                            _syncStatus.value = SyncStatus.Syncing(
                                "Playlist ${idx + 1}/${validPlaylists.size} : ${dto.name}",
                            )
                            runCatching {
                                syncPlaylistItemsInternal(
                                    playlistId = dto.id,
                                    playlistUri = dto.uri,
                                    likedUriSet = likedUris,
                                    expectedName = dto.name,
                                    reportedTotal = expectedTotal,
                                )
                            }.onFailure { err ->
                                if (err is PlaylistRestrictedException) {
                                    logWarn("Playlist '${dto.name}' (${dto.id}) restricted by Spotify Dev Mode: ${err.detail}")
                                } else {
                                    allAccessiblePlaylistsSyncedCleanly = false
                                    logError("Sync playlist items failed (${dto.name} / ${dto.id}): ${describeHttpFailure(err)}", err)
                                }
                            }
                        }
                    }

                    if (forceFullSchemaResync && allAccessiblePlaylistsSyncedCleanly) {
                        tokenStore.setSyncSchemaVersion(SYNC_SCHEMA_VERSION)
                    }
                }

                // 3. Saved Albums (full pagination)
                _syncStatus.value = SyncStatus.Syncing("Albums favoris...")
                runCatching {
                    val existingAlbums = dao.observeAlbums().first().associateBy { it.id }
                    val albums = collectAllPages { limit, offset ->
                        api.getSavedAlbums(limit = limit, offset = offset)
                    }
                    val entities = albums.mapNotNull { saved ->
                        val album = saved.album ?: return@mapNotNull null
                        if (album.id.isBlank()) return@mapNotNull null
                        val art = album.images.firstOrNull()?.url?.takeIf { it.isNotBlank() }
                        val prev = existingAlbums[album.id]
                        AlbumEntity(
                            id = album.id,
                            uri = album.uri,
                            name = album.name,
                            artistName = album.artists.joinToString(", ") { it.name },
                            artworkUrl = art,
                            releaseYear = album.releaseDate?.take(4).orEmpty(),
                            totalTracks = album.totalTracks,
                            dominantColorArgb = prev?.dominantColorArgb
                                ?: paletteExtractor?.extractDominantColor(art)
                                ?: DEFAULT_DOMINANT_COLOR,
                        )
                    }.distinctBy { it.id }
                    dao.upsertAlbums(entities)
                    logInfo("Synced ${entities.size} saved albums")
                }.onFailure { logError("Sync getSavedAlbums failed: ${describeHttpFailure(it)}", it) }

                // 4. Followed Artists (full cursor pagination)
                runCatching {
                    val existingArtists = dao.observeArtists().first().associateBy { it.id }
                    val followed = collectCursorPages { limit, after ->
                        api.getFollowedArtists(limit = limit, after = after).artists
                    }
                    _followedArtistsPermissionMissing.value = false
                    val entities = followed.filter { it.id.isNotBlank() }.distinctBy { it.id }.map { artist ->
                        val art = artist.images.firstOrNull()?.url?.takeIf { it.isNotBlank() }
                        val prev = existingArtists[artist.id]
                        ArtistEntity(
                            id = artist.id,
                            uri = artist.uri,
                            name = artist.name,
                            artworkUrl = art,
                            genresCsv = artist.genres.joinToString(", "),
                            dominantColorArgb = prev?.dominantColorArgb
                                ?: paletteExtractor?.extractDominantColor(art)
                                ?: DEFAULT_DOMINANT_COLOR,
                        )
                    }
                    dao.upsertArtists(entities)
                    logInfo("Synced ${entities.size} followed artists")
                }.onFailure { err ->
                    if (isForbiddenOrUnauthorized(err)) {
                        _followedArtistsPermissionMissing.value = true
                        logWarn("getFollowedArtists HTTP 403 (scope user-follow-read absent du token actuel)")
                    } else {
                        logError("Sync getFollowedArtists failed: ${describeHttpFailure(err)}", err)
                    }
                }

                // 5. Saved Shows & Episodes (full pagination)
                runCatching {
                    val existingShows = dao.observeShows().first().associateBy { it.id }
                    val shows = collectAllPages { limit, offset ->
                        api.getSavedShows(limit = limit, offset = offset)
                    }
                    val showEntities = shows.mapNotNull { saved ->
                        val show = saved.show ?: return@mapNotNull null
                        if (show.id.isBlank()) return@mapNotNull null
                        val art = show.images.firstOrNull()?.url?.takeIf { it.isNotBlank() }
                        val prev = existingShows[show.id]
                        ShowEntity(
                            id = show.id,
                            uri = show.uri,
                            name = show.name,
                            publisher = show.publisher.orEmpty(),
                            description = show.description.orEmpty(),
                            artworkUrl = art,
                            totalEpisodes = show.totalEpisodes,
                            dominantColorArgb = prev?.dominantColorArgb
                                ?: paletteExtractor?.extractDominantColor(art)
                                ?: DEFAULT_DOMINANT_COLOR,
                        )
                    }.distinctBy { it.id }
                    dao.upsertShows(showEntities)
                    shows.take(10).forEach { saved ->
                        val show = saved.show ?: return@forEach
                        runCatching { syncShowEpisodes(show.id, show.uri, show.name, maxPages = 4) }
                            .onFailure { logError("Sync episodes failed (${show.id}): ${describeHttpFailure(it)}", it) }
                    }
                    logInfo("Synced ${showEntities.size} saved shows")
                }.onFailure { logError("Sync getSavedShows failed: ${describeHttpFailure(it)}", it) }

                // 6. Saved Audiobooks (full pagination)
                runCatching {
                    val existingBooks = dao.observeAudiobooks().first().associateBy { it.id }
                    val audiobooks = collectAllPages { limit, offset ->
                        api.getSavedAudiobooks(limit = limit, offset = offset)
                    }
                    val entities = audiobooks.filter { it.id.isNotBlank() }.distinctBy { it.id }.map { book ->
                        val art = book.images.firstOrNull()?.url?.takeIf { it.isNotBlank() }
                        val prev = existingBooks[book.id]
                        AudiobookEntity(
                            id = book.id,
                            uri = book.uri,
                            name = book.name,
                            authorName = book.authors.joinToString(", ") { it.name },
                            description = book.description.orEmpty(),
                            artworkUrl = art,
                            totalChapters = book.totalChapters,
                            dominantColorArgb = prev?.dominantColorArgb
                                ?: paletteExtractor?.extractDominantColor(art)
                                ?: DEFAULT_DOMINANT_COLOR,
                        )
                    }
                    dao.upsertAudiobooks(entities)
                    logInfo("Synced ${entities.size} saved audiobooks")
                }.onFailure { logError("Sync getSavedAudiobooks failed: ${describeHttpFailure(it)}", it) }

                // 7. Recently Played Tracks & shortcuts
                runCatching {
                    val recent = api.getRecentlyPlayed(limit = PAGE_LIMIT).items
                    val recentEntities = recent.mapIndexedNotNull { idx, history ->
                        val track = history.track ?: return@mapIndexedNotNull null
                        if (track.uri.isBlank()) return@mapIndexedNotNull null
                        track.toTrackEntity(
                            contextUri = "spotify:recent",
                            position = idx,
                            isLiked = track.uri in likedUris,
                            isRecentPlay = true,
                            playedAtEpochMs = System.currentTimeMillis() - idx * 60_000L,
                        )
                    }
                    dao.replaceTracksForContext("spotify:recent", recentEntities)

                    val uncheckedUris = recentEntities.map { it.uri }.distinct().filter { it !in likedUris }
                    uncheckedUris.chunked(40).forEach { batch ->
                        runCatching {
                            val flags = api.checkLibraryContains(batch.joinToString(","))
                            batch.zip(flags).forEach { (uri, liked) ->
                                dao.updateTrackLikedFlag(uri, liked)
                            }
                        }.onFailure { logError("Sync checkLibraryContains failed: ${describeHttpFailure(it)}", it) }
                    }
                    logInfo("Synced ${recentEntities.size} recently played tracks")
                }.onFailure { logError("Sync getRecentlyPlayed failed: ${describeHttpFailure(it)}", it) }

                rebuildHomeShortcuts()

                val status = SyncStatus.Success(
                    playlists = dao.getAllPlaylistsOnce().size,
                    likedTracks = dao.observeLikedTracksCount().first(),
                    albums = dao.observeAlbums().first().size,
                    shows = dao.observeShows().first().size,
                    artists = dao.observeArtists().first().size,
                )
                _syncStatus.value = status
                logInfo(
                    "syncAll completed: playlists=${status.playlists}, likedTracks=${status.likedTracks}, " +
                        "albums=${status.albums}, shows=${status.shows}, artists=${status.artists}",
                )
            }.onFailure { err ->
                logError("syncAll top-level failure: ${describeHttpFailure(err)}", err)
                _syncStatus.value = SyncStatus.Error(err.message ?: "Erreur de synchronisation")
            }
        }
    }

    suspend fun syncSavedTracks(): Set<String> = withContext(ioDispatcher) {
        syncSavedTracksInternal()
    }

    private suspend fun syncSavedTracksInternal(): Set<String> {
        return runCatching {
            val savedTracks = collectAllPages(
                onPageFetched = { count, total ->
                    val label = if (total > count) "$count / $total" else "$count"
                    _syncStatus.value = SyncStatus.Syncing("Titres likés : $label...")
                },
            ) { limit, offset ->
                api.getSavedTracks(limit = limit, offset = offset)
            }
            val entities = savedTracks.mapIndexedNotNull { idx, wrapper ->
                val track = wrapper.resolvedTrack ?: return@mapIndexedNotNull null
                if (track.uri.isBlank()) return@mapIndexedNotNull null
                track.toTrackEntity(
                    contextUri = LIKED_TRACKS_CONTEXT_URI,
                    position = idx,
                    isLiked = true,
                )
            }
            dao.replaceTracksForContext(LIKED_TRACKS_CONTEXT_URI, entities)
            logInfo("Synced ${entities.size} liked tracks (all pages)")
            entities.mapTo(HashSet(entities.size)) { it.uri }
        }.onFailure {
            logError("Sync getSavedTracks failed: ${describeHttpFailure(it)}", it)
        }.getOrElse {
            dao.getTracksForContextOnce(LIKED_TRACKS_CONTEXT_URI).mapTo(HashSet()) { it.uri }
        }
    }

    suspend fun syncPlaylistItems(playlistId: String, playlistUri: String) = withContext(ioDispatcher) {
        val likedUriSet = dao.getTracksForContextOnce(LIKED_TRACKS_CONTEXT_URI)
            .mapTo(HashSet()) { it.uri }
        val existing = dao.getPlaylist(playlistUri)
        val name = existing?.name ?: playlistId
        syncPlaylistItemsInternal(
            playlistId = playlistId,
            playlistUri = playlistUri,
            likedUriSet = likedUriSet,
            expectedName = name,
            reportedTotal = existing?.trackCount ?: 0,
        )
    }

    private suspend fun syncPlaylistItemsInternal(
        playlistId: String,
        playlistUri: String,
        likedUriSet: Set<String>,
        expectedName: String,
        reportedTotal: Int = 0,
    ) {
        var lastError: Throwable? = null

        val itemsFromPrimary = runCatching {
            collectAllPages(
                onPageFetched = { count, total ->
                    val label = if (total > count) "$count / $total" else "$count"
                    _syncStatus.value = SyncStatus.Syncing("$expectedName : $label titres...")
                },
            ) { limit, offset ->
                api.getPlaylistItems(playlistId, limit = limit, offset = offset)
            }
        }.onFailure { err ->
            lastError = err
            logWarn("getPlaylistItems($playlistId) failed (${describeHttpFailure(err)}), trying fallbacks")
        }.getOrNull()

        val itemsFromTracks = if (!itemsFromPrimary.isNullOrEmpty()) {
            null
        } else {
            runCatching {
                collectAllPages(
                    onPageFetched = { count, total ->
                        val label = if (total > count) "$count / $total" else "$count"
                        _syncStatus.value = SyncStatus.Syncing("$expectedName : $label titres...")
                    },
                ) { limit, offset ->
                    api.getPlaylistTracks(playlistId, limit = limit, offset = offset)
                }
            }.onFailure { err ->
                lastError = err
                logWarn("getPlaylistTracks($playlistId) fallback failed (${describeHttpFailure(err)})")
            }.getOrNull()
        }

        val itemsFromPlaylistObject = if (!itemsFromPrimary.isNullOrEmpty() || !itemsFromTracks.isNullOrEmpty()) {
            null
        } else {
            runCatching {
                fetchPlaylistObjectItemsAllPages(playlistId, expectedName)
            }.onFailure { err ->
                lastError = err
                logWarn("getPlaylist($playlistId) container fallback failed (${describeHttpFailure(err)})")
            }.getOrNull()
        }

        val allWrappers = when {
            !itemsFromPrimary.isNullOrEmpty() -> itemsFromPrimary
            !itemsFromTracks.isNullOrEmpty() -> itemsFromTracks
            !itemsFromPlaylistObject.isNullOrEmpty() -> itemsFromPlaylistObject
            itemsFromPrimary != null -> itemsFromPrimary
            itemsFromTracks != null -> itemsFromTracks
            itemsFromPlaylistObject != null -> itemsFromPlaylistObject
            else -> {
                val err = lastError
                if (isForbiddenOrUnauthorized(err)) {
                    _restrictedPlaylistUris.value = _restrictedPlaylistUris.value + playlistUri
                    throw PlaylistRestrictedException(
                        playlistId = playlistId,
                        detail = describeHttpFailure(err!!),
                        cause = err,
                    )
                }
                throw IllegalStateException(
                    "Impossible de récupérer les titres de la playlist $playlistId (${err?.let(::describeHttpFailure) ?: "unknown"})",
                    err,
                )
            }
        }

        _restrictedPlaylistUris.value = _restrictedPlaylistUris.value - playlistUri

        val tracks = allWrappers.mapIndexedNotNull { idx, wrapper ->
            val item = wrapper.resolvedItem ?: return@mapIndexedNotNull null
            if (item.uri.isBlank()) return@mapIndexedNotNull null
            item.toTrackEntity(
                contextUri = playlistUri,
                position = idx,
                isLiked = item.uri in likedUriSet,
            )
        }
        dao.replaceTracksForContext(playlistUri, tracks)
        val resolvedCount = maxOf(reportedTotal, tracks.size)
        if (resolvedCount > 0) {
            dao.updatePlaylistTrackCount(playlistUri, resolvedCount)
        }
        logInfo("Synced ${tracks.size} tracks for playlist $playlistId ($expectedName)")
    }

    private suspend fun fetchPlaylistObjectItemsAllPages(
        playlistId: String,
        expectedName: String,
    ): List<PlaylistItemWrapperDto> {
        val full = api.getPlaylist(playlistId)
        val firstPaging = full.resolvedPaging
            ?: throw IllegalStateException("Playlist $playlistId has no items/tracks container")
        val collected = firstPaging.items.toMutableList()
        var nextUrl = firstPaging.next
        var pageCount = 1
        while (!nextUrl.isNullOrBlank() && pageCount < MAX_PAGINATION_PAGES) {
            val nextPage = api.getPlaylistItemsByUrl(nextUrl)
            pageCount++
            collected += nextPage.items
            val total = maxOf(firstPaging.total, nextPage.total, collected.size)
            _syncStatus.value = SyncStatus.Syncing("$expectedName : ${collected.size} / $total titres...")
            if (nextPage.rawItems.isEmpty() || nextPage.next == nextUrl) break
            nextUrl = nextPage.next
        }
        return collected
    }

    suspend fun syncAlbumTracks(albumId: String, albumUri: String) = withContext(ioDispatcher) {
        val album = api.getAlbum(albumId)
        val art = album.images.firstOrNull()?.url
        val color = dao.getAlbum(albumUri)?.dominantColorArgb
            ?: paletteExtractor?.extractDominantColor(art)
            ?: DEFAULT_DOMINANT_COLOR
        val artistName = album.artists.joinToString(", ") { it.name }
        dao.upsertAlbums(
            listOf(
                AlbumEntity(
                    id = album.id,
                    uri = album.uri,
                    name = album.name,
                    artistName = artistName,
                    artworkUrl = art,
                    releaseYear = album.releaseDate?.take(4).orEmpty(),
                    totalTracks = album.tracks.total,
                    dominantColorArgb = color,
                ),
            ),
        )

        val allTrackDtos = mutableListOf<PlayableItemDto>()
        allTrackDtos += album.tracks.items
        val firstPage = album.tracks
        val hasMore = !firstPage.next.isNullOrBlank() || firstPage.total > firstPage.rawItems.size
        if (hasMore && firstPage.rawItems.isNotEmpty()) {
            val startOffset = firstPage.rawItems.size
            var offset = startOffset
            var pages = 1
            while (pages < MAX_PAGINATION_PAGES) {
                val page = api.getAlbumTracks(albumId, limit = PAGE_LIMIT, offset = offset)
                pages++
                allTrackDtos += page.items
                val rawCount = page.rawItems.size
                if (rawCount == 0) break
                val nextParams = page.next?.let(::parseNextPageParams)
                val moreByNext = !page.next.isNullOrBlank()
                val moreByTotal = page.total > offset + rawCount
                if (!moreByNext && !moreByTotal) break
                val nextOffset = nextParams?.offset ?: (offset + rawCount)
                if (nextOffset <= offset) break
                offset = nextOffset
            }
        }

        val likedUriSet = dao.getTracksForContextOnce(LIKED_TRACKS_CONTEXT_URI)
            .mapTo(HashSet()) { it.uri }

        val tracks = allTrackDtos.mapIndexed { idx, dto ->
            TrackEntity(
                uri = dto.uri,
                id = dto.id ?: dto.uri,
                contextUri = albumUri,
                positionInContext = idx,
                title = dto.name,
                artistName = dto.primarySubtitle.ifBlank { artistName },
                albumName = album.name,
                artworkUrl = art,
                durationMs = dto.durationMs,
                isLiked = dto.uri in likedUriSet,
                dominantColorArgb = color,
            )
        }
        dao.replaceTracksForContext(albumUri, tracks)
    }

    suspend fun syncArtistTopTracks(artistId: String, artistUri: String) = withContext(ioDispatcher) {
        val top = api.getArtistTopTracks(artistId)
        val tracks = top.tracks.mapIndexed { idx, dto ->
            dto.toTrackEntity(contextUri = artistUri, position = idx)
        }
        dao.replaceTracksForContext(artistUri, tracks)
    }

    suspend fun syncShowEpisodes(
        showId: String,
        showUri: String,
        showName: String,
        maxPages: Int = 40,
    ) = withContext(ioDispatcher) {
        val episodes = collectAllPages(maxPages = maxPages) { limit, offset ->
            api.getShowEpisodes(showId, limit = limit, offset = offset)
        }
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
        val chapters = collectAllPages { limit, offset ->
            api.getAudiobookChapters(audiobookId, limit = limit, offset = offset)
        }
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
            tracks = PagingDto(rawItems = demoTracks, total = demoTracks.size),
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

    companion object {
        private const val LOG_TAG = "SpotifyFast"
        private const val PAGE_LIMIT = 50
        private const val MAX_PAGINATION_PAGES = 200
        private const val SYNC_SCHEMA_VERSION = 2
    }
}
