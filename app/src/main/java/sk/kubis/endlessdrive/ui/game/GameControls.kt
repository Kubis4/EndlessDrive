package sk.kubis.endlessdrive.ui.game

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

/**
 * HillRush-štýl: brzda/cúvanie vľavo, plyn vpravo.
 */
@Composable
fun GameControls(
    onGasChanged: (Boolean) -> Unit,
    onBrakeChanged: (Boolean) -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 16.dp)) {
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxSize(),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            PedalButton(
                icon = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                tint = Color(0xFFEF5350),
                label = "BRZDA / CÚVAJ",
                onPressChanged = onBrakeChanged
            )
            PedalButton(
                icon = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                tint = Color(0xFF66BB6A),
                label = "PLYN",
                onPressChanged = onGasChanged
            )
        }
        TextButton(
            onClick = onStop,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp)
        ) {
            Text("ZASTAVIŤ", color = Color(0xFFE8DFD0))
        }
    }
}

@Composable
private fun PedalButton(
    icon: ImageVector,
    tint: Color,
    label: String,
    onPressChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (pressed) 0.92f else 1f, label = "pedalScale")
    val pressCallback by rememberUpdatedState(onPressChanged)

    Box(
        modifier = modifier
            .size(112.dp)
            .scale(scale)
            .background(
                brush = Brush.radialGradient(
                    listOf(
                        tint.copy(alpha = if (pressed) 0.85f else 0.55f),
                        tint.copy(alpha = 0.18f)
                    )
                ),
                shape = CircleShape
            )
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    pressed = true
                    pressCallback(true)
                    do {
                        val event = awaitPointerEvent()
                    } while (event.changes.any { it.pressed })
                    pressed = false
                    pressCallback(false)
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = Color.White,
                modifier = Modifier.size(52.dp).alpha(0.9f)
            )
            Text(label, color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.labelLarge)
        }
    }
}
