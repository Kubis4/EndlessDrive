package sk.kubis.endlessdrive.game.world

import sk.kubis.endlessdrive.core.GameConfig
import sk.kubis.endlessdrive.core.MathX
import sk.kubis.endlessdrive.core.SimplexNoise
import sk.kubis.endlessdrive.domain.model.BranchStyle
import kotlin.math.pow
import kotlin.math.PI
import kotlin.math.sin

/**
 * HillRush-štýl kopce: od začiatku jemné vlnky, neskôr ostré hrebene.
 */
class TerrainProfile(seed: Long) : Terrain {
    private val noise = SimplexNoise(seed)

    /**
     * @param challengeMul násobiteľ členitosti z aktuálneho úseku trate
     *   (rovinka < 1, kopce > 1, most ≈ 0).
     */
    override fun heightAt(worldX: Float, style: BranchStyle, challengeMul: Float): Float {
        val x = worldX
        // Rýchly nástup jemných vĺn – nie kilometer roviny.
        val intro = MathX.smoothstep(12f, 70f, x)
        // Ťažšie kopce neskôr; skoré km ostávajú zjazdné. Krivka nemá strop –
        // aj na 30. km je terén členitejší než na 8., len rozdiel sa zmenšuje.
        val difficulty = MathX.growth(x - 120f, 2600f).coerceAtMost(GameConfig.TERRAIN_MAX_DIFFICULTY)
        val challenge = style.hillChallenge * challengeMul.coerceIn(0f, 2.8f)

        val flatMask = MathX.smoothstep(-0.02f, 0.55f, noise.noise2(x * 0.0034f, 71.5f))
        // Skôr menej „mŕtvej“ roviny, neskôr viac priestoru medzi hrebeňmi.
        val flatFloor = 0.22f + 0.28f * difficulty
        val calm = flatFloor + (1f - flatFloor) * flatMask

        // Skoré stúpania mierne (zjazdné), neskôr prudšie.
        val steep = ((0.38f + 0.36f * difficulty) * challenge)
            .coerceAtMost(GameConfig.TERRAIN_MAX_STEEP) * intro * calm
        val stretch = 1.08f + 0.30f * difficulty

        var h = 0f
        // Dlhé vlny = „rolling hills“ hneď od štartu.
        h += octave(x, 0.0085f / stretch, 3.7f, 0.55f * steep)
        h += octave(x, 0.017f / stretch, 19.1f, 0.28f * steep)
        // Hrebene na skok – hlavne neskôr.
        h += octave(x, 0.027f / stretch, 33.4f, 0.15f * steep * (0.45f + 0.35f * difficulty))
        h += octave(x, 0.040f, 41.3f, 0.06f * steep)
        h += style.bumpiness * noise.noise2(x * 0.048f, 9.2f) * 0.20f * intro

        // Jemné prekážky aj v normálnej hre: krátke hladké hrboly a občasný
        // zvlnený pás. Sú malé, no pri rýchlosti rozhýbu obe nápravy podobne
        // ako prvá časť testovacej trate.
        val detailIntro = MathX.smoothstep(28f, 85f, x)
        val patch = MathX.smoothstep(0.12f, 0.72f, noise.noise2(x * 0.0065f, 118.7f))
        h += sin(x / 7.5f * 2f * PI.toFloat()) * 0.075f * patch * detailIntro
        val smallBump = noise.noise2(x * 0.115f, 207.4f).coerceAtLeast(0f).pow(5)
        h += smallBump * (0.13f + style.bumpiness * 0.12f) * detailIntro

        return 3.2f + h
    }

    fun slopeAt(worldX: Float, style: BranchStyle, challengeMul: Float = 1f): Float {
        val d = 0.55f
        return (heightAt(worldX + d, style, challengeMul) -
            heightAt(worldX - d, style, challengeMul)) / (2f * d)
    }

    private fun octave(x: Float, freq: Float, phase: Float, slope: Float): Float =
        noise.noise2(x * freq, phase) * (slope / (freq * 5.2f))
}
