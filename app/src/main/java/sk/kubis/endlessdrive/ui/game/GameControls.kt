package sk.kubis.endlessdrive.ui.game

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import sk.kubis.endlessdrive.domain.model.ThrottleMode
import sk.kubis.endlessdrive.domain.model.ThrottleSlide
import sk.kubis.endlessdrive.ui.theme.GameColors

/**
 * HillRush-štýl: brzda/cúvanie vľavo, plyn vpravo, parkovacia brzda pri pedáli.
 */
@Composable
fun GameControls(
    onThrottle: (Float) -> Unit,
    onBrakeChanged: (Boolean) -> Unit,
    onStop: () -> Unit,
    buildingNearby: Boolean = false,
    throttleMode: ThrottleMode = ThrottleMode.BINARY,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 14.dp)) {
        Row(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxSize(),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            PedalButton(
                tint = Color(0xFFD9584A),
                label = "Brake / reverse",
                wide = true,
                onPressChanged = onBrakeChanged
            )
            if (throttleMode == ThrottleMode.SLIDE) {
                ThrottleSlidePedal(
                    tint = Color(0xFF7CB86A),
                    onThrottle = onThrottle
                )
            } else {
                PedalButton(
                    tint = Color(0xFF7CB86A),
                    label = "Throttle",
                    wide = false,
                    onPressChanged = { pressed -> onThrottle(if (pressed) 1f else 0f) }
                )
            }
        }
        // Ručná brzda patrí k pedálom. Sedí tesne vľavo od plynu a jej stred
        // je vo výške stredu pedála, takže neprekrýva auto ani dashboard.
        ParkingButton(
            buildingNearby = buildingNearby,
            onClick = onStop,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(x = (-132).dp, y = (-24).dp)
        )
    }
}
/** Kompaktná kontrolka/tlačidlo ručnej brzdy podľa reálneho symbolu (P). */
@Composable
private fun ParkingButton(
    buildingNearby: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Rovnaká tlmená červená ako brzda; zvýraznenie pri budove je v obryse,
    // nie v agresívnej svietiacej výplni.
    val tint = if (buildingNearby) GameColors.accent else Color(0xFFD9584A)
    Column(
        modifier
            .width(72.dp)
            .height(70.dp)
            .background(
                Brush.verticalGradient(
                    listOf(tint.copy(alpha = 0.14f), Color(0xD91B1816))
                ),
                RoundedCornerShape(18.dp)
            )
            .border(1.5.dp, tint.copy(alpha = if (buildingNearby) 0.82f else 0.52f), RoundedCornerShape(18.dp))
            .clickable(onClickLabel = if (buildingNearby) "Park and search" else "Park") {
                onClick()
            }
            .semantics {
                contentDescription = if (buildingNearby) "Park to search building" else "Parking brake"
            }
            .padding(top = 5.dp, bottom = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AutomotiveIconView(
            AutomotiveIcon.PARK,
            tint.copy(alpha = if (buildingNearby) 0.95f else 0.78f),
            Modifier.size(44.dp)
        )
        Text(
            if (buildingNearby) "SEARCH" else "PARK",
            color = tint.copy(alpha = if (buildingNearby) 0.95f else 0.72f),
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

/**
 * Pedál ako pedál – šliapadlo s ryhovaním a ramenom, nie textové tlačidlo.
 * Brzdový je široký (v aute je tiež), plynový úzky a vysoký.
 */
@Composable
private fun PedalButton(
    tint: Color,
    label: String,
    wide: Boolean,
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
                        tint.copy(alpha = if (pressed) 0.55f else 0.26f),
                        tint.copy(alpha = 0.06f)
                    )
                ),
                shape = CircleShape
            )
            .border(2.dp, tint.copy(alpha = if (pressed) 0.9f else 0.40f), CircleShape)
            .semantics { contentDescription = label }
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
        Canvas(Modifier.size(76.dp)) {
            val w = size.width
            val h = size.height
            // Rameno pedála vedie od podlahy nahor k šliapadlu.
            val padW = if (wide) w * 0.62f else w * 0.40f
            val padH = if (wide) h * 0.52f else h * 0.72f
            val padLeft = (w - padW) / 2f
            val padTop = (h - padH) / 2f
            val armColor = Color(0xFF2A2622)
            drawLine(
                armColor,
                Offset(w * 0.5f, padTop + padH),
                Offset(w * 0.5f, h),
                strokeWidth = w * 0.10f,
                cap = StrokeCap.Round
            )
            // Gumené šliapadlo.
            drawRoundRect(
                color = Color(0xFF1E1B18),
                topLeft = Offset(padLeft, padTop),
                size = Size(padW, padH),
                cornerRadius = CornerRadius(w * 0.06f)
            )
            drawRoundRect(
                color = tint.copy(alpha = if (pressed) 0.95f else 0.75f),
                topLeft = Offset(padLeft, padTop),
                size = Size(padW, padH),
                cornerRadius = CornerRadius(w * 0.06f),
                style = Stroke(width = w * 0.035f)
            )
            // Ryhovanie proti šmyku.
            val grooves = if (wide) 4 else 5
            for (i in 1..grooves) {
                val gy = padTop + padH * i / (grooves + 1f)
                drawLine(
                    Color.White.copy(alpha = 0.16f),
                    Offset(padLeft + padW * 0.16f, gy),
                    Offset(padLeft + padW * 0.84f, gy),
                    strokeWidth = h * 0.026f,
                    cap = StrokeCap.Round
                )
            }
        }
    }
}

/**
 * Zvislý plyn: prst odspodu nahor perie ťah. Uvoľnenie = 0.
 */
@Composable
private fun ThrottleSlidePedal(
    tint: Color,
    onThrottle: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    var amount by remember { mutableStateOf(0f) }
    val fill by animateFloatAsState(amount, label = "throttleSlide")
    val throttleCallback by rememberUpdatedState(onThrottle)

    Box(
        modifier = modifier
            .width(72.dp)
            .height(196.dp)
            .background(
                Brush.verticalGradient(
                    listOf(tint.copy(alpha = 0.10f), Color(0xD91B1816))
                ),
                RoundedCornerShape(22.dp)
            )
            .border(2.dp, tint.copy(alpha = if (amount > 0.04f) 0.9f else 0.40f), RoundedCornerShape(22.dp))
            .semantics { contentDescription = "Throttle slide" }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val top = 0f
                    val bottom = size.height.toFloat()
                    fun emit(y: Float) {
                        val next = ThrottleSlide.amount(y, top, bottom)
                        amount = next
                        throttleCallback(next)
                    }
                    emit(down.position.y)
                    do {
                        val event = awaitPointerEvent()
                        event.changes.firstOrNull()?.let { emit(it.position.y) }
                    } while (event.changes.any { it.pressed })
                    amount = 0f
                    throttleCallback(0f)
                }
            },
        contentAlignment = Alignment.BottomCenter
    ) {
        Canvas(Modifier.fillMaxSize().padding(10.dp)) {
            val w = size.width
            val h = size.height
            val trackLeft = w * 0.32f
            val trackW = w * 0.36f
            drawRoundRect(
                Color(0xFF1E1B18),
                topLeft = Offset(trackLeft, 0f),
                size = Size(trackW, h),
                cornerRadius = CornerRadius(w * 0.12f)
            )
            val fillH = h * fill.coerceIn(0f, 1f)
            if (fillH > 1f) {
                drawRoundRect(
                    tint.copy(alpha = 0.85f),
                    topLeft = Offset(trackLeft, h - fillH),
                    size = Size(trackW, fillH),
                    cornerRadius = CornerRadius(w * 0.12f)
                )
            }
            val knobY = h - fillH
            drawCircle(
                tint.copy(alpha = if (amount > 0.04f) 0.95f else 0.55f),
                radius = w * 0.28f,
                center = Offset(w * 0.5f, knobY.coerceIn(w * 0.28f, h - w * 0.28f))
            )
        }
    }
}
