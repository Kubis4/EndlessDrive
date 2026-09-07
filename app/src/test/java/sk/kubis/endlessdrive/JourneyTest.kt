package sk.kubis.endlessdrive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import sk.kubis.endlessdrive.domain.model.BuildingType
import sk.kubis.endlessdrive.domain.model.DebugOptions
import sk.kubis.endlessdrive.domain.model.ItemCatalog
import sk.kubis.endlessdrive.domain.model.ItemStack
import sk.kubis.endlessdrive.game.GameEngine
import sk.kubis.endlessdrive.game.Journey
import sk.kubis.endlessdrive.game.save.RunCodec
import sk.kubis.endlessdrive.game.world.WorldBuilding

class JourneyTest {
    @Test fun journeyUsesLongUnevenGoalsAndEndsAtSafeHaven() {
        assertTrue(Journey.goals.size >= 7)
        assertEquals(3, Journey.completedGoals(20_000f))
        assertEquals(7, Journey.completedGoals(Journey.FINAL_DISTANCE_M))
        assertEquals(35f, Journey.nextGoal(3)!!.distanceKm, 0.001f)
        assertEquals(100f, Journey.goals.last().distanceKm, 0.001f)
    }

    @Test fun rewardsOnlyNewKilometres() {
        assertEquals(0, Journey.rewardBetween(0f, 999.9f))
        assertEquals(3, Journey.rewardBetween(999.9f, 1000f))
        assertEquals(0, Journey.rewardBetween(1000f, 1000f))
        assertEquals(0, Journey.rewardBetween(1200f, 900f))
        assertEquals(9, Journey.rewardBetween(999f, 3001f))
    }

    @Test fun milestoneRewardSurvivesSaveWithoutBeingAwardedAgain() {
        val game = GameEngine(42L, 0f, DebugOptions(allComponents = true, fullFluids = true))
        game.tryStartEngine()
        game.car.x = 1001f
        game.advance(1f / 30f)
        assertEquals(3, game.scrap)
        assertFalse(game.message.contains("km reached", ignoreCase = true))
        val restored = GameEngine.restore(RunCodec.decode(RunCodec.encode(game.snapshot()))!!, 0f)
        restored.tryStartEngine()
        restored.advance(1f / 30f)
        assertEquals(3, restored.scrap)
    }

    @Test fun pickingUpScrapAddsCurrency() {
        val game = GameEngine(8L, 0f, DebugOptions(allComponents = true, fullFluids = true))
        val wreck = WorldBuilding(
            id = 77L,
            type = BuildingType.WRECK,
            localX = 0f,
            loot = mutableListOf(
                ItemStack(ItemCatalog.SCRAP_PILE.id, count = 3)
            )
        )
        game.segment.buildings.add(0, wreck)
        game.car.x = game.segment.worldOrigin
        game.car.speed = 0f
        assertTrue(game.enterNearestBuilding())
        val before = game.scrap
        val pack = game.inventory.usedSlots
        assertTrue(game.takeLoot(0))
        assertEquals(before + 3, game.scrap)
        assertEquals(pack, game.inventory.usedSlots)
        assertTrue(game.activeBuilding!!.loot.isEmpty())
    }
}
