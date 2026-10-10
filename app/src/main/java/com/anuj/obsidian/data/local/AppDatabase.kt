package com.anuj.obsidian.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import com.anuj.obsidian.data.model.DownloadTask
import com.anuj.obsidian.data.model.DownloadedTrack
import com.anuj.obsidian.data.model.ExtensionItem
import com.anuj.obsidian.data.model.SearchHistoryItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        DownloadedTrack::class,
        ExtensionItem::class,
        DownloadTask::class,
        SearchHistoryItem::class,
        com.anuj.obsidian.data.model.Playlist::class,
        com.anuj.obsidian.data.model.PlaylistTrack::class,
        com.anuj.obsidian.data.model.TrackListeningStat::class,
        com.anuj.obsidian.data.model.CachedLyric::class
    ],
    version = 7,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun searchHistoryDao(): SearchHistoryDao
    abstract fun trackDao(): TrackDao
    abstract fun extensionDao(): ExtensionDao
    abstract fun downloadDao(): DownloadDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun listeningStatsDao(): ListeningStatsDao
    abstract fun cachedLyricDao(): CachedLyricDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context, scope: CoroutineScope): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val dbName = "obsidianobsidian_database"
                try {
                    val db = buildDatabase(context, scope, dbName)
                    // Trigger an active write-ahead log open check to verify integrity instantly
                    db.openHelper.writableDatabase
                    INSTANCE = db
                    db
                } catch (e: Throwable) {
                    android.util.Log.e("AppDatabase", "Database initialization failed (likely due to corruption). Wiping database and retrying...", e)
                    try {
                        context.deleteDatabase(dbName)
                    } catch (delEx: Throwable) {
            com.anuj.obsidian.ObsidianCrashLogger.logException(delEx, "AppDatabase.getDatabase - attempt")
            
                        android.util.Log.e("AppDatabase", "Failed to delete corrupted database file", delEx)
                    }
                    val freshDb = buildDatabase(context, scope, dbName)
                    // Trigger a check on the freshly created database as well to guarantee it works
                    freshDb.openHelper.writableDatabase
                    INSTANCE = freshDb
                    freshDb
                }
            }
        }

        val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                ensureSchemaVersion5(db)
            }
        }
        val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                ensureSchemaVersion5(db)
            }
        }
        val MIGRATION_3_4 = object : androidx.room.migration.Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                ensureSchemaVersion5(db)
            }
        }
        val MIGRATION_4_5 = object : androidx.room.migration.Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                ensureSchemaVersion5(db)
            }
        }
        val MIGRATION_5_6 = object : androidx.room.migration.Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                ensureSchemaVersion6(db)
            }
        }
        val MIGRATION_6_7 = object : androidx.room.migration.Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                ensureSchemaVersion7(db)
            }
        }

        private fun ensureSchemaVersion7(db: SupportSQLiteDatabase) {
            ensureSchemaVersion6(db)
            try {
                db.execSQL("CREATE TABLE IF NOT EXISTS `cached_lyrics` (`lookupKey` TEXT NOT NULL, `isrc` TEXT NOT NULL, `lyrics` TEXT NOT NULL, `isSynced` INTEGER NOT NULL, `source` TEXT NOT NULL, `updatedTimestamp` INTEGER NOT NULL, PRIMARY KEY(`lookupKey`))")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_cached_lyrics_lookupKey` ON `cached_lyrics` (`lookupKey`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_cached_lyrics_isrc` ON `cached_lyrics` (`isrc`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_cached_lyrics_updatedTimestamp` ON `cached_lyrics` (`updatedTimestamp`)")
            } catch (e: Throwable) {
                com.anuj.obsidian.ObsidianCrashLogger.logException(e, "AppDatabase.ensureSchemaVersion7")
            }
        }

        private fun ensureSchemaVersion6(db: SupportSQLiteDatabase) {
            ensureSchemaVersion5(db)
            try {
                db.execSQL("CREATE TABLE IF NOT EXISTS `track_listening_stats` (`trackId` INTEGER NOT NULL, `playCount` INTEGER NOT NULL, `lastPlayedTimestamp` INTEGER NOT NULL, PRIMARY KEY(`trackId`))")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_track_listening_stats_lastPlayedTimestamp` ON `track_listening_stats` (`lastPlayedTimestamp`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_track_listening_stats_playCount` ON `track_listening_stats` (`playCount`)")
            } catch (e: Throwable) {
                com.anuj.obsidian.ObsidianCrashLogger.logException(e, "AppDatabase.ensureSchemaVersion6")
            }
        }

        private fun ensureSchemaVersion5(db: SupportSQLiteDatabase) {
            try {
                // Ensure downloaded_tracks exists
                db.execSQL("CREATE TABLE IF NOT EXISTS `downloaded_tracks` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT NOT NULL, `artist` TEXT NOT NULL, `album` TEXT NOT NULL, `durationSeconds` INTEGER NOT NULL, `format` TEXT NOT NULL, `bitDepth` TEXT NOT NULL, `sampleRate` TEXT NOT NULL, `bitrateKbps` INTEGER NOT NULL, `albumArtUrl` TEXT NOT NULL, `localFilePath` TEXT NOT NULL, `fileSizeMb` REAL NOT NULL, `lyrics` TEXT NOT NULL, `isrc` TEXT NOT NULL, `releaseDate` TEXT NOT NULL, `genre` TEXT NOT NULL, `trackNumber` INTEGER NOT NULL, `discNumber` INTEGER NOT NULL, `providerSource` TEXT NOT NULL, `isFavorite` INTEGER NOT NULL, `dateAddedTimestamp` INTEGER NOT NULL, `tagsLoaded` INTEGER NOT NULL)")
                
                // Add any missing columns to downloaded_tracks in case an older version lacked them
                val cols = listOf(
                    "format" to "TEXT NOT NULL DEFAULT 'FLAC'",
                    "bitDepth" to "TEXT NOT NULL DEFAULT '16-bit'",
                    "sampleRate" to "TEXT NOT NULL DEFAULT '44.1 kHz'",
                    "bitrateKbps" to "INTEGER NOT NULL DEFAULT 2840",
                    "albumArtUrl" to "TEXT NOT NULL DEFAULT ''",
                    "localFilePath" to "TEXT NOT NULL DEFAULT ''",
                    "fileSizeMb" to "REAL NOT NULL DEFAULT 34.5",
                    "lyrics" to "TEXT NOT NULL DEFAULT ''",
                    "isrc" to "TEXT NOT NULL DEFAULT ''",
                    "releaseDate" to "TEXT NOT NULL DEFAULT '2024'",
                    "genre" to "TEXT NOT NULL DEFAULT 'Lossless Master'",
                    "trackNumber" to "INTEGER NOT NULL DEFAULT 1",
                    "discNumber" to "INTEGER NOT NULL DEFAULT 1",
                    "providerSource" to "TEXT NOT NULL DEFAULT 'Qobuz Studio Master'",
                    "isFavorite" to "INTEGER NOT NULL DEFAULT 0",
                    "dateAddedTimestamp" to "INTEGER NOT NULL DEFAULT 0",
                    "tagsLoaded" to "INTEGER NOT NULL DEFAULT 1"
                )
                for (pair in cols) {
                    val colName = pair.first
                    val colDef = pair.second
                    if (!hasColumn(db, "downloaded_tracks", colName)) {
                        db.execSQL("ALTER TABLE `downloaded_tracks` ADD COLUMN `$colName` $colDef")
                    }
                }

                // Indices for downloaded_tracks
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_downloaded_tracks_localFilePath` ON `downloaded_tracks` (`localFilePath`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_downloaded_tracks_title_artist` ON `downloaded_tracks` (`title`, `artist`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_downloaded_tracks_album` ON `downloaded_tracks` (`album`)")

                // Ensure extensions exists
                db.execSQL("CREATE TABLE IF NOT EXISTS `extensions` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `version` TEXT NOT NULL, `author` TEXT NOT NULL, `description` TEXT NOT NULL, `repoUrl` TEXT NOT NULL, `isEnabled` INTEGER NOT NULL, `isInstalled` INTEGER NOT NULL, `capabilities` TEXT NOT NULL, `pingMs` INTEGER NOT NULL, `sourceProvider` TEXT NOT NULL, `isOfficial` INTEGER NOT NULL, `downloadUrl` TEXT NOT NULL, PRIMARY KEY(`id`))")

                // Ensure download_tasks exists
                db.execSQL("CREATE TABLE IF NOT EXISTS `download_tasks` (`taskId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT NOT NULL, `artist` TEXT NOT NULL, `album` TEXT NOT NULL, `albumArtUrl` TEXT NOT NULL, `quality` TEXT NOT NULL, `progress` REAL NOT NULL, `speedKbps` REAL NOT NULL, `downloadedBytes` INTEGER NOT NULL, `totalBytes` INTEGER NOT NULL, `status` TEXT NOT NULL, `errorMessage` TEXT, `provider` TEXT NOT NULL, `startedAt` INTEGER NOT NULL, `lyricsAvailable` INTEGER NOT NULL, `isrc` TEXT NOT NULL)")

                // Ensure search_history exists
                db.execSQL("CREATE TABLE IF NOT EXISTS `search_history` (`query` TEXT NOT NULL, `timestamp` INTEGER NOT NULL, PRIMARY KEY(`query`))")

                // Ensure playlists exists
                db.execSQL("CREATE TABLE IF NOT EXISTS `playlists` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `description` TEXT NOT NULL, `createdAt` INTEGER NOT NULL)")

                // Ensure playlist_tracks exists
                db.execSQL("CREATE TABLE IF NOT EXISTS `playlist_tracks` (`playlistId` INTEGER NOT NULL, `trackId` INTEGER NOT NULL, `orderIndex` INTEGER NOT NULL, PRIMARY KEY(`playlistId`, `trackId`))")

            } catch (e: Throwable) {
                com.anuj.obsidian.ObsidianCrashLogger.logException(e, "AppDatabase.ensureSchemaVersion5")
            }
        }

        private fun hasColumn(db: SupportSQLiteDatabase, tableName: String, columnName: String): Boolean {
            var cursor: android.database.Cursor? = null
            try {
                cursor = db.query("PRAGMA table_info($tableName)", emptyArray())
                val nameIndex = cursor.getColumnIndex("name")
                if (nameIndex != -1) {
                    while (cursor.moveToNext()) {
                        if (cursor.getString(nameIndex) == columnName) {
                            return true
                        }
                    }
                }
            } catch (e: Throwable) {
            com.anuj.obsidian.ObsidianCrashLogger.logException(e, "AppDatabase.hasColumn - attempt")
            
            } finally {
                cursor?.close()
            }
            return false
        }

        private fun buildDatabase(context: Context, scope: CoroutineScope, dbName: String): AppDatabase {
            var instanceHolder: AppDatabase? = null
            val db = Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                dbName
            )
                .addCallback(DatabaseCallback(scope) { instanceHolder ?: INSTANCE })
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
                // Keep explicit migrations for seamless data preservation; fallback to destructive migration only as last resort with logging
                .fallbackToDestructiveMigration(false)
                .build()
            instanceHolder = db
            return db
        }

        private class DatabaseCallback(
            private val scope: CoroutineScope,
            private val databaseProvider: () -> AppDatabase?
        ) : RoomDatabase.Callback() {
            override fun onOpen(db: SupportSQLiteDatabase) {
                super.onOpen(db)
            }

            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                scope.launch(Dispatchers.IO) {
                    try {
                        val database = databaseProvider()
                        if (database != null) {
                            populateInitialData(database)
                        }
                    } catch (e: Exception) {
                        if (e is kotlinx.coroutines.CancellationException) throw e
                        com.anuj.obsidian.ObsidianCrashLogger.logException(e, "AppDatabase.onCreate - attempt")
                    }
                }
            }
        }

        val DEFAULT_EXTENSIONS = listOf(
            ExtensionItem(
                id = "spotify-web",
                name = "Spotify Web",
                version = "1.10.2",
                author = "zarzet",
                description = "Spotify metadata provider for SpotiFLAC Mobile with complete native release, disc, catalog ID, credits, and artwork metadata plus guarded genre enrichment.",
                repoUrl = "https://raw.githubusercontent.com/zarzet/SpotiFLAC-Extension/main/extensions/spotify-web.sflx",
                isEnabled = true,
                isInstalled = true,
                capabilities = "spotify, web, streaming, homefeed",
                pingMs = 15,
                sourceProvider = "Spotify Web",
                isOfficial = true,
                downloadUrl = "https://raw.githubusercontent.com/zarzet/SpotiFLAC-Extension/main/extensions/spotify-web.sflx"
            ),
            ExtensionItem(
                id = "amazon",
                name = "Amazon Music",
                version = "2.3.3",
                author = "zarzet",
                description = "Amazon Music metadata & download provider for SpotiFLAC Mobile. Includes complete Amazon Web release metadata, original-resolution artwork, lossless audio, and Dolby Atmos.",
                repoUrl = "https://raw.githubusercontent.com/zarzet/SpotiFLAC-Extension/main/extensions/amzn.sflx",
                isEnabled = true,
                isInstalled = true,
                capabilities = "amazon, download, lossless, metadata, search",
                pingMs = 30,
                sourceProvider = "Amazon Music",
                isOfficial = true,
                downloadUrl = "https://raw.githubusercontent.com/zarzet/SpotiFLAC-Extension/main/extensions/amzn.sflx"
            ),
            ExtensionItem(
                id = "apple-music",
                name = "Apple Music",
                version = "1.4.4",
                author = "zarzet",
                description = "Apple Music metadata and lyrics provider for SpotiFLAC Mobile with guarded track matching, bounded caching, resilient requests, and complete release metadata.",
                repoUrl = "https://raw.githubusercontent.com/zarzet/SpotiFLAC-Extension/main/extensions/apple-music.sflx",
                isEnabled = true,
                isInstalled = true,
                capabilities = "apple, metadata, lyrics, search",
                pingMs = 42,
                sourceProvider = "Apple Music",
                isOfficial = true,
                downloadUrl = "https://raw.githubusercontent.com/zarzet/SpotiFLAC-Extension/main/extensions/apple-music.sflx"
            ),
            ExtensionItem(
                id = "soundcloud",
                name = "SoundCloud",
                version = "1.0.8",
                author = "zarzet",
                description = "SoundCloud metadata and download provider. Search tracks, albums, playlists, artists. Downloads via direct SoundCloud streams.",
                repoUrl = "https://raw.githubusercontent.com/zarzet/SpotiFLAC-Extension/main/extensions/soundcloud.sflx",
                isEnabled = true,
                isInstalled = true,
                capabilities = "soundcloud, download, metadata, search",
                pingMs = 48,
                sourceProvider = "SoundCloud",
                isOfficial = true,
                downloadUrl = "https://raw.githubusercontent.com/zarzet/SpotiFLAC-Extension/main/extensions/soundcloud.sflx"
            ),
            ExtensionItem(
                id = "ytmusic-spotiflac",
                name = "YouTube Music",
                version = "2.4.1",
                author = "zarzet",
                description = "YouTube Music metadata and download provider with native album, artist, explicit, release, and song-credit metadata plus validated catalog enrichment.",
                repoUrl = "https://raw.githubusercontent.com/zarzet/SpotiFLAC-Extension/main/extensions/ytmusic-spotiflac.sflx",
                isEnabled = true,
                isInstalled = true,
                capabilities = "youtube, ytmusic, metadata, search, homefeed, download",
                pingMs = 38,
                sourceProvider = "YouTube Music",
                isOfficial = true,
                downloadUrl = "https://raw.githubusercontent.com/zarzet/SpotiFLAC-Extension/main/extensions/ytmusic-spotiflac.sflx"
            ),
            ExtensionItem(
                id = "deezer",
                name = "Deezer",
                version = "1.3.4",
                author = "zarzet",
                description = "Deezer metadata and lossless download provider with complete release, disc, barcode, credits, explicit, genre, rights, link, and original artwork metadata.",
                repoUrl = "https://raw.githubusercontent.com/zarzet/SpotiFLAC-Extension/main/extensions/deezer.sflx",
                isEnabled = true,
                isInstalled = true,
                capabilities = "deezer, download, lossless, metadata, search",
                pingMs = 45,
                sourceProvider = "Deezer",
                isOfficial = true,
                downloadUrl = "https://raw.githubusercontent.com/zarzet/SpotiFLAC-Extension/main/extensions/deezer.sflx"
            ),
            ExtensionItem(
                id = "qobuz-web",
                name = "Qobuz",
                version = "1.2.10",
                author = "zarzet",
                description = "Qobuz metadata and download provider for SpotiFLAC Mobile with resilient album search and track recovery, complete release metadata, credits, multi-disc totals, and original artwork.",
                repoUrl = "https://raw.githubusercontent.com/zarzet/SpotiFLAC-Extension/main/extensions/qobuz-web.sflx",
                isEnabled = true,
                isInstalled = true,
                capabilities = "qobuz, download, lossless, metadata, search",
                pingMs = 28,
                sourceProvider = "Qobuz",
                isOfficial = true,
                downloadUrl = "https://raw.githubusercontent.com/zarzet/SpotiFLAC-Extension/main/extensions/qobuz-web.sflx"
            ),
            ExtensionItem(
                id = "tidal-web",
                name = "Tidal",
                version = "1.2.2",
                author = "zarzet",
                description = "TIDAL metadata, search, and download provider for SpotiFLAC Mobile with complete release, credits, explicit, and disc metadata from TIDAL Web.",
                repoUrl = "https://raw.githubusercontent.com/zarzet/SpotiFLAC-Extension/main/extensions/tidal-web.sflx",
                isEnabled = true,
                isInstalled = true,
                capabilities = "tidal, download, lossless, metadata, search",
                pingMs = 34,
                sourceProvider = "Tidal",
                isOfficial = true,
                downloadUrl = "https://raw.githubusercontent.com/zarzet/SpotiFLAC-Extension/main/extensions/tidal-web.sflx"
            ),
            ExtensionItem(
                id = "musixmatch-lyrics",
                name = "Musixmatch Lyrics",
                version = "1.1.0",
                author = "zarzet",
                description = "Musixmatch synchronized and word-by-word lyrics provider with rich timecode alignment and multi-language translations for SpotiFLAC.",
                repoUrl = "https://raw.githubusercontent.com/zarzet/SpotiFLAC-Extension/main/extensions/musixmatch.sflx",
                isEnabled = true,
                isInstalled = true,
                capabilities = "musixmatch, lyrics, synced, karaoke",
                pingMs = 22,
                sourceProvider = "Musixmatch",
                isOfficial = true,
                downloadUrl = "https://raw.githubusercontent.com/zarzet/SpotiFLAC-Extension/main/extensions/musixmatch.sflx"
            ),
            ExtensionItem(
                id = "lrclib-lyrics",
                name = "LRCLIB Lyrics",
                version = "1.0.5",
                author = "zarzet",
                description = "LRCLIB real-time synced lyrics provider with zero-rate-limit crowd-sourced time-stamped lyrics and instrumental detection.",
                repoUrl = "https://raw.githubusercontent.com/zarzet/SpotiFLAC-Extension/main/extensions/lrclib.sflx",
                isEnabled = true,
                isInstalled = true,
                capabilities = "lrclib, lyrics, synced, lrc",
                pingMs = 18,
                sourceProvider = "LRCLIB",
                isOfficial = true,
                downloadUrl = "https://raw.githubusercontent.com/zarzet/SpotiFLAC-Extension/main/extensions/lrclib.sflx"
            ),
            ExtensionItem(
                id = "musicbrainz-metadata",
                name = "MusicBrainz Metadata",
                version = "1.0.3",
                author = "zarzet",
                description = "MusicBrainz open music encyclopedia provider. Resolves canonical track titles, artist disambiguation, release groups, and acoustic IDs.",
                repoUrl = "https://raw.githubusercontent.com/zarzet/SpotiFLAC-Extension/main/extensions/musicbrainz.sflx",
                isEnabled = true,
                isInstalled = true,
                capabilities = "musicbrainz, metadata, isrc, acousticid",
                pingMs = 35,
                sourceProvider = "MusicBrainz",
                isOfficial = true,
                downloadUrl = "https://raw.githubusercontent.com/zarzet/SpotiFLAC-Extension/main/extensions/musicbrainz.sflx"
            ),
            ExtensionItem(
                id = "discogs-metadata",
                name = "Discogs Catalog",
                version = "1.0.2",
                author = "zarzet",
                description = "Discogs master release catalog provider with vinyl pressing details, master barcode scanning, and audio engineers credit data.",
                repoUrl = "https://raw.githubusercontent.com/zarzet/SpotiFLAC-Extension/main/extensions/discogs.sflx",
                isEnabled = true,
                isInstalled = true,
                capabilities = "discogs, vinyl, credits, catalog",
                pingMs = 40,
                sourceProvider = "Discogs",
                isOfficial = true,
                downloadUrl = "https://raw.githubusercontent.com/zarzet/SpotiFLAC-Extension/main/extensions/discogs.sflx"
            ),
            ExtensionItem(
                id = "lastfm-scrobbler",
                name = "Last.fm Scrobbler",
                version = "1.0.4",
                author = "zarzet",
                description = "Last.fm scrobbler and discovery extension for tracking playback stats, user recommendations, and real-time listening history.",
                repoUrl = "https://raw.githubusercontent.com/zarzet/SpotiFLAC-Extension/main/extensions/lastfm.sflx",
                isEnabled = true,
                isInstalled = true,
                capabilities = "lastfm, scrobble, stats, discovery",
                pingMs = 25,
                sourceProvider = "Last.fm",
                isOfficial = true,
                downloadUrl = "https://raw.githubusercontent.com/zarzet/SpotiFLAC-Extension/main/extensions/lastfm.sflx"
            )
        )

        private suspend fun populateInitialData(database: AppDatabase) {
            val extensionDao = database.extensionDao()
            extensionDao.insertExtensions(DEFAULT_EXTENSIONS)
            // Initial state starts with a fresh, clean library
        }
    }
}
