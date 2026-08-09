package sk.kubis.endlessdrive.ui.game

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Shader
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import sk.kubis.endlessdrive.R
import sk.kubis.endlessdrive.domain.model.BiomeType
import kotlin.random.Random

/**
 * Pozadia, textúry zeme a vrstvy sedanu (rozobranie / montáž).
 */
class GameAssets(context: Context) {
    private val app = context.applicationContext

    val bgDay: ImageBitmap = decode(R.drawable.bg_day_mountains)
    val bgNight: ImageBitmap = decode(R.drawable.bg_night)
    val bgLake: ImageBitmap = decode(R.drawable.bg_lake)

    val grassTex: Bitmap = proceduralGrass()
    val dirtTex: Bitmap = proceduralDirt()

    val sedan = SedanLayers(decodeBitmap(R.drawable.sedan_full))

    fun backgroundFor(biome: BiomeType): ImageBitmap = when (biome) {
        BiomeType.RURAL -> bgDay
        BiomeType.INDUSTRIAL -> bgLake
        BiomeType.WASTELAND -> bgNight
    }

    private fun decode(resId: Int): ImageBitmap = decodeBitmap(resId).asImageBitmap()

    private fun decodeBitmap(resId: Int): Bitmap =
        BitmapFactory.decodeResource(app.resources, resId)
            ?: Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)

    private fun proceduralGrass(): Bitmap {
        val size = 64
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val rnd = Random(11)
        for (y in 0 until size) {
            for (x in 0 until size) {
                val n = rnd.nextFloat()
                val g = (70 + n * 45).toInt()
                val r = (40 + n * 25).toInt()
                val b = (28 + n * 18).toInt()
                bmp.setPixel(x, y, (0xFF shl 24) or (r shl 16) or (g shl 8) or b)
            }
        }
        // jemná tráva – čiarky
        for (i in 0 until 180) {
            val x = rnd.nextInt(size)
            val y = rnd.nextInt(size)
            val h = 2 + rnd.nextInt(4)
            val col = (0xFF shl 24) or ((50 + rnd.nextInt(30)) shl 16) or
                ((90 + rnd.nextInt(50)) shl 8) or (30 + rnd.nextInt(20))
            for (dy in 0 until h) {
                val yy = (y + dy) % size
                bmp.setPixel(x, yy, col)
            }
        }
        return bmp
    }

    private fun proceduralDirt(): Bitmap {
        val size = 64
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val rnd = Random(29)
        for (y in 0 until size) {
            for (x in 0 until size) {
                val n = rnd.nextFloat()
                val r = (78 + n * 40).toInt()
                val g = (58 + n * 28).toInt()
                val b = (38 + n * 18).toInt()
                bmp.setPixel(x, y, (0xFF shl 24) or (r shl 16) or (g shl 8) or b)
            }
        }
        for (i in 0 until 40) {
            val cx = rnd.nextInt(size)
            val cy = rnd.nextInt(size)
            val rad = 1 + rnd.nextInt(3)
            val col = (0xFF shl 24) or ((55 + rnd.nextInt(25)) shl 16) or
                ((40 + rnd.nextInt(20)) shl 8) or (28 + rnd.nextInt(15))
            for (dy in -rad..rad) for (dx in -rad..rad) {
                if (dx * dx + dy * dy <= rad * rad) {
                    bmp.setPixel((cx + dx + size) % size, (cy + dy + size) % size, col)
                }
            }
        }
        return bmp
    }
}

/**
 * Sedan rozobratý na vrstvy z jedného sprite (bez kolies – tie kreslíme zvlášť).
 */
class SedanLayers(raw: Bitmap) {
    data class Part(val image: ImageBitmap, val fx: Float, val fy: Float, val fw: Float, val fh: Float)

    val stripped: ImageBitmap
    val body: Part
    val doors: Part
    val hood: Part
    val windows: Part
    val frontBumper: Part
    val rearBumper: Part
    val imageWidth: Int
    val imageHeight: Int
    val worldWidthM = 5.6f
    /** Relatívne stredy oblúkov kolies v orezenom sprite. */
    val rearWheelFx: Float
    val frontWheelFx: Float
    val wheelCenterFy: Float
    val wheelRadiusFx: Float

    init {
        // PNG má solidné čierne pozadie → odstránime okrajové čierne + orežeme.
        val keyed = keyOutEdgeBlack(raw, threshold = 48)
        val cropped = cropToOpaque(keyed, pad = 2)
        punchWheelWells(cropped)
        scrubFringe(cropped)
        imageWidth = cropped.width
        imageHeight = cropped.height
        rearWheelFx = 0.18f
        frontWheelFx = 0.80f
        wheelCenterFy = 0.82f
        wheelRadiusFx = 0.092f
        stripped = darken(cropped, 0.92f).asImageBitmap()
        body = part(cropped, 0.06f, 0.08f, 0.88f, 0.82f)
        doors = part(cropped, 0.28f, 0.18f, 0.38f, 0.62f)
        hood = part(cropped, 0.68f, 0.22f, 0.26f, 0.48f)
        windows = part(cropped, 0.30f, 0.05f, 0.42f, 0.32f)
        frontBumper = part(cropped, 0.86f, 0.42f, 0.12f, 0.38f)
        rearBumper = part(cropped, 0.02f, 0.38f, 0.12f, 0.42f)
    }

    private fun part(src: Bitmap, fx: Float, fy: Float, fw: Float, fh: Float): Part =
        Part(crop(src, fx, fy, fw, fh).asImageBitmap(), fx, fy, fw, fh)

    private fun crop(src: Bitmap, fx: Float, fy: Float, fw: Float, fh: Float): Bitmap {
        val x = (fx * src.width).toInt().coerceIn(0, src.width - 1)
        val y = (fy * src.height).toInt().coerceIn(0, src.height - 1)
        val w = (fw * src.width).toInt().coerceAtLeast(1).coerceAtMost(src.width - x)
        val h = (fh * src.height).toInt().coerceAtLeast(1).coerceAtMost(src.height - y)
        return Bitmap.createBitmap(src, x, y, w, h)
    }

    private fun darken(src: Bitmap, factor: Float): Bitmap {
        val w = src.width
        val h = src.height
        val out = src.copy(Bitmap.Config.ARGB_8888, true)
        val px = IntArray(w * h)
        out.getPixels(px, 0, w, 0, 0, w, h)
        for (i in px.indices) {
            val c = px[i]
            val a = (c ushr 24) and 0xFF
            if (a < 8) {
                px[i] = 0
                continue
            }
            val r = (((c shr 16) and 0xFF) * factor).toInt().coerceIn(0, 255)
            val g = (((c shr 8) and 0xFF) * factor).toInt().coerceIn(0, 255)
            val b = ((c and 0xFF) * factor).toInt().coerceIn(0, 255)
            px[i] = (a shl 24) or (r shl 16) or (g shl 8) or b
        }
        out.setPixels(px, 0, w, 0, 0, w, h)
        return out
    }

    companion object {
        /**
         * Flood-fill z okrajov: čierne pozadie → alpha 0.
         * Vnútorné čierne (B-stĺpik, zrkadlo) ostane.
         */
        fun keyOutEdgeBlack(src: Bitmap, threshold: Int = 32): Bitmap {
            val w = src.width
            val h = src.height
            val out = src.copy(Bitmap.Config.ARGB_8888, true)
            val px = IntArray(w * h)
            out.getPixels(px, 0, w, 0, 0, w, h)

            fun isBg(i: Int): Boolean {
                val c = px[i]
                val a = (c ushr 24) and 0xFF
                if (a < 8) return true
                val r = (c shr 16) and 0xFF
                val g = (c shr 8) and 0xFF
                val b = c and 0xFF
                return r <= threshold && g <= threshold && b <= threshold
            }

            val seen = BooleanArray(w * h)
            val queue = IntArray(w * h)
            var qh = 0
            var qt = 0

            fun offer(i: Int) {
                if (i !in 0 until seen.size || seen[i] || !isBg(i)) return
                seen[i] = true
                queue[qt++] = i
            }

            for (x in 0 until w) {
                offer(x)
                offer((h - 1) * w + x)
            }
            for (y in 0 until h) {
                offer(y * w)
                offer(y * w + (w - 1))
            }

            while (qh < qt) {
                val i = queue[qh++]
                px[i] = 0
                val x = i % w
                val y = i / w
                if (x > 0) offer(i - 1)
                if (x + 1 < w) offer(i + 1)
                if (y > 0) offer(i - w)
                if (y + 1 < h) offer(i + w)
            }
            out.setPixels(px, 0, w, 0, 0, w, h)
            return out
        }

        fun cropToOpaque(src: Bitmap, pad: Int = 2): Bitmap {
            val w = src.width
            val h = src.height
            val px = IntArray(w * h)
            src.getPixels(px, 0, w, 0, 0, w, h)
            var minX = w
            var minY = h
            var maxX = -1
            var maxY = -1
            for (y in 0 until h) {
                val row = y * w
                for (x in 0 until w) {
                    if ((px[row + x] ushr 24) and 0xFF > 8) {
                        if (x < minX) minX = x
                        if (y < minY) minY = y
                        if (x > maxX) maxX = x
                        if (y > maxY) maxY = y
                    }
                }
            }
            if (maxX < minX) return src
            minX = (minX - pad).coerceAtLeast(0)
            minY = (minY - pad).coerceAtLeast(0)
            maxX = (maxX + pad).coerceAtMost(w - 1)
            maxY = (maxY + pad).coerceAtMost(h - 1)
            return Bitmap.createBitmap(src, minX, minY, maxX - minX + 1, maxY - minY + 1)
        }

        fun punchWheelWells(src: Bitmap) {
            val w = src.width
            val h = src.height
            val px = IntArray(w * h)
            src.getPixels(px, 0, w, 0, 0, w, h)
            val centers = floatArrayOf(0.18f, 0.80f)
            // Len spodné oblúky — nezasahovať do kapoty/blatníkov.
            val cy = (h * 0.88f).toInt()
            val rx = (w * 0.078f).toInt().coerceAtLeast(8)
            val ry = (h * 0.16f).toInt().coerceAtLeast(6)
            for (cxF in centers) {
                val cx = (w * cxF).toInt()
                for (dy in -ry..ry) {
                    for (dx in -rx..rx) {
                        val nx = dx.toFloat() / rx
                        val ny = dy.toFloat() / ry
                        if (nx * nx + ny * ny <= 1f) {
                            val x = cx + dx
                            val y = cy + dy
                            if (x in 0 until w && y in 0 until h) px[y * w + x] = 0
                        }
                    }
                }
            }
            src.setPixels(px, 0, w, 0, 0, w, h)
        }

        /** Odstráni polopriehľadný okraj (často vyzerá ako „box“ na sprite). */
        fun scrubFringe(src: Bitmap) {
            val w = src.width
            val h = src.height
            val px = IntArray(w * h)
            src.getPixels(px, 0, w, 0, 0, w, h)
            for (i in px.indices) {
                val a = (px[i] ushr 24) and 0xFF
                if (a in 1..40) px[i] = 0
            }
            src.setPixels(px, 0, w, 0, 0, w, h)
        }
    }
}


/** Helper na BitmapShader prekreslenie Pathu (trávy / zem). */
class TerrainShaders(private val grass: Bitmap, private val dirt: Bitmap) {
    private val grassShader = BitmapShader(grass, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
    private val dirtShader = BitmapShader(dirt, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
    private val matrix = Matrix()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    fun drawGrass(canvas: android.graphics.Canvas, path: android.graphics.Path, ppm: Float, camX: Float) {
        paint(canvas, path, grassShader, grass.width, 4.2f, ppm, camX, 180)
    }

    fun drawDirt(canvas: android.graphics.Canvas, path: android.graphics.Path, ppm: Float, camX: Float) {
        paint(canvas, path, dirtShader, dirt.width, 8.5f, ppm, camX, 200)
    }

    private fun paint(
        canvas: android.graphics.Canvas,
        path: android.graphics.Path,
        shader: BitmapShader,
        texW: Int,
        tileM: Float,
        ppm: Float,
        camX: Float,
        alpha: Int
    ) {
        matrix.reset()
        val scale = (tileM * ppm) / texW
        matrix.setScale(scale, scale)
        matrix.postTranslate(-(camX * ppm) % (texW * scale), 0f)
        shader.setLocalMatrix(matrix)
        paint.shader = shader
        paint.alpha = alpha
        canvas.drawPath(path, paint)
        paint.shader = null
        paint.alpha = 255
    }
}
