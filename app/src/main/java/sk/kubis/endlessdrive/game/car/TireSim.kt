package sk.kubis.endlessdrive.game.car

import sk.kubis.endlessdrive.domain.model.ItemCatalog
import sk.kubis.endlessdrive.domain.model.ItemDef
import sk.kubis.endlessdrive.domain.model.RoadFeature
import sk.kubis.endlessdrive.domain.model.RoadSurface

/** Stav pneumatiky na náprave – nafuknutá, defekt, alebo roztrhnutá na ráfik. */
enum class TireInjury {
    INFLATED,
    PUNCTURED,
    SHREDDED
}

/**
 * Ako guma znáša ostré veci na ceste. Sport a ojazdené sú tenké;
 * off-road prežije konáre, ktoré sportovú zničia.
 */
object TireSim {

    fun hazardMul(def: ItemDef): Float = when (def.id) {
        ItemCatalog.TIRE_OFFROAD.id -> 0.25f
        ItemCatalog.TIRE_WINTER.id -> 0.62f
        ItemCatalog.TIRE.id -> 0.82f
        ItemCatalog.TIRE_SPORT.id -> 1.48f
        ItemCatalog.TIRE_POOR.id -> 1.62f
        else -> 1f
    }

    /**
     * Relatívne riziko defektu za daných podmienok. 0 = pokojná cesta,
     * nad ~1 už treba spomaliť alebo vymeniť gumy.
     */
    fun risk(
        def: ItemDef,
        health: Float,
        speedMs: Float,
        debris: Boolean,
        surface: RoadSurface,
        feature: RoadFeature,
        roughness: Float
    ): Float {
        val speed = (kotlin.math.abs(speedMs) / 14f).coerceIn(0f, 2.2f)
        if (speed < 0.35f) return 0f
        val surfaceHaz = when {
            debris -> 1.75f
            surface == RoadSurface.GRAVEL || feature == RoadFeature.BROKEN -> 1.35f
            surface == RoadSurface.ICE -> 0.85f
            roughness > 0.35f -> 1.05f
            else -> 0.28f
        }
        val worn = 1.15f + (1f - health.coerceIn(0.05f, 1f)) * 0.7f
        return speed * surfaceHaz * hazardMul(def) * worn
    }

    /** Pri vysokom riziku sa guma skôr roztrhne, než ostane na defekte. */
    fun shredChance(
        def: ItemDef,
        speedMs: Float,
        debris: Boolean,
        alreadyPunctured: Boolean
    ): Float {
        val fast = kotlin.math.abs(speedMs) > 16f
        val base = when (def.id) {
            ItemCatalog.TIRE_OFFROAD.id -> 0.08f
            ItemCatalog.TIRE_WINTER.id -> 0.12f
            ItemCatalog.TIRE.id -> 0.20f
            ItemCatalog.TIRE_SPORT.id -> if (fast && debris) 0.55f else 0.24f
            ItemCatalog.TIRE_POOR.id -> if (debris) 0.48f else 0.28f
            else -> 0.22f
        }
        return (if (alreadyPunctured) base + 0.35f else base).coerceIn(0.05f, 0.85f)
    }

    /**
     * Pravdepodobnosť, že za [dt] sekúnd guma chytí defekt.
     * [risk] ~1 je už nebezpečná jazda; šport po konároch ide ďaleko nad to.
     */
    fun punctureChance(risk: Float, dt: Float): Float {
        if (risk < 0.18f) return 0f
        return (risk * 0.016f * dt).coerceIn(0f, 0.42f)
    }

    /**
     * Defekt až pri opotrebovaní pod [PUNCTURE_HEALTH_MAX] — nad tým
     * štrk a konáre gumu len žerú, nedierujú ju.
     */
    fun punctureChance(risk: Float, dt: Float, health: Float): Float {
        if (!canPuncture(health)) return 0f
        return punctureChance(risk, dt)
    }

    /** true = táto guma je dosť zodratá na to, aby chytla defekt. */
    fun canPuncture(health: Float): Boolean = health < PUNCTURE_HEALTH_MAX

    const val PUNCTURE_HEALTH_MAX = 0.20f
}
