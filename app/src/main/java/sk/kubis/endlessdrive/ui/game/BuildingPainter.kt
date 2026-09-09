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
import androidx.compose.ui.graphics.drawscope.scale
import sk.kubis.endlessdrive.core.MathX
import sk.kubis.endlessdrive.domain.model.BiomeType
import sk.kubis.endlessdrive.domain.model.BuildingType
import sk.kubis.endlessdrive.game.world.WorldBuilding

/**
 * Budovy pri ceste. Každý typ má vlastnú siluetu, v noci svietia okná
 * a vyrabovaná budova ostane tmavá – hráč vidí, kde už bol.
 */
class BuildingPainter(sprites: BuildingSprites? = null) {
    private val spritePainter = sprites?.let { BuildingSpritePainter(it) }
    private val path = Path()
    private val materials = MaterialPainter()
    private var seed = 0
    private var variant = 0
    private var biome = BiomeType.RURAL
    private var textureDay = 1f
    private var wallKind = MaterialKind.BRICK
    private var snow = 0f

    private fun roll(salt: Int) = MathX.hash01(seed, salt)
    private fun palette(base: Color): Color {
        val local = when {
            biome.arid -> Color(0xFFC3A27D)
            biome == BiomeType.ALPINE -> Color(0xFF879A9D)
            biome == BiomeType.INDUSTRIAL -> Color(0xFF747E7B)
            biome == BiomeType.FOREST -> Color(0xFF63746E)
            biome == BiomeType.FOREST_ALIVE -> Color(0xFF936246)
            biome == BiomeType.WASTELAND -> Color(0xFF8A867D)
            biome == BiomeType.RURAL -> Color(0xFF93886A)
            else -> Color(0xFFB49A78)
        }
        val paint = when (variant) {
            0 -> base
            1 -> Color(0xFF9DACA0)
            2 -> Color(0xFFB59B82)
            else -> Color(0xFF8595A0)
        }
        return shade(lerp(paint, local, 0.32f), textureDay)
    }

    fun DrawScope.drawBuilding(
        b: WorldBuilding,
        px: Float,
        py: Float,
        s: Float,
        day: Float,
        near: Boolean,
        slopeDeg: Float = 0f,
        environment: BiomeType = BiomeType.RURAL,
        winterAmount: Float = 0f,
        paintService: Boolean = false
    ) {
        seed = (b.id xor (b.id ushr 32)).toInt()
        variant = (roll(7103) * 4).toInt().coerceAtMost(3)
        biome = environment
        textureDay = day
        snow = if (biome == BiomeType.ALPINE) 1f else winterAmount.coerceIn(0f, 1f)
        wallKind = when {
            biome.arid -> MaterialKind.CONCRETE
            b.type == BuildingType.HOUSE && (variant % 2 == 0 || biome == BiomeType.ALPINE || biome == BiomeType.FOREST_ALIVE) -> MaterialKind.PLANKS
            b.type != BuildingType.HOUSE && variant % 2 == 0 -> MaterialKind.METAL
            else -> MaterialKind.BRICK
        }
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
            val authored = spritePainter
            if (authored != null) {
                with(authored) { draw(b, px, py, s, day, environment, winterAmount, paintService) }
                if (near) drawSearchMarker(px, py, s, b.looted)
                return@rotate
            }
            // Keep variations inside the original placement footprint; markers remain upright.
            scale(scaleX = (if (roll(7193) < 0.5f) -1f else 1f) * (0.88f + roll(7207) * 0.12f),
                scaleY = 0.88f + roll(7211) * 0.22f, pivot = Offset(px, py)) {
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
            }
            if (b.landmark) relayTower(px, py, s, day, b.relayRestored)
            if (near) drawSearchMarker(px, py, s, b.looted)
        }
    }

    fun DrawScope.drawSearchMarker(x: Float, y: Float, s: Float, looted: Boolean) {
        marker(x, y, s, looted)
    }

    private fun DrawScope.house(x: Float, y: Float, s: Float, day: Float, lit: Boolean) {
        val w = s * 3.0f
        val h = s * (if (variant == 3) 2.75f else 2.2f)
        val left = x - w * 0.5f
        wall(left, y - h, w, h, palette(Color(0xFF9A7551)))
        val roofRise = s * (if (biome.arid) 0.40f else if (variant == 1) 0.65f else 0.95f)
        // Sedlová strecha.
        path.reset()
        path.moveTo(left - s * 0.28f, y - h)
        path.lineTo(x, y - h - roofRise)
        path.lineTo(left + w + s * 0.28f, y - h)
        path.close()
        drawPath(path, shade(Color(0xFF6E4030), day))
        materials.shape(this, path, MaterialKind.BRICK, left, y - h - roofRise, s * 1.6f, 0.75f * day)
        if (snow > 0f) {
            drawLine(shade(Color(0xFFE2EBEA), day).copy(alpha = snow), Offset(left - s * 0.28f, y - h), Offset(x, y - h - roofRise), s * 0.12f, StrokeCap.Round)
            drawLine(shade(Color(0xFFE2EBEA), day).copy(alpha = snow), Offset(x, y - h - roofRise), Offset(left + w + s * 0.28f, y - h), s * 0.12f, StrokeCap.Round)
        }
        // Odkvapová hrana a rady šindľov kopírujú sklon strechy.
        drawLine(
            shade(Color(0xFF3D2922), day),
            Offset(left - s * 0.28f, y - h),
            Offset(left + w + s * 0.28f, y - h),
            strokeWidth = (s * 0.12f).coerceAtLeast(1.5f)
        )
        for (i in 1..4) {
            val t = i / 5f
            val yy = y - h - roofRise * (1f - t)
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
        // Corner trim frames the textured facade.
        drawRect(palette(Color(0xFFD1C5A6)), Offset(left, y - h), Size(s * 0.07f, h))
        drawRect(palette(Color(0xFFD1C5A6)), Offset(left + w - s * 0.07f, y - h), Size(s * 0.07f, h))
        drawCircle(shade(Color(0xFF2B3138), day), s * 0.13f, Offset(x, y - h - roofRise * 0.32f))
        drawCircle(shade(Color(0xFFC2AA7D), day), s * 0.13f, Offset(x, y - h - roofRise * 0.32f), style = Stroke((s * 0.04f).coerceAtLeast(1f)))
        window(left + w * 0.16f, y - h * 0.78f, s * 0.52f, s * 0.44f, day, lit)
        window(left + w * 0.62f, y - h * 0.78f, s * 0.52f, s * 0.44f, day, lit)
        for (wx in floatArrayOf(left + w * 0.16f, left + w * 0.62f)) {
            drawRect(
                shade(Color(0xFF6B4935), day),
                topLeft = Offset(wx - s * 0.04f, y - h * 0.78f + s * 0.44f),
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
        wall(left, y - h, w, h, palette(Color(0xFF7C7C6C)))
        drawRect(
            shade(Color(0xFF54544A), day),
            topLeft = Offset(left - s * 0.16f, y - h - s * 0.22f),
            size = Size(w + s * 0.32f, s * 0.24f)
        )
        // Trapézový bok strechy dá plochej hale hĺbku.
        if (snow > 0f) drawLine(shade(Color(0xFFE2EBEA), day).copy(alpha = snow),
            Offset(left - s * 0.16f, y - h - s * 0.22f), Offset(left + w + s * 0.16f, y - h - s * 0.22f), s * 0.12f, StrokeCap.Round)
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
        val gw = w * (if (variant == 1) 0.48f else 0.60f)
        val gh = h * 0.74f
        val gx = left + w * 0.20f
        drawRect(shade(Color(0xFF3D4148), day), topLeft = Offset(gx, y - gh), size = Size(gw, gh))
        textureRect(gx, y - gh, gw, gh, MaterialKind.METAL, day)
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
        wall(left, y - h, w, h, palette(Color(0xFFB9AE95)))
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
            palette(Color(0xFFC8503A)),
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
        if (snow > 0f) drawLine(shade(Color(0xFFE2EBEA), day).copy(alpha = snow),
            Offset(cx - s * 1.5f, canopyY), Offset(cx + s * 1.5f, canopyY), s * 0.12f, StrokeCap.Round)
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
        wall(left, y - h, w, h, palette(Color(0xFF6E7C89)))
        // Pílová strecha.
        path.reset()
        path.moveTo(left, y - h)
        var sx = left
        var up = true
        while (sx < left + w) {
            val nx = (sx + w / 4f).coerceAtMost(left + w)
            path.lineTo(nx, y - h - (if (variant == 1) s * 0.14f else if (up) s * 0.45f else 0f))
            up = !up
            sx = nx
        }
        path.lineTo(left + w, y - h)
        path.close()
        drawPath(path, shade(Color(0xFF47535E), day))
        materials.shape(this, path, MaterialKind.METAL, left, y - h, s * 1.8f, day)
        if (snow > 0f) {
            var roofX = left
            var roofY = y - h
            for (i in 1..4) {
                val nextX = left + w * i / 4f
                val nextY = y - h - (if (variant == 1) s * 0.14f else if (i % 2 == 1) s * 0.45f else 0f)
                drawLine(shade(Color(0xFFE2EBEA), day).copy(alpha = snow), Offset(roofX, roofY), Offset(nextX, nextY), s * 0.10f, StrokeCap.Round)
                roofX = nextX
                roofY = nextY
            }
        }

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
        textureRect(left + w * 0.08f, y - bh, bw, bh, MaterialKind.METAL, day)
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
    private fun DrawScope.textureRect(x: Float, y: Float, w: Float, h: Float, kind: MaterialKind, alpha: Float) {
        path.reset()
        path.addRect(androidx.compose.ui.geometry.Rect(x, y, x + w, y + h))
        materials.shape(this, path, kind, x - roll(7307) * w, y, w * 0.62f, alpha)
    }

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
        textureRect(left, top, w, h, wallKind, 0.85f * (0.15f + textureDay * 0.85f))
        // Seeded rain streaks / chipped paint remain attached to this facade when scrolling.
        for (i in 0 until 12) {
            val x = left + w * roll(7409 + i)
            val length = h * (0.08f + roll(7507 + i) * 0.42f)
            drawRect(base.copy(alpha = 0.18f), Offset(x, top + h * 0.06f), Size(w * 0.014f, length))
            if (roll(7603 + i) > 0.55f) drawRect(lerp(base, Color(0xFF483D33), 0.45f).copy(alpha = 0.35f),
                Offset(x, top + h * (0.65f + roll(7703 + i) * 0.25f)), Size(w * 0.035f, h * 0.025f))
        }
        if (snow > 0f) drawLine(shade(Color(0xFFE2EBEA), textureDay).copy(alpha = snow),
            Offset(left, top), Offset(left + w, top), h * 0.045f, StrokeCap.Round)
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
        if (!lit) drawRect(Brush.linearGradient(listOf(shade(Color(0xFF869A9E), day).copy(alpha = 0.65f), Color.Transparent),
            start = Offset(x, y), end = Offset(x + w, y + h)), Offset(x, y), Size(w, h))
        if (variant == 1) {
            for (dx in floatArrayOf(-w * 0.27f, w * 1.05f)) {
                drawRect(palette(Color(0xFF52665C)), Offset(x + dx, y - h * 0.05f), Size(w * 0.22f, h * 1.1f))
            }
        }
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

    /** Samostatný rádiový stožiar nad relay stanicou. */
    private fun DrawScope.relayTower(x: Float, y: Float, s: Float, day: Float, restored: Boolean) {
        val mastX = x + s * 1.65f
        val baseY = y - s * 0.05f
        val topY = y - s * 8.2f
        val metal = shade(Color(0xFF9FA6A3), day)
        val darkMetal = shade(Color(0xFF4D5655), day)
        val signal = if (restored) Color(0xFF63E3C6) else Color(0xFFFF6B55)

        drawLine(metal, Offset(mastX, baseY), Offset(mastX, topY), (s * 0.12f).coerceAtLeast(1.5f))
        drawLine(darkMetal, Offset(mastX - s * 0.72f, baseY), Offset(mastX, topY), (s * 0.08f).coerceAtLeast(1f))
        drawLine(darkMetal, Offset(mastX + s * 0.72f, baseY), Offset(mastX, topY), (s * 0.08f).coerceAtLeast(1f))
        var rungY = baseY - s * 0.9f
        while (rungY > topY + s * 0.6f) {
            val half = s * ((baseY - rungY) / (baseY - topY)).coerceIn(0.08f, 0.72f)
            drawLine(darkMetal, Offset(mastX - half, rungY), Offset(mastX + half, rungY), (s * 0.07f).coerceAtLeast(1f))
            rungY -= s * 0.95f
        }
        drawCircle(signal.copy(alpha = if (restored) 0.95f else 0.75f), s * 0.20f, Offset(mastX, topY))
        drawCircle(signal.copy(alpha = 0.20f), s * 0.46f, Offset(mastX, topY))
        // Small dish and control cabinet make the object read as a radio relay,
        // not as a decorative flag attached to a fuel station.
        drawLine(signal, Offset(mastX - s * 0.55f, topY + s * 1.25f), Offset(mastX + s * 0.35f, topY + s * 1.0f), (s * 0.11f).coerceAtLeast(1.5f))
        drawCircle(darkMetal, s * 0.18f, Offset(mastX - s * 0.60f, topY + s * 1.28f))
        drawRect(darkMetal, Offset(mastX - s * 0.48f, baseY - s * 0.9f), Size(s * 0.95f, s * 0.60f))
        drawRect(signal.copy(alpha = 0.75f), Offset(mastX - s * 0.30f, baseY - s * 0.73f), Size(s * 0.18f, s * 0.14f))
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
