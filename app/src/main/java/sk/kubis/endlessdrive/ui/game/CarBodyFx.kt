package sk.kubis.endlessdrive.ui.game

/**
 * Dym, para a misfire z karosérie – rovnaká geometria ako prach z kolies:
 * zdroj na spritoch, stopa po sklone/rýchlosti, nie gule prilepené na náboj.
 */
internal object CarBodyFx {
    /** Koniec výfuku v predlohe (zadný ľavý roh karosérie). */
    const val EXHAUST_FX = 0.04f
    const val EXHAUST_FY = 0.72f
    /** Mriežka chladiča – predok, pod hranou kapoty. */
    const val RADIATOR_FX = 0.918f
    const val RADIATOR_FY = 0.55f
    /** Štrbina kapoty, kadiaľ ide para. */
    const val HOOD_VENT_FX = 0.835f
    const val HOOD_VENT_FY = 0.40f

    /**
     * Os auta v obrazovke. [bodyPitch] je svetový (kladný = nos hore),
     * [alongRoad] čaká uhol ako atan2 obrazovky (Y dolu).
     */
    fun carAxisRad(bodyPitch: Float): Float = -bodyPitch

    /**
     * Pozdĺžny posun oblaku z rúry: vždy von chrbtom, navyše ho vietor
     * ťahá proti rýchlosti.
     */
    fun exhaustAlong(phase: Float, speed: Float, ppm: Float, hash: Float): Float {
        val slip = WheelContactFx.trailAlongSign(speed)
        val stretch = 0.32f + kotlin.math.abs(speed) * 0.042f
        val rear = -(0.07f + phase * 0.38f)
        return ppm * (rear + slip * phase * stretch + (hash - 0.5f) * 0.09f)
    }

    fun exhaustLift(phase: Float, ppm: Float, hash: Float): Float =
        -ppm * (0.018f + phase * 0.20f + (hash - 0.42f) * 0.035f)

    fun exhaustRadius(phase: Float, ppm: Float, sick: Float, hash: Float): Float =
        ppm * (0.016f + phase * (0.048f + sick * 0.032f) + hash * 0.010f)

    fun misfirePopping(beat: Float): Boolean = beat >= 0f && beat < 0.18f

    fun misfireJetAlong(hash: Float, ppm: Float): Float =
        -ppm * (0.06f + hash * 0.52f)

    fun misfireJetLift(hash: Float, ppm: Float): Float =
        -ppm * (hash * 0.11f)

    fun steamAlong(phase: Float, speed: Float, ppm: Float, hash: Float): Float {
        val slip = WheelContactFx.trailAlongSign(speed)
        val wind = phase * (0.22f + kotlin.math.abs(speed) * 0.038f)
        return ppm * (slip * wind + (hash - 0.5f) * 0.13f)
    }

    fun steamLift(phase: Float, ppm: Float, hash: Float): Float =
        -ppm * (0.05f + phase * 0.82f + hash * 0.07f)

    fun steamRadius(phase: Float, ppm: Float, hash: Float): Float =
        ppm * (0.015f + phase * 0.058f + hash * 0.011f)
}
