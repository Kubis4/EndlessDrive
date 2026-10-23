package sk.kubis.endlessdrive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import sk.kubis.endlessdrive.core.SeededRandom
import sk.kubis.endlessdrive.domain.model.BranchStyle
import sk.kubis.endlessdrive.domain.model.ComponentCondition
import sk.kubis.endlessdrive.domain.model.ComponentSlot
import sk.kubis.endlessdrive.domain.model.DebugOptions
import sk.kubis.endlessdrive.domain.model.ItemCatalog
import sk.kubis.endlessdrive.domain.model.ItemStack
import sk.kubis.endlessdrive.game.GameEngine
import sk.kubis.endlessdrive.game.car.Car
import sk.kubis.endlessdrive.game.world.WorldGenerator

class VehicleMassAndTimingTest {

    @Test
    fun lowFpsFramesDoNotLoseSimulationTime() {
        val engine = GameEngine(8_001L, 0f)
        repeat(50) { engine.advance(0.1f) } // 10 FPS počas piatich reálnych sekúnd

        assertEquals(
            "nízke FPS nesmie spomaliť herný čas ani kilometre",
            5f,
            engine.elapsed,
            0.025f
        )
    }

    @Test
    fun oneThirtyCoversTheSameKilometresAtTenAndSixtyFps() {
        fun prepared(): GameEngine = GameEngine(
            8_002L,
            0f,
            DebugOptions(fullUpgrades = true, fullFluids = true)
        ).also {
            assertTrue(it.tryStartEngine())
            it.car.speed = 130f / 3.6f
            it.throttleInput = 0.82f
        }

        val sixtyFps = prepared()
        val tenFps = prepared()
        repeat(300) { sixtyFps.advance(1f / 60f) }
        repeat(50) { tenFps.advance(0.1f) }

        assertEquals(5f, tenFps.elapsed, 0.025f)
        assertEquals(
            "rovnakých päť sekúnd jazdy musí dať rovnakú vzdialenosť bez ohľadu na FPS",
            sixtyFps.distanceM,
            tenFps.distanceM,
            1.0f
        )
    }

    @Test
    fun frontEngineAndBootCargoMoveTheActualCenterOfMass() {
        val car = Car()
        car.installStarterKit(SeededRandom(91L))
        car.fuel = 30f
        car.oil = 3f
        car.coolant = 5f

        val withFrontEngine = car.rearWeightBias
        val engine = car.parts.remove(ComponentSlot.ENGINE)!!
        val withoutEngine = car.rearWeightBias
        car.parts[ComponentSlot.ENGINE] = engine

        assertTrue(
            "motor vpredu musí posunúť váhu na prednú nápravu",
            withFrontEngine < withoutEngine - 0.025f
        )
        assertTrue("predomotorové auto nemá byť ťažšie vzadu", withFrontEngine < 0.48f)

        val emptyMass = car.totalMassKg
        car.setCargoLoad(packKg = 0f, bootKg = 150f)
        val loadedRear = car.rearWeightBias
        assertEquals(emptyMass + 150f, car.totalMassKg, 0.01f)
        assertTrue(
            "ťažký kufor musí citeľne posunúť ťažisko dozadu",
            loadedRear > withFrontEngine + 0.045f
        )
    }

    @Test
    fun cargoMassChangesSuspensionAndAcceleration() {
        fun makeCar(): Car = Car().apply {
            installStarterKit(SeededRandom(17L))
            mount(
                ComponentSlot.DRIVETRAIN,
                ItemStack(ItemCatalog.DRIVE_AWD.id, ComponentCondition.NEW, 1f)
            )
            fuel = 30f
            oil = 3f
            coolant = 5f
            engineRunning = true
        }

        val empty = makeCar()
        val loaded = makeCar().apply { setCargoLoad(packKg = 0f, bootKg = 150f) }
        empty.snapToGround(0f)
        loaded.snapToGround(0f)

        assertTrue(loaded.rearCompression > empty.rearCompression)

        repeat(240) {
            empty.applyDrive(1f / 60f, 1f, 0f, 0f, 0f, 0f)
            loaded.applyDrive(1f / 60f, 1f, 0f, 0f, 0f, 0f)
        }
        assertTrue(
            "150 kg nákladu musí znížiť zrýchlenie (${empty.speed} vs ${loaded.speed})",
            loaded.speed < empty.speed - 0.35f
        )
    }

    @Test
    fun cargoMassAlsoRaisesFuelUseWearAndBrakingDistance() {
        fun makeCar(): Car = Car().apply {
            installStarterKit(
                SeededRandom(221L),
                DebugOptions(fullUpgrades = true, fullBody = true, fullFluids = true)
            )
            engineRunning = true
            speed = 20f
            snapToGround(0f)
        }

        val empty = makeCar()
        val loaded = makeCar().apply {
            setCargoLoad(packKg = 55f, bootKg = 190f)
            snapToGround(0f)
        }
        val emptyFuel = empty.fuel
        val loadedFuel = loaded.fuel
        empty.tickDriving(12f, 0.85f, 1f)
        loaded.tickDriving(12f, 0.85f, 1f)
        assertTrue(
            "naložené auto musí pri rovnakom plyne spáliť viac",
            loadedFuel - loaded.fuel > emptyFuel - empty.fuel
        )

        val emptySuspension = empty.parts.getValue(ComponentSlot.SUSPENSION).health
        val loadedSuspension = loaded.parts.getValue(ComponentSlot.SUSPENSION).health
        empty.tickWear(20f, bumpMul = 0.8f, brake = 0.6f)
        loaded.tickWear(20f, bumpMul = 0.8f, brake = 0.6f)
        assertTrue(
            "váha musí viac opotrebovať podvozok",
            loadedSuspension - loaded.parts.getValue(ComponentSlot.SUSPENSION).health >
                emptySuspension - empty.parts.getValue(ComponentSlot.SUSPENSION).health
        )

        repeat(24) {
            empty.applyDrive(1f / 60f, 0f, 1f, 0f, 0f, 0f)
            loaded.applyDrive(1f / 60f, 0f, 1f, 0f, 0f, 0f)
        }
        assertTrue(
            "rovnaké brzdy musia naloženému autu nechať dlhšiu brzdnú dráhu",
            loaded.speed > empty.speed + 0.25f
        )
    }



    @Test
    fun garageBrakePackageShortensLoadedCarStoppingDistance() {
        fun loadedCar(brakeLevel: Int): Car = Car().apply {
            installStarterKit(
                SeededRandom(552L),
                DebugOptions(fullUpgrades = true, fullBody = true, fullFluids = true)
            )
            setGaragePerformance(engineTune = 0, loadSuspension = 0, brakePackage = brakeLevel)
            setCargoLoad(packKg = 55f, bootKg = 190f)
            speed = 20f
            snapToGround(0f)
        }

        val standard = loadedCar(0)
        val upgraded = loadedCar(3)
        repeat(18) {
            standard.applyDrive(1f / 60f, 0f, 1f, 0f, 0f, 0f)
            upgraded.applyDrive(1f / 60f, 0f, 1f, 0f, 0f, 0f)
        }
        assertTrue(
            "garážové brzdy sa musia prejaviť aj vo fyzike, nie iba v profile (std=${standard.speed}, up=${upgraded.speed})",
            upgraded.speed < standard.speed - 0.08f
        )
    }

    @Test
    fun newRunsUseSeveralSafeStartingLandscapes() {
        val starts = (1L..120L).map { WorldGenerator.startingStyle(it * 7_919L) }.toSet()
        assertTrue("štartovacia krajina sa musí meniť: $starts", starts.size >= 3)
        assertTrue(
            starts.all {
                it == BranchStyle.SAFE_RURAL ||
                    it == BranchStyle.FOREST_ALIVE ||
                    it == BranchStyle.FOREST
            }
        )
    }
}
