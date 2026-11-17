package com.tristan.spotifyfast.data.remote

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Spotify Web API client aligned with the February 2026 Developer Mode endpoints
 * (`/v1/me/library`, `/v1/playlists/{id}/items`, `search` limit=10) and Connect controls.
 */
interface SpotifyWebApi {

    @GET("v1/me")
    suspend fun getCurrentProfile(): UserProfileDto

    @GET("v1/me/playlists")
    suspend fun getUserPlaylists(
        @Query("limit") limit: Int = 50,
        @Query("offset") offset: Int = 0,
    ): PagingDto<PlaylistSimplifiedDto>

    @POST("v1/users/{user_id}/playlists")
    suspend fun createPlaylist(
        @Path("user_id") userId: String,
        @Body body: CreatePlaylistRequest,
    ): PlaylistSimplifiedDto

    @GET("v1/playlists/{id}/items")
    suspend fun getPlaylistItems(
        @Path("id") id: String,
        @Query("limit") limit: Int = 50,
        @Query("offset") offset: Int = 0,
    ): PagingDto<PlaylistItemWrapperDto>

    @GET("v1/me/tracks")
    suspend fun getSavedTracks(
        @Query("limit") limit: Int = 50,
        @Query("offset") offset: Int = 0,
    ): PagingDto<SavedTrackDto>

    @GET("v1/me/albums")
    suspend fun getSavedAlbums(
        @Query("limit") limit: Int = 50,
        @Query("offset") offset: Int = 0,
    ): PagingDto<SavedAlbumDto>

    @GET("v1/albums/{id}")
    suspend fun getAlbum(@Path("id") id: String): AlbumFullDto

    @GET("v1/artists/{id}/top-tracks")
    suspend fun getArtistTopTracks(@Path("id") id: String): ArtistTopTracksDto

    @GET("v1/me/shows")
    suspend fun getSavedShows(
        @Query("limit") limit: Int = 50,
        @Query("offset") offset: Int = 0,
    ): PagingDto<SavedShowDto>

    @GET("v1/shows/{id}/episodes")
    suspend fun getShowEpisodes(
        @Path("id") id: String,
        @Query("limit") limit: Int = 50,
        @Query("offset") offset: Int = 0,
    ): PagingDto<EpisodeDto>

    @GET("v1/me/audiobooks")
    suspend fun getSavedAudiobooks(
        @Query("limit") limit: Int = 50,
        @Query("offset") offset: Int = 0,
    ): PagingDto<AudiobookDto>

    @GET("v1/audiobooks/{id}/chapters")
    suspend fun getAudiobookChapters(
        @Path("id") id: String,
        @Query("limit") limit: Int = 50,
        @Query("offset") offset: Int = 0,
    ): PagingDto<ChapterDto>

    @GET("v1/me/player/recently-played")
    suspend fun getRecentlyPlayed(
        @Query("limit") limit: Int = 20,
    ): CursorPagingDto<PlayHistoryDto>

    @GET("v1/me/library/contains")
    suspend fun checkLibraryContains(
        @Query("uris") commaSeparatedUris: String,
    ): List<Boolean>

    @PUT("v1/me/library")
    suspend fun saveToLibrary(
        @Query("uris") commaSeparatedUris: String,
    ): Response<Unit>

    @DELETE("v1/me/library")
    suspend fun removeFromLibrary(
        @Query("uris") commaSeparatedUris: String,
    ): Response<Unit>

    @GET("v1/search")
    suspend fun search(
        @Query("q") query: String,
        @Query("type") types: String = "track,artist,album,playlist,show,episode,audiobook",
        @Query("limit") limit: Int = 10,
        @Query("offset") offset: Int = 0,
    ): SearchResponseDto

    @GET("v1/me/player")
    suspend fun getPlaybackState(
        @Query("additional_types") types: String = "track,episode",
    ): Response<PlaybackStateDto>

    @GET("v1/me/player/devices")
    suspend fun getAvailableDevices(): DevicesResponseDto

    @PUT("v1/me/player")
    suspend fun transferPlayback(@Body body: TransferPlaybackRequest): Response<Unit>

    @PUT("v1/me/player/play")
    suspend fun startOrResumePlayback(
        @Query("device_id") deviceId: String? = null,
        @Body body: StartPlaybackRequest? = null,
    ): Response<Unit>

    @PUT("v1/me/player/pause")
    suspend fun pausePlayback(@Query("device_id") deviceId: String? = null): Response<Unit>

    @POST("v1/me/player/next")
    suspend fun skipToNext(@Query("device_id") deviceId: String? = null): Response<Unit>

    @POST("v1/me/player/previous")
    suspend fun skipToPrevious(@Query("device_id") deviceId: String? = null): Response<Unit>

    @PUT("v1/me/player/seek")
    suspend fun seekToPosition(
        @Query("position_ms") positionMs: Long,
        @Query("device_id") deviceId: String? = null,
    ): Response<Unit>

    @PUT("v1/me/player/shuffle")
    suspend fun setShuffle(
        @Query("state") state: Boolean,
        @Query("device_id") deviceId: String? = null,
    ): Response<Unit>

    @PUT("v1/me/player/repeat")
    suspend fun setRepeatMode(
        @Query("state") state: String,
        @Query("device_id") deviceId: String? = null,
    ): Response<Unit>

    @PUT("v1/me/player/volume")
    suspend fun setVolume(
        @Query("volume_percent") volumePercent: Int,
        @Query("device_id") deviceId: String? = null,
    ): Response<Unit>

    @GET("v1/me/player/queue")
    suspend fun getQueue(): QueueResponseDto

    @POST("v1/me/player/queue")
    suspend fun addToQueue(@Query("uri") uri: String): Response<Unit>
}
