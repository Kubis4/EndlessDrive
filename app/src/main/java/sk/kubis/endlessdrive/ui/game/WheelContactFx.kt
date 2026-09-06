package sk.kubis.endlessdrive.ui.game

/**
 * Geometria prachu, iskier a šmyku pri styku kolesa s vozovkou.
 * Bez Compose, aby to vedel overiť unit test.
 */
internal object WheelContactFx {
    /** Aj pri cúvaní – prah musí byť pod REVERSE_MAX a pod typickým plazením na ráfiku. */
    const val SPARK_MIN_SPEED = 0.85f
    const val DUST_MIN_SPEED_RATIO = 0.08f

    /** Smer jazdy v obrazovke: +1 doprava (vpred), −1 doľava (cúvanie). */
    fun travelAlongSign(speed: Float): Float = if (speed >= 0f) 1f else -1f

    /** Smer stopy v obrazovke: proti rýchlosti, pozdĺž vozovky. */
    fun trailAlongSign(speed: Float): Float = -travelAlongSign(speed)

    fun screenSlopeRad(x0: Float, y0: Float, x1: Float, y1: Float): Float =
        kotlin.math.atan2(y1 - y0, x1 - x0)

    /**
     * Bod na vozovke. [along] ide po spáde (kladné = doprava po ceste),
     * [across] kolmo na ňu (kladné = do zeme v obrazovke).
     */
    fun alongRoad(
        contactX: Float,
        contactY: Float,
        slopeRad: Float,
        along: Float,
        across: Float = 0f
    ): Pair<Float, Float> {
        val c = kotlin.math.cos(slopeRad)
        val s = kotlin.math.sin(slopeRad)
        return (contactX + c * along - s * across) to (contactY + s * along + c * across)
    }

    fun emitsGroundFx(grounded: Boolean, speed: Float, minAbsSpeed: Float): Boolean =
        emitsGroundFx(grounded, grounded, speed, minAbsSpeed)

    fun emitsGroundFx(
        physicallyGrounded: Boolean,
        visuallyGrounded: Boolean,
        speed: Float,
        minAbsSpeed: Float
    ): Boolean = physicallyGrounded && visuallyGrounded &&
        kotlin.math.abs(speed) >= minAbsSpeed

    /**
     * Gumené stopy a odletujúce úlomky. Vo vzduchu, na hrbolci v 0 km/h
     * a bez preklzu nič – inak ostávajú fialové čiarky pod stojacim autom.
     */
    fun emitsSkidMarks(
        physicallyGrounded: Boolean,
        visuallyGrounded: Boolean,
        speed: Float,
        slip: Float,
        locked: Boolean,
        minSlip: Float = 0.22f,
        minAbsSpeed: Float = 0.35f
    ): Boolean {
        if (!physicallyGrounded || !visuallyGrounded) return false
        if (kotlin.math.abs(speed) < minAbsSpeed) return false
        return locked || slip >= minSlip
    }

    /** Posun stredu kolesa od bodu na vozovke pozdĺž normály svahu (Y dole). */
    fun wheelSitFromGround(radius: Float, slopeRad: Float): Pair<Float, Float> {
        val r = radius.coerceAtLeast(0f)
        return (-kotlin.math.sin(slopeRad) * r) to (-kotlin.math.cos(slopeRad) * r)
    }

    /** Koleso sedí na ceste, nie vo vzduchu nad ňou. */
    fun contactingRoad(
        wheelY: Float,
        groundY: Float,
        restRadius: Float,
        slop: Float,
        slopeRad: Float = 0f
    ): Boolean {
        val expected = restRadius * kotlin.math.cos(slopeRad).coerceAtLeast(0.35f)
        return (groundY - wheelY) <= expected + slop
    }

    /**
     * Predný ráfik: posun k nábežnej hrane kovu v smere jazdy.
     * Zadný ostáva na pôvodnom mieste pod stredom disku.
     */
    fun sparkLeadM(frontAxle: Boolean): Float = if (frontAxle) 0.14f else 0f

    /**
     * Zdroj iskier v obrazovke. Posun je v smere rýchlosti, nie „doprava na monitore“:
     * vpred = predná hrana disku, cúvanie = zadná hrana disku.
     */
    fun sparkOriginX(
        contactX: Float,
        speed: Float,
        ppm: Float,
        frontAxle: Boolean
    ): Float {
        val travel = travelAlongSign(speed)
        return contactX - travel * ppm * 0.05f + travel * ppm * sparkLeadM(frontAxle)
    }

    /**
     * Orez pásu iskier: vždy zahŕňa kontakt, nábežný origin aj stopu proti rýchlosti.
     */
    fun sparkClipX(
        contactX: Float,
        originX: Float,
        trailAlong: Float,
        trailPx: Float,
        padPx: Float
    ): Pair<Float, Float> {
        val trailEnd = originX + trailAlong * trailPx
        return minOf(contactX, originX, trailEnd) - padPx to
            maxOf(contactX, originX, trailEnd) + padPx
    }

    /** SHREDDED / ON RIM – ryhy kovu, nie guma. Defekt ostáva guma. */
    fun metalSkid(onRim: Boolean): Boolean = onRim

    fun rubberSkidWidth(ppm: Float): Float = (0.20f * ppm).coerceAtLeast(2f)

    fun metalSkidWidth(ppm: Float): Float = (0.048f * ppm).coerceAtLeast(1.05f)

    fun skidHalfLengthPx(ppm: Float, rim: Boolean): Float =
        (if (rim) 0.34f else 0.22f) * ppm

    /**
     * Konce stopy na lokálnom spáde. [acrossA]/[acrossB] sú kolmo na vozovku,
     * nie zvisle na obrazovke – inak ryha na kopci visí vodorovne.
     */
    fun skidMarkEnds(
        centerX: Float,
        centerY: Float,
        slopeRad: Float,
        halfLengthPx: Float,
        acrossA: Float = 0f,
        acrossB: Float = 0f
    ): Pair<Pair<Float, Float>, Pair<Float, Float>> {
        val a = alongRoad(centerX, centerY, slopeRad, -halfLengthPx, acrossA)
        val b = alongRoad(centerX, centerY, slopeRad, halfLengthPx, acrossB)
        return a to b
    }

    /** V tme iskry svietia; vo dne ostávajú ako za dňa. */
    fun sparkNightGlow(night: Float): Float = 1f + night.coerceIn(0f, 1f) * 1.55f

    fun sparkNightCore(night: Float): Float = 1f + night.coerceIn(0f, 1f) * 0.70f
}
