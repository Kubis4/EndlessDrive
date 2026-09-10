package sk.kubis.endlessdrive.ui.game

import sk.kubis.endlessdrive.domain.model.BiomeType

enum class BuildingSeason {
    DEFAULT, AUTUMN, WINTER;

    companion object {
        /** FOREST_ALIVE uses the authored autumn backdrop; snow takes precedence. */
        fun forEnvironment(biome: BiomeType, winterAmount: Float): BuildingSeason = when {
            biome == BiomeType.ALPINE || winterAmount > 0f -> WINTER
            biome == BiomeType.FOREST_ALIVE -> AUTUMN
            else -> DEFAULT
        }
    }
}
