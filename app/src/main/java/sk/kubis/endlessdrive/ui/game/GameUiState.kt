package sk.kubis.endlessdrive.ui.game

import sk.kubis.endlessdrive.domain.model.ComponentSlot
import sk.kubis.endlessdrive.domain.model.EndReason
import sk.kubis.endlessdrive.domain.model.GamePhase
import sk.kubis.endlessdrive.domain.model.ItemDef
import sk.kubis.endlessdrive.domain.model.RoadFeature
import sk.kubis.endlessdrive.domain.model.RoadSurface
import sk.kubis.endlessdrive.game.PrepStep
import sk.kubis.endlessdrive.game.car.Car

/** Stav HUD / overlayov – aktualizuje sa max ~10×/s, nie každú snímku. */
data class GameUiState(
    val phase: GamePhase = GamePhase.PREP,
    val message: String = "",
    /** Dôvod, prečo sa auto nevie pohnúť vpred (null = ide). */
    val blockedReason: String? = null,
    val prepStep: PrepStep = PrepStep.CHECK_ENGINE,
    val fuelL: Float = 0f,
    val oilL: Float = 0f,
    val coolantL: Float = 0f,
    val fuelCapacityL: Float = 40f,
    val oilCapacityL: Float = 4f,
    val coolantCapacityL: Float = 6f,
    /** Čistota kvapalín v nádržiach (1 = čistá). */
    val fuelPurity: Float = 1f,
    val oilPurity: Float = 1f,
    val coolantPurity: Float = 1f,
    val roadFeature: RoadFeature = RoadFeature.STRAIGHT,
    /** Povrch pod kolesami – bahno, piesok, voda, štrk. */
    val surface: RoadSurface = RoadSurface.ASPHALT,
    /** true = zasnežená vetva; auto rieši chlad a zimnú výbavu. */
    val isWinter: Boolean = false,
    val hasChains: Boolean = false,
    /** Naplavenina pred autom + vzdialenosť k nej (null = čistá cesta). */
    val surfaceAhead: Pair<RoadSurface, Int>? = null,
    /** Rázcestie: dá sa voliť za jazdy, kým sa naň nedôjde. */
    val approachingJunction: Boolean = false,
    val junctionDistanceM: Float = 0f,
    val pendingChoiceId: Int? = null,
    /** Bežiace udalosti: štítok + zvyšok času v sekundách. */
    val events: List<Pair<String, Int>> = emptyList(),
    /** 0..1 – ako veľmi práve preklzávajú kolesá. */
    val wheelSlip: Float = 0f,
    val wheelsLocked: Boolean = false,
    val temperature: Float = 0f,
    val speedKmh: Float = 0f,
    val distanceKm: Float = 0f,
    val overallHealth: Float = 0f,
    val batteryCharge: Float = 0f,
    val engineRunning: Boolean = false,
    val headlightsOn: Boolean = false,
    val isNight: Boolean = false,
    val clock: String = "08:00",
    val hasNearbyBuilding: Boolean = false,
    val exploring: Boolean = false,
    /** Zásoba v stojane preskúmavanej benzínky. */
    val pumpFuelL: Float = 0f,
    val paused: Boolean = false,
    /** Snímková frekvencia pre voliteľný ukazovateľ. */
    val fps: Int = 0,
    val endReason: EndReason? = null,
    val isNewRecord: Boolean = false,
    val bestDistanceKm: Float = 0f,
    val fuelBurnedL: Float = 0f,
    val itemsLooted: Int = 0,
    val buildingsVisited: Int = 0,
    /** Krátke mená namontovaných dielov do HUD. */
    val fittedEngine: String = "—",
    val fittedDrive: String = "—",
    val fittedTires: String = "—",
    val fittedSuspension: String = "—",
    /** Invalidácia inventára / panelov auta. */
    val bagRevision: Int = 0
)

/** Krátky popis dielu do HUD (bez zbytočných slov). */
fun ItemDef.hudLabel(): String {
    val raw = name
        .removePrefix("Tyres ")
        .removePrefix("Tyre ")
        .removePrefix("Drivetrain ")
        .removePrefix("Suspension ")
        .removePrefix("Engine ")
        .trim()
    return raw.trim('(', ')', ' ').ifBlank { name }
}

fun Car.fittedHudLabel(slot: ComponentSlot): String =
    parts[slot]?.def?.hudLabel() ?: "—"
