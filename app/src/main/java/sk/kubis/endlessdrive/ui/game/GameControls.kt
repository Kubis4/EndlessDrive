package sk.kubis.endlessdrive.ui.game

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import sk.kubis.endlessdrive.ui.theme.GameButton
import sk.kubis.endlessdrive.ui.theme.GameColors

/**
 * HillRush-štýl: brzda/cúvanie vľavo, plyn vpravo, ZASTAVIŤ v strede.
 */
@Composable
fun GameControls(
    onGasChanged: (Boolean) -> Unit,
    onBrakeChanged: (Boolean) -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 14.dp)) {
        Row(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxSize(),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            PedalButton(
                glyph = "◀",
                tint = Color(0xFFD9584A),
                label = "BRAKE / REVERSE",
                onPressChanged = onBrakeChanged
            )
            PedalButton(
                glyph = "▶",
                tint = Color(0xFF7CB86A),
                label = "THROTTLE",
                onPressChanged = onGasChanged
            )
        }
        GameButton(
            "STOP",
            onStop,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp)
        )
    }
}

@Composable
private fun PedalButton(
    glyph: String,
    tint: Color,
    label: String,
    onPressChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (pressed) 0.93f else 1f, label = "pedalScale")
    val pressCallback by rememberUpdatedState(onPressChanged)

    Box(
        modifier = modifier
            .size(118.dp)
            .scale(scale)
            .background(
                brush = Brush.radialGradient(
                    listOf(
                        tint.copy(alpha = if (pressed) 0.80f else 0.42f),
                        tint.copy(alpha = 0.10f)
                    )
                ),
                shape = CircleShape
            )
            .border(2.dp, tint.copy(alpha = if (pressed) 0.9f else 0.45f), CircleShape)
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
            Text(glyph, color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Bold)
            Text(
                label,
                color = GameColors.text.copy(alpha = 0.9f),
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
