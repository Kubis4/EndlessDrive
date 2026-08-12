package sk.kubis.endlessdrive.game

import sk.kubis.endlessdrive.core.GameConfig
import sk.kubis.endlessdrive.core.MathX
import sk.kubis.endlessdrive.core.SeededRandom
import sk.kubis.endlessdrive.domain.model.ComponentCondition
import sk.kubis.endlessdrive.domain.model.BranchStyle
import sk.kubis.endlessdrive.domain.model.BuildingType
import sk.kubis.endlessdrive.domain.model.ComponentSlot
import sk.kubis.endlessdrive.domain.model.TIRE_SLOTS
import sk.kubis.endlessdrive.domain.model.EndReason
import sk.kubis.endlessdrive.domain.model.FluidGrade
import sk.kubis.endlessdrive.domain.model.FluidType
import sk.kubis.endlessdrive.domain.model.GamePhase
import sk.kubis.endlessdrive.domain.model.ItemCatalog
import sk.kubis.endlessdrive.domain.model.ItemStack
import sk.kubis.endlessdrive.domain.model.RoadFeature
import sk.kubis.endlessdrive.domain.model.RoadSurface
import sk.kubis.endlessdrive.domain.model.SedanSpec
import sk.kubis.endlessdrive.game.car.Car
import sk.kubis.endlessdrive.game.car.EndCause
import sk.kubis.endlessdrive.game.event.ActiveEvent
import sk.kubis.endlessdrive.game.event.RoadEvent
import sk.kubis.endlessdrive.game.inventory.Inventory
import sk.kubis.endlessdrive.game.save.BuildingState
import sk.kubis.endlessdrive.game.save.CarState
import sk.kubis.endlessdrive.game.save.EventState
import sk.kubis.endlessdrive.game.save.PartState
import sk.kubis.endlessdrive.game.save.RunSnapshot
import sk.kubis.endlessdrive.game.save.SegmentState
import sk.kubis.endlessdrive.game.save.StackState
import sk.kubis.endlessdrive.game.world.BranchChoice
import sk.kubis.endlessdrive.game.world.RoadSegment
import sk.kubis.endlessdrive.game.world.SurfacePatch
import sk.kubis.endlessdrive.game.world.SegmentPlan
import sk.kubis.endlessdrive.game.world.TerrainProfile
import sk.kubis.endlessdrive.game.world.WorldBuilding
import sk.kubis.endlessdrive.game.world.WorldGenerator

enum class PrepStep(val hint: String) {
    CHECK_ENGINE("Check the engine"),
    REFILL_OIL("Top up the engine oil"),
    REFILL_COOLANT("Top up the coolant"),
    CHECK_FUEL("Check the fuel"),
    START_ENGINE("Start the engine"),
    DONE("Hit the road")
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
    /**
     * Hláška pre hráča. Rovnaký text sa nikdy „neobnoví“ – čas beží ďalej,
     * takže sa po [MESSAGE_TTL] sekundách sama stratí a neopakuje sa.
     */
    var message: String = prepStep.hint
        private set(value) {
            if (field == value) return
            field = value
            messageAge = 0f
        }
    var messageAge: Float = 0f
        private set

    /** true, kým sa má hláška ešte zobrazovať. */
    val messageFresh: Boolean get() = message.isNotBlank() && messageAge < MESSAGE_TTL

    /** Prečo sa auto nevie pohnúť dopredu (null = ide). */
    var blockedReason: String? = null
        private set

    /** Denný cyklus 0..1 (0 = polnoc). */
    var timeOfDay: Float = GameConfig.DAY_START
        private set
    var headlightsOn: Boolean = false
        private set
    private var nightWarned = false
    private var altWarned = false

    /** Úsek trate, v ktorom auto práve je. */
    var currentFeature: RoadFeature = RoadFeature.STRAIGHT
        private set

    private var wearWarnCooldown = 0f
    private var lastWearWarnStep = 0
    private var stuckTime = 0f
    private var fluidRescues = 0
    /** Vrak zo záchrannej siete – aby nevznikal nový pri každom pokuse. */
    private var rescueStashId: Long? = null
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

    /** Vzdialenosť, pri ktorej segment vznikol – križovatky z nej vychádzajú. */
    var segmentTripDistance: Float = 0f
        private set

    private val events = mutableListOf<ActiveEvent>()

    private var accumulator = 0f
    private var eventCooldown = 14f
    private var screenHeightPx = 720f

    init {
        val startRng = SeededRandom(seed xor 0x57A27L)
        car.installStarterKit(startRng)
        // Kanister vody – núdzovka do chladiča, ale motor po nej ide horúci.
        inventory.add(
            ItemStack(
                defId = ItemCatalog.WATER.id,
                condition = ComponentCondition.NEW,
                health = 1f,
                count = 2,
                purity = 0.05f
            )
        )

        segment = WorldGenerator.createSegment(
            segmentSeed = seed,
            style = BranchStyle.SAFE_RURAL,
            worldOrigin = 0f,
            tripDistance = 0f,
            terrain = terrain,
            isTutorial = true
        )
        car.x = 4f
        car.snapToGround(segment.heightAtWorld(car.x))
        maxReachedX = car.x
        camera.snapTo(car.x, car.y)
        stockStarterShed(startRng)
        message = "An abandoned sedan. Search the shed beside it and get the car running."
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
                ComponentSlot.TIRE_FRONT, ComponentSlot.TIRE_REAR -> ItemCatalog.TIRE_POOR
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

    /** Koľko metrov zostáva k rázcestiu. */
    val junctionDistanceM: Float get() = (segment.endWorldX - car.x).coerceAtLeast(0f)

    /** true, kým sa dá vetva vybrať za jazdy (posledných pár sto metrov). */
    val approachingJunction: Boolean
        get() = phase == GamePhase.DRIVING &&
            junctionDistanceM <= GameConfig.JUNCTION_APPROACH &&
            segment.choices.isNotEmpty()

    /** 0 = normálne počasie, 1 = poriadny mráz. Riadi ho zasnežená vetva. */
    val cold: Float
        get() {
            if (!segment.paving.winter) return 0f
            // V noci mrzne viac než cez deň.
            return 0.75f + 0.25f * (1f - daylight)
        }

    /** true = jazdí sa v zime (sneh pod kolesami). */
    val isWinter: Boolean get() = segment.paving.winter

    /** Povrch pod kolesami – asfalt, bahno, piesok, voda, štrk. */
    var currentSurface: RoadSurface = RoadSurface.ASPHALT
        private set

    /** Naplavenina, ku ktorej sa auto blíži (do 60 m) – na varovanie v HUD. */
    val patchAhead: SurfacePatch?
        get() = if (phase != GamePhase.DRIVING) null
        else segment.patchAheadOfLocal(localX, 60f)

    /** Vetva, do ktorej sa auto zaradí na rázcestí (null = ešte nevybraté). */
    var pendingChoiceId: Int? = null
        private set

    /**
     * Zastavenie pri rázcestí otvára panel len vtedy, keď hráč ešte nevolil.
     * Kto si vetvu vybral za jazdy, môže pri odbočke normálne lootovať.
     */
    private val needsJunctionCall: Boolean
        get() = inJunctionZone && pendingChoiceId == null && segment.choices.isNotEmpty()

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
        // Trasenie podľa hrboľatosti úseku a rýchlosti.
        camera.setShake(
            if (phase == GamePhase.DRIVING) {
                val road = segment.bumpinessAtLocal(localX.coerceIn(0f, segment.length - 0.5f)) *
                    (kotlin.math.abs(car.speed) / GameConfig.MAX_SPEED).coerceIn(0f, 1f)
                // Pretáčajúce sa kolesá autom myknú aj keď stojí na mieste.
                maxOf(road, car.wheelSlip * 0.55f)
            } else 0f
        )
        // V kľude kameru prilepíme – mikro-damp by každú snímku menil Path geometriu.
        if (phase == GamePhase.DRIVING || car.speed > 0.05f) {
            camera.update(car.x, car.y, car.speed, dt, screenHeightPx, slope, car.pitch)
        } else {
            camera.snapTo(car.x, car.y)
        }
    }

    private fun fixedUpdate(dt: Float) {
        messageAge += dt
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
        if (phase != GamePhase.DRIVING) updateBlockedReason(dt)
    }

    /** Svetlá a batéria bežia vo všetkých fázach. Vracia true, ak jazda skončila. */
    private fun tickElectrics(dt: Float): Boolean {
        val cause = car.tickElectrics(
            dt, headlightsOn, charging = !hasEvent(RoadEvent.BELT_SNAPPED), cold = cold
        )
        if (cause != null) return stallOrEnd(cause)
        if (isNight && !headlightsOn && !nightWarned && phase != GamePhase.PREP) {
            nightWarned = true
            message = "Getting dark — switch the headlights on"
        }
        if (!isNight) nightWarned = false
        if (car.engineRunning &&
            !car.hasPart(ComponentSlot.ALTERNATOR) &&
            !altWarned &&
            phase == GamePhase.DRIVING
        ) {
            altWarned = true
            message = "No alternator fitted — battery won't recharge"
        }
        return false
    }

    /** Zapne/vypne svetlomety. */
    fun toggleHeadlights(): Boolean {
        headlightsOn = !headlightsOn
        message = if (headlightsOn) {
            when {
                !car.hasPart(ComponentSlot.ALTERNATOR) ->
                    "Headlights on — no alternator, battery will drain"
                car.batteryCharge < 0.25f && !car.engineRunning ->
                    "Headlights on — careful, the battery is weak"
                else -> "Headlights on"
            }
        } else {
            "Headlights off"
        }
        return headlightsOn
    }

    private fun syncRide(dt: Float) {
        val gy = segment.heightAtWorld(car.x)
        val slope = segment.slopeAtLocal(localX)
        car.snapToGround(gy, slope)
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
            message = "Missing ${missing.joinToString(", ") { it.displayName.lowercase() }}"
        } else if (prepStep != PrepStep.DONE) {
            message = prepStep.hint
        } else {
            car.prepChecklistDone = true
            val worst = minOf(car.fuelPurity, car.oilPurity, car.coolantPurity)
            message = if (worst < 0.8f) {
                "The tanks hold ${FluidGrade.of(worst).displayName.lowercase()} — look for clean cans."
            } else {
                "Engine running. Press DRIVE."
            }
        }
    }

    private fun updateDriving(dt: Float) {
        elapsed += dt
        val fuelBefore = car.fuel
        val slopeNow = segment.slopeAtLocal(localX.coerceIn(0f, segment.length - 0.5f))
        tickEvents(dt)
        val cause = car.tickDriving(
            dt, throttleInput, segment.style.fuelDrainMul * eventDrainMul, slopeNow, cold
        )
        fuelBurnedL += (fuelBefore - car.fuel).coerceAtLeast(0f)
        if (cause != null) {
            stallOrEnd(cause)
            return
        }

        // Cez rázcestie sa dá prejsť plynulo – zastavuje len ten, kto chce.
        val maxX = if (segment.choices.isEmpty()) segment.endWorldX - 0.5f
        else segment.endWorldX + GameConfig.JUNCTION_APPROACH
        val minX = maxOf(segment.worldOrigin - 2f, maxReachedX - GameConfig.REVERSE_LIMIT)
        val cx = car.x.coerceIn(minX, maxX)
        val wb = SedanSpec.wheelOffsetX
        val rearGy = segment.heightAtWorld((cx - wb).coerceIn(minX - 8f, maxX + 8f))
        val frontGy = segment.heightAtWorld((cx + wb).coerceIn(minX - 8f, maxX + 8f))
        val bumpiness = segment.bumpinessAtLocal(localX.coerceIn(0f, segment.length - 0.5f))
        currentSurface = segment.surfaceAtLocal(localX.coerceIn(0f, segment.length - 0.5f))
        car.applyDrive(
            dt,
            throttleInput * eventThrottleMul,
            brakeInput,
            rearGy,
            frontGy,
            bumpiness + eventBumpBonus,
            currentSurface,
            segment.paving.gripMul,
            isWinter
        )
        car.tickWear(dt, bumpiness + eventBumpBonus, brakeInput)
        applyFeatureEffects(dt)
        if (car.x > maxX) {
            car.x = maxX
            if (car.speed > 0f) car.speed = 0f
        }
        // Prejdenie rázcestia: pokračujeme bez zastavenia aj bez teleportu.
        if (car.x >= segment.endWorldX && segment.choices.isNotEmpty()) {
            enterBranch(resolveChoice())
            return
        }
        if (car.x < minX) {
            car.x = minX
            if (car.speed < 0f) car.speed = 0f
        }
        if (car.x > maxReachedX) maxReachedX = car.x

        // Udalosť môže zastropovať rýchlosť rovnako ako úsek trate.
        val evCap = eventSpeedCap
        if (evCap > 0f && car.speed > evCap) {
            car.speed = MathX.damp(car.speed, evCap, 2.5f, dt)
        }

        // Bez svetiel v noci sa dá len plaziť.
        if (isNight && !headlightsOn && car.speed > GameConfig.NIGHT_BLIND_SPEED) {
            car.speed = GameConfig.NIGHT_BLIND_SPEED
        }

        distanceM = maxReachedX.coerceAtLeast(0f)
        warnAboutEngineWear(dt)

        if (approachingJunction) {
            // Kto nechce voliť za jazdy, môže zastaviť a otvoriť si panel.
            if (car.speed <= GameConfig.STOP_SPEED * 1.5f && needsJunctionCall) {
                phase = GamePhase.JUNCTION
                car.speed = 0f
                message = "Junction — pick a road"
                return
            }
            message = if (pendingChoiceId != null) {
                val picked = segment.choices.firstOrNull { it.id == pendingChoiceId }
                "Heading for ${picked?.label ?: "the fork"}"
            } else {
                "Fork ahead — pick a road"
            }
        } else {
            // Teplotu a palivo rieši pás výstrah v HUD – tu by z toho boli
            // len donekonečna sa opakujúce hlášky.
            val near = buildingNear()
            if (near != null && car.speed < GameConfig.STOP_SPEED * 2.5f) {
                message = if (near.landmark) {
                    "DEPOT — fuel and decent parts. Worth stopping."
                } else {
                    "${near.type.displayName} — stop and search it"
                }
            }
        }

        eventCooldown -= dt
        if (eventCooldown <= 0f) {
            rollEvent()
            eventCooldown = MathX.lerp(
                GameConfig.EVENT_GAP_MIN,
                GameConfig.EVENT_GAP_MAX,
                MathX.hash01(seed.toInt(), distanceM.toInt())
            )
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
                step >= 3 -> "ENGINE ABOUT TO DIE: ${(health * 100).toInt()} % — ${cause?.warning ?: "oprav ho"}"
                step == 2 -> "Engine is badly worn (${(health * 100).toInt()} %)"
                else -> cause?.warning ?: "Engine is wearing down (${(health * 100).toInt()} %)"
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
     * Keď hráč drží plyn a auto nejde, musí byť jasné prečo.
     * Dôvod držíme trvale (nie ako miznúcu hlášku), kým sa auto nepohne.
     */
    private fun updateBlockedReason(dt: Float) {
        // Stojace auto: ak sa nedá naštartovať, dôvod držíme trvale.
        if (phase == GamePhase.PREP || phase == GamePhase.STOPPED || phase == GamePhase.EXPLORING) {
            stuckTime = 0f
            blockedReason = if (!car.engineRunning) startBlocker() else null
            return
        }
        if (phase != GamePhase.DRIVING || throttleInput < 0.3f || car.speed > 1.2f) {
            stuckTime = 0f
            blockedReason = null
            return
        }
        stuckTime += dt
        if (stuckTime < 0.6f) return

        val missing = car.missingEssentials()
        val slope = segment.slopeAtLocal(localX.coerceIn(0f, segment.length - 0.5f))
        val bump = segment.bumpinessAtLocal(localX.coerceIn(0f, segment.length - 0.5f))
        val climb = car.maxClimbSlope(bump, car.speed)
        val absSlope = kotlin.math.abs(slope)
        blockedReason = when {
            missing.isNotEmpty() ->
                "Missing ${missing.joinToString(", ") { it.displayName.lowercase() }}"
            !car.engineRunning -> "Engine is off — press START"
            car.fuel <= 0.05f -> "Out of fuel"
            brakeInput > 0.2f -> "Release the brake"
            !car.grounded -> "No traction in the air"
            // Len pri skutočnom stúpaní – na vrchole/rovinke nehlás grip.
            slope > 0.12f && slope > climb * 0.95f ->
                "Climb too steep for these tyres — need grip or a run-up"
            absSlope < 0.08f && car.wheelSlip > 0.55f && car.tireGrip < 0.45f ->
                "Tyres spinning — ease off or find better rubber"
            car.powerHp < 22f -> "Engine too weak (${car.powerHp.toInt()} hp)"
            else -> "Car won't move"
        }
    }

    /** Čo bráni naštartovaniu stojaceho auta (null = dá sa naštartovať). */
    private fun startBlocker(): String? {
        val missing = car.missingEssentials()
        val engineHealth = car.parts[ComponentSlot.ENGINE]?.health ?: 0f
        return when {
            missing.isNotEmpty() ->
                "Missing ${missing.joinToString(", ") { it.displayName.lowercase() }}"
            car.fuel < 0.5f -> "No fuel in the tank"
            car.oil < 0.3f -> "No oil in the engine"
            car.coolant < 0.3f -> "No coolant"
            car.batteryCharge < 0.2f -> "Battery is flat"
            engineHealth <= 0.15f -> "Engine is beyond saving"
            else -> null
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
        updateBlockedReason(dt)

        val cap = feature.speedCap
        if (cap > 0f && car.speed > cap) {
            // Nedá sa cez to prehnať – auto samo spomalí a otrasie sa.
            car.speed = MathX.damp(car.speed, cap, 2.2f, dt)
        }
        if (feature.roughness > 0.5f && car.speed > cap * 0.6f) {
            val over = ((car.speed - cap * 0.6f) / GameConfig.MAX_SPEED).coerceIn(0f, 1f)
            listOf(ComponentSlot.TIRE_FRONT, ComponentSlot.TIRE_REAR).forEach { slot ->
                car.parts[slot]?.let {
                    it.health = (it.health - GameConfig.TIRE_WEAR_BROKEN * over * dt).coerceAtLeast(0.05f)
                }
            }
            car.parts[ComponentSlot.SUSPENSION]?.let {
                it.health = (it.health - GameConfig.TIRE_WEAR_BROKEN * 0.6f * over * dt).coerceAtLeast(0.05f)
            }
        }
    }

    // --- Nečakané udalosti -------------------------------------------------

    /** Udalosti, ktoré práve bežia (únik paliva, dážď…). */
    val activeEvents: List<ActiveEvent> get() = events

    fun hasEvent(kind: RoadEvent): Boolean = events.any { it.event == kind }

    /** Vyvolá udalosť naschvál – pre testy a ladenie. */
    fun forceEvent(kind: RoadEvent) {
        trigger(kind, SeededRandom(seed xor kind.ordinal.toLong()))
    }

    private fun tickEvents(dt: Float) {
        if (events.isEmpty()) return
        val it = events.iterator()
        while (it.hasNext()) {
            val e = it.next()
            e.remaining -= dt
            if (e.remaining <= 0f) it.remove()
        }
        // Priebežné účinky.
        if (hasEvent(RoadEvent.FUEL_LEAK)) {
            car.fuel = (car.fuel - GameConfig.EVENT_FUEL_LEAK * dt).coerceAtLeast(0f)
        }
        if (hasEvent(RoadEvent.COOLANT_LEAK)) {
            car.coolant = (car.coolant - GameConfig.EVENT_COOLANT_LEAK * dt).coerceAtLeast(0f)
        }
    }

    /** Násobiteľ plynu – misfire uberie výkon, palivo sa míňa ďalej. */
    private val eventThrottleMul: Float
        get() = if (hasEvent(RoadEvent.MISFIRE)) 0.55f else 1f

    /** Násobiteľ spotreby – vietor v chrbte a čistá cesta šetria. */
    private val eventDrainMul: Float
        get() = when {
            hasEvent(RoadEvent.TAILWIND) -> 0.6f
            hasEvent(RoadEvent.CLEAR_ROAD) -> 0.8f
            else -> 1f
        }

    /** Extra hrboľatosť z počasia a prekážok – zhorší záber aj zrýchlenie. */
    private val eventBumpBonus: Float
        get() = (if (hasEvent(RoadEvent.RAIN)) 0.45f else 0f) +
            (if (hasEvent(RoadEvent.MUD)) 0.8f else 0f) +
            (if (hasEvent(RoadEvent.DEBRIS)) 0.35f else 0f)

    /** Strop rýchlosti z udalosti (0 = bez obmedzenia). */
    private val eventSpeedCap: Float
        get() = when {
            hasEvent(RoadEvent.MUD) -> 7f
            hasEvent(RoadEvent.DEBRIS) -> 10f
            hasEvent(RoadEvent.RAIN) -> 17f
            else -> 0f
        }

    /**
     * Vylosuje udalosť. Čím ďalej hráč je, tým väčšia šanca na smolu –
     * blízko štartu ho hra nedrví.
     */
    private fun rollEvent() {
        val rng = SeededRandom(
            seed xor distanceM.toRawBits().toLong() xor (elapsed * 1000f).toLong()
        )
        val hardness = MathX.growth(distanceM, 6000f).coerceAtMost(2.2f)
        val pool = RoadEvent.entries.filter { canHappen(it) }
        if (pool.isEmpty()) return

        var total = 0f
        val weights = pool.map { e ->
            val w = e.weight * if (e.good) 1f else (0.55f + hardness)
            total += w
            w
        }
        var pick = rng.nextFloat() * total
        var chosen = pool.last()
        for (i in pool.indices) {
            pick -= weights[i]
            if (pick <= 0f) {
                chosen = pool[i]
                break
            }
        }
        trigger(chosen, rng)
    }

    private fun canHappen(e: RoadEvent): Boolean = when (e) {
        // Nemá zmysel prepichnúť gumu, ktorá tam nie je.
        RoadEvent.FLAT_TYRE -> TIRE_SLOTS.any {
            (car.parts[it]?.health ?: 0f) > 0.15f
        }
        RoadEvent.ROCK_STRIKE -> car.hasPart(ComponentSlot.RADIATOR)
        RoadEvent.BELT_SNAPPED -> car.hasPart(ComponentSlot.ALTERNATOR)
        RoadEvent.COOLANT_LEAK -> car.coolant > 1f
        RoadEvent.FUEL_LEAK -> car.fuel > 4f
        RoadEvent.TAILWIND, RoadEvent.CLEAR_ROAD -> !inJunctionZone
        else -> true
    } && !hasEvent(e)

    private fun trigger(e: RoadEvent, rng: SeededRandom) {
        when (e) {
            RoadEvent.FLAT_TYRE -> {
                val options = TIRE_SLOTS.mapNotNull { s -> car.parts[s]?.let { s to it } }
                if (options.isNotEmpty()) {
                    val part = options[rng.nextInt(options.size)].second
                    part.health = (part.health - rng.nextFloat(0.25f, 0.45f)).coerceAtLeast(0.08f)
                }
            }
            RoadEvent.ROCK_STRIKE -> car.parts[ComponentSlot.RADIATOR]?.let {
                it.health = (it.health - rng.nextFloat(0.15f, 0.3f)).coerceAtLeast(0.08f)
            }
            RoadEvent.OIL_SPLASH -> {
                // Špina v oleji – objem ostane, kvalita klesne.
                car.oilPurity = (car.oilPurity - rng.nextFloat(0.1f, 0.25f)).coerceAtLeast(0.05f)
            }
            RoadEvent.BELT_SNAPPED -> car.parts[ComponentSlot.ALTERNATOR]?.let {
                it.health = (it.health - 0.2f).coerceAtLeast(0.05f)
            }
            RoadEvent.ROADSIDE_STASH -> dropRoadsideFind(rng, parts = false)
            RoadEvent.ABANDONED_WRECK -> dropRoadsideFind(rng, parts = true)
            else -> Unit
        }
        if (e.timed) events += ActiveEvent(e, e.duration)
        message = e.message
    }

    /** Nález pri ceste – vznikne budova v dosahu, aby sa dal zobrať. */
    private fun dropRoadsideFind(rng: SeededRandom, parts: Boolean) {
        val find = WorldBuilding(
            id = seed xor (distanceM.toRawBits().toLong() * 31L),
            type = if (parts) BuildingType.GARAGE else BuildingType.HOUSE,
            localX = (car.x - segment.worldOrigin) + rng.nextFloat(6f, 14f)
        )
        if (parts) {
            val pool = listOf(
                ItemCatalog.TIRE_POOR, ItemCatalog.TIRE, ItemCatalog.BATTERY, ItemCatalog.RADIATOR,
                ItemCatalog.BRAKES, ItemCatalog.ALTERNATOR, ItemCatalog.STARTER,
                ItemCatalog.HOOD, ItemCatalog.DOORS
            )
            repeat(1 + rng.nextInt(2)) {
                val def = rng.pick(pool)
                find.loot.add(ItemStack(def.id, ComponentCondition.DAMAGED, rng.nextFloat(0.3f, 0.7f)))
            }
        } else {
            val def = if (rng.chance(0.6f)) ItemCatalog.FUEL_CAN
            else rng.pick(listOf(ItemCatalog.OIL_BOTTLE, ItemCatalog.COOLANT_BOTTLE, ItemCatalog.WATER))
            find.loot.add(
                ItemStack(
                    defId = def.id,
                    condition = ComponentCondition.NEW,
                    health = 1f,
                    count = 1,
                    purity = rng.nextFloat(0.55f, 0.95f)
                )
            )
        }
        segment.buildings += find
    }

    /**
     * Vyberie vetvu dopredu, za jazdy. Auto nezastavuje – na rázcestí sa
     * jednoducho zaradí tam, kam hráč ukázal.
     */
    fun selectBranch(choiceId: Int): Boolean {
        val choice = segment.choices.find { it.id == choiceId } ?: return false
        pendingChoiceId = choiceId
        message = "Taking ${choice.label}"
        return true
    }

    /** Voľba z panela pri zastavení – vyberie vetvu a rovno sa rozbehne. */
    fun chooseBranch(choiceId: Int): Boolean {
        if (!selectBranch(choiceId)) return false
        if (phase == GamePhase.JUNCTION) phase = GamePhase.DRIVING
        return true
    }

    /** Keď hráč nič nevybral, ide sa najbezpečnejšou vetvou. */
    private fun resolveChoice(): BranchChoice {
        val picked = pendingChoiceId?.let { id -> segment.choices.find { it.id == id } }
        if (picked != null) return picked
        val safest = segment.choices.minByOrNull { it.plan.risk } ?: segment.choices.first()
        message = "No call made — staying on ${safest.label}"
        return safest
    }

    /** Napojí ďalší segment tak, aby jazda pokračovala bez švíku. */
    private fun enterBranch(choice: BranchChoice) {
        val origin = segment.endWorldX
        segmentTripDistance = distanceM
        segment = WorldGenerator.createSegment(
            plan = choice.plan,
            worldOrigin = origin,
            tripDistance = distanceM,
            terrain = terrain,
            isTutorial = false
        )
        pendingChoiceId = null
        rescueStashId = null
        currentFeature = segment.featureAtWorld(car.x)
        // Rýchlosť ani pozícia sa nemenia – žiadny teleport, žiadna strata švihu.
        car.snapToGround(segment.heightAtWorld(car.x))
        activeBuilding = null
        phase = GamePhase.DRIVING
        if (!message.startsWith("No call made")) {
            message = "${choice.label} — ${choice.hint.lowercase()}"
        }
    }

    fun requestStop(): Boolean {
        if (phase != GamePhase.DRIVING) return false
        if (car.speed > GameConfig.STOP_SPEED * 3f) {
            message = "Slow down first"
            return false
        }
        car.speed = 0f
        if (needsJunctionCall) {
            phase = GamePhase.JUNCTION
            message = "Junction — pick a road"
        } else {
            phase = GamePhase.STOPPED
            message = "Stopped"
        }
        return true
    }

    fun resumeDriving(): Boolean {
        if (phase != GamePhase.STOPPED && phase != GamePhase.EXPLORING && phase != GamePhase.PREP) {
            return false
        }
        if (!car.engineRunning) {
            message = "Start the engine"
            return false
        }
        if (needsJunctionCall) {
            phase = GamePhase.JUNCTION
            message = "Pick a road first"
            return false
        }
        activeBuilding = null
        prepStep = PrepStep.DONE
        car.prepChecklistDone = true
        phase = GamePhase.DRIVING
        message = "Back on the road"
        return true
    }

    /** Svetová pozícia budovy segmentu. */
    private fun worldXOf(b: WorldBuilding): Float = segment.worldOrigin + b.localX

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
            message = "Pick a road first"
            return false
        }
        // PREP je tiež „stojíme pri aute“ – bez toho by sa hráč nedostal
        // ani do kôlne hneď vedľa, z ktorej má vrak sprevádzkovať.
        if (phase != GamePhase.STOPPED && phase != GamePhase.DRIVING && phase != GamePhase.PREP) {
            return false
        }
        if (phase == GamePhase.DRIVING && car.speed > GameConfig.STOP_SPEED) {
            message = "Stop at the building first"
            return false
        }
        car.speed = 0f
        val b = buildingNear() ?: run {
            message = "No building nearby"
            return false
        }
        activeBuilding = b
        phase = GamePhase.EXPLORING
        if (visitedBuildingIds.add(b.id)) buildingsVisited++
        message = "Searching: ${b.type.displayName}"
        return true
    }

    /** Natankuje priamo z pumpy na benzínke (má obmedzenú zásobu). */
    fun refuelFromPump(): Boolean {
        val b = activeBuilding ?: return false
        if (b.pumpFuelL <= 0.05f) {
            message = if (b.type == BuildingType.GAS_STATION) {
                "The pump is empty"
            } else {
                "There is no pump here"
            }
            return false
        }
        val room = car.fuelCapacity - car.fuel
        if (room <= 0.05f) {
            message = "The tank is full"
            return false
        }
        val moved = car.refill(FluidType.FUEL, kotlin.math.min(room, b.pumpFuelL), b.pumpPurity)
        b.pumpFuelL = (b.pumpFuelL - moved).coerceAtLeast(0f)
        message = "Filled up +${String.format("%.1f", moved)} L · " +
            "${FluidGrade.of(b.pumpPurity).displayName.lowercase()} (pump has ${String.format("%.0f", b.pumpFuelL)} L)"
        return true
    }

    /** Prehodenie gúm predok ↔ zadok – jazda sa dá predĺžiť aj bez nálezu. */
    fun swapTyres(): Boolean {
        if (phase == GamePhase.DRIVING || phase == GamePhase.GAME_OVER) {
            message = "Stop the car first"
            return false
        }
        if (!car.swapTyres()) {
            message = "No tyres to swap"
            return false
        }
        message = "Swapped front and rear tyres"
        return true
    }

    fun leaveBuilding() {
        if (phase != GamePhase.EXPLORING) return
        activeBuilding = null
        phase = if (needsJunctionCall) GamePhase.JUNCTION else GamePhase.STOPPED
        message = "Back at the car"
    }

    fun takeLoot(index: Int): Boolean {
        val b = activeBuilding ?: return false
        if (index !in b.loot.indices) return false
        val item = b.loot[index]
        if (!inventory.canFit(item)) {
            message = "Pack is full or too heavy"
            return false
        }
        inventory.add(item)
        b.loot.removeAt(index)
        itemsLooted += item.count
        message = "Picked up: ${item.def.name}"
        return true
    }

    /** Demontuje diel z auta späť do inventára. */
    fun unmountSlot(slot: ComponentSlot): Boolean {
        val part = car.parts[slot] ?: run {
            message = "${slot.displayName}: nothing is fitted"
            return false
        }
        if (car.engineRunning && slot in RUNNING_CRITICAL) {
            message = "Switch the engine off first"
            return false
        }
        val stack = ItemStack(part.defId, part.condition, part.health)
        // Zložiť úložisko sa dá, len keď sa obsah zmestí aj bez neho –
        // inak by sa veci ticho stratili.
        if (part.def.extraSlots > 0) {
            val remaining = car.extraCargoSlots - part.def.extraSlots
            if (!inventory.fitsWithin(remaining)) {
                message = "Empty it first — the pack would not hold everything"
                return false
            }
        }
        if (!inventory.canFit(stack)) {
            message = "Pack is full or too heavy"
            return false
        }
        car.parts.remove(slot)
        inventory.add(stack)
        syncCargoCapacity()
        message = "Removed: ${slot.displayName}"
        return true
    }

    fun discardInventoryItem(index: Int): Boolean {
        val stack = inventory.removeAt(index) ?: return false
        message = "Dropped: ${stack.def.name}"
        return true
    }

    /** Prepočíta kapacitu batohu podľa namontovaného úložiska. */
    private fun syncCargoCapacity() {
        inventory.applyCapacity(car.extraCargoSlots, car.extraCargoWeight)
    }

    fun useInventoryItem(index: Int, target: ComponentSlot? = null): Boolean {
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
                message = "Already full (${def.fluid.displayName})"
                return false
            }
            val used = car.refill(def.fluid, def.fluidAmount, stack.purity)
            if (used <= 0f) {
                message = "Already full (${def.fluid.displayName})"
                return false
            }
            stack.count--
            if (stack.count <= 0) inventory.removeAt(index)
            message = if (stack.grade == FluidGrade.PURE) {
                "Topped up +${String.format("%.1f", used)} L ${def.fluid.displayName}"
            } else {
                "Topped up +${String.format("%.1f", used)} L — ${stack.grade.displayName.lowercase()}!"
            }
            return true
        }
        val targets = def.mountTargets()
        if (targets.isNotEmpty()) {
            // Gumu si hráč zaradí sám – predok alebo zadok, podľa toho,
            // ktorá náprava je zodratejšia a ktorá práve ťahá.
            val slot = when {
                target != null && def.canMountTo(target) -> target
                def.axleTire -> car.pickTireMountSlot()
                else -> targets.first()
            }
            if (!def.canMountTo(slot)) return false
            val prev = car.mount(slot, stack)
            inventory.removeAt(index)
            if (prev != null) inventory.add(ItemStack(prev.defId, prev.condition, prev.health))
            syncCargoCapacity()
            message = "Fitted: ${def.name} → ${slot.displayName.lowercase()}"
            return true
        }
        message = "That cannot be used"
        return false
    }

    fun repairSlot(slot: ComponentSlot): Boolean {
        val part = car.parts[slot] ?: return false
        if (part.health >= 0.98f) {
            message = "That one is fine"
            return false
        }
        val oilIdx = inventory.slots.indexOfFirst { it?.defId == ItemCatalog.OIL_BOTTLE.id }
        if (oilIdx >= 0) {
            val s = inventory.slots[oilIdx]!!
            s.count--
            if (s.count <= 0) inventory.removeAt(oilIdx)
            car.repair(slot, 0.35f)
            message = "Repaired: ${slot.displayName}"
        } else {
            car.repair(slot, 0.12f)
            message = "Rough patch-up: ${slot.displayName}"
        }
        return true
    }

    fun tryStartEngine(): Boolean {
        if (car.engineRunning) {
            message = "Engine is already running"
            return true
        }
        return if (car.tryStart()) {
            prepStep = PrepStep.DONE
            car.prepChecklistDone = true
            activeBuilding = null
            phase = GamePhase.DRIVING
            // V noci si svetlá zapneme sami – bez nich sa nedá jazdiť.
            if (isNight && !headlightsOn) headlightsOn = true
            message = "Engine running — hold the throttle"
            true
        } else {
            val missing = car.missingEssentials()
            // Zablokovaný štart nesmie byť slepá ulička – čo chýba, nájde sa nablízku.
            val rescued = offerRescue(missing)
            message = when {
                rescued != null -> rescued
                missing.isNotEmpty() ->
                    "Missing ${missing.joinToString(", ") { it.displayName.lowercase() }}"
                car.fuel < 0.5f -> "Fuel is low"
                car.oil < 0.3f -> "Oil is low"
                car.coolant < 0.3f -> "Coolant is low"
                car.batteryCharge < 0.2f -> "Battery is weak"
                else -> "The engine would not start"
            }
            false
        }
    }

    fun stopEngine() {
        car.stopEngine()
        message = "Engine switched off"
    }

    /**
     * Záchranná sieť: keď sa auto nedá naštartovať a chýbajúci diel či kvapalina
     * nie je ani v batohu, ani v budove nablízku, objaví sa pri ceste vrak,
     * z ktorého sa to dá vybrať. Hra nikdy neskončí patom.
     */
    private fun offerRescue(missing: List<ComponentSlot>): String? {
        val needParts = missing.filter { slot ->
            inventory.slots.none { it?.def?.canMountTo(slot) == true }
        }.toMutableList()
        // Vybitá batéria bez alternátora je rovnaká slepá ulička ako chýbajúci diel.
        if (car.batteryCharge < 0.2f &&
            batteryRescues < MAX_FLUID_RESCUES &&
            inventory.slots.none { it?.def?.canMountTo(ComponentSlot.BATTERY) == true } &&
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

        // Vrak sa vždy len jeden a stojí za autom – nesmie „vyskočiť“ z ničoho
        // priamo pred kapotu. Ďalšie zaseknutie doplní ten istý.
        val stash = buildingNear()
            ?: rescueStashId?.let { id -> segment.buildings.firstOrNull { it.id == id } }
                ?.takeIf { kotlin.math.abs(worldXOf(it) - car.x) <= GameConfig.BUILDING_INTERACT_RANGE }
            ?: WorldBuilding(
                id = seed xor 0x412CL xor segment.buildings.size.toLong(),
                type = BuildingType.GARAGE,
                localX = (car.x - segment.worldOrigin) - 4.5f
            ).also {
                segment.buildings.add(it)
                rescueStashId = it.id
            }

        val rng = SeededRandom(seed xor stash.id xor distanceM.toRawBits().toLong())
        val added = mutableListOf<String>()
        needParts.forEach { slot ->
            if (stash.loot.any { it.def.canMountTo(slot) }) return@forEach
            val def = when (slot) {
                ComponentSlot.ENGINE -> ItemCatalog.ENGINE_A
                ComponentSlot.FUEL_TANK -> ItemCatalog.FUEL_TANK
                ComponentSlot.TIRE_FRONT, ComponentSlot.TIRE_REAR -> ItemCatalog.TIRE_POOR
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
        return "A stripped wreck sits just behind you — ${added.joinToString(", ")} inside. Check BUILDING."
    }

    /**
     * Auto zhaslo. Jazda tým ale nekončí – kvapalina sa dá doliať z batohu,
     * diel vymeniť, motor nechať vychladnúť. Koniec je až vtedy, keď hráč
     * naozaj nemá čím sa pohnúť ďalej.
     *
     * @return true, ak jazda skončila
     */
    private fun stallOrEnd(cause: EndCause): Boolean {
        if (phase == GamePhase.GAME_OVER) return true
        if (!canRecoverFrom(cause)) {
            endRun(cause.toEndReason())
            return true
        }
        car.engineRunning = false
        car.speed = 0f
        throttleInput = 0f
        brakeInput = 0f
        if (phase == GamePhase.DRIVING) phase = GamePhase.STOPPED
        message = when (cause) {
            EndCause.OUT_OF_FUEL -> "Ran dry — top the tank up from your pack"
            EndCause.BATTERY_DEAD -> "Battery is flat — fit a charged one"
            EndCause.OVERHEAT -> "Engine cut out from the heat — let it cool"
            EndCause.ENGINE_DESTROYED -> "The engine is finished — swap it out"
        }
        return false
    }

    /** Má hráč ešte reálne čím zhasnuté auto oživiť? */
    private fun canRecoverFrom(cause: EndCause): Boolean {
        val near = buildingNear()
        fun packHas(predicate: (ItemStack) -> Boolean) = inventory.slots.any { it != null && predicate(it) }
        fun buildingHas(predicate: (ItemStack) -> Boolean) =
            near?.loot?.any(predicate) == true
        return when (cause) {
            EndCause.OUT_OF_FUEL ->
                packHas { it.def.fluid == FluidType.FUEL } ||
                    buildingHas { it.def.fluid == FluidType.FUEL } ||
                    (near?.pumpFuelL ?: 0f) > 0.5f ||
                    fluidRescues < MAX_FLUID_RESCUES
            EndCause.BATTERY_DEAD ->
                packHas { it.def.canMountTo(ComponentSlot.BATTERY) } ||
                    buildingHas { it.def.canMountTo(ComponentSlot.BATTERY) } ||
                    batteryRescues < MAX_FLUID_RESCUES
            EndCause.OVERHEAT ->
                // Vychladnutý motor naštartuje, pokiaľ ho teplo nezožralo.
                (car.parts[ComponentSlot.ENGINE]?.health ?: 0f) > 0.06f
            EndCause.ENGINE_DESTROYED ->
                packHas { it.def.canMountTo(ComponentSlot.ENGINE) } ||
                    buildingHas { it.def.canMountTo(ComponentSlot.ENGINE) }
        }
    }

    fun endRun(reason: EndReason = EndReason.MANUAL) {
        phase = GamePhase.GAME_OVER
        endReason = reason
        endDetail = when (reason) {
            EndReason.ENGINE_DESTROYED, EndReason.OVERHEAT ->
                car.wearCause?.let { "Cause: ${it.fatal}." } ?: ""
            EndReason.OUT_OF_FUEL -> "The tank is bone dry."
            EndReason.BATTERY_DEAD -> "The headlights drained the battery and the engine never turned over again."
            EndReason.MANUAL -> ""
        }
        car.stopEngine()
        car.speed = 0f
        message = reason.message
    }

    // --- Ukladanie a obnova jazdy ------------------------------------------

    /** Odfotí trvalý stav jazdy. Prechodná fyzika sa neukladá – usadí sa sama. */
    fun snapshot(): RunSnapshot = RunSnapshot(
        seed = seed,
        phase = if (phase == GamePhase.EXPLORING) GamePhase.STOPPED else phase,
        distanceM = distanceM,
        maxReachedX = maxReachedX,
        elapsed = elapsed,
        timeOfDay = timeOfDay,
        headlightsOn = headlightsOn,
        fuelBurnedL = fuelBurnedL,
        itemsLooted = itemsLooted,
        buildingsVisited = buildingsVisited,
        fluidRescues = fluidRescues,
        batteryRescues = batteryRescues,
        car = CarState(
            parts = car.parts.map { (slot, p) -> PartState(slot, p.defId, p.condition, p.health) },
            fuel = car.fuel, oil = car.oil, coolant = car.coolant,
            fuelPurity = car.fuelPurity, oilPurity = car.oilPurity, coolantPurity = car.coolantPurity,
            temperature = car.temperature, batteryCharge = car.batteryCharge,
            engineRunning = car.engineRunning,
            x = car.x, y = car.y, speed = car.speed, pitch = car.pitch
        ),
        inventory = inventory.slots.map { it?.toState() },
        segment = SegmentState(
            planSeed = segment.seed,
            style = segment.style,
            length = segment.length,
            features = segment.sections.map { it.feature },
            buildingCount = segment.buildings.size,
            worldOrigin = segment.worldOrigin,
            tripDistance = segmentTripDistance,
            buildings = segment.buildings.map { b ->
                BuildingState(
                    id = b.id, type = b.type, localX = b.localX,
                    pumpFuelL = b.pumpFuelL, pumpPurity = b.pumpPurity,
                    landmark = b.landmark,
                    loot = b.loot.map { it.toState() }
                )
            }
        ),
        events = events.map { EventState(it.event, it.remaining) }
    )

    private fun applySnapshot(snap: RunSnapshot) {
        // Svet: plán je deterministický zo seedu, budovy prepíšeme stavom z uloženia.
        val plan = SegmentPlan(
            seed = snap.segment.planSeed,
            style = snap.segment.style,
            length = snap.segment.length,
            features = snap.segment.features,
            buildingCount = snap.segment.buildingCount
        )
        segmentTripDistance = snap.segment.tripDistance
        segment = WorldGenerator.createSegment(
            plan = plan,
            worldOrigin = snap.segment.worldOrigin,
            tripDistance = snap.segment.tripDistance,
            terrain = terrain,
            isTutorial = false
        )
        segment.buildings.clear()
        snap.segment.buildings.forEach { b ->
            segment.buildings += WorldBuilding(
                id = b.id,
                type = b.type,
                localX = b.localX,
                loot = b.loot.map { it.toStack() }.toMutableList(),
                pumpFuelL = b.pumpFuelL,
                pumpPurity = b.pumpPurity,
                landmark = b.landmark
            )
        }

        // Auto.
        car.parts.clear()
        snap.car.parts.forEach { p ->
            car.parts[p.slot] = sk.kubis.endlessdrive.game.car.MountedPart(p.defId, p.condition, p.health)
        }
        car.fuel = snap.car.fuel
        car.oil = snap.car.oil
        car.coolant = snap.car.coolant
        car.fuelPurity = snap.car.fuelPurity
        car.oilPurity = snap.car.oilPurity
        car.coolantPurity = snap.car.coolantPurity
        car.temperature = snap.car.temperature
        car.batteryCharge = snap.car.batteryCharge
        car.engineRunning = snap.car.engineRunning
        car.x = snap.car.x
        car.y = snap.car.y
        car.speed = 0f
        car.pitch = snap.car.pitch

        // Batoh. Kapacitu treba nastaviť skôr, než sa napĺňa – s nosičom
        // má batoh viac slotov a inak by sa presahujúce veci stratili.
        inventory.clear()
        syncCargoCapacity()
        snap.inventory.forEachIndexed { i, st ->
            if (st != null && i < inventory.slots.size) inventory.slots[i] = st.toStack()
        }

        // Bežiace udalosti pokračujú tam, kde skončili.
        events.clear()
        snap.events.forEach { events += ActiveEvent(it.kind, it.remaining) }

        // Priebeh.
        distanceM = snap.distanceM
        maxReachedX = snap.maxReachedX
        elapsed = snap.elapsed
        timeOfDay = snap.timeOfDay
        headlightsOn = snap.headlightsOn
        fuelBurnedL = snap.fuelBurnedL
        itemsLooted = snap.itemsLooted
        buildingsVisited = snap.buildingsVisited
        fluidRescues = snap.fluidRescues
        batteryRescues = snap.batteryRescues
        // Po obnove nikdy nejdeme rovno v jazde – hráč sa najprv rozhliadne.
        phase = when (snap.phase) {
            GamePhase.DRIVING, GamePhase.EXPLORING -> GamePhase.STOPPED
            else -> snap.phase
        }
        currentFeature = segment.featureAtWorld(car.x)
        activeBuilding = null
        camera.snapTo(car.x, car.y)
        message = "Run restored — you were at ${String.format("%.2f", distanceKm)} km."
    }

    private fun ItemStack.toState() = StackState(defId, condition, health, count, purity)

    private fun StackState.toStack() = ItemStack(defId, condition, health, count, purity)

    companion object {
        /**
         * Postaví engine späť z uloženej jazdy. Terén aj úseky sa dopočítajú
         * zo seedu, budovy sa nahradia tým, čo v nich hráč nechal.
         */
        fun restore(snap: RunSnapshot, bestDistanceKm: Float): GameEngine {
            val engine = GameEngine(snap.seed, bestDistanceKm)
            engine.applySnapshot(snap)
            return engine
        }

        /** Ako dlho ostane hláška na obrazovke (s). */
        private const val MESSAGE_TTL = 4.5f

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
