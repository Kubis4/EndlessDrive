package sk.kubis.endlessdrive.domain.model

/**
 * Ladiace prepínače z nastavení. Menia len to, s čím jazda začína – priebeh
 * hry, generovanie sveta ani loot sa nimi nedotýkajú, aby sa dalo testovať
 * to jedno, čo práve testujem.
 */
data class DebugOptions(
    /** Každý slot obsadený základným dielom – nič nechýba. */
    val allComponents: Boolean = false,
    /** Najlepší kus do každého slotu namiesto základného. */
    val fullUpgrades: Boolean = false,
    /** Plechy karosérie – na kontrolu polôh dielov bez zháňania lootu. */
    val fullBody: Boolean = false,
    /** Plné a čisté kvapaliny, nabitá batéria. */
    val fullFluids: Boolean = false
) {
    /** true = aspoň jeden prepínač je zapnutý; menu to hlási hráčovi. */
    val any: Boolean
        get() = allComponents || fullUpgrades || fullBody || fullFluids

    companion object {
        val OFF = DebugOptions()
    }
}
