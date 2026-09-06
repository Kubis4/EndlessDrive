package sk.kubis.endlessdrive.ui.game

import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.toArgb

/**
 * [ColorFilter.tint] wraps a native Android filter. Creating one per tile per
 * frame is the usual source of `NativeAlloc concurrent mark compact GC`.
 */
internal class TintFilterSlot(private val mode: BlendMode) {
    private var argb = 0
    private var filter: ColorFilter? = null

    fun of(color: Color): ColorFilter {
        val packed = color.toArgb()
        val hit = filter
        if (hit != null && packed == argb) return hit
        argb = packed
        return ColorFilter.tint(color, mode).also { filter = it }
    }
}
