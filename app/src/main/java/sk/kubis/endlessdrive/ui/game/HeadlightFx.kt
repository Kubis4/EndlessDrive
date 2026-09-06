package sk.kubis.endlessdrive.ui.game

import sk.kubis.endlessdrive.core.GameConfig
import kotlin.math.cos

/**
 * Dosah stretávacích a diaľkových – diera v nočnom závoji, nie žltý overlay.
 * Bez Compose, aby to vedel overiť unit test.
 */
internal object HeadlightFx {
    /**
     * Posun pred stred nameraného skla (m), aby diera začínala na šošovke
     * a nie v plechu vedľa nej.
     */
    const val LAMP_EMIT_M = 0.08f

    /** Strešný reflektor na expedičnom nosiči (podiel na sprite 1472×459). */
    const val RACK_LAMP_FX = 905f / 1472f
    const val RACK_LAMP_FY = -29f / 459f

    fun reachM(highBeam: Boolean): Float = if (highBeam) 38f else 20.5f

    /**
     * Metre pred stredom karosérie, kde svieti žiarovka.
     * [headlightFx] je nameraný podiel na sprite ([SedanLayers.headlightFx]),
     * nie stred tela a nie predné koleso.
     */
    fun lampAheadM(headlightFx: Float, worldWidthM: Float): Float =
        (headlightFx - 0.5f) * worldWidthM + LAMP_EMIT_M

    fun rackLampAheadM(worldWidthM: Float): Float =
        (RACK_LAMP_FX - 0.5f) * worldWidthM

    /**
     * Svetové X začiatku cestného pásu / DstOut masky. Vždy vpredu pri lampách;
     * náklon auta posúva začiatok s nosom, nikdy pod kabínu.
     */
    fun beamStartWorldX(
        carX: Float,
        pitchRad: Float,
        headlightFx: Float,
        worldWidthM: Float
    ): Float {
        val ahead = lampAheadM(headlightFx, worldWidthM)
        return carX + ahead * cos(pitchRad).coerceAtLeast(0f)
    }

    /**
     * Horná hrana odhaleného kužeľa relatívne k lampe (kladné = dole).
     * Stretávacie idú tesne nad vodorovnicu, diaľkové vyššie – nie do celej oblohy.
     */
    fun revealUpperM(highBeam: Boolean): Float = if (highBeam) -2.95f else -1.32f

    fun revealLowerM(highBeam: Boolean): Float = if (highBeam) 3.82f else 3.32f

    /**
     * Šírka diery tesne pri lampe. Trojuholník z bodu nechal vozovku pred
     * nárazníkom v tme, kým sa kužeľ nerozšíril; lichobežník svieti od auta.
     */
    fun revealNearUpperM(highBeam: Boolean): Float = if (highBeam) -0.95f else -0.62f

    fun revealNearLowerM(highBeam: Boolean): Float = if (highBeam) 2.45f else 2.15f

    /**
     * Strešný reflektor: kužeľ z lampy dopredu-dole na vozovku.
     * Nie doska cez kulisu a nie druhý široký pás cez stretávacie.
     */
    fun rackReachM(): Float = 11.5f

    /** Ďaleká horná hrana ostáva pod vodorovnicou lampy – nesvieti do mesy. */
    fun rackRevealUpperM(): Float = 0.18f

    fun rackRevealLowerM(): Float = 2.40f

    /** Vrchol kužeľa je lampa; šírka pri zdroji je takmer nula. */
    fun rackRevealNearUpperM(): Float = 0f

    fun rackRevealNearLowerM(): Float = 0.10f

    /**
     * Koľko nočného stmavnutia sa v kuželi zruší (0 = tma ostáva, 1 = deň).
     * Pod 1, aby diera nepôsobila ako vystrihnutý denný záber.
     */
    fun veilLift(highBeam: Boolean): Float = if (highBeam) 0.86f else 0.72f

    fun revealedDay(day: Float, highBeam: Boolean, strength: Float): Float {
        val night = (1f - day).coerceIn(0f, 1f)
        val lift = veilLift(highBeam) * strength.coerceIn(0f, 1f)
        return (day + night * lift).coerceIn(0f, 1f)
    }

    fun revealNearDepth(highBeam: Boolean): Float =
        if (highBeam) GameConfig.VERGE_DEPTH * 0.50f else GameConfig.VERGE_DEPTH * 0.92f

    /** Diaľkové berú aj pás lúky pod stromami; stretávacie ostávajú na vozovke. */
    fun revealFarDepth(highBeam: Boolean): Float =
        if (highBeam) GameConfig.ROAD_DEPTH + 1.05f else GameConfig.ROAD_DEPTH + 0.22f

    fun revealStations(highBeam: Boolean): Int = if (highBeam) 11 else 7
}
