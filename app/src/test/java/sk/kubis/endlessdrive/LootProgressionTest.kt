package sk.kubis.endlessdrive

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import sk.kubis.endlessdrive.core.SeededRandom
import sk.kubis.endlessdrive.domain.model.BranchStyle
import sk.kubis.endlessdrive.domain.model.BuildingType
import sk.kubis.endlessdrive.domain.model.ItemCatalog
import sk.kubis.endlessdrive.game.GameEngine
import sk.kubis.endlessdrive.game.world.LootGenerator
import sk.kubis.endlessdrive.game.world.TerrainProfile
import sk.kubis.endlessdrive.game.world.WorldGenerator

class LootProgressionTest {
    private val performanceEngines = setOf(
        ItemCatalog.ENGINE_B.id,
        ItemCatalog.ENGINE_E.id,
        ItemCatalog.ENGINE_C.id
    )

    @Test
    fun starterShedDoesNotSkipEngineProgression() {
        for (seed in 1L..100L) {
            val loot = GameEngine(seed, 0f).segment.buildings.first().loot
            assertFalse("seed $seed gives a performance engine at the start", loot.any { it.defId in performanceEngines })
            assertTrue("seed $seed should teach tyre repair", loot.any { it.defId == ItemCatalog.PUNCTURE_KIT.id })
        }
    }

    @Test
    fun performanceEnginesRespectDistanceGates() {
        val types = BuildingType.entries
        for (seed in 1L..600L) {
            val early = types.flatMap { type ->
                LootGenerator.generate(SeededRandom(seed * 31 + type.ordinal), type, 500f, BranchStyle.INDUSTRIAL)
            }
            assertFalse("seed $seed gives advanced engine in early loot", early.any { it.defId in performanceEngines })
            assertFalse("seed $seed gives diesel before a diesel engine can drop", early.any { it.defId == ItemCatalog.DIESEL_CAN.id })

            val beforeV6 = LootGenerator.generate(
                SeededRandom(seed), BuildingType.GARAGE, 24_999f, BranchStyle.INDUSTRIAL
            )
            assertFalse("seed $seed gives the V6 before 25 km", beforeV6.any { it.defId == ItemCatalog.ENGINE_E.id })
        }
    }

    @Test
    fun serviceShopsGenerateNoLoot() {
        for (seed in 1L..400L) {
            for (type in listOf(BuildingType.AUTO_SHOP, BuildingType.PAINT_SHOP)) {
                val loot = LootGenerator.generate(
                    SeededRandom(seed), type, 30_000f, BranchStyle.INDUSTRIAL
                )
                assertTrue("$type seed $seed musí byť bez lootu, má $loot", loot.isEmpty())
            }
        }
        val terrain = TerrainProfile(7L)
        var workshops = 0
        var paints = 0
        // Servis stojí na míľnikoch (~20 / 30 km), nie v každom segmente.
        for (seed in 1L..20L) {
            for (distance in listOf(20_000f, 30_000f, 40_000f, 50_000f)) {
                val segment = WorldGenerator.createSegment(
                    seed + distance.toLong(),
                    BranchStyle.INDUSTRIAL,
                    distance,
                    distance,
                    terrain,
                    false
                )
                segment.buildings.filter { it.type.isServiceShop }.forEach { shop ->
                    assertTrue(
                        "svetový servis seed $seed musí byť bez lootu, má ${shop.loot}",
                        shop.loot.isEmpty()
                    )
                    if (shop.type == BuildingType.AUTO_SHOP) workshops++ else paints++
                }
            }
        }
        assertTrue("test musí stretnúť aspoň jednu dielňu, bolo $workshops", workshops > 0)
        assertTrue("test musí stretnúť aspoň jednu lakovačku, bolo $paints", paints > 0)
    }

    @Test
    fun roadsideBuildingsAreNeverEmpty() {
        val types = listOf(
            BuildingType.HOUSE,
            BuildingType.GARAGE,
            BuildingType.GAS_STATION
        )
        for (seed in 1L..400L) {
            for (type in types) {
                val loot = LootGenerator.generate(
                    SeededRandom(seed * 17 + type.ordinal),
                    type,
                    8_000f,
                    BranchStyle.INDUSTRIAL
                )
                assertTrue(
                    "$type seed $seed spawned empty",
                    loot.isNotEmpty()
                )
            }
        }
    }
}
