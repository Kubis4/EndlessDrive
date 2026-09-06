package sk.kubis.endlessdrive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import sk.kubis.endlessdrive.domain.model.ThrottleMode
import sk.kubis.endlessdrive.domain.model.ThrottleSlide

class ThrottleModeTest {

    @Test
    fun settingsToggleHasPedalAndSlide() {
        assertEquals(ThrottleMode.BINARY, ThrottleMode.fromStored(null))
        assertEquals(ThrottleMode.BINARY, ThrottleMode.fromStored("nope"))
        assertEquals(ThrottleMode.BINARY, ThrottleMode.fromStored("BINARY"))
        assertEquals(ThrottleMode.SLIDE, ThrottleMode.fromStored("SLIDE"))
        assertEquals(ThrottleMode.SLIDE, ThrottleMode.fromStored("slide"))
        assertTrue(ThrottleMode.entries.contains(ThrottleMode.BINARY))
        assertTrue(ThrottleMode.entries.contains(ThrottleMode.SLIDE))
    }

    @Test
    fun slideIsZeroAtTheBottomAndFullAtTheTop() {
        assertEquals(0f, ThrottleSlide.amount(200f, 0f, 200f), 0.001f)
        assertEquals(1f, ThrottleSlide.amount(0f, 0f, 200f), 0.001f)
        assertEquals(0.5f, ThrottleSlide.amount(100f, 0f, 200f), 0.001f)
        assertEquals(0f, ThrottleSlide.amount(400f, 0f, 200f), 0.001f)
        assertEquals(1f, ThrottleSlide.amount(-10f, 0f, 200f), 0.001f)
    }
}
