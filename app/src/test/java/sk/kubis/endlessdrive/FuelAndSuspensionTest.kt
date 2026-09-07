package sk.kubis.endlessdrive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import sk.kubis.endlessdrive.core.GameConfig
import sk.kubis.endlessdrive.domain.model.ComponentCondition
import sk.kubis.endlessdrive.domain.model.ComponentSlot
import sk.kubis.endlessdrive.domain.model.FluidType
import sk.kubis.endlessdrive.domain.model.FuelKind
import sk.kubis.endlessdrive.domain.model.ItemCatalog
import sk.kubis.endlessdrive.domain.model.ItemStack
import sk.kubis.endlessdrive.domain.model.SedanSpec
import sk.kubis.endlessdrive.domain.model.DebugOptions
import sk.kubis.endlessdrive.game.GameEngine
import sk.kubis.endlessdrive.game.car.Car
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sin

class FuelAndSuspensionTest {

    private fun completeCar(): Car = Car().apply {
        installStarterKit()
        mount(ComponentSlot.BATTERY, ItemStack(ItemCatalog.BATTERY.id, ComponentCondition.NEW, 1f))
        mount(ComponentSlot.STARTER, ItemStack(ItemCatalog.STARTER.id, ComponentCondition.NEW, 1f))
        fuel = 20f
        oil = 3f
        coolant = 5f
        batteryCharge = 1f
        fuelPurity = 1f
    }

    @Test
    fun petrolAndDieselBlendByLitres() {
        val car = completeCar()
        car.fuel = 10f
        car.fuelDieselFraction = 0f

        assertEquals(10f, car.refill(FluidType.FUEL, 10f, 1f, FuelKind.DIESEL), 0.001f)
        assertEquals(0.5f, car.fuelDieselFraction, 0.001f)
        assertEquals(0.5f, car.wrongFuelFraction, 0.001f)
    }

    @Test
    fun dieselEngineRejectsPetrolAndRunsAfterTankIsCorrected() {
        val car = completeCar()
        car.mount(ComponentSlot.ENGINE, ItemStack(ItemCatalog.ENGINE_D.id, ComponentCondition.NEW, 1f))
        car.fuelDieselFraction = 0f

        assertEquals(FuelKind.DIESEL, car.requiredFuelKind)
        assertFalse("diesel nesmie naštartovať s prevažne benzínovou nádržou", car.canStart())

        car.drain(FluidType.FUEL)
        car.refill(FluidType.FUEL, 15f, 1f, FuelKind.DIESEL)
        assertTrue(car.canStart())
    }

    @Test
    fun wrongFuelRapidlyWearsAForcedRunningEngine() {
        val car = completeCar()
        car.mount(ComponentSlot.ENGINE, ItemStack(ItemCatalog.ENGINE_D.id, ComponentCondition.NEW, 1f))
        car.fuelDieselFraction = 0f
        car.engineRunning = true
        val before = car.parts[ComponentSlot.ENGINE]!!.health

        car.tickDriving(5f, 1f, 1f)

        assertTrue(
            "nesprávne palivo musí mať citeľný následok",
            before - car.parts[ComponentSlot.ENGINE]!!.health > 0.04f
        )
    }

    @Test
    fun drainControlsClearMixesButRequireTheEngineOff() {
        val engine = GameEngine(72L, 0f)
        engine.car.fuel = 18f
        engine.car.fuelPurity = 0.62f
        engine.car.fuelDieselFraction = 0.70f

        assertTrue(engine.drainFluid(FluidType.FUEL))
        assertEquals(0f, engine.car.fuel, 0.001f)
        assertEquals(1f, engine.car.fuelPurity, 0.001f)
        assertEquals(0f, engine.car.fuelDieselFraction, 0.001f)

        engine.car.engineRunning = true
        val oilBefore = engine.car.oil
        assertFalse("bežiaci motor sa nesmie vypustiť", engine.drainFluid(FluidType.OIL))
        assertEquals(oilBefore, engine.car.oil, 0.001f)

        engine.car.engineRunning = false
        assertTrue(engine.drainFluid(FluidType.OIL))
        assertEquals(0f, engine.car.oil, 0.001f)
        assertEquals(1f, engine.car.oilPurity, 0.001f)
    }

    @Test
    fun drainControlsCanRemoveAnExactPartBeforeDrainingTheRest() {
        val engine = GameEngine(73L, 0f)
        engine.car.fuel = 17.5f
        engine.car.fuelPurity = 0.64f
        engine.car.fuelDieselFraction = 0.35f

        assertTrue(engine.drainFluid(FluidType.FUEL, 5f))
        assertEquals(12.5f, engine.car.fuel, 0.001f)
        // Pri čiastočnom vypustení ostáva zloženie zvyšku rovnaké.
        assertEquals(0.64f, engine.car.fuelPurity, 0.001f)
        assertEquals(0.35f, engine.car.fuelDieselFraction, 0.001f)

        assertTrue(engine.drainFluid(FluidType.FUEL))
        assertEquals(0f, engine.car.fuel, 0.001f)
        assertEquals(1f, engine.car.fuelPurity, 0.001f)
        assertEquals(0f, engine.car.fuelDieselFraction, 0.001f)
    }

    @Test
    fun headlightsCycleThroughLowHighAndOffAndHighBeamsUseMoreBattery() {
        val engine = GameEngine(74L, 0f)
        engine.car.mount(
            ComponentSlot.HEADLIGHT,
            ItemStack(ItemCatalog.HEADLIGHT.id, ComponentCondition.NEW, 1f)
        )

        assertTrue(engine.toggleHeadlights())
        assertTrue(engine.headlightsOn)
        assertFalse(engine.highBeamsOn)
        assertTrue(engine.toggleHeadlights())
        assertTrue(engine.headlightsOn)
        assertTrue(engine.highBeamsOn)
        assertFalse(engine.toggleHeadlights())
        assertFalse(engine.headlightsOn)
        assertFalse(engine.highBeamsOn)

        val lowBeamCar = Car().apply { batteryCharge = 1f }
        val highBeamCar = Car().apply { batteryCharge = 1f }
        repeat(60) {
            lowBeamCar.tickElectrics(1f / 60f, headlightsOn = true)
            highBeamCar.tickElectrics(
                1f / 60f,
                headlightsOn = true,
                headlightDrainMultiplier = GameConfig.HIGH_BEAM_DRAIN_MULTIPLIER
            )
        }
        assertTrue(highBeamCar.batteryCharge < lowBeamCar.batteryCharge)
    }

    @Test
    fun eachAxleTracksItsOwnSlopeWithoutCrestLaunchSpike() {
        val car = completeCar()
        car.engineRunning = false
        car.x = -18f
        car.speed = 24f

        fun height(x: Float): Float = (5.2 * exp(-((x * x) / 95.0))).toFloat()
        fun slope(x: Float): Float = height(x) * (-2f * x / 95f)

        car.snapToGround(height(car.x), slope(car.x))
        var maxUpwardSpeed = 0f
        var maxPitchRate = 0f
        val wb = SedanSpec.wheelOffsetX
        repeat(180) {
            val rearX = car.x - wb
            val frontX = car.x + wb
            car.applyDrive(
                dt = 1f / 60f,
                throttle = 0f,
                brake = 0f,
                rearGroundY = height(rearX),
                frontGroundY = height(frontX),
                bumpMul = 0f,
                rearGroundSlope = slope(rearX),
                frontGroundSlope = slope(frontX)
            )
            maxUpwardSpeed = max(maxUpwardSpeed, car.vy)
            maxPitchRate = max(maxPitchRate, kotlin.math.abs(car.pitchRate))
            val forceLimit = car.physicsMass * GameConfig.AIR_GRAVITY *
                GameConfig.SUSP_AXLE_FORCE_LIMIT + 0.01f
            assertTrue(car.rearNormalForce <= forceLimit)
            assertTrue(car.frontNormalForce <= forceLimit)
        }

        assertTrue("hrebeň vystrelil auto nahor: $maxUpwardSpeed m/s", maxUpwardSpeed < 12f)
        assertTrue("hrebeň roztočil karosériu: $maxPitchRate rad/s", maxPitchRate < 8f)
    }

    @Test
    fun highSpeedTestTrackNeverLetsTheBodyTunnelBelowTheRoad() {
        val engine = GameEngine(
            44L,
            0f,
            DebugOptions(fullUpgrades = true, fullFluids = true, testTrack = true)
        )
        assertTrue(engine.tryStartEngine())
        engine.throttleInput = 1f
        var worstClearance = Float.POSITIVE_INFINITY
        repeat(1500) {
            engine.advance(1f / 60f)
            val car = engine.car
            val wb = SedanSpec.wheelOffsetX
            val rearBody = car.y - wb * sin(car.pitch)
            val frontBody = car.y + wb * sin(car.pitch)
            val rearFloor = engine.segment.heightAtWorld(car.x - wb) + car.rideHeight - car.suspTravel
            val frontFloor = engine.segment.heightAtWorld(car.x + wb) + car.rideHeight - car.suspTravel
            worstClearance = minOf(worstClearance, rearBody - rearFloor, frontBody - frontFloor)
        }
        assertTrue("karoséria prenikla pod cestu o ${-worstClearance} m", worstClearance > 0.01f)
    }

    @Test
    fun narrowCrestCannotPassThroughTheChassisOrLaunchIt() {
        val car = completeCar()
        car.snapToGround(0f)
        car.speed = 40f

        car.resolveRoadPenetration(
            rearGroundY = 0f,
            frontGroundY = 0f,
            rearGroundSlope = 0.8f,
            frontGroundSlope = 0.8f,
            bodyGroundAtOffset = { offset -> if (kotlin.math.abs(offset) < 0.1f) 0.65f else 0f }
        )

        assertTrue(car.y >= 0.65f + GameConfig.ROAD_CHASSIS_CLEARANCE - 0.001f)
        assertTrue(
            "tvrdý kontakt vystrelil auto rýchlosťou ${car.vy} m/s",
            car.vy <= GameConfig.ROAD_CONTACT_MAX_UP_SPEED + 0.001f
        )
    }

    @Test
    fun finalAxlePositionCannotTunnelOnGeneratedRoads() {
        for (seed in 1L..6L) {
            val engine = GameEngine(
                seed,
                0f,
                DebugOptions(fullUpgrades = true, fullFluids = true)
            )
            assertTrue(engine.tryStartEngine())
            engine.throttleInput = 1f
            repeat(1800) {
                // Simuluje aj pomalšiu obrazovku; engine si rám rozdelí na
                // pevné fyzikálne kroky a po každom musí vyriešiť finálne X.
                engine.advance(1f / 30f)
                val car = engine.car
                val wb = SedanSpec.wheelOffsetX
                val rearBody = car.y - wb * sin(car.pitch)
                val frontBody = car.y + wb * sin(car.pitch)
                val floorOffset = car.rideHeight - car.suspTravel + 0.01f
                assertTrue(
                    "seed=$seed zadná náprava prenikla pri ${car.speedKmh} km/h",
                    rearBody >= engine.segment.heightAtWorld(car.x - wb) + floorOffset - 0.002f
                )
                assertTrue(
                    "seed=$seed predná náprava prenikla pri ${car.speedKmh} km/h",
                    frontBody >= engine.segment.heightAtWorld(car.x + wb) + floorOffset - 0.002f
                )
                for (offset in floatArrayOf(-wb * 0.5f, 0f, wb * 0.5f)) {
                    val bodyPoint = car.y + offset * sin(car.pitch)
                    assertTrue(
                        "seed=$seed podlaha prenikla do cesty pri ${car.speedKmh} km/h",
                        bodyPoint >= engine.segment.heightAtWorld(car.x + offset) +
                            GameConfig.ROAD_CHASSIS_CLEARANCE - 0.003f
                    )
                }
            }
        }
    }

    @Test
    fun oneFrameContactLossDoesNotSwitchTheCarVisualUnderTheRoad() {
        val car = completeCar()
        car.snapToGround(0f)

        car.applyDrive(1f / 60f, 0f, 0f, -50f, -50f, 0f)

        assertFalse(car.grounded)
        assertTrue("jediný vynechaný kontakt nesmie prepnúť polohu sprite", car.visuallyGrounded)
        repeat(6) { car.applyDrive(1f / 60f, 0f, 0f, -50f, -50f, 0f) }
        assertFalse("skutočný dlhší skok sa musí normálne zobraziť", car.visuallyGrounded)
    }

    @Test
    fun legendaryEngineCanRunWellPastEightyKmh() {
        val car = completeCar()
        car.mount(ComponentSlot.ENGINE, ItemStack(ItemCatalog.ENGINE_C.id, ComponentCondition.NEW, 1f))
        car.mount(ComponentSlot.DRIVETRAIN, ItemStack(ItemCatalog.DRIVE_AWD.id, ComponentCondition.NEW, 1f))
        car.mount(ComponentSlot.TIRE_FRONT, ItemStack(ItemCatalog.TIRE_SPORT.id, ComponentCondition.NEW, 1f))
        car.mount(ComponentSlot.TIRE_REAR, ItemStack(ItemCatalog.TIRE_SPORT.id, ComponentCondition.NEW, 1f))
        car.fuelDieselFraction = 1f
        car.engineRunning = true
        car.snapToGround(0f)
        repeat(1800) { car.applyDrive(1f / 60f, 1f, 0f, 0f, 0f, 0f) }
        assertTrue("230 hp motor ide iba ${car.speedKmh} km/h", car.speedKmh > 105f)
    }
}
