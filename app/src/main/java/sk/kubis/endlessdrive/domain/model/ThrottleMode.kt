package sk.kubis.endlessdrive.domain.model

/**
 * Ako hráč dávkuje plyn. Pedál je binárny (držať = plný plyn),
 * slide je zvislý ťah od podlahy nahor – dá sa perieť a menej pretáčať.
 */
enum class ThrottleMode {
    BINARY,
    SLIDE;

    companion object {
        fun fromStored(raw: String?): ThrottleMode =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: BINARY
    }
}

/** Mapovanie prsta na zvislej dráhe: spodok = 0, vrch = 1. */
object ThrottleSlide {
    fun amount(pointerY: Float, trackTop: Float, trackBottom: Float): Float {
        val span = (trackBottom - trackTop).coerceAtLeast(1f)
        return ((trackBottom - pointerY) / span).coerceIn(0f, 1f)
    }
}
