package sk.kubis.endlessdrive.ui.game

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import sk.kubis.endlessdrive.domain.model.GaugeStyle
import sk.kubis.endlessdrive.ui.theme.GameColors
import sk.kubis.endlessdrive.ui.theme.Space
import sk.kubis.endlessdrive.ui.theme.Type
import kotlin.math.cos
import kotlin.math.sin

/**
 * Bez podkladového bloku môže text ležať na svetlej oblohe aj na tmavej hline.
 * Tieň ho udrží čitateľný na oboch, a pritom nezakrýva kulisu.
 */
internal val OnSceneText = TextStyle(
    shadow = Shadow(color = Color(0xCC000000), offset = Offset(0f, 1.5f), blurRadius = 5f)
)

internal data class GaugeSpec(
    val label: String,
    val ratio: Float,
    val value: String,
    val redFrom: Float,
    val redTo: Float = 1f,
    val accent: Color = GameColors.accent,
    val mixRatio: Float? = null,
    val mixBaseColor: Color = GameColors.ok,
    val mixColor: Color = GameColors.info,
    val mixLabel: String? = null,
    val primary: Boolean = false
)

@Composable
internal fun GaugeTrio(
    temp: GaugeSpec,
    speed: GaugeSpec,
    fuel: GaugeSpec,
    style: GaugeStyle
) {
    when (style) {
        GaugeStyle.CLASSIC -> ClassicTrio(temp, speed, fuel)
        GaugeStyle.STRIP -> StripTrio(temp, speed, fuel)
    }
}

/** Statický náhľad používa ten istý renderer budíkov ako samotná hra. */
@Composable
fun GaugeStylePreview(style: GaugeStyle, modifier: Modifier = Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        GaugeTrio(
            temp = GaugeSpec("TEMP", 0.48f, "82°", redFrom = 0.78f),
            speed = GaugeSpec("KM/H", 0.57f, "103", redFrom = 0.88f, primary = true),
            fuel = GaugeSpec("FUEL", 0.68f, "28 L", redFrom = 0f, redTo = 0.15f),
            style = style
        )
    }
}

@Composable
private fun ClassicTrio(temp: GaugeSpec, speed: GaugeSpec, fuel: GaugeSpec) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(Space.m),
        verticalAlignment = Alignment.Bottom
    ) {
        ClassicDial(temp, diameter = 54.dp)
        ClassicDial(speed, diameter = 74.dp, valueSize = Type.title)
        ClassicDial(fuel, diameter = 54.dp)
    }
}

@Composable
private fun StripTrio(temp: GaugeSpec, speed: GaugeSpec, fuel: GaugeSpec) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        StripBar(speed, width = 168.dp)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StripBar(temp, width = 80.dp)
            StripBar(fuel, width = 80.dp)
        }
    }
}

@Composable
private fun ClassicDial(
    spec: GaugeSpec,
    diameter: Dp,
    valueSize: TextUnit = Type.body
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(diameter), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(diameter)) {
                val stroke = size.minDimension * 0.09f
                val r = (size.minDimension - stroke) / 2f
                val c = Offset(size.width / 2f, size.height / 2f)
                val topLeft = Offset(c.x - r, c.y - r)
                val arcSize = Size(r * 2f, r * 2f)
                val start = 150f
                val sweep = 240f

                drawArc(
                    color = Color(0xFF2A241D),
                    startAngle = start,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round)
                )
                val redStart = start + sweep * spec.redFrom.coerceIn(0f, 1f)
                val redSweep = sweep * (spec.redTo - spec.redFrom).coerceIn(0f, 1f)
                if (redSweep > 0.5f) {
                    drawArc(
                        color = GameColors.danger.copy(alpha = 0.55f),
                        startAngle = redStart,
                        sweepAngle = redSweep,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = stroke, cap = StrokeCap.Butt)
                    )
                }
                val t = spec.ratio.coerceIn(0f, 1f)
                val inRed = t >= spec.redFrom && t <= spec.redTo
                drawArc(
                    color = if (inRed) GameColors.danger else spec.accent,
                    startAngle = start,
                    sweepAngle = sweep * t,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round)
                )
                val a = Math.toRadians((start + sweep * t).toDouble())
                drawLine(
                    color = GameColors.text,
                    start = c,
                    end = Offset(
                        c.x + (r * 0.82f) * cos(a).toFloat(),
                        c.y + (r * 0.82f) * sin(a).toFloat()
                    ),
                    strokeWidth = stroke * 0.42f,
                    cap = StrokeCap.Round
                )
                drawCircle(GameColors.text, radius = stroke * 0.42f, center = c)
            }
            Text(
                spec.value,
                color = GameColors.text,
                fontSize = valueSize,
                fontWeight = FontWeight.Bold,
                style = OnSceneText,
                modifier = Modifier.offset(y = diameter * 0.24f)
            )
        }
        GaugeCaption(spec, diameter * 0.78f)
    }
}

@Composable
private fun StripBar(spec: GaugeSpec, width: Dp) {
    val t = spec.ratio.coerceIn(0f, 1f)
    val inRed = t >= spec.redFrom && t <= spec.redTo
    val fill = if (inRed) GameColors.danger else spec.accent
    Column(Modifier.width(width)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            Text(
                spec.label,
                color = GameColors.text.copy(alpha = 0.8f),
                fontSize = Type.micro,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.7.sp,
                style = OnSceneText
            )
            Text(
                spec.value,
                color = GameColors.text,
                fontSize = if (spec.primary) Type.title else Type.value,
                fontWeight = FontWeight.Bold,
                style = OnSceneText
            )
        }
        Spacer(Modifier.height(3.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(if (spec.primary) 10.dp else 7.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Color(0xFF1C1814))
        ) {
            val redFrom = spec.redFrom.coerceIn(0f, 1f)
            val redTo = spec.redTo.coerceIn(0f, 1f)
            if (redTo > redFrom + 0.01f) {
                Box(
                    Modifier
                        .align(Alignment.CenterStart)
                        .fillMaxHeight()
                        .fillMaxWidth(redTo)
                        .padding(start = width * redFrom)
                        .background(GameColors.danger.copy(alpha = 0.35f))
                )
            }
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(t)
                    .background(fill)
            )
        }
        MixStrip(spec, width)
    }
}

@Composable
private fun GaugeCaption(spec: GaugeSpec, mixWidth: Dp) {
    Text(
        spec.label,
        color = GameColors.text.copy(alpha = 0.85f),
        fontSize = Type.micro,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.8.sp,
        style = OnSceneText
    )
    MixStrip(spec, mixWidth)
}

@Composable
private fun MixStrip(spec: GaugeSpec, width: Dp) {
    if (spec.mixRatio == null || spec.mixLabel == null) return
    val second = spec.mixRatio.coerceIn(0f, 1f)
    Row(
        Modifier
            .width(width)
            .height(3.dp)
            .clip(RoundedCornerShape(2.dp))
    ) {
        Box(
            Modifier
                .weight((1f - second).coerceAtLeast(0.001f))
                .fillMaxHeight()
                .background(spec.mixBaseColor)
        )
        Box(
            Modifier
                .weight(second.coerceAtLeast(0.001f))
                .fillMaxHeight()
                .background(spec.mixColor)
        )
    }
    Text(
        spec.mixLabel,
        color = GameColors.text.copy(alpha = 0.82f),
        fontSize = 8.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.2.sp,
        style = OnSceneText
    )
}
