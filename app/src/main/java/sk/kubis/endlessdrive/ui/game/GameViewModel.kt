package sk.kubis.endlessdrive.ui.game

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import sk.kubis.endlessdrive.core.MathX
import sk.kubis.endlessdrive.domain.model.ComponentSlot
import sk.kubis.endlessdrive.domain.model.GamePhase
import sk.kubis.endlessdrive.domain.repository.PlayerRepository
import sk.kubis.endlessdrive.game.GameEngine
import kotlin.random.Random

class GameViewModel(
    private val playerRepository: PlayerRepository,
    bestDistanceKm: Float
) : ViewModel() {

    private var engine: GameEngine = GameEngine(Random.nextLong(), bestDistanceKm)
    private var recorded = false
    private var bestKm = bestDistanceKm
    private var hudTimer = 0f
    private var pausedByLifecycle = false
    private var pausedByUser = false
    private var bagRevision = 0

    /** Invalidácia Canvasu – čítať len vnútri Canvas. */
    var frame by mutableIntStateOf(0)
        private set

    private val _ui = MutableStateFlow(snapshotUi())
    val ui: StateFlow<GameUiState> = _ui.asStateFlow()

    private var gasPressed = false
    private var brakePressed = false
    private var throttle = 0f
    private var brake = 0f

    val game: GameEngine get() = engine

    fun onGasChanged(pressed: Boolean) {
        gasPressed = pressed
    }

    fun onBrakeChanged(pressed: Boolean) {
        brakePressed = pressed
    }

    fun onLifecyclePause() {
        pausedByLifecycle = true
        gasPressed = false
        brakePressed = false
        throttle = 0f
        brake = 0f
        engine.throttleInput = 0f
        engine.brakeInput = 0f
        publishUi(force = true)
    }

    fun onLifecycleResume() {
        pausedByLifecycle = false
        publishUi(force = true)
    }

    /** Ručná pauza – čas, spotreba aj batéria stoja. */
    fun setPaused(value: Boolean) {
        pausedByUser = value
        if (value) {
            gasPressed = false
            brakePressed = false
            throttle = 0f
            brake = 0f
            engine.throttleInput = 0f
            engine.brakeInput = 0f
        }
        publishUi(force = true)
    }

    fun onFrame(dt: Float, screenHeightPx: Float) {
        if (pausedByLifecycle || pausedByUser) {
            frame++
            return
        }

        if (engine.phase == GamePhase.GAME_OVER) {
            maybeRecord()
            frame++
            publishUi(dt, force = true)
            return
        }

        throttle = MathX.damp(throttle, if (gasPressed) 1f else 0f, 12f, dt)
        brake = MathX.damp(brake, if (brakePressed) 1f else 0f, 14f, dt)

        val driving = engine.phase == GamePhase.DRIVING
        engine.throttleInput = if (driving) throttle else 0f
        engine.brakeInput = if (driving) brake else 0f
        engine.setScreenHeight(screenHeightPx)
        engine.advance(dt)

        // V PREP/STOPPED netreba 60×/s prekresľovať ťažké Pathy – šetrí CPU/GPU a batériu.
        val moving = driving || engine.car.speed > 0.05f ||
            engine.phase == GamePhase.JUNCTION ||
            engine.phase == GamePhase.GAME_OVER
        if (moving || frame == 0) {
            frame++
        }
        publishUi(dt)
    }

    private fun publishUi(dt: Float = 0f, force: Boolean = false) {
        hudTimer += dt
        val phaseChanged = _ui.value.phase != engine.phase
        val messageChanged = _ui.value.message != engine.message
        if (force || phaseChanged || messageChanged || hudTimer >= 0.1f) {
            hudTimer = 0f
            _ui.value = snapshotUi()
        }
    }

    private fun snapshotUi(): GameUiState {
        val e = engine
        return GameUiState(
            phase = e.phase,
            message = e.message,
            prepStep = e.prepStep,
            fuelL = e.car.fuel,
            oilL = e.car.oil,
            coolantL = e.car.coolant,
            fuelCapacityL = e.car.fuelCapacity,
            oilCapacityL = e.car.oilCapacity,
            coolantCapacityL = e.car.coolantCapacity,
            fuelPurity = e.car.fuelPurity,
            oilPurity = e.car.oilPurity,
            coolantPurity = e.car.coolantPurity,
            roadFeature = e.currentFeature,
            temperature = e.car.temperature,
            speedKmh = e.car.speedKmh,
            distanceKm = e.distanceKm,
            overallHealth = e.car.overallHealth,
            batteryCharge = e.car.batteryCharge,
            engineRunning = e.car.engineRunning,
            headlightsOn = e.headlightsOn,
            isNight = e.isNight,
            clock = e.clock,
            hasNearbyBuilding = e.buildingNear() != null,
            exploring = e.phase == GamePhase.EXPLORING && e.activeBuilding != null,
            pumpFuelL = e.activeBuilding?.pumpFuelL ?: 0f,
            paused = pausedByUser,
            endReason = e.endReason,
            isNewRecord = e.isNewRecord,
            bestDistanceKm = bestKm,
            fuelBurnedL = e.fuelBurnedL,
            itemsLooted = e.itemsLooted,
            buildingsVisited = e.buildingsVisited,
            bagRevision = bagRevision
        )
    }

    private fun bumpBag() {
        bagRevision++
        bump()
    }

    private fun bump() {
        frame++
        publishUi(force = true)
    }

    fun stop() {
        gasPressed = false
        brakePressed = false
        throttle = 0f
        brake = 0f
        engine.requestStop()
        bump()
    }

    fun resume() {
        engine.resumeDriving()
        bump()
    }

    fun enterBuilding() {
        engine.enterNearestBuilding()
        bump()
    }

    fun leaveBuilding() {
        engine.leaveBuilding()
        bump()
    }

    fun takeLoot(i: Int) {
        engine.takeLoot(i)
        bumpBag()
    }

    fun discardItem(i: Int) {
        val ok = engine.discardInventoryItem(i)
        if (ok) bumpBag() else bump()
    }

    fun useItem(i: Int) {
        val ok = engine.useInventoryItem(i)
        if (ok) bumpBag() else bump()
    }

    fun repair(slot: ComponentSlot) {
        engine.repairSlot(slot)
        bumpBag()
    }

    fun unmount(slot: ComponentSlot) {
        engine.unmountSlot(slot)
        bumpBag()
    }

    fun refuelFromPump() {
        engine.refuelFromPump()
        bumpBag()
    }

    fun toggleHeadlights() {
        engine.toggleHeadlights()
        bump()
    }

    fun startEngine() {
        engine.tryStartEngine()
        bump()
    }

    fun stopEngine() {
        engine.stopEngine()
        bump()
    }

    fun chooseBranch(id: Int) {
        gasPressed = false
        brakePressed = false
        throttle = 0f
        brake = 0f
        engine.chooseBranch(id)
        bump()
    }

    fun endRun() = engine.endRun().also { bump() }

    private fun maybeRecord() {
        if (recorded) return
        recorded = true
        val distance = engine.distanceKm
        if (distance > bestKm) bestKm = distance
        viewModelScope.launch {
            runCatching { playerRepository.recordRun(distance) }
        }
    }

    fun retry() {
        recorded = false
        gasPressed = false
        brakePressed = false
        throttle = 0f
        brake = 0f
        pausedByLifecycle = false
        pausedByUser = false
        engine = GameEngine(Random.nextLong(), bestKm)
        hudTimer = 0f
        bump()
    }

    companion object {
        fun factory(repo: PlayerRepository, bestDistanceKm: Float) =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return GameViewModel(repo, bestDistanceKm) as T
                }
            }
    }
}
