package sk.kubis.endlessdrive.ui.game

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
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
import sk.kubis.endlessdrive.game.save.RunCodec
import kotlin.random.Random

class GameViewModel(
    private val playerRepository: PlayerRepository,
    bestDistanceKm: Float
) : ViewModel() {

    private var engine: GameEngine by mutableStateOf(GameEngine(Random.nextLong(), bestDistanceKm))
    private var recorded = false
    private var bestKm = bestDistanceKm
    private var hudTimer = 0f
    private var pausedByLifecycle = false
    private var pausedByUser = false
    private var bagRevision = 0

    // Kĺzavý priemer snímkovej frekvencie pre HUD.
    private var fpsAccum = 0f
    private var fpsFrames = 0
    private var fps = 0

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

    /** true, keď je rozohraná jazda, do ktorej sa dá vrátiť z menu. */
    var hasActiveRun: Boolean by mutableStateOf(false)
        private set

    init {
        // Rozohraná jazda z minula – ak sedí, pokračujeme presne tam, kde sme skončili.
        viewModelScope.launch {
            val saved = runCatching { playerRepository.loadRun() }.getOrNull() ?: return@launch
            val snap = RunCodec.decode(saved)
            if (snap == null) {
                runCatching { playerRepository.clearRun() }
                return@launch
            }
            engine = GameEngine.restore(snap, bestKm)
            recorded = false
            hasActiveRun = true
            publishUi(force = true)
        }
    }

    /** Odloží jazdu na disk – volá sa pri pauze a na križovatke. */
    private fun persistRun() {
        if (!hasActiveRun || engine.phase == GamePhase.GAME_OVER) return
        val data = RunCodec.encode(engine.snapshot())
        viewModelScope.launch { runCatching { playerRepository.saveRun(data) } }
    }

    private fun forgetRun() {
        viewModelScope.launch { runCatching { playerRepository.clearRun() } }
    }

    /** Rekord z profilu môže doraziť z DataStore až po vytvorení ViewModelu. */
    fun updateBestDistance(km: Float) {
        if (km > bestKm) {
            bestKm = km
            publishUi(force = true)
        }
    }

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
        persistRun()
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
        trackFps(dt)
        if (pausedByLifecycle || pausedByUser) {
            frame++
            return
        }

        if (engine.phase == GamePhase.GAME_OVER) {
            hasActiveRun = false
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

    private fun trackFps(dt: Float) {
        fpsAccum += dt
        fpsFrames++
        if (fpsAccum >= 0.5f) {
            fps = (fpsFrames / fpsAccum).toInt()
            fpsAccum = 0f
            fpsFrames = 0
        }
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
            // Stará hláška zmizne sama; blokáciu držíme, kým trvá.
            message = if (e.messageFresh) e.message else "",
            blockedReason = e.blockedReason,
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
            surface = e.currentSurface,
            isWinter = e.isWinter,
            hasChains = e.car.hasChains,
            surfaceAhead = e.patchAhead?.let { p ->
                p.surface to (p.start - e.localX).toInt().coerceAtLeast(0)
            },
            approachingJunction = e.approachingJunction,
            junctionDistanceM = e.junctionDistanceM,
            pendingChoiceId = e.pendingChoiceId,
            events = e.activeEvents.mapNotNull { ev ->
                ev.event.chip?.let { it to ev.remaining.toInt() }
            },
            wheelSlip = e.car.wheelSlip,
            wheelsLocked = e.car.wheelsLocked,
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
            fps = fps,
            endReason = e.endReason,
            isNewRecord = e.isNewRecord,
            bestDistanceKm = bestKm,
            fuelBurnedL = e.fuelBurnedL,
            itemsLooted = e.itemsLooted,
            buildingsVisited = e.buildingsVisited,
            fittedEngine = e.car.fittedHudLabel(ComponentSlot.ENGINE),
            fittedDrive = e.car.fittedHudLabel(ComponentSlot.DRIVETRAIN),
            fittedTires = "${e.car.fittedHudLabel(ComponentSlot.TIRE_FRONT)}/${e.car.fittedHudLabel(ComponentSlot.TIRE_REAR)}",
            fittedSuspension = e.car.fittedHudLabel(ComponentSlot.SUSPENSION),
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

    fun useItem(i: Int, target: ComponentSlot? = null) {
        val ok = engine.useInventoryItem(i, target)
        if (ok) bumpBag() else bump()
    }

    fun repair(slot: ComponentSlot) {
        engine.repairSlot(slot)
        bumpBag()
    }

    fun swapTyres() {
        engine.swapTyres()
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
        if (engine.car.engineRunning) {
            hasActiveRun = true
            persistRun()
        }
        bump()
    }

    fun stopEngine() {
        engine.stopEngine()
        bump()
    }

    /** Voľba vetvy za jazdy – auto nezastavuje. */
    fun selectBranch(id: Int) {
        engine.selectBranch(id)
        bump()
    }

    fun chooseBranch(id: Int) {
        gasPressed = false
        brakePressed = false
        throttle = 0f
        brake = 0f
        engine.chooseBranch(id)
        persistRun()
        bump()
    }

    fun endRun() = engine.endRun().also { bump() }

    private fun maybeRecord() {
        if (recorded) return
        recorded = true
        forgetRun()
        val distance = engine.distanceKm
        if (distance > bestKm) bestKm = distance
        viewModelScope.launch {
            runCatching { playerRepository.recordRun(distance) }
        }
    }

    /** Nová jazda od nuly – nový vrak, nový svet. */
    fun retry() {
        hasActiveRun = false
        forgetRun()
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
