package com.anuj.obsidian

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.anuj.obsidian.data.remote.FlacStreamBufferManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Obsidian", appName)
    }

    @Test
    fun `test lossless acoustic master generation`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val testFile = File(context.cacheDir, "test_track.wav")
        val generated = FlacStreamBufferManager.generateMelodicAudioMaster(
            destinationFile = testFile,
            title = "Test Song",
            artist = "Test Artist",
            durationSeconds = 2,
            onProgress = { _, _, _ -> }
        )

        assertTrue(generated.exists())
        assertTrue(generated.length() > 1000L)
        val container = FlacStreamBufferManager.detectAudioContainer(generated)
        assertEquals(FlacStreamBufferManager.AudioContainerType.WAV, container)
    }
}
