package sk.kubis.endlessdrive.game

import sk.kubis.endlessdrive.core.GameConfig
import sk.kubis.endlessdrive.core.MathX
import kotlin.math.PI
import kotlin.math.sin

/**
 * Denný cyklus: [time] je 0..1 (0 = polnoc, 0.5 = poludnie).
 * Renderer aj engine z neho čítajú rovnaké hodnoty – žiadny stav navyše.
 */
object DayCycle {

    /** Výška slnka nad horizontom: -1 (hlboká noc) .. 1 (poludnie). */
    fun sunElevation(time: Float): Float =
        sin(((time - 0.25f) * 2f * PI).toDouble()).toFloat()

    /** 0 = tma, 1 = plné denné svetlo – širší „deň“, kratšia skutočná noc. */
    fun daylight(time: Float): Float =
        MathX.smoothstep(-0.40f, 0.10f, sunElevation(time))

    /** 1 tesne pri východe/západe slnka, 0 inak – pre oranžovú oblohu. */
    fun goldenHour(time: Float): Float {
        val e = sunElevation(time)
        return (1f - (e / 0.28f).let { it * it }).coerceIn(0f, 1f)
    }

    fun isNight(time: Float): Boolean = daylight(time) < GameConfig.NIGHT_THRESHOLD

    /** Herné hodiny pre HUD, napr. "21:40". */
    fun clock(time: Float): String {
        val t = ((time % 1f) + 1f) % 1f
        val minutes = (t * 24f * 60f).toInt()
        return String.format("%02d:%02d", minutes / 60, minutes % 60)
    }

    /** Posunie čas o dt sekúnd reálneho behu. */
    fun advance(time: Float, dt: Float): Float {
        val next = time + dt / GameConfig.DAY_LENGTH
        return next - kotlin.math.floor(next)
    }
}
