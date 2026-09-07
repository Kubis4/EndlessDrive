package sk.kubis.endlessdrive.ui.game

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import sk.kubis.endlessdrive.domain.model.RoadPaving
import sk.kubis.endlessdrive.domain.model.RoadSurface
import java.util.Random

enum class MaterialKind {
    ASPHALT, CRACKS, CONCRETE, DIRT, GRAVEL, SAND, SNOW, ICE, WATER,
    GRASS, LEAVES, NEEDLES, BARK, STONE, PAPER, BRICK, PLANKS, METAL;

    companion object {
        fun paving(p: RoadPaving): MaterialKind = when (p) {
            RoadPaving.ASPHALT -> ASPHALT
            RoadPaving.CRACKED -> CRACKS
            RoadPaving.CONCRETE -> CONCRETE
            RoadPaving.DIRT -> DIRT
            RoadPaving.GRAVEL_ROAD -> GRAVEL
            RoadPaving.SAND_TRACK -> SAND
            RoadPaving.SNOW, RoadPaving.PACKED_SNOW -> SNOW
        }
        fun surface(s: RoadSurface): MaterialKind = when (s) {
            RoadSurface.MUD -> DIRT
            RoadSurface.SAND -> SAND
            RoadSurface.WATER -> WATER
            RoadSurface.GRAVEL -> GRAVEL
            RoadSurface.ICE -> ICE
            RoadSurface.SLUSH -> SNOW
            RoadSurface.ASPHALT -> ASPHALT
        }
    }
}

/** Small, palette-neutral illustrated material tiles. Built once, never randomised per frame. */
object MaterialTextures {
    const val SIZE = 256
    private val cache = mutableMapOf<MaterialKind, Bitmap>()
    @Synchronized fun bitmap(kind: MaterialKind): Bitmap = cache.getOrPut(kind) { create(kind) }

    private fun create(kind: MaterialKind): Bitmap {
        val image = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(image)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val rng = Random(4919L + kind.ordinal * 7919L)
        val path = Path()
        fun tone(light: Boolean, alpha: Int) {
            paint.color = if (light) Color.argb(alpha, 244, 239, 218) else Color.argb(alpha, 18, 24, 23)
        }
        // Every mark wraps at both borders. REPEAT never meets a cut-off stroke.
        fun wrap(x: Float, y: Float, margin: Float, draw: (Float, Float) -> Unit) {
            draw(x, y)
            val dx = if (x < margin) SIZE.toFloat() else if (x > SIZE - margin) -SIZE.toFloat() else 0f
            val dy = if (y < margin) SIZE.toFloat() else if (y > SIZE - margin) -SIZE.toFloat() else 0f
            if (dx != 0f) draw(x + dx, y)
            if (dy != 0f) draw(x, y + dy)
            if (dx != 0f && dy != 0f) draw(x + dx, y + dy)
        }
        // Faint small tonal patches; never large spots across the sky or road.
        repeat(if (kind == MaterialKind.PAPER) 0 else 24) {
            val x = rng.nextFloat() * SIZE; val y = rng.nextFloat() * SIZE
            val r = 2f + rng.nextFloat() * 5f
            tone(rng.nextBoolean(), 3 + rng.nextInt(5))
            wrap(x, y, r) { a, b -> canvas.drawOval(a - r, b - r * 0.55f, a + r, b + r * 0.55f, paint) }
        }
        val count = when (kind) {
            MaterialKind.LEAVES, MaterialKind.NEEDLES, MaterialKind.GRASS -> 600
            MaterialKind.WATER, MaterialKind.ICE, MaterialKind.BARK -> 100
            MaterialKind.PAPER, MaterialKind.ASPHALT, MaterialKind.CONCRETE -> 2400
            else -> 1500
        }
        repeat(count) {
            val x = rng.nextFloat() * SIZE; val y = rng.nextFloat() * SIZE
            val r = 0.25f + rng.nextFloat() * when (kind) {
                MaterialKind.GRAVEL, MaterialKind.STONE -> 2.0f
                MaterialKind.LEAVES -> 2.4f
                MaterialKind.PAPER -> 0.3f
                MaterialKind.ASPHALT, MaterialKind.CONCRETE, MaterialKind.SNOW -> 0.7f
                else -> 1.1f
            }
            tone(rng.nextBoolean(), if (kind == MaterialKind.PAPER) 5 + rng.nextInt(14) else 16 + rng.nextInt(44))
            wrap(x, y, 22f) { a, b ->
                when (kind) {
                    MaterialKind.GRASS, MaterialKind.NEEDLES -> {
                        paint.strokeWidth = 0.8f
                        canvas.drawLine(a - r, b + 1f, a + r * 0.5f, b - 1.5f - r, paint)
                    }
                    MaterialKind.LEAVES -> canvas.drawOval(a - r, b - r * 0.4f, a + r, b + r * 0.6f, paint)
                    MaterialKind.BARK -> {
                        paint.strokeWidth = 0.8f + r
                        canvas.drawLine(a, b - 15f, a + 1f, b + 15f, paint)
                    }
                    MaterialKind.WATER, MaterialKind.ICE -> {
                        paint.strokeWidth = 0.7f + r * 0.3f
                        canvas.drawLine(a - 12f, b, a + 12f, b - 1.5f, paint)
                    }
                    MaterialKind.SAND -> canvas.drawOval(a - r * 2f, b - r * 0.4f, a + r * 2f, b + r * 0.4f, paint)
                    else -> canvas.drawOval(a - r, b - r * 0.7f, a + r, b + r * 0.7f, paint)
                }
            }
        }
        if (kind == MaterialKind.CRACKS || kind == MaterialKind.DIRT || kind == MaterialKind.ICE) {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = if (kind == MaterialKind.ICE) 0.8f else 1.05f
            tone(kind == MaterialKind.ICE, 56)
            repeat(if (kind == MaterialKind.CRACKS) 7 else 5) {
                val x = rng.nextFloat() * SIZE; val y = rng.nextFloat() * SIZE
                val span = 10f + rng.nextFloat() * 14f
                path.reset(); path.moveTo(x - span, y - 6f)
                path.lineTo(x - span * 0.25f, y - 1f); path.lineTo(x + span * 0.15f, y - 4f)
                path.lineTo(x + span, y + 8f)
                path.moveTo(x + span * 0.15f, y - 4f); path.lineTo(x + span * 0.35f, y - 11f)
                for (dy in -1..1) for (dx in -1..1) {
                    canvas.save(); canvas.translate(dx * SIZE.toFloat(), dy * SIZE.toFloat())
                    canvas.drawPath(path, paint); canvas.restore()
                }
            }
        }
        // Architectural tiles divide the full tile exactly, including staggered joints.
        if (kind == MaterialKind.BRICK || kind == MaterialKind.PLANKS || kind == MaterialKind.METAL) {
            paint.style = Paint.Style.FILL
            val step = if (kind == MaterialKind.METAL) 16f else 32f
            var row = 0
            var y = 0f
            while (y < SIZE) {
                tone(false, 62)
                if (kind == MaterialKind.METAL) {
                    canvas.drawRect(y, 0f, y + 3f, SIZE.toFloat(), paint)
                    tone(true, 50)
                    canvas.drawRect(y + 3f, 0f, y + 5f, SIZE.toFloat(), paint)
                } else {
                    canvas.drawRect(0f, y, SIZE.toFloat(), y + 2f, paint)
                    tone(true, 32)
                    canvas.drawRect(0f, y + 2f, SIZE.toFloat(), y + 3f, paint)
                    if (kind == MaterialKind.BRICK) {
                        var x = if (row % 2 == 0) 0f else -32f
                        while (x < SIZE) {
                            tone(false, 55)
                            canvas.drawRect(x, y, x + 2f, y + step, paint)
                            tone(rng.nextBoolean(), 12 + rng.nextInt(22))
                            canvas.drawRect(x + 3f, y + 4f, x + 63f, y + step - 1f, paint)
                            x += 64f
                        }
                    } else {
                        repeat(9) {
                            val x = rng.nextFloat() * SIZE
                            val gy = y + 5f + rng.nextFloat() * 23f
                            tone(false, 24)
                            paint.strokeWidth = 0.7f
                            wrap(x, gy, 18f) { a, b -> canvas.drawLine(a - 16f, b, a + 16f, b + 1f, paint) }
                        }
                    }
                }
                row++
                y += step
            }
        }
        return image
    }
}

/** Reuses paint and matrices; projects each texture cell to the actual sloping surface. */
class MaterialPainter {
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val matrix = Matrix()
    private val quad = Path()
    private val src = FloatArray(8)
    private val dst = FloatArray(8)
    private val shaders = mutableMapOf<MaterialKind, BitmapShader>()
    private fun shader(kind: MaterialKind) = shaders.getOrPut(kind) {
        BitmapShader(MaterialTextures.bitmap(kind), Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
    }

    /** u/v are metres in the world (or local object coordinates), never screen scrolling. */
    fun quad(scope: DrawScope, kind: MaterialKind, alpha: Float,
        x0: Float, y0: Float, x1: Float, y1: Float,
        x2: Float, y2: Float, x3: Float, y3: Float,
        u0: Float, u1: Float, v0: Float, v1: Float, period: Float = 5f) {
        if (alpha <= 0f) return
        val unit = MaterialTextures.SIZE / period
        val left = ((u0 % period + period) % period) * unit
        val right = left + (u1 - u0) * unit
        src[0] = left; src[1] = v0 * unit; src[2] = right; src[3] = v0 * unit
        src[4] = right; src[5] = v1 * unit; src[6] = left; src[7] = v1 * unit
        dst[0] = x0; dst[1] = y0; dst[2] = x1; dst[3] = y1
        dst[4] = x2; dst[5] = y2; dst[6] = x3; dst[7] = y3
        if (!matrix.setPolyToPoly(src, 0, dst, 0, 4)) return
        val shader = shader(kind); shader.setLocalMatrix(matrix)
        paint.shader = shader; paint.alpha = (alpha.coerceIn(0f, 1f) * 255).toInt()
        quad.rewind(); quad.moveTo(x0, y0); quad.lineTo(x1, y1)
        quad.lineTo(x2, y2); quad.lineTo(x3, y3); quad.close()
        scope.drawIntoCanvas { it.nativeCanvas.drawPath(quad, paint) }
    }

    fun shape(scope: DrawScope, path: androidx.compose.ui.graphics.Path, kind: MaterialKind,
        x: Float, y: Float, tilePixels: Float, alpha: Float) {
        matrix.setScale(tilePixels / MaterialTextures.SIZE, tilePixels / MaterialTextures.SIZE)
        matrix.postTranslate(x, y)
        val shader = shader(kind); shader.setLocalMatrix(matrix)
        paint.shader = shader; paint.alpha = (alpha.coerceIn(0f, 1f) * 255).toInt()
        scope.drawIntoCanvas { it.nativeCanvas.drawPath(path.asAndroidPath(), paint) }
    }
}
