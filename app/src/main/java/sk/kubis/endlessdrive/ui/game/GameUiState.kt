package sk.kubis.endlessdrive.ui.game

import sk.kubis.endlessdrive.domain.model.EndReason
import sk.kubis.endlessdrive.domain.model.GamePhase
import sk.kubis.endlessdrive.game.PrepStep

/** Stav HUD / overlayov – aktualizuje sa max ~10×/s, nie každú snímku. */
data class GameUiState(
    val phase: GamePhase = GamePhase.PREP,
    val message: String = "",
    val prepStep: PrepStep = PrepStep.CHECK_ENGINE,
    val fuelL: Float = 0f,
    val oilL: Float = 0f,
    val coolantL: Float = 0f,
    val fuelCapacityL: Float = 40f,
    val oilCapacityL: Float = 4f,
    val coolantCapacityL: Float = 6f,
    val temperature: Float = 0f,
    val speedKmh: Float = 0f,
    val distanceKm: Float = 0f,
    val overallHealth: Float = 0f,
    val engineRunning: Boolean = false,
    val hasNearbyBuilding: Boolean = false,
    val exploring: Boolean = false,
    val endReason: EndReason? = null,
    val isNewRecord: Boolean = false,
    val bestDistanceKm: Float = 0f,
    /** Invalidácia inventára / panelov auta. */
    val bagRevision: Int = 0
)
