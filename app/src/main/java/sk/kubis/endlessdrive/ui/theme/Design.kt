package sk.kubis.endlessdrive.ui.theme

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import sk.kubis.endlessdrive.R

/** Rovnaké kondenzované písmo, ktoré používa hlavné menu. */
val MenuCondensedFont = FontFamily(Font(R.font.bebas_neue_regular))

/**
 * Jednotná paleta a stavebné prvky herného UI.
 * Všetko na jednom mieste – obrazovky už nemiešajú vlastné hex hodnoty.
 */
object GameColors {
    val panel = Color(0xFF182126)
    val panelHigh = Color(0xFF253239)
    val panelSoft = Color(0xFF10171B)
    val outline = Color(0xFF46565E)
    val text = Color(0xFFF3F0E8)
    val textDim = Color(0xFFB1BDC1)
    val accent = Color(0xFFE9BA68)
    val ok = Color(0xFF7CB86A)
    val warn = Color(0xFFE0A33C)
    val danger = Color(0xFFD9584A)
    val info = Color(0xFF7FA8CC)
    val scrim = Color(0xE60D0B09)
    val hudBg = Color(0xDE10191F)
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
    compact: Boolean = false,
    @DrawableRes iconRes: Int? = null,
    iconOnly: Boolean = false,
) {
    val bg = when (style) {
        BtnStyle.Primary -> listOf(Color(0xFFF0CD87), GameColors.accent)
        BtnStyle.Secondary -> listOf(GameColors.panelHigh, GameColors.panel)
        BtnStyle.Danger -> listOf(Color(0xFF68372F), Color(0xFF44231F))
        BtnStyle.Ghost -> listOf(Color.Transparent, Color.Transparent)
    }
    val fg = when (style) {
        BtnStyle.Primary -> Color(0xFF202019)
        BtnStyle.Danger -> Color(0xFFF0A9A0)
        else -> GameColors.text
    }
    val edge = when (style) {
        BtnStyle.Primary -> Color(0xFFFFDDA0)
        BtnStyle.Secondary -> GameColors.outline
        BtnStyle.Danger -> Color(0xFF895047)
        BtnStyle.Ghost -> GameColors.outline
    }
    val shape = RoundedCornerShape(12.dp)
    val iconSize = if (compact) 20.dp else 26.dp
    Box(
        modifier
            .heightIn(min = if (compact) 40.dp else 48.dp)
            .then(if (iconOnly && iconRes != null) Modifier.widthIn(min = if (compact) 36.dp else 48.dp) else Modifier)
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    if (enabled) bg else bg.map { it.copy(alpha = 0.38f) }
                ),
                shape
            )
            .border(
                width = 1.dp,
                color = if (enabled) edge else edge.copy(alpha = 0.35f),
                shape = shape
            )
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(
                horizontal = if (iconOnly && iconRes != null) {
                    if (compact) 7.dp else 10.dp
                } else if (compact) 10.dp else 16.dp,
                vertical = if (compact) 8.dp else 11.dp
            ),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (iconRes != null) {
                Image(
                    painter = painterResource(iconRes),
                    contentDescription = if (iconOnly) text else null,
                    modifier = Modifier.size(iconSize),
                    contentScale = ContentScale.Fit,
                    alpha = if (enabled) 1f else 0.45f
                )
            }
            if (!iconOnly || iconRes == null) {
                Text(
                    text,
                    color = if (enabled) fg else fg.copy(alpha = 0.45f),
                    fontSize = if (compact) 12.sp else 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.6.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/** Zatvorenie panelu – vektorové X, nie znak z fontu. */
@Composable
fun CloseIconButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(44.dp)
            .background(GameColors.panelHigh.copy(alpha = 0.92f), RoundedCornerShape(10.dp))
            .border(1.dp, GameColors.outline, RoundedCornerShape(10.dp))
            .clickable(onClickLabel = "Close", onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.size(16.dp)) {
            val s = size.minDimension
            val stroke = s * 0.12f
            drawLine(
                GameColors.text,
                Offset(s * 0.12f, s * 0.12f),
                Offset(s * 0.88f, s * 0.88f),
                stroke,
                StrokeCap.Round
            )
            drawLine(
                GameColors.text,
                Offset(s * 0.88f, s * 0.12f),
                Offset(s * 0.12f, s * 0.88f),
                stroke,
                StrokeCap.Round
            )
        }
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
        Text(label, color = GameColors.textDim, fontSize = Type.label, letterSpacing = 0.6.sp, maxLines = 1)
        Spacer(Modifier.height(Space.xs))
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
        Spacer(Modifier.height(Space.xs))
        Text(value, color = GameColors.text, fontSize = Type.value, fontWeight = FontWeight.Medium, maxLines = 1)
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
            // Jemný gradient namiesto plochej výplne – panel tak nepôsobí ako
            // nalepená krabica a kreslené pozadie za ním ostáva tušiť.
            .background(
                Brush.verticalGradient(
                    0f to GameColors.panelHigh,
                    0.35f to GameColors.panel,
                    1f to GameColors.panelSoft
                ),
                RoundedCornerShape(18.dp)
            )
            .border(1.dp, GameColors.outline, RoundedCornerShape(18.dp))
            .padding(Space.l)
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
                        fontSize = Type.title,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.4.sp
                    )
                    if (subtitle != null) {
                        Text(
                            subtitle,
                            color = GameColors.textDim,
                            fontSize = Type.body,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                header?.invoke()
                if (onClose != null) {
                    Spacer(Modifier.width(Space.s))
                    CloseIconButton(onClick = onClose)
                }
            }
            // Vlasová linka pod hlavičkou – oddelí ju od obsahu bez ďalšej krabice.
            Spacer(Modifier.height(Space.m))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(GameColors.outline.copy(alpha = 0.55f))
            )
            Spacer(Modifier.height(Space.m))
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
    val xl = 24.dp
    val screen = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
}

/**
 * Typografický rebríček. Päť stupňov stačí – keď má každý text vlastnú
 * veľkosť, panel sa číta ako zoznam náhodných riadkov namiesto hierarchie.
 */
object Type {
    /** Jedno číslo, na ktoré sa pozerá za jazdy – rýchlosť. */
    val hero = 32.sp
    val title = 19.sp
    val value = 15.sp
    val body = 13.sp
    val label = 11.sp
    val micro = 9.sp
}
