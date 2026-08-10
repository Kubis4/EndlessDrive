package sk.kubis.endlessdrive.ui.game

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp
import sk.kubis.endlessdrive.core.MathX
import sk.kubis.endlessdrive.domain.model.BiomeType
import sk.kubis.endlessdrive.game.DayCycle
import kotlin.math.PI
import kotlin.math.sin

/**
 * Procedurálna obloha: gradient podľa dennej doby, slnko/mesiac, hviezdy,
 * oblaky a siluety vzdialených kopcov s parallaxom.
 */
class SkyPainter {
    private val ridgePath = Path()

    fun DrawScope.drawSky(time: Float, biome: BiomeType, camX: Float, horizonY: Float) {
        val day = DayCycle.daylight(time)
        val golden = DayCycle.goldenHour(time)

        val zenith = lerp(NIGHT_ZENITH, biomeZenith(biome), day)
            .let { lerp(it, GOLDEN_ZENITH, golden * 0.45f) }
        val horizon = lerp(NIGHT_HORIZON, biomeHorizon(biome), day)
            .let { lerp(it, GOLDEN_HORIZON, golden * 0.75f) }

        drawRect(
            brush = Brush.verticalGradient(
                0f to zenith,
                0.65f to lerp(zenith, horizon, 0.6f),
                1f to horizon
            ),
            size = Size(size.width, horizonY)
        )

        drawStars(day, camX, horizonY)
        drawCelestial(time, day, horizonY)
        drawClouds(day, golden, camX, horizonY)
    }

    private fun DrawScope.drawStars(day: Float, camX: Float, horizonY: Float) {
        val alpha = (1f - day * 1.6f).coerceIn(0f, 1f)
        if (alpha <= 0.01f) return
        val drift = (camX * 0.6f) % size.width
        for (i in 0 until STAR_COUNT) {
            val hx = MathX.hash01(i, 17)
            val hy = MathX.hash01(i, 71)
            val tw = MathX.hash01(i, 131)
            var x = hx * size.width - drift
            if (x < 0f) x += size.width
            val y = hy * horizonY * 0.78f
            val r = (0.7f + tw * 1.6f)
            drawCircle(
                Color.White.copy(alpha = alpha * (0.35f + tw * 0.65f)),
                radius = r,
                center = Offset(x, y)
            )
        }
    }

    private fun DrawScope.drawCelestial(time: Float, day: Float, horizonY: Float) {
        // Slnko: východ pri 0.25, západ pri 0.75. Mesiac o pol dňa posunutý.
        drawDisc(time, horizonY, sun = true, visible = day > 0.02f)
        drawDisc(time - 0.5f, horizonY, sun = false, visible = day < 0.75f)
    }

    private fun DrawScope.drawDisc(time: Float, horizonY: Float, sun: Boolean, visible: Boolean) {
        if (!visible) return
        val t = ((time % 1f) + 1f) % 1f
        val u = (t - 0.25f) / 0.5f
        if (u < -0.06f || u > 1.06f) return
        val x = size.width * u.coerceIn(-0.05f, 1.05f)
        val arc = sin((u.coerceIn(0f, 1f) * PI).toDouble()).toFloat()
        val y = horizonY - arc * horizonY * 0.72f - horizonY * 0.05f
        val r = if (sun) size.height * 0.055f else size.height * 0.042f

        val core = if (sun) Color(0xFFFFE9A8) else Color(0xFFE6ECF5)
        val glow = if (sun) Color(0xFFFFC46B) else Color(0xFFAFC4E8)
        drawCircle(glow.copy(alpha = 0.20f), r * 2.9f, Offset(x, y))
        drawCircle(glow.copy(alpha = 0.32f), r * 1.7f, Offset(x, y))
        drawCircle(core, r, Offset(x, y))
        if (!sun) {
            // Krátery / fáza.
            drawCircle(Color(0xFFCBD6E6).copy(alpha = 0.7f), r * 0.22f, Offset(x - r * 0.32f, y - r * 0.18f))
            drawCircle(Color(0xFFCBD6E6).copy(alpha = 0.5f), r * 0.15f, Offset(x + r * 0.30f, y + r * 0.26f))
        }
    }

    private fun DrawScope.drawClouds(day: Float, golden: Float, camX: Float, horizonY: Float) {
        val base = lerp(Color(0xFF3B4360), Color(0xFFF6F8FA), day)
        val tinted = lerp(base, Color(0xFFF3B98A), golden * 0.6f)
        for (layer in 0 until 2) {
            val parallax = 0.020f + layer * 0.018f
            val yBand = horizonY * (0.20f + layer * 0.22f)
            val scale = 0.8f + layer * 0.3f
            val alpha = (0.13f + 0.05f * layer) * (0.4f + day * 0.6f)
            val span = size.width * 2.2f
            for (i in 0 until 4) {
                val h = MathX.hash01(i, layer * 37 + 3)
                var x = (h * span - camX * parallax * 40f) % span
                if (x < 0f) x += span
                x -= span * 0.35f
                val y = yBand + (MathX.hash01(i, layer * 91 + 7) - 0.5f) * horizonY * 0.10f
                puff(x, y, size.width * 0.075f * scale, tinted.copy(alpha = alpha))
            }
        }
    }

    /** Nízky pretiahnutý oblak – nie guľa. */
    private fun DrawScope.puff(cx: Float, cy: Float, r: Float, color: Color) {
        drawOval(color, topLeft = Offset(cx - r * 1.6f, cy - r * 0.24f), size = Size(r * 3.2f, r * 0.48f))
        drawOval(color, topLeft = Offset(cx - r * 0.75f, cy - r * 0.40f), size = Size(r * 1.5f, r * 0.62f))
        drawOval(color, topLeft = Offset(cx + r * 0.25f, cy - r * 0.34f), size = Size(r * 1.0f, r * 0.52f))
    }

    /** Dve vrstvy vzdialených kopcov – hlavný zdroj hĺbky za cestou. */
    fun DrawScope.drawDistantHills(camX: Float, horizonY: Float, day: Float, biome: BiomeType) {
        for (layer in 0 until 2) {
            val parallax = if (layer == 0) 0.10f else 0.22f
            val amp = horizonY * (if (layer == 0) 0.16f else 0.11f)
            val baseY = horizonY - (if (layer == 0) horizonY * 0.02f else -horizonY * 0.03f)
            val far = hillColor(biome, layer, day)

            ridgePath.reset()
            ridgePath.moveTo(-4f, size.height)
            var x = -4f
            while (x <= size.width + 4f) {
                val u = (camX * parallax + x / 90f)
                val y = baseY - amp * (ridge(u, layer * 13.7f) * 0.5f + 0.5f)
                ridgePath.lineTo(x, y)
                x += 10f
            }
            ridgePath.lineTo(size.width + 4f, size.height)
            ridgePath.close()
            drawPath(ridgePath, far)
        }
    }

    private fun ridge(x: Float, phase: Float): Float =
        sin((x * 0.55f + phase).toDouble()).toFloat() * 0.55f +
            sin((x * 0.23f + phase * 1.7f).toDouble()).toFloat() * 0.32f +
            sin((x * 1.31f + phase * 0.4f).toDouble()).toFloat() * 0.13f

    private fun hillColor(biome: BiomeType, layer: Int, day: Float): Color {
        val nearC = when (biome) {
            BiomeType.RURAL -> Color(0xFF5E7355)
            BiomeType.INDUSTRIAL -> Color(0xFF5A6470)
            BiomeType.WASTELAND -> Color(0xFF6B5F49)
        }
        val farC = lerp(nearC, Color(0xFFAFC6D6), 0.45f)
        val c = if (layer == 0) farC else nearC
        return lerp(lerp(c, Color(0xFF141A2A), 0.72f), c, day)
    }

    private fun biomeZenith(biome: BiomeType) = when (biome) {
        BiomeType.RURAL -> Color(0xFF4C8AC6)
        BiomeType.INDUSTRIAL -> Color(0xFF5B7C93)
        BiomeType.WASTELAND -> Color(0xFF7A7FA0)
    }

    private fun biomeHorizon(biome: BiomeType) = when (biome) {
        BiomeType.RURAL -> Color(0xFFC7DEEA)
        BiomeType.INDUSTRIAL -> Color(0xFFB6C3C9)
        BiomeType.WASTELAND -> Color(0xFFD8C7A8)
    }

    private companion object {
        const val STAR_COUNT = 70
        val NIGHT_ZENITH = Color(0xFF080D22)
        val NIGHT_HORIZON = Color(0xFF1B2240)
        val GOLDEN_ZENITH = Color(0xFF4E5C96)
        val GOLDEN_HORIZON = Color(0xFFE8A365)
    }
}
