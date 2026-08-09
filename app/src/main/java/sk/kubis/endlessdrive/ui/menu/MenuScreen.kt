package sk.kubis.endlessdrive.ui.menu

import androidx.compose.foundation.background
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import sk.kubis.endlessdrive.domain.repository.PlayerProfile

@Composable
fun MenuScreen(
    profile: PlayerProfile,
    onPlay: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF1A1612),
                        Color(0xFF2B2418),
                        Color(0xFF3A4A3A)
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(horizontal = 48.dp, vertical = 32.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "ENDLESS DRIVE",
                style = MaterialTheme.typography.displayLarge,
                color = Color(0xFFE8DFD0)
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Nekonečná cesta. Tvoje auto. Nevieš, čo je za horizontom.",
                style = MaterialTheme.typography.bodyLarge,
                color = Color(0xFFC8BFAE)
            )
            Spacer(Modifier.height(28.dp))
            Button(
                onClick = onPlay,
                shape = RoundedCornerShape(6.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFB85C38),
                    contentColor = Color(0xFFFFF8F0)
                ),
                modifier = Modifier.width(200.dp).height(52.dp)
            ) {
                Text("VYRAZIŤ", style = MaterialTheme.typography.labelLarge)
            }
            Spacer(Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                Stat("REKORD", String.format("%.1f km", profile.bestDistanceKm))
                Stat("JÁZD", profile.totalRuns.toString())
                Stat("SPOLU", String.format("%.1f km", profile.totalDistanceKm))
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelLarge, color = Color(0xFF8A7F6E))
        Text(value, style = MaterialTheme.typography.titleLarge, color = Color(0xFFE8DFD0))
    }
}
