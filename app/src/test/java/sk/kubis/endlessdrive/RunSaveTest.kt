package sk.kubis.endlessdrive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import sk.kubis.endlessdrive.domain.model.ComponentCondition
import sk.kubis.endlessdrive.domain.model.ComponentSlot
import sk.kubis.endlessdrive.domain.model.GamePhase
import sk.kubis.endlessdrive.domain.model.ItemCatalog
import sk.kubis.endlessdrive.domain.model.ItemStack
import sk.kubis.endlessdrive.domain.model.FuelKind
import sk.kubis.endlessdrive.game.GameEngine
import sk.kubis.endlessdrive.game.car.MountedPart
import sk.kubis.endlessdrive.game.car.TireInjury
import sk.kubis.endlessdrive.game.save.RunCodec

class RunSaveTest {

    private fun playedRun(seed: Long): GameEngine {
        val engine = GameEngine(seed, 0f)
        // Dorobíme auto do pojazdného stavu a chvíľu ním ideme.
        listOf(
            ComponentSlot.BATTERY to ItemCatalog.BATTERY,
            ComponentSlot.STARTER to ItemCatalog.STARTER,
            ComponentSlot.RADIATOR to ItemCatalog.RADIATOR,
            ComponentSlot.ALTERNATOR to ItemCatalog.ALTERNATOR
        ).forEach { (slot, def) ->
            if (!engine.car.hasPart(slot)) {
                engine.car.mount(slot, ItemStack(def.id, ComponentCondition.USED, 0.7f))
            }
        }
        engine.car.batteryCharge = 0.8f
        engine.car.fuel = 30f
        engine.car.oil = 3f
        engine.car.coolant = 5f
        engine.tryStartEngine()
        engine.throttleInput = 1f
        repeat(400) { engine.advance(1f / 60f) }
        return engine
    }

    @Test
    fun snapshotSurvivesRoundTrip() {
        val engine = playedRun(1234L)
        engine.car.fuelDieselFraction = 0.35f
        engine.car.bodyPaintIndex = 6
        engine.car.mount(
            ComponentSlot.DOOR_FRONT,
            ItemStack(ItemCatalog.DOOR_FRONT.id, paintIndex = 3)
        )
        engine.car.mount(
            ComponentSlot.HEADLIGHT,
            ItemStack(ItemCatalog.HEADLIGHT.id)
        )
        // Starší save mohol svetlometu priradiť lak ako dverám.
        engine.car.parts[ComponentSlot.HEADLIGHT]?.paintIndex = 2
        engine.segment.buildings.first().apply {
            pumpFuelL = 4.5f
            pumpDieselL = 7.25f
            pumpFuelKind = FuelKind.DIESEL
        }
        // Simuluje starší save, v ktorom nosič dostal náhodný lak.
        engine.car.parts[ComponentSlot.ROOF_RACK] = MountedPart(
            ItemCatalog.ROOF_RACK.id,
            ComponentCondition.NEW,
            1f,
            paintIndex = 3
        )
        val text = RunCodec.encode(engine.snapshot())
        val decoded = RunCodec.decode(text)
        assertNotNull("kodek musí prečítať vlastný zápis", decoded)

        val restored = GameEngine.restore(decoded!!, 0f)
        assertEquals(engine.seed, restored.seed)
        assertEquals(engine.distanceM, restored.distanceM, 0.01f)
        assertEquals(engine.timeOfDay, restored.timeOfDay, 0.0001f)
        assertEquals(engine.car.fuel, restored.car.fuel, 0.01f)
        assertEquals(engine.car.fuelPurity, restored.car.fuelPurity, 0.001f)
        assertEquals(engine.car.fuelDieselFraction, restored.car.fuelDieselFraction, 0.001f)
        assertEquals(engine.car.bodyPaintIndex, restored.car.bodyPaintIndex)
        assertEquals(3, restored.car.parts[ComponentSlot.DOOR_FRONT]?.paintIndex)
        assertEquals(-1, restored.car.parts[ComponentSlot.HEADLIGHT]?.paintIndex)
        assertEquals(engine.car.x, restored.car.x, 0.01f)
        assertEquals(engine.car.parts.size, restored.car.parts.size)
        assertEquals(-1, restored.car.parts[ComponentSlot.ROOF_RACK]?.paintIndex)
        assertEquals(engine.fuelBurnedL, restored.fuelBurnedL, 0.01f)
        // Terén aj úseky sa dopočítajú zo seedu – musia vyjsť rovnako.
        assertEquals(engine.segment.length, restored.segment.length, 0.01f)
        assertEquals(engine.segment.sections.size, restored.segment.sections.size)
        assertEquals(
            engine.segment.buildings.first().pumpFuelKind,
            restored.segment.buildings.first().pumpFuelKind
        )
        assertEquals(4.5f, restored.segment.buildings.first().pumpFuelL, 0.01f)
        assertEquals(7.25f, restored.segment.buildings.first().pumpDieselL, 0.01f)
        assertEquals(
            engine.segment.heightAtWorld(engine.car.x),
            restored.segment.heightAtWorld(restored.car.x),
            0.001f
        )
    }

    @Test
    fun tyreInjurySurvivesRoundTrip() {
        val engine = playedRun(91L)
        engine.car.parts[ComponentSlot.TIRE_FRONT]!!.injury = TireInjury.PUNCTURED
        engine.car.parts[ComponentSlot.TIRE_REAR]!!.injury = TireInjury.SHREDDED
        val restored = GameEngine.restore(RunCodec.decode(RunCodec.encode(engine.snapshot()))!!, 0f)
        assertEquals(TireInjury.PUNCTURED, restored.car.tireInjury(ComponentSlot.TIRE_FRONT))
        assertEquals(TireInjury.SHREDDED, restored.car.tireInjury(ComponentSlot.TIRE_REAR))
    }

    @Test
    fun lootedBuildingsStayLooted() {
        val engine = GameEngine(77L, 0f)
        val shed = engine.segment.buildings.first()
        val before = shed.loot.size
        assertTrue(before > 0)
        // Vyberieme z kôlne prvú vec.
        engine.car.x = engine.segment.worldOrigin + shed.localX
        engine.enterNearestBuilding()
        engine.takeLoot(0)
        engine.leaveBuilding()

        val restored = GameEngine.restore(RunCodec.decode(RunCodec.encode(engine.snapshot()))!!, 0f)
        val restoredShed = restored.segment.buildings.first { it.id == shed.id }
        assertEquals(before - 1, restoredShed.loot.size)
        assertEquals(shed.pumpFuelL, restoredShed.pumpFuelL, 0.01f)
        assertEquals(shed.pumpDieselL, restoredShed.pumpDieselL, 0.01f)
    }

    @Test
    fun restoredRunNeverResumesMidDrive() {
        val engine = playedRun(9L)
        assertEquals(GamePhase.DRIVING, engine.phase)
        val restored = GameEngine.restore(RunCodec.decode(RunCodec.encode(engine.snapshot()))!!, 0f)
        // Po návrate stojíme – hráč sa najprv rozhliadne, nevbehne do zákruty.
        assertEquals(GamePhase.STOPPED, restored.phase)
        assertEquals(0f, restored.car.speed, 0.001f)
    }

    @Test
    fun eventsKeepRunningAfterRestore() {
        val engine = playedRun(4242L)
        // Dojazdíme dosť dlho na to, aby sa nejaká udalosť spustila.
        repeat(60 * 90) {
            engine.advance(1f / 60f)
            if (engine.activeEvents.isNotEmpty()) return@repeat
        }
        if (engine.activeEvents.isEmpty()) return // v tomto seede sa nič netrafilo

        val before = engine.activeEvents.map { it.event to it.remaining }
        val restored = GameEngine.restore(RunCodec.decode(RunCodec.encode(engine.snapshot()))!!, 0f)
        val after = restored.activeEvents.map { it.event to it.remaining }
        assertEquals(before.size, after.size)
        before.zip(after).forEach { (a, b) ->
            assertEquals(a.first, b.first)
            assertEquals(a.second, b.second, 0.01f)
        }
    }

    @Test
    fun brokenSaveIsIgnored() {
        assertNull(RunCodec.decode(""))
        assertNull(RunCodec.decode("v99\nnonsense"))
        assertNull(RunCodec.decode("v1|garbage"))
    }

    @Test
    fun highBeamModeSurvivesSaveAndRestore() {
        val engine = GameEngine(55L, 0f)
        engine.car.mount(
            ComponentSlot.HEADLIGHT,
            ItemStack(ItemCatalog.HEADLIGHT.id, ComponentCondition.NEW, 1f)
        )
        engine.toggleHeadlights()
        engine.toggleHeadlights()
        assertTrue(engine.highBeamsOn)

        val restored = GameEngine.restore(
            RunCodec.decode(RunCodec.encode(engine.snapshot()))!!,
            0f
        )
        assertTrue(restored.headlightsOn)
        assertTrue(restored.highBeamsOn)
    }
}
