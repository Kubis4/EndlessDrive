package sk.kubis.endlessdrive.ui.game

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
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
import sk.kubis.endlessdrive.ui.l10n.LocalCopy
import sk.kubis.endlessdrive.ui.l10n.lightsHighOn
import sk.kubis.endlessdrive.ui.l10n.lightsLowOn
import sk.kubis.endlessdrive.ui.l10n.lightsOff
import sk.kubis.endlessdrive.ui.l10n.pause
import sk.kubis.endlessdrive.ui.l10n.resume
import sk.kubis.endlessdrive.ui.l10n.roofLightsOff
import sk.kubis.endlessdrive.ui.l10n.roofLightsOn
import sk.kubis.endlessdrive.ui.theme.Chip
import sk.kubis.endlessdrive.ui.theme.GameColors
import sk.kubis.endlessdrive.ui.theme.levelColor

/**
 * Hlášky a výstrahy hore v strede. Vitals a ovládanie sedia v palubnej
 * doske dole, sem patrí len to, čo si žiada okamžitú pozornosť.
 */
@Composable
@OptIn(ExperimentalLayoutApi::class)
fun AlertColumn(ui: GameUiState, modifier: Modifier = Modifier) {
    val copy = LocalCopy.current
    val alerts = composeHudAlerts(ui, copy)

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

/** Vertical toggles at the right edge. */
@Composable
fun SideIcons(
    ui: GameUiState,
    onToggleLights: () -> Unit,
    onToggleRoofLights: () -> Unit = {},
    onTogglePause: () -> Unit,
    modifier: Modifier = Modifier
) {
    val copy = LocalCopy.current
    // Vodorovne v hornom rohu – zvislý stĺpec cez polovicu obrazovky bol
    // zbytočne cez scénu a bil sa s kreslenou kulisou.
    Row(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (ui.parts.firstOrNull { it.tag == "HEAD" }?.fitted == true) {
            AutomotiveIconToggleButton(
                if (ui.highBeamsOn) AutomotiveIcon.HIGH_BEAM else AutomotiveIcon.LOW_BEAM,
                ui.headlightsOn,
                onToggleLights,
                activeColor = if (ui.highBeamsOn) Color(0xFF4D8DFF) else Color(0xFF45C96B),
                label = when {
                    !ui.headlightsOn -> copy.lightsLowOn
                    !ui.highBeamsOn -> copy.lightsHighOn
                    else -> copy.lightsOff
                }
            )
        }
        if (ui.hasExpeditionKit) {
            AutomotiveIconToggleButton(
                AutomotiveIcon.ROOF_LIGHT,
                ui.roofLightsOn,
                onToggleRoofLights,
                activeColor = Color(0xFFFFCF75),
                label = if (ui.roofLightsOn) copy.roofLightsOff else copy.roofLightsOn
            )
        }
        HudActionToggleButton(
            icon = if (ui.paused) HudActionIcon.PLAY else HudActionIcon.PAUSE,
            active = ui.paused,
            onClick = onTogglePause,
            label = if (ui.paused) copy.resume else copy.pause
        )
    }
}

/** Colour for a part's condition. */
fun healthColor(h: Float): Color = levelColor(h, 0.3f, 0.6f)
