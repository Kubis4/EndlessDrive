package sk.kubis.endlessdrive.ui.game

import sk.kubis.endlessdrive.domain.model.RoadSurface
import sk.kubis.endlessdrive.game.car.TireInjury
import sk.kubis.endlessdrive.ui.l10n.Copy
import sk.kubis.endlessdrive.ui.l10n.alternatorWeak
import sk.kubis.endlessdrive.ui.l10n.badFuel
import sk.kubis.endlessdrive.ui.l10n.batteryLow
import sk.kubis.endlessdrive.ui.l10n.batteryWontHold
import sk.kubis.endlessdrive.ui.l10n.cantMove
import sk.kubis.endlessdrive.ui.l10n.cantStart
import sk.kubis.endlessdrive.ui.l10n.carFallingApart
import sk.kubis.endlessdrive.ui.l10n.chainsOnDry
import sk.kubis.endlessdrive.ui.l10n.dirtyOil
import sk.kubis.endlessdrive.ui.l10n.engineHot
import sk.kubis.endlessdrive.ui.l10n.frontPuncture
import sk.kubis.endlessdrive.ui.l10n.fuelCritical
import sk.kubis.endlessdrive.ui.l10n.lowCoolant
import sk.kubis.endlessdrive.ui.l10n.lowFuel
import sk.kubis.endlessdrive.ui.l10n.lowOil
import sk.kubis.endlessdrive.ui.l10n.noHeadlights
import sk.kubis.endlessdrive.ui.l10n.notCharging
import sk.kubis.endlessdrive.ui.l10n.onRimPuncture
import sk.kubis.endlessdrive.ui.l10n.overheating
import sk.kubis.endlessdrive.ui.l10n.puncture
import sk.kubis.endlessdrive.ui.l10n.rearPuncture
import sk.kubis.endlessdrive.ui.l10n.runningOnRim
import sk.kubis.endlessdrive.ui.l10n.snowChainsOn
import sk.kubis.endlessdrive.ui.l10n.snowNoChains
import sk.kubis.endlessdrive.ui.l10n.surfaceChip
import sk.kubis.endlessdrive.ui.l10n.surfaceInMeters
import sk.kubis.endlessdrive.ui.l10n.wheelsLocked
import sk.kubis.endlessdrive.ui.l10n.wheelspin
import sk.kubis.endlessdrive.ui.l10n.wrongFuel

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

internal fun composeHudAlerts(ui: GameUiState, copy: Copy = Copy.English): HudAlertView {
    val block = ui.blockedReason?.takeIf { it.isNotBlank() }?.let { reason ->
        if (ui.engineRunning) copy.cantMove(reason) else copy.cantStart(reason)
    }
    val claimed = mutableListOf<String>()
    fun taken(text: String): Boolean = claimed.any { hudAlertsOverlap(it, text) }
    fun claim(text: String) { claimed += text }

    if (ui.blockedReason != null) claim(ui.blockedReason)

    val chips = mutableListOf<HudChip>()
    for (chip in hudWarningChips(ui, copy)) {
        if (taken(chip.text)) continue
        chips += chip
        claim(chip.text)
    }

    return HudAlertView(
        block = block,
        message = null,
        events = emptyList(),
        chips = chips.filter { it.tone == HudTone.DANGER }.take(4)
    )
}

internal fun tireWarningLabel(
    front: TireInjury,
    rear: TireInjury,
    copy: Copy = Copy.English
): String? {
    val punctured = front == TireInjury.PUNCTURED || rear == TireInjury.PUNCTURED
    val shredded = front == TireInjury.SHREDDED || rear == TireInjury.SHREDDED
    return when {
        shredded && punctured -> copy.onRimPuncture
        shredded -> copy.runningOnRim
        front == TireInjury.PUNCTURED && rear == TireInjury.PUNCTURED -> copy.puncture
        front == TireInjury.PUNCTURED -> copy.frontPuncture
        rear == TireInjury.PUNCTURED -> copy.rearPuncture
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
    AlertTopic.BATTERY to setOf(
        "battery", "charge", "alternator", "batterie", "batéria", "bateria", "carica", "carga"
    ),
    AlertTopic.FUEL to setOf("fuel", "tank", "kraftstoff", "carburant", "carburante", "palivo", "combustible"),
    AlertTopic.OIL to setOf("oil", "öl", "huile", "olio", "olej", "aceite"),
    AlertTopic.COOLANT to setOf("coolant", "kühlmittel", "liquide", "refrigerante", "chladiac"),
    AlertTopic.TYRE to setOf(
        "puncture", "rim", "tyre", "tyres", "tire", "tires",
        "blowout", "wheelspin", "wheels", "wheel", "rubber", "traction",
        "reifen", "pneu", "gomma", "pneumatik", "neumático", "felge", "ráfik"
    ),
    AlertTopic.HEAT to setOf("overheating", "overheat", "heat", "überhitz", "surchauffe", "surriscald", "prehri", "sobrecalent"),
    AlertTopic.ENGINE to setOf("engine", "misfire", "seize", "seized", "motor", "moteur", "motore"),
    AlertTopic.LIGHTS to setOf("headlight", "headlights", "dark", "dusk", "scheinwerfer", "phares", "fari", "svetl"),
    AlertTopic.WINTER to setOf("snow", "chains", "schnee", "neige", "neve", "sneh", "nieve", "ketten", "chaînes", "reťaze"),
    AlertTopic.SURFACE to setOf(
        "mud", "ice", "slush", "gravel", "sand", "water", "slippery", "debris", "rain",
        "schlamm", "eis", "blato", "ľad", "barro", "hielo"
    ),
    AlertTopic.MISSING to setOf("missing", "fehlt", "manque", "manca", "chýba", "falta"),
    AlertTopic.HEALTH to setOf("apart", "auseinander", "pièces", "pezzi", "rozpadá", "desmoron")
)

private fun hudWarningChips(ui: GameUiState, copy: Copy): List<HudChip> = buildList {
    if (ui.isWinter) {
        add(
            if (ui.hasChains) HudChip(copy.snowChainsOn, HudTone.OK)
            else HudChip(copy.snowNoChains, HudTone.DANGER)
        )
    } else if (ui.hasChains) {
        add(HudChip(copy.chainsOnDry, HudTone.WARN))
    }
    copy.surfaceChip(ui.surface)?.let { add(HudChip(it, HudTone.WARN)) }
    ui.surfaceAhead?.let { (surface, meters) ->
        if (ui.surface == RoadSurface.ASPHALT) {
            copy.surfaceChip(surface)?.let { add(HudChip(copy.surfaceInMeters(it, meters), HudTone.ACCENT)) }
        }
    }
    if (ui.wheelsLocked) add(HudChip(copy.wheelsLocked, HudTone.WARN))
    else if (ui.wheelSlip > 0.45f) add(HudChip(copy.wheelspin, HudTone.WARN))
    tireWarningLabel(ui.frontTireInjury, ui.rearTireInjury, copy)?.let {
        add(HudChip(it, HudTone.DANGER))
    }
    if (ui.temperature > 110f) add(HudChip(copy.overheating, HudTone.DANGER))
    else if (ui.temperature > 98f) add(HudChip(copy.engineHot, HudTone.WARN))
    val fuelRatio = ui.fuelL / ui.fuelCapacityL.coerceAtLeast(1f)
    if (fuelRatio < 0.08f) add(HudChip(copy.fuelCritical, HudTone.DANGER))
    else if (fuelRatio < 0.2f) add(HudChip(copy.lowFuel, HudTone.WARN))
    if (ui.oilL < 0.4f) add(HudChip(copy.lowOil, HudTone.DANGER))
    if (ui.coolantL < 0.5f) add(HudChip(copy.lowCoolant, HudTone.DANGER))
    if (ui.oilPurity < 0.55f) add(HudChip(copy.dirtyOil, HudTone.WARN))
    if (ui.wrongFuelFraction >= 0.15f) add(HudChip(copy.wrongFuel, HudTone.DANGER))
    else if (ui.fuelPurity < 0.55f) add(HudChip(copy.badFuel, HudTone.WARN))
    if (ui.engineRunning && ui.alternatorOutput in 0.005f..0.22f) {
        add(HudChip(copy.alternatorWeak, HudTone.WARN))
    }
    if (ui.engineRunning && ui.batteryHoldCapacity in 0.001f..0.18f) {
        add(HudChip(copy.batteryWontHold, HudTone.DANGER))
    } else if (ui.engineRunning &&
        (ui.alternatorOutput <= 0.005f || ui.events.any { it.first == "NO CHARGE" })
    ) {
        add(HudChip(copy.notCharging, HudTone.DANGER))
    }
    if (ui.batteryCharge < 0.2f) add(HudChip(copy.batteryLow, HudTone.DANGER))
    if (ui.isNight && !ui.headlightsOn) add(HudChip(copy.noHeadlights, HudTone.DANGER))
    if (ui.overallHealth < 0.25f) add(HudChip(copy.carFallingApart, HudTone.DANGER))
}

private fun normalizeAlert(text: String): String =
    text.lowercase().replace('—', ' ').replace('-', ' ').replace('·', ' ').trim()

private fun alertTokens(normalized: String): Set<String> =
    normalized.split(Regex("[^a-z0-9áäčďéíĺľňóôŕšťúýžüößàèùâêîôûç]+")).filter { it.length >= 3 }.toSet()

private fun alertTopics(tokens: Set<String>): Set<AlertTopic> = buildSet {
    for ((topic, words) in TOPIC_WORDS) {
        if (tokens.any { token -> words.any { word -> token.contains(word) || word.contains(token) } }) add(topic)
    }
}
