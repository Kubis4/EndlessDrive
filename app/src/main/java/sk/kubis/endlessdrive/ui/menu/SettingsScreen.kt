package sk.kubis.endlessdrive.ui.menu

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import sk.kubis.endlessdrive.domain.model.AudioSettings
import sk.kubis.endlessdrive.domain.model.DebugOptions
import sk.kubis.endlessdrive.domain.model.ThrottleMode
import sk.kubis.endlessdrive.ui.theme.BtnStyle
import sk.kubis.endlessdrive.ui.theme.GameButton
import sk.kubis.endlessdrive.ui.theme.GameColors

/**
 * Nastavenia: ovládanie plynu, zvuk a ladiace prepínače.
 */
@Composable
fun SettingsScreen(
    options: DebugOptions,
    onChange: (DebugOptions) -> Unit,
    onBack: () -> Unit,
    audioSettings: AudioSettings = AudioSettings(),
    onAudioChange: (AudioSettings) -> Unit = {},
    throttleMode: ThrottleMode = ThrottleMode.BINARY,
    onThrottleModeChange: (ThrottleMode) -> Unit = {},
    showFps: Boolean = false,
    onShowFpsChange: (Boolean) -> Unit = {},
    showPrivacyOptions: Boolean = false,
    onPrivacyOptions: () -> Unit = {}
) {
    Box(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .background(
                Brush.verticalGradient(
                    listOf(GameColors.panelSoft, GameColors.panelHigh)
                )
            )
    ) {
        Column(
            Modifier
                .align(Alignment.Center)
                .widthIn(max = 680.dp)
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 20.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("SETTINGS", modifier = Modifier.weight(1f), color = GameColors.text,
                    fontSize = 30.sp, fontWeight = FontWeight.Bold, letterSpacing = 3.sp)
                GameButton("BACK", onBack, style = BtnStyle.Ghost, compact = true)
            }
            Spacer(Modifier.height(18.dp))

            Text("DISPLAY", color = GameColors.accent, fontSize = 12.sp,
                fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
            Toggle("FPS counter", "Show the live frame-rate counter in the driving HUD.", showFps, onShowFpsChange)
            Spacer(Modifier.height(18.dp))

            Text("CONTROLS", color = GameColors.accent, fontSize = 12.sp,
                fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
            Text(
                "How the right pedal works. Brake stays a hold button.",
                color = GameColors.textDim, fontSize = 13.sp
            )
            Spacer(Modifier.height(10.dp))
            Toggle(
                title = "Throttle pedal",
                detail = "Hold for full power. Same as before.",
                checked = throttleMode == ThrottleMode.BINARY,
                onToggle = { if (throttleMode != ThrottleMode.BINARY) onThrottleModeChange(ThrottleMode.BINARY) }
            )
            Toggle(
                title = "Throttle slide",
                detail = "Swipe up from the bottom to feather power and cut wheelspin on launch.",
                checked = throttleMode == ThrottleMode.SLIDE,
                onToggle = { if (throttleMode != ThrottleMode.SLIDE) onThrottleModeChange(ThrottleMode.SLIDE) }
            )
            Spacer(Modifier.height(24.dp))

            Text("SOUND", color = GameColors.accent, fontSize = 12.sp,
                fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
            Text("Tyre sounds are brief traction cues. Set them to zero for silent tyres.",
                color = GameColors.textDim, fontSize = 13.sp)
            AudioSlider("Master volume", audioSettings.master) { onAudioChange(audioSettings.copy(master = it)) }
            AudioSlider("Road & weather", audioSettings.surfaces) { onAudioChange(audioSettings.copy(surfaces = it)) }
            AudioSlider("Tyre slip", audioSettings.tyres) { onAudioChange(audioSettings.copy(tyres = it)) }
            GameButton("RESET SOUND", { onAudioChange(AudioSettings()) }, compact = true)
            if (showPrivacyOptions) {
                Spacer(Modifier.height(18.dp))
                Text("PRIVACY", color = GameColors.accent, fontSize = 12.sp,
                    fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                Text(
                    "Change how rewarded advertising uses your data.",
                    color = GameColors.textDim, fontSize = 13.sp
                )
                Spacer(Modifier.height(8.dp))
                GameButton("AD PRIVACY OPTIONS", onPrivacyOptions, compact = true)
            }
            Spacer(Modifier.height(24.dp))

            Text(
                "TESTING",
                color = GameColors.accent,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp
            )
            Text(
                "Applies to the next new run — an already started one is left alone.",
                color = GameColors.textDim,
                fontSize = 13.sp
            )
            Spacer(Modifier.height(10.dp))

            Toggle(
                title = "All components",
                detail = "Fills every empty slot with a basic part, so nothing is missing.",
                checked = options.allComponents,
                onToggle = { onChange(options.copy(allComponents = it)) }
            )
            Toggle(
                title = "Full upgrades",
                detail = "Best part in every slot — strongest engine, sport tyres, big tank.",
                checked = options.fullUpgrades,
                onToggle = { onChange(options.copy(fullUpgrades = it)) }
            )
            Toggle(
                title = "Whole body",
                detail = "Doors, bonnet, boot lid, bumpers, lights and seats fitted from the start.",
                checked = options.fullBody,
                // Plná výbava karosériu obsahuje tiež – samostatný prepínač
                // by potom nič nemenil, tak sa vypne.
                enabled = !options.allComponents && !options.fullUpgrades,
                onToggle = { onChange(options.copy(fullBody = it)) }
            )
            Toggle(
                title = "Full fluids",
                detail = "Clean fuel, oil and coolant to the brim, battery charged.",
                checked = options.fullFluids,
                onToggle = { onChange(options.copy(fullFluids = it)) }
            )
            Toggle(
                title = "Test track",
                detail = "Washboard, a sharp bump, a jump, a long climb, dips and " +
                    "growing steps — on a loop, for tuning suspension and handling.",
                checked = options.testTrack,
                onToggle = { onChange(options.copy(testTrack = it)) }
            )
            Toggle(
                title = "Repair controls",
                detail = "Shows the instant REPAIR action in CAR. Debug only; normal runs hide it.",
                checked = options.repairControls,
                onToggle = { onChange(options.copy(repairControls = it)) }
            )

            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                GameButton("BACK", onBack, style = BtnStyle.Primary)
                GameButton("FULL CAR + TRACK", { onChange(DebugOptions.TUNE_SUSPENSION) }, compact = true)
                GameButton("TURN ALL OFF", { onChange(DebugOptions.OFF) })
            }
        }
    }
}

@Composable
private fun AudioSlider(label: String, value: Float, onChange: (Float) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = GameColors.text, fontSize = 14.sp)
            Text(if (value <= 0f) "OFF" else "${(value * 100).toInt()}%",
                color = GameColors.accent, fontSize = 13.sp)
        }
        Slider(value = value, onValueChange = onChange,
            modifier = Modifier.semantics { contentDescription = label },
            colors = SliderDefaults.colors(thumbColor = GameColors.accent,
                activeTrackColor = GameColors.accent, inactiveTrackColor = GameColors.outline))
    }
}

@Composable
private fun Toggle(
    title: String,
    detail: String,
    checked: Boolean,
    onToggle: (Boolean) -> Unit,
    enabled: Boolean = true
) {
    val accent = if (checked) GameColors.accent else GameColors.outline
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(GameColors.panelSoft)
            .border(1.dp, accent, RoundedCornerShape(10.dp))
            .clickable(enabled = enabled) { onToggle(!checked) }
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                color = if (enabled) GameColors.text else GameColors.textDim,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(detail, color = GameColors.textDim, fontSize = 12.sp)
        }
        Spacer(Modifier.width(12.dp))
        // Prepínač ako kontrolka na palubovke – svieti, keď je zapnutý.
        Box(
            Modifier
                .size(width = 46.dp, height = 24.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(if (checked) GameColors.accent.copy(alpha = 0.25f) else Color(0x22FFFFFF))
                .border(1.dp, accent, RoundedCornerShape(12.dp)),
            contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart
        ) {
            Box(
                Modifier
                    .padding(horizontal = 3.dp)
                    .size(18.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(if (checked) GameColors.accent else GameColors.textDim)
            )
        }
    }
}
