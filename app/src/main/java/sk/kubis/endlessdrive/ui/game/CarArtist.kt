package sk.kubis.endlessdrive.ui.game

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import sk.kubis.endlessdrive.core.GameConfig
import sk.kubis.endlessdrive.core.MathX
import sk.kubis.endlessdrive.domain.model.ComponentSlot
import sk.kubis.endlessdrive.domain.model.SedanSpec
import sk.kubis.endlessdrive.domain.model.VehiclePaint
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

        val hasFrontTire = car.hasPart(ComponentSlot.TIRE_FRONT)
        val hasRearTire = car.hasPart(ComponentSlot.TIRE_REAR)

        val accent = SedanSpec.accentColor

        // Karoséria podľa fyziky; kolesá samostatne → viditeľné pruženie.
        val layout = spriteLayoutAtBody(layers, bodyX, bodyY, ppm)
        rotate(degrees = deg, pivot = Offset(bodyX, bodyY)) {
            drawSpriteAssembly(car, layers, layout)
        }
        // Nosič patrí na strechu – kreslí sa v rovine karosérie, teda spolu
        // s ňou aj rotuje.
        if (car.hasRoofRack) {
            rotate(degrees = deg, pivot = Offset(bodyX, bodyY)) {
                drawRoofLoad(layers, layout, cargoFill)
            }
        }
        val blur = 1 + (kotlin.math.abs(car.speed) / 6f).toInt().coerceAtMost(3)
        if (hasRearTire) {
            val tire = car.parts[ComponentSlot.TIRE_REAR]
            val r = layout.wheelR * car.wheelScale(ComponentSlot.TIRE_REAR)
            drawWheelScreen(
                rearWheelX, rearWheelY, rearSpinDeg, r,
                tire?.health ?: 0.5f, accent, blur, tire?.defId,
                layers.wheelImage(tire?.defId)
            )
            if (car.hasChains) {
                drawWheelScreen(
                    rearWheelX, rearWheelY, rearSpinDeg, r,
                    tire?.health ?: 0.5f, accent, blur, tire?.defId,
                    layers.chainOverlay()
                )
            }
        }
        if (hasFrontTire) {
            val tire = car.parts[ComponentSlot.TIRE_FRONT]
            val r = layout.wheelR * car.wheelScale(ComponentSlot.TIRE_FRONT)
            drawWheelScreen(
                frontWheelX, frontWheelY, frontSpinDeg, r,
                tire?.health ?: 0.5f, accent, blur, tire?.defId,
                layers.wheelImage(tire?.defId)
            )
            if (car.hasChains) {
                drawWheelScreen(
                    frontWheelX, frontWheelY, frontSpinDeg, r,
                    tire?.health ?: 0.5f, accent, blur, tire?.defId,
                    layers.chainOverlay()
                )
            }
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
        highBeam: Boolean,
        braking: Boolean,
        roadY: Float
    ) {
        if (strength <= 0.02f) return
        val deg = -Math.toDegrees(angle.toDouble()).toFloat()
        // bodyX/Y = obrazovkový stred karosérie.
        val layout = spriteLayoutAtBody(layers, bodyX, bodyY, ppm)
        rotate(degrees = deg, pivot = Offset(bodyX, bodyY)) {
            val hx = layout.originX + layers.headlightFx * layout.drawW + ppm * 0.08f
            val hy = layout.originY + layers.headlightFy * layout.drawH
            val tx = layout.originX + layers.taillightFx * layout.drawW
            val ty = layout.originY + layers.taillightFy * layout.drawH
            // Dve vrstvy lúča: mäkký široký okraj a jasné stretávacie svetlo.
            // Kužeľ smeruje mierne k vozovke, nie vysoko do oblohy.
            fun cone(lenM: Float, upper: Float, lower: Float, alpha: Float) {
                val len = ppm * lenM
                bodyPath.reset()
                bodyPath.moveTo(hx, hy - ppm * 0.07f)
                bodyPath.lineTo(hx + len, hy + ppm * upper)
                bodyPath.lineTo(hx + len, hy + ppm * lower)
                bodyPath.lineTo(hx, hy + ppm * 0.12f)
                bodyPath.close()
                drawPath(
                    bodyPath,
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            Color(0xFFFFF4CC).copy(alpha = alpha * strength),
                            Color(0xFFFFEDB0).copy(alpha = alpha * 0.58f * strength),
                            Color.Transparent
                        ),
                        startX = hx,
                        endX = hx + len
                    )
                )
            }
            if (highBeam) {
                // Diaľkové: dlhší a užší kužeľ, ktorý svieti vyššie do diaľky.
                cone(lenM = 39f, upper = -0.72f, lower = 1.02f, alpha = 0.27f)
                cone(lenM = 31f, upper = -0.32f, lower = 0.50f, alpha = 0.58f)
            } else {
                // Stretávacie: kratší kužeľ s ostrým spádom k vozovke.
                cone(lenM = 21f, upper = -0.08f, lower = 1.22f, alpha = 0.21f)
                cone(lenM = 14f, upper = 0.12f, lower = 0.67f, alpha = 0.46f)
            }

            // Mäkký hotspot na asfalte drží svetlo pri povrchu aj na kopci.
            val len = ppm * if (highBeam) 32f else 18f
            drawOval(
                Color(0xFFFFF3C4).copy(alpha = (if (highBeam) 0.26f else 0.20f) * strength),
                topLeft = Offset(hx + ppm * 1.5f, roadY - ppm * 0.38f),
                size = Size(len * 0.88f, ppm * if (highBeam) 0.72f else 0.90f)
            )
            drawCircle(Color(0xFFFFF8DC).copy(alpha = 0.85f * strength), ppm * 0.14f, Offset(hx, hy))
            drawCircle(Color(0xFFFFEFA8).copy(alpha = 0.35f * strength), ppm * 0.34f, Offset(hx, hy))

            val tail = if (braking) Color(0xFFFF2A2A) else Color(0xFFD8402F)
            drawCircle(tail.copy(alpha = (if (braking) 0.95f else 0.6f) * strength), ppm * 0.11f, Offset(tx, ty))
            drawCircle(tail.copy(alpha = 0.22f * strength), ppm * 0.30f, Offset(tx, ty))
        }
    }

    /**
     * Náklad na strešnom nosiči. Samotný nosič je kresba ako ostatné diely
     * karosérie – tu sa dokresľuje len to, čo hráč naozaj vezie.
     *
     * Bedne rastú s obsadenosťou batoha, takže na streche je vidieť, či ide
     * naprázdno alebo naložený.
     */
    private fun DrawScope.drawRoofLoad(layers: SedanLayers, layout: SpriteLayout, fill: Float) {
        if (fill <= 0.02f) return
        val spec = BodyPartCatalog.specs[BodyPart.ROOF_RACK] ?: return
        val rack = layers.partImage(BodyPart.ROOF_RACK, null) ?: return
        val k = layout.drawW / layers.imageWidth
        val x0 = layout.originX + spec.fx * layout.drawW
        val x1 = x0 + rack.fixed.width * k
        // Bedne stoja na lište, nie na jej spodnej hrane s nožičkami.
        val barY = layout.originY + spec.fy * layout.drawH + rack.fixed.height * k * 0.35f

        val crates = (1 + (fill * 3f).toInt()).coerceAtMost(4)
        val slotW = (x1 - x0) / crates
        for (i in 0 until crates) {
            val h = layout.drawH * (0.05f + 0.035f * MathX.hash01(i, 91))
            val cx = x0 + slotW * i + slotW * 0.08f
            val cw = slotW * 0.84f
            drawRect(
                if (i % 2 == 0) Color(0xFF6B5334) else Color(0xFF4E4A44),
                topLeft = Offset(cx, barY - h),
                size = Size(cw, h)
            )
            drawRect(
                Color(0xFF2A2621).copy(alpha = 0.55f),
                topLeft = Offset(cx + cw * 0.38f, barY - h),
                size = Size(cw * 0.10f, h)
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

    /** Rovnaká poskladaná karoséria ako v jazde, prispôsobená panelu CAR. */
    fun DrawScope.drawBodyPreview(
        car: Car,
        layers: SedanLayers,
        originX: Float,
        originY: Float,
        drawW: Float,
        drawH: Float
    ) {
        drawSpriteAssembly(
            car,
            layers,
            SpriteLayout(originX, originY, drawW, drawH, 0f, 0f, 0f, 0f, layers.wheelRadiusFx * drawW)
        )
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

    /**
     * Auto sa skladá: základ karosérie s interiérom a na ňom to, čo je
     * namontované.
     *
     * Predlohy sú v rovnakej mierke ako základ, takže sa nikam neťahajú –
     * len sa posunú na svoje miesto a zmenšia rovnakým pomerom ako základ.
     * Vďaka tomu si zachovajú proporcie a variant sa nakreslí presne tam,
     * kde základný diel. Kresba sa vyberá podľa namontovaného kusu, nie
     * podľa slotu.
     */
    private fun DrawScope.drawSpriteAssembly(
        car: Car,
        layers: SedanLayers,
        layout: SpriteLayout
    ) {
        val originX = layout.originX
        val originY = layout.originY
        val drawW = layout.drawW
        val drawH = layout.drawH
        // Rovnaký pomer ako pri základe – diel si drží svoju veľkosť voči autu.
        val k = drawW / layers.imageWidth

        fun blit(part: BodyPart, defId: String?, health: Float, paintIndex: Int) {
            val spec = BodyPartCatalog.specs.getValue(part)
            val img = layers.partImage(part, defId) ?: return
            val painted = part == BodyPart.TRUNK || part == BodyPart.HOOD ||
                part == BodyPart.BUMPER_REAR || part == BodyPart.BUMPER_FRONT ||
                part == BodyPart.DOOR_REAR || part == BodyPart.DOOR_FRONT
            val paintColor = if (painted) {
                partPaint(paintIndex, BodyPartCatalog.slotOf(part), defId)
            } else null
            val dstOffset = androidx.compose.ui.unit.IntOffset(
                (originX + spec.fx * drawW).toInt(),
                (originY + spec.fy * drawH).toInt()
            )
            val dstSize = androidx.compose.ui.unit.IntSize(
                (img.fixed.width * k).toInt().coerceAtLeast(1),
                (img.fixed.height * k).toInt().coerceAtLeast(1)
            )
            drawImage(
                image = img.fixed,
                dstOffset = dstOffset,
                dstSize = dstSize
            )
            drawImage(
                image = img.paint,
                dstOffset = dstOffset,
                dstSize = dstSize,
                colorFilter = ColorFilter.tint(
                    paintColor ?: Color(VehiclePaint.ORANGE.argb),
                    BlendMode.Modulate
                )
            )
            // Nízky stav plechu sa vizuálne prizná hrdzavou patinou. Druhý
            // priechod rešpektuje alfa masku obrázka, takže nefarbí okolie dielu.
            val rust = if (painted) ((0.62f - health) / 0.60f).coerceIn(0f, 0.48f) else 0f
            if (rust > 0.01f) {
                drawImage(
                    image = img.paint,
                    dstOffset = dstOffset,
                    dstSize = dstSize,
                    alpha = rust,
                    colorFilter = ColorFilter.tint(Color(0xFF9A4F22), BlendMode.SrcIn)
                )
            }
        }

        // Sedadlá sú v kabíne, teda pod plechom; zvyšok naň.
        BodyPartCatalog.order.filter { BodyPartCatalog.behindBody(it) }.forEach { part ->
            car.parts[BodyPartCatalog.slotOf(part)]?.let {
                blit(part, it.defId, it.health, it.paintIndex)
            }
        }
        drawImage(
            image = layers.stripped.fixed,
            dstOffset = androidx.compose.ui.unit.IntOffset(originX.toInt(), originY.toInt()),
            dstSize = androidx.compose.ui.unit.IntSize(
                drawW.toInt().coerceAtLeast(1), drawH.toInt().coerceAtLeast(1)
            )
        )
        drawImage(
            image = layers.stripped.paint,
            dstOffset = androidx.compose.ui.unit.IntOffset(originX.toInt(), originY.toInt()),
            dstSize = androidx.compose.ui.unit.IntSize(
                drawW.toInt().coerceAtLeast(1), drawH.toInt().coerceAtLeast(1)
            ),
            colorFilter = ColorFilter.tint(
                Color(VehiclePaint.at(car.bodyPaintIndex).argb),
                BlendMode.Modulate
            )
        )
        BodyPartCatalog.order.filterNot { BodyPartCatalog.behindBody(it) }.forEach { part ->
            car.parts[BodyPartCatalog.slotOf(part)]?.let {
                blit(part, it.defId, it.health, it.paintIndex)
            }
        }
    }

    /** Plechy z rôznych nálezov nemusia ladiť; pár dverí však drží jednu farbu. */
    private fun partPaint(paintIndex: Int, slot: ComponentSlot, defId: String?): Color {
        if (slot == ComponentSlot.ROOF_RACK) return Color(0xFF73787A)
        if (paintIndex >= 0) return Color(VehiclePaint.at(paintIndex).argb)
        val seed = (defId.orEmpty().hashCode() * 31 + slot.ordinal * 17) and Int.MAX_VALUE
        return Color(VehiclePaint.at(seed).argb)
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
        tireId: String? = null,
        art: ImageBitmap? = null
    ) = drawWheelScreen(cx, cy, spinDeg, r, tireHealth, accent, blurSteps, tireId, art)

    /**
     * Koleso z predlohy. Stred obrázka je stred kolesa, takže sa kreslí okolo
     * osi a rotuje s ňou – guma aj disk sú v jednej kresbe.
     *
     * Pri rýchlosti sa dokresľuje niekoľko pootočených kópií so zníženou
     * krytím: to je rozmazanie, vďaka ktorému koleso nevyzerá, že stojí.
     * Zodratá guma má menší priemer, tak ako predtým.
     *
     * @param load 0..1 – ako je náprava zaťažená. Guma sa pod váhou sploští
     *   na styčnej ploche a mierne vydutí do strán; bez toho pôsobí ako
     *   nakreslený kotúč, ktorý sa len točí.
     * @param airborne true = koleso visí vo vzduchu a guma sa uvoľní
     * @param blown true = guma je na handry, ide sa prakticky na disku
     */
    private fun DrawScope.drawWheelScreen(
        cx: Float,
        cy: Float,
        spinDeg: Float,
        r: Float,
        tireHealth: Float,
        accent: Color,
        blurSteps: Int = 1,
        tireId: String? = null,
        art: ImageBitmap? = null,
    ) {
        val rr = r * (0.90f + 0.10f * tireHealth)
        val img = art ?: run {
            // Bez predlohy aspoň čierny kotúč, nech koleso nezmizne.
            drawCircle(Color(0xFF1B1E22), rr, Offset(cx, cy))
            return
        }
        val diameter = rr * 2f
        // IntOffset/IntSize pri každom snímku zaokrúhlili stred inak. Pri
        // pomalej jazde to vyzeralo ako poskakovanie kolesa vo blatníku.
        // Transformácia drží os kolesa vo floatových súradniciach a bitmapu
        // len plynulo škáluje okolo nej.
        val artScale = diameter / img.width.toFloat()
        val left = cx - rr
        val top = cy - rr
        // Koleso ostáva kruhové. Skúšal som ho pod záťažou splošťovať na
        // styčnej ploche, ale aj pri pár percentách je predloha dosť veľká
        // na to, aby to bolo vidieť ako vajce – a stojace auto tak vyzeralo
        // na prázdnych gumách. Pruženie pohyb kolesa ukáže aj bez toho.
        val blur = blurSteps.coerceIn(1, 4)
        for (b in 0 until blur) {
            rotate(degrees = spinDeg - b * 9f, pivot = Offset(cx, cy)) {
                withTransform({
                    translate(left = left, top = top)
                    scale(scaleX = artScale, scaleY = artScale, pivot = Offset.Zero)
                }) {
                    drawImage(image = img, alpha = 1f / blur)
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
