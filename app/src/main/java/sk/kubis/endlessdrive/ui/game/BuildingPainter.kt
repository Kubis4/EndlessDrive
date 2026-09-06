package sk.kubis.endlessdrive.ui.game

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
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
        near: Boolean,
        slopeDeg: Float = 0f
    ) {
        val night = 1f - day
        val lit = night > 0.35f && !b.looted
        // Stojí na svahu ako vozovka – otočka okolo päty, nie celej oblohy.
        rotate(degrees = slopeDeg, pivot = Offset(px, py)) {
            // Tieň na zemi.
            drawOval(
                Color.Black.copy(alpha = 0.22f),
                topLeft = Offset(px - s * 2.2f, py - s * 0.12f),
                size = Size(s * 4.4f, s * 0.34f)
            )
            when (b.type) {
                BuildingType.HOUSE -> house(px, py, s, day, lit)
                BuildingType.GARAGE -> garage(px, py, s, day, lit)
                BuildingType.GAS_STATION -> gasStation(
                    px, py, s, day, lit, b.pumpFuelL > 0.05f || b.pumpDieselL > 0.05f
                )
                BuildingType.AUTO_SHOP -> autoShop(px, py, s, day, lit)
                // Vrak kreslí SceneryPainter zo spritov – tu ostane len tieň a značka.
                BuildingType.WRECK -> Unit
            }
            if (b.landmark) depotFlag(px, py, s, day)
            if (near) drawSearchMarker(px, py, s, b.looted)
        }
    }

    fun DrawScope.drawSearchMarker(x: Float, y: Float, s: Float, looted: Boolean) {
        marker(x, y, s, looted)
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
        // Odkvapová hrana a rady šindľov kopírujú sklon strechy.
        drawLine(
            shade(Color(0xFF3D2922), day),
            Offset(left - s * 0.28f, y - h),
            Offset(left + w + s * 0.28f, y - h),
            strokeWidth = (s * 0.12f).coerceAtLeast(1.5f)
        )
        for (i in 1..4) {
            val t = i / 5f
            val yy = y - h - s * 0.95f * (1f - t)
            val half = (w * 0.5f + s * 0.28f) * t
            drawLine(
                Color.Black.copy(alpha = 0.15f),
                Offset(x - half, yy), Offset(x + half, yy),
                strokeWidth = (s * 0.025f).coerceAtLeast(1f)
            )
        }
        // Komín.
        drawRect(
            shade(Color(0xFF7A5340), day),
            topLeft = Offset(left + w * 0.68f, y - h - s * 0.86f),
            size = Size(s * 0.30f, s * 0.62f)
        )
        drawRect(
            shade(Color(0xFF4D342B), day),
            topLeft = Offset(left + w * 0.68f - s * 0.05f, y - h - s * 0.91f),
            size = Size(s * 0.40f, s * 0.08f)
        )
        // Drevené obloženie, podkrovné okno a parapety.
        for (i in 1 until 8) {
            val sx = left + w * i / 8f
            drawLine(Color.Black.copy(alpha = 0.08f), Offset(sx, y - h), Offset(sx, y), 1f)
        }
        drawCircle(shade(Color(0xFF2B3138), day), s * 0.18f, Offset(x, y - h - s * 0.29f))
        drawCircle(shade(Color(0xFFC2AA7D), day), s * 0.18f, Offset(x, y - h - s * 0.29f), style = Stroke((s * 0.05f).coerceAtLeast(1f)))
        window(left + w * 0.16f, y - h * 0.78f, s * 0.52f, s * 0.44f, day, lit)
        window(left + w * 0.62f, y - h * 0.78f, s * 0.52f, s * 0.44f, day, lit)
        for (wx in floatArrayOf(left + w * 0.16f, left + w * 0.62f)) {
            drawRect(
                shade(Color(0xFF6B4935), day),
                topLeft = Offset(wx - s * 0.04f, y - h * 0.33f),
                size = Size(s * 0.60f, s * 0.07f)
            )
        }
        door(left + w * 0.40f, y, s * 0.50f, h * 0.52f, day)
        // Malá strieška, stĺpiky a dva schody pred vstupom.
        val porchX = left + w * 0.32f
        drawRect(
            shade(Color(0xFF5A382B), day),
            topLeft = Offset(porchX, y - h * 0.58f),
            size = Size(w * 0.36f, s * 0.12f)
        )
        for (postX in floatArrayOf(porchX + s * 0.06f, porchX + w * 0.36f - s * 0.06f)) {
            drawLine(shade(Color(0xFF6D533B), day), Offset(postX, y - h * 0.52f), Offset(postX, y), (s * 0.06f).coerceAtLeast(1f))
        }
        drawRect(shade(Color(0xFF6D6256), day), Offset(left + w * 0.35f, y - s * 0.10f), Size(w * 0.30f, s * 0.10f))
        drawRect(shade(Color(0xFF817469), day), Offset(left + w * 0.39f, y - s * 0.20f), Size(w * 0.22f, s * 0.10f))
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
        // Trapézový bok strechy dá plochej hale hĺbku.
        path.reset()
        path.moveTo(left + w, y - h)
        path.lineTo(left + w + s * 0.34f, y - h - s * 0.16f)
        path.lineTo(left + w + s * 0.34f, y - s * 0.10f)
        path.lineTo(left + w, y)
        path.close()
        drawPath(path, shade(Color(0xFF5F6258), day))
        // Spoje prefabrikovaných panelov a skrutky.
        for (i in 1 until 6) {
            val sx = left + w * i / 6f
            drawLine(Color.Black.copy(alpha = 0.13f), Offset(sx, y - h), Offset(sx, y), 1f)
            drawCircle(Color.Black.copy(alpha = 0.25f), (s * 0.025f).coerceAtLeast(1f), Offset(sx, y - h * 0.12f))
        }
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
        // Koľajnice, rukoväť a výstražné rohy brány.
        drawRect(
            shade(Color(0xFF262A2F), day),
            Offset(gx - s * 0.07f, y - gh),
            Size(s * 0.07f, gh)
        )
        drawRect(
            shade(Color(0xFF262A2F), day),
            Offset(gx + gw, y - gh),
            Size(s * 0.07f, gh)
        )
        drawLine(
            shade(Color(0xFFB0A792), day),
            Offset(gx + gw * 0.43f, y - s * 0.17f),
            Offset(gx + gw * 0.57f, y - s * 0.17f),
            (s * 0.045f).coerceAtLeast(1f),
            StrokeCap.Round
        )
        for (i in 0..5) {
            val sx = gx + gw * i / 6f
            drawLine(
                shade(if (i % 2 == 0) Color(0xFFE0A33C) else Color(0xFF272727), day),
                Offset(sx, y - s * 0.08f),
                Offset(sx + gw / 6f, y - s * 0.25f),
                (s * 0.06f).coerceAtLeast(1f)
            )
        }
        window(left + w * 0.83f, y - h * 0.72f, s * 0.42f, s * 0.36f, day, lit)
        // Vonkajšie svetlo, odvetranie a debna pri stene.
        drawCircle(
            if (lit) Color(0xFFFFD98A) else shade(Color(0xFF8E856F), day),
            s * 0.09f,
            Offset(left + w * 0.74f, y - h * 0.83f)
        )
        drawRect(shade(Color(0xFF33383C), day), Offset(left + w * 0.82f, y - h * 0.35f), Size(s * 0.45f, s * 0.22f))
        for (i in 1..3) {
            val vy = y - h * 0.35f + s * 0.22f * i / 4f
            drawLine(Color.Black.copy(alpha = 0.35f), Offset(left + w * 0.83f, vy), Offset(left + w * 0.82f + s * 0.42f, vy), 1f)
        }
        drawRect(shade(Color(0xFF6A4E32), day), Offset(left + w * 0.78f, y - s * 0.35f), Size(s * 0.48f, s * 0.35f))
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
        // Fasádny pás, markíza a samostatné tabule nad obchodom.
        drawRect(shade(Color(0xFFF0E6CD), day), Offset(left, y - h * 0.92f), Size(w, s * 0.18f))
        drawRect(shade(Color(0xFFC8503A), day), Offset(left, y - h * 0.92f), Size(w * 0.58f, s * 0.07f))
        for (i in 0..3) {
            drawRect(
                shade(if (i % 2 == 0) Color(0xFFC8503A) else Color(0xFFF0E6CD), day),
                Offset(left + w * 0.08f + i * w * 0.11f, y - h * 0.47f),
                Size(w * 0.11f, s * 0.12f)
            )
        }
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
        drawRect(
            shade(Color(0xFFF3E7CC), day),
            Offset(cx - s * 1.5f, canopyY + s * 0.23f),
            Size(s * 3.0f, s * 0.11f)
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
        // Zapustené svetlá v spodnej hrane prístrešku.
        for (dx in floatArrayOf(-0.85f, 0f, 0.85f)) {
            drawOval(
                if (lit) Color(0xFFFFE7A6) else shade(Color(0xFF555047), day),
                Offset(cx + s * dx - s * 0.11f, canopyY + s * 0.27f),
                Size(s * 0.22f, s * 0.08f)
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
            // Displej, tlačidlá, hadica a ochranný stĺpik.
            drawRect(
                shade(Color(0xFF1D262A), day),
                Offset(pxx - s * 0.09f, y - s * 0.66f),
                Size(s * 0.18f, s * 0.10f)
            )
            drawCircle(shade(Color(0xFFE0A33C), day), s * 0.025f, Offset(pxx - s * 0.04f, y - s * 0.48f))
            drawCircle(shade(Color(0xFFD9584A), day), s * 0.025f, Offset(pxx + s * 0.04f, y - s * 0.48f))
            drawArc(
                shade(Color(0xFF202326), day),
                250f,
                220f,
                false,
                Offset(pxx - s * 0.28f, y - s * 0.68f),
                Size(s * 0.55f, s * 0.62f),
                style = Stroke((s * 0.045f).coerceAtLeast(1f), cap = StrokeCap.Round)
            )
            drawLine(
                shade(Color(0xFFE0A33C), day),
                Offset(pxx - s * 0.27f, y - s * 0.26f),
                Offset(pxx - s * 0.27f, y),
                (s * 0.09f).coerceAtLeast(1f)
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
        // Cenové riadky na toteme a odpadkový kôš pri obchode.
        for (i in 1..2) {
            val ly = y - s * 3.5f + s * 0.62f * i / 3f
            drawLine(Color.Black.copy(alpha = 0.30f), Offset(sx - s * 0.46f, ly), Offset(sx + s * 0.46f, ly), (s * 0.025f).coerceAtLeast(1f))
        }
        drawRect(shade(Color(0xFF3F4B4D), day), Offset(left - s * 0.35f, y - s * 0.45f), Size(s * 0.28f, s * 0.45f))
        drawRect(shade(Color(0xFF657174), day), Offset(left - s * 0.39f, y - s * 0.48f), Size(s * 0.36f, s * 0.07f))
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

        // Strešné odvetranie a žľab po celej fasáde.
        for (i in 0..2) {
            val vx = left + w * (0.22f + i * 0.28f)
            drawRect(shade(Color(0xFF3A454E), day), Offset(vx, y - h - s * 0.62f), Size(s * 0.20f, s * 0.42f))
            drawRect(shade(Color(0xFF77838B), day), Offset(vx - s * 0.06f, y - h - s * 0.65f), Size(s * 0.32f, s * 0.08f))
        }
        drawLine(
            shade(Color(0xFF303A42), day),
            Offset(left, y - h), Offset(left + w, y - h),
            (s * 0.10f).coerceAtLeast(1.5f)
        )

        // Vývesný pás s jednoduchým symbolom kľúča.
        val signX = left + w * 0.56f
        val signY = y - h * 0.91f
        drawRect(shade(Color(0xFFD2AE63), day), Offset(signX, signY), Size(w * 0.36f, s * 0.30f))
        drawLine(
            shade(Color(0xFF273139), day),
            Offset(signX + w * 0.08f, signY + s * 0.22f),
            Offset(signX + w * 0.24f, signY + s * 0.08f),
            (s * 0.08f).coerceAtLeast(1.5f),
            StrokeCap.Round
        )
        drawCircle(
            shade(Color(0xFF273139), day),
            s * 0.10f,
            Offset(signX + w * 0.26f, signY + s * 0.07f),
            style = Stroke((s * 0.06f).coerceAtLeast(1f))
        )

        val bw = w * 0.44f
        val bh = h * 0.72f
        drawRect(shade(Color(0xFF2E3840), day), topLeft = Offset(left + w * 0.08f, y - bh), size = Size(bw, bh))
        drawRect(
            shade(Color(0xFF9AA6B0), day),
            topLeft = Offset(left + w * 0.08f, y - bh),
            size = Size(bw, bh),
            style = Stroke(width = (s * 0.06f).coerceAtLeast(1f))
        )
        // Segmenty priemyselnej brány, spodné okná a výstražný prah.
        for (i in 1 until 7) {
            val gy = y - bh + bh * i / 7f
            drawLine(Color.White.copy(alpha = 0.10f), Offset(left + w * 0.08f, gy), Offset(left + w * 0.08f + bw, gy), 1f)
        }
        for (i in 0..2) {
            drawRect(
                shade(Color(0xFF26343B), day),
                Offset(left + w * 0.11f + i * bw * 0.29f, y - bh * 0.72f),
                Size(bw * 0.22f, bh * 0.14f)
            )
        }
        for (i in 0..5) {
            val sx = left + w * 0.08f + bw * i / 6f
            drawLine(
                shade(if (i % 2 == 0) Color(0xFFE0A33C) else Color(0xFF292929), day),
                Offset(sx, y - s * 0.06f),
                Offset(sx + bw / 6f, y - s * 0.20f),
                (s * 0.055f).coerceAtLeast(1f)
            )
        }
        window(left + w * 0.60f, y - h * 0.72f, w * 0.30f, h * 0.30f, day, lit)
        door(left + w * 0.78f, y, s * 0.42f, h * 0.42f, day)
        // Olejové sudy pri stene.
        for (i in 0..1) {
            val bx = left - s * (0.38f + i * 0.30f)
            drawRect(shade(if (i == 0) Color(0xFF355E78) else Color(0xFF8B4A35), day), Offset(bx, y - s * 0.50f), Size(s * 0.26f, s * 0.50f))
            drawLine(Color.White.copy(alpha = 0.15f), Offset(bx, y - s * 0.39f), Offset(bx + s * 0.26f, y - s * 0.39f), 1f)
            drawLine(Color.White.copy(alpha = 0.15f), Offset(bx, y - s * 0.10f), Offset(bx + s * 0.26f, y - s * 0.10f), 1f)
        }
        // Stoh pneumatík.
        for (i in 0 until 3) {
            val top = Offset(left + w + s * 0.15f, y - s * (0.28f + i * 0.24f))
            drawOval(
                shade(Color(0xFF23262A), day),
                topLeft = top,
                size = Size(s * 0.62f, s * 0.26f)
            )
            drawOval(
                shade(Color(0xFF54585A), day),
                topLeft = Offset(top.x + s * 0.21f, top.y + s * 0.07f),
                size = Size(s * 0.20f, s * 0.10f)
            )
        }
    }

    // --- Stavebné prvky ----------------------------------------------------

    /**
     * Múr s materiálom: plynulý svetelný spád zľava doprava, zvislé zašpinenie
     * pri zemi, vodorovné škáry muriva a náznak podmurovky. Ploché obdĺžniky
     * s dvoma pruhmi pôsobili ako papierová kulisa.
     */
    private fun DrawScope.wall(left: Float, top: Float, w: Float, h: Float, base: Color) {
        drawRect(
            brush = Brush.horizontalGradient(
                0f to lerp(base, Color.Black, 0.30f),
                0.42f to base,
                0.78f to lerp(base, Color.White, 0.10f),
                1f to lerp(base, Color.Black, 0.14f),
                startX = left,
                endX = left + w
            ),
            topLeft = Offset(left, top),
            size = Size(w, h)
        )
        // Zašpinenie a vlhkosť pri zemi.
        drawRect(
            brush = Brush.verticalGradient(
                0f to Color.Transparent,
                1f to Color.Black.copy(alpha = 0.30f),
                startY = top + h * 0.55f,
                endY = top + h
            ),
            topLeft = Offset(left, top + h * 0.55f),
            size = Size(w, h * 0.45f)
        )
        // Škáry muriva – riedke, len aby stena nebola hladká plocha.
        val rows = (h / (w * 0.16f)).toInt().coerceIn(2, 7)
        for (i in 1 until rows) {
            val ly = top + h * i / rows
            drawLine(
                Color.Black.copy(alpha = 0.10f),
                Offset(left, ly), Offset(left + w, ly),
                strokeWidth = 1f
            )
        }
        // Podmurovka – tmavší pás, na ktorom dom stojí.
        drawRect(
            lerp(base, Color.Black, 0.42f),
            topLeft = Offset(left, top + h * 0.93f),
            size = Size(w, h * 0.07f)
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
