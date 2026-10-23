package sk.kubis.endlessdrive.game.world

import sk.kubis.endlessdrive.core.GameConfig
import sk.kubis.endlessdrive.domain.model.RoadFeature
import sk.kubis.endlessdrive.domain.model.RoadPaving
import sk.kubis.endlessdrive.domain.model.RoadSurface
import kotlin.math.abs

/**
 * ROAD ASSIST hľadá rovinu s gripom – nie dielňu 5 m pred autom na tom istom
 * ílovitom kopci.
 */
internal object RescueSpotFinder {
    const val SAMPLE_STEP_M = 4f
    /** Koľko metrov pred bodom musí byť ešte rovina, aby sa dalo rozbehnúť. */
    const val START_RUN_M = 14f

    data class Sample(
        val worldX: Float,
        val slope: Float,
        val surface: RoadSurface,
        val paving: RoadPaving,
        val feature: RoadFeature,
        val occupied: Boolean
    )

    fun isTowableGround(sample: Sample, neighbors: List<Sample> = emptyList()): Boolean {
        if (!isStandable(sample)) return false
        if (abs(sample.slope) > GameConfig.TIRE_RESCUE_TOW_SLOPE) return false
        if (isMudOrIce(sample.surface)) return false
        if (neighbors.isNotEmpty() &&
            neighborhoodSlope(sample, neighbors) > GameConfig.TIRE_RESCUE_TOW_SLOPE
        ) {
            return false
        }
        return true
    }

    fun pickWorldX(originX: Float, samples: List<Sample>): Float {
        if (samples.isEmpty()) return originX
        val aheadGood = samples
            .filter { it.worldX >= originX - 0.01f && isGoodStart(it, samples) }
            .minByOrNull { it.worldX }
        if (aheadGood != null) return aheadGood.worldX

        val behindGood = samples
            .filter {
                it.worldX < originX &&
                    originX - it.worldX <= GameConfig.TIRE_RESCUE_FLAT_BEHIND_M + 0.01f &&
                    isGoodStart(it, samples)
            }
            .maxByOrNull { it.worldX }
        if (behindGood != null) return behindGood.worldX

        return samples.minByOrNull { fallbackScore(originX, it, samples) }?.worldX ?: originX
    }

    fun isGoodStart(sample: Sample, samples: List<Sample>): Boolean {
        if (!isStandable(sample)) return false
        if (isMudOrIce(sample.surface)) return false
        if (abs(sample.slope) > GameConfig.TIRE_RESCUE_FLAT_SLOPE) return false
        return neighborhoodSlope(sample, samples) <= GameConfig.TIRE_RESCUE_FLAT_SLOPE
    }

    fun neighborhoodSlope(sample: Sample, samples: List<Sample>): Float {
        val runEnd = sample.worldX + START_RUN_M
        var worst = abs(sample.slope)
        var seen = false
        for (s in samples) {
            if (s.worldX + 0.01f >= sample.worldX && s.worldX <= runEnd + 0.01f) {
                worst = maxOf(worst, abs(s.slope))
                seen = true
            }
        }
        return if (seen) worst else abs(sample.slope)
    }

    private fun isStandable(sample: Sample): Boolean =
        !sample.occupied && sample.feature != RoadFeature.BRIDGE

    private fun isMudOrIce(surface: RoadSurface): Boolean =
        surface == RoadSurface.MUD || surface == RoadSurface.ICE

    private fun fallbackScore(originX: Float, sample: Sample, samples: List<Sample>): Float {
        val slope = neighborhoodSlope(sample, samples)
        val surface = when (sample.surface) {
            RoadSurface.MUD, RoadSurface.ICE -> 0.50f
            RoadSurface.SAND, RoadSurface.SLUSH, RoadSurface.WATER -> 0.20f
            RoadSurface.GRAVEL -> 0.06f
            RoadSurface.ASPHALT -> 0f
        }
        val paving = when {
            sample.paving == RoadPaving.ASPHALT ||
                sample.paving == RoadPaving.CRACKED ||
                sample.paving == RoadPaving.CONCRETE -> 0f
            sample.paving == RoadPaving.DIRT ||
                sample.paving == RoadPaving.GRAVEL_ROAD -> 0.04f
            sample.paving.winter -> 0.28f
            else -> 0.12f
        }
        val bridge = if (sample.feature == RoadFeature.BRIDGE) 8f else 0f
        val occupied = if (sample.occupied) 6f else 0f
        val behind = if (sample.worldX < originX) 0.40f else 0f
        val dist = abs(sample.worldX - originX) * 0.0009f
        return slope + surface + paving + bridge + occupied + behind + dist
    }
}
