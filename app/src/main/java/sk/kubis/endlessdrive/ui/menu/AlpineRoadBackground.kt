package sk.kubis.endlessdrive.ui.menu

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Paint
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import sk.kubis.endlessdrive.R
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.sin

/** One complete seamless cycle; each stripe advances two spacings, slowly toward the viewer. */
internal const val MENU_ROAD_LOOP_MS = 12_000

@Composable
internal fun AlpineRoadBackground() {
    val resources = LocalContext.current.resources
    val bitmap = remember(resources) {
        BitmapFactory.decodeResource(resources, R.drawable.menu_alpine_evening,
            BitmapFactory.Options().apply { inScaled = false })
    }
    val scene = remember(bitmap) { AlpineScene(bitmap) }
    // Read animation state only in the draw lambda: menu text/buttons never recompose per frame.
    val phase = rememberInfiniteTransition(label = "alpine drive").animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(MENU_ROAD_LOOP_MS, easing = LinearEasing), RepeatMode.Restart),
        label = "slow forward travel")
    Canvas(Modifier.fillMaxSize()) {
        val scale = max(size.width / bitmap.width, size.height / bitmap.height)
        val left = (size.width - bitmap.width * scale) / 2f
        val top = (size.height - bitmap.height * scale) / 2f
        withTransform({
            translate(left, top)
            scale(scale, scale, pivot = androidx.compose.ui.geometry.Offset.Zero)
        }) {
            drawIntoCanvas { scene.draw(it.nativeCanvas, phase.value) }
            val w = bitmap.width.toFloat()
            val h = bitmap.height.toFloat()
            // Geometry uses the photograph's coordinates, so cropping on wide screens keeps
            // the markings anchored to the asphalt and the same vanishing point.
            for (i in 0 until 14) {
                val t = ((i + phase.value * 2f) / 14f) % 1f
                val p0 = MenuRoadFx.perspective(t)
                val p1 = MenuRoadFx.perspective((t + 0.026f).coerceAtMost(1f))
                val fade = MenuRoadFx.travelFade(t)
                val x = w * 0.505f
                val y0 = h * (0.505f + 0.495f * p0)
                val y1 = h * (0.505f + 0.495f * p1)
                scene.stripe.reset()
                scene.stripe.moveTo(x - w * 0.003f * p0, y0)
                scene.stripe.lineTo(x + w * 0.003f * p0, y0)
                scene.stripe.lineTo(x + w * 0.003f * p1, y1)
                scene.stripe.lineTo(x - w * 0.003f * p1, y1)
                scene.stripe.close()
                drawPath(scene.stripe, Color(0xFFE9B83F).copy(alpha = 0.78f * fade))
            }
            // Low-contrast sunlight stays behind the UI and follows the same seamless clock.
            val breath = (0.5f + 0.5f * sin(2.0 * PI * phase.value)).toFloat()
            val sun = Offset(w * 0.83f, h * 0.16f)
            drawCircle(Brush.radialGradient(listOf(
                Color(0xFFFFD78A).copy(alpha = 0.055f + breath * 0.018f),
                Color.Transparent), center = sun, radius = w * 0.42f),
                radius = w * 0.42f, center = sun)
            for (i in 0..2) {
                scene.lightRay.reset()
                scene.lightRay.moveTo(sun.x - w * 0.015f, sun.y)
                scene.lightRay.lineTo(sun.x + w * 0.015f, sun.y)
                scene.lightRay.lineTo(w * (0.25f + i * 0.16f), h * 0.84f)
                scene.lightRay.lineTo(w * (0.12f + i * 0.16f), h * 0.84f)
                scene.lightRay.close()
                drawPath(scene.lightRay, Brush.verticalGradient(listOf(Color.Transparent,
                    Color(0xFFFFDF9A).copy(alpha = 0.017f + breath * 0.009f),
                    Color.Transparent), startY = sun.y, endY = h * 0.84f))
            }
            // A broad, feathered veil over the distant valley, not over the controls.
            withTransform({
                translate(w * (0.50f + 0.008f * sin(2.0 * PI * phase.value).toFloat()), h * 0.51f)
                scale(1f, 0.13f, pivot = Offset.Zero)
            }) {
                drawCircle(Brush.radialGradient(listOf(
                    Color(0xFFFFE6B5).copy(alpha = 0.055f + breath * 0.015f), Color.Transparent),
                    center = Offset.Zero, radius = w * 0.29f), radius = w * 0.29f, center = Offset.Zero)
            }
        }
    }
}

/**
 * Depth-dependent photographic parallax: the sky/mountains stay still, nearby asphalt and
 * verges expand gently out from the vanishing point. Two offset passes crossfade with zero
 * weight at their wrap, avoiding a zoom-out or a visible reset. Buffers are reused per frame.
 */
private class AlpineScene(private val bitmap: Bitmap) {
    private val columns = 12
    private val rows = 32
    private val vertices = FloatArray((columns + 1) * (rows + 1) * 2)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    val stripe = Path()
    val lightRay = Path()

    fun draw(canvas: android.graphics.Canvas, phase: Float) {
        val weight = sin(PI * phase).toFloat().let { it * it }
        pass(canvas, phase, 255)
        pass(canvas, (phase + 0.5f) % 1f, ((1f - weight) * 255f).toInt())
    }

    private fun pass(canvas: android.graphics.Canvas, phase: Float, alpha: Int) {
        if (alpha == 0) return
        var index = 0
        val w = bitmap.width.toFloat()
        val h = bitmap.height.toFloat()
        for (row in 0..rows) {
            val y = row.toFloat() / rows
            val depth = ((y - 0.5f) * 2f).coerceAtLeast(0f)
            val expansion = 1f / (1f - 0.065f * phase * depth)
            for (column in 0..columns) {
                val x = column.toFloat() / columns
                vertices[index++] = w * (0.505f + (x - 0.505f) * expansion)
                vertices[index++] = h * (0.5f + (y - 0.5f) * expansion)
            }
        }
        paint.alpha = alpha
        canvas.drawBitmapMesh(bitmap, columns, rows, vertices, 0, null, 0, paint)
    }
}
