package sk.kubis.endlessdrive.ui.game

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import sk.kubis.endlessdrive.core.GameConfig
import sk.kubis.endlessdrive.domain.model.ComponentSlot
import sk.kubis.endlessdrive.domain.model.SedanSpec
import sk.kubis.endlessdrive.game.DepthProjection
import sk.kubis.endlessdrive.game.car.Car
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Sedan: sprite vrstvy (montáž) + kolesá. Bez dielov = „holý“ podvozok.
 */
class CarArtist {
    private val bodyPath = Path()
    private val sidePath = Path()
    private val polyX = FloatArray(MAX_POLY)
    private val polyY = FloatArray(MAX_POLY)
    private val farX = FloatArray(MAX_POLY)
    private val farY = FloatArray(MAX_POLY)

    fun DrawScope.drawSedan(
        car: Car,
        bodyX: Float,
        bodyY: Float,
        angle: Float,
        ppm: Float,
        proj: DepthProjection,
        wheelSpinDeg: Float,
        braking: Boolean,
        layers: SedanLayers?
    ) {
        val cosA = cos(angle)
        val sinA = sin(angle)
        val deg = -Math.toDegrees(angle.toDouble()).toFloat()

        val near = GameConfig.CAR_NEAR_DEPTH
        val mid = near + GameConfig.CAR_DEPTH * 0.5f
        val wheelDepth = near - WHEEL_STICK_OUT

        val hasDoors = car.hasPart(ComponentSlot.DOORS)
        val hasHood = car.hasPart(ComponentSlot.HOOD)
        val hasWindows = car.hasPart(ComponentSlot.WINDOWS)
        val hasFrontBumper = car.hasPart(ComponentSlot.FRONT_BUMPER)
        val hasRearBumper = car.hasPart(ComponentSlot.REAR_BUMPER)
        val hasTires = car.hasPart(ComponentSlot.TIRES)

        val accent = SedanSpec.accentColor
        val px = proj.linearX(bodyX, near)
        val py = proj.linearY(bodyY, near)

        if (layers != null) {
            rotate(degrees = deg, pivot = Offset(px, py)) {
                val layout = spriteLayout(layers, px, py, ppm)
                drawSpriteAssembly(
                    layers, layout,
                    hasDoors, hasHood, hasWindows, hasFrontBumper, hasRearBumper
                )
                if (hasTires) {
                    val tireHealth = car.parts[ComponentSlot.TIRES]?.health ?: 0.5f
                    drawWheelScreen(layout.rearWx, layout.rearWy, wheelSpinDeg, layout.wheelR, tireHealth, accent)
                    drawWheelScreen(layout.frontWx, layout.frontWy, wheelSpinDeg, layout.wheelR, tireHealth, accent)
                }
            }
        } else {
            val rearLocalX = -SedanSpec.wheelOffsetX
            val frontLocalX = SedanSpec.wheelOffsetX
            val wheelLocalY = GameConfig.WHEEL_OFFSET_Y

            fun worldFromLocal(lx: Float, ly: Float): Pair<Float, Float> {
                val wx = bodyX + (lx * cosA - ly * sinA) * ppm
                val wy = bodyY - (lx * sinA + ly * cosA) * ppm
                return wx to wy
            }

            val (rearWx, rearWy) = worldFromLocal(rearLocalX, wheelLocalY)
            val (frontWx, frontWy) = worldFromLocal(frontLocalX, wheelLocalY)

            drawArm(bodyX, bodyY, cosA, sinA, -SedanSpec.wheelOffsetX * 0.25f, rearWx, rearWy, ppm, proj, mid)
            drawArm(bodyX, bodyY, cosA, sinA, SedanSpec.wheelOffsetX * 0.25f, frontWx, frontWy, ppm, proj, mid)

            val bodyColor = if (hasDoors && hasHood) SedanSpec.bodyColor else SedanSpec.rustColor
            val far = near + GameConfig.CAR_DEPTH
            drawExtruded(SedanSpec.chassisPoly, bodyX, bodyY, cosA, sinA, ppm, proj, near, far, bodyColor, accent)
            drawExtruded(
                SedanSpec.cabinPoly, bodyX, bodyY, cosA, sinA, ppm, proj,
                near, far, bodyColor.copy(alpha = if (hasDoors) 1f else 0.85f), accent
            )
            if (hasHood) {
                drawExtruded(SedanSpec.hoodPoly, bodyX, bodyY, cosA, sinA, ppm, proj, near, far, bodyColor, accent)
            } else {
                drawExtruded(
                    SedanSpec.hoodPoly, bodyX, bodyY, cosA, sinA, ppm, proj,
                    near, far, Color(0xFF1A1510), Color(0xFF3E2723)
                )
            }
            if (hasDoors) {
                drawExtruded(SedanSpec.doorPoly, bodyX, bodyY, cosA, sinA, ppm, proj, near, far, bodyColor, accent)
            }
            rotate(degrees = deg, pivot = Offset(px, py)) {
                drawBodyDetails(px, py, ppm, hasFrontBumper, hasRearBumper, braking, accent, car.engineRunning)
                if (hasWindows) drawGlass(px, py, ppm)
                else {
                    buildCabinPath(SedanSpec.cabinPoly, px, py, ppm, 1f)
                    drawPath(sidePath, accent.copy(alpha = 0.7f), style = Stroke(width = 0.07f * ppm))
                }
                drawDriver(px, py, ppm, accent)
            }
            if (hasTires) {
                val tireHealth = car.parts[ComponentSlot.TIRES]?.health ?: 0.5f
                drawWheel(rearWx, rearWy, wheelSpinDeg, ppm, proj, wheelDepth, tireHealth, accent)
                drawWheel(frontWx, frontWy, wheelSpinDeg, ppm, proj, wheelDepth, tireHealth, accent)
            }
        }
    }

    private data class SpriteLayout(
        val originX: Float,
        val originY: Float,
        val drawW: Float,
        val drawH: Float,
        val rearWx: Float,
        val rearWy: Float,
        val frontWx: Float,
        val frontWy: Float,
        val wheelR: Float
    )

    private fun spriteLayout(layers: SedanLayers, px: Float, py: Float, ppm: Float): SpriteLayout {
        val scale = (layers.worldWidthM * ppm) / layers.imageWidth
        val drawW = layers.imageWidth * scale
        val drawH = layers.imageHeight * scale
        val wheelR = layers.wheelRadiusFx * drawW
        // car.y je stred nad cestou → kolesá posadiť vizuálne do pásu cesty (pod grestom trávy).
        val roadPy = py + (GameConfig.CAR_RIDE_HEIGHT + 0.38f) * ppm
        val originX = px - drawW * 0.50f
        val originY = roadPy - layers.wheelCenterFy * drawH - wheelR
        return SpriteLayout(
            originX = originX,
            originY = originY,
            drawW = drawW,
            drawH = drawH,
            rearWx = originX + layers.rearWheelFx * drawW,
            rearWy = originY + layers.wheelCenterFy * drawH,
            frontWx = originX + layers.frontWheelFx * drawW,
            frontWy = originY + layers.wheelCenterFy * drawH,
            wheelR = wheelR
        )
    }

    private fun DrawScope.drawSpriteAssembly(
        layers: SedanLayers,
        layout: SpriteLayout,
        hasDoors: Boolean,
        hasHood: Boolean,
        hasWindows: Boolean,
        hasFront: Boolean,
        hasRear: Boolean
    ) {
        val originX = layout.originX
        val originY = layout.originY
        val drawW = layout.drawW
        val drawH = layout.drawH

        fun blitFull(img: ImageBitmap, alpha: Float = 1f) {
            drawImage(
                image = img,
                dstOffset = androidx.compose.ui.unit.IntOffset(originX.toInt(), originY.toInt()),
                dstSize = androidx.compose.ui.unit.IntSize(drawW.toInt().coerceAtLeast(1), drawH.toInt().coerceAtLeast(1)),
                alpha = alpha
            )
        }

        fun blitPart(part: SedanLayers.Part) {
            val dx = originX + part.fx * drawW
            val dy = originY + part.fy * drawH
            val dw = (part.fw * drawW).toInt().coerceAtLeast(1)
            val dh = (part.fh * drawH).toInt().coerceAtLeast(1)
            drawImage(
                image = part.image,
                dstOffset = androidx.compose.ui.unit.IntOffset(dx.toInt(), dy.toInt()),
                dstSize = androidx.compose.ui.unit.IntSize(dw, dh)
            )
        }

        // Celé auto vždy viditeľné; namontované diely jemne zvýrazníme.
        blitFull(layers.stripped, 1f)
        if (hasRear) blitPart(layers.rearBumper)
        if (hasHood) blitPart(layers.hood)
        if (hasDoors) blitPart(layers.doors)
        if (hasWindows) blitPart(layers.windows)
        if (hasFront) blitPart(layers.frontBumper)
    }

    private fun DrawScope.drawWheelScreen(
        cx: Float,
        cy: Float,
        spinDeg: Float,
        r: Float,
        tireHealth: Float,
        accent: Color
    ) {
        val rr = r * (0.85f + 0.15f * tireHealth)
        drawCircle(Color(0xFF15171A), rr * 0.98f, Offset(cx + rr * 0.12f, cy + rr * 0.04f))
        drawCircle(Color(0xFF26292E), rr, Offset(cx, cy))
        drawCircle(Color(0xFF3C4247), rr * 0.62f, Offset(cx, cy))
        drawCircle(accent, rr * 0.34f, Offset(cx, cy))
        drawCircle(Color(0xFFCFD8DC), rr * 0.14f, Offset(cx, cy))
        rotate(degrees = spinDeg, pivot = Offset(cx, cy)) {
            for (i in 0 until 5) {
                val a = (i * 72f) * (Math.PI / 180.0).toFloat()
                drawLine(
                    Color(0xFFB0BEC5),
                    Offset(cx + cos(a) * rr * 0.16f, cy + sin(a) * rr * 0.16f),
                    Offset(cx + cos(a) * rr * 0.55f, cy + sin(a) * rr * 0.55f),
                    strokeWidth = rr * 0.12f
                )
            }
        }
    }

    private fun DrawScope.drawBodyDetails(
        px: Float,
        py: Float,
        ppm: Float,
        hasFront: Boolean,
        hasRear: Boolean,
        braking: Boolean,
        accent: Color,
        engineOn: Boolean
    ) {
        fun lx(m: Float) = px + m * ppm
        fun ly(m: Float) = py - m * ppm
        val nose = SedanSpec.noseX
        val tail = SedanSpec.tailX
        val floor = SedanSpec.floorY
        val lightY = SedanSpec.deckY * 0.48f

        drawLine(
            accent.copy(alpha = 0.75f),
            Offset(lx(tail + 0.36f), ly(floor + 0.07f)),
            Offset(lx(nose - 0.34f), ly(floor + 0.07f)),
            strokeWidth = 0.10f * ppm,
            cap = StrokeCap.Round
        )

        if (hasFront || engineOn) {
            drawRoundRect(
                Color(0xFFFFF3B0).copy(alpha = if (engineOn) 0.95f else 0.55f),
                topLeft = Offset(lx(nose - 0.29f), ly(lightY + 0.07f)),
                size = Size(0.19f * ppm, 0.14f * ppm),
                cornerRadius = CornerRadius(0.05f * ppm)
            )
        }
        if (hasRear) {
            val tailLight = if (braking) Color(0xFFFF1744) else Color(0xFFE04B3C)
            drawRoundRect(
                tailLight,
                topLeft = Offset(lx(tail + 0.05f), ly(lightY + 0.07f)),
                size = Size(0.13f * ppm, 0.14f * ppm),
                cornerRadius = CornerRadius(0.04f * ppm)
            )
        }
        drawLine(
            Color(0xFF9AA5AD),
            Offset(lx(tail + 0.07f), ly(floor + 0.13f)),
            Offset(lx(tail - 0.16f), ly(floor + 0.13f)),
            strokeWidth = 0.10f * ppm,
            cap = StrokeCap.Round
        )
    }

    private fun buildCabinPath(cabin: FloatArray, px: Float, py: Float, ppm: Float, shrink: Float) {
        val n = cabin.size / 2
        var cx = 0f
        var cy = 0f
        for (i in 0 until n) {
            cx += cabin[i * 2]
            cy += cabin[i * 2 + 1]
        }
        cx /= n
        cy /= n
        sidePath.reset()
        for (i in 0 until n) {
            val lx = cx + (cabin[i * 2] - cx) * shrink
            val ly = cy + (cabin[i * 2 + 1] - cy) * shrink
            val x = px + lx * ppm
            val y = py - ly * ppm
            if (i == 0) sidePath.moveTo(x, y) else sidePath.lineTo(x, y)
        }
        sidePath.close()
    }

    private fun DrawScope.drawGlass(px: Float, py: Float, ppm: Float) {
        buildCabinPath(SedanSpec.cabinPoly, px, py, ppm, 0.82f)
        drawPath(sidePath, Color(0xFF8FD2FF).copy(alpha = 0.38f))
        drawPath(sidePath, Color(0xFFCDE9FF).copy(alpha = 0.55f), style = Stroke(width = 0.04f * ppm))
    }

    private fun DrawScope.drawDriver(px: Float, py: Float, ppm: Float, accent: Color) {
        val hr = GameConfig.HEAD_RADIUS * ppm
        val neckX = px + SedanSpec.headX * ppm
        val neckY = py - (SedanSpec.headY - GameConfig.HEAD_RADIUS * 1.6f) * ppm
        val headX = px + SedanSpec.headX * ppm
        val headY = py - SedanSpec.headY * ppm
        drawLine(Color(0xFFD9A472), Offset(neckX, neckY), Offset(headX, headY), hr * 0.55f)
        drawCircle(Color(0xFFF3C08B), hr, Offset(headX, headY))
        drawArc(
            accent,
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = true,
            topLeft = Offset(headX - hr * 1.12f, headY - hr * 1.12f),
            size = Size(hr * 2.24f, hr * 2.24f)
        )
        drawLine(
            Color(0xFF2B3A45),
            Offset(headX + hr * 0.08f, headY - hr * 0.10f),
            Offset(headX + hr * 0.78f, headY - hr * 0.05f),
            strokeWidth = hr * 0.30f,
            cap = StrokeCap.Round
        )
    }

    private fun DrawScope.drawArm(
        bodyX: Float, bodyY: Float, cosA: Float, sinA: Float, localX: Float,
        wheelX: Float, wheelY: Float, ppm: Float, proj: DepthProjection, depth: Float
    ) {
        val ax0 = bodyX + (localX * cosA - GameConfig.WHEEL_OFFSET_Y * sinA) * ppm
        val ay0 = bodyY - (localX * sinA + GameConfig.WHEEL_OFFSET_Y * cosA) * ppm
        drawLine(
            Color(0xFF37474F),
            Offset(proj.linearX(ax0, depth), proj.linearY(ay0, depth)),
            Offset(proj.linearX(wheelX, depth), proj.linearY(wheelY, depth)),
            strokeWidth = 0.11f * ppm
        )
    }

    private fun DrawScope.drawWheel(
        frontX: Float,
        frontY: Float,
        spinDeg: Float,
        ppm: Float,
        proj: DepthProjection,
        faceDepth: Float,
        tireHealth: Float,
        accent: Color
    ) {
        val r = SedanSpec.wheelRadius * ppm * (0.85f + 0.15f * tireHealth)
        val px = proj.linearX(frontX, faceDepth)
        val py = proj.linearY(frontY, faceDepth)
        val backX = proj.linearX(frontX, faceDepth + GameConfig.WHEEL_DEPTH)
        val backY = proj.linearY(frontY, faceDepth + GameConfig.WHEEL_DEPTH)

        drawCircle(Color(0xFF15171A), r, Offset(backX, backY))
        drawLine(Color(0xFF1C1F23), Offset(backX, backY), Offset(px, py), r * 2f, StrokeCap.Round)
        drawCircle(Color(0xFF26292E), r, Offset(px, py))
        drawCircle(Color(0xFF3C4247), r * 0.62f, Offset(px, py))
        drawCircle(accent, r * 0.34f, Offset(px, py))
        drawCircle(Color(0xFFCFD8DC), r * 0.14f, Offset(px, py))

        rotate(degrees = spinDeg, pivot = Offset(px, py)) {
            for (i in 0 until 5) {
                val a = (i * 72f) * (Math.PI / 180.0).toFloat()
                drawLine(
                    Color(0xFFB0BEC5),
                    Offset(px + cos(a) * r * 0.16f, py + sin(a) * r * 0.16f),
                    Offset(px + cos(a) * r * 0.55f, py + sin(a) * r * 0.55f),
                    strokeWidth = r * 0.12f
                )
            }
        }
    }

    fun DrawScope.drawExtruded(
        poly: FloatArray,
        pivotX: Float, pivotY: Float,
        cosA: Float, sinA: Float,
        ppm: Float,
        proj: DepthProjection,
        nearDepth: Float,
        farDepth: Float,
        base: Color,
        outline: Color?
    ) {
        val n = poly.size / 2
        if (n < 3 || n > MAX_POLY) return
        for (i in 0 until n) {
            val lx = poly[i * 2]
            val ly = poly[i * 2 + 1]
            val vx0 = pivotX + (lx * cosA - ly * sinA) * ppm
            val vy0 = pivotY - (lx * sinA + ly * cosA) * ppm
            polyX[i] = proj.linearX(vx0, nearDepth)
            polyY[i] = proj.linearY(vy0, nearDepth)
            farX[i] = proj.linearX(vx0, farDepth)
            farY[i] = proj.linearY(vy0, farDepth)
        }

        bodyPath.reset()
        bodyPath.moveTo(farX[0], farY[0])
        for (i in 1 until n) bodyPath.lineTo(farX[i], farY[i])
        bodyPath.close()
        drawPath(bodyPath, base.shade(0.58f))

        for (i in 0 until n) {
            val j = if (i + 1 == n) 0 else i + 1
            val sdx = polyX[j] - polyX[i]
            val sdy = polyY[j] - polyY[i]
            val len = hypot(sdx, sdy)
            if (len < 0.01f) continue
            val nx = -sdy / len
            val ny = -sdx / len
            val lambert = (nx * 0.30f + ny * 0.95f).coerceAtLeast(0f)
            sidePath.reset()
            sidePath.moveTo(polyX[i], polyY[i])
            sidePath.lineTo(polyX[j], polyY[j])
            sidePath.lineTo(farX[j], farY[j])
            sidePath.lineTo(farX[i], farY[i])
            sidePath.close()
            drawPath(sidePath, base.shade(0.66f + 0.34f * lambert))
        }

        bodyPath.reset()
        bodyPath.moveTo(polyX[0], polyY[0])
        for (i in 1 until n) bodyPath.lineTo(polyX[i], polyY[i])
        bodyPath.close()
        drawPath(bodyPath, base)
        if (outline != null) {
            drawPath(bodyPath, outline.copy(alpha = 0.55f), style = Stroke(width = 3f, join = StrokeJoin.Round))
        }
    }

    companion object {
        private const val MAX_POLY = 16
        private const val WHEEL_STICK_OUT = 0.14f
    }
}

private fun Color.shade(factor: Float): Color = Color(
    red = (red * factor).coerceIn(0f, 1f),
    green = (green * factor).coerceIn(0f, 1f),
    blue = (blue * factor).coerceIn(0f, 1f),
    alpha = alpha
)
