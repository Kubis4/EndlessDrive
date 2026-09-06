package sk.kubis.endlessdrive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import sk.kubis.endlessdrive.core.GameConfig
import sk.kubis.endlessdrive.core.SeededRandom
import sk.kubis.endlessdrive.domain.model.ComponentCondition
import sk.kubis.endlessdrive.domain.model.ComponentSlot
import sk.kubis.endlessdrive.domain.model.ItemCatalog
import sk.kubis.endlessdrive.domain.model.ItemStack
import sk.kubis.endlessdrive.domain.model.RoadFeature
import sk.kubis.endlessdrive.domain.model.RoadSurface
import sk.kubis.endlessdrive.game.GameEngine
import sk.kubis.endlessdrive.game.car.Car
import sk.kubis.endlessdrive.game.car.TireInjury
import sk.kubis.endlessdrive.game.car.TireSim

class TireSimTest {

    @Test
    fun sportOnDebrisIsMuchRiskierThanOffroad() {
        val sport = TireSim.risk(
            ItemCatalog.TIRE_SPORT, 1f, 20f, true,
            RoadSurface.GRAVEL, RoadFeature.BROKEN, 0.4f
        )
        val offroad = TireSim.risk(
            ItemCatalog.TIRE_OFFROAD, 1f, 20f, true,
            RoadSurface.GRAVEL, RoadFeature.BROKEN, 0.4f
        )
        assertTrue("šport $sport vs off-road $offroad", sport > offroad * 4f)
        assertTrue("poor musí byť ešte horší ako štandard",
            TireSim.hazardMul(ItemCatalog.TIRE_POOR) >
                TireSim.hazardMul(ItemCatalog.TIRE)
        )
    }

    @Test
    fun crawlingDoesNotPuncture() {
        assertEquals(
            0f,
            TireSim.risk(
                ItemCatalog.TIRE_POOR, 0.2f, 2f, true,
                RoadSurface.GRAVEL, RoadFeature.BROKEN, 1f
            ),
            0f
        )
        assertEquals(0f, TireSim.punctureChance(0.1f, 1f), 0f)
    }

    @Test
    fun sportShredsOnFastDebrisMoreThanOffroad() {
        val sport = TireSim.shredChance(ItemCatalog.TIRE_SPORT, 18f, true, false)
        val offroad = TireSim.shredChance(ItemCatalog.TIRE_OFFROAD, 18f, true, false)
        assertTrue("šport $sport vs off-road $offroad", sport > offroad * 4f)
        assertTrue(
            "už defektná guma sa trhá ľahšie",
            TireSim.shredChance(ItemCatalog.TIRE, 12f, false, true) >
                TireSim.shredChance(ItemCatalog.TIRE, 12f, false, false)
        )
    }

    @Test
    fun punctureAndShredCutGripAndNewTyreHealsTheInjury() {
        val car = Car().apply { installStarterKit(SeededRandom(8L)) }
        car.mount(
            ComponentSlot.TIRE_FRONT,
            ItemStack(ItemCatalog.TIRE_SPORT.id, ComponentCondition.NEW, 1f)
        )
        car.mount(
            ComponentSlot.TIRE_REAR,
            ItemStack(ItemCatalog.TIRE_SPORT.id, ComponentCondition.NEW, 1f)
        )
        val healthy = car.axleGrip(ComponentSlot.TIRE_FRONT)
        car.parts[ComponentSlot.TIRE_FRONT]!!.injury = TireInjury.PUNCTURED
        val punctured = car.axleGrip(ComponentSlot.TIRE_FRONT)
        assertTrue("defekt musí zhodiť grip ($punctured vs $healthy)", punctured < healthy * 0.35f)
        car.parts[ComponentSlot.TIRE_FRONT]!!.injury = TireInjury.SHREDDED
        val shredded = car.axleGrip(ComponentSlot.TIRE_FRONT)
        assertEquals(GameConfig.RIM_GRIP, shredded, 0.001f)
        assertTrue("ráfik musí byť horší ako defekt ($shredded vs $punctured)", shredded < punctured)
        assertTrue(car.hasShreddedTyre)

        car.mount(
            ComponentSlot.TIRE_FRONT,
            ItemStack(ItemCatalog.TIRE_OFFROAD.id, ComponentCondition.USED, 0.8f)
        )
        assertEquals(TireInjury.INFLATED, car.tireInjury(ComponentSlot.TIRE_FRONT))
        assertFalse(car.hasShreddedTyre)
        assertFalse(car.hasPuncturedTyre)
    }

    @Test
    fun punctureOnlyBelowTwentyPercentHealth() {
        val hot = TireSim.risk(
            ItemCatalog.TIRE_SPORT, 0.5f, 22f, true,
            RoadSurface.GRAVEL, RoadFeature.BROKEN, 0.5f
        )
        assertTrue("pri 50 % musí opotrebenie z rizika stále existovať ($hot)", hot > 0.5f)
        assertEquals(0f, TireSim.punctureChance(hot, 1f, 1f), 0f)
        assertEquals(0f, TireSim.punctureChance(hot, 1f, 0.50f), 0f)
        assertEquals(0f, TireSim.punctureChance(hot, 1f, 0.20f), 0f)
        assertTrue(
            "pod 20 % už defekt hroziť má",
            TireSim.punctureChance(hot, 1f, 0.19f) > 0f
        )
        assertFalse(TireSim.canPuncture(0.20f))
        assertTrue(TireSim.canPuncture(0.19f))
    }

    @Test
    fun scrapRepairHealsWearButLeavesPuncture() {
        val engine = GameEngine(seed = 11L, bestDistanceKm = 0f)
        val part = engine.car.parts[ComponentSlot.TIRE_FRONT]!!
        part.health = 0.14f
        part.injury = TireInjury.PUNCTURED
        engine.fundScrap()
        val before = part.health
        assertTrue(engine.repairWithScrap(ComponentSlot.TIRE_FRONT))
        assertEquals(TireInjury.PUNCTURED, part.injury)
        assertTrue("scrap má doplniť opotrebenie ($before → ${part.health})", part.health > before + 0.05f)
        assertTrue(engine.car.hasPuncturedTyre)
    }

    @Test
    fun punctureKitClearsFlatAndLeavesWear() {
        val engine = GameEngine(seed = 12L, bestDistanceKm = 0f)
        val part = engine.car.parts[ComponentSlot.TIRE_FRONT]!!
        part.health = 0.16f
        part.injury = TireInjury.PUNCTURED
        engine.inventory.clear()
        assertTrue(
            engine.inventory.add(
                ItemStack(ItemCatalog.PUNCTURE_KIT.id, ComponentCondition.NEW, 1f)
            )
        )
        val wear = part.health
        assertTrue(engine.repairPuncture(ComponentSlot.TIRE_FRONT))
        assertEquals(TireInjury.INFLATED, part.injury)
        assertEquals(wear, part.health, 0.001f)
        assertFalse(engine.car.hasPuncturedTyre)
        assertEquals(0, engine.punctureKitCount())
    }

    @Test
    fun scrapCanPatchWhenKitIsMissingButNotAShreddedRim() {
        val engine = GameEngine(seed = 13L, bestDistanceKm = 0f)
        val part = engine.car.parts[ComponentSlot.TIRE_FRONT]!!
        part.health = 0.11f
        part.injury = TireInjury.PUNCTURED
        engine.inventory.clear()
        engine.fundScrap()
        val scrapBefore = engine.scrap
        assertTrue(engine.repairPuncture(ComponentSlot.TIRE_FRONT))
        assertEquals(TireInjury.INFLATED, part.injury)
        assertEquals(scrapBefore - engine.scrapPatchCost(), engine.scrap)

        part.injury = TireInjury.SHREDDED
        val scrapHeld = engine.scrap
        assertFalse(engine.repairPuncture(ComponentSlot.TIRE_FRONT))
        assertEquals(TireInjury.SHREDDED, part.injury)
        assertEquals(scrapHeld, engine.scrap)
    }

    @Test
    fun swapAndScrapRepairRefreshLiveTyreState() {
        val engine = GameEngine(seed = 14L, bestDistanceKm = 0f)
        val front = engine.car.parts[ComponentSlot.TIRE_FRONT]!!
        val rear = engine.car.parts[ComponentSlot.TIRE_REAR]!!
        front.health = 0.15f
        front.injury = TireInjury.PUNCTURED
        rear.health = 0.40f
        rear.injury = TireInjury.INFLATED
        assertTrue(engine.swapTyres())
        assertEquals(TireInjury.INFLATED, engine.car.tireInjury(ComponentSlot.TIRE_FRONT))
        assertEquals(TireInjury.PUNCTURED, engine.car.tireInjury(ComponentSlot.TIRE_REAR))
        engine.fundScrap()
        assertTrue(engine.repairWithScrap(ComponentSlot.TIRE_REAR))
        assertEquals(TireInjury.PUNCTURED, engine.car.tireInjury(ComponentSlot.TIRE_REAR))
        assertTrue(engine.car.parts[ComponentSlot.TIRE_REAR]!!.health > 0.30f)
    }

    @Test
    fun scrapRepairAndPatchRefuseShreddedRim() {
        val engine = GameEngine(seed = 16L, bestDistanceKm = 0f)
        val part = engine.car.parts[ComponentSlot.TIRE_FRONT]!!
        part.health = 0.08f
        part.injury = TireInjury.SHREDDED
        engine.fundScrap()
        val scrapHeld = engine.scrap
        val wear = part.health
        assertEquals(null, engine.scrapRepairCost(ComponentSlot.TIRE_FRONT))
        assertFalse(engine.canPatchPuncture(ComponentSlot.TIRE_FRONT))
        assertFalse(engine.repairWithScrap(ComponentSlot.TIRE_FRONT))
        assertFalse(engine.repairPuncture(ComponentSlot.TIRE_FRONT))
        assertFalse(engine.repairSlot(ComponentSlot.TIRE_FRONT))
        assertEquals(TireInjury.SHREDDED, part.injury)
        assertEquals(wear, part.health, 0.001f)
        assertEquals(scrapHeld, engine.scrap)
    }

    @Test
    fun carPatchUsesBagKitWhileEngineIdlesParked() {
        val engine = GameEngine(seed = 17L, bestDistanceKm = 0f)
        engine.car.engineRunning = true
        engine.car.speed = 0f
        val part = engine.car.parts[ComponentSlot.TIRE_FRONT]!!
        part.health = 0.38f
        part.injury = TireInjury.PUNCTURED
        engine.inventory.clear()
        assertTrue(
            engine.inventory.add(
                ItemStack(ItemCatalog.PUNCTURE_KIT.id, ComponentCondition.NEW, 1f)
            )
        )
        assertTrue(engine.canPatchPuncture(ComponentSlot.TIRE_FRONT))
        assertTrue(engine.parkedForService())
        assertTrue(engine.repairPuncture(ComponentSlot.TIRE_FRONT))
        assertEquals(TireInjury.INFLATED, part.injury)
        assertEquals(0.38f, part.health, 0.001f)
        assertEquals(0, engine.punctureKitCount())
        assertTrue(engine.car.engineRunning)
    }

    @Test
    fun usingKitFromInventoryPatchesTheFlatTyre() {
        val engine = GameEngine(seed = 15L, bestDistanceKm = 0f)
        engine.car.parts[ComponentSlot.TIRE_REAR]!!.injury = TireInjury.PUNCTURED
        engine.car.parts[ComponentSlot.TIRE_REAR]!!.health = 0.18f
        engine.inventory.clear()
        assertTrue(
            engine.inventory.add(
                ItemStack(ItemCatalog.PUNCTURE_KIT.id, ComponentCondition.NEW, 1f)
            )
        )
        val idx = engine.inventory.slots.indexOfFirst { it?.defId == ItemCatalog.PUNCTURE_KIT.id }
        assertTrue(engine.useInventoryItem(idx))
        assertEquals(TireInjury.INFLATED, engine.car.tireInjury(ComponentSlot.TIRE_REAR))
        assertEquals(0.18f, engine.car.parts[ComponentSlot.TIRE_REAR]!!.health, 0.001f)
    }

    private fun GameEngine.fundScrap() {
        inventory.clear()
        val pile = ItemStack(
            ItemCatalog.SCRAP_PILE.id,
            ComponentCondition.NEW,
            1f,
            count = 20
        )
        assertTrue(inventory.add(pile))
        val idx = inventory.slots.indexOfFirst { it?.defId == ItemCatalog.SCRAP_PILE.id }
        assertTrue(scrapInventoryItem(idx))
        assertTrue(scrap >= 20)
    }
}
