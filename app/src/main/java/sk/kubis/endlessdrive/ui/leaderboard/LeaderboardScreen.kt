package sk.kubis.endlessdrive.ui.leaderboard

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import sk.kubis.endlessdrive.domain.repository.PlayerProfile
import sk.kubis.endlessdrive.playgames.PlayGamesService
import sk.kubis.endlessdrive.ui.theme.GameColors
import sk.kubis.endlessdrive.ui.theme.GamePanel

@Composable
fun LeaderboardScreen(
    profile: PlayerProfile,
    playGames: PlayGamesService? = null,
    activity: Activity? = null,
    onEditProfile: () -> Unit,
    onBack: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(LeaderboardTab.WORLD) }
    val localEntry = remember(profile) {
        LeaderboardEntry(
            rank = 1,
            nickname = profile.nickname.ifBlank { "DRIVER" },
            countryCode = profile.countryCode,
            distanceKm = profile.bestDistanceKm,
            timeSeconds = profile.bestTimeSeconds,
            isCurrentPlayer = true
        )
    }
    var onlineEntries by remember { mutableStateOf<List<LeaderboardEntry>?>(null) }
    var onlineError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(selectedTab, playGames, activity, profile.bestDistanceKm) {
        onlineEntries = null
        onlineError = null
        if (playGames != null && activity != null) {
            playGames.loadScores(activity, selectedTab) { entries, error ->
                onlineEntries = entries
                onlineError = error
            }
        }
    }
    val displayEntries = onlineEntries?.takeIf { it.isNotEmpty() } ?: listOf(localEntry)

    Box(Modifier.fillMaxSize().background(GameColors.panelSoft), contentAlignment = Alignment.Center) {
        GamePanel(
            title = "LEADERBOARD",
            subtitle = "Distance first · time shown for every run",
            modifier = Modifier.widthIn(max = 760.dp).padding(20.dp),
            onClose = onBack
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LeaderboardTab.entries.forEach { tab ->
                    TabButton(tab, selectedTab == tab) { selectedTab = tab }
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(
                Modifier.fillMaxWidth().background(Color(0x6610181D), RoundedCornerShape(8.dp)).padding(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("${countryFlag(profile.countryCode)}  ${profile.nickname.ifBlank { "DRIVER" }}", color = GameColors.text, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Spacer(Modifier.weight(1f))
                Text("PERSONAL BEST", color = GameColors.textDim, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(14.dp))
            LeaderboardHeader()
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
                displayEntries.forEach { entry -> LeaderboardRow(entry) }
                Spacer(Modifier.height(10.dp))
                Text(
                    onlineError ?: when (selectedTab) {
                        LeaderboardTab.FRIENDS -> "Friends scores are loaded from Play Games when the service is connected."
                        LeaderboardTab.COUNTRY -> "Country rankings need a country-aware backend; Play Games offers Friends and Public collections."
                        LeaderboardTab.WORLD -> "World scores are loaded from the public Play Games leaderboard when configured."
                    },
                    color = GameColors.textDim,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Text(
                    "EDIT PROFILE",
                    color = GameColors.accent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable(onClick = onEditProfile).padding(8.dp)
                )
            }
        }
    }
}

@Composable
private fun RowScope.TabButton(tab: LeaderboardTab, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.weight(1f)
            .background(if (selected) GameColors.accent else GameColors.panelHigh, RoundedCornerShape(8.dp))
            .border(1.dp, if (selected) GameColors.accent else GameColors.outline, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 11.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            tab.label,
            color = if (selected) Color(0xFF211B13) else GameColors.text,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.8.sp
        )
    }
}

@Composable
private fun LeaderboardHeader() {
    Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("#", color = GameColors.textDim, fontSize = 10.sp, modifier = Modifier.width(28.dp))
        Text("PLAYER", color = GameColors.textDim, fontSize = 10.sp, modifier = Modifier.weight(1f))
        Text("DISTANCE", color = GameColors.textDim, fontSize = 10.sp, modifier = Modifier.width(82.dp))
        Text("TIME", color = GameColors.textDim, fontSize = 10.sp, modifier = Modifier.width(72.dp))
    }
}

@Composable
private fun LeaderboardRow(entry: LeaderboardEntry) {
    Row(
        Modifier.fillMaxWidth()
            .background(if (entry.isCurrentPlayer) Color(0x553C3020) else Color(0x3310181D), RoundedCornerShape(9.dp))
            .border(1.dp, if (entry.isCurrentPlayer) GameColors.accent.copy(alpha = 0.7f) else GameColors.outline.copy(alpha = 0.45f), RoundedCornerShape(9.dp))
            .padding(horizontal = 10.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(entry.rank.toString(), color = GameColors.accent, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(28.dp))
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Text(countryFlag(entry.countryCode), fontSize = 21.sp)
            Spacer(Modifier.width(8.dp))
            Text(entry.nickname, color = GameColors.text, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
        Text(String.format("%.1f km", entry.distanceKm), color = GameColors.text, fontSize = 12.sp, modifier = Modifier.width(82.dp))
        Text(formatLeaderboardTime(entry.timeSeconds), color = GameColors.textDim, fontSize = 12.sp, modifier = Modifier.width(72.dp))
    }
}
