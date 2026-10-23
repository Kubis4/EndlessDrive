package sk.kubis.endlessdrive.game.event

/** Búrka, ktorá sa pohybuje po trase za hráčom namiesto časovaného HUD debuffu. */
enum class PursuitStormKind(
    val label: String,
    val speedMps: Float
) {
    SAND("SANDSTORM", 15.5f),
    DUST("DUST STORM", 14.5f),
    THUNDER("THUNDERSTORM", 16.5f),
    BLIZZARD("BLIZZARD", 13.5f)
}

data class ActivePursuitStorm(
    val kind: PursuitStormKind,
    var gapM: Float,
    var ageS: Float = 0f,
    var exposureS: Float = 0f
)
