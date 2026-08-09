package sk.kubis.endlessdrive.core

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

object MathX {
    const val TAU = (PI * 2).toFloat()

    fun clamp(v: Float, lo: Float, hi: Float): Float = max(lo, min(hi, v))

    fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

    fun damp(a: Float, b: Float, lambda: Float, dt: Float): Float =
        lerp(a, b, 1f - exp(-lambda * dt))

    fun smoothstep(edge0: Float, edge1: Float, x: Float): Float {
        val t = clamp((x - edge0) / (edge1 - edge0), 0f, 1f)
        return t * t * (3f - 2f * t)
    }

    fun hash01(a: Int, b: Int): Float {
        var n = a * 374761393 + b * 668265263
        n = (n xor (n ushr 13)) * 1274126177
        n = n xor (n ushr 16)
        return (n and 0x7fffffff) / 2147483647f
    }

    fun hash01(x: Float): Float {
        val i = x.toRawBits()
        return hash01(i, i ushr 16)
    }

    fun approxSin(x: Float): Float = sin(x.toDouble()).toFloat()

    fun floorDiv(v: Float, size: Float): Int = floor(v / size).toInt()
}

class SeededRandom(seed: Long) {
    private var state = seed xor 0x5DEECE66DL

    fun nextLong(): Long {
        state = (state * 0x5DEECE66DL + 0xBL) and ((1L shl 48) - 1)
        return state
    }

    fun nextInt(bound: Int): Int {
        if (bound <= 0) return 0
        val v = ((nextLong() ushr 16) % bound).toInt()
        return if (v < 0) v + bound else v
    }

    fun nextFloat(): Float = ((nextLong() ushr 24) and 0xFFFFFFL) / 16777216f

    fun nextFloat(min: Float, max: Float): Float = min + nextFloat() * (max - min)

    fun chance(p: Float): Boolean = nextFloat() < p

    fun <T> pick(list: List<T>): T = list[nextInt(list.size)]
}

class ObjectPool<T : Any>(
    private val capacity: Int,
    private val factory: () -> T
) {
    private val free = ArrayDeque<T>(capacity)

    init {
        repeat(capacity) { free.addLast(factory()) }
    }

    fun obtain(): T? = free.removeLastOrNull()
    fun recycle(item: T) {
        if (free.size < capacity) free.addLast(item)
    }
}
