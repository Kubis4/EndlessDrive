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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import sk.kubis.endlessdrive.domain.model.DebugOptions
import sk.kubis.endlessdrive.ui.theme.BtnStyle
import sk.kubis.endlessdrive.ui.theme.GameButton
import sk.kubis.endlessdrive.ui.theme.GameColors

/**
 * Nastavenia. Zatiaľ v nich je len ladiaca sekcia – prepínače, ktoré menia
 * to, s čím jazda začína, aby sa dala testovať jedna vec bez zháňania lootu.
 */
@Composable
fun SettingsScreen(
    options: DebugOptions,
    onChange: (DebugOptions) -> Unit,
    onBack: () -> Unit
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF0E1522), Color(0xFF241C13), Color(0xFF3A3524))
                )
            )
    ) {
        Column(
            Modifier
                .align(Alignment.Center)
                .fillMaxWidth(0.72f)
                .padding(vertical = 20.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                "SETTINGS",
                color = GameColors.text,
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 3.sp
            )
            Spacer(Modifier.height(18.dp))

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

            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                GameButton("BACK", onBack, style = BtnStyle.Primary)
                GameButton("TURN ALL OFF", { onChange(DebugOptions.OFF) })
            }
        }
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
