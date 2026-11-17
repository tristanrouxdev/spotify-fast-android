package com.tristan.spotifyfast.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SpotifyDao {

    // Home Shortcuts
    @Query("SELECT * FROM home_shortcuts ORDER BY rank ASC LIMIT 6")
    fun observeHomeShortcuts(): Flow<List<HomeShortcutEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertHomeShortcuts(items: List<HomeShortcutEntity>)

    @Query("DELETE FROM home_shortcuts")
    suspend fun clearHomeShortcuts()

    // Playlists
    @Query("SELECT * FROM playlists ORDER BY isPinned DESC, updatedAtEpochMs DESC")
    fun observePlaylists(): Flow<List<PlaylistEntity>>

    @Query("SELECT * FROM playlists WHERE id = :id OR uri = :id LIMIT 1")
    suspend fun getPlaylist(id: String): PlaylistEntity?

    @Query("SELECT * FROM playlists")
    suspend fun getAllPlaylistsOnce(): List<PlaylistEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPlaylists(items: List<PlaylistEntity>)

    @Query("UPDATE playlists SET isPinned = :pinned WHERE id = :id")
    suspend fun setPlaylistPinned(id: String, pinned: Boolean)

    @Query("UPDATE playlists SET dominantColorArgb = :colorArgb WHERE uri = :uri")
    suspend fun updatePlaylistColor(uri: String, colorArgb: Int)

    // Tracks
    @Query("SELECT * FROM tracks WHERE contextUri = :contextUri ORDER BY positionInContext ASC")
    fun observeTracksForContext(contextUri: String): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE contextUri = :contextUri ORDER BY positionInContext ASC")
    suspend fun getTracksForContextOnce(contextUri: String): List<TrackEntity>

    @Query("SELECT * FROM tracks WHERE uri = :uri LIMIT 1")
    suspend fun findTrackByUriOnce(uri: String): TrackEntity?

    @Query("SELECT * FROM tracks WHERE isRecentPlay = 1 ORDER BY playedAtEpochMs DESC LIMIT 20")
    fun observeRecentTracks(): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE contextUri = '$LIKED_TRACKS_CONTEXT_URI' ORDER BY positionInContext ASC")
    fun observeLikedTracks(): Flow<List<TrackEntity>>

    @Query("SELECT COUNT(*) FROM tracks WHERE contextUri = '$LIKED_TRACKS_CONTEXT_URI'")
    fun observeLikedTracksCount(): Flow<Int>

    @Query("SELECT EXISTS(SELECT 1 FROM tracks WHERE uri = :trackUri AND isLiked = 1)")
    suspend fun isTrackLiked(trackUri: String): Boolean

    @Query("UPDATE tracks SET isLiked = :liked WHERE uri = :trackUri")
    suspend fun updateTrackLikedFlag(trackUri: String, liked: Boolean)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTracks(items: List<TrackEntity>)

    @Query("DELETE FROM tracks WHERE contextUri = :contextUri")
    suspend fun deleteTracksForContext(contextUri: String)

    // Albums
    @Query("SELECT * FROM albums ORDER BY addedAtEpochMs DESC")
    fun observeAlbums(): Flow<List<AlbumEntity>>

    @Query("SELECT * FROM albums WHERE id = :id OR uri = :id LIMIT 1")
    suspend fun getAlbum(id: String): AlbumEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAlbums(items: List<AlbumEntity>)

    @Query("DELETE FROM albums WHERE id = :id OR uri = :id")
    suspend fun deleteAlbum(id: String)

    @Query("UPDATE albums SET dominantColorArgb = :colorArgb WHERE uri = :uri")
    suspend fun updateAlbumColor(uri: String, colorArgb: Int)

    // Artists
    @Query("SELECT * FROM artists ORDER BY name ASC")
    fun observeArtists(): Flow<List<ArtistEntity>>

    @Query("SELECT * FROM artists WHERE id = :id OR uri = :id LIMIT 1")
    suspend fun getArtist(id: String): ArtistEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertArtists(items: List<ArtistEntity>)

    // Shows & Episodes
    @Query("SELECT * FROM shows ORDER BY addedAtEpochMs DESC")
    fun observeShows(): Flow<List<ShowEntity>>

    @Query("SELECT * FROM shows WHERE id = :id OR uri = :id LIMIT 1")
    suspend fun getShow(id: String): ShowEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertShows(items: List<ShowEntity>)

    @Query("DELETE FROM shows WHERE id = :id OR uri = :id")
    suspend fun deleteShow(id: String)

    @Query("SELECT * FROM show_episodes WHERE showUri = :showUri ORDER BY releaseDate DESC")
    fun observeEpisodesForShow(showUri: String): Flow<List<ShowEpisodeEntity>>

    @Query("SELECT * FROM show_episodes WHERE isFullyPlayed = 0 ORDER BY updatedAtEpochMs DESC LIMIT 15")
    fun observeInProgressEpisodes(): Flow<List<ShowEpisodeEntity>>

    @Query("SELECT * FROM show_episodes WHERE uri = :episodeUri LIMIT 1")
    suspend fun getEpisode(episodeUri: String): ShowEpisodeEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertEpisodes(items: List<ShowEpisodeEntity>)

    @Query("UPDATE show_episodes SET resumePositionMs = :positionMs, isFullyPlayed = :fullyPlayed, updatedAtEpochMs = :updatedAt WHERE uri = :episodeUri")
    suspend fun updateEpisodeProgress(
        episodeUri: String,
        positionMs: Long,
        fullyPlayed: Boolean,
        updatedAt: Long = System.currentTimeMillis(),
    )

    // Audiobooks & Chapters
    @Query("SELECT * FROM audiobooks ORDER BY addedAtEpochMs DESC")
    fun observeAudiobooks(): Flow<List<AudiobookEntity>>

    @Query("SELECT * FROM audiobooks WHERE id = :id OR uri = :id LIMIT 1")
    suspend fun getAudiobook(id: String): AudiobookEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAudiobooks(items: List<AudiobookEntity>)

    @Query("SELECT * FROM audiobook_chapters WHERE audiobookUri = :audiobookUri ORDER BY chapterNumber ASC")
    fun observeChaptersForAudiobook(audiobookUri: String): Flow<List<AudiobookChapterEntity>>

    @Query("SELECT * FROM audiobook_chapters WHERE uri = :chapterUri LIMIT 1")
    suspend fun getChapter(chapterUri: String): AudiobookChapterEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertChapters(items: List<AudiobookChapterEntity>)

    @Query("UPDATE audiobook_chapters SET resumePositionMs = :positionMs, isFullyPlayed = :fullyPlayed, updatedAtEpochMs = :updatedAt WHERE uri = :chapterUri")
    suspend fun updateChapterProgress(
        chapterUri: String,
        positionMs: Long,
        fullyPlayed: Boolean,
        updatedAt: Long = System.currentTimeMillis(),
    )

    // Playback Snapshot
    @Query("SELECT * FROM playback_snapshot WHERE singletonId = 0 LIMIT 1")
    fun observePlaybackSnapshot(): Flow<PlaybackSnapshotEntity?>

    @Query("SELECT * FROM playback_snapshot WHERE singletonId = 0 LIMIT 1")
    suspend fun getPlaybackSnapshotOnce(): PlaybackSnapshotEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPlaybackSnapshot(snapshot: PlaybackSnapshotEntity)

    // Search History
    @Query("SELECT * FROM search_history ORDER BY searchedAtEpochMs DESC LIMIT 12")
    fun observeSearchHistory(): Flow<List<SearchHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSearchHistory(entry: SearchHistoryEntity)

    @Query("DELETE FROM search_history WHERE query = :query")
    suspend fun deleteSearchHistory(query: String)

    // Lyrics Cache
    @Query("SELECT * FROM lyrics_cache WHERE trackUri = :trackUri LIMIT 1")
    suspend fun getLyrics(trackUri: String): LyricsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLyrics(entity: LyricsEntity)

    // Color updates across shortcuts & snapshot
    @Query("UPDATE home_shortcuts SET dominantColorArgb = :colorArgb WHERE uri = :uri")
    suspend fun updateShortcutColor(uri: String, colorArgb: Int)
}
