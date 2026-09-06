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
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import sk.kubis.endlessdrive.ui.theme.GamePanel
import sk.kubis.endlessdrive.ui.theme.Scrim
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import sk.kubis.endlessdrive.core.MathX
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
    var confirmNewRun by remember { mutableStateOf(false) }
    BoxWithConstraints(Modifier.fillMaxSize().background(GameColors.panelSoft)) {
        val compact = maxWidth < 850.dp || maxHeight < 470.dp
        val cardWidth = (maxWidth * 0.46f).coerceIn(220.dp, 370.dp)
        CinematicRoadBackground()
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(
            listOf(Color(0xA610171B), Color.Transparent, Color(0xB310171B)))))
        Row(
            Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(if (compact) 20.dp else 40.dp),
            horizontalArrangement = Arrangement.spacedBy(if (compact) 24.dp else 48.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                MenuBrand(compact)
                Spacer(Modifier.height(if (compact) 14.dp else 24.dp))
                Text("FUEL · SCRAP · TYRES · PARTS", color = GameColors.text,
                    fontSize = if (compact) 12.sp else 16.sp, fontWeight = FontWeight.Medium,
                    letterSpacing = 1.sp)
                Text("Repair with scrap. Fill fuel, oil and coolant.", color = GameColors.textDim,
                    fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                Spacer(Modifier.height(if (compact) 20.dp else 40.dp))
                if (compact) Text(String.format("PERSONAL BEST  ·  %.1f KM", profile.bestDistanceKm),
                    color = GameColors.accent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                else ProfileStats(profile)
            }
            Column(
                Modifier.width(cardWidth)
                    .verticalScroll(rememberScrollState())
                    .background(GameColors.hudBg, RoundedCornerShape(20.dp))
                    .border(1.dp, GameColors.outline, RoundedCornerShape(20.dp))
                    .padding(if (compact) 18.dp else 26.dp)
            ) {
                Text(if (canContinue) "SAVED RUN" else "NEW RUN",
                    color = GameColors.accent, fontSize = 11.sp,
                    letterSpacing = 1.4.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(10.dp))
                Text(if (canContinue) "Continue from last stop." else "Fit parts. Fill fluids.",
                    color = GameColors.text, fontSize = if (compact) 19.sp else 24.sp,
                    fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                if (canContinue) RunChip(runDistanceKm, runClock)
                else Text("Search the garage for missing parts, fuel, oil and coolant. Then start the engine.",
                    color = GameColors.textDim, fontSize = 12.sp)
                Spacer(Modifier.height(18.dp))
                MenuActions(canContinue, compact = true, debugActive, onContinue,
                    onNewRun = { if (canContinue) confirmNewRun = true else onNewRun() },
                    onSettings = onSettings)
            }
        }
        if (confirmNewRun) {
            Scrim(onDismiss = { confirmNewRun = false })
            GamePanel("REPLACE THIS RUN?", Modifier.align(Alignment.Center).widthIn(max = 430.dp)
                .padding(20.dp), fillHeight = false, onClose = { confirmNewRun = false }) {
                Text("Your current car and supplies will be lost. Your personal best stays saved.",
                    color = GameColors.textDim, fontSize = 14.sp)
                Spacer(Modifier.height(18.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GameButton("KEEP RUN", { confirmNewRun = false }, Modifier.weight(1f))
                    GameButton("REPLACE RUN", { confirmNewRun = false; onNewRun() },
                        Modifier.weight(1f), style = BtnStyle.Danger)
                }
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
                "OPEN ROAD",
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
    val materials = remember { sk.kubis.endlessdrive.ui.game.MaterialPainter() }
    // Posun o jednu medzeru je cyklický: na konci má cesta presne rovnaké
    // rozloženie značiek ako na začiatku, takže animácia nikdy neskočí.
    val roadPhase = rememberInfiniteTransition(label = "menu road motion").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2400, easing = LinearEasing),
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

            materials.quad(this, sk.kubis.endlessdrive.ui.game.MaterialKind.ASPHALT, 0.90f,
                w * 0.519f, roadCrestY, w * 0.521f, roadCrestY,
                w * 0.88f, h, w * 0.14f, h,
                0f, 12f, -roadPhase * 5f, 60f - roadPhase * 5f)

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

            drawMenuRoadMarkings(w, h, roadCrestY, roadPhase)
            drawMenuRoadAtmosphere(w, h, roadCrestY, roadPhase)

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

/** Stredová čiara a patníky – tenšie, s fade na okrajoch slučky. */
private fun DrawScope.drawMenuRoadMarkings(w: Float, h: Float, crestY: Float, phase: Float) {
    for (i in 0 until 12) {
        val t0 = ((i + phase) / 12f) % 1f
        val fade = MenuRoadFx.travelFade(t0)
        if (fade < 0.04f) continue
        val t1 = (t0 + (0.026f + 0.010f * t0)).coerceAtMost(0.998f)
        val p0 = MenuRoadFx.perspective(t0)
        val p1 = MenuRoadFx.perspective(t1)
        drawLine(
            color = Color(0xC8E0D4AE).copy(alpha = 0.55f * fade + 0.25f * p1),
            start = Offset(MenuRoadFx.centerX(w, p0), MenuRoadFx.roadY(crestY, h, p0)),
            end = Offset(MenuRoadFx.centerX(w, p1), MenuRoadFx.roadY(crestY, h, p1)),
            strokeWidth = MenuRoadFx.dashStroke(p1),
            cap = StrokeCap.Round
        )
    }
    for (i in 0 until 14) {
        val t = ((i + phase) / 14f) % 1f
        val fade = MenuRoadFx.travelFade(t)
        if (fade < 0.05f) continue
        val p = MenuRoadFx.perspective(t)
        val y = MenuRoadFx.roadY(crestY, h, p)
        val postH = 2.0f + 12f * p
        val postW = (0.65f + 1.7f * p).coerceAtLeast(0.8f)
        val cap = Color(0xE0D2AE63).copy(alpha = fade * (0.30f + 0.50f * p))
        val stem = Color(0xFFD9D3C4).copy(alpha = fade * (0.22f + 0.40f * p))
        for (lane in floatArrayOf(-1.02f, 1.02f)) {
            val x = MenuRoadFx.laneX(w, p, lane)
            drawLine(stem, Offset(x, y), Offset(x, y - postH), postW, StrokeCap.Round)
            drawCircle(cap, postW * 0.55f, Offset(x, y - postH + postH * 0.20f))
        }
    }
}

/**
 * Prach, dym a drobný grit sedia na asfalte (alebo tesne nad ním)
 * a s perspektivou prichádzajú ku kamere. Žiadne HUD gule na úbežníku.
 */
private fun DrawScope.drawMenuRoadAtmosphere(w: Float, h: Float, crestY: Float, phase: Float) {
    val dust = Color(0xFFB8A888)
    val exhaust = Color(0xFF9AA19A)
    val grit = Color(0xFF6B5A44)
    for (i in 0 until 20) {
        val t = ((i + phase) / 20f) % 1f
        val fade = MenuRoadFx.travelFade(t)
        if (fade < 0.05f) continue
        val p = MenuRoadFx.perspective(t)
        val y = MenuRoadFx.roadY(crestY, h, p)
        val lane = if (i % 2 == 0) -0.36f else 0.36f
        val jitter = (MathX.hash01(i, 71) - 0.5f) * 0.10f
        val x = MenuRoadFx.laneX(w, p, lane + jitter)
        val lift = (1.1f + 5.5f * p) * (0.35f + MathX.hash01(i, 19) * 0.35f)
        val r = MenuRoadFx.particleRadius(p, 3.4f)
        drawMenuPuff(dust.copy(alpha = fade * (0.10f + 0.22f * p)), Offset(x, y - lift), r)
    }
    for (i in 0 until 10) {
        val t = ((i + phase * 0.85f + 0.12f) / 10f) % 1f
        val fade = MenuRoadFx.travelFade(t)
        if (fade < 0.06f) continue
        val p = MenuRoadFx.perspective(t)
        val y = MenuRoadFx.roadY(crestY, h, p)
        val x = MenuRoadFx.laneX(w, p, -0.08f + (MathX.hash01(i, 43) - 0.5f) * 0.12f)
        val lift = 2.2f + 9f * p
        val r = MenuRoadFx.particleRadius(p, 2.6f)
        drawMenuPuff(
            exhaust.copy(alpha = fade * (0.08f + 0.16f * p)),
            Offset(x, y - lift),
            r
        )
    }
    for (i in 0 until 12) {
        val t = ((i + phase) / 12f) % 1f
        if (t < 0.52f) continue
        val fade = MenuRoadFx.travelFade(t)
        if (fade < 0.08f) continue
        val p = MenuRoadFx.perspective(t)
        val y = MenuRoadFx.roadY(crestY, h, p)
        val lane = if (i % 2 == 0) -0.42f else 0.42f
        val x = MenuRoadFx.laneX(w, p, lane)
        val r = MenuRoadFx.particleRadius(p, 1.35f)
        drawCircle(grit.copy(alpha = fade * 0.38f), r, Offset(x, y - r * 0.4f))
    }
}

private fun DrawScope.drawMenuPuff(color: Color, center: Offset, r: Float) {
    val rr = r.coerceAtLeast(0.55f)
    drawCircle(color, rr * 0.56f, center)
    drawCircle(
        color.copy(alpha = color.alpha * 0.40f),
        rr * 0.36f,
        Offset(center.x + rr * 0.38f, center.y - rr * 0.10f)
    )
    drawCircle(
        color.copy(alpha = color.alpha * 0.30f),
        rr * 0.26f,
        Offset(center.x - rr * 0.28f, center.y + rr * 0.08f)
    )
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
