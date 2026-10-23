package sk.kubis.endlessdrive.domain.model

/**
 * In-game dial cluster look. Names are English HUD labels.
 */
enum class GaugeStyle {
    CLASSIC,
    STRIP;

    val displayName: String
        get() = when (this) {
            CLASSIC -> "Classic"
            STRIP -> "Strip"
        }

    val detail: String
        get() = when (this) {
            CLASSIC -> "Analog rings with needles."
            STRIP -> "Minimal digital bars."
        }

    fun next(): GaugeStyle = entries[(ordinal + 1) % entries.size]

    companion object {
        fun fromStored(raw: String?): GaugeStyle =
            if (raw.equals(STRIP.name, ignoreCase = true)) STRIP else CLASSIC
    }
}
