package sk.kubis.endlessdrive.ui.game

import sk.kubis.endlessdrive.core.GameConfig
import kotlin.math.max
import kotlin.math.min

/**
 * Vizuálny zdvih kolesa v blatníku. Fyzika môže mať pol metra zdvihu, sprite
 * však otvor kolesa nemá – bez stropu zadné koleso vybehlo cez karosériu.
 *
 * Predok aj zadok berú tie isté limity oblúka; menší (SHREDDED) disk smie
 * klesnúť o rozdiel polomerov, aby sedel na ráfiku, nie aby preliezol hore.
 */
internal object SuspensionVisual {
    /**
     * Koľko zo zdvihu pruženia sa vo vzduchu roztiahne. Kolesá visia nadol –
     * auto v skoku pôsobí odľahčene, nie ako doska.
     */
    const val AIR_DROOP = 0.45f

    /** Max zdvih stredu kolesa hore, ako podiel pokojového polomeru v oblúku. */
    const val WELL_JOUNCE_FRAC = 0.28f

    /** Max pokles stredu kolesa dole, kým ešte ostane v otvore blatníka. */
    const val WELL_DROOP_FRAC = 0.36f

    fun travelPx(suspTravel: Float, ppm: Float): Float =
        suspTravel.coerceAtLeast(0f) * ppm * GameConfig.SUSP_VISUAL_GAIN

    /**
     * Povolený posun stredu kolesa voči stredu blatníka (obrazovkové Y,
     * kladné = dole). Rovnaký oblúk pre obe nápravy.
     *
     * @param restRadiusPx polomer, na ktorý je kreslený výrez blatníka
     * @param radiusPx skutočný vizuálny polomer nápravy (škála gumy, SHREDDED)
     */
    fun wellDyBounds(
        restRadiusPx: Float,
        radiusPx: Float,
        travelPx: Float
    ): ClosedFloatingPointRange<Float> {
        val rest = restRadiusPx.coerceAtLeast(1f)
        val radius = radiusPx.coerceAtLeast(1f)
        val travel = travelPx.coerceAtLeast(0f)
        val jounce = min(travel, rest * WELL_JOUNCE_FRAC)
        val droop = min(travel, rest * WELL_DROOP_FRAC)
        val extraDrop = max(0f, rest - radius)
        val extraRise = max(0f, radius - rest)
        val minDy = -jounce + extraRise
        val maxDy = droop + extraDrop
        return minDy..maxDy
    }

    /** Stred kolesa v obrazovke. [wellY] je stred otočeného oblúka. */
    fun wheelCenterY(
        wellY: Float,
        groundY: Float,
        radiusPx: Float,
        restRadiusPx: Float,
        travelPx: Float,
        visualContact: Boolean
    ): Float = wheelOnRoad(
        wellX = 0f,
        wellY = wellY,
        groundY = groundY,
        radiusPx = radiusPx,
        restRadiusPx = restRadiusPx,
        travelPx = travelPx,
        visualContact = visualContact,
        slopeRad = 0f
    ).second

    /**
     * Stred kolesa na vozovke. Vodorovne ostáva pod blatníkom – sklon
     * vozovky sa pri rýchlosti mení snímok od snímku a koleso by inak
     * jazdilo pozdĺž karosérie. Zvisle sleduje terén v limite zdvihu.
     * Defekt ([alongSlope]) upraví zvislú výšku podľa normály svahu, aby
     * placka nevisela nad spádom. Stred X vždy ostáva na osi blatníka;
     * vodorovný posun podľa meniaceho sa sklonu vyzeral ako cestovanie kolesa.
     * Vracia (x, y) v obrazovke.
     */
    fun wheelOnRoad(
        wellX: Float,
        wellY: Float,
        groundY: Float,
        radiusPx: Float,
        restRadiusPx: Float,
        travelPx: Float,
        visualContact: Boolean,
        slopeRad: Float,
        alongSlope: Boolean = false
    ): Pair<Float, Float> {
        val bounds = wellDyBounds(restRadiusPx, radiusPx, travelPx)
        if (!visualContact) {
            val targetY = wellY + (travelPx * AIR_DROOP).coerceAtMost(bounds.endInclusive)
            val y = wellY + (targetY - wellY).coerceIn(bounds.start, bounds.endInclusive)
            return wellX to y
        }
        val sitY = if (alongSlope) {
            -kotlin.math.cos(slopeRad) * radiusPx
        } else {
            -radiusPx
        }
        val targetY = groundY + sitY
        val y = wellY + (targetY - wellY).coerceIn(bounds.start, bounds.endInclusive)
        return wellX to y
    }
}
