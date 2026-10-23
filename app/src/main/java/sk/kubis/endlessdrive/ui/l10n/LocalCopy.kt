package sk.kubis.endlessdrive.ui.l10n

import androidx.compose.runtime.staticCompositionLocalOf
import sk.kubis.endlessdrive.domain.model.AppLanguage

val LocalCopy = staticCompositionLocalOf { Copy.of(AppLanguage.ENGLISH) }
