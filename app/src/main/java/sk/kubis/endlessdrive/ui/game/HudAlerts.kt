package sk.kubis.endlessdrive.ui.game

import sk.kubis.endlessdrive.domain.model.RoadSurface
import sk.kubis.endlessdrive.game.car.TireInjury

/** Závažnosť štítku – farba sa doplní až v Compose. */
internal enum class HudTone { DANGER, WARN, ACCENT, OK, INFO }

internal data class HudChip(val text: String, val tone: HudTone)

/**
 * Čo má AlertColumn naozaj vykresliť. Jedna porucha = jeden riadok:
 * blokácia štartu/jazdy prebije chuťovú hlášku aj časovač tej istej témy.
 */
internal data class HudAlertView(
    val block: String? = null,
    val message: String? = null,
    val events: List<Pair<String, Int>> = emptyList(),
    val chips: List<HudChip> = emptyList()
)

internal fun composeHudAlerts(ui: GameUiState): HudAlertView {
    val block = ui.blockedReason?.takeIf { it.isNotBlank() }?.let { reason ->
        val prefix = if (ui.engineRunning) "CAN'T MOVE" else "CAN'T START"
        "$prefix: $reason"
    }
    val claimed = mutableListOf<String>()
    fun taken(text: String): Boolean = claimed.any { hudAlertsOverlap(it, text) }
    fun claim(text: String) { claimed += text }

    // Blokácia najprv – „CAN'T START: Battery is flat“ drží tému batérie.
    if (ui.blockedReason != null) claim(ui.blockedReason)

    val chips = mutableListOf<HudChip>()
    for (chip in hudWarningChips(ui)) {
        if (taken(chip.text)) continue
        chips += chip
        claim(chip.text)
    }

    // Timed events are represented by their gameplay effects and audio only.
    // The top HUD is reserved for red, actionable failures.
    return HudAlertView(
        block = block,
        message = null,
        events = emptyList(),
        chips = chips.filter { it.tone == HudTone.DANGER }.take(4)
    )
}

internal fun tireWarningLabel(front: TireInjury, rear: TireInjury): String? {
    val punctured = front == TireInjury.PUNCTURED || rear == TireInjury.PUNCTURED
    val shredded = front == TireInjury.SHREDDED || rear == TireInjury.SHREDDED
    return when {
        shredded && punctured -> "ON RIM · PUNCTURE"
        shredded -> "RUNNING ON RIM"
        front == TireInjury.PUNCTURED && rear == TireInjury.PUNCTURED -> "PUNCTURE"
        front == TireInjury.PUNCTURED -> "FRONT PUNCTURE"
        rear == TireInjury.PUNCTURED -> "REAR PUNCTURE"
        else -> null
    }
}

internal fun hudAlertsOverlap(a: String, b: String): Boolean {
    val na = normalizeAlert(a)
    val nb = normalizeAlert(b)
    if (na.isEmpty() || nb.isEmpty()) return false
    if (na.contains(nb) || nb.contains(na)) return true
    val ta = alertTokens(na)
    val tb = alertTokens(nb)
    if (ta.intersect(tb).any { it.length >= 4 }) return true
    val topicsA = alertTopics(ta)
    val topicsB = alertTopics(tb)
    return topicsA.isNotEmpty() && topicsA.any { it in topicsB }
}

private enum class AlertTopic {
    BATTERY, FUEL, OIL, COOLANT, TYRE, HEAT, ENGINE, LIGHTS, WINTER, SURFACE, MISSING, HEALTH
}

private val TOPIC_WORDS: Map<AlertTopic, Set<String>> = mapOf(
    AlertTopic.BATTERY to setOf("battery", "charge", "alternator"),
    AlertTopic.FUEL to setOf("fuel", "tank"),
    AlertTopic.OIL to setOf("oil"),
    AlertTopic.COOLANT to setOf("coolant"),
    AlertTopic.TYRE to setOf(
        "puncture", "rim", "tyre", "tyres", "tire", "tires",
        "blowout", "wheelspin", "wheels", "wheel", "rubber", "traction"
    ),
    AlertTopic.HEAT to setOf("overheating", "overheat", "heat"),
    AlertTopic.ENGINE to setOf("engine", "misfire", "seize", "seized"),
    AlertTopic.LIGHTS to setOf("headlight", "headlights", "dark", "dusk"),
    AlertTopic.WINTER to setOf("snow", "chains"),
    AlertTopic.SURFACE to setOf(
        "mud", "ice", "slush", "gravel", "sand", "water", "slippery", "debris", "rain"
    ),
    AlertTopic.MISSING to setOf("missing"),
    AlertTopic.HEALTH to setOf("apart")
)

private fun hudWarningChips(ui: GameUiState): List<HudChip> = buildList {
    if (ui.isWinter) {
        add(
            if (ui.hasChains) HudChip("SNOW · CHAINS ON", HudTone.OK)
            else HudChip("SNOW · NO CHAINS", HudTone.DANGER)
        )
    } else if (ui.hasChains) {
        add(HudChip("CHAINS ON DRY ROAD", HudTone.WARN))
    }
    ui.surface.chip?.let { add(HudChip(it, HudTone.WARN)) }
    ui.surfaceAhead?.let { (surface, meters) ->
        if (ui.surface == RoadSurface.ASPHALT) {
            surface.chip?.let { add(HudChip("$it IN $meters m", HudTone.ACCENT)) }
        }
    }
    if (ui.wheelsLocked) add(HudChip("WHEELS LOCKED", HudTone.WARN))
    else if (ui.wheelSlip > 0.45f) add(HudChip("WHEELSPIN", HudTone.WARN))
    tireWarningLabel(ui.frontTireInjury, ui.rearTireInjury)?.let {
        add(HudChip(it, HudTone.DANGER))
    }
    if (ui.temperature > 110f) add(HudChip("OVERHEATING", HudTone.DANGER))
    else if (ui.temperature > 98f) add(HudChip("ENGINE HOT", HudTone.WARN))
    val fuelRatio = ui.fuelL / ui.fuelCapacityL.coerceAtLeast(1f)
    if (fuelRatio < 0.08f) add(HudChip("FUEL CRITICAL", HudTone.DANGER))
    else if (fuelRatio < 0.2f) add(HudChip("LOW FUEL", HudTone.WARN))
    if (ui.oilL < 0.4f) add(HudChip("LOW OIL", HudTone.DANGER))
    if (ui.coolantL < 0.5f) add(HudChip("LOW COOLANT", HudTone.DANGER))
    if (ui.oilPurity < 0.55f) add(HudChip("DIRTY OIL", HudTone.WARN))
    if (ui.wrongFuelFraction >= 0.15f) add(HudChip("WRONG FUEL", HudTone.DANGER))
    else if (ui.fuelPurity < 0.55f) add(HudChip("BAD FUEL", HudTone.WARN))
    if (ui.engineRunning && ui.alternatorOutput in 0.005f..0.22f) {
        add(HudChip("ALTERNATOR WEAK", HudTone.WARN))
    }
    if (ui.batteryCharge < 0.2f) add(HudChip("BATTERY LOW", HudTone.DANGER))
    if (ui.isNight && !ui.headlightsOn) add(HudChip("NO HEADLIGHTS", HudTone.DANGER))
    if (ui.overallHealth < 0.25f) add(HudChip("CAR FALLING APART", HudTone.DANGER))
}

private fun normalizeAlert(text: String): String =
    text.lowercase().replace('—', ' ').replace('-', ' ').replace('·', ' ').trim()

private fun alertTokens(normalized: String): Set<String> =
    normalized.split(Regex("[^a-z0-9]+")).filter { it.length >= 3 }.toSet()

private fun alertTopics(tokens: Set<String>): Set<AlertTopic> = buildSet {
    for ((topic, words) in TOPIC_WORDS) {
        if (tokens.any { it in words }) add(topic)
    }
}
