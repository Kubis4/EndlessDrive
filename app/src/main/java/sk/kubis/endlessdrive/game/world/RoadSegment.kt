package sk.kubis.endlessdrive.game.world

import sk.kubis.endlessdrive.core.GameConfig
import sk.kubis.endlessdrive.core.MathX
import sk.kubis.endlessdrive.domain.model.BiomeType
import sk.kubis.endlessdrive.domain.model.BranchStyle
import sk.kubis.endlessdrive.domain.model.BuildingType
import sk.kubis.endlessdrive.domain.model.ItemStack
import sk.kubis.endlessdrive.domain.model.FuelKind
import sk.kubis.endlessdrive.domain.model.RoadPaving
import sk.kubis.endlessdrive.domain.model.RoadFeature
import sk.kubis.endlessdrive.domain.model.RoadSurface

data class WorldBuilding(
    val id: Long,
    val type: BuildingType,
    val localX: Float,
    val loot: MutableList<ItemStack> = mutableListOf(),
    /** Zásoba v stojane – len benzínová stanica. */
    /** Benzín v stojane. */
    var pumpFuelL: Float = 0f,
    /** Diesel má vlastnú hadicu a vlastnú zásobu. */
    var pumpDieselL: Float = 0f,
    /** Čistota paliva v stojane. */
    var pumpPurity: Float = 1f,
    /** Druh paliva označený na stojane. */
    var pumpFuelKind: FuelKind = FuelKind.PETROL,
    /** Relay checkpoint na míľniku – vždy stojí za zastavenie. */
    val landmark: Boolean = false,
    /** Poradie uzla v meta-cieli; -1 znamená starý/nespárovaný save. */
    val relayIndex: Int = -1,
    /** Stav opravy v aktuálnej jazde. */
    var relayRestored: Boolean = false
) {
    val looted: Boolean get() = loot.isEmpty() && pumpFuelL <= 0.05f && pumpDieselL <= 0.05f
}

/**
 * Naplavenina na ceste – bahno, piesok, voda, štrk. Je vidieť dopredu,
 * takže sa dá pribrzdiť alebo si na ňu vziať rozbeh.
 */
class SurfacePatch(
    val surface: RoadSurface,
    val start: Float,
    val end: Float
) {
    val length: Float get() = end - start
    fun contains(localX: Float): Boolean = localX >= start && localX < end
}

/** Konkrétny úsek trate v segmente (lokálne súradnice). */
class RoadSection(
    val feature: RoadFeature,
    val start: Float,
    val end: Float
) {
    val length: Float get() = end - start
    fun contains(localX: Float): Boolean = localX >= start && localX < end
}

/**
 * Vopred vypočítaný plán segmentu. Vzniká zo seedu, takže križovatka
 * môže o vetve ukázať pravdu ešte predtým, než sa segment postaví.
 */
data class SegmentPlan(
    val seed: Long,
    val style: BranchStyle,
    val length: Float,
    val features: List<RoadFeature>,
    val buildingCount: Int,
    /** Z čoho je vetva postavená – vidno to hneď, ako sa na ňu vojde. */
    val paving: RoadPaving = RoadPaving.ASPHALT
) {
    val lengthKm: Float get() = length / 1000f

    /** 0..1 – hrubý odhad, ako nepríjemná vetva bude. */
    val risk: Float
        get() {
            val terrain = features.sumOf {
                (it.roughness * 0.6f + (it.hillMul - 0.55f).coerceAtLeast(0f) * 0.4f).toDouble()
            }.toFloat() / features.size.coerceAtLeast(1)
            return (terrain * 0.7f + (style.fuelDrainMul - 1f) * 0.9f).coerceIn(0f, 1f)
        }

    val featureSummary: String
        get() = features.map { it.displayName }.distinct().joinToString(" · ")
}

data class BranchChoice(
    val id: Int,
    val plan: SegmentPlan
) {
    val style: BranchStyle get() = plan.style
    val segmentSeed: Long get() = plan.seed
    /** Zasneženú vetvu vidno už z rázcestia – biela cesta sa nedá prehliadnuť. */
    val winter: Boolean get() = plan.paving.winter
    val label: String get() = if (winter) "${style.label} ❄" else style.label
    val hint: String get() = style.hint
}

/** Prostredie v konkrétnom bode cesty; [amount] 0..1 mieša [from] do [to]. */
data class BiomeBlend(
    val from: BiomeType,
    val to: BiomeType,
    val amount: Float
) {
    val dominant: BiomeType get() = if (amount < 0.5f) from else to
}

/**
 * Úsek cesty medzi križovatkami. Skladá sa z [RoadSection] – rovinky, kopce,
 * serpentíny, rozbitá cesta a mosty. Výška vychádza z [TerrainProfile],
 * úsek ju len moduluje (a most ju nahradí rovnou mostovkou).
 */
class RoadSegment(
    val seed: Long,
    val style: BranchStyle,
    val length: Float,
    val sections: List<RoadSection>,
    val choices: List<BranchChoice>,
    var worldOrigin: Float,
    private val terrain: Terrain,
    val paving: RoadPaving = RoadPaving.ASPHALT
) {
    /** Budovy sa dopĺňajú až po vzniku segmentu – potrebujú jeho výškový profil. */
    val buildings: MutableList<WorldBuilding> = mutableListOf()

    /** Naplaveniny na vozovke; zoradené podľa [SurfacePatch.start]. */
    val patches: MutableList<SurfacePatch> = mutableListOf()

    val biome: BiomeType get() = style.biome
    val endWorldX: Float get() = worldOrigin + length
    val nextStyle: BranchStyle? get() = choices.firstOrNull()?.style
    val transitionLength: Float
        get() = MathX.lerp(
            GameConfig.BIOME_TRANSITION_MIN,
            GameConfig.BIOME_TRANSITION_MAX,
            MathX.hash01(seed.toInt(), TRANSITION_SALT)
        ).coerceAtMost(length * 0.72f)
    val transitionStartWorldX: Float get() = endWorldX - transitionLength

    fun biomeBlendAtWorld(worldX: Float): BiomeBlend {
        val next = nextStyle ?: return BiomeBlend(biome, biome, 0f)
        val amount = MathX.smoothstep(transitionStartWorldX, endWorldX, worldX)
        return BiomeBlend(biome, next.biome, amount)
    }

    /**
     * Hĺbka rokliny pod mostom (m). Pri 3.4 m sedela mostovka prakticky na
     * teréne a most nebolo od cesty rozoznať – roklina musí byť priepasť.
     */
    private val gorgeDepth = 8.5f

    fun sectionAtLocal(localX: Float): RoadSection? {
        if (sections.isEmpty()) return null
        val clamped = localX.coerceIn(0f, length - 0.01f)
        return sections.firstOrNull { it.contains(clamped) } ?: sections.last()
    }

    /**
     * Stojí na [worldX] budova (do vzdialenosti [clearanceM] od jej stredu)?
     *
     * Kulisy sa podľa toho miestu vyhnú. Budovy sa kreslia až po nich, takže
     * strom za garážou jej prerastal cez strechu.
     */
    fun buildingOccupies(worldX: Float, clearanceM: Float): Boolean =
        buildings.any { kotlin.math.abs(worldX - (worldOrigin + it.localX)) < clearanceM }

    fun featureAtWorld(worldX: Float): RoadFeature =
        sectionAtLocal(worldX - worldOrigin)?.feature ?: RoadFeature.STRAIGHT

    /**
     * Členitosť plynule prechádza medzi úsekmi – tvrdý skok by na hranici
     * spravil schod v teréne a auto by nadskočilo.
     */
    private fun challengeAtLocal(localX: Float): Float {
        if (sections.isEmpty()) return 1f
        val idx = sections.indexOfFirst { it.contains(localX.coerceIn(0f, length - 0.01f)) }
            .let { if (it < 0) sections.lastIndex else it }
        val sec = sections[idx]
        val cur = sec.feature.hillMul
        val half = TRANSITION * 0.5f
        // Prechod je symetrický okolo hranice – inak by z jednej strany vyšlo
        // iné číslo ako z druhej a v teréne by vznikol zráz.
        if (localX < sec.start + half) {
            val prev = sections.getOrNull(idx - 1)?.feature?.hillMul ?: return cur
            return MathX.lerp(prev, cur, MathX.smoothstep(sec.start - half, sec.start + half, localX))
        }
        if (localX > sec.end - half) {
            val next = sections.getOrNull(idx + 1)?.feature?.hillMul ?: return cur
            return MathX.lerp(cur, next, MathX.smoothstep(sec.end - half, sec.end + half, localX))
        }
        return cur
    }

    /**
     * Terén pod cestou – na moste sa prepadne do rokliny.
     *
     * Roklina používa presne ten istý priebeh ako mostovka. Kým mala vlastný
     * sínus, terén sa prepadal skôr, než sa vozovka narovnala – pri vjazde
     * a výjazde tak vznikala nepekná šikmá ostroha pod začiatkom mosta.
     */
    fun groundAtWorld(worldX: Float): Float {
        val local = worldX - worldOrigin
        val base = blendedTerrainHeight(worldX, challengeAtLocal(local))
        val sec = sectionAtLocal(local) ?: return base
        if (sec.feature != RoadFeature.BRIDGE || sec.length < 1f) return base
        return base - gorgeDepth * deckRamp(sec, local)
    }

    /** Podiel, akým je na danom mieste v platnosti mostovka (0 = terén, 1 = lávka). */
    private fun deckRamp(sec: RoadSection, local: Float): Float {
        val t = ((local - sec.start) / sec.length).coerceIn(0f, 1f)
        val edge = (BRIDGE_RAMP / sec.length).coerceIn(0.05f, 0.45f)
        return MathX.smoothstep(0f, edge, t) * (1f - MathX.smoothstep(1f - edge, 1f, t))
    }

    /** Vozovka – to, po čom jazdí auto. */
    fun heightAtWorld(worldX: Float): Float {
        val local = worldX - worldOrigin
        val base = blendedTerrainHeight(worldX, challengeAtLocal(local))
        val sec = sectionAtLocal(local) ?: return base
        if (sec.feature != RoadFeature.BRIDGE || sec.length < 1f) return base
        // Mostovka je rovná lávka medzi koncami úseku.
        val t = ((local - sec.start) / sec.length).coerceIn(0f, 1f)
        val deck = MathX.lerp(deckStart(sec), deckEnd(sec), t)
        return MathX.lerp(base, deck, deckRamp(sec, local))
    }

    /** Výška mostovky nad terénom v danom bode (0 = žiadny most). */
    fun bridgeClearanceAtWorld(worldX: Float): Float {
        val local = worldX - worldOrigin
        val sec = sectionAtLocal(local) ?: return 0f
        if (sec.feature != RoadFeature.BRIDGE) return 0f
        return (heightAtWorld(worldX) - groundAtWorld(worldX)).coerceAtLeast(0f)
    }

    // Konce mostovky musia sedieť presne na terén, inak vznikne schod.
    private fun deckStart(sec: RoadSection): Float =
        blendedTerrainHeight(worldOrigin + sec.start, challengeAtLocal(sec.start))

    private fun deckEnd(sec: RoadSection): Float =
        blendedTerrainHeight(worldOrigin + sec.end, challengeAtLocal(sec.end))

    fun heightAtLocal(localX: Float): Float = heightAtWorld(worldOrigin + localX)

    /** Posledné kilometre regiónu už tvarujú kopce nasledujúcej oblasti. */
    private fun blendedTerrainHeight(worldX: Float, challenge: Float): Float {
        val from = terrain.heightAt(worldX, style, challenge)
        val next = nextStyle ?: return from
        val amount = biomeBlendAtWorld(worldX).amount
        if (amount <= 0f) return from
        val nextChallenge = MathX.lerp(challenge, RoadFeature.STRAIGHT.hillMul, amount)
        return MathX.lerp(from, terrain.heightAt(worldX, next, nextChallenge), amount)
    }

    fun slopeAtLocal(localX: Float): Float {
        val d = 0.55f
        return (heightAtLocal(localX + d) - heightAtLocal(localX - d)) / (2f * d)
    }

    /** Povrch pod kolesami – asfalt, ak tam nič nenaplavilo. */
    fun surfaceAtLocal(localX: Float): RoadSurface =
        patches.firstOrNull { it.contains(localX) }?.surface ?: RoadSurface.ASPHALT

    /** Najbližšia naplavenina pred autom (na varovanie a vykreslenie). */
    fun patchAheadOfLocal(localX: Float, within: Float): SurfacePatch? =
        patches.firstOrNull { it.start > localX && it.start - localX <= within }

    /** Hrboľatosť pod autom = štýl vetvy + aktuálny úsek. */
    fun bumpinessAtLocal(localX: Float): Float {
        val worldX = worldOrigin + localX
        val next = nextStyle
        val base = if (next == null) style.bumpiness else MathX.lerp(
            style.bumpiness,
            next.bumpiness,
            biomeBlendAtWorld(worldX).amount
        )
        val sectionRoughness = sectionAtLocal(localX)?.feature?.roughness ?: 0f
        // Každý nový región začína rovinkou. Poslednú lokálnu hrboľatosť
        // preto pred švom stíšime, aby grip ani kamera na hranici neskočili.
        val seam = MathX.smoothstep(length - SEAM_SMOOTHING, length, localX)
        return base + sectionRoughness * (1f - seam)
    }

    private companion object {
        const val TRANSITION = 26f
        const val SEAM_SMOOTHING = 90f
        const val BRIDGE_RAMP = 12f
        const val TRANSITION_SALT = 0x4B10
    }
}
