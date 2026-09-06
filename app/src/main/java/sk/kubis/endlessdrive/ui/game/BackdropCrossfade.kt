package sk.kubis.endlessdrive.ui.game

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.DrawScope

/** Blend complete opaque scenes, so holes in incoming sprites cannot retain old scenery. */
internal fun DrawScope.drawBackdropCrossfade(
    amount: Float,
    blendPaint: Paint,
    from: DrawScope.() -> Unit,
    to: DrawScope.() -> Unit
) {
    val t = amount.coerceIn(0f, 1f)
    if (t >= 1f) {
        to()
        return
    }
    from()
    if (t <= 0f) return
    blendPaint.alpha = t
    val canvas = drawContext.canvas
    canvas.saveLayer(Rect(Offset.Zero, size), blendPaint)
    try {
        to()
    } finally {
        canvas.restore()
    }
}
