package sk.kubis.endlessdrive.ui.game

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import sk.kubis.endlessdrive.core.GameConfig
import sk.kubis.endlessdrive.domain.model.ComponentSlot
import sk.kubis.endlessdrive.domain.model.FluidGrade
import sk.kubis.endlessdrive.domain.model.FluidType
import sk.kubis.endlessdrive.domain.model.FuelKind
import sk.kubis.endlessdrive.domain.model.GamePhase
import sk.kubis.endlessdrive.domain.model.AudioSettings
import sk.kubis.endlessdrive.domain.model.ItemDef
import sk.kubis.endlessdrive.domain.model.ItemStack
import sk.kubis.endlessdrive.domain.model.ThrottleMode
import sk.kubis.endlessdrive.game.car.TireInjury
import sk.kubis.endlessdrive.domain.model.SedanSpec
import sk.kubis.endlessdrive.domain.model.VehiclePaint
import sk.kubis.endlessdrive.game.GameEngine
import sk.kubis.endlessdrive.game.car.Car
import sk.kubis.endlessdrive.game.car.MountedPart
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

/** Odstup UI od okrajov – zaoblené displeje a výrezy nesmú nič odrezať. */
private val SCREEN_MARGIN = 10.dp
private val CAR_ACTION_HEIGHT = 36.dp

@Composable
fun GameScreen(
    viewModel: GameViewModel,
    assets: GameAssets,
    onExitToMenu: () -> Unit,
    audioSettings: AudioSettings = AudioSettings(),
    throttleMode: ThrottleMode = ThrottleMode.BINARY,
    showFps: Boolean = false,
    onToggleFps: () -> Unit = {}
) {
    var showInventory by remember { mutableStateOf(false) }
    var showCar by remember { mutableStateOf(false) }
    val renderer = remember(assets) { GameRenderer(assets) }
    val density = LocalDensity.current
    val context = LocalContext.current
    val audio = remember(context) { GameAudio(context) }
    LaunchedEffect(audio, audioSettings) { audio.settings = audioSettings }
    val configuration = LocalConfiguration.current
    val compactLoot = configuration.screenWidthDp < 760 || configuration.screenHeightDp < 500
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }
    val ui by viewModel.ui.collectAsState()
    val engine = viewModel.game

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
                }
                Lifecycle.Event.ON_RESUME -> {
                    viewModel.onLifecycleResume()
                    audio.setMuted(false)
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
        while (true) {
            withFrameNanos { now ->
                if (now + GameConfig.FRAME_LOCK_TOLERANCE_NANOS < nextDue) return@withFrameNanos
                nextDue += interval
                if (nextDue <= now) nextDue = now + interval
                val dt = ((now - last) / 1_000_000_000.0).toFloat()
                last = now
                if (dt > 0f) {
                    val step = dt.coerceAtMost(GameConfig.MAX_FRAME_TIME)
                    viewModel.onFrame(step, screenHeightPx)
                    audio.update(
                        viewModel.game,
                        step,
                        paused = viewModel.ui.value.paused
                    )
                }
            }
        }
    }

    val garageOpen = showInventory || showCar
    DisposableEffect(garageOpen) {
        viewModel.setGarageOpen(garageOpen)
        onDispose { viewModel.setGarageOpen(false) }
    }

    Box(Modifier.fillMaxSize().background(GameColors.panelSoft)) {
        // Scéna ide cez celú plochu vrátane výrezu. Odsadenie plátna do čiernych
        // pásov po stranách sa neosvedčilo – obraz tým utrpel viac, než získal.
        Canvas(Modifier.fillMaxSize()) {
            @Suppress("UNUSED_EXPRESSION")
            viewModel.frame
            with(renderer) { draw(engine) }
        }

        // …ale ovládanie a HUD sa držia mimo výrezu, zaoblených rohov aj
        // systémových líšt. Bez navigačnej lišty sa spodok palubnej dosky
        // schoval pod gesto-pruh a kontrolky boli orezané.
        val layoutDir = LocalLayoutDirection.current
        val safe = WindowInsets.safeDrawing
        val cutoutSide = with(density) {
            max(safe.getLeft(this, layoutDir).toDp(), safe.getRight(this, layoutDir).toDp())
        }
        val cutoutTop = with(density) { safe.getTop(this).toDp() }
        val cutoutBottom = with(density) { safe.getBottom(this).toDp() }
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
                    .padding(top = 12.dp, start = 150.dp, end = 210.dp)
            )

            SideIcons(
                ui = ui,
                showFps = showFps,
                onToggleLights = { viewModel.toggleHeadlights() },
                onToggleRoofLights = { viewModel.toggleRoofLights() },
                onTogglePause = { viewModel.setPaused(!ui.paused) },
                onToggleFps = onToggleFps,
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
                    onBrakeChanged = viewModel::onBrakeChanged,
                    onStop = viewModel::stop,
                    buildingNearby = ui.hasNearbyBuilding,
                    throttleMode = throttleMode,
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
                    // Bez tmavého bloku už doska autu nezavadzia, takže môže
                    // sedieť v strede v oboch stavoch.
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }

            // Korisť sa prehrabáva pri bežiacom HUD – stmavenie nemá.
            if (ui.exploring) {
                val lootModifier = if (compactLoot) {
                    Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                        .fillMaxWidth(0.96f)
                        .fillMaxHeight(0.94f)
                } else {
                    Modifier
                        .align(Alignment.CenterEnd)
                        .padding(top = 104.dp, bottom = 84.dp, end = 78.dp)
                        .fillMaxWidth(0.42f)
                        .fillMaxHeight()
                }
                LootPanel(
                    engine = engine,
                    bagRevision = ui.bagRevision,
                    onTake = { viewModel.takeLoot(it) },
                    onRefuel = { viewModel.refuelFromPump(it) },
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
                        modifier = Modifier.align(Alignment.Center).fillMaxWidth(0.95f).fillMaxHeight(0.92f)
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
                    onDismiss = { showCar = false }
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
                        layers = assets.sedan,
                        debugRepair = viewModel.debugOptions.repairControls,
                        onRepair = { viewModel.repair(it) },
                        onScrapRepair = { viewModel.repairWithScrap(it) },
                        onPatchPuncture = { viewModel.repairPuncture(it) },
                        onUpgrade = { viewModel.upgradeWithScrap(it) },
                        onDrain = { fluid, litres -> viewModel.drainFluid(fluid, litres) },
                        onUnmount = { viewModel.unmount(it) },
                        onSwapTyres = { viewModel.swapTyres() },
                        onClose = { showCar = false },
                        modifier = Modifier.align(Alignment.Center).fillMaxWidth(0.95f).fillMaxHeight(0.92f)
                    )
                }
            }

            if (ui.paused && ui.phase != GamePhase.GAME_OVER) {
                Overlay(safeArea) {
                    PausePanel(
                        ui = ui,
                        onResume = { viewModel.setPaused(false) },
                        onRestart = {
                            viewModel.retry()
                            viewModel.setPaused(false)
                        },
                        onEndRun = { viewModel.endRun() },
                        onMenu = onExitToMenu,
                        modifier = Modifier.align(Alignment.Center).width(360.dp)
                    )
                }
            }

            if (ui.phase == GamePhase.GAME_OVER) {
                Overlay(safeArea) {
                    GameOverPanel(
                        engine = engine,
                        onRetry = { viewModel.retry() },
                        onMenu = onExitToMenu,
                        modifier = Modifier.align(Alignment.Center).width(420.dp)
                    )
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


// ---------------------------------------------------------------------------
// Križovatka
// ---------------------------------------------------------------------------

@Composable
private fun JunctionPanel(
    engine: GameEngine,
    onChoose: (Int) -> Unit,
    onInventory: () -> Unit,
    onCar: () -> Unit,
    modifier: Modifier = Modifier
) {
    GamePanel(
        title = "JUNCTION",
        subtitle = "Pick a road. Hints show fuel, loot and terrain.",
        modifier = modifier,
        header = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GameButton("PACK", onInventory, compact = true)
                GameButton("CAR", onCar, compact = true)
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
                            choice.label,
                            color = GameColors.text,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(choice.hint, color = GameColors.textDim, fontSize = 13.sp)
                    }
                    Chip("TAKE IT", accent, filled = true)
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

    GamePanel(
        title = "PACK",
        subtitle = "Scrap ${engine.scrap} · On you ${pack.usedSlots}/${pack.slots.size} · " +
            "${pack.totalWeight.toInt()}/${pack.maxWeight.toInt()} kg   ·   " +
            "Boot ${boot.usedSlots}/${boot.slots.size} · " +
            "${boot.totalWeight.toInt()}/${boot.maxWeight.toInt()} kg",
        onClose = onClose,
        modifier = modifier
    ) {
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(
                Modifier
                    .weight(1.4f)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState())
            ) {
                SectionLabel("ON YOU")
                Spacer(Modifier.height(6.dp))
                if (pack.usedSlots == 0) {
                    Text("The pack is empty.", color = GameColors.textDim, fontSize = 14.sp)
                }
                pack.slots.forEachIndexed { i, stack ->
                    if (stack == null) return@forEachIndexed
                    val tyre = stack.def.axleTire
                    val kit = stack.def.isPunctureKit
                    ItemCard(
                        revision = bagRevision,
                        stack = stack,
                        // Pri gume porovnávame s tou horšou z náprav.
                        mounted = if (tyre) engine.car.worstTyre()
                        else stack.def.mountsTo?.let { engine.car.parts[it] },
                        primaryLabel = when {
                            stack.def.fluid != null -> "POUR IN"
                            kit -> "USE"
                            else -> "FIT"
                        },
                        onPrimary = { onUse(i, null) },
                        secondaryLabel = if (engine.bootReachable) "TO BOOT" else null,
                        onSecondary = if (engine.bootReachable) ({ onStow(i) }) else null,
                        onScrap = { onScrap(i) },
                        onFitFront = if (tyre) ({ onUse(i, ComponentSlot.TIRE_FRONT) }) else null,
                        onFitRear = if (tyre) ({ onUse(i, ComponentSlot.TIRE_REAR) }) else null,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                    )
                }
            }
            Column(
                Modifier
                    .weight(1.4f)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState())
            ) {
                SectionLabel(if (engine.bootReachable) "BOOT" else "BOOT — STOP AT THE CAR")
                Spacer(Modifier.height(6.dp))
                if (boot.usedSlots == 0) {
                    Text("The boot is empty.", color = GameColors.textDim, fontSize = 14.sp)
                }
                boot.slots.forEachIndexed { i, stack ->
                    if (stack == null) return@forEachIndexed
                    val tyre = stack.def.axleTire
                    val kit = stack.def.isPunctureKit
                    val mountable = stack.def.fluid != null ||
                        stack.def.mountTargets().isNotEmpty() ||
                        kit
                    ItemCard(
                        revision = bagRevision,
                        stack = stack,
                        mounted = if (tyre) engine.car.worstTyre()
                        else stack.def.mountsTo?.let { engine.car.parts[it] },
                        // Motor sa montuje rovno z kufra – na chrbát sa nezmestí.
                        primaryLabel = when {
                            stack.def.fluid != null -> "POUR IN"
                            kit -> "USE"
                            else -> "FIT"
                        },
                        onPrimary = { if (mountable) onUseFromBoot(i, null) },
                        secondaryLabel = "TAKE",
                        onSecondary = { onTake(i) },
                        onScrap = { onScrapBoot(i) },
                        onFitFront = if (tyre) ({ onUseFromBoot(i, ComponentSlot.TIRE_FRONT) }) else null,
                        onFitRear = if (tyre) ({ onUseFromBoot(i, ComponentSlot.TIRE_REAR) }) else null,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                    )
                }
            }
            CarSummary(engine, bagRevision, Modifier.weight(1f).fillMaxHeight())
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
    innerColor: Color
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        StatBar(label, ratio, value, color, inner = inner, innerColor = innerColor)
    }
}

/** Pravý stĺpec inventára: čo je práve v aute. */
@Composable
private fun CarSummary(engine: GameEngine, revision: Int, modifier: Modifier = Modifier) {
    // Compose nevidí do GameEngine – bez tohto parametra by sa stĺpec „v aute“
    // po namontovaní dielu neprekreslil a ukazoval by staré čísla.
    @Suppress("UNUSED_VARIABLE")
    val rev = revision
    val car = engine.car
    Column(
        modifier
            .background(GameColors.panelSoft, RoundedCornerShape(12.dp))
            .border(1.dp, GameColors.outline, RoundedCornerShape(12.dp))
            .padding(12.dp)
            .verticalScroll(rememberScrollState())
    ) {
        SectionLabel("IN THE CAR")
        Text(
            "Body paint: ${VehiclePaint.at(car.bodyPaintIndex).displayName}",
            color = Color(VehiclePaint.at(car.bodyPaintIndex).argb),
            fontSize = 12.sp
        )
        val rearPercent = (car.rearWeightBias * 100f).toInt()
        Text(
            "Mass ${car.totalMassKg.toInt()} kg · front ${100 - rearPercent}% / rear $rearPercent%",
            color = GameColors.textDim,
            fontSize = 11.sp
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FluidStat(
                car.requiredFuelKind.displayName.uppercase(),
                (car.fuel / car.fuelCapacity).coerceIn(0f, 1f),
                "${car.fuel.toInt()} L",
                levelColor((car.fuel / car.fuelCapacity).coerceIn(0f, 1f)),
                car.fuelPurity,
                purityColor(car.fuelPurity)
            )
            FluidStat(
                "OIL",
                (car.oil / car.oilCapacity).coerceIn(0f, 1f),
                String.format("%.1f L", car.oil),
                levelColor((car.oil / car.oilCapacity).coerceIn(0f, 1f)),
                car.oilPurity,
                purityColor(car.oilPurity)
            )
            FluidStat(
                "COOLANT",
                (car.coolant / car.coolantCapacity).coerceIn(0f, 1f),
                String.format("%.1f L", car.coolant),
                levelColor((car.coolant / car.coolantCapacity).coerceIn(0f, 1f)),
                car.coolantPurity,
                purityColor(car.coolantPurity)
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "Quality: ${FluidGrade.of(minOf(car.fuelPurity, car.oilPurity, car.coolantPurity)).displayName.lowercase()}",
            color = purityColor(minOf(car.fuelPurity, car.oilPurity, car.coolantPurity)),
            fontSize = 12.sp
        )
        if (car.fuel > 0.05f) {
            val diesel = (car.fuelDieselFraction * 100f).toInt()
            Text(
                "Tank mix: ${100 - diesel}% petrol · $diesel% diesel" +
                    if (car.wrongFuelFraction >= 0.15f) " — WRONG FOR THIS ENGINE" else "",
                color = if (car.wrongFuelFraction >= 0.15f) GameColors.danger else GameColors.textDim,
                fontSize = 12.sp
            )
        }

        Spacer(Modifier.height(12.dp))
        SectionLabel("PARTS")
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
                        append(slot.displayName)
                        part?.def?.slotDetail(slot)?.takeIf { it.isNotBlank() }
                            ?.let { append(" ($it)") }
                        part?.paintIndex?.takeIf { it >= 0 && part.def.mountsTo?.takesBodyPaint == true }
                            ?.let { append(" · ${VehiclePaint.at(it).displayName}") }
                    },
                    color = if (part == null) GameColors.textDim else GameColors.text,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(end = 8.dp)
                )
                if (part == null) {
                    Chip("missing", GameColors.danger)
                } else if (!part.def.hasDurability) {
                    Chip("PERMANENT", GameColors.ok)
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
    onFitRear: (() -> Unit)? = null
) {
    @Suppress("UNUSED_VARIABLE")
    val rev = revision
    val def = stack.def
    val isFluid = def.fluid != null
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
                    if (isFluid) "${def.name} · ${String.format("%.1f", stack.fluidLitres)} L"
                    else "${def.name}${if (stack.count > 1) " ×${stack.count}" else ""}",
                    color = GameColors.text,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(3.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (isFluid) {
                        Chip(stack.grade.displayName, purityColor(stack.purity))
                        Chip("${(stack.purity * 100).toInt()} %", purityColor(stack.purity))
                    } else if (def.isPunctureKit) {
                        Chip("PATCHES A FLAT", GameColors.ok)
                    } else if (!def.hasDurability) {
                        Chip("PERMANENT", GameColors.ok)
                    } else {
                        Chip(stack.condition.displayName, healthColor(stack.health))
                        Chip("${(stack.health * 100).toInt()} %", healthColor(stack.health))
                    }
                    Chip("${(def.weight * stack.count).toInt()} kg", GameColors.textDim)
                }
                if (stack.paintIndex >= 0 && def.mountsTo?.takesBodyPaint == true) {
                    val paint = VehiclePaint.at(stack.paintIndex)
                    Spacer(Modifier.height(5.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PaintedPartPreview(
                            slot = def.mountsTo,
                            paint = paint,
                            modifier = Modifier.width(68.dp).height(32.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text("PAINT", color = GameColors.textDim, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            Text(paint.displayName, color = Color(paint.argb), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
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
                            mounted == null -> "car: none → this is better"
                            !def.hasDurability && better ->
                                "car +${mounted.def.extraSlots} slots → +${def.extraSlots} slots"
                            !def.hasDurability && worse ->
                                "car +${mounted.def.extraSlots} slots → smaller storage"
                            !def.hasDurability -> "permanent storage upgrade"
                            better -> "car ${(mounted.health * 100).toInt()} % → this is better"
                            worse -> "car ${(mounted.health * 100).toInt()} % → this is worse"
                            else -> "car ${(mounted.health * 100).toInt()} % → about the same"
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
                    GameButton("REAR", onFitRear, compact = true, style = BtnStyle.Primary, modifier = Modifier.weight(1f))
                    GameButton("FRONT", onFitFront, compact = true, style = BtnStyle.Primary, modifier = Modifier.weight(1f))
                } else {
                    GameButton(primaryLabel, onPrimary, compact = true, style = BtnStyle.Primary, modifier = Modifier.weight(1f))
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
                            "SCRAP +${stack.scrapValue}",
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

/** Samostatný náhľad nájdeného dielu – bez podkladového modelu celého auta. */
@Composable
private fun PaintedPartPreview(
    slot: ComponentSlot?,
    paint: VehiclePaint,
    modifier: Modifier = Modifier
) {
    val bodyPart = slot?.let { BodyPartCatalog.partsOf(it).firstOrNull() }
    val sprite = bodyPart?.let { BodyPartCatalog.specs[it] }
    if (sprite != null) {
        Box(
            modifier,
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(sprite.res),
                contentDescription = slot.displayName,
                contentScale = ContentScale.Fit,
                // SrcIn zachová priehľadnosť PNG: farbu dostane iba samotný
                // plech, nikdy celý obdĺžnik náhľadu.
                colorFilter = ColorFilter.tint(Color(paint.argb), BlendMode.SrcIn),
                modifier = Modifier.fillMaxSize()
            )
        }
        return
    }
    Canvas(
        modifier
            .background(GameColors.panelSoft, RoundedCornerShape(6.dp))
            .border(1.dp, GameColors.outline, RoundedCornerShape(6.dp))
            .padding(3.dp)
    ) {
        val w = size.width
        val h = size.height
        val painted = Color(paint.argb)
        val dark = Color(0xFF292622)
        val glass = Color(0xFF26343B)
        drawOval(
            Color.Black.copy(alpha = 0.22f),
            topLeft = Offset(w * 0.10f, h * 0.75f),
            size = Size(w * 0.80f, h * 0.14f)
        )

        when (slot) {
            ComponentSlot.DOOR_REAR, ComponentSlot.DOOR_FRONT -> {
                val front = slot == ComponentSlot.DOOR_FRONT
                val door = Path().apply {
                    moveTo(w * 0.16f, h * 0.22f)
                    lineTo(w * if (front) 0.78f else 0.70f, h * 0.16f)
                    lineTo(w * 0.86f, h * 0.70f)
                    lineTo(w * 0.18f, h * 0.74f)
                    close()
                }
                drawPath(door, painted)
                val window = Path().apply {
                    moveTo(w * 0.22f, h * 0.25f)
                    lineTo(w * if (front) 0.72f else 0.65f, h * 0.21f)
                    lineTo(w * 0.76f, h * 0.40f)
                    lineTo(w * 0.24f, h * 0.43f)
                    close()
                }
                drawPath(window, glass)
                drawRoundRect(dark, Offset(w * 0.68f, h * 0.51f), Size(w * 0.11f, h * 0.045f))
            }
            ComponentSlot.HOOD, ComponentSlot.TRUNK_LID -> {
                val panel = Path().apply {
                    moveTo(w * 0.12f, h * 0.32f)
                    lineTo(w * 0.82f, h * 0.20f)
                    lineTo(w * 0.90f, h * 0.62f)
                    lineTo(w * 0.20f, h * 0.72f)
                    close()
                }
                drawPath(panel, painted)
                drawLine(
                    Color.White.copy(alpha = 0.22f),
                    Offset(w * 0.24f, h * 0.38f),
                    Offset(w * 0.76f, h * 0.29f),
                    1.2f
                )
                if (slot == ComponentSlot.TRUNK_LID) {
                    drawRoundRect(dark, Offset(w * 0.48f, h * 0.52f), Size(w * 0.12f, h * 0.055f))
                }
            }
            ComponentSlot.FRONT_BUMPER, ComponentSlot.REAR_BUMPER -> {
                drawRoundRect(painted, Offset(w * 0.08f, h * 0.38f), Size(w * 0.84f, h * 0.28f))
                drawRoundRect(dark, Offset(w * 0.25f, h * 0.48f), Size(w * 0.50f, h * 0.08f))
            }
            ComponentSlot.HEADLIGHT, ComponentSlot.TAILLIGHT -> {
                val lamp = Path().apply {
                    moveTo(w * 0.15f, h * 0.30f)
                    lineTo(w * 0.82f, h * 0.22f)
                    lineTo(w * 0.90f, h * 0.62f)
                    lineTo(w * 0.24f, h * 0.70f)
                    close()
                }
                drawPath(lamp, painted)
                drawPath(lamp, Color.White.copy(alpha = 0.22f))
            }
            else -> drawRoundRect(painted, Offset(w * 0.14f, h * 0.30f), Size(w * 0.72f, h * 0.40f))
        }
    }
}

// ---------------------------------------------------------------------------
// Budova / loot
// ---------------------------------------------------------------------------

@Composable
private fun LootPanel(
    engine: GameEngine,
    bagRevision: Int,
    onTake: (Int) -> Unit,
    onRefuel: (FuelKind) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val b = engine.activeBuilding ?: return
    @Suppress("UNUSED_VARIABLE")
    val rev = bagRevision

    GamePanel(
        title = b.type.displayName.uppercase(),
        subtitle = if (b.loot.isEmpty()) "Nothing left here." else "${b.loot.size} things to take",
        onClose = onClose,
        modifier = modifier
    ) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            if (b.type == sk.kubis.endlessdrive.domain.model.BuildingType.GAS_STATION) {
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

@Composable
private fun FuelPumpRow(
    kind: FuelKind,
    litres: Float,
    recommended: Boolean,
    onRefuel: (FuelKind) -> Unit
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                kind.displayName.uppercase() + if (recommended) " · YOUR ENGINE" else "",
                color = if (recommended) GameColors.ok else GameColors.text,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                if (litres > 0.05f) "remaining ${String.format("%.0f", litres)} L" else "empty",
                color = if (litres > 0.05f) GameColors.textDim else GameColors.danger,
                fontSize = 11.sp
            )
        }
        GameButton(
            "FILL ${kind.displayName.uppercase()}",
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
    ItemCard(
        stack = item,
        mounted = item.def.mountsTo?.let { engine.car.parts[it] },
        primaryLabel = "TAKE",
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
    layers: SedanLayers,
    debugRepair: Boolean,
    onRepair: (ComponentSlot) -> Unit,
    onScrapRepair: (ComponentSlot) -> Unit,
    onPatchPuncture: (ComponentSlot) -> Unit,
    onUpgrade: (ComponentSlot) -> Unit,
    onDrain: (FluidType, Float?) -> Unit,
    onUnmount: (ComponentSlot) -> Unit,
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

    GamePanel(
        title = "CAR",
        onClose = onClose,
        modifier = modifier,
        header = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ScrapCurrencyIcon(Modifier.size(20.dp))
                    CarHeaderStat("SCRAP", "$scrap", GameColors.accent)
                }
                CarHeaderStat(
                    "CONDITION",
                    "${(overallNow * 100).toInt()} %",
                    healthColor(overallNow)
                )
            }
        }
    ) {
        Row(
            Modifier.fillMaxWidth().weight(1f),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            CarView(
                layers = layers,
                car = car,
                revision = partsRevision,
                frontHealth = frontTireHealth,
                rearHealth = rearTireHealth,
                frontInjury = frontTireInjury,
                rearInjury = rearTireInjury,
                section = section,
                onSection = {
                    section = it
                    selected = null
                    pendingDrain = null
                },
                modifier = Modifier.weight(1.05f).fillMaxHeight()
            )
            Column(Modifier.weight(1f).fillMaxHeight()) {
                SectionLabel(
                    section.label.uppercase(),
                    modifier = Modifier.padding(bottom = 6.dp)
                )
                SlotGrid(
                    engine = engine,
                    revision = partsRevision,
                    section = section,
                    selected = selected,
                    onSelect = {
                        selected = it
                        pendingDrain = null
                    },
                    modifier = Modifier.weight(1f).fillMaxWidth()
                )
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
            onUpgrade = onUpgrade,
            onDrain = onDrain,
            onUnmount = onUnmount,
            onSwapTyres = onSwapTyres
        )
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
    onUpgrade: (ComponentSlot) -> Unit,
    onDrain: (FluidType, Float?) -> Unit,
    onUnmount: (ComponentSlot) -> Unit,
    onSwapTyres: () -> Unit
) {
    @Suppress("UNUSED_VARIABLE")
    val rev = partsRevision
    val selectedPart = selected?.let { engine.car.parts[it] }
    val liveHealth = selectedPart?.health ?: -1f
    val liveInjury = selectedPart?.injury ?: TireInjury.INFLATED
    Column(
        Modifier
            .fillMaxWidth()
            .background(GameColors.panelSoft, RoundedCornerShape(10.dp))
            .border(1.dp, GameColors.outline, RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        val drain = pendingDrain
        if (drain != null) {
            val amount = car.fluidLevel(drain)
            val step = drainStep(drain).coerceAtMost(amount)
            val ready = !car.engineRunning && engine.phase != GamePhase.DRIVING
            val drainNote = when {
                car.engineRunning -> "Switch the engine off first."
                engine.phase == GamePhase.DRIVING -> "Park the car first."
                else -> "${String.format("%.1f", amount)} L in ${drainLocation(drain)} — discarded."
            }
            Text(
                "DRAIN ${drain.displayName.uppercase()}",
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
                GameButton("CANCEL", { onPendingDrain(null) }, compact = true, modifier = drainMod)
                if (amount > step + 0.05f) {
                    GameButton(
                        "DRAIN ${String.format("%.1f", step)} L",
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
                    "DRAIN ALL ${String.format("%.1f", amount)} L",
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
                        "Pick a part in this zone.",
                        color = GameColors.textDim,
                        fontSize = 12.sp,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    selectedPart == null -> {
                        Text(
                            selected!!.displayName,
                            color = GameColors.accent,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1
                        )
                        Text(
                            "Empty — find one and fit it",
                            color = GameColors.danger,
                            fontSize = 12.sp,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    else -> {
                        val part = selectedPart
                        val status = partStatusLabel(part)
                        Text(
                            selected!!.displayName,
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
                                paint.displayName,
                                color = Color(paint.argb),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        val hint = partBarHint(selected!!, part, car, engine)
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
                    val upgradeCost = engine.scrapUpgradeCost(slot)
                    val workshopBlock = engine.scrapWorkshopBlockReason()
                    val punctured = engine.canPatchPuncture(slot)
                    val shredded = liveInjury == TireInjury.SHREDDED
                    val workshopReady = workshopBlock == null
                    val kits = engine.punctureKitCount()
                    val parked = engine.phase != GamePhase.DRIVING &&
                        kotlin.math.abs(engine.car.speed) <= 0.2f
                    val patchEnabled = parked && (kits > 0 || workshopReady)
                    val drainAction = when (slot) {
                        ComponentSlot.ENGINE -> FluidType.OIL to "DRAIN OIL"
                        ComponentSlot.FUEL_TANK -> FluidType.FUEL to "DRAIN FUEL"
                        ComponentSlot.RADIATOR -> FluidType.COOLANT to "DRAIN COOLANT"
                        else -> null
                    }
                    val patchLabel = if (kits > 0) {
                        "PATCH KIT"
                    } else {
                        "PATCH −${engine.scrapPatchCost()}"
                    }
                    val primary = when {
                        shredded && tyreSelected -> CarCta.Swap
                        punctured -> CarCta.Patch
                        repairCost != null -> CarCta.Repair
                        !tyreSelected && upgradeTarget != null && upgradeCost != null -> CarCta.Upgrade
                        else -> null
                    }
                    when (primary) {
                        CarCta.Swap -> GameButton(
                            "SWAP",
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
                        CarCta.Upgrade -> GameButton(
                            "UPGRADE −$upgradeCost",
                            { onUpgrade(slot) },
                            compact = true,
                            enabled = workshopReady,
                            style = BtnStyle.Primary,
                            modifier = actionMod
                        )
                        null -> Unit
                    }
                    if (tyreSelected && primary != CarCta.Swap) {
                        GameButton(
                            "SWAP",
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
                    if (upgradeTarget != null && upgradeCost != null && primary != CarCta.Upgrade) {
                        GameButton(
                            "UPGRADE −$upgradeCost",
                            { onUpgrade(slot) },
                            compact = true,
                            enabled = workshopReady,
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
                    GameButton(
                        "REMOVE",
                        { onUnmount(slot) },
                        compact = true,
                        style = BtnStyle.Ghost,
                        modifier = actionMod
                    )
                    if (debugRepair) {
                        GameButton(
                            "DEBUG",
                            { onRepair(slot) },
                            compact = true,
                            style = BtnStyle.Ghost,
                            modifier = actionMod
                        )
                    }
                } else {
                    GameButton(
                        "SWAP TYRES",
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

private fun drainLocation(fluid: FluidType): String = when (fluid) {
    FluidType.FUEL -> "the fuel tank"
    FluidType.OIL -> "the engine"
    FluidType.COOLANT -> "the radiator"
    FluidType.BRAKE_FLUID -> "the brake system"
}

private enum class CarCta { Swap, Patch, Repair, Upgrade }

private fun partStatusLabel(part: MountedPart): Pair<String, Color> = when {
    !part.def.hasDurability -> "PERMANENT" to GameColors.ok
    part.injury == TireInjury.SHREDDED -> "ON RIM" to GameColors.danger
    part.injury == TireInjury.PUNCTURED -> "FLAT" to GameColors.danger
    else -> part.condition.displayName to healthColor(part.health)
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

/** Bočný pohľad zhodný s hrou + klikacie zóny. */
@Composable
private fun CarView(
    layers: SedanLayers,
    car: Car,
    revision: Int,
    frontHealth: Float,
    rearHealth: Float,
    frontInjury: TireInjury,
    rearInjury: TireInjury,
    section: CarSection,
    onSection: (CarSection) -> Unit,
    modifier: Modifier = Modifier
) {
    @Suppress("UNUSED_VARIABLE")
    val rev = revision
    @Suppress("UNUSED_VARIABLE")
    val visualKey = Triple(frontInjury to frontHealth, rearInjury to rearHealth, rev)
    val aspect = (layers.imageWidth.toFloat() / layers.imageHeight.toFloat()).coerceIn(1.8f, 4.5f)
    val artist = remember { CarArtist() }
    val accent = SedanSpec.accentColor
    Column(
        modifier
            .background(Color(0xFF2E3B44), RoundedCornerShape(12.dp))
            .border(1.dp, GameColors.outline, RoundedCornerShape(12.dp))
            .padding(8.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            CarSection.entries.forEach { zone ->
                CarZoneTab(
                    label = zone.label,
                    active = section == zone,
                    onClick = { onSection(zone) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Box(
            Modifier.weight(1f).fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Box(Modifier.fillMaxWidth(0.96f).aspectRatio(aspect)) {
                Canvas(Modifier.fillMaxSize()) {
                    @Suppress("UNUSED_EXPRESSION")
                    visualKey
                    @Suppress("UNUSED_EXPRESSION")
                    frontInjury
                    @Suppress("UNUSED_EXPRESSION")
                    rearInjury
                    @Suppress("UNUSED_EXPRESSION")
                    frontHealth
                    @Suppress("UNUSED_EXPRESSION")
                    rearHealth
                    val imgAspect = layers.imageWidth.toFloat() / layers.imageHeight.toFloat()
                    val boxAspect = size.width / size.height.coerceAtLeast(1f)
                    val drawW: Float
                    val drawH: Float
                    if (boxAspect > imgAspect) {
                        drawH = size.height
                        drawW = drawH * imgAspect
                    } else {
                        drawW = size.width
                        drawH = drawW / imgAspect
                    }
                    val ox = (size.width - drawW) * 0.5f
                    val oy = (size.height - drawH) * 0.5f
                    with(artist) {
                        drawBodyPreview(car, layers, ox, oy, drawW, drawH)
                    }
                    listOf(ComponentSlot.TIRE_REAR, ComponentSlot.TIRE_FRONT).forEach { slot ->
                        val r = layers.wheelRadiusFx * drawW
                        val cy = oy + layers.wheelCenterFy * drawH
                        val cx = ox + if (slot == ComponentSlot.TIRE_REAR) {
                            layers.rearWheelFx * drawW
                        } else {
                            layers.frontWheelFx * drawW
                        }
                        with(artist) {
                            drawAxleWheel(
                                car = car,
                                slot = slot,
                                cx = cx,
                                cy = cy,
                                spinDeg = 0f,
                                wellR = r,
                                layers = layers,
                                accent = accent
                            )
                        }
                    }
                }
                Row(Modifier.fillMaxSize()) {
                    CarSection.entries.forEach { zone ->
                        CarZoneHit(
                            active = section == zone,
                            onClick = { onSection(zone) },
                            modifier = Modifier.weight(1f).fillMaxHeight()
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            zoneSummary(section, car),
            color = GameColors.text,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
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
                slot.displayName,
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
                missing -> "empty"
                permanent -> "PERMANENT"
                injury == TireInjury.PUNCTURED -> "${(health * 100).toInt()} % · FLAT"
                injury == TireInjury.SHREDDED -> "${(health * 100).toInt()} % · RIM"
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

private fun zoneSummary(section: CarSection, car: Car): String = when (section) {
    CarSection.PREDOK ->
        "${car.fittedHudLabel(ComponentSlot.ENGINE)} · F ${car.fittedHudLabel(ComponentSlot.TIRE_FRONT)}"
    CarSection.STRED ->
        "${car.fittedHudLabel(ComponentSlot.SUSPENSION)} · ${car.fittedHudLabel(ComponentSlot.DRIVETRAIN)}"
    CarSection.ZADOK ->
        "R ${car.fittedHudLabel(ComponentSlot.TIRE_REAR)}"
}

private fun partBarHint(
    slot: ComponentSlot,
    part: MountedPart,
    car: Car,
    engine: GameEngine
): String? {
    val injury = when (part.injury) {
        TireInjury.SHREDDED -> "Fit a new tyre"
        TireInjury.PUNCTURED -> if (engine.punctureKitCount() > 0) {
            "Patch kit in the bag"
        } else {
            "Patch the puncture"
        }
        else -> null
    }
    val tire = tireStatLine(slot, part.def, car)?.let {
        "Grip ${it.gripNow}/${it.gripMax} · Snow ${it.snow}%"
    }
    val extra = partExtraHint(slot, part.def, car)
    val next = engine.scrapUpgradeTarget(slot)?.let { "Next ${it.name}" }
    val workshop = engine.canPatchPuncture(slot) ||
        engine.scrapRepairCost(slot) != null ||
        engine.scrapUpgradeTarget(slot) != null
    val block = if (workshop) engine.scrapWorkshopBlockReason() else null
    return listOfNotNull(injury, tire, extra, next, block)
        .joinToString(" · ")
        .ifBlank { null }
}

private fun partExtraHint(slot: ComponentSlot, def: ItemDef, car: Car): String? = when (slot) {
    ComponentSlot.SUSPENSION ->
        "Ride height ${String.format("%.0f", car.rideHeight * 100)} cm · travel ${String.format("%.0f", car.suspTravel * 100)} cm"
    ComponentSlot.DRIVETRAIN ->
        "Layout ${car.driveLayout.displayName}"
    ComponentSlot.ALTERNATOR ->
        "Charging output ${(car.alternatorOutput * 100).toInt()} % · " +
            "battery ceiling ${(car.batteryChargeCeiling * 100).toInt()} %"
    ComponentSlot.BATTERY -> {
        val healthPct = ((car.parts[ComponentSlot.BATTERY]?.health ?: 0f) * 100f).toInt()
        val socPct = (car.batteryCharge * 100f).toInt()
        "Electrical charge $socPct % · health $healthPct %"
    }
    ComponentSlot.CARGO, ComponentSlot.ROOF_RACK ->
        "+${def.extraSlots} slots · +${def.extraWeight.toInt()} kg" +
            if (def.dragAdd > 0f) " · adds aerodynamic drag" else ""
    ComponentSlot.CHAINS ->
        "On snow ×${String.format("%.2f", GameConfig.CHAINS_SNOW_BONUS)} grip · " +
            "on dry tarmac ×${String.format("%.2f", GameConfig.CHAINS_TARMAC_PENALTY)}, " +
            "heavy drag above ${(GameConfig.CHAINS_MAX_SPEED * 3.6f).toInt()} km/h"
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
private fun PausePanel(
    ui: GameUiState,
    onResume: () -> Unit,
    onRestart: () -> Unit,
    onEndRun: () -> Unit,
    onMenu: () -> Unit,
    modifier: Modifier = Modifier
) {
    GamePanel(
        title = "PAUSED",
        subtitle = "Time and fuel use are on hold.",
        modifier = modifier,
        fillHeight = false
    ) {
        RunStats(
            distanceKm = ui.distanceKm,
            fuelBurnedL = ui.fuelBurnedL,
            itemsLooted = ui.itemsLooted,
            buildings = ui.buildingsVisited,
            clock = ui.clock
        )
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GameButton("CONTINUE", onResume, style = BtnStyle.Primary, modifier = Modifier.weight(1f))
            GameButton("MENU", onMenu, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        // Keď sa už nedá pohnúť, hráč musí vedieť jazdu ukončiť tak, aby sa
        // prejdené kilometre zapísali. Reštart ich zahodí, toto nie.
        GameButton(
            "END RUN · ${String.format("%.1f", ui.distanceKm)} km",
            onEndRun,
            style = BtnStyle.Secondary,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        // Zaseknutú jazdu treba vedieť zahodiť bez chodenia cez menu.
        GameButton(
            "RESTART RUN",
            onRestart,
            style = BtnStyle.Danger,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun GameOverPanel(
    engine: GameEngine,
    onRetry: () -> Unit,
    onMenu: () -> Unit,
    modifier: Modifier = Modifier
) {
    GamePanel(
        title = "RUN OVER",
        subtitle = engine.endReason?.message,
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
                Chip("NEW RECORD", GameColors.accent, filled = true, modifier = Modifier.padding(bottom = 10.dp))
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
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GameButton("RETRY", onRetry, style = BtnStyle.Primary, modifier = Modifier.weight(1f))
            GameButton("MENU", onMenu, modifier = Modifier.weight(1f))
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
    showDistance: Boolean = true
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        if (showDistance) StatCell("DISTANCE", String.format("%.2f km", distanceKm))
        StatCell("BURNED", String.format("%.1f L", fuelBurnedL))
        StatCell("LOOT", itemsLooted.toString())
        StatCell("BUILDINGS", buildings.toString())
        StatCell("TIME", clock)
    }
}

@Composable
private fun StatCell(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = GameColors.textDim, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        Text(value, color = GameColors.text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}
