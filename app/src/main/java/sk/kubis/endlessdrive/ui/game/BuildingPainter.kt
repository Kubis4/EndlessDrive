package sk.kubis.endlessdrive.ui.game

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import sk.kubis.endlessdrive.domain.model.BuildingType
import sk.kubis.endlessdrive.game.world.WorldBuilding

/**
 * Budovy pri ceste. Každý typ má vlastnú siluetu, v noci svietia okná
 * a vyrabovaná budova ostane tmavá – hráč vidí, kde už bol.
 */
class BuildingPainter {
    private val path = Path()

    fun DrawScope.drawBuilding(
        b: WorldBuilding,
        px: Float,
        py: Float,
        s: Float,
        day: Float,
        near: Boolean
    ) {
        val night = 1f - day
        val lit = night > 0.35f && !b.looted
        // Tieň na zemi.
        drawOval(
            Color.Black.copy(alpha = 0.22f),
            topLeft = Offset(px - s * 2.2f, py - s * 0.12f),
            size = Size(s * 4.4f, s * 0.34f)
        )
        when (b.type) {
            BuildingType.HOUSE -> house(px, py, s, day, lit)
            BuildingType.GARAGE -> garage(px, py, s, day, lit)
            BuildingType.GAS_STATION -> gasStation(px, py, s, day, lit, b.pumpFuelL > 0.05f)
            BuildingType.AUTO_SHOP -> autoShop(px, py, s, day, lit)
        }
        if (b.landmark) depotFlag(px, py, s, day)
        if (near) marker(px, py, s, b.looted)
    }

    private fun DrawScope.house(x: Float, y: Float, s: Float, day: Float, lit: Boolean) {
        val w = s * 3.0f
        val h = s * 2.2f
        val left = x - w * 0.5f
        wall(left, y - h, w, h, shade(Color(0xFF9A7551), day))
        // Sedlová strecha.
        path.reset()
        path.moveTo(left - s * 0.28f, y - h)
        path.lineTo(x, y - h - s * 0.95f)
        path.lineTo(left + w + s * 0.28f, y - h)
        path.close()
        drawPath(path, shade(Color(0xFF6E4030), day))
        // Komín.
        drawRect(
            shade(Color(0xFF7A5340), day),
            topLeft = Offset(left + w * 0.68f, y - h - s * 0.86f),
            size = Size(s * 0.30f, s * 0.62f)
        )
        window(left + w * 0.16f, y - h * 0.78f, s * 0.52f, s * 0.44f, day, lit)
        window(left + w * 0.62f, y - h * 0.78f, s * 0.52f, s * 0.44f, day, lit)
        door(left + w * 0.40f, y, s * 0.50f, h * 0.52f, day)
    }

    private fun DrawScope.garage(x: Float, y: Float, s: Float, day: Float, lit: Boolean) {
        val w = s * 3.4f
        val h = s * 1.8f
        val left = x - w * 0.5f
        wall(left, y - h, w, h, shade(Color(0xFF7C7C6C), day))
        drawRect(
            shade(Color(0xFF54544A), day),
            topLeft = Offset(left - s * 0.16f, y - h - s * 0.22f),
            size = Size(w + s * 0.32f, s * 0.24f)
        )
        // Rolovacia brána so segmentmi.
        val gw = w * 0.60f
        val gh = h * 0.74f
        val gx = left + w * 0.20f
        drawRect(shade(Color(0xFF3D4148), day), topLeft = Offset(gx, y - gh), size = Size(gw, gh))
        var yy = y - gh
        while (yy < y - s * 0.05f) {
            drawLine(
                shade(Color(0xFF585E66), day),
                Offset(gx, yy), Offset(gx + gw, yy),
                strokeWidth = (s * 0.05f).coerceAtLeast(1f)
            )
            yy += s * 0.19f
        }
        window(left + w * 0.83f, y - h * 0.72f, s * 0.42f, s * 0.36f, day, lit)
    }

    private fun DrawScope.gasStation(
        x: Float,
        y: Float,
        s: Float,
        day: Float,
        lit: Boolean,
        hasFuel: Boolean
    ) {
        val w = s * 2.4f
        val h = s * 1.9f
        val left = x - w * 0.5f - s * 0.9f
        wall(left, y - h, w, h, shade(Color(0xFFB9AE95), day))
        drawRect(
            shade(Color(0xFF8B6A4E), day),
            topLeft = Offset(left - s * 0.14f, y - h - s * 0.2f),
            size = Size(w + s * 0.28f, s * 0.22f)
        )
        window(left + w * 0.12f, y - h * 0.72f, w * 0.72f, h * 0.34f, day, lit)
        door(left + w * 0.42f, y, s * 0.46f, h * 0.5f, day)

        // Prístrešok nad stojanmi.
        val cx = x + s * 1.5f
        val canopyY = y - s * 2.5f
        drawRect(
            shade(Color(0xFFC8503A), day),
            topLeft = Offset(cx - s * 1.5f, canopyY),
            size = Size(s * 3.0f, s * 0.34f)
        )
        for (dx in floatArrayOf(-1.25f, 1.25f)) {
            drawLine(
                shade(Color(0xFFB0A894), day),
                Offset(cx + s * dx, canopyY + s * 0.34f),
                Offset(cx + s * dx, y),
                strokeWidth = (s * 0.13f).coerceAtLeast(1.5f)
            )
        }
        if (lit) {
            drawRect(
                Color(0xFFFFE7A6).copy(alpha = 0.16f),
                topLeft = Offset(cx - s * 1.5f, canopyY + s * 0.34f),
                size = Size(s * 3.0f, s * 2.2f)
            )
        }
        // Dva stojany.
        for (dx in floatArrayOf(-0.55f, 0.55f)) {
            val pxx = cx + s * dx
            drawRect(
                shade(if (hasFuel) Color(0xFFDCDCD2) else Color(0xFF8A8A80), day),
                topLeft = Offset(pxx - s * 0.16f, y - s * 0.78f),
                size = Size(s * 0.32f, s * 0.78f)
            )
            drawRect(
                shade(if (hasFuel) Color(0xFF4CAF50) else Color(0xFF55584F), day),
                topLeft = Offset(pxx - s * 0.11f, y - s * 0.70f),
                size = Size(s * 0.22f, s * 0.18f)
            )
        }
        // Totem.
        val sx = x - s * 2.6f
        drawLine(
            shade(Color(0xFF9A9384), day),
            Offset(sx, y), Offset(sx, y - s * 3.1f),
            strokeWidth = (s * 0.12f).coerceAtLeast(1.5f)
        )
        drawRect(
            shade(if (hasFuel) Color(0xFFE8B23C) else Color(0xFF6E6A5E), day),
            topLeft = Offset(sx - s * 0.55f, y - s * 3.5f),
            size = Size(s * 1.1f, s * 0.62f)
        )
    }

    private fun DrawScope.autoShop(x: Float, y: Float, s: Float, day: Float, lit: Boolean) {
        val w = s * 3.8f
        val h = s * 2.3f
        val left = x - w * 0.5f
        wall(left, y - h, w, h, shade(Color(0xFF6E7C89), day))
        // Pílová strecha.
        path.reset()
        path.moveTo(left, y - h)
        var sx = left
        var up = true
        while (sx < left + w) {
            val nx = (sx + w / 4f).coerceAtMost(left + w)
            path.lineTo(nx, y - h - (if (up) s * 0.45f else 0f))
            up = !up
            sx = nx
        }
        path.lineTo(left + w, y - h)
        path.close()
        drawPath(path, shade(Color(0xFF47535E), day))

        val bw = w * 0.44f
        val bh = h * 0.72f
        drawRect(shade(Color(0xFF2E3840), day), topLeft = Offset(left + w * 0.08f, y - bh), size = Size(bw, bh))
        drawRect(
            shade(Color(0xFF9AA6B0), day),
            topLeft = Offset(left + w * 0.08f, y - bh),
            size = Size(bw, bh),
            style = Stroke(width = (s * 0.06f).coerceAtLeast(1f))
        )
        window(left + w * 0.60f, y - h * 0.72f, w * 0.30f, h * 0.30f, day, lit)
        // Stoh pneumatík.
        for (i in 0 until 3) {
            drawOval(
                shade(Color(0xFF23262A), day),
                topLeft = Offset(left + w + s * 0.15f, y - s * (0.28f + i * 0.24f)),
                size = Size(s * 0.62f, s * 0.26f)
            )
        }
    }

    // --- Stavebné prvky ----------------------------------------------------

    private fun DrawScope.wall(left: Float, top: Float, w: Float, h: Float, base: Color) {
        drawRect(base, topLeft = Offset(left, top), size = Size(w, h))
        // Jemné vertikálne stmavenie – dodá objem bez shaderov.
        drawRect(
            Color.Black.copy(alpha = 0.16f),
            topLeft = Offset(left, top),
            size = Size(w * 0.22f, h)
        )
        drawRect(
            Color.White.copy(alpha = 0.05f),
            topLeft = Offset(left + w * 0.22f, top),
            size = Size(w * 0.3f, h)
        )
    }

    private fun DrawScope.window(x: Float, y: Float, w: Float, h: Float, day: Float, lit: Boolean) {
        val glass = if (lit) Color(0xFFFFD98A) else shade(Color(0xFF2B3138), day)
        drawRect(glass, topLeft = Offset(x, y), size = Size(w, h))
        drawRect(
            shade(Color(0xFF3E3428), day),
            topLeft = Offset(x, y),
            size = Size(w, h),
            style = Stroke(width = (w * 0.09f).coerceAtLeast(1f))
        )
        drawLine(
            shade(Color(0xFF3E3428), day),
            Offset(x + w * 0.5f, y), Offset(x + w * 0.5f, y + h),
            strokeWidth = (w * 0.07f).coerceAtLeast(1f)
        )
        if (lit) {
            drawRect(
                Color(0xFFFFE0A0).copy(alpha = 0.10f),
                topLeft = Offset(x - w * 0.35f, y - h * 0.25f),
                size = Size(w * 1.7f, h * 1.9f)
            )
        }
    }

    private fun DrawScope.door(x: Float, y: Float, w: Float, h: Float, day: Float) {
        drawRect(shade(Color(0xFF4A3527), day), topLeft = Offset(x, y - h), size = Size(w, h))
        drawCircle(shade(Color(0xFFCBB47A), day), w * 0.08f, Offset(x + w * 0.8f, y - h * 0.5f))
    }

    /** Vlajka nad depom – vidno ju už z diaľky, dá jazde cieľ. */
    private fun DrawScope.depotFlag(x: Float, y: Float, s: Float, day: Float) {
        val mast = y - s * 5.2f
        drawLine(
            shade(Color(0xFFBFB6A4), day),
            Offset(x - s * 2.2f, y),
            Offset(x - s * 2.2f, mast),
            strokeWidth = (s * 0.10f).coerceAtLeast(1.5f)
        )
        path.reset()
        path.moveTo(x - s * 2.2f, mast)
        path.lineTo(x - s * 0.7f, mast + s * 0.42f)
        path.lineTo(x - s * 2.2f, mast + s * 0.84f)
        path.close()
        drawPath(path, shade(Color(0xFFD2AE63), day))
    }

    /** Ukazovateľ nad budovou, keď je auto v dosahu. */
    private fun DrawScope.marker(x: Float, y: Float, s: Float, looted: Boolean) {
        val col = if (looted) Color(0xFF8A8F97) else Color(0xFFE8C15A)
        val topY = y - s * 4.3f
        path.reset()
        path.moveTo(x, topY + s * 0.55f)
        path.lineTo(x - s * 0.30f, topY)
        path.lineTo(x + s * 0.30f, topY)
        path.close()
        drawPath(path, col.copy(alpha = 0.9f))
        drawLine(
            col.copy(alpha = 0.55f),
            Offset(x, topY - s * 0.45f), Offset(x, topY - s * 0.05f),
            strokeWidth = (s * 0.12f).coerceAtLeast(2f),
            cap = StrokeCap.Round
        )
    }
}
