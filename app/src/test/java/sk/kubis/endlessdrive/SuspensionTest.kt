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
import sk.kubis.endlessdrive.ui.game.SuspensionVisual
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Pruženie: koleso ostane v blatníku a skok nestrhne auto na jednu stranu.
 */
class SuspensionTest {

    @Test
    fun physicsStrokeIsLargerThanTheWheelWell() {
        val restR = 31f
        val travel = SuspensionVisual.travelPx(0.52f, GameConfig.CAMERA_BASE_PPM)
        assertTrue("zdvih liftu musí byť väčší než oblúk, inak niet čo klampovať ($travel)", travel > restR * 0.7f)
        val bounds = SuspensionVisual.wellDyBounds(restR, restR, travel)
        assertTrue(
            "hore nesmie koleso vliezť do karosérie (jounce=${-bounds.start})",
            -bounds.start <= restR * SuspensionVisual.WELL_JOUNCE_FRAC + 0.05f
        )
        assertTrue(
            "dole nesmie spadnúť cez podlahu sprite (droop=${bounds.endInclusive})",
            bounds.endInclusive <= restR * SuspensionVisual.WELL_DROOP_FRAC + 0.05f
        )
        assertTrue(-bounds.start < travel * 0.55f)
    }

    @Test
    fun frontAndRearShareTheSameWellLimits() {
        val restR = 30f
        val travel = SuspensionVisual.travelPx(0.38f, 66f)
        val front = SuspensionVisual.wellDyBounds(restR, restR, travel)
        val rear = SuspensionVisual.wellDyBounds(restR, restR, travel)
        assertEquals(front.start, rear.start, 0.001f)
        assertEquals(front.endInclusive, rear.endInclusive, 0.001f)
    }

    @Test
    fun wheelCannotRiseThroughTheArchOnABump() {
        val wellY = 200f
        val restR = 31f
        val travel = SuspensionVisual.travelPx(0.52f, 66f)
        val bounds = SuspensionVisual.wellDyBounds(restR, restR, travel)
        // Vozovka vysoko nad blatníkom – koleso chce ísť cez karosériu.
        val y = SuspensionVisual.wheelCenterY(wellY, wellY - 80f, restR, restR, travel, true)
        assertEquals(wellY + bounds.start, y, 0.05f)
        assertTrue("stred kolesa ostane v oblúku, nie v blatníku", y >= wellY - restR * 0.35f)
    }

    @Test
    fun puncturedWheelSitsOnSlopeWithoutTravellingAcrossTheWheelArch() {
        val wellX = 100f
        val wellY = 180f
        val groundY = 210f
        val r = 30f
        val travel = SuspensionVisual.travelPx(0.45f, 66f)
        val slope = (kotlin.math.PI / 5.0).toFloat()
        val seated = SuspensionVisual.wheelOnRoad(
            wellX, wellY, groundY, r, r, travel, true, slope, alongSlope = true
        )
        val vertical = SuspensionVisual.wheelOnRoad(
            wellX, wellY, groundY, r, r, travel, true, 0f, alongSlope = true
        )
        assertEquals(wellX, vertical.first, 0.05f)
        assertEquals(groundY - r, vertical.second, 0.05f)
        assertEquals("defekt ostane na osi blatníka", wellX, seated.first, 0.05f)
        assertTrue(
            "na svahu je Y bližšie k vozovke než zvislý drop (${seated.second} vs ${vertical.second})",
            seated.second > vertical.second
        )
    }

    @Test
    fun puncturedWheelXStaysFixedWhenSlopeChanges() {
        val wellX = 100f
        var minX = Float.POSITIVE_INFINITY
        var maxX = Float.NEGATIVE_INFINITY
        for (deg in -35..35) {
            val seated = SuspensionVisual.wheelOnRoad(
                wellX = wellX,
                wellY = 180f,
                groundY = 210f,
                radiusPx = 30f,
                restRadiusPx = 30f,
                travelPx = 18f,
                visualContact = true,
                slopeRad = Math.toRadians(deg.toDouble()).toFloat(),
                alongSlope = true
            )
            minX = min(minX, seated.first)
            maxX = max(maxX, seated.first)
        }
        assertEquals("defekt nesmie cestovať do strán", 0f, maxX - minX, 0.01f)
    }

    @Test
    fun inflatedWheelStaysUnderTheArchWhenSlopeJitters() {
        val wellX = 100f
        val wellY = 180f
        val groundY = 210f
        val r = 30f
        val travel = SuspensionVisual.travelPx(0.45f, 66f)
        var minX = Float.POSITIVE_INFINITY
        var maxX = Float.NEGATIVE_INFINITY
        for (deg in -28..28) {
            val slope = Math.toRadians(deg.toDouble()).toFloat()
            val seated = SuspensionVisual.wheelOnRoad(
                wellX, wellY, groundY, r, r, travel, true, slope
            )
            assertEquals("koleso ostane pod blatníkom pri $deg°", wellX, seated.first, 0.05f)
            minX = min(minX, seated.first)
            maxX = max(maxX, seated.first)
            assertEquals(groundY - r, seated.second, 0.05f)
        }
        assertEquals("vodorovný lov pri zmene sklonu", 0f, maxX - minX, 0.05f)
    }

    @Test
    fun airborneDroopStaysInsideTheWell() {
        val wellY = 200f
        val restR = 31f
        val travel = SuspensionVisual.travelPx(0.52f, 66f)
        val bounds = SuspensionVisual.wellDyBounds(restR, restR, travel)
        val y = SuspensionVisual.wheelCenterY(wellY, 0f, restR, restR, travel, false)
        assertTrue("vo vzduchu kolesá visia dole", y >= wellY)
        assertTrue(y <= wellY + bounds.endInclusive + 0.05f)
        val unconstrained = wellY + travel * SuspensionVisual.AIR_DROOP
        if (unconstrained > wellY + bounds.endInclusive) {
            assertEquals(wellY + bounds.endInclusive, y, 0.05f)
        }
    }

    @Test
    fun shreddedHubDropsFurtherButDoesNotRiseThroughTheArch() {
        val restR = 31f
        val hubR = restR * WHEEL_HUB_FRAC
        val travel = SuspensionVisual.travelPx(0.45f, 66f)
        val inflated = SuspensionVisual.wellDyBounds(restR, restR, travel)
        val shredded = SuspensionVisual.wellDyBounds(restR, hubR, travel)
        assertEquals("oblúk je ten istý pre disk aj gumu", inflated.start, shredded.start, 0.05f)
        assertTrue("ráfik smie klesnúť nižšie", shredded.endInclusive > inflated.endInclusive + 4f)
        val wellY = 180f
        val up = SuspensionVisual.wheelCenterY(wellY, wellY - 90f, hubR, restR, travel, true)
        assertTrue(up >= wellY + shredded.start - 0.05f)
        assertTrue("disk neprelezie hore cez blatník", up >= wellY - restR * 0.35f)
    }

    @Test
    fun loweredSuspensionTravelsLessThanLifted() {
        val ppm = GameConfig.CAMERA_BASE_PPM
        val low = SuspensionVisual.travelPx(ItemCatalog.SUSPENSION_LOW.suspTravel, ppm)
        val lift = SuspensionVisual.travelPx(ItemCatalog.SUSPENSION_LIFT.suspTravel, ppm)
        assertTrue("znížený podvozok má menší vizuálny zdvih ($low vs $lift)", low < lift)
        val restR = 31f
        val lowWell = SuspensionVisual.wellDyBounds(restR, restR, low)
        val liftWell = SuspensionVisual.wellDyBounds(restR, restR, lift)
        assertTrue(-lowWell.start <= -liftWell.start + 0.05f)
    }

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
