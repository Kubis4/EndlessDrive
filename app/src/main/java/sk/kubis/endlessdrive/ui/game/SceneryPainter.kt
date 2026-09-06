package sk.kubis.endlessdrive.ui.game

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import sk.kubis.endlessdrive.core.GameConfig
import sk.kubis.endlessdrive.core.MathX
import sk.kubis.endlessdrive.domain.model.BiomeType
import sk.kubis.endlessdrive.game.DepthProjection
import sk.kubis.endlessdrive.game.world.BiomeBlend

/**
 * Kulisy pozdĺž cesty – stromy, kríky, kamene, stĺpy elektriky, ploty a míľniky.
 * Všetko je deterministické z pozície (hash), takže netreba žiadny stav ani alokácie.
 */
/**
 * @param carLayers sprite sedanu aj s kotvami kolies. Vraky pri ceste sú tie
 *   isté autá, len opustené – kreslený tvar vedľa detailného sprite hráčovho
 *   auta pôsobil ako z inej hry.
 */
class SceneryPainter(
    private val carLayers: SedanLayers? = null,
    /** Karosérie vrakov – iné než hráčova, aby cesta nevyzerala ako sklad. */
    private val wreckSprites: List<WreckSprite> = emptyList()
) {
    private val propPath = Path()
    private val canopyPath = Path()
    private val materials = MaterialPainter()
    /** Farba lúky z kresby biómu – koruny a tráva sa k nej priblížia. */
    private var landTint = Color.White
    private var landTintAmount = 0f
    private val wreckFilters = arrayOfNulls<ColorFilter>(WRECK_PAINT.size)
    private val wreckFilterArgb = IntArray(WRECK_PAINT.size) { Int.MIN_VALUE }

    private fun land(color: Color, day: Float): Color =
        shade(lerp(color, landTint, landTintAmount), day)

    /**
     * Vegetácia a stĺpy za cestou.
     *
     * @param occupiedAt vráti true tam, kde už niečo stojí – kulisa sa tam
     *   nespawnne. Budovy sa kreslia až po kulisách, takže strom za garážou jej
     *   prerastal cez strechu a vyzeralo to, akoby rástol zo šindľov.
     */
    fun DrawScope.drawBackProps(
        fromX: Float,
        toX: Float,
        biomeAt: (Float) -> BiomeBlend,
        day: Float,
        depth: DepthProjection,
        heightAt: (Float) -> Float,
        occupiedAt: (Float) -> Boolean = { false },
        landTint: Color = Color.White,
        landTintAmount: Float = 0f
    ) {
        this@SceneryPainter.landTint = landTint
        this@SceneryPainter.landTintAmount = landTintAmount.coerceIn(0f, 1f)
        val first = MathX.floorDiv(fromX, CELL)
        val last = MathX.floorDiv(toX, CELL)
        for (cell in first..last) {
            val r = MathX.hash01(cell, BACK_SALT)
            val wx = cell * CELL + MathX.hash01(cell, 991) * CELL * 0.8f
            val blend = biomeAt(wx)
            val density = MathX.lerp(densityFor(blend.from), densityFor(blend.to), blend.amount)
            if (r > density) continue
            val biome = if (MathX.hash01(cell, BIOME_SALT) < blend.amount) blend.to else blend.from
            if (occupiedAt(wx)) continue
            // Hĺbka musí ostať v páse lúky (GameRenderer.SCENERY_BACK_DEPTH),
            // inak by kulisa vyletela nad terén k úbežníku.
            val d = GameConfig.ROAD_DEPTH + 0.45f + MathX.hash01(cell, 137) * 1.2f
            val kind = MathX.hash01(cell, 313)
            val scale = MathX.hash01(cell, 577)
            drawProp(wx, d, kind, scale, biome, day, depth, heightAt, cell)
        }
        drawPowerLine(fromX, toX, day, depth, heightAt, occupiedAt)
        drawGuidePosts(fromX, toX, day, depth, heightAt, occupiedAt)
        drawMilestones(fromX, toX, day, depth, heightAt, occupiedAt)
    }

    /**
     * Patníky pri krajnici. Na rovnej ceste stoja aj v skutočnosti a sú
     * najsilnejší zdroj pocitu rýchlosti – rýchlejšie sa míňajú než čokoľvek
     * iné v zábere. Odrazka je otočená k autu, takže v noci sa rozsvieti.
     */
    private fun DrawScope.drawGuidePosts(
        fromX: Float,
        toX: Float,
        day: Float,
        depth: DepthProjection,
        heightAt: (Float) -> Float,
        occupiedAt: (Float) -> Boolean
    ) {
        val d = GameConfig.ROAD_DEPTH + 0.06f
        val first = MathX.floorDiv(fromX, POST_SPACING)
        val last = MathX.floorDiv(toX, POST_SPACING)
        val body = shade(Color(0xFFD9D3C4), day)
        val cap = shade(Color(0xFF3A352C), day)
        // V noci odrazky svietia – cez deň sú len tmavé bodky.
        val night = (1f - day).coerceIn(0f, 1f)
        val reflector = lerp(Color(0xFF8A6A2A), Color(0xFFFFD87A), night)

        for (i in first..last) {
            val wx = i * POST_SPACING
            if (occupiedAt(wx)) continue
            val px = depth.atX(depth.frontX(wx), d)
            if (px < -40f || px > size.width + 40f) continue
            val py = depth.atY(depth.frontY(heightAt(wx)), d)
            val s = depth.ppm * (1f - depth.perspectiveT(d))
            val h = s * 0.62f
            val w = (s * 0.085f).coerceAtLeast(1.5f)
            rotate(degrees = slopeAt(wx, heightAt), pivot = Offset(px, py)) {
                drawLine(body, Offset(px, py), Offset(px, py - h), strokeWidth = w)
                // Čierna hlavica a pod ňou odrazka.
                drawLine(
                    cap,
                    Offset(px, py - h), Offset(px, py - h + s * 0.10f),
                    strokeWidth = w
                )
                drawCircle(
                    reflector.copy(alpha = 0.55f + 0.45f * night),
                    w * 0.55f,
                    Offset(px, py - h + s * 0.17f)
                )
            }
        }
    }

    /** Drobnosti v tráve pred cestou – rám záberu, kreslí sa až nad terénom. */
    fun DrawScope.drawFrontProps(
        fromX: Float,
        toX: Float,
        biomeAt: (Float) -> BiomeBlend,
        day: Float,
        depth: DepthProjection,
        heightAt: (Float) -> Float,
        occupiedAt: (Float) -> Boolean = { false },
        landTint: Color = Color.White,
        landTintAmount: Float = 0f
    ) {
        this@SceneryPainter.landTint = landTint
        this@SceneryPainter.landTintAmount = landTintAmount.coerceIn(0f, 1f)
        val first = MathX.floorDiv(fromX, FRONT_CELL)
        val last = MathX.floorDiv(toX, FRONT_CELL)
        for (cell in first..last) {
            if (MathX.hash01(cell, FRONT_SALT) > 0.55f) continue
            val wx = cell * FRONT_CELL + MathX.hash01(cell, 401) * FRONT_CELL
            if (occupiedAt(wx)) continue
            val blend = biomeAt(wx)
            val biome = if (MathX.hash01(cell, BIOME_FRONT_SALT) < blend.amount) blend.to else blend.from
            val d = 0.02f + MathX.hash01(cell, 733) * 0.22f
            val fx = depth.atX(depth.frontX(wx), d)
            val fy = depth.atY(depth.frontY(heightAt(wx)), d)
            val s = depth.ppm * (1f - depth.perspectiveT(d))
            val tall = MathX.hash01(cell, 1237)
            rotate(degrees = slopeAt(wx, heightAt), pivot = Offset(fx, fy)) {
                if (tall < 0.25f) {
                    stone(fx, fy, s * (0.10f + tall * 0.16f), day)
                } else {
                    grassTuft(fx, fy, s * (0.22f + tall * 0.30f), biome, day)
                }
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
        heightAt: (Float) -> Float,
        /** Bunka vo svete – z nej si kulisa berie svoju variantu. */
        cell: Int
    ) {
        val px = depth.atX(depth.frontX(wx), d)
        val py = depth.atY(depth.frontY(heightAt(wx)), d)
        if (px < -120f || px > size.width + 120f) return
        val s = depth.ppm * (1f - depth.perspectiveT(d)) * (0.8f + scaleRnd * 0.55f)
        val groundDeg = slopeAt(wx, heightAt)
        fun planted(block: DrawScope.() -> Unit) {
            rotate(degrees = groundDeg, pivot = Offset(px, py), block)
        }

        when (biome) {
            BiomeType.RURAL -> when {
                kind < 0.42f -> broadTree(px, py, s, day)
                kind < 0.62f -> pineTree(px, py, s, day)
                kind < 0.80f -> bush(px, py, s, day, Color(0xFF4E6B3C))
                kind < 0.92f -> planted { fence(px, py, s, day) }
                kind < 0.985f -> planted { stone(px, py, s * 0.32f, day) }
                else -> wreck(px, py, s, day, cell, groundDeg)
            }
            BiomeType.INDUSTRIAL -> when {
                kind < 0.26f -> pineTree(px, py, s, day)
                kind < 0.46f -> deadTree(px, py, s, day)
                kind < 0.66f -> planted { container(px, py, s, day) }
                kind < 0.84f -> planted { fence(px, py, s, day) }
                else -> planted { stone(px, py, s * 0.36f, day) }
            }
            BiomeType.WASTELAND -> when {
                kind < 0.34f -> deadTree(px, py, s, day)
                kind < 0.56f -> bush(px, py, s * 0.8f, day, Color(0xFF6E6741))
                kind < 0.74f -> planted { stone(px, py, s * 0.42f, day) }
                kind < 0.80f -> wreck(px, py, s, day, cell, groundDeg)
                else -> planted { fence(px, py, s * 0.8f, day) }
            }
            // Uschnutý les je stena holých kmeňov – ihličie ani kry tu nie sú.
            BiomeType.FOREST -> when {
                kind < 0.58f -> deadTree(px, py, s * 1.15f, day)
                kind < 0.80f -> pineTree(px, py, s * 1.05f, day)
                kind < 0.90f -> planted { stone(px, py, s * 0.34f, day) }
                else -> planted { logPile(px, py, s, day) }
            }
            // Živý les je stena kmeňov – takmer nič iné tam nestojí.
            BiomeType.FOREST_ALIVE -> when {
                kind < 0.52f -> pineTree(px, py, s * 1.15f, day)
                kind < 0.80f -> broadTree(px, py, s * 1.05f, day)
                kind < 0.92f -> bush(px, py, s, day, Color(0xFF3E5E32))
                else -> planted { logPile(px, py, s, day) }
            }
            BiomeType.DESERT -> when {
                kind < 0.34f -> cactus(px, py, s, day)
                kind < 0.52f -> bush(px, py, s * 0.7f, day, Color(0xFF8C8451))
                kind < 0.70f -> planted { stone(px, py, s * 0.50f, day) }
                kind < 0.84f -> deadTree(px, py, s * 0.9f, day)
                kind < 0.89f -> wreck(px, py, s, day, cell, groundDeg)
                else -> planted { fence(px, py, s * 0.75f, day) }
            }
            // Za súmraku ostanú z kulís len siluety – kaktus a skala.
            BiomeType.DESERT_DUSK -> when {
                kind < 0.40f -> cactus(px, py, s, day)
                kind < 0.66f -> planted { stone(px, py, s * 0.55f, day) }
                kind < 0.84f -> deadTree(px, py, s * 0.9f, day)
                kind < 0.90f -> wreck(px, py, s, day, cell, groundDeg)
                else -> planted { fence(px, py, s * 0.75f, day) }
            }
            // V búrke prežije len to najotužilejšie a aj to je sotva vidieť.
            BiomeType.SANDSTORM -> when {
                kind < 0.30f -> cactus(px, py, s * 0.85f, day)
                kind < 0.52f -> deadTree(px, py, s * 0.85f, day)
                kind < 0.76f -> planted { stone(px, py, s * 0.55f, day) }
                kind < 0.83f -> wreck(px, py, s, day, cell, groundDeg)
                else -> planted { fence(px, py, s * 0.7f, day) }
            }
            // Prachová búrka stojí nad mestom – pri ceste zostal jeho odpad.
            BiomeType.DUST_STORM -> when {
                kind < 0.24f -> planted { container(px, py, s * 0.9f, day) }
                kind < 0.44f -> deadTree(px, py, s * 0.85f, day)
                kind < 0.64f -> planted { fence(px, py, s * 0.8f, day) }
                kind < 0.82f -> planted { stone(px, py, s * 0.5f, day) }
                kind < 0.89f -> wreck(px, py, s, day, cell, groundDeg)
                else -> cactus(px, py, s * 0.8f, day)
            }
            BiomeType.ALPINE -> when {
                kind < 0.50f -> pineTree(px, py, s * 1.05f, day)
                kind < 0.78f -> planted { stone(px, py, s * 0.62f, day) }
                kind < 0.90f -> deadTree(px, py, s * 0.85f, day)
                else -> planted { logPile(px, py, s, day) }
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

    /**
     * Listnatý strom s objemom: kmeň sa zužuje a rozvetvuje, koruna je z
     * viacerých zhlukov v troch tónoch – tmavý spodok, stredný plášť a
     * presvetlená horná strana. Ploché kruhy v jednej farbe vyzerali kreslene.
     */
    private fun DrawScope.broadTree(x: Float, y: Float, s: Float, day: Float) {
        val h = s * 2.6f
        groundShadow(x, y, s, day, 1.1f)

        // Kmeň: zdola hrubší, hore užší, s náznakom rozvetvenia.
        val bark = shade(Color(0xFF4A382A), day)
        val barkLit = shade(Color(0xFF6B5340), day)
        propPath.reset()
        propPath.moveTo(x - s * 0.13f, y)
        propPath.lineTo(x - s * 0.055f, y - h * 0.60f)
        propPath.lineTo(x + s * 0.055f, y - h * 0.60f)
        propPath.lineTo(x + s * 0.13f, y)
        propPath.close()
        drawPath(propPath, bark)
        materials.shape(this, propPath, MaterialKind.BARK, x, y, s * 1.3f, 0.75f * day)
        drawLine(
            barkLit.copy(alpha = 0.6f),
            Offset(x - s * 0.04f, y - h * 0.05f), Offset(x - s * 0.02f, y - h * 0.55f),
            strokeWidth = s * 0.045f
        )
        // Dve vetvy do koruny.
        drawLine(bark, Offset(x, y - h * 0.48f), Offset(x - s * 0.34f, y - h * 0.66f), strokeWidth = s * 0.06f)
        drawLine(bark, Offset(x, y - h * 0.52f), Offset(x + s * 0.32f, y - h * 0.68f), strokeWidth = s * 0.055f)

        // Koruna v troch tónoch – tieň, plášť, svetlo.
        val dark = land(Color(0xFF2F4F2A), day)
        val mid = land(Color(0xFF4A7038), day)
        val lit = land(Color(0xFF6E9A46), day)
        canopyPath.reset()
        fun foliage(color: Color, radius: Float, center: Offset) {
            drawCircle(color, radius, center)
            canopyPath.addOval(Rect(center.x - radius, center.y - radius,
                center.x + radius, center.y + radius))
        }
        foliage(dark, s * 0.74f, Offset(x + s * 0.06f, y - h * 0.66f))
        foliage(dark, s * 0.50f, Offset(x - s * 0.52f, y - h * 0.50f))
        foliage(dark, s * 0.46f, Offset(x + s * 0.54f, y - h * 0.54f))
        foliage(mid, s * 0.62f, Offset(x - s * 0.02f, y - h * 0.72f))
        foliage(mid, s * 0.40f, Offset(x - s * 0.48f, y - h * 0.58f))
        foliage(mid, s * 0.36f, Offset(x + s * 0.50f, y - h * 0.60f))
        foliage(lit, s * 0.34f, Offset(x - s * 0.14f, y - h * 0.88f))
        foliage(lit.copy(alpha = 0.8f), s * 0.22f, Offset(x + s * 0.26f, y - h * 0.82f))
        materials.shape(this, canopyPath, MaterialKind.LEAVES, x - s, y - h,
            s * 3.6f, 0.95f * (0.1f + day * 0.9f))
    }

    /**
     * Ihličnan z piatich previsnutých poschodí. Každé má tmavý spodok a
     * svetlejší vrch, takže strom má objem – nie tri ploché trojuholníky.
     */
    private fun DrawScope.pineTree(x: Float, y: Float, s: Float, day: Float) {
        val h = s * 3.0f
        groundShadow(x, y, s, day, 0.9f)
        drawLine(
            shade(Color(0xFF3E2E22), day),
            Offset(x, y), Offset(x, y - h * 0.26f),
            strokeWidth = s * 0.14f
        )
        canopyPath.reset()
        val tiers = 5
        for (i in 0 until tiers) {
            val t = i / (tiers - 1f)
            val cy = y - h * (0.22f + t * 0.62f)
            val w = s * (0.92f - t * 0.62f)
            val tierH = h * (0.30f - t * 0.09f)
            // Spodná, tmavšia polovica poschodia.
            val dark = land(lerp(Color(0xFF23412A), Color(0xFF33583A), t), day)
            val lit = land(lerp(Color(0xFF396540), Color(0xFF548C4E), t), day)
            propPath.reset()
            propPath.moveTo(x - w, cy)
            // Previs na koncoch – vetvy nie sú rovná čiara.
            propPath.lineTo(x - w * 0.45f, cy - tierH * 0.14f)
            propPath.lineTo(x, cy - tierH)
            propPath.lineTo(x + w * 0.45f, cy - tierH * 0.14f)
            propPath.lineTo(x + w, cy)
            propPath.close()
            drawPath(propPath, dark)
            canopyPath.addPath(propPath)
            propPath.reset()
            propPath.moveTo(x - w * 0.62f, cy - tierH * 0.30f)
            propPath.lineTo(x, cy - tierH)
            propPath.lineTo(x + w * 0.28f, cy - tierH * 0.34f)
            propPath.close()
            drawPath(propPath, lit)
        }
        materials.shape(this, canopyPath, MaterialKind.NEEDLES, x - s, y - h,
            s * 3.2f, 0.90f * (0.1f + day * 0.9f))
    }

    private fun DrawScope.deadTree(x: Float, y: Float, s: Float, day: Float) {
        groundShadow(x, y, s, day, 0.7f)
        val col = land(Color(0xFF6B5B4A), day)
        val h = s * 2.2f
        drawLine(col, Offset(x, y), Offset(x - s * 0.08f, y - h), strokeWidth = s * 0.13f, cap = StrokeCap.Round)
        drawLine(col, Offset(x - s * 0.05f, y - h * 0.62f), Offset(x - s * 0.62f, y - h * 0.90f), strokeWidth = s * 0.08f, cap = StrokeCap.Round)
        drawLine(col, Offset(x - s * 0.06f, y - h * 0.75f), Offset(x + s * 0.55f, y - h * 0.98f), strokeWidth = s * 0.07f, cap = StrokeCap.Round)
    }

    private fun DrawScope.bush(x: Float, y: Float, s: Float, day: Float, base: Color) {
        groundShadow(x, y, s, day, 0.6f)
        val c = land(base, day)
        drawCircle(c, s * 0.42f, Offset(x, y - s * 0.30f))
        drawCircle(c, s * 0.32f, Offset(x - s * 0.35f, y - s * 0.18f))
        drawCircle(c, s * 0.30f, Offset(x + s * 0.33f, y - s * 0.20f))
        canopyPath.reset()
        canopyPath.addOval(Rect(x - s * 0.42f, y - s * 0.72f, x + s * 0.42f, y + s * 0.12f))
        canopyPath.addOval(Rect(x - s * 0.67f, y - s * 0.50f, x - s * 0.03f, y + s * 0.14f))
        canopyPath.addOval(Rect(x + s * 0.03f, y - s * 0.50f, x + s * 0.63f, y + s * 0.10f))
        materials.shape(this, canopyPath, MaterialKind.LEAVES, x, y, s * 3f, 0.85f * day)
    }

    private fun DrawScope.grassTuft(x: Float, y: Float, s: Float, biome: BiomeType, day: Float) {
        val c = land(
            when (biome) {
                BiomeType.RURAL -> Color(0xFF6F8B4A)
                BiomeType.INDUSTRIAL -> Color(0xFF6A7355)
                BiomeType.WASTELAND -> Color(0xFF8E8151)
                BiomeType.FOREST -> Color(0xFF6B7758)
                BiomeType.FOREST_ALIVE -> Color(0xFF56743F)
                BiomeType.DESERT -> Color(0xFFB49A62)
                BiomeType.DESERT_DUSK -> Color(0xFF8E6E6C)
                BiomeType.SANDSTORM -> Color(0xFFA98F5E)
                BiomeType.DUST_STORM -> Color(0xFFA1855A)
                BiomeType.ALPINE -> Color(0xFFB8C8C9)
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
        propPath.reset()
        propPath.addOval(Rect(x - s, y - s * 0.85f, x + s, y + s * 0.15f))
        materials.shape(this, propPath, MaterialKind.STONE, x, y, s * 6f, 0.8f * day)
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

    /**
     * Odstavený vrak ako budova – rovnaká kresba ako kulisa, aby sa
     * prehľadateľné auto pri ceste nezmenilo na obdĺžnik.
     */
    fun DrawScope.drawParkedWreck(
        x: Float,
        y: Float,
        s: Float,
        day: Float,
        seed: Int,
        groundDeg: Float
    ) {
        wreck(x, y, s, day, seed, groundDeg)
    }

    /**
     * Ohorený vrak pri ceste. Nie dva obdĺžniky – má tvar karosérie so
     * sklonenými stĺpikmi, vyzuté koleso, sadnutú nápravu a otvorenú kapotu.
     * Variantu určuje [seed], takže dva vraky vedľa seba nie sú rovnaké.
     */
    /** Sklon terénu pod kulisou v stupňoch – aby vrak neležal vodorovne na svahu. */
    private fun slopeAt(wx: Float, heightAt: (Float) -> Float): Float {
        val d = 1.2f
        val rise = heightAt(wx + d) - heightAt(wx - d)
        return -Math.toDegrees(kotlin.math.atan2(rise.toDouble(), (2f * d).toDouble())).toFloat()
    }

    private fun DrawScope.wreck(
        x: Float,
        y: Float,
        s: Float,
        day: Float,
        seed: Int = 0,
        groundDeg: Float = 0f
    ) {
        groundShadow(x, y, s, day, 1.35f)
        // Karoséria sa losuje zo sady – minivan, pickup, kabrio.
        // Hráčov sedan medzi vrakmi zámerne nie je.
        val wreckArt = if (wreckSprites.isNotEmpty()) {
            wreckSprites[
                (MathX.hash01(seed, 4157) * wreckSprites.size).toInt()
                    .coerceIn(0, wreckSprites.lastIndex)
            ]
        } else null
        val sprite = wreckArt?.image ?: carLayers?.stripped?.fixed
        if (sprite != null && sprite.width > 8) {
            val w = s * 3.6f
            val h = w * sprite.height / sprite.width
            // Kotvy kolies sú odmerané z konkrétneho spritu (blatníky sú
            // v predlohe vyrezané). Spoločné podiely by sadli len jednej
            // karosérii – minivan má rázvor inde než pickup.
            val wheelR = w * (wreckArt?.wheelRadiusFx ?: 0.072f)
            // Na zemi stojí koleso, nie spodná hrana obrázka – tá je prah
            // a leží vyššie. Preto sa karoséria posadí podľa groundFy.
            val topY = y - h * (wreckArt?.groundFy ?: 1f)
            val axleY = topY + h * (wreckArt?.axleFy ?: 0.90f)
            val rearX = x - w * 0.5f + w * (wreckArt?.rearFx ?: 0.235f)
            val frontX = x - w * 0.5f + w * (wreckArt?.frontFx ?: 0.775f)

            // Varianty: ktoré koleso chýba a či ostali okná.
            val variant = MathX.hash01(seed, 271)
            val hasRear = variant > 0.28f
            val hasFront = variant < 0.30f || variant > 0.62f

            // Poloha vraku. Rad áut stojacich rovnako a rovnako otočených
            // vyzeral ako výstavná plocha, nie ako opustená cesta.
            // Poloha „na boku“ je preč – auto stojace zvisle na nárazníku
            // vyzeralo ako zapichnuté do zeme, nie ako havarované.
            val poseRoll = MathX.hash01(seed, 1471)
            val pose = when {
                poseRoll < 0.56f -> WreckPose.UPRIGHT
                poseRoll < 0.86f -> WreckPose.REVERSED
                else -> WreckPose.OVERTURNED
            }
            // Auto bez kolesa si na ten roh sadne až na náboj a otáča sa
            // pritom okolo toho kolesa, ktoré mu ostalo – to zostáva na zemi.
            // Prevrátené leží na streche, tam sadanie nemá zmysel.
            val onWheels = pose == WreckPose.UPRIGHT || pose == WreckPose.REVERSED
            // Roh klesne o rozdiel medzi kolesom a holým bubnom – na ňom
            // nakoniec spočinie, do zeme sa nezaborí.
            val sagDeg = Math.toDegrees(
                kotlin.math.atan2((wheelR * 0.55f).toDouble(), (frontX - rearX).toDouble())
            ).toFloat()
            val settleDeg: Float
            val settlePivot: Offset
            when {
                onWheels && !hasRear -> { settleDeg = -sagDeg; settlePivot = Offset(frontX, y) }
                onWheels && !hasFront -> { settleDeg = sagDeg; settlePivot = Offset(rearX, y) }
                else -> { settleDeg = 0f; settlePivot = Offset(x, y) }
            }
            val poseDeg = when (pose) {
                WreckPose.UPRIGHT, WreckPose.REVERSED -> 0f
                WreckPose.OVERTURNED -> 180f + (MathX.hash01(seed, 331) - 0.5f) * 14f
            }
            // Sklon svahu sa pripočíta vždy – vrak leží na kopci ako všetko ostatné.
            val tiltDeg = groundDeg + poseDeg
            // Prevrátené sa točí okolo stredu karosérie, inak by sa zabodlo pod terén.
            val pivot = when (pose) {
                WreckPose.UPRIGHT, WreckPose.REVERSED -> Offset(x, y)
                // Stred medzi strechou a zemou – po otočení o 180° sadne
                // strecha presne tam, kde predtým stáli kolesá.
                WreckPose.OVERTURNED -> Offset(x, (topY + y) * 0.5f)
            }
            val paintIdx = (MathX.hash01(seed, 2237) * WRECK_PAINT.size).toInt()
                .coerceIn(0, WRECK_PAINT.size - 1)
            val paint = shade(WRECK_PAINT[paintIdx], day)
            val rust = lerp(paint, shade(Color(0xFF6B4A34), day), 0.35f + MathX.hash01(seed, 811) * 0.4f)
            val tyre = shade(Color(0xFF23201D), day)
            // Otočené autá pozerajú opačným smerom.
            val faceX = if (pose == WreckPose.REVERSED) -1f else 1f

            rotate(degrees = tiltDeg, pivot = pivot) {
                // Sadanie patrí dovnútra zrkadlenia: rearX/frontX platia
                // v orientácii predlohy. Zvonku by sa u otočeného vraku
                // otáčalo okolo opačného konca a auto by sa zabodlo.
                scale(scaleX = faceX, scaleY = 1f, pivot = Offset(x, y)) {
                    rotate(degrees = settleDeg, pivot = settlePivot) {
                        // Kolesá sedia v odmeraných blatníkoch, takže sa točia
                        // spolu s karosériou – prevrátenému autu trčia hore,
                        // ako má.
                        if (hasRear) wreckWheel(rearX, axleY, wheelR, tyre, day)
                        else wreckHub(rearX, axleY, wheelR, day)
                        if (hasFront) wreckWheel(frontX, axleY, wheelR, tyre, day)
                        else wreckHub(frontX, axleY, wheelR, day)

                        drawImage(
                            image = sprite,
                            dstOffset = IntOffset((x - w * 0.5f).toInt(), topY.toInt()),
                            dstSize = IntSize(w.toInt().coerceAtLeast(1), h.toInt().coerceAtLeast(1)),
                            colorFilter = bodyPaint(rust, paintIdx)
                        )
                    }
                }
            }
            return
        }
        val rust = shade(Color(0xFF6B4433), day)
        val rustDark = shade(Color(0xFF472E23), day)
        val glassless = shade(Color(0xFF241D19), day)
        val tyre = shade(Color(0xFF23201D), day)
        // Bez jedného kolesa auto sadne – ktoré chýba, určí seed.
        val missingRear = MathX.hash01(seed, 271) < 0.5f
        val tilt = if (missingRear) -s * 0.10f else s * 0.10f

        // Karoséria: nižší predok, vyšší zadok, sklonené stĺpiky kabíny.
        propPath.reset()
        propPath.moveTo(x - s * 1.05f, y - s * 0.18f + tilt)
        propPath.lineTo(x - s * 1.00f, y - s * 0.52f + tilt)
        propPath.lineTo(x - s * 0.42f, y - s * 0.60f)
        propPath.lineTo(x - s * 0.20f, y - s * 0.96f)
        propPath.lineTo(x + s * 0.38f, y - s * 0.94f)
        propPath.lineTo(x + s * 0.56f, y - s * 0.58f)
        propPath.lineTo(x + s * 1.02f, y - s * 0.50f - tilt)
        propPath.lineTo(x + s * 1.06f, y - s * 0.16f - tilt)
        propPath.close()
        drawPath(propPath, rust)

        // Prázdne okná – z kabíny ostal len otvor.
        propPath.reset()
        propPath.moveTo(x - s * 0.30f, y - s * 0.62f)
        propPath.lineTo(x - s * 0.14f, y - s * 0.88f)
        propPath.lineTo(x + s * 0.30f, y - s * 0.86f)
        propPath.lineTo(x + s * 0.42f, y - s * 0.62f)
        propPath.close()
        drawPath(propPath, glassless)

        // Otvorená kapota opretá dohora.
        if (MathX.hash01(seed, 613) < 0.6f) {
            drawLine(
                rustDark,
                Offset(x + s * 0.58f, y - s * 0.56f),
                Offset(x + s * 1.02f, y - s * 0.96f),
                strokeWidth = s * 0.09f,
                cap = StrokeCap.Round
            )
        }
        // Hrdza a diery v boku.
        drawOval(
            rustDark.copy(alpha = 0.8f),
            topLeft = Offset(x - s * 0.72f, y - s * 0.46f),
            size = Size(s * 0.38f, s * 0.20f)
        )

        // Kolesá: jedno chýba, náprava tam sadla do zeme.
        val front = Offset(x + s * 0.62f, y - s * 0.10f + if (missingRear) 0f else tilt)
        val rear = Offset(x - s * 0.62f, y - s * 0.10f + if (missingRear) tilt else 0f)
        if (missingRear) {
            drawCircle(tyre, s * 0.20f, front)
            drawCircle(shade(Color(0xFF3A342E), day), s * 0.08f, front)
            drawLine(rustDark, Offset(rear.x, rear.y - s * 0.14f), Offset(rear.x, rear.y), strokeWidth = s * 0.10f)
        } else {
            drawCircle(tyre, s * 0.20f, rear)
            drawCircle(shade(Color(0xFF3A342E), day), s * 0.08f, rear)
            drawLine(rustDark, Offset(front.x, front.y - s * 0.14f), Offset(front.x, front.y), strokeWidth = s * 0.10f)
        }
    }

    /** V akej polohe vrak pri ceste skončil. */
    private enum class WreckPose { UPRIGHT, REVERSED, OVERTURNED }

    /**
     * Prefarbenie karosérie vraku.
     *
     * Všetky tri predlohy sú oranžové, takže obyčajný Modulate z nich modrú
     * ani olivovú nespraví – násobenie sýtej oranžovej ju len stmaví. Preto
     * sa obraz najprv takmer odfarbí a až potom zafarbí; tieňovanie plechu
     * ostane, len sa prenesie do novej farby.
     */
    private fun bodyPaint(tint: Color, paintIdx: Int, sat: Float = 0.22f, gain: Float = 1.55f): ColorFilter {
        val packed = tint.toArgb()
        val slot = paintIdx.coerceIn(0, wreckFilters.lastIndex)
        wreckFilters[slot]?.let { if (wreckFilterArgb[slot] == packed) return it }
        val inv = 1f - sat
        fun row(t: Float, c: Int) = floatArrayOf(
            t * (0.299f * inv + if (c == 0) sat else 0f),
            t * (0.587f * inv + if (c == 1) sat else 0f),
            t * (0.114f * inv + if (c == 2) sat else 0f),
            0f, 0f
        )
        val r = row(tint.red * gain, 0)
        val g = row(tint.green * gain, 1)
        val b = row(tint.blue * gain, 2)
        return ColorFilter.colorMatrix(
            ColorMatrix(
                floatArrayOf(
                    r[0], r[1], r[2], 0f, 0f,
                    g[0], g[1], g[2], 0f, 0f,
                    b[0], b[1], b[2], 0f, 0f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
        ).also {
            wreckFilters[slot] = it
            wreckFilterArgb[slot] = packed
        }
    }

    /** Splasnuté koleso vraku – nie dokonalý kruh, guma je dole sadnutá. */
    private fun DrawScope.wreckWheel(x: Float, y: Float, r: Float, tyre: Color, day: Float) {
        drawOval(
            tyre,
            topLeft = Offset(x - r, y - r * 0.86f),
            size = Size(r * 2f, r * 1.72f)
        )
        drawCircle(shade(Color(0xFF474038), day), r * 0.40f, Offset(x, y))
    }

    /** Náboj bez kolesa – z blatníka trčí holý bubon aj so zvyškom čapu. */
    private fun DrawScope.wreckHub(x: Float, y: Float, r: Float, day: Float) {
        drawCircle(shade(Color(0xFF2E2822), day), r * 0.46f, Offset(x, y))
        drawCircle(shade(Color(0xFF6A5F52), day), r * 0.30f, Offset(x, y))
        drawCircle(shade(Color(0xFF3A322A), day), r * 0.11f, Offset(x, y))
    }

    /** Saguaro – jediný tvar, ktorý púšť pomenuje na prvý pohľad. */
    private fun DrawScope.cactus(x: Float, y: Float, s: Float, day: Float) {
        groundShadow(x, y, s, day, 0.6f)
        val c = land(Color(0xFF4B7247), day)
        val h = s * 2.1f
        drawLine(c, Offset(x, y), Offset(x, y - h), strokeWidth = s * 0.30f, cap = StrokeCap.Round)
        // Ramená sa zdvíhajú nahor – bez nich by to bol len zelený stĺpik.
        drawLine(c, Offset(x, y - h * 0.55f), Offset(x - s * 0.45f, y - h * 0.55f), strokeWidth = s * 0.18f, cap = StrokeCap.Round)
        drawLine(c, Offset(x - s * 0.45f, y - h * 0.55f), Offset(x - s * 0.45f, y - h * 0.86f), strokeWidth = s * 0.18f, cap = StrokeCap.Round)
        drawLine(c, Offset(x, y - h * 0.40f), Offset(x + s * 0.38f, y - h * 0.40f), strokeWidth = s * 0.16f, cap = StrokeCap.Round)
        drawLine(c, Offset(x + s * 0.38f, y - h * 0.40f), Offset(x + s * 0.38f, y - h * 0.66f), strokeWidth = s * 0.16f, cap = StrokeCap.Round)
    }

    /** Zrovnané klády pri lesnej ceste – stopa po ťažbe. */
    private fun DrawScope.logPile(x: Float, y: Float, s: Float, day: Float) {
        groundShadow(x, y, s, day, 1.2f)
        val bark = shade(Color(0xFF5C4530), day)
        val cut = shade(Color(0xFFA98456), day)
        for (row in 0 until 2) {
            val ry = y - s * (0.18f + row * 0.30f)
            val count = 3 - row
            for (i in 0 until count) {
                val cx = x + (i - (count - 1) * 0.5f) * s * 0.36f
                drawOval(
                    bark,
                    topLeft = Offset(cx - s * 0.18f, ry - s * 0.16f),
                    size = Size(s * 0.36f, s * 0.30f)
                )
                drawOval(
                    cut,
                    topLeft = Offset(cx - s * 0.11f, ry - s * 0.11f),
                    size = Size(s * 0.22f, s * 0.20f)
                )
            }
        }
    }

    /** Stĺpy elektrického vedenia s previsnutým drôtom – najsilnejší dojem rýchlosti. */
    private fun DrawScope.drawPowerLine(
        fromX: Float,
        toX: Float,
        day: Float,
        depth: DepthProjection,
        heightAt: (Float) -> Float,
        occupiedAt: (Float) -> Boolean
    ) {
        val d = GameConfig.ROAD_DEPTH + 0.55f
        val first = MathX.floorDiv(fromX, POLE_SPACING) - 1
        val last = MathX.floorDiv(toX, POLE_SPACING) + 1
        val poleCol = shade(Color(0xFF6A5540), day)
        val wireCol = shade(Color(0xFF3A3630), day).copy(alpha = 0.85f)

        for (i in first..last) {
            val wx = i * POLE_SPACING
            if (occupiedAt(wx)) continue
            val px = depth.atX(depth.frontX(wx), d)
            val py = depth.atY(depth.frontY(heightAt(wx)), d)
            val s = depth.ppm * (1f - depth.perspectiveT(d))
            val topY = py - s * 3.4f

            // Káble kreslíme ako dve hladké reťazovky. Spoj nevznikne cez
            // vynechaný stĺp (napr. v rokline), takže sa vedenie nekríži s mostom.
            val nx = (i + 1) * POLE_SPACING
            if (!occupiedAt(nx)) {
                val npx = depth.atX(depth.frontX(nx), d)
                val nextGroundY = depth.atY(depth.frontY(heightAt(nx)), d)
                val nextTopY = nextGroundY - s * 3.4f
                for (wire in 0 until 2) {
                    val off = s * (0.23f + wire * 0.34f)
                    val startY = topY + off
                    val endY = nextTopY + off
                    propPath.reset()
                    propPath.moveTo(px, startY)
                    propPath.quadraticBezierTo(
                        (px + npx) * 0.5f,
                        maxOf(startY, endY) + s * 0.44f,
                        npx,
                        endY
                    )
                    drawPath(
                        propPath,
                        wireCol,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(
                            width = (s * 0.026f).coerceAtLeast(0.8f),
                            cap = StrokeCap.Round
                        )
                    )
                }
            }
            drawLine(poleCol, Offset(px, py), Offset(px, topY), strokeWidth = (s * 0.12f).coerceAtLeast(1.5f))
            drawLine(
                poleCol,
                Offset(px - s * 0.42f, topY + s * 0.30f),
                Offset(px + s * 0.42f, topY + s * 0.30f),
                strokeWidth = (s * 0.08f).coerceAtLeast(1f)
            )
            // Malé izolátory ukážu, kam sa vodiče pripájajú.
            val insulator = shade(Color(0xFFB7A98D), day)
            drawCircle(insulator, (s * 0.075f).coerceAtLeast(1f), Offset(px, topY + s * 0.23f))
            drawCircle(insulator, (s * 0.075f).coerceAtLeast(1f), Offset(px, topY + s * 0.57f))
        }
    }

    /** Míľniky: každých 100 m stĺpik, každých 500 m tabuľa so vzdialenosťou. */
    private fun DrawScope.drawMilestones(
        fromX: Float,
        toX: Float,
        day: Float,
        depth: DepthProjection,
        heightAt: (Float) -> Float,
        occupiedAt: (Float) -> Boolean
    ) {
        val d = GameConfig.ROAD_DEPTH + 0.12f
        val first = MathX.floorDiv(fromX, MILESTONE)
        val last = MathX.floorDiv(toX, MILESTONE)
        for (i in first..last) {
            if (i <= 0) continue
            val wx = i * MILESTONE
            if (occupiedAt(wx)) continue
            val px = depth.atX(depth.frontX(wx), d)
            val py = depth.atY(depth.frontY(heightAt(wx)), d)
            val s = depth.ppm * (1f - depth.perspectiveT(d))
            val big = i % 5 == 0
            val h = s * (if (big) 1.5f else 0.75f)
            rotate(degrees = slopeAt(wx, heightAt), pivot = Offset(px, py)) {
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
    }

    private fun densityFor(biome: BiomeType): Float = when (biome) {
        BiomeType.RURAL -> 0.72f
        BiomeType.INDUSTRIAL -> 0.58f
        BiomeType.WASTELAND -> 0.42f
        // Les má kreslenú stenu stromov v pozadí – kulisy pri ceste ju len rámujú.
        BiomeType.FOREST -> 0.68f
        BiomeType.FOREST_ALIVE -> 0.74f
        BiomeType.DESERT -> 0.30f
        BiomeType.DESERT_DUSK -> 0.28f
        BiomeType.SANDSTORM -> 0.24f
        // V prachu presvitá mesto – pri ceste je toho viac než v čistej púšti.
        BiomeType.DUST_STORM -> 0.34f
        BiomeType.ALPINE -> 0.46f
    }

    private companion object {
        const val CELL = 5.5f
        const val FRONT_CELL = 2.6f
        const val POLE_SPACING = 26f
        /** Rozostup patníkov (m) – hustejšie než míľniky, preto je z nich cítiť rýchlosť. */
        const val POST_SPACING = 13f

        /**
         * Pôvodné laky vrakov. Auto pri ceste bolo kedysi niečie – jedna
         * hrdzavohnedá farba pre všetky pôsobila ako kópia toho istého kusu.
         */
        val WRECK_PAINT = listOf(
            Color(0xFF8A5A4A), // suriková červená
            Color(0xFF5E6B72), // vyblednutá modrosivá
            Color(0xFF6E7350), // olivová
            Color(0xFF4E4A46), // uhľová
            Color(0xFF8C7A46), // pieskovo béžová
            Color(0xFF7A5138), // hrdza
            Color(0xFF6A6F79)  // strieborná
        )
        const val MILESTONE = 100f
        const val BACK_SALT = 4523
        const val FRONT_SALT = 8171
        const val BIOME_SALT = 28939
        const val BIOME_FRONT_SALT = 28949
    }
}

/** Nočné stmavenie kulís – jeden spoločný vzorec pre celú scénu. */
internal fun shade(color: Color, day: Float): Color =
    lerp(lerp(color, Color(0xFF141B2E), 0.78f), color, day.coerceIn(0f, 1f))
