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
import sk.kubis.endlessdrive.domain.model.VehiclePaint
import sk.kubis.endlessdrive.game.GameEngine
import sk.kubis.endlessdrive.game.world.WorldBuilding

class PaintShopTest {

    private fun parkedAtPaintShop(
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
            type = BuildingType.PAINT_SHOP,
            localX = restored.car.x - restored.segment.worldOrigin
        )
        if (enter) {
            assertTrue(restored.enterNearestBuilding())
        }
        return restored
    }

    private fun paintOtherThan(index: Int): VehiclePaint =
        VehiclePaint.entries.first { it.ordinal != index }

    @Test
    fun paintShopDoesNotSpawnBeforeUnlockDistance() {
        val terrain = sk.kubis.endlessdrive.game.world.TerrainProfile(19L)
        for (seed in 1L..40L) {
            for (style in listOf(
                sk.kubis.endlessdrive.domain.model.BranchStyle.DESERT,
                sk.kubis.endlessdrive.domain.model.BranchStyle.INDUSTRIAL,
                sk.kubis.endlessdrive.domain.model.BranchStyle.SAFE_RURAL
            )) {
                val distance = 4_000f + (seed % 5) * 1_000f
                val segment = sk.kubis.endlessdrive.game.world.WorldGenerator.createSegment(
                    seed, style, distance, 0f, terrain, false
                )
                assertTrue(
                    "seed $seed $style @ ${distance}m: lakovačka pred unlockom",
                    segment.buildings.none { it.type == BuildingType.PAINT_SHOP }
                )
                assertTrue(
                    "seed $seed $style @ ${distance}m: dielňa pred prvým míľnikom",
                    segment.buildings.none { it.type == BuildingType.AUTO_SHOP }
                )
            }
        }
    }

    @Test
    fun serviceShopsFollowSpacingMilestones() {
        val terrain = sk.kubis.endlessdrive.game.world.TerrainProfile(3L)
        val autoAt = mutableListOf<Float>()
        val paintAt = mutableListOf<Float>()
        var origin = 0f
        for (seed in 1L..80L) {
            val plan = sk.kubis.endlessdrive.game.world.WorldGenerator.planSegment(
                seed * 91L,
                sk.kubis.endlessdrive.domain.model.BranchStyle.INDUSTRIAL,
                origin
            )
            val segment = sk.kubis.endlessdrive.game.world.WorldGenerator.createSegment(
                plan, origin, origin, terrain, false
            )
            segment.buildings.forEach { b ->
                val world = origin + b.localX
                when (b.type) {
                    BuildingType.AUTO_SHOP -> autoAt += world
                    BuildingType.PAINT_SHOP -> paintAt += world
                    else -> Unit
                }
            }
            origin += plan.length
            if (origin > 95_000f) break
        }
        assertTrue("musí byť aspoň pár dielní, bolo ${autoAt.size}", autoAt.size >= 3)
        assertTrue("musí byť aspoň pár lakovačiek, bolo ${paintAt.size}", paintAt.size >= 2)
        autoAt.zipWithNext().forEach { (a, b) ->
            assertTrue(
                "dielne majú byť ~${GameConfig.AUTO_SHOP_SPACING_M}m od seba, $a → $b",
                b - a >= GameConfig.AUTO_SHOP_SPACING_M * 0.85f
            )
        }
        paintAt.zipWithNext().forEach { (a, b) ->
            assertTrue(
                "lakovačky majú byť ~${GameConfig.PAINT_SHOP_SPACING_M}m od seba, $a → $b",
                b - a >= GameConfig.PAINT_SHOP_SPACING_M * 0.85f
            )
        }
        assertTrue(
            "prvá dielňa okolo ${GameConfig.AUTO_SHOP_FIRST_M}m, bola ${autoAt.first()}",
            autoAt.first() in (GameConfig.AUTO_SHOP_FIRST_M - 800f)..(GameConfig.AUTO_SHOP_FIRST_M + 1200f)
        )
    }

    @Test
    fun paintShopIsItsOwnBuilding() {
        assertEquals("Paint shop", BuildingType.PAINT_SHOP.displayName)
        assertEquals("Workshop", BuildingType.AUTO_SHOP.displayName)
        assertTrue(BuildingType.PAINT_SHOP.isServiceShop)
        assertTrue(BuildingType.AUTO_SHOP.isServiceShop)
    }

    @Test
    fun paintShopStaysLockedBeforeTenKmEvenInsideTheShop() {
        val engine = parkedAtPaintShop(9_999f, enter = true, scrap = 80)
        assertTrue(engine.atPaintShop)
        assertFalse(engine.atAutoShop)
        assertFalse(engine.paintShopAvailable)
        val before = engine.car.bodyPaintIndex
        assertFalse(engine.paintBody(paintOtherThan(before)))
        assertEquals(before, engine.car.bodyPaintIndex)
        assertEquals("Paint service after 10 km", engine.message)
    }

    @Test
    fun paintShopOpensAfterTenKmWhenParkedAtTheShop() {
        val engine = parkedAtPaintShop(GameConfig.PAINT_SHOP_UNLOCK_M)
        assertEquals(GamePhase.STOPPED, engine.phase)
        assertTrue(engine.atPaintShop)
        assertTrue(engine.paintShopAvailable)
        assertEquals(BuildingType.PAINT_SHOP, engine.buildingNear()?.type)
    }

    @Test
    fun paintShopOpensAfterTenKmOnceInsideTheShop() {
        val engine = parkedAtPaintShop(10_500f, enter = true)
        assertEquals(BuildingType.PAINT_SHOP, engine.activeBuilding?.type)
        assertTrue(engine.paintShopAvailable)
    }

    @Test
    fun paintBodyChangesShellAndFittedPanels() {
        val engine = parkedAtPaintShop(12_000f, scrap = 150)
        engine.car.mount(
            ComponentSlot.DOOR_FRONT,
            ItemStack(ItemCatalog.DOOR_FRONT.id, paintIndex = 0)
        )
        engine.car.mount(
            ComponentSlot.HOOD,
            ItemStack(ItemCatalog.HOOD.id, paintIndex = 1)
        )
        engine.car.mount(
            ComponentSlot.HEADLIGHT,
            ItemStack(ItemCatalog.HEADLIGHT.id)
        )
        engine.car.bodyPaintIndex = 2
        val cost = engine.paintShopCost
        val target = paintOtherThan(2)

        assertTrue(engine.paintBody(target))
        assertEquals(target.ordinal, engine.car.bodyPaintIndex)
        assertEquals(target.ordinal, engine.car.parts[ComponentSlot.DOOR_FRONT]!!.paintIndex)
        assertEquals(target.ordinal, engine.car.parts[ComponentSlot.HOOD]!!.paintIndex)
        assertEquals(-1, engine.car.parts[ComponentSlot.HEADLIGHT]!!.paintIndex)
        assertEquals(150 - cost, engine.scrap)
        assertTrue(engine.message.contains("Repainted the car"))
    }

    @Test
    fun paintShopCostsOneHundredScrapAndClearsBodyDirt() {
        val engine = parkedAtPaintShop(GameConfig.PAINT_SHOP_UNLOCK_M, scrap = 100)
        engine.car.bodyDirt = 0.62f
        engine.car.bodyRust = 0.48f
        assertEquals(GameConfig.PAINT_SHOP_COST, engine.paintShopCost)
        val target = paintOtherThan(engine.car.bodyPaintIndex)
        assertTrue(engine.paintBody(target))
        assertEquals(0, engine.scrap)
        assertEquals(0f, engine.car.bodyDirt, 0.001f)
        assertEquals(0f, engine.car.bodyRust, 0.001f)
        assertEquals(1f, engine.car.bodyCondition, 0.001f)
    }

    @Test
    fun paidPaintRefusesWhenScrapIsShort() {
        val engine = parkedAtPaintShop(10_000f, scrap = 0)
        val before = engine.car.bodyPaintIndex
        val target = paintOtherThan(before)
        assertFalse(engine.paintBody(target))
        assertEquals(before, engine.car.bodyPaintIndex)
        assertEquals(0, engine.scrap)
        assertTrue(engine.message.contains("Need"))
    }

    @Test
    fun rewardedAdPaintsForFreeWhenBroke() {
        val engine = parkedAtPaintShop(10_000f, scrap = 0)
        val before = engine.car.bodyPaintIndex
        val target = paintOtherThan(before)
        assertTrue(engine.paintBodyFromRewardedAd(target))
        assertEquals(target.ordinal, engine.car.bodyPaintIndex)
        assertEquals(0, engine.scrap)
        assertTrue(engine.message.contains("ad reward"))
        assertFalse(engine.paintAdAvailable())
        val other = paintOtherThan(engine.car.bodyPaintIndex)
        assertFalse(engine.paintBodyFromRewardedAd(other))
        assertEquals(target.ordinal, engine.car.bodyPaintIndex)
        assertTrue(engine.message.contains("already used"))
    }

    @Test
    fun rewardedPaintDoesNotConsumeHelpAdsQuota() {
        val engine = parkedAtPaintShop(10_000f, scrap = 0)
        val before = engine.car.bodyPaintIndex
        val target = paintOtherThan(before)
        assertEquals(0, engine.helpAdsUsed)
        assertEquals(GameConfig.HELP_ADS_MAX, engine.helpAdsRemaining)
        assertTrue(engine.paintBodyFromRewardedAd(target))
        assertEquals(0, engine.helpAdsUsed)
        assertEquals(GameConfig.HELP_ADS_MAX, engine.helpAdsRemaining)
    }

    @Test
    fun paintAwayFromTheShopIsBlocked() {
        val engine = GameEngine.restore(
            GameEngine(7L, 0f).snapshot().copy(scrap = 80),
            0f
        )
        val before = engine.car.bodyPaintIndex
        assertFalse(engine.atPaintShop)
        assertFalse(engine.paintShopAvailable)
        assertFalse(engine.paintBody(paintOtherThan(before)))
        assertEquals(before, engine.car.bodyPaintIndex)
        assertEquals("Take the car to a paint shop", engine.message)
    }

    @Test
    fun paintNeedsTheEngineOffThenWorks() {
        val engine = parkedAtPaintShop(12_000f, enter = true, scrap = 100)
        val before = engine.car.bodyPaintIndex
        val target = paintOtherThan(before)
        engine.car.engineRunning = true
        assertEquals(
            "Switch the engine off to respray.",
            engine.workshopServiceBanner()
        )
        assertFalse(engine.paintBody(target))
        assertEquals(before, engine.car.bodyPaintIndex)
        assertTrue(engine.message.contains("engine off", ignoreCase = true))

        engine.stopEngine()
        assertEquals(null, engine.workshopServiceBanner())
        assertTrue(engine.paintBody(target))
        assertEquals(target.ordinal, engine.car.bodyPaintIndex)
    }

    @Test
    fun workshopDoesNotPaintAndPaintShopDoesNotUpgrade() {
        val paint = parkedAtPaintShop(12_000f, enter = true, scrap = 400)
        paint.car.parts[ComponentSlot.ENGINE] = sk.kubis.endlessdrive.game.car.MountedPart(
            ItemCatalog.ENGINE_A.id,
            ComponentCondition.USED,
            0.75f
        )
        assertFalse(paint.upgradeWithScrap(ComponentSlot.ENGINE))
        assertEquals("Take the car to a workshop before upgrading", paint.message)

        val workshop = GameEngine.restore(
            GameEngine(42L, 0f).snapshot().copy(
                distanceM = 12_000f,
                maxReachedX = 12_000f,
                phase = GamePhase.STOPPED,
                scrap = 80,
                car = GameEngine(42L, 0f).snapshot().car.copy(engineRunning = false, speed = 0f)
            ),
            0f
        )
        workshop.car.engineRunning = false
        workshop.car.speed = 0f
        workshop.segment.buildings.clear()
        workshop.segment.buildings += WorldBuilding(
            id = 0xA12L,
            type = BuildingType.AUTO_SHOP,
            localX = workshop.car.x - workshop.segment.worldOrigin
        )
        assertTrue(workshop.enterNearestBuilding())
        val before = workshop.car.bodyPaintIndex
        assertFalse(workshop.paintBody(paintOtherThan(before)))
        assertEquals("Take the car to a paint shop", workshop.message)
        assertEquals(before, workshop.car.bodyPaintIndex)
    }

    @Test
    fun savedWorkshopLootIsStrippedOnRestore() {
        val engine = parkedAtPaintShop(12_000f)
        engine.segment.buildings.clear()
        engine.segment.buildings += WorldBuilding(
            id = 0xB00L,
            type = BuildingType.AUTO_SHOP,
            localX = engine.car.x - engine.segment.worldOrigin,
            loot = mutableListOf(
                ItemStack(ItemCatalog.SCRAP_PILE.id, ComponentCondition.NEW, 1f, count = 4)
            )
        )
        val restored = GameEngine.restore(engine.snapshot(), 0f)
        val shop = restored.segment.buildings.single { it.type == BuildingType.AUTO_SHOP }
        assertTrue("starý loot v dielni sa má zhodiť", shop.loot.isEmpty())
    }
}
