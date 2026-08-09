package sk.kubis.endlessdrive.game

import sk.kubis.endlessdrive.core.GameConfig
import sk.kubis.endlessdrive.core.MathX
import sk.kubis.endlessdrive.domain.model.BranchStyle
import sk.kubis.endlessdrive.domain.model.ComponentSlot
import sk.kubis.endlessdrive.domain.model.EndReason
import sk.kubis.endlessdrive.domain.model.GamePhase
import sk.kubis.endlessdrive.domain.model.ItemCatalog
import sk.kubis.endlessdrive.domain.model.ItemStack
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
        car.installStarterKit()
        inventory.add(ItemStack(ItemCatalog.OIL_BOTTLE.id, count = 1))
        inventory.add(ItemStack(ItemCatalog.COOLANT_BOTTLE.id, count = 1))
        inventory.add(ItemStack(ItemCatalog.FUEL_CAN.id, count = 1))

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
        message = "Opustený sedan bez dverí. Naštartuj a vyraz."
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

    private fun syncRide(dt: Float) {
        val gy = segment.heightAtWorld(car.x)
        val slope = segment.slopeAtLocal(localX)
        car.y = MathX.damp(car.y, gy + GameConfig.CAR_RIDE_HEIGHT, 14f, dt)
        car.pitch = MathX.damp(car.pitch, kotlin.math.atan(slope), GameConfig.BODY_PITCH_SMOOTH, dt)
    }

    private fun updatePrep(dt: Float) {
        elapsed += dt
        syncRide(dt)
        prepStep = when {
            !car.parts.containsKey(ComponentSlot.ENGINE) -> PrepStep.CHECK_ENGINE
            car.oil < 0.8f -> PrepStep.REFILL_OIL
            car.coolant < 1.2f -> PrepStep.REFILL_COOLANT
            car.fuel < 5f -> PrepStep.CHECK_FUEL
            !car.engineRunning -> PrepStep.START_ENGINE
            else -> PrepStep.DONE
        }
        if (prepStep != PrepStep.DONE) {
            message = prepStep.hint
        } else {
            car.prepChecklistDone = true
            message = "Motor beží. Stlač JAZDIŤ."
        }
    }

    private fun updateDriving(dt: Float) {
        elapsed += dt
        val cause = car.tickDriving(dt, throttleInput, segment.style.fuelDrainMul)
        if (cause != null) {
            endRun(cause.toEndReason())
            return
        }

        // Pred križovatkou auto neprepustíme von zo segmentu.
        val maxX = segment.endWorldX - 0.5f
        val minX = maxOf(segment.worldOrigin - 2f, maxReachedX - GameConfig.REVERSE_LIMIT)
        val gy = segment.heightAtWorld(car.x.coerceIn(minX, maxX))
        val slope = segment.slopeAtLocal(localX.coerceIn(0f, segment.length - 0.5f))
        car.applyDrive(dt, throttleInput, brakeInput, gy, slope, segment.style.bumpiness)
        if (car.x > maxX) {
            car.x = maxX
            if (car.speed > 0f) car.speed = 0f
        }
        if (car.x < minX) {
            car.x = minX
            if (car.speed < 0f) car.speed = 0f
        }
        if (car.x > maxReachedX) maxReachedX = car.x

        distanceM = maxReachedX.coerceAtLeast(0f)

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
            segmentSeed = choice.segmentSeed,
            style = choice.style,
            worldOrigin = origin,
            tripDistance = distanceM,
            terrain = terrain,
            isTutorial = false
        )
        car.x = origin + 1.5f
        car.y = segment.heightAtWorld(car.x) + GameConfig.CAR_RIDE_HEIGHT
        car.speed = 0f
        maxReachedX = car.x
        activeBuilding = null
        phase = GamePhase.DRIVING
        message = "Cesta: ${choice.label} — ${choice.hint}"
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
        if (phase != GamePhase.STOPPED && phase != GamePhase.DRIVING) return false
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
        message = "Preskúmavaš: ${b.type.displayName}"
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
        message = "Zobrané: ${item.def.name}"
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
                sk.kubis.endlessdrive.domain.model.FluidType.FUEL -> car.fuelCapacity - car.fuel
                sk.kubis.endlessdrive.domain.model.FluidType.OIL -> car.oilCapacity - car.oil
                sk.kubis.endlessdrive.domain.model.FluidType.COOLANT -> car.coolantCapacity - car.coolant
                else -> 0f
            }
            if (room <= 0.05f) {
                message = "Už je plné (${def.fluid.displayName})"
                return false
            }
            val used = car.refill(def.fluid, def.fluidAmount)
            if (used <= 0f) {
                message = "Už je plné (${def.fluid.displayName})"
                return false
            }
            stack.count--
            if (stack.count <= 0) inventory.removeAt(index)
            message = "Doplnené +${String.format("%.1f", used)} L ${def.fluid.displayName}"
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
            message = "Motor beží — podrž plyn"
            true
        } else {
            message = when {
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

    fun endRun(reason: EndReason = EndReason.MANUAL) {
        phase = GamePhase.GAME_OVER
        endReason = reason
        car.stopEngine()
        car.speed = 0f
        message = reason.message
    }

    private fun EndCause.toEndReason(): EndReason = when (this) {
        EndCause.OUT_OF_FUEL -> EndReason.OUT_OF_FUEL
        EndCause.ENGINE_DESTROYED -> EndReason.ENGINE_DESTROYED
        EndCause.OVERHEAT -> EndReason.OVERHEAT
        EndCause.BATTERY_DEAD -> EndReason.BATTERY_DEAD
    }
}
