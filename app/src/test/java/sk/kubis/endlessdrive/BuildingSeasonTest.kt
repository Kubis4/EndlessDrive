package sk.kubis.endlessdrive

import org.junit.Assert.assertEquals
import org.junit.Test
import sk.kubis.endlessdrive.domain.model.BiomeType
import sk.kubis.endlessdrive.ui.game.BuildingSeason

class BuildingSeasonTest {
    @Test fun snowOverridesTheAutumnBackdrop() {
        assertEquals(BuildingSeason.AUTUMN, BuildingSeason.forEnvironment(BiomeType.FOREST_ALIVE, 0f))
        assertEquals(BuildingSeason.WINTER, BuildingSeason.forEnvironment(BiomeType.FOREST_ALIVE, 1f))
        assertEquals(BuildingSeason.WINTER, BuildingSeason.forEnvironment(BiomeType.RURAL, 0.2f))
        assertEquals(BuildingSeason.WINTER, BuildingSeason.forEnvironment(BiomeType.ALPINE, 0f))
    }

    @Test fun nonSeasonalBiomesKeepTheirOriginalArtwork() {
        for (biome in BiomeType.entries) {
            if (biome != BiomeType.ALPINE && biome != BiomeType.FOREST_ALIVE) {
                assertEquals(BuildingSeason.DEFAULT, BuildingSeason.forEnvironment(biome, 0f))
            }
        }
    }
}
