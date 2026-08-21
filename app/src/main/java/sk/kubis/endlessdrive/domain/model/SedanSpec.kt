package sk.kubis.endlessdrive.domain.model

import androidx.compose.ui.graphics.Color
import sk.kubis.endlessdrive.core.GameConfig

/**
 * Sedan z HillRush (HATCHBACK) – jediné auto MVP.
 * Geometria chassis/cabin je priamo z [sk.kubis.hillrush.domain.model.CarModel.HATCHBACK].
 */
object SedanSpec {
    val bodyColor = Color(0xFFFF7043)
    val accentColor = Color(0xFF3E2723)
    val rustColor = Color(0xFF8D6E63)

    val chassisPoly = floatArrayOf(
        -2.559f, -0.044f,
        -2.246f, -0.332f,
        1.965f, -0.332f,
        2.179f, 0.007f,
        2.103f, 0.433f,
        1.138f, 0.884f,
        -1.080f, 0.959f,
        -2.559f, 0.759f
    )

    val cabinPoly = floatArrayOf(
        -1.20f, 0.95f,
        1.00f, 0.90f,
        0.60f, 1.50f,
        -0.85f, 1.55f
    )

    /** Dvere – bočný panel (lokálne metre). */
    val doorPoly = floatArrayOf(
        -0.55f, 0.05f,
        0.85f, 0.05f,
        0.85f, 0.92f,
        -0.55f, 0.95f
    )

    /** Kapota. */
    val hoodPoly = floatArrayOf(
        0.95f, 0.55f,
        2.05f, 0.35f,
        2.00f, 0.78f,
        1.05f, 0.88f
    )

    val wheelRadius = GameConfig.WHEEL_RADIUS
    val wheelOffsetX = GameConfig.WHEEL_OFFSET_X
    val headX = GameConfig.HEAD_LOCAL_X
    val headY = GameConfig.HEAD_LOCAL_Y

    val noseX: Float = extreme(chassisPoly, 0, max = true)
    val tailX: Float = extreme(chassisPoly, 0, max = false)
    val floorY: Float = extreme(chassisPoly, 1, max = false)
    val deckY: Float = extreme(chassisPoly, 1, max = true)

    private fun extreme(poly: FloatArray, axis: Int, max: Boolean): Float {
        var best = poly[axis]
        var i = axis + 2
        while (i < poly.size) {
            val v = poly[i]
            if (max) {
                if (v > best) best = v
            } else if (v < best) best = v
            i += 2
        }
        return best
    }
}

/** Vizuálne / karosériové diely, ktoré sa dajú namontovať. */
enum class BodyPart(val displayName: String) {
    DOOR_FRONT("Front door"),
    DOOR_REAR("Rear door"),
    HOOD("Hood"),
    FRONT_BUMPER("Front bumper"),
    REAR_BUMPER("Rear bumper")
}
