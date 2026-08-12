package sk.kubis.endlessdrive.ui.game

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import sk.kubis.endlessdrive.domain.model.RoadSurface
import sk.kubis.endlessdrive.ui.theme.Chip
import sk.kubis.endlessdrive.ui.theme.GameColors
import sk.kubis.endlessdrive.ui.theme.IconToggleButton
import sk.kubis.endlessdrive.ui.theme.StatBar
import sk.kubis.endlessdrive.ui.theme.levelColor
import sk.kubis.endlessdrive.ui.theme.purityColor

/**
 * Dashboard: car vitals on the left in two compact rows.
 * Short labels and colour do the work so it stays readable while driving.
 */
@Composable
fun VitalsPanel(ui: GameUiState, modifier: Modifier = Modifier) {
    val fuel = (ui.fuelL / ui.fuelCapacityL.coerceAtLeast(1f)).coerceIn(0f, 1f)
    val oil = (ui.oilL / ui.oilCapacityL.coerceAtLeast(1f)).coerceIn(0f, 1f)
    val cool = (ui.coolantL / ui.coolantCapacityL.coerceAtLeast(1f)).coerceIn(0f, 1f)
    val temp = ((ui.temperature - 40f) / 90f).coerceIn(0f, 1f)

    Column(
        modifier
            .background(GameColors.hudBg, RoundedCornerShape(12.dp))
            .border(1.dp, GameColors.outline.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatBar(
                "FUEL", fuel, "${ui.fuelL.toInt()} L", levelColor(fuel),
                inner = ui.fuelPurity, innerColor = purityColor(ui.fuelPurity)
            )
            StatBar(
                "OIL", oil, String.format("%.1f L", ui.oilL), levelColor(oil),
                inner = ui.oilPurity, innerColor = purityColor(ui.oilPurity)
            )
            StatBar(
                "COOLANT", cool, String.format("%.1f L", ui.coolantL), levelColor(cool),
                inner = ui.coolantPurity, innerColor = purityColor(ui.coolantPurity)
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatBar(
                "BATTERY", ui.batteryCharge, "${(ui.batteryCharge * 100).toInt()} %",
                levelColor(ui.batteryCharge)
            )
            StatBar(
                "TEMP", temp, "${ui.temperature.toInt()}°C",
                when {
                    ui.temperature > 110f -> GameColors.danger
                    ui.temperature > 98f -> GameColors.warn
                    else -> GameColors.ok
                }
            )
            StatBar(
                "CONDITION", ui.overallHealth, "${(ui.overallHealth * 100).toInt()} %",
                levelColor(ui.overallHealth, 0.25f, 0.5f)
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            EquipChip("ENG", ui.fittedEngine)
            EquipChip("DRV", ui.fittedDrive)
            EquipChip("TYRES", ui.fittedTires)
            EquipChip("SUS", ui.fittedSuspension)
        }
    }
}

@Composable
private fun EquipChip(tag: String, value: String) {
    Column(
        Modifier
            .background(GameColors.panelSoft, RoundedCornerShape(6.dp))
            .border(1.dp, GameColors.outline.copy(alpha = 0.45f), RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 3.dp)
    ) {
        Text(tag, color = GameColors.textDim, fontSize = 8.sp, fontWeight = FontWeight.SemiBold)
        Text(
            value,
            color = GameColors.text,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
    }
}

/** Speed, distance and clock – always in the same spot. */
@Composable
fun TripPanel(ui: GameUiState, showFps: Boolean, modifier: Modifier = Modifier) {
    val reversing = ui.speedKmh < -0.5f
    Column(
        modifier
            .background(GameColors.hudBg, RoundedCornerShape(12.dp))
            .border(1.dp, GameColors.outline.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.End
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                (if (reversing) "R " else "") + kotlin.math.abs(ui.speedKmh).toInt(),
                color = if (reversing) GameColors.warn else GameColors.text,
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.width(3.dp))
            Text("km/h", color = GameColors.textDim, fontSize = 11.sp, modifier = Modifier.padding(bottom = 5.dp))
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                String.format("%.2f", ui.distanceKm),
                color = GameColors.accent,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.width(3.dp))
            Text(
                "km · best ${String.format("%.1f", ui.bestDistanceKm)}",
                color = GameColors.textDim,
                fontSize = 11.sp,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }
        Spacer(Modifier.height(2.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (showFps) {
                Text(
                    "${ui.fps} FPS",
                    color = when {
                        ui.fps >= 50 -> GameColors.ok
                        ui.fps >= 30 -> GameColors.warn
                        else -> GameColors.danger
                    },
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Text(
                (if (ui.isNight) "☾" else "☀") + " " + ui.clock,
                color = GameColors.text,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

/**
 * Top strip: why the car is stuck, the current message and live warnings.
 * Anything already spelled out by the message is dropped from the warnings,
 * so the same thing is never shown twice.
 */
@Composable
fun AlertColumn(ui: GameUiState, modifier: Modifier = Modifier) {
    val message = ui.message
    val warnings = buildList {
        // Zima je stav celej vetvy, nie chvíľková udalosť – hlásime ju vždy.
        if (ui.isWinter) {
            add((if (ui.hasChains) "SNOW · CHAINS ON" else "SNOW · NO CHAINS") to
                (if (ui.hasChains) GameColors.ok else GameColors.danger))
        } else if (ui.hasChains) {
            add("CHAINS ON DRY ROAD" to GameColors.warn)
        }
        // Naplavenina má prednosť – hráč musí vedieť, prečo auto zrazu nejde.
        ui.surface.chip?.let { add(it to GameColors.warn) }
        ui.surfaceAhead?.let { (surface, meters) ->
            if (ui.surface == RoadSurface.ASPHALT) {
                surface.chip?.let { add("$it IN $meters m" to GameColors.accent) }
            }
        }
        if (ui.wheelsLocked) add("WHEELS LOCKED" to GameColors.warn)
        else if (ui.wheelSlip > 0.45f) add("WHEELSPIN" to GameColors.warn)
        if (ui.temperature > 110f) add("OVERHEATING" to GameColors.danger)
        else if (ui.temperature > 98f) add("ENGINE HOT" to GameColors.warn)
        val fuelRatio = ui.fuelL / ui.fuelCapacityL.coerceAtLeast(1f)
        if (fuelRatio < 0.08f) add("FUEL CRITICAL" to GameColors.danger)
        else if (fuelRatio < 0.2f) add("LOW FUEL" to GameColors.warn)
        if (ui.oilL < 0.4f) add("LOW OIL" to GameColors.danger)
        if (ui.coolantL < 0.5f) add("LOW COOLANT" to GameColors.danger)
        if (ui.oilPurity < 0.55f) add("DIRTY OIL" to GameColors.warn)
        if (ui.fuelPurity < 0.55f) add("BAD FUEL" to GameColors.warn)
        if (ui.batteryCharge < 0.2f) add("BATTERY LOW" to GameColors.danger)
        if (ui.isNight && !ui.headlightsOn) add("NO HEADLIGHTS" to GameColors.danger)
        if (ui.overallHealth < 0.25f) add("CAR FALLING APART" to GameColors.danger)
    }.filterNot { (text, _) ->
        // Nezopakujeme to, čo už stojí v hláške.
        message.isNotBlank() && message.contains(text.split(" ").first(), ignoreCase = true)
    }

    if (ui.blockedReason == null && message.isBlank() &&
        warnings.isEmpty() && ui.events.isEmpty()
    ) return

    val pulse by rememberInfiniteTransition(label = "alert").animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(750), RepeatMode.Reverse),
        label = "alertAlpha"
    )

    Column(
        modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        if (ui.blockedReason != null) {
            val prefix = if (ui.engineRunning) "CAN'T MOVE" else "CAN'T START"
            Banner("$prefix: ${ui.blockedReason}", GameColors.danger.copy(alpha = pulse), strong = true)
        }
        if (message.isNotBlank()) {
            Banner(message, messageColor(message))
        }
        if (ui.events.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ui.events.forEach { (label, secs) ->
                    Chip("$label ${secs}s", GameColors.info)
                }
            }
        }
        if (warnings.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                warnings.take(4).forEach { (text, color) ->
                    Chip(text, if (color == GameColors.danger) color.copy(alpha = pulse) else color, filled = true)
                }
            }
        }
    }
}

@Composable
private fun Banner(text: String, color: Color, strong: Boolean = false) {
    Box(
        Modifier
            .background(
                if (strong) color.copy(alpha = 0.22f) else GameColors.hudBg,
                RoundedCornerShape(10.dp)
            )
            .border(1.dp, color.copy(alpha = if (strong) 0.9f else 0.35f), RoundedCornerShape(10.dp))
            .padding(horizontal = 14.dp, vertical = 6.dp)
    ) {
        Text(
            text,
            color = color,
            fontSize = if (strong) 15.sp else 14.sp,
            fontWeight = if (strong) FontWeight.Bold else FontWeight.Medium
        )
    }
}

private fun messageColor(message: String): Color {
    val upper = message.uppercase()
    return when {
        upper.contains("MISSING") || upper.contains("DESTROY") || upper.contains("SEIZ") ||
            upper.contains("RUIN") || upper.contains("CRITICAL") -> GameColors.danger
        upper.contains("SLOW") || upper.contains("WATCH") || upper.contains("DUSK") ||
            upper.contains("LOW") || upper.contains("WEAK") -> GameColors.warn
        else -> GameColors.text
    }
}

/**
 * Fork bar: pick a branch while still driving. No modal, no full stop —
 * the road just bends the way you pointed.
 */
@Composable
fun JunctionBar(
    ui: GameUiState,
    choices: List<Pair<Int, String>>,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    if (!ui.approachingJunction || choices.isEmpty()) return
    Column(
        modifier
            .background(GameColors.hudBg, RoundedCornerShape(12.dp))
            .border(1.dp, GameColors.accent.copy(alpha = 0.55f), RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 7.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "FORK IN ${ui.junctionDistanceM.toInt()} m",
            color = GameColors.accent,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )
        Spacer(Modifier.height(5.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            choices.forEach { (id, label) ->
                val picked = ui.pendingChoiceId == id
                Box(
                    Modifier
                        .background(
                            if (picked) GameColors.accent else GameColors.panelHigh,
                            RoundedCornerShape(8.dp)
                        )
                        .border(
                            1.dp,
                            if (picked) GameColors.accent else GameColors.outline,
                            RoundedCornerShape(8.dp)
                        )
                        .clickable { onSelect(id) }
                        .padding(horizontal = 14.dp, vertical = 7.dp)
                ) {
                    Text(
                        label,
                        color = if (picked) Color(0xFF1B1712) else GameColors.text,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

/** Vertical toggles at the right edge. */
@Composable
fun SideIcons(
    ui: GameUiState,
    showFps: Boolean,
    onToggleLights: () -> Unit,
    onTogglePause: () -> Unit,
    onToggleFps: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        IconToggleButton("☀", ui.headlightsOn, onToggleLights, label = "Headlights")
        IconToggleButton(if (ui.paused) "▶" else "❚❚", ui.paused, onTogglePause, label = "Pause")
        IconToggleButton("⏱", showFps, onToggleFps, label = "FPS")
    }
}

/** Colour for a part's condition. */
fun healthColor(h: Float): Color = levelColor(h, 0.3f, 0.6f)
