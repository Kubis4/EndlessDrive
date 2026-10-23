package sk.kubis.endlessdrive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import sk.kubis.endlessdrive.core.GameConfig
import sk.kubis.endlessdrive.domain.model.RoadFeature
import sk.kubis.endlessdrive.domain.model.RoadPaving
import sk.kubis.endlessdrive.domain.model.RoadSurface
import sk.kubis.endlessdrive.game.world.RescueSpotFinder
import kotlin.math.abs

class RescueSpotFinderTest {

    @Test
    fun prefersNearestFlatAsphaltAheadOverFiveMetresOnTheSameMudHill() {
        val origin = 100f
        val samples = buildList {
            // Prudký ílovitý kopec pod autom a 5.5 m pred ním.
            for (x in 80..160 step 4) {
                add(
                    sample(
                        x.toFloat(),
                        slope = 0.28f,
                        surface = RoadSurface.MUD
                    )
                )
            }
            // Rovina s asfaltom ~80 m dopredu.
            for (x in 180..240 step 4) {
                add(sample(x.toFloat(), slope = 0.02f, surface = RoadSurface.ASPHALT))
            }
        }
        val picked = RescueSpotFinder.pickWorldX(origin, samples)
        assertTrue("má ísť na rovinu, nie 5.5 m dopredu ($picked)", picked >= 180f)
        assertTrue(picked <= 220f)
        assertTrue(abs(picked - (origin + GameConfig.TIRE_RESCUE_SPAWN_AHEAD_M)) > 20f)
    }

    @Test
    fun rejectsCrestThatIsLocallyFlatButStillOnTheHill() {
        val origin = 100f
        val samples = buildList {
            for (x in 80..200 step 4) {
                val t = (x - 140f) / 40f
                val slope = if (t in -1f..1f) {
                    -0.27f * kotlin.math.sin(t * Math.PI.toFloat())
                } else {
                    0.02f
                }
                add(sample(x.toFloat(), slope = slope))
            }
        }
        val picked = RescueSpotFinder.pickWorldX(origin, samples)
        // Hrebeň okolo 140 má |sklon| ≈ 0, ale susedia sú prudkí.
        assertTrue("nesmie zostať na hrebeni ($picked)", abs(picked - 140f) > 12f)
        val at = samples.minBy { abs(it.worldX - picked) }
        assertTrue(
            "vybraný bod má mať malý sklon v okolí",
            RescueSpotFinder.neighborhoodSlope(at, samples) <= GameConfig.TIRE_RESCUE_FLAT_SLOPE + 0.01f
        )
    }

    @Test
    fun fallbackPicksLocalSlopeMinimumWhenNothingIsTrulyFlat() {
        val origin = 50f
        val samples = listOf(
            sample(50f, slope = 0.40f, surface = RoadSurface.MUD),
            sample(54f, slope = 0.38f, surface = RoadSurface.MUD),
            sample(70f, slope = 0.22f, surface = RoadSurface.MUD),
            sample(90f, slope = 0.16f, surface = RoadSurface.ASPHALT),
            sample(94f, slope = 0.18f, surface = RoadSurface.ASPHALT)
        )
        val picked = RescueSpotFinder.pickWorldX(origin, samples)
        assertEquals(90f, picked, 0.01f)
    }

    @Test
    fun mudShopIsNotTowableEvenWhenSlopeIsMild() {
        val sample = sample(40f, slope = 0.04f, surface = RoadSurface.MUD)
        assertFalse(RescueSpotFinder.isTowableGround(sample))
        val asphalt = sample(40f, slope = 0.04f, surface = RoadSurface.ASPHALT)
        assertTrue(RescueSpotFinder.isTowableGround(asphalt))
        val steep = sample(40f, slope = 0.25f, surface = RoadSurface.ASPHALT)
        assertFalse(RescueSpotFinder.isTowableGround(steep))
    }

    private fun sample(
        worldX: Float,
        slope: Float,
        surface: RoadSurface = RoadSurface.ASPHALT,
        paving: RoadPaving = RoadPaving.ASPHALT,
        feature: RoadFeature = RoadFeature.STRAIGHT,
        occupied: Boolean = false
    ) = RescueSpotFinder.Sample(worldX, slope, surface, paving, feature, occupied)
}
