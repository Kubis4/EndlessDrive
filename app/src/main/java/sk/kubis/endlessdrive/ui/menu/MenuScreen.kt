package sk.kubis.endlessdrive.ui.menu

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import sk.kubis.endlessdrive.domain.repository.PlayerProfile
import sk.kubis.endlessdrive.ui.theme.BtnStyle
import sk.kubis.endlessdrive.ui.theme.GameButton
import sk.kubis.endlessdrive.ui.theme.GameColors

@Composable
fun MenuScreen(
    profile: PlayerProfile,
    canContinue: Boolean,
    onContinue: () -> Unit,
    onNewRun: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0E1522),
                        Color(0xFF241C13),
                        Color(0xFF3A3524)
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(horizontal = 52.dp, vertical = 28.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "ENDLESS DRIVE",
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Bold,
                fontSize = 46.sp,
                letterSpacing = 2.sp,
                color = GameColors.text
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "An endless road. Your car. No idea what waits past the horizon.",
                fontSize = 15.sp,
                color = GameColors.textDim
            )

            Spacer(Modifier.height(26.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (canContinue) {
                    GameButton(
                        "CONTINUE",
                        onContinue,
                        style = BtnStyle.Primary,
                        modifier = Modifier.width(190.dp).height(52.dp)
                    )
                }
                GameButton(
                    if (canContinue) "NEW RUN" else "HIT THE ROAD",
                    onNewRun,
                    style = if (canContinue) BtnStyle.Secondary else BtnStyle.Primary,
                    modifier = Modifier.width(190.dp).height(52.dp)
                )
            }

            Spacer(Modifier.height(28.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatCard("BEST", String.format("%.1f km", profile.bestDistanceKm))
                StatCard("RUNS", profile.totalRuns.toString())
                StatCard("TOTAL", String.format("%.1f km", profile.totalDistanceKm))
            }
        }
    }
}

@Composable
private fun StatCard(label: String, value: String) {
    Column(
        Modifier
            .background(GameColors.panel.copy(alpha = 0.75f), RoundedCornerShape(10.dp))
            .border(1.dp, GameColors.outline, RoundedCornerShape(10.dp))
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Text(label, color = GameColors.textDim, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp)
        Spacer(Modifier.height(2.dp))
        Text(value, color = GameColors.accent, fontSize = 20.sp, fontWeight = FontWeight.Bold)
    }
}
