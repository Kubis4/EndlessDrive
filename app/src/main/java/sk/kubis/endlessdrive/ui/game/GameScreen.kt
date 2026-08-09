package sk.kubis.endlessdrive.ui.game

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.ImageBitmap
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

        GameHud(ui, Modifier.align(Alignment.TopCenter).padding(top = 8.dp))

        Text(
            text = ui.message,
            color = Color(0xFFE8DFD0),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 56.dp)
                .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                .padding(horizontal = 12.dp, vertical = 4.dp)
        )

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
                carImage = assets.sedan.stripped,
                onClose = { showCar = false },
                modifier = Modifier.align(Alignment.Center)
            )
        }
        if (ui.exploring) {
            LootPanel(
                engine = engine,
                onTake = { viewModel.takeLoot(it) },
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
            .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(8.dp))
            .padding(horizontal = 14.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        HudStat("PALIVO", String.format("%.0f/%.0f L", ui.fuelL, ui.fuelCapacityL))
        HudStat("OLEJ", String.format("%.1f/%.0f L", ui.oilL, ui.oilCapacityL))
        HudStat("CHLADIACA", String.format("%.1f/%.0f L", ui.coolantL, ui.coolantCapacityL))
        HudStat("TEPLOTA", "${ui.temperature.toInt()}°C")
        HudStat("RÝCHLOSŤ", "${ui.speedKmh.toInt()} km/h")
        HudStat("VZDIALENOSŤ", String.format("%.1f km", ui.distanceKm))
    }
}

@Composable
private fun HudStat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = Color(0xFF9A8F7E), style = MaterialTheme.typography.labelLarge)
        Text(value, color = Color(0xFFE8DFD0), style = MaterialTheme.typography.titleLarge)
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
                        modifier = Modifier.weight(1f).height(72.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = accent,
                            contentColor = Color(0xFFFFF8F0)
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(choice.label, style = MaterialTheme.typography.labelLarge)
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
                                "${def.name} · ${String.format("%.0f", def.fluidAmount * stack.count)} L"
                            } else {
                                "${def.name} ×${stack.count} · ${stack.condition.displayName} · ${(stack.health * 100).toInt()}%"
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
    carImage: ImageBitmap,
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
            .fillMaxWidth(0.92f)
            .fillMaxHeight(0.88f),
        color = Color(0xFF2A241C).copy(alpha = 0.97f),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(Modifier.fillMaxSize().padding(14.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("AUTO", style = MaterialTheme.typography.headlineMedium, color = Color(0xFFE8DFD0))
                TextButton(onClick = onClose) { Text("✕") }
            }
            Text(
                "Klikni na zadok / stred / predok auta",
                color = Color(0xFF9A8F7E),
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                "Palivo ${engine.car.fuel.toInt()} L · Olej ${String.format("%.1f", engine.car.oil)} L · Chladiaca ${String.format("%.1f", engine.car.coolant)} L",
                color = Color(0xFFC8BFAE)
            )
            Spacer(Modifier.height(10.dp))

            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(0.55f)
                    .background(Color(0xFF3A4A55), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    Modifier
                        .fillMaxHeight(0.92f)
                        .aspectRatio(
                            (carImage.width.toFloat() / carImage.height.toFloat()).coerceIn(2.2f, 4.2f)
                        )
                ) {
                    Image(
                        bitmap = carImage,
                        contentDescription = "Sedan",
                        contentScale = ContentScale.FillBounds,
                        modifier = Modifier.fillMaxSize()
                    )
                    Canvas(Modifier.fillMaxSize()) {
                        val w = size.width
                        val h = size.height
                        val cy = h * 0.80f
                        val r = h * 0.20f
                        for (fx in floatArrayOf(0.18f, 0.80f)) {
                            val cx = w * fx
                            drawCircle(Color(0xFF15171A), r, androidx.compose.ui.geometry.Offset(cx, cy))
                            drawCircle(Color(0xFF2E343A), r * 0.62f, androidx.compose.ui.geometry.Offset(cx, cy))
                            drawCircle(Color(0xFFCFD8DC), r * 0.16f, androidx.compose.ui.geometry.Offset(cx, cy))
                        }
                    }
                    // Hotspoty priamo na aute
                    CarZone(
                        label = "Zadok",
                        active = section == CarSection.ZADOK,
                        onClick = {
                            section = CarSection.ZADOK
                            selected = null
                        },
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .fillMaxWidth(0.30f)
                            .fillMaxHeight(0.85f)
                    )
                    CarZone(
                        label = "Stred",
                        active = section == CarSection.STRED,
                        onClick = {
                            section = CarSection.STRED
                            selected = null
                        },
                        modifier = Modifier
                            .align(Alignment.Center)
                            .fillMaxWidth(0.34f)
                            .fillMaxHeight(0.85f)
                    )
                    CarZone(
                        label = "Predok",
                        active = section == CarSection.PREDOK,
                        onClick = {
                            section = CarSection.PREDOK
                            selected = null
                        },
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .fillMaxWidth(0.30f)
                            .fillMaxHeight(0.85f)
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            Surface(
                color = Color(0xFF221C16),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(10.dp)) {
                    when {
                        section == null -> Text("Vyber zónu na aute", color = Color(0xFF9A8F7E))
                        selected == null -> Text(
                            "Sekcia ${section!!.label} — vyber diel",
                            color = Color(0xFF9A8F7E)
                        )
                        selectedPart == null -> {
                            Text(selected!!.displayName, color = Color(0xFFC4A35A))
                            Text("Chýba — nič namontované", color = Color(0xFFB85C38))
                        }
                        else -> {
                            Text(selected!!.displayName, color = Color(0xFFC4A35A))
                            Text(
                                "${selectedPart.def.name} · ${selectedPart.condition.displayName}",
                                color = Color(0xFFE8DFD0)
                            )
                            Text(
                                "Stav: ${(selectedPart.health * 100).toInt()} %",
                                color = healthColor(selectedPart.health),
                                style = MaterialTheme.typography.titleLarge
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(6.dp))
            Column(
                Modifier
                    .weight(0.45f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                val slots = section?.slots.orEmpty()
                if (slots.isEmpty()) {
                    Text("…", color = Color(0xFF9A8F7E))
                } else {
                    slots.forEach { slot ->
                        val part = engine.car.parts[slot]
                        val isSel = selected == slot
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp)
                                .background(
                                    if (isSel) Color(0xFFC4A35A).copy(alpha = 0.22f) else Color.Transparent,
                                    RoundedCornerShape(6.dp)
                                )
                                .clickable { selected = slot }
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(slot.displayName, color = Color(0xFFE8DFD0))
                            Text(
                                if (part == null) "—" else "${(part.health * 100).toInt()} %",
                                color = if (part == null) Color(0xFFB85C38) else healthColor(part.health)
                            )
                        }
                    }
                }
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
                if (active) Color(0xFFC4A35A).copy(alpha = 0.28f) else Color.Black.copy(alpha = 0.12f),
                RoundedCornerShape(6.dp)
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.BottomCenter
    ) {
        Text(
            label,
            color = if (active) Color(0xFFFFF8E7) else Color(0xFFE8DFD0).copy(alpha = 0.85f),
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(bottom = 6.dp)
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
    onTake: (Int) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val b = engine.activeBuilding ?: return
    PanelCard(b.type.displayName.uppercase(), onClose, modifier.widthIn(max = 320.dp)) {
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
                        "$title\n${item.condition.displayName}",
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
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
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
            Column(Modifier.verticalScroll(rememberScrollState())) { content() }
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
        modifier = modifier.width(320.dp),
        color = Color(0xFF1A1612).copy(alpha = 0.95f),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("KONIEC JAZDY", style = MaterialTheme.typography.headlineMedium, color = Color(0xFFE8DFD0))
            Spacer(Modifier.height(8.dp))
            Text(engine.endReason?.message ?: "", color = Color(0xFFC8BFAE))
            Spacer(Modifier.height(12.dp))
            Text(
                String.format("%.2f km", engine.distanceKm),
                style = MaterialTheme.typography.displayLarge,
                color = Color(0xFFC4A35A)
            )
            if (engine.isNewRecord) {
                Text("NOVÝ REKORD", color = Color(0xFFB85C38), style = MaterialTheme.typography.labelLarge)
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
