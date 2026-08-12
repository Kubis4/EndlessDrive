package sk.kubis.endlessdrive.ui.game

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp
import sk.kubis.endlessdrive.core.GameConfig
import sk.kubis.endlessdrive.core.MathX
import sk.kubis.endlessdrive.domain.model.BiomeType
import sk.kubis.endlessdrive.game.DepthProjection

/**
 * Kulisy pozdĺž cesty – stromy, kríky, kamene, stĺpy elektriky, ploty a míľniky.
 * Všetko je deterministické z pozície (hash), takže netreba žiadny stav ani alokácie.
 */
class SceneryPainter {
    private val propPath = Path()

    /** Vegetácia a stĺpy za cestou. */
    fun DrawScope.drawBackProps(
        fromX: Float,
        toX: Float,
        biome: BiomeType,
        day: Float,
        depth: DepthProjection,
        heightAt: (Float) -> Float
    ) {
        val first = MathX.floorDiv(fromX, CELL)
        val last = MathX.floorDiv(toX, CELL)
        for (cell in first..last) {
            val r = MathX.hash01(cell, BACK_SALT)
            if (r > densityFor(biome)) continue
            val wx = cell * CELL + MathX.hash01(cell, 991) * CELL * 0.8f
            // Hĺbka musí ostať v páse lúky (GameRenderer.SCENERY_BACK_DEPTH),
            // inak by kulisa vyletela nad terén k úbežníku.
            val d = GameConfig.ROAD_DEPTH + 0.45f + MathX.hash01(cell, 137) * 1.2f
            val kind = MathX.hash01(cell, 313)
            val scale = MathX.hash01(cell, 577)
            drawProp(wx, d, kind, scale, biome, day, depth, heightAt)
        }
        drawPowerLine(fromX, toX, day, depth, heightAt)
        drawMilestones(fromX, toX, day, depth, heightAt)
    }

    /** Drobnosti v tráve pred cestou – rám záberu, kreslí sa až nad terénom. */
    fun DrawScope.drawFrontProps(
        fromX: Float,
        toX: Float,
        biome: BiomeType,
        day: Float,
        depth: DepthProjection,
        heightAt: (Float) -> Float
    ) {
        val first = MathX.floorDiv(fromX, FRONT_CELL)
        val last = MathX.floorDiv(toX, FRONT_CELL)
        for (cell in first..last) {
            if (MathX.hash01(cell, FRONT_SALT) > 0.55f) continue
            val wx = cell * FRONT_CELL + MathX.hash01(cell, 401) * FRONT_CELL
            val d = 0.02f + MathX.hash01(cell, 733) * 0.22f
            val fx = depth.atX(depth.frontX(wx), d)
            val fy = depth.atY(depth.frontY(heightAt(wx)), d)
            val s = depth.ppm * (1f - depth.perspectiveT(d))
            val tall = MathX.hash01(cell, 1237)
            if (tall < 0.25f) {
                stone(fx, fy, s * (0.10f + tall * 0.16f), day)
            } else {
                grassTuft(fx, fy, s * (0.22f + tall * 0.30f), biome, day)
            }
        }
    }

    private fun DrawScope.drawProp(
        wx: Float,
        d: Float,
        kind: Float,
        scaleRnd: Float,
        biome: BiomeType,
        day: Float,
        depth: DepthProjection,
        heightAt: (Float) -> Float
    ) {
        val px = depth.atX(depth.frontX(wx), d)
        val py = depth.atY(depth.frontY(heightAt(wx)), d)
        if (px < -120f || px > size.width + 120f) return
        val s = depth.ppm * (1f - depth.perspectiveT(d)) * (0.8f + scaleRnd * 0.55f)

        when (biome) {
            BiomeType.RURAL -> when {
                kind < 0.42f -> broadTree(px, py, s, day)
                kind < 0.62f -> pineTree(px, py, s, day)
                kind < 0.80f -> bush(px, py, s, day, Color(0xFF4E6B3C))
                kind < 0.92f -> fence(px, py, s, day)
                else -> stone(px, py, s * 0.32f, day)
            }
            BiomeType.INDUSTRIAL -> when {
                kind < 0.26f -> pineTree(px, py, s, day)
                kind < 0.46f -> deadTree(px, py, s, day)
                kind < 0.66f -> container(px, py, s, day)
                kind < 0.84f -> fence(px, py, s, day)
                else -> stone(px, py, s * 0.36f, day)
            }
            BiomeType.WASTELAND -> when {
                kind < 0.34f -> deadTree(px, py, s, day)
                kind < 0.56f -> bush(px, py, s * 0.8f, day, Color(0xFF6E6741))
                kind < 0.74f -> stone(px, py, s * 0.42f, day)
                kind < 0.88f -> wreck(px, py, s, day)
                else -> fence(px, py, s * 0.8f, day)
            }
        }
    }

    // --- Jednotlivé kulisy -------------------------------------------------

    /** Tieň na zemi – jediná vec, ktorá kulisu „posadí“ do terénu. */
    private fun DrawScope.groundShadow(x: Float, y: Float, s: Float, day: Float, spread: Float = 1f) {
        val alpha = 0.10f + 0.16f * day.coerceIn(0f, 1f)
        drawOval(
            Color(0xFF1A1408).copy(alpha = alpha),
            topLeft = Offset(x - s * 0.85f * spread, y - s * 0.11f),
            size = Size(s * 1.7f * spread, s * 0.24f)
        )
    }

    private fun DrawScope.broadTree(x: Float, y: Float, s: Float, day: Float) {
        val h = s * 2.6f
        groundShadow(x, y, s, day, 1.1f)
        drawLine(
            shade(Color(0xFF5B4433), day),
            Offset(x, y), Offset(x, y - h * 0.55f),
            strokeWidth = s * 0.17f, cap = StrokeCap.Round
        )
        val leaf = shade(Color(0xFF4F7A3C), day)
        drawCircle(leaf, s * 0.72f, Offset(x, y - h * 0.72f))
        drawCircle(leaf.copy(alpha = 0.95f), s * 0.52f, Offset(x - s * 0.55f, y - h * 0.52f))
        drawCircle(leaf.copy(alpha = 0.95f), s * 0.48f, Offset(x + s * 0.52f, y - h * 0.56f))
        drawCircle(shade(Color(0xFF639350), day), s * 0.36f, Offset(x + s * 0.16f, y - h * 0.88f))
    }

    private fun DrawScope.pineTree(x: Float, y: Float, s: Float, day: Float) {
        val h = s * 3.0f
        groundShadow(x, y, s, day, 0.9f)
        drawLine(
            shade(Color(0xFF4A3728), day),
            Offset(x, y), Offset(x, y - h * 0.28f),
            strokeWidth = s * 0.13f
        )
        val green = shade(Color(0xFF35603A), day)
        for (i in 0 until 3) {
            val t = i / 2f
            val cy = y - h * (0.30f + t * 0.52f)
            val w = s * (0.85f - t * 0.30f)
            propPath.reset()
            propPath.moveTo(x - w, cy)
            propPath.lineTo(x + w, cy)
            propPath.lineTo(x, cy - h * 0.30f)
            propPath.close()
            drawPath(propPath, if (i == 2) shade(Color(0xFF3F7245), day) else green)
        }
    }

    private fun DrawScope.deadTree(x: Float, y: Float, s: Float, day: Float) {
        groundShadow(x, y, s, day, 0.7f)
        val col = shade(Color(0xFF6B5B4A), day)
        val h = s * 2.2f
        drawLine(col, Offset(x, y), Offset(x - s * 0.08f, y - h), strokeWidth = s * 0.13f, cap = StrokeCap.Round)
        drawLine(col, Offset(x - s * 0.05f, y - h * 0.62f), Offset(x - s * 0.62f, y - h * 0.90f), strokeWidth = s * 0.08f, cap = StrokeCap.Round)
        drawLine(col, Offset(x - s * 0.06f, y - h * 0.75f), Offset(x + s * 0.55f, y - h * 0.98f), strokeWidth = s * 0.07f, cap = StrokeCap.Round)
    }

    private fun DrawScope.bush(x: Float, y: Float, s: Float, day: Float, base: Color) {
        groundShadow(x, y, s, day, 0.6f)
        val c = shade(base, day)
        drawCircle(c, s * 0.42f, Offset(x, y - s * 0.30f))
        drawCircle(c, s * 0.32f, Offset(x - s * 0.35f, y - s * 0.18f))
        drawCircle(c, s * 0.30f, Offset(x + s * 0.33f, y - s * 0.20f))
    }

    private fun DrawScope.grassTuft(x: Float, y: Float, s: Float, biome: BiomeType, day: Float) {
        val c = shade(
            when (biome) {
                BiomeType.RURAL -> Color(0xFF6F8B4A)
                BiomeType.INDUSTRIAL -> Color(0xFF6A7355)
                BiomeType.WASTELAND -> Color(0xFF8E8151)
            },
            day
        )
        for (i in -2..2) {
            val dx = i * s * 0.14f
            drawLine(
                c,
                Offset(x + dx, y),
                Offset(x + dx * 2.1f, y - s * (0.5f + 0.16f * (2 - kotlin.math.abs(i)))),
                strokeWidth = s * 0.10f,
                cap = StrokeCap.Round
            )
        }
    }

    private fun DrawScope.stone(x: Float, y: Float, s: Float, day: Float) {
        drawOval(
            shade(Color(0xFF7A7568), day),
            topLeft = Offset(x - s, y - s * 0.85f),
            size = Size(s * 2f, s * 1.0f)
        )
        drawOval(
            shade(Color(0xFF938D7E), day),
            topLeft = Offset(x - s * 0.7f, y - s * 0.85f),
            size = Size(s * 1.1f, s * 0.55f)
        )
    }

    private fun DrawScope.fence(x: Float, y: Float, s: Float, day: Float) {
        val c = shade(Color(0xFF7A6448), day)
        val h = s * 0.85f
        for (i in 0 until 4) {
            val px = x + i * s * 0.75f
            drawLine(c, Offset(px, y), Offset(px, y - h), strokeWidth = s * 0.09f)
        }
        drawLine(c, Offset(x, y - h * 0.75f), Offset(x + s * 2.25f, y - h * 0.75f), strokeWidth = s * 0.07f)
        drawLine(c, Offset(x, y - h * 0.35f), Offset(x + s * 2.25f, y - h * 0.35f), strokeWidth = s * 0.07f)
    }

    private fun DrawScope.container(x: Float, y: Float, s: Float, day: Float) {
        groundShadow(x, y, s, day, 1.5f)
        val w = s * 2.4f
        val h = s * 1.1f
        val body = shade(Color(0xFF7A5A3E), day)
        drawRect(body, topLeft = Offset(x - w * 0.5f, y - h), size = Size(w, h))
        drawRect(
            shade(Color(0xFF5E4630), day),
            topLeft = Offset(x - w * 0.5f, y - h),
            size = Size(w, h * 0.14f)
        )
        for (i in 1 until 5) {
            val px = x - w * 0.5f + w * i / 5f
            drawLine(shade(Color(0xFF6A4E36), day), Offset(px, y - h), Offset(px, y), strokeWidth = s * 0.05f)
        }
    }

    private fun DrawScope.wreck(x: Float, y: Float, s: Float, day: Float) {
        groundShadow(x, y, s, day, 1.2f)
        val body = shade(Color(0xFF6E4B3A), day)
        drawRect(body, topLeft = Offset(x - s * 0.9f, y - s * 0.55f), size = Size(s * 1.8f, s * 0.42f))
        drawRect(body, topLeft = Offset(x - s * 0.35f, y - s * 0.85f), size = Size(s * 0.85f, s * 0.34f))
        drawCircle(shade(Color(0xFF2E2A26), day), s * 0.17f, Offset(x - s * 0.55f, y - s * 0.10f))
        drawCircle(shade(Color(0xFF2E2A26), day), s * 0.17f, Offset(x + s * 0.55f, y - s * 0.10f))
    }

    /** Stĺpy elektrického vedenia s previsnutým drôtom – najsilnejší dojem rýchlosti. */
    private fun DrawScope.drawPowerLine(
        fromX: Float,
        toX: Float,
        day: Float,
        depth: DepthProjection,
        heightAt: (Float) -> Float
    ) {
        val d = GameConfig.ROAD_DEPTH + 0.55f
        val first = MathX.floorDiv(fromX, POLE_SPACING) - 1
        val last = MathX.floorDiv(toX, POLE_SPACING) + 1
        val poleCol = shade(Color(0xFF6A5540), day)
        val wireCol = shade(Color(0xFF3A3630), day).copy(alpha = 0.85f)

        for (i in first..last) {
            val wx = i * POLE_SPACING
            val px = depth.atX(depth.frontX(wx), d)
            val py = depth.atY(depth.frontY(heightAt(wx)), d)
            val s = depth.ppm * (1f - depth.perspectiveT(d))
            val topY = py - s * 3.4f

            // Drôt k ďalšiemu stĺpu (dva vodiče s previsom).
            val nx = (i + 1) * POLE_SPACING
            val npx = depth.atX(depth.frontX(nx), d)
            val npy = depth.atY(depth.frontY(heightAt(nx)), d) - s * 3.4f
            for (wire in 0 until 2) {
                val off = s * (0.22f + wire * 0.34f)
                var prevX = px
                var prevY = topY + off
                for (k in 1..6) {
                    val t = k / 6f
                    val sag = s * 0.55f * (t * (1f - t) * 4f)
                    val cx = px + (npx - px) * t
                    val cy = (topY + off) + (npy + off - (topY + off)) * t + sag
                    drawLine(wireCol, Offset(prevX, prevY), Offset(cx, cy), strokeWidth = (s * 0.035f).coerceAtLeast(1f))
                    prevX = cx
                    prevY = cy
                }
            }
            drawLine(poleCol, Offset(px, py), Offset(px, topY), strokeWidth = (s * 0.12f).coerceAtLeast(1.5f))
            drawLine(
                poleCol,
                Offset(px - s * 0.42f, topY + s * 0.30f),
                Offset(px + s * 0.42f, topY + s * 0.30f),
                strokeWidth = (s * 0.08f).coerceAtLeast(1f)
            )
        }
    }

    /** Míľniky: každých 100 m stĺpik, každých 500 m tabuľa so vzdialenosťou. */
    private fun DrawScope.drawMilestones(
        fromX: Float,
        toX: Float,
        day: Float,
        depth: DepthProjection,
        heightAt: (Float) -> Float
    ) {
        val d = GameConfig.ROAD_DEPTH + 0.12f
        val first = MathX.floorDiv(fromX, MILESTONE)
        val last = MathX.floorDiv(toX, MILESTONE)
        for (i in first..last) {
            if (i <= 0) continue
            val wx = i * MILESTONE
            val px = depth.atX(depth.frontX(wx), d)
            val py = depth.atY(depth.frontY(heightAt(wx)), d)
            val s = depth.ppm * (1f - depth.perspectiveT(d))
            val big = i % 5 == 0
            val h = s * (if (big) 1.5f else 0.75f)
            drawLine(
                shade(Color(0xFFD8D2C4), day),
                Offset(px, py), Offset(px, py - h),
                strokeWidth = (s * (if (big) 0.13f else 0.10f)).coerceAtLeast(1.5f)
            )
            drawLine(
                shade(Color(0xFFB85C38), day),
                Offset(px, py - h), Offset(px, py - h + s * 0.18f),
                strokeWidth = (s * 0.13f).coerceAtLeast(1.5f)
            )
            if (big) {
                val w = s * 1.05f
                drawRect(
                    shade(Color(0xFF2F4F3A), day),
                    topLeft = Offset(px - w * 0.5f, py - h - s * 0.62f),
                    size = Size(w, s * 0.62f)
                )
                drawRect(
                    shade(Color(0xFFDDD6C6), day),
                    topLeft = Offset(px - w * 0.42f, py - h - s * 0.46f),
                    size = Size(w * 0.84f, s * 0.10f)
                )
            }
        }
    }

    private fun densityFor(biome: BiomeType): Float = when (biome) {
        BiomeType.RURAL -> 0.72f
        BiomeType.INDUSTRIAL -> 0.58f
        BiomeType.WASTELAND -> 0.42f
    }

    private companion object {
        const val CELL = 5.5f
        const val FRONT_CELL = 2.6f
        const val POLE_SPACING = 26f
        const val MILESTONE = 100f
        const val BACK_SALT = 4523
        const val FRONT_SALT = 8171
    }
}

/** Nočné stmavenie kulís – jeden spoločný vzorec pre celú scénu. */
internal fun shade(color: Color, day: Float): Color =
    lerp(lerp(color, Color(0xFF141B2E), 0.78f), color, day.coerceIn(0f, 1f))
