package sk.kubis.endlessdrive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import sk.kubis.endlessdrive.ui.menu.MenuRoadFx

class MenuRoadFxTest {

    @Test
    fun perspectiveGrowsTowardCamera() {
        val near = MenuRoadFx.perspective(0.9f)
        val far = MenuRoadFx.perspective(0.2f)
        assertTrue("bližšie musí byť väčšie p ($near vs $far)", near > far)
        assertEquals(0f, MenuRoadFx.perspective(0f), 0f)
        assertEquals(1f, MenuRoadFx.perspective(1f), 0f)
    }

    @Test
    fun fadeHidesLoopWrapAtHorizonAndFoot() {
        assertTrue(MenuRoadFx.travelFade(0.01f) < 0.08f)
        assertTrue(MenuRoadFx.travelFade(0.5f) > 0.85f)
        assertTrue(MenuRoadFx.travelFade(0.99f) < 0.15f)
    }

    @Test
    fun particlesSitOnAsphaltBetweenEdges() {
        val w = 640f
        val p = 0.6f
        val left = MenuRoadFx.leftEdgeX(w, p)
        val right = MenuRoadFx.rightEdgeX(w, p)
        val dust = MenuRoadFx.laneX(w, p, -0.36f)
        val exhaust = MenuRoadFx.laneX(w, p, 0f)
        assertTrue("prach mimo vozovky", dust in left..right)
        assertTrue("dym mimo vozovky", exhaust in left..right)
        assertTrue(MenuRoadFx.particleRadius(0.15f, 3.4f) < MenuRoadFx.particleRadius(0.9f, 3.4f))
        assertTrue("popredie nesmie byť obria guľa", MenuRoadFx.particleRadius(1f, 3.4f) < 4f)
    }
}
