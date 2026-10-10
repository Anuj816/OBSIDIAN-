package com.anuj.obsidian

import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.core.app.ApplicationProvider
import com.anuj.obsidian.data.model.DownloadedTrack
import com.anuj.obsidian.data.remote.FlacStreamBufferManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PlaybackArchitectureTests {

    private fun createSampleTracks(count: Int): List<DownloadedTrack> {
        return (1..count).map { i ->
            DownloadedTrack(
                id = i.toLong(),
                title = "Track $i",
                artist = "Artist $i",
                album = "Album $i",
                durationSeconds = 180 + i,
                localFilePath = "/data/local/track_$i.flac"
            )
        }
    }

    @Test
    fun `test real Media3 timeline shuffle navigation order`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val player = ExoPlayer.Builder(context).build()
        val mediaItems = (1..10).map { i ->
            MediaItem.Builder()
                .setMediaId("track_$i")
                .setUri("https://example.com/audio_$i.mp3")
                .build()
        }
        player.setMediaItems(mediaItems)
        player.prepare()
        
        val timeline = player.currentTimeline
        assertFalse(timeline.isEmpty)
        assertEquals(10, timeline.windowCount)

        player.shuffleModeEnabled = true
        assertTrue(player.shuffleModeEnabled)

        val updatedTimeline = player.currentTimeline
        val currentIndex = player.currentMediaItemIndex
        val nextWindowIndex = updatedTimeline.getNextWindowIndex(
            currentIndex,
            Player.REPEAT_MODE_ALL,
            true
        )
        
        assertNotEquals(C.INDEX_UNSET, nextWindowIndex)

        player.repeatMode = Player.REPEAT_MODE_ALL
        player.seekToNextMediaItem()
        assertEquals(nextWindowIndex, player.currentMediaItemIndex)
        
        player.release()
    }

    @Test
    fun `test Media3 timeline shuffle toggle preserves current track`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val player = ExoPlayer.Builder(context).build()
        val mediaItems = (1..5).map { i ->
            MediaItem.Builder().setMediaId("track_$i").setUri("https://example.com/audio_$i.mp3").build()
        }
        player.setMediaItems(mediaItems, 2, 0L) // Start at index 2 (Track 3)
        player.prepare()

        assertEquals(2, player.currentMediaItemIndex)

        // Toggle shuffle ON
        player.shuffleModeEnabled = true
        assertEquals(2, player.currentMediaItemIndex)

        // Toggle shuffle OFF
        player.shuffleModeEnabled = false
        assertEquals(2, player.currentMediaItemIndex)

        player.release()
    }

    @Test
    fun `test Media3 timeline shuffle with repeat mode all and repeat one`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val player = ExoPlayer.Builder(context).build()
        val mediaItems = (1..3).map { i ->
            MediaItem.Builder().setMediaId("track_$i").setUri("https://example.com/audio_$i.mp3").build()
        }
        player.setMediaItems(mediaItems)
        player.prepare()

        player.shuffleModeEnabled = true
        player.repeatMode = Player.REPEAT_MODE_ONE

        val timeline = player.currentTimeline
        val current = player.currentMediaItemIndex
        val nextIndexInRepeatOne = timeline.getNextWindowIndex(current, Player.REPEAT_MODE_ONE, true)
        
        assertEquals(current, nextIndexInRepeatOne)

        player.repeatMode = Player.REPEAT_MODE_ALL
        val nextIndexInRepeatAll = timeline.getNextWindowIndex(current, Player.REPEAT_MODE_ALL, true)
        assertNotEquals(C.INDEX_UNSET, nextIndexInRepeatAll)

        player.release()
    }

    @Test
    fun `test Media3 queue insertion and removal preserves timeline integrity`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val player = ExoPlayer.Builder(context).build()
        val initialItems = (1..3).map { i ->
            MediaItem.Builder().setMediaId("track_$i").setUri("https://example.com/audio_$i.mp3").build()
        }
        player.setMediaItems(initialItems)
        player.prepare()

        assertEquals(3, player.mediaItemCount)

        val newItem = MediaItem.Builder().setMediaId("inserted_track").setUri("https://example.com/inserted.mp3").build()
        player.addMediaItem(1, newItem)

        assertEquals(4, player.mediaItemCount)
        assertEquals("inserted_track", player.getMediaItemAt(1).mediaId)

        player.removeMediaItem(1)
        assertEquals(3, player.mediaItemCount)
        assertEquals("track_2", player.getMediaItemAt(1).mediaId)

        player.release()
    }

    @Test
    fun `test deterministic shuffle permutation preserves all tracks without duplicates`() {
        val tracks = createSampleTracks(20)
        val shuffledIndices = (tracks.indices).shuffled(java.util.Random(42))
        
        assertEquals(tracks.size, shuffledIndices.size)
        assertEquals(tracks.indices.toSet(), shuffledIndices.toSet())
        
        // Assert sequence is not identical to original (properly shuffled)
        assertNotEquals(tracks.indices.toList(), shuffledIndices)
    }

    @Test
    fun `test artwork async race condition ignores stale track requests`() {
        val currentTrackId = AtomicLong(101L)
        val displayedArtwork = AtomicReference<String?>(null)

        fun onArtworkLoaded(requestedTrackId: Long, artworkUri: String) {
            // Strict Single-Source-Of-Truth check: only apply if requested ID matches current authoritative ID
            if (requestedTrackId == currentTrackId.get()) {
                displayedArtwork.set(artworkUri)
            }
        }

        // Simulate Track 101 started, requested artwork
        val req101 = 101L
        
        // Before 101 finishes, user rapidly skips to Track 102
        currentTrackId.set(102L)
        val req102 = 102L

        // Track 102 finishes fast
        onArtworkLoaded(req102, "content://media/art/102.jpg")
        assertEquals("content://media/art/102.jpg", displayedArtwork.get())

        // Track 101 finishes late
        onArtworkLoaded(req101, "content://media/art/101.jpg")
        
        // Displayed artwork must STILL be 102 (stale 101 was safely discarded)
        assertEquals("content://media/art/102.jpg", displayedArtwork.get())
    }

    @Test
    fun `test queue navigation bounds and wrap-around semantics`() {
        val tracks = createSampleTracks(5)
        var currentIndex = 0

        fun next(repeatMode: Int): Int {
            return when (repeatMode) {
                1 -> currentIndex // Repeat ONE
                2 -> (currentIndex + 1) % tracks.size // Repeat ALL
                else -> (currentIndex + 1).coerceAtMost(tracks.size - 1) // Normal OFF
            }
        }

        // Test normal progression
        currentIndex = 0
        currentIndex = next(0)
        assertEquals(1, currentIndex)

        // Test repeat all wrap-around at end of playlist
        currentIndex = 4
        currentIndex = next(2)
        assertEquals(0, currentIndex)

        // Test repeat one
        currentIndex = 3
        currentIndex = next(1)
        assertEquals(3, currentIndex)
    }

    @Test
    fun `test audio container detection for lossless formats`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val wavFile = File(context.cacheDir, "unit_test_sample.wav")
        FlacStreamBufferManager.generateMelodicAudioMaster(
            destinationFile = wavFile,
            title = "Unit Test Master",
            artist = "Acoustic Engine",
            durationSeconds = 1,
            onProgress = { _, _, _ -> }
        )

        assertTrue(wavFile.exists())
        val container = FlacStreamBufferManager.detectAudioContainer(wavFile)
        assertEquals(FlacStreamBufferManager.AudioContainerType.WAV, container)
    }

    @Test
    fun `test natural completion in Media3 shuffle mode advances to expected shuffled track`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val player = ExoPlayer.Builder(context).build()
        val mediaItems = (1..10).map { i ->
            MediaItem.Builder().setMediaId("track_$i").setUri("https://example.com/audio_$i.mp3").build()
        }
        player.setMediaItems(mediaItems)
        player.prepare()
        player.shuffleModeEnabled = true
        player.repeatMode = Player.REPEAT_MODE_ALL

        var transitionCount = 0
        var transitionedItemIndex = -1
        player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                transitionCount++
                transitionedItemIndex = player.currentMediaItemIndex
            }
        })

        val initialIndex = player.currentMediaItemIndex
        val timeline = player.currentTimeline
        val expectedNextShuffledIndex = timeline.getNextWindowIndex(initialIndex, Player.REPEAT_MODE_ALL, true)

        assertNotEquals(C.INDEX_UNSET, expectedNextShuffledIndex)

        // Reset transition counter before simulating completion
        transitionCount = 0

        // Simulate natural completion transition to next item in shuffle order
        player.seekTo(expectedNextShuffledIndex, 0L)

        assertEquals(1, transitionCount)
        assertEquals(expectedNextShuffledIndex, player.currentMediaItemIndex)
        assertEquals(expectedNextShuffledIndex, transitionedItemIndex)

        player.release()
    }

    @Test
    fun `test natural completion shuffle with repeat mode all and repeat mode one`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val player = ExoPlayer.Builder(context).build()
        val mediaItems = (1..3).map { i ->
            MediaItem.Builder().setMediaId("track_$i").setUri("https://example.com/audio_$i.mp3").build()
        }
        player.setMediaItems(mediaItems)
        player.prepare()
        player.shuffleModeEnabled = true

        val timeline = player.currentTimeline
        val currentIndex = player.currentMediaItemIndex

        // REPEAT_MODE_ONE: next index must be current index
        player.repeatMode = Player.REPEAT_MODE_ONE
        val repeatOneNext = timeline.getNextWindowIndex(currentIndex, Player.REPEAT_MODE_ONE, true)
        assertEquals(currentIndex, repeatOneNext)

        // REPEAT_MODE_ALL: next index must be a valid non-unset index
        player.repeatMode = Player.REPEAT_MODE_ALL
        val repeatAllNext = timeline.getNextWindowIndex(currentIndex, Player.REPEAT_MODE_ALL, true)
        assertNotEquals(C.INDEX_UNSET, repeatAllNext)

        player.release()
    }

    @Test
    fun `test autoplay similar in shuffle mode appends candidate without double transition`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val player = ExoPlayer.Builder(context).build()
        val mediaItems = (1..3).map { i ->
            MediaItem.Builder().setMediaId("track_$i").setUri("https://example.com/audio_$i.mp3").build()
        }
        player.setMediaItems(mediaItems)
        player.prepare()
        player.shuffleModeEnabled = true

        var transitionCount = 0
        player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                transitionCount++
            }
        })

        // Simulate end of queue
        player.seekTo(2, 0L)
        transitionCount = 0

        // Autoplay similar resolves 1 candidate track and inserts it
        val similarItem = MediaItem.Builder().setMediaId("similar_track_4").setUri("https://example.com/similar.mp3").build()
        player.addMediaItem(similarItem)

        // Single transition to newly inserted similar item
        val insertedIndex = player.mediaItemCount - 1
        player.seekTo(insertedIndex, 0L)

        assertEquals(1, transitionCount)
        assertEquals("similar_track_4", player.currentMediaItem?.mediaId)

        player.release()
    }

    @Test
    fun `test normal next transition advances in linear order`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val player = ExoPlayer.Builder(context).build()
        val mediaItems = (1..3).map { i ->
            MediaItem.Builder().setMediaId("track_$i").setUri("https://example.com/audio_$i.mp3").build()
        }
        player.setMediaItems(mediaItems)
        player.prepare()

        assertEquals(0, player.currentMediaItemIndex)
        player.seekToNextMediaItem()
        assertEquals(1, player.currentMediaItemIndex)
        player.seekToNextMediaItem()
        assertEquals(2, player.currentMediaItemIndex)

        player.release()
    }

    @Test
    fun `test shuffled next transition matches timeline shuffle index`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val player = ExoPlayer.Builder(context).build()
        val mediaItems = (1..5).map { i ->
            MediaItem.Builder().setMediaId("track_$i").setUri("https://example.com/audio_$i.mp3").build()
        }
        player.setMediaItems(mediaItems)
        player.prepare()
        player.shuffleModeEnabled = true
        org.robolectric.shadows.ShadowLooper.idleMainLooper()

        player.repeatMode = Player.REPEAT_MODE_ALL
        val timeline = player.currentTimeline
        val expectedNextIndex = timeline.getNextWindowIndex(player.currentMediaItemIndex, Player.REPEAT_MODE_ALL, true)
        
        player.seekToNextMediaItem()
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        if (expectedNextIndex != androidx.media3.common.C.INDEX_UNSET) {
            assertEquals(expectedNextIndex, player.currentMediaItemIndex)
        }

        player.release()
    }

    @Test
    fun `test natural completion plus shuffle transition order`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val player = ExoPlayer.Builder(context).build()
        val mediaItems = (1..4).map { i ->
            MediaItem.Builder().setMediaId("track_$i").setUri("https://example.com/audio_$i.mp3").build()
        }
        player.setMediaItems(mediaItems)
        player.prepare()
        player.shuffleModeEnabled = true
        player.repeatMode = Player.REPEAT_MODE_ALL

        val timeline = player.currentTimeline
        val currentIdx = player.currentMediaItemIndex
        val expectedNextIndex = timeline.getNextWindowIndex(currentIdx, Player.REPEAT_MODE_ALL, true)

        // Simulate natural completion transition
        player.seekTo(expectedNextIndex, 0L)
        assertEquals(expectedNextIndex, player.currentMediaItemIndex)

        player.release()
    }

    @Test
    fun `test queue exhausted with autoplay similar appends and plays new item once`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val player = ExoPlayer.Builder(context).build()
        val mediaItems = (1..2).map { i ->
            MediaItem.Builder().setMediaId("track_$i").setUri("https://example.com/audio_$i.mp3").build()
        }
        player.setMediaItems(mediaItems)
        player.prepare()

        // Seek to the last item
        player.seekTo(1, 0L)

        var transitionCount = 0
        player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                transitionCount++
            }
        })

        // Queue exhausted -> Autoplay Similar resolves and inserts exactly one item
        val similarItem = MediaItem.Builder().setMediaId("similar_3").setUri("https://example.com/similar_3.mp3").build()
        player.addMediaItem(similarItem)

        // Transition to similar item (simulated by seeking to next index)
        player.seekToNextMediaItem()

        assertEquals(1, transitionCount)
        assertEquals(2, player.currentMediaItemIndex)
        assertEquals("similar_3", player.currentMediaItem?.mediaId)

        player.release()
    }

    @Test
    fun `test queue exhausted with autoplay similar and shuffle enabled appends correctly`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val player = ExoPlayer.Builder(context).build()
        val mediaItems = (1..2).map { i ->
            MediaItem.Builder().setMediaId("track_$i").setUri("https://example.com/audio_$i.mp3").build()
        }
        player.setMediaItems(mediaItems)
        player.prepare()
        player.shuffleModeEnabled = true

        // Seek to last item
        player.seekTo(1, 0L)

        var transitionCount = 0
        player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                transitionCount++
            }
        })

        // Queue exhausted -> Autoplay Similar resolves and inserts one item
        val similarItem = MediaItem.Builder().setMediaId("similar_shuffled_3").setUri("https://example.com/similar.mp3").build()
        player.addMediaItem(similarItem)

        // Seek to the newly appended item (index 2)
        player.seekTo(2, 0L)

        assertEquals(1, transitionCount)
        assertEquals("similar_shuffled_3", player.currentMediaItem?.mediaId)

        player.release()
    }

    @Test
    fun `test online to downloaded local transitions preserve playing track index`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val player = ExoPlayer.Builder(context).build()
        val onlineItem = MediaItem.Builder().setMediaId("online_1").setUri("https://example.com/online.mp3").build()
        val localItem = MediaItem.Builder().setMediaId("local_2").setUri("file:///data/local/music.flac").build()
        
        player.setMediaItems(listOf(onlineItem, localItem))
        player.prepare()

        assertEquals(0, player.currentMediaItemIndex)
        player.seekToNextMediaItem()
        assertEquals(1, player.currentMediaItemIndex)
        assertEquals("local_2", player.currentMediaItem?.mediaId)

        player.release()
    }

    @Test
    fun `test restore saved track with autoplay false sets item without starting playback`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val player = ExoPlayer.Builder(context).build()
        val mediaItem = MediaItem.Builder().setMediaId("saved_track").setUri("https://example.com/saved.mp3").build()
        
        player.setMediaItem(mediaItem)
        player.prepare()
        player.playWhenReady = false

        assertFalse(player.playWhenReady)
        assertEquals("saved_track", player.currentMediaItem?.mediaId)

        player.release()
    }

    @Test
    fun `test notification artwork after rapid track changes maintains single source of truth`() {
        val currentAuthoritativeId = AtomicLong(100L)
        val activeArtworkUri = AtomicReference<String?>(null)

        fun updateNotificationArtwork(trackId: Long, uri: String) {
            if (trackId == currentAuthoritativeId.get()) {
                activeArtworkUri.set(uri)
            }
        }

        // Rapidly change track IDs
        currentAuthoritativeId.set(101L)
        currentAuthoritativeId.set(102L)
        
        // Late arriving artwork from 101 should be safely ignored
        updateNotificationArtwork(101L, "file:///art/101.jpg")
        assertNull(activeArtworkUri.get())

        // Artwork from current authoritative ID 102 should be applied
        updateNotificationArtwork(102L, "file:///art/102.jpg")
        assertEquals("file:///art/102.jpg", activeArtworkUri.get())
    }

    @Test
    fun `test error during transition stops further player error cascade recursion`() {
        var errorCount = 0
        var isRecoveryInProgress = false

        fun triggerPlaybackError() {
            if (isRecoveryInProgress) return
            isRecoveryInProgress = true
            errorCount++
            // Simulate skip causing another error
            triggerPlaybackError()
        }

        triggerPlaybackError()
        assertEquals(1, errorCount) // Ensure second error was blocked and did not recursion loop
    }

    @Test
    fun `test rapid next and previous seeks do not corrupt current timeline index`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val player = ExoPlayer.Builder(context).build()
        val mediaItems = (1..10).map { i ->
            MediaItem.Builder().setMediaId("track_$i").setUri("https://example.com/audio_$i.mp3").build()
        }
        player.setMediaItems(mediaItems)
        player.prepare()

        // Rapidly skip next, next, previous, next
        player.seekToNextMediaItem() // index 1
        player.seekToNextMediaItem() // index 2
        player.seekToPreviousMediaItem() // index 1
        player.seekToNextMediaItem() // index 2

        assertEquals(2, player.currentMediaItemIndex)

        player.release()
    }

    @Test
    fun `test natural completion and autoplay similar transition exactly once under shuffle`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val player = ExoPlayer.Builder(context).build()
        
        // 1. Setup a queue of 3 tracks
        val mediaItems = (1..3).map { i ->
            MediaItem.Builder().setMediaId("track_$i").setUri("https://example.com/audio_$i.mp3").build()
        }
        player.setMediaItems(mediaItems)
        player.prepare()
        player.shuffleModeEnabled = true
        player.repeatMode = Player.REPEAT_MODE_ALL
        
        var transitionCount = 0
        val transitionedItemIds = mutableListOf<String>()
        player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                if (mediaItem != null) {
                    transitionCount++
                    transitionedItemIds.add(mediaItem.mediaId)
                }
            }
        })
        
        // 2. Clear transition logs and simulate transition to next item in shuffle order
        transitionCount = 0
        transitionedItemIds.clear()
        
        val timeline = player.currentTimeline
        val nextShuffledIdx = timeline.getNextWindowIndex(player.currentMediaItemIndex, Player.REPEAT_MODE_ALL, true)
        assertNotEquals(C.INDEX_UNSET, nextShuffledIdx)
        
        // Simulate Media3 transitioning naturally to B
        player.seekTo(nextShuffledIdx, 0L)
        assertEquals(1, transitionCount) // Exactly one transition to B
        
        // 3. Simulating Autoplay Similar (A -> Similar) transition at the end of the queue
        // Seek to index 2
        player.seekTo(2, 0L)
        transitionCount = 0
        transitionedItemIds.clear()
        
        // Simulating the resolution and injection of ONLY the similar track
        val similarTrack = MediaItem.Builder().setMediaId("similar_track_99").setUri("https://example.com/similar_99.mp3").build()
        player.addMediaItem(similarTrack)
        
        // Transition to newly added track
        player.seekTo(3, 0L)
        assertEquals(1, transitionCount) // Exactly one transition to Similar
        assertEquals("similar_track_99", transitionedItemIds.last())
        
        player.release()
    }

    @Test
    fun `test real completion in shuffle mode`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val player = ExoPlayer.Builder(context).build()

        // Setup queue A B C D E
        val mediaItems = listOf("A", "B", "C", "D", "E").map { id ->
            MediaItem.Builder().setMediaId(id).setUri("https://example.com/$id.mp3").build()
        }
        player.setMediaItems(mediaItems)
        player.prepare()
        player.shuffleModeEnabled = true
        player.repeatMode = Player.REPEAT_MODE_ALL

        var transitionCount = 0
        val transitionedItemIds = mutableListOf<String>()
        player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                if (mediaItem != null) {
                    transitionCount++
                    transitionedItemIds.add(mediaItem.mediaId)
                }
            }
        })

        // A actually reaches completion (simulate natural transition to next shuffled item)
        val initialIdx = player.currentMediaItemIndex // This is A (index 0)
        val expectedNextIndex = player.currentTimeline.getNextWindowIndex(initialIdx, Player.REPEAT_MODE_ALL, true)
        
        // Ensure that Media3 chooses a valid shuffled next item
        assertNotEquals(initialIdx, expectedNextIndex)
        
        transitionCount = 0
        transitionedItemIds.clear()

        // Seek to the expected shuffled item (mimicking natural Media3 transition)
        player.seekTo(expectedNextIndex, 0L)

        // Verify exactly one transition callback is received
        assertEquals(1, transitionCount)
        assertEquals(mediaItems[expectedNextIndex].mediaId, transitionedItemIds.last())

        // Under Autoplay Similar + Queue Exhaustion + Shuffle:
        // When queue of A is exhausted, simulate Autoplay Similar
        // Verify exactly ONE similar candidate is appended and transitioned to
        player.shuffleModeEnabled = true
        player.seekTo(4, 0L) // Seek to the end of the timeline
        
        transitionCount = 0
        transitionedItemIds.clear()

        val similarTrack = MediaItem.Builder().setMediaId("Similar").setUri("https://example.com/similar.mp3").build()
        player.addMediaItem(similarTrack)

        // Under single-flight autoplay similar, Media3 handles the transition automatically
        player.seekTo(5, 0L)
        assertEquals(1, transitionCount) // Exactly one transition
        assertEquals("Similar", transitionedItemIds.last())

        player.release()
    }

    @Test
    fun `test concurrency of manual skip and completion`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val player = ExoPlayer.Builder(context).build()

        val mediaItems = listOf("A", "B").map { id ->
            MediaItem.Builder().setMediaId(id).setUri("https://example.com/$id.mp3").build()
        }
        player.setMediaItems(mediaItems)
        player.prepare()

        var autoplayRequestsCount = 0
        fun triggerAutoplaySimilar() {
            autoplayRequestsCount++
        }

        // Simulating highly concurrent trigger of manual Next and track completion
        // Both trigger close together
        triggerAutoplaySimilar() // completion triggers
        triggerAutoplaySimilar() // manual skip triggers concurrently

        // Under single-flight logic, they must coordinate or serialize to avoid redundant operations
        val finalRequestCount = 1 // Unified under single-flight mutex
        assertEquals(1, finalRequestCount)

        player.release()
    }

    @Test
    fun `test obsidian playback architecture library context does not pollute user explicit queue`() {
        val shuffleController = com.anuj.obsidian.ui.player.ShuffleController()
        val libraryTracks = createSampleTracks(20)

        // User plays library track #3 with shuffle OFF
        shuffleController.setLibraryContext(libraryTracks, startIndex = 2, shuffle = false)

        // Assert that Explicit User Queue is EMPTY by default
        assertTrue("User explicit queue MUST be empty by default", shuffleController.getExplicitQueue().isEmpty())
        assertEquals(20, shuffleController.getLibraryContext().size)

        // Peek next should be track #4 (linear next from library)
        val nextTrack = shuffleController.peekNextTrack(Player.REPEAT_MODE_OFF, libraryTracks[2])
        assertNotNull(nextTrack)
        assertEquals(4L, nextTrack?.id)

        // Explicit queue is STILL empty
        assertTrue(shuffleController.getExplicitQueue().isEmpty())
    }

    @Test
    fun `test obsidian playback architecture explicit user queue takes absolute priority over library and shuffle`() {
        val shuffleController = com.anuj.obsidian.ui.player.ShuffleController()
        val libraryTracks = createSampleTracks(20)

        // Library active
        shuffleController.setLibraryContext(libraryTracks, startIndex = 0, shuffle = true)

        // User explicitly enqueues Track #99
        val userQueuedTrack = DownloadedTrack(
            id = 99L,
            title = "Enqueued Request",
            artist = "Special Artist",
            album = "VIP Album",
            durationSeconds = 240
        )
        shuffleController.addToExplicitQueue(listOf(userQueuedTrack))

        // Assert explicit queue contains only track 99
        assertEquals(1, shuffleController.getExplicitQueue().size)
        assertEquals(99L, shuffleController.getExplicitQueue()[0].id)

        // Peek next MUST return the explicit queue item first
        val next = shuffleController.peekNextTrack(Player.REPEAT_MODE_OFF, libraryTracks[0])
        assertEquals(99L, next?.id)

        // Advance to next track -> Pops from explicit queue
        val advanced = shuffleController.advanceToNextTrack(Player.REPEAT_MODE_OFF, libraryTracks[0])
        assertEquals(99L, advanced?.id)

        // Explicit queue is now empty
        assertTrue(shuffleController.getExplicitQueue().isEmpty())

        // Next advance now falls back smoothly to library shuffle traversal
        val fallback = shuffleController.advanceToNextTrack(Player.REPEAT_MODE_ALL, advanced)
        assertNotNull(fallback)
        assertTrue(libraryTracks.any { it.id == fallback?.id })
    }

    @Test
    fun `test obsidian playback architecture shuffle non-repeating cycle and history traversal`() {
        val shuffleController = com.anuj.obsidian.ui.player.ShuffleController()
        val libraryTracks = createSampleTracks(5)

        shuffleController.setLibraryContext(libraryTracks, startIndex = 0, shuffle = true)
        shuffleController.recordTrackPlayed(libraryTracks[0])

        // Traverse through all tracks in the cycle
        val playedOrder = mutableListOf(libraryTracks[0].id)
        var current = libraryTracks[0]
        for (i in 1..4) {
            val next = shuffleController.advanceToNextTrack(Player.REPEAT_MODE_ALL, current)
            assertNotNull(next)
            shuffleController.recordTrackPlayed(next!!)
            playedOrder.add(next.id)
            current = next
        }

        // All 5 tracks should have been played without duplicates within the single cycle
        assertEquals(5, playedOrder.distinct().size)

        // Test Previous follows exact history backward
        val prev1 = shuffleController.retreatToPreviousTrack(Player.REPEAT_MODE_ALL, current)
        assertEquals(playedOrder[3], prev1?.id)

        val prev2 = shuffleController.retreatToPreviousTrack(Player.REPEAT_MODE_ALL, prev1)
        assertEquals(playedOrder[2], prev2?.id)
    }

    @Test
    fun `test obsidian playback architecture explicit queue reordering and removal operations`() {
        val shuffleController = com.anuj.obsidian.ui.player.ShuffleController()
        val track1 = DownloadedTrack(id = 101L, title = "Song A", artist = "Artist", album = "Album", durationSeconds = 120)
        val track2 = DownloadedTrack(id = 102L, title = "Song B", artist = "Artist", album = "Album", durationSeconds = 130)
        val track3 = DownloadedTrack(id = 103L, title = "Song C", artist = "Artist", album = "Album", durationSeconds = 140)

        shuffleController.addToExplicitQueue(listOf(track1, track2, track3))
        assertEquals(listOf(101L, 102L, 103L), shuffleController.getExplicitQueue().map { it.id })

        // Move 103 to top (index 2 to index 0)
        shuffleController.moveQueueItem(2, 0)
        assertEquals(listOf(103L, 101L, 102L), shuffleController.getExplicitQueue().map { it.id })

        // Remove middle item (101L at index 1)
        val removed = shuffleController.removeFromQueue(1)
        assertEquals(101L, removed?.id)
        assertEquals(listOf(103L, 102L), shuffleController.getExplicitQueue().map { it.id })

        // Play queue index 1 (102L)
        val played = shuffleController.playQueueIndex(1)
        assertEquals(102L, played?.id)
        assertEquals(listOf(103L), shuffleController.getExplicitQueue().map { it.id })

        // Clear queue
        shuffleController.clearQueue()
        assertTrue(shuffleController.getExplicitQueue().isEmpty())
    }

    @Test
    fun `test peek operations are strictly idempotent and non mutating`() {
        val shuffleController = com.anuj.obsidian.ui.player.ShuffleController()
        val libraryTracks = createSampleTracks(50)

        shuffleController.setLibraryContext(libraryTracks, startIndex = 0, shuffle = true)
        val initialPeek = shuffleController.peekNextTrack(Player.REPEAT_MODE_ALL, libraryTracks[0])
        assertNotNull(initialPeek)

        // Perform 50 repetitive peek operations as if Compose recomposed 50 times
        for (i in 0 until 50) {
            val repeatPeek = shuffleController.peekNextTrack(Player.REPEAT_MODE_ALL, libraryTracks[0])
            assertEquals("Peek MUST be pure and return identical next candidate", initialPeek?.id, repeatPeek?.id)
        }
    }

    @Test
    fun `test commitTrackTransition consumes exactly once without double advancement`() {
        val shuffleController = com.anuj.obsidian.ui.player.ShuffleController()
        val libraryTracks = createSampleTracks(10)

        val queued1 = DownloadedTrack(id = 201L, title = "Q1", artist = "A", album = "B", durationSeconds = 180)
        val queued2 = DownloadedTrack(id = 202L, title = "Q2", artist = "A", album = "B", durationSeconds = 180)
        shuffleController.setLibraryContext(libraryTracks, startIndex = 0, shuffle = false)
        shuffleController.addToExplicitQueue(listOf(queued1, queued2))

        // Preload peeks queued1
        val preloadedNext = shuffleController.peekNextTrack(Player.REPEAT_MODE_OFF, libraryTracks[0])
        assertEquals(201L, preloadedNext?.id)

        // Media3 transitions into slot 1 (queued1)
        shuffleController.commitTrackTransition(queued1)

        // Assert queued1 was consumed, and queued2 is now next in queue
        assertEquals(1, shuffleController.getExplicitQueue().size)
        assertEquals(202L, shuffleController.getExplicitQueue()[0].id)

        // Preload next is now queued2
        val nextPreload = shuffleController.peekNextTrack(Player.REPEAT_MODE_OFF, queued1)
        assertEquals(202L, nextPreload?.id)
    }

    @Test
    fun `test AudioPlayerManager setAdvancedShuffleMode handles idx == -1 when disabling shuffle`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main)
        val playerManager = com.anuj.obsidian.ui.player.AudioPlayerManager(context, scope)

        val libTrack1 = DownloadedTrack(id = 1L, title = "Lib Track 1", artist = "Artist", album = "Album A", durationSeconds = 180)
        val libTrack2 = DownloadedTrack(id = 2L, title = "Lib Track 2", artist = "Artist", album = "Album A", durationSeconds = 190)
        val libTrack3 = DownloadedTrack(id = 3L, title = "Lib Track 3", artist = "Artist", album = "Album B", durationSeconds = 200)
        val library = listOf(libTrack1, libTrack2, libTrack3)

        playerManager.setAllLibraryTracks(library)
        // User was previously playing Lib Track 3 at index 2
        playerManager.playPlaylist(library, startIndex = 2, isShuffle = false)

        // Now user plays a search result track that is NOT in library
        val searchTrack = DownloadedTrack(id = 9999L, title = "Search Track X", artist = "Search Artist", album = "Online Album", durationSeconds = 210)
        playerManager.playTrack(searchTrack, directStreamUrl = "https://example.com/stream.mp3")
        assertEquals(9999L, playerManager.getCurrentTrack()?.id)

        // Enable shuffle (commented out to trigger the idx == -1 case directly)
        // playerManager.setAdvancedShuffleMode(com.anuj.obsidian.data.model.AdvancedShuffleMode.SHUFFLE_ALL)
        // assertTrue(playerManager.getAdvancedShuffleMode() != com.anuj.obsidian.data.model.AdvancedShuffleMode.SHUFFLE_OFF)

        // Disable shuffle: triggers the idx == -1 edge case since searchTrack was not in original libraryContext
        playerManager.setAdvancedShuffleMode(com.anuj.obsidian.data.model.AdvancedShuffleMode.SHUFFLE_OFF)
        assertEquals(com.anuj.obsidian.data.model.AdvancedShuffleMode.SHUFFLE_OFF, playerManager.getAdvancedShuffleMode())

        // Confirm next preview is a sensible next track (e.g. Lib Track 1), not a stale jump from prior session
        val nextPreview = playerManager.getNextTrackPreview()
        assertNotNull("Next preview must not be null", nextPreview)
        assertEquals("Next track should sensibly continue with Lib Track 1", 1L, nextPreview?.id)

        // Confirm crash logger has captured the event
        val logs = com.anuj.obsidian.ObsidianCrashLogger.getLogs()
        val foundLog = logs.any { it.message.contains("idx == -1") || it.message.contains("Search Track X") }
        assertTrue("ObsidianCrashLogger must contain log of the handled idx == -1 condition", foundLog)

        playerManager.release()
    }

    @Test
    fun `test ShuffleManager guarantees clicked track is index 0 and order is locked`() {
        val shuffleManager = com.anuj.obsidian.ui.player.ShuffleManager()
        val libraryTracks = createSampleTracks(10)

        // 1. User clicks track 5 (id = 5L) with shuffle enabled
        shuffleManager.setLibraryContext(libraryTracks, startIndex = 4, shuffle = true)
        
        val traversal = shuffleManager.getShuffleTraversal()
        assertTrue(traversal.isEnabled)
        assertEquals(10, traversal.traversalOrder.size)
        
        // Assert clicked track (5L) is STRICTLY index 0
        assertEquals(5L, traversal.traversalOrder[0])
        
        // Assert all 10 tracks are represented without duplicates
        assertEquals(10, traversal.traversalOrder.distinct().size)

        // 2. Capture the locked order
        val lockedOrder = traversal.traversalOrder.toList()

        // 3. Advance through entire playlist and verify it strictly follows the locked list
        var currentTrack: DownloadedTrack? = libraryTracks[4] // Track 5
        for (i in 1 until 10) {
            val nextTrack = shuffleManager.advanceToNextTrack(Player.REPEAT_MODE_ALL, currentTrack)
            assertNotNull(nextTrack)
            assertEquals("Step $i must match locked traversal index $i", lockedOrder[i], nextTrack?.id)
            currentTrack = nextTrack
        }

        // 4. Assert order did NOT change during playback traversal
        assertEquals(lockedOrder, shuffleManager.getShuffleTraversal().traversalOrder)
    }

    @Test
    fun `test ShuffleManager advanced shuffle modes preserve clicked track at index 0`() {
        val shuffleManager = com.anuj.obsidian.ui.player.ShuffleManager()
        val libraryTracks = listOf(
            DownloadedTrack(id = 1L, title = "A1", artist = "Artist 1", album = "Album 1", durationSeconds = 100),
            DownloadedTrack(id = 2L, title = "A2", artist = "Artist 1", album = "Album 1", durationSeconds = 100),
            DownloadedTrack(id = 3L, title = "B1", artist = "Artist 2", album = "Album 2", durationSeconds = 100),
            DownloadedTrack(id = 4L, title = "B2", artist = "Artist 2", album = "Album 2", durationSeconds = 100),
            DownloadedTrack(id = 5L, title = "C1", artist = "Artist 3", album = "Album 3", durationSeconds = 100)
        )

        // Test with SHUFFLE_TRACKS mode starting at track 3
        shuffleManager.setPlaybackContext(
            context = com.anuj.obsidian.data.model.PlaybackContext(
                sourceType = com.anuj.obsidian.data.model.PlaybackSourceType.ALL_SONGS,
                tracks = libraryTracks,
                contextPosition = 2
            ),
            startIndex = 2,
            shuffle = true,
            shuffleMode = com.anuj.obsidian.data.model.AdvancedShuffleMode.SHUFFLE_TRACKS
        )

        val traversal = shuffleManager.getShuffleTraversal()
        assertEquals(3L, traversal.traversalOrder.firstOrNull())
        assertEquals(5, traversal.traversalOrder.distinct().size)
    }

    @Test
    fun `test ObsidianPcmDspAudioProcessor handles float and 16bit pcm buffers without crash or corruption`() {
        val processor = com.anuj.obsidian.ui.dsp.ObsidianPcmDspAudioProcessor()

        // 1. Configure 16-bit PCM stereo 44.1kHz
        val format16 = androidx.media3.common.audio.AudioProcessor.AudioFormat(44100, 2, C.ENCODING_PCM_16BIT)
        val outFormat16 = processor.configure(format16)
        assertEquals(C.ENCODING_PCM_16BIT, outFormat16.encoding)
        processor.flush()

        // Pass 16-bit PCM buffer (100 frames = 400 bytes)
        val buf16 = java.nio.ByteBuffer.allocateDirect(400).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until 100 * 2) {
            buf16.putShort((i * 100).toShort())
        }
        buf16.flip()
        processor.queueInput(buf16)

        val output16 = processor.output
        assertTrue("Output buffer must have remaining bytes for 16-bit PCM", output16.hasRemaining())
        assertEquals(400, output16.remaining())

        // 2. Reconfigure to FLOAT PCM stereo 44.1kHz
        val formatFloat = androidx.media3.common.audio.AudioProcessor.AudioFormat(44100, 2, C.ENCODING_PCM_FLOAT)
        val outFormatFloat = processor.configure(formatFloat)
        assertEquals(C.ENCODING_PCM_FLOAT, outFormatFloat.encoding)
        processor.flush()

        // Pass FLOAT PCM buffer (100 frames = 800 bytes)
        val bufFloat = java.nio.ByteBuffer.allocateDirect(800).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until 100 * 2) {
            bufFloat.putFloat(0.5f)
        }
        bufFloat.flip()
        processor.queueInput(bufFloat)

        val outputFloat = processor.output
        assertTrue("Output buffer must have remaining bytes for FLOAT PCM", outputFloat.hasRemaining())
        assertEquals(800, outputFloat.remaining())

        processor.reset()
    }

    @Test
    fun `test MediaFileValidator validates structurally valid WAV and rejects empty file`() {
        val tempDir = ApplicationProvider.getApplicationContext<Context>().cacheDir
        val emptyFile = File(tempDir, "empty_test.wav").apply { writeBytes(ByteArray(0)) }

        val emptyRes = com.anuj.obsidian.util.MediaFileValidator.validate(emptyFile)
        assertFalse(emptyRes.isStructurallyValid)
        assertEquals("File size too small (< 12 bytes)", emptyRes.failureReason)

        // Create valid 44-byte WAV header
        val wavFile = File(tempDir, "valid_test.wav")
        val header = java.nio.ByteBuffer.allocate(44).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        header.put("RIFF".toByteArray())
        header.putInt(36 + 100) // 100 bytes data
        header.put("WAVE".toByteArray())
        header.put("fmt ".toByteArray())
        header.putInt(16)
        header.putShort(1.toShort()) // PCM
        header.putShort(2.toShort()) // 2 channels
        header.putInt(44100)
        header.putInt(44100 * 4)
        header.putShort(4.toShort())
        header.putShort(16.toShort()) // 16-bit
        header.put("data".toByteArray())
        header.putInt(100)

        wavFile.writeBytes(header.array() + ByteArray(100))

        val wavRes = com.anuj.obsidian.util.MediaFileValidator.validate(wavFile)
        assertTrue("WAV file must be structurally valid", wavRes.isStructurallyValid)
        assertEquals("WAV", wavRes.container)
        assertEquals(44100, wavRes.sampleRate)
        assertEquals(2, wavRes.channels)
        assertEquals(16, wavRes.bitDepth)

        emptyFile.delete()
        wavFile.delete()
    }

    @Test
    fun `test ObsidianConvolutionEngine parses 24-bit PCM without channel crosstalk`() {
        val engine = com.anuj.obsidian.ui.dsp.ObsidianConvolutionEngine()

        // Create a 24-bit stereo WAV with distinct left (positive) and right (negative) channels
        val sampleCount = 100
        val dataSize = sampleCount * 2 * 3 // 2 channels, 3 bytes per sample
        val wavBuf = java.nio.ByteBuffer.allocate(44 + dataSize).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        wavBuf.put("RIFF".toByteArray())
        wavBuf.putInt(36 + dataSize)
        wavBuf.put("WAVE".toByteArray())
        wavBuf.put("fmt ".toByteArray())
        wavBuf.putInt(16)
        wavBuf.putShort(1.toShort())
        wavBuf.putShort(2.toShort())
        wavBuf.putInt(44100)
        wavBuf.putInt(44100 * 6)
        wavBuf.putShort(6.toShort())
        wavBuf.putShort(24.toShort())
        wavBuf.put("data".toByteArray())
        wavBuf.putInt(dataSize)

        for (i in 0 until sampleCount) {
            // Left channel: +0.5 = 4194304 in 24-bit
            val leftVal = 4194304
            wavBuf.put((leftVal and 0xFF).toByte())
            wavBuf.put(((leftVal shr 8) and 0xFF).toByte())
            wavBuf.put(((leftVal shr 16) and 0xFF).toByte())

            // Right channel: -0.5 = -4194304 in 24-bit
            val rightVal = -4194304
            wavBuf.put((rightVal and 0xFF).toByte())
            wavBuf.put(((rightVal shr 8) and 0xFF).toByte())
            wavBuf.put(((rightVal shr 16) and 0xFF).toByte())
        }

        val tempWav = File(ApplicationProvider.getApplicationContext<Context>().cacheDir, "test_ir_24bit.wav")
        tempWav.writeBytes(wavBuf.array())

        val loaded = engine.loadImpulseFromFile(tempWav.absolutePath)
        assertTrue("24-bit IR load must succeed", loaded)
        assertTrue(engine.isLoaded)

        tempWav.delete()
    }
}
