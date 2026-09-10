package sk.kubis.endlessdrive.ui.game

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import sk.kubis.endlessdrive.core.MathX
import sk.kubis.endlessdrive.domain.model.BiomeType
import sk.kubis.endlessdrive.domain.model.BuildingType
import sk.kubis.endlessdrive.game.world.WorldBuilding
import kotlin.math.roundToInt

/** Front + roof projection is baked into the art; signs must never be mirrored. */
class BuildingSpritePainter(private val sprites: BuildingSprites) {
    private val dayFilters = Array(33) { level ->
        val light = level / 32f
        ColorFilter.tint(Color(0.23f + light * 0.77f, 0.28f + light * 0.72f,
            0.38f + light * 0.62f), BlendMode.Modulate)
    }

    fun DrawScope.draw(b: WorldBuilding, x: Float, y: Float, s: Float, day: Float,
        environment: BiomeType, winter: Float, paintService: Boolean) {
        val filter = dayFilters[(day.coerceIn(0f, 1f) * 32).roundToInt()]
        val season = BuildingSeason.forEnvironment(environment, winter)
        if (b.landmark) {
            val relayArt = sprites.relay(season)
            val height = s * 8.2f
            val width = height * relayArt.width / relayArt.height
            sprite(relayArt, x + s * 1.65f, y, width, filter)
            val signal = if (b.relayRestored) Color(0xFF63E3C6) else Color(0xFFFF6B55)
            val tip = Offset(x + s * 1.65f, y - height + s * 0.20f)
            drawCircle(signal.copy(alpha = 0.18f), s * 0.30f, tip)
            drawCircle(signal, s * 0.075f, tip)
        }
        val art = sprites.building(b.type, paintService, season) ?: return
        val seed = (b.id xor (b.id ushr 32)).toInt()
        val width = s * when (b.type) {
            BuildingType.HOUSE -> 3.6f
            BuildingType.GARAGE -> 3.8f
            BuildingType.GAS_STATION -> 5.3f
            BuildingType.AUTO_SHOP -> 4.6f
            BuildingType.WRECK -> 0f
        } * (0.92f + MathX.hash01(seed, 7207) * 0.08f)
        val height = width * art.height / art.width
        sprite(art, x, y, width, filter)
        // Exterior status lamp remains dynamic even though material detail is baked.
        val lamp = when (b.type) {
            BuildingType.HOUSE -> Offset(x, y - height * 0.61f)
            BuildingType.GAS_STATION -> Offset(x, y - height * 0.64f)
            else -> Offset(x + width * 0.43f, y - height * 0.43f)
        }
        if (b.type != BuildingType.GARAGE && !b.looted && day < 0.65f) {
            drawCircle(Color(0xFFFFD994).copy(alpha = (0.65f - day) * 0.20f), s * 0.14f, lamp)
            drawCircle(Color(0xFFFFE8B0), s * 0.035f, lamp)
        }
        if (b.type == BuildingType.GAS_STATION) {
            val fuel = b.pumpFuelL > 0.05f || b.pumpDieselL > 0.05f
            drawCircle(if (fuel) Color(0xFF72C995) else Color(0xFF654C43),
                s * 0.045f, Offset(x - width * 0.29f, y - height * 0.29f))
        }
    }

    private fun DrawScope.sprite(art: ImageBitmap, x: Float, y: Float, width: Float, filter: ColorFilter) {
        val height = width * art.height / art.width
        drawImage(art, dstOffset = IntOffset((x - width / 2).roundToInt(), (y - height).roundToInt()),
            dstSize = IntSize(width.roundToInt().coerceAtLeast(1), height.roundToInt().coerceAtLeast(1)),
            colorFilter = filter, filterQuality = FilterQuality.Medium)
    }
}
