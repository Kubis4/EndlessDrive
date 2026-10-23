package sk.kubis.endlessdrive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import sk.kubis.endlessdrive.core.GameConfig
import sk.kubis.endlessdrive.domain.model.BuildingType
import sk.kubis.endlessdrive.domain.model.ComponentCondition
import sk.kubis.endlessdrive.domain.model.ComponentSlot
import sk.kubis.endlessdrive.domain.model.GamePhase
import sk.kubis.endlessdrive.domain.model.ItemCatalog
import sk.kubis.endlessdrive.game.GameEngine
import sk.kubis.endlessdrive.game.car.MountedPart
import sk.kubis.endlessdrive.game.world.WorldBuilding

class WorkshopAdTest {

    private fun parkedAtWorkshop(
        buildingId: Long = 0xA11L,
        seed: Long = 42L,
        distanceM: Float = 12_000f,
        enter: Boolean = true,
        scrap: Int = 400
    ): GameEngine {
        val engine = GameEngine(seed, 0f)
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
        fitCatalogParts(restored)
        restored.segment.buildings.clear()
        restored.segment.buildings += WorldBuilding(
            id = buildingId,
            type = BuildingType.AUTO_SHOP,
            localX = restored.car.x - restored.segment.worldOrigin
        )
        if (enter) {
            assertTrue(restored.enterNearestBuilding())
        }
        return restored
    }

    private fun fitCatalogParts(engine: GameEngine) {
        engine.car.parts[ComponentSlot.ENGINE] = MountedPart(
            ItemCatalog.ENGINE_A.id, ComponentCondition.USED, 0.75f
        )
        engine.car.parts[ComponentSlot.DRIVETRAIN] = MountedPart(
            ItemCatalog.DRIVE_FWD.id, ComponentCondition.USED, 0.75f
        )
        engine.car.parts[ComponentSlot.BATTERY] = MountedPart(
            ItemCatalog.BATTERY.id, ComponentCondition.USED, 0.75f
        )
        engine.car.parts[ComponentSlot.RADIATOR] = MountedPart(
            ItemCatalog.RADIATOR.id, ComponentCondition.USED, 0.75f
        )
        engine.car.parts[ComponentSlot.BRAKES] = MountedPart(
            ItemCatalog.BRAKES.id, ComponentCondition.USED, 0.75f
        )
        engine.car.parts[ComponentSlot.FUEL_TANK] = MountedPart(
            ItemCatalog.FUEL_TANK.id, ComponentCondition.USED, 0.75f
        )
    }

    private fun maxCatalogParts(engine: GameEngine) {
        engine.car.parts[ComponentSlot.ENGINE] = MountedPart(
            ItemCatalog.ENGINE_E.id, ComponentCondition.USED, 0.90f
        )
        engine.car.parts[ComponentSlot.DRIVETRAIN] = MountedPart(
            ItemCatalog.DRIVE_AWD.id, ComponentCondition.USED, 0.90f
        )
        engine.car.parts[ComponentSlot.BATTERY] = MountedPart(
            ItemCatalog.BATTERY_GOOD.id, ComponentCondition.USED, 0.90f
        )
        engine.car.parts[ComponentSlot.RADIATOR] = MountedPart(
            ItemCatalog.RADIATOR_HD.id, ComponentCondition.USED, 0.90f
        )
        engine.car.parts[ComponentSlot.BRAKES] = MountedPart(
            ItemCatalog.BRAKES_GOOD.id, ComponentCondition.USED, 0.90f
        )
        engine.car.parts[ComponentSlot.FUEL_TANK] = MountedPart(
            ItemCatalog.FUEL_TANK_LONG.id, ComponentCondition.USED, 0.90f
        )
    }

    @Test
    fun seededPickIsStableForTheSameShop() {
        val a = parkedAtWorkshop(buildingId = 0xA11L, seed = 42L)
        val b = parkedAtWorkshop(buildingId = 0xA11L, seed = 42L)
        val pick = a.workshopAdUpgradeSlot()
        assertNotNull(pick)
        assertEquals(pick, b.workshopAdUpgradeSlot())
        a.leaveBuilding()
        assertTrue(a.enterNearestBuilding())
        assertEquals(pick, a.workshopAdUpgradeSlot())
    }

    @Test
    fun differentShopsCanOfferDifferentSlots() {
        val ids = listOf(0xA11L, 0xB22L, 0xC33L, 0xD44L, 0xE55L, 0xF66L, 0x111L, 0x222L)
        val picks = ids.map { parkedAtWorkshop(buildingId = it, seed = 42L).workshopAdUpgradeSlot() }
        assertTrue(picks.all { it != null })
        assertTrue(
            "rôzne dielne musia vedieť vybrať iný slot",
            picks.distinct().size >= 2
        )
    }

    @Test
    fun rewardedAdUpgradesWithoutScrapOrHelpQuota() {
        val engine = parkedAtWorkshop(scrap = 0)
        val slot = engine.workshopAdUpgradeSlot()!!
        val target = engine.scrapUpgradeTarget(slot)!!
        val beforeDef = engine.car.parts[slot]!!.defId
        val packBefore = engine.inventory.slots.map { it?.defId to it?.count }
        val bootBefore = engine.boot.slots.map { it?.defId to it?.count }
        val lootBefore = engine.activeBuilding!!.loot.toList()
        val fuel = engine.car.fuel
        val oil = engine.car.oil
        val coolant = engine.car.coolant

        assertEquals(0, engine.helpAdsUsed)
        assertEquals(GameConfig.HELP_ADS_MAX, engine.helpAdsRemaining)
        assertTrue(engine.upgradeFromRewardedAd(slot))
        assertEquals(target.id, engine.car.parts[slot]!!.defId)
        assertNotEquals(beforeDef, engine.car.parts[slot]!!.defId)
        assertEquals(0, engine.scrap)
        assertEquals(0, engine.helpAdsUsed)
        assertEquals(GameConfig.HELP_ADS_MAX, engine.helpAdsRemaining)
        assertTrue(engine.message.contains("ad reward"))
        assertEquals(packBefore, engine.inventory.slots.map { it?.defId to it?.count })
        assertEquals(bootBefore, engine.boot.slots.map { it?.defId to it?.count })
        assertEquals(lootBefore, engine.activeBuilding!!.loot)
        assertEquals(fuel, engine.car.fuel, 0.001f)
        assertEquals(oil, engine.car.oil, 0.001f)
        assertEquals(coolant, engine.car.coolant, 0.001f)
    }

    @Test
    fun otherSlotsStayScrapOnly() {
        val engine = parkedAtWorkshop(scrap = 0)
        val offer = engine.workshopAdUpgradeSlot()!!
        val other = engine.scrapUpgradeSlots()
            .first { it != offer && engine.scrapUpgradeBlockReason(it) == null }
        val otherBefore = engine.car.parts[other]!!.defId
        assertFalse(engine.upgradeFromRewardedAd(other))
        assertEquals(otherBefore, engine.car.parts[other]!!.defId)
        assertEquals(0, engine.scrap)
        assertEquals(offer, engine.workshopAdUpgradeSlot())
        assertTrue(engine.message.contains("ad offer"))
    }

    @Test
    fun claimedAdOfferIsOncePerShop() {
        val engine = parkedAtWorkshop()
        val first = engine.workshopAdUpgradeSlot()!!
        assertTrue(engine.upgradeFromRewardedAd(first))
        assertNull(engine.workshopAdUpgradeSlot())
        assertFalse(engine.upgradeFromRewardedAd(first))
        engine.leaveBuilding()
        assertTrue(engine.enterNearestBuilding())
        assertNull("tá istá dielňa nesmie dať druhú reklamu", engine.workshopAdUpgradeSlot())
    }

    @Test
    fun nextWorkshopCanOfferAnotherAd() {
        val firstShop = parkedAtWorkshop(buildingId = 0xA11L)
        val slot = firstShop.workshopAdUpgradeSlot()!!
        assertTrue(firstShop.upgradeFromRewardedAd(slot))
        firstShop.leaveBuilding()
        firstShop.segment.buildings.clear()
        firstShop.segment.buildings += WorldBuilding(
            id = 0xB22L,
            type = BuildingType.AUTO_SHOP,
            localX = firstShop.car.x - firstShop.segment.worldOrigin
        )
        assertTrue(firstShop.enterNearestBuilding())
        assertNotNull(firstShop.workshopAdUpgradeSlot())
    }

    @Test
    fun emptyCatalogHasNoAdOffer() {
        val engine = parkedAtWorkshop()
        maxCatalogParts(engine)
        assertTrue(engine.scrapUpgradeSlots().isEmpty())
        assertNull(engine.workshopAdUpgradeSlot())
        assertFalse(engine.upgradeFromRewardedAd(ComponentSlot.ENGINE))
    }

    @Test
    fun adUpgradeNeedsTheEngineOff() {
        val engine = parkedAtWorkshop()
        val slot = engine.workshopAdUpgradeSlot()!!
        val before = engine.car.parts[slot]!!.defId
        engine.car.engineRunning = true
        assertFalse(engine.upgradeFromRewardedAd(slot))
        assertEquals(before, engine.car.parts[slot]!!.defId)
        assertTrue(engine.message.contains("engine off", ignoreCase = true))

        engine.stopEngine()
        assertTrue(engine.upgradeFromRewardedAd(slot))
        assertNotEquals(before, engine.car.parts[slot]!!.defId)
    }

    @Test
    fun paintShopDoesNotOfferWorkshopAdUpgrade() {
        val engine = GameEngine(42L, 0f)
        val snap = engine.snapshot()
        val restored = GameEngine.restore(
            snap.copy(
                distanceM = 12_000f,
                maxReachedX = 12_000f,
                phase = GamePhase.STOPPED,
                scrap = 0,
                car = snap.car.copy(engineRunning = false, speed = 0f)
            ),
            0f
        )
        restored.car.engineRunning = false
        restored.car.speed = 0f
        fitCatalogParts(restored)
        restored.segment.buildings.clear()
        restored.segment.buildings += WorldBuilding(
            id = 0xA11L,
            type = BuildingType.PAINT_SHOP,
            localX = restored.car.x - restored.segment.worldOrigin
        )
        assertTrue(restored.enterNearestBuilding())
        assertNull(restored.workshopAdUpgradeSlot())
        assertFalse(restored.upgradeFromRewardedAd(ComponentSlot.ENGINE))
        assertEquals(ItemCatalog.ENGINE_A.id, restored.car.parts[ComponentSlot.ENGINE]!!.defId)
        assertEquals(0, restored.scrap)
    }
}
