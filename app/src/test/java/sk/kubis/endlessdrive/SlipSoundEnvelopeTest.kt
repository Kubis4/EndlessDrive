package sk.kubis.endlessdrive

import org.junit.Assert.*
import org.junit.Test
import sk.kubis.endlessdrive.game.audio.SlipSoundEnvelope

class SlipSoundEnvelopeTest {
    @Test fun prolongedWheelspinGoesSilent() {
        val cue = SlipSoundEnvelope()
        assertTrue(cue.update(true, 0.1f) > 0f)
        repeat(600) {
            val gain = cue.update(true, 0.1f)
            if (it > 10) assertEquals(0f, gain, 0.001f)
        }
    }
    @Test fun briefGripChangesDoNotRetriggerButRecoveryDoes() {
        val cue = SlipSoundEnvelope()
        repeat(12) { cue.update(true, 0.1f) }
        repeat(30) {
            cue.update(false, 0.1f)
            assertEquals(0f, cue.update(true, 0.1f), 0.001f)
        }
        repeat(10) { cue.update(false, 0.1f) }
        assertTrue(cue.update(true, 0.1f) > 0f)
    }
    @Test fun normalDrivingIsSilent() {
        val cue = SlipSoundEnvelope()
        repeat(100) { assertEquals(0f, cue.update(false, 0.1f), 0f) }
    }
}
