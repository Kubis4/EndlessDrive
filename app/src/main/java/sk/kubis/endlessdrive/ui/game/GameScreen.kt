package sk.kubis.endlessdrive.ui.game

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import sk.kubis.endlessdrive.core.GameConfig
import sk.kubis.endlessdrive.domain.model.BranchStyle
import sk.kubis.endlessdrive.domain.model.ComponentSlot
import sk.kubis.endlessdrive.domain.model.FluidGrade
import sk.kubis.endlessdrive.domain.model.GamePhase
import sk.kubis.endlessdrive.game.GameEngine

@Composable
fun GameScreen(
    viewModel: GameViewModel,
    onExitToMenu: () -> Unit
) {
    var showInventory by remember { mutableStateOf(false) }
    var showCar by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val assets = remember(context) { GameAssets(context) }
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

    Box(Modifier.fillMaxSize().background(Color(0xFF3A4550))) {
        Canvas(Modifier.fillMaxSize()) {
            @Suppress("UNUSED_EXPRESSION")
            viewModel.frame
            with(renderer) { draw(engine) }
        }

        // Horná lišta v jednom stĺpci – HUD a stavové tlačidlá sa nikdy neprekryjú.
        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                GameHud(ui, Modifier.weight(1f, fill = false))
                Spacer(Modifier.width(8.dp))
                StatusBar(
                    ui = ui,
                    onToggleLights = { viewModel.toggleHeadlights() },
                    onTogglePause = { viewModel.setPaused(!ui.paused) }
                )
            }
            if (ui.message.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = ui.message,
                    color = Color(0xFFE8DFD0),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                )
            }
        }

        when (ui.phase) {
            GamePhase.PREP, GamePhase.STOPPED, GamePhase.EXPLORING -> {
                ActionBar(
                    engineRunning = ui.engineRunning,
                    exploring = ui.phase == GamePhase.EXPLORING,
                    showBuilding = ui.hasNearbyBuilding || ui.exploring,
                    onStart = { viewModel.startEngine() },
                    onStopEngine = { viewModel.stopEngine() },
                    onInventory = { showInventory = true },
                    onCar = { showCar = true },
                    onEnter = { viewModel.enterBuilding() },
                    onLeave = { viewModel.leaveBuilding() },
                    onDrive = { viewModel.resume() },
                    modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp)
                )
            }
            GamePhase.DRIVING -> {
                GameControls(
                    onGasChanged = viewModel::onGasChanged,
                    onBrakeChanged = viewModel::onBrakeChanged,
                    onStop = viewModel::stop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            GamePhase.JUNCTION -> {
                JunctionPanel(
                    engine = engine,
                    onChoose = viewModel::chooseBranch,
                    onInventory = { showInventory = true },
                    onCar = { showCar = true },
                    modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp)
                )
            }
            GamePhase.GAME_OVER -> {
                GameOverOverlay(
                    engine = engine,
                    onRetry = { viewModel.retry() },
                    onMenu = onExitToMenu,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }

        if (ui.paused && ui.phase != GamePhase.GAME_OVER) {
            PausePanel(
                ui = ui,
                onResume = { viewModel.setPaused(false) },
                onMenu = onExitToMenu,
                modifier = Modifier.align(Alignment.Center)
            )
        }

        if (showInventory) {
            InventoryPanel(
                engine = engine,
                bagRevision = ui.bagRevision,
                onUse = { viewModel.useItem(it) },
                onDiscard = { viewModel.discardItem(it) },
                onClose = { showInventory = false },
                modifier = Modifier.align(Alignment.Center)
            )
        }
        if (showCar) {
            CarPanel(
                engine = engine,
                bagRevision = ui.bagRevision,
                layers = assets.sedan,
                onRepair = { viewModel.repair(it) },
                onUnmount = { viewModel.unmount(it) },
                onClose = { showCar = false },
                modifier = Modifier.align(Alignment.Center)
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
                modifier = Modifier.align(Alignment.CenterEnd).padding(16.dp)
            )
        }
    }
}

@Composable
private fun GameHud(ui: GameUiState, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.52f), RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val fuelRatio = (ui.fuelL / ui.fuelCapacityL.coerceAtLeast(1f)).coerceIn(0f, 1f)
        val oilRatio = (ui.oilL / ui.oilCapacityL.coerceAtLeast(1f)).coerceIn(0f, 1f)
        val coolRatio = (ui.coolantL / ui.coolantCapacityL.coerceAtLeast(1f)).coerceIn(0f, 1f)
        val tempRatio = ((ui.temperature - 40f) / 90f).coerceIn(0f, 1f)

        // Ukazovatele sa v núdzi zmestia posunom, rýchlosť a km ostávajú vždy vidieť.
        Row(
            modifier = Modifier.weight(1f, fill = false).horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Gauge(
                "PALIVO", fuelRatio, String.format("%.0f L", ui.fuelL),
                levelColor(fuelRatio, 0.15f, 0.35f), ui.fuelPurity
            )
            Gauge(
                "OLEJ", oilRatio, String.format("%.1f L", ui.oilL),
                levelColor(oilRatio, 0.15f, 0.35f), ui.oilPurity
            )
            Gauge(
                "CHLADENIE", coolRatio, String.format("%.1f L", ui.coolantL),
                levelColor(coolRatio, 0.15f, 0.35f), ui.coolantPurity
            )
            Gauge(
                "BATÉRIA", ui.batteryCharge, "${(ui.batteryCharge * 100).toInt()} %",
                levelColor(ui.batteryCharge, 0.15f, 0.35f)
            )
            Gauge(
                "TEPLOTA",
                tempRatio,
                "${ui.temperature.toInt()}°C",
                when {
                    ui.temperature > 110f -> Color(0xFFEF5350)
                    ui.temperature > 98f -> Color(0xFFFFB74D)
                    else -> Color(0xFF81C784)
                }
            )
            Gauge(
                "STAV AUTA", ui.overallHealth, "${(ui.overallHealth * 100).toInt()} %",
                levelColor(ui.overallHealth, 0.25f, 0.5f)
            )
        }

        val reversing = ui.speedKmh < -0.5f
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                // Pri cúvaní ukazujeme R a kladné číslo, nie mínus.
                (if (reversing) "R " else "") + kotlin.math.abs(ui.speedKmh).toInt(),
                color = if (reversing) Color(0xFFFFB74D) else Color(0xFFE8DFD0),
                style = MaterialTheme.typography.displaySmall
            )
            Text("km/h", color = Color(0xFF9A8F7E), style = MaterialTheme.typography.labelLarge)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                String.format("%.2f", ui.distanceKm),
                color = Color(0xFFC4A35A),
                style = MaterialTheme.typography.displaySmall,
                maxLines = 1
            )
            Text(
                "km · rek. ${String.format("%.1f", ui.bestDistanceKm)}",
                color = Color(0xFF9A8F7E),
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun Gauge(
    label: String,
    ratio: Float,
    value: String,
    color: Color,
    /** Kvapaliny: čistota obsahu nádrže. Záporné = ukazovateľ bez čistoty. */
    purity: Float = -1f
) {
    Column(horizontalAlignment = Alignment.Start) {
        Text(label, color = Color(0xFF9A8F7E), style = MaterialTheme.typography.labelLarge)
        Box(
            Modifier
                .width(58.dp)
                .height(7.dp)
                .background(Color(0xFF2B2721), RoundedCornerShape(4.dp))
        ) {
            Box(
                Modifier
                    .fillMaxWidth(ratio.coerceIn(0f, 1f))
                    .fillMaxHeight()
                    .background(color, RoundedCornerShape(4.dp))
            )
            if (purity in 0f..0.999f) {
                // Tenký prúžok = koľko z objemu je naozaj kvapalina a nie voda.
                Box(
                    Modifier
                        .fillMaxWidth((ratio * purity).coerceIn(0f, 1f))
                        .height(3.dp)
                        .background(purityColor(purity), RoundedCornerShape(4.dp))
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(value, color = Color(0xFFE8DFD0), style = MaterialTheme.typography.titleLarge)
            if (purity in 0f..0.92f) {
                Spacer(Modifier.width(3.dp))
                Text(
                    "${(purity * 100).toInt()}%",
                    color = purityColor(purity),
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

private fun purityColor(purity: Float): Color = when {
    purity >= 0.92f -> Color(0xFF81C784)
    purity >= 0.75f -> Color(0xFFDCE775)
    purity >= 0.55f -> Color(0xFFFFB74D)
    else -> Color(0xFFEF5350)
}

private fun levelColor(ratio: Float, low: Float, mid: Float): Color = when {
    ratio <= low -> Color(0xFFEF5350)
    ratio <= mid -> Color(0xFFFFB74D)
    else -> Color(0xFF81C784)
}

/** Hodiny, svetlá a pauza – vpravo hore. */
@Composable
private fun StatusBar(
    ui: GameUiState,
    onToggleLights: () -> Unit,
    onTogglePause: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            (if (ui.isNight) "☾" else "☀") + " " + ui.clock,
            color = Color(0xFFE8DFD0),
            style = MaterialTheme.typography.titleLarge
        )
        SmallToggle("SVETLÁ", ui.headlightsOn, onToggleLights)
        SmallToggle("PAUZA", ui.paused, onTogglePause)
    }
}

@Composable
private fun SmallToggle(label: String, active: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (active) Color(0xFFC4A35A) else Color(0xFF3A342C),
            contentColor = if (active) Color(0xFF1A1612) else Color(0xFFE8DFD0)
        ),
        shape = RoundedCornerShape(6.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge)
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
    Surface(
        modifier = modifier.fillMaxWidth(0.92f),
        color = Color(0xFF1A1612).copy(alpha = 0.92f),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(
                "KRIŽOVATKA — kam pôjdeš?",
                style = MaterialTheme.typography.headlineMedium,
                color = Color(0xFFE8DFD0)
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                engine.junctionChoices.forEach { choice ->
                    val accent = when (choice.style) {
                        BranchStyle.SAFE_RURAL -> Color(0xFF6B8F5A)
                        BranchStyle.INDUSTRIAL -> Color(0xFF6A7A8A)
                        BranchStyle.SHORTCUT_RISK -> Color(0xFFB85C38)
                    }
                    Button(
                        onClick = { onChoose(choice.id) },
                        modifier = Modifier.weight(1f).heightIn(min = 84.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = accent,
                            contentColor = Color(0xFFFFF8F0)
                        ),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(10.dp)
                    ) {
                        // Zámerne bez čísel – čo je za odbočkou, sa zistí až jazdou.
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(choice.label, style = MaterialTheme.typography.headlineMedium)
                            Text(choice.hint, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionBtn("INVENTÁR", onInventory)
                ActionBtn("AUTO", onCar)
            }
        }
    }
}

@Composable
private fun ActionBar(
    engineRunning: Boolean,
    exploring: Boolean,
    showBuilding: Boolean,
    onStart: () -> Unit,
    onStopEngine: () -> Unit,
    onInventory: () -> Unit,
    onCar: () -> Unit,
    onEnter: () -> Unit,
    onLeave: () -> Unit,
    onDrive: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = Color(0xFF1A1612).copy(alpha = 0.88f),
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (!engineRunning) ActionBtn("ŠTART", onStart)
            else ActionBtn("VYPNÚŤ", onStopEngine)
            ActionBtn("INVENTÁR", onInventory)
            ActionBtn("AUTO", onCar)
            if (exploring) ActionBtn("ODÍSŤ", onLeave)
            else if (showBuilding) ActionBtn("BUDOVA", onEnter)
            if (engineRunning) ActionBtn("JAZDIŤ", onDrive)
        }
    }
}

@Composable
private fun ActionBtn(label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFF3A342C),
            contentColor = Color(0xFFE8DFD0)
        ),
        shape = RoundedCornerShape(6.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun InventoryPanel(
    engine: GameEngine,
    bagRevision: Int,
    onUse: (Int) -> Unit,
    onDiscard: (Int) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    // bagRevision forces recomposition when inventory mutates
    @Suppress("UNUSED_VARIABLE")
    val rev = bagRevision
    Surface(
        modifier = modifier
            .fillMaxWidth(0.94f)
            .fillMaxHeight(0.88f),
        color = Color(0xFF1A1612).copy(alpha = 0.96f),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(Modifier.fillMaxSize().padding(14.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "INVENTÁR  (${engine.inventory.totalWeight.toInt()} / ${GameConfig.INVENTORY_MAX_WEIGHT.toInt()} kg)",
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color(0xFFE8DFD0)
                )
                TextButton(onClick = onClose) { Text("✕") }
            }
            Text(
                "Porovnaj diely v aute vpravo. Montáž okno nezavrie.",
                color = Color(0xFF9A8F7E),
                style = MaterialTheme.typography.bodyLarge
            )
            Spacer(Modifier.height(8.dp))
            Row(Modifier.weight(1f).fillMaxWidth()) {
                Column(
                    Modifier
                        .weight(1.15f)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState())
                        .padding(end = 8.dp)
                ) {
                    Text("V BATOHU", color = Color(0xFFC4A35A), style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(6.dp))
                    engine.inventory.slots.forEachIndexed { i, stack ->
                        if (stack != null) {
                            val def = stack.def
                            val label = if (def.fluid != null) {
                                "${def.name} · ${String.format("%.0f", def.fluidAmount * stack.count)} L · ${stack.stateLabel}"
                            } else {
                                "${def.name} ×${stack.count} · ${stack.stateLabel}"
                            }
                            val mounted = def.mountsTo?.let { engine.car.parts[it] }
                            val compare = when {
                                def.fluid != null -> null
                                mounted == null -> "v aute: —"
                                else -> "v aute: ${(mounted.health * 100).toInt()}% · ${mounted.condition.displayName}"
                            }
                            Column(Modifier.padding(vertical = 4.dp).fillMaxWidth()) {
                                Text(label, color = Color(0xFFE8DFD0))
                                if (compare != null) {
                                    Text(compare, color = Color(0xFF9A8F7E), style = MaterialTheme.typography.bodyLarge)
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Button(
                                        onClick = { onUse(i) },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color(0xFF3D3428),
                                            contentColor = Color(0xFFE8DFD0)
                                        )
                                    ) {
                                        Text(if (def.fluid != null) "DOPLNIŤ" else "MONTOVAŤ")
                                    }
                                    Button(
                                        onClick = { onDiscard(i) },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color(0xFF4A2A28),
                                            contentColor = Color(0xFFEF9A9A)
                                        )
                                    ) {
                                        Text("VYHODIŤ")
                                    }
                                }
                            }
                        }
                    }
                    if (engine.inventory.usedSlots == 0) {
                        Text("Prázdny batoh", color = Color(0xFF9A8F7E))
                    }
                }
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState())
                        .padding(start = 8.dp)
                ) {
                    Text("V AUTE", color = Color(0xFFC4A35A), style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Palivo ${String.format("%.0f", engine.car.fuel)} L · Olej ${String.format("%.1f", engine.car.oil)} L · Chladiaca ${String.format("%.1f", engine.car.coolant)} L",
                        color = Color(0xFFC8BFAE)
                    )
                    Spacer(Modifier.height(8.dp))
                    ComponentSlot.entries.forEach { slot ->
                        val part = engine.car.parts[slot]
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 3.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(slot.displayName, color = Color(0xFFE8DFD0), modifier = Modifier.weight(1f))
                            Text(
                                if (part == null) "chýba"
                                else "${(part.health * 100).toInt()}%",
                                color = if (part == null) Color(0xFFB85C38) else Color(0xFF9A8F7E)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CarPanel(
    engine: GameEngine,
    bagRevision: Int,
    layers: SedanLayers,
    onRepair: (ComponentSlot) -> Unit,
    onUnmount: (ComponentSlot) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    @Suppress("UNUSED_VARIABLE")
    val rev = bagRevision
    var section by remember { mutableStateOf<CarSection?>(null) }
    var selected by remember { mutableStateOf<ComponentSlot?>(null) }
    val selectedPart = selected?.let { engine.car.parts[it] }

    Surface(
        modifier = modifier
            .fillMaxWidth(0.96f)
            .fillMaxHeight(0.94f),
        color = Color(0xFF2A241C).copy(alpha = 0.97f),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(Modifier.fillMaxSize().padding(12.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("AUTO", style = MaterialTheme.typography.headlineMedium, color = Color(0xFFE8DFD0))
                Text(
                    "Palivo ${engine.car.fuel.toInt()} L · Olej ${String.format("%.1f", engine.car.oil)} L · " +
                        "Chladiaca ${String.format("%.1f", engine.car.coolant)} L",
                    color = Color(0xFFC8BFAE),
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    "palivo ${(engine.car.fuelPurity * 100).toInt()} % · " +
                        "olej ${(engine.car.oilPurity * 100).toInt()} % · " +
                        "chladiaca ${(engine.car.coolantPurity * 100).toInt()} %",
                    color = purityColor(
                        minOf(engine.car.fuelPurity, engine.car.oilPurity, engine.car.coolantPurity)
                    ),
                    style = MaterialTheme.typography.bodyLarge
                )
                TextButton(onClick = onClose) { Text("✕") }
            }

            Spacer(Modifier.height(8.dp))

            Row(Modifier.fillMaxWidth().weight(1f)) {
                // Auto vyzerá rovnako ako v hre – ten istý sprite aj kolesá.
                CarView(
                    layers = layers,
                    section = section,
                    onSection = {
                        section = it
                        selected = null
                    },
                    modifier = Modifier.weight(1.05f).fillMaxHeight()
                )
                Spacer(Modifier.width(10.dp))
                // Všetkých 14 slotov naraz – žiadne scrollovanie.
                SlotGrid(
                    engine = engine,
                    section = section,
                    selected = selected,
                    onSelect = { selected = it },
                    modifier = Modifier.weight(1f).fillMaxHeight()
                )
            }

            Spacer(Modifier.height(8.dp))
            Surface(
                color = Color(0xFF221C16),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    Modifier.padding(10.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        when {
                            selected == null -> Text(
                                "Vyber diel vpravo alebo zónu na aute",
                                color = Color(0xFF9A8F7E)
                            )
                            selectedPart == null -> {
                                Text(selected!!.displayName, color = Color(0xFFC4A35A))
                                Text("Chýba — nič namontované", color = Color(0xFFB85C38))
                            }
                            else -> {
                                Text(
                                    "${selected!!.displayName} · ${selectedPart.def.name}",
                                    color = Color(0xFFC4A35A)
                                )
                                Text(
                                    "${selectedPart.condition.displayName} · ${(selectedPart.health * 100).toInt()} %",
                                    color = healthColor(selectedPart.health),
                                    style = MaterialTheme.typography.titleLarge
                                )
                            }
                        }
                    }
                    if (selectedPart != null) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { onRepair(selected!!) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF3D4A34),
                                    contentColor = Color(0xFFE8DFD0)
                                ),
                                shape = RoundedCornerShape(6.dp)
                            ) { Text("OPRAVIŤ") }
                            Button(
                                onClick = { onUnmount(selected!!) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF4A3A28),
                                    contentColor = Color(0xFFE8DFD0)
                                ),
                                shape = RoundedCornerShape(6.dp)
                            ) { Text("DEMONTOVAŤ") }
                        }
                    }
                }
            }
        }
    }
}

/** Bočný pohľad na auto zhodný s hrou + klikacie zóny. */
@Composable
private fun CarView(
    layers: SedanLayers,
    section: CarSection?,
    onSection: (CarSection) -> Unit,
    modifier: Modifier = Modifier
) {
    val aspect = (layers.imageWidth.toFloat() / layers.imageHeight.toFloat()).coerceIn(1.8f, 4.5f)
    Box(
        modifier.background(Color(0xFF3A4A55), RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier
                .fillMaxWidth(0.94f)
                .aspectRatio(aspect)
        ) {
            Image(
                bitmap = layers.stripped,
                contentDescription = "Sedan",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
            Canvas(Modifier.fillMaxSize()) {
                val w = size.width
                val r = layers.wheelRadiusFx * w
                for (fx in floatArrayOf(layers.rearWheelFx, layers.frontWheelFx)) {
                    val c = androidx.compose.ui.geometry.Offset(w * fx, size.height * layers.wheelCenterFy)
                    drawCircle(Color(0xFF1B1E22), r, c)
                    drawCircle(Color(0xFF32373D), r * 0.66f, c)
                    drawCircle(Color(0xFF3E2723), r * 0.30f, c)
                    drawCircle(Color(0xFFCFD8DC), r * 0.12f, c)
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
    part: sk.kubis.endlessdrive.game.car.MountedPart?,
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
                    selected -> Color(0xFFC4A35A).copy(alpha = 0.30f)
                    part == null -> Color(0xFF4A2A28).copy(alpha = 0.45f)
                    else -> Color(0xFF221C16)
                },
                RoundedCornerShape(6.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            slot.displayName,
            color = Color(0xFFE8DFD0).copy(alpha = if (dimmed) 0.45f else 1f),
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1
        )
        Text(
            if (health == null) "chýba" else "${(health * 100).toInt()} %",
            color = (if (health == null) Color(0xFFB85C38) else healthColor(health))
                .copy(alpha = if (dimmed) 0.5f else 1f),
            style = MaterialTheme.typography.titleLarge
        )
        if (health != null) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(Color(0xFF3A342C), RoundedCornerShape(2.dp))
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
                if (active) Color(0xFFC4A35A).copy(alpha = 0.22f) else Color.Transparent,
                RoundedCornerShape(6.dp)
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.BottomCenter
    ) {
        Text(
            label,
            color = if (active) Color(0xFFFFF8E7) else Color(0xFFE8DFD0).copy(alpha = 0.75f),
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(bottom = 4.dp)
        )
    }
}

private enum class CarSection(val label: String, val slots: List<ComponentSlot>) {
    ZADOK(
        "Zadok",
        listOf(ComponentSlot.REAR_BUMPER, ComponentSlot.FUEL_TANK, ComponentSlot.TIRES)
    ),
    STRED(
        "Stred",
        listOf(
            ComponentSlot.DOORS,
            ComponentSlot.WINDOWS,
            ComponentSlot.ALTERNATOR,
            ComponentSlot.STARTER,
            ComponentSlot.SUSPENSION
        )
    ),
    PREDOK(
        "Predok",
        listOf(
            ComponentSlot.FRONT_BUMPER,
            ComponentSlot.HOOD,
            ComponentSlot.ENGINE,
            ComponentSlot.RADIATOR,
            ComponentSlot.BATTERY,
            ComponentSlot.BRAKES
        )
    )
}

private fun healthColor(h: Float): Color = when {
    h > 0.6f -> Color(0xFF81C784)
    h > 0.3f -> Color(0xFFFFB74D)
    else -> Color(0xFFEF9A9A)
}

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
    // Loot je obyčajný MutableList – Compose ho nesleduje, revízia vynúti prekreslenie.
    val rev = bagRevision
    PanelCard(b.type.displayName.uppercase(), rev, onClose, modifier.widthIn(max = 320.dp)) {
        if (pumpFuelL > 0.05f) {
            Column(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                Text(
                    "V stojane zostáva ${String.format("%.0f", pumpFuelL)} L",
                    color = Color(0xFFC4A35A)
                )
                Button(
                    onClick = onRefuel,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF3F5A3A),
                        contentColor = Color(0xFFE8F5E9)
                    ),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text("NATANKOVAŤ", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
        if (b.loot.isEmpty()) {
            Text("Nič tu už nie je.", color = Color(0xFF9A8F7E))
        } else {
            b.loot.forEachIndexed { i, item ->
                val def = item.def
                val title = if (def.fluid != null) {
                    "${def.name} · ${String.format("%.0f", def.fluidAmount * item.count)} L"
                } else {
                    def.name
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "$title\n${item.stateLabel}",
                        color = Color(0xFFE8DFD0),
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { onTake(i) }) { Text("ZOBRAŤ") }
                }
            }
        }
    }
}

@Composable
private fun PanelCard(
    title: String,
    /** Mení sa pri každej zmene obsahu – bez toho by Compose obsah preskočil. */
    revision: Int,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (Int) -> Unit
) {
    Surface(
        modifier = modifier.widthIn(min = 280.dp, max = 420.dp).fillMaxHeight(0.85f),
        color = Color(0xFF1A1612).copy(alpha = 0.94f),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(title, style = MaterialTheme.typography.headlineMedium, color = Color(0xFFE8DFD0))
                TextButton(onClick = onClose) { Text("✕") }
            }
            Spacer(Modifier.height(8.dp))
            Column(Modifier.verticalScroll(rememberScrollState())) { content(revision) }
        }
    }
}

@Composable
private fun RunStat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = Color(0xFF8A7F6E), style = MaterialTheme.typography.labelLarge)
        Text(value, color = Color(0xFFE8DFD0), style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
private fun PausePanel(
    ui: GameUiState,
    onResume: () -> Unit,
    onMenu: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.width(300.dp),
        color = Color(0xFF1A1612).copy(alpha = 0.95f),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("PAUZA", style = MaterialTheme.typography.headlineMedium, color = Color(0xFFE8DFD0))
            Spacer(Modifier.height(10.dp))
            Text(
                "${String.format("%.2f", ui.distanceKm)} km · ${ui.clock}",
                color = Color(0xFFC8BFAE)
            )
            Text(
                "Spálené ${String.format("%.1f", ui.fuelBurnedL)} L · ${ui.itemsLooted} vecí · ${ui.buildingsVisited} budov",
                color = Color(0xFF9A8F7E),
                style = MaterialTheme.typography.bodyLarge
            )
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = onResume) { Text("POKRAČOVAŤ") }
                Button(
                    onClick = onMenu,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3A342C))
                ) { Text("MENU") }
            }
        }
    }
}

@Composable
private fun GameOverOverlay(
    engine: GameEngine,
    onRetry: () -> Unit,
    onMenu: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.width(340.dp),
        color = Color(0xFF1A1612).copy(alpha = 0.95f),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("KONIEC JAZDY", style = MaterialTheme.typography.headlineMedium, color = Color(0xFFE8DFD0))
            Spacer(Modifier.height(8.dp))
            Text(engine.endReason?.message ?: "", color = Color(0xFFC8BFAE))
            if (engine.endDetail.isNotEmpty()) {
                Text(
                    engine.endDetail,
                    color = Color(0xFFEF9A9A),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                String.format("%.2f km", engine.distanceKm),
                style = MaterialTheme.typography.displayLarge,
                color = Color(0xFFC4A35A)
            )
            if (engine.isNewRecord) {
                Text("NOVÝ REKORD", color = Color(0xFFB85C38), style = MaterialTheme.typography.labelLarge)
            }
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                RunStat("PALIVO", String.format("%.1f L", engine.fuelBurnedL))
                RunStat("LOOT", engine.itemsLooted.toString())
                RunStat("BUDOVY", engine.buildingsVisited.toString())
                RunStat("ČAS", engine.clock)
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = onRetry) { Text("ZNOVA") }
                Button(
                    onClick = onMenu,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3A342C))
                ) { Text("MENU") }
            }
        }
    }
}
