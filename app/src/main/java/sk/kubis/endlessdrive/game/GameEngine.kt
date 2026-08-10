package sk.kubis.endlessdrive.game

import sk.kubis.endlessdrive.core.GameConfig
import sk.kubis.endlessdrive.core.MathX
import sk.kubis.endlessdrive.core.SeededRandom
import sk.kubis.endlessdrive.domain.model.ComponentCondition
import sk.kubis.endlessdrive.domain.model.BranchStyle
import sk.kubis.endlessdrive.domain.model.BuildingType
import sk.kubis.endlessdrive.domain.model.ComponentSlot
import sk.kubis.endlessdrive.domain.model.EndReason
import sk.kubis.endlessdrive.domain.model.FluidGrade
import sk.kubis.endlessdrive.domain.model.FluidType
import sk.kubis.endlessdrive.domain.model.GamePhase
import sk.kubis.endlessdrive.domain.model.ItemCatalog
import sk.kubis.endlessdrive.domain.model.ItemStack
import sk.kubis.endlessdrive.domain.model.RoadFeature
import sk.kubis.endlessdrive.game.car.Car
import sk.kubis.endlessdrive.game.car.EndCause
import sk.kubis.endlessdrive.game.inventory.Inventory
import sk.kubis.endlessdrive.game.world.BranchChoice
import sk.kubis.endlessdrive.game.world.RoadSegment
import sk.kubis.endlessdrive.game.world.TerrainProfile
import sk.kubis.endlessdrive.game.world.WorldBuilding
import sk.kubis.endlessdrive.game.world.WorldGenerator

enum class PrepStep(val hint: String) {
    CHECK_ENGINE("Skontroluj motor"),
    REFILL_OIL("Doplň motorový olej"),
    REFILL_COOLANT("Doplň chladiacu kvapalinu"),
    CHECK_FUEL("Skontroluj palivo"),
    START_ENGINE("Naštartuj motor"),
    DONE("Vyraz na cestu")
}

/**
 * Herný engine – bočný arcade pohyb + segmenty s križovatkami.
 */
class GameEngine(
    val seed: Long,
    val bestDistanceKm: Float
) {
    val car = Car()
    val inventory = Inventory()
    val camera = Camera2D()
    val terrain = TerrainProfile(seed)

    var segment: RoadSegment
        private set

    var phase: GamePhase = GamePhase.PREP
        private set
    var endReason: EndReason? = null
        private set
    var prepStep: PrepStep = PrepStep.CHECK_ENGINE
        private set

    var distanceM: Float = 0f
        private set
    var elapsed: Float = 0f
        private set
    var message: String = prepStep.hint
        private set

    /** Denný cyklus 0..1 (0 = polnoc). */
    var timeOfDay: Float = GameConfig.DAY_START
        private set
    var headlightsOn: Boolean = false
        private set
    private var nightWarned = false

    /** Úsek trate, v ktorom auto práve je. */
    var currentFeature: RoadFeature = RoadFeature.STRAIGHT
        private set

    private var wearWarnCooldown = 0f
    private var lastWearWarnStep = 0
    private var stuckTime = 0f
    private var fluidRescues = 0
    private var batteryRescues = 0

    /** Doplnok k dôvodu konca jazdy – napr. „zodratý špinavým olejom“. */
    var endDetail: String = ""
        private set

    // --- Štatistiky jazdy (game over prehľad) ---
    var fuelBurnedL: Float = 0f
        private set
    var itemsLooted: Int = 0
        private set
    var buildingsVisited: Int = 0
        private set
    private val visitedBuildingIds = HashSet<Long>()

    val daylight: Float get() = DayCycle.daylight(timeOfDay)
    val isNight: Boolean get() = DayCycle.isNight(timeOfDay)
    val clock: String get() = DayCycle.clock(timeOfDay)

    var throttleInput = 0f
    var brakeInput = 0f

    /** Najďalej dosiahnuté X – limituje cúvanie. */
    var maxReachedX: Float = 4f
        private set

    var activeBuilding: WorldBuilding? = null
        private set

    val junctionChoices: List<BranchChoice>
        get() = segment.choices

    private var accumulator = 0f
    private var eventCooldown = 14f
    private var screenHeightPx = 720f

    init {
        val startRng = SeededRandom(seed xor 0x57A27L)
        car.installStarterKit(startRng)

        segment = WorldGenerator.createSegment(
            segmentSeed = seed,
            style = BranchStyle.SAFE_RURAL,
            worldOrigin = 0f,
            tripDistance = 0f,
            terrain = terrain,
            isTutorial = true
        )
        car.x = 4f
        car.y = segment.heightAtWorld(car.x) + GameConfig.CAR_RIDE_HEIGHT
        maxReachedX = car.x
        camera.snapTo(car.x, car.y)
        stockStarterShed(startRng)
        message = "Opustený sedan. Prehľadaj kôlňu vedľa a sprav ho pojazdným."
    }

    /**
     * Kôlňa priamo pri aute – obsahuje presne to, čo autu chýba,
     * aby sa štart nedal prehrať do neriešiteľnej pozície.
     */
    private fun stockStarterShed(rng: SeededRandom) {
        val shed = WorldBuilding(
            id = seed xor 0x5AED1L,
            type = BuildingType.GARAGE,
            localX = car.x + 2.5f
        )
        fun canister(def: sk.kubis.endlessdrive.domain.model.ItemDef, count: Int = 1) {
            shed.loot.add(
                ItemStack(
                    defId = def.id,
                    condition = ComponentCondition.NEW,
                    health = 1f,
                    count = count,
                    purity = rng.nextFloat(0.6f, 0.95f)
                )
            )
        }
        if (car.fuel < 6f) canister(ItemCatalog.FUEL_CAN, if (car.fuel < 1f) 2 else 1)
        if (car.oil < 1.2f) canister(ItemCatalog.OIL_BOTTLE)
        if (car.coolant < 2f) canister(ItemCatalog.COOLANT_BOTTLE)
        car.missingEssentials().forEach { slot ->
            val def = when (slot) {
                ComponentSlot.BATTERY -> ItemCatalog.BATTERY
                ComponentSlot.STARTER -> ItemCatalog.STARTER
                ComponentSlot.TIRES -> ItemCatalog.TIRE
                ComponentSlot.FUEL_TANK -> ItemCatalog.FUEL_TANK
                ComponentSlot.ENGINE -> ItemCatalog.ENGINE_A
                else -> null
            }
            if (def != null) {
                shed.loot.add(ItemStack(def.id, ComponentCondition.DAMAGED, rng.nextFloat(0.4f, 0.7f)))
            }
        }
        if (!car.hasPart(ComponentSlot.RADIATOR)) {
            shed.loot.add(ItemStack(ItemCatalog.RADIATOR.id, ComponentCondition.DAMAGED, rng.nextFloat(0.4f, 0.7f)))
        }
        if (!car.hasPart(ComponentSlot.ALTERNATOR)) {
            shed.loot.add(ItemStack(ItemCatalog.ALTERNATOR.id, ComponentCondition.DAMAGED, rng.nextFloat(0.4f, 0.7f)))
        }
        // Nech sa oplatí pozrieť aj keď auto nič nepotrebuje.
        if (shed.loot.isEmpty() || rng.chance(0.5f)) {
            shed.loot.add(ItemStack(ItemCatalog.HOOD.id, ComponentCondition.USED, rng.nextFloat(0.5f, 0.8f)))
        }
        segment.buildings.add(0, shed)
    }

    val distanceKm: Float get() = distanceM / 1000f
    val isNewRecord: Boolean get() = distanceKm > bestDistanceKm && distanceKm > 0.05f
    val localX: Float get() = car.x - segment.worldOrigin
    val inJunctionZone: Boolean
        get() = localX >= segment.length - GameConfig.JUNCTION_ZONE

    fun setScreenHeight(px: Float) {
        screenHeightPx = px.coerceAtLeast(320f)
    }

    fun advance(frameDt: Float) {
        val dt = frameDt.coerceAtMost(GameConfig.MAX_FRAME_TIME)
        accumulator += dt
        var steps = 0
        while (accumulator >= GameConfig.FIXED_TIME_STEP && steps < 5) {
            fixedUpdate(GameConfig.FIXED_TIME_STEP)
            accumulator -= GameConfig.FIXED_TIME_STEP
            steps++
        }
        val slope = segment.slopeAtLocal(localX)
        // V kľude kameru prilepíme – mikro-damp by každú snímku menil Path geometriu.
        if (phase == GamePhase.DRIVING || car.speed > 0.05f) {
            camera.update(car.x, car.y, car.speed, dt, screenHeightPx, slope, car.pitch)
        } else {
            camera.snapTo(car.x, car.y)
        }
    }

    private fun fixedUpdate(dt: Float) {
        if (phase != GamePhase.GAME_OVER) {
            timeOfDay = DayCycle.advance(timeOfDay, dt)
            if (tickElectrics(dt)) return
        }
        when (phase) {
            GamePhase.PREP -> updatePrep(dt)
            GamePhase.DRIVING -> updateDriving(dt)
            GamePhase.STOPPED, GamePhase.EXPLORING, GamePhase.JUNCTION -> {
                car.speed = MathX.lerp(car.speed, 0f, dt * 5f)
                syncRide(dt)
            }
            GamePhase.GAME_OVER -> Unit
        }
    }

    /** Svetlá a batéria bežia vo všetkých fázach. Vracia true, ak jazda skončila. */
    private fun tickElectrics(dt: Float): Boolean {
        val cause = car.tickElectrics(dt, headlightsOn)
        if (cause != null) {
            endRun(cause.toEndReason())
            return true
        }
        if (isNight && !headlightsOn && !nightWarned && phase != GamePhase.PREP) {
            nightWarned = true
            message = "Stmieva sa — zapni svetlá"
        }
        if (!isNight) nightWarned = false
        return false
    }

    /** Zapne/vypne svetlomety. */
    fun toggleHeadlights(): Boolean {
        headlightsOn = !headlightsOn
        message = if (headlightsOn) {
            if (car.batteryCharge < 0.25f && !car.engineRunning) {
                "Svetlá zapnuté — pozor, slabá batéria"
            } else {
                "Svetlá zapnuté"
            }
        } else {
            "Svetlá vypnuté"
        }
        return headlightsOn
    }

    private fun syncRide(dt: Float) {
        val gy = segment.heightAtWorld(car.x)
        val slope = segment.slopeAtLocal(localX)
        car.y = MathX.damp(car.y, gy + GameConfig.CAR_RIDE_HEIGHT, 14f, dt)
        car.pitch = MathX.damp(car.pitch, kotlin.math.atan(slope), GameConfig.BODY_PITCH_SMOOTH, dt)
    }

    private fun updatePrep(dt: Float) {
        elapsed += dt
        syncRide(dt)
        val missing = car.missingEssentials()
        prepStep = when {
            missing.isNotEmpty() -> PrepStep.CHECK_ENGINE
            car.oil < 0.8f -> PrepStep.REFILL_OIL
            car.coolant < 1.2f -> PrepStep.REFILL_COOLANT
            car.fuel < 5f -> PrepStep.CHECK_FUEL
            !car.engineRunning -> PrepStep.START_ENGINE
            else -> PrepStep.DONE
        }
        if (missing.isNotEmpty()) {
            message = "Chýba: ${missing.joinToString(", ") { it.displayName.lowercase() }}"
        } else if (prepStep != PrepStep.DONE) {
            message = prepStep.hint
        } else {
            car.prepChecklistDone = true
            val worst = minOf(car.fuelPurity, car.oilPurity, car.coolantPurity)
            message = if (worst < 0.8f) {
                "V nádržiach je ${FluidGrade.of(worst).displayName.lowercase()} — hľadaj čisté kanistre."
            } else {
                "Motor beží. Stlač JAZDIŤ."
            }
        }
    }

    private fun updateDriving(dt: Float) {
        elapsed += dt
        val fuelBefore = car.fuel
        val slopeNow = segment.slopeAtLocal(localX.coerceIn(0f, segment.length - 0.5f))
        val cause = car.tickDriving(dt, throttleInput, segment.style.fuelDrainMul, slopeNow)
        fuelBurnedL += (fuelBefore - car.fuel).coerceAtLeast(0f)
        if (cause != null) {
            endRun(cause.toEndReason())
            return
        }

        // Pred križovatkou auto neprepustíme von zo segmentu.
        val maxX = segment.endWorldX - 0.5f
        val minX = maxOf(segment.worldOrigin - 2f, maxReachedX - GameConfig.REVERSE_LIMIT)
        val gy = segment.heightAtWorld(car.x.coerceIn(minX, maxX))
        val slope = segment.slopeAtLocal(localX.coerceIn(0f, segment.length - 0.5f))
        val bumpiness = segment.bumpinessAtLocal(localX.coerceIn(0f, segment.length - 0.5f))
        car.applyDrive(dt, throttleInput, brakeInput, gy, slope, bumpiness)
        applyFeatureEffects(dt)
        if (car.x > maxX) {
            car.x = maxX
            if (car.speed > 0f) car.speed = 0f
        }
        if (car.x < minX) {
            car.x = minX
            if (car.speed < 0f) car.speed = 0f
        }
        if (car.x > maxReachedX) maxReachedX = car.x

        // Bez svetiel v noci sa dá len plaziť.
        if (isNight && !headlightsOn && car.speed > GameConfig.NIGHT_BLIND_SPEED) {
            car.speed = GameConfig.NIGHT_BLIND_SPEED
        }

        distanceM = maxReachedX.coerceAtLeast(0f)
        warnAboutEngineWear(dt)

        if (inJunctionZone) {
            if (car.speed <= GameConfig.STOP_SPEED * 1.5f) {
                phase = GamePhase.JUNCTION
                car.speed = 0f
                message = "Križovatka — vyber cestu"
                return
            } else {
                message = "Križovatka vpredu — spomaľ a vyber smer"
            }
        } else {
            val near = buildingNear()
            when {
                near != null && car.speed < GameConfig.STOP_SPEED * 2.5f ->
                    message = "${near.type.displayName} — zastav a preskúmaj"
                car.temperature > 100f ->
                    message = "Motor sa prehrieva!"
                car.fuelRatio < 0.15f ->
                    message = "Málo paliva"
                message == "Motor sa prehrieva!" || message.contains("zahrieva") ->
                    message = ""
            }
        }

        eventCooldown -= dt
        if (eventCooldown <= 0f) {
            maybeRandomEvent()
            eventCooldown = MathX.lerp(16f, 36f, MathX.hash01(seed.toInt(), distanceM.toInt()))
        }
    }

    /**
     * Motor sa nikdy nesmie zničiť potichu – hráč dostane hlášku o príčine
     * aj varovanie, keď stav klesne pod kritickú hranicu.
     */
    private fun warnAboutEngineWear(dt: Float) {
        wearWarnCooldown -= dt
        val health = car.parts[ComponentSlot.ENGINE]?.health ?: return
        val cause = car.wearCause
        val step = when {
            health <= 0.12f -> 3
            health <= 0.25f -> 2
            health <= 0.45f -> 1
            else -> 0
        }
        if (step > lastWearWarnStep && step > 0) {
            lastWearWarnStep = step
            wearWarnCooldown = 6f
            message = when {
                step >= 3 -> "MOTOR NA POKRAJI: ${(health * 100).toInt()} % — ${cause?.warning ?: "oprav ho"}"
                step == 2 -> "Motor je vážne opotrebený (${(health * 100).toInt()} %)"
                else -> cause?.warning ?: "Motor sa opotrebúva (${(health * 100).toInt()} %)"
            }
            return
        }
        if (health > 0.5f) lastWearWarnStep = 0
        // Aj pri zdravom motore hlásime, ak ho niečo aktívne zožiera.
        if (cause != null && car.wearRate > 0.002f && wearWarnCooldown <= 0f) {
            wearWarnCooldown = 12f
            message = cause.warning
        }
    }

    /**
     * Úsek trate obmedzuje rýchlosť a rozbitá cesta trhá pneumatiky.
     * Pri vstupe do nového úseku hráča upozorníme.
     */
    private fun applyFeatureEffects(dt: Float) {
        val feature = segment.featureAtWorld(car.x)
        if (feature != currentFeature) {
            currentFeature = feature
            if (feature.warning.isNotEmpty()) message = feature.warning
        }
        // Keď hráč drží plyn a auto sa nehýbe, musí sa dozvedieť prečo.
        if (throttleInput > 0.6f && car.speed < 1.2f) {
            stuckTime += dt
            if (stuckTime > 1.5f) {
                stuckTime = 0f
                val slope = segment.slopeAtLocal(localX.coerceIn(0f, segment.length - 0.5f))
                message = when {
                    brakeInput > 0.2f -> "Pustíš brzdu?"
                    !car.engineRunning -> "Motor nebeží — naštartuj"
                    slope > 0.18f -> "Strmé stúpanie — vycúvaj a nabehni to s rozbehom"
                    car.tireGrip < 0.32f -> "Pneumatiky preklzávajú — treba lepšie"
                    else -> "Motor ledva ťahá (${(car.powerHp).toInt()} HP)"
                }
            }
        } else {
            stuckTime = 0f
        }

        val cap = feature.speedCap
        if (cap > 0f && car.speed > cap) {
            // Nedá sa cez to prehnať – auto samo spomalí a otrasie sa.
            car.speed = MathX.damp(car.speed, cap, 2.2f, dt)
        }
        if (feature.roughness > 0.5f && car.speed > cap * 0.6f) {
            val over = ((car.speed - cap * 0.6f) / GameConfig.MAX_SPEED).coerceIn(0f, 1f)
            car.parts[ComponentSlot.TIRES]?.let {
                it.health = (it.health - GameConfig.TIRE_WEAR_BROKEN * over * dt).coerceAtLeast(0.05f)
            }
            car.parts[ComponentSlot.SUSPENSION]?.let {
                it.health = (it.health - GameConfig.TIRE_WEAR_BROKEN * 0.6f * over * dt).coerceAtLeast(0.05f)
            }
        }
    }

    private fun maybeRandomEvent() {
        val roll = MathX.hash01((seed xor distanceM.toRawBits().toLong()).toInt(), elapsed.toInt())
        when {
            roll < 0.08f && car.parts[ComponentSlot.TIRES] != null -> {
                car.parts[ComponentSlot.TIRES]!!.health =
                    (car.parts[ComponentSlot.TIRES]!!.health - 0.12f).coerceAtLeast(0.1f)
                message = "Rozbitá cesta poškodila pneumatiky."
            }
            roll < 0.14f -> message = "Zvláštny rádiový šum…"
            roll < 0.20f -> message = "Stopy vedú do poľa. Nič viac."
        }
    }

    fun chooseBranch(choiceId: Int): Boolean {
        if (phase != GamePhase.JUNCTION) return false
        val choice = segment.choices.find { it.id == choiceId } ?: return false
        val origin = segment.endWorldX
        segment = WorldGenerator.createSegment(
            plan = choice.plan,
            worldOrigin = origin,
            tripDistance = distanceM,
            terrain = terrain,
            isTutorial = false
        )
        currentFeature = segment.featureAtWorld(origin + 1.5f)
        car.x = origin + 1.5f
        car.y = segment.heightAtWorld(car.x) + GameConfig.CAR_RIDE_HEIGHT
        car.speed = 0f
        maxReachedX = car.x
        activeBuilding = null
        phase = GamePhase.DRIVING
        message = "Odbočil si: ${choice.label}"
        return true
    }

    fun requestStop(): Boolean {
        if (phase != GamePhase.DRIVING) return false
        if (car.speed > GameConfig.STOP_SPEED * 3f) {
            message = "Najprv spomaľ"
            return false
        }
        car.speed = 0f
        if (inJunctionZone) {
            phase = GamePhase.JUNCTION
            message = "Križovatka — vyber cestu"
        } else {
            phase = GamePhase.STOPPED
            message = "Zastavené"
        }
        return true
    }

    fun resumeDriving(): Boolean {
        if (phase != GamePhase.STOPPED && phase != GamePhase.EXPLORING && phase != GamePhase.PREP) {
            return false
        }
        if (!car.engineRunning) {
            message = "Naštartuj motor"
            return false
        }
        if (inJunctionZone) {
            phase = GamePhase.JUNCTION
            message = "Najprv vyber cestu"
            return false
        }
        activeBuilding = null
        prepStep = PrepStep.DONE
        car.prepChecklistDone = true
        phase = GamePhase.DRIVING
        message = "Jazda pokračuje"
        return true
    }

    fun buildingNear(): WorldBuilding? {
        var best: WorldBuilding? = null
        var bestDist = GameConfig.BUILDING_INTERACT_RANGE
        for (b in segment.buildings) {
            val wx = segment.worldOrigin + b.localX
            val d = kotlin.math.abs(wx - car.x)
            if (d < bestDist) {
                bestDist = d
                best = b
            }
        }
        return best
    }

    fun enterNearestBuilding(): Boolean {
        if (phase == GamePhase.JUNCTION) {
            message = "Najprv vyber cestu"
            return false
        }
        // PREP je tiež „stojíme pri aute“ – bez toho by sa hráč nedostal
        // ani do kôlne hneď vedľa, z ktorej má vrak sprevádzkovať.
        if (phase != GamePhase.STOPPED && phase != GamePhase.DRIVING && phase != GamePhase.PREP) {
            return false
        }
        if (phase == GamePhase.DRIVING && car.speed > GameConfig.STOP_SPEED) {
            message = "Zastav pred budovou"
            return false
        }
        car.speed = 0f
        val b = buildingNear() ?: run {
            message = "Žiadna budova nablízku"
            return false
        }
        activeBuilding = b
        phase = GamePhase.EXPLORING
        if (visitedBuildingIds.add(b.id)) buildingsVisited++
        message = "Preskúmavaš: ${b.type.displayName}"
        return true
    }

    /** Natankuje priamo z pumpy na benzínke (má obmedzenú zásobu). */
    fun refuelFromPump(): Boolean {
        val b = activeBuilding ?: return false
        if (b.pumpFuelL <= 0.05f) {
            message = if (b.type == BuildingType.GAS_STATION) {
                "Pumpa je prázdna"
            } else {
                "Tu nie je pumpa"
            }
            return false
        }
        val room = car.fuelCapacity - car.fuel
        if (room <= 0.05f) {
            message = "Nádrž je plná"
            return false
        }
        val moved = car.refill(FluidType.FUEL, kotlin.math.min(room, b.pumpFuelL), b.pumpPurity)
        b.pumpFuelL = (b.pumpFuelL - moved).coerceAtLeast(0f)
        message = "Natankované +${String.format("%.1f", moved)} L · " +
            "${FluidGrade.of(b.pumpPurity).displayName.lowercase()} (v pumpe ${String.format("%.0f", b.pumpFuelL)} L)"
        return true
    }

    fun leaveBuilding() {
        if (phase != GamePhase.EXPLORING) return
        activeBuilding = null
        phase = if (inJunctionZone) GamePhase.JUNCTION else GamePhase.STOPPED
        message = "Späť pri aute"
    }

    fun takeLoot(index: Int): Boolean {
        val b = activeBuilding ?: return false
        if (index !in b.loot.indices) return false
        val item = b.loot[index]
        if (!inventory.canFit(item)) {
            message = "Inventár plný / príliš ťažký"
            return false
        }
        inventory.add(item)
        b.loot.removeAt(index)
        itemsLooted += item.count
        message = "Zobrané: ${item.def.name}"
        return true
    }

    /** Demontuje diel z auta späť do inventára. */
    fun unmountSlot(slot: ComponentSlot): Boolean {
        val part = car.parts[slot] ?: run {
            message = "${slot.displayName}: nič nie je namontované"
            return false
        }
        if (car.engineRunning && slot in RUNNING_CRITICAL) {
            message = "Najprv vypni motor"
            return false
        }
        val stack = ItemStack(part.defId, part.condition, part.health)
        if (!inventory.canFit(stack)) {
            message = "Inventár plný / príliš ťažký"
            return false
        }
        car.parts.remove(slot)
        inventory.add(stack)
        message = "Demontované: ${slot.displayName}"
        return true
    }

    fun discardInventoryItem(index: Int): Boolean {
        val stack = inventory.removeAt(index) ?: return false
        message = "Vyhodené: ${stack.def.name}"
        return true
    }

    fun useInventoryItem(index: Int): Boolean {
        val stack = inventory.get(index) ?: return false
        val def = stack.def
        if (def.fluid != null) {
            val room = when (def.fluid) {
                FluidType.FUEL -> car.fuelCapacity - car.fuel
                FluidType.OIL -> car.oilCapacity - car.oil
                FluidType.COOLANT -> car.coolantCapacity - car.coolant
                else -> 0f
            }
            if (room <= 0.05f) {
                message = "Už je plné (${def.fluid.displayName})"
                return false
            }
            val used = car.refill(def.fluid, def.fluidAmount, stack.purity)
            if (used <= 0f) {
                message = "Už je plné (${def.fluid.displayName})"
                return false
            }
            stack.count--
            if (stack.count <= 0) inventory.removeAt(index)
            message = if (stack.grade == FluidGrade.PURE) {
                "Doplnené +${String.format("%.1f", used)} L ${def.fluid.displayName}"
            } else {
                "Doplnené +${String.format("%.1f", used)} L — ${stack.grade.displayName.lowercase()}!"
            }
            return true
        }
        if (def.mountsTo != null) {
            val prev = car.mount(def.mountsTo, stack)
            inventory.removeAt(index)
            if (prev != null) inventory.add(ItemStack(prev.defId, prev.condition, prev.health))
            message = "Namontované: ${def.name}"
            return true
        }
        message = "Toto sa nedá použiť"
        return false
    }

    fun repairSlot(slot: ComponentSlot): Boolean {
        val part = car.parts[slot] ?: return false
        if (part.health >= 0.98f) {
            message = "Už je v poriadku"
            return false
        }
        val oilIdx = inventory.slots.indexOfFirst { it?.defId == ItemCatalog.OIL_BOTTLE.id }
        if (oilIdx >= 0) {
            val s = inventory.slots[oilIdx]!!
            s.count--
            if (s.count <= 0) inventory.removeAt(oilIdx)
            car.repair(slot, 0.35f)
            message = "Opravené: ${slot.displayName}"
        } else {
            car.repair(slot, 0.12f)
            message = "Provizórna oprava: ${slot.displayName}"
        }
        return true
    }

    fun tryStartEngine(): Boolean {
        if (car.engineRunning) {
            message = "Motor už beží"
            return true
        }
        return if (car.tryStart()) {
            prepStep = PrepStep.DONE
            car.prepChecklistDone = true
            activeBuilding = null
            phase = GamePhase.DRIVING
            // V noci si svetlá zapneme sami – bez nich sa nedá jazdiť.
            if (isNight && !headlightsOn) headlightsOn = true
            message = "Motor beží — podrž plyn"
            true
        } else {
            val missing = car.missingEssentials()
            // Zablokovaný štart nesmie byť slepá ulička – čo chýba, nájde sa nablízku.
            val rescued = offerRescue(missing)
            message = when {
                rescued != null -> rescued
                missing.isNotEmpty() ->
                    "Chýba: ${missing.joinToString(", ") { it.displayName.lowercase() }}"
                car.fuel < 0.5f -> "Málo paliva"
                car.oil < 0.3f -> "Málo oleja"
                car.coolant < 0.3f -> "Málo chladiacej kvapaliny"
                car.batteryCharge < 0.2f -> "Slabá batéria"
                else -> "Motor sa nepodarilo naštartovať"
            }
            false
        }
    }

    fun stopEngine() {
        car.stopEngine()
        message = "Motor vypnutý"
    }

    /**
     * Záchranná sieť: keď sa auto nedá naštartovať a chýbajúci diel či kvapalina
     * nie je ani v batohu, ani v budove nablízku, objaví sa pri ceste vrak,
     * z ktorého sa to dá vybrať. Hra nikdy neskončí patom.
     */
    private fun offerRescue(missing: List<ComponentSlot>): String? {
        val needParts = missing.filter { slot ->
            inventory.slots.none { it?.def?.mountsTo == slot }
        }.toMutableList()
        // Vybitá batéria bez alternátora je rovnaká slepá ulička ako chýbajúci diel.
        if (car.batteryCharge < 0.2f &&
            batteryRescues < MAX_FLUID_RESCUES &&
            inventory.slots.none { it?.def?.mountsTo == ComponentSlot.BATTERY } &&
            ComponentSlot.BATTERY !in needParts
        ) {
            needParts += ComponentSlot.BATTERY
            batteryRescues++
        }
        val needFluid = when {
            car.fuel < 0.5f -> FluidType.FUEL
            car.oil < 0.3f -> FluidType.OIL
            car.coolant < 0.3f -> FluidType.COOLANT
            else -> null
        }?.takeIf { fluid ->
            inventory.slots.none { it?.def?.fluid == fluid } && fluidRescues < MAX_FLUID_RESCUES
        }
        if (needParts.isEmpty() && needFluid == null) return null

        val stash = buildingNear() ?: WorldBuilding(
            id = seed xor 0x412CL xor segment.buildings.size.toLong(),
            type = BuildingType.GARAGE,
            localX = (car.x - segment.worldOrigin) + 3f
        ).also { segment.buildings.add(it) }

        val rng = SeededRandom(seed xor stash.id xor distanceM.toRawBits().toLong())
        val added = mutableListOf<String>()
        needParts.forEach { slot ->
            if (stash.loot.any { it.def.mountsTo == slot }) return@forEach
            val def = when (slot) {
                ComponentSlot.ENGINE -> ItemCatalog.ENGINE_A
                ComponentSlot.FUEL_TANK -> ItemCatalog.FUEL_TANK
                ComponentSlot.TIRES -> ItemCatalog.TIRE
                ComponentSlot.BATTERY -> ItemCatalog.BATTERY
                ComponentSlot.STARTER -> ItemCatalog.STARTER
                else -> null
            } ?: return@forEach
            stash.loot.add(ItemStack(def.id, ComponentCondition.DAMAGED, rng.nextFloat(0.35f, 0.65f)))
            added += def.name.lowercase()
        }
        if (needFluid != null && stash.loot.none { it.def.fluid == needFluid }) {
            val def = when (needFluid) {
                FluidType.FUEL -> ItemCatalog.FUEL_CAN
                FluidType.OIL -> ItemCatalog.OIL_BOTTLE
                else -> ItemCatalog.COOLANT_BOTTLE
            }
            stash.loot.add(
                ItemStack(
                    defId = def.id,
                    condition = ComponentCondition.NEW,
                    health = 1f,
                    count = 1,
                    purity = rng.nextFloat(0.5f, 0.85f)
                )
            )
            fluidRescues++
            added += def.name.lowercase()
        }
        if (added.isEmpty()) return null
        return "Pri ceste stojí vrak — je v ňom ${added.joinToString(", ")}. Pozri BUDOVA."
    }

    fun endRun(reason: EndReason = EndReason.MANUAL) {
        phase = GamePhase.GAME_OVER
        endReason = reason
        endDetail = when (reason) {
            EndReason.ENGINE_DESTROYED, EndReason.OVERHEAT ->
                car.wearCause?.let { "Príčina: ${it.fatal}." } ?: ""
            EndReason.OUT_OF_FUEL -> "Nádrž je na dne."
            EndReason.BATTERY_DEAD -> "Svetlá vybili batériu a motor sa už nerozbehol."
            EndReason.MANUAL -> ""
        }
        car.stopEngine()
        car.speed = 0f
        message = reason.message
    }

    companion object {
        /** Koľkokrát za jazdu môže vrak pri ceste doplniť chýbajúcu kvapalinu. */
        private const val MAX_FLUID_RESCUES = 2

        /** Diely, ktoré sa nedajú vybrať za chodu motora. */
        private val RUNNING_CRITICAL = setOf(
            ComponentSlot.ENGINE,
            ComponentSlot.FUEL_TANK,
            ComponentSlot.RADIATOR,
            ComponentSlot.BATTERY,
            ComponentSlot.ALTERNATOR,
            ComponentSlot.STARTER
        )
    }

    private fun EndCause.toEndReason(): EndReason = when (this) {
        EndCause.OUT_OF_FUEL -> EndReason.OUT_OF_FUEL
        EndCause.ENGINE_DESTROYED -> EndReason.ENGINE_DESTROYED
        EndCause.OVERHEAT -> EndReason.OVERHEAT
        EndCause.BATTERY_DEAD -> EndReason.BATTERY_DEAD
    }
}
