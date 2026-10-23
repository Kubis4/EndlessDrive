package sk.kubis.endlessdrive.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import sk.kubis.endlessdrive.core.GameConfig

/** Hráčsky posuvník veľkosti UI z nastavení (1 = návrhová veľkosť). */
val LocalUiScale = compositionLocalOf { GameConfig.UI_USER_SCALE_DEFAULT }

/** HUD density: jemný auto-scale podľa obrazovky krát posuvník z nastavení. */
@Composable
fun rememberResolutionScaledDensity(): Density {
    val base = LocalDensity.current
    val configuration = LocalConfiguration.current
    val userScale = LocalUiScale.current
    val scale = GameConfig.uiDensityScale(
        configuration.screenWidthDp,
        configuration.screenHeightDp,
        userScale
    )
    return remember(base.density, base.fontScale, scale) {
        Density(density = base.density * scale, fontScale = base.fontScale)
    }
}

@Composable
fun ProvideResolutionScaledUi(content: @Composable () -> Unit) {
    val scaled = rememberResolutionScaledDensity()
    CompositionLocalProvider(LocalDensity provides scaled, content = content)
}

/**
 * Spoločná size-class politika pre celú hernú obrazovku. Rozhoduje z reálneho
 * priestoru v dp (po výrezoch sa obsah ešte odsúva), nie z počtu pixelov.
 */
data class GameUiLayout(
    val compactHud: Boolean,
    val compactLoot: Boolean,
    val compactGarage: Boolean
) {
    companion object {
        fun forViewport(widthDp: Int, heightDp: Int): GameUiLayout {
            val width = widthDp.coerceAtLeast(1)
            val height = heightDp.coerceAtLeast(1)
            return GameUiLayout(
                compactHud = width < 720 || height < 380,
                compactLoot = width < 760 || height < 500,
                compactGarage = width < 900 || height < 540
            )
        }
    }
}
