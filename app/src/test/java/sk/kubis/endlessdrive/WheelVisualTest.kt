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






}
