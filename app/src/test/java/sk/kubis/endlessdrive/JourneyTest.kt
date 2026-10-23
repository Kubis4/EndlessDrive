package sk.kubis.endlessdrive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import sk.kubis.endlessdrive.domain.model.BuildingType
import sk.kubis.endlessdrive.domain.model.DebugOptions
import sk.kubis.endlessdrive.domain.model.EndReason
import sk.kubis.endlessdrive.domain.model.GamePhase
import sk.kubis.endlessdrive.domain.model.ItemCatalog
import sk.kubis.endlessdrive.domain.model.ItemStack
import sk.kubis.endlessdrive.domain.repository.GarageUpgrades
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

    @Test fun tutorialAlwaysOffersBothFuelTypes() {
        repeat(20) { index ->
            val game = GameEngine(index + 1L, 0f)
            val tutorialHouse = game.segment.buildings.firstOrNull { building ->
                building.type == BuildingType.HOUSE &&
                    building.loot.any { it.defId == ItemCatalog.FUEL_CAN.id } &&
                    building.loot.any { it.defId == ItemCatalog.DIESEL_CAN.id }
            }
            assertTrue("seed ${index + 1} nemá oba druhy paliva", tutorialHouse != null)
        }
    }

    @Test fun garageUpgradesImproveNewRunWithoutChangingTheSeed() {
        val base = GameEngine(314L, 0f)
        val upgraded = GameEngine(
            314L,
            0f,
            garageUpgrades = GarageUpgrades(fuelReserve = 2, reliability = 2, storage = 2)
        )

        assertEquals(
            (base.car.fuel + 6f).coerceAtMost(upgraded.car.fuelCapacity),
            upgraded.car.fuel,
            0.001f
        )
        assertEquals(base.inventory.slots.size + 2, upgraded.inventory.slots.size)
        assertEquals(base.inventory.maxWeight + 8f, upgraded.inventory.maxWeight, 0.001f)
        val commonParts = base.car.parts.keys.intersect(upgraded.car.parts.keys)
        assertTrue(commonParts.all { upgraded.car.parts[it]!!.health >= base.car.parts[it]!!.health })
        assertTrue(commonParts.any { upgraded.car.parts[it]!!.health > base.car.parts[it]!!.health })
    }

    @Test fun newGarageUpgradesApplyOnFreshRuns() {
        val bareElectrics = GameEngine(501L, 0f)
        val charged = GameEngine(
            501L,
            0f,
            garageUpgrades = GarageUpgrades(electrics = 2)
        )
        assertTrue(charged.car.hasPart(sk.kubis.endlessdrive.domain.model.ComponentSlot.BATTERY))
        assertTrue(charged.car.hasPart(sk.kubis.endlessdrive.domain.model.ComponentSlot.STARTER))
        assertTrue(charged.car.hasPart(sk.kubis.endlessdrive.domain.model.ComponentSlot.ALTERNATOR))
        assertTrue(charged.car.batteryCharge >= bareElectrics.car.batteryCharge)

        val rubber = GameEngine(
            502L,
            0f,
            garageUpgrades = GarageUpgrades(starterTyres = 2)
        )
        assertEquals(ItemCatalog.TIRE.id, rubber.car.parts[sk.kubis.endlessdrive.domain.model.ComponentSlot.TIRE_FRONT]!!.defId)
        assertEquals(ItemCatalog.TIRE.id, rubber.car.parts[sk.kubis.endlessdrive.domain.model.ComponentSlot.TIRE_REAR]!!.defId)
        assertTrue(rubber.car.parts[sk.kubis.endlessdrive.domain.model.ComponentSlot.TIRE_FRONT]!!.health >= 0.82f)

        val oily = GameEngine(
            503L,
            0f,
            garageUpgrades = GarageUpgrades(oilReserve = 2)
        )
        val plain = GameEngine(503L, 0f)
        assertEquals(
            (plain.car.oil + 2.4f).coerceAtMost(oily.car.oilCapacity),
            oily.car.oil,
            0.05f
        )

        val kit = GameEngine(
            504L,
            0f,
            garageUpgrades = GarageUpgrades(spareKit = 2)
        )
        assertTrue(kit.inventory.slots.any { it?.defId == ItemCatalog.PUNCTURE_KIT.id })
        assertTrue(kit.inventory.slots.any { it?.defId == ItemCatalog.COOLANT_BOTTLE.id })

        val performance = GameEngine(
            505L,
            0f,
            garageUpgrades = GarageUpgrades(engineTune = 3, loadSuspension = 2, brakePackage = 1)
        )
        assertEquals(1.18f, performance.car.garagePowerMultiplier, 0.001f)
        assertEquals(1.20f, performance.car.garageSpringMultiplier, 0.001f)
        assertEquals(1.16f, performance.car.garageDamperMultiplier, 0.001f)
        assertEquals(1.10f, performance.car.garageBrakeMultiplier, 0.001f)
    }

    @Test fun garageUpgradePricesArePremiumForNewOptions() {
        assertTrue(sk.kubis.endlessdrive.domain.repository.GarageUpgrade.STARTER_TYRES.costAt(0)!! >= 90)
        assertTrue(sk.kubis.endlessdrive.domain.repository.GarageUpgrade.ELECTRICS.costAt(0)!! >= 80)
        assertTrue(sk.kubis.endlessdrive.domain.repository.GarageUpgrade.SPARE_KIT.costAt(0)!! >= 85)
        assertTrue(sk.kubis.endlessdrive.domain.repository.GarageUpgrade.OIL_RESERVE.costAt(0)!! >= 70)
        assertTrue(sk.kubis.endlessdrive.domain.repository.GarageUpgrade.ENGINE_TUNE.costAt(0)!! >= 90)
        assertTrue(sk.kubis.endlessdrive.domain.repository.GarageUpgrade.LOAD_SUSPENSION.costAt(0)!! >= 75)
        assertTrue(sk.kubis.endlessdrive.domain.repository.GarageUpgrade.BRAKE_PACKAGE.costAt(0)!! >= 70)
        assertTrue(sk.kubis.endlessdrive.domain.repository.GarageUpgrade.CRUISE_EFFICIENCY.costAt(0)!! >= 85)
    }

    @Test fun cruiseFuelMultiplierStartsAtOnePointFiveAndGarageReducesIt() {
        val base = GameEngine(606L, 0f)
        assertEquals(1.5f, base.car.cruiseFuelMultiplier, 0.001f)

        val tuned = GameEngine(
            606L,
            0f,
            garageUpgrades = GarageUpgrades(cruiseEfficiency = 3)
        )
        assertEquals(1.05f, tuned.car.cruiseFuelMultiplier, 0.001f)
    }

    @Test fun safeHavenChoiceSurvivesSaveAndCanFinishOrContinue() {
        val game = GameEngine(99L, 0f)
        val decision = game.snapshot().copy(
            phase = GamePhase.STOPPED,
            distanceM = Journey.FINAL_DISTANCE_M,
            maxReachedX = Journey.FINAL_DISTANCE_M,
            safeHavenDecisionPending = true,
            endlessMode = false
        )

        val endless = GameEngine.restore(RunCodec.decode(RunCodec.encode(decision))!!, 0f)
        assertTrue(endless.safeHavenDecisionPending)
        assertTrue(endless.continueEndless())
        assertTrue(endless.endlessMode)
        assertFalse(endless.safeHavenDecisionPending)
        val endlessRestored = GameEngine.restore(
            RunCodec.decode(RunCodec.encode(endless.snapshot()))!!,
            0f
        )
        assertTrue(endlessRestored.endlessMode)

        val finished = GameEngine.restore(decision, 0f)
        assertTrue(finished.finishJourney())
        assertEquals(GamePhase.GAME_OVER, finished.phase)
        assertEquals(EndReason.ARRIVED, finished.endReason)
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

    @Test fun relayNeedsLootedModuleAndScrapAndPersistsItsRestoredState() {
        val game = GameEngine(81L, 0f, DebugOptions(allComponents = true, fullFluids = true))
        val relay = WorldBuilding(
            id = 88L,
            type = BuildingType.GAS_STATION,
            localX = 4f,
            loot = mutableListOf(
                ItemStack(ItemCatalog.SCRAP_PILE.id, count = 12),
                ItemStack(ItemCatalog.RELAY_MODULE.id)
            ),
            landmark = true,
            relayIndex = 0
        )
        game.segment.buildings.clear()
        game.segment.buildings += relay
        game.car.x = game.segment.worldOrigin + 4f
        game.car.speed = 0f

        assertTrue(game.enterNearestBuilding())
        assertTrue(game.takeLoot(0))
        assertTrue(game.takeLoot(0))
        assertTrue(game.relayActivationReady(0))
        assertTrue(game.activateDepot(0))
        assertTrue(relay.relayRestored)
        assertEquals(6, game.scrap)
        assertEquals(0, game.relayModuleCount())

        val restored = GameEngine.restore(RunCodec.decode(RunCodec.encode(game.snapshot()))!!, 0f)
        assertTrue(restored.segment.buildings.single().relayRestored)
        assertEquals(0, restored.segment.buildings.single().relayIndex)
    }
}
