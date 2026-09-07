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
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
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
@OptIn(ExperimentalLayoutApi::class)
fun AlertColumn(ui: GameUiState, modifier: Modifier = Modifier) {
    val alerts = composeHudAlerts(ui)

    val pulse by rememberInfiniteTransition(label = "alert").animateFloat(
        initialValue = 0.85f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1100), RepeatMode.Reverse),
        label = "alertAlpha"
    )

    Column(
        modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        alerts.block?.let { reason ->
            Banner(reason, GameColors.danger.copy(alpha = pulse), strong = true)
        }
        if (alerts.chips.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(4.dp)) {
                alerts.chips.forEach { chip ->
                    val color = chip.tone.toColor()
                    Chip(
                        chip.text,
                        if (chip.tone == HudTone.DANGER) color.copy(alpha = pulse) else color,
                        filled = true
                    )
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

private fun HudTone.toColor(): Color = when (this) {
    HudTone.DANGER -> GameColors.danger
    HudTone.WARN -> GameColors.warn
    HudTone.ACCENT -> GameColors.accent
    HudTone.OK -> GameColors.ok
    HudTone.INFO -> GameColors.info
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
    onToggleRoofLights: () -> Unit = {},
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
        if (ui.hasExpeditionKit) {
            AutomotiveIconToggleButton(
                AutomotiveIcon.ROOF_LIGHT,
                ui.roofLightsOn,
                onToggleRoofLights,
                activeColor = Color(0xFFFFCF75),
                label = if (ui.roofLightsOn) "Switch roof lights off" else "Switch roof lights on"
            )
        }
        HudActionToggleButton(
            icon = if (ui.paused) HudActionIcon.PLAY else HudActionIcon.PAUSE,
            active = ui.paused,
            onClick = onTogglePause,
            label = if (ui.paused) "Resume" else "Pause"
        )
    }
}

/** Colour for a part's condition. */
fun healthColor(h: Float): Color = levelColor(h, 0.3f, 0.6f)
