package sk.kubis.endlessdrive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import sk.kubis.endlessdrive.core.SeededRandom
import sk.kubis.endlessdrive.domain.model.ComponentCondition
import sk.kubis.endlessdrive.domain.model.ComponentSlot
import sk.kubis.endlessdrive.domain.model.ItemCatalog
import sk.kubis.endlessdrive.domain.model.ItemStack
import sk.kubis.endlessdrive.game.car.Car
import sk.kubis.endlessdrive.game.car.TireInjury
import sk.kubis.endlessdrive.game.car.WHEEL_HUB_FRAC
import sk.kubis.endlessdrive.ui.game.PuncturedTireShape
import sk.kubis.endlessdrive.ui.game.WheelMotionBlur

class WheelVisualTest {

    private fun carWithMatchingTires(): Car {
        val car = Car().apply { installStarterKit(SeededRandom(8L)) }
        val tyre = ItemStack(ItemCatalog.TIRE.id, ComponentCondition.USED, 0.8f)
        car.mount(ComponentSlot.TIRE_FRONT, tyre)
        car.mount(ComponentSlot.TIRE_REAR, ItemStack(ItemCatalog.TIRE.id, ComponentCondition.USED, 0.8f))
        return car
    }

    @Test
    fun punctureKeepsVisualRadiusPunctureDoesNotDropLikeRim() {
        val car = carWithMatchingTires()
        val inflated = car.wheelVisualScale(ComponentSlot.TIRE_FRONT)
        assertEquals(car.wheelScale(ComponentSlot.TIRE_FRONT), inflated, 0.001f)

        car.parts[ComponentSlot.TIRE_FRONT]!!.injury = TireInjury.PUNCTURED
        assertEquals(inflated, car.wheelVisualScale(ComponentSlot.TIRE_FRONT), 0.001f)
        assertEquals(0f, car.rimSagPitch(), 0.0001f)

        car.parts[ComponentSlot.TIRE_FRONT]!!.injury = TireInjury.SHREDDED
        assertEquals(inflated * WHEEL_HUB_FRAC, car.wheelVisualScale(ComponentSlot.TIRE_FRONT), 0.001f)
        assertTrue(car.wheelVisualScale(ComponentSlot.TIRE_FRONT) < car.wheelScale(ComponentSlot.TIRE_FRONT))
        assertTrue("predný disk musí sklopiť nos", car.rimSagPitch() < 0f)
    }

    @Test
    fun shreddedRearPitchesNoseUp() {
        val car = carWithMatchingTires()
        car.parts[ComponentSlot.TIRE_REAR]!!.injury = TireInjury.SHREDDED
        assertTrue(car.rimSagPitch() > 0f)
        assertEquals(
            car.wheelScale(ComponentSlot.TIRE_FRONT),
            car.wheelVisualScale(ComponentSlot.TIRE_FRONT),
            0.001f
        )
    }

    @Test
    fun puncturedPancakeStaysInsideInflatedBox() {
        val r = 40f
        val rr = PuncturedTireShape.rubberRadius(r, 1f)
        assertEquals(r, rr, 0.001f)
        assertEquals(r, PuncturedTireShape.rubberRadius(r, 0.12f), 0.001f)
        val half = PuncturedTireShape.contactHalf(rr)
        assertTrue("kontakt musí byť užší než priemer", half < rr)
        val cx = 100f
        PuncturedTireShape.controlXs(cx, rr).forEach { x ->
            assertTrue("riadiaci bod $x mimo ±rr", x in (cx - rr)..(cx + rr))
        }
    }

    @Test
    fun punctureWarpKeepsHubAndMapsPancakeInsideCircle() {
        val hub = WHEEL_HUB_FRAC
        val centre = PuncturedTireShape.destToSource(0f, 0f, hub)!!
        assertEquals(0f, centre.first, 0.001f)
        assertEquals(0f, centre.second, 0.001f)

        val hubPt = PuncturedTireShape.destToSource(hub * 0.4f, 0f, hub)!!
        assertEquals(hub * 0.4f, hubPt.first, 0.001f)
        assertEquals(0f, hubPt.second, 0.001f)

        val upper = PuncturedTireShape.destToSource(0.5f, -0.5f, hub)!!
        assertEquals(0.5f, upper.first, 0.02f)
        assertEquals(-0.5f, upper.second, 0.001f)

        val contact = PuncturedTireShape.destToSource(0f, 1f, hub)!!
        assertTrue(
            "kontakt musí čítať z dna gumy",
            kotlin.math.hypot(contact.first.toDouble(), contact.second.toDouble()) <= 1.001
        )
        assertEquals(0f, contact.first, 0.05f)

        val pad = PuncturedTireShape.destToSource(PuncturedTireShape.CONTACT_HALF_FRAC, 1f, hub)
        assertNotNull("roh placky musí mať vzorku gumy", pad)
        assertTrue(
            kotlin.math.hypot(pad!!.first.toDouble(), pad.second.toDouble()) <= 1.001
        )

        assertEquals(
            null,
            PuncturedTireShape.destToSource(0.95f, 0.95f, hub)
        )
    }

    @Test
    fun motionBlurKeepsTheTireOpaque() {
        for (blur in 1..4) {
            assertEquals(1f, WheelMotionBlur.copyAlpha(0, blur), 0.001f)
            for (b in 1 until blur) {
                val a = WheelMotionBlur.copyAlpha(b, blur)
                assertTrue("šmuh $b pri $blur krokoch má byť slabý, nie $a", a in 0.05f..0.35f)
            }
        }
        // Starý vzorec 1/blur by pri štyroch kópiách dal 0.25 a guma by
        // cez ňu ukazovala cestu.
        assertEquals(1f, WheelMotionBlur.copyAlpha(0, 4), 0.001f)
        assertTrue(WheelMotionBlur.copyAlpha(1, 4) < 0.30f)
        assertEquals(0f, WheelMotionBlur.copyAlpha(4, 4), 0.001f)
    }
}
