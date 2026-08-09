package sk.kubis.endlessdrive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import sk.kubis.endlessdrive.domain.model.BranchStyle
import sk.kubis.endlessdrive.domain.model.ComponentSlot
import sk.kubis.endlessdrive.domain.model.GamePhase
import sk.kubis.endlessdrive.domain.model.ItemCatalog
import sk.kubis.endlessdrive.domain.model.ItemStack
import sk.kubis.endlessdrive.game.GameEngine
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

        engine.car.oil = 2f
        engine.car.coolant = 3f
        engine.car.fuel = 15f
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
        engine.car.oil = 3f
        engine.car.coolant = 4f
        engine.car.fuel = 20f
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
    fun junctionChoiceCreatesNewSegment() {
        val engine = GameEngine(11L, 0f)
        engine.car.oil = 3f
        engine.car.coolant = 4f
        engine.car.fuel = 30f
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
