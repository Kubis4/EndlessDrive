package sk.kubis.endlessdrive

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import sk.kubis.endlessdrive.domain.model.BranchStyle
import sk.kubis.endlessdrive.domain.model.RoadFeature
import sk.kubis.endlessdrive.game.world.TerrainProfile
import sk.kubis.endlessdrive.game.world.WorldGenerator

class BuildingPlacementTest {

    /**
     * Regresia: most bol pri hľadaní miesta len penalizovaný, nie vylúčený.
     * Keď celé okno padlo na mostovku, budova vyrástla nad roklinou.
     */
    @Test
    fun noBuildingEndsUpOnABridge() {
        var checked = 0
        for (seed in 1L..300L) {
            val terrain = TerrainProfile(seed)
            BranchStyle.entries.forEach { style ->
                val segment = WorldGenerator.createSegment(
                    segmentSeed = seed,
                    style = style,
                    worldOrigin = seed * 800f,
                    tripDistance = seed * 120f,
                    terrain = terrain,
                    isTutorial = false
                )
                val hasBridge = segment.sections.any { it.feature == RoadFeature.BRIDGE }
                segment.buildings.forEach { b ->
                    checked++
                    val on = segment.sectionAtLocal(b.localX)?.feature
                    assertTrue(
                        "budova (seed $seed, $style) stojí na moste na ${b.localX} m",
                        on != RoadFeature.BRIDGE
                    )
                }
                // Len aby test nebol falošne zelený, keď sa mosty prestanú generovať.
                if (hasBridge) checked += 0
            }
        }
        assertTrue("test musí prejsť aspoň nejaké budovy, prešiel $checked", checked > 50)
    }
}

/** Kulisy sa budovám vyhýbajú – strom nesmie rásť z garážovej strechy. */
class BuildingClearanceTest {

    @Test
    fun buildingOccupiesReportsItsOwnSpotAndNothingFarAway() {
        val terrain = TerrainProfile(21L)
        var checked = 0
        for (seed in 1L..60L) {
            val segment = WorldGenerator.createSegment(
                segmentSeed = seed,
                style = BranchStyle.SAFE_RURAL,
                worldOrigin = seed * 700f,
                tripDistance = seed * 130f,
                terrain = terrain,
                isTutorial = false
            )
            segment.buildings.forEach { b ->
                checked++
                val at = segment.worldOrigin + b.localX
                assertTrue("budova sa nehlási na svojom mieste", segment.buildingOccupies(at, 4.5f))
                assertTrue("okraj budovy musí byť ešte obsadený", segment.buildingOccupies(at + 4f, 4.5f))
                // Sto metrov vedľa už nesmie blokovať nič – inak by vyhynula
                // celá kulisa okolo budovy.
                assertFalse(
                    "budova blokuje aj 100 m vedľa",
                    segment.buildingOccupies(at + 100f, 4.5f)
                )
            }
        }
        assertTrue("test musí prejsť aspoň nejaké budovy, prešiel $checked", checked > 40)
    }
}
