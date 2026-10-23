package sk.kubis.endlessdrive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import sk.kubis.endlessdrive.game.event.RoadEvent
import sk.kubis.endlessdrive.game.SceneEffects

class WeatherEventsTest {

    @Test
    fun fogAndStormAreTimedChips() {
        assertTrue(RoadEvent.FOG.timed)
        assertTrue(RoadEvent.STORM.timed)
        assertEquals("FOG", RoadEvent.FOG.chip)
        assertEquals("STORM", RoadEvent.STORM.chip)
        assertTrue(RoadEvent.FOG.duration > 20f)
        assertTrue(RoadEvent.STORM.duration > 20f)
    }

    @Test
    fun stormFlashIsMostlyQuietWithBriefPeaks() {
        var peaks = 0
        var samples = 0
        var t = 0f
        while (t < 60f) {
            val flash = SceneEffects.stormFlash(t)
            if (flash > 0.5f) peaks++
            if (flash > 0.02f) samples++
            t += 0.05f
        }
        assertTrue("blesk musí niekedy peakať", peaks > 5)
        assertTrue("väčšinu času je tma", samples < 120)
        assertEquals(0f, SceneEffects.stormFlash(0.05f), 0.001f)
        assertFalse(SceneEffects.stormFlash(0.05f) > 0.02f)
    }
}
