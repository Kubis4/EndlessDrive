package sk.kubis.endlessdrive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import sk.kubis.endlessdrive.core.GameConfig
import sk.kubis.endlessdrive.domain.model.BuildingType
import sk.kubis.endlessdrive.domain.model.ComponentCondition
import sk.kubis.endlessdrive.domain.model.ComponentSlot
import sk.kubis.endlessdrive.domain.model.GamePhase
import sk.kubis.endlessdrive.domain.model.ItemCatalog
import sk.kubis.endlessdrive.domain.model.ItemStack
import sk.kubis.endlessdrive.game.GameEngine
import sk.kubis.endlessdrive.game.car.MountedPart
import sk.kubis.endlessdrive.game.save.RunCodec
import sk.kubis.endlessdrive.game.world.WorldBuilding
import kotlin.math.ceil

class ScrapEconomyTest {

    private fun parkedAtWorkshop(
        distanceM: Float,
        enter: Boolean = false,
        scrap: Int = 0
    ): GameEngine {
        val engine = GameEngine(42L, 0f)
        val snap = engine.snapshot()
        val restored = GameEngine.restore(
            snap.copy(
                distanceM = distanceM,
                maxReachedX = maxOf(snap.maxReachedX, distanceM),
                phase = GamePhase.STOPPED,
                scrap = scrap,
                car = snap.car.copy(engineRunning = false, speed = 0f)
            ),
            0f
        )
        restored.car.engineRunning = false
        restored.car.speed = 0f
        restored.segment.buildings.clear()
        restored.segment.buildings += WorldBuilding(
            id = 0xA11L,
            type = BuildingType.AUTO_SHOP,
            localX = restored.car.x - restored.segment.worldOrigin
        )
        if (enter) {
            assertTrue(restored.enterNearestBuilding())
        }
        return restored
    }

    @Test
    fun betterPartQualityProducesMoreScrap() {
        val def = ItemCatalog.ENGINE_C.id
        val critical = ItemStack(def, ComponentCondition.CRITICAL, 0.20f).scrapValue
        val damaged = ItemStack(def, ComponentCondition.DAMAGED, 0.45f).scrapValue
        val used = ItemStack(def, ComponentCondition.USED, 0.75f).scrapValue
        val new = ItemStack(def, ComponentCondition.NEW, 1f).scrapValue

        assertTrue("damaged=$damaged musí byť viac než critical=$critical", damaged > critical)
        assertTrue("used=$used musí byť viac než damaged=$damaged", used > damaged)
        assertTrue("new=$new musí byť viac než used=$used", new > used)
    }

    @Test
    fun scrapCostsUseRepairAndUpgradeMultipliers() {
        val engine = GameEngine(501L, 0f)
        engine.car.parts[ComponentSlot.ENGINE] = MountedPart(
            ItemCatalog.ENGINE_A.id,
            ComponentCondition.DAMAGED,
            0.40f
        )
        val repairBase = ceil((ItemCatalog.ENGINE_A.baseValue * 0.045f).coerceAtLeast(2f)).toInt()
            .coerceAtLeast(1)
        assertEquals(
            ceil(repairBase * GameConfig.SCRAP_REPAIR_COST_MUL).toInt(),
            engine.scrapRepairCost(ComponentSlot.ENGINE)
        )
        assertEquals(60, engine.scrapRepairCost(ComponentSlot.ENGINE))

        val difference = ItemCatalog.ENGINE_B.baseValue - ItemCatalog.ENGINE_A.baseValue
        val upgradeBase = ceil(difference * 0.72f + ItemCatalog.ENGINE_B.baseValue * 0.12f)
            .toInt()
            .coerceAtLeast(18)
        assertEquals(
            ceil(upgradeBase * GameConfig.SCRAP_UPGRADE_COST_MUL).toInt(),
            engine.scrapUpgradeCost(ComponentSlot.ENGINE)
        )
        assertEquals(297, engine.scrapUpgradeCost(ComponentSlot.ENGINE))
    }

    @Test
    fun scrappingFundsRepairAndPetrolUpgrade() {
        val engine = GameEngine(501L, 0f)
        engine.car.parts[ComponentSlot.ENGINE] = MountedPart(
            ItemCatalog.ENGINE_A.id,
            ComponentCondition.DAMAGED,
            0.40f
        )
        val donor = ItemStack(ItemCatalog.ENGINE_C.id, ComponentCondition.NEW, 1f)
        engine.inventory.slots[0] = donor

        assertTrue(engine.scrapInventoryItem(0))
        val earned = donor.scrapValue
        assertEquals(earned, engine.scrap)

        val repairCost = engine.scrapRepairCost(ComponentSlot.ENGINE)!!
        assertTrue(engine.repairWithScrap(ComponentSlot.ENGINE))
        assertEquals(0.60f, engine.car.parts[ComponentSlot.ENGINE]!!.health, 0.001f)
        assertEquals(earned - repairCost, engine.scrap)
    }

    @Test
    fun petrolEngineUpgradesFromCatalogAtWorkshop() {
        val engine = parkedAtWorkshop(8_000f, enter = true, scrap = 400)
        engine.car.parts[ComponentSlot.ENGINE] = MountedPart(
            ItemCatalog.ENGINE_A.id,
            ComponentCondition.USED,
            0.75f
        )
        assertEquals(ItemCatalog.ENGINE_B.id, engine.scrapUpgradeTarget(ComponentSlot.ENGINE)?.id)
        assertTrue(engine.scrapUpgradeSlots().contains(ComponentSlot.ENGINE))
        assertEquals(null, engine.scrapUpgradeBlockReason(ComponentSlot.ENGINE))

        val upgradeCost = engine.scrapUpgradeCost(ComponentSlot.ENGINE)!!
        val lootBefore = engine.activeBuilding!!.loot.toList()
        assertTrue(engine.upgradeWithScrap(ComponentSlot.ENGINE))
        assertEquals(ItemCatalog.ENGINE_B.id, engine.car.parts[ComponentSlot.ENGINE]!!.defId)
        assertEquals(400 - upgradeCost, engine.scrap)
        assertEquals(lootBefore, engine.activeBuilding!!.loot)
    }

    @Test
    fun dieselUpgradeStaysDieselAndScrapSurvivesSave() {
        val engine = GameEngine(502L, 0f)
        engine.car.parts[ComponentSlot.ENGINE] = MountedPart(
            ItemCatalog.ENGINE_D.id,
            ComponentCondition.USED,
            0.75f
        )
        assertEquals(ItemCatalog.ENGINE_C.id, engine.scrapUpgradeTarget(ComponentSlot.ENGINE)?.id)

        val donor = ItemStack(ItemCatalog.ENGINE_C.id, ComponentCondition.NEW, 1f)
        engine.inventory.slots[0] = donor
        assertTrue(engine.scrapInventoryItem(0))

        val restored = GameEngine.restore(
            RunCodec.decode(RunCodec.encode(engine.snapshot()))!!,
            0f
        )
        assertEquals(donor.scrapValue, restored.scrap)
    }

    @Test
    fun repairExplainsParkingAndEngineRequirements() {
        val engine = GameEngine(503L, 0f)
        engine.car.parts[ComponentSlot.ENGINE] = MountedPart(
            ItemCatalog.ENGINE_A.id,
            ComponentCondition.DAMAGED,
            0.40f
        )
        engine.inventory.slots[0] = ItemStack(
            ItemCatalog.ENGINE_C.id,
            ComponentCondition.NEW,
            1f
        )
        assertTrue(engine.scrapInventoryItem(0))
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
        assertTrue(engine.tryStartEngine())

        assertFalse(engine.repairWithScrap(ComponentSlot.ENGINE))
        assertTrue(engine.message.contains("Park", ignoreCase = true))

        engine.car.speed = 0f
        assertTrue(engine.requestStop())
        assertFalse(engine.repairWithScrap(ComponentSlot.ENGINE))
        assertTrue(engine.message.contains("engine off", ignoreCase = true))

        engine.stopEngine()
        assertTrue(
            "po zaparkovaní a vypnutí motora má oprava fungovať aj bez autoservisu",
            engine.repairWithScrap(ComponentSlot.ENGINE)
        )
    }

    @Test
    fun upgradeNeedsTheEngineOffThenWorks() {
        val engine = parkedAtWorkshop(8_000f, enter = true, scrap = 400)
        engine.car.parts[ComponentSlot.ENGINE] = MountedPart(
            ItemCatalog.ENGINE_A.id,
            ComponentCondition.USED,
            0.75f
        )
        engine.car.engineRunning = true
        assertEquals(
            "Switch the engine off to upgrade.",
            engine.workshopServiceBanner()
        )
        assertFalse(engine.upgradeWithScrap(ComponentSlot.ENGINE))
        assertTrue(engine.message.contains("engine off", ignoreCase = true))
        assertEquals(ItemCatalog.ENGINE_A.id, engine.car.parts[ComponentSlot.ENGINE]!!.defId)

        engine.stopEngine()
        assertEquals(null, engine.workshopServiceBanner())
        assertTrue(engine.upgradeWithScrap(ComponentSlot.ENGINE))
        assertEquals(ItemCatalog.ENGINE_B.id, engine.car.parts[ComponentSlot.ENGINE]!!.defId)
    }
}
