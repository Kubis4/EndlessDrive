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
import androidx.compose.foundation.layout.fillMaxWidth
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
import sk.kubis.endlessdrive.ui.theme.StatBar
import sk.kubis.endlessdrive.ui.theme.levelColor
import sk.kubis.endlessdrive.ui.theme.purityColor

/**
 * Hlášky a výstrahy hore v strede. Vitals a ovládanie sedia v palubnej
 * doske dole, sem patrí len to, čo si žiada okamžitú pozornosť.
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
        if (ui.wrongFuelFraction >= 0.15f) add("WRONG FUEL" to GameColors.danger)
        else if (ui.fuelPurity < 0.55f) add("BAD FUEL" to GameColors.warn)
        // Slabé dobíjanie je porucha alternátora, nie prázdna batéria.
        if (ui.engineRunning && ui.alternatorOutput in 0.005f..0.22f) {
            add("ALTERNATOR WEAK" to GameColors.warn)
        }
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
        // Hláška sa neopakuje pod dôvodom blokácie – „Missing battery“ dvakrát
        // pod sebou vyzeralo ako chyba, nie ako dôraz.
        val duplicate = ui.blockedReason != null &&
            message.equals(ui.blockedReason, ignoreCase = true)
        if (message.isNotBlank() && !duplicate) {
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
    // Zvislý stĺpec nad plynom: voliť sa musí za jazdy jedným palcom, takže
    // tlačidlá patria tam, kde ten palec už je. Hore v strede boli mimo dosahu.
    Column(
        modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Text(
            "FORK ${ui.junctionDistanceM.toInt()} m",
            color = GameColors.accent,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            style = androidx.compose.ui.text.TextStyle(
                shadow = androidx.compose.ui.graphics.Shadow(
                    color = Color(0xCC000000),
                    blurRadius = 6f
                )
            )
        )
        choices.forEach { (id, label) ->
            val picked = ui.pendingChoiceId == id
            Box(
                Modifier
                    .width(150.dp)
                    .background(
                        if (picked) GameColors.accent else GameColors.hudBg,
                        RoundedCornerShape(10.dp)
                    )
                    .border(
                        1.dp,
                        if (picked) GameColors.accent else GameColors.outline,
                        RoundedCornerShape(10.dp)
                    )
                    .clickable { onSelect(id) }
                    .padding(horizontal = 12.dp, vertical = 9.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label,
                    color = if (picked) Color(0xFF1B1712) else GameColors.text,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
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
    // Vodorovne v hornom rohu – zvislý stĺpec cez polovicu obrazovky bol
    // zbytočne cez scénu a bil sa s kreslenou kulisou.
    Row(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AutomotiveIconToggleButton(
            if (ui.highBeamsOn) AutomotiveIcon.HIGH_BEAM else AutomotiveIcon.LOW_BEAM,
            ui.headlightsOn,
            onToggleLights,
            activeColor = if (ui.highBeamsOn) Color(0xFF4D8DFF) else Color(0xFF45C96B),
            label = when {
                !ui.headlightsOn -> "Switch on low beams"
                !ui.highBeamsOn -> "Switch on high beams"
                else -> "Switch headlights off"
            }
        )
        HudActionToggleButton(
            icon = if (ui.paused) HudActionIcon.PLAY else HudActionIcon.PAUSE,
            active = ui.paused,
            onClick = onTogglePause,
            label = if (ui.paused) "Resume" else "Pause"
        )
        HudActionToggleButton(
            icon = HudActionIcon.TIMER,
            active = showFps,
            onClick = onToggleFps,
            label = "FPS"
        )
    }
}

/** Colour for a part's condition. */
fun healthColor(h: Float): Color = levelColor(h, 0.3f, 0.6f)
