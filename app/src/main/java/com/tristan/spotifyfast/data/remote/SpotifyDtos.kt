package com.tristan.spotifyfast.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ImageDto(
    val url: String = "",
    val height: Int? = null,
    val width: Int? = null,
)

@Serializable
data class UserProfileDto(
    val id: String,
    @SerialName("display_name") val displayName: String? = null,
    val product: String? = null,
    val images: List<ImageDto> = emptyList(),
)

@Serializable
data class PagingDto<T>(
    @SerialName("items") val rawItems: List<T?> = emptyList(),
    val limit: Int = 20,
    val offset: Int = 0,
    val total: Int = 0,
    val next: String? = null,
) {
    val items: List<T> get() = rawItems.filterNotNull()
}

@Serializable
data class CursorPagingDto<T>(
    @SerialName("items") val rawItems: List<T?> = emptyList(),
    val limit: Int = 20,
    val next: String? = null,
) {
    val items: List<T> get() = rawItems.filterNotNull()
}

@Serializable
data class PlaylistOwnerDto(
    val id: String? = null,
    @SerialName("display_name") val displayName: String? = null,
)

@Serializable
data class PlaylistItemsRefDto(
    val href: String? = null,
    val total: Int = 0,
)

@Serializable
data class PlaylistSimplifiedDto(
    val id: String = "",
    val uri: String = "",
    val name: String = "",
    val description: String? = null,
    val images: List<ImageDto> = emptyList(),
    val owner: PlaylistOwnerDto? = null,
    @SerialName("snapshot_id") val snapshotId: String? = null,
    val items: PlaylistItemsRefDto? = null,
    val tracks: PlaylistItemsRefDto? = null,
) {
    val itemCount: Int get() = items?.total ?: tracks?.total ?: 0
}

@Serializable
data class ArtistSimplifiedDto(
    val id: String,
    val uri: String = "spotify:artist:$id",
    val name: String,
    val images: List<ImageDto> = emptyList(),
    val genres: List<String> = emptyList(),
)

@Serializable
data class AlbumSimplifiedDto(
    val id: String,
    val uri: String = "spotify:album:$id",
    val name: String,
    @SerialName("album_type") val albumType: String? = null,
    @SerialName("release_date") val releaseDate: String? = null,
    @SerialName("total_tracks") val totalTracks: Int = 0,
    val images: List<ImageDto> = emptyList(),
    val artists: List<ArtistSimplifiedDto> = emptyList(),
)

@Serializable
data class AlbumFullDto(
    val id: String,
    val uri: String = "spotify:album:$id",
    val name: String,
    @SerialName("release_date") val releaseDate: String? = null,
    val images: List<ImageDto> = emptyList(),
    val artists: List<ArtistSimplifiedDto> = emptyList(),
    val tracks: PagingDto<PlayableItemDto> = PagingDto(),
)

@Serializable
data class ShowSimplifiedDto(
    val id: String,
    val uri: String = "spotify:show:$id",
    val name: String,
    val publisher: String? = null,
    val description: String? = null,
    val images: List<ImageDto> = emptyList(),
    @SerialName("total_episodes") val totalEpisodes: Int = 0,
)

@Serializable
data class ResumePointDto(
    @SerialName("fully_played") val fullyPlayed: Boolean = false,
    @SerialName("resume_position_ms") val resumePositionMs: Long = 0L,
)

@Serializable
data class PlayableItemDto(
    val id: String? = null,
    val uri: String = "",
    val name: String = "",
    val type: String = "track",
    @SerialName("duration_ms") val durationMs: Long = 0L,
    val explicit: Boolean = false,
    val artists: List<ArtistSimplifiedDto> = emptyList(),
    val album: AlbumSimplifiedDto? = null,
    val show: ShowSimplifiedDto? = null,
    val images: List<ImageDto> = emptyList(),
    val description: String? = null,
    @SerialName("release_date") val releaseDate: String? = null,
    @SerialName("resume_point") val resumePoint: ResumePointDto? = null,
) {
    val isEpisodeOrChapter: Boolean
        get() = type == "episode" || type == "chapter" || uri.startsWith("spotify:episode:") || uri.startsWith("spotify:chapter:")

    val primarySubtitle: String
        get() = when {
            artists.isNotEmpty() -> artists.joinToString(", ") { it.name }
            show != null -> show.name
            else -> ""
        }

    val artworkUrl: String?
        get() = images.firstOrNull()?.url ?: album?.images?.firstOrNull()?.url ?: show?.images?.firstOrNull()?.url
}

/**
 * Polymorphic wrapper supporting Spotify's February 2026 `/v1/playlists/{id}/items` (`item` field)
 * while retaining compatibility with legacy `track` payloads.
 */
@Serializable
data class PlaylistItemWrapperDto(
    @SerialName("added_at") val addedAt: String? = null,
    @SerialName("item") val item: PlayableItemDto? = null,
    @SerialName("track") val legacyTrack: PlayableItemDto? = null,
) {
    val resolvedItem: PlayableItemDto? get() = item ?: legacyTrack
}

@Serializable
data class SavedTrackDto(
    @SerialName("added_at") val addedAt: String? = null,
    val track: PlayableItemDto? = null,
    val item: PlayableItemDto? = null,
) {
    val resolvedTrack: PlayableItemDto? get() = track ?: item
}

@Serializable
data class SavedAlbumDto(
    @SerialName("added_at") val addedAt: String? = null,
    val album: AlbumSimplifiedDto? = null,
)

@Serializable
data class SavedShowDto(
    @SerialName("added_at") val addedAt: String? = null,
    val show: ShowSimplifiedDto? = null,
)

@Serializable
data class FollowedArtistsResponseDto(
    val artists: CursorPagingDto<ArtistSimplifiedDto> = CursorPagingDto(),
)

@Serializable
data class AuthorDto(val name: String)

@Serializable
data class AudiobookDto(
    val id: String,
    val uri: String = "spotify:audiobook:$id",
    val name: String,
    val authors: List<AuthorDto> = emptyList(),
    val description: String? = null,
    val images: List<ImageDto> = emptyList(),
    @SerialName("total_chapters") val totalChapters: Int = 0,
)

@Serializable
data class EpisodeDto(
    val id: String,
    val uri: String = "spotify:episode:$id",
    val name: String,
    val description: String? = null,
    @SerialName("duration_ms") val durationMs: Long = 0L,
    @SerialName("release_date") val releaseDate: String? = null,
    val images: List<ImageDto> = emptyList(),
    @SerialName("resume_point") val resumePoint: ResumePointDto? = null,
)

@Serializable
data class ChapterDto(
    val id: String,
    val uri: String = "spotify:chapter:$id",
    val name: String,
    @SerialName("chapter_number") val chapterNumber: Int = 0,
    @SerialName("duration_ms") val durationMs: Long = 0L,
    val images: List<ImageDto> = emptyList(),
    @SerialName("resume_point") val resumePoint: ResumePointDto? = null,
)

@Serializable
data class ArtistTopTracksDto(
    val tracks: List<PlayableItemDto> = emptyList(),
)

@Serializable
data class PlayContextDto(
    val uri: String? = null,
    val type: String? = null,
)

@Serializable
data class PlayHistoryDto(
    val track: PlayableItemDto? = null,
    @SerialName("played_at") val playedAt: String? = null,
    val context: PlayContextDto? = null,
)

@Serializable
data class SearchResponseDto(
    val tracks: PagingDto<PlayableItemDto>? = null,
    val artists: PagingDto<ArtistSimplifiedDto>? = null,
    val albums: PagingDto<AlbumSimplifiedDto>? = null,
    val playlists: PagingDto<PlaylistSimplifiedDto>? = null,
    val shows: PagingDto<ShowSimplifiedDto>? = null,
    val episodes: PagingDto<EpisodeDto>? = null,
    val audiobooks: PagingDto<AudiobookDto>? = null,
)

@Serializable
data class ConnectDeviceDto(
    val id: String? = null,
    @SerialName("is_active") val isActive: Boolean = false,
    @SerialName("is_private_session") val isPrivateSession: Boolean = false,
    @SerialName("is_restricted") val isRestricted: Boolean = false,
    val name: String,
    val type: String = "Smartphone",
    @SerialName("volume_percent") val volumePercent: Int? = 50,
    @SerialName("supports_volume") val supportsVolume: Boolean = true,
)

@Serializable
data class DevicesResponseDto(
    val devices: List<ConnectDeviceDto> = emptyList(),
)

@Serializable
data class PlaybackStateDto(
    val device: ConnectDeviceDto? = null,
    @SerialName("repeat_state") val repeatState: String = "off",
    @SerialName("shuffle_state") val shuffleState: Boolean = false,
    val context: PlayContextDto? = null,
    val timestamp: Long = 0L,
    @SerialName("progress_ms") val progressMs: Long = 0L,
    @SerialName("is_playing") val isPlaying: Boolean = false,
    val item: PlayableItemDto? = null,
    @SerialName("currently_playing_type") val currentlyPlayingType: String = "track",
)

@Serializable
data class QueueResponseDto(
    @SerialName("currently_playing") val currentlyPlaying: PlayableItemDto? = null,
    val queue: List<PlayableItemDto> = emptyList(),
)

@Serializable
data class TransferPlaybackRequest(
    @SerialName("device_ids") val deviceIds: List<String>,
    val play: Boolean = true,
)

@Serializable
data class PlaybackOffsetDto(
    val uri: String? = null,
    val position: Int? = null,
)

@Serializable
data class StartPlaybackRequest(
    @SerialName("context_uri") val contextUri: String? = null,
    val uris: List<String>? = null,
    val offset: PlaybackOffsetDto? = null,
    @SerialName("position_ms") val positionMs: Long? = null,
)

@Serializable
data class CreatePlaylistRequest(
    val name: String,
    val description: String = "",
    val public: Boolean = false,
)

@Serializable
data class LrcResponseDto(
    val id: Long? = null,
    val trackName: String? = null,
    val artistName: String? = null,
    val albumName: String? = null,
    val duration: Double? = null,
    val instrumental: Boolean = false,
    val plainLyrics: String? = null,
    val syncedLyrics: String? = null,
)
