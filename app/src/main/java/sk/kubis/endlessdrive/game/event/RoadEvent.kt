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
    FLAT_TYRE("Blowout — the tyre is shredded.", null, 0f, 5f),
    ROCK_STRIKE("A rock hit the radiator.", null, 0f, 7f),
    FUEL_LEAK("Fuel leak — tank is dropping.", "FUEL LEAK", 45f, 8f),
    COOLANT_LEAK("Coolant leak — level is dropping.", "COOLANT LEAK", 40f, 7f),
    MISFIRE("Engine misfire — less power.", "MISFIRE", 35f, 8f),
    BELT_SNAPPED("Alternator belt snapped — battery not charging.", "NO CHARGE", 70f, 5f),
    OIL_SPLASH("Dirty puddle — oil purity dropped.", null, 0f, 6f),

    // --- Cesta a počasie ---
    RAIN("Rain — road is slippery.", "SLIPPERY", 55f, 9f),
    DEBRIS("Debris on the road — tyre risk.", "DEBRIS", 22f, 8f),
    MUD("Mud — wheels digging in.", "MUD", 30f, 6f),

    // --- Šťastie ---
    HEADWIND("Headwind — slower and using more fuel.", "HEADWIND", 45f, 5f),
    TAILWIND("Tailwind — faster and using less fuel.", "TAILWIND", 40f, 6f, good = true),
    CLEAR_ROAD("Smooth road — using less fuel.", "SMOOTH", 35f, 6f, good = true),
    ROADSIDE_STASH("Supplies ahead — stop to pick them up.", null, 0f, 8f, good = true),
    ABANDONED_WRECK("Wreck ahead — scrap and parts.", null, 0f, 7f, good = true);

    val timed: Boolean get() = duration > 0f && chip != null
}

/** Bežiaca udalosť aj so zvyškom času. */
data class ActiveEvent(val event: RoadEvent, var remaining: Float)
