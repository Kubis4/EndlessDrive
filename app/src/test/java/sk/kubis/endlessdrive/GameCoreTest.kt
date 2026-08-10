package sk.kubis.endlessdrive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import sk.kubis.endlessdrive.core.GameConfig
import sk.kubis.endlessdrive.domain.model.BranchStyle
import sk.kubis.endlessdrive.domain.model.ComponentSlot
import sk.kubis.endlessdrive.domain.model.EndReason
import sk.kubis.endlessdrive.domain.model.FluidType
import sk.kubis.endlessdrive.domain.model.GamePhase
import sk.kubis.endlessdrive.domain.model.ItemCatalog
import sk.kubis.endlessdrive.domain.model.ItemStack
import sk.kubis.endlessdrive.domain.model.RoadFeature
import sk.kubis.endlessdrive.game.DayCycle
import sk.kubis.endlessdrive.game.GameEngine
import sk.kubis.endlessdrive.game.car.Car
import sk.kubis.endlessdrive.game.world.TerrainProfile
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
        assertTrue(engine.car.hasPart(ComponentSlot.TIRES))
        assertTrue(engine.unmountSlot(ComponentSlot.TIRES))
        assertFalse(engine.car.hasPart(ComponentSlot.TIRES))
        assertTrue(engine.inventory.slots.any { it?.def?.mountsTo == ComponentSlot.TIRES })
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
            assertTrue(car.hasPart(ComponentSlot.TIRES))

            // Kôlňa pri aute musí obsahovať všetko, čo chýba do štartu.
            val shed = engine.segment.buildings.first()
            assertTrue(shed.localX < 10f)
            car.missingEssentials().forEach { slot ->
                assertTrue(
                    "kôlňa musí ponúkať $slot (seed $seed)",
                    shed.loot.any { it.def.mountsTo == slot }
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
}

/**
 * Štartovací vrak je náhodný – testom, ktoré potrebujú jazdiace auto,
 * doplníme chýbajúce diely a čisté kvapaliny.
 */
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
