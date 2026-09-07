package sk.kubis.endlessdrive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import sk.kubis.endlessdrive.ui.game.BackdropLayout

class BackdropLayoutTest {
    @Test
    fun parallaxAnchorStaysFixedAcrossHillsAndScreenShapes() {
        val heights = listOf(720f, 932f, 1080f, 1440f)
        val authoredRises = listOf(0f, 0.028f, 0.04f, 0.045f, 0.08f)
        for (height in heights) {
            val horizon = height * 0.56f
            val anchor = BackdropLayout.anchorForHorizon(horizon, height)
            for (rise in authoredRises) {
                val mid = BackdropLayout.midBase(anchor, height, rise)
                val near = BackdropLayout.nearBase(mid, height)
                assertTrue(
                    "mid moved too far from the horizon",
                    mid >= height * 0.574f && mid <= height * 0.586f
                )
                assertTrue("near must cover more of the join than mid", near > mid)
                assertTrue("near must remain close to the horizon", near < height * 0.60f)
            }
        }
    }

    @Test
    fun forestCelestialDiscStopsAtTheCanopyLine() {
        val height = 1000f
        val horizon = height * 0.56f
        assertEquals(horizon, BackdropLayout.celestialClipBottom(horizon, height, false), 0.01f)
        assertEquals(horizon, BackdropLayout.celestialClipBottom(horizon, height, true), 0.01f)
    }
}
