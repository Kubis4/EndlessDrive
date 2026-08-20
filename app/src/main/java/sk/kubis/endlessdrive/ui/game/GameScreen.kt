package sk.kubis.endlessdrive.ui.game

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
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
import sk.kubis.endlessdrive.core.GameConfig
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
                    .padding(top = 12.dp, start = 120.dp, end = 120.dp)
            )

            SideIcons(
                ui = ui,
                showFps = showFps,
                onToggleLights = { viewModel.toggleHeadlights() },
                onTogglePause = { viewModel.setPaused(!ui.paused) },
                onToggleFps = { showFps = !showFps },
                // Vrch obrazovky je po presune vitals do dosky voľný – ikony
                // tam nezavadzajú a neplávajú cez scénu.
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = SCREEN_MARGIN, top = SCREEN_MARGIN)
            )

            // --- Ovládanie podľa fázy ----------------------------------------
            if (ui.phase == GamePhase.DRIVING) {
                GameControls(
                    onGasChanged = viewModel::onGasChanged,
                    onBrakeChanged = viewModel::onBrakeChanged,
                    onStop = viewModel::stop,
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
                    onInventory = { showInventory = true },
                    onCar = { showCar = true },
                    onEnter = { viewModel.enterBuilding() },
                    onLeave = { viewModel.leaveBuilding() },
                    onDrive = { viewModel.resume() },
                    onRest = { viewModel.restUntilDawn() },
                    onStopDriving = { viewModel.stop() },
                    // Bez tmavého bloku už doska autu nezavadzia, takže môže
                    // sedieť v strede v oboch stavoch.
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
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
                // Nad plynovým pedálom – palec je pri voľbe už tam.
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 18.dp, bottom = 150.dp)
            )

            // Korisť sa prehrabáva pri bežiacom HUD – stmavenie nemá.
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
            if (ui.phase == GamePhase.JUNCTION) {
                Overlay(safeArea) {
                    JunctionPanel(
                        engine = engine,
                        onChoose = viewModel::chooseBranch,
                        onInventory = { showInventory = true },
                        onCar = { showCar = true },
                        modifier = Modifier.align(Alignment.Center).fillMaxWidth(0.94f).fillMaxHeight(0.7f)
                    )
                }
            }

            if (showInventory) {
                Overlay(safeArea, onDismiss = { showInventory = false }) {
                    InventoryPanel(
                        engine = engine,
                        bagRevision = ui.bagRevision,
                        onUse = { i, slot -> viewModel.useItem(i, slot) },
                        onUseFromBoot = { i, slot -> viewModel.useBootItem(i, slot) },
                        onDiscard = { viewModel.discardItem(it) },
                        onDiscardBoot = { viewModel.discardBootItem(it) },
                        onStow = { viewModel.stowInBoot(it) },
                        onTake = { viewModel.takeFromBoot(it) },
                        onClose = { showInventory = false },
                        modifier = Modifier.align(Alignment.Center).fillMaxWidth(0.95f).fillMaxHeight(0.92f)
                    )
                }
            }

            if (showCar) {
                Overlay(safeArea, onDismiss = { showCar = false }) {
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
    onDismiss: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
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
    onDiscard: (Int) -> Unit,
    onDiscardBoot: (Int) -> Unit,
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
        subtitle = "On you ${pack.usedSlots}/${pack.slots.size} · " +
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
                    ItemCard(
                        revision = bagRevision,
                        stack = stack,
                        // Pri gume porovnávame s tou horšou z náprav.
                        mounted = if (tyre) engine.car.worstTyre()
                        else stack.def.mountsTo?.let { engine.car.parts[it] },
                        primaryLabel = if (stack.def.fluid != null) "POUR IN" else "FIT",
                        onPrimary = { onUse(i, null) },
                        secondaryLabel = if (engine.bootReachable) "TO BOOT" else null,
                        onSecondary = if (engine.bootReachable) ({ onStow(i) }) else null,
                        onDiscard = { onDiscard(i) },
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
                    val mountable = stack.def.fluid != null || stack.def.mountTargets().isNotEmpty()
                    ItemCard(
                        revision = bagRevision,
                        stack = stack,
                        mounted = if (tyre) engine.car.worstTyre()
                        else stack.def.mountsTo?.let { engine.car.parts[it] },
                        // Motor sa montuje rovno z kufra – na chrbát sa nezmestí.
                        primaryLabel = if (stack.def.fluid != null) "POUR IN" else "FIT",
                        onPrimary = { if (mountable) onUseFromBoot(i, null) },
                        secondaryLabel = "TAKE",
                        onSecondary = { onTake(i) },
                        onDiscard = { onDiscardBoot(i) },
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
                // Za názvom slotu ide v zátvorke to, čím sa namontovaný diel
                // líši – bez toho sa hráč nedozvedel, ktorý motor či pruženie
                // vlastne má. Riadok si berie zvyšok šírky a dlhý názov radšej
                // skráti; inak by percentá vpravo vytisol do stĺpca znakov.
                Text(
                    buildString {
                        append(slot.displayName)
                        part?.def?.slotDetail(slot)?.takeIf { it.isNotBlank() }
                            ?.let { append(" ($it)") }
                    },
                    color = if (part == null) GameColors.textDim else GameColors.text,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(end = 8.dp)
                )
                if (part == null) {
                    Chip("missing", GameColors.danger)
                } else {
                    Text(
                        "${(part.health * 100).toInt()} %",
                        color = healthColor(part.health),
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
    /** Zahodenie veci – vždy samostatné tlačidlo, nikdy nie zdieľané. */
    onDiscard: (() -> Unit)? = null,
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
            if ((secondaryLabel != null && onSecondary != null) || onDiscard != null) {
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
                    if (onDiscard != null) {
                        GameButton("DROP", onDiscard, compact = true, style = BtnStyle.Danger, modifier = Modifier.weight(1f))
                    }
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
                    revision = bagRevision,
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
                revision = bagRevision,
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
                            tireId = tire?.defId,
                            art = layers.wheelImage(tire?.defId)
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
    revision: Int,
    section: CarSection?,
    selected: ComponentSlot?,
    onSelect: (ComponentSlot) -> Unit,
    modifier: Modifier = Modifier
) {
    // Bez revízie by mriežka po výmene dielu ostala na starých hodnotách.
    @Suppress("UNUSED_VARIABLE")
    val rev = revision
    val slots = ComponentSlot.entries
    val columns = 3
    val rows = (slots.size + columns - 1) / columns
    // Riadky si výšku určia podľa obsahu a mriežka sa v prípade potreby posúva.
    // Pri rovnomernom delení výšky (weight) sa percentá aj „missing“ orezávali.
    Column(
        modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        for (row in 0 until rows) {
            Row(
                Modifier.fillMaxWidth().height(IntrinsicSize.Min),
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
