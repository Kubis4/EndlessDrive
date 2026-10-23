package sk.kubis.endlessdrive.ui.menu

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import sk.kubis.endlessdrive.core.GameConfig
import sk.kubis.endlessdrive.domain.model.AppLanguage
import sk.kubis.endlessdrive.domain.model.AudioSettings
import sk.kubis.endlessdrive.domain.model.GaugeStyle
import sk.kubis.endlessdrive.domain.model.ThrottleMode
import sk.kubis.endlessdrive.ui.game.GaugeStylePreview
import sk.kubis.endlessdrive.ui.game.ThrottleModePreview
import sk.kubis.endlessdrive.ui.l10n.LocalCopy
import sk.kubis.endlessdrive.ui.l10n.languageEnglishName
import sk.kubis.endlessdrive.ui.theme.BtnStyle
import sk.kubis.endlessdrive.ui.theme.GameButton
import sk.kubis.endlessdrive.ui.theme.GameColors

/** Nastavenia bez interných debug prepínačov – tie sa nesmú dostať k hráčovi. */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    audioSettings: AudioSettings = AudioSettings(),
    onAudioChange: (AudioSettings) -> Unit = {},
    lightningFlashesEnabled: Boolean = true,
    onLightningFlashesEnabledChange: (Boolean) -> Unit = {},
    throttleMode: ThrottleMode = ThrottleMode.BINARY,
    onThrottleModeChange: (ThrottleMode) -> Unit = {},
    gaugeStyle: GaugeStyle = GaugeStyle.CLASSIC,
    onGaugeStyleChange: (GaugeStyle) -> Unit = {},
    uiScale: Float = GameConfig.UI_USER_SCALE_DEFAULT,
    onUiScaleChange: (Float) -> Unit = {},
    language: AppLanguage = AppLanguage.ENGLISH,
    onLanguageChange: (AppLanguage) -> Unit = {},
    showPrivacyOptions: Boolean = false,
    onPrivacyOptions: () -> Unit = {}
) {
    val copy = LocalCopy.current
    Box(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .background(Brush.verticalGradient(listOf(GameColors.panelSoft, GameColors.panelHigh)))
    ) {
        Column(
            Modifier
                .align(Alignment.Center)
                .widthIn(max = 640.dp)
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    copy.settings,
                    modifier = Modifier.weight(1f),
                    color = GameColors.text,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 3.sp
                )
                GameButton(copy.back, onBack, style = BtnStyle.Ghost, compact = true)
            }
            Spacer(Modifier.height(18.dp))

            SettingsSection(copy.languageSection, copy.languageDetail)
            Spacer(Modifier.height(10.dp))
            LanguagePicker(language, onLanguageChange)
            Spacer(Modifier.height(24.dp))

            SettingsSection(copy.controls, copy.controlsDetail)
            Spacer(Modifier.height(10.dp))
            ChoiceGrid(
                first = {
                    PreviewChoiceCard(
                        title = copy.throttlePedal,
                        detail = copy.throttlePedalDetail,
                        selected = throttleMode == ThrottleMode.BINARY,
                        onClick = { onThrottleModeChange(ThrottleMode.BINARY) }
                    ) {
                        ThrottleModePreview(ThrottleMode.BINARY, Modifier.fillMaxSize())
                    }
                },
                second = {
                    PreviewChoiceCard(
                        title = copy.throttleSlide,
                        detail = copy.throttleSlideDetail,
                        selected = throttleMode == ThrottleMode.SLIDE,
                        onClick = { onThrottleModeChange(ThrottleMode.SLIDE) }
                    ) {
                        ThrottleModePreview(ThrottleMode.SLIDE, Modifier.fillMaxSize())
                    }
                }
            )
            Spacer(Modifier.height(24.dp))

            SettingsSection(copy.hud, copy.hudDetail)
            Spacer(Modifier.height(10.dp))
            ChoiceGrid(
                first = {
                    PreviewChoiceCard(
                        title = copy.gaugeName(GaugeStyle.CLASSIC),
                        detail = copy.gaugeDetail(GaugeStyle.CLASSIC),
                        selected = gaugeStyle == GaugeStyle.CLASSIC,
                        onClick = { onGaugeStyleChange(GaugeStyle.CLASSIC) }
                    ) {
                        GaugeStylePreview(GaugeStyle.CLASSIC, Modifier.fillMaxSize())
                    }
                },
                second = {
                    PreviewChoiceCard(
                        title = copy.gaugeName(GaugeStyle.STRIP),
                        detail = copy.gaugeDetail(GaugeStyle.STRIP),
                        selected = gaugeStyle == GaugeStyle.STRIP,
                        onClick = { onGaugeStyleChange(GaugeStyle.STRIP) }
                    ) {
                        GaugeStylePreview(GaugeStyle.STRIP, Modifier.fillMaxSize())
                    }
                }
            )
            Spacer(Modifier.height(24.dp))

            SettingsSection(copy.display, copy.displayDetail)
            UiScaleSlider(copy.uiScale, uiScale, onUiScaleChange)
            LightningFlashToggle(
                enabled = lightningFlashesEnabled,
                onEnabledChange = onLightningFlashesEnabledChange
            )
            Spacer(Modifier.height(24.dp))

            SettingsSection(copy.sound, copy.soundDetail)
            AudioSlider(copy.masterVolume, audioSettings.master) {
                onAudioChange(audioSettings.copy(master = it))
            }
            AudioSlider(copy.roadWeather, audioSettings.surfaces) {
                onAudioChange(audioSettings.copy(surfaces = it))
            }
            AudioSlider(copy.tyreSlip, audioSettings.tyres) {
                onAudioChange(audioSettings.copy(tyres = it))
            }
            GameButton(copy.resetSound, { onAudioChange(AudioSettings()) }, compact = true)

            if (showPrivacyOptions) {
                Spacer(Modifier.height(20.dp))
                SettingsSection(copy.privacy, copy.privacyDetail)
                Spacer(Modifier.height(8.dp))
                GameButton(copy.adPrivacyOptions, onPrivacyOptions, compact = true)
            }
        }
    }
}

@Composable
private fun LightningFlashToggle(
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit
) {
    val copy = LocalCopy.current
    val shape = RoundedCornerShape(10.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .background(GameColors.panelSoft, shape)
            .border(1.dp, GameColors.outline, shape)
            .toggleable(
                value = enabled,
                role = Role.Switch,
                onValueChange = onEnabledChange
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(
                copy.lightningFlashes,
                color = GameColors.text,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                copy.lightningFlashesDetail,
                color = GameColors.textDim,
                fontSize = 11.sp,
                lineHeight = 15.sp
            )
        }
        Switch(checked = enabled, onCheckedChange = null)
    }
}

@Composable
private fun SettingsSection(title: String, detail: String) {
    Text(
        title,
        color = GameColors.accent,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 2.sp
    )
    Text(detail, color = GameColors.textDim, fontSize = 13.sp)
}

@Composable
private fun LanguagePicker(
    language: AppLanguage,
    onLanguageChange: (AppLanguage) -> Unit
) {
    val copy = LocalCopy.current
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val columns = if (maxWidth < 500.dp) 2 else 3
        val gap = 8.dp
        val cardWidth = (maxWidth - gap * (columns - 1)) / columns.toFloat()
        Column(verticalArrangement = Arrangement.spacedBy(gap)) {
            AppLanguage.entries.chunked(columns).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    row.forEach { item ->
                        val selected = language == item
                        Row(
                            Modifier
                                .width(cardWidth)
                                .heightIn(min = 58.dp)
                                .background(
                                    if (selected) GameColors.accent.copy(alpha = 0.16f)
                                    else GameColors.panelSoft,
                                    RoundedCornerShape(10.dp)
                                )
                                .border(
                                    if (selected) 2.dp else 1.dp,
                                    if (selected) GameColors.accent else GameColors.outline,
                                    RoundedCornerShape(10.dp)
                                )
                                .selectable(
                                    selected = selected,
                                    role = Role.RadioButton,
                                    onClick = { if (!selected) onLanguageChange(item) }
                                )
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                Modifier
                                    .width(34.dp)
                                    .height(28.dp)
                                    .background(
                                        if (selected) GameColors.accent else Color(0xFF34383A),
                                        RoundedCornerShape(7.dp)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    item.code.uppercase(),
                                    color = if (selected) GameColors.panelHigh else GameColors.text,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Black
                                )
                            }
                            Column(Modifier.padding(start = 9.dp).weight(1f)) {
                                Text(
                                    item.nativeName,
                                    color = GameColors.text,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    copy.languageEnglishName(item),
                                    color = GameColors.textDim,
                                    fontSize = 10.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            if (selected) {
                                Text("✓", color = GameColors.accent, fontWeight = FontWeight.Black)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChoiceGrid(
    first: @Composable () -> Unit,
    second: @Composable () -> Unit
) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth >= 540.dp) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.weight(1f)) { first() }
                Box(Modifier.weight(1f)) { second() }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                first()
                second()
            }
        }
    }
}

@Composable
private fun PreviewChoiceCard(
    title: String,
    detail: String,
    selected: Boolean,
    onClick: () -> Unit,
    preview: @Composable () -> Unit
) {
    val shape = RoundedCornerShape(12.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 180.dp)
            .background(
                if (selected) GameColors.accent.copy(alpha = 0.12f) else GameColors.panelSoft,
                shape
            )
            .border(
                if (selected) 2.dp else 1.dp,
                if (selected) GameColors.accent else GameColors.outline,
                shape
            )
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(12.dp)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(102.dp)
                .background(Color(0xCC171A1B), RoundedCornerShape(9.dp)),
            contentAlignment = Alignment.Center
        ) {
            preview()
            if (selected) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(7.dp)
                        .width(24.dp)
                        .height(24.dp)
                        .background(GameColors.accent, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("✓", color = GameColors.panelHigh, fontWeight = FontWeight.Black)
                }
            }
        }
        Spacer(Modifier.height(9.dp))
        Text(title, color = GameColors.text, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        Text(detail, color = GameColors.textDim, fontSize = 11.sp, lineHeight = 15.sp)
    }
}

@Composable
private fun UiScaleSlider(label: String, value: Float, onChange: (Float) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = GameColors.text, fontSize = 14.sp)
            Text(
                "${(value * 100f).toInt()}%",
                color = GameColors.accent,
                fontSize = 13.sp
            )
        }
        Slider(
            value = value,
            onValueChange = {
                val snapped = (it * 20f).roundToInt() / 20f
                onChange(
                    snapped.coerceIn(GameConfig.UI_USER_SCALE_MIN, GameConfig.UI_USER_SCALE_MAX)
                )
            },
            valueRange = GameConfig.UI_USER_SCALE_MIN..GameConfig.UI_USER_SCALE_MAX,
            steps = 9,
            modifier = Modifier.semantics { contentDescription = label },
            colors = SliderDefaults.colors(
                thumbColor = GameColors.accent,
                activeTrackColor = GameColors.accent,
                inactiveTrackColor = GameColors.outline
            )
        )
    }
}

@Composable
private fun AudioSlider(label: String, value: Float, onChange: (Float) -> Unit) {
    val copy = LocalCopy.current
    Column(Modifier.fillMaxWidth().padding(top = 12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = GameColors.text, fontSize = 14.sp)
            Text(
                if (value <= 0f) copy.off else "${(value * 100).toInt()}%",
                color = GameColors.accent,
                fontSize = 13.sp
            )
        }
        Slider(
            value = value,
            onValueChange = onChange,
            modifier = Modifier.semantics { contentDescription = label },
            colors = SliderDefaults.colors(
                thumbColor = GameColors.accent,
                activeTrackColor = GameColors.accent,
                inactiveTrackColor = GameColors.outline
            )
        )
    }
}
