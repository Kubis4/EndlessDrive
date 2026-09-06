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

    /** Východ: 06:00. Západ: 18:00. Medzi nimi ide slnko plynulo zľava doprava. */
    const val SUNRISE = 0.25f
    const val SUNSET = 0.75f

    fun wrap(time: Float): Float = time - kotlin.math.floor(time)

    /** Zlomok dňa z hodín HUD; slnko berie plný float, text sa zaokrúhli na minúty. */
    fun at(hour: Int, minute: Int = 0): Float {
        val minutes = hour * 60 + minute
        val day = 24 * 60
        val wrapped = ((minutes % day) + day) % day
        return wrapped / day.toFloat()
    }

    /** Výška slnka nad horizontom: -1 (hlboká noc) .. 1 (poludnie). */
    fun sunElevation(time: Float): Float =
        sin(((wrap(time) - SUNRISE) * 2f * PI).toDouble()).toFloat()

    /**
     * Vodorovná dráha slnka: 0 východ, 1 západ. Mimo 0..1 je pod obzorom.
     * Lineárna v [time] – žiadne hodinové schody ani predlohy oblohy.
     */
    fun sunProgress(time: Float): Float =
        (wrap(time) - SUNRISE) / (SUNSET - SUNRISE)

    fun moonProgress(time: Float): Float = sunProgress(time - 0.5f)

    fun celestialArc(progress: Float): Float =
        sin((progress.coerceIn(0f, 1f) * PI).toDouble()).toFloat()

    /** 0 = tma, 1 = plné denné svetlo – širší „deň“, kratšia skutočná noc. */
    fun daylight(time: Float): Float =
        MathX.smoothstep(-0.40f, 0.10f, sunElevation(time))

    /** 1 tesne pri východe/západe slnka, 0 inak – pre oranžovú oblohu. */
    fun goldenHour(time: Float): Float {
        val e = sunElevation(time)
        return (1f - (e / 0.28f).let { it * it }).coerceIn(0f, 1f)
    }

    fun isNight(time: Float): Boolean = daylight(time) < GameConfig.NIGHT_THRESHOLD

    /** Herné hodiny pre HUD, napr. "21:40" – minúty dolu, slnko ostáva plynulé. */
    fun clock(time: Float): String {
        val minutes = (wrap(time) * 24f * 60f).toInt()
        return String.format("%02d:%02d", minutes / 60, minutes % 60)
    }

    /** Posunie čas o dt sekúnd reálneho behu. */
    fun advance(time: Float, dt: Float): Float = wrap(time + dt / GameConfig.DAY_LENGTH)
}
