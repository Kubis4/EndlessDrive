package sk.kubis.endlessdrive.ui.game

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import sk.kubis.endlessdrive.core.GameConfig
import sk.kubis.endlessdrive.core.MathX
import sk.kubis.endlessdrive.domain.model.ComponentSlot
import sk.kubis.endlessdrive.domain.model.SedanSpec
import sk.kubis.endlessdrive.game.DepthProjection
import sk.kubis.endlessdrive.game.car.Car
import kotlin.math.cos
import kotlin.math.sin

/**
 * Sedan: sprite vrstvy (montáž) + kolesá. Bez dielov = „holý“ podvozok.
 */
class CarArtist {
    private val bodyPath = Path()

    /**
     * @param bodyX / bodyY obrazovkový stred karosérie (z fyziky)
     * @param rearWheelX/Y, frontWheelX/Y obrazovkové stredy kolies (na ceste / vo vzduchu)
     * @param roadY povrch cesty pod autom (tieň / svetlá)
     */
    fun DrawScope.drawSedan(
        car: Car,
        bodyX: Float,
        bodyY: Float,
        angle: Float,
        ppm: Float,
        proj: DepthProjection,
        wheelSpinDeg: Float,
        braking: Boolean,
        layers: SedanLayers,
        roadY: Float,
        /** Uhly zvlášť – hnaná náprava sa pri preklze točí rýchlejšie. */
        rearSpinDeg: Float = wheelSpinDeg,
        frontSpinDeg: Float = wheelSpinDeg,
        /** 0..1 – ako plný je batoh; podľa toho stojí náklad na nosiči. */
        cargoFill: Float = 0f,
        rearWheelX: Float = bodyX,
        rearWheelY: Float = bodyY,
        frontWheelX: Float = bodyX,
        frontWheelY: Float = bodyY
    ) {
        val cosA = cos(angle)
        val sinA = sin(angle)
        val deg = -Math.toDegrees(angle.toDouble()).toFloat()

        val hasDoors = car.hasPart(ComponentSlot.DOORS)
        val hasHood = car.hasPart(ComponentSlot.HOOD)
        val hasWindows = car.hasPart(ComponentSlot.WINDOWS)
        val hasFrontBumper = car.hasPart(ComponentSlot.FRONT_BUMPER)
        val hasRearBumper = car.hasPart(ComponentSlot.REAR_BUMPER)
        val hasFrontTire = car.hasPart(ComponentSlot.TIRE_FRONT)
        val hasRearTire = car.hasPart(ComponentSlot.TIRE_REAR)

        val accent = SedanSpec.accentColor

        // Karoséria podľa fyziky; kolesá samostatne → viditeľné pruženie.
        val layout = spriteLayoutAtBody(layers, bodyX, bodyY, ppm)
        rotate(degrees = deg, pivot = Offset(bodyX, bodyY)) {
            drawSpriteAssembly(
                layers, layout,
                hasDoors, hasHood, hasWindows, hasFrontBumper, hasRearBumper
            )
        }
        // Nosič patrí na strechu – kreslí sa v rovine karosérie, teda spolu
        // s ňou aj rotuje.
        if (car.hasRoofRack) {
            rotate(degrees = deg, pivot = Offset(bodyX, bodyY)) {
                drawRoofRack(layout, cargoFill)
            }
        }
        val blur = 1 + (kotlin.math.abs(car.speed) / 6f).toInt().coerceAtMost(3)
        if (hasRearTire) {
            val tire = car.parts[ComponentSlot.TIRE_REAR]
            val r = layout.wheelR * car.wheelScale(ComponentSlot.TIRE_REAR)
            drawWheelScreen(
                rearWheelX, rearWheelY, rearSpinDeg, r,
                tire?.health ?: 0.5f, accent, blur, tire?.defId
            )
        }
        if (hasFrontTire) {
            val tire = car.parts[ComponentSlot.TIRE_FRONT]
            val r = layout.wheelR * car.wheelScale(ComponentSlot.TIRE_FRONT)
            drawWheelScreen(
                frontWheelX, frontWheelY, frontSpinDeg, r,
                tire?.health ?: 0.5f, accent, blur, tire?.defId
            )
        }
    }

    /**
     * Kužeľ svetlometov vychádza z nameranej pozície svetla v sprite a rotuje s autom.
     * Kreslí sa až po nočnom závoji, aby ho tma nezhasla.
     */
    fun DrawScope.drawHeadlightBeam(
        bodyX: Float,
        bodyY: Float,
        angle: Float,
        ppm: Float,
        proj: DepthProjection,
        layers: SedanLayers,
        strength: Float,
        braking: Boolean,
        roadY: Float
    ) {
        if (strength <= 0.02f) return
        val deg = -Math.toDegrees(angle.toDouble()).toFloat()
        // bodyX/Y = obrazovkový stred karosérie.
        val layout = spriteLayoutAtBody(layers, bodyX, bodyY, ppm)
        rotate(degrees = deg, pivot = Offset(bodyX, bodyY)) {
            val hx = layout.originX + layers.headlightFx * layout.drawW
            val hy = layout.originY + layers.headlightFy * layout.drawH
            val tx = layout.originX + layers.taillightFx * layout.drawW
            val ty = layout.originY + layers.taillightFy * layout.drawH
            val len = ppm * 12f

            bodyPath.reset()
            bodyPath.moveTo(hx, hy - ppm * 0.10f)
            bodyPath.lineTo(hx + len, roadY - ppm * 2.1f)
            bodyPath.lineTo(hx + len, roadY + ppm * 0.35f)
            bodyPath.lineTo(hx, hy + ppm * 0.14f)
            bodyPath.close()
            drawPath(
                bodyPath,
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        Color(0xFFFFF2C0).copy(alpha = 0.34f * strength),
                        Color(0xFFFFF2C0).copy(alpha = 0.10f * strength),
                        Color.Transparent
                    ),
                    startX = hx,
                    endX = hx + len
                )
            )
            drawOval(
                Color(0xFFFFF3C4).copy(alpha = 0.22f * strength),
                topLeft = Offset(hx, roadY - ppm * 0.45f),
                size = Size(len * 0.8f, ppm * 0.9f)
            )
            drawCircle(Color(0xFFFFF8DC).copy(alpha = 0.85f * strength), ppm * 0.14f, Offset(hx, hy))
            drawCircle(Color(0xFFFFEFA8).copy(alpha = 0.35f * strength), ppm * 0.34f, Offset(hx, hy))

            val tail = if (braking) Color(0xFFFF2A2A) else Color(0xFFD8402F)
            drawCircle(tail.copy(alpha = (if (braking) 0.95f else 0.6f) * strength), ppm * 0.11f, Offset(tx, ty))
            drawCircle(tail.copy(alpha = 0.22f * strength), ppm * 0.30f, Offset(tx, ty))
        }
    }

    /**
     * Strešný nosič: dve pozdĺžne lišty na nožičkách a náklad podľa toho,
     * koľko toho hráč naozaj vezie.
     */
    private fun DrawScope.drawRoofRack(layout: SpriteLayout, fill: Float) {
        val roofY = layout.originY + layout.drawH * ROOF_FY
        val x0 = layout.originX + layout.drawW * 0.30f
        val x1 = layout.originX + layout.drawW * 0.68f
        val barY = roofY - layout.drawH * 0.06f
        val metal = Color(0xFF3A3D42)
        val bar = (layout.drawH * 0.022f).coerceAtLeast(1.5f)

        // Nožičky.
        listOf(x0 + (x1 - x0) * 0.12f, x1 - (x1 - x0) * 0.12f).forEach { fx ->
            drawLine(metal, Offset(fx, roofY), Offset(fx, barY), strokeWidth = bar)
        }
        drawLine(metal, Offset(x0, barY), Offset(x1, barY), strokeWidth = bar)

        if (fill <= 0.02f) return
        // Náklad: bedne rastú s obsadenosťou batoha.
        val crates = (1 + (fill * 3f).toInt()).coerceAtMost(4)
        val slotW = (x1 - x0) / crates
        for (i in 0 until crates) {
            val h = layout.drawH * (0.05f + 0.035f * MathX.hash01(i, 91))
            val cx = x0 + slotW * i + slotW * 0.08f
            drawRect(
                if (i % 2 == 0) Color(0xFF6B5334) else Color(0xFF4E4A44),
                topLeft = Offset(cx, barY - h),
                size = Size(slotW * 0.84f, h)
            )
        }
    }

    data class SpriteLayout(
        val originX: Float,
        val originY: Float,
        val drawW: Float,
        val drawH: Float,
        val rearWx: Float,
        val rearWy: Float,
        val frontWx: Float,
        val frontWy: Float,
        val wheelR: Float
    ) {
        val exhaustX: Float get() = originX + drawW * 0.04f
        val exhaustY: Float get() = originY + drawH * 0.72f
        val centerY: Float get() = originY + drawH * BODY_CENTER_FY
    }

    fun layoutAtBody(
        layers: SedanLayers,
        bodyScreenX: Float,
        bodyScreenY: Float,
        ppm: Float
    ): SpriteLayout = spriteLayoutAtBody(layers, bodyScreenX, bodyScreenY, ppm)

    fun wheelRadiusPx(layers: SedanLayers, ppm: Float): Float {
        val scale = (layers.worldWidthM * ppm) / layers.imageWidth
        return layers.wheelRadiusFx * layers.imageWidth * scale
    }

    /** Karoséria: vizuálny stred na [bodyPy]. */
    private fun spriteLayoutAtBody(
        layers: SedanLayers,
        px: Float,
        bodyPy: Float,
        ppm: Float
    ): SpriteLayout {
        val scale = (layers.worldWidthM * ppm) / layers.imageWidth
        val drawW = layers.imageWidth * scale
        val drawH = layers.imageHeight * scale
        val wheelR = layers.wheelRadiusFx * drawW
        val originX = px - drawW * 0.50f
        val originY = bodyPy - BODY_CENTER_FY * drawH
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

    /**
     * Layout s kolesami na [contactY] – pre efekty viazané na povrch.
     */
    fun layoutOnContact(
        layers: SedanLayers,
        screenX: Float,
        contactY: Float,
        ppm: Float
    ): SpriteLayout {
        val scale = (layers.worldWidthM * ppm) / layers.imageWidth
        val drawW = layers.imageWidth * scale
        val drawH = layers.imageHeight * scale
        val wheelR = layers.wheelRadiusFx * drawW
        val originX = screenX - drawW * 0.50f
        val originY = contactY - layers.wheelCenterFy * drawH - wheelR
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

    /** Rovnaké koleso ako v hre – aj pre panel CAR. */
    fun DrawScope.drawTireScreen(
        cx: Float,
        cy: Float,
        spinDeg: Float,
        r: Float,
        tireHealth: Float,
        accent: Color,
        blurSteps: Int = 1,
        tireId: String? = null
    ) = drawWheelScreen(cx, cy, spinDeg, r, tireHealth, accent, blurSteps, tireId)

    private fun DrawScope.drawWheelScreen(
        cx: Float,
        cy: Float,
        spinDeg: Float,
        r: Float,
        tireHealth: Float,
        accent: Color,
        blurSteps: Int = 1,
        tireId: String? = null
    ) {
        val rr = r * (0.90f + 0.10f * tireHealth)
        val offroad = tireId == "tire_offroad"
        val sport = tireId == "tire_sport"
        val poor = tireId == "tire_poor" || tireId == "tire_bald"
        val standard = tireId == "tire_std" || (!offroad && !sport && !poor && tireId != null)
        val sidewall = when {
            offroad -> Color(0xFF121416)
            poor -> Color(0xFF2A2E33)
            sport -> Color(0xFF171A1E)
            else -> Color(0xFF1B1E22)
        }
        val rubberOuter = when {
            offroad -> rr * 1.02f
            sport -> rr * 0.98f
            else -> rr
        }
        drawCircle(sidewall, rubberOuter, Offset(cx, cy))
        if (offroad) {
            // Hrubý dezén – zuby na obvode.
            rotate(degrees = spinDeg, pivot = Offset(cx, cy)) {
                for (i in 0 until 10) {
                    val a = (i * 36f) * (Math.PI / 180.0).toFloat()
                    drawCircle(
                        Color(0xFF0E1012),
                        rr * 0.10f,
                        Offset(cx + cos(a) * rr * 0.92f, cy + sin(a) * rr * 0.92f)
                    )
                }
            }
        } else if (standard || sport) {
            // Jemné drážky dezénu.
            rotate(degrees = spinDeg, pivot = Offset(cx, cy)) {
                val grooves = if (sport) 12 else 8
                for (i in 0 until grooves) {
                    val a = (i * (360f / grooves)) * (Math.PI / 180.0).toFloat()
                    drawLine(
                        Color(0xFF0C0E10).copy(alpha = if (sport) 0.85f else 0.55f),
                        Offset(cx + cos(a) * rr * 0.72f, cy + sin(a) * rr * 0.72f),
                        Offset(cx + cos(a) * rr * 0.96f, cy + sin(a) * rr * 0.96f),
                        strokeWidth = rr * (if (sport) 0.045f else 0.055f)
                    )
                }
            }
        }
        val rimOuter = when {
            sport -> rr * 0.58f
            offroad -> rr * 0.60f
            poor -> rr * 0.70f
            else -> rr * 0.66f
        }
        drawCircle(Color(0xFF32373D), rimOuter, Offset(cx, cy))
        if (sport) {
            // Športový disk – tenší kruh.
            drawCircle(Color(0xFF1E242A), rr * 0.48f, Offset(cx, cy))
        }
        drawCircle(accent.copy(alpha = 0.85f), rr * (if (sport) 0.26f else 0.30f), Offset(cx, cy))
        drawCircle(Color(0xFFCFD8DC), rr * 0.12f, Offset(cx, cy))
        val spokes = when {
            sport -> 6
            offroad -> 5
            poor -> 4
            else -> 5
        }
        val blur = blurSteps.coerceIn(1, 4)
        val alpha = 1f / blur
        for (b in 0 until blur) {
            rotate(degrees = spinDeg - b * 9f, pivot = Offset(cx, cy)) {
                for (i in 0 until spokes) {
                    val a = (i * (360f / spokes)) * (Math.PI / 180.0).toFloat()
                    drawLine(
                        Color(0xFFB0BEC5).copy(alpha = alpha * if (poor) 0.55f else 1f),
                        Offset(cx + cos(a) * rr * 0.16f, cy + sin(a) * rr * 0.16f),
                        Offset(cx + cos(a) * rr * 0.55f, cy + sin(a) * rr * 0.55f),
                        strokeWidth = rr * (if (sport) 0.09f else 0.12f)
                    )
                }
            }
        }
    }

    companion object {
        /** Relatívny stred karosérie v sprite (pivot / fyzický stred). */
        private const val BODY_CENTER_FY = 0.52f
        /** Kde v sprite začína strecha (podiel výšky obrázka). */
        private const val ROOF_FY = 0.22f
    }
}
