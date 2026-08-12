package sk.kubis.endlessdrive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import sk.kubis.endlessdrive.core.GameConfig
import sk.kubis.endlessdrive.core.SeededRandom
import sk.kubis.endlessdrive.domain.model.BranchStyle
import sk.kubis.endlessdrive.domain.model.ComponentCondition
import sk.kubis.endlessdrive.domain.model.ComponentSlot
import sk.kubis.endlessdrive.domain.model.DriveLayout
import sk.kubis.endlessdrive.domain.model.EndReason
import sk.kubis.endlessdrive.domain.model.FluidType
import sk.kubis.endlessdrive.domain.model.GamePhase
import sk.kubis.endlessdrive.domain.model.ItemCatalog
import sk.kubis.endlessdrive.domain.model.ItemStack
import sk.kubis.endlessdrive.domain.model.RoadFeature
import sk.kubis.endlessdrive.domain.model.RoadPaving
import sk.kubis.endlessdrive.domain.model.RoadSurface
import sk.kubis.endlessdrive.domain.model.SedanSpec
import sk.kubis.endlessdrive.domain.model.TIRE_SLOTS
import sk.kubis.endlessdrive.game.DayCycle
import sk.kubis.endlessdrive.game.GameEngine
import sk.kubis.endlessdrive.game.car.Car
import sk.kubis.endlessdrive.game.world.TerrainProfile
import sk.kubis.endlessdrive.domain.model.BuildingType
import sk.kubis.endlessdrive.game.save.RunCodec
import sk.kubis.endlessdrive.game.world.LootGenerator
import sk.kubis.endlessdrive.game.world.WorldGenerator

class GameCoreTest {

    @Test
    fun segmentIsDeterministic() {
        val terrain = TerrainProfile(42L)
        val a = WorldGenerator.createSegment(42L, BranchStyle.SAFE_RURAL, 0f, 0f, terrain, false)
        val b = WorldGenerator.createSegment(42L, BranchStyle.SAFE_RURAL, 0f, 0f, terrain, false)
        assertEquals(a.length, b.length, 0.001f)
        assertEquals(a.buildings.size, b.buildings.size)
        assertEquals(a.choices.size, b.choices.size)
        assertEquals(a.heightAtLocal(20f), b.heightAtLocal(20f), 0.001f)
    }

    @Test
    fun prepRequiresFluidsThenStart() {
        val engine = GameEngine(seed = 1L, bestDistanceKm = 0f)
        assertEquals(GamePhase.PREP, engine.phase)
        assertFalse(engine.car.engineRunning)

        engine.prepareForDriving()
        assertTrue(engine.tryStartEngine())
        assertTrue(engine.car.engineRunning)
    }

    @Test
    fun mountingEngineReplacesPrevious() {
        val engine = GameEngine(7L, 0f)
        engine.inventory.clear()
        val before = engine.car.parts[ComponentSlot.ENGINE]!!.defId
        assertTrue(engine.inventory.add(ItemStack(ItemCatalog.ENGINE_B.id)))
        val idx = engine.inventory.slots.indexOfFirst { it?.defId == ItemCatalog.ENGINE_B.id }
        assertTrue(idx >= 0)
        assertTrue(engine.useInventoryItem(idx))
        assertEquals(ItemCatalog.ENGINE_B.id, engine.car.parts[ComponentSlot.ENGINE]!!.defId)
        assertTrue(engine.inventory.slots.any { it?.defId == before })
    }

    @Test
    fun drivingConsumesFuelAndMoves() {
        val engine = GameEngine(9L, 0f)
        engine.prepareForDriving()
        assertTrue(engine.tryStartEngine())
        repeat(10) { engine.advance(1f / 60f) }
        assertEquals(GamePhase.DRIVING, engine.phase)
        engine.throttleInput = 1f
        val fuelBefore = engine.car.fuel
        repeat(180) { engine.advance(1f / 60f) }
        assertTrue(engine.car.fuel < fuelBefore)
        assertTrue(engine.car.x > 5f)
    }

    @Test
    fun dayCycleWrapsAndReportsNight() {
        assertEquals(0.25f, DayCycle.advance(0.25f, 0f), 0.0001f)
        val wrapped = DayCycle.advance(0.999f, GameConfig.DAY_LENGTH * 0.01f)
        assertTrue(wrapped in 0f..1f)
        assertTrue(DayCycle.daylight(0.5f) > 0.9f)
        assertTrue(DayCycle.isNight(0.0f))
        assertFalse(DayCycle.isNight(0.5f))
        assertEquals("12:00", DayCycle.clock(0.5f))
    }

    @Test
    fun headlightsDrainBatteryAndKillTheRun() {
        val engine = GameEngine(3L, 0f)
        engine.prepareForDriving()
        assertFalse(engine.headlightsOn)
        assertTrue(engine.toggleHeadlights())
        val before = engine.car.batteryCharge
        repeat(120) { engine.advance(1f / 60f) }
        assertTrue(engine.car.batteryCharge < before)

        engine.car.batteryCharge = 0.005f
        repeat(120) { engine.advance(1f / 60f) }
        assertEquals(GamePhase.GAME_OVER, engine.phase)
        assertEquals(EndReason.BATTERY_DEAD, engine.endReason)
    }

    @Test
    fun alternatorRechargesWhileEngineRuns() {
        val engine = GameEngine(4L, 0f)
        engine.prepareForDriving()
        assertTrue(engine.tryStartEngine())
        engine.car.batteryCharge = 0.4f
        repeat(120) { engine.advance(1f / 60f) }
        assertTrue(engine.car.batteryCharge > 0.4f)
    }

    @Test
    fun pumpRefuelsUntilEmpty() {
        val engine = GameEngine(5L, 0f)
        engine.prepareForDriving()
        assertTrue(engine.tryStartEngine())

        val station = engine.segment.buildings.first()
        station.pumpFuelL = 6f
        engine.car.x = engine.segment.worldOrigin + station.localX
        engine.car.speed = 0f
        engine.car.fuel = 1f
        assertTrue(engine.enterNearestBuilding())
        assertTrue(engine.refuelFromPump())
        assertEquals(7f, engine.car.fuel, 0.01f)
        assertEquals(0f, station.pumpFuelL, 0.01f)
        assertFalse(engine.refuelFromPump())
    }

    @Test
    fun unmountMovesPartToInventory() {
        val engine = GameEngine(6L, 0f)
        engine.inventory.clear()
        assertTrue(engine.car.hasPart(ComponentSlot.TIRE_FRONT))
        assertTrue(engine.unmountSlot(ComponentSlot.TIRE_FRONT))
        assertFalse(engine.car.hasPart(ComponentSlot.TIRE_FRONT))
        assertTrue(engine.inventory.slots.any { it?.def?.axleTire == true })
    }

    @Test
    fun engineCannotBeRemovedWhileRunning() {
        val engine = GameEngine(8L, 0f)
        engine.prepareForDriving()
        assertTrue(engine.tryStartEngine())
        assertFalse(engine.unmountSlot(ComponentSlot.ENGINE))
        assertTrue(engine.car.hasPart(ComponentSlot.ENGINE))
    }

    @Test
    fun fluidPurityMixesInTankAndCutsPower() {
        val engine = GameEngine(21L, 0f)
        engine.car.fuel = 10f
        engine.car.fuelPurity = 1f
        // 10 L čistého + 10 L vody → polovičná čistota.
        val added = engine.car.refill(FluidType.FUEL, 10f, 0f)
        assertEquals(10f, added, 0.01f)
        assertEquals(0.5f, engine.car.fuelPurity, 0.01f)

        val dirtyPower = engine.car.powerHp
        engine.car.fuelPurity = 1f
        assertTrue(engine.car.powerHp > dirtyPower)
    }

    @Test
    fun dirtyOilWearsTheEngineDown() {
        val engine = GameEngine(22L, 0f)
        engine.prepareForDriving()
        engine.car.oilPurity = 0.2f
        assertTrue(engine.tryStartEngine())
        val before = engine.car.parts[ComponentSlot.ENGINE]!!.health
        engine.throttleInput = 1f
        repeat(300) { engine.advance(1f / 60f) }
        assertTrue(engine.car.parts[ComponentSlot.ENGINE]!!.health < before)
    }

    @Test
    fun uphillBurnsMoreFuelThanDownhill() {
        fun burn(slope: Float): Float {
            val car = Car()
            car.installStarterKit()
            car.fuel = 30f
            car.fuelPurity = 1f
            car.engineRunning = true
            val before = car.fuel
            repeat(120) { car.tickDriving(1f / 60f, 1f, 1f, slope) }
            return before - car.fuel
        }
        val up = burn(0.25f)
        val flat = burn(0f)
        val down = burn(-0.25f)
        assertTrue("do kopca musí byť viac ako po rovine", up > flat)
        assertTrue("z kopca musí byť menej ako po rovine", down < flat)
    }

    @Test
    fun segmentPlanMatchesBuiltSegment() {
        val terrain = TerrainProfile(77L)
        val plan = WorldGenerator.planSegment(77L, BranchStyle.INDUSTRIAL, 1200f)
        val a = WorldGenerator.createSegment(plan, 0f, 1200f, terrain)
        val b = WorldGenerator.createSegment(plan, 0f, 1200f, terrain)
        assertEquals(plan.length, a.length, 0.001f)
        assertEquals(plan.features.size, a.sections.size)
        assertEquals(a.buildings.size, b.buildings.size)
        assertTrue(a.sections.first().start == 0f)
        assertEquals(plan.length, a.sections.last().end, 0.01f)
        // Úseky na seba nadväzujú bez dier.
        for (i in 1 until a.sections.size) {
            assertEquals(a.sections[i - 1].end, a.sections[i].start, 0.001f)
        }
    }

    @Test
    fun bridgeDeckStaysAboveTheGround() {
        val terrain = TerrainProfile(5L)
        var found = false
        for (seed in 1L..40L) {
            val plan = WorldGenerator.planSegment(seed, BranchStyle.INDUSTRIAL, 2000f)
            if (!plan.features.contains(RoadFeature.BRIDGE)) continue
            val seg = WorldGenerator.createSegment(plan, 0f, 2000f, terrain)
            val bridge = seg.sections.first { it.feature == RoadFeature.BRIDGE }
            val mid = seg.worldOrigin + (bridge.start + bridge.end) / 2f
            assertTrue(seg.heightAtWorld(mid) > seg.groundAtWorld(mid))
            assertTrue(seg.bridgeClearanceAtWorld(mid) > 1f)
            found = true
            break
        }
        assertTrue("v 40 seedoch musí byť aspoň jeden most", found)
    }

    @Test
    fun starterWreckIsRandomButAlwaysRecoverable() {
        val seen = mutableSetOf<String>()
        for (seed in 1L..30L) {
            val engine = GameEngine(seed, 0f)
            val car = engine.car
            seen += "${car.parts.keys.sorted().joinToString()}|${(car.fuel * 2).toInt()}"
            // Motor, nádrž, pneumatiky a pruženie ostávajú vždy.
            assertTrue(car.hasPart(ComponentSlot.ENGINE))
            assertTrue(car.hasPart(ComponentSlot.TIRE_FRONT))
            assertTrue(car.hasPart(ComponentSlot.TIRE_REAR))
            assertEquals(ItemCatalog.TIRE_POOR.id, car.parts[ComponentSlot.TIRE_FRONT]?.defId)

            // Kôlňa pri aute musí obsahovať všetko, čo chýba do štartu.
            val shed = engine.segment.buildings.first()
            assertTrue(shed.localX < 10f)
            car.missingEssentials().forEach { slot ->
                assertTrue(
                    "kôlňa musí ponúkať $slot (seed $seed)",
                    shed.loot.any { it.def.canMountTo(slot) }
                )
            }
            if (car.fuel < 1f) {
                assertTrue(shed.loot.any { it.def.fluid == FluidType.FUEL })
            }
        }
        assertTrue("štartovací vrak musí byť zakaždým iný", seen.size > 10)
    }

    @Test
    fun canEnterTheShedBeforeTheEngineEverRuns() {
        val engine = GameEngine(15L, 0f)
        assertEquals(GamePhase.PREP, engine.phase)
        assertTrue("kôlňa musí byť v dosahu", engine.buildingNear() != null)
        assertTrue("do budovy sa musí dať vojsť aj z PREP", engine.enterNearestBuilding())
        assertEquals(GamePhase.EXPLORING, engine.phase)
    }

    @Test
    fun strandedCarAlwaysGetsAWayOut() {
        for (seed in 1L..20L) {
            val engine = GameEngine(seed, 0f)
            // Najhorší možný stav: bez štartéra, batérie a paliva, prázdny batoh.
            engine.car.parts.remove(ComponentSlot.STARTER)
            engine.car.parts.remove(ComponentSlot.BATTERY)
            engine.car.fuel = 0f
            engine.inventory.clear()

            assertFalse(engine.tryStartEngine())
            assertTrue(engine.enterNearestBuilding())
            val loot = engine.activeBuilding!!.loot
            assertTrue(
                "vrak musí ponúknuť štartér (seed $seed)",
                loot.any { it.def.mountsTo == ComponentSlot.STARTER }
            )
            assertTrue(
                "vrak musí ponúknuť batériu (seed $seed)",
                loot.any { it.def.mountsTo == ComponentSlot.BATTERY }
            )
            assertTrue(
                "vrak musí ponúknuť palivo (seed $seed)",
                loot.any { it.def.fluid == FluidType.FUEL }
            )

            // A po namontovaní a natankovaní musí auto naozaj naštartovať.
            while (loot.isNotEmpty()) {
                if (!engine.takeLoot(0)) break
            }
            engine.leaveBuilding()
            repeat(engine.inventory.slots.size) { engine.useInventoryItem(it) }
            engine.car.oil = maxOf(engine.car.oil, 1f)
            engine.car.coolant = maxOf(engine.car.coolant, 1f)
            assertTrue("auto musí byť po oprave pojazdné (seed $seed)", engine.car.canStart())
        }
    }

    @Test
    fun weakCarCanStillClimbFromStandstill() {
        val car = Car()
        car.installStarterKit()
        car.fuel = 20f
        car.fuelPurity = 1f
        car.engineRunning = true
        // Najstrmšie, čo terén dovolí.
        repeat(180) { car.applyDrive(1f / 60f, 1f, 0f, 0f, 0.35f, 0.2f) }
        assertTrue("auto sa musí rozbehnúť aj do kopca, dostalo ${car.speed}", car.speed > 1f)
    }

    private fun drivingCar(layout: String, tireHealth: Float = 0.9f): Car {
        val car = Car()
        car.installStarterKit()
        car.mount(
            ComponentSlot.DRIVETRAIN,
            ItemStack(
                when (layout) {
                    "FWD" -> ItemCatalog.DRIVE_FWD.id
                    "AWD" -> ItemCatalog.DRIVE_AWD.id
                    else -> ItemCatalog.DRIVE_RWD.id
                },
                ComponentCondition.NEW, 1f
            )
        )
        TIRE_SLOTS.forEach {
            car.mount(it, ItemStack(ItemCatalog.TIRE.id, ComponentCondition.USED, tireHealth))
        }
        car.fuel = 20f
        car.engineRunning = true
        return car
    }

    @Test
    fun runningDryStopsTheCarButNotTheRun() {
        val engine = GameEngine(21L, 0f)
        engine.prepareForDriving()
        engine.tryStartEngine()
        engine.resumeDriving()
        // V batohu je kanister – dôjdené palivo teda nesmie znamenať koniec.
        engine.inventory.add(ItemStack(ItemCatalog.FUEL_CAN.id, ComponentCondition.NEW, 1f))
        engine.car.fuel = 0.02f
        engine.throttleInput = 1f
        repeat(120) { engine.advance(1f / 60f) }

        assertTrue("jazda nesmie skončiť, kým sa dá doliať", engine.phase != GamePhase.GAME_OVER)
        assertNull(engine.endReason)
        assertFalse("motor musí zhasnúť", engine.car.engineRunning)
        assertEquals(GamePhase.STOPPED, engine.phase)
    }

    @Test
    fun deadEngineWithNoSpareEndsTheRun() {
        val engine = GameEngine(22L, 0f)
        engine.prepareForDriving()
        engine.tryStartEngine()
        engine.resumeDriving()
        // Motor sa vymeniť nedá – v batohu nič a nablízku tiež nič.
        repeat(engine.inventory.slots.size) { engine.discardInventoryItem(it) }
        engine.segment.buildings.clear()
        engine.car.parts[ComponentSlot.ENGINE]?.health = 0.02f
        engine.throttleInput = 1f
        repeat(120) { engine.advance(1f / 60f) }

        assertEquals(GamePhase.GAME_OVER, engine.phase)
        assertEquals(EndReason.ENGINE_DESTROYED, engine.endReason)
    }

    @Test
    fun starterCarHooksUpOnTarmacButSpinsInMud() {
        // Štartovné 2WD auto sa na rovine musí chytiť, nie páliť gumy na mieste.
        for (seed in 1L..6L) {
            val car = Car()
            car.installStarterKit(SeededRandom(seed))
            car.fuel = 20f
            car.fuelPurity = 1f
            car.engineRunning = true
            repeat(120) { car.applyDrive(1f / 60f, 1f, 0f, 0f, 0f, 0f) }
            assertTrue("štart sa musí rozbehnúť (seed $seed), má ${car.speed}", car.speed > 6f)
            assertTrue(
                "na asfalte nemá stále prešmykovať (seed $seed), slip ${car.wheelSlip}",
                car.wheelSlip < 0.25f
            )
        }

        // V bahne naopak musí byť preklz jasne vidieť.
        val muddy = Car()
        muddy.installStarterKit(SeededRandom(3L))
        muddy.fuel = 20f
        muddy.engineRunning = true
        repeat(90) { muddy.applyDrive(1f / 60f, 1f, 0f, 0f, 0f, 0f, RoadSurface.MUD) }
        assertTrue("v bahne sa musí pretáčať, slip ${muddy.wheelSlip}", muddy.wheelSlip > 0.2f)
    }

    @Test
    fun spinWearsOnlyTheDrivenTyre() {
        val car = drivingCar("RWD", 0.5f)
        car.mount(ComponentSlot.ENGINE, ItemStack(ItemCatalog.ENGINE_C.id, ComponentCondition.NEW, 1f))
        val front0 = car.parts[ComponentSlot.TIRE_FRONT]!!.health
        val rear0 = car.parts[ComponentSlot.TIRE_REAR]!!.health
        repeat(300) {
            car.applyDrive(1f / 60f, 1f, 0f, 0f, 0f, 0f)
            car.tickWear(1f / 60f, 0f, 0f)
        }
        val frontLost = front0 - car.parts[ComponentSlot.TIRE_FRONT]!!.health
        val rearLost = rear0 - car.parts[ComponentSlot.TIRE_REAR]!!.health
        assertTrue("preklz musí zodierať hnanú nápravu viac", rearLost > frontLost * 1.3f)
    }

    @Test
    fun fwdPullsAwayWithoutBurningTheTyres() {
        // FWD má motor nad hnanou nápravou – nesmie sa len pretáčať.
        val fwd = drivingCar("FWD", 0.5f)
        repeat(180) { fwd.applyDrive(1f / 60f, 1f, 0f, 0f, 0f, 0f) }
        assertTrue("FWD sa musí rozbehnúť, má ${fwd.speed}", fwd.speed > 6f)
        assertTrue("FWD nesmie stále preklzávať, slip ${fwd.wheelSlip}", fwd.wheelSlip < 0.25f)

        val rwd = drivingCar("RWD", 0.5f)
        repeat(180) { rwd.applyDrive(1f / 60f, 1f, 0f, 0f, 0f, 0f) }
        // Rozdiel medzi pohonmi má byť v charaktere, nie v tom, či sa dá ísť.
        assertTrue(
            "FWD nesmie byť proti RWD nepoužiteľné (${fwd.speed} vs ${rwd.speed})",
            fwd.speed > rwd.speed * 0.8f
        )
    }

    @Test
    fun fwdTyresSurviveALongPull() {
        val fwd = drivingCar("FWD", 0.6f)
        val front0 = fwd.parts[ComponentSlot.TIRE_FRONT]!!.health
        repeat(1800) {
            fwd.applyDrive(1f / 60f, 1f, 0f, 0f, 0f, 0f)
            fwd.tickWear(1f / 60f, 0f, 0f)
        }
        val lost = front0 - fwd.parts[ComponentSlot.TIRE_FRONT]!!.health
        assertTrue("30 s plného plynu nesmie zožrať gumu, ubudlo $lost", lost < 0.08f)
    }

    /** Ustálená rýchlosť po dlhom plnom plyne na danom stúpaní (km/h). */
    private fun cruiseKmh(car: Car, slope: Float, paving: Float = 1f): Float {
        val wb = SedanSpec.wheelOffsetX * 2f
        car.snapToGround(0f, slope)
        repeat(1200) {
            car.applyDrive(1f / 60f, 1f, 0f, 0f, slope * wb, 0f, RoadSurface.ASPHALT, paving)
        }
        return car.speedKmh
    }

    @Test
    fun starterCarHasUsableSpeedAndPullsHills() {
        // Regresia: s ACCEL 9.5 a COAST_DRAG 1.8 auto na rovine skončilo na
        // 38 km/h a 25 % stúpanie ho úplne zastavilo.
        listOf("RWD", "FWD").forEach { layout ->
            val flat = cruiseKmh(drivingCar(layout, 0.6f), 0f)
            assertTrue("$layout na rovine musí ísť aspoň 45 km/h, ide $flat", flat > 45f)

            val hill = cruiseKmh(drivingCar(layout, 0.6f), 0.25f)
            assertTrue("$layout musí utiahnuť 25 % stúpanie, ide $hill", hill > 8f)
        }
    }

    @Test
    fun engineUpgradesShowUpAsSpeed() {
        fun withEngine(id: String): Car {
            val car = drivingCar("RWD", 0.9f)
            car.mount(ComponentSlot.ENGINE, ItemStack(id, ComponentCondition.NEW, 1f))
            return car
        }
        val a = cruiseKmh(withEngine(ItemCatalog.ENGINE_A.id), 0f)
        val b = cruiseKmh(withEngine(ItemCatalog.ENGINE_B.id), 0f)
        val c = cruiseKmh(withEngine(ItemCatalog.ENGINE_C.id), 0f)
        assertTrue("silnejší motor musí byť cítiť ($a → $b → $c)", b > a + 8f && c > b)

        // A hlavne v kopci, kde slabý motor zastane.
        val weakHill = cruiseKmh(withEngine(ItemCatalog.ENGINE_A.id), 0.35f)
        val strongHill = cruiseKmh(withEngine(ItemCatalog.ENGINE_C.id), 0.35f)
        assertTrue("v kopci musí byť rozdiel ešte väčší", strongHill > weakHill + 15f)
    }

    @Test
    fun starterCarClimbsOnEveryDryPaving() {
        // Regresia: po križovatke sa vetva zmení na hlinu/štrk/piesok. Ani tam
        // sa nesmie stať, že auto nevyjde ani mierny kopec.
        RoadPaving.entries.filter { !it.winter }.forEach { paving ->
            for (seed in 1L..4L) {
                val car = Car()
                car.installStarterKit(SeededRandom(seed))
                car.fuel = 20f
                car.fuelPurity = 1f
                car.engineRunning = true
                val wb = SedanSpec.wheelOffsetX * 2f
                car.snapToGround(0f, 0.25f)
                repeat(240) {
                    car.applyDrive(
                        1f / 60f, 1f, 0f, 0f, 0.25f * wb, 0f,
                        RoadSurface.ASPHALT, paving.gripMul
                    )
                }
                assertTrue(
                    "$paving (seed $seed) sa musí dať vyjsť, auto má ${car.speed}",
                    car.speed > 2.5f
                )
            }
        }
    }

    @Test
    fun tractionEaseOffNeverKillsTheClimb() {
        // Preklz smie ubrať ťah, ale nikdy nie tak, aby auto zastalo.
        val car = drivingCar("RWD", 0.35f)
        car.mount(ComponentSlot.ENGINE, ItemStack(ItemCatalog.ENGINE_C.id, ComponentCondition.NEW, 1f))
        val wb = SedanSpec.wheelOffsetX * 2f
        car.snapToGround(0f, 0.22f)
        repeat(300) {
            car.applyDrive(1f / 60f, 1f, 0f, 0f, 0.22f * wb, 0f, RoadSurface.ASPHALT, 0.88f)
        }
        assertTrue("aj s preklzom sa musí ísť hore, má ${car.speed}", car.speed > 2f)
    }

    @Test
    fun snowOnlyShowsUpLateInTheRun() {
        fun winterShare(distance: Float): Float {
            var winter = 0
            var total = 0
            for (seed in 1L..60L) {
                BranchStyle.entries.forEach { style ->
                    total++
                    if (WorldGenerator.planSegment(seed, style, distance).paving.winter) winter++
                }
            }
            return winter.toFloat() / total
        }
        assertEquals("skoro v jazde nesmie snežiť", 0f, winterShare(3000f), 0.001f)
        assertEquals(0f, winterShare(GameConfig.SNOW_START_M - 500f), 0.001f)
        assertTrue("neskôr už sneh musí prísť", winterShare(25000f) > 0.15f)
    }

    @Test
    fun winterGearOnlyDropsBeforeItIsNeeded() {
        fun winterFinds(distance: Float): Int {
            var found = 0
            for (seed in 1L..80L) {
                val loot = LootGenerator.generate(
                    SeededRandom(seed), BuildingType.AUTO_SHOP, distance, BranchStyle.INDUSTRIAL
                )
                found += loot.count {
                    it.defId == ItemCatalog.TIRE_WINTER.id || it.defId == ItemCatalog.SNOW_CHAINS.id
                }
            }
            return found
        }
        assertEquals("na začiatku zimná výbava nemá čo padať", 0, winterFinds(2000f))
        // Pred snehom sa už nájsť musí, inak by hráč do zimy vošiel bez šance.
        assertTrue(winterFinds(GameConfig.SNOW_START_M - 1500f) > 0)
    }

    @Test
    fun winterGearIsWhatMakesSnowDrivable() {
        fun snowRun(tire: String, chains: Boolean): Float {
            val car = Car()
            car.installStarterKit()
            TIRE_SLOTS.forEach {
                car.mount(it, ItemStack(tire, ComponentCondition.NEW, 1f))
            }
            if (chains) {
                car.mount(
                    ComponentSlot.CHAINS,
                    ItemStack(ItemCatalog.SNOW_CHAINS.id, ComponentCondition.NEW, 1f)
                )
            }
            car.fuel = 20f
            car.engineRunning = true
            val wb = SedanSpec.wheelOffsetX * 2f
            car.snapToGround(0f, 0.16f)
            repeat(200) {
                car.applyDrive(
                    1f / 60f, 1f, 0f, 0f, 0.16f * wb, 0f,
                    RoadSurface.ASPHALT, RoadPaving.SNOW.gripMul, winter = true
                )
            }
            return car.speed
        }
        val summer = snowRun(ItemCatalog.TIRE.id, chains = false)
        val winterTyres = snowRun(ItemCatalog.TIRE_WINTER.id, chains = false)
        val chained = snowRun(ItemCatalog.TIRE_WINTER.id, chains = true)
        assertTrue("zimné gumy musia byť na snehu lepšie ($winterTyres vs $summer)", winterTyres > summer + 1f)
        assertTrue("reťaze pridajú ešte viac", chained > winterTyres)
    }

    @Test
    fun coldEngineBurnsMoreAndFreezesWateryCoolant() {
        fun burn(cold: Float): Float {
            val car = Car()
            car.installStarterKit()
            car.fuel = 20f
            car.fuelPurity = 1f
            car.coolantPurity = 1f
            car.temperature = 35f
            car.engineRunning = true
            val before = car.fuel
            repeat(300) { car.tickDriving(1f / 60f, 1f, 1f, 0f, cold) }
            return before - car.fuel
        }
        assertTrue("v mraze musí studený motor žrať viac", burn(1f) > burn(0f) * 1.05f)

        // Voda v chladiči v mraze motor trhá – čistá kvapalina nie.
        fun engineLoss(purity: Float): Float {
            val car = Car()
            car.installStarterKit()
            car.fuel = 20f
            car.coolant = 4f
            car.coolantPurity = purity
            car.engineRunning = true
            val h0 = car.parts[ComponentSlot.ENGINE]!!.health
            repeat(600) { car.tickDriving(1f / 60f, 0.5f, 1f, 0f, 1f) }
            return h0 - car.parts[ComponentSlot.ENGINE]!!.health
        }
        assertTrue("zamrznutá voda musí ničiť motor", engineLoss(0.1f) > engineLoss(0.95f) * 1.5f)
    }

    @Test
    fun tyreCanBeFittedToTheChosenAxle() {
        val engine = GameEngine(31L, 0f)
        engine.inventory.clear()
        engine.inventory.add(ItemStack(ItemCatalog.TIRE_SPORT.id, ComponentCondition.NEW, 1f))
        assertTrue(engine.useInventoryItem(0, ComponentSlot.TIRE_FRONT))
        assertEquals(ItemCatalog.TIRE_SPORT.id, engine.car.parts[ComponentSlot.TIRE_FRONT]!!.defId)

        engine.inventory.clear()
        engine.inventory.add(ItemStack(ItemCatalog.TIRE_OFFROAD.id, ComponentCondition.NEW, 1f))
        assertTrue(engine.useInventoryItem(0, ComponentSlot.TIRE_REAR))
        assertEquals(ItemCatalog.TIRE_OFFROAD.id, engine.car.parts[ComponentSlot.TIRE_REAR]!!.defId)
        // Predok sa tým nesmie prepísať.
        assertEquals(ItemCatalog.TIRE_SPORT.id, engine.car.parts[ComponentSlot.TIRE_FRONT]!!.defId)
    }

    @Test
    fun storageAddsSlotsAndWeight() {
        val engine = GameEngine(33L, 0f)
        val baseSlots = engine.inventory.slots.size
        val baseWeight = engine.inventory.maxWeight

        engine.inventory.clear()
        engine.inventory.add(ItemStack(ItemCatalog.ROOF_RACK.id, ComponentCondition.NEW, 1f))
        assertTrue(engine.useInventoryItem(0, null))
        assertEquals(baseSlots + ItemCatalog.ROOF_RACK.extraSlots, engine.inventory.slots.size)
        assertEquals(
            baseWeight + ItemCatalog.ROOF_RACK.extraWeight,
            engine.inventory.maxWeight,
            0.01f
        )
        assertTrue(engine.car.hasRoofRack)

        // Batoh a nosič sa sčítajú – sú to dva rôzne sloty.
        engine.inventory.add(ItemStack(ItemCatalog.BACKPACK.id, ComponentCondition.NEW, 1f))
        val idx = engine.inventory.slots.indexOfFirst { it?.defId == ItemCatalog.BACKPACK.id }
        assertTrue(engine.useInventoryItem(idx, null))
        assertEquals(
            baseSlots + ItemCatalog.ROOF_RACK.extraSlots + ItemCatalog.BACKPACK.extraSlots,
            engine.inventory.slots.size
        )
    }

    @Test
    fun fullRackCannotBeRemovedUntilEmptied() {
        val engine = GameEngine(34L, 0f)
        engine.inventory.clear()
        engine.inventory.add(ItemStack(ItemCatalog.ROOF_RACK.id, ComponentCondition.NEW, 1f))
        engine.useInventoryItem(0, null)
        // Zaplníme batoh tak, aby sa bez nosiča nezmestil.
        repeat(engine.inventory.slots.size) {
            engine.inventory.add(ItemStack(ItemCatalog.WATER.id, ComponentCondition.NEW, 1f))
        }
        val used = engine.inventory.usedSlots
        assertFalse("plný nosič sa nesmie dať zložiť", engine.unmountSlot(ComponentSlot.ROOF_RACK))
        assertEquals("nič sa nesmie stratiť", used, engine.inventory.usedSlots)
        assertTrue(engine.car.hasRoofRack)
    }

    @Test
    fun storageSurvivesSaveAndRestore() {
        val engine = GameEngine(35L, 0f)
        engine.inventory.clear()
        engine.inventory.add(ItemStack(ItemCatalog.BOOT_CRATE.id, ComponentCondition.NEW, 1f))
        engine.useInventoryItem(0, null)
        repeat(5) {
            engine.inventory.add(ItemStack(ItemCatalog.OIL_BOTTLE.id, ComponentCondition.NEW, 1f))
        }
        val before = engine.inventory.usedSlots
        val restored = GameEngine.restore(
            RunCodec.decode(RunCodec.encode(engine.snapshot()))!!, 0f
        )
        assertEquals(engine.inventory.slots.size, restored.inventory.slots.size)
        assertEquals(before, restored.inventory.usedSlots)
    }

    @Test
    fun tyresCanBeSwappedBetweenAxles() {
        val car = Car()
        car.installStarterKit()
        car.mount(ComponentSlot.TIRE_FRONT, ItemStack(ItemCatalog.TIRE.id, ComponentCondition.USED, 0.8f))
        car.mount(ComponentSlot.TIRE_REAR, ItemStack(ItemCatalog.TIRE_POOR.id, ComponentCondition.CRITICAL, 0.2f))
        assertTrue(car.swapTyres())
        assertEquals(ItemCatalog.TIRE_POOR.id, car.parts[ComponentSlot.TIRE_FRONT]!!.defId)
        assertEquals(0.8f, car.parts[ComponentSlot.TIRE_REAR]!!.health, 0.001f)
        assertEquals(ItemCatalog.TIRE.id, car.parts[ComponentSlot.TIRE_REAR]!!.defId)
    }

    @Test
    fun everyBranchStyleCanProduceItsOwnPaving() {
        val seen = mutableSetOf<RoadPaving>()
        for (seed in 1L..40L) {
            BranchStyle.entries.forEach { style ->
                seen += WorldGenerator.planSegment(seed, style, 3000f).paving
            }
        }
        assertTrue("varianty vozovky sa musia striedať, videné: $seen", seen.size >= 5)
        // Tutorial ostáva na asfalte – prvý dojem nemá byť piesková stopa.
        assertEquals(RoadPaving.ASPHALT, GameEngine(7L, 0f).segment.paving)
    }

    @Test
    fun onlyDrivenAxleSpinsUnderPower() {
        // RWD s vypálenými gumami: zadné koleso sa točí rýchlejšie než predné,
        // ktoré sa len valí po ceste.
        val rwd = drivingCar("RWD", 0.3f)
        rwd.mount(ComponentSlot.ENGINE, ItemStack(ItemCatalog.ENGINE_C.id, ComponentCondition.NEW, 1f))
        repeat(40) { rwd.applyDrive(1f / 60f, 1f, 0f, 0f, 0f, 0f) }
        assertTrue("RWD musí pretáčať zadok, slip ${rwd.wheelSlip}", rwd.wheelSlip > 0.15f)
        assertTrue(
            "zadné koleso sa musí točiť inak než predné",
            kotlin.math.abs(rwd.wheelSpinRearDeg - rwd.wheelSpinFrontDeg) > 1f
        )

        val fwd = drivingCar("FWD", 0.3f)
        fwd.mount(ComponentSlot.ENGINE, ItemStack(ItemCatalog.ENGINE_C.id, ComponentCondition.NEW, 1f))
        repeat(40) { fwd.applyDrive(1f / 60f, 1f, 0f, 0f, 0f, 0f) }
        assertEquals(ComponentSlot.TIRE_FRONT, fwd.drivenSlot)
        assertEquals(ComponentSlot.TIRE_REAR, rwd.drivenSlot)
    }

    @Test
    fun mudSlowsTheCarDownComparedToTarmac() {
        fun run(surface: RoadSurface): Float {
            val car = drivingCar("RWD")
            repeat(240) { car.applyDrive(1f / 60f, 1f, 0f, 0f, 0f, 0f, surface) }
            return car.speed
        }
        val tarmac = run(RoadSurface.ASPHALT)
        assertTrue("bahno musí byť citeľne pomalšie", run(RoadSurface.MUD) < tarmac * 0.85f)
        assertTrue("voda musí brzdiť", run(RoadSurface.WATER) < tarmac * 0.92f)
        assertTrue("štrk brzdí len mierne", run(RoadSurface.GRAVEL) < tarmac)
    }

    @Test
    fun surfacePatchesAppearAndStayOffBridges() {
        val terrain = TerrainProfile(9L)
        var total = 0
        for (seed in 1L..8L) {
            val plan = WorldGenerator.planSegment(seed, BranchStyle.SHORTCUT_RISK, 9000f)
            val seg = WorldGenerator.createSegment(plan, 0f, 9000f, terrain)
            total += seg.patches.size
            var prevEnd = 0f
            seg.patches.forEach { patch ->
                assertTrue("naplaveniny sa nesmú prekrývať", patch.start >= prevEnd)
                prevEnd = patch.end
                assertTrue(patch.length > 3f)
                assertTrue("nesmie zasahovať do rázcestia", patch.end <= seg.length)
                val feature = seg.sectionAtLocal(patch.start)?.feature
                assertTrue("na moste naplavenina nemá čo robiť", feature != RoadFeature.BRIDGE)
                assertEquals(patch.surface, seg.surfaceAtLocal(patch.start + 0.5f))
            }
        }
        assertTrue("na 9. km už majú prekážky byť bežné, bolo $total", total > 8)
    }

    @Test
    fun tutorialRoadHasNoSurfaceTraps() {
        val engine = GameEngine(4L, 0f)
        assertTrue(engine.segment.patches.isEmpty())
        assertEquals(RoadSurface.ASPHALT, engine.currentSurface)
    }

    @Test
    fun terrainHasNoCliffsBetweenSections() {
        val terrain = TerrainProfile(123L)
        for (seed in 1L..12L) {
            val plan = WorldGenerator.planSegment(seed, BranchStyle.SHORTCUT_RISK, 3000f)
            val seg = WorldGenerator.createSegment(plan, 0f, 3000f, terrain)
            var x = 0.5f
            var prev = seg.heightAtLocal(x)
            while (x < seg.length - 1f) {
                x += 0.5f
                val h = seg.heightAtLocal(x)
                val slope = kotlin.math.abs(h - prev) / 0.5f
                assertTrue(
                    "zráz na seede $seed pri x=$x (sklon $slope)",
                    slope < 1.2f
                )
                prev = h
            }
        }
    }

    @Test
    fun engineNeverDiesSilently() {
        val engine = GameEngine(41L, 0f)
        engine.prepareForDriving()
        engine.car.oilPurity = 0.1f
        assertTrue(engine.tryStartEngine())
        engine.throttleInput = 1f
        var warned = false
        repeat(60 * 60) {
            engine.advance(1f / 60f)
            if (engine.message.contains("olej", ignoreCase = true) ||
                engine.message.contains("motor", ignoreCase = true)
            ) {
                warned = true
            }
            if (engine.phase == GamePhase.GAME_OVER) return@repeat
        }
        assertTrue("hráč musí dostať varovanie o motore", warned)
        if (engine.endReason == EndReason.ENGINE_DESTROYED) {
            assertTrue(engine.endDetail.isNotEmpty())
        }
    }

    @Test
    fun buildingLootVariesBetweenBuildings() {
        val terrain = TerrainProfile(9L)
        val sizes = mutableSetOf<Int>()
        var buildings = 0
        for (seed in 1L..25L) {
            val plan = WorldGenerator.planSegment(seed, BranchStyle.INDUSTRIAL, 1500f)
            val seg = WorldGenerator.createSegment(plan, 0f, 1500f, terrain)
            seg.buildings.forEach {
                sizes += it.loot.size
                buildings++
            }
        }
        assertTrue("musia vzniknúť budovy", buildings > 10)
        assertTrue("obsah budov musí kolísať, nie byť rovnaký", sizes.size >= 4)
    }

    @Test
    fun junctionChoicesCarryRealPlans() {
        val engine = GameEngine(31L, 0f)
        val choices = engine.junctionChoices
        assertTrue(choices.size >= 2)
        choices.forEach {
            assertTrue(it.plan.length > 300f)
            assertTrue(it.plan.features.isNotEmpty())
            assertTrue(it.plan.risk in 0f..1f)
        }
        // Vybraná vetva musí sedieť s tým, čo križovatka sľúbila.
        engine.prepareForDriving()
        engine.tryStartEngine()
        engine.car.x = engine.segment.endWorldX - 2f
        engine.car.speed = 0f
        engine.requestStop()
        val chosen = engine.junctionChoices.first()
        assertTrue(engine.chooseBranch(chosen.id))
        assertEquals(chosen.plan.length, engine.segment.length, 0.01f)
        assertEquals(chosen.plan.features.size, engine.segment.sections.size)
    }

    @Test
    fun junctionChoiceCreatesNewSegment() {
        val engine = GameEngine(11L, 0f)
        engine.prepareForDriving()
        engine.tryStartEngine()
        repeat(10) { engine.advance(1f / 60f) }

        // Teleport near junction
        engine.car.x = engine.segment.endWorldX - 2f
        engine.car.speed = 0f
        engine.requestStop()
        assertEquals(GamePhase.JUNCTION, engine.phase)
        assertTrue(engine.junctionChoices.isNotEmpty())
        val choice = engine.junctionChoices.first()
        val oldEnd = engine.segment.endWorldX
        assertTrue(engine.chooseBranch(choice.id))
        assertEquals(GamePhase.DRIVING, engine.phase)
        assertEquals(oldEnd, engine.segment.worldOrigin, 0.01f)
        assertEquals(choice.style, engine.segment.style)
    }

    @Test
    fun axleTiresMountSeparatelyAndAffectGrip() {
        val car = Car()
        car.installStarterKit(SeededRandom(3L))
        assertEquals(ItemCatalog.TIRE_POOR.id, car.parts[ComponentSlot.TIRE_FRONT]?.defId)
        assertEquals(ItemCatalog.TIRE_POOR.id, car.parts[ComponentSlot.TIRE_REAR]?.defId)
        val poorGrip = car.tireGrip

        car.mountBothTires(ItemCatalog.TIRE_SPORT.id, ComponentCondition.NEW, 1f)
        assertTrue("sport gumy musia držať lepšie", car.tireGrip > poorGrip + 0.2f)

        // Predok poor, zadok sport – RWD ťaží zadok.
        car.mount(ComponentSlot.DRIVETRAIN, ItemStack(ItemCatalog.DRIVE_RWD.id, ComponentCondition.NEW, 1f))
        car.mount(ComponentSlot.TIRE_FRONT, ItemStack(ItemCatalog.TIRE_POOR.id, ComponentCondition.USED, 0.5f))
        car.mount(ComponentSlot.TIRE_REAR, ItemStack(ItemCatalog.TIRE_SPORT.id, ComponentCondition.NEW, 1f))
        val rwdMixed = car.tireGrip
        car.mount(ComponentSlot.DRIVETRAIN, ItemStack(ItemCatalog.DRIVE_FWD.id, ComponentCondition.NEW, 1f))
        val fwdMixed = car.tireGrip
        assertTrue("RWD má ťažiť lepší zadok", rwdMixed > fwdMixed)
        assertEquals(DriveLayout.FWD, car.driveLayout)
    }

    @Test
    fun inventoryTireFitsEmptyAxleFirst() {
        val engine = GameEngine(12L, 0f)
        engine.inventory.clear()
        assertTrue(engine.unmountSlot(ComponentSlot.TIRE_FRONT))
        assertFalse(engine.car.hasPart(ComponentSlot.TIRE_FRONT))
        assertTrue(engine.car.hasPart(ComponentSlot.TIRE_REAR))
        assertTrue(engine.inventory.add(ItemStack(ItemCatalog.TIRE.id, ComponentCondition.USED, 0.8f)))
        val idx = engine.inventory.slots.indexOfFirst { it?.defId == ItemCatalog.TIRE.id }
        assertTrue(engine.useInventoryItem(idx))
        assertEquals(ItemCatalog.TIRE.id, engine.car.parts[ComponentSlot.TIRE_FRONT]?.defId)
        assertEquals(ItemCatalog.TIRE_POOR.id, engine.car.parts[ComponentSlot.TIRE_REAR]?.defId)
    }
}

/**
 * Štartovací vrak je náhodný – testom, ktoré potrebujú jazdiace auto,
 * doplníme chýbajúce diely a čisté kvapaliny.
 */
private fun Car.mountBothTires(defId: String, condition: ComponentCondition, health: Float) {
    mount(ComponentSlot.TIRE_FRONT, ItemStack(defId, condition, health))
    mount(ComponentSlot.TIRE_REAR, ItemStack(defId, condition, health))
}

private fun GameEngine.prepareForDriving() {
    listOf(
        ComponentSlot.BATTERY to ItemCatalog.BATTERY,
        ComponentSlot.STARTER to ItemCatalog.STARTER,
        ComponentSlot.RADIATOR to ItemCatalog.RADIATOR,
        ComponentSlot.ALTERNATOR to ItemCatalog.ALTERNATOR
    ).forEach { (slot, def) ->
        if (!car.hasPart(slot)) {
            car.mount(slot, ItemStack(def.id, ComponentCondition.USED, 0.7f))
        }
    }
    car.batteryCharge = 0.8f
    car.fuel = 30f
    car.oil = 3f
    car.coolant = 5f
    car.fuelPurity = 1f
    car.oilPurity = 1f
    car.coolantPurity = 1f
}
