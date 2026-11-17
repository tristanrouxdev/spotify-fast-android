package com.tristan.spotifyfast.data.sync

import com.tristan.spotifyfast.data.local.LyricsEntity
import com.tristan.spotifyfast.data.local.SpotifyDao
import com.tristan.spotifyfast.data.remote.LrcLibApi
import com.tristan.spotifyfast.data.remote.LrcResponseDto
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs

data class TrackLyrics(
    val trackUri: String,
    val syncedLines: List<SyncedLyricLine>,
    val plainLyrics: String?,
    val isNotFound: Boolean = false,
)

/**
 * Room-cached lyrics repository backed by LRCLIB (`/api/get` with `/api/search` ±3s tolerance fallback).
 */
class LyricsRepository(
    private val dao: SpotifyDao,
    private val lrcLibApi: LrcLibApi,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    suspend fun getLyrics(
        trackUri: String,
        trackTitle: String,
        artistName: String,
        albumName: String,
        durationMs: Long,
    ): TrackLyrics = withContext(ioDispatcher) {
        if (trackUri.isBlank()) {
            return@withContext TrackLyrics(trackUri, emptyList(), null, isNotFound = true)
        }

        dao.getLyrics(trackUri)?.let { cached ->
            return@withContext TrackLyrics(
                trackUri = trackUri,
                syncedLines = LrcParser.parse(cached.syncedLrc),
                plainLyrics = cached.plainLyrics,
                isNotFound = cached.isNotFound,
            )
        }

        val normalizedTitle = LrcParser.normalizeTrackTitle(trackTitle)
        val primaryArtist = artistName.substringBefore(",").trim()
        val durationSeconds = (durationMs / 1000L).toInt().coerceAtLeast(1)

        val directDto = runCatching {
            val response = lrcLibApi.getSyncedLyrics(
                trackName = normalizedTitle,
                artistName = primaryArtist,
                albumName = albumName,
                durationSeconds = durationSeconds,
            )
            if (response.isSuccessful) response.body() else null
        }.getOrNull()

        val resolvedDto = if (directDto != null && hasAnyLyrics(directDto)) {
            directDto
        } else {
            runCatching {
                val candidates = lrcLibApi.searchLyrics(
                    trackName = normalizedTitle,
                    artistName = primaryArtist,
                )
                pickBestSearchCandidate(candidates, durationSeconds)
            }.getOrNull()
        }

        val syncedLrc = resolvedDto?.syncedLyrics?.takeIf { it.isNotBlank() }
        val plain = resolvedDto?.plainLyrics?.takeIf { it.isNotBlank() }
        val notFound = syncedLrc == null && plain == null

        dao.upsertLyrics(
            LyricsEntity(
                trackUri = trackUri,
                syncedLrc = syncedLrc,
                plainLyrics = plain,
                isNotFound = notFound,
                updatedAtEpochMs = System.currentTimeMillis(),
            ),
        )

        TrackLyrics(
            trackUri = trackUri,
            syncedLines = LrcParser.parse(syncedLrc),
            plainLyrics = plain,
            isNotFound = notFound,
        )
    }

    private fun hasAnyLyrics(dto: LrcResponseDto): Boolean =
        !dto.syncedLyrics.isNullOrBlank() || !dto.plainLyrics.isNullOrBlank()

    private fun pickBestSearchCandidate(
        candidates: List<LrcResponseDto>,
        targetDurationSeconds: Int,
    ): LrcResponseDto? {
        val withLyrics = candidates.filter(::hasAnyLyrics)
        if (withLyrics.isEmpty()) return null
        val withinTolerance = withLyrics.filter { dto ->
            val d = dto.duration ?: return@filter false
            abs(d - targetDurationSeconds.toDouble()) <= 3.0
        }
        return withinTolerance.firstOrNull { !it.syncedLyrics.isNullOrBlank() }
            ?: withinTolerance.firstOrNull()
    }
}
