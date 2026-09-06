package sk.kubis.endlessdrive.ui.menu

import sk.kubis.endlessdrive.core.MathX

/**
 * Perspektíva menu-cesty. Značky a prach idú od hrebeňa k kamere
 * rovnakým kvadratickým mapovaním, aby slučka neskočila.
 */
internal object MenuRoadFx {
    const val CREST_LEFT = 0.519f
    const val CREST_RIGHT = 0.521f
    const val FOOT_LEFT = 0.14f
    const val FOOT_RIGHT = 0.88f
    const val CENTER = 0.52f

    fun perspective(t: Float): Float {
        val u = t.coerceIn(0f, 1f)
        return u * u
    }

    /** Schová záber, keď značka zmizne dole a znova sa narodí na horizonte. */
    fun travelFade(t: Float): Float {
        val enter = MathX.smoothstep(0.03f, 0.13f, t)
        val leave = 1f - MathX.smoothstep(0.86f, 0.995f, t)
        return (enter * leave).coerceIn(0f, 1f)
    }

    fun leftEdgeX(width: Float, p: Float): Float =
        width * (CREST_LEFT + (FOOT_LEFT - CREST_LEFT) * p)

    fun rightEdgeX(width: Float, p: Float): Float =
        width * (CREST_RIGHT + (FOOT_RIGHT - CREST_RIGHT) * p)

    fun centerX(width: Float, p: Float): Float =
        width * (CENTER + 0.015f * p)

    fun roadY(crestY: Float, bottom: Float, p: Float): Float =
        crestY + (bottom - crestY) * p

    /** lane −1 ľavý okraj, 0 stred, +1 pravý okraj. */
    fun laneX(width: Float, p: Float, lane: Float): Float {
        val left = leftEdgeX(width, p)
        val right = rightEdgeX(width, p)
        return MathX.lerp(left, right, (lane * 0.5f + 0.5f).coerceIn(0f, 1f))
    }

    fun dashStroke(p: Float): Float = 1.05f + 3.1f * p

    fun particleRadius(p: Float, near: Float): Float =
        (near * (0.28f + 0.72f * p)).coerceAtLeast(0.55f)
}
