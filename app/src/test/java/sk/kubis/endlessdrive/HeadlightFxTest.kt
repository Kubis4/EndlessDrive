package sk.kubis.endlessdrive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import sk.kubis.endlessdrive.core.GameConfig
import sk.kubis.endlessdrive.ui.game.BodyPart
import sk.kubis.endlessdrive.ui.game.BodyPartCatalog
import sk.kubis.endlessdrive.ui.game.HeadlightFx
import kotlin.math.cos

class HeadlightFxTest {

    private val sedanWorldWidthM = 5.6f
    private val headlightFx = BodyPartCatalog.specs.getValue(BodyPart.HEADLIGHT).fx

    @Test
    fun highBeamReachesFartherAndWiderThanDipped() {
        assertTrue(HeadlightFx.reachM(true) > HeadlightFx.reachM(false) * 1.6f)
        assertTrue(HeadlightFx.revealFarDepth(true) > HeadlightFx.revealFarDepth(false))
        assertTrue(HeadlightFx.revealNearDepth(true) < HeadlightFx.revealNearDepth(false))
        assertTrue(HeadlightFx.revealLowerM(true) > HeadlightFx.revealLowerM(false))
        assertTrue(HeadlightFx.revealStations(true) > HeadlightFx.revealStations(false))
    }

    @Test
    fun beamOriginIsTheFrontLampsNotBodyCenterOrCabin() {
        val ahead = HeadlightFx.lampAheadM(headlightFx, sedanWorldWidthM)
        val midCarWrong = GameConfig.WHEEL_OFFSET_X * 0.35f
        assertTrue("lúč musí začínať pred stredom karosérie", ahead > 1.6f)
        assertTrue("lúč nesmie začínať pod kabínou / B-stĺpikom", ahead > midCarWrong + 1f)
        assertTrue("lúč je pred prednou nápravou, pri svetlometoch", ahead > GameConfig.WHEEL_OFFSET_X)
        assertTrue(
            "stred sprite nie je lampa",
            HeadlightFx.lampAheadM(0.5f, sedanWorldWidthM) < 0.2f
        )

        val carX = 40f
        val start = HeadlightFx.beamStartWorldX(carX, 0f, headlightFx, sedanWorldWidthM)
        assertEquals(carX + ahead, start, 1e-4f)
        assertTrue(start > carX)

        val pitch = 0.25f
        val pitched = HeadlightFx.beamStartWorldX(carX, pitch, headlightFx, sedanWorldWidthM)
        assertEquals(carX + ahead * cos(pitch), pitched, 1e-4f)
        assertTrue("aj pri náklone ostáva začiatok pred karosériou", pitched > carX)
        assertTrue("diaľkové aj stretávacie idú z toho istého miesta", HeadlightFx.reachM(true) > HeadlightFx.reachM(false))
    }

    @Test
    fun veilHoleIsStrongerOnHighBeamAndDoesNotDumpFullDaylight() {
        assertTrue(HeadlightFx.veilLift(true) > HeadlightFx.veilLift(false))
        val dipped = HeadlightFx.revealedDay(0f, highBeam = false, strength = 1f)
        val high = HeadlightFx.revealedDay(0f, highBeam = true, strength = 1f)
        assertTrue("v kuželi musí byť scéna čitateľná", dipped > 0.50f)
        assertTrue("diaľkové odhalia viac než stretávacie", high > dipped + 0.10f)
        assertTrue("diera nie je denný vystrih", high < 0.92f)
        assertEquals(0f, HeadlightFx.revealedDay(0f, highBeam = true, strength = 0f), 0.001f)
        assertEquals(1f, HeadlightFx.revealedDay(1f, highBeam = true, strength = 1f), 0.001f)
    }

    @Test
    fun dippedRevealStartsWideAtTheLampsNotAsAFarNeedle() {
        val nearSpan = HeadlightFx.revealNearLowerM(false) - HeadlightFx.revealNearUpperM(false)
        val farSpan = HeadlightFx.revealLowerM(false) - HeadlightFx.revealUpperM(false)
        assertTrue("pri lampe musí byť diera široká, nie ihlan z bodu", nearSpan > 2.4f)
        assertTrue("vozovka pred nárazníkom musí byť v kuželi", HeadlightFx.revealNearLowerM(false) > 1.8f)
        assertTrue("kužeľ sa ďalej ešte rozširuje", farSpan > nearSpan)
        assertTrue(HeadlightFx.revealNearUpperM(false) < 0f)
        assertTrue(
            "diaľkové sú pri lampe aspoň tak široké ako stretávacie",
            HeadlightFx.revealNearLowerM(true) >= HeadlightFx.revealNearLowerM(false)
        )
    }

    @Test
    fun rackLampAddsASecondNearRevealFromTheRoof() {
        assertTrue(HeadlightFx.rackReachM() > 6f)
        assertTrue(
            "strešný reflektor je pracovné svetlo, nie druhý diaľkový kužeľ",
            HeadlightFx.rackReachM() < HeadlightFx.reachM(false)
        )
        val rackNear = HeadlightFx.rackRevealNearLowerM() - HeadlightFx.rackRevealNearUpperM()
        val rackFar = HeadlightFx.rackRevealLowerM() - HeadlightFx.rackRevealUpperM()
        assertTrue("strecha začína v bode, nie ako doska", rackNear < 0.45f)
        assertTrue("ďalej sa rozširuje do kužeľa", rackFar > rackNear * 8f)
        assertTrue("nesvieti do oblohy / mesy", HeadlightFx.rackRevealUpperM() > -0.05f)
        assertTrue("smeruje na vozovku", HeadlightFx.rackRevealLowerM() > 1.6f)
        val headAhead = HeadlightFx.lampAheadM(headlightFx, sedanWorldWidthM)
        val rackAhead = HeadlightFx.rackLampAheadM(sedanWorldWidthM)
        assertTrue("reflektor je pred stredom karosérie", rackAhead > 0f)
        assertTrue("reflektor ostáva za svetlometmi", rackAhead < headAhead)
    }

    @Test
    fun dippedStaysNearTheLampHighBeamMayLiftButNotTheWholeSky() {
        assertTrue(
            "stretávacie nesmú zalievať oblohu",
            HeadlightFx.revealUpperM(false) > -1.6f
        )
        assertTrue(
            "diaľkové idú vyššie než stretávacie",
            HeadlightFx.revealUpperM(true) < HeadlightFx.revealUpperM(false)
        )
        assertTrue(HeadlightFx.revealLowerM(true) > HeadlightFx.revealLowerM(false))
        // Horná hrana ostáva v metroch od lampy, nie cez celú obrazovku.
        assertTrue(HeadlightFx.revealUpperM(true) > -4f)
    }
}
