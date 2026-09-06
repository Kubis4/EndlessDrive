package sk.kubis.endlessdrive

import org.junit.Assert.*
import org.junit.Test
import sk.kubis.endlessdrive.core.GameConfig
import sk.kubis.endlessdrive.core.SeededRandom
import sk.kubis.endlessdrive.domain.model.*
import sk.kubis.endlessdrive.game.car.Car

class ParkingTractionTest {
    private fun car(drive: ItemDef = ItemCatalog.DRIVE_RWD): Car = Car().apply {
        installStarterKit(SeededRandom(42L), DebugOptions(allComponents = true, fullFluids = true))
        mount(ComponentSlot.DRIVETRAIN, ItemStack(drive.id, ComponentCondition.NEW, 1f))
        TIRE_SLOTS.forEach {
            mount(it, ItemStack(ItemCatalog.TIRE_POOR.id, ComponentCondition.USED, 0.35f))
        }
        engineRunning = true
        snapToGround(0f)
    }

    @Test fun poorTyresCanReverseToAMissedHouseWithoutBurnout() {
        for (drive in listOf(ItemCatalog.DRIVE_RWD, ItemCatalog.DRIVE_FWD, ItemCatalog.DRIVE_AWD)) {
            val car = car(drive)
            repeat(600) { car.applyDrive(1f / 60f, 0f, 1f, 0f, 0f, 0f) }
            println("${drive.name}: reverse=${car.speed * 3.6f} km/h distance=${car.x} slip=${car.wheelSlip}")
            assertTrue("${drive.name} must cover 20 m in ten seconds", car.x < -20f)
            assertTrue("${drive.name} must grip on flat asphalt", car.wheelSlip < 0.1f)
            assertTrue(car.speed >= -GameConfig.REVERSE_MAX_SPEED)
        }
    }

    @Test fun genuineReverseSlipSpinsOnlyDrivenWheelsBackwards() {
        for (drive in listOf(ItemCatalog.DRIVE_RWD, ItemCatalog.DRIVE_FWD)) {
            val car = car(drive)
            repeat(180) { car.applyDrive(1f / 60f, 0f, 1f, 0f, 0f, 0f, RoadSurface.ICE) }
            assertTrue("Ice should still challenge poor summer tyres", car.wheelSlip > 0.1f)
            assertTrue("Driven wheels must spin faster backwards", car.wheelSpeed < car.speed - 0.5f)
            assertTrue("Axles must have different rotation", kotlin.math.abs(car.wheelSpinRearDeg - car.wheelSpinFrontDeg) > 1f)
        }
    }

    @Test fun reverseNeedsFuelAndARunningEngine() {
        for (emptyTank in listOf(false, true)) {
            val car = car()
            if (emptyTank) car.fuel = 0f else car.engineRunning = false
            repeat(180) { car.applyDrive(1f / 60f, 0f, 1f, 0f, 0f, 0f) }
            assertEquals(0f, car.x, 0.1f)
        }
    }

    @Test fun reverseClimbsAModestDownhillCrest() {
        val slope = -0.16f
        for (drive in listOf(ItemCatalog.DRIVE_RWD, ItemCatalog.DRIVE_FWD, ItemCatalog.DRIVE_AWD)) {
            val car = Car().apply {
                installStarterKit(SeededRandom(7L), DebugOptions(allComponents = true, fullFluids = true))
                mount(ComponentSlot.DRIVETRAIN, ItemStack(drive.id, ComponentCondition.NEW, 1f))
                TIRE_SLOTS.forEach {
                    mount(it, ItemStack(ItemCatalog.TIRE.id, ComponentCondition.USED, 0.9f))
                }
                engineRunning = true
                snapToGround(0f, slope)
            }
            repeat(240) { car.applyDriveSlope(1f / 60f, 0f, 1f, 0f, slope, 0f) }
            assertTrue(
                "${drive.name} must reverse up a 16% crest (x=${car.x}, v=${car.speed})",
                car.x < -5f && car.speed < -0.8f
            )
        }
    }

    @Test fun reverseStaysSlowerThanForwardOnFlat() {
        fun sprint(throttle: Float, brake: Float): Float {
            val car = Car().apply {
                installStarterKit(SeededRandom(3L), DebugOptions(allComponents = true, fullFluids = true))
                mount(
                    ComponentSlot.DRIVETRAIN,
                    ItemStack(ItemCatalog.DRIVE_RWD.id, ComponentCondition.NEW, 1f)
                )
                engineRunning = true
                snapToGround(0f)
            }
            repeat(180) { car.applyDrive(1f / 60f, throttle, brake, 0f, 0f, 0f) }
            return kotlin.math.abs(car.speed)
        }
        val forward = sprint(1f, 0f)
        val reverse = sprint(0f, 1f)
        assertTrue("cúvanie $reverse musí byť pomalšie ako vpred $forward", reverse < forward * 0.75f)
        assertTrue(reverse <= GameConfig.REVERSE_MAX_SPEED + 0.05f)
        assertTrue(GameConfig.REVERSE_ACCEL < GameConfig.ACCEL)
    }

    @Test fun emptyRearSitsLowerAndCargoStillCompressesIt() {
        val car = car()
        val oldSpring = GameConfig.SUSP_SPRING * car.suspensionMul.coerceIn(0.35f, 1.4f)
        val oldRearSag = car.physicsMass * GameConfig.AIR_GRAVITY * car.rearWeightBias / oldSpring
        val oldFrontSag = car.physicsMass * GameConfig.AIR_GRAVITY * (1f - car.rearWeightBias) / oldSpring
        fun rearHeight() = car.y - SedanSpec.wheelOffsetX * kotlin.math.sin(car.pitch)
        assertTrue(rearHeight() < car.rideHeight - oldRearSag)
        val heightDifference = 2f * SedanSpec.wheelOffsetX * kotlin.math.abs(kotlin.math.sin(car.pitch))
        assertTrue("Reduce empty-car rake", heightDifference < kotlin.math.abs(oldFrontSag - oldRearSag))
        val emptyRear = rearHeight()
        val emptyPitch = car.pitch
        car.setCargoLoad(0f, 160f)
        car.snapToGround(0f)
        assertTrue(rearHeight() < emptyRear)
        assertTrue(car.pitch > emptyPitch)
    }
}
