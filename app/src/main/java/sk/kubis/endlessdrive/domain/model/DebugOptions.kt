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
    val fullFluids: Boolean = false,
    /** Jazda na pripravenej trati s prekážkami namiesto náhodnej cesty. */
    val testTrack: Boolean = false,
    /** Zobrazí ladiace tlačidlo REPAIR v paneli auta. */
    val repairControls: Boolean = false
) {
    /** true = aspoň jeden prepínač je zapnutý; menu to hlási hráčovi. */
    val any: Boolean
        get() = allComponents || fullUpgrades || fullBody || fullFluids || testTrack || repairControls

    companion object {
        val OFF = DebugOptions()
        /**
         * Plná výbava, kvapaliny a testovacia trať. Na ladenie pruženia
         * bez zháňania dielov – nové spustenie jazdy.
         */
        val TUNE_SUSPENSION = DebugOptions(
            fullUpgrades = true,
            fullBody = true,
            fullFluids = true,
            testTrack = true,
            repairControls = true
        )
    }
}
