package sk.kubis.endlessdrive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import sk.kubis.endlessdrive.core.GameConfig
import sk.kubis.endlessdrive.core.SeededRandom
import sk.kubis.endlessdrive.domain.model.ComponentSlot
import sk.kubis.endlessdrive.domain.model.DebugOptions
import sk.kubis.endlessdrive.domain.model.ItemCatalog
import sk.kubis.endlessdrive.game.GameEngine
import sk.kubis.endlessdrive.game.car.Car
import sk.kubis.endlessdrive.game.car.WHEEL_HUB_FRAC
import sk.kubis.endlessdrive.game.world.TestTrackProfile
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Pruženie: koleso ostane v blatníku a skok nestrhne auto na jednu stranu.
 */
class SuspensionTest {



















    @Test
    fun oneWheelLandingDoesNotYankPitchToTheStop() {
        val car = tunedCar()
        car.speed = 20f
        var maxAbsPitch = 0f
        var sawAir = false
        var sawLand = false
        for (i in 0 until 120) {
            val frontY = if (i in 10..70) -3.2f else 0f
            val rearY = if (i in 22..70) -3.2f else 0f
            if (frontY < -0.5f || rearY < -0.5f) {
                if (!car.grounded) sawAir = true
            }
            car.applyDrive(1f / 60f, 0.35f, 0f, rearY, frontY, 0f)
            maxAbsPitch = max(maxAbsPitch, abs(car.pitch))
            if (sawAir && car.grounded && i > 70) sawLand = true
        }
        assertTrue("skok musí auto odlepiť", sawAir)
        assertTrue("musí znova pristáť", sawLand)
        assertTrue("náklon nesmie ísť na doraz ($maxAbsPitch)", maxAbsPitch < 0.85f)
        assertTrue("po dopade nos nesmie ostať strhnutý (${car.pitch})", abs(car.pitch) < 0.40f)
        assertTrue(abs(car.pitchRate) < 2.2f)
    }

    @Test
    fun axlesShareCompressionOnALevelLanding() {
        val car = tunedCar()
        car.y += 1.8f
        car.vy = -9f
        repeat(90) { car.applyDrive(1f / 60f, 0.2f, 0f, 0f, 0f, 0f) }
        assertTrue("po dopade musí držať cesta", car.grounded)
        val diff = abs(car.frontCompression - car.rearCompression)
        assertTrue(
            "nápravy sa po rovnom dopade nesmú rozísť (predok=${car.frontCompression} zadok=${car.rearCompression})",
            diff < 0.14f
        )
        assertTrue(abs(car.pitch) < 0.22f)
    }

    @Test
    fun fullCarOnTestTrackJumpStaysStable() {
        val engine = GameEngine(3L, 0f, DebugOptions.TUNE_SUSPENSION)
        assertTrue(engine.tryStartEngine())
        assertEquals(ItemCatalog.SUSPENSION_LIFT.id, engine.car.parts[ComponentSlot.SUSPENSION]?.defId)
        engine.throttleInput = 1f
        var airborne = false
        var maxAbsPitch = 0f
        var maxCompDiff = 0f
        var landingPitch = 0f
        var jumped = false
        var landedAfterJump = false
        repeat(60 * 28) {
            engine.advance(1f / 60f)
            val car = engine.car
            val x = car.x
            maxAbsPitch = max(maxAbsPitch, abs(car.pitch))
            if (car.grounded) {
                maxCompDiff = max(maxCompDiff, abs(car.frontCompression - car.rearCompression))
            }
            if (x in 165f..210f && !car.grounded) {
                airborne = true
                jumped = true
            }
            if (jumped && airborne && car.grounded && x > 190f && !landedAfterJump) {
                landingPitch = car.pitch
                landedAfterJump = true
            }
            if (x > 240f && landedAfterJump) return@repeat
        }
        assertTrue("plné auto musí dôjsť na skok (x=${engine.car.x})", engine.car.x > 170f)
        assertTrue("rampa na testovacej trati musí auto odlepiť (x=${engine.car.x})", airborne)
        assertTrue("skok nestrhne pitch na doraz ($maxAbsPitch)", maxAbsPitch < 0.80f)
        assertTrue("dopad nenechá nos na jednej strane ($landingPitch)", abs(landingPitch) < 0.55f)
        assertTrue("nápravy sa nesmú extrémne rozísť ($maxCompDiff)", maxCompDiff < 0.55f)
        assertTrue(engine.car.grounded || engine.car.x > 200f)
    }

    @Test
    fun tunePresetIsFullCarAndTestTrack() {
        val o = DebugOptions.TUNE_SUSPENSION
        assertTrue(o.fullUpgrades)
        assertTrue(o.fullFluids)
        assertTrue(o.testTrack)
        assertTrue(o.repairControls)
        val car = Car().apply { installStarterKit(SeededRandom(2L), o) }
        assertEquals(ItemCatalog.ENGINE_C.id, car.parts[ComponentSlot.ENGINE]?.defId)
        assertTrue(car.suspTravel > 0.3f)
        assertTrue(car.rideHeight > 0.3f)
    }

    @Test
    fun testTrackJumpSitsOnTheKnownLap() {
        val h = TestTrackProfile().heightAt(182f, sk.kubis.endlessdrive.domain.model.BranchStyle.SAFE_RURAL, 1f)
        assertTrue("skok na 182 m musí byť poriadny ($h)", h > 1.5f)
        assertEquals(0f, TestTrackProfile().heightAt(10f, sk.kubis.endlessdrive.domain.model.BranchStyle.SAFE_RURAL, 1f), 0.02f)
    }

    private fun tunedCar(): Car = Car().apply {
        installStarterKit(SeededRandom(7L), DebugOptions.TUNE_SUSPENSION)
        engineRunning = true
        snapToGround(0f)
    }
}
