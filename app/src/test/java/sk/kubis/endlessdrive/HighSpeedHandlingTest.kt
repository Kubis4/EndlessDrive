package sk.kubis.endlessdrive

import org.junit.Assert.assertTrue
import org.junit.Test
import sk.kubis.endlessdrive.domain.model.DebugOptions
import sk.kubis.endlessdrive.game.GameEngine
import kotlin.math.abs
import kotlin.math.sin

/** Reálna telemetrická jazda pre ladenie plného auta nad 130 km/h. */
class HighSpeedHandlingTest {

    private data class RunMetrics(
        var maxKmh: Float = 0f,
        var minKmh: Float = Float.POSITIVE_INFINITY,
        var maxRiseSpeed: Float = 0f,
        var maxFallSpeed: Float = 0f,
        var maxVerticalAcceleration: Float = 0f,
        var maxPitchRate: Float = 0f,
        var maxPitch: Float = 0f,
        var airborneFrames: Int = 0,
        var longestAirborneFrames: Int = 0,
        var bottomOutFrames: Int = 0,
        var maxNearestAxleGap: Float = 0f,
        var landingPitch: Float? = null
    )

    private fun run(
        seed: Long,
        testTrack: Boolean,
        frames: Int = 2400,
        startX: Float = 4f
    ): RunMetrics {
        val engine = GameEngine(
            seed,
            0f,
            DebugOptions(fullUpgrades = true, fullFluids = true, testTrack = testTrack)
        )
        assertTrue(engine.tryStartEngine())
        engine.car.x = startX
        engine.car.speed = 130f / 3.6f
        engine.car.snapToGround(
            engine.segment.heightAtWorld(engine.car.x),
            engine.segment.slopeAtLocal(engine.localX)
        )
        engine.throttleInput = 0.82f

        val out = RunMetrics()
        var airborneStreak = 0
        var wasAirborne = false
        var previousVy = engine.car.vy
        repeat(frames) {
            engine.advance(1f / 60f)
            val car = engine.car
            out.maxKmh = maxOf(out.maxKmh, car.speedKmh)
            out.minKmh = minOf(out.minKmh, car.speedKmh)
            out.maxRiseSpeed = maxOf(out.maxRiseSpeed, car.vy)
            out.maxFallSpeed = minOf(out.maxFallSpeed, car.vy)
            out.maxVerticalAcceleration = maxOf(
                out.maxVerticalAcceleration,
                abs(car.vy - previousVy) * 60f
            )
            out.maxPitchRate = maxOf(out.maxPitchRate, abs(car.pitchRate))
            out.maxPitch = maxOf(out.maxPitch, abs(car.pitch))
            if (!car.grounded) {
                wasAirborne = true
                out.airborneFrames++
                airborneStreak++
                out.longestAirborneFrames = maxOf(out.longestAirborneFrames, airborneStreak)
                val wb = sk.kubis.endlessdrive.domain.model.SedanSpec.wheelOffsetX
                val rearBody = car.y - wb * sin(car.pitch)
                val frontBody = car.y + wb * sin(car.pitch)
                val nearestGap = minOf(
                    rearBody - engine.segment.heightAtWorld(car.x - wb) - car.rideHeight,
                    frontBody - engine.segment.heightAtWorld(car.x + wb) - car.rideHeight
                )
                out.maxNearestAxleGap = maxOf(out.maxNearestAxleGap, nearestGap)
            } else {
                if (wasAirborne) out.landingPitch = car.pitch
                wasAirborne = false
                airborneStreak = 0
            }
            if (car.rearCompression >= car.suspTravel * 0.98f ||
                car.frontCompression >= car.suspTravel * 0.98f
            ) {
                out.bottomOutFrames++
            }
            previousVy = car.vy
        }
        println("HIGH_SPEED seed=$seed test=$testTrack start=$startX $out")
        return out
    }

    @Test
    fun fullBuildAtOneThirtyAcrossTestTrackAndGeneratedHills() {
        val smallBumps = run(44L, testTrack = true, frames = 260)
        val bigJump = run(44L, testTrack = true, frames = 210, startX = 145f)
        val stableRuns = listOf(
            // Prvých 155 m obsahuje iba drobné vlny a jeden 55 cm hrbol.
            smallBumps,
            run(3L, testTrack = false),
            run(17L, testTrack = false),
            run(61L, testTrack = false)
        )

        assertTrue("full build sa nedostal nad 130 km/h", stableRuns.all { it.maxKmh >= 130f })
        assertTrue(stableRuns.all { it.maxPitchRate < 2f })
        assertTrue(stableRuns.all { it.maxVerticalAcceleration < 100f })
        assertTrue("malý hrbol auto príliš odhodil", smallBumps.maxNearestAxleGap < 0.30f)
        assertTrue("bežné kopce odliepajú auto", stableRuns.drop(1).all { it.airborneFrames <= 2 })
        // Stabilita nesmie znamenať prilepenie auta: 2.4 m testovacia rampa
        // zostáva skutočným skokom.
        assertTrue("veľká rampa už auto neodlepí", bigJump.longestAirborneFrames > 10)
        assertTrue(
            "predomotorové auto dopadlo priveľmi na zadok: ${bigJump.landingPitch}",
            (bigJump.landingPitch ?: 0f) < 0.18f
        )
        assertTrue("veľká rampa je príliš utlmená", bigJump.maxNearestAxleGap > 0.40f)
        assertTrue(bigJump.maxPitchRate < 4f)
    }
}
