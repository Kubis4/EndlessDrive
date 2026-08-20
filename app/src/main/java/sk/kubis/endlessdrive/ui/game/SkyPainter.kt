package sk.kubis.endlessdrive.ui.game

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp
import sk.kubis.endlessdrive.core.MathX
import sk.kubis.endlessdrive.domain.model.BiomeType
import kotlin.math.PI
import kotlin.math.sin

/**
 * Doplnky nad kreslené pozadie: hviezdy, slnko s mesiacom a hmla nad
 * horizontom. Vlastnú oblohu ani kopce už nekreslíme – tie si každý bióm
 * nesie v predlohe.
 */
class SkyPainter {

    /**
     * Hviezdy nad kreslené pozadie – to má vlastnú oblohu, ale v noci by bez
     * nich bolo len tmavé pole.
     */
    fun DrawScope.drawStarfield(day: Float, camX: Float, horizonY: Float) {
        drawStars(day, camX, horizonY)
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

    /** Slnko a mesiac nad kreslenú oblohu – tá je statická, denný cyklus nie. */
    fun DrawScope.drawCelestialOver(time: Float, day: Float, horizonY: Float) {
        drawCelestial(time, day, horizonY)
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


    /**
     * Hmla nad horizontom – zjemní prechod medzi oblohou a krajinou.
     * Kreslené pozadia si atmosféru nesú samy, tam stačí [strength] okolo 0.4;
     * na plnú silu by z nich spravila sivý filter.
     */
    fun DrawScope.drawHaze(horizonY: Float, day: Float, biome: BiomeType, strength: Float = 1f) {
        val haze = lerp(Color(0xFF1B2338), biomeHorizon(biome), day.coerceIn(0f, 1f))
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color.Transparent, haze.copy(alpha = 0.55f * strength), Color.Transparent),
                startY = horizonY - horizonY * 0.30f,
                endY = horizonY + horizonY * 0.14f
            ),
            topLeft = Offset(0f, horizonY - horizonY * 0.30f),
            size = Size(size.width, horizonY * 0.44f)
        )
    }

    /** Farba pri horizonte – ladí aj hmlu nad kreslenými pozadiami. */
    private fun biomeHorizon(biome: BiomeType) = when (biome) {
        BiomeType.RURAL -> Color(0xFFC7DEEA)
        BiomeType.INDUSTRIAL -> Color(0xFFB6C3C9)
        BiomeType.WASTELAND -> Color(0xFFD8C7A8)
        BiomeType.DESERT -> Color(0xFFF0D9A6)
        BiomeType.DESERT_DUSK -> Color(0xFFE8A867)
        BiomeType.FOREST -> Color(0xFFC3CEC4)
        BiomeType.FOREST_ALIVE -> Color(0xFFCADCC9)
        BiomeType.SANDSTORM -> Color(0xFFD9B078)
        BiomeType.DUST_STORM -> Color(0xFFD5B37F)
    }

    private companion object {
        const val STAR_COUNT = 70
    }
}
