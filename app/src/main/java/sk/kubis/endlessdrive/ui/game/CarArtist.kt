package sk.kubis.endlessdrive.ui.game

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.clipPath
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
import sk.kubis.endlessdrive.game.car.TireInjury
import sk.kubis.endlessdrive.game.car.WHEEL_HUB_FRAC
import kotlin.math.cos
import kotlin.math.sin

/**
 * Sedan: sprite vrstvy (montáž) + kolesá. Bez dielov = „holý“ podvozok.
 */
class CarArtist {
    private val bodyPath = Path()
    private val rubberPath = Path()
    /** Vlastný clip na náboj – zdieľaný Path v clipPath() orezával aj druhé koleso. */
    private val hubClip = Path()
    private val rustFilter = ColorFilter.tint(Color(0xFF9A4F22), BlendMode.SrcIn)
    private val modulateFilters = mutableMapOf<Int, ColorFilter>()

    private fun modulateTint(color: Color): ColorFilter {
        val argb = color.toArgb()
        return modulateFilters.getOrPut(argb) { ColorFilter.tint(color, BlendMode.Modulate) }
    }

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
        frontWheelY: Float = bodyY,
        rearRoadSlopeDeg: Float = 0f,
        frontRoadSlopeDeg: Float = 0f,
        /** Iskry z ráfika ešte pred kolesom – inak idú cez výrezy v disku. */
        betweenBodyAndWheels: DrawScope.() -> Unit = {}
    ) {
        val cosA = cos(angle)
        val sinA = sin(angle)
        val deg = -Math.toDegrees(angle.toDouble()).toFloat()

        val accent = SedanSpec.accentColor

        // Karoséria podľa fyziky; kolesá samostatne → viditeľné pruženie.
        val layout = spriteLayoutAtBody(layers, bodyX, bodyY, ppm)
        rotate(degrees = deg, pivot = Offset(bodyX, bodyY)) {
            drawSpriteAssembly(car, layers, layout)
        }
        // Nosič patrí na strechu – kreslí sa v rovine karosérie, teda spolu
        // s ňou aj rotuje.
        if (car.hasRoofRack && !ExpeditionEquipment.installed(car)) {
            rotate(degrees = deg, pivot = Offset(bodyX, bodyY)) {
                drawRoofLoad(layers, layout, cargoFill)
            }
        }
        betweenBodyAndWheels()
        drawAxleWheel(
            car, ComponentSlot.TIRE_REAR, rearWheelX, rearWheelY, rearSpinDeg,
            layout.wheelR, layers, accent, rearRoadSlopeDeg
        )
        drawAxleWheel(
            car, ComponentSlot.TIRE_FRONT, frontWheelX, frontWheelY, frontSpinDeg,
            layout.wheelR, layers, accent, frontRoadSlopeDeg
        )
    }

    /** Obrazovková pozícia žiarovky – rovnaká kotva ako diera v nočnom závoji. */
    fun lampOnBody(layers: SedanLayers, bodyX: Float, bodyY: Float, ppm: Float): Offset {
        val layout = spriteLayoutAtBody(layers, bodyX, bodyY, ppm)
        return Offset(
            bodyX + HeadlightFx.lampAheadM(layers.headlightFx, layers.worldWidthM) * ppm,
            layout.originY + layers.headlightFy * layout.drawH
        )
    }

    /** Strešný reflektor expedičného nosiča – druhá diera v nočnom závoji. */
    fun rackLampOnBody(layers: SedanLayers, bodyX: Float, bodyY: Float, ppm: Float): Offset {
        val layout = spriteLayoutAtBody(layers, bodyX, bodyY, ppm)
        return Offset(
            layout.originX + HeadlightFx.RACK_LAMP_FX * layout.drawW,
            layout.originY + HeadlightFx.RACK_LAMP_FY * layout.drawH
        )
    }

    /**
     * Žiara žiarovky a zadné svetlá. Samotné osvetlenie scény robí renderer
     * dierou v nočnom závoji, nie žltým overlayom cez tmu.
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
        val layout = spriteLayoutAtBody(layers, bodyX, bodyY, ppm)
        val lamp = lampOnBody(layers, bodyX, bodyY, ppm)
        rotate(degrees = deg, pivot = Offset(bodyX, bodyY)) {
            val tx = layout.originX + layers.taillightFx * layout.drawW
            val ty = layout.originY + layers.taillightFy * layout.drawH
            val coreR = ppm * if (highBeam) 0.17f else 0.14f
            val bloomR = ppm * if (highBeam) 0.40f else 0.32f
            drawCircle(
                Color(0xFFFFF8DC).copy(alpha = 0.90f * strength),
                coreR,
                lamp,
                blendMode = BlendMode.Plus
            )
            drawCircle(
                Color(0xFFFFEFA8).copy(alpha = 0.28f * strength),
                bloomR,
                lamp,
                blendMode = BlendMode.Plus
            )

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
        val exhaustX: Float get() = originX + drawW * CarBodyFx.EXHAUST_FX
        val exhaustY: Float get() = originY + drawH * CarBodyFx.EXHAUST_FY
        val radiatorX: Float get() = originX + drawW * CarBodyFx.RADIATOR_FX
        val radiatorY: Float get() = originY + drawH * CarBodyFx.RADIATOR_FY
        val hoodVentX: Float get() = originX + drawW * CarBodyFx.HOOD_VENT_FX
        val hoodVentY: Float get() = originY + drawH * CarBodyFx.HOOD_VENT_FY
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
            val slot = BodyPartCatalog.slotOf(part)
            val tinted = BodyPartCatalog.appliesPaintTint(part)
            val paintColor = if (tinted) partPaint(paintIndex, slot, defId) else null
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
            // Svetlá ostávajú ako v PNG – žiadna laková ani tmavá vrstva na skle.
            val lights = part == BodyPart.HEADLIGHT || part == BodyPart.TAILLIGHT
            if (paintColor != null && !lights) {
                drawImage(
                    image = img.paint,
                    dstOffset = dstOffset,
                    dstSize = dstSize,
                    colorFilter = modulateTint(paintColor)
                )
            }
            // Nízky stav plechu sa vizuálne prizná hrdzavou patinou. Druhý
            // priechod rešpektuje alfa masku obrázka, takže nefarbí okolie dielu.
            val rust = if (slot.takesBodyPaint) {
                ((0.62f - health) / 0.60f).coerceIn(0f, 0.48f)
            } else {
                0f
            }
            if (rust > 0.01f) {
                drawImage(
                    image = img.paint,
                    dstOffset = dstOffset,
                    dstSize = dstSize,
                    alpha = rust,
                    colorFilter = rustFilter
                )
            }
        }

        // Sedadlá sú v kabíne, teda pod plechom; zvyšok naň.
        BodyPartCatalog.behindBodyParts.forEach { part ->
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
            colorFilter = modulateTint(Color(VehiclePaint.at(car.bodyPaintIndex).argb))
        )
        BodyPartCatalog.inFrontBodyParts.forEach { part ->
            car.parts[BodyPartCatalog.slotOf(part)]?.let {
                blit(part, it.defId, it.health, it.paintIndex)
            }
        }
        with(ExpeditionEquipment) { draw(car, layout) }
    }

    /** Plechy z rôznych nálezov nemusia ladiť; pár dverí však drží jednu farbu. */
    private fun partPaint(paintIndex: Int, slot: ComponentSlot, defId: String?): Color {
        if (slot == ComponentSlot.ROOF_RACK) return Color(0xFF73787A)
        if (!slot.takesBodyPaint) return Color.White
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
        art: ImageBitmap? = null,
        injury: TireInjury = TireInjury.INFLATED,
        flatArt: ImageBitmap? = null
    ) {
        when (injury) {
            TireInjury.SHREDDED -> drawWheelHub(
                cx, cy, spinDeg, r, r * HUB_FRAC, art, 1
            )
            TireInjury.PUNCTURED ->
                drawPuncturedWheel(cx, cy, spinDeg, r, art, flatArt, blurSteps, tireHealth)
            TireInjury.INFLATED ->
                drawWheelScreen(cx, cy, spinDeg, r, tireHealth, accent, blurSteps, tireId, art)
        }
    }

    /**
     * Koleso podľa stavu gumy: nafúknuté, defekt (spľasnuté), alebo holý ráfik.
     * Chýbajúca guma vyzerá ako jazda na disku.
     */
    fun DrawScope.drawAxleWheel(
        car: Car,
        slot: ComponentSlot,
        cx: Float,
        cy: Float,
        spinDeg: Float,
        wellR: Float,
        layers: SedanLayers,
        accent: Color,
        roadSlopeDeg: Float = 0f
    ) {
        val injury = car.tireInjury(slot)
        val tire = car.parts[slot]
        // Veľkosť predlohy ostáva podľa gumy. Pokles nápravy pri jazde na
        // disku rieši póza, nie zmenšenie celého sprite.
        val r = wellR * car.wheelScale(slot)
        val speed = when {
            car.wheelsLocked -> 0f
            car.drives(slot) -> car.wheelSpeed
            else -> car.speed
        }
        val blur = 1 + (kotlin.math.abs(speed) / 6f).toInt().coerceAtMost(3)
        val sitOnSlope = injury == TireInjury.PUNCTURED && kotlin.math.abs(roadSlopeDeg) > 0.05f
        fun paint() {
            when (injury) {
                TireInjury.SHREDDED -> drawWheelHub(
                    cx, cy, spinDeg, r, r * HUB_FRAC,
                    layers.wheelImage(tire?.defId), 1
                )
                TireInjury.PUNCTURED -> drawPuncturedWheel(
                    cx, cy, spinDeg, r,
                    layers.wheelImage(tire?.defId),
                    layers.flatWheelImage(tire?.defId),
                    blur,
                    tire?.health ?: 1f,
                    if (car.hasChains) layers.flatChainOverlay() else null
                )
                TireInjury.INFLATED -> {
                    drawWheelScreen(
                        cx, cy, spinDeg, r,
                        tire?.health ?: 0.5f, accent, blur, tire?.defId,
                        layers.wheelImage(tire?.defId)
                    )
                    if (car.hasChains) {
                        drawWheelScreen(
                            cx, cy, spinDeg, r,
                            tire?.health ?: 0.5f, accent, blur, tire?.defId,
                            layers.chainOverlay()
                        )
                    }
                }
            }
        }
        if (sitOnSlope) {
            rotate(degrees = roadSlopeDeg, pivot = Offset(cx, cy)) { paint() }
        } else {
            paint()
        }
    }

    /**
     * Defekt: spľasnutá guma z predlohy daného modelu (šport / terén /
     * ojazdené / std), disk ostáva kruhový a točí sa z nafúknutej kresby.
     * Placka drží to isté AABB ako nafúknuté koleso.
     */
    private fun DrawScope.drawPuncturedWheel(
        cx: Float,
        cy: Float,
        spinDeg: Float,
        r: Float,
        art: ImageBitmap?,
        flatArt: ImageBitmap?,
        blurSteps: Int,
        tireHealth: Float,
        chainFlat: ImageBitmap? = null
    ) {
        val rr = PuncturedTireShape.rubberRadius(r, tireHealth)
        val hubR = rr * HUB_FRAC
        if (flatArt != null) {
            // Guma sa netočí – leží na ceste. Rozmazanie by placku roztočilo.
            drawWheelScreen(cx, cy, 0f, rr, 1f, Color.White, 1, null, flatArt)
            if (chainFlat != null) {
                drawWheelScreen(cx, cy, 0f, rr, 1f, Color.White, 1, null, chainFlat)
            }
        } else {
            clipRect(left = cx - rr, top = cy - rr, right = cx + rr, bottom = cy + rr) {
                drawFlattenedRubber(cx, cy, rr, hubR)
            }
        }
        drawWheelHub(cx, cy, spinDeg, rr, hubR, art, blurSteps)
    }

    /**
     * Bočný silueta prázdnej gumy. Vrch ostáva oblúk, spodok je placka na
     * ceste pod ráfikom. Prstenec (EvenOdd) vysekne disk, inak by to bol
     * sivý koláč. Riadiace body kubic ostávajú v |x| <= rr.
     */
    private fun DrawScope.drawFlattenedRubber(cx: Float, cy: Float, rr: Float, hubR: Float) {
        val road = cy + rr
        val half = PuncturedTireShape.contactHalf(rr)
        val sideY = cy + rr * PuncturedTireShape.SIDE_CTRL_Y_FRAC
        val oval = Rect(cx - rr, cy - rr, cx + rr, cy + rr)
        rubberPath.reset()
        rubberPath.fillType = PathFillType.EvenOdd
        rubberPath.moveTo(cx - rr, cy)
        rubberPath.arcTo(oval, 180f, 180f, false)
        rubberPath.cubicTo(
            cx + rr, sideY,
            cx + half, road,
            cx + half, road
        )
        rubberPath.lineTo(cx - half, road)
        rubberPath.cubicTo(
            cx - half, road,
            cx - rr, sideY,
            cx - rr, cy
        )
        rubberPath.close()
        rubberPath.addOval(Rect(cx - hubR, cy - hubR, cx + hubR, cy + hubR))
        drawPath(rubberPath, Color(0xFF2A3036))
        val shadowH = rr * 0.055f
        drawOval(
            Color(0xFF121416),
            topLeft = Offset(cx - half * 0.92f, road - shadowH),
            size = Size(half * 1.84f, shadowH)
        )
    }

    /**
     * Len stred predlohy – guma z obrázka sa oreže, ostane kruhový disk
     * v pôvodnom štýle (sport / off-road / ojazdené).
     */
    private fun DrawScope.drawWheelHub(
        cx: Float,
        cy: Float,
        spinDeg: Float,
        fullR: Float,
        hubR: Float,
        art: ImageBitmap?,
        blurSteps: Int
    ) {
        if (art == null) {
            drawBareRim(cx, cy, spinDeg, hubR)
            return
        }
        // Tesnejší clip – okraj gumy z predlohy robil biely „duch“ okolo ráfika.
        val clipR = hubR * 0.94f
        hubClip.rewind()
        hubClip.addOval(Rect(cx - clipR, cy - clipR, cx + clipR, cy + clipR))
        withTransform({ clipPath(hubClip) }) {
            drawWheelScreen(cx, cy, spinDeg, fullR, 1f, Color.White, blurSteps, null, art)
        }
        drawCircle(
            Color(0xFF1A1E22),
            clipR,
            Offset(cx, cy),
            style = Stroke(width = (hubR * 0.06f).coerceAtLeast(1f))
        )
    }

    /** Holý disk – guma je preč. Iskry kreslí renderer pri kontakte s vozovkou. */
    fun DrawScope.drawBareRim(cx: Float, cy: Float, spinDeg: Float, r: Float) {
        val metal = Color(0xFF9AA0A6)
        val dark = Color(0xFF2A2E32)
        val hub = Color(0xFFD4D7DB)
        val rimR = r.coerceAtLeast(2f)
        rotate(degrees = spinDeg, pivot = Offset(cx, cy)) {
            drawCircle(dark, rimR, Offset(cx, cy))
            drawCircle(
                metal,
                rimR * 0.88f,
                Offset(cx, cy),
                style = Stroke(width = rimR * 0.16f)
            )
            for (i in 0 until 5) {
                val a = Math.toRadians(i * 72.0 + 8.0)
                drawLine(
                    color = metal,
                    start = Offset(cx, cy),
                    end = Offset(
                        cx + (kotlin.math.cos(a) * rimR * 0.78).toFloat(),
                        cy + (kotlin.math.sin(a) * rimR * 0.78).toFloat()
                    ),
                    strokeWidth = rimR * 0.09f,
                    cap = StrokeCap.Round
                )
            }
            drawCircle(hub, rimR * 0.24f, Offset(cx, cy))
            drawCircle(dark, rimR * 0.08f, Offset(cx, cy))
        }
    }

    /**
     * Koleso z predlohy. Stred obrázka je stred kolesa, takže sa kreslí okolo
     * osi a rotuje s ňou – guma aj disk sú v jednej kresbe.
     *
     * Pri rýchlosti sa dokresľujú slabo pootočené kópie okolo plne krytej
     * gumy: to je rozmazanie, vďaka ktorému koleso nevyzerá, že stojí.
     * Základ ostáva nepriehľadný – delenie alfy počtom krokov by gumu
     * spravilo priesvitnou. Zodratá guma má menší priemer, tak ako predtým.
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
        // Najprv slabo pootočené kópie, nakoniec plné koleso – stred gumy
        // ostane nepriehľadný aj pri štyroch krokoch rozmazania.
        for (b in (blur - 1) downTo 0) {
            rotate(degrees = spinDeg - b * 9f, pivot = Offset(cx, cy)) {
                withTransform({
                    translate(left = left, top = top)
                    scale(scaleX = artScale, scaleY = artScale, pivot = Offset.Zero)
                }) {
                    drawImage(image = img, alpha = WheelMotionBlur.copyAlpha(b, blur))
                }
            }
        }
    }

    companion object {
        /** Relatívny stred karosérie v sprite (pivot / fyzický stred). */
        private const val BODY_CENTER_FY = 0.52f
        /** Kde v sprite začína strecha (podiel výšky obrázka). */
        private const val ROOF_FY = 0.22f
        /** Podiel vonkajšieho polomeru, ktorý v predlohe patrí disku, nie gume. */
        private const val HUB_FRAC = WHEEL_HUB_FRAC
    }
}

/**
 * Geometria spľasnutej gumy – rovnaké AABB ako nafúknuté koleso, placka
 * na y = cy+rr, bez bočného vydutia. Bez Compose, aby to vedel overiť test.
 */
internal object PuncturedTireShape {
    const val CONTACT_HALF_FRAC = 0.70f
    const val SIDE_CTRL_Y_FRAC = 0.38f

    fun rubberRadius(r: Float, health: Float): Float {
        // Styčná výška ostáva ako pri nafúknutom kole; [health] je pre API volajúcich.
        if (health < 0f) return r
        return r
    }

    fun contactHalf(rr: Float): Float = rr * CONTACT_HALF_FRAC

    fun controlXs(cx: Float, rr: Float): FloatArray {
        val half = contactHalf(rr)
        return floatArrayOf(cx - rr, cx + rr, cx - half, cx + half)
    }

    /**
     * Polovičná šírka placky v normalizovaných súradniciach (vonkajší okraj = 1,
     * +y dole). Vrch ostáva kruh, spodok sa roztiahne na styčnú plochu.
     */
    fun halfWidth(ny: Float): Float {
        if (ny < -1f || ny > 1f) return 0f
        val circle = kotlin.math.sqrt((1f - ny * ny).coerceAtLeast(0f))
        if (ny <= 0f) return circle
        val pancake = 1f - (1f - CONTACT_HALF_FRAC) * ny * ny
        return kotlin.math.max(circle, pancake)
    }

    /**
     * Dest pixel placky → zdroj v nafúknutom kole. Hub je identita, guma v
     * spodnej polovici sa vodorovne natiahne z kruhu na D. Mimo placky null.
     */
    fun destToSource(
        dx: Float,
        dy: Float,
        hubFrac: Float = WHEEL_HUB_FRAC
    ): Pair<Float, Float>? {
        val destR = kotlin.math.hypot(dx, dy)
        if (destR <= hubFrac) return dx to dy
        if (kotlin.math.abs(dy) > 1f + 1e-4f) return null
        val dstOuter = halfWidth(dy)
        if (dstOuter <= 0f || kotlin.math.abs(dx) > dstOuter + 1e-4f) return null
        val hubEdge = if (kotlin.math.abs(dy) < hubFrac) {
            kotlin.math.sqrt((hubFrac * hubFrac - dy * dy).coerceAtLeast(0f))
        } else {
            0f
        }
        if (kotlin.math.abs(dx) <= hubEdge) return dx to dy
        val srcOuter = kotlin.math.sqrt((1f - dy * dy).coerceAtLeast(0f))
        val span = dstOuter - hubEdge
        if (span <= 1e-6f) return null
        val t = ((kotlin.math.abs(dx) - hubEdge) / span).coerceIn(0f, 1f)
        val srcX = kotlin.math.sign(dx) * (hubEdge + t * (srcOuter - hubEdge))
        return srcX to dy
    }
}

/**
 * Motion blur kolesa: prvá kópia je plne krytá, ďalšie len slabý šmuh.
 * Delenie 1/blur by gumu spravilo priesvitnou, lebo pootočené kópie
 * sa na disku neprekryjú.
 */
internal object WheelMotionBlur {
    fun copyAlpha(copyIndex: Int, blurSteps: Int): Float {
        val blur = blurSteps.coerceIn(1, 4)
        if (copyIndex <= 0) return 1f
        if (copyIndex >= blur) return 0f
        return (0.22f / copyIndex).coerceIn(0.08f, 0.28f)
    }
}
