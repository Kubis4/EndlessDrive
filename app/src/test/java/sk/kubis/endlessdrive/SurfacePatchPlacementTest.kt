package sk.kubis.endlessdrive

import org.junit.Assert.assertTrue
import org.junit.Test
import sk.kubis.endlessdrive.domain.model.BranchStyle
import sk.kubis.endlessdrive.domain.model.RoadFeature
import sk.kubis.endlessdrive.domain.model.RoadSurface
import sk.kubis.endlessdrive.game.world.TerrainProfile
import sk.kubis.endlessdrive.game.world.WorldGenerator

class SurfacePatchPlacementTest {

    private fun eachPatch(block: (String, sk.kubis.endlessdrive.game.world.RoadSegment, sk.kubis.endlessdrive.game.world.SurfacePatch) -> Unit) {
        for (seed in 1L..250L) {
            val terrain = TerrainProfile(seed)
            BranchStyle.entries.forEach { style ->
                val segment = WorldGenerator.createSegment(
                    segmentSeed = seed,
                    style = style,
                    worldOrigin = seed * 800f,
                    tripDistance = seed * 140f,
                    terrain = terrain,
                    isTutorial = false
                )
                segment.patches.forEach { block("seed $seed, $style", segment, it) }
            }
        }
    }

    /**
     * Regresia: naplavenina sa testovala len na svojom začiatku, takže mláka
     * dlhá 7–25 m mohla začať pred mostom a tiecť ďalej po mostovke.
     */
    @Test
    fun noPatchReachesOntoABridge() {
        var checked = 0
        eachPatch { where, segment, patch ->
            checked++
            val touched = segment.sections.filter {
                it.start < patch.end && it.end > patch.start
            }
            assertTrue(
                "$where: naplavenina ${patch.start}–${patch.end} m zasahuje na most",
                touched.none { it.feature == RoadFeature.BRIDGE }
            )
        }
        assertTrue("test musí prejsť aspoň nejaké naplaveniny, prešiel $checked", checked > 100)
    }

    /** Voda stojí len tam, kam steká – v rokline alebo na rovine. */
    @Test
    fun waterOnlyPoolsInRavinesAndOnTheFlat() {
        var water = 0
        eachPatch { where, segment, patch ->
            if (patch.surface != RoadSurface.WATER) return@eachPatch
            water++
            val features = segment.sections
                .filter { it.start < patch.end && it.end > patch.start }
                .map { it.feature }
            assertTrue(
                "$where: voda na ${patch.start}–${patch.end} m leží na $features",
                features.all { it == RoadFeature.RAVINE || it == RoadFeature.STRAIGHT }
            )
        }
        assertTrue("test musí nájsť aspoň nejakú vodu, našiel $water", water > 20)
    }

    @Test
    fun surfacePatchesAreLongEnoughToReadAndFeel() {
        var sand = 0
        var mud = 0
        eachPatch { where, _, patch ->
            when (patch.surface) {
                RoadSurface.SAND -> {
                    sand++
                    assertTrue("$where: piesok je príliš krátky (${patch.length} m)", patch.length >= 36f)
                }
                RoadSurface.MUD -> {
                    mud++
                    assertTrue("$where: bahno je príliš krátke (${patch.length} m)", patch.length >= 32f)
                }
                RoadSurface.WATER -> assertTrue("$where: voda je príliš krátka", patch.length >= 32f)
                RoadSurface.GRAVEL -> assertTrue("$where: štrk je príliš krátky", patch.length >= 24f)
                RoadSurface.ICE, RoadSurface.SLUSH ->
                    assertTrue("$where: zimný úsek je príliš krátky", patch.length >= 28f)
                RoadSurface.ASPHALT -> Unit
            }
        }
        assertTrue("test musí nájsť piesok", sand > 20)
        assertTrue("test musí nájsť bahno", mud > 20)
    }
}
