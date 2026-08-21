package sk.kubis.endlessdrive.ui.menu

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import sk.kubis.endlessdrive.domain.repository.PlayerProfile
import sk.kubis.endlessdrive.ui.theme.BtnStyle
import sk.kubis.endlessdrive.ui.theme.GameButton
import sk.kubis.endlessdrive.ui.theme.GameColors

@Composable
fun MenuScreen(
    profile: PlayerProfile,
    canContinue: Boolean,
    runDistanceKm: Float,
    runClock: String,
    onContinue: () -> Unit,
    onNewRun: () -> Unit,
    onSettings: () -> Unit,
    debugActive: Boolean = false
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF090C0E))
    ) {
        val narrow = maxWidth < 620.dp
        val compact = maxWidth < 850.dp || maxHeight < 470.dp

        CinematicRoadBackground()

        Box(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
        ) {
            MenuBrand(
                compact = compact,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(
                        start = if (compact) 22.dp else 52.dp,
                        top = when {
                            narrow && canContinue -> 72.dp
                            compact -> 24.dp
                            else -> 42.dp
                        }
                    )
            )

            if (canContinue) {
                RunChip(
                    distanceKm = runDistanceKm,
                    clock = runClock,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(
                            end = if (compact) 18.dp else 34.dp,
                            top = if (compact) 16.dp else 28.dp
                        )
                )
            }

            MenuActions(
                canContinue = canContinue,
                compact = compact,
                debugActive = debugActive,
                onContinue = onContinue,
                onNewRun = onNewRun,
                onSettings = onSettings,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(
                        start = if (compact) 18.dp else 52.dp,
                        end = 18.dp,
                        bottom = if (compact) 18.dp else 38.dp
                    )
            )

            if (!compact) {
                ProfileStats(
                    profile = profile,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 34.dp, bottom = 28.dp)
                )
            }
        }
    }
}

@Composable
private fun MenuBrand(compact: Boolean, modifier: Modifier = Modifier) {
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .width(28.dp)
                    .height(2.dp)
                    .background(GameColors.accent, RoundedCornerShape(1.dp))
            )
            Spacer(Modifier.width(9.dp))
            Text(
                "ROAD SURVIVAL",
                color = GameColors.accent,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.8.sp
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "ENDLESS\nDRIVE",
            color = GameColors.text,
            fontSize = if (compact) 39.sp else 55.sp,
            lineHeight = if (compact) 34.sp else 47.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.5.sp
        )
        Spacer(Modifier.height(12.dp))
        Box(
            Modifier
                .width(if (compact) 42.dp else 56.dp)
                .height(3.dp)
                .background(GameColors.accent, RoundedCornerShape(2.dp))
        )
    }
}

@Composable
private fun RunChip(distanceKm: Float, clock: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .background(Color(0xD91A1713), RoundedCornerShape(9.dp))
            .border(1.dp, GameColors.outline.copy(alpha = 0.8f), RoundedCornerShape(9.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(7.dp).background(GameColors.ok, RoundedCornerShape(4.dp)))
        Spacer(Modifier.width(8.dp))
        Text(
            String.format("%.1f km", distanceKm),
            color = GameColors.text,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.width(9.dp))
        Text(clock, color = GameColors.textDim, fontSize = 11.sp)
    }
}

@Composable
private fun MenuActions(
    canContinue: Boolean,
    compact: Boolean,
    debugActive: Boolean,
    onContinue: () -> Unit,
    onNewRun: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier.widthIn(max = if (compact) 390.dp else 620.dp)) {
        if (debugActive) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(7.dp).background(GameColors.warn, RoundedCornerShape(4.dp)))
                Spacer(Modifier.width(7.dp))
                Text(
                    "TESTING OPTIONS ACTIVE",
                    color = GameColors.warn,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.7.sp
                )
            }
            Spacer(Modifier.height(8.dp))
        }

        if (compact) {
            Column(Modifier.fillMaxWidth()) {
                GameButton(
                    text = if (canContinue) "CONTINUE" else "START RUN",
                    onClick = if (canContinue) onContinue else onNewRun,
                    style = BtnStyle.Primary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (canContinue) {
                        GameButton(
                            text = "NEW RUN",
                            onClick = onNewRun,
                            style = BtnStyle.Secondary,
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                        )
                    }
                    GameButton(
                        text = "SETTINGS",
                        onClick = onSettings,
                        style = BtnStyle.Ghost,
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp)
                    )
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                GameButton(
                    text = if (canContinue) "CONTINUE" else "START RUN",
                    onClick = if (canContinue) onContinue else onNewRun,
                    style = BtnStyle.Primary,
                    modifier = Modifier
                        .width(184.dp)
                        .height(54.dp)
                )
                if (canContinue) {
                    GameButton(
                        text = "NEW RUN",
                        onClick = onNewRun,
                        style = BtnStyle.Secondary,
                        modifier = Modifier
                            .width(142.dp)
                            .height(48.dp)
                    )
                }
                GameButton(
                    text = "SETTINGS",
                    onClick = onSettings,
                    style = BtnStyle.Ghost,
                    modifier = Modifier
                        .width(142.dp)
                        .height(48.dp)
                )
            }
        }
    }
}

@Composable
private fun ProfileStats(profile: PlayerProfile, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .background(Color(0x99110F0D), RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        ProfileStat("BEST", String.format("%.1f KM", profile.bestDistanceKm))
        ProfileStat("RUNS", profile.totalRuns.toString())
        ProfileStat("TOTAL", String.format("%.1f KM", profile.totalDistanceKm))
    }
}

@Composable
private fun ProfileStat(label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            "$label  ",
            color = GameColors.textDim,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.7.sp
        )
        Text(value, color = GameColors.text, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun CinematicRoadBackground() {
    // Posun o jednu medzeru je cyklický: na konci má cesta presne rovnaké
    // rozloženie značiek ako na začiatku, takže animácia nikdy neskočí.
    val roadPhase = rememberInfiniteTransition(label = "menu road motion").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 780, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "road travelling toward camera"
    ).value

    Box(Modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val horizon = h * 0.59f

            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF111E2A),
                        Color(0xFF273741),
                        Color(0xFFB06F43),
                        Color(0xFF20271F)
                    ),
                    startY = 0f,
                    endY = horizon + h * 0.12f
                )
            )

            val sun = Offset(w * 0.27f, horizon - h * 0.19f)
            drawCircle(Color(0x22FFD895), radius = h * 0.15f, center = sun)
            drawCircle(Color(0x44FFD895), radius = h * 0.075f, center = sun)
            drawCircle(Color(0xCCF4C980), radius = h * 0.038f, center = sun)

            val farHills = Path().apply {
                moveTo(0f, horizon)
                lineTo(0f, horizon - h * 0.08f)
                lineTo(w * 0.10f, horizon - h * 0.17f)
                lineTo(w * 0.19f, horizon - h * 0.10f)
                lineTo(w * 0.31f, horizon - h * 0.24f)
                lineTo(w * 0.43f, horizon - h * 0.09f)
                lineTo(w * 0.56f, horizon - h * 0.19f)
                lineTo(w * 0.70f, horizon - h * 0.07f)
                lineTo(w * 0.84f, horizon - h * 0.20f)
                lineTo(w, horizon - h * 0.10f)
                lineTo(w, horizon)
                close()
            }
            drawPath(farHills, Color(0xAA39483E))

            val nearHills = Path().apply {
                moveTo(0f, horizon + h * 0.10f)
                lineTo(0f, horizon - h * 0.01f)
                lineTo(w * 0.12f, horizon - h * 0.13f)
                lineTo(w * 0.25f, horizon - h * 0.03f)
                lineTo(w * 0.40f, horizon - h * 0.16f)
                lineTo(w * 0.54f, horizon - h * 0.02f)
                lineTo(w * 0.69f, horizon - h * 0.12f)
                lineTo(w * 0.82f, horizon - h * 0.01f)
                lineTo(w, horizon - h * 0.15f)
                lineTo(w, horizon + h * 0.10f)
                close()
            }
            drawPath(nearHills, Color(0xFF1C2923))

            // Predná krajina má v strede mäkký vrchol kopca. Cesta sa na ňom
            // zúži do bodu a za hranou už nepokračuje viditeľným asfaltom.
            val roadCrestY = horizon + h * 0.018f
            val foregroundTerrain = Path().apply {
                moveTo(0f, horizon + h * 0.065f)
                cubicTo(
                    w * 0.22f, horizon + h * 0.055f,
                    w * 0.40f, horizon + h * 0.025f,
                    w * 0.52f, roadCrestY
                )
                cubicTo(
                    w * 0.64f, horizon + h * 0.024f,
                    w * 0.80f, horizon + h * 0.060f,
                    w, horizon + h * 0.072f
                )
                lineTo(w, h)
                lineTo(0f, h)
                close()
            }
            drawPath(
                foregroundTerrain,
                brush = Brush.verticalGradient(
                    listOf(Color(0xFF1A211C), Color(0xFF0C110F)),
                    startY = roadCrestY,
                    endY = h
                )
            )

            val shoulder = Path().apply {
                moveTo(w * 0.517f, roadCrestY)
                lineTo(w * 0.523f, roadCrestY)
                lineTo(w * 0.94f, h)
                lineTo(w * 0.06f, h)
                close()
            }
            drawPath(
                shoulder,
                brush = Brush.verticalGradient(
                    listOf(Color(0xFF5B4A36), Color(0xFF2C261E)),
                    startY = roadCrestY,
                    endY = h
                )
            )

            val road = Path().apply {
                moveTo(w * 0.519f, roadCrestY)
                lineTo(w * 0.521f, roadCrestY)
                lineTo(w * 0.88f, h)
                lineTo(w * 0.14f, h)
                close()
            }
            drawPath(
                road,
                brush = Brush.verticalGradient(
                    listOf(Color(0xFF323232), Color(0xFF151616)),
                    startY = roadCrestY,
                    endY = h
                )
            )

            drawLine(
                Color(0xB8D2AE63),
                Offset(w * 0.519f, roadCrestY),
                Offset(w * 0.14f, h),
                strokeWidth = 2f
            )
            drawLine(
                Color(0xB8D2AE63),
                Offset(w * 0.521f, roadCrestY),
                Offset(w * 0.88f, h),
                strokeWidth = 2f
            )

            // Perspektívne značky zrýchľujú a rastú smerom k divákovi. Posun
            // roadPhase ich vedie od horizontu po spodný okraj bez pohybu
            // samotného kopca alebo horizontu.
            for (i in 0 until 9) {
                val t0 = ((i + roadPhase) / 9f) % 1f
                val t1 = (t0 + (0.045f + 0.018f * t0)).coerceAtMost(0.998f)
                val p0 = t0 * t0
                val p1 = t1 * t1
                val x0 = w * (0.52f + 0.015f * p0)
                val x1 = w * (0.52f + 0.015f * p1)
                drawLine(
                    color = Color(0xC8E0D4AE),
                    start = Offset(x0, roadCrestY + (h - roadCrestY) * p0),
                    end = Offset(x1, roadCrestY + (h - roadCrestY) * p1),
                    strokeWidth = 1.2f + 7f * p1
                )
            }

            // Drobné odrazky na okrajoch dávajú pohybu čitateľnosť aj tam,
            // kde stredovú čiaru prekrýva spodné menu.
            for (i in 0 until 12) {
                val t = ((i + roadPhase) / 12f) % 1f
                val p = t * t
                val y = roadCrestY + (h - roadCrestY) * p
                val leftX = w * (0.519f + (0.14f - 0.519f) * p)
                val rightX = w * (0.521f + (0.88f - 0.521f) * p)
                val radius = 0.7f + 3.2f * p
                val reflector = Color(0xB8D2AE63).copy(alpha = 0.28f + 0.52f * p)
                drawCircle(reflector, radius, Offset(leftX, y))
                drawCircle(reflector, radius, Offset(rightX, y))
            }

            drawPine(w * 0.035f, h * 0.84f, h * 0.45f, Color(0xFF09100E))
            drawPine(w * 0.12f, h * 0.80f, h * 0.30f, Color(0xFF0B1411))
            drawPine(w * 0.95f, h * 0.89f, h * 0.53f, Color(0xFF080F0D))
            drawPine(w * 0.86f, h * 0.81f, h * 0.29f, Color(0xFF0B1411))
        }

        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        0f to Color(0xB80A0B0A),
                        0.38f to Color(0x360A0B0A),
                        0.72f to Color.Transparent,
                        1f to Color(0x3D080908)
                    )
                )
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color(0x26000000),
                        0.58f to Color.Transparent,
                        1f to Color(0xA8000000)
                    )
                )
        )
    }
}

private fun DrawScope.drawPine(x: Float, baseY: Float, height: Float, color: Color) {
    val trunkWidth = height * 0.055f
    drawRect(
        color = color,
        topLeft = Offset(x - trunkWidth / 2f, baseY - height * 0.25f),
        size = Size(trunkWidth, height * 0.25f)
    )

    val crown = Path().apply {
        moveTo(x, baseY - height)
        lineTo(x - height * 0.17f, baseY - height * 0.58f)
        lineTo(x - height * 0.08f, baseY - height * 0.58f)
        lineTo(x - height * 0.24f, baseY - height * 0.28f)
        lineTo(x - height * 0.11f, baseY - height * 0.28f)
        lineTo(x - height * 0.30f, baseY - height * 0.02f)
        lineTo(x + height * 0.30f, baseY - height * 0.02f)
        lineTo(x + height * 0.11f, baseY - height * 0.28f)
        lineTo(x + height * 0.24f, baseY - height * 0.28f)
        lineTo(x + height * 0.08f, baseY - height * 0.58f)
        lineTo(x + height * 0.17f, baseY - height * 0.58f)
        close()
    }
    drawPath(crown, color)
}
