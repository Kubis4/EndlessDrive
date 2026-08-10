package sk.kubis.endlessdrive.game.world

import sk.kubis.endlessdrive.core.MathX
import sk.kubis.endlessdrive.domain.model.BiomeType
import sk.kubis.endlessdrive.domain.model.BranchStyle
import sk.kubis.endlessdrive.domain.model.BuildingType
import sk.kubis.endlessdrive.domain.model.ItemStack
import sk.kubis.endlessdrive.domain.model.RoadFeature

data class WorldBuilding(
    val id: Long,
    val type: BuildingType,
    val localX: Float,
    val loot: MutableList<ItemStack> = mutableListOf(),
    /** Zásoba v stojane – len benzínová stanica. */
    var pumpFuelL: Float = 0f,
    /** Čistota paliva v stojane. */
    var pumpPurity: Float = 1f
) {
    val looted: Boolean get() = loot.isEmpty() && pumpFuelL <= 0.05f
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
    val buildingCount: Int
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
    val label: String get() = style.label
    val hint: String get() = style.hint
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
    private val terrain: TerrainProfile
) {
    /** Budovy sa dopĺňajú až po vzniku segmentu – potrebujú jeho výškový profil. */
    val buildings: MutableList<WorldBuilding> = mutableListOf()

    val biome: BiomeType get() = style.biome
    val endWorldX: Float get() = worldOrigin + length

    /** Hĺbka rokliny pod mostom (m). */
    private val gorgeDepth = 3.4f

    fun sectionAtLocal(localX: Float): RoadSection? {
        if (sections.isEmpty()) return null
        val clamped = localX.coerceIn(0f, length - 0.01f)
        return sections.firstOrNull { it.contains(clamped) } ?: sections.last()
    }

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

    /** Terén pod cestou – na moste sa prepadne do rokliny. */
    fun groundAtWorld(worldX: Float): Float {
        val local = worldX - worldOrigin
        val base = terrain.heightAt(worldX, style, challengeAtLocal(local))
        val sec = sectionAtLocal(local) ?: return base
        if (sec.feature != RoadFeature.BRIDGE || sec.length < 1f) return base
        val t = ((local - sec.start) / sec.length).coerceIn(0f, 1f)
        val dip = MathX.approxSin(t * Math.PI.toFloat())
        return base - gorgeDepth * dip
    }

    /** Vozovka – to, po čom jazdí auto. */
    fun heightAtWorld(worldX: Float): Float {
        val local = worldX - worldOrigin
        val base = terrain.heightAt(worldX, style, challengeAtLocal(local))
        val sec = sectionAtLocal(local) ?: return base
        if (sec.feature != RoadFeature.BRIDGE || sec.length < 1f) return base
        // Mostovka je rovná lávka medzi koncami úseku.
        val t = ((local - sec.start) / sec.length).coerceIn(0f, 1f)
        val deck = MathX.lerp(deckStart(sec), deckEnd(sec), t)
        val ramp = MathX.smoothstep(0f, BRIDGE_RAMP / sec.length, t) *
            (1f - MathX.smoothstep(1f - BRIDGE_RAMP / sec.length, 1f, t))
        return MathX.lerp(base, deck, ramp)
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
        terrain.heightAt(worldOrigin + sec.start, style, challengeAtLocal(sec.start))

    private fun deckEnd(sec: RoadSection): Float =
        terrain.heightAt(worldOrigin + sec.end, style, challengeAtLocal(sec.end))

    fun heightAtLocal(localX: Float): Float = heightAtWorld(worldOrigin + localX)

    fun slopeAtLocal(localX: Float): Float {
        val d = 0.55f
        return (heightAtLocal(localX + d) - heightAtLocal(localX - d)) / (2f * d)
    }

    /** Hrboľatosť pod autom = štýl vetvy + aktuálny úsek. */
    fun bumpinessAtLocal(localX: Float): Float =
        style.bumpiness + (sectionAtLocal(localX)?.feature?.roughness ?: 0f)

    private companion object {
        const val TRANSITION = 26f
        const val BRIDGE_RAMP = 12f
    }
}
