package com.anuj.obsidian

import androidx.media3.common.Player
import com.anuj.obsidian.data.model.AdvancedShuffleMode
import com.anuj.obsidian.data.model.DownloadedTrack
import com.anuj.obsidian.data.model.PlaybackContext
import com.anuj.obsidian.data.model.PlaybackSourceType
import com.anuj.obsidian.ui.player.ShuffleManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ShuffleManagerUnitTest {

    private lateinit var shuffleManager: ShuffleManager

    private fun createSampleTrack(
        id: Long,
        title: String = "Track $id",
        album: String = "Album A",
        artist: String = "Artist 1"
    ): DownloadedTrack {
        return DownloadedTrack(
            id = id,
            title = title,
            artist = artist,
            album = album,
            durationSeconds = 200,
            localFilePath = "/storage/emulated/0/Music/track_$id.flac"
        )
    }

    private fun createMultiCategoryTracks(): List<DownloadedTrack> {
        return listOf(
            createSampleTrack(1, "Track 1A", "Album A", "Artist 1"),
            createSampleTrack(2, "Track 2A", "Album A", "Artist 1"),
            createSampleTrack(3, "Track 3A", "Album A", "Artist 1"),
            createSampleTrack(4, "Track 1B", "Album B", "Artist 2"),
            createSampleTrack(5, "Track 2B", "Album B", "Artist 2"),
            createSampleTrack(6, "Track 1C", "Album C", "Artist 3"),
            createSampleTrack(7, "Track 2C", "Album C", "Artist 3"),
            createSampleTrack(8, "Track 3C", "Album C", "Artist 3")
        )
    }

    @Before
    fun setUp() {
        shuffleManager = ShuffleManager()
    }

    // ==========================================
    // 2. Tests for retreatToPreviousTrack
    // ==========================================

    @Test
    fun testRetreatToPreviousTrack_navigatesHistoryAccurately() {
        val tracks = createMultiCategoryTracks()
        shuffleManager.setLibraryContext(tracks, startIndex = 0, shuffle = false)

        val track1 = tracks[0]
        val track2 = tracks[1]
        val track3 = tracks[2]

        shuffleManager.recordTrackPlayed(track1)
        shuffleManager.recordTrackPlayed(track2)
        shuffleManager.recordTrackPlayed(track3)

        // Currently at track3 -> retreat should give track2
        val retreated1 = shuffleManager.retreatToPreviousTrack(Player.REPEAT_MODE_OFF, track3)
        assertEquals(track2.id, retreated1?.id)

        // Next retreat should give track1
        val retreated2 = shuffleManager.retreatToPreviousTrack(Player.REPEAT_MODE_OFF, track2)
        assertEquals(track1.id, retreated2?.id)
    }

    @Test
    fun testRetreatToPreviousTrack_withoutHistory_retreatsWithinPlaybackContext() {
        val tracks = createMultiCategoryTracks()
        shuffleManager.setLibraryContext(tracks, startIndex = 2, shuffle = false)

        val retreated = shuffleManager.retreatToPreviousTrack(Player.REPEAT_MODE_OFF, tracks[2])
        assertEquals(tracks[1].id, retreated?.id)
    }

    // ==========================================
    // 3. Tests for 5 AdvancedShuffleMode Traversal Paths
    // ==========================================

    @Test
    fun testShuffleMode_SHUFFLE_OFF_sequentialPlayback() {
        val tracks = createMultiCategoryTracks()
        shuffleManager.setPlaybackContext(
            context = PlaybackContext(tracks = tracks),
            startIndex = 0,
            shuffle = false,
            shuffleMode = AdvancedShuffleMode.SHUFFLE_OFF
        )

        assertFalse(shuffleManager.isShuffleEnabled())
        assertEquals(AdvancedShuffleMode.SHUFFLE_OFF, shuffleManager.getShuffleTraversal().mode)

        // Traverses sequentially in exact list order
        for (i in 0 until tracks.size - 1) {
            val next = shuffleManager.advanceToNextTrack(Player.REPEAT_MODE_OFF, tracks[i])
            assertEquals(tracks[i + 1].id, next?.id)
        }

        // At end of list with repeat OFF -> returns null
        val endNext = shuffleManager.advanceToNextTrack(Player.REPEAT_MODE_OFF, tracks.last())
        assertNull(endNext)

        // At end of list with repeat ALL -> loops back to index 0
        val loopNext = shuffleManager.advanceToNextTrack(Player.REPEAT_MODE_ALL, tracks.last())
        assertEquals(tracks.first().id, loopNext?.id)
    }

    @Test
    fun testShuffleMode_SHUFFLE_ALL_locksStartTrackAndRandomizesRemaining() {
        val tracks = createMultiCategoryTracks()
        val startTrackId = 3L

        shuffleManager.setPlaybackContext(
            context = PlaybackContext(tracks = tracks),
            startIndex = 2, // Track 3 (id=3)
            shuffle = true,
            shuffleMode = AdvancedShuffleMode.SHUFFLE_ALL
        )

        assertTrue(shuffleManager.isShuffleEnabled())
        val traversal = shuffleManager.getShuffleTraversal()
        assertEquals(AdvancedShuffleMode.SHUFFLE_ALL, traversal.mode)
        val shuffledTrackIds = shuffleManager.getShuffledList().map { it.id }

        // Guarantee 1: Start track is strictly index 0
        assertEquals(startTrackId, shuffledTrackIds.first())

        // Guarantee 2: All tracks in the context are included without duplicates
        assertEquals(tracks.size, shuffledTrackIds.size)
        assertEquals(tracks.map { it.id }.toSet(), shuffledTrackIds.toSet())
    }

    @Test
    fun testShuffleMode_SHUFFLE_TRACKS_groupsCurrentCategoryFirst() {
        val tracks = createMultiCategoryTracks()
        val startTrack = tracks[1] // Track 2A in "Album A"

        shuffleManager.setPlaybackContext(
            context = PlaybackContext(tracks = tracks),
            startIndex = 1,
            shuffle = true,
            shuffleMode = AdvancedShuffleMode.SHUFFLE_TRACKS
        )

        val traversal = shuffleManager.getShuffleTraversal()
        assertEquals(AdvancedShuffleMode.SHUFFLE_TRACKS, traversal.mode)
        val shuffledTrackIds = shuffleManager.getShuffledList().map { it.id }
        assertEquals(startTrack.id, shuffledTrackIds.first())

        // The first 3 items must all be from "Album A" (ids: 1, 2, 3)
        val firstCategoryIds = shuffledTrackIds.take(3).toSet()
        assertEquals(setOf(1L, 2L, 3L), firstCategoryIds)

        // Full list preserved without duplicates
        assertEquals(tracks.size, shuffledTrackIds.size)
        assertEquals(tracks.map { it.id }.toSet(), shuffledTrackIds.toSet())
    }

    @Test
    fun testShuffleMode_SHUFFLE_CATEGORIES_preservesSequentialTracksInsideCategories() {
        val tracks = createMultiCategoryTracks()
        val startTrack = tracks[0] // Track 1A in Album A

        shuffleManager.setPlaybackContext(
            context = PlaybackContext(tracks = tracks),
            startIndex = 0,
            shuffle = true,
            shuffleMode = AdvancedShuffleMode.SHUFFLE_CATEGORIES
        )

        val traversal = shuffleManager.getShuffleTraversal()
        assertEquals(AdvancedShuffleMode.SHUFFLE_CATEGORIES, traversal.mode)
        val shuffledTrackIds = shuffleManager.getShuffledList().map { it.id }
        assertEquals(startTrack.id, shuffledTrackIds.first())

        // Start category "Album A" remaining tracks (Track 2A, Track 3A) should appear in order immediately
        val idx2A = shuffledTrackIds.indexOf(2L)
        val idx3A = shuffledTrackIds.indexOf(3L)
        assertTrue("Track 2A should precede Track 3A", idx2A < idx3A)

        // For Album B (ids 4, 5), 4 should precede 5
        val idx4 = shuffledTrackIds.indexOf(4L)
        val idx5 = shuffledTrackIds.indexOf(5L)
        assertTrue("Track 1B should precede Track 2B", idx4 < idx5)

        // For Album C (ids 6, 7, 8), 6 < 7 < 8
        val idx6 = shuffledTrackIds.indexOf(6L)
        val idx7 = shuffledTrackIds.indexOf(7L)
        val idx8 = shuffledTrackIds.indexOf(8L)
        assertTrue("Track 1C should precede Track 2C and 3C", idx6 < idx7 && idx7 < idx8)
    }

    @Test
    fun testShuffleMode_SHUFFLE_SONGS_AND_CATEGORIES_containsAllTracksDistinctly() {
        val tracks = createMultiCategoryTracks()
        val startTrack = tracks[4] // Track 2B in Album B

        shuffleManager.setPlaybackContext(
            context = PlaybackContext(tracks = tracks),
            startIndex = 4,
            shuffle = true,
            shuffleMode = AdvancedShuffleMode.SHUFFLE_SONGS_AND_CATEGORIES
        )

        val traversal = shuffleManager.getShuffleTraversal()
        assertEquals(AdvancedShuffleMode.SHUFFLE_SONGS_AND_CATEGORIES, traversal.mode)
        val shuffledTrackIds = shuffleManager.getShuffledList().map { it.id }
        assertEquals(startTrack.id, shuffledTrackIds.first())

        // All tracks must be present exactly once
        assertEquals(tracks.size, shuffledTrackIds.size)
        assertEquals(tracks.map { it.id }.toSet(), shuffledTrackIds.toSet())
    }

    // ==========================================
    // 4. Tests for Explicit Queue Integration
    // ==========================================

    @Test
    fun testExplicitQueue_takesPrecedenceAndReturnsSmoothly() {
        val tracks = createMultiCategoryTracks()
        shuffleManager.setLibraryContext(tracks, startIndex = 0, shuffle = false)

        val queuedTrack = createSampleTrack(999, "Priority Queued Song")
        shuffleManager.addToExplicitQueue(listOf(queuedTrack))

        assertEquals(1, shuffleManager.getExplicitQueue().size)

        // advanceToNextTrack must prioritize explicit queue
        val nextTrack = shuffleManager.advanceToNextTrack(Player.REPEAT_MODE_OFF, tracks[0])
        assertEquals(999L, nextTrack?.id)
        assertTrue(shuffleManager.isPlayingFromExplicitQueue())
        assertEquals(0, shuffleManager.getExplicitQueue().size)

        // Advancing again returns smoothly to library context
        val afterQueueTrack = shuffleManager.advanceToNextTrack(Player.REPEAT_MODE_OFF, nextTrack)
        assertFalse(shuffleManager.isPlayingFromExplicitQueue())
        assertEquals(tracks[1].id, afterQueueTrack?.id)
    }
}
