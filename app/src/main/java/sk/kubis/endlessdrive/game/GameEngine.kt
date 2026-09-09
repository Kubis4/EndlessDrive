package sk.kubis.endlessdrive.game

import sk.kubis.endlessdrive.domain.model.DebugOptions
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
import sk.kubis.endlessdrive.domain.model.FuelKind
import sk.kubis.endlessdrive.domain.model.GamePhase
import sk.kubis.endlessdrive.domain.model.ItemCatalog
import sk.kubis.endlessdrive.domain.model.ItemDef
import sk.kubis.endlessdrive.domain.model.ItemStack
import sk.kubis.endlessdrive.domain.model.RoadFeature
import sk.kubis.endlessdrive.domain.model.RoadSurface
import sk.kubis.endlessdrive.domain.model.SedanSpec
import sk.kubis.endlessdrive.domain.model.VehiclePaint
import sk.kubis.endlessdrive.domain.model.candidatesFor
import sk.kubis.endlessdrive.game.audio.GameSfx
import sk.kubis.endlessdrive.game.car.Car
import sk.kubis.endlessdrive.game.car.EndCause
import sk.kubis.endlessdrive.game.car.MountedPart
import sk.kubis.endlessdrive.game.car.TireInjury
import sk.kubis.endlessdrive.game.car.TireSim
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
import sk.kubis.endlessdrive.game.world.Terrain
import sk.kubis.endlessdrive.game.world.TerrainProfile
import sk.kubis.endlessdrive.game.world.TestTrackProfile
import sk.kubis.endlessdrive.game.world.WorldBuilding
import sk.kubis.endlessdrive.game.world.WorldGenerator

enum class PrepStep(val hint: String) {
    CHECK_ENGINE("Check the engine"),
    REFILL_OIL("Top up the engine oil"),
    REFILL_COOLANT("Top up the coolant"),
    CHECK_FUEL("Check the fuel"),
    START_ENGINE("Start the engine"),
    DONE("Ready to drive")
}

/**
 * Herný engine – bočný arcade pohyb cez plynulo nadväzujúce regióny.
 */
class GameEngine(
    val seed: Long,
    val bestDistanceKm: Float,
    /** Ladiace prepínače z nastavení – ovplyvňujú len výbavu na štarte. */
    private val debugOptions: DebugOptions = DebugOptions.OFF,
    /** Trvalý bonus z obnovenej rádiovej siete. */
    private val metaRelayNodes: Int = 0
) {
    val car = Car()
    /** Batoh na chrbte – ide s hráčom do budovy. */
    val inventory = Inventory()

    /**
     * Kufor auta. Väčší než batoh, ale dostupný len pri stojacom aute, takže
     * pri prehrabávaní budovy treba rátať s tým, čo unesiem naraz.
     */
    val boot = Inventory(GameConfig.BOOT_SLOTS, GameConfig.BOOT_MAX_WEIGHT)
    val camera = Camera2D()
    /**
     * Profil terénu. Ladenie pruženia potrebuje známe prekážky v známom
     * poradí – na náhodnej ceste sa na poriadny hrbol čaká pol kilometra.
     */
    val terrain: Terrain =
        if (debugOptions.testTrack) TestTrackProfile() else TerrainProfile(seed)

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
    /**
     * CAR/PACK overlay: herný čas, svetlá aj SoC stoja. Inak by otvorenie
     * CAR pri zapnutých svetlách vybilo batériu, kým hráč číta karty.
     */
    var timeHeldByMenu: Boolean = false

    var headlightsOn: Boolean = false
        private set
    /** true = diaľkové; pri zhasnutých svetlách je vždy false. */
    var highBeamsOn: Boolean = false
        private set
    private var roofLightsRequested = false
    val hasExpeditionKit: Boolean
        get() = car.parts[ComponentSlot.ROOF_RACK]?.defId == ItemCatalog.EXPEDITION_RACK.id
    val roofLightsOn: Boolean
        get() = roofLightsRequested && hasExpeditionKit

    fun toggleRoofLights() {
        roofLightsRequested = hasExpeditionKit && !roofLightsOn
        message = if (roofLightsOn) "Roof lights on" else "Roof lights off"
    }

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
    /** Materiál z rozobratých predmetov; patrí aktuálnej jazde. */
    var scrap: Int = 0
        private set
    private val visitedBuildingIds = HashSet<Long>()

    /** Milníky aktuálnej jazdy, ktoré sa nedajú spoľahlivo odvodiť z rekordu. */
    private var fuelWasBelowFull = false
    private var fullTankReached = false
    private var fullUpgradeReached = false
    private val seenEventKinds = linkedSetOf<RoadEvent>()

    val hasReachedFullTank: Boolean get() = fullTankReached
    val hasReachedFullUpgrade: Boolean get() = fullUpgradeReached
    val eventKindsSeen: Set<RoadEvent> get() = seenEventKinds.toSet()

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

    /** Trvalý meta-progres dostupný v tejto jazde; ovplyvní aj finálny cieľ. */
    private var relayProgress = metaRelayNodes.coerceIn(0, Journey.goals.size)

    val junctionChoices: List<BranchChoice>
        get() = segment.choices

    /** Vzdialenosť, pri ktorej segment vznikol – križovatky z nej vychádzajú. */
    var segmentTripDistance: Float = 0f
        private set

    private val events = mutableListOf<ActiveEvent>()
    /** Jednorazové SFX – UI ich vyberie cez [consumeSfx]. */
    private val sfxQueue = ArrayDeque<GameSfx>()
    private var sweepGroundY = 0f
    private var sweepSlopeRad = 0f

    private var accumulator = 0f
    private var eventCooldown = 14f
    private var tireHazardAcc = 0f
    private var screenHeightPx = 720f
    /** Hotový nasledujúci región – hranica potom nerobí generovanie v jednom frame. */
    private var preparedContinuation: RoadSegment? = null

    init {
        val startRng = SeededRandom(seed xor 0x57A27L)
        car.installStarterKit(startRng, debugOptions)
        stockStarterPack(startRng)

        segment = WorldGenerator.createSegment(
            segmentSeed = seed,
            style = WorldGenerator.startingStyle(seed),
            worldOrigin = 0f,
            tripDistance = 0f,
            terrain = terrain,
            isTutorial = true
        )
        applyRelayProgress(segment)
        car.x = 4f
        car.setCargoLoad(inventory.totalWeight, boot.totalWeight)
        car.snapToGround(segment.heightAtWorld(car.x))
        maxReachedX = car.x
        camera.snapTo(car.x, car.y)
        stockStarterShed(startRng)
        fuelWasBelowFull = car.fuelRatio < 0.95f
        // Prvý región pripravíme počas načítania jazdy, nie počas rýchlej jazdy.
        prepareContinuation()
        message = "Search the garage for missing parts, fuel, oil and coolant. Then start the engine."
    }

    /** Aktualizuje stav achievementov po každej akcii aj po snímke jazdy. */
    fun refreshAchievementFlags() {
        if (car.fuelRatio < 0.95f) fuelWasBelowFull = true
        if (fuelWasBelowFull && car.fuelRatio >= 0.995f) fullTankReached = true
        if (isFullyUpgraded()) fullUpgradeReached = true
    }

    /** Všetky diely, ktoré majú v hre jednoznačný scrap upgrade, sú na maxime. */
    fun isFullyUpgraded(): Boolean = listOf(
        ComponentSlot.ENGINE,
        ComponentSlot.BATTERY,
        ComponentSlot.RADIATOR,
        ComponentSlot.BRAKES,
        ComponentSlot.FUEL_TANK,
        ComponentSlot.DRIVETRAIN
    ).all { slot -> car.parts[slot] != null && scrapUpgradeTarget(slot) == null }

    /**
     * Batoh na štarte. Nie je to výbava na cestu, ale to, čo si človek stihol
     * pobrať: trocha kvapalín a jedna náhradná drobnosť. Samotná voda bola
     * málo – hráč nemal čím začať a prvé kilometre boli len hľadanie.
     */
    private fun stockStarterPack(rng: SeededRandom) {
        fun add(def: ItemDef, count: Int = 1, purity: Float = 1f) {
            inventory.add(
                ItemStack(
                    defId = def.id,
                    condition = ComponentCondition.NEW,
                    health = 1f,
                    count = count,
                    purity = purity
                )
            )
        }
        add(ItemCatalog.OIL_BOTTLE, purity = rng.nextFloat(0.70f, 0.92f))
        add(ItemCatalog.FUEL_CAN, purity = rng.nextFloat(0.72f, 0.95f))
        // Obnovené relé zlepšujú ďalšie štarty, ale nikdy nepreskočia loot loop.
        when {
            metaRelayNodes >= 1 -> add(ItemCatalog.PUNCTURE_KIT)
        }
        when {
            metaRelayNodes >= 2 -> add(ItemCatalog.COOLANT_BOTTLE, purity = 0.9f)
        }
        when {
            metaRelayNodes >= 3 -> add(ItemCatalog.OIL_BOTTLE, purity = 0.9f)
        }
        when {
            metaRelayNodes >= 4 -> add(ItemCatalog.FUEL_CAN, purity = 0.95f)
        }
        if (metaRelayNodes >= 5) {
            inventory.add(
                ItemStack(ItemCatalog.TIRE_POOR.id, ComponentCondition.USED, health = 0.78f)
            )
        }
        // Jedna náhradná vec do začiatku – nie vždy tá istá.
        val spare = rng.pick(
            listOf(
                ItemCatalog.COOLANT_BOTTLE,
                ItemCatalog.TIRE_POOR,
                ItemCatalog.BATTERY,
                ItemCatalog.BRAKES,
                ItemCatalog.PUNCTURE_KIT
            )
        )
        if (spare.fluid != null) {
            add(spare, purity = rng.nextFloat(0.60f, 0.90f))
        } else if (spare.id == ItemCatalog.PUNCTURE_KIT.id) {
            add(spare)
        } else {
            inventory.add(ItemStack(spare.id, ComponentCondition.DAMAGED, rng.nextFloat(0.45f, 0.70f)))
        }
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
        // Na začiatku je jeden výkonnejší, ale krátkodobý „lucky find“.
        // Pomôže s prvým prudším kopcom bez toho, aby hráč dostal trvalý
        // endgame motor zadarmo.
        shed.loot.add(
            ItemStack(
                ItemCatalog.ENGINE_B.id,
                ComponentCondition.CRITICAL,
                rng.nextFloat(0.28f, 0.40f)
            )
        )
        // Len jeden ukážkový interiérový/karosársky diel. Kôlňa má hráča
        // sprevádzkovať, nie mu hneď odovzdať polovicu hotového auta.
        val showcase = rng.pick(
            listOf(
                ItemCatalog.SEAT_FRONT, ItemCatalog.SEAT_REAR,
                ItemCatalog.DOOR_FRONT, ItemCatalog.DOOR_REAR, ItemCatalog.HOOD
            )
        )
        shed.loot.add(
            ItemStack(
                showcase.id,
                ComponentCondition.USED,
                rng.nextFloat(0.52f, 0.82f),
                paintIndex = if (showcase.mountsTo?.takesBodyPaint == true) {
                    rng.nextInt(VehiclePaint.entries.size)
                } else -1
            )
        )
        segment.buildings.add(0, shed)
    }

    val distanceKm: Float get() = distanceM / 1000f
    val isNewRecord: Boolean get() = distanceKm > bestDistanceKm && distanceKm > 0.05f
    val localX: Float get() = car.x - segment.worldOrigin
    /** Najbližší vrak s lootom pred autom – pre včasné brzdenie. */
    val lootableWreckAheadDistanceM: Int?
        get() = segment.buildings
            .asSequence()
            .filter { it.type == BuildingType.WRECK && it.loot.isNotEmpty() }
            .map { (it.localX - localX).toInt() }
            // Upozornenie má pomôcť s brzdením, nie ukazovať vrak dva kilometre
            // dopredu a zahlcovať obrazovku počas celej jazdy.
            .filter { it in 3..90 }
            .minOrNull()
    val inJunctionZone: Boolean get() = false

    /** Koľko metrov zostáva k rázcestiu. */
    val junctionDistanceM: Float get() = (segment.endWorldX - car.x).coerceAtLeast(0f)

    /** true, kým sa dá vetva vybrať za jazdy (posledných pár sto metrov). */
    val approachingJunction: Boolean get() = false

    /** Aktuálne prelínanie prostredia; renderer aj fyzika čítajú tú istú hodnotu. */
    val biomeBlend get() = segment.biomeBlendAtWorld(car.x)
    private val nextPaving get() = segment.choices.firstOrNull()?.plan?.paving
    val winterAmount: Float
        get() {
            val from = if (segment.paving.winter) 1f else 0f
            val to = if (nextPaving?.winter == true) 1f else 0f
            return MathX.lerp(from, to, biomeBlend.amount)
        }

    /** 0 = normálne počasie, 1 = poriadny mráz. Riadi ho zasnežená vetva. */
    val cold: Float
        get() {
            if (winterAmount <= 0.01f) return 0f
            // V noci mrzne viac než cez deň.
            return winterAmount * (0.75f + 0.25f * (1f - daylight))
        }

    /** true = jazdí sa v zime (sneh pod kolesami), nie odhad ďalšieho regiónu. */
    val isWinter: Boolean get() = segment.paving.winter

    /** Povrch pod kolesami – asfalt, bahno, piesok, voda, štrk. */
    var currentSurface: RoadSurface = RoadSurface.ASPHALT
        private set

    /**
     * Naplavenina pred autom. Hysterézia: objaví sa do 50 m, zmizne až
     * po prejdení alebo keď je ďalej ako 75 m — prah 60 m blikal na hranici.
     */
    val patchAhead: SurfacePatch?
        get() = if (phase != GamePhase.DRIVING) null else heldPatchAhead

    private var heldPatchAhead: SurfacePatch? = null

    private fun refreshPatchAhead() {
        if (phase != GamePhase.DRIVING) {
            heldPatchAhead = null
            return
        }
        val next = segment.patchAheadOfLocal(localX, PATCH_AHEAD_HIDE_M)
        val held = heldPatchAhead
        heldPatchAhead = when {
            next != null && next.start - localX <= PATCH_AHEAD_SHOW_M -> next
            held != null && held.start > localX && held.start - localX <= PATCH_AHEAD_HIDE_M -> held
            else -> null
        }
    }

    /** Vetva, do ktorej sa auto zaradí na rázcestí (null = ešte nevybraté). */
    var pendingChoiceId: Int? = null
        private set

    /**
     * Zastavenie pri rázcestí otvára panel len vtedy, keď hráč ešte nevolil.
     * Kto si vetvu vybral za jazdy, môže pri odbočke normálne lootovať.
     */
    private val needsJunctionCall: Boolean get() = false

    fun setScreenHeight(px: Float) {
        screenHeightPx = px.coerceAtLeast(320f)
    }

    fun advance(frameDt: Float) {
        if (timeHeldByMenu) return
        val dt = frameDt.coerceAtMost(GameConfig.MAX_FRAME_TIME)
        accumulator += dt
        var steps = 0
        // Päť krokov stačilo iba od ~12 FPS vyššie. Na slabšom zariadení
        // potom tachometer ukazoval správnu okamžitú rýchlosť, ale herný čas
        // a kilometre bežali pomalšie než skutočný čas. MAX_FRAME_TIME je
        // 0.25 s = presne 15 bezpečných krokov, takže celý prijatý frame
        // dobehneme bez straty fyzikálneho času.
        while (accumulator >= GameConfig.FIXED_TIME_STEP &&
            steps < GameConfig.MAX_FIXED_STEPS_PER_FRAME
        ) {
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
        car.setCargoLoad(inventory.totalWeight, boot.totalWeight)
        messageAge += dt
        if (phase != GamePhase.GAME_OVER) {
            timeOfDay = DayCycle.advance(timeOfDay, dt)
            if (tickElectrics(dt)) return
        }
        when (phase) {
            GamePhase.PREP -> updatePrep(dt)
            GamePhase.DRIVING -> updateDriving(dt)
            GamePhase.STOPPED, GamePhase.EXPLORING, GamePhase.JUNCTION -> {
                // Exponenciálne dobrzdenie sa nuly nikdy nedotkne, takže auto
                // donekonečna popolzávalo dopredu a dozadu. Pod prahom ho
                // zastavíme natvrdo – stojace auto má stáť.
                car.speed = MathX.lerp(car.speed, 0f, dt * 5f)
                if (kotlin.math.abs(car.speed) < 0.05f) car.speed = 0f
                syncRide(dt)
            }
            GamePhase.GAME_OVER -> Unit
        }
        if (phase != GamePhase.DRIVING) updateBlockedReason(dt)
    }

    /** Svetlá a batéria bežia vo všetkých fázach. Vracia true, ak jazda skončila. */
    private fun tickElectrics(dt: Float): Boolean {
        if (!hasExpeditionKit) roofLightsRequested = false
        val cause = car.tickElectrics(
            dt,
            headlightsOn || roofLightsOn,
            headlightDrainMultiplier = (if (headlightsOn) {
                if (highBeamsOn) GameConfig.HIGH_BEAM_DRAIN_MULTIPLIER else 1f
            } else 0f) + (if (roofLightsOn) GameConfig.ROOF_LIGHT_DRAIN_MULTIPLIER else 0f),
            charging = !hasEvent(RoadEvent.BELT_SNAPPED),
            cold = cold
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

    /** Cyklicky: vypnuté → stretávacie → diaľkové → vypnuté. */
    fun toggleHeadlights(): Boolean {
        // Bez svetlometu nie je čo rozsvietiť. Plná batéria ani bežiaci
        // alternátor na tom nič nezmenia – žiarovka je v tom kuse plastu.
        if (!headlightsOn && !car.hasPart(ComponentSlot.HEADLIGHT)) {
            message = "No headlight fitted — find one first"
            return false
        }
        when {
            !headlightsOn -> {
                headlightsOn = true
                highBeamsOn = false
            }
            !highBeamsOn -> highBeamsOn = true
            else -> {
                headlightsOn = false
                highBeamsOn = false
            }
        }
        message = if (headlightsOn) {
            when {
                !car.hasPart(ComponentSlot.ALTERNATOR) ->
                    "${if (highBeamsOn) "High beams" else "Low beams"} — no alternator, battery will drain"
                car.batteryCharge < 0.25f && !car.engineRunning ->
                    "${if (highBeamsOn) "High beams" else "Low beams"} — careful, the battery is weak"
                highBeamsOn -> "High beams on"
                else -> "Low beams on"
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

    /**
     * Najvyšší povrch pozdĺž posunu nápravy. Bez List/Pair – volá sa každý
     * fyzikálny krok.
     */
    private fun sweepAxle(axleX: Float, travel: Float, minBound: Float, maxBound: Float) {
        val sampleCount = (kotlin.math.abs(travel) / 0.16f).toInt().coerceIn(2, 10)
        var bestX = axleX
        var bestH = Float.NEGATIVE_INFINITY
        for (index in 0..sampleCount) {
            val wx = (axleX + travel * index / sampleCount).coerceIn(minBound - 8f, maxBound + 8f)
            val h = segment.heightAtWorld(wx)
            if (h > bestH) {
                bestH = h
                bestX = wx
            }
        }
        sweepGroundY = bestH
        val local = (bestX - segment.worldOrigin)
            .coerceIn(0.55f, segment.length - 0.55f)
        sweepSlopeRad = segment.slopeAtLocal(local)
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
                "Fluids are ${FluidGrade.of(worst).displayName.lowercase()} — find cleaner fuel, oil or coolant."
            } else {
                "Engine running. Press DRIVE."
            }
        }
    }

    private fun updateDriving(dt: Float) {
        elapsed += dt
        // Ďalší región vznikne ešte pred začiatkom viditeľného prelínania.
        // Práca s budovami a lootom sa tak nikdy nestretne s technickou hranicou.
        if (preparedContinuation == null &&
            car.x >= segment.transitionStartWorldX - CONTINUATION_PRELOAD_LEAD
        ) {
            prepareContinuation()
        }
        val fuelBefore = car.fuel
        val slopeNow = segment.slopeAtLocal(localX.coerceIn(0f, segment.length - 0.5f))
        tickEvents(dt)
        val nextStyle = segment.nextStyle
        val regionDrain = if (nextStyle == null) segment.style.fuelDrainMul else MathX.lerp(
            segment.style.fuelDrainMul,
            nextStyle.fuelDrainMul,
            biomeBlend.amount
        )
        val cause = car.tickDriving(dt, throttleInput, regionDrain * eventDrainMul, slopeNow, cold)
        fuelBurnedL += (fuelBefore - car.fuel).coerceAtLeast(0f)
        if (cause != null) {
            stallOrEnd(cause)
            return
        }
        // Zhasnuté auto dojazdí zotrvačnosťou. tickDriving() už žiadnu príčinu
        // nevráti (motor nebeží), takže prechod do STOPPED musí spraviť tento
        // krok – inak by jazda ostala navždy vo fáze DRIVING.
        if (!car.engineRunning && kotlin.math.abs(car.speed) < GameConfig.STOP_SPEED) {
            car.speed = 0f
            phase = GamePhase.STOPPED
            return
        }

        // Cez rázcestie sa dá prejsť plynulo – zastavuje len ten, kto chce.
        val maxX = if (segment.choices.isEmpty()) segment.endWorldX - 0.5f
        else segment.endWorldX + GameConfig.JUNCTION_APPROACH
        val minX = maxOf(segment.worldOrigin - 2f, maxReachedX - GameConfig.REVERSE_LIMIT)
        val cx = car.x.coerceIn(minX, maxX)
        val wb = SedanSpec.wheelOffsetX
        // Swept kontakt: pri 140 km/h prejde koleso za snímku vyše pol metra.
        // Vzorkujeme začiatok, polovicu aj koniec kroku a vezmeme najvyšší
        // povrch, aby úzky hrbol neprepadol medzi dve fyzikálne snímky.
        val travel = car.speed * dt
        sweepAxle(cx - wb, travel, minX, maxX)
        val rearGy = sweepGroundY
        val rearSlope = sweepSlopeRad
        sweepAxle(cx + wb, travel, minX, maxX)
        val frontGy = sweepGroundY
        val frontSlope = sweepSlopeRad
        // Každá náprava môže byť na inej strane hrebeňa. Jeden spoločný sklon
        // pridával zadnému kolesu falošnú vertikálnu rýchlosť a vystreľoval ho.
        val bumpiness = segment.bumpinessAtLocal(localX.coerceIn(0f, segment.length - 0.5f))
        currentSurface = segment.surfaceAtLocal(localX.coerceIn(0f, segment.length - 0.5f))
        refreshPatchAhead()
        car.applyDrive(
            dt,
            throttleInput * eventThrottleMul,
            brakeInput,
            rearGy,
            frontGy,
            bumpiness + eventBumpBonus,
            currentSurface,
            nextPaving?.let { MathX.lerp(segment.paving.gripMul, it.gripMul, biomeBlend.amount) }
                ?: segment.paving.gripMul,
            isWinter,
            rearGroundSlope = rearSlope,
            frontGroundSlope = frontSlope,
            windAcceleration = eventWindAcceleration
        )
        // applyDrive posunie X až po výpočte pruženia. Preto kontakt ešte raz
        // vyriešime presne pod finálnou polohou auta; pri vysokej rýchlosti
        // nesmie zostať ani jediná snímka medzi predikciou a skutočnou cestou.
        val finalRearX = car.x - wb
        val finalFrontX = car.x + wb
        fun exactSlope(worldX: Float): Float {
            val local = (worldX - segment.worldOrigin)
                .coerceIn(0.55f, segment.length - 0.55f)
            return segment.slopeAtLocal(local)
        }
        car.resolveRoadPenetration(
            segment.heightAtWorld(finalRearX),
            segment.heightAtWorld(finalFrontX),
            exactSlope(finalRearX),
            exactSlope(finalFrontX),
            bodyGroundAtOffset = { offset -> segment.heightAtWorld(car.x + offset) }
        )
        car.tickWear(dt, bumpiness + eventBumpBonus, brakeInput, winterRoad = isWinter)
        tickTireHazards(dt)
        applyFeatureEffects(dt)
        if (car.x > maxX) {
            car.x = maxX
            if (car.speed > 0f) car.speed = 0f
        }
        // Región sa na hranici len technicky vymení. Vizuál a terén sa menili
        // už kilometre pred ňou, preto tu nesmie byť zastavenie ani teleport.
        if (car.x >= segment.endWorldX && segment.choices.isNotEmpty()) {
            enterBranch(segment.choices.first())
            return
        }
        if (car.x < minX) {
            car.x = minX
            if (car.speed < 0f) car.speed = 0f
        }
        if (car.x > maxReachedX) maxReachedX = car.x

        distanceM = maxReachedX.coerceAtLeast(0f)
        if (distanceM >= Journey.FINAL_DISTANCE_M) {
            if (relayProgress >= Journey.goals.size) {
                endRun(EndReason.ARRIVED)
            } else {
                car.speed = 0f
                phase = GamePhase.STOPPED
                message = "The safe haven relay is still offline — restore it before finishing"
            }
            return
        }
        warnAboutEngineWear(dt)

        run {
            // Teplotu a palivo rieši pás výstrah v HUD – tu by z toho boli
            // len donekonečna sa opakujúce hlášky.
            val near = buildingNear()
            if (near != null && car.speed < GameConfig.STOP_SPEED * 2.5f) {
                message = if (near.landmark) {
                    if (near.relayRestored) "RELAY ONLINE — fuel and parts"
                    else "RELAY STATION — fuel, parts and signal"
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
                step >= 3 -> "ENGINE ABOUT TO DIE: ${(health * 100).toInt()} % — ${cause?.warning ?: "fix it"}"
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
            car.wrongFuelFraction >= 0.70f ->
                "Wrong fuel — drain the tank and fill with ${car.requiredFuelKind.displayName.lowercase()}"
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
            // Úsek sa neohlasuje textom. Rady typu „drž sa v strede“ predpokladali
            // riadenie, ktoré hra nemá – ide sa dopredu a dozadu, nič iné.
            currentFeature = feature
        }
        updateBlockedReason(dt)

        // Hodnota úseku je bezpečná rýchlosť pre opotrebenie, nie limiter.
        // Hráč môže ísť rýchlejšie; rozbitá cesta to zaplatí na gumách a pružení.
        val cap = feature.speedCap
        if (feature.roughness > 0.5f && car.speed > cap * 0.6f) {
            val over = ((car.speed - cap * 0.6f) / GameConfig.MAX_SPEED).coerceIn(0f, 1f)
            TIRE_SLOTS.forEach { slot ->
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

    /** Vráti a vyprázdni frontu jednorazových zvukov. */
    fun consumeSfx(): List<GameSfx> {
        if (sfxQueue.isEmpty()) return emptyList()
        val out = sfxQueue.toList()
        sfxQueue.clear()
        return out
    }

    private fun emitSfx(sfx: GameSfx) {
        if (sfxQueue.size < 12) sfxQueue.addLast(sfx)
    }

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

    /** Násobiteľ plynu – misfire a protivietor uberú výkon. */
    private val eventThrottleMul: Float
        get() = (if (hasEvent(RoadEvent.MISFIRE)) 0.55f else 1f) *
            (if (hasEvent(RoadEvent.HEADWIND)) 0.92f else 1f)

    /** Násobiteľ spotreby – vietor mení cenu jazdy, čistá cesta šetrí. */
    private val eventDrainMul: Float
        get() = (if (hasEvent(RoadEvent.HEADWIND)) 1.22f else 1f) *
            (if (hasEvent(RoadEvent.TAILWIND)) 0.72f else 1f) *
            (if (hasEvent(RoadEvent.CLEAR_ROAD)) 0.8f else 1f)

    /** Malá sila pozdĺž cesty; protivietor brzdí, zadný vietor pomáha. */
    private val eventWindAcceleration: Float
        get() = (if (hasEvent(RoadEvent.HEADWIND)) -0.65f else 0f) +
            (if (hasEvent(RoadEvent.TAILWIND)) 0.55f else 0f)

    /** Extra hrboľatosť z počasia a prekážok – zhorší záber aj zrýchlenie. */
    private val eventBumpBonus: Float
        get() = (if (hasEvent(RoadEvent.RAIN)) 0.45f else 0f) +
            (if (hasEvent(RoadEvent.MUD)) 0.8f else 0f) +
            (if (hasEvent(RoadEvent.DEBRIS)) 0.35f else 0f)

    /**
     * Vylosuje udalosť. Čím ďalej hráč je, tým väčšia šanca na smolu –
     * blízko štartu ho hra nedrví.
     */
    private fun rollEvent() {
        val rng = SeededRandom(
            seed xor distanceM.toRawBits().toLong() xor (elapsed * 1000f).toLong()
        )
        val hardness = MathX.growth(distanceM, 40_000f).coerceAtMost(2.8f)
        val pool = RoadEvent.entries.filter { canHappen(it) && it != lastRoadEvent }
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
        lastRoadEvent = chosen
        trigger(chosen, rng)
    }

    private var lastRoadEvent: RoadEvent? = null

    /**
     * Poškodenie musí byť dôsledok, nie kocka. Defekt a zásah kameňom sa preto
     * viažu na to, ako sa práve ide – rýchlo po rozbitom či sypkom povrchu.
     * Ako čistý náhodný trest boli tieto udalosti len daňou bez obrany.
     */
    private fun canHappen(e: RoadEvent): Boolean = when (e) {
        // Nemá zmysel prepichnúť gumu, ktorá tam nie je, a bez rizika
        // (rýchlosť + povrch + typ gumy) to nie je trest z kocky.
        RoadEvent.FLAT_TYRE -> TIRE_SLOTS.any { slot ->
            val part = car.parts[slot] ?: return@any false
            part.injury == TireInjury.INFLATED &&
                TireSim.canPuncture(part.health) &&
                kotlin.math.abs(car.speed) > 8f &&
                TireSim.risk(
                    part.def,
                    part.health,
                    car.speed,
                    hasEvent(RoadEvent.DEBRIS),
                    currentSurface,
                    currentFeature,
                    roughnessNow()
                ) > 0.55f
        }
        RoadEvent.ROCK_STRIKE -> car.hasPart(ComponentSlot.RADIATOR) &&
            kotlin.math.abs(car.speed) > 8f &&
            (roughnessNow() > 0.2f || currentSurface == RoadSurface.GRAVEL)
        RoadEvent.BELT_SNAPPED -> car.hasPart(ComponentSlot.ALTERNATOR)
        RoadEvent.COOLANT_LEAK -> car.coolant > 1f
        RoadEvent.FUEL_LEAK -> car.fuel > 4f
        RoadEvent.HEADWIND, RoadEvent.TAILWIND, RoadEvent.CLEAR_ROAD -> !inJunctionZone
        else -> true
    } && !hasEvent(e) && (e.good || e.chip == null ||
        events.count { !it.event.good } < 2)

    /** Hrboľatosť pod autom – vetva plus aktuálny úsek trate. */
    private fun roughnessNow(): Float =
        segment.bumpinessAtLocal(car.x - segment.worldOrigin)

    private fun trigger(e: RoadEvent, rng: SeededRandom) {
        seenEventKinds += e
        var customMessage: String? = null
        when (e) {
            RoadEvent.FLAT_TYRE -> {
                val options = TIRE_SLOTS.filter { slot ->
                    val part = car.parts[slot]
                    part != null &&
                        part.injury == TireInjury.INFLATED &&
                        TireSim.canPuncture(part.health)
                }
                if (options.isNotEmpty()) {
                    customMessage = injureTire(options[rng.nextInt(options.size)], rng)
                }
            }
            RoadEvent.ROCK_STRIKE -> car.parts[ComponentSlot.RADIATOR]?.let {
                it.health = (it.health - rng.nextFloat(0.15f, 0.3f)).coerceAtLeast(0.08f)
            }
            RoadEvent.OIL_SPLASH -> {
                // Špina v oleji – objem ostane, kvalita klesne. Zásah musí byť
                // malý: dolievanie čistotu len mieša, takže veľké skoky sa
                // nasčítali do stavu, z ktorého sa hráč nemal ako dostať.
                car.oilPurity = (car.oilPurity - rng.nextFloat(0.04f, 0.11f)).coerceAtLeast(0.05f)
            }
            RoadEvent.BELT_SNAPPED -> car.parts[ComponentSlot.ALTERNATOR]?.let {
                it.health = (it.health - 0.2f).coerceAtLeast(0.05f)
            }
            RoadEvent.ROADSIDE_STASH -> customMessage = dropRoadsideFind(rng, parts = false)
            RoadEvent.ABANDONED_WRECK -> customMessage = dropRoadsideFind(rng, parts = true)
            else -> Unit
        }
        if (e != RoadEvent.FLAT_TYRE) emitSfx(e.toSfx())
        if (e.timed) events += ActiveEvent(e, e.duration)
        // Timed events are visible through their effect/icons, not a temporary
        // banner that competes with the critical red warnings.
        if (!e.timed) message = customMessage ?: e.message
    }

    /**
     * Defekt a roztrhnutie podľa gumy, rýchlosti a toho, čo je na ceste.
     * Šport po konároch to odnesie skôr než off-road; pomalá jazda takmer vôbec.
     */
    private fun tickTireHazards(dt: Float) {
        if (!car.grounded && !car.visuallyGrounded) return
        tireHazardAcc += dt
        if (tireHazardAcc < 0.16f) return
        val step = tireHazardAcc
        tireHazardAcc = 0f
        val debris = hasEvent(RoadEvent.DEBRIS)
        val rng = SeededRandom(
            seed xor distanceM.toRawBits().toLong() xor
                (elapsed * 997f).toLong() xor 0x71E15L
        )
        TIRE_SLOTS.forEach { slot ->
            val part = car.parts[slot] ?: return@forEach
            if (part.injury == TireInjury.SHREDDED) return@forEach
            val risk = TireSim.risk(
                part.def,
                part.health,
                car.speed,
                debris,
                currentSurface,
                currentFeature,
                roughnessNow()
            )
            if (part.injury == TireInjury.PUNCTURED) {
                // Už defektná guma sa môže roztrhnúť aj po scrap oprave dezénu.
                val p = TireSim.punctureChance(risk, step)
                if (p > 0f && rng.nextFloat() <= p) injureTire(slot, rng)
                return@forEach
            }
            val p = TireSim.punctureChance(risk, step, part.health)
            if (p <= 0f || rng.nextFloat() > p) return@forEach
            injureTire(slot, rng)
        }
    }

    /**
     * Poškodí gumu na náprave. Vráti hlášku pre hráča, alebo null ak sa
     * stav nezmenil (už je na ráfiku).
     */
    private fun injureTire(
        slot: ComponentSlot,
        rng: SeededRandom,
        force: TireInjury? = null
    ): String? {
        val part = car.parts[slot] ?: return null
        if (part.injury == TireInjury.SHREDDED) return null
        val debris = hasEvent(RoadEvent.DEBRIS)
        val shred = force == TireInjury.SHREDDED ||
            (force == null && rng.nextFloat() < TireSim.shredChance(
                part.def,
                car.speed,
                debris,
                part.injury == TireInjury.PUNCTURED
            ))
        val next = when {
            force == TireInjury.PUNCTURED && part.injury == TireInjury.INFLATED ->
                TireInjury.PUNCTURED
            shred || part.injury == TireInjury.PUNCTURED -> TireInjury.SHREDDED
            else -> TireInjury.PUNCTURED
        }
        if (next == part.injury) return null
        part.injury = next
        if (next == TireInjury.SHREDDED) {
            part.health = part.health.coerceAtMost(0.10f)
        } else {
            part.health = (part.health - rng.nextFloat(0.12f, 0.28f)).coerceAtLeast(0.05f)
        }
        val axle = if (slot == ComponentSlot.TIRE_FRONT) "front" else "rear"
        val text = if (next == TireInjury.SHREDDED) {
            "Blowout — the $axle tyre is shredded."
        } else {
            "Puncture — the $axle tyre is going flat."
        }
        message = text
        emitSfx(GameSfx.BLOWOUT)
        return text
    }

    private fun RoadEvent.toSfx(): GameSfx = when (this) {
        RoadEvent.FLAT_TYRE -> GameSfx.BLOWOUT
        RoadEvent.ROCK_STRIKE -> GameSfx.ROCK
        RoadEvent.FUEL_LEAK -> GameSfx.FUEL_LEAK
        RoadEvent.COOLANT_LEAK -> GameSfx.COOLANT_LEAK
        RoadEvent.MISFIRE -> GameSfx.MISFIRE
        RoadEvent.BELT_SNAPPED -> GameSfx.BELT
        RoadEvent.OIL_SPLASH -> GameSfx.OIL_SPLASH
        RoadEvent.RAIN -> GameSfx.RAIN
        RoadEvent.DEBRIS -> GameSfx.DEBRIS
        RoadEvent.MUD -> GameSfx.MUD
        RoadEvent.HEADWIND, RoadEvent.TAILWIND -> GameSfx.TAILWIND
        RoadEvent.CLEAR_ROAD -> GameSfx.CLEAR_ROAD
        RoadEvent.ROADSIDE_STASH, RoadEvent.ABANDONED_WRECK -> GameSfx.FIND
    }

    /** Nález pri ceste – vznikne budova v dosahu, aby sa dal zobrať. */
    private fun dropRoadsideFind(rng: SeededRandom, parts: Boolean): String {
        // Ďaleko pred autom, nie vedľa neho: pri 6–14 m sa budova zjavila
        // priamo v zábere. Takto na ňu hráč dobehne a stihne zabrzdiť.
        val find = WorldBuilding(
            id = seed xor (distanceM.toRawBits().toLong() * 31L),
            type = if (parts) BuildingType.WRECK else BuildingType.HOUSE,
            localX = (car.x - segment.worldOrigin) + rng.nextFloat(70f, 130f)
        )
        if (parts) {
            if (rng.chance(0.55f)) {
                find.loot.add(
                    ItemStack(
                        defId = ItemCatalog.SCRAP_PILE.id,
                        condition = ComponentCondition.NEW,
                        health = 1f,
                        count = 1 + rng.nextInt(3)
                    )
                )
            }
            val pool = listOf(
                ItemCatalog.TIRE_POOR, ItemCatalog.TIRE, ItemCatalog.BATTERY, ItemCatalog.RADIATOR,
                ItemCatalog.BRAKES, ItemCatalog.ALTERNATOR, ItemCatalog.STARTER,
                ItemCatalog.HOOD, ItemCatalog.DOOR_FRONT, ItemCatalog.DOOR_REAR
            )
            repeat(1 + rng.nextInt(2)) {
                val def = rng.pick(pool)
                find.loot.add(ItemStack(def.id, ComponentCondition.DAMAGED, rng.nextFloat(0.3f, 0.7f)))
            }
        } else {
            val def = if (rng.chance(0.6f)) {
                if (car.requiredFuelKind == FuelKind.DIESEL) ItemCatalog.DIESEL_CAN
                else ItemCatalog.FUEL_CAN
            }
            else rng.pick(
                listOf(
                    ItemCatalog.OIL_BOTTLE,
                    ItemCatalog.COOLANT_BOTTLE,
                    ItemCatalog.WATER,
                    ItemCatalog.PUNCTURE_KIT
                )
            )
            find.loot.add(
                ItemStack(
                    defId = def.id,
                    condition = ComponentCondition.NEW,
                    health = 1f,
                    count = 1,
                    purity = rng.nextFloat(0.55f, 0.95f)
                )
            )
            segment.buildings += find
            return "${def.name} ahead — stop to pick it up"
        }
        segment.buildings += find
        return "Wreck ahead — scrap and parts"
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
    /** Napojí ďalší segment tak, aby jazda pokračovala bez švíku. */
    private fun enterBranch(choice: BranchChoice) {
        val origin = segment.endWorldX
        segmentTripDistance = origin
        val ready = preparedContinuation?.takeIf {
            it.seed == choice.plan.seed && kotlin.math.abs(it.worldOrigin - origin) < 0.01f
        }
        segment = ready ?: WorldGenerator.createSegment(
            plan = choice.plan,
            worldOrigin = origin,
            tripDistance = origin,
            terrain = terrain,
            isTutorial = false
        )
        applyRelayProgress(segment)
        preparedContinuation = null
        pendingChoiceId = null
        rescueStashId = null
        currentFeature = segment.featureAtWorld(car.x)
        // Rýchlosť, poloha ani pruženie sa nemenia – žiadny teleport či snap.
        activeBuilding = null
        phase = GamePhase.DRIVING
        heldPatchAhead = null
        // Bez novej hlášky a premerania HUD: hráč už zmenu regiónu videl
        // počas prelínania a technická hranica má byť úplne neviditeľná.
    }

    /** Vytvorí jediné pokračovanie vopred; opakované volanie je lacné. */
    private fun prepareContinuation() {
        val choice = segment.choices.singleOrNull() ?: return
        val origin = segment.endWorldX
        if (preparedContinuation?.let {
                it.seed == choice.plan.seed && kotlin.math.abs(it.worldOrigin - origin) < 0.01f
            } == true
        ) return
        preparedContinuation = WorldGenerator.createSegment(
            plan = choice.plan,
            worldOrigin = origin,
            tripDistance = origin,
            terrain = terrain,
            isTutorial = false
        )
        applyRelayProgress(preparedContinuation!!)
    }

    fun requestStop(): Boolean {
        if (phase != GamePhase.DRIVING) return false
        if (car.speed > GameConfig.STOP_SPEED * 3f) {
            message = "Slow down first"
            return false
        }
        car.speed = 0f
        phase = GamePhase.STOPPED
        message = "Stopped"
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
        activeBuilding = null
        prepStep = PrepStep.DONE
        car.prepChecklistDone = true
        phase = GamePhase.DRIVING
        message = "Driving"
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

    /** Cena opravy rastie spolu s tým, ako hlboko je hráč na expedícii. */
    fun relayRestoreCost(relayIndex: Int): Int =
        Journey.relayRestoreCost(relayIndex)

    fun relayModuleCount(): Int = countRelayModules(inventory) + countRelayModules(boot)

    fun relayActivationReady(relayIndex: Int): Boolean {
        val b = activeBuilding ?: return false
        return relayIndex in Journey.goals.indices &&
            phase == GamePhase.EXPLORING && b.landmark && !b.relayRestored &&
            relayIndex == relayProgress &&
            (b.relayIndex < 0 || b.relayIndex == relayIndex) &&
            scrap >= relayRestoreCost(relayIndex) && relayModuleCount() > 0
    }

    /**
     * Obnoví konkrétny uzol siete. Vzdialenosť iba určuje poradie cieľa;
     * samotný progres vznikne až po fyzickom loote a aktivácii.
     */
    fun activateDepot(relayIndex: Int): Boolean {
        val b = activeBuilding
        if (phase != GamePhase.EXPLORING || b == null || !b.landmark) {
            message = "Enter a relay station first"
            return false
        }
        if (relayIndex !in Journey.goals.indices) {
            message = "The radio network is fully restored"
            return false
        }
        if (relayIndex != relayProgress) {
            message = if (relayIndex < relayProgress) {
                "This relay is already online"
            } else {
                "Restore the previous relay first"
            }
            return false
        }
        if (b.relayRestored || (b.relayIndex >= 0 && b.relayIndex < relayIndex)) {
            message = "This relay is already online"
            return false
        }
        if (b.relayIndex >= 0 && b.relayIndex != relayIndex) {
            message = "Restore the previous relay first"
            return false
        }
        val cost = relayRestoreCost(relayIndex)
        if (scrap < cost) {
            message = "Need $cost scrap — you have $scrap"
            return false
        }
        if (relayModuleCount() <= 0) {
            message = "Find a relay module before restoring this station"
            return false
        }

        if (!consumeRelayModule()) return false
        scrap -= cost
        b.relayRestored = true
        emitSfx(GameSfx.RADIO)
        message = "Relay ${relayIndex + 1}/${Journey.goals.size} restored — signal back online"
        return true
    }

    private fun countRelayModules(source: Inventory): Int = source.slots.sumOf { stack ->
        if (stack?.defId == ItemCatalog.RELAY_MODULE.id) stack.count.coerceAtLeast(1) else 0
    }

    private fun consumeRelayModule(): Boolean {
        fun takeFrom(source: Inventory): Boolean {
            val index = source.slots.indexOfFirst { it?.defId == ItemCatalog.RELAY_MODULE.id }
            if (index < 0) return false
            val stack = source.slots[index] ?: return false
            stack.count--
            if (stack.count <= 0) source.removeAt(index)
            return true
        }
        return takeFrom(inventory) || takeFrom(boot)
    }

    /** Synchronizuje vizuálny stav známych uzlov s trvalým profilom hráča. */
    fun setRelayProgress(relayNodes: Int) {
        relayProgress = relayNodes.coerceIn(0, Journey.goals.size)
        applyRelayProgress(segment)
        preparedContinuation?.let(::applyRelayProgress)
    }

    private fun applyRelayProgress(target: RoadSegment) {
        target.buildings
            .filter { it.landmark && it.relayIndex in 0 until relayProgress }
            .forEach { it.relayRestored = true }
    }

    /** Jednorazová núdzová pomoc z rewarded reklamy. */
    val rescueAdLabel: String?
        get() = when (endReason) {
            EndReason.OUT_OF_FUEL -> "FUEL +12 L · WATCH AD"
            EndReason.BATTERY_DEAD -> if (car.hasPart(ComponentSlot.BATTERY)) {
                "BATTERY CHARGE +50 % · WATCH AD"
            } else {
                "BATTERY +50 % · WATCH AD"
            }
            EndReason.ENGINE_DESTROYED -> "ENGINE +50 % · WATCH AD"
            EndReason.OVERHEAT -> when {
                !car.hasPart(ComponentSlot.RADIATOR) -> "RADIATOR +50 % · WATCH AD"
                car.coolant < 0.3f -> "COOLANT +3 L · WATCH AD"
                else -> "ENGINE +50 % · WATCH AD"
            }
            EndReason.ARRIVED, EndReason.MANUAL, null -> null
        }

    fun recoverFromRewardedAd(): Boolean {
        if (phase != GamePhase.GAME_OVER) return false
        val reason = endReason ?: return false
        val detail = when (reason) {
            EndReason.OUT_OF_FUEL -> {
                if (!car.hasPart(ComponentSlot.FUEL_TANK)) {
                    car.mount(
                        ComponentSlot.FUEL_TANK,
                        ItemStack(ItemCatalog.FUEL_TANK.id, ComponentCondition.DAMAGED, 0.50f)
                    )
                }
                car.refill(FluidType.FUEL, 12f, 0.88f, car.requiredFuelKind)
                "Roadside help delivered 12 L of fuel"
            }
            EndReason.ENGINE_DESTROYED -> {
                if (car.hasPart(ComponentSlot.ENGINE)) {
                    car.repair(ComponentSlot.ENGINE, 0.50f)
                } else {
                    car.mount(
                        ComponentSlot.ENGINE,
                        ItemStack(ItemCatalog.ENGINE_A.id, ComponentCondition.DAMAGED, 0.50f)
                    )
                }
                "Roadside help restored 50 % engine health"
            }
            EndReason.OVERHEAT -> {
                val missingRadiator = !car.hasPart(ComponentSlot.RADIATOR)
                val lowCoolant = car.coolant < 0.3f
                when {
                    missingRadiator -> car.mount(
                        ComponentSlot.RADIATOR,
                        ItemStack(ItemCatalog.RADIATOR.id, ComponentCondition.DAMAGED, 0.50f)
                    )
                    lowCoolant -> car.refill(FluidType.COOLANT, 3f, 0.75f)
                    else -> car.repair(ComponentSlot.ENGINE, 0.50f)
                }
                car.temperature = 55f
                when {
                    missingRadiator ->
                        "Roadside help fitted a radiator at 50 % health"
                    lowCoolant -> "Roadside help delivered 3 L of coolant"
                    else -> "Roadside help restored 50 % engine health and cooled it"
                }
            }
            EndReason.BATTERY_DEAD -> {
                if (!car.hasPart(ComponentSlot.BATTERY)) {
                    car.mount(
                        ComponentSlot.BATTERY,
                        ItemStack(ItemCatalog.BATTERY.id, ComponentCondition.DAMAGED, 0.50f)
                    )
                } else {
                    car.repair(ComponentSlot.BATTERY, 0.50f)
                }
                car.batteryCharge = minOf(car.batteryHoldCapacity, 0.50f)
                if (car.batteryHoldCapacity >= 0.50f) {
                    "Roadside help restored 50 % battery charge"
                } else {
                    "Roadside help fitted a battery and charged it"
                }
            }
            EndReason.ARRIVED -> return false
            EndReason.MANUAL -> return false
        }
        endReason = null
        endDetail = ""
        phase = GamePhase.STOPPED
        prepStep = PrepStep.DONE
        car.prepChecklistDone = true
        car.stopEngine()
        car.speed = 0f
        message = detail
        return true
    }

    /** Natankuje vybranú hadicu priamo z pumpy (obe majú vlastnú zásobu). */
    fun refuelFromPump(kind: FuelKind = FuelKind.PETROL): Boolean {
        if (!car.hasPart(ComponentSlot.FUEL_TANK)) {
            message = "Fit a fuel tank before using the pump"
            return false
        }
        val b = activeBuilding ?: run {
            message = "Enter a fuel station first"
            return false
        }
        if (b.type != BuildingType.GAS_STATION) {
            message = "There is no pump here"
            return false
        }
        val available = if (kind == FuelKind.DIESEL) b.pumpDieselL else b.pumpFuelL
        if (available <= 0.05f) {
            message = if (b.type == BuildingType.GAS_STATION) {
                "The ${kind.displayName.lowercase()} pump is empty"
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
        val moved = car.refill(
            FluidType.FUEL,
            kotlin.math.min(room, available),
            b.pumpPurity,
            kind
        )
        if (kind == FuelKind.DIESEL) {
            b.pumpDieselL = (b.pumpDieselL - moved).coerceAtLeast(0f)
        } else {
            b.pumpFuelL = (b.pumpFuelL - moved).coerceAtLeast(0f)
        }
        val remaining = if (kind == FuelKind.DIESEL) b.pumpDieselL else b.pumpFuelL
        message = "Filled up +${String.format("%.1f", moved)} L ${kind.displayName.lowercase()} · " +
            "${FluidGrade.of(b.pumpPurity).displayName.lowercase()} (pump has ${String.format("%.0f", remaining)} L)"
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
        phase = GamePhase.STOPPED
        message = "Back at the car"
    }

    fun takeLoot(index: Int): Boolean {
        val b = activeBuilding ?: return false
        if (index !in b.loot.indices) return false
        val item = b.loot[index]
        if (item.defId == ItemCatalog.SCRAP_PILE.id) {
            val gained = item.count.coerceAtLeast(1)
            scrap += gained
            b.loot.removeAt(index)
            itemsLooted += 1
            message = "Salvaged +$gained scrap"
            return true
        }
        // Nájdené ide do batoha; keď je plný, prepadne rovno do kufra, ak je
        // auto na dosah. Nútiť hráča behať tam a späť po jednej veci by bola
        // len práca navyše, nie rozhodovanie.
        val target = when {
            inventory.canFit(item) -> inventory
            bootReachable && boot.canFit(item) -> boot
            else -> {
                message = "Pack is full or too heavy"
                return false
            }
        }
        target.add(item)
        b.loot.removeAt(index)
        itemsLooted += item.count
        message = "Picked up: ${item.def.name}"
        return true
    }

    /** Hráč môže vyriešiť bežnú zastávku jedným rozhodnutím. */
    fun takeAllLoot(): Boolean {
        if (phase != GamePhase.EXPLORING || activeBuilding == null) return false
        var moved = false
        while (activeBuilding?.loot?.isNotEmpty() == true) {
            val before = activeBuilding?.loot?.size ?: 0
            if (!takeLoot(0)) break
            moved = moved || before > (activeBuilding?.loot?.size ?: before)
        }
        if (moved) message = "Took everything that fit"
        return moved
    }

    /** Zoberie všetok zostávajúci loot ako materiál bez riešenia kapacity. */
    fun scrapAllLoot(): Boolean {
        val b = activeBuilding ?: return false
        if (phase != GamePhase.EXPLORING || b.loot.isEmpty()) return false
        val gained = b.loot.sumOf { it.scrapValue }
        b.loot.clear()
        scrap += gained
        itemsLooted += 1
        message = "Scrapped the leftovers: +$gained scrap"
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
        val stack = ItemStack(part.defId, part.condition, part.health, paintIndex = part.paintIndex)
        if (slot == ComponentSlot.BATTERY) stack.heldCharge = car.batteryCharge
        // Zložiť úložisko sa dá, len keď sa obsah zmestí aj bez neho –
        // inak by sa veci ticho stratili. Batoh drží batoh, ostatné kufor.
        if (part.def.extraSlots > 0) {
            val onBack = part.defId == ItemCatalog.BACKPACK.id
            val target = if (onBack) inventory else boot
            val remaining = (if (onBack) car.packBonusSlots else car.bootBonusSlots) -
                part.def.extraSlots
            if (!target.fitsWithin(remaining)) {
                message = "Empty it first — it would not all fit"
                return false
            }
        }
        // Ťažký diel sa do batoha nezmestí, ale do kufra pri aute áno.
        val landing = when {
            inventory.canFit(stack) -> inventory
            bootReachable && boot.canFit(stack) -> boot
            else -> {
                message = "No room for it"
                return false
            }
        }
        // Kvapalina ide von s dielom, nie do priekopy.
        fluidHeldBy(slot)?.let { fluid ->
            val held = car.fluidLevel(fluid)
            if (held > 0.01f) {
                stack.heldFluidL = held
                stack.heldPurity = car.fluidPurity(fluid)
                if (fluid == FluidType.FUEL) {
                    stack.heldDieselFraction = car.fuelDieselFraction
                }
                car.drain(fluid)
            }
        }
        // So svetlometom odchádza aj svetlo – svietiť by nemalo z čoho.
        if (slot == ComponentSlot.HEADLIGHT) {
            headlightsOn = false
            highBeamsOn = false
        }
        car.parts.remove(slot)
        if (slot == ComponentSlot.BATTERY) car.batteryCharge = 0f
        if (slot == ComponentSlot.ROOF_RACK) roofLightsRequested = false
        landing.add(stack)
        syncCargoCapacity()
        message = "Removed: ${slot.displayName}"
        return true
    }

    fun discardInventoryItem(index: Int): Boolean {
        val stack = inventory.removeAt(index) ?: return false
        message = "Dropped: ${stack.def.name}"
        return true
    }

    fun discardBootItem(index: Int): Boolean {
        if (!bootReachable) {
            message = "Stop at the car first"
            return false
        }
        val stack = boot.removeAt(index) ?: return false
        message = "Dropped: ${stack.def.name}"
        return true
    }

    fun scrapInventoryItem(index: Int): Boolean = scrapFrom(inventory, index)

    fun scrapBootItem(index: Int): Boolean {
        if (!bootReachable) {
            message = "Stop at the car first"
            return false
        }
        return scrapFrom(boot, index)
    }

    private fun scrapFrom(source: Inventory, index: Int): Boolean {
        if (phase == GamePhase.DRIVING || kotlin.math.abs(car.speed) > 0.2f) {
            message = "Stop before dismantling parts"
            return false
        }
        val stack = source.removeAt(index) ?: return false
        val gained = stack.scrapValue
        scrap += gained
        message = "Scrapped ${stack.def.name}: +$gained scrap"
        return true
    }

    /** Prepočíta kapacitu batohu podľa namontovaného úložiska. */
    private fun syncCargoCapacity() {
        // Batoh sa nosí, debna a nosič sú na aute – každé zväčšuje niečo iné.
        inventory.applyCapacity(car.packBonusSlots, car.packBonusWeight)
        boot.applyCapacity(car.bootBonusSlots, car.bootBonusWeight)
    }

    /** Do kufra sa dá siahnuť len pri aute – teda vždy, keď sa práve nejde. */
    val bootReachable: Boolean
        get() = phase != GamePhase.DRIVING && phase != GamePhase.GAME_OVER

    /** Presun batoh → kufor. */
    fun stowInBoot(index: Int): Boolean = transfer(inventory, boot, index, "boot")

    /** Presun kufor → batoh. */
    fun takeFromBoot(index: Int): Boolean = transfer(boot, inventory, index, "pack")

    private fun transfer(from: Inventory, to: Inventory, index: Int, whereTo: String): Boolean {
        if (!bootReachable) {
            message = "Stop at the car first"
            return false
        }
        val stack = from.get(index) ?: return false
        if (!to.canFit(stack)) {
            message = "No room in the $whereTo"
            return false
        }
        from.removeAt(index)
        if (!to.add(stack)) {
            // Nemalo by nastať, ale radšej vec vrátiť než stratiť.
            from.add(stack)
            message = "No room in the $whereTo"
            return false
        }
        message = "Moved to the $whereTo: ${stack.def.name}"
        return true
    }

    fun useInventoryItem(index: Int, target: ComponentSlot? = null): Boolean =
        useItemFrom(inventory, index, target)

    /**
     * Montáž priamo z kufra. Motor váži cez sto kíl – do batoha na chrbte sa
     * nikdy nezmestí, takže bez tohto by sa nájdený motor nedal namontovať vôbec.
     */
    fun useBootItem(index: Int, target: ComponentSlot? = null): Boolean {
        if (!bootReachable) {
            message = "Stop at the car first"
            return false
        }
        return useItemFrom(boot, index, target)
    }

    private fun useItemFrom(
        source: Inventory,
        index: Int,
        target: ComponentSlot? = null
    ): Boolean {
        val inventory = source
        val stack = inventory.get(index) ?: return false
        val def = stack.def
        if (def.id == ItemCatalog.PUNCTURE_KIT.id) {
            return usePunctureKitFrom(inventory, index, target)
        }
        if (def.fluid != null) {
            val requiredPart = when (def.fluid) {
                FluidType.FUEL -> ComponentSlot.FUEL_TANK
                FluidType.OIL -> ComponentSlot.ENGINE
                FluidType.COOLANT -> ComponentSlot.RADIATOR
                FluidType.BRAKE_FLUID -> null
            }
            if (requiredPart != null && !car.hasPart(requiredPart)) {
                message = "Fit the ${requiredPart.displayName.lowercase()} before using this fluid"
                return false
            }
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
            // Leje sa presne toľko, koľko sa zmestí – zvyšok ostáva v nádobe.
            // Predtým sa minul celý kus, aj keď sa doň vošiel liter.
            val available = stack.fluidLitres
            if (available <= 0.01f) {
                inventory.removeAt(index)
                return false
            }
            val used = car.refill(
                def.fluid,
                kotlin.math.min(room, available),
                stack.purity,
                def.fuelKind ?: FuelKind.PETROL
            )
            if (used <= 0f) {
                message = "Already full (${def.fluid.displayName})"
                return false
            }
            stack.setFluidLitres(available - used)
            if (stack.fluidLitres <= 0.01f) inventory.removeAt(index)
            val fluidLabel = if (def.fluid == FluidType.FUEL) def.name else def.fluid.displayName
            message = if (stack.grade == FluidGrade.PURE) {
                "Topped up +${String.format("%.1f", used)} L $fluidLabel"
            } else {
                "Topped up +${String.format("%.1f", used)} L — ${stack.grade.displayName.lowercase()}"
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
            // Kvapalina patrí dielu, nie autu. Pri výmene ostane vo vymontovanom
            // kuse a s novým dielom sa naleje presne to, čo si so sebou priniesol.
            val fluidOf = fluidHeldBy(slot)
            val heldL = fluidOf?.let { car.fluidLevel(it) } ?: 0f
            val heldPurity = fluidOf?.let { car.fluidPurity(it) } ?: 1f
            val heldDieselFraction = if (fluidOf == FluidType.FUEL) car.fuelDieselFraction else 0f

            val outgoingCharge = if (slot == ComponentSlot.BATTERY) car.batteryCharge else -1f
            val prev = car.mount(slot, stack)
            inventory.removeAt(index)
            if (fluidOf != null) {
                car.drain(fluidOf)
                if (stack.heldFluidL > 0.01f) {
                    val heldKind = if (stack.heldDieselFraction >= 0.5f) FuelKind.DIESEL else FuelKind.PETROL
                    car.refill(fluidOf, stack.heldFluidL, stack.heldPurity, heldKind)
                    // Nádrž môže obsahovať ľubovoľnú zmes, nielen väčšinový typ.
                    if (fluidOf == FluidType.FUEL) {
                        car.fuelDieselFraction = stack.heldDieselFraction.coerceIn(0f, 1f)
                    }
                }
            }
            // Vymontovaný diel skús odložiť tam, odkiaľ prišiel nový; ťažký kus
            // sa do batoha nezmestí, tak nech skončí v kufri.
            if (prev != null) {
                val old = ItemStack(prev.defId, prev.condition, prev.health, paintIndex = prev.paintIndex)
                if (slot == ComponentSlot.BATTERY) old.heldCharge = outgoingCharge
                // Kvapalina odchádza spolu s dielom – po vrátení ju bude mať.
                if (fluidOf != null && heldL > 0.01f) {
                    old.heldFluidL = heldL
                    old.heldPurity = heldPurity
                    old.heldDieselFraction = heldDieselFraction
                }
                if (!inventory.add(old) && !boot.add(old) && !this.inventory.add(old)) {
                    message = "Removed ${old.def.name} left by the road — no room"
                }
            }
            syncCargoCapacity()
            val note = when {
                fluidOf == null -> ""
                stack.heldFluidL > 0.01f ->
                    " — with ${String.format("%.1f", stack.heldFluidL)} L still in it"
                else -> " — dry, needs ${fluidOf.displayName.lowercase()}"
            }
            message = "Fitted: ${def.name} → ${slot.displayName.lowercase()}$note"
            return true
        }
        message = "That cannot be used"
        return false
    }

    /** Ktorú kvapalinu drží diel v tomto slote (null = žiadnu). */
    private fun fluidHeldBy(slot: ComponentSlot): FluidType? = when (slot) {
        ComponentSlot.ENGINE -> FluidType.OIL
        ComponentSlot.RADIATOR -> FluidType.COOLANT
        ComponentSlot.FUEL_TANK -> FluidType.FUEL
        else -> null
    }

    /**
     * Vypustenie zanesenej kvapaliny. Bez toho sa špinavý olej nedá dostať von –
     * dolievaním sa čistota len mieša, takže raz zanesený motor by sa už len
     * zodieral. Cena je, že auto ostane suché, kým hráč nemá čo naliať.
     */
    fun drainFluid(fluid: FluidType, litres: Float? = null): Boolean {
        if (phase == GamePhase.DRIVING) {
            message = "Stop first"
            return false
        }
        if (car.engineRunning) {
            message = "Turn the engine off before draining fluids"
            return false
        }
        val had = when (fluid) {
            FluidType.FUEL -> car.fuel
            FluidType.OIL -> car.oil
            FluidType.COOLANT -> car.coolant
            FluidType.BRAKE_FLUID -> 0f
        }
        if (had <= 0.05f) {
            message = "Nothing to drain"
            return false
        }
        val requested = litres?.coerceAtLeast(0f) ?: had
        val drained = car.drainAmount(fluid, requested)
        message = "Drained ${String.format("%.1f", drained)} L ${fluid.displayName.lowercase()}" +
            if (car.fluidLevel(fluid) > 0.05f) {
                " — ${String.format("%.1f", car.fluidLevel(fluid))} L remains"
            } else {
                " — empty"
            }
        return true
    }

    fun repairSlot(slot: ComponentSlot): Boolean {
        val part = car.parts[slot] ?: return false
        if (slot in TIRE_SLOTS && part.injury == TireInjury.SHREDDED) {
            message = "That tyre is shredded — fit a new one"
            return false
        }
        if (!part.def.hasDurability) {
            message = "${part.def.name} is a permanent upgrade"
            return false
        }
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

    /** Cena jedného opravného kroku (najviac +20 % zdravia). */
    fun scrapRepairCost(slot: ComponentSlot): Int? {
        val part = car.parts[slot] ?: return null
        if (slot in TIRE_SLOTS && part.injury == TireInjury.SHREDDED) return null
        if (!part.def.hasDurability) return null
        val missing = (1f - part.health).coerceAtLeast(0f)
        if (missing < 0.01f) return null
        val step = minOf(0.20f, missing)
        val fullStepCost = (part.def.baseValue * 0.045f).coerceAtLeast(2f)
        return kotlin.math.ceil(fullStepCost * (step / 0.20f)).toInt().coerceAtLeast(1)
    }

    /** Najbližší jednoznačne lepší mechanický diel. Varianty gúm a pruženia nepreskakujeme. */
    fun scrapUpgradeTarget(slot: ComponentSlot): ItemDef? {
        val part = car.parts[slot] ?: return null
        val candidates = when (slot) {
            ComponentSlot.ENGINE -> ItemCatalog.candidatesFor(slot)
                .filter { it.fuelKind == part.def.fuelKind }
            ComponentSlot.BATTERY,
            ComponentSlot.RADIATOR,
            ComponentSlot.BRAKES,
            ComponentSlot.FUEL_TANK -> ItemCatalog.candidatesFor(slot)
            ComponentSlot.DRIVETRAIN -> listOf(ItemCatalog.DRIVE_AWD)
            else -> emptyList()
        }
        return candidates
            .filter { it.baseValue > part.def.baseValue }
            .minByOrNull { it.baseValue }
    }

    fun scrapUpgradeCost(slot: ComponentSlot): Int? {
        val current = car.parts[slot]?.def ?: return null
        val target = scrapUpgradeTarget(slot) ?: return null
        val difference = (target.baseValue - current.baseValue).coerceAtLeast(1)
        // Upgrade je dlhodobá odmena zo servisnej budovy, nie lacný nákup
        // každé dva kilometre. Opravy ostávajú lacnejšie.
        return kotlin.math.ceil(difference * 0.72f + target.baseValue * 0.12f)
            .toInt()
            .coerceAtLeast(18)
    }

    private fun upgradeWorkshopBlockReason(slot: ComponentSlot): String? = when {
        activeBuilding?.type != BuildingType.AUTO_SHOP ->
            "Take the car to a repair shop before upgrading"
        distanceM < when (slot) {
            ComponentSlot.ENGINE -> 8_000f
            ComponentSlot.DRIVETRAIN -> 12_000f
            else -> 6_000f
        } -> "This workshop upgrade unlocks further down the road"
        else -> null
    }

    /** Dôvod, prečo scrap oprava/upgrade práve nejde; null znamená pripravené. */
    fun scrapWorkshopBlockReason(): String? = when {
        phase == GamePhase.DRIVING || kotlin.math.abs(car.speed) > 0.2f ->
            "Park the car first"
        car.engineRunning -> "Switch the engine off first"
        else -> null
    }

    private fun workshopReady(): Boolean {
        val blocked = scrapWorkshopBlockReason() ?: return true
        message = blocked
        return false
    }

    fun repairWithScrap(slot: ComponentSlot): Boolean {
        if (!workshopReady()) return false
        if (slot in TIRE_SLOTS && car.parts[slot]?.injury == TireInjury.SHREDDED) {
            message = "That tyre is shredded — fit a new one"
            return false
        }
        val cost = scrapRepairCost(slot) ?: run {
            message = "${slot.displayName} does not need repair"
            return false
        }
        if (scrap < cost) {
            message = "Need $cost scrap — you have $scrap"
            return false
        }
        scrap -= cost
        // Len dezén / opotrebenie. Defekt ostáva — na to je puncture kit.
        car.repair(slot, 0.20f)
        message = "Repaired ${slot.displayName}: -$cost scrap"
        return true
    }

    /** Koľko sád na defekt má hráč pri aute (batoh + kufor). */
    fun punctureKitCount(): Int =
        countPunctureKits(inventory) + if (bootReachable) countPunctureKits(boot) else 0

    fun scrapPatchCost(): Int = GameConfig.PUNCTURE_PATCH_SCRAP

    fun canPatchPuncture(slot: ComponentSlot): Boolean =
        slot in TIRE_SLOTS && car.parts[slot]?.injury == TireInjury.PUNCTURED

    /** Stojí a nie je v jazde – záplata z kit-u nevyžaduje vypnutý motor. */
    fun parkedForService(): Boolean {
        if (phase == GamePhase.DRIVING || kotlin.math.abs(car.speed) > 0.2f) {
            message = "Park the car first"
            return false
        }
        return true
    }

    /**
     * Záplata defektu. Kit z batoha (alebo kufra) má prednosť a ide aj pri
     * bežiacom motore, keď auto stojí. Bez kit-u ostáva scrap, ten už chce
     * dielňu (motor vypnutý). Roztrhnutú gumu (ráfik) týmto nespravíš.
     */
    fun repairPuncture(slot: ComponentSlot): Boolean {
        if (!parkedForService()) return false
        val part = car.parts[slot] ?: run {
            message = "${slot.displayName}: nothing is fitted"
            return false
        }
        if (slot !in TIRE_SLOTS) {
            message = "That is not a tyre"
            return false
        }
        when (part.injury) {
            TireInjury.INFLATED -> {
                message = "That tyre is not flat"
                return false
            }
            TireInjury.SHREDDED -> {
                message = "That tyre is shredded — fit a new one"
                return false
            }
            TireInjury.PUNCTURED -> Unit
        }
        if (consumePunctureKit()) {
            part.injury = TireInjury.INFLATED
            car.repair(slot, GameConfig.PUNCTURE_KIT_HEAL)
            message = "Patched the ${slot.displayName.lowercase()}"
            return true
        }
        if (!workshopReady()) return false
        val cost = scrapPatchCost()
        if (scrap < cost) {
            message = "Need a puncture kit or $cost scrap"
            return false
        }
        scrap -= cost
        part.injury = TireInjury.INFLATED
        car.repair(slot, GameConfig.PUNCTURE_KIT_HEAL)
        message = "Patched the ${slot.displayName.lowercase()}: -$cost scrap"
        return true
    }

    private fun countPunctureKits(source: Inventory): Int =
        source.slots.sumOf { stack ->
            if (stack?.defId == ItemCatalog.PUNCTURE_KIT.id) stack.count.coerceAtLeast(1) else 0
        }

    private fun consumePunctureKit(): Boolean {
        fun takeFrom(source: Inventory): Boolean {
            val idx = source.slots.indexOfFirst { it?.defId == ItemCatalog.PUNCTURE_KIT.id }
            if (idx < 0) return false
            val stack = source.slots[idx] ?: return false
            stack.count--
            if (stack.count <= 0) source.removeAt(idx)
            return true
        }
        return takeFrom(inventory) || (bootReachable && takeFrom(boot))
    }

    private fun usePunctureKitFrom(
        source: Inventory,
        index: Int,
        target: ComponentSlot?
    ): Boolean {
        if (!parkedForService()) return false
        val slot = when {
            target != null && target in TIRE_SLOTS -> target
            else -> TIRE_SLOTS.firstOrNull { car.parts[it]?.injury == TireInjury.PUNCTURED }
        }
        if (slot == null) {
            message = "No flat tyre to patch"
            return false
        }
        val part = car.parts[slot] ?: run {
            message = "${slot.displayName}: nothing is fitted"
            return false
        }
        when (part.injury) {
            TireInjury.INFLATED -> {
                message = "That tyre is not flat"
                return false
            }
            TireInjury.SHREDDED -> {
                message = "That tyre is shredded — fit a new one"
                return false
            }
            TireInjury.PUNCTURED -> Unit
        }
        val stack = source.get(index) ?: return false
        stack.count--
        if (stack.count <= 0) source.removeAt(index)
        part.injury = TireInjury.INFLATED
        car.repair(slot, GameConfig.PUNCTURE_KIT_HEAL)
        message = "Patched the ${slot.displayName.lowercase()}"
        return true
    }

    fun upgradeWithScrap(slot: ComponentSlot): Boolean {
        if (!workshopReady()) return false
        upgradeWorkshopBlockReason(slot)?.let {
            message = it
            return false
        }
        val current = car.parts[slot] ?: return false
        val target = scrapUpgradeTarget(slot) ?: run {
            message = "No direct upgrade for ${slot.displayName.lowercase()}"
            return false
        }
        val cost = scrapUpgradeCost(slot) ?: return false
        if (scrap < cost) {
            message = "Need $cost scrap — you have $scrap"
            return false
        }
        scrap -= cost
        car.parts[slot] = MountedPart(
            defId = target.id,
            condition = current.condition,
            health = (current.health + 0.08f).coerceAtMost(1f),
            paintIndex = current.paintIndex
        )
        message = "Upgraded ${slot.displayName}: ${target.name} (-$cost scrap)"
        return true
    }

    val paintShopAvailable: Boolean
        get() = activeBuilding?.type == BuildingType.AUTO_SHOP && distanceM >= 10_000f

    val paintShopCost: Int
        get() = (18 + (distanceKm / 20f).toInt() * 4).coerceAtMost(42)

    fun paintBody(paint: VehiclePaint): Boolean = paintBodyInternal(paint, free = false)

    /** Odmena z rewarded reklamy: jedna zmena laku bez scrapu. */
    fun paintBodyFromRewardedAd(paint: VehiclePaint): Boolean =
        paintBodyInternal(paint, free = true)

    private fun paintBodyInternal(paint: VehiclePaint, free: Boolean): Boolean {
        if (!paintShopAvailable) {
            message = if (activeBuilding?.type == BuildingType.AUTO_SHOP) {
                "Paint service unlocks after 10 km"
            } else {
                "Take the car to a repair shop"
            }
            return false
        }
        if (!workshopReady()) return false
        if (car.bodyPaintIndex == paint.ordinal) {
            message = "The car already has ${paint.displayName.lowercase()} paint"
            return false
        }
        val cost = paintShopCost
        if (!free && scrap < cost) {
            message = "Need $cost scrap to repaint — you have $scrap"
            return false
        }
        if (!free) scrap -= cost
        car.bodyPaintIndex = paint.ordinal
        message = if (free) {
            "Repainted the car ${paint.displayName.lowercase()} — ad reward"
        } else {
            "Repainted the car ${paint.displayName.lowercase()}: -$cost scrap"
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
            if (isNight && !headlightsOn && car.hasPart(ComponentSlot.HEADLIGHT)) {
                headlightsOn = true
                highBeamsOn = false
            }
            message = "Engine running — hold the throttle"
            emitSfx(GameSfx.ENGINE_START)
            true
        } else {
            val missing = car.missingEssentials()
            // Zablokovaný štart nesmie byť slepá ulička – čo chýba, nájde sa nablízku.
            val rescued = offerRescue(missing)
            emitSfx(GameSfx.ENGINE_FAIL_START)
            message = when {
                rescued != null -> rescued
                missing.isNotEmpty() ->
                    "Missing ${missing.joinToString(", ") { it.displayName.lowercase() }}"
                car.fuel < 0.5f -> "Fuel is low"
                car.oil < 0.3f -> "Oil is low"
                car.coolant < 0.3f -> "Coolant is low"
                car.wrongFuelFraction >= 0.70f ->
                    "Wrong fuel — drain the tank and use ${car.requiredFuelKind.displayName.lowercase()}"
                car.batteryCharge < 0.2f -> "Battery is weak"
                else -> "The engine would not start"
            }
            false
        }
    }

    fun stopEngine() {
        car.stopEngine()
        emitSfx(GameSfx.ENGINE_STOP)
        message = "Engine switched off"
    }

    /** Dá sa prespať do rána? Len po zotmení, pri budove a so stojacim autom. */
    val canRest: Boolean
        get() = isNight && phase != GamePhase.DRIVING && phase != GamePhase.GAME_OVER &&
            kotlin.math.abs(car.speed) < 0.2f &&
            (activeBuilding ?: buildingNear())?.type != BuildingType.WRECK &&
            (activeBuilding != null || buildingNear() != null)

    /**
     * Prespanie noci. Jazdiť po tme je len horšie videnie bez inej hodnoty,
     * takže hráč môže počkať do rána. Nie je to zadarmo – motor medzitým
     * vychladne a batéria sa sama trochu vybije.
     */
    fun restUntilDawn(): Boolean {
        if (!canRest) {
            message = when {
                !isNight -> "It is still light out"
                phase == GamePhase.DRIVING || kotlin.math.abs(car.speed) >= 0.2f -> "Stop the car first"
                activeBuilding?.type == BuildingType.WRECK ||
                    (activeBuilding == null && buildingNear()?.type == BuildingType.WRECK) ->
                    "You can only sleep beside a building, not a wreck"
                activeBuilding == null && buildingNear() == null -> "You can only sleep beside a building"
                else -> "You cannot sleep here"
            }
            return false
        }
        car.stopEngine()
        timeOfDay = GameConfig.DAY_START
        // Noc v aute: motor vychladne na okolitú teplotu, batéria trochu klesne.
        car.temperature = 30f
        car.batteryCharge = (car.batteryCharge - 0.05f).coerceAtLeast(0f)
        headlightsOn = false
        highBeamsOn = false
        nightWarned = false
        message = "Dawn. Battery slightly lower."
        roofLightsRequested = false
        return true
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
            inventory.slots.none {
                it?.def?.fluid == fluid &&
                    (fluid != FluidType.FUEL || it.def.fuelKind == car.requiredFuelKind)
            } && fluidRescues < MAX_FLUID_RESCUES
        }
        if (needParts.isEmpty() && needFluid == null) return null

        // Prednostne do batoha, nie novou budovou. Vrak, ktorý sa zjaví štyri
        // metre od stojaceho auta, pôsobí ako chyba, aj keď je to zámerná
        // záchranná sieť. Do prehrabania sa vo vlastnej batožine hráč uverí.
        val stash = buildingNear()
            ?: rescueStashId?.let { id -> segment.buildings.firstOrNull { it.id == id } }
                ?.takeIf { kotlin.math.abs(worldXOf(it) - car.x) <= GameConfig.BUILDING_INTERACT_RANGE }
            ?: return rescueIntoPack(needParts, needFluid)

        val rng = SeededRandom(seed xor stash.id xor distanceM.toRawBits().toLong())
        val added = mutableListOf<String>()
        var rescueInsert = 0
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
            // Nutné veci idú navrch zoznamu, aby ich sedačka či ťažký motor
            // nezablokovali plným batohom skôr, než sa hráč dostane k záchrane.
            stash.loot.add(
                rescueInsert++,
                ItemStack(def.id, ComponentCondition.DAMAGED, rng.nextFloat(0.35f, 0.65f))
            )
            added += def.name.lowercase()
        }
        if (needFluid != null && stash.loot.none {
                it.def.fluid == needFluid &&
                    (needFluid != FluidType.FUEL || it.def.fuelKind == car.requiredFuelKind)
            }
        ) {
            val def = when (needFluid) {
                FluidType.FUEL -> compatibleFuelCan()
                FluidType.OIL -> ItemCatalog.OIL_BOTTLE
                else -> ItemCatalog.COOLANT_BOTTLE
            }
            stash.loot.add(
                rescueInsert,
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
        // Hláška musí sedieť s tým, čo sa naozaj stalo. Loot ide prednostne do
        // budovy, ktorá pri ceste už stojí – tvrdiť pritom, že sa za autom
        // zjavil vrak, znie ako chyba aj vtedy, keď je všetko v poriadku.
        val where = when (stash.type) {
            BuildingType.HOUSE -> "In the house right here"
            BuildingType.GARAGE -> "In the garage right here"
            BuildingType.GAS_STATION -> "At the pump right here"
            BuildingType.AUTO_SHOP -> "In the workshop right here"
            BuildingType.WRECK -> "In the wreck right here"
        }
        return "$where — ${added.joinToString(", ")}. Check BUILDING."
    }

    /**
     * Záchrana bez novej budovy: hráč nájde to nutné v batožinovom priestore.
     * Ak sa do batoha nič nezmestí, vraciame null a jazda skončí – vtedy už
     * problém nie je v tom, že sa nedá pokračovať, ale že niet kam veci dať.
     */
    private fun rescueIntoPack(
        needParts: List<ComponentSlot>,
        needFluid: FluidType?
    ): String? {
        val rng = SeededRandom(seed xor 0x412CL xor distanceM.toRawBits().toLong())
        val added = mutableListOf<String>()

        needParts.forEach { slot ->
            val def = when (slot) {
                ComponentSlot.ENGINE -> ItemCatalog.ENGINE_A
                ComponentSlot.FUEL_TANK -> ItemCatalog.FUEL_TANK
                ComponentSlot.TIRE_FRONT, ComponentSlot.TIRE_REAR -> ItemCatalog.TIRE_POOR
                ComponentSlot.BATTERY -> ItemCatalog.BATTERY
                ComponentSlot.STARTER -> ItemCatalog.STARTER
                else -> null
            } ?: return@forEach
            val stack = ItemStack(def.id, ComponentCondition.DAMAGED, rng.nextFloat(0.35f, 0.65f))
            if (inventory.add(stack)) added += def.name.lowercase()
        }
        if (needFluid != null) {
            val def = when (needFluid) {
                FluidType.FUEL -> compatibleFuelCan()
                FluidType.OIL -> ItemCatalog.OIL_BOTTLE
                else -> ItemCatalog.COOLANT_BOTTLE
            }
            val stack = ItemStack(
                defId = def.id,
                condition = ComponentCondition.NEW,
                health = 1f,
                count = 1,
                purity = rng.nextFloat(0.5f, 0.85f)
            )
            if (inventory.add(stack)) {
                fluidRescues++
                added += def.name.lowercase()
            }
        }
        if (added.isEmpty()) return null
        return "Dug out of the boot: ${added.joinToString(", ")}. Check PACK."
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
        throttleInput = 0f
        brakeInput = 0f
        emitSfx(GameSfx.ENGINE_STALL)
        // Zhasnuté auto sa nezastaví na fleku – dojazdí zotrvačnosťou a z kopca
        // sa ešte kus zvezie. Do STOPPED prejde až keď naozaj stojí.
        if (phase == GamePhase.DRIVING && kotlin.math.abs(car.speed) < GameConfig.STOP_SPEED) {
            car.speed = 0f
            phase = GamePhase.STOPPED
        }
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
                packHas { it.def.fluid == FluidType.FUEL && it.def.fuelKind == car.requiredFuelKind } ||
                    buildingHas { it.def.fluid == FluidType.FUEL && it.def.fuelKind == car.requiredFuelKind } ||
                    (near?.let {
                        if (car.requiredFuelKind == FuelKind.DIESEL) it.pumpDieselL else it.pumpFuelL
                    } ?: 0f) > 0.5f ||
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

    private fun compatibleFuelCan(): ItemDef =
        if (car.requiredFuelKind == FuelKind.DIESEL) ItemCatalog.DIESEL_CAN else ItemCatalog.FUEL_CAN

    fun endRun(reason: EndReason = EndReason.MANUAL) {
        phase = GamePhase.GAME_OVER
        endReason = reason
        endDetail = when (reason) {
            EndReason.ENGINE_DESTROYED, EndReason.OVERHEAT ->
                car.wearCause?.let { "Cause: ${it.fatal}." } ?: ""
            EndReason.OUT_OF_FUEL -> "Out of fuel."
            EndReason.BATTERY_DEAD -> "Battery drained. Engine would not start."
            EndReason.ARRIVED -> "The final relay is online. The route is complete."
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
            parts = car.parts.map { (slot, p) ->
                PartState(slot, p.defId, p.condition, p.health, p.paintIndex, p.injury)
            },
            fuel = car.fuel, oil = car.oil, coolant = car.coolant,
            fuelPurity = car.fuelPurity, oilPurity = car.oilPurity, coolantPurity = car.coolantPurity,
            temperature = car.temperature, batteryCharge = car.batteryCharge,
            engineRunning = car.engineRunning,
            x = car.x, y = car.y, speed = car.speed, pitch = car.pitch,
            fuelDieselFraction = car.fuelDieselFraction,
            bodyPaintIndex = car.bodyPaintIndex
        ),
        inventory = inventory.slots.map { it?.toState() },
        boot = boot.slots.map { it?.toState() },
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
                    pumpFuelL = b.pumpFuelL, pumpDieselL = b.pumpDieselL,
                    pumpPurity = b.pumpPurity,
                    landmark = b.landmark,
                    loot = b.loot.map { it.toState() },
                    pumpFuelKind = b.pumpFuelKind,
                    relayIndex = b.relayIndex,
                    relayRestored = b.relayRestored
                )
            },
            paving = segment.paving
        ),
        events = events.map { EventState(it.event, it.remaining) },
        scrap = scrap,
        highBeamsOn = highBeamsOn,
        roofLightsOn = roofLightsOn
    )

    private fun applySnapshot(snap: RunSnapshot) {
        // Svet: plán je deterministický zo seedu, budovy prepíšeme stavom z uloženia.
        val plan = SegmentPlan(
            seed = snap.segment.planSeed,
            style = snap.segment.style,
            length = snap.segment.length,
            features = snap.segment.features,
            buildingCount = snap.segment.buildingCount,
            paving = snap.segment.paving
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
                pumpDieselL = b.pumpDieselL,
                pumpPurity = b.pumpPurity,
                pumpFuelKind = b.pumpFuelKind,
                landmark = b.landmark,
                relayIndex = b.relayIndex,
                relayRestored = b.relayRestored
            )
        }
        applyRelayProgress(segment)

        // Auto.
        car.parts.clear()
        snap.car.parts.forEach { p ->
            car.parts[p.slot] = sk.kubis.endlessdrive.game.car.MountedPart(
                p.defId, p.condition, p.health, p.paintIndex, p.injury
            )
        }
        // Staršie save mohli mať kapacitné upgrady v poškodenom stave.
        car.parts.values.filterNot { it.def.hasDurability }.forEach {
            it.condition = ComponentCondition.NEW
            it.health = 1f
        }
        // Svetlá a nosič nie sú lakovaný plech. Staršie save im mohli dať
        // náhodný odtieň, preto pri obnove ostanú bez laku.
        car.parts.forEach { (slot, part) ->
            if (!slot.takesBodyPaint) part.paintIndex = -1
        }
        car.fuel = snap.car.fuel
        car.oil = snap.car.oil
        car.coolant = snap.car.coolant
        car.fuelPurity = snap.car.fuelPurity
        car.fuelDieselFraction = snap.car.fuelDieselFraction
        car.bodyPaintIndex = snap.car.bodyPaintIndex
        car.oilPurity = snap.car.oilPurity
        car.coolantPurity = snap.car.coolantPurity
        car.temperature = snap.car.temperature
        car.batteryCharge = snap.car.batteryCharge
        car.engineRunning = snap.car.engineRunning
        car.x = snap.car.x
        car.y = snap.car.y
        car.speed = 0f
        car.pitch = snap.car.pitch

        // Batoh a kufor. Kapacitu treba nastaviť skôr, než sa napĺňa – s nosičom
        // má kufor viac slotov a inak by sa presahujúce veci stratili.
        inventory.clear()
        boot.clear()
        syncCargoCapacity()
        snap.inventory.forEachIndexed { i, st ->
            if (st != null && i < inventory.slots.size) inventory.slots[i] = st.toStack()
        }
        snap.boot.forEachIndexed { i, st ->
            if (st != null && i < boot.slots.size) boot.slots[i] = st.toStack()
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
        highBeamsOn = snap.highBeamsOn && snap.headlightsOn
        roofLightsRequested = snap.roofLightsOn && hasExpeditionKit
        fuelBurnedL = snap.fuelBurnedL
        itemsLooted = snap.itemsLooted
        buildingsVisited = snap.buildingsVisited
        scrap = snap.scrap
        fluidRescues = snap.fluidRescues
        batteryRescues = snap.batteryRescues
        // Po obnove nikdy nejdeme rovno v jazde – hráč sa najprv rozhliadne.
        phase = when (snap.phase) {
            GamePhase.DRIVING, GamePhase.EXPLORING, GamePhase.JUNCTION -> GamePhase.STOPPED
            else -> snap.phase
        }
        currentFeature = segment.featureAtWorld(car.x)
        activeBuilding = null
        camera.snapTo(car.x, car.y)
        // Obnova môže skončiť tesne pred hranicou; zaplatíme prípravu teraz,
        // keď je jazda aj tak zastavená, nie v prvom rýchlom prejazde.
        preparedContinuation = null
        prepareContinuation()
        message = "Run restored — you were at ${String.format("%.2f", distanceKm)} km."
    }

    private fun ItemStack.toState() =
        StackState(
            defId, condition, health, count, purity,
            heldFluidL, heldPurity, heldDieselFraction, paintIndex, heldCharge
        )

    private fun StackState.toStack(): ItemStack {
        val restoredPaint = if (ItemCatalog.byId(defId)?.mountsTo?.takesBodyPaint == true) {
            paintIndex
        } else {
            -1
        }
        return ItemStack(
            defId, condition, health, count, purity,
            heldFluidL, heldPurity, heldDieselFraction, restoredPaint,
            heldCharge
        )
    }

    companion object {
        /**
         * Postaví engine späť z uloženej jazdy. Terén aj úseky sa dopočítajú
         * zo seedu, budovy sa nahradia tým, čo v nich hráč nechal.
         */
        fun restore(snap: RunSnapshot, bestDistanceKm: Float, metaRelayNodes: Int = 0): GameEngine {
            val engine = GameEngine(snap.seed, bestDistanceKm, metaRelayNodes = metaRelayNodes)
            engine.applySnapshot(snap)
            return engine
        }

        /** Ako dlho ostane hláška na obrazovke (s). */
        private const val MESSAGE_TTL = 4.5f

        /** Naplavenina: zapnúť hlášku; vypnúť neskôr, aby neblikala na prahu. */
        private const val PATCH_AHEAD_SHOW_M = 50f
        private const val PATCH_AHEAD_HIDE_M = 75f

        /** Rezerva pred vizuálnym prechodom, v ktorej sa pripraví ďalší región. */
        private const val CONTINUATION_PRELOAD_LEAD = 450f

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
