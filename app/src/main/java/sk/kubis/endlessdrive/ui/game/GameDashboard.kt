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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import sk.kubis.endlessdrive.core.GameConfig
import sk.kubis.endlessdrive.domain.model.GamePhase
import sk.kubis.endlessdrive.ui.theme.BtnStyle
import sk.kubis.endlessdrive.ui.theme.GameButton
import sk.kubis.endlessdrive.ui.theme.GameColors
import sk.kubis.endlessdrive.ui.theme.IconToggleButton
import sk.kubis.endlessdrive.ui.theme.Space
import sk.kubis.endlessdrive.ui.theme.Type
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import kotlin.math.cos
import kotlin.math.sin

/**
 * Bez podkladového bloku môže text ležať na svetlej oblohe aj na tmavej hline.
 * Tieň ho udrží čitateľný na oboch, a pritom nezakrýva kulisu.
 */
private val OnSceneText = TextStyle(
    shadow = Shadow(color = Color(0xCC000000), offset = Offset(0f, 1.5f), blurRadius = 5f)
)

/** Koniec stupnice tachometra (km/h) – nad MAX_SPEED s rezervou. */
private const val SPEEDO_MAX = 140f

/**
 * Palubná doska cez spodok obrazovky. Nahrádza tabuľku prúžkov v rohu –
 * za jazdy sa človek pozerá na budíky a kontrolky, nie na zoznam čísel.
 *
 * Rozdelenie zámerne kopíruje skutočné auto: naľavo budíky, v strede
 * kontrolky a ovládanie, napravo rýchlosť a trasa.
 */
@Composable
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
    onStopDriving: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Bez podkladu – budíky a kontrolky ležia priamo na scéne. Tmavý blok cez
    // spodok obrazovky ukrajoval z kreslenej kulisy, ktorá je na ňom to pekné.
    val driving = ui.phase == GamePhase.DRIVING
    Row(
        modifier.padding(start = Space.l, end = Space.l, top = Space.xs, bottom = Space.s),
        // Za jazdy je obsah len budíky + ručná brzda, takže sa dá vycentrovať.
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.l, Alignment.CenterHorizontally)
    ) {
        // --- Budíky, pod nimi kontrolky -----------------------------------
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Space.m),
                verticalAlignment = Alignment.Bottom
            ) {
                Gauge(
                    label = "TEMP",
                    ratio = ((ui.temperature - 40f) / 100f).coerceIn(0f, 1f),
                    value = "${ui.temperature.toInt()}°",
                    redFrom = (GameConfig.OVERHEAT_THRESHOLD - 40f) / 100f
                )
                // Tachometer je najväčší – tak to má auto aj tak sa to číta.
                Gauge(
                    label = if (ui.speedKmh < -0.5f) "REVERSE" else "km/h",
                    ratio = (kotlin.math.abs(ui.speedKmh) / SPEEDO_MAX).coerceIn(0f, 1f),
                    value = kotlin.math.abs(ui.speedKmh).toInt().toString(),
                    redFrom = 1f,
                    diameter = 74.dp,
                    valueSize = Type.title,
                    accent = if (ui.speedKmh < -0.5f) GameColors.warn else GameColors.accent
                )
                Gauge(
                    label = "FUEL",
                    ratio = (ui.fuelL / ui.fuelCapacityL.coerceAtLeast(1f)).coerceIn(0f, 1f),
                    value = "${ui.fuelL.toInt()} L",
                    redFrom = 0f,
                    redTo = 0.15f
                )
            }
            Spacer(Modifier.height(Space.xs))
            TellTales(ui)
        }

        // --- Ovládanie ----------------------------------------------------
        if (driving) {
            // Ručná brzda ako ikona – patrí k prístrojovke a nemá kričať.
            IconToggleButton(
                glyph = "🅿",
                active = false,
                onClick = onStopDriving,
                label = "Parking brake"
            )
        } else {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Space.s),
                verticalAlignment = Alignment.CenterVertically
            ) {
                GameButton("PACK", onInventory, compact = true)
                GameButton("CAR", onCar, compact = true)
                if (ui.exploring) {
                    GameButton("LEAVE", onLeave, compact = true)
                } else if (ui.hasNearbyBuilding) {
                    GameButton("SEARCH", onEnter, style = BtnStyle.Ghost, compact = true)
                }
                if (ui.canRest) {
                    GameButton("SLEEP", onRest, style = BtnStyle.Ghost, compact = true)
                }
                Spacer(Modifier.width(Space.s))
                if (!ui.engineRunning) {
                    GameButton("START", onStart, style = BtnStyle.Primary)
                } else {
                    GameButton("OFF", onStopEngine, compact = true)
                    GameButton("DRIVE", onDrive, style = BtnStyle.Primary)
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
        Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
            Text(
                (if (ui.isNight) "☾ " else "☀ ") + ui.clock,
                color = GameColors.textDim,
                fontSize = Type.label
            )
            if (ui.bestDistanceKm > 0f) {
                Text(
                    String.format("best %.1f", ui.bestDistanceKm),
                    color = GameColors.textDim,
                    fontSize = Type.label
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
    accent: Color = GameColors.accent
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
    }
}

/**
 * Kontrolky ako v aute: zhasnuté sú sotva viditeľné, rozsvietené kričia.
 * Nahrádzajú prúžky oleja, chladiacej a batérie – tie zaujímajú len vtedy,
 * keď je s nimi problém.
 */
@Composable
private fun TellTales(ui: GameUiState) {
    val engineHp = ui.parts.firstOrNull { it.tag == "ENG" }?.health ?: 1f
    val oilRatio = (ui.oilL / ui.oilCapacityL.coerceAtLeast(1f)).coerceIn(0f, 1f)
    val coolRatio = (ui.coolantL / ui.coolantCapacityL.coerceAtLeast(1f)).coerceIn(0f, 1f)

    Row(horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
        Lamp(
            glyph = "OIL",
            // Málo oleja svieti načerveno, špinavý oranžovo – iná porucha, iná farba.
            state = when {
                oilRatio < 0.25f -> LampState.Alarm
                ui.oilPurity < 0.6f -> LampState.Warn
                else -> LampState.Off
            }
        )
        Lamp(
            glyph = "TEMP",
            state = when {
                ui.temperature > GameConfig.OVERHEAT_THRESHOLD -> LampState.Alarm
                ui.temperature > GameConfig.OVERHEAT_THRESHOLD - 12f -> LampState.Warn
                else -> LampState.Off
            }
        )
        Lamp(
            glyph = "BAT",
            state = when {
                ui.batteryCharge < 0.2f -> LampState.Alarm
                ui.batteryCharge < 0.4f -> LampState.Warn
                else -> LampState.Off
            }
        )
        Lamp(
            glyph = "ENG",
            state = when {
                // Aktívne opotrebenie bliká – motor sa práve teraz ničí.
                ui.wearWarning != null -> LampState.Alarm
                engineHp < 0.2f -> LampState.Alarm
                engineHp < 0.45f -> LampState.Warn
                else -> LampState.Off
            }
        )
        Lamp(
            glyph = "COOL",
            state = when {
                coolRatio < 0.2f -> LampState.Alarm
                coolRatio < 0.4f || ui.coolantPurity < 0.5f -> LampState.Warn
                else -> LampState.Off
            }
        )
        // Gumy a brzdy nemajú kvapalinu ani budík – bez kontrolky by sa hráč
        // o ich stave dozvedel až v paneli auta.
        PartLamp("TYRE", ui, "TYRES")
        PartLamp("BRK", ui, "BRK")
        Lamp(glyph = "LGT", state = if (ui.headlightsOn) LampState.On else LampState.Off)
    }
}

@Composable
private fun PartLamp(glyph: String, ui: GameUiState, tag: String) {
    val part = ui.parts.firstOrNull { it.tag == tag }
    Lamp(
        glyph = glyph,
        state = when {
            part == null -> LampState.Off
            !part.fitted -> LampState.Alarm
            // Diel, ktorý sa práve ničí, bliká bez ohľadu na to, koľko mu ostáva.
            // Preklz zodiera gumy hneď – vidieť to má hráč vtedy, nie až potom.
            part.wearing -> LampState.Alarm
            part.health < 0.2f -> LampState.Alarm
            part.health < 0.4f -> LampState.Warn
            else -> LampState.Off
        }
    )
}

private enum class LampState { Off, On, Warn, Alarm }

@Composable
private fun Lamp(glyph: String, state: LampState) {
    val pulse by rememberInfiniteTransition(label = "lamp").animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "lampPulse"
    )
    val color = when (state) {
        // Zhasnutá kontrolka musí ostať čitateľná aj na svetlej oblohe aj na
        // tmavej hline – preto nie priehľadná, ale tlmená svetlá.
        LampState.Off -> GameColors.text.copy(alpha = 0.40f)
        LampState.On -> GameColors.info
        LampState.Warn -> GameColors.warn
        // Len skutočná porucha bliká – inak by prístrojovka blikala stále.
        LampState.Alarm -> GameColors.danger.copy(alpha = pulse)
    }
    Box(
        Modifier
            .size(width = 30.dp, height = 19.dp)
            // Vlastný tmavý podklad namiesto bloku cez celú lištu – kontrolka
            // si nesie kontrast so sebou a kulisa medzi nimi ostáva vidieť.
            .background(
                if (state == LampState.Off) Color(0x66000000) else color.copy(alpha = 0.22f),
                RoundedCornerShape(6.dp)
            )
            .border(1.dp, color.copy(alpha = if (state == LampState.Off) 0.55f else 1f), RoundedCornerShape(6.dp)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            glyph,
            color = color,
            fontSize = Type.micro,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.4.sp,
            style = OnSceneText
        )
    }
}
