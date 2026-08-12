package sk.kubis.endlessdrive.game.event

/**
 * Nečakané udalosti na ceste. Časť z nich je okamžitá (defekt, nález),
 * časť beží nejaký čas a mení jazdu (únik paliva, dážď, misfire).
 *
 * @param message hláška pri vzniku
 * @param chip krátky štítok do HUD; null = udalosť nemá trvanie
 * @param duration ako dlho účinok trvá (s); 0 = okamžitá
 * @param weight základná váha pri losovaní
 * @param good true = hráčovi pomôže (nezvyšuje sa so vzdialenosťou)
 */
enum class RoadEvent(
    val message: String,
    val chip: String?,
    val duration: Float,
    val weight: Float,
    val good: Boolean = false
) {
    // --- Poruchy a smola ---
    FLAT_TYRE("Blowout — the tyre is shredded.", null, 0f, 9f),
    ROCK_STRIKE("A rock punched into the radiator.", null, 0f, 7f),
    FUEL_LEAK("Fuel line is weeping — you're losing petrol.", "FUEL LEAK", 45f, 8f),
    COOLANT_LEAK("A coolant hose split — it's boiling away.", "COOLANT LEAK", 40f, 7f),
    MISFIRE("The engine starts missing a beat.", "MISFIRE", 35f, 8f),
    BELT_SNAPPED("Alternator belt snapped — nothing is charging.", "NO CHARGE", 70f, 5f),
    OIL_SPLASH("You ploughed through a puddle of filth.", null, 0f, 6f),

    // --- Cesta a počasie ---
    RAIN("Rain sweeps in — the road turns greasy.", "SLIPPERY", 55f, 9f),
    DEBRIS("Branches and junk all over the road.", "DEBRIS", 22f, 8f),
    MUD("Deep mud — the wheels are digging in.", "MUD", 18f, 6f),

    // --- Šťastie ---
    TAILWIND("Long descent — the engine barely sips.", "TAILWIND", 40f, 6f, good = true),
    CLEAR_ROAD("Clean tarmac, nothing in the way.", "CLEAR", 35f, 6f, good = true),
    ROADSIDE_STASH("Someone left a can by the road.", null, 0f, 8f, good = true),
    ABANDONED_WRECK("A stripped wreck sits in the ditch.", null, 0f, 7f, good = true),

    // --- Atmosféra ---
    RADIO("Strange static on the radio…", null, 0f, 4f, good = true),
    TRACKS("Tracks lead off into a field. Nothing else.", null, 0f, 4f, good = true),
    ANIMAL("A deer watches you from the verge.", null, 0f, 4f, good = true);

    val timed: Boolean get() = duration > 0f && chip != null
}

/** Bežiaca udalosť aj so zvyškom času. */
data class ActiveEvent(val event: RoadEvent, var remaining: Float)
