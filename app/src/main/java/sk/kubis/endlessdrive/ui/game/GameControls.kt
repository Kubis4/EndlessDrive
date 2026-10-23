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
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import sk.kubis.endlessdrive.domain.model.ThrottleMode
import sk.kubis.endlessdrive.domain.model.ThrottleSlide
import sk.kubis.endlessdrive.ui.l10n.LocalCopy
import sk.kubis.endlessdrive.ui.l10n.brakeReverse
import sk.kubis.endlessdrive.ui.l10n.cruiseOff
import sk.kubis.endlessdrive.ui.l10n.cruiseOn
import sk.kubis.endlessdrive.ui.l10n.park
import sk.kubis.endlessdrive.ui.l10n.parkAndSearch
import sk.kubis.endlessdrive.ui.l10n.parkLabel
import sk.kubis.endlessdrive.ui.l10n.parkToSearch
import sk.kubis.endlessdrive.ui.l10n.parkingBrake
import sk.kubis.endlessdrive.ui.l10n.search
import sk.kubis.endlessdrive.ui.l10n.throttle
import sk.kubis.endlessdrive.ui.l10n.throttleSlideA11y
import sk.kubis.endlessdrive.ui.l10n.steerLeft
import sk.kubis.endlessdrive.ui.l10n.steerRight
import sk.kubis.endlessdrive.ui.theme.GameColors

/**
 * Left thumb steers; the right thumb operates the independent brake and throttle.
 */
@Composable
fun GameControls(
    onThrottle: (Float) -> Unit,
    onSteering: (Float) -> Unit,
    onBrakeChanged: (Boolean) -> Unit,
    onStop: () -> Unit,
    cruiseOn: Boolean = false,
    cruiseAvailable: Boolean = true,
    onToggleCruise: () -> Unit = {},
    buildingNearby: Boolean = false,
    throttleMode: ThrottleMode = ThrottleMode.BINARY,
    compact: Boolean = false,
    modifier: Modifier = Modifier
) {
    val copy = LocalCopy.current
    Box(
        modifier = modifier.fillMaxSize().padding(
            horizontal = if (compact) 10.dp else 18.dp,
            vertical = if (compact) 8.dp else 14.dp
        )
    ) {
        Row(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxSize(),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            SteeringPad(onSteering, compact)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
            PedalButton(
                tint = Color(0xFFD9584A),
                label = copy.brakeReverse,
                wide = true,
                diameter = if (compact) 96.dp else 118.dp,
                onPressChanged = onBrakeChanged
            )
            if (throttleMode == ThrottleMode.SLIDE) {
                ThrottleSlidePedal(
                    tint = Color(0xFF7CB86A),
                    compact = compact,
                    onThrottle = onThrottle
                )
            } else {
                PedalButton(
                    tint = Color(0xFF7CB86A),
                    label = copy.throttle,
                    wide = false,
                    diameter = if (compact) 96.dp else 118.dp,
                    onPressChanged = { pressed -> onThrottle(if (pressed) 1f else 0f) }
                )
            }
            }
        }
        // Ručná brzda patrí k pedálom. Sedí tesne vľavo od plynu a jej stred
        // je vo výške stredu pedála, takže neprekrýva auto ani dashboard.
        ParkingButton(
            buildingNearby = buildingNearby,
            onClick = onStop,
            compact = compact,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(
                    x = if (compact) (-112).dp else (-132).dp,
                    y = if (throttleMode == ThrottleMode.SLIDE) (-170).dp else if (compact) (-104).dp else (-126).dp
                )
        )
        // Tempomat je jazdný ovládač, preto patrí k plynu. Pri klasickom
        // pedáli sedí tesne nad ním; pri posuvnom plyne vedľa jeho hornej časti.
        if (cruiseAvailable) {
            CruiseControlButton(
                active = cruiseOn,
                onClick = onToggleCruise,
                compact = compact,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(
                        x = when {
                            compact -> (-24).dp
                            else -> (-30).dp
                        },
                        y = if (throttleMode == ThrottleMode.SLIDE) (-170).dp else if (compact) (-108).dp else (-130).dp
                    )
            )
        }
    }
}

@Composable
private fun SteeringPad(onSteering: (Float) -> Unit, compact: Boolean) {
    var left by remember { mutableStateOf(false) }
    var right by remember { mutableStateOf(false) }
    val callback by rememberUpdatedState(onSteering)
    val copy = LocalCopy.current
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SteeringArrow("←", copy.steerLeft, left, compact) {
            left=it; callback((if(right) 1f else 0f)-(if(left) 1f else 0f))
        }
        SteeringArrow("→", copy.steerRight, right, compact) {
            right=it; callback((if(right) 1f else 0f)-(if(left) 1f else 0f))
        }
    }
}

@Composable
private fun SteeringArrow(symbol:String,label:String,pressed:Boolean,compact:Boolean,onPressed:(Boolean)->Unit) {
    val callback by rememberUpdatedState(onPressed)
    Box(Modifier.size(if(compact) 66.dp else 80.dp,if(compact) 86.dp else 104.dp)
        .background(if(pressed) Color(0xCC4E6657) else Color(0xB319211F),RoundedCornerShape(20.dp))
        .border(1.5.dp,GameColors.accent.copy(alpha=if(pressed) 1f else 0.55f),RoundedCornerShape(20.dp))
        .semantics { contentDescription=label }
        .pointerInput(Unit) {
            try {
                awaitEachGesture {
                    val down=awaitFirstDown(requireUnconsumed=false)
                    down.consume(); callback(true)
                    while(true) {
                        val change=awaitPointerEvent().changes.firstOrNull { it.id==down.id } ?: break
                        change.consume()
                        if(!change.pressed) break
                    }
                    callback(false)
                }
            } finally { callback(false) }
        },contentAlignment=Alignment.Center) {
        Text(symbol,color=GameColors.text,fontSize=40.sp,fontWeight=FontWeight.Bold)
    }
}

/** Tempomat pri plyne: tlmený v pokoji, zelená kontrolka po zapnutí. */
@Composable
private fun CruiseControlButton(
    active: Boolean,
    onClick: () -> Unit,
    compact: Boolean,
    modifier: Modifier = Modifier
) {
    val copy = LocalCopy.current
    val activeColor = Color(0xFF45C96B)
    val tint = if (active) activeColor else GameColors.text.copy(alpha = 0.78f)
    val shape = RoundedCornerShape(if (compact) 14.dp else 16.dp)
    Box(
        modifier = modifier
            .size(if (compact) 48.dp else 54.dp)
            .background(
                Brush.radialGradient(
                    listOf(
                        tint.copy(alpha = if (active) 0.30f else 0.10f),
                        Color(0xE61B1816)
                    )
                ),
                shape
            )
            .border(
                width = if (active) 1.8.dp else 1.dp,
                color = if (active) activeColor else GameColors.outline,
                shape = shape
            )
            .clickable(
                onClickLabel = if (active) copy.cruiseOff else copy.cruiseOn,
                onClick = onClick
            )
            .semantics {
                contentDescription = if (active) copy.cruiseOff else copy.cruiseOn
            },
        contentAlignment = Alignment.Center
    ) {
        AutomotiveIconView(
            AutomotiveIcon.CRUISE,
            tint,
            Modifier.size(if (compact) 31.dp else 36.dp)
        )
    }
}

/** Kompaktná kontrolka/tlačidlo ručnej brzdy podľa reálneho symbolu (P). */
@Composable
private fun ParkingButton(
    buildingNearby: Boolean,
    onClick: () -> Unit,
    compact: Boolean = false,
    modifier: Modifier = Modifier
) {
    val copy = LocalCopy.current
    // Rovnaká tlmená červená ako brzda; zvýraznenie pri budove je v obryse,
    // nie v agresívnej svietiacej výplni.
    val tint = if (buildingNearby) GameColors.accent else Color(0xFFD9584A)
    Column(
        modifier
            .width(if (compact) 60.dp else 72.dp)
            .height(if (compact) 58.dp else 70.dp)
            .background(
                Brush.verticalGradient(
                    listOf(tint.copy(alpha = 0.14f), Color(0xD91B1816))
                ),
                RoundedCornerShape(18.dp)
            )
            .border(1.5.dp, tint.copy(alpha = if (buildingNearby) 0.82f else 0.52f), RoundedCornerShape(18.dp))
            .clickable(onClickLabel = if (buildingNearby) copy.parkAndSearch else copy.parkLabel) {
                onClick()
            }
            .semantics {
                contentDescription = if (buildingNearby) copy.parkToSearch else copy.parkingBrake
            }
            .padding(top = 5.dp, bottom = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AutomotiveIconView(
            AutomotiveIcon.PARK,
            tint.copy(alpha = if (buildingNearby) 0.95f else 0.78f),
            Modifier.size(if (compact) 34.dp else 44.dp)
        )
        Text(
            if (buildingNearby) copy.search else copy.park,
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
    diameter: Dp = 118.dp,
    onPressChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (pressed) 0.93f else 1f, label = "pedalScale")
    val pressCallback by rememberUpdatedState(onPressChanged)

    Box(
        modifier = modifier
            .size(diameter)
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
                try { awaitEachGesture {
                    val down=awaitFirstDown(requireUnconsumed = false)
                    pressed = true
                    pressCallback(true)
                    while (awaitPointerEvent().changes.any { it.id==down.id && it.pressed }) { }
                    pressed = false
                    pressCallback(false)
                } } finally { pressed=false; pressCallback(false) }
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.size(diameter * 0.64f)) {
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
    compact: Boolean = false,
    modifier: Modifier = Modifier
) {
    val copy = LocalCopy.current
    var amount by remember { mutableStateOf(0f) }
    val fill by animateFloatAsState(amount, label = "throttleSlide")
    val throttleCallback by rememberUpdatedState(onThrottle)

    Box(
        modifier = modifier
            .width(if (compact) 62.dp else 72.dp)
            .height(if (compact) 158.dp else 196.dp)
            .background(
                Brush.verticalGradient(
                    listOf(tint.copy(alpha = 0.10f), Color(0xD91B1816))
                ),
                RoundedCornerShape(22.dp)
            )
            .border(2.dp, tint.copy(alpha = if (amount > 0.04f) 0.9f else 0.40f), RoundedCornerShape(22.dp))
            .semantics { contentDescription = copy.throttleSlideA11y }
            .pointerInput(Unit) {
                try { awaitEachGesture {
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
                        event.changes.firstOrNull { it.id==down.id }?.let { emit(it.position.y) }
                    } while (event.changes.any { it.id==down.id && it.pressed })
                    amount = 0f
                    throttleCallback(0f)
                } } finally { amount=0f; throttleCallback(0f) }
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

/** Náhľad nastavenia plynu – tvarovo zodpovedá ovládaču v hre. */
@Composable
fun ThrottleModePreview(mode: ThrottleMode, modifier: Modifier = Modifier) {
    val tint = Color(0xFF7CB86A)
    Box(modifier, contentAlignment = Alignment.Center) {
        if (mode == ThrottleMode.BINARY) {
            Box(
                Modifier
                    .size(82.dp)
                    .background(tint.copy(alpha = 0.16f), CircleShape)
                    .border(1.5.dp, tint.copy(alpha = 0.65f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Canvas(Modifier.size(48.dp, 66.dp)) {
                    val padLeft = size.width * 0.31f
                    val padWidth = size.width * 0.38f
                    drawLine(
                        Color(0xFF2A2622),
                        Offset(size.width * 0.5f, size.height * 0.76f),
                        Offset(size.width * 0.5f, size.height),
                        strokeWidth = size.width * 0.10f,
                        cap = StrokeCap.Round
                    )
                    drawRoundRect(
                        Color(0xFF1E1B18),
                        Offset(padLeft, 0f),
                        Size(padWidth, size.height * 0.78f),
                        CornerRadius(5.dp.toPx())
                    )
                    drawRoundRect(
                        tint.copy(alpha = 0.78f),
                        Offset(padLeft, 0f),
                        Size(padWidth, size.height * 0.78f),
                        CornerRadius(5.dp.toPx()),
                        style = Stroke(2.dp.toPx())
                    )
                    repeat(4) { index ->
                        val y = size.height * (0.15f + index * 0.14f)
                        drawLine(
                            Color.White.copy(alpha = 0.18f),
                            Offset(padLeft + padWidth * 0.18f, y),
                            Offset(padLeft + padWidth * 0.82f, y),
                            1.5.dp.toPx(),
                            StrokeCap.Round
                        )
                    }
                }
            }
        } else {
            Box(
                Modifier
                    .width(48.dp)
                    .height(88.dp)
                    .background(Color(0xFF1E1B18), RoundedCornerShape(16.dp))
                    .border(1.5.dp, tint.copy(alpha = 0.7f), RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) {
                Canvas(Modifier.width(28.dp).height(70.dp)) {
                    val trackLeft = size.width * 0.36f
                    val trackWidth = size.width * 0.28f
                    drawRoundRect(
                        Color(0xFF34302C),
                        Offset(trackLeft, 0f),
                        Size(trackWidth, size.height),
                        CornerRadius(trackWidth * 0.5f)
                    )
                    drawRoundRect(
                        tint.copy(alpha = 0.78f),
                        Offset(trackLeft, size.height * 0.38f),
                        Size(trackWidth, size.height * 0.62f),
                        CornerRadius(trackWidth * 0.5f)
                    )
                    drawCircle(tint, size.width * 0.28f, Offset(size.width * 0.5f, size.height * 0.38f))
                }
            }
        }
    }
}
