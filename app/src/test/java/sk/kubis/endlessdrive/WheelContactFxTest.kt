package sk.kubis.endlessdrive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import sk.kubis.endlessdrive.ui.game.WheelContactFx
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class WheelContactFxTest {

    @Test
    fun trailGoesOppositeTravelIncludingReverse() {
        assertEquals(1f, WheelContactFx.travelAlongSign(12f))
        assertEquals(-1f, WheelContactFx.travelAlongSign(-3.2f))
        assertEquals(-1f, WheelContactFx.trailAlongSign(12f))
        assertEquals(1f, WheelContactFx.trailAlongSign(-3.2f))
        val fwd = WheelContactFx.alongRoad(100f, 200f, 0f, WheelContactFx.trailAlongSign(8f) * 10f)
        assertEquals(90f, fwd.first, 0.01f)
        assertEquals(200f, fwd.second, 0.01f)
        val rev = WheelContactFx.alongRoad(100f, 200f, 0f, WheelContactFx.trailAlongSign(-4f) * 10f)
        assertEquals(110f, rev.first, 0.01f)
        assertEquals(200f, rev.second, 0.01f)
    }

    @Test
    fun trailFollowsRoadSlopeNotScreenHorizontal() {
        val slope = (PI / 6.0).toFloat()
        val p = WheelContactFx.alongRoad(0f, 0f, slope, 10f, 0f)
        assertEquals(10f * cos(slope), p.first, 0.05f)
        assertEquals(10f * sin(slope), p.second, 0.05f)
    }

    @Test
    fun sparksEmitWhenReversingOnTheRim() {
        assertTrue(
            WheelContactFx.emitsGroundFx(true, -3f, WheelContactFx.SPARK_MIN_SPEED)
        )
        assertTrue(
            WheelContactFx.emitsGroundFx(true, 3f, WheelContactFx.SPARK_MIN_SPEED)
        )
        assertFalse(
            WheelContactFx.emitsGroundFx(false, -8f, WheelContactFx.SPARK_MIN_SPEED)
        )
        assertFalse(
            WheelContactFx.emitsGroundFx(true, -0.2f, WheelContactFx.SPARK_MIN_SPEED)
        )
    }

    @Test
    fun airborneAndStationaryDoNotSpawnSkids() {
        assertFalse(
            WheelContactFx.emitsSkidMarks(
                physicallyGrounded = false,
                visuallyGrounded = true,
                speed = 12f,
                slip = 0.9f,
                locked = false
            )
        )
        assertFalse(
            WheelContactFx.emitsSkidMarks(
                physicallyGrounded = false,
                visuallyGrounded = false,
                speed = 8f,
                slip = 1f,
                locked = false
            )
        )
        assertFalse(
            WheelContactFx.emitsSkidMarks(
                physicallyGrounded = true,
                visuallyGrounded = true,
                speed = 0f,
                slip = 0.8f,
                locked = false
            )
        )
        assertTrue(
            WheelContactFx.emitsSkidMarks(
                physicallyGrounded = true,
                visuallyGrounded = true,
                speed = 6f,
                slip = 0.4f,
                locked = false
            )
        )
        assertFalse(
            WheelContactFx.emitsGroundFx(false, true, -8f, WheelContactFx.SPARK_MIN_SPEED)
        )
    }

    @Test
    fun wheelSitFollowsSlopeNormalNotVerticalDrop() {
        val r = 20f
        val slope = (PI / 6.0).toFloat()
        val sit = WheelContactFx.wheelSitFromGround(r, slope)
        assertEquals(-r * sin(slope), sit.first, 0.05f)
        assertEquals(-r * cos(slope), sit.second, 0.05f)
        val flat = WheelContactFx.wheelSitFromGround(r, 0f)
        assertEquals(0f, flat.first, 0.01f)
        assertEquals(-r, flat.second, 0.01f)
    }

    @Test
    fun frontSparkOriginLeadsRearOnTheSameContact() {
        val contact = 200f
        val ppm = 30f
        val rear = WheelContactFx.sparkOriginX(contact, 12f, ppm, frontAxle = false)
        val front = WheelContactFx.sparkOriginX(contact, 12f, ppm, frontAxle = true)
        assertTrue("predok $front musí byť pred zadkom $rear", front > rear)
        assertEquals(contact - ppm * 0.05f, rear, 0.01f)
        assertEquals(0f, WheelContactFx.sparkLeadM(false), 0f)
        assertTrue(WheelContactFx.sparkLeadM(true) > 0.1f)
        assertTrue("vpred je nábežná hrana vpravo od kontaktu", front > contact)
    }

    @Test
    fun frontSparkOriginLeadsTravelWhenReversing() {
        val contact = 200f
        val ppm = 30f
        val rear = WheelContactFx.sparkOriginX(contact, -6f, ppm, frontAxle = false)
        val front = WheelContactFx.sparkOriginX(contact, -6f, ppm, frontAxle = true)
        assertTrue(
            "pri cúvaní predok $front musí byť vzadu (vľavo) od zadku $rear",
            front < rear
        )
        assertTrue("nábežná hrana pri cúvaní je vľavo od kontaktu", front < contact)
        assertEquals(contact + ppm * 0.05f, rear, 0.01f)
        val fwd = WheelContactFx.sparkOriginX(contact, 8f, ppm, frontAxle = true)
        assertTrue(
            "posun nesmie ostať dopredu na obrazovke pri cúvaní ($front vs $fwd)",
            front < contact && fwd > contact
        )
    }

    @Test
    fun sparkClipKeepsOriginWhenReversing() {
        val contact = 400f
        val ppm = 40f
        val origin = WheelContactFx.sparkOriginX(contact, -5f, ppm, frontAxle = true)
        val trail = WheelContactFx.trailAlongSign(-5f)
        val (left, right) = WheelContactFx.sparkClipX(
            contact, origin, trail, ppm * 1.55f, ppm * 0.12f
        )
        assertTrue("origin $origin mimo clipu $left..$right", origin in left..right)
        assertTrue(contact in left..right)
        assertTrue(right > origin)
    }

    @Test
    fun skidMarkEndsFollowLocalSlopeNotHorizontal() {
        val slope = (PI / 6.0).toFloat()
        val half = 12f
        val (a, b) = WheelContactFx.skidMarkEnds(100f, 50f, slope, half)
        assertEquals(2f * half * cos(slope), b.first - a.first, 0.08f)
        assertEquals(2f * half * sin(slope), b.second - a.second, 0.08f)
        assertTrue("konce nesmú mať rovnaké Y na svahu", kotlin.math.abs(b.second - a.second) > 4f)
        val flat = WheelContactFx.skidMarkEnds(0f, 10f, 0f, 8f)
        assertEquals(flat.first.second, flat.second.second, 0.01f)
    }

    @Test
    fun rimSkidIsThinnerThanRubberAndNightSparksGlow() {
        assertTrue(WheelContactFx.metalSkid(true))
        assertFalse(WheelContactFx.metalSkid(false))
        val ppm = 28f
        assertTrue(
            WheelContactFx.metalSkidWidth(ppm) < WheelContactFx.rubberSkidWidth(ppm) * 0.4f
        )
        assertTrue(WheelContactFx.skidHalfLengthPx(ppm, true) > WheelContactFx.skidHalfLengthPx(ppm, false))
        assertEquals(1f, WheelContactFx.sparkNightGlow(0f), 0.001f)
        assertTrue(WheelContactFx.sparkNightGlow(1f) > 2f)
        assertTrue(WheelContactFx.sparkNightCore(1f) > WheelContactFx.sparkNightCore(0f))
    }
}
