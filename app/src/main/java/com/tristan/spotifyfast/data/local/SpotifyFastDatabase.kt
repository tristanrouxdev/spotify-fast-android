package com.tristan.spotifyfast.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        HomeShortcutEntity::class,
        PlaylistEntity::class,
        TrackEntity::class,
        AlbumEntity::class,
        ArtistEntity::class,
        ShowEntity::class,
        ShowEpisodeEntity::class,
        AudiobookEntity::class,
        AudiobookChapterEntity::class,
        PlaybackSnapshotEntity::class,
        SearchHistoryEntity::class,
        LyricsEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class SpotifyFastDatabase : RoomDatabase() {
    abstract fun spotifyDao(): SpotifyDao

    companion object {
        fun create(context: Context): SpotifyFastDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                SpotifyFastDatabase::class.java,
                "spotify_fast.db",
            )
                .fallbackToDestructiveMigration()
                .build()
    }
}
