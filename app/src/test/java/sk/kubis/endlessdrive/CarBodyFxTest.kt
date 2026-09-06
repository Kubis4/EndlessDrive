package sk.kubis.endlessdrive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import sk.kubis.endlessdrive.ui.game.CarBodyFx
import sk.kubis.endlessdrive.ui.game.WheelContactFx
import kotlin.math.PI
import kotlin.math.abs

class CarBodyFxTest {

    @Test
    fun sourcesSitOnTheSpriteNotTheHub() {
        assertTrue("výfuk musí byť vzadu", CarBodyFx.EXHAUST_FX < 0.12f)
        assertTrue("chladič vpredu", CarBodyFx.RADIATOR_FX > 0.85f)
        assertTrue("kapota pred stredom", CarBodyFx.HOOD_VENT_FX > 0.70f)
        assertTrue(CarBodyFx.RADIATOR_FX > CarBodyFx.HOOD_VENT_FX)
        assertTrue(CarBodyFx.HOOD_VENT_FY < CarBodyFx.RADIATOR_FY)
        assertTrue(CarBodyFx.EXHAUST_FY > 0.6f)
    }

    @Test
    fun exhaustTrailsAlongCarPitchNotScreenHorizontal() {
        val axis = CarBodyFx.carAxisRad((PI / 6.0).toFloat())
        val along = CarBodyFx.exhaustAlong(0.8f, 10f, 20f, 0.5f)
        assertTrue("dym ide dozadu", along < 0f)
        val p = WheelContactFx.alongRoad(0f, 0f, axis, along, 0f)
        assertTrue("stopa musí ísť so sklonom", abs(p.second) > 0.5f)
        val idle = CarBodyFx.exhaustAlong(0.5f, 0f, 20f, 0.5f)
        assertTrue(idle < 0f)
    }

    @Test
    fun steamRisesFromRadiatorAndMisfirePops() {
        val lift = CarBodyFx.steamLift(0.6f, 24f, 0.4f)
        assertTrue("para ide hore", lift < 0f)
        assertTrue(CarBodyFx.steamRadius(0.9f, 24f, 0.5f) < 24f * 0.12f)
        assertTrue(CarBodyFx.misfirePopping(0.05f))
        assertFalse(CarBodyFx.misfirePopping(0.5f))
        assertTrue(CarBodyFx.misfireJetAlong(0.4f, 20f) < 0f)
    }

    @Test
    fun puffsStaySmallerThanOldBlobs() {
        assertTrue(CarBodyFx.exhaustRadius(1f, 30f, 1f, 1f) < 30f * 0.14f)
        assertEquals(-0.4f, CarBodyFx.carAxisRad(0.4f), 0.0001f)
    }
}
