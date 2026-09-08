package sk.kubis.endlessdrive.ui.leaderboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import sk.kubis.endlessdrive.domain.repository.PlayerProfile
import sk.kubis.endlessdrive.ui.theme.BtnStyle
import sk.kubis.endlessdrive.ui.theme.GameButton
import sk.kubis.endlessdrive.ui.theme.GameColors
import sk.kubis.endlessdrive.ui.theme.GamePanel

@Composable
fun PlayerProfileScreen(
    profile: PlayerProfile,
    required: Boolean,
    suggestedNickname: String? = null,
    onSave: (nickname: String, countryCode: String) -> Unit,
    onBack: (() -> Unit)? = null
) {
    var nickname by remember(profile.nickname) { mutableStateOf(profile.nickname) }
    var countryCode by remember(profile.countryCode) { mutableStateOf(profile.countryCode) }
    val cleanNickname = nickname.trim()
    val cleanCountry = countryCode.trim().uppercase()
    val valid = cleanNickname.length in 2..18 && cleanCountry.length == 2 &&
        cleanCountry.all { it in 'A'..'Z' }

    Box(
        Modifier.fillMaxSize().background(GameColors.panelSoft),
        contentAlignment = Alignment.Center
    ) {
        GamePanel(
            title = "DRIVER PROFILE",
            subtitle = if (required) "Set your identity before the first run." else "Your name and country shown on the road.",
            modifier = Modifier.widthIn(max = 520.dp).padding(20.dp),
            fillHeight = false,
            onClose = if (required) null else onBack
        ) {
            Text("NICKNAME", color = GameColors.accent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = nickname,
                onValueChange = { nickname = it.take(18) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("e.g. ROADRUNNER") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii)
            )
            if (!suggestedNickname.isNullOrBlank() && suggestedNickname != nickname) {
                Spacer(Modifier.height(6.dp))
                GameButton(
                    "USE PLAY GAMES NAME",
                    { nickname = suggestedNickname.take(18) },
                    style = BtnStyle.Ghost,
                    compact = true
                )
            }
            Spacer(Modifier.height(16.dp))
            Text("COUNTRY", color = GameColors.accent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = countryCode,
                    onValueChange = { countryCode = it.filter(Char::isLetter).take(2).uppercase() },
                    modifier = Modifier.width(110.dp),
                    singleLine = true,
                    placeholder = { Text("SK") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii)
                )
                Spacer(Modifier.width(14.dp))
                Text(countryFlag(cleanCountry), fontSize = 30.sp)
                Spacer(Modifier.width(10.dp))
                Text("Use a two-letter ISO code", color = GameColors.textDim, fontSize = 12.sp)
            }
            Spacer(Modifier.height(14.dp))
            Text(
                "Play Games can supply the display name after the online service is connected. Until then, this local profile is used.",
                color = GameColors.textDim,
                fontSize = 12.sp
            )
            Spacer(Modifier.height(20.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (!required) {
                    GameButton("CANCEL", onBack ?: {}, Modifier.weight(1f), style = BtnStyle.Ghost)
                }
                GameButton(
                    "SAVE PROFILE",
                    { onSave(cleanNickname, cleanCountry) },
                    Modifier.weight(1f),
                    style = BtnStyle.Primary,
                    enabled = valid
                )
            }
        }
    }
}
