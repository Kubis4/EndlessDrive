package sk.kubis.endlessdrive.ui.menu

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import sk.kubis.endlessdrive.domain.repository.GarageUpgrade
import sk.kubis.endlessdrive.domain.repository.PlayerProfile
import sk.kubis.endlessdrive.ui.l10n.LocalCopy
import sk.kubis.endlessdrive.ui.theme.BtnStyle
import sk.kubis.endlessdrive.ui.theme.GameButton
import sk.kubis.endlessdrive.ui.theme.GameColors
import sk.kubis.endlessdrive.ui.theme.GamePanel

@Composable
fun GarageScreen(
    profile: PlayerProfile,
    onBuy: (GarageUpgrade) -> Unit,
    onBack: () -> Unit
) {
    val copy = LocalCopy.current
    Box(
        Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).background(GameColors.panelSoft),
        contentAlignment = Alignment.Center
    ) {
        GamePanel(
            title = copy.garage,
            subtitle = copy.garageSubtitle,
            modifier = Modifier.padding(18.dp).widthIn(max = 720.dp),
            onClose = onBack
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(copy.bankedScrapLabel, color = GameColors.textDim, fontSize = 12.sp, letterSpacing = 1.2.sp)
                Spacer(Modifier.weight(1f))
                Text(profile.bankedScrap.toString(), color = GameColors.accent, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(14.dp))
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                GarageUpgrade.entries.forEach { upgrade ->
                    val level = profile.garageUpgrades.levelOf(upgrade).coerceIn(0, upgrade.maxLevel)
                    val cost = upgrade.costAt(level)
                    Column(
                        Modifier.fillMaxWidth()
                            .background(Color(0x5520272B), RoundedCornerShape(10.dp))
                            .border(1.dp, GameColors.outline, RoundedCornerShape(10.dp))
                            .padding(14.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(copy.garageTitle(upgrade).uppercase(), color = GameColors.text, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                Text(copy.garageLevel(level, upgrade.maxLevel), color = GameColors.accent, fontSize = 11.sp, letterSpacing = 0.8.sp)
                            }
                            GameButton(
                                if (cost == null) copy.max else copy.buyCost(cost),
                                { onBuy(upgrade) },
                                enabled = cost != null && profile.bankedScrap >= cost,
                                compact = true,
                                style = if (cost == null) BtnStyle.Ghost else BtnStyle.Primary
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(copy.garageDetail(upgrade), color = GameColors.textDim, fontSize = 13.sp)
                        Spacer(Modifier.height(7.dp))
                        Text(
                            copy.garageCurrentEffect(copy.garageEffect(upgrade, level)),
                            color = GameColors.ok,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 0.5.sp
                        )
                        if (cost != null) {
                            Text(
                                copy.garageNextEffect(copy.garageEffect(upgrade, level + 1)),
                                color = GameColors.accent,
                                fontSize = 11.sp,
                                letterSpacing = 0.5.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
