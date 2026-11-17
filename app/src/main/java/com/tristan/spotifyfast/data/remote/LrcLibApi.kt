package com.tristan.spotifyfast.data.remote

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Keyless open synced lyrics API (LRCLIB).
 */
interface LrcLibApi {

    @GET("api/get")
    suspend fun getSyncedLyrics(
        @Query("track_name") trackName: String,
        @Query("artist_name") artistName: String,
        @Query("album_name") albumName: String,
        @Query("duration") durationSeconds: Int,
    ): Response<LrcResponseDto>

    @GET("api/search")
    suspend fun searchLyrics(
        @Query("track_name") trackName: String,
        @Query("artist_name") artistName: String,
    ): List<LrcResponseDto>
}
