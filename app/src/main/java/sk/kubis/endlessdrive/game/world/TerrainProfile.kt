package sk.kubis.endlessdrive.game.world

import sk.kubis.endlessdrive.core.MathX
import sk.kubis.endlessdrive.core.SimplexNoise
import sk.kubis.endlessdrive.domain.model.BranchStyle
import kotlin.math.pow

/**
 * HillRush-štýl kopce: hladšie profilové vlny, menej vysokofrekvenčného šumu.
 */
class TerrainProfile(seed: Long) {
    private val noise = SimplexNoise(seed)

    fun heightAt(worldX: Float, style: BranchStyle): Float {
        val x = worldX
        val intro = MathX.smoothstep(8f, 70f, x)
        val difficulty = MathX.clamp((x - 100f) / 1400f, 0f, 1f).toDouble().pow(0.75).toFloat()
        val challenge = style.hillChallenge

        // Viac rovín medzi kopcami.
        val flatMask = MathX.smoothstep(-0.05f, 0.45f, noise.noise2(x * 0.0028f, 71.5f))
        val flatFloor = 0.48f + 0.35f * difficulty
        val calm = flatFloor + (1f - flatFloor) * flatMask

        val steep = ((0.45f + 0.40f * difficulty) * challenge).coerceAtMost(1.0f) * intro * calm
        val stretch = 1.15f + 0.65f * difficulty

        var h = 0f
        h += octave(x, 0.008f / stretch, 3.7f, 0.62f * steep)
        h += octave(x, 0.018f / stretch, 19.1f, 0.28f * steep)
        // Jemný detail namiesto ostrých zlomov.
        h += octave(x, 0.038f, 41.3f, 0.06f * steep)
        h += style.bumpiness * noise.noise2(x * 0.045f, 9.2f) * 0.18f * intro

        return 3.2f + h
    }

    fun slopeAt(worldX: Float, style: BranchStyle): Float {
        val d = 0.55f
        return (heightAt(worldX + d, style) - heightAt(worldX - d, style)) / (2f * d)
    }

    /** Nájde lokálne najrovnejšie miesto v [fromLocal, toLocal] (abs slope). */
    fun flattestLocalX(
        worldOrigin: Float,
        style: BranchStyle,
        fromLocal: Float,
        toLocal: Float,
        step: Float = 2.5f
    ): Float {
        var bestX = fromLocal
        var best = Float.MAX_VALUE
        var x = fromLocal
        while (x <= toLocal) {
            val s = kotlin.math.abs(slopeAt(worldOrigin + x, style))
            if (s < best) {
                best = s
                bestX = x
            }
            x += step
        }
        return bestX
    }

    private fun octave(x: Float, freq: Float, phase: Float, slope: Float): Float =
        noise.noise2(x * freq, phase) * (slope / (freq * 5.2f))
}
