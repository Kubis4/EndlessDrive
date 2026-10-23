package sk.kubis.endlessdrive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import sk.kubis.endlessdrive.domain.model.BiomeType
import sk.kubis.endlessdrive.domain.model.BuildingType
import sk.kubis.endlessdrive.domain.model.BranchStyle
import sk.kubis.endlessdrive.domain.model.RoadFeature
import sk.kubis.endlessdrive.domain.model.RoadPaving
import sk.kubis.endlessdrive.game.world.TerrainProfile
import sk.kubis.endlessdrive.game.world.WorldGenerator

class WorldGenerationVisualRulesTest {
    @Test
    fun bridgesStayShortAndNeverReceiveRoadsideWrecks() {
        for (seed in 1L..160L) {
            for (style in BranchStyle.entries) {
                val segment = WorldGenerator.createSegment(
                    segmentSeed = seed * 31L + style.ordinal,
                    style = style,
                    worldOrigin = 20_000f,
                    tripDistance = 20_000f,
                    terrain = TerrainProfile(seed),
                    isTutorial = false
                )
                segment.sections
                    .filter { it.feature == RoadFeature.BRIDGE }
                    .forEach { assertTrue("bridge too long: ${it.length}", it.length <= 72.01f) }
                segment.buildings
                    .filter { it.type == BuildingType.WRECK }
                    .forEach { building ->
                        assertTrue(
                            "wreck spawned on a bridge",
                            segment.sectionAtLocal(building.localX)?.feature != RoadFeature.BRIDGE
                        )
                    }
            }
        }
    }

    @Test
    fun snowPavingDrawsAlpineSceneryEvenOnMarshStyle() {
        val base = WorldGenerator.planSegment(77L, BranchStyle.FOREST, 18_000f)
        val segment = WorldGenerator.createSegment(
            plan = base.copy(paving = RoadPaving.PACKED_SNOW),
            worldOrigin = 500f,
            tripDistance = 20_000f,
            terrain = TerrainProfile(77L),
            isTutorial = false
        )
        assertEquals(BranchStyle.FOREST, segment.style)
        assertEquals(BiomeType.ALPINE, segment.visualBiome)
        assertEquals(BiomeType.ALPINE, segment.biomeBlendAtWorld(segment.worldOrigin).from)
    }
}
