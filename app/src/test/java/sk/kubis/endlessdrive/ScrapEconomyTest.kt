package sk.kubis.endlessdrive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import sk.kubis.endlessdrive.domain.model.ComponentCondition
import sk.kubis.endlessdrive.domain.model.ComponentSlot
import sk.kubis.endlessdrive.domain.model.ItemCatalog
import sk.kubis.endlessdrive.domain.model.ItemStack
import sk.kubis.endlessdrive.game.GameEngine
import sk.kubis.endlessdrive.game.car.MountedPart
import sk.kubis.endlessdrive.game.save.RunCodec

class ScrapEconomyTest {

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

        val upgradeCost = engine.scrapUpgradeCost(ComponentSlot.ENGINE)!!
        assertTrue(engine.upgradeWithScrap(ComponentSlot.ENGINE))
        assertEquals(ItemCatalog.ENGINE_B.id, engine.car.parts[ComponentSlot.ENGINE]!!.defId)
        assertEquals(earned - repairCost - upgradeCost, engine.scrap)
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
}
