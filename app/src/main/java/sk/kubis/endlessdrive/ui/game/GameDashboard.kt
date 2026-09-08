package sk.kubis.endlessdrive.ui.game

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import sk.kubis.endlessdrive.R
import sk.kubis.endlessdrive.core.GameConfig
import sk.kubis.endlessdrive.domain.model.GamePhase
import sk.kubis.endlessdrive.game.Journey
import sk.kubis.endlessdrive.game.car.TireInjury
import sk.kubis.endlessdrive.ui.theme.BtnStyle
import sk.kubis.endlessdrive.ui.theme.GameButton
import sk.kubis.endlessdrive.ui.theme.GameColors
import sk.kubis.endlessdrive.ui.theme.Space
import sk.kubis.endlessdrive.ui.theme.Type
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Bez podkladového bloku môže text ležať na svetlej oblohe aj na tmavej hline.
 * Tieň ho udrží čitateľný na oboch, a pritom nezakrýva kulisu.
 */
private val OnSceneText = TextStyle(
    shadow = Shadow(color = Color(0xCC000000), offset = Offset(0f, 1.5f), blurRadius = 5f)
)

/** Koniec stupnice tachometra (km/h) – nad MAX_SPEED s rezervou. */
private const val SPEEDO_MAX = 180f

/**
 * Palubná doska cez spodok obrazovky. Nahrádza tabuľku prúžkov v rohu –
 * za jazdy sa človek pozerá na budíky a kontrolky, nie na zoznam čísel.
 *
 * Rozdelenie zámerne kopíruje skutočné auto: naľavo budíky, v strede
 * kontrolky a ovládanie, napravo rýchlosť a trasa.
 */
@Composable
@OptIn(ExperimentalLayoutApi::class)
fun Dashboard(
    ui: GameUiState,
    onStart: () -> Unit,
    onStopEngine: () -> Unit,
    onInventory: () -> Unit,
    onCar: () -> Unit,
    onEnter: () -> Unit,
    onLeave: () -> Unit,
    onDrive: () -> Unit,
    onRest: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Palubovka sedí priamo na scéne. Tmavý film cez spodok by zakryl cestu
    // aj hlinu; čitateľnosť drží tieň textu a vlastný podklad kontrolek.
    val driving = ui.phase == GamePhase.DRIVING
    FlowRow(
        modifier
            .fillMaxWidth()
            .padding(start = Space.l, end = Space.l, top = Space.s, bottom = Space.s),
        // Za jazdy je obsah len budíky + ručná brzda, takže sa dá vycentrovať.
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalArrangement = Arrangement.spacedBy(Space.l, Alignment.CenterHorizontally)
    ) {
        // --- Budíky, pod nimi kontrolky -----------------------------------
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            val diesel = ui.fuelDieselFraction.coerceIn(0f, 1f)
            val coolantWater = (1f - ui.coolantPurity).coerceIn(0f, 1f)
            Row(
                horizontalArrangement = Arrangement.spacedBy(Space.m),
                verticalAlignment = Alignment.Bottom
            ) {
                Gauge(
                    label = "TEMP",
                    ratio = ((ui.temperature - 40f) / 100f).coerceIn(0f, 1f),
                    value = "${ui.temperature.toInt()}°",
                    redFrom = (GameConfig.OVERHEAT_THRESHOLD - 40f) / 100f,
                    mixRatio = coolantWater,
                    mixBaseColor = GameColors.ok,
                    mixColor = GameColors.info,
                    mixLabel = "C ${(ui.coolantPurity * 100f).toInt()} · W ${(coolantWater * 100f).toInt()}"
                )
                // Tachometer je najväčší – tak to má auto aj tak sa to číta.
                // Keď stojí, ten istý budík ukáže stav auta (nie 0 km/h).
                Gauge(
                    label = if (!driving) {
                        "CONDITION"
                    } else if (ui.speedKmh < -0.5f) {
                        "REVERSE"
                    } else {
                        "km/h"
                    },
                    ratio = if (!driving) {
                        ui.overallHealth.coerceIn(0f, 1f)
                    } else {
                        (kotlin.math.abs(ui.speedKmh) / SPEEDO_MAX).coerceIn(0f, 1f)
                    },
                    value = if (!driving) {
                        "${(ui.overallHealth * 100).toInt()} %"
                    } else {
                        kotlin.math.abs(ui.speedKmh).toInt().toString()
                    },
                    redFrom = 1f,
                    diameter = 74.dp,
                    valueSize = Type.title,
                    accent = if (driving && ui.speedKmh < -0.5f) GameColors.warn else GameColors.accent
                )
                Gauge(
                    label = "FUEL",
                    ratio = (ui.fuelL / ui.fuelCapacityL.coerceAtLeast(1f)).coerceIn(0f, 1f),
                    value = "${ui.fuelL.toInt()} L",
                    redFrom = 0f,
                    redTo = 0.15f,
                    mixRatio = diesel,
                    mixBaseColor = Color(0xFFE1A84F),
                    mixColor = GameColors.info,
                    mixLabel = "P ${((1f - diesel) * 100f).toInt()} · D ${(diesel * 100f).toInt()}"
                )
            }
            Spacer(Modifier.height(Space.xs))
            TellTales(ui)
        }

        // --- Ovládanie ----------------------------------------------------
        if (!driving) {
            Column(
                Modifier.width(420.dp).align(Alignment.Bottom),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FlowRow(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    GameButton("PACK", onInventory, compact = true)
                    GameButton("CAR", onCar, compact = true)
                    if (ui.exploring) {
                        GameButton("LEAVE", onLeave, compact = true)
                    } else if (ui.hasNearbyBuilding) {
                        GameButton("SEARCH", onEnter, compact = true)
                    }
                    if (ui.canRest) {
                        GameButton("SLEEP", onRest, compact = true)
                    }
                }
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!ui.engineRunning) {
                        GameButton(
                            "START",
                            onStart,
                            style = BtnStyle.Primary,
                            iconRes = R.drawable.ic_start_engine,
                            modifier = Modifier.width(144.dp)
                        )
                    } else {
                        GameButton(
                            text = "OFF",
                            onClick = onStopEngine,
                            compact = true,
                            iconRes = R.drawable.ic_stop_engine,
                            iconOnly = true,
                        )
                        GameButton(
                            "DRIVE",
                            onDrive,
                            style = BtnStyle.Primary,
                            modifier = Modifier.width(144.dp)
                        )
                    }
                }
            }
        }

        // Trasa, počasie a čas sedia hore vľavo (TripBadge) – dole na nich
        // za jazdy nie je čas pozerať a brali miesto autu.
    }
}

/** Trasa, hodiny a počasie v hornom rohu – mimo jazdnej scény. */
@Composable
fun TripBadge(ui: GameUiState, showFps: Boolean, modifier: Modifier = Modifier) {
    Column(
        modifier
            .background(GameColors.hudBg, RoundedCornerShape(12.dp))
            .border(1.dp, GameColors.outline.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
            .padding(horizontal = Space.m, vertical = Space.s)
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                String.format("%.2f", ui.distanceKm),
                color = GameColors.accent,
                fontSize = Type.hero,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.width(Space.xs))
            Text(
                "km",
                color = GameColors.textDim,
                fontSize = Type.label,
                modifier = Modifier.padding(bottom = 6.dp)
            )
        }
        // Druhý riadok je výlučne pre aktuálny stav jazdy. Scrap je mena,
        // preto patrí vedľa času; rekord patrí na obrazovku výsledkov.
        Row(
            horizontalArrangement = Arrangement.spacedBy(Space.s),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AutomotiveIconView(
                    if (ui.isNight) AutomotiveIcon.MOON else AutomotiveIcon.SUN,
                    GameColors.textDim,
                    Modifier.size(12.dp)
                )
                Text(
                    ui.clock,
                    color = GameColors.textDim,
                    fontSize = Type.label
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ScrapCurrencyIcon(Modifier.size(16.dp))
                Text(
                    "${ui.scrap}",
                    color = GameColors.accent,
                    fontSize = Type.label,
                    fontWeight = FontWeight.Bold
                )
            }
            if (showFps) {
                Text(
                    "${ui.fps} FPS",
                    color = when {
                        ui.fps >= 50 -> GameColors.ok
                        ui.fps >= 30 -> GameColors.warn
                        else -> GameColors.danger
                    },
                    fontSize = Type.label,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
        Text(
            "RELAY NETWORK  ${ui.relayNodes}/${Journey.goals.size}",
            color = if (ui.relayNodes >= Journey.goals.size) GameColors.ok else GameColors.textDim,
            fontSize = Type.label,
            fontWeight = FontWeight.SemiBold
        )
    }
}

/**
 * Okrúhly budík s ručičkou. Červená zóna je časť stupnice, nie zmena farby
 * ručičky – hráč tak vidí, ako blízko k nej je, nielen že už v nej je.
 */
@Composable
private fun Gauge(
    label: String,
    ratio: Float,
    value: String,
    redFrom: Float,
    redTo: Float = 1f,
    // Nie „size“ – to by v Canvas zatienilo DrawScope.size.
    diameter: androidx.compose.ui.unit.Dp = 54.dp,
    valueSize: androidx.compose.ui.unit.TextUnit = Type.body,
    accent: Color = GameColors.accent,
    /** Druhá zložka zmesi (diesel v palive, voda v chladiacej zmesi). */
    mixRatio: Float? = null,
    mixBaseColor: Color = GameColors.ok,
    mixColor: Color = GameColors.info,
    mixLabel: String? = null
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(diameter), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(diameter)) {
                val stroke = size.minDimension * 0.09f
                val r = (size.minDimension - stroke) / 2f
                val c = Offset(size.width / 2f, size.height / 2f)
                val topLeft = Offset(c.x - r, c.y - r)
                val arcSize = Size(r * 2f, r * 2f)
                // Stupnica je 240° s medzerou dole – klasický budík, nie kruh.
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
                // Nebezpečná zóna.
                val redStart = start + sweep * redFrom.coerceIn(0f, 1f)
                val redSweep = sweep * (redTo - redFrom).coerceIn(0f, 1f)
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
                // Naplnená časť po ručičku.
                val t = ratio.coerceIn(0f, 1f)
                val inRed = t >= redFrom && t <= redTo
                drawArc(
                    color = if (inRed) GameColors.danger else accent,
                    startAngle = start,
                    sweepAngle = sweep * t,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round)
                )
                // Ručička.
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
            // Hodnota sedí v dolnej tretine ciferníka, kde nie je ručička.
            Text(
                value,
                color = GameColors.text,
                fontSize = valueSize,
                fontWeight = FontWeight.Bold,
                style = OnSceneText,
                modifier = Modifier.offset(y = diameter * 0.24f)
            )
        }
        Text(
            label,
            color = GameColors.text.copy(alpha = 0.85f),
            fontSize = Type.micro,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.8.sp,
            style = OnSceneText
        )
        if (mixRatio != null && mixLabel != null) {
            val second = mixRatio.coerceIn(0f, 1f)
            Row(
                Modifier
                    .width(diameter * 0.78f)
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp))
            ) {
                Box(
                    Modifier
                        .weight((1f - second).coerceAtLeast(0.001f))
                        .fillMaxHeight()
                        .background(mixBaseColor)
                )
                Box(
                    Modifier
                        .weight(second.coerceAtLeast(0.001f))
                        .fillMaxHeight()
                        .background(mixColor)
                )
            }
            Text(
                mixLabel,
                color = GameColors.text.copy(alpha = 0.82f),
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.2.sp,
                style = OnSceneText
            )
        }
    }
}

/**
 * Kontrolky ako v aute: zhasnuté tlmená sivá, rozsvietené plná farba v glyfe.
 * Nahrádzajú prúžky oleja, chladiacej a batérie – tie zaujímajú len vtedy,
 * keď je s nimi problém.
 */
@Composable
private fun TellTales(ui: GameUiState) {
    val engineHp = ui.parts.firstOrNull { it.tag == "ENG" }?.health ?: 1f
    val oilRatio = (ui.oilL / ui.oilCapacityL.coerceAtLeast(1f)).coerceIn(0f, 1f)
    val coolRatio = (ui.coolantL / ui.coolantCapacityL.coerceAtLeast(1f)).coerceIn(0f, 1f)

    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Lamp(
            icon = AutomotiveIcon.OIL,
            // Málo oleja svieti načerveno, špinavý oranžovo – iná porucha, iná farba.
            state = when {
                oilRatio < 0.25f -> LampState.Alarm
                ui.oilPurity < 0.6f -> LampState.Warn
                else -> LampState.Off
            }
        )
        Lamp(
            icon = AutomotiveIcon.TEMPERATURE,
            state = when {
                ui.temperature > GameConfig.OVERHEAT_THRESHOLD -> LampState.Alarm
                ui.temperature > GameConfig.OVERHEAT_THRESHOLD - 12f -> LampState.Warn
                else -> LampState.Off
            }
        )
        Lamp(
            icon = AutomotiveIcon.BATTERY,
            state = when {
                ui.batteryCharge < 0.2f -> LampState.Alarm
                ui.batteryCharge < 0.4f -> LampState.Warn
                else -> LampState.Off
            }
        )
        Lamp(
            icon = AutomotiveIcon.ENGINE,
            state = when {
                // Aktívne opotrebenie bliká – motor sa práve teraz ničí.
                ui.wearWarning != null -> LampState.Alarm
                engineHp < 0.2f -> LampState.Alarm
                engineHp < 0.45f -> LampState.Warn
                else -> LampState.Off
            }
        )
        Lamp(
            icon = AutomotiveIcon.COOLANT,
            state = when {
                coolRatio < 0.2f -> LampState.Alarm
                coolRatio < 0.4f || ui.coolantPurity < 0.5f -> LampState.Warn
                else -> LampState.Off
            }
        )
        // Opotrebenie / defekt gumy — nie TPMS a nie preklz (ten má vlastnú kontrolku).
        TyreWearLamp(ui)
        // ESC silueta: jantárové blikajúce wheelspin pri preklze.
        Lamp(
            icon = AutomotiveIcon.WHEELSPIN,
            state = if (ui.wheelSlip > 0.25f) LampState.WarnBlink else LampState.Off
        )
        PartLamp(AutomotiveIcon.BRAKE, ui, "BRK")
        // Stretávacie (zelená) / diaľkové (modrá) — D-tvar bez vlnovky.
        Lamp(
            icon = if (ui.highBeamsOn) AutomotiveIcon.HIGH_BEAM else AutomotiveIcon.LOW_BEAM,
            state = if (ui.headlightsOn) LampState.On else LampState.Off,
            onColor = if (ui.highBeamsOn) Color(0xFF1A5CFF) else Color(0xFF2EBB55)
        )
        // Prídavné / strešné — D-tvar s vodorovnými lúčmi a zvislou vlnovkou.
        if (ui.hasExpeditionKit) {
            Lamp(
                icon = AutomotiveIcon.ROOF_LIGHT,
                state = if (ui.roofLightsOn) LampState.On else LampState.Off,
                onColor = Color(0xFFE0A33C)
            )
        }
    }
}

@Composable
private fun TyreWearLamp(ui: GameUiState) {
    val punctured = ui.frontTireInjury == TireInjury.PUNCTURED ||
        ui.rearTireInjury == TireInjury.PUNCTURED
    val shredded = ui.frontTireInjury == TireInjury.SHREDDED ||
        ui.rearTireInjury == TireInjury.SHREDDED
    val health = min(ui.frontTireHealth, ui.rearTireHealth)
    val fitted = ui.parts.firstOrNull { it.tag == "TYRES" }?.fitted != false
    Lamp(
        icon = AutomotiveIcon.TYRE,
        state = when {
            !fitted || shredded || punctured || health < 0.2f -> LampState.Alarm
            health < 0.45f -> LampState.Warn
            else -> LampState.Off
        }
    )
}

@Composable
private fun PartLamp(icon: AutomotiveIcon, ui: GameUiState, tag: String) {
    val part = ui.parts.firstOrNull { it.tag == tag }
    Lamp(
        icon = icon,
        state = when {
            part == null -> LampState.Off
            !part.fitted -> LampState.Alarm
            // Diel, ktorý sa práve ničí, bliká bez ohľadu na to, koľko mu ostáva.
            part.wearing -> LampState.Alarm
            part.health < 0.2f -> LampState.Alarm
            part.health < 0.4f -> LampState.Warn
            else -> LampState.Off
        }
    )
}

private enum class LampState { Off, On, Warn, WarnBlink, Alarm }

@Composable
private fun Lamp(
    icon: AutomotiveIcon,
    state: LampState,
    onColor: Color = GameColors.info
) {
    val pulse by rememberInfiniteTransition(label = "lamp").animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "lampPulse"
    )
    val color = when (state) {
        // Tlmená sivá = zhasnutá; rozsvietená = plná chart farba v glyfe (bez boxu/glow).
        LampState.Off -> Color(0xFFADB6B3)
        LampState.On -> onColor
        LampState.Warn -> GameColors.warn
        // Wheelspin: jantár bliká, kým kolesá preklzujú.
        LampState.WarnBlink -> GameColors.warn.copy(alpha = pulse)
        // Len skutočná porucha bliká – inak by prístrojovka blikala stále.
        LampState.Alarm -> GameColors.danger.copy(alpha = pulse)
    }
    // Ako Subaru chart: len plochý piktogram na čiernom pozadí, bez rámu a bloomu.
    Box(
        Modifier.size(width = 24.dp, height = 20.dp),
        contentAlignment = Alignment.Center
    ) {
        AutomotiveIconView(icon, color, Modifier.size(18.dp))
    }
}
