package com.tristan.spotifyfast.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "home_shortcuts")
data class HomeShortcutEntity(
    @PrimaryKey val uri: String,
    val title: String,
    val subtitle: String,
    val artworkUrl: String?,
    val category: String, // "MUSIC" | "PODCAST"
    val dominantColorArgb: Int = DEFAULT_DOMINANT_COLOR,
    val rank: Int = 0,
    val updatedAtEpochMs: Long = System.currentTimeMillis(),
)

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey val id: String,
    val uri: String,
    val name: String,
    val description: String? = null,
    val ownerName: String = "",
    val artworkUrl: String? = null,
    val snapshotId: String? = null,
    val trackCount: Int = 0,
    val dominantColorArgb: Int = DEFAULT_DOMINANT_COLOR,
    val isPinned: Boolean = false,
    val addedAtEpochMs: Long = System.currentTimeMillis(),
    val updatedAtEpochMs: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "tracks",
    primaryKeys = ["contextUri", "uri", "positionInContext"],
)
data class TrackEntity(
    val uri: String,
    val id: String,
    val contextUri: String,
    val positionInContext: Int,
    val title: String,
    val artistName: String,
    val albumName: String,
    val artworkUrl: String?,
    val durationMs: Long,
    val isLiked: Boolean = false,
    val isRecentPlay: Boolean = false,
    val playedAtEpochMs: Long = 0L,
    val dominantColorArgb: Int = DEFAULT_DOMINANT_COLOR,
)

@Entity(tableName = "albums")
data class AlbumEntity(
    @PrimaryKey val id: String,
    val uri: String,
    val name: String,
    val artistName: String,
    val artworkUrl: String?,
    val releaseYear: String = "",
    val totalTracks: Int = 0,
    val dominantColorArgb: Int = DEFAULT_DOMINANT_COLOR,
    val addedAtEpochMs: Long = System.currentTimeMillis(),
)

@Entity(tableName = "artists")
data class ArtistEntity(
    @PrimaryKey val id: String,
    val uri: String,
    val name: String,
    val artworkUrl: String?,
    val genresCsv: String = "",
    val dominantColorArgb: Int = DEFAULT_DOMINANT_COLOR,
)

@Entity(tableName = "shows")
data class ShowEntity(
    @PrimaryKey val id: String,
    val uri: String,
    val name: String,
    val publisher: String = "",
    val description: String = "",
    val artworkUrl: String?,
    val totalEpisodes: Int = 0,
    val dominantColorArgb: Int = DEFAULT_DOMINANT_COLOR,
    val addedAtEpochMs: Long = System.currentTimeMillis(),
)

@Entity(tableName = "show_episodes")
data class ShowEpisodeEntity(
    @PrimaryKey val uri: String,
    val id: String,
    val showUri: String,
    val showName: String,
    val title: String,
    val description: String = "",
    val artworkUrl: String?,
    val durationMs: Long,
    val resumePositionMs: Long = 0L,
    val isFullyPlayed: Boolean = false,
    val releaseDate: String = "",
    val updatedAtEpochMs: Long = System.currentTimeMillis(),
)

@Entity(tableName = "audiobooks")
data class AudiobookEntity(
    @PrimaryKey val id: String,
    val uri: String,
    val name: String,
    val authorName: String,
    val description: String = "",
    val artworkUrl: String?,
    val totalChapters: Int = 0,
    val dominantColorArgb: Int = DEFAULT_DOMINANT_COLOR,
    val addedAtEpochMs: Long = System.currentTimeMillis(),
)

@Entity(tableName = "audiobook_chapters")
data class AudiobookChapterEntity(
    @PrimaryKey val uri: String,
    val id: String,
    val audiobookUri: String,
    val title: String,
    val chapterNumber: Int,
    val artworkUrl: String?,
    val durationMs: Long,
    val resumePositionMs: Long = 0L,
    val isFullyPlayed: Boolean = false,
    val updatedAtEpochMs: Long = System.currentTimeMillis(),
)

@Entity(tableName = "playback_snapshot")
data class PlaybackSnapshotEntity(
    @PrimaryKey val singletonId: Int = 0,
    val itemUri: String,
    val title: String,
    val subtitle: String,
    val albumName: String = "",
    val artworkUrl: String?,
    val contextUri: String?,
    val isEpisodeOrChapter: Boolean,
    val isPlaying: Boolean,
    val positionMs: Long,
    val durationMs: Long,
    val playbackSpeed: Float = 1.0f,
    val shuffleEnabled: Boolean = false,
    val repeatMode: String = "OFF",
    val isSavedInLibrary: Boolean = false,
    val activeDeviceId: String? = null,
    val activeDeviceName: String? = null,
    val activeDeviceType: String? = null,
    val dominantColorArgb: Int = DEFAULT_DOMINANT_COLOR,
    val updatedAtEpochMs: Long = System.currentTimeMillis(),
)

@Entity(tableName = "search_history")
data class SearchHistoryEntity(
    @PrimaryKey val query: String,
    val searchedAtEpochMs: Long = System.currentTimeMillis(),
)

@Entity(tableName = "lyrics_cache")
data class LyricsEntity(
    @PrimaryKey val trackUri: String,
    val syncedLrc: String?,
    val plainLyrics: String?,
    val isNotFound: Boolean = false,
    val updatedAtEpochMs: Long,
)

const val DEFAULT_DOMINANT_COLOR: Int = 0xFF1DB954.toInt()
const val LIKED_TRACKS_CONTEXT_URI: String = "spotify:collection:tracks"
