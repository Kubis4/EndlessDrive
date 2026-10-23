package sk.kubis.endlessdrive

import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AudioResourceTest {
    private val rawSounds = intArrayOf(
        R.raw.sfx_belt,
        R.raw.sfx_blowout,
        R.raw.sfx_brake,
        R.raw.sfx_coolant_hiss,
        R.raw.sfx_debris,
        R.raw.sfx_engine_idle,
        R.raw.sfx_engine_rev,
        R.raw.sfx_engine_start,
        R.raw.sfx_engine_stop,
        R.raw.sfx_fail_start,
        R.raw.sfx_find,
        R.raw.sfx_leak,
        R.raw.sfx_leak_loop,
        R.raw.sfx_misfire,
        R.raw.sfx_mud_1,
        R.raw.sfx_mud_2,
        R.raw.sfx_mud_3,
        R.raw.sfx_mud_4,
        R.raw.sfx_mud_5,
        R.raw.sfx_oil_splash,
        R.raw.sfx_radio,
        R.raw.sfx_skid,
        R.raw.sfx_splash_1,
        R.raw.sfx_stall,
        R.raw.sfx_surface_gravel,
        R.raw.sfx_surface_ice,
        R.raw.sfx_surface_mud,
        R.raw.sfx_surface_water,
        R.raw.sfx_wind,
        R.raw.sfx_rain,
        R.raw.sfx_thunder,
        R.raw.sfx_thunder_roll
    )

    @Test
    fun everyAudioResourceIsDecodableOnAndroid() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        rawSounds.forEach { resource ->
            val descriptor = context.resources.openRawResourceFd(resource)
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(
                    descriptor.fileDescriptor,
                    descriptor.startOffset,
                    descriptor.length
                )
                val duration = retriever.extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_DURATION
                )?.toLongOrNull()
                assertNotNull("Missing duration for raw resource $resource", duration)
                assertTrue("Empty raw resource $resource", duration!! > 0L)
            } finally {
                retriever.release()
                descriptor.close()
            }
        }
    }

    @Test
    fun weatherPlayersCanStart() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        intArrayOf(R.raw.sfx_rain, R.raw.sfx_thunder, R.raw.sfx_thunder_roll).forEach { resource ->
            val player = MediaPlayer.create(context, resource)
            assertNotNull("MediaPlayer rejected raw resource $resource", player)
            try {
                player!!.setVolume(0f, 0f)
                player.start()
                assertTrue("MediaPlayer did not start raw resource $resource", player.isPlaying)
            } finally {
                player?.release()
            }
        }
    }
}
