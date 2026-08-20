package sk.kubis.endlessdrive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import sk.kubis.endlessdrive.domain.model.BranchStyle
import sk.kubis.endlessdrive.game.world.TestTrackProfile

/** Trať na ladenie pruženia: musí mať čo skúšať a nesmie mať zráz. */
class TestTrackProfileTest {

    private val track = TestTrackProfile()
    private fun h(x: Float) = track.heightAt(x, BranchStyle.SAFE_RURAL, 1f)

    /** Rovina by nič neotestovala – prekážky musia byť poriadne. */
    @Test
    fun theTrackActuallyHasObstacles() {
        var lo = Float.MAX_VALUE
        var hi = -Float.MAX_VALUE
        var x = 0f
        while (x < TestTrackProfile.LAP) {
            val y = h(x)
            if (y < lo) lo = y
            if (y > hi) hi = y
            x += 0.25f
        }
        assertTrue("trať musí poriadne stúpať, má len ${hi - lo} m", hi - lo > 5f)
        assertTrue("musí mať aj priehlbiny pod úroveň cesty", lo < -0.5f)
    }

    /**
     * Zráz by auto zastavil alebo vystrelil. Každý hrbol má na okrajoch
     * nulovú výšku aj sklon, takže sklon musí ostať v rozumnom pásme.
     */
    @Test
    fun noCliffsAnywhereOnTheLap() {
        val step = 0.25f
        var x = 0f
        while (x < TestTrackProfile.LAP * 2f) {
            val slope = kotlin.math.abs(h(x + step) - h(x)) / step
            assertTrue("zráz na $x m (sklon $slope)", slope < 1.0f)
            x += step
        }
    }

    /** Okruh sa opakuje – po prejdení kola nadväzuje sám na seba. */
    @Test
    fun theLapWrapsWithoutAStep() {
        val before = h(TestTrackProfile.LAP - 0.05f)
        val after = h(TestTrackProfile.LAP + 0.05f)
        assertEquals("na spoji kola je schod", before, after, 0.02f)
        assertEquals("kolo musí začínať aj končiť na rovine", 0f, h(0f), 0.01f)
    }
}
