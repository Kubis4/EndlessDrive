package sk.kubis.endlessdrive.game

import sk.kubis.endlessdrive.domain.model.ComponentSlot

enum class VisualDamageBurstKind {
    TYRE_BLOWOUT,
    ROCK_STRIKE,
    OIL_SPLASH
}

class VisualDamageBurst internal constructor() {
    var kind: VisualDamageBurstKind = VisualDamageBurstKind.ROCK_STRIKE
        internal set
    var startedAtSec: Float = Float.NEGATIVE_INFINITY
        internal set
    var tireSlot: ComponentSlot? = null
        internal set
    /** Extra metre po +X; protivietor posunie kameň ešte pred nárazník. */
    var alongM: Float = 0f
        internal set
}

/**
 * Malý kruhový buffer. Udalosti vznikajú zriedka a render cez ne iteruje bez
 * snapshotov, filtrovania a alokácií. Nezapisuje sa do save-u.
 */
class VisualDamageBurstBuffer(val capacity: Int = 4) {
    private val entries = Array(capacity.coerceAtLeast(1)) { VisualDamageBurst() }
    private var next = 0

    fun add(
        kind: VisualDamageBurstKind,
        nowSec: Float,
        tireSlot: ComponentSlot? = null,
        alongM: Float = 0f
    ) {
        val entry = entries[next]
        entry.kind = kind
        entry.startedAtSec = nowSec
        entry.tireSlot = tireSlot
        entry.alongM = alongM
        next = (next + 1) % entries.size
    }

    fun entryAt(index: Int): VisualDamageBurst? =
        entries.getOrNull(index)?.takeIf { it.startedAtSec.isFinite() }

    companion object {
        const val DURATION_SEC = 0.72f

        fun normalizedAge(
            burst: VisualDamageBurst,
            nowSec: Float,
            durationSec: Float = DURATION_SEC
        ): Float? {
            if (durationSec <= 0f) return null
            val age = (nowSec - burst.startedAtSec) / durationSec
            return age.takeIf { it in 0f..1f }
        }
    }
}
