package sk.kubis.endlessdrive.ui.game

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import sk.kubis.endlessdrive.R
import sk.kubis.endlessdrive.core.GameConfig
import sk.kubis.endlessdrive.domain.model.GamePhase
import sk.kubis.endlessdrive.domain.model.GaugeStyle
import sk.kubis.endlessdrive.game.Journey
import sk.kubis.endlessdrive.game.car.TireInjury
import sk.kubis.endlessdrive.ui.l10n.LocalCopy
import sk.kubis.endlessdrive.ui.l10n.adLoadingQuota
import sk.kubis.endlessdrive.ui.l10n.batteryWatchAd
import sk.kubis.endlessdrive.ui.l10n.car
import sk.kubis.endlessdrive.ui.l10n.condition
import sk.kubis.endlessdrive.ui.l10n.drive
import sk.kubis.endlessdrive.ui.l10n.fuel
import sk.kubis.endlessdrive.ui.l10n.gaugeCluster
import sk.kubis.endlessdrive.ui.l10n.kmh
import sk.kubis.endlessdrive.ui.l10n.leave
import sk.kubis.endlessdrive.ui.l10n.pack
import sk.kubis.endlessdrive.ui.l10n.relayNetwork
import sk.kubis.endlessdrive.ui.l10n.reverse
import sk.kubis.endlessdrive.ui.l10n.roadAssistWatchAd
import sk.kubis.endlessdrive.ui.l10n.search
import sk.kubis.endlessdrive.ui.l10n.sleep
import sk.kubis.endlessdrive.ui.l10n.start
import sk.kubis.endlessdrive.ui.l10n.temp
import sk.kubis.endlessdrive.ui.theme.BtnStyle
import sk.kubis.endlessdrive.ui.theme.GameButton
import sk.kubis.endlessdrive.ui.theme.GameColors
import sk.kubis.endlessdrive.ui.theme.Space
import sk.kubis.endlessdrive.ui.theme.Type
import kotlin.math.min

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
    onAssist: () -> Unit = {},
    assistAdReady: Boolean = false,
    gaugeStyle: GaugeStyle = GaugeStyle.CLASSIC,
    compact: Boolean = false,
    modifier: Modifier = Modifier
) {
    val copy = LocalCopy.current
    // Palubovka sedí priamo na scéne. Tmavý film cez spodok by zakryl cestu
    // aj hlinu; čitateľnosť drží tieň textu a vlastný podklad kontrolek.
    val driving = ui.phase == GamePhase.DRIVING
    Row(
        modifier
            .fillMaxWidth()
            .padding(start = Space.l, end = Space.l, top = Space.s, bottom = Space.s),
        // Za jazdy ostáva dashboard v strede. Iba v PARK sa rozdelí na
        // dashboard vľavo a ovládanie vpravo.
        horizontalArrangement = if (driving) {
            Arrangement.Center
        } else {
            Arrangement.SpaceBetween
        },
        verticalAlignment = Alignment.Bottom
    ) {
        // --- Budíky, pod nimi kontrolky -----------------------------------
        Column(
            Modifier
                .semantics { contentDescription = copy.gaugeCluster },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val diesel = ui.fuelDieselFraction.coerceIn(0f, 1f)
            val coolantWater = (1f - ui.coolantPurity).coerceIn(0f, 1f)
            GaugeTrio(
                temp = GaugeSpec(
                    label = copy.temp,
                    ratio = ((ui.temperature - 40f) / 100f).coerceIn(0f, 1f),
                    value = "${ui.temperature.toInt()}°",
                    redFrom = (GameConfig.OVERHEAT_THRESHOLD - 40f) / 100f,
                    mixRatio = coolantWater,
                    mixBaseColor = GameColors.ok,
                    mixColor = GameColors.info,
                    mixLabel = "C ${(ui.coolantPurity * 100f).toInt()} · W ${(coolantWater * 100f).toInt()}"
                ),
                speed = GaugeSpec(
                    label = if (!driving) {
                        copy.condition
                    } else if (ui.speedKmh < -0.5f) {
                        copy.reverse
                    } else {
                        copy.kmh
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
                    accent = if (driving && ui.speedKmh < -0.5f) GameColors.warn else GameColors.accent,
                    primary = true
                ),
                fuel = GaugeSpec(
                    label = copy.fuel,
                    ratio = (ui.fuelL / ui.fuelCapacityL.coerceAtLeast(1f)).coerceIn(0f, 1f),
                    value = "${ui.fuelL.toInt()} L",
                    redFrom = 0f,
                    redTo = 0.15f,
                    mixRatio = diesel,
                    mixBaseColor = Color(0xFFE1A84F),
                    mixColor = GameColors.info,
                    mixLabel = "P ${((1f - diesel) * 100f).toInt()} · D ${(diesel * 100f).toInt()}"
                ),
                style = gaugeStyle
            )
            Spacer(Modifier.height(Space.xs))
            TellTales(ui)
        }

        // --- Ovládanie ----------------------------------------------------
        if (!driving) {
            Column(
                Modifier.width(if (compact) 318.dp else 420.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(if (compact) 5.dp else 8.dp)
            ) {
                // V PARK už akcie nezalamujeme podľa ich textu. Každá má
                // rovnakú šírku a panel tak ostáva vizuálne pokojný.
                val parkActions = buildList<Pair<String, () -> Unit>> {
                    add(copy.pack to onInventory)
                    add(copy.car to onCar)
                    when {
                        ui.exploring -> add(copy.leave to onLeave)
                        ui.hasNearbyBuilding -> add(copy.search to onEnter)
                    }
                    if (ui.canRest) add(copy.sleep to onRest)
                }
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(
                        if (compact) 6.dp else 8.dp,
                        Alignment.CenterHorizontally
                    ),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    parkActions.forEach { (label, action) ->
                        GameButton(
                            label,
                            action,
                            compact = true,
                            modifier = Modifier.width(if (compact) 72.dp else 94.dp)
                        )
                    }
                }
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!ui.engineRunning) {
                        GameButton(
                            copy.start,
                            onStart,
                            style = BtnStyle.Primary,
                            iconRes = R.drawable.ic_start_engine,
                            modifier = Modifier.width(if (compact) 120.dp else 144.dp)
                        )
                    } else {
                        GameButton(
                            text = copy.off,
                            onClick = onStopEngine,
                            compact = true,
                            iconRes = R.drawable.ic_stop_engine,
                            iconOnly = true,
                            modifier = Modifier.width(if (compact) 54.dp else 64.dp),
                        )
                        GameButton(
                            copy.drive,
                            onDrive,
                            style = BtnStyle.Primary,
                            modifier = Modifier.width(if (compact) 120.dp else 144.dp)
                        )
                    }
                }
                if (ui.canRequestAssist) {
                        val quota = ui.helpAdsQuotaLabel
                        val label = if (!assistAdReady) copy.adLoadingQuota(quota)
                        else when (ui.assistOffer) {
                            sk.kubis.endlessdrive.domain.model.RescueOffer.BATTERY,
                            sk.kubis.endlessdrive.domain.model.RescueOffer.BATTERY_FIT ->
                                copy.batteryWatchAd(quota)
                            else -> copy.roadAssistWatchAd(quota)
                        }
                        GameButton(
                        label,
                        onAssist,
                        style = BtnStyle.Primary,
                        compact = true,
                        modifier = Modifier.widthIn(min = 168.dp, max = 232.dp)
                    )
                }
            }
        }

        // Trasa, počasie a čas sedia hore vľavo (TripBadge) – dole na nich
        // za jazdy nie je čas pozerať a brali miesto autu.
    }
}

/** Trasa, hodiny a počasie v hornom rohu – mimo jazdnej scény. */
@Composable
fun TripBadge(
    ui: GameUiState,
    showFps: Boolean,
    compact: Boolean = false,
    modifier: Modifier = Modifier
) {
    val copy = LocalCopy.current
    Column(
        modifier
            .background(GameColors.hudBg, RoundedCornerShape(12.dp))
            .border(1.dp, GameColors.outline.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
            .padding(
                horizontal = if (compact) Space.s else Space.m,
                vertical = if (compact) Space.xs else Space.s
            )
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                String.format("%.2f", ui.distanceKm),
                color = GameColors.accent,
                fontSize = if (compact) Type.title else Type.hero,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.width(Space.xs))
            Text(
                "km",
                color = GameColors.textDim,
                fontSize = Type.label,
                modifier = Modifier.padding(bottom = if (compact) 3.dp else 6.dp)
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
            copy.relayNetwork(ui.relayNodes, Journey.goals.size),
            color = if (ui.relayNodes >= Journey.goals.size) GameColors.ok else GameColors.textDim,
            fontSize = Type.label,
            fontWeight = FontWeight.SemiBold
        )
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
                sk.kubis.endlessdrive.game.event.RoadEvent.COOLANT_LEAK in ui.activeEventKinds -> LampState.Alarm
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
