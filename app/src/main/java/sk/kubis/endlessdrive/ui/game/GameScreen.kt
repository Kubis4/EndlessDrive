package sk.kubis.endlessdrive.ui.game

import android.app.Activity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import sk.kubis.endlessdrive.core.GameConfig
import sk.kubis.endlessdrive.ui.game.expedition.ExpeditionSurfaceView
import sk.kubis.endlessdrive.ui.rememberResolutionScaledDensity
import sk.kubis.endlessdrive.ui.GameUiLayout
import sk.kubis.endlessdrive.domain.model.BuildingType
import sk.kubis.endlessdrive.domain.model.ComponentSlot
import sk.kubis.endlessdrive.domain.model.FluidGrade
import sk.kubis.endlessdrive.domain.model.FluidType
import sk.kubis.endlessdrive.domain.model.FuelKind
import sk.kubis.endlessdrive.domain.model.GamePhase
import sk.kubis.endlessdrive.domain.model.RescueOffer
import sk.kubis.endlessdrive.domain.model.AudioSettings
import sk.kubis.endlessdrive.game.event.PursuitStormKind
import sk.kubis.endlessdrive.domain.model.GaugeStyle
import sk.kubis.endlessdrive.domain.model.ItemDef
import sk.kubis.endlessdrive.domain.model.ItemCatalog
import sk.kubis.endlessdrive.domain.model.ItemStack
import sk.kubis.endlessdrive.domain.model.ThrottleMode
import sk.kubis.endlessdrive.game.car.TireInjury
import sk.kubis.endlessdrive.domain.model.VehiclePaint
import sk.kubis.endlessdrive.game.GameEngine
import sk.kubis.endlessdrive.game.Journey
import sk.kubis.endlessdrive.game.SideMissionCompletion
import sk.kubis.endlessdrive.game.SideMissionKind
import sk.kubis.endlessdrive.game.world.BranchChoice
import sk.kubis.endlessdrive.ads.RewardedAdManager
import sk.kubis.endlessdrive.game.car.Car
import sk.kubis.endlessdrive.game.car.MountedPart
import sk.kubis.endlessdrive.ui.l10n.LocalCopy
import sk.kubis.endlessdrive.ui.l10n.*
import sk.kubis.endlessdrive.ui.theme.BtnStyle
import sk.kubis.endlessdrive.ui.theme.Chip
import sk.kubis.endlessdrive.ui.theme.GameButton
import sk.kubis.endlessdrive.ui.theme.GameColors
import sk.kubis.endlessdrive.ui.theme.GamePanel
import sk.kubis.endlessdrive.ui.theme.Scrim
import sk.kubis.endlessdrive.ui.theme.SectionLabel
import sk.kubis.endlessdrive.ui.theme.StatBar
import sk.kubis.endlessdrive.ui.theme.levelColor
import sk.kubis.endlessdrive.ui.theme.purityColor
import java.util.concurrent.atomic.AtomicReference

/** Odstup UI od okrajov – zaoblené displeje a výrezy nesmú nič odrezať. */
private val SCREEN_MARGIN = 10.dp
private val CAR_ACTION_HEIGHT = 36.dp
private val WORKSHOP_ACTION_HEIGHT = 48.dp
private val WORKSHOP_SWATCH_HEIGHT = 48.dp
private val WORKSHOP_CATALOG_SLOTS = listOf(
    ComponentSlot.ENGINE,
    ComponentSlot.DRIVETRAIN,
    ComponentSlot.BATTERY,
    ComponentSlot.RADIATOR,
    ComponentSlot.BRAKES,
    ComponentSlot.FUEL_TANK
)

@Composable
fun GameScreen(
    viewModel: GameViewModel,
    onExitToMenu: () -> Unit,
    rewardedAds: RewardedAdManager? = null,
    audioSettings: AudioSettings = AudioSettings(),
    throttleMode: ThrottleMode = ThrottleMode.BINARY,
    gaugeStyle: GaugeStyle = GaugeStyle.CLASSIC,
    lightningFlashesEnabled: Boolean = true,
    showFps: Boolean = false
) {
    var showInventory by remember { mutableStateOf(false) }
    var showCar by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    val hudDensity = rememberResolutionScaledDensity()
    val context = LocalContext.current
    val activity = context as? Activity
    val adReadyState = rewardedAds?.isReady?.collectAsState()
    val adReady = adReadyState?.value == true
    val audio = remember(context) { GameAudio(context) }
    LaunchedEffect(audio, audioSettings) { audio.settings = audioSettings }
    LaunchedEffect(rewardedAds) { rewardedAds?.load() }
    val configuration = LocalConfiguration.current
    val uiLayout = GameUiLayout.forViewport(
        configuration.screenWidthDp,
        configuration.screenHeightDp
    )
    val compactHud = uiLayout.compactHud
    val compactLoot = uiLayout.compactLoot
    val compactGarage = uiLayout.compactGarage
    val layoutDirection = LocalLayoutDirection.current
    val surfaceRef = remember { AtomicReference<GameSceneSurface?>(null) }
    var exitingToMenu by remember { mutableStateOf(false) }
    val exitToMenu = {
        if (!exitingToMenu) {
            exitingToMenu = true
            // Drain the renderer, then leave two display frames for HWUI to
            // finish its asynchronous final post before Navigation detaches it.
            val surface = surfaceRef.getAndSet(null)
            if (surface != null) surface.shutdownThen(onExitToMenu)
            else onExitToMenu()
        }
    }
    val ui by viewModel.ui.collectAsState()
    val engine = viewModel.game
    LaunchedEffect(ui.phase, ui.canRequestAssist, ui.sideMissionCompletion, rewardedAds) {
        if (ui.phase == GamePhase.GAME_OVER || ui.canRequestAssist ||
            ui.sideMissionCompletion != null
        ) rewardedAds?.load()
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, audio) {
        // Ak už sme RESUMED, ON_RESUME znova nepríde – sync mute hneď.
        audio.setMuted(
            !lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        )
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    viewModel.onLifecyclePause()
                    audio.setMuted(true)
                    surfaceRef.get()?.setSceneActive(false)
                }
                Lifecycle.Event.ON_RESUME -> {
                    viewModel.onLifecycleResume()
                    audio.setMuted(false)
                    surfaceRef.get()?.setSceneActive(true)
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            audio.release()
        }
    }

    LaunchedEffect(audio) {
        val interval = GameConfig.TARGET_FRAME_NANOS
        var last = withFrameNanos { it }
        var nextDue = last + interval
        var sceneAge=1f
        while (true) {
            withFrameNanos { now ->
                if (now + GameConfig.FRAME_LOCK_TOLERANCE_NANOS < nextDue) return@withFrameNanos
                nextDue += interval
                if (nextDue <= now) nextDue = now + interval
                val dt = ((now - last) / 1_000_000_000.0).toFloat()
                last = now
                if (dt > 0f) {
                    val step = dt.coerceAtMost(GameConfig.MAX_FRAME_TIME)
                    viewModel.onFrame(step)
                    val cabinOpen = showInventory || showCar ||
                        viewModel.ui.value.exploring
                    audio.update(
                        viewModel.game,
                        step,
                        paused = viewModel.ui.value.paused ||
                            viewModel.ui.value.sideMissionCompletion != null,
                        cabinDuck = if (cabinOpen) 1f else 0f
                    )
                    sceneAge+=step
                    val state=viewModel.ui.value
                    val sceneInterval=when {
                        state.paused || cabinOpen -> 0.2f
                        state.phase==GamePhase.DRIVING -> 1f/60f
                        else -> 1f/12f
                    }
                    if(sceneAge>=sceneInterval) {
                        sceneAge=0f
                        surfaceRef.get()?.requestGameRender()
                    }
                }
            }
        }
    }

    val garageOpen = showInventory || showCar
    DisposableEffect(garageOpen) {
        viewModel.setGarageOpen(garageOpen)
        onDispose { viewModel.setGarageOpen(false) }
    }

    CompositionLocalProvider(
        LocalTextStyle provides LocalTextStyle.current.copy(fontFamily = FontFamily.SansSerif)
    ) {
    Box(Modifier.fillMaxSize().background(GameColors.panelSoft)) {
        // Scéna ide cez celú plochu vrátane výrezu. Odsadenie plátna do čiernych
        // pásov po stranách sa neosvedčilo – obraz tým utrpel viac, než získal.
        AndroidView(
            factory = { context ->
                val surface = ExpeditionSurfaceView(context, viewModel)
                surface.lightningFlashesEnabled = lightningFlashesEnabled
                surfaceRef.set(surface)
                surface.setSceneActive(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
                surface
            },
            update = { surface ->
                surface.lightningFlashesEnabled = lightningFlashesEnabled
                surfaceRef.set(surface)
            },
            onRelease = { surface ->
                val sceneSurface = surface as GameSceneSurface
                sceneSurface.shutdown()
                surfaceRef.compareAndSet(sceneSurface, null)
            },
            modifier = Modifier
                .fillMaxSize()
        )

        val junctionChoices = engine.junctionChoices

        CompositionLocalProvider(LocalDensity provides hudDensity) {
        // …ale ovládanie a HUD sa držia mimo výrezu, zaoblených rohov aj
        // systémových líšt. Bez navigačnej lišty sa spodok palubnej dosky
        // schoval pod gesto-pruh a kontrolky boli orezané.
        // Cutout dp sú v škálovanej hustote, aby padding v pixeloch sedel
        // na skutočný výrez aj po zjednotení Full HD / 2K.
        val layoutDir = layoutDirection
        val safe = WindowInsets.safeDrawing
        val hudDp = LocalDensity.current
        val cutoutSide = with(hudDp) {
            max(safe.getLeft(this, layoutDir).toDp(), safe.getRight(this, layoutDir).toDp())
        }
        val cutoutTop = with(hudDp) { safe.getTop(this).toDp() }
        val cutoutBottom = with(hudDp) { safe.getBottom(this).toDp() }
        Box(
            Modifier
                .fillMaxSize()
                .padding(
                    start = cutoutSide,
                    end = cutoutSide,
                    top = cutoutTop,
                    bottom = cutoutBottom
                )
        ) {

            TripBadge(
                ui = ui,
                showFps = showFps,
                compact = compactHud,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = SCREEN_MARGIN, top = SCREEN_MARGIN)
            )

            // Rozcestník má prednosť hore; hlášky sa pod neho odsunú, aby sa
            // neprekrývali. Dole už miesto nie je – tam sedí palubná doska.
            AlertColumn(
                ui = ui,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    // Vyhradí skutočný stred medzi odznakom jazdy a tromi
                    // pravými tlačidlami. Samotný padding na prirodzene širokom
                    // obsahu dovolil dlhej hláške vliezť pod obe skupiny.
                    .fillMaxWidth()
                    .padding(
                        top = if (compactHud) 8.dp else 12.dp,
                        start = if (compactHud) 118.dp else 150.dp,
                        end = if (compactHud) 154.dp else 210.dp
                    )
            )

            val lootDistance = ui.lootableWreckAheadM
            if (ui.phase == GamePhase.DRIVING && lootDistance != null) {
                LootAheadIndicator(
                    distanceM = lootDistance,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(
                            top = if (ui.pursuitStormKind != null) 92.dp
                            else if (compactHud) 50.dp else 58.dp
                        )
                )
            }

            ui.pursuitStormKind?.let { storm ->
                StormChaseIndicator(
                    kind = storm,
                    gapM = ui.pursuitStormGapM ?: 0,
                    caught = ui.pursuitStormCaught,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = if (compactHud) 50.dp else 58.dp)
                )
            }

            ui.sideMissionTitle?.takeIf { ui.phase == GamePhase.DRIVING }?.let { missionTitle ->
                SideMissionIndicator(
                    title = missionTitle,
                    progress = ui.sideMissionProgress.orEmpty(),
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(
                            start = SCREEN_MARGIN,
                            bottom = if (compactHud) 126.dp else 150.dp
                        )
                )
            }

            if (ui.phase == GamePhase.DRIVING && ui.approachingJunction &&
                junctionChoices.size >= 2
            ) {
                JunctionRouteChooser(
                    choices = junctionChoices,
                    selectedId = ui.pendingChoiceId ?: engine.segment.selectedChoice?.id,
                    distanceM = ui.junctionDistanceM.toInt().coerceAtLeast(0),
                    onChoose = viewModel::selectBranch,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = SCREEN_MARGIN)
                )
            }

            SideIcons(
                ui = ui,
                onToggleLights = { viewModel.toggleHeadlights() },
                onToggleRoofLights = { viewModel.toggleRoofLights() },
                onTogglePause = { viewModel.setPaused(!ui.paused) },
                // Vrch obrazovky je po presune vitals do dosky voľný – ikony
                // tam nezavadzajú a neplávajú cez scénu.
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = SCREEN_MARGIN, top = SCREEN_MARGIN)
            )

            // --- Ovládanie podľa fázy ----------------------------------------
            if (ui.phase == GamePhase.DRIVING) {
                GameControls(
                    onThrottle = viewModel::onThrottle,
                    onSteering = viewModel::onSteering,
                    onBrakeChanged = viewModel::onBrakeChanged,
                    onStop = viewModel::stop,
                    cruiseOn = ui.cruiseOn,
                    cruiseAvailable = ui.engineRunning,
                    onToggleCruise = viewModel::toggleCruise,
                    buildingNearby = ui.hasNearbyBuilding,
                    throttleMode = throttleMode,
                    compact = compactHud,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Palubná doska drží spodok obrazovky vždy – budíky a kontrolky
            // patria pred oči aj za jazdy, ovládanie sa v nej len prepína.
            if (ui.phase != GamePhase.GAME_OVER) {
                Dashboard(
                    ui = ui,
                    onStart = { viewModel.startEngine() },
                    onStopEngine = { viewModel.stopEngine() },
                    onInventory = {
                        viewModel.setGarageOpen(true)
                        showInventory = true
                    },
                    onCar = {
                        viewModel.setGarageOpen(true)
                        showCar = true
                    },
                    onEnter = { viewModel.enterBuilding() },
                    onLeave = { viewModel.leaveBuilding() },
                    onDrive = { viewModel.resume() },
                    onRest = { viewModel.restUntilDawn() },
                    onAssist = {
                        activity?.let { host ->
                            val shown = rewardedAds?.show(host, onReward = {
                                viewModel.recoverFromAd()
                            }) == true
                            if (!shown) {
                                rewardedAds?.load()
                                viewModel.markRescueAdUnavailable()
                            }
                        } ?: viewModel.markRescueAdUnavailable()
                    },
                    assistAdReady = adReady && activity != null,
                    gaugeStyle = gaugeStyle,
                    compact = compactHud,
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }

            // Korisť sa prehrabáva pri bežiacom HUD – stmavenie nemá.
            if (ui.exploring) {
                val lootModifier = if (compactLoot) {
                    Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                        .workshopSheet(compact = true)
                } else {
                    Modifier
                        .align(Alignment.CenterEnd)
                        .padding(top = 104.dp, bottom = 84.dp, end = 78.dp)
                        .fillMaxWidth(0.42f)
                        .widthIn(max = 560.dp)
                        .fillMaxHeight()
                }
                LootPanel(
                    engine = engine,
                    bagRevision = ui.bagRevision,
                    partsRevision = ui.partsRevision,
                    scrap = ui.scrap,
                    relayNodes = ui.relayNodes,
                    adReady = adReady && activity != null,
                    onTake = { viewModel.takeLoot(it) },
                    onTakeAll = { viewModel.takeAllLoot() },
                    onStartMission = { viewModel.startSideMission() },
                    onShelter = { viewModel.shelterFromPursuit() },
                    onScrapAll = { viewModel.scrapAllLoot() },
                    onRefuel = { viewModel.refuelFromPump(it) },
                    onActivateDepot = { viewModel.activateDepot() },
                    onPaint = { viewModel.paintBody(it) },
                    onUpgrade = { viewModel.upgradeWithScrap(it) },
                    onUpgradeFromAd = { slot ->
                        if (activity != null) {
                            val shown = rewardedAds?.show(activity, onReward = {
                                viewModel.upgradeFromAd(slot)
                            }) == true
                            if (!shown) rewardedAds?.load()
                        }
                    },
                    onPaintFromAd = { paint ->
                        if (activity != null) {
                            val shown = rewardedAds?.show(activity, onReward = {
                                viewModel.paintBodyFromAd(paint)
                            }) == true
                            if (!shown) rewardedAds?.load()
                        }
                    },
                    onClose = { viewModel.leaveBuilding() },
                    modifier = lootModifier
                )
            }
        }

        // --- Prekryvné panely ---------------------------------------------
        // Bývali vnútri odsadenej plochy a spolu s nimi aj stmavenie. Pri
        // výreze tak po stranách ostali svetlé pruhy bežiacej hry a otvorené
        // menu vyzeralo, akoby sa scéna kreslila len v strede obrazovky.
        val safeArea = PaddingValues(
            start = cutoutSide,
            end = cutoutSide,
            top = cutoutTop,
            bottom = cutoutBottom
        )
        Box(Modifier.fillMaxSize()) {
            if (showInventory) {
                Overlay(safeArea, onDismiss = { showInventory = false }) {
                    InventoryPanel(
                        engine = engine,
                        bagRevision = ui.bagRevision,
                        onUse = { i, slot -> viewModel.useItem(i, slot) },
                        onUseFromBoot = { i, slot -> viewModel.useBootItem(i, slot) },
                        onScrap = { viewModel.scrapItem(it) },
                        onScrapBoot = { viewModel.scrapBootItem(it) },
                        onStow = { viewModel.stowInBoot(it) },
                        onTake = { viewModel.takeFromBoot(it) },
                        onClose = { showInventory = false },
                        modifier = Modifier.align(Alignment.Center)
                            .workshopSheet(compactGarage)
                    )
                }
            }

            if (showCar) {
                val partsRev = ui.partsRevision
                val bagRev = ui.bagRevision
                val scrapNow = ui.scrap
                val ftHealth = ui.frontTireHealth
                val rtHealth = ui.rearTireHealth
                val ftInjury = ui.frontTireInjury
                val rtInjury = ui.rearTireInjury
                Overlay(
                    safeArea,
                    invalidate = partsRev,
                    onDismiss = {}
                ) {
                    CarPanel(
                        engine = engine,
                        bagRevision = bagRev,
                        partsRevision = partsRev,
                        scrap = scrapNow,
                        frontTireHealth = ftHealth,
                        rearTireHealth = rtHealth,
                        frontTireInjury = ftInjury,
                        rearTireInjury = rtInjury,
                        debugRepair = viewModel.debugOptions.repairControls,
                        onRepair = { viewModel.repair(it) },
                        onScrapRepair = { viewModel.repairWithScrap(it) },
                        onPatchPuncture = { viewModel.repairPuncture(it) },
                        onDrain = { fluid, litres -> viewModel.drainFluid(fluid, litres) },
                        onUnmount = { viewModel.unmount(it) },
                        onCancelUnmount = { viewModel.cancelPendingUnmount() },
                        onSwapTyres = { viewModel.swapTyres() },
                        onClose = { showCar = false },
                        modifier = Modifier.align(Alignment.Center)
                            .workshopSheet(compactGarage)
                    )
                }
            }

            if (ui.safeHavenDecisionPending) {
                Overlay(safeArea) {
                    SafeHavenPanel(
                        onFinish = viewModel::finishJourney,
                        onContinue = viewModel::continueEndless,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .dialogCard(maxWidth = 440.dp)
                    )
                }
            }

            ui.sideMissionCompletion?.let { completion ->
                Overlay(safeArea) {
                    SideMissionCompletePanel(
                        completion = completion,
                        adReady = adReady && activity != null,
                        onCollect = viewModel::collectSideMissionReward,
                        onDoubleReward = {
                            activity?.let { host ->
                                val shown = rewardedAds?.show(host, onReward = {
                                    viewModel.claimDoubleSideMissionReward()
                                }) == true
                                if (!shown) rewardedAds?.load()
                            }
                        },
                        modifier = Modifier
                            .align(Alignment.Center)
                            .dialogCard(maxWidth = 390.dp)
                    )
                }
            }

            if (ui.paused && ui.phase != GamePhase.GAME_OVER && !ui.safeHavenDecisionPending) {
                Overlay(safeArea) {
                    PausePanel(
                        ui = ui,
                        adReady = adReady && activity != null,
                        onResume = { viewModel.setPaused(false) },
                        onRestart = {
                            viewModel.retry()
                            viewModel.setPaused(false)
                        },
                        onEndRun = { viewModel.endRun() },
                        onAssist = {
                            activity?.let { host ->
                                val shown = rewardedAds?.show(host, onReward = {
                                    viewModel.recoverFromAd()
                                }) == true
                                if (!shown) {
                                    rewardedAds?.load()
                                    viewModel.markRescueAdUnavailable()
                                }
                            } ?: viewModel.markRescueAdUnavailable()
                        },
                        onMenu = exitToMenu,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .dialogCard(maxWidth = 360.dp)
                    )
                }
            }

            if (ui.phase == GamePhase.GAME_OVER) {
                Overlay(safeArea) {
                    GameOverPanel(
                        engine = engine,
                        scrapDoubled = ui.scrapDoubled,
                        adReady = adReady && activity != null,
                        onDoubleScrap = {
                            activity?.let { host ->
                                rewardedAds?.show(host, onReward = { viewModel.claimDoubleScrap() })
                            }
                        },
                        onRescue = {
                            activity?.let { host ->
                                val shown = rewardedAds?.show(host, onReward = {
                                    viewModel.recoverFromAd()
                                }) == true
                                if (!shown) {
                                    rewardedAds?.load()
                                    viewModel.markRescueAdUnavailable()
                                }
                            } ?: viewModel.markRescueAdUnavailable()
                        },
                        onRetry = { viewModel.retry() },
                        onMenu = { viewModel.finalizeRun(); exitToMenu() },
                        modifier = Modifier
                            .align(Alignment.Center)
                            .dialogCard(maxWidth = 420.dp)
                    )
                }
            }
        }
        }
    }
    }
}

/**
 * Prekryv nad hrou: stmavenie cez celú obrazovku, obsah v bezpečnej ploche.
 *
 * Rozdelenie je podstatné. Keby stmavenie dostalo to isté odsadenie ako panel,
 * pri výreze by po stranách presvitala nezatmavená hra – panel patrí mimo
 * výrezu, tieň nie.
 */
@Composable
private fun Overlay(
    insets: PaddingValues,
    invalidate: Int = 0,
    onDismiss: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    @Suppress("UNUSED_VARIABLE")
    val seen = invalidate
    Scrim(onDismiss = onDismiss)
    Box(Modifier.fillMaxSize().padding(insets), content = content)
}

/** Pauza / game over – šírka podľa obsahu, nie podľa šírky monitora. */
private fun Modifier.dialogCard(maxWidth: Dp): Modifier =
    fillMaxWidth(0.86f).widthIn(max = maxWidth)

/** Inventár, auto, korisť – na tablete strop, na telefóne takmer celá plocha. */
private fun Modifier.workshopSheet(compact: Boolean): Modifier =
    fillMaxWidth(if (compact) 0.98f else 0.88f)
        .widthIn(max = 900.dp)
        .fillMaxHeight(if (compact) 0.96f else 0.86f)
        .heightIn(max = 640.dp)


// ---------------------------------------------------------------------------
// Križovatka
// ---------------------------------------------------------------------------

@Composable
private fun JunctionRouteChooser(
    choices: List<BranchChoice>,
    selectedId: Int?,
    distanceM: Int,
    onChoose: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val copy = LocalCopy.current
    Column(
        modifier
            .widthIn(min = 150.dp, max = 210.dp)
            .background(GameColors.panel.copy(alpha = 0.90f), RoundedCornerShape(12.dp))
            .border(1.dp, GameColors.outline, RoundedCornerShape(12.dp))
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Text(
            "${copy.junction} · $distanceM m",
            color = GameColors.text,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
        choices.take(2).forEachIndexed { index, choice ->
            val selected = selectedId == choice.id
            val accent = Color(choice.style.accentArgb)
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(
                        accent.copy(alpha = if (selected) 0.38f else 0.14f),
                        RoundedCornerShape(8.dp)
                    )
                    .border(
                        if (selected) 2.dp else 1.dp,
                        accent.copy(alpha = if (selected) 1f else 0.55f),
                        RoundedCornerShape(8.dp)
                    )
                    .clickable { onChoose(choice.id) }
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                Text(if (index == 0) "←" else "→", color = accent, fontSize = 18.sp)
                Column(Modifier.weight(1f)) {
                    Text(
                        copy.branchLabel(choice.style) + if (choice.winter) " ❄" else "",
                        color = GameColors.text,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                    Text(
                        copy.branchHint(choice.style),
                        color = GameColors.textDim,
                        fontSize = 9.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun StormChaseIndicator(
    kind: PursuitStormKind,
    gapM: Int,
    caught: Boolean,
    modifier: Modifier = Modifier
) {
    val copy = LocalCopy.current
    val color = if (caught) GameColors.danger else GameColors.warn
    Row(
        modifier
            .background(GameColors.panel.copy(alpha = 0.90f), RoundedCornerShape(10.dp))
            .border(1.dp, color, RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("≋", color = color, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Column {
            Text(
                copy.stormHud(kind, gapM, caught),
                color = GameColors.text,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
            Text(copy.stormEscapeHint, color = color, fontSize = 9.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun SideMissionIndicator(title: String, progress: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .background(GameColors.hudBg.copy(alpha = 0.88f), RoundedCornerShape(8.dp))
            .border(1.dp, GameColors.accent.copy(alpha = 0.75f), RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(title, color = GameColors.accent, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Text(progress, color = GameColors.text, fontSize = 10.sp, maxLines = 1)
    }
}

@Composable
private fun JunctionPanel(
    engine: GameEngine,
    onChoose: (Int) -> Unit,
    onInventory: () -> Unit,
    onCar: () -> Unit,
    modifier: Modifier = Modifier
) {
    val copy = LocalCopy.current
    GamePanel(
        title = copy.junction,
        subtitle = copy.junctionSubtitle,
        modifier = modifier,
        header = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GameButton(copy.pack, onInventory, compact = true)
                GameButton(copy.car, onCar, compact = true)
            }
        }
    ) {
        Row(
            Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            engine.junctionChoices.forEach { choice ->
                val accent = Color(choice.style.accentArgb)
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(accent.copy(alpha = 0.22f), RoundedCornerShape(12.dp))
                        .border(1.dp, accent, RoundedCornerShape(12.dp))
                        .clickable { onChoose(choice.id) }
                        .padding(14.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            copy.branchLabel(choice.style) + if (choice.winter) " ❄" else "",
                            color = GameColors.text,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(copy.branchHint(choice.style), color = GameColors.textDim, fontSize = 13.sp)
                    }
                    Chip(copy.takeIt, accent, filled = true)
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Inventár
// ---------------------------------------------------------------------------

@Composable
private fun InventoryPanel(
    engine: GameEngine,
    bagRevision: Int,
    onUse: (Int, ComponentSlot?) -> Unit,
    onUseFromBoot: (Int, ComponentSlot?) -> Unit,
    onScrap: (Int) -> Unit,
    onScrapBoot: (Int) -> Unit,
    onStow: (Int) -> Unit,
    onTake: (Int) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    @Suppress("UNUSED_VARIABLE")
    val rev = bagRevision
    val pack = engine.inventory
    val boot = engine.boot
    val copy = LocalCopy.current

    GamePanel(
        title = copy.pack,
        subtitle = copy.packSubtitle(
            engine.scrap,
            pack.usedSlots, pack.slots.size, pack.totalWeight.toInt(), pack.maxWeight.toInt(),
            boot.usedSlots, boot.slots.size, boot.totalWeight.toInt(), boot.maxWeight.toInt()
        ),
        onClose = onClose,
        modifier = modifier
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val compact = maxWidth < 800.dp
            val panelMaxHeight = maxHeight
            val packCol: @Composable (Modifier) -> Unit = { colMod ->
                Column(
                    colMod
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState())
                ) {
                    SectionLabel(copy.onYou)
                    Spacer(Modifier.height(6.dp))
                    if (pack.usedSlots == 0) {
                        Text(copy.packEmpty, color = GameColors.textDim, fontSize = 14.sp)
                    }
                    pack.slots.forEachIndexed { i, stack ->
                        if (stack == null) return@forEachIndexed
                        val tyre = stack.def.axleTire
                        val kit = stack.def.isPunctureKit
                        val trailer = stack.def.isUtilityTrailer
                        val relayModule = stack.def.id == ItemCatalog.RELAY_MODULE.id
                        ItemCard(
                            revision = bagRevision,
                            stack = stack,
                            mounted = if (tyre) engine.car.worstTyre()
                            else stack.def.mountsTo?.let { engine.car.parts[it] },
                            primaryLabel = when {
                                stack.def.fluid != null -> copy.pourIn
                                kit -> copy.use
                                trailer -> copy.hitch
                                relayModule -> copy.forRelay
                                else -> copy.fit
                            },
                            onPrimary = { if (!relayModule) onUse(i, null) },
                            primaryEnabled = !relayModule,
                            secondaryLabel = if (engine.bootReachable) copy.toBoot else null,
                            onSecondary = if (engine.bootReachable) ({ onStow(i) }) else null,
                            onScrap = { onScrap(i) },
                            onFitFront = if (tyre) ({ onUse(i, ComponentSlot.TIRE_FRONT) }) else null,
                            onFitRear = if (tyre) ({ onUse(i, ComponentSlot.TIRE_REAR) }) else null,
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                        )
                    }
                }
            }
            val bootCol: @Composable (Modifier) -> Unit = { colMod ->
                Column(
                    colMod
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState())
                ) {
                    SectionLabel(if (engine.bootReachable) copy.boot else copy.bootStopAtCar)
                    Spacer(Modifier.height(6.dp))
                    if (boot.usedSlots == 0) {
                        Text(copy.bootEmpty, color = GameColors.textDim, fontSize = 14.sp)
                    }
                    boot.slots.forEachIndexed { i, stack ->
                        if (stack == null) return@forEachIndexed
                        val tyre = stack.def.axleTire
                        val kit = stack.def.isPunctureKit
                        val trailer = stack.def.isUtilityTrailer
                        val mountable = stack.def.fluid != null ||
                            stack.def.mountTargets().isNotEmpty() ||
                            kit || trailer
                        val relayModule = stack.def.id == ItemCatalog.RELAY_MODULE.id
                        ItemCard(
                            revision = bagRevision,
                            stack = stack,
                            mounted = if (tyre) engine.car.worstTyre()
                            else stack.def.mountsTo?.let { engine.car.parts[it] },
                            primaryLabel = when {
                                stack.def.fluid != null -> copy.pourIn
                                kit -> copy.use
                                trailer -> copy.hitch
                                relayModule -> copy.forRelay
                                else -> copy.fit
                            },
                            onPrimary = { if (mountable && !relayModule) onUseFromBoot(i, null) },
                            primaryEnabled = mountable && !relayModule,
                            secondaryLabel = copy.take,
                            onSecondary = { onTake(i) },
                            onScrap = { onScrapBoot(i) },
                            onFitFront = if (tyre) ({ onUseFromBoot(i, ComponentSlot.TIRE_FRONT) }) else null,
                            onFitRear = if (tyre) ({ onUseFromBoot(i, ComponentSlot.TIRE_REAR) }) else null,
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                        )
                    }
                }
            }
            if (compact) {
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    CarSummary(
                        engine,
                        bagRevision,
                        Modifier.fillMaxWidth().heightIn(max = panelMaxHeight * 0.40f)
                    )
                    Row(
                        Modifier.weight(1f).fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        packCol(Modifier.weight(1f))
                        bootCol(Modifier.weight(1f))
                    }
                }
            } else {
                Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    packCol(Modifier.weight(1.25f))
                    bootCol(Modifier.weight(1.25f))
                    CarSummary(engine, bagRevision, Modifier.weight(1.2f).fillMaxHeight())
                }
            }
        }
    }
}

@Composable
private fun FluidStat(
    label: String,
    ratio: Float,
    value: String,
    color: Color,
    inner: Float,
    innerColor: Color,
    modifier: Modifier = Modifier
) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        StatBar(
            label, ratio, value, color,
            modifier = Modifier.fillMaxWidth(),
            inner = inner,
            innerColor = innerColor,
            barWidth = null
        )
    }
}

/** Pravý stĺpec inventára: čo je práve v aute. */
@Composable
private fun CarSummary(engine: GameEngine, revision: Int, modifier: Modifier = Modifier) {
    // Compose nevidí do GameEngine – bez tohto parametra by sa stĺpec „v aute“
    // po namontovaní dielu neprekreslil a ukazoval by staré čísla.
    @Suppress("UNUSED_VARIABLE")
    val rev = revision
    val copy = LocalCopy.current
    val car = engine.car
    Column(
        modifier
            .background(GameColors.panelSoft, RoundedCornerShape(12.dp))
            .border(1.dp, GameColors.outline, RoundedCornerShape(12.dp))
            .padding(12.dp)
            .verticalScroll(rememberScrollState())
    ) {
        SectionLabel(copy.inTheCar)
        Text(
            copy.bodyPaint(copy.paintName(VehiclePaint.at(car.bodyPaintIndex))),
            color = Color(VehiclePaint.at(car.bodyPaintIndex).argb),
            fontSize = 12.sp
        )
        val rearPercent = (car.rearWeightBias * 100f).toInt()
        Text(
            copy.massLine(car.totalMassKg.toInt(), 100 - rearPercent, rearPercent),
            color = GameColors.textDim,
            fontSize = 11.sp
        )
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FluidStat(
                copy.fuelKindName(car.requiredFuelKind).uppercase(),
                (car.fuel / car.fuelCapacity).coerceIn(0f, 1f),
                "${car.fuel.toInt()} L",
                levelColor((car.fuel / car.fuelCapacity).coerceIn(0f, 1f)),
                car.fuelPurity,
                purityColor(car.fuelPurity),
                Modifier.weight(1f)
            )
            FluidStat(
                copy.oil,
                (car.oil / car.oilCapacity).coerceIn(0f, 1f),
                String.format("%.1f L", car.oil),
                levelColor((car.oil / car.oilCapacity).coerceIn(0f, 1f)),
                car.oilPurity,
                purityColor(car.oilPurity),
                Modifier.weight(1f)
            )
            FluidStat(
                copy.coolant,
                (car.coolant / car.coolantCapacity).coerceIn(0f, 1f),
                String.format("%.1f L", car.coolant),
                levelColor((car.coolant / car.coolantCapacity).coerceIn(0f, 1f)),
                car.coolantPurity,
                purityColor(car.coolantPurity),
                Modifier.weight(1f)
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            copy.quality(copy.fluidGradeName(FluidGrade.of(minOf(car.fuelPurity, car.oilPurity, car.coolantPurity))).lowercase()),
            color = purityColor(minOf(car.fuelPurity, car.oilPurity, car.coolantPurity)),
            fontSize = 12.sp
        )
        if (car.fuel > 0.05f) {
            val diesel = (car.fuelDieselFraction * 100f).toInt()
            Text(
                copy.tankMix(100 - diesel, diesel, car.wrongFuelFraction >= 0.15f),
                color = if (car.wrongFuelFraction >= 0.15f) GameColors.danger else GameColors.textDim,
                fontSize = 12.sp
            )
        }

        Spacer(Modifier.height(12.dp))
        SectionLabel(copy.parts)
        Spacer(Modifier.height(6.dp))
        ComponentSlot.entries.forEach { slot ->
            val part = car.parts[slot]
            Row(
                Modifier.fillMaxWidth().padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    buildString {
                        append(copy.slotName(slot))
                        part?.def?.let { copy.itemSlotDetail(it, slot) }?.takeIf { it.isNotBlank() }
                            ?.let { append(" ($it)") }
                        part?.paintIndex?.takeIf { it >= 0 && part.def.mountsTo?.takesBodyPaint == true }
                            ?.let { append(" · ${copy.paintName(VehiclePaint.at(it))}") }
                    },
                    color = if (part == null) GameColors.textDim else GameColors.text,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(end = 8.dp)
                )
                if (part == null) {
                    Chip(copy.missingChip, GameColors.danger)
                } else if (!part.def.hasDurability) {
                    Chip(copy.permanent, GameColors.ok)
                } else {
                    Text(
                        buildString {
                            append("${(part.health * 100).toInt()} %")
                            when (part.injury) {
                                TireInjury.PUNCTURED -> append(" · FLAT")
                                TireInjury.SHREDDED -> append(" · RIM")
                                else -> Unit
                            }
                        },
                        color = if (part.injury == TireInjury.INFLATED) {
                            healthColor(part.health)
                        } else {
                            GameColors.danger
                        },
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }
    }
}

/** Karta predmetu – rovnaká v inventári aj v budove. */
@Composable
private fun ItemCard(
    stack: ItemStack,
    mounted: MountedPart?,
    primaryLabel: String,
    onPrimary: () -> Unit,
    primaryEnabled: Boolean = true,
    /**
     * Compose nevidí do [ItemStack] – po naliatí sa mení jeho obsah, nie
     * identita, takže bez tohto by nadpis ďalej ukazoval pôvodný objem.
     */
    revision: Int,
    modifier: Modifier = Modifier,
    secondaryLabel: String? = null,
    onSecondary: (() -> Unit)? = null,
    /** Rozobratie nepotrebnej veci na materiál. */
    onScrap: (() -> Unit)? = null,
    /** Gumu si hráč zaradí sám – predok alebo zadok. */
    onFitFront: (() -> Unit)? = null,
    onFitRear: (() -> Unit)? = null,
) {
    @Suppress("UNUSED_VARIABLE")
    val rev = revision
    val copy = LocalCopy.current
    val def = stack.def
    val isFluid = def.fluid != null
    // Staršie save nemali pri každom plechu uložený paintIndex. Použi rovnaký
    // stabilný fallback ako renderer auta, aby aj taký Hood dostal náhľad farby.
    val previewPaint = def.mountsTo?.takeIf { it.takesBodyPaint }?.let { slot ->
        val index = if (stack.paintIndex >= 0) {
            stack.paintIndex
        } else {
            (def.id.hashCode() * 31 + slot.ordinal * 17) and Int.MAX_VALUE
        }
        VehiclePaint.at(index)
    }
    Column(
        modifier
            .background(GameColors.panelHigh, RoundedCornerShape(10.dp))
            .border(1.dp, GameColors.outline, RoundedCornerShape(10.dp))
            .padding(10.dp)
    ) {
        // Popis hore, ovládanie pod ním. Tlačidlá vedľa obsahu ukrajovali
        // šírku textu a karta bola zbytočne vysoká a úzka.
        Column(Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth()) {
                Text(
                    // Skutočný zostatok v nádobe, nie menovitý objem × počet.
                    if (isFluid) "${copy.itemName(def)} · ${String.format("%.1f", stack.fluidLitres)} L"
                    else "${copy.itemName(def)}${if (stack.count > 1) " ×${stack.count}" else ""}",
                    color = GameColors.text,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(3.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (isFluid) {
                        Chip(copy.fluidGradeName(stack.grade), purityColor(stack.purity))
                        Chip("${(stack.purity * 100).toInt()} %", purityColor(stack.purity))
                    } else if (def.isPunctureKit) {
                        Chip(copy.patchesAFlat, GameColors.ok)
                    } else if (!def.hasDurability) {
                        Chip(copy.permanent, GameColors.ok)
                    } else {
                        Chip(copy.conditionName(stack.condition), healthColor(stack.health))
                        Chip("${(stack.health * 100).toInt()} %", healthColor(stack.health))
                    }
                    Chip("${(def.weight * stack.count).toInt()} kg", GameColors.textDim)
                }
                if (previewPaint != null) {
                    val paint = previewPaint
                    Spacer(Modifier.height(5.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PaintedPartPreview(
                            slot = def.mountsTo,
                            paint = paint,
                            defId = def.id,
                            modifier = Modifier.width(68.dp).height(32.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(copy.paint, color = GameColors.textDim, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            Text(copy.paintName(paint), color = Color(paint.argb), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
                if (def.mountsTo != null) {
                    Spacer(Modifier.height(4.dp))
                    val better = if (!def.hasDurability) {
                        mounted == null || def.extraSlots > mounted.def.extraSlots
                    } else mounted == null || stack.health > mounted.health + 0.05f
                    val worse = if (!def.hasDurability) {
                        mounted != null && def.extraSlots < mounted.def.extraSlots
                    } else mounted != null && stack.health < mounted.health - 0.05f
                    Text(
                        when {
                            mounted == null -> copy.carCompareNoneBetter()
                            !def.hasDurability && better ->
                                copy.carCompareSlots(mounted.def.extraSlots, def.extraSlots)
                            !def.hasDurability && worse ->
                                copy.carCompareSmallerStorage(mounted.def.extraSlots)
                            !def.hasDurability -> copy.permanentStorageUpgrade
                            better -> copy.carCompareBetter((mounted.health * 100).toInt())
                            worse -> copy.carCompareWorse((mounted.health * 100).toInt())
                            else -> copy.carCompareSame((mounted.health * 100).toInt())
                        },
                        color = when {
                            better -> GameColors.ok
                            worse -> GameColors.danger
                            else -> GameColors.textDim
                        },
                        fontSize = 11.sp
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            // Dva riadky namiesto jedného: hlavná akcia hore, presun a
            // zahodenie dole. V jednom rade sa štyri tlačidlá nezmestili
            // a dlhšie popisy („TO BOOT“) sa orezávali.
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Poradie ako na aute v bočnom pohľade: zadok vľavo, predok vpravo.
                if (onFitFront != null && onFitRear != null) {
                    GameButton(copy.rear, onFitRear, compact = true, style = BtnStyle.Primary, modifier = Modifier.weight(1f))
                    GameButton(copy.front, onFitFront, compact = true, style = BtnStyle.Primary, modifier = Modifier.weight(1f))
                } else {
                    GameButton(
                        primaryLabel,
                        onPrimary,
                        compact = true,
                        style = BtnStyle.Primary,
                        enabled = primaryEnabled,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            if ((secondaryLabel != null && onSecondary != null) || onScrap != null) {
                Spacer(Modifier.height(6.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (secondaryLabel != null && onSecondary != null) {
                        // Ghost má obrys – Secondary má rovnaké pozadie ako karta
                        // a tlačidlo tak vyzeralo ako obyčajný text.
                        GameButton(secondaryLabel, onSecondary, compact = true, style = BtnStyle.Ghost, modifier = Modifier.weight(1f))
                    }
                    if (onScrap != null) {
                        GameButton(
                            copy.scrapPlus(stack.scrapValue),
                            onScrap,
                            compact = true,
                            style = BtnStyle.Danger,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

/** Inventory swatches are UI icons; all vehicle inspection uses the live 3D model. */
@Composable
private fun PaintedPartPreview(slot:ComponentSlot?,paint:VehiclePaint,defId:String?=null,modifier:Modifier=Modifier) {
    val icon=when(slot) {
        ComponentSlot.TIRE_FRONT,ComponentSlot.TIRE_REAR -> AutomotiveIcon.TYRE
        ComponentSlot.HEADLIGHT,ComponentSlot.TAILLIGHT -> AutomotiveIcon.LOW_BEAM
        ComponentSlot.BATTERY -> AutomotiveIcon.BATTERY
        ComponentSlot.RADIATOR -> AutomotiveIcon.COOLANT
        ComponentSlot.SUSPENSION -> AutomotiveIcon.SPRING
        else -> AutomotiveIcon.ENGINE
    }
    Box(modifier.background(GameColors.panelSoft,RoundedCornerShape(6.dp)),contentAlignment=Alignment.Center) {
        AutomotiveIconView(icon,Color(paint.argb),Modifier.fillMaxSize().padding(5.dp))
    }
}

// ---------------------------------------------------------------------------
// Budova / loot
// ---------------------------------------------------------------------------

@Composable
private fun LootPanel(
    engine: GameEngine,
    bagRevision: Int,
    partsRevision: Int,
    scrap: Int,
    relayNodes: Int,
    adReady: Boolean,
    onTake: (Int) -> Unit,
    onTakeAll: () -> Unit,
    onStartMission: () -> Unit,
    onShelter: () -> Unit,
    onScrapAll: () -> Unit,
    onRefuel: (FuelKind) -> Unit,
    onActivateDepot: () -> Unit,
    onPaint: (VehiclePaint) -> Unit,
    onPaintFromAd: (VehiclePaint) -> Unit,
    onUpgrade: (ComponentSlot) -> Unit,
    onUpgradeFromAd: (ComponentSlot) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val b = engine.activeBuilding ?: return
    @Suppress("UNUSED_VARIABLE")
    val rev = bagRevision + 31 * partsRevision + scrap
    val relayAlreadyOnline = b.relayRestored || (b.relayIndex in 0 until relayNodes)
    val isNextRelay = relayNodes < Journey.goals.size && (b.relayIndex < 0 || b.relayIndex == relayNodes)
    val restoreCost = engine.relayRestoreCost(relayNodes)
    val canRestore = !relayAlreadyOnline && isNextRelay && engine.relayActivationReady(relayNodes)
    val isWorkshop = b.type == BuildingType.AUTO_SHOP
    val isPaintShop = b.type == BuildingType.PAINT_SHOP
    val isService = isWorkshop || isPaintShop
    val copy = LocalCopy.current

    GamePanel(
        title = if (b.landmark) copy.relayStation else copy.buildingName(b.type).uppercase(),
        subtitle = when {
            isPaintShop -> if (engine.distanceM < GameConfig.PAINT_SHOP_UNLOCK_M) {
                copy.paintAfter10(scrap)
            } else {
                copy.resprayScrap(scrap)
            }
            isWorkshop -> copy.upgradesScrap(scrap)
            b.loot.isEmpty() -> copy.nothingLeft()
            else -> copy.thingsToTake(b.loot.size)
        },
        onClose = onClose,
        modifier = modifier
    ) {
        val servicesOnly = isService && !b.landmark
        Column(
            if (servicesOnly) {
                Modifier.fillMaxSize()
            } else {
                Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            },
            verticalArrangement = Arrangement.spacedBy(if (isService) 12.dp else 0.dp)
        ) {
            if (engine.pursuitStormKind != null) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(GameColors.warn.copy(alpha = 0.14f), RoundedCornerShape(10.dp))
                        .border(1.dp, GameColors.warn.copy(alpha = 0.65f), RoundedCornerShape(10.dp))
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    Text(copy.stormShelterTitle, color = GameColors.warn, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text(copy.stormShelterBody, color = GameColors.textDim, fontSize = 12.sp)
                    GameButton(
                        copy.findShelter,
                        onShelter,
                        compact = true,
                        style = BtnStyle.Primary,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Spacer(Modifier.height(8.dp))
            }
            val missionOffer = engine.sideMissionOfferText
            val missionProgress = engine.sideMissionProgress
            if (missionOffer != null || missionProgress != null) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(GameColors.accent.copy(alpha = 0.14f), RoundedCornerShape(10.dp))
                        .border(1.dp, GameColors.accent.copy(alpha = 0.55f), RoundedCornerShape(10.dp))
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    Text(
                        engine.sideMissionTitle ?: copy.startMission,
                        color = GameColors.accent,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(missionProgress ?: missionOffer.orEmpty(), color = GameColors.textDim, fontSize = 12.sp)
                    if (missionOffer != null) {
                        GameButton(
                            copy.startMission,
                            onStartMission,
                            compact = true,
                            style = BtnStyle.Primary,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            if (isService) {
                val banner = engine.workshopServiceBanner()
                if (banner != null) {
                    WorkshopEngineBanner(banner)
                }
                val serviceMod = if (servicesOnly) {
                    Modifier.weight(1f).fillMaxWidth()
                } else {
                    Modifier.fillMaxWidth()
                }
                if (isPaintShop) {
                    PaintService(
                        engine = engine,
                        bagRevision = bagRevision,
                        partsRevision = partsRevision,
                        scrap = scrap,
                        adReady = adReady,
                        onPaint = onPaint,
                        onPaintFromAd = onPaintFromAd,
                        expand = servicesOnly,
                        modifier = serviceMod
                    )
                }
                if (isWorkshop) {
                    UpgradeService(
                        engine = engine,
                        bagRevision = bagRevision,
                        partsRevision = partsRevision,
                        scrap = scrap,
                        adReady = adReady,
                        onUpgrade = onUpgrade,
                        onUpgradeFromAd = onUpgradeFromAd,
                        expand = servicesOnly,
                        modifier = serviceMod
                    )
                }
            }
            if (!isService && b.loot.isNotEmpty()) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    GameButton(copy.takeAll, onTakeAll, compact = true, style = BtnStyle.Primary)
                    GameButton(copy.scrapAll, onScrapAll, compact = true, style = BtnStyle.Danger)
                }
                Spacer(Modifier.height(8.dp))
            }
            if (b.landmark) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(GameColors.accent.copy(alpha = 0.14f), RoundedCornerShape(10.dp))
                        .border(1.dp, GameColors.accent.copy(alpha = 0.55f), RoundedCornerShape(10.dp))
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    Text(
                        if (relayAlreadyOnline) copy.relayRestored else copy.relayCheckpoint,
                        color = if (relayAlreadyOnline) GameColors.ok else GameColors.accent,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        when {
                            relayAlreadyOnline -> copy.relaySignalOnline
                            !isNextRelay -> copy.restorePreviousBody
                            else -> copy.restoreRelayCost(restoreCost, engine.scrap, engine.relayModuleCount())
                        },
                        color = GameColors.textDim,
                        fontSize = 12.sp
                    )
                    if (!relayAlreadyOnline) {
                        GameButton(
                            if (isNextRelay) copy.restoreRelay else copy.restorePreviousFirst,
                            onActivateDepot,
                            compact = true,
                            style = BtnStyle.Primary,
                            enabled = canRestore,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            if (b.type == BuildingType.GAS_STATION) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF2A3A28), RoundedCornerShape(10.dp))
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    FuelPumpRow(
                        kind = FuelKind.PETROL,
                        litres = b.pumpFuelL,
                        recommended = engine.car.requiredFuelKind == FuelKind.PETROL,
                        onRefuel = onRefuel
                    )
                    FuelPumpRow(
                        kind = FuelKind.DIESEL,
                        litres = b.pumpDieselL,
                        recommended = engine.car.requiredFuelKind == FuelKind.DIESEL,
                        onRefuel = onRefuel
                    )
                }
                Spacer(Modifier.height(8.dp))
            }
            if (!isService && b.loot.isNotEmpty()) {
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    if (maxWidth >= 620.dp && b.loot.size > 1) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            repeat(2) { column ->
                                Column(Modifier.weight(1f)) {
                                    b.loot.forEachIndexed { i, item ->
                                        if (i % 2 != column) return@forEachIndexed
                                        LootItemCard(engine, item, i, bagRevision, onTake)
                                    }
                                }
                            }
                        }
                    } else {
                        Column(Modifier.fillMaxWidth()) {
                            b.loot.forEachIndexed { i, item ->
                                LootItemCard(engine, item, i, bagRevision, onTake)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WorkshopEngineBanner(text: String) {
    Text(
        text,
        color = GameColors.warn,
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .fillMaxWidth()
            .background(GameColors.warn.copy(alpha = 0.16f), RoundedCornerShape(10.dp))
            .border(1.dp, GameColors.warn.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp)
    )
}

@Composable
private fun ShopPurchaseReceipt(text: String) {
    Text(
        text,
        color = GameColors.ok,
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .fillMaxWidth()
            .background(GameColors.ok.copy(alpha = 0.16f), RoundedCornerShape(10.dp))
            .border(1.dp, GameColors.ok.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp)
    )
}

@Composable
private fun FuelPumpRow(
    kind: FuelKind,
    litres: Float,
    recommended: Boolean,
    onRefuel: (FuelKind) -> Unit
) {
    val copy = LocalCopy.current
    val kindName = copy.fuelKindName(kind)
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                if (recommended) copy.yourEngine(kindName) else kindName.uppercase(),
                color = if (recommended) GameColors.ok else GameColors.text,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                if (litres > 0.05f) copy.remainingLitres(String.format("%.0f", litres)) else copy.emptyLower,
                color = if (litres > 0.05f) GameColors.textDim else GameColors.danger,
                fontSize = 11.sp
            )
        }
        GameButton(
            copy.fillKind(kindName),
            { onRefuel(kind) },
            style = if (recommended) BtnStyle.Primary else BtnStyle.Ghost,
            compact = true,
            enabled = litres > 0.05f
        )
    }
}

@Composable
private fun LootItemCard(
    engine: GameEngine,
    item: ItemStack,
    index: Int,
    revision: Int,
    onTake: (Int) -> Unit
) {
    val copy = LocalCopy.current
    ItemCard(
        stack = item,
        mounted = item.def.mountsTo?.let { engine.car.parts[it] },
        primaryLabel = copy.take,
        onPrimary = { onTake(index) },
        revision = revision,
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
    )
}

// ---------------------------------------------------------------------------
// Panel auta
// ---------------------------------------------------------------------------

@Composable
private fun CarPanel(
    engine: GameEngine,
    bagRevision: Int,
    partsRevision: Int,
    scrap: Int,
    frontTireHealth: Float,
    rearTireHealth: Float,
    frontTireInjury: TireInjury,
    rearTireInjury: TireInjury,
    debugRepair: Boolean,
    onRepair: (ComponentSlot) -> Unit,
    onScrapRepair: (ComponentSlot) -> Unit,
    onPatchPuncture: (ComponentSlot) -> Unit,
    onDrain: (FluidType, Float?) -> Unit,
    onUnmount: (ComponentSlot) -> Unit,
    onCancelUnmount: () -> Unit,
    onSwapTyres: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    @Suppress("UNUSED_VARIABLE")
    val rev = bagRevision + 31 * partsRevision + scrap
    var section by rememberSaveable { mutableStateOf(CarSection.PREDOK) }
    var selected by remember { mutableStateOf<ComponentSlot?>(null) }
    var pendingDrain by remember { mutableStateOf<FluidType?>(null) }
    val car = engine.car
    val overallNow = car.overallHealth
    val copy = LocalCopy.current

    GamePanel(
        title = copy.car,
        onClose = onClose,
        modifier = modifier,
        header = {
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ScrapCurrencyIcon(Modifier.size(20.dp))
                    CarHeaderStat(copy.scrap, "$scrap", GameColors.accent)
                }
                CarHeaderStat(
                    copy.condition,
                    "${(overallNow * 100).toInt()} %",
                    healthColor(overallNow)
                )
                CarHeaderStat(
                    copy.fuelKindName(car.requiredFuelKind).uppercase(),
                    "${car.fuel.toInt()} L",
                    levelColor((car.fuel / car.fuelCapacity).coerceIn(0f, 1f))
                )
                CarHeaderStat(
                    copy.oil,
                    String.format("%.1f L", car.oil),
                    levelColor((car.oil / car.oilCapacity).coerceIn(0f, 1f))
                )
                CarHeaderStat(
                    copy.coolant,
                    String.format("%.1f L", car.coolant),
                    levelColor((car.coolant / car.coolantCapacity).coerceIn(0f, 1f))
                )
            }
        }
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
            val compact = maxWidth < 800.dp
            val preview = @Composable { mod: Modifier ->
                CarView(
                    engine = engine,
                    revision = partsRevision,
                    section = section,
                    onSection = {
                        section = it
                        selected = null
                        pendingDrain = null
                        onCancelUnmount()
                    },
                    modifier = mod
                )
            }
            val slots = @Composable { mod: Modifier ->
                Column(mod) {
                    SectionLabel(
                        when (section) {
                            CarSection.ZADOK -> copy.rear
                            CarSection.STRED -> copy.middle
                            CarSection.PREDOK -> copy.front
                        },
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                    SlotGrid(
                        engine = engine,
                        revision = partsRevision,
                        section = section,
                        selected = selected,
                        onSelect = {
                            if (it != selected) onCancelUnmount()
                            selected = it
                            pendingDrain = null
                        },
                        modifier = Modifier.weight(1f).fillMaxWidth()
                    )
                }
            }
            if (compact) {
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    preview(Modifier.fillMaxWidth().weight(0.44f))
                    slots(Modifier.fillMaxWidth().weight(0.56f))
                }
            } else {
                Row(
                    Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    preview(Modifier.weight(0.95f).fillMaxHeight())
                    slots(Modifier.weight(1.15f).fillMaxHeight())
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        CarWorkshopBar(
            engine = engine,
            car = car,
            partsRevision = partsRevision,
            selected = selected,
            pendingDrain = pendingDrain,
            debugRepair = debugRepair,
            onPendingDrain = { pendingDrain = it },
            onRepair = onRepair,
            onScrapRepair = onScrapRepair,
            onPatchPuncture = onPatchPuncture,
            onDrain = onDrain,
            onUnmount = onUnmount,
            onSwapTyres = onSwapTyres
        )
    }
}

@Composable
private fun LootAheadIndicator(distanceM: Int, modifier: Modifier = Modifier) {
    val copy = LocalCopy.current
    Row(
        modifier
            .background(GameColors.hudBg.copy(alpha = 0.86f), RoundedCornerShape(8.dp))
            .border(1.dp, GameColors.accent.copy(alpha = 0.8f), RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text("➜", color = GameColors.accent, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text(copy.lootableWreck(distanceM), color = GameColors.text, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun PaintService(
    engine: GameEngine,
    bagRevision: Int,
    partsRevision: Int,
    scrap: Int,
    adReady: Boolean,
    onPaint: (VehiclePaint) -> Unit,
    onPaintFromAd: (VehiclePaint) -> Unit,
    expand: Boolean = false,
    modifier: Modifier = Modifier
) {
    @Suppress("UNUSED_VARIABLE")
    val rev = bagRevision + 31 * partsRevision + scrap
    val current = VehiclePaint.at(engine.car.bodyPaintIndex)
    val paints = VehiclePaint.entries
    var picked by remember {
        mutableStateOf(paints.firstOrNull { it != current } ?: current)
    }
    var lastPurchase by remember { mutableStateOf<String?>(null) }
    var seenPaint by remember { mutableStateOf(current) }
    val copy = LocalCopy.current
    SideEffect {
        if (current != seenPaint) {
            seenPaint = current
            val prefix = copy.repaintedPrefix(copy.paintName(current))
            if (lastPurchase?.startsWith(prefix) != true) {
                lastPurchase = prefix
            }
        }
    }
    val unlocked = engine.paintShopAvailable
    val cost = engine.paintShopCost
    val canAfford = scrap >= cost
    val alreadyThis = picked == current
    val workshopReady = engine.scrapWorkshopBlockReason() == null
    val canRespray = unlocked && workshopReady && !alreadyThis
    Column(
        modifier
            .fillMaxWidth()
            .background(GameColors.panelSoft, RoundedCornerShape(12.dp))
            .border(1.dp, GameColors.accent.copy(alpha = 0.55f), RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                copy.paintRespray,
                color = GameColors.accent,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.weight(1f))
            ScrapCurrencyIcon(Modifier.size(18.dp))
            Text(
                "$scrap",
                color = GameColors.accent,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
            PaintSwatch(current)
            Text(
            copy.nowPaint(copy.paintName(current)),
                color = Color(current.argb),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        lastPurchase?.let { ShopPurchaseReceipt(it) }
        Text(
            when {
                !unlocked -> copy.paintAfter10km
                alreadyThis -> copy.alreadyOnCarName(copy.paintName(picked))
                canAfford -> copy.resprayWhole(cost, scrap)
                else -> copy.needScrapWatchAdPaint(cost, scrap)
            },
            color = if (!unlocked) GameColors.warn else GameColors.textDim,
            fontSize = 13.sp
        )
        paints.chunked(5).forEach { row ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                row.forEach { paint ->
                    val onCar = paint == current
                    val chosen = paint == picked
                    Box(
                        Modifier
                            .weight(1f)
                            .height(WORKSHOP_SWATCH_HEIGHT)
                            .background(Color(paint.argb), RoundedCornerShape(8.dp))
                            .border(
                                width = if (chosen) 3.dp else 1.dp,
                                color = when {
                                    chosen -> Color.White
                                    onCar -> Color.White.copy(alpha = 0.45f)
                                    else -> Color.Black.copy(alpha = 0.35f)
                                },
                                RoundedCornerShape(8.dp)
                            )
                            .clickable(enabled = unlocked) { picked = paint },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            copy.paintName(paint).take(1),
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
        if (expand) Spacer(Modifier.weight(1f))
        if (unlocked) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                GameButton(
                    if (alreadyThis) copy.alreadyOnTheCar else copy.resprayCost(cost),
                    {
                        val colorName = copy.paintName(picked)
                        val paid = cost
                        onPaint(picked)
                        if (engine.car.bodyPaintIndex == picked.ordinal) {
                            lastPurchase = copy.repaintedCost(colorName, paid)
                        }
                    },
                    compact = false,
                    style = BtnStyle.Primary,
                    enabled = canRespray && canAfford,
                    modifier = Modifier.weight(1f).height(WORKSHOP_ACTION_HEIGHT)
                )
                if (engine.paintAdAvailable()) {
                    GameButton(
                        if (adReady) copy.watchAd else copy.adLoading,
                        { onPaintFromAd(picked) },
                        compact = false,
                        style = BtnStyle.Secondary,
                        enabled = canRespray,
                        modifier = Modifier.weight(1f).height(WORKSHOP_ACTION_HEIGHT)
                    )
                }
            }
        }
    }
}

@Composable
private fun UpgradeService(
    engine: GameEngine,
    bagRevision: Int,
    partsRevision: Int,
    scrap: Int,
    adReady: Boolean,
    onUpgrade: (ComponentSlot) -> Unit,
    onUpgradeFromAd: (ComponentSlot) -> Unit,
    expand: Boolean = false,
    modifier: Modifier = Modifier
) {
    @Suppress("UNUSED_VARIABLE")
    val rev = bagRevision + 31 * partsRevision + scrap
    val rows = WORKSHOP_CATALOG_SLOTS.mapNotNull { slot ->
        engine.car.parts[slot]?.let { slot to it }
    }
    val workshopBlock = engine.scrapWorkshopBlockReason()
    val adSlot = engine.workshopAdUpgradeSlot()
    var lastPurchase by remember { mutableStateOf<String?>(null) }
    val copy = LocalCopy.current
    SideEffect {
        val msg = engine.message
        if (msg.contains("ad reward") && msg.startsWith("Upgraded")) {
            lastPurchase = msg
        }
    }
    Column(
        modifier
            .fillMaxWidth()
            .background(GameColors.panelSoft, RoundedCornerShape(12.dp))
            .border(1.dp, GameColors.accent.copy(alpha = 0.55f), RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                copy.upgrades,
                color = GameColors.accent,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.weight(1f))
            ScrapCurrencyIcon(Modifier.size(18.dp))
            Text(
                "$scrap",
                color = GameColors.accent,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
        }
        lastPurchase?.let { ShopPurchaseReceipt(it) }
        if (rows.isEmpty()) {
            Text(
                copy.noCatalogUpgrades,
                color = GameColors.textDim,
                fontSize = 13.sp
            )
            if (expand) Spacer(Modifier.weight(1f))
        } else {
            Column(
                Modifier
                    .fillMaxWidth()
                    .then(if (expand) Modifier.weight(1f) else Modifier)
                    .then(if (expand) Modifier.verticalScroll(rememberScrollState()) else Modifier),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                rows.forEach { (slot, current) ->
                    val target = engine.scrapUpgradeTarget(slot)
                    val cost = engine.scrapUpgradeCost(slot)
                    val slotBlock = engine.scrapUpgradeBlockReason(slot)
                    val canAfford = cost != null && scrap >= cost
                    val enabled = target != null &&
                        cost != null &&
                        workshopBlock == null &&
                        slotBlock == null &&
                        canAfford
                    val adOffer = slot == adSlot && target != null
                    Column(
                        Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    copy.slotName(slot),
                                    color = GameColors.accent,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    if (target == null) {
                                        copy.noFurtherUpgrade(copy.itemName(current.def))
                                    } else {
                                        copy.upgradeArrow(copy.itemName(current.def), copy.itemName(target))
                                    },
                                    color = GameColors.text,
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    when {
                                        target == null || cost == null -> copy.soldOut
                                        slotBlock != null -> slotBlock
                                        canAfford -> copy.scrapYouHave(cost, scrap)
                                        else -> copy.needScrap(cost, scrap)
                                    },
                                    color = if (target == null || cost == null) {
                                        GameColors.textDim
                                    } else if (slotBlock != null || !canAfford) {
                                        GameColors.warn
                                    } else {
                                        GameColors.textDim
                                    },
                                    fontSize = 12.sp,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            if (target == null || cost == null) {
                                GameButton(
                                    copy.soldOut,
                                    {},
                                    compact = false,
                                    style = BtnStyle.Ghost,
                                    enabled = false,
                                    modifier = Modifier.height(WORKSHOP_ACTION_HEIGHT)
                                )
                            } else {
                                val paid = cost
                                val fromName = copy.itemName(current.def)
                                val toName = copy.itemName(target)
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    GameButton(
                                        copy.upgradeMinus(paid),
                                        {
                                            onUpgrade(slot)
                                            if (engine.car.parts[slot]?.defId == target.id) {
                                                lastPurchase =
                                                    copy.upgradedFromTo(copy.slotName(slot), fromName, toName, paid)
                                            }
                                        },
                                        compact = false,
                                        style = BtnStyle.Primary,
                                        enabled = enabled,
                                        modifier = Modifier.height(WORKSHOP_ACTION_HEIGHT)
                                    )
                                    if (adOffer) {
                                        GameButton(
                                            if (adReady) copy.watchAd else copy.adLoading,
                                            { onUpgradeFromAd(slot) },
                                            compact = false,
                                            style = BtnStyle.Secondary,
                                            enabled = workshopBlock == null && slotBlock == null,
                                            modifier = Modifier.height(WORKSHOP_ACTION_HEIGHT)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CarHeaderStat(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.End) {
        Text(
            label,
            color = GameColors.textDim,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.9.sp
        )
        Text(value, color = color, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
private fun CarWorkshopBar(
    engine: GameEngine,
    car: Car,
    partsRevision: Int,
    selected: ComponentSlot?,
    pendingDrain: FluidType?,
    debugRepair: Boolean,
    onPendingDrain: (FluidType?) -> Unit,
    onRepair: (ComponentSlot) -> Unit,
    onScrapRepair: (ComponentSlot) -> Unit,
    onPatchPuncture: (ComponentSlot) -> Unit,
    onDrain: (FluidType, Float?) -> Unit,
    onUnmount: (ComponentSlot) -> Unit,
    onSwapTyres: () -> Unit
) {
    @Suppress("UNUSED_VARIABLE")
    val rev = partsRevision
    val selectedPart = selected?.let { engine.car.parts[it] }
    val liveHealth = selectedPart?.health ?: -1f
    val liveInjury = selectedPart?.injury ?: TireInjury.INFLATED
    val copy = LocalCopy.current
    Column(
        Modifier
            .fillMaxWidth()
            .background(GameColors.panelSoft, RoundedCornerShape(10.dp))
            .border(1.dp, GameColors.outline, RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        val drain = pendingDrain
        if (drain == null) {
            Text(
                copy.service,
                color = GameColors.accent,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
            val workshopBlock = engine.scrapWorkshopBlockReason()
            if (workshopBlock != null) {
                Text(
                    workshopBlock,
                    color = GameColors.warn,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
        if (drain != null) {
            val amount = car.fluidLevel(drain)
            val step = drainStep(drain).coerceAtMost(amount)
            val ready = !car.engineRunning && engine.phase != GamePhase.DRIVING
            val drainNote = when {
                car.engineRunning -> copy.switchEngineOffFirst
                engine.phase == GamePhase.DRIVING -> copy.parkTheCarFirst
                else -> copy.discardedIn(String.format("%.1f", amount), drainLocation(drain, copy))
            }
            Text(
                copy.drainFluid(copy.fluidName(drain)),
                color = GameColors.danger,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
            Text(
                drainNote,
                color = if (ready) GameColors.warn else GameColors.danger,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val drainMod = Modifier.weight(1f).height(CAR_ACTION_HEIGHT)
                GameButton(copy.cancel, { onPendingDrain(null) }, compact = true, modifier = drainMod)
                if (amount > step + 0.05f) {
                    GameButton(
                        copy.drainLitres(String.format("%.1f", step)),
                        {
                            onDrain(drain, step)
                            onPendingDrain(null)
                        },
                        compact = true,
                        enabled = ready,
                        style = BtnStyle.Danger,
                        modifier = drainMod
                    )
                }
                GameButton(
                    copy.drainAllLitres(String.format("%.1f", amount)),
                    {
                        onDrain(drain, null)
                        onPendingDrain(null)
                    },
                    compact = true,
                    enabled = ready,
                    style = BtnStyle.Danger,
                    modifier = drainMod
                )
            }
        } else {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                when {
                    selected == null -> Text(
                        copy.pickAPartInZone,
                        color = GameColors.textDim,
                        fontSize = 12.sp,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    selectedPart == null -> {
                        Text(
                            copy.slotName(selected!!),
                            color = GameColors.accent,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1
                        )
                        Text(
                            copy.emptyFindAndFit,
                            color = GameColors.danger,
                            fontSize = 12.sp,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    else -> {
                        val part = selectedPart
                        val status = partStatusLabel(part, copy)
                        Text(
                            copy.slotName(selected!!),
                            color = GameColors.accent,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1
                        )
                        if (part.def.hasDurability) {
                            Text(
                                "${(liveHealth * 100).toInt()} %",
                                color = status.second,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                        }
                        Chip(status.first, status.second)
                        if (part.paintIndex >= 0 && part.def.mountsTo?.takesBodyPaint == true) {
                            val paint = VehiclePaint.at(part.paintIndex)
                            PaintSwatch(paint)
                            Text(
                                copy.paintName(paint),
                                color = Color(paint.argb),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        val hint = partBarHint(selected!!, part, car, engine, copy)
                        if (hint != null) {
                            Text(
                                hint,
                                color = if (part.injury != TireInjury.INFLATED) GameColors.danger else GameColors.textDim,
                                fontSize = 11.sp,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        } else {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }

            val tyreSelected = selected == ComponentSlot.TIRE_FRONT ||
                selected == ComponentSlot.TIRE_REAR
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val actionMod = Modifier.weight(1f).height(CAR_ACTION_HEIGHT)
                if (selectedPart != null) {
                    val slot = selected!!
                    val repairCost = engine.scrapRepairCost(slot)
                    val upgradeTarget = engine.scrapUpgradeTarget(slot)
                    val workshopBlock = engine.scrapWorkshopBlockReason()
                    val punctured = engine.canPatchPuncture(slot)
                    val shredded = liveInjury == TireInjury.SHREDDED
                    val workshopReady = workshopBlock == null
                    val kits = engine.punctureKitCount()
                    val parked = engine.phase != GamePhase.DRIVING &&
                        kotlin.math.abs(engine.car.speed) <= 0.2f
                    val patchEnabled = parked && (kits > 0 || workshopReady)
                    val drainAction = when (slot) {
                        ComponentSlot.ENGINE -> FluidType.OIL to copy.drainOil
                        ComponentSlot.FUEL_TANK -> FluidType.FUEL to copy.drainFuel
                        ComponentSlot.RADIATOR -> FluidType.COOLANT to copy.drainCoolant
                        else -> null
                    }
                    val patchLabel = if (kits > 0) {
                        copy.patchKit
                    } else {
                        "PATCH −${engine.scrapPatchCost()}"
                    }
                    val primary = when {
                        shredded && tyreSelected -> CarCta.Swap
                        punctured -> CarCta.Patch
                        repairCost != null -> CarCta.Repair
                        else -> null
                    }
                    when (primary) {
                        CarCta.Swap -> GameButton(
                            copy.swap,
                            onSwapTyres,
                            compact = true,
                            style = BtnStyle.Primary,
                            modifier = actionMod
                        )
                        CarCta.Patch -> GameButton(
                            patchLabel,
                            { onPatchPuncture(slot) },
                            compact = true,
                            enabled = patchEnabled,
                            style = BtnStyle.Primary,
                            modifier = actionMod
                        )
                        CarCta.Repair -> GameButton(
                            "REPAIR −$repairCost",
                            { onScrapRepair(slot) },
                            compact = true,
                            enabled = workshopReady,
                            style = BtnStyle.Primary,
                            modifier = actionMod
                        )
                        null -> Unit
                    }
                    if (tyreSelected && primary != CarCta.Swap) {
                        GameButton(
                            copy.swap,
                            onSwapTyres,
                            compact = true,
                            style = BtnStyle.Ghost,
                            modifier = actionMod
                        )
                    }
                    if (punctured && !shredded && primary != CarCta.Patch) {
                        GameButton(
                            patchLabel,
                            { onPatchPuncture(slot) },
                            compact = true,
                            enabled = patchEnabled,
                            style = BtnStyle.Ghost,
                            modifier = actionMod
                        )
                    }
                    if (repairCost != null && !shredded && primary != CarCta.Repair) {
                        GameButton(
                            "REPAIR −$repairCost",
                            { onScrapRepair(slot) },
                            compact = true,
                            enabled = workshopReady,
                            style = BtnStyle.Ghost,
                            modifier = actionMod
                        )
                    }
                    if (upgradeTarget != null) {
                        GameButton(
                            copy.atWorkshop,
                            {},
                            compact = true,
                            enabled = false,
                            style = BtnStyle.Ghost,
                            modifier = actionMod
                        )
                    }
                    if (drainAction != null && car.fluidLevel(drainAction.first) > 0.05f) {
                        GameButton(
                            drainAction.second,
                            { onPendingDrain(drainAction.first) },
                            compact = true,
                            style = BtnStyle.Danger,
                            modifier = actionMod
                        )
                    }
                    val confirmingRemove = engine.pendingUnmountSlot == slot
                    if (engine.canUnmount(slot)) {
                        GameButton(
                            if (confirmingRemove) copy.tapAgain else copy.remove,
                            { onUnmount(slot) },
                            compact = true,
                            style = if (confirmingRemove) BtnStyle.Danger else BtnStyle.Ghost,
                            modifier = actionMod
                        )
                    }
                    if (debugRepair) {
                        GameButton(
                            copy.debug,
                            { onRepair(slot) },
                            compact = true,
                            style = BtnStyle.Ghost,
                            modifier = actionMod
                        )
                    }
                } else {
                    GameButton(
                        copy.swapTyres,
                        onSwapTyres,
                        compact = true,
                        style = BtnStyle.Primary,
                        modifier = actionMod
                    )
                }
            }
        }
    }
}

private fun drainStep(fluid: FluidType): Float = when (fluid) {
    FluidType.FUEL -> 5f
    FluidType.OIL, FluidType.COOLANT -> 1f
    FluidType.BRAKE_FLUID -> 0.5f
}

private fun drainLocation(fluid: FluidType, copy: Copy): String = when (fluid) {
    FluidType.FUEL -> copy.drainTheFuelTank
    FluidType.OIL -> copy.drainTheEngine
    FluidType.COOLANT -> copy.drainTheRadiator
    FluidType.BRAKE_FLUID -> copy.drainTheBrakes
}

private enum class CarCta { Swap, Patch, Repair }

private fun partStatusLabel(part: MountedPart, copy: Copy): Pair<String, Color> = when {
    !part.def.hasDurability -> copy.permanent to GameColors.ok
    part.injury == TireInjury.SHREDDED -> copy.onRim to GameColors.danger
    part.injury == TireInjury.PUNCTURED -> copy.flat to GameColors.danger
    else -> copy.conditionName(part.condition) to healthColor(part.health)
}

private data class TireStatLine(
    val gripNow: Int,
    val gripMax: Int,
    val snow: Int,
    val climbDeg: Int,
    val size: String
)

private fun tireStatLine(slot: ComponentSlot, def: ItemDef, car: Car): TireStatLine? {
    if (slot != ComponentSlot.TIRE_FRONT && slot != ComponentSlot.TIRE_REAR) return null
    val nominal = if (def.grip > 0f) def.grip else def.reliability
    val tread = car.parts[slot]?.let { car.treadFactor(it.health) } ?: 1f
    val climb = Math.toDegrees(kotlin.math.atan(car.maxClimbSlope(0.1f, 8f)).toDouble())
    return TireStatLine(
        gripNow = (nominal * tread * 100).toInt(),
        gripMax = (nominal * 100).toInt(),
        snow = (def.snowGrip * 100).toInt(),
        climbDeg = climb.toInt(),
        size = String.format("%.2f", def.wheelScale)
    )
}

/** Textured 3D inspection of the installed vehicle, with selectable component groups. */
@Composable
private fun CarView(engine:GameEngine,revision:Int,section:CarSection,onSection:(CarSection)->Unit,modifier:Modifier=Modifier) {
    val copy=LocalCopy.current
    Column(modifier.background(Color(0xFF2E3B44),RoundedCornerShape(12.dp))
        .border(1.dp,GameColors.outline,RoundedCornerShape(12.dp)).padding(8.dp)) {
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
            CarSection.entries.forEach { zone ->
                CarZoneTab(label=when(zone) {
                    CarSection.ZADOK -> copy.rear
                    CarSection.STRED -> copy.middle
                    CarSection.PREDOK -> copy.front
                },active=section==zone,onClick={onSection(zone)},modifier=Modifier.weight(1f))
            }
        }
        androidx.compose.runtime.key(engine) {
        androidx.compose.ui.viewinterop.AndroidView(
            factory={sk.kubis.endlessdrive.ui.game.expedition.ExpeditionCarPreview(it,engine)},
            update={ revision; it.requestRender() },
            onRelease={it.onPause()},
            modifier=Modifier.weight(1f).fillMaxWidth().padding(vertical=6.dp)
        )
        }
        Text(zoneSummary(section,engine.car,copy),color=GameColors.text,fontSize=11.sp,
            fontWeight=FontWeight.SemiBold,maxLines=1,overflow=TextOverflow.Ellipsis)
    }
}

/** Mriežka slotov – pri zvolenej zóne len jej diely, nikdy pod detailom. */
@Composable
private fun SlotGrid(
    engine: GameEngine,
    revision: Int,
    section: CarSection,
    selected: ComponentSlot?,
    onSelect: (ComponentSlot) -> Unit,
    modifier: Modifier = Modifier
) {
    @Suppress("UNUSED_VARIABLE")
    val rev = revision
    val slots = section.slots
    val columns = if (slots.size <= 4) 1 else 2
    val rows = (slots.size + columns - 1) / columns
    Column(
        modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        if (section == CarSection.STRED) {
            BodyConditionChip(condition = engine.car.bodyCondition)
        }
        for (row in 0 until rows) {
            Row(
                Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                for (col in 0 until columns) {
                    val index = row * columns + col
                    if (index >= slots.size) {
                        Spacer(Modifier.weight(1f))
                        continue
                    }
                    val slot = slots[index]
                    val part = engine.car.parts[slot]
                    SlotChip(
                        slot = slot,
                        part = part,
                        health = part?.health ?: -1f,
                        injury = part?.injury ?: TireInjury.INFLATED,
                        partId = part?.defId.orEmpty(),
                        selected = selected == slot,
                        onClick = { onSelect(slot) },
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    )
                }
            }
        }
    }
}

@Composable
private fun BodyConditionChip(condition: Float, modifier: Modifier = Modifier) {
    val copy = LocalCopy.current
    val pct = (condition.coerceIn(0f, 1f) * 100f).toInt()
    val bar = when {
        pct >= 70 -> GameColors.ok
        pct >= 40 -> GameColors.warn
        else -> GameColors.danger
    }
    Column(
        modifier
            .fillMaxWidth()
            .background(GameColors.panelHigh, RoundedCornerShape(8.dp))
            .border(1.dp, GameColors.outline, RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(copy.bodyShell, color = GameColors.text, fontSize = 12.sp)
            Text("$pct %", color = bar, fontSize = 12.sp)
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(4.dp)
                .background(GameColors.panelSoft, RoundedCornerShape(2.dp))
        ) {
            Box(
                Modifier
                    .fillMaxWidth(condition.coerceIn(0f, 1f))
                    .fillMaxHeight()
                    .background(bar, RoundedCornerShape(2.dp))
            )
        }
        Text(
            copy.bodyShellHint,
            color = GameColors.textDim,
            fontSize = 10.sp
        )
    }
}

@Composable
private fun SlotChip(
    slot: ComponentSlot,
    part: MountedPart?,
    health: Float,
    injury: TireInjury,
    partId: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    @Suppress("UNUSED_VARIABLE")
    val seen = partId
    val copy = LocalCopy.current
    val missing = health < 0f
    val permanent = part != null && !part.def.hasDurability
    val paint = part?.takeIf {
        it.paintIndex >= 0 && it.def.mountsTo?.takesBodyPaint == true
    }?.let { VehiclePaint.at(it.paintIndex) }
    Column(
        modifier
            .background(
                when {
                    selected -> GameColors.accent.copy(alpha = 0.28f)
                    missing -> GameColors.panelSoft
                    else -> GameColors.panelHigh
                },
                RoundedCornerShape(8.dp)
            )
            .border(
                1.dp,
                when {
                    selected -> GameColors.accent
                    missing -> GameColors.outline.copy(alpha = 0.55f)
                    else -> GameColors.outline
                },
                RoundedCornerShape(8.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                copy.slotName(slot),
                color = if (missing) GameColors.textDim else GameColors.text,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(end = 6.dp)
            )
            if (paint != null) {
                PaintSwatch(paint)
            }
        }
        Text(
            when {
                missing -> copy.emptyLower
                permanent -> copy.permanent
                injury == TireInjury.PUNCTURED -> copy.pctFlat((health * 100).toInt())
                injury == TireInjury.SHREDDED -> copy.pctRim((health * 100).toInt())
                else -> "${(health * 100).toInt()} %"
            },
            color = when {
                missing -> GameColors.textDim
                permanent -> GameColors.ok
                injury != TireInjury.INFLATED -> GameColors.danger
                else -> healthColor(health)
            },
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
        if (!missing && !permanent) {
            Spacer(Modifier.height(4.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(Color(0xFF2A241D), RoundedCornerShape(2.dp))
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(health.coerceIn(0f, 1f))
                        .fillMaxHeight()
                        .background(healthColor(health), RoundedCornerShape(2.dp))
                )
            }
        }
    }
}

@Composable
private fun CarZoneTab(
    label: String,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier
            .background(
                if (active) GameColors.accent else GameColors.panelSoft,
                RoundedCornerShape(8.dp)
            )
            .border(
                1.dp,
                if (active) GameColors.accent else GameColors.outline,
                RoundedCornerShape(8.dp)
            )
            .clickable(onClick = onClick)
            .padding(vertical = 5.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = if (active) Color(0xFF1B1712) else GameColors.text,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}

@Composable
private fun CarZoneHit(
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier
            .padding(2.dp)
            .background(
                if (active) GameColors.accent.copy(alpha = 0.20f) else Color.Transparent,
                RoundedCornerShape(8.dp)
            )
            .then(
                if (active) Modifier.border(1.5.dp, GameColors.accent.copy(alpha = 0.85f), RoundedCornerShape(8.dp))
                else Modifier
            )
            .clickable(onClick = onClick)
    )
}

@Composable
private fun PaintSwatch(paint: VehiclePaint, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(10.dp)
            .background(Color(paint.argb), RoundedCornerShape(50))
            .border(1.dp, Color.White.copy(alpha = 0.4f), RoundedCornerShape(50))
    )
}

private fun zoneSummary(section: CarSection, car: Car, copy: Copy): String = when (section) {
    CarSection.PREDOK ->
        "${copy.fittedHudLabel(car, ComponentSlot.ENGINE)} · F ${copy.fittedHudLabel(car, ComponentSlot.TIRE_FRONT)}"
    CarSection.STRED ->
        "${copy.fittedHudLabel(car, ComponentSlot.SUSPENSION)} · ${copy.fittedHudLabel(car, ComponentSlot.DRIVETRAIN)}"
    CarSection.ZADOK ->
        "R ${copy.fittedHudLabel(car, ComponentSlot.TIRE_REAR)}"
}

private fun partBarHint(
    slot: ComponentSlot,
    part: MountedPart,
    car: Car,
    engine: GameEngine,
    copy: Copy
): String? {
    val injury = when (part.injury) {
        TireInjury.SHREDDED -> copy.fitNewTyre
        TireInjury.PUNCTURED -> if (engine.punctureKitCount() > 0) {
            copy.patchKitInBag
        } else {
            copy.patchThePuncture
        }
        else -> null
    }
    val tire = tireStatLine(slot, part.def, car)?.let {
        copy.gripLine(it.gripNow.toString(), it.gripMax.toString(), it.snow.toString())
    }
    val extra = partExtraHint(slot, part.def, car, copy)
    val next = engine.scrapUpgradeTarget(slot)?.let { copy.upgradeAtWorkshop }
    val workshop = engine.canPatchPuncture(slot) ||
        engine.scrapRepairCost(slot) != null
    val block = if (workshop) engine.scrapWorkshopBlockReason() else null
    return listOfNotNull(injury, tire, extra, next, block)
        .joinToString(" · ")
        .ifBlank { null }
}

private fun partExtraHint(slot: ComponentSlot, def: ItemDef, car: Car, copy: Copy): String? = when (slot) {
    ComponentSlot.SUSPENSION ->
        copy.rideHeight(
            String.format("%.0f", car.rideHeight * 100),
            String.format("%.0f", car.suspTravel * 100)
        )
    ComponentSlot.DRIVETRAIN ->
        copy.layoutLine(copy.driveLayoutName(car.driveLayout))
    ComponentSlot.ALTERNATOR ->
        copy.chargingLine(
            (car.alternatorOutput * 100).toInt(),
            (car.batteryHoldCapacity * 100).toInt()
        )
    ComponentSlot.BATTERY -> {
        val healthPct = ((car.parts[ComponentSlot.BATTERY]?.health ?: 0f) * 100f).toInt()
        val socPct = (car.batteryCharge * 100f).toInt()
        copy.electricalLine(socPct, healthPct)
    }
    ComponentSlot.CARGO, ComponentSlot.ROOF_RACK ->
        copy.cargoBonus(def.extraSlots, def.extraWeight.toInt(), def.dragAdd > 0f)
    ComponentSlot.CHAINS ->
        copy.chainsHint(
            String.format("%.2f", GameConfig.CHAINS_SNOW_BONUS),
            String.format("%.2f", GameConfig.CHAINS_TARMAC_PENALTY),
            (GameConfig.CHAINS_MAX_SPEED * 3.6f).toInt()
        )
    ComponentSlot.TIRE_FRONT, ComponentSlot.TIRE_REAR -> null
    else -> null
}

internal enum class CarSection(val label: String, val slots: List<ComponentSlot>) {
    ZADOK(
        "Rear",
        listOf(
            ComponentSlot.REAR_BUMPER,
            ComponentSlot.DOOR_REAR,
            ComponentSlot.FUEL_TANK,
            ComponentSlot.TIRE_REAR,
            ComponentSlot.CHAINS,
            ComponentSlot.CARGO,
            ComponentSlot.TRUNK_LID,
            ComponentSlot.TAILLIGHT,
            ComponentSlot.SEAT_REAR
        )
    ),
    STRED(
        "Middle",
        listOf(
            ComponentSlot.DOOR_FRONT,
            ComponentSlot.SUSPENSION,
            ComponentSlot.SEAT_FRONT,
            ComponentSlot.DRIVETRAIN,
            ComponentSlot.ROOF_RACK
        )
    ),
    PREDOK(
        "Front",
        listOf(
            ComponentSlot.FRONT_BUMPER,
            ComponentSlot.HOOD,
            ComponentSlot.TIRE_FRONT,
            ComponentSlot.ENGINE,
            ComponentSlot.ALTERNATOR,
            ComponentSlot.STARTER,
            ComponentSlot.RADIATOR,
            ComponentSlot.BATTERY,
            ComponentSlot.BRAKES,
            ComponentSlot.HEADLIGHT
        )
    )
}

// ---------------------------------------------------------------------------
// Pauza a koniec jazdy
// ---------------------------------------------------------------------------

@Composable
private fun SafeHavenPanel(
    onFinish: () -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier
) {
    val copy = LocalCopy.current
    GamePanel(
        title = copy.safeHavenTitle,
        subtitle = copy.safeHavenSubtitle,
        modifier = modifier,
        fillHeight = false
    ) {
        Text(
            copy.safeHavenBody,
            color = GameColors.text,
            fontSize = 14.sp,
            lineHeight = 20.sp
        )
        Spacer(Modifier.height(16.dp))
        GameButton(
            copy.continueEndless,
            onContinue,
            style = BtnStyle.Primary,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        GameButton(
            copy.finishExpedition,
            onFinish,
            style = BtnStyle.Secondary,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun SideMissionCompletePanel(
    completion: SideMissionCompletion,
    adReady: Boolean,
    onCollect: () -> Unit,
    onDoubleReward: () -> Unit,
    modifier: Modifier = Modifier
) {
    val copy = LocalCopy.current
    GamePanel(
        title = copy.missionCompleteTitle,
        subtitle = copy.sideMissionTitle(completion.kind),
        modifier = modifier,
        fillHeight = false
    ) {
        Text(
            copy.missionRewardBody(completion.rewardScrap, completion.bonusScrap),
            color = GameColors.text,
            fontSize = 14.sp,
            lineHeight = 20.sp
        )
        if (completion.kind == SideMissionKind.FRAGILE_DELIVERY) {
            Spacer(Modifier.height(8.dp))
            StatBar(
                label = copy.sideMissionTitle(completion.kind),
                ratio = completion.cargoIntegrity,
                value = "${(completion.cargoIntegrity * 100f).toInt()} %",
                color = levelColor(completion.cargoIntegrity, 0.35f, 0.70f),
                modifier = Modifier.fillMaxWidth(),
                barWidth = null
            )
        }
        Spacer(Modifier.height(16.dp))
        GameButton(
            if (adReady) copy.doubleMissionReward(completion.rewardScrap) else copy.adLoading,
            onDoubleReward,
            enabled = adReady,
            style = BtnStyle.Primary,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        GameButton(
            copy.collectMissionReward,
            onCollect,
            style = BtnStyle.Secondary,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun PausePanel(
    ui: GameUiState,
    adReady: Boolean,
    onResume: () -> Unit,
    onRestart: () -> Unit,
    onEndRun: () -> Unit,
    onAssist: () -> Unit,
    onMenu: () -> Unit,
    modifier: Modifier = Modifier
) {
    val copy = LocalCopy.current
    GamePanel(
        title = copy.paused,
        subtitle = copy.pausedSubtitle,
        modifier = modifier,
        fillHeight = false
    ) {
        RunStats(
            distanceKm = ui.distanceKm,
            fuelBurnedL = ui.fuelBurnedL,
            itemsLooted = ui.itemsLooted,
            buildings = ui.buildingsVisited,
            clock = ui.clock,
            compactPause = true
        )
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GameButton(copy.continueRun, onResume, style = BtnStyle.Primary, modifier = Modifier.weight(1f))
            GameButton(copy.menu, onMenu, modifier = Modifier.weight(1f))
        }
        if (ui.canRequestAssist) {
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                GameButton(
                    if (!adReady) copy.adLoadingQuota(ui.helpAdsQuotaLabel)
                    else when (ui.assistOffer) {
                        RescueOffer.BATTERY, RescueOffer.BATTERY_FIT ->
                            copy.batteryWatchAd(ui.helpAdsQuotaLabel)
                        else -> copy.roadAssistWatchAd(ui.helpAdsQuotaLabel)
                    },
                    onAssist,
                    style = BtnStyle.Primary,
                    compact = true,
                    modifier = Modifier.widthIn(min = 168.dp, max = 240.dp)
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        // Keď sa už nedá pohnúť, hráč musí vedieť jazdu ukončiť tak, aby sa
        // prejdené kilometre zapísali. Reštart ich zahodí, toto nie.
        GameButton(
            copy.endRunKm(String.format("%.1f", ui.distanceKm)),
            onEndRun,
            style = BtnStyle.Secondary,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        // Zaseknutú jazdu treba vedieť zahodiť bez chodenia cez menu.
        GameButton(
            copy.restartRun,
            onRestart,
            style = BtnStyle.Danger,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun GameOverPanel(
    engine: GameEngine,
    scrapDoubled: Boolean,
    adReady: Boolean,
    onDoubleScrap: () -> Unit,
    onRescue: () -> Unit,
    onRetry: () -> Unit,
    onMenu: () -> Unit,
    modifier: Modifier = Modifier
) {
    val copy = LocalCopy.current
    GamePanel(
        title = if (engine.endReason == sk.kubis.endlessdrive.domain.model.EndReason.ARRIVED) {
            copy.journeyComplete
        } else {
            copy.runOver
        },
        subtitle = engine.endReason?.let { copy.endReasonMessage(it) },
        modifier = modifier,
        fillHeight = false
    ) {
        if (engine.endDetail.isNotEmpty()) {
            Text(engine.endDetail, color = GameColors.danger, fontSize = 13.sp)
            Spacer(Modifier.height(8.dp))
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                String.format("%.2f", engine.distanceKm),
                color = GameColors.accent,
                fontSize = 46.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.width(6.dp))
            Text("km", color = GameColors.textDim, fontSize = 16.sp, modifier = Modifier.padding(bottom = 8.dp))
            if (engine.isNewRecord) {
                Spacer(Modifier.width(10.dp))
                Chip(copy.newRecord, GameColors.accent, filled = true, modifier = Modifier.padding(bottom = 10.dp))
            }
        }
        Spacer(Modifier.height(10.dp))
        RunStats(
            distanceKm = engine.distanceKm,
            fuelBurnedL = engine.fuelBurnedL,
            itemsLooted = engine.itemsLooted,
            buildings = engine.buildingsVisited,
            clock = engine.clock,
            showDistance = false
        )
        Spacer(Modifier.height(14.dp))
        val rescueLabel = engine.rescueAdLabel
        if (rescueLabel != null) {
            GameButton(
                if (adReady) rescueLabel else copy.adLoadingQuota(engine.helpAdsQuotaLabel),
                onRescue,
                style = BtnStyle.Primary,
                modifier = Modifier.fillMaxWidth()
            )
            if (engine.message.isNotBlank() &&
                engine.message != engine.endReason?.let { copy.endReasonMessage(it) }
            ) {
                Spacer(Modifier.height(6.dp))
                Text(engine.message, color = GameColors.textDim, fontSize = 12.sp)
            }
            Spacer(Modifier.height(8.dp))
        }
        GameButton(
            when {
                scrapDoubled -> copy.scrapX2Ready
                engine.scrap > 0 -> copy.bankX2(engine.scrap)
                else -> copy.noScrapToDouble
            },
            onDoubleScrap,
            style = BtnStyle.Secondary,
            enabled = adReady && engine.scrap > 0 && !scrapDoubled,
            modifier = Modifier.fillMaxWidth()
        )
        if (engine.scrap > 0 && !scrapDoubled) {
            Spacer(Modifier.height(4.dp))
            Text(
                copy.cashOutHint,
                color = GameColors.textDim,
                fontSize = 11.sp
            )
        }
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GameButton(copy.retry, onRetry, style = BtnStyle.Primary, modifier = Modifier.weight(1f))
            GameButton(copy.menu, onMenu, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun RunStats(
    distanceKm: Float,
    fuelBurnedL: Float,
    itemsLooted: Int,
    buildings: Int,
    clock: String,
    showDistance: Boolean = true,
    compactPause: Boolean = false
) {
    val copy = LocalCopy.current
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (compactPause) {
            Arrangement.spacedBy(18.dp, Alignment.CenterHorizontally)
        } else {
            Arrangement.SpaceBetween
        }
    ) {
        if (showDistance) StatCell(copy.distance, String.format("%.2f km", distanceKm))
        if (!compactPause) {
            StatCell(copy.burned, String.format("%.1f L", fuelBurnedL))
            StatCell(copy.loot, itemsLooted.toString())
            StatCell(copy.buildings, buildings.toString())
        }
        StatCell(copy.time, clock)
    }
}

@Composable
private fun StatCell(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = GameColors.textDim, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        Text(value, color = GameColors.text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}
