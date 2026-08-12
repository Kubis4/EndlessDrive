package sk.kubis.endlessdrive.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Jednotná paleta a stavebné prvky herného UI.
 * Všetko na jednom mieste – obrazovky už nemiešajú vlastné hex hodnoty.
 */
object GameColors {
    val panel = Color(0xFF1B1712)
    val panelHigh = Color(0xFF272019)
    val panelSoft = Color(0xFF141210)
    val outline = Color(0xFF3D362C)
    val text = Color(0xFFEDE4D4)
    val textDim = Color(0xFF9C9182)
    val accent = Color(0xFFD2AE63)
    val ok = Color(0xFF7CB86A)
    val warn = Color(0xFFE0A33C)
    val danger = Color(0xFFD9584A)
    val info = Color(0xFF7FA8CC)
    val scrim = Color(0xE60D0B09)
    val hudBg = Color(0xB30C0A08)
}

/** Farba podľa stavu 0..1 (palivo, stav dielu…). */
fun levelColor(ratio: Float, low: Float = 0.15f, mid: Float = 0.35f): Color = when {
    ratio <= low -> GameColors.danger
    ratio <= mid -> GameColors.warn
    else -> GameColors.ok
}

/** Farba podľa čistoty kvapaliny. */
fun purityColor(purity: Float): Color = when {
    purity >= 0.92f -> GameColors.ok
    purity >= 0.75f -> Color(0xFFBFCB5C)
    purity >= 0.55f -> GameColors.warn
    else -> GameColors.danger
}

enum class BtnStyle { Primary, Secondary, Danger, Ghost }

@Composable
fun GameButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: BtnStyle = BtnStyle.Secondary,
    enabled: Boolean = true,
    compact: Boolean = false
) {
    val bg = when (style) {
        BtnStyle.Primary -> Color(0xFF4F6B3A)
        BtnStyle.Secondary -> GameColors.panelHigh
        BtnStyle.Danger -> Color(0xFF4E2A26)
        BtnStyle.Ghost -> Color.Transparent
    }
    val fg = when (style) {
        BtnStyle.Primary -> Color(0xFFF2FFE8)
        BtnStyle.Danger -> Color(0xFFF0A9A0)
        else -> GameColors.text
    }
    Box(
        modifier
            .background(
                if (enabled) bg else bg.copy(alpha = 0.4f),
                RoundedCornerShape(8.dp)
            )
            .border(
                width = 1.dp,
                color = if (style == BtnStyle.Ghost) GameColors.outline else Color.Transparent,
                shape = RoundedCornerShape(8.dp)
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(
                horizontal = if (compact) 10.dp else 16.dp,
                vertical = if (compact) 6.dp else 10.dp
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            color = if (enabled) fg else fg.copy(alpha = 0.45f),
            fontSize = if (compact) 12.sp else 14.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.6.sp,
            maxLines = 1
        )
    }
}

/** Štvorcové ikonové tlačidlo (svetlá, pauza, FPS…). */
@Composable
fun IconToggleButton(
    glyph: String,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null
) {
    Box(
        modifier
            .size(44.dp)
            .background(
                if (active) GameColors.accent else GameColors.panelHigh.copy(alpha = 0.92f),
                RoundedCornerShape(10.dp)
            )
            .border(1.dp, GameColors.outline, RoundedCornerShape(10.dp))
            .clickable(onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            glyph,
            color = if (active) Color(0xFF1B1712) else GameColors.text,
            fontSize = 18.sp
        )
    }
}

/**
 * Ukazovateľ do HUD: krátky názov, prúžok a hodnota.
 * [inner] kreslí druhý, tenší prúžok (napr. koľko z objemu je naozaj kvapalina).
 */
@Composable
fun StatBar(
    label: String,
    ratio: Float,
    value: String,
    color: Color,
    modifier: Modifier = Modifier,
    inner: Float = -1f,
    innerColor: Color = GameColors.ok,
    barWidth: androidx.compose.ui.unit.Dp = 54.dp
) {
    Column(modifier) {
        Text(label, color = GameColors.textDim, fontSize = 11.sp, letterSpacing = 0.6.sp, maxLines = 1)
        Spacer(Modifier.height(2.dp))
        Box(
            Modifier
                .width(barWidth)
                .height(6.dp)
                .background(Color(0xFF2A241D), RoundedCornerShape(3.dp))
        ) {
            Box(
                Modifier
                    .fillMaxWidth(ratio.coerceIn(0f, 1f))
                    .fillMaxHeight()
                    .background(color, RoundedCornerShape(3.dp))
            )
            if (inner in 0f..0.999f) {
                Box(
                    Modifier
                        .fillMaxWidth((ratio * inner).coerceIn(0f, 1f))
                        .height(2.dp)
                        .background(innerColor, RoundedCornerShape(3.dp))
                )
            }
        }
        Spacer(Modifier.height(2.dp))
        Text(value, color = GameColors.text, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

/** Malý farebný štítok – varovania, rarita, porovnanie. */
@Composable
fun Chip(text: String, color: Color, modifier: Modifier = Modifier, filled: Boolean = false) {
    Box(
        modifier
            .background(
                if (filled) color else color.copy(alpha = 0.16f),
                RoundedCornerShape(6.dp)
            )
            .padding(horizontal = 7.dp, vertical = 3.dp)
    ) {
        Text(
            text,
            color = if (filled) Color(0xFF15120E) else color,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
    }
}

/** Nadpis sekcie vnútri panelu. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        color = GameColors.accent,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.2.sp,
        modifier = modifier
    )
}

/**
 * Jednotný panel: tmavé pozadie, hlavička s názvom, podnadpisom a krížikom.
 * [fillHeight] = true: obsah vyplní zvyšok výšky (inventár, auto).
 * [fillHeight] = false: panel sa zbalí podľa obsahu (pauza, game over).
 */
@Composable
fun GamePanel(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onClose: (() -> Unit)? = null,
    fillHeight: Boolean = true,
    header: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Box(
        modifier
            .background(GameColors.panel, RoundedCornerShape(16.dp))
            .border(1.dp, GameColors.outline, RoundedCornerShape(16.dp))
            .padding(14.dp)
    ) {
        Column(if (fillHeight) Modifier.fillMaxSize() else Modifier) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        title,
                        color = GameColors.text,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    if (subtitle != null) {
                        Text(
                            subtitle,
                            color = GameColors.textDim,
                            fontSize = 12.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                header?.invoke()
                if (onClose != null) {
                    Spacer(Modifier.width(8.dp))
                    IconToggleButton("✕", active = false, onClick = onClose, label = "Close")
                }
            }
            Spacer(Modifier.height(10.dp))
            Column(
                if (fillHeight) Modifier.weight(1f).fillMaxWidth() else Modifier.fillMaxWidth()
            ) { content() }
        }
    }
}

/** Tmavý podklad pod panely, ktorý pohltí kliknutia mimo panelu. */
@Composable
fun Scrim(onDismiss: (() -> Unit)? = null, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(Color(0x99000000), GameColors.scrim),
                    radius = 1400f
                )
            )
            .then(if (onDismiss != null) Modifier.clickable(onClick = onDismiss) else Modifier)
    )
}

/** Rovnaké odsadenia naprieč obrazovkami. */
object Space {
    val xs = 4.dp
    val s = 8.dp
    val m = 12.dp
    val l = 16.dp
    val screen = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
}
