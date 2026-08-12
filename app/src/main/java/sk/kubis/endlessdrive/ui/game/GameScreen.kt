package sk.kubis.endlessdrive.ui.game

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import sk.kubis.endlessdrive.core.GameConfig
import sk.kubis.endlessdrive.domain.model.BranchStyle
import sk.kubis.endlessdrive.domain.model.ComponentSlot
import sk.kubis.endlessdrive.domain.model.FluidGrade
import sk.kubis.endlessdrive.domain.model.GamePhase
import sk.kubis.endlessdrive.domain.model.ItemDef
import sk.kubis.endlessdrive.domain.model.ItemStack
import sk.kubis.endlessdrive.domain.model.SedanSpec
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

@Composable
fun GameScreen(
    viewModel: GameViewModel,
    assets: GameAssets,
    onExitToMenu: () -> Unit
) {
    var showInventory by remember { mutableStateOf(false) }
    var showCar by remember { mutableStateOf(false) }
    var showFps by rememberSaveable { mutableStateOf(false) }
    val renderer = remember(assets) { GameRenderer(assets) }
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }
    val ui by viewModel.ui.collectAsState()
    val engine = viewModel.game

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> viewModel.onLifecyclePause()
                Lifecycle.Event.ON_RESUME -> viewModel.onLifecycleResume()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) {
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
                    viewModel.onFrame(dt.coerceAtMost(GameConfig.MAX_FRAME_TIME), screenHeightPx)
                }
            }
        }
    }

    val anyPanelOpen = showInventory || showCar || ui.exploring ||
        ui.paused || ui.phase == GamePhase.GAME_OVER

    Box(Modifier.fillMaxSize().background(GameColors.panelSoft)) {
        // Hra kreslí cez celú plochu vrátane výrezu…
        Canvas(Modifier.fillMaxSize()) {
            @Suppress("UNUSED_EXPRESSION")
            viewModel.frame
            with(renderer) { draw(engine) }
        }

        // …ale ovládanie a HUD sa držia mimo výrezu a zaoblených rohov.
        // Výrez býva len na jednej strane – rovnaký inset na L/R, aby UI nebolo posunuté.
        val cutout = WindowInsets.displayCutout
        val layoutDir = LocalLayoutDirection.current
        val cutoutSide = with(density) {
            max(cutout.getLeft(this, layoutDir).toDp(), cutout.getRight(this, layoutDir).toDp())
        }
        val cutoutTop = with(density) { cutout.getTop(this).toDp() }
        val cutoutBottom = with(density) { cutout.getBottom(this).toDp() }
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

            // --- Prístrojovka -------------------------------------------------
            // Väčší odstup od okrajov kvôli zaobleným displejom a výrezom.
            Row(
                Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                    .padding(horizontal = SCREEN_MARGIN, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                VitalsPanel(ui)
                TripPanel(ui, showFps)
            }

            // Hlášky a výstrahy idú na úplný vrch, do voľného stredu medzi panely.
            AlertColumn(
                ui = ui,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 12.dp, start = 260.dp, end = 200.dp)
            )

            SideIcons(
                ui = ui,
                showFps = showFps,
                onToggleLights = { viewModel.toggleHeadlights() },
                onTogglePause = { viewModel.setPaused(!ui.paused) },
                onToggleFps = { showFps = !showFps },
                modifier = Modifier.align(Alignment.CenterEnd).padding(end = SCREEN_MARGIN)
            )

            // --- Ovládanie podľa fázy ----------------------------------------
            when (ui.phase) {
                GamePhase.PREP, GamePhase.STOPPED, GamePhase.EXPLORING -> {
                    if (!anyPanelOpen || ui.exploring) {
                        ActionBar(
                            ui = ui,
                            onStart = { viewModel.startEngine() },
                            onStopEngine = { viewModel.stopEngine() },
                            onInventory = { showInventory = true },
                            onCar = { showCar = true },
                            onEnter = { viewModel.enterBuilding() },
                            onLeave = { viewModel.leaveBuilding() },
                            onDrive = { viewModel.resume() },
                            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 18.dp)
                        )
                    }
                }
                GamePhase.DRIVING -> {
                    GameControls(
                        onGasChanged = viewModel::onGasChanged,
                        onBrakeChanged = viewModel::onBrakeChanged,
                        onStop = viewModel::stop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                GamePhase.JUNCTION -> Unit
                GamePhase.GAME_OVER -> Unit
            }

            // Rozcestie sa voli za jazdy - pas nad tlacidlom STOP, bez modalu.
            // Kresli sa az za GameControls, aby klik neprepadol na pedale.
            JunctionBar(
                ui = ui,
                // Nový segment = nové vetvy; kľúčom je jeho začiatok vo svete.
                choices = remember(engine.segment.worldOrigin) {
                    engine.junctionChoices.map { it.id to it.label }
                },
                onSelect = viewModel::selectBranch,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 76.dp)
            )

            // --- Prekryvné panely ---------------------------------------------
            if (ui.phase == GamePhase.JUNCTION) {
                Scrim()
                JunctionPanel(
                    engine = engine,
                    onChoose = viewModel::chooseBranch,
                    onInventory = { showInventory = true },
                    onCar = { showCar = true },
                    modifier = Modifier.align(Alignment.Center).fillMaxWidth(0.94f).fillMaxHeight(0.7f)
                )
            }

            if (ui.exploring) {
                LootPanel(
                    engine = engine,
                    bagRevision = ui.bagRevision,
                    pumpFuelL = ui.pumpFuelL,
                    onTake = { viewModel.takeLoot(it) },
                    onRefuel = { viewModel.refuelFromPump() },
                    onClose = { viewModel.leaveBuilding() },
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(top = 104.dp, bottom = 84.dp, end = 78.dp)
                        .fillMaxWidth(0.42f)
                        .fillMaxHeight()
                )
            }

            if (showInventory) {
                Scrim(onDismiss = { showInventory = false })
                InventoryPanel(
                    engine = engine,
                    bagRevision = ui.bagRevision,
                    onUse = { i, slot -> viewModel.useItem(i, slot) },
                    onDiscard = { viewModel.discardItem(it) },
                    onClose = { showInventory = false },
                    modifier = Modifier.align(Alignment.Center).fillMaxWidth(0.95f).fillMaxHeight(0.92f)
                )
            }

            if (showCar) {
                Scrim(onDismiss = { showCar = false })
                CarPanel(
                    engine = engine,
                    bagRevision = ui.bagRevision,
                    layers = assets.sedan,
                    onRepair = { viewModel.repair(it) },
                    onUnmount = { viewModel.unmount(it) },
                    onSwapTyres = { viewModel.swapTyres() },
                    onClose = { showCar = false },
                    modifier = Modifier.align(Alignment.Center).fillMaxWidth(0.95f).fillMaxHeight(0.92f)
                )
            }

            if (ui.paused && ui.phase != GamePhase.GAME_OVER) {
                Scrim()
                PausePanel(
                    ui = ui,
                    onResume = { viewModel.setPaused(false) },
                    onRestart = {
                        viewModel.retry()
                        viewModel.setPaused(false)
                    },
                    onMenu = onExitToMenu,
                    modifier = Modifier.align(Alignment.Center).width(360.dp)
                )
            }

            if (ui.phase == GamePhase.GAME_OVER) {
                Scrim()
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

// ---------------------------------------------------------------------------
// Spodná lišta akcií
// ---------------------------------------------------------------------------

@Composable
private fun ActionBar(
    ui: GameUiState,
    onStart: () -> Unit,
    onStopEngine: () -> Unit,
    onInventory: () -> Unit,
    onCar: () -> Unit,
    onEnter: () -> Unit,
    onLeave: () -> Unit,
    onDrive: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier
            .background(GameColors.hudBg, RoundedCornerShape(14.dp))
            .border(1.dp, GameColors.outline.copy(alpha = 0.7f), RoundedCornerShape(14.dp))
            .padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        GameButton("PACK", onInventory)
        GameButton("CAR", onCar)
        if (ui.exploring) {
            GameButton("LEAVE", onLeave)
        } else if (ui.hasNearbyBuilding) {
            GameButton("BUILDING", onEnter, style = BtnStyle.Ghost)
        }
        // Rozjazd je oddelený, aby sa neklikol pri prehľadávaní.
        Spacer(Modifier.width(24.dp))
        if (!ui.engineRunning) {
            GameButton("START", onStart, style = BtnStyle.Primary)
        } else {
            GameButton("SHUT OFF", onStopEngine)
            GameButton("DRIVE", onDrive, style = BtnStyle.Primary)
        }
    }
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
        subtitle = "Which way? What lies past the turn you only learn out there.",
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
                val accent = when (choice.style) {
                    BranchStyle.SAFE_RURAL -> Color(0xFF5C7F4C)
                    BranchStyle.INDUSTRIAL -> Color(0xFF5A6E80)
                    BranchStyle.SHORTCUT_RISK -> Color(0xFF9E4E30)
                }
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
    onDiscard: (Int) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    @Suppress("UNUSED_VARIABLE")
    val rev = bagRevision
    val weight = engine.inventory.totalWeight
    val cap = engine.inventory.maxWeight

    GamePanel(
        title = "PACK",
        subtitle = "${engine.inventory.usedSlots} / ${engine.inventory.slots.size} slots · " +
            "${weight.toInt()} / ${cap.toInt()} kg",
        onClose = onClose,
        modifier = modifier
    ) {
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(
                Modifier
                    .weight(1.5f)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState())
            ) {
                if (engine.inventory.usedSlots == 0) {
                    Text("The pack is empty.", color = GameColors.textDim, fontSize = 14.sp)
                }
                engine.inventory.slots.forEachIndexed { i, stack ->
                    if (stack == null) return@forEachIndexed
                    val tyre = stack.def.axleTire
                    ItemCard(
                        stack = stack,
                        // Pri gume porovnávame s tou horšou z náprav.
                        mounted = if (tyre) engine.car.worstTyre()
                        else stack.def.mountsTo?.let { engine.car.parts[it] },
                        primaryLabel = if (stack.def.fluid != null) "POUR IN" else "FIT",
                        onPrimary = { onUse(i, null) },
                        secondaryLabel = "DROP",
                        onSecondary = { onDiscard(i) },
                        onFitFront = if (tyre) ({ onUse(i, ComponentSlot.TIRE_FRONT) }) else null,
                        onFitRear = if (tyre) ({ onUse(i, ComponentSlot.TIRE_REAR) }) else null,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                    )
                }
            }
            CarSummary(engine, Modifier.weight(1f).fillMaxHeight())
        }
    }
}

/** Pravý stĺpec inventára: čo je práve v aute. */
@Composable
private fun CarSummary(engine: GameEngine, modifier: Modifier = Modifier) {
    val car = engine.car
    Column(
        modifier
            .background(GameColors.panelSoft, RoundedCornerShape(12.dp))
            .border(1.dp, GameColors.outline, RoundedCornerShape(12.dp))
            .padding(12.dp)
            .verticalScroll(rememberScrollState())
    ) {
        SectionLabel("IN THE CAR")
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatBar(
                "FUEL",
                (car.fuel / car.fuelCapacity).coerceIn(0f, 1f),
                "${car.fuel.toInt()} L",
                levelColor((car.fuel / car.fuelCapacity).coerceIn(0f, 1f)),
                inner = car.fuelPurity,
                innerColor = purityColor(car.fuelPurity)
            )
            StatBar(
                "OIL",
                (car.oil / car.oilCapacity).coerceIn(0f, 1f),
                String.format("%.1f L", car.oil),
                levelColor((car.oil / car.oilCapacity).coerceIn(0f, 1f)),
                inner = car.oilPurity,
                innerColor = purityColor(car.oilPurity)
            )
            StatBar(
                "COOLANT",
                (car.coolant / car.coolantCapacity).coerceIn(0f, 1f),
                String.format("%.1f L", car.coolant),
                levelColor((car.coolant / car.coolantCapacity).coerceIn(0f, 1f)),
                inner = car.coolantPurity,
                innerColor = purityColor(car.coolantPurity)
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "Quality: ${FluidGrade.of(minOf(car.fuelPurity, car.oilPurity, car.coolantPurity)).displayName.lowercase()}",
            color = purityColor(minOf(car.fuelPurity, car.oilPurity, car.coolantPurity)),
            fontSize = 12.sp
        )

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
                    slot.displayName,
                    color = if (part == null) GameColors.textDim else GameColors.text,
                    fontSize = 12.sp
                )
                if (part == null) {
                    Chip("missing", GameColors.danger)
                } else {
                    Text(
                        "${(part.health * 100).toInt()} %",
                        color = healthColor(part.health),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
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
    modifier: Modifier = Modifier,
    secondaryLabel: String? = null,
    onSecondary: (() -> Unit)? = null,
    /** Gumu si hráč zaradí sám – predok alebo zadok. */
    onFitFront: (() -> Unit)? = null,
    onFitRear: (() -> Unit)? = null
) {
    val def = stack.def
    val isFluid = def.fluid != null
    Column(
        modifier
            .background(GameColors.panelHigh, RoundedCornerShape(10.dp))
            .border(1.dp, GameColors.outline, RoundedCornerShape(10.dp))
            .padding(10.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    if (isFluid) "${def.name} · ${String.format("%.0f", def.fluidAmount * stack.count)} L"
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
                    } else {
                        Chip(stack.condition.displayName, healthColor(stack.health))
                        Chip("${(stack.health * 100).toInt()} %", healthColor(stack.health))
                    }
                    Chip("${(def.weight * stack.count).toInt()} kg", GameColors.textDim)
                }
                if (def.mountsTo != null) {
                    Spacer(Modifier.height(4.dp))
                    val better = mounted == null || stack.health > mounted.health + 0.05f
                    val worse = mounted != null && stack.health < mounted.health - 0.05f
                    Text(
                        when {
                            mounted == null -> "car: none → this is better"
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
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (onFitFront != null && onFitRear != null) {
                    GameButton("→ FRONT", onFitFront, compact = true, style = BtnStyle.Primary)
                    GameButton("→ REAR", onFitRear, compact = true, style = BtnStyle.Primary)
                } else {
                    GameButton(primaryLabel, onPrimary, compact = true, style = BtnStyle.Primary)
                }
                if (secondaryLabel != null && onSecondary != null) {
                    GameButton(secondaryLabel, onSecondary, compact = true, style = BtnStyle.Danger)
                }
            }
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
    pumpFuelL: Float,
    onTake: (Int) -> Unit,
    onRefuel: () -> Unit,
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
            if (pumpFuelL > 0.05f) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF2A3A28), RoundedCornerShape(10.dp))
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Pump", color = GameColors.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            "remaining ${String.format("%.0f", pumpFuelL)} L",
                            color = GameColors.textDim,
                            fontSize = 12.sp
                        )
                    }
                    GameButton("FILL UP", onRefuel, style = BtnStyle.Primary, compact = true)
                }
                Spacer(Modifier.height(8.dp))
            }
            b.loot.forEachIndexed { i, item ->
                ItemCard(
                    stack = item,
                    mounted = item.def.mountsTo?.let { engine.car.parts[it] },
                    primaryLabel = "TAKE",
                    onPrimary = { onTake(i) },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Panel auta
// ---------------------------------------------------------------------------

@Composable
private fun CarPanel(
    engine: GameEngine,
    bagRevision: Int,
    layers: SedanLayers,
    onRepair: (ComponentSlot) -> Unit,
    onUnmount: (ComponentSlot) -> Unit,
    onSwapTyres: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    @Suppress("UNUSED_VARIABLE")
    val rev = bagRevision
    var section by remember { mutableStateOf<CarSection?>(null) }
    var selected by remember { mutableStateOf<ComponentSlot?>(null) }
    val selectedPart = selected?.let { engine.car.parts[it] }
    val car = engine.car

    GamePanel(
        title = "CAR",
        subtitle = "Condition ${(car.overallHealth * 100).toInt()} % · " +
            "${car.fittedHudLabel(ComponentSlot.ENGINE)} · ${car.fittedHudLabel(ComponentSlot.DRIVETRAIN)} · " +
            "F ${car.fittedHudLabel(ComponentSlot.TIRE_FRONT)} / R ${car.fittedHudLabel(ComponentSlot.TIRE_REAR)} · " +
            car.fittedHudLabel(ComponentSlot.SUSPENSION),
        onClose = onClose,
        modifier = modifier
    ) {
        Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CarView(
                layers = layers,
                car = car,
                section = section,
                onSection = {
                    section = if (section == it) null else it
                    selected = null
                },
                modifier = Modifier.weight(1.05f).fillMaxHeight()
            )
            SlotGrid(
                engine = engine,
                section = section,
                selected = selected,
                onSelect = { selected = it },
                modifier = Modifier.weight(1f).fillMaxHeight()
            )
        }

        Spacer(Modifier.height(10.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .background(GameColors.panelSoft, RoundedCornerShape(10.dp))
                .border(1.dp, GameColors.outline, RoundedCornerShape(10.dp))
                .padding(10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                when {
                    selected == null -> Text(
                        "Pick a part on the right or a zone on the car.",
                        color = GameColors.textDim,
                        fontSize = 13.sp
                    )
                    selectedPart == null -> {
                        Text(selected!!.displayName, color = GameColors.accent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text("Missing — find one and fit it", color = GameColors.danger, fontSize = 12.sp)
                    }
                    else -> {
                        Text(
                            "${selected!!.displayName} · ${selectedPart.def.name}",
                            color = GameColors.accent,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Chip(selectedPart.condition.displayName, healthColor(selectedPart.health))
                            Chip("${(selectedPart.health * 100).toInt()} %", healthColor(selectedPart.health))
                        }
                        val extra = partExtraHint(selected!!, selectedPart.def, car)
                        if (extra != null) {
                            Text(extra, color = GameColors.text, fontSize = 12.sp)
                        }
                        Text(
                            "Repairs use oil from the pack; without it only a rough patch-up.",
                            color = GameColors.textDim,
                            fontSize = 11.sp
                        )
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Prehodenie gúm: zodratá hnaná náprava sa vymení za menej
                // ojazdenú – funguje aj bez jediného nálezu.
                GameButton("SWAP TYRES", onSwapTyres, compact = true)
                if (selectedPart != null) {
                    GameButton("REPAIR", { onRepair(selected!!) }, style = BtnStyle.Primary)
                    GameButton("REMOVE", { onUnmount(selected!!) })
                }
            }
        }
    }
}

/** Bočný pohľad zhodný s hrou + klikacie zóny. */
@Composable
private fun CarView(
    layers: SedanLayers,
    car: Car,
    section: CarSection?,
    onSection: (CarSection) -> Unit,
    modifier: Modifier = Modifier
) {
    val aspect = (layers.imageWidth.toFloat() / layers.imageHeight.toFloat()).coerceIn(1.8f, 4.5f)
    val artist = remember { CarArtist() }
    val accent = SedanSpec.accentColor
    Box(
        modifier
            .background(Color(0xFF2E3B44), RoundedCornerShape(12.dp))
            .border(1.dp, GameColors.outline, RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center
    ) {
        Box(Modifier.fillMaxWidth(0.92f).aspectRatio(aspect)) {
            Canvas(Modifier.fillMaxSize()) {
                // Fit sprite do boxu (rovnaký rect pre karosériu aj kolesá).
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
                drawImage(
                    image = layers.stripped,
                    dstOffset = androidx.compose.ui.unit.IntOffset(ox.toInt(), oy.toInt()),
                    dstSize = androidx.compose.ui.unit.IntSize(
                        drawW.toInt().coerceAtLeast(1),
                        drawH.toInt().coerceAtLeast(1)
                    )
                )
                listOf(ComponentSlot.TIRE_REAR, ComponentSlot.TIRE_FRONT).forEach { slot ->
                    if (!car.hasPart(slot)) return@forEach
                    val tire = car.parts[slot]
                    val r = layers.wheelRadiusFx * drawW * car.wheelScale(slot)
                    val cy = oy + layers.wheelCenterFy * drawH
                    val cx = ox + if (slot == ComponentSlot.TIRE_REAR) {
                        layers.rearWheelFx * drawW
                    } else {
                        layers.frontWheelFx * drawW
                    }
                    with(artist) {
                        drawTireScreen(
                            cx = cx,
                            cy = cy,
                            spinDeg = 0f,
                            r = r,
                            tireHealth = tire?.health ?: 0.7f,
                            accent = accent,
                            blurSteps = 1,
                            tireId = tire?.defId
                        )
                    }
                }
            }
            Row(Modifier.fillMaxSize()) {
                CarSection.entries.forEach { zone ->
                    CarZone(
                        label = zone.label,
                        active = section == zone,
                        onClick = { onSection(zone) },
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    )
                }
            }
        }
    }
}

/** Mriežka všetkých slotov – 3 stĺpce, bez scrollovania. */
@Composable
private fun SlotGrid(
    engine: GameEngine,
    section: CarSection?,
    selected: ComponentSlot?,
    onSelect: (ComponentSlot) -> Unit,
    modifier: Modifier = Modifier
) {
    val slots = ComponentSlot.entries
    val columns = 3
    val rows = (slots.size + columns - 1) / columns
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        for (row in 0 until rows) {
            Row(
                Modifier.fillMaxWidth().weight(1f),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                for (col in 0 until columns) {
                    val index = row * columns + col
                    if (index >= slots.size) {
                        Spacer(Modifier.weight(1f))
                        continue
                    }
                    val slot = slots[index]
                    SlotChip(
                        slot = slot,
                        part = engine.car.parts[slot],
                        selected = selected == slot,
                        dimmed = section != null && !section.slots.contains(slot),
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
    selected: Boolean,
    dimmed: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val health = part?.health
    Column(
        modifier
            .background(
                when {
                    selected -> GameColors.accent.copy(alpha = 0.28f)
                    part == null -> GameColors.danger.copy(alpha = 0.18f)
                    else -> GameColors.panelHigh
                },
                RoundedCornerShape(8.dp)
            )
            .border(
                1.dp,
                if (selected) GameColors.accent else GameColors.outline,
                RoundedCornerShape(8.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 7.dp, vertical = 5.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            slot.displayName,
            color = GameColors.textDim.copy(alpha = if (dimmed) 0.45f else 1f),
            fontSize = 11.sp,
            maxLines = 1
        )
        Text(
            if (health == null) "missing" else "${(health * 100).toInt()} %",
            color = (if (health == null) GameColors.danger else healthColor(health))
                .copy(alpha = if (dimmed) 0.5f else 1f),
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold
        )
        if (health != null) {
            Spacer(Modifier.height(3.dp))
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
private fun CarZone(
    label: String,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier
            .padding(3.dp)
            .background(
                if (active) GameColors.accent.copy(alpha = 0.22f) else Color.Transparent,
                RoundedCornerShape(8.dp)
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.BottomCenter
    ) {
        Text(
            label,
            color = if (active) GameColors.text else GameColors.text.copy(alpha = 0.7f),
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 4.dp)
        )
    }
}

private fun partExtraHint(slot: ComponentSlot, def: ItemDef, car: Car): String? = when (slot) {
    ComponentSlot.SUSPENSION ->
        "Ride height ${String.format("%.0f", car.rideHeight * 100)} cm · travel ${String.format("%.0f", car.suspTravel * 100)} cm"
    ComponentSlot.DRIVETRAIN ->
        "Layout ${car.driveLayout.displayName}"
    ComponentSlot.CARGO, ComponentSlot.ROOF_RACK ->
        "+${def.extraSlots} slots · +${def.extraWeight.toInt()} kg" +
            if (def.dragAdd > 0f) " · costs top speed" else ""
    ComponentSlot.CHAINS ->
        "On snow ×${String.format("%.2f", GameConfig.CHAINS_SNOW_BONUS)} grip · " +
            "on dry tarmac ×${String.format("%.2f", GameConfig.CHAINS_TARMAC_PENALTY)}, " +
            "top speed ${(GameConfig.CHAINS_MAX_SPEED * 3.6f).toInt()} km/h"
    ComponentSlot.TIRE_FRONT, ComponentSlot.TIRE_REAR -> {
        val nominal = if (def.grip > 0f) def.grip else def.reliability
        val tread = car.parts[slot]?.let { car.treadFactor(it.health) } ?: 1f
        val climb = Math.toDegrees(kotlin.math.atan(car.maxClimbSlope(0.1f, 8f)).toDouble())
        "Grip ${String.format("%.0f", nominal * tread * 100)} % of ${String.format("%.0f", nominal * 100)} % " +
            "· snow ${String.format("%.0f", def.snowGrip * 100)} % " +
            "· climbs ~${String.format("%.0f", climb)}° · size ×${String.format("%.2f", def.wheelScale)}"
    }
    else -> null
}

private enum class CarSection(val label: String, val slots: List<ComponentSlot>) {
    ZADOK(
        "Rear",
        listOf(
            ComponentSlot.REAR_BUMPER,
            ComponentSlot.FUEL_TANK,
            ComponentSlot.TIRE_REAR,
            ComponentSlot.DRIVETRAIN,
            ComponentSlot.CHAINS,
            ComponentSlot.CARGO,
            ComponentSlot.ROOF_RACK
        )
    ),
    STRED(
        "Middle",
        listOf(
            ComponentSlot.DOORS,
            ComponentSlot.WINDOWS,
            ComponentSlot.ALTERNATOR,
            ComponentSlot.STARTER,
            ComponentSlot.SUSPENSION
        )
    ),
    PREDOK(
        "Front",
        listOf(
            ComponentSlot.FRONT_BUMPER,
            ComponentSlot.HOOD,
            ComponentSlot.TIRE_FRONT,
            ComponentSlot.ENGINE,
            ComponentSlot.RADIATOR,
            ComponentSlot.BATTERY,
            ComponentSlot.BRAKES
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
