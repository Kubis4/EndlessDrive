package sk.kubis.endlessdrive.domain.model

import java.util.Locale

/**
 * In-app UI language. English stays the default for tests and first launch
 * unless the device language is one of the supported codes.
 */
enum class AppLanguage(val code: String, val nativeName: String) {
    ENGLISH("en", "English"),
    GERMAN("de", "Deutsch"),
    FRENCH("fr", "Français"),
    ITALIAN("it", "Italiano"),
    SLOVAK("sk", "Slovenčina"),
    SPANISH("es", "Español");

    val locale: Locale
        get() = Locale.forLanguageTag(code)

    companion object {
        fun fromStored(raw: String?): AppLanguage {
            if (raw.isNullOrBlank()) return systemDefault()
            return entries.find {
                it.name.equals(raw, ignoreCase = true) || it.code.equals(raw, ignoreCase = true)
            } ?: systemDefault()
        }

        fun systemDefault(): AppLanguage {
            val lang = Locale.getDefault().language
            return entries.find { it.code.equals(lang, ignoreCase = true) } ?: ENGLISH
        }
    }
}
