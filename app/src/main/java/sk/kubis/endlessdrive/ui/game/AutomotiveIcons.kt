package sk.kubis.endlessdrive.ui.game

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import sk.kubis.endlessdrive.R
import sk.kubis.endlessdrive.ui.theme.GameColors
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Subaru chart / ISO kontrolky: orezané PNG (biela maska) + tint, bez bloomu.
 * Farba prichádza zvonku (sivá / jantár / červená / zelená / modrá).
 */
enum class AutomotiveIcon {
    OIL, TEMPERATURE, BATTERY, ENGINE, COOLANT, TYRE, BRAKE,
    WHEELSPIN, LOW_BEAM, HIGH_BEAM, ROOF_LIGHT, SCRAP, FUEL, PARK,
    SUN, MOON, SPRING, SNOW
}

/** Akcie v pravom hornom rohu; nepoužívame emoji, aby mali stabilný vzhľad. */
enum class HudActionIcon { PAUSE, PLAY, TIMER, CLOSE }

/**
 * Bitmapové kontrolky (Subaru chart + ISO svetlá: stretávacie / diaľkové / prídavné).
 * FUEL ostáva ISO vektor — na charte nie je použiteľný glyf.
 */
private val AutomotiveIcon.telltaleRes: Int?
    get() = when (this) {
        AutomotiveIcon.OIL -> R.drawable.ic_telltale_oil
        AutomotiveIcon.TEMPERATURE -> R.drawable.ic_telltale_temp
        AutomotiveIcon.BATTERY -> R.drawable.ic_telltale_battery
        AutomotiveIcon.ENGINE -> R.drawable.ic_telltale_engine
        AutomotiveIcon.COOLANT -> R.drawable.ic_telltale_coolant
        AutomotiveIcon.TYRE -> R.drawable.ic_telltale_tyre
        AutomotiveIcon.BRAKE -> R.drawable.ic_telltale_brake
        AutomotiveIcon.WHEELSPIN -> R.drawable.ic_telltale_wheelspin
        AutomotiveIcon.FUEL -> R.drawable.ic_telltale_fuel
        AutomotiveIcon.PARK -> R.drawable.ic_telltale_park
        AutomotiveIcon.HIGH_BEAM -> R.drawable.ic_telltale_high_beam
        AutomotiveIcon.LOW_BEAM -> R.drawable.ic_telltale_low_beam
        AutomotiveIcon.ROOF_LIGHT -> R.drawable.ic_telltale_roof_light
        else -> null
    }

@Composable
fun AutomotiveIconView(
    icon: AutomotiveIcon,
    color: Color,
    modifier: Modifier = Modifier
) {
    if (icon == AutomotiveIcon.SCRAP) {
        ScrapCurrencyIcon(modifier)
        return
    }
    TelltaleGlyph(icon, color, modifier)
}

/**
 * Značka šrotu čitateľná aj v 16 dp: kusy plechu a matica. Pôvodná detailná
 * miniatúra motora sa v HUD-e zlievala a štýlom nesedela ku kontrolkám.
 */
@Composable
fun ScrapCurrencyIcon(modifier: Modifier = Modifier) {
    Canvas(modifier.semantics { contentDescription = "Scrap" }) {
        val s = min(size.width, size.height)
        val ox = (size.width - s) * 0.5f
        val oy = (size.height - s) * 0.5f
        fun p(x: Float, y: Float) = Offset(ox + s * x, oy + s * y)

        val outline = Color(0xFF182126)
        val steel = Color(0xFF9EAAAE)
        val steelLight = Color(0xFFC3CCCE)
        val rust = Color(0xFFB86E43)
        val edge = Stroke(
            width = (s * 0.075f).coerceAtLeast(1f),
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        )

        // Dva jednoduché odlomené plechy vytvoria siluetu kopy aj pri 16 dp.
        val backPlate = Path().apply {
            moveTo(p(0.16f, 0.30f).x, p(0.16f, 0.30f).y)
            lineTo(p(0.47f, 0.14f).x, p(0.47f, 0.14f).y)
            lineTo(p(0.82f, 0.31f).x, p(0.82f, 0.31f).y)
            lineTo(p(0.68f, 0.58f).x, p(0.68f, 0.58f).y)
            lineTo(p(0.29f, 0.56f).x, p(0.29f, 0.56f).y)
            close()
        }
        drawPath(
            backPlate,
            outline,
            style = Stroke(width = edge.width * 1.9f, cap = edge.cap, join = edge.join)
        )
        drawPath(backPlate, rust)

        val frontPlate = Path().apply {
            moveTo(p(0.12f, 0.50f).x, p(0.12f, 0.50f).y)
            lineTo(p(0.42f, 0.31f).x, p(0.42f, 0.31f).y)
            lineTo(p(0.88f, 0.55f).x, p(0.88f, 0.55f).y)
            lineTo(p(0.68f, 0.83f).x, p(0.68f, 0.83f).y)
            lineTo(p(0.27f, 0.78f).x, p(0.27f, 0.78f).y)
            close()
        }
        drawPath(
            frontPlate,
            outline,
            style = Stroke(width = edge.width * 1.9f, cap = edge.cap, join = edge.join)
        )
        drawPath(frontPlate, steel)
        drawLine(steelLight, p(0.24f, 0.51f), p(0.45f, 0.39f), edge.width * 0.72f, StrokeCap.Round)

        // Šesťhran s otvorom odlíši menu šrotu od motorovej kontrolky.
        val nut = Path().apply {
            moveTo(p(0.25f, 0.51f).x, p(0.25f, 0.51f).y)
            lineTo(p(0.42f, 0.42f).x, p(0.42f, 0.42f).y)
            lineTo(p(0.59f, 0.51f).x, p(0.59f, 0.51f).y)
            lineTo(p(0.59f, 0.69f).x, p(0.59f, 0.69f).y)
            lineTo(p(0.42f, 0.78f).x, p(0.42f, 0.78f).y)
            lineTo(p(0.25f, 0.69f).x, p(0.25f, 0.69f).y)
            close()
        }
        drawPath(
            nut,
            outline,
            style = Stroke(width = edge.width * 1.75f, cap = edge.cap, join = edge.join)
        )
        drawPath(nut, steelLight)
        drawCircle(outline, s * 0.075f, p(0.42f, 0.60f))

        drawCircle(rust, s * 0.045f, p(0.72f, 0.61f))
    }
}

@Composable
private fun TelltaleGlyph(icon: AutomotiveIcon, color: Color, modifier: Modifier) {
    val res = icon.telltaleRes
    if (res != null) {
        Icon(
            painter = painterResource(res),
            contentDescription = null,
            tint = color,
            modifier = modifier
        )
        return
    }
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val s = min(w, h)
        val sw = (s * 0.10f).coerceAtLeast(1.15f)
        when (icon) {
            AutomotiveIcon.SUN -> drawSun(color, w, h, sw)
            AutomotiveIcon.MOON -> drawMoon(color, w, h)
            AutomotiveIcon.SPRING -> drawSpring(color, w, h, sw)
            AutomotiveIcon.SNOW -> drawSnowflake(color, w, h, sw)
            else -> Unit
        }
    }
}

@Composable
fun AutomotiveIconToggleButton(
    icon: AutomotiveIcon,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    activeColor: Color = GameColors.accent,
    label: String? = null
) {
    val color = if (active) activeColor else GameColors.text
    Box(
        modifier
            .size(44.dp)
            .background(
                if (active) activeColor.copy(alpha = 0.25f) else GameColors.panelHigh.copy(alpha = 0.92f),
                RoundedCornerShape(10.dp)
            )
            .border(1.dp, if (active) activeColor else GameColors.outline, RoundedCornerShape(10.dp))
            .clickable(onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        AutomotiveIconView(icon, color, Modifier.size(24.dp))
    }
}

@Composable
fun HudActionToggleButton(
    icon: HudActionIcon,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null
) {
    val iconColor = if (active) Color(0xFFFFE9B6) else GameColors.text
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier
            .size(44.dp)
            .background(
                if (active) Color(0xFF4A3B1F) else GameColors.panelSoft.copy(alpha = 0.94f),
                shape
            )
            .border(1.dp, if (active) GameColors.accent else GameColors.outline, shape)
            .clickable(onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.size(22.dp)) {
            val w = size.width
            val h = size.height
            val stroke = min(w, h) * 0.12f
            when (icon) {
                HudActionIcon.PAUSE -> {
                    drawRoundRect(
                        iconColor, Offset(w * 0.25f, h * 0.18f), Size(w * 0.17f, h * 0.64f),
                        CornerRadius(stroke * 0.45f)
                    )
                    drawRoundRect(
                        iconColor, Offset(w * 0.58f, h * 0.18f), Size(w * 0.17f, h * 0.64f),
                        CornerRadius(stroke * 0.45f)
                    )
                }
                HudActionIcon.PLAY -> {
                    val path = Path().apply {
                        moveTo(w * 0.30f, h * 0.16f)
                        lineTo(w * 0.30f, h * 0.84f)
                        lineTo(w * 0.78f, h * 0.50f)
                        close()
                    }
                    drawPath(path, iconColor)
                }
                HudActionIcon.TIMER -> {
                    drawCircle(
                        iconColor, radius = w * 0.31f, center = Offset(w * 0.50f, h * 0.57f),
                        style = Stroke(stroke)
                    )
                    drawLine(
                        iconColor, Offset(w * 0.50f, h * 0.57f), Offset(w * 0.50f, h * 0.37f),
                        stroke, StrokeCap.Round
                    )
                    drawLine(
                        iconColor, Offset(w * 0.50f, h * 0.57f), Offset(w * 0.65f, h * 0.67f),
                        stroke, StrokeCap.Round
                    )
                    drawLine(
                        iconColor, Offset(w * 0.40f, h * 0.11f), Offset(w * 0.60f, h * 0.11f),
                        stroke, StrokeCap.Round
                    )
                    drawLine(
                        iconColor, Offset(w * 0.50f, h * 0.11f), Offset(w * 0.50f, h * 0.23f),
                        stroke, StrokeCap.Round
                    )
                }
                HudActionIcon.CLOSE -> {
                    drawLine(
                        iconColor, Offset(w * 0.18f, h * 0.18f), Offset(w * 0.82f, h * 0.82f),
                        stroke, StrokeCap.Round
                    )
                    drawLine(
                        iconColor, Offset(w * 0.82f, h * 0.18f), Offset(w * 0.18f, h * 0.82f),
                        stroke, StrokeCap.Round
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// ISO piktogramy – súradnice 0..1 v rámci Canvasu
// ---------------------------------------------------------------------------

private fun DrawScope.drawOilCan(color: Color, w: Float, h: Float, sw: Float, round: Stroke) {
    // Plná kanvica ako na kontrolke tlaku oleja: telo, hubica, kvapka; rukoväť obrys.
    val body = Path().apply {
        moveTo(w * 0.24f, h * 0.44f)
        lineTo(w * 0.52f, h * 0.44f)
        lineTo(w * 0.60f, h * 0.52f)
        lineTo(w * 0.56f, h * 0.80f)
        lineTo(w * 0.22f, h * 0.80f)
        lineTo(w * 0.18f, h * 0.54f)
        close()
    }
    drawPath(body, color)
    val spout = Path().apply {
        moveTo(w * 0.50f, h * 0.44f)
        lineTo(w * 0.78f, h * 0.20f)
        lineTo(w * 0.90f, h * 0.34f)
        lineTo(w * 0.62f, h * 0.54f)
        close()
    }
    drawPath(spout, color)
    val handle = Path().apply {
        moveTo(w * 0.22f, h * 0.52f)
        lineTo(w * 0.07f, h * 0.40f)
        lineTo(w * 0.12f, h * 0.20f)
        lineTo(w * 0.30f, h * 0.30f)
        lineTo(w * 0.28f, h * 0.48f)
    }
    drawPath(handle, color, style = round)
    drawRoundRect(
        color,
        Offset(w * 0.32f, h * 0.22f),
        Size(w * 0.22f, h * 0.12f),
        CornerRadius(sw * 0.45f)
    )
    drawLine(color, Offset(w * 0.43f, h * 0.34f), Offset(w * 0.43f, h * 0.44f), sw * 0.75f, StrokeCap.Round)
    val drop = Path().apply {
        moveTo(w * 0.86f, h * 0.50f)
        cubicTo(w * 0.76f, h * 0.66f, w * 0.78f, h * 0.84f, w * 0.86f, h * 0.86f)
        cubicTo(w * 0.94f, h * 0.84f, w * 0.96f, h * 0.66f, w * 0.86f, h * 0.50f)
        close()
    }
    drawPath(drop, color)
}

private fun DrawScope.drawCoolantTemp(color: Color, w: Float, h: Float, sw: Float, round: Stroke) {
    // Teplomer + tri vlny chladiacej kvapaliny (ISO teplota motora).
    drawRoundRect(
        color,
        Offset(w * 0.16f, h * 0.10f),
        Size(w * 0.22f, h * 0.56f),
        CornerRadius(w * 0.11f),
        style = round
    )
    drawCircle(color, radius = w * 0.145f, center = Offset(w * 0.27f, h * 0.78f))
    drawRoundRect(
        color,
        Offset(w * 0.225f, h * 0.36f),
        Size(w * 0.09f, h * 0.36f),
        CornerRadius(w * 0.04f)
    )
    drawLine(color, Offset(w * 0.12f, h * 0.22f), Offset(w * 0.18f, h * 0.22f), sw * 0.7f)
    drawLine(color, Offset(w * 0.12f, h * 0.34f), Offset(w * 0.18f, h * 0.34f), sw * 0.7f)
    drawLine(color, Offset(w * 0.12f, h * 0.46f), Offset(w * 0.18f, h * 0.46f), sw * 0.7f)
    for (i in 0..2) {
        val y = h * (0.38f + i * 0.16f)
        val wave = Path().apply {
            moveTo(w * 0.48f, y)
            quadraticBezierTo(w * 0.58f, y - h * 0.07f, w * 0.68f, y)
            quadraticBezierTo(w * 0.78f, y + h * 0.07f, w * 0.90f, y)
        }
        drawPath(wave, color, style = Stroke(sw * 0.85f, cap = StrokeCap.Round))
    }
}

private fun DrawScope.drawBattery(color: Color, w: Float, h: Float, sw: Float, miter: Stroke) {
    drawRoundRect(
        color,
        Offset(w * 0.10f, h * 0.34f),
        Size(w * 0.80f, h * 0.52f),
        CornerRadius(sw * 0.35f),
        style = miter
    )
    // Kladný pól je širší.
    drawRect(color, Offset(w * 0.20f, h * 0.18f), Size(w * 0.18f, h * 0.16f))
    drawRect(color, Offset(w * 0.64f, h * 0.22f), Size(w * 0.14f, h * 0.12f))
    drawLine(color, Offset(w * 0.22f, h * 0.60f), Offset(w * 0.40f, h * 0.60f), sw * 0.9f, StrokeCap.Square)
    drawLine(color, Offset(w * 0.31f, h * 0.50f), Offset(w * 0.31f, h * 0.70f), sw * 0.9f, StrokeCap.Square)
    drawLine(color, Offset(w * 0.62f, h * 0.60f), Offset(w * 0.80f, h * 0.60f), sw * 0.9f, StrokeCap.Square)
}

private fun DrawScope.drawEngineMil(color: Color, w: Float, h: Float, sw: Float) {
    // Bočný obrys motora: sanie, vzduchový filter, blok, výfuk, vaňa.
    val path = Path().apply {
        moveTo(w * 0.06f, h * 0.42f)
        lineTo(w * 0.22f, h * 0.42f)
        lineTo(w * 0.30f, h * 0.26f)
        lineTo(w * 0.38f, h * 0.26f)
        lineTo(w * 0.38f, h * 0.14f)
        lineTo(w * 0.64f, h * 0.14f)
        lineTo(w * 0.64f, h * 0.26f)
        lineTo(w * 0.70f, h * 0.26f)
        lineTo(w * 0.78f, h * 0.40f)
        lineTo(w * 0.94f, h * 0.40f)
        lineTo(w * 0.94f, h * 0.58f)
        lineTo(w * 0.78f, h * 0.58f)
        lineTo(w * 0.70f, h * 0.74f)
        lineTo(w * 0.28f, h * 0.74f)
        lineTo(w * 0.22f, h * 0.60f)
        lineTo(w * 0.06f, h * 0.60f)
        close()
    }
    drawPath(
        path,
        color,
        style = Stroke(sw * 1.05f, cap = StrokeCap.Square, join = StrokeJoin.Miter)
    )
}

private fun DrawScope.drawCoolantTank(color: Color, w: Float, h: Float, sw: Float, round: Stroke) {
    // Expanzná nádoba chladiacej kvapaliny – iná kontrolka ako teplomer.
    drawRoundRect(
        color,
        Offset(w * 0.34f, h * 0.08f),
        Size(w * 0.32f, h * 0.10f),
        CornerRadius(sw * 0.4f)
    )
    drawRect(color, Offset(w * 0.42f, h * 0.16f), Size(w * 0.16f, h * 0.10f))
    val tank = Path().apply {
        moveTo(w * 0.28f, h * 0.26f)
        lineTo(w * 0.72f, h * 0.26f)
        lineTo(w * 0.80f, h * 0.86f)
        lineTo(w * 0.20f, h * 0.86f)
        close()
    }
    drawPath(tank, color, style = round)
    for (i in 0..1) {
        val y = h * (0.48f + i * 0.16f)
        val wave = Path().apply {
            moveTo(w * 0.34f, y)
            quadraticBezierTo(w * 0.44f, y - h * 0.05f, w * 0.54f, y)
            quadraticBezierTo(w * 0.64f, y + h * 0.05f, w * 0.74f, y)
        }
        drawPath(wave, color, style = Stroke(sw * 0.8f, cap = StrokeCap.Round))
    }
}

private fun DrawScope.drawTpms(color: Color, w: Float, h: Float, sw: Float) {
    // Prierez pneumatiky (podkova) s výkričníkom – ISO / TPMS, nie koleso zhora.
    val c = Offset(w * 0.46f, h * 0.54f)
    val r = min(w, h) * 0.36f
    drawArc(
        color,
        startAngle = 48f,
        sweepAngle = 268f,
        useCenter = false,
        topLeft = Offset(c.x - r, c.y - r),
        size = Size(r * 2f, r * 2f),
        style = Stroke(sw * 1.85f, cap = StrokeCap.Round)
    )
    drawBang(color, Offset(w * 0.48f, h * 0.50f), min(w, h) * 0.42f, sw)
}

private fun DrawScope.drawBrakeWarn(color: Color, w: Float, h: Float, sw: Float) {
    // ISO porucha bŕzd: kruh, zátvorky, výkričník.
    val c = Offset(w * 0.50f, h * 0.50f)
    val r = min(w, h) * 0.40f
    val ring = Stroke(sw * 1.05f, cap = StrokeCap.Round)
    drawCircle(color, r, c, style = ring)
    drawArc(
        color, 70f, 140f, false,
        Offset(w * 0.08f, h * 0.18f), Size(w * 0.28f, h * 0.64f),
        style = Stroke(sw * 0.95f, cap = StrokeCap.Round)
    )
    drawArc(
        color, -70f, -140f, false,
        Offset(w * 0.64f, h * 0.18f), Size(w * 0.28f, h * 0.64f),
        style = Stroke(sw * 0.95f, cap = StrokeCap.Round)
    )
    drawBang(color, Offset(w * 0.50f, h * 0.48f), min(w, h) * 0.46f, sw)
}

private fun DrawScope.drawHeadlamp(color: Color, w: Float, h: Float, sw: Float, dipped: Boolean) {
    val lamp = Path().apply {
        moveTo(w * 0.50f, h * 0.12f)
        lineTo(w * 0.50f, h * 0.88f)
        cubicTo(w * 0.22f, h * 0.84f, w * 0.08f, h * 0.68f, w * 0.08f, h * 0.50f)
        cubicTo(w * 0.08f, h * 0.32f, w * 0.22f, h * 0.16f, w * 0.50f, h * 0.12f)
        close()
    }
    drawPath(lamp, color)
    val slope = if (dipped) 0.16f else 0f
    for (y in floatArrayOf(0.26f, 0.42f, 0.58f, 0.74f)) {
        drawLine(
            color,
            Offset(w * 0.62f, h * y),
            Offset(w * 0.94f, h * (y + slope)),
            sw * 0.82f,
            StrokeCap.Square
        )
    }
}

private fun DrawScope.drawRoofBar(color: Color, w: Float, h: Float, sw: Float) {
    drawLine(color, Offset(w * 0.08f, h * 0.78f), Offset(w * 0.92f, h * 0.78f), sw, StrokeCap.Round)
    drawRoundRect(
        color,
        Offset(w * 0.18f, h * 0.28f),
        Size(w * 0.44f, h * 0.38f),
        CornerRadius(sw * 0.8f)
    )
    for (y in floatArrayOf(0.34f, 0.47f, 0.60f)) {
        drawLine(
            color,
            Offset(w * 0.68f, h * y),
            Offset(w * 0.92f, h * y),
            sw * 0.8f,
            StrokeCap.Round
        )
    }
}

private fun DrawScope.drawScrap(color: Color, w: Float, h: Float, sw: Float, round: Stroke) {
    val plates = arrayOf(
        Rect(w * 0.14f, h * 0.14f, w * 0.78f, h * 0.38f),
        Rect(w * 0.22f, h * 0.40f, w * 0.88f, h * 0.64f),
        Rect(w * 0.10f, h * 0.66f, w * 0.74f, h * 0.90f)
    )
    plates.forEach { r ->
        drawRoundRect(
            color,
            Offset(r.left, r.top),
            Size(r.width, r.height),
            CornerRadius(sw * 0.35f),
            style = round
        )
    }
    drawCircle(color, sw * 0.85f, Offset(w * 0.30f, h * 0.26f), style = Stroke(sw * 0.7f))
    drawCircle(color, sw * 0.85f, Offset(w * 0.70f, h * 0.52f), style = Stroke(sw * 0.7f))
    drawCircle(color, sw * 0.85f, Offset(w * 0.28f, h * 0.78f), style = Stroke(sw * 0.7f))
}

private fun DrawScope.drawFuelPump(color: Color, w: Float, h: Float, sw: Float, round: Stroke) {
    drawRoundRect(
        color,
        Offset(w * 0.12f, h * 0.22f),
        Size(w * 0.48f, h * 0.64f),
        CornerRadius(sw * 0.4f),
        style = round
    )
    drawRect(color, Offset(w * 0.08f, h * 0.82f), Size(w * 0.56f, h * 0.08f))
    drawRoundRect(
        color,
        Offset(w * 0.20f, h * 0.34f),
        Size(w * 0.32f, h * 0.22f),
        CornerRadius(sw * 0.3f)
    )
    val hose = Path().apply {
        moveTo(w * 0.60f, h * 0.28f)
        quadraticBezierTo(w * 0.82f, h * 0.22f, w * 0.84f, h * 0.48f)
        lineTo(w * 0.84f, h * 0.62f)
    }
    drawPath(hose, color, style = Stroke(sw * 0.95f, cap = StrokeCap.Round))
    drawRoundRect(
        color,
        Offset(w * 0.76f, h * 0.60f),
        Size(w * 0.16f, h * 0.22f),
        CornerRadius(sw * 0.35f)
    )
}

private fun DrawScope.drawParkBrake(color: Color, w: Float, h: Float, sw: Float) {
    val c = Offset(w * 0.50f, h * 0.50f)
    val r = min(w, h) * 0.28f
    drawCircle(color, r, c, style = Stroke(sw, cap = StrokeCap.Round))
    drawArc(
        color, 108f, 144f, false,
        Offset(w * 0.02f, h * 0.10f), Size(w * 0.42f, h * 0.80f),
        style = Stroke(sw, cap = StrokeCap.Round)
    )
    drawArc(
        color, -72f, 144f, false,
        Offset(w * 0.56f, h * 0.10f), Size(w * 0.42f, h * 0.80f),
        style = Stroke(sw, cap = StrokeCap.Round)
    )
    val stemX = w * 0.44f
    drawLine(
        color,
        Offset(stemX, h * 0.34f),
        Offset(stemX, h * 0.68f),
        sw * 1.05f,
        StrokeCap.Round
    )
    drawArc(
        color, -90f, 180f, false,
        Offset(stemX - sw * 0.15f, h * 0.34f),
        Size(w * 0.18f, h * 0.20f),
        style = Stroke(sw * 1.05f, cap = StrokeCap.Round)
    )
}

private fun DrawScope.drawSun(color: Color, w: Float, h: Float, sw: Float) {
    val c = Offset(w * 0.50f, h * 0.50f)
    drawCircle(color, min(w, h) * 0.22f, c)
    for (i in 0 until 8) {
        val a = Math.toRadians(i * 45.0).toFloat()
        val inner = min(w, h) * 0.32f
        val outer = min(w, h) * 0.46f
        drawLine(
            color,
            Offset(c.x + cos(a) * inner, c.y + sin(a) * inner),
            Offset(c.x + cos(a) * outer, c.y + sin(a) * outer),
            sw * 0.85f,
            StrokeCap.Round
        )
    }
}

private fun DrawScope.drawMoon(color: Color, w: Float, h: Float) {
    val path = Path().apply {
        fillType = PathFillType.EvenOdd
        addOval(Rect(w * 0.22f, h * 0.16f, w * 0.82f, h * 0.84f))
        addOval(Rect(w * 0.38f, h * 0.12f, w * 0.92f, h * 0.72f))
    }
    drawPath(path, color, style = Fill)
}

private fun DrawScope.drawSpring(color: Color, w: Float, h: Float, sw: Float) {
    drawLine(color, Offset(w * 0.22f, h * 0.14f), Offset(w * 0.78f, h * 0.14f), sw, StrokeCap.Round)
    drawLine(color, Offset(w * 0.22f, h * 0.86f), Offset(w * 0.78f, h * 0.86f), sw, StrokeCap.Round)
    val coil = Path().apply {
        moveTo(w * 0.50f, h * 0.14f)
        lineTo(w * 0.28f, h * 0.26f)
        lineTo(w * 0.72f, h * 0.38f)
        lineTo(w * 0.28f, h * 0.50f)
        lineTo(w * 0.72f, h * 0.62f)
        lineTo(w * 0.28f, h * 0.74f)
        lineTo(w * 0.50f, h * 0.86f)
    }
    drawPath(coil, color, style = Stroke(sw, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

private fun DrawScope.drawSnowflake(color: Color, w: Float, h: Float, sw: Float) {
    val c = Offset(w * 0.50f, h * 0.50f)
    val arm = min(w, h) * 0.40f
    for (i in 0 until 6) {
        val a = Math.toRadians(i * 60.0).toFloat()
        val ex = c.x + cos(a) * arm
        val ey = c.y + sin(a) * arm
        drawLine(color, c, Offset(ex, ey), sw * 0.85f, StrokeCap.Round)
        val b = a + 0.55f
        val d = a - 0.55f
        val mid = 0.58f
        val bx = c.x + cos(a) * arm * mid
        val by = c.y + sin(a) * arm * mid
        val barb = arm * 0.28f
        drawLine(
            color, Offset(bx, by),
            Offset(bx + cos(b) * barb, by + sin(b) * barb),
            sw * 0.7f, StrokeCap.Round
        )
        drawLine(
            color, Offset(bx, by),
            Offset(bx + cos(d) * barb, by + sin(d) * barb),
            sw * 0.7f, StrokeCap.Round
        )
    }
}

private fun DrawScope.drawBang(color: Color, center: Offset, scale: Float, sw: Float) {
    val stemW = (sw * 0.95f).coerceAtLeast(1.2f)
    val stemH = scale * 0.42f
    drawRoundRect(
        color,
        Offset(center.x - stemW / 2f, center.y - stemH * 0.72f),
        Size(stemW, stemH * 0.58f),
        CornerRadius(stemW / 2f)
    )
    drawCircle(color, radius = stemW * 0.62f, center = Offset(center.x, center.y + stemH * 0.42f))
}
