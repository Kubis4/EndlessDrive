package sk.kubis.endlessdrive.core

import kotlin.math.floor
import kotlin.random.Random

/**
 * 2D Simplex noise (Perlin-Simplex) so seedovanou permutačnou tabuľkou.
 *
 * Implementácia je bezalokačná v [noise2] – volá sa desiatky-tisíckrát za beh
 * generovania terénu, takže sa vyhýbame vytváraniu objektov.
 *
 * Vychádza z klasického public-domain algoritmu (Perlin / Gustavson),
 * prepísaného do Kotlinu.
 */
class SimplexNoise(seed: Long) {

    private val perm = IntArray(512)
    private val permMod12 = IntArray(512)

    init {
        val p = IntArray(256) { it }
        val rnd = Random(seed)
        // Fisher–Yates shuffle riadený seedom -> deterministický terén.
        for (i in 255 downTo 1) {
            val j = rnd.nextInt(i + 1)
            val t = p[i]; p[i] = p[j]; p[j] = t
        }
        for (i in 0 until 512) {
            perm[i] = p[i and 255]
            permMod12[i] = perm[i] % 12
        }
    }

    /** Vráti hodnotu v rozsahu približne <-1, 1>. */
    fun noise2(xin: Float, yin: Float): Float {
        var n0: Float; var n1: Float; var n2: Float

        val s = (xin + yin) * F2
        val i = floor((xin + s).toDouble()).toInt()
        val j = floor((yin + s).toDouble()).toInt()
        val t = (i + j) * G2
        val x0 = xin - (i - t)
        val y0 = yin - (j - t)

        val i1: Int; val j1: Int
        if (x0 > y0) { i1 = 1; j1 = 0 } else { i1 = 0; j1 = 1 }

        val x1 = x0 - i1 + G2
        val y1 = y0 - j1 + G2
        val x2 = x0 - 1f + 2f * G2
        val y2 = y0 - 1f + 2f * G2

        val ii = i and 255
        val jj = j and 255
        val gi0 = permMod12[ii + perm[jj]]
        val gi1 = permMod12[ii + i1 + perm[jj + j1]]
        val gi2 = permMod12[ii + 1 + perm[jj + 1]]

        var t0 = 0.5f - x0 * x0 - y0 * y0
        n0 = if (t0 < 0) 0f else { t0 *= t0; t0 * t0 * dot(gi0, x0, y0) }

        var t1 = 0.5f - x1 * x1 - y1 * y1
        n1 = if (t1 < 0) 0f else { t1 *= t1; t1 * t1 * dot(gi1, x1, y1) }

        var t2 = 0.5f - x2 * x2 - y2 * y2
        n2 = if (t2 < 0) 0f else { t2 *= t2; t2 * t2 * dot(gi2, x2, y2) }

        return 70f * (n0 + n1 + n2)
    }

    /**
     * Fraktálny (fBm) šum – niekoľko oktáv nad sebou.
     * @param octaves počet vrstiev
     * @param lacunarity násobič frekvencie medzi oktávami
     * @param gain násobič amplitúdy medzi oktávami
     */
    fun fbm(x: Float, y: Float, octaves: Int, lacunarity: Float = 2.05f, gain: Float = 0.5f): Float {
        var amp = 1f
        var freq = 1f
        var sum = 0f
        var norm = 0f
        repeat(octaves) {
            sum += amp * noise2(x * freq, y * freq)
            norm += amp
            amp *= gain
            freq *= lacunarity
        }
        return if (norm == 0f) 0f else sum / norm
    }

    private fun dot(g: Int, x: Float, y: Float): Float =
        GRAD[g * 2] * x + GRAD[g * 2 + 1] * y

    private companion object {
        val F2 = (0.5 * (Math.sqrt(3.0) - 1.0)).toFloat()
        val G2 = ((3.0 - Math.sqrt(3.0)) / 6.0).toFloat()
        val GRAD = floatArrayOf(
            1f, 1f, -1f, 1f, 1f, -1f, -1f, -1f,
            1f, 0f, -1f, 0f, 1f, 0f, -1f, 0f,
            0f, 1f, 0f, -1f, 0f, 1f, 0f, -1f
        )
    }
}
