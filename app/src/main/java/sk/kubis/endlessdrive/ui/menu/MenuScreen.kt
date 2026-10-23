package sk.kubis.endlessdrive.ui.menu

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.style.TextGeometricTransform
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Surface
import androidx.compose.foundation.BorderStroke
import sk.kubis.endlessdrive.R
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import sk.kubis.endlessdrive.domain.repository.PlayerProfile
import sk.kubis.endlessdrive.game.Journey
import sk.kubis.endlessdrive.ui.l10n.LocalCopy
import sk.kubis.endlessdrive.ui.theme.*

private val MenuIvory = Color(0xFFF5ECD7)
private val MenuAmber = Color(0xFFE9BA68)
private val MenuTitle = FontFamily(Font(R.font.anton_regular))

@Composable
fun MenuScreen(
    profile: PlayerProfile,
    canContinue: Boolean,
    runDistanceKm: Float,
    runClock: String,
    onContinue: () -> Unit,
    onNewRun: () -> Unit,
    onSettings: () -> Unit,
    debugActive: Boolean = false,
    onLeaderboard: () -> Unit = {},
    onTutorial: () -> Unit = {},
    onGarage: () -> Unit = {}
) {
    val copy = LocalCopy.current
    var confirmNewRun by remember { mutableStateOf(false) }
    CompositionLocalProvider(LocalTextStyle provides LocalTextStyle.current.copy(fontFamily = MenuCondensedFont)) {
    BoxWithConstraints(Modifier.fillMaxSize().background(Color(0xFF25271E))) {
        val compact = maxHeight < 470.dp
        val portrait = maxWidth < maxHeight
        AlpineRoadBackground()
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(
            0f to Color(0x880D100B), 0.36f to Color.Transparent,
            0.7f to Color.Transparent, 1f to Color(0x300D100B))))
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(
            0f to Color.Transparent, 0.62f to Color.Transparent, 1f to Color(0x990B0D09))))

        val actions: @Composable () -> Unit = {
            RunCard(canContinue, runDistanceKm, runClock, compact, debugActive,
                onContinue, { if (canContinue) confirmNewRun = true else onNewRun() },
                onSettings, onLeaderboard, onTutorial, onGarage)
        }
        if (portrait) {
            FitToBounds(
                Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(24.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    MenuBrand(true)
                    actions()
                    ExpeditionStats(profile)
                }
            }
        } else {
            Row(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = if (compact) 28.dp else 48.dp,
                    vertical = if (compact) 18.dp else 32.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                FittedMenuIdentity(profile, compact, Modifier.fillMaxHeight().weight(0.36f))
                Spacer(Modifier.weight(0.28f))
                FitToBounds(Modifier.weight(0.36f).widthIn(max = 360.dp).fillMaxHeight()) {
                    actions()
                }
            }
        }
        if (confirmNewRun) {
            Scrim(onDismiss = { confirmNewRun = false })
            GamePanel(
                copy.replaceRunTitle,
        Modifier.align(Alignment.Center).fillMaxWidth(0.86f).widthIn(max = 430.dp).padding(20.dp),
                fillHeight = false,
                onClose = { confirmNewRun = false },
                titleFontFamily = MenuCondensedFont
            ) {
                Text(copy.replaceRunBody,
                    color = GameColors.textDim, fontFamily = MenuCondensedFont,
                    fontSize = 18.sp, letterSpacing = 0.5.sp)
                Spacer(Modifier.height(18.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MenuButton(
                        copy.keepRun, { confirmNewRun = false },
                        Modifier.weight(1f).height(54.dp), style = BtnStyle.Secondary
                    )
                    MenuButton(
                        copy.replaceRun,
                        { confirmNewRun = false; onNewRun() },
                        Modifier.weight(1f).height(54.dp),
                        style = BtnStyle.Danger
                    )
                }
            }
        }
    }
}

}

@Composable
private fun FitToBounds(modifier: Modifier, content: @Composable () -> Unit) {
    Layout(modifier = modifier, content = content) { measurables, constraints ->
        val width = constraints.maxWidth.coerceAtLeast(1)
        val height = constraints.maxHeight.coerceAtLeast(1)
        val child = measurables.first().measure(
            Constraints(maxWidth = width, maxHeight = Constraints.Infinity)
        )
        val scale = minOf(1f, width.toFloat() / child.width.coerceAtLeast(1),
            height.toFloat() / child.height.coerceAtLeast(1))
        layout(width, height) {
            val x = ((width - child.width * scale) / 2f).toInt()
            val y = ((height - child.height * scale) / 2f).toInt().coerceAtLeast(0)
            child.placeWithLayer(x, y) {
                transformOrigin = TransformOrigin(0f, 0f)
                scaleX = scale
                scaleY = scale
            }
        }
    }
}

@Composable
private fun FittedMenuIdentity(profile: PlayerProfile, compact: Boolean, modifier: Modifier) {
    // Measure the real fonts (including the user's font scale) before fitting the entire
    // identity block. No scroll state, repeated recomposition or guessed text heights.
    Layout(modifier = modifier, content = {
        MenuBrand(compact)
        ExpeditionStats(profile)
    }) { measurables, constraints ->
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val naturalWidth = maxOf(width, 280.dp.roundToPx())
        val naturalConstraints = Constraints.fixedWidth(naturalWidth)
        val brand = measurables[0].measure(naturalConstraints)
        val stats = measurables[1].measure(naturalConstraints)
        val gap = 16.dp.roundToPx()
        val naturalHeight = brand.height + gap + stats.height
        val scale = minOf(1f, width.toFloat() / naturalWidth,
            height.toFloat() / naturalHeight.coerceAtLeast(1))
        layout(width, height) {
            brand.placeWithLayer(0, 0) {
                transformOrigin = TransformOrigin(0f, 0f)
                scaleX = scale
                scaleY = scale
            }
            stats.placeWithLayer(0, (height - stats.height * scale).toInt().coerceAtLeast(0)) {
                transformOrigin = TransformOrigin(0f, 0f)
                scaleX = scale
                scaleY = scale
            }
        }
    }
}

@Composable
private fun MenuBrand(compact: Boolean) {
    val copy = LocalCopy.current
    BoxWithConstraints(Modifier.fillMaxWidth()) {
      val headlineSize = (maxWidth.value / 2.8f).coerceIn(48f, 130f)
      Column {
        Text(copy.openRoad, color = MenuAmber, fontSize = 13.sp,
            letterSpacing = 3.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(10.dp))
        Box(Modifier.width(48.dp).height(1.dp).background(MenuAmber.copy(alpha = 0.65f)))
        Spacer(Modifier.height(if (compact) 12.dp else 22.dp))
        Text("ENDLESS\nDRIVE", color = MenuIvory, fontFamily = MenuTitle,
            style = LocalTextStyle.current.copy(textGeometricTransform = TextGeometricTransform(scaleX = 0.76f)),
            fontSize = headlineSize.sp,
            lineHeight = (headlineSize * 0.91f).sp,
            fontWeight = FontWeight.Normal, letterSpacing = 0.sp)
        Spacer(Modifier.height(14.dp))
        Text(copy.keepEngineAlive, color = MenuAmber,
            fontSize = if (compact) 13.sp else 16.sp, letterSpacing = 1.6.sp)
      }
    }
}

@Composable
private fun ExpeditionStats(profile: PlayerProfile) {
    val copy = LocalCopy.current
    Column {
        Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
            Column {
                Text(copy.best, color = MenuAmber, fontSize = 12.sp, letterSpacing = 1.5.sp)
                Text(String.format("%.1f KM", profile.bestDistanceKm), color = MenuIvory,
                    fontFamily = MenuCondensedFont, fontSize = 23.sp)
            }
            Column {
                Text(copy.relays, color = MenuAmber, fontSize = 12.sp, letterSpacing = 1.5.sp)
                Text("${profile.relayNodes}/${Journey.goals.size}", color = MenuIvory,
                    fontFamily = MenuCondensedFont, fontSize = 23.sp)
            }
        }
        Spacer(Modifier.height(7.dp))
        Text(copy.bankedScrap(profile.bankedScrap), color = MenuIvory.copy(alpha = 0.8f),
            fontSize = 12.sp, letterSpacing = 0.7.sp)
    }
}

@Composable
private fun RunCard(
    canContinue: Boolean, distanceKm: Float, clock: String, compact: Boolean,
    debugActive: Boolean, onContinue: () -> Unit, onNewRun: () -> Unit,
    onSettings: () -> Unit, onLeaderboard: () -> Unit,
    onTutorial: () -> Unit, onGarage: () -> Unit
) {
    val copy = LocalCopy.current
    val shape = RoundedCornerShape(12.dp)
    Column(Modifier.fillMaxWidth()
        .background(Brush.verticalGradient(listOf(Color(0xDE24251E), Color(0xEB131711))), shape)
        .border(1.dp, MenuIvory.copy(alpha = 0.23f), shape)
        .padding(if (compact) 12.dp else 26.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (canContinue) copy.savedRun else copy.nextExpedition,
                color = MenuAmber, fontSize = 12.sp, letterSpacing = 1.7.sp,
                modifier = Modifier.weight(1f)
            )
            SettingsGearButton(onSettings, compact)
        }
        Spacer(Modifier.height(if (compact) 9.dp else 18.dp))
                Text(copy.roadIsCalling, color = MenuIvory, fontFamily = MenuCondensedFont,
            fontSize = if (compact) 20.sp else 27.sp)
        if (canContinue) {
            Row(Modifier.padding(vertical = if (compact) 4.dp else 16.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                Text(String.format("%.1f", distanceKm), color = MenuIvory,
                    fontFamily = MenuCondensedFont, fontSize = if (compact) 31.sp else 59.sp,
                    lineHeight = if (compact) 33.sp else 62.sp)
                Text(copy.kmClock(clock), color = MenuAmber, fontSize = 13.sp,
                    modifier = Modifier.padding(bottom = 6.dp))
            }
        } else {
            Text(copy.findParts, color = MenuIvory.copy(alpha = 0.78f),
                fontSize = 12.sp, modifier = Modifier.padding(vertical = 12.dp))
        }
        if (debugActive) {
            Text(copy.testingOptionsActive, color = GameColors.warn, fontSize = 12.sp,
                modifier = Modifier.padding(bottom = 6.dp))
        }
        MenuButton(if (canContinue) copy.continueRun else copy.startRun,
            if (canContinue) onContinue else onNewRun,
            Modifier.fillMaxWidth().height(if (compact) 48.dp else 58.dp), style = BtnStyle.Primary)
        if (canContinue) {
            Spacer(Modifier.height(8.dp))
            MenuButton(copy.newRun, onNewRun, Modifier.fillMaxWidth().height(48.dp), style = BtnStyle.Ghost)
        }
        Spacer(Modifier.height(8.dp))
        MenuButton(copy.garage, onGarage, Modifier.fillMaxWidth().height(48.dp), style = BtnStyle.Ghost)
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MenuButton(
                copy.records, onLeaderboard,
                Modifier.weight(1f).height(48.dp), style = BtnStyle.Ghost, fontSize = 18.sp
            )
            MenuButton(
                copy.howToPlay, onTutorial,
                Modifier.weight(1f).height(48.dp), style = BtnStyle.Ghost, fontSize = 18.sp
            )
        }
        Spacer(Modifier.height(if (compact) 7.dp else 10.dp))
        Text(
            copy.flashingWarning,
            color = MenuIvory.copy(alpha = 0.62f),
            fontSize = 9.sp,
            lineHeight = 12.sp,
            letterSpacing = 0.2.sp
        )
    }
}

/** Menu-only typography and amber treatment; gameplay controls keep their own styling. */
@Composable
private fun MenuButton(
    text: String, onClick: () -> Unit, modifier: Modifier, style: BtnStyle,
    fontSize: androidx.compose.ui.unit.TextUnit = 23.sp
) {
    val primary = style == BtnStyle.Primary
    Surface(onClick = onClick, modifier = modifier, shape = RoundedCornerShape(7.dp),
        color = if (primary) Color(0xFFECAF39) else Color.Transparent,
        contentColor = if (primary) Color(0xFF211B10) else MenuIvory,
        border = BorderStroke(1.dp, if (primary) Color(0xFFFFD383) else MenuIvory.copy(alpha = 0.26f))) {
        Box(Modifier.fillMaxSize().padding(horizontal = 8.dp), contentAlignment = Alignment.Center) {
            Text(text, fontFamily = MenuCondensedFont, fontSize = fontSize,
                fontWeight = FontWeight.Normal, letterSpacing = 1.4.sp, maxLines = 1,
                overflow = TextOverflow.Clip)
        }
    }
}

@Composable
private fun SettingsGearButton(onClick: () -> Unit, compact: Boolean) {
    val copy = LocalCopy.current
    val size = if (compact) 40.dp else 44.dp
    Surface(
        onClick = onClick,
        modifier = Modifier.size(size).semantics { contentDescription = copy.settings },
        shape = RoundedCornerShape(7.dp),
        color = Color.Transparent,
        contentColor = MenuIvory,
        border = BorderStroke(1.dp, MenuIvory.copy(alpha = 0.26f))
    ) {
        Image(
            painter = painterResource(R.drawable.ic_settings_gear),
            contentDescription = null,
            modifier = Modifier.padding(if (compact) 8.dp else 9.dp),
            contentScale = ContentScale.Fit
        )
    }
}
