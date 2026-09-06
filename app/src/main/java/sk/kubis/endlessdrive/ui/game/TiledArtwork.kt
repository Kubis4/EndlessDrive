package sk.kubis.endlessdrive.ui.game

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Shader
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Runtime finish of the original artwork: same shapes/palette/alpha, subtle material grain.
 *
 * The tile is made to loop by dropping a few pixels on the right so the new
 * join sits on originally neighbouring columns, then a short cross-fade hides
 * that cut. A wide blend (a twelfth of the image) used to smear two different
 * pieces of landscape into a visible vertical band once per repeat.
 *
 * The wrap is started at the quietest cut so a tree sliced at the PNG edge is
 * not the thing that meets the next tile. Grain is applied on the source
 * before the wrap, so the noise is blended with the art and the tile edges
 * stay neighbouring source pixels.
 */
object TiledArtwork {
    fun finish(source: Bitmap, material: MaterialKind, strength: Float, preserveSeam: Boolean = false): Bitmap {
        val src = softwareArgb(source)
        try {
            val grained = if (strength > 0f) {
                val mutable = if (src.isMutable) src else copyArgb(src)
                paintGrain(mutable, material, strength)
                mutable
            } else src
            try {
                // Authored periodic tiles must not receive another crossfade
                // through opaque silhouettes. Return an owned bitmap as usual.
                val tile = if (preserveSeam) copyArgb(grained) else wrapSeamless(grained)
                healSilhouette(tile)
                return tile
            } finally {
                if (grained !== src && grained !== source) grained.recycle()
            }
        } finally {
            if (src !== source) src.recycle()
        }
    }

    /**
     * Zrno pred ovinutím, nie po ňom. Shader sa opakuje po 256 px, takže na
     * hotovej dlaždici by ľavý a pravý okraj (šírka zriedka násobok 256)
     * nesadli a na oblohe by z toho bola zvislá čiara.
     */
    private fun paintGrain(target: Bitmap, material: MaterialKind, strength: Float) {
        val paint = Paint().apply {
            shader = BitmapShader(
                MaterialTextures.bitmap(material),
                Shader.TileMode.REPEAT,
                Shader.TileMode.REPEAT
            )
            alpha = (strength * 255).toInt().coerceIn(0, 255)
            xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP)
        }
        Canvas(target).drawRect(0f, 0f, target.width.toFloat(), target.height.toFloat(), paint)
    }

    private fun wrapSeamless(src: Bitmap): Bitmap {
        // Pár pixelov na prelínanie; na úzkom obrázku nesmie šírka klesnúť na 0.
        val overlap = (src.width / 96).coerceIn(4, 24).coerceAtMost((src.width - 1).coerceAtLeast(1))
        val width = src.width - overlap
        if (width < 1 || src.height < 1) return copyArgb(src)
        val pixels = IntArray(src.width * src.height)
        src.getPixels(pixels, 0, src.width, 0, 0, src.width, src.height)
        val offset = bestWrapOffset(pixels, src.width, src.height, overlap, width)
        val result = IntArray(width * src.height)
        val srcW = src.width
        val srcH = src.height
        val blendSpan = (overlap - 1).coerceAtLeast(1)
        val hillTop = hillTopRow(pixels, srcW, srcH)
        fun at(x: Int, y: Int): Int {
            val col = Math.floorMod(x + offset, srcW)
            return pixels[y * srcW + col]
        }
        for (y in 0 until srcH) {
            val inHill = y >= hillTop
            for (x in 0 until width) {
                val start = at(x, y)
                result[y * width + x] = if (x < overlap) {
                    val t = x / blendSpan.toFloat()
                    val smooth = t * t * (3f - 2f * t)
                    mixWrap(at(width + x, y), start, smooth, inHill)
                } else start
            }
        }
        // createBitmap(int[]) vracia nemennú bitmapu – ďalší Canvas by spadol.
        val seamless = Bitmap.createBitmap(width, src.height, Bitmap.Config.ARGB_8888)
        seamless.setPixels(result, 0, width, 0, 0, width, src.height)
        return seamless
    }

    /**
     * Posunie rez tam, kde sa prelínané pruhy najmenej líšia – typicky medzera
     * medzi stromami, nie zrezaná koruna na okraji PNG.
     */
    private fun bestWrapOffset(
        pixels: IntArray,
        srcW: Int,
        srcH: Int,
        overlap: Int,
        width: Int
    ): Int {
        var best = 0
        var bestScore = Long.MAX_VALUE
        var o = 0
        while (o < srcW) {
            // Rez presne na pôvodnom spoji ľavý/pravý okraj PNG by spojil
            // nesusedné pixely (test gradientu by to zachytil ako skok).
            if ((o + width) % srcW != 0) {
                var score = 0L
                var y = 0
                val joinA = Math.floorMod(o + width - 1, srcW)
                val joinB = Math.floorMod(o + width, srcW)
                while (y < srcH) {
                    for (x in 0 until overlap) {
                        val a = pixels[y * srcW + Math.floorMod(o + x, srcW)]
                        val b = pixels[y * srcW + Math.floorMod(o + width + x, srcW)]
                        score += abs(((a ushr 24) and 255) - ((b ushr 24) and 255))
                        score += abs(((a ushr 16) and 255) - ((b ushr 16) and 255))
                        score += abs(((a ushr 8) and 255) - ((b ushr 8) and 255))
                        score += abs((a and 255) - (b and 255))
                    }
                    val ja = pixels[y * srcW + joinA]
                    val jb = pixels[y * srcW + joinB]
                    score += abs(((ja ushr 24) and 255) - ((jb ushr 24) and 255)) * 8L
                    y += 4
                }
                if (score < bestScore) {
                    bestScore = score
                    best = o
                }
            }
            o += 4
        }
        return best
    }

    /**
     * Na kopci nesmie prelínanie vyrezať oblohu; v korunách nesmie nechať
     * polopriehľadný „duch“ kmeňa. Obidva okraje nepriehľadné – bežný mix.
     */
    private fun mixWrap(a: Int, b: Int, t: Float, inHill: Boolean): Int {
        val aa = a ushr 24
        val ba = b ushr 24
        if (aa < 32 && ba < 32) return 0
        return if (inHill) {
            when {
                aa < 40 -> b
                ba < 40 -> a
                else -> mixPremultiplied(a, b, t)
            }
        } else {
            if (aa < 48 || ba < 48) 0 else mixPremultiplied(a, b, t)
        }
    }

    private fun mixPremultiplied(a: Int, b: Int, t: Float): Int {
        val aa = a ushr 24; val ba = b ushr 24
        val alpha = aa * (1f - t) + ba * t
        if (alpha < 0.5f) return 0
        fun channel(shift: Int): Int =
            ((((a ushr shift) and 255) * aa * (1f - t) + ((b ushr shift) and 255) * ba * t) / alpha)
                .roundToInt().coerceIn(0, 255)
        return (alpha.roundToInt().coerceIn(0, 255) shl 24) or
            (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
    }

    private fun healSilhouette(bmp: Bitmap) {
        val w = bmp.width
        val h = bmp.height
        if (w < 8 || h < 8) return
        val px = IntArray(w * h)
        bmp.getPixels(px, 0, w, 0, 0, w, h)
        val sky = skyRef(px, w, h)
        val hillTop = hillTopRow(px, w, h)
        stripGhosts(px, w, h, hillTop, sky)
        fillHillHoles(px, w, h, hillTop, sky)
        bmp.setPixels(px, 0, w, 0, 0, w, h)
    }

    private fun skyRef(px: IntArray, w: Int, h: Int): Int {
        var r = 0L; var g = 0L; var b = 0L; var a = 0L; var n = 0
        val rows = (h / 12).coerceAtLeast(1)
        for (y in 0 until rows) for (x in 0 until w step 2) {
            val c = px[y * w + x]
            a += (c ushr 24) and 255
            r += (c ushr 16) and 255
            g += (c ushr 8) and 255
            b += c and 255
            n++
        }
        if (n == 0) return 0
        return ((a / n).toInt() shl 24) or ((r / n).toInt() shl 16) or
            ((g / n).toInt() shl 8) or (b / n).toInt()
    }

    private fun isSkyLike(c: Int, sky: Int): Boolean {
        val ca = (c ushr 24) and 255
        if (ca < 40) return true
        if (((sky ushr 24) and 255) < 40) return ca < 48
        return colorDist(c, sky) < 48
    }

    private fun colorDist(a: Int, b: Int): Int =
        abs(((a ushr 24) and 255) - ((b ushr 24) and 255)) +
            abs(((a ushr 16) and 255) - ((b ushr 16) and 255)) +
            abs(((a ushr 8) and 255) - ((b ushr 8) and 255)) +
            abs((a and 255) - (b and 255))

    private fun hillTopRow(px: IntArray, w: Int, h: Int): Int {
        val sky = skyRef(px, w, h)
        val occ = IntArray(h)
        for (y in 0 until h) for (x in 0 until w) {
            if (!isSkyLike(px[y * w + x], sky)) occ[y]++
        }
        var top = h - 1
        val need = (w * 62) / 100
        for (y in h - 1 downTo h / 6) {
            if (occ[y] >= need) top = y
            else if (y < h * 4 / 5 && top < h - 2) break
        }
        return top
    }

    private fun stripGhosts(px: IntArray, w: Int, h: Int, hillTop: Int, sky: Int) {
        val keep = BooleanArray(w * h)
        val stack = IntArray(w * h)
        var sp = 0
        fun offer(i: Int) {
            if (i !in keep.indices || keep[i] || isSkyLike(px[i], sky)) return
            keep[i] = true
            stack[sp++] = i
        }
        for (x in 0 until w) offer((h - 1) * w + x)
        for (y in hillTop until h) for (x in 0 until w) offer(y * w + x)
        while (sp > 0) {
            val i = stack[--sp]
            val x = i % w
            val y = i / w
            for (dy in -1..1) for (dx in -1..1) {
                if (dx == 0 && dy == 0) continue
                val nx = Math.floorMod(x + dx, w)
                val ny = y + dy
                if (ny in 0 until h) offer(ny * w + nx)
            }
        }
        val clear = if (((sky ushr 24) and 255) < 40) 0 else sky
        for (i in px.indices) {
            if (keep[i] || isSkyLike(px[i], sky)) continue
            px[i] = clear
        }
    }

    private fun fillHillHoles(px: IntArray, w: Int, h: Int, hillTop: Int, sky: Int) {
        val crest = IntArray(w)
        for (x in 0 until w) {
            var y = h - 1
            while (y >= hillTop && !isSkyLike(px[y * w + x], sky)) y--
            crest[x] = y + 1
        }
        val smooth = IntArray(w)
        for (x in 0 until w) {
            var s = 0
            var n = 0
            for (dx in -10..10) {
                s += crest[Math.floorMod(x + dx, w)]
                n++
            }
            smooth[x] = maxOf(hillTop, s / n)
        }
        for (x in 0 until w) {
            val cl = crest[Math.floorMod(x - 1, w)]
            val cr = crest[Math.floorMod(x + 1, w)]
            var target = hillTop.coerceAtLeast(minOf(crest[x], smooth[x], minOf(cl, cr)))
            // Pri spoji dlaždice zatvor aj malý zárez – inak cez kopec preblikne obloha.
            val nearWrap = x < 12 || x >= w - 12
            if (nearWrap) target = minOf(target, smooth[x])
            if (!nearWrap && crest[x] <= target + 6) continue
            if (!nearWrap && (cl > crest[x] - 6 || cr > crest[x] - 6)) continue
            val reach = if (nearWrap) 36 else 20
            for (y in target until h) {
                val i = y * w + x
                if (!isSkyLike(px[i], sky)) continue
                val sample = sampleHill(px, w, h, x, y, sky, reach)
                if (sample != 0) px[i] = sample
            }
            crest[x] = target
        }
        for (x in 0 until w) {
            for (y in (crest[x] + 1) until h) {
                val i = y * w + x
                if (!isSkyLike(px[i], sky)) continue
                val sample = sampleHill(px, w, h, x, y, sky, 12)
                if (sample != 0) px[i] = sample
            }
        }
        // Posledný/ľavý stĺpec v páse kopca zjednoť – bilinear inak urobí vlas.
        for (y in hillTop until h) {
            val left = px[y * w]
            val right = px[y * w + w - 1]
            when {
                isSkyLike(left, sky) && !isSkyLike(right, sky) -> px[y * w] = right
                !isSkyLike(left, sky) && isSkyLike(right, sky) -> px[y * w + w - 1] = left
                !isSkyLike(left, sky) && !isSkyLike(right, sky) -> {
                    val m = mixPremultiplied(left, right, 0.5f)
                    px[y * w] = m
                    px[y * w + w - 1] = m
                }
            }
        }
    }

    private fun sampleHill(px: IntArray, w: Int, h: Int, x: Int, y: Int, sky: Int, maxDist: Int): Int {
        for (d in 1..maxDist) {
            val left = px[y * w + Math.floorMod(x - d, w)]
            if (!isSkyLike(left, sky)) return left
            val right = px[y * w + Math.floorMod(x + d, w)]
            if (!isSkyLike(right, sky)) return right
            if (y + 1 < h) {
                val down = px[(y + 1) * w + x]
                if (!isSkyLike(down, sky)) return down
            }
        }
        return 0
    }

    private fun softwareArgb(source: Bitmap): Bitmap {
        // Config.HARDWARE je až API 26; na starších zariadeniach konštanta nie je.
        if (source.config?.name == "HARDWARE") {
            return source.copy(Bitmap.Config.ARGB_8888, false) ?: copyArgb(source)
        }
        return source
    }

    private fun copyArgb(source: Bitmap): Bitmap =
        source.copy(Bitmap.Config.ARGB_8888, true)
            ?: Bitmap.createBitmap(
                source.width.coerceAtLeast(1),
                source.height.coerceAtLeast(1),
                Bitmap.Config.ARGB_8888
            )
}
