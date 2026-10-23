package sk.kubis.endlessdrive.game

import sk.kubis.endlessdrive.domain.model.DebugOptions
import sk.kubis.endlessdrive.core.GameConfig
import sk.kubis.endlessdrive.core.MathX
import sk.kubis.endlessdrive.core.SeededRandom
import sk.kubis.endlessdrive.domain.model.AppLanguage
import sk.kubis.endlessdrive.domain.model.BiomeType
import sk.kubis.endlessdrive.domain.model.ComponentCondition
import sk.kubis.endlessdrive.domain.model.BranchStyle
import sk.kubis.endlessdrive.domain.model.BuildingType
import sk.kubis.endlessdrive.domain.model.ComponentSlot
import sk.kubis.endlessdrive.domain.model.TIRE_SLOTS
import sk.kubis.endlessdrive.domain.model.EndReason
import sk.kubis.endlessdrive.domain.model.RescueOffer
import sk.kubis.endlessdrive.domain.model.FluidGrade
import sk.kubis.endlessdrive.domain.model.FluidType
import sk.kubis.endlessdrive.domain.model.FuelKind
import sk.kubis.endlessdrive.domain.model.GamePhase
import sk.kubis.endlessdrive.domain.model.ItemCatalog
import sk.kubis.endlessdrive.domain.model.ItemDef
import sk.kubis.endlessdrive.domain.model.ItemStack
import sk.kubis.endlessdrive.domain.model.RoadFeature
import sk.kubis.endlessdrive.domain.model.RoadPaving
import sk.kubis.endlessdrive.domain.model.RoadSurface
import sk.kubis.endlessdrive.domain.model.SedanSpec
import sk.kubis.endlessdrive.domain.model.VehiclePaint
import sk.kubis.endlessdrive.domain.model.candidatesFor
import sk.kubis.endlessdrive.game.audio.GameSfx
import sk.kubis.endlessdrive.game.car.Car
import sk.kubis.endlessdrive.game.car.EndCause
import sk.kubis.endlessdrive.game.car.WearCause
import sk.kubis.endlessdrive.game.car.MountedPart
import sk.kubis.endlessdrive.game.car.TireInjury
import sk.kubis.endlessdrive.game.car.TireSim
import sk.kubis.endlessdrive.game.event.ActiveEvent
import sk.kubis.endlessdrive.game.event.ActivePursuitStorm
import sk.kubis.endlessdrive.game.event.PursuitStormKind
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
import sk.kubis.endlessdrive.game.world.BiomeBlend
import sk.kubis.endlessdrive.game.world.RoadSegment
import sk.kubis.endlessdrive.game.world.SurfacePatch
import sk.kubis.endlessdrive.game.world.SegmentPlan
import sk.kubis.endlessdrive.game.world.Terrain
import sk.kubis.endlessdrive.game.world.TerrainProfile
import sk.kubis.endlessdrive.game.world.TestTrackProfile
import sk.kubis.endlessdrive.game.world.RescueSpotFinder
import sk.kubis.endlessdrive.game.world.WorldBuilding
import sk.kubis.endlessdrive.game.world.WorldGenerator
import sk.kubis.endlessdrive.game.world.World3D
import sk.kubis.endlessdrive.domain.repository.GarageUpgrades
import sk.kubis.endlessdrive.ui.l10n.Copy
import sk.kubis.endlessdrive.ui.l10n.*
import sk.kubis.endlessdrive.ui.l10n.adNotReady
import sk.kubis.endlessdrive.ui.l10n.alreadyFull
import sk.kubis.endlessdrive.ui.l10n.alreadyHasPaint
import sk.kubis.endlessdrive.ui.l10n.arrivedEnd
import sk.kubis.endlessdrive.ui.l10n.backAtTheCar
import sk.kubis.endlessdrive.ui.l10n.batteryDrained
import sk.kubis.endlessdrive.ui.l10n.batteryFlatFit
import sk.kubis.endlessdrive.ui.l10n.batteryIsFlat
import sk.kubis.endlessdrive.ui.l10n.batteryWontHold
import sk.kubis.endlessdrive.ui.l10n.beamsNoAlt
import sk.kubis.endlessdrive.ui.l10n.beamsWeakBattery
import sk.kubis.endlessdrive.ui.l10n.buildingName
import sk.kubis.endlessdrive.ui.l10n.cannotSleepHere
import sk.kubis.endlessdrive.ui.l10n.carWontMove
import sk.kubis.endlessdrive.ui.l10n.causeFatal
import sk.kubis.endlessdrive.ui.l10n.climbTooSteep
import sk.kubis.endlessdrive.ui.l10n.coolingShot
import sk.kubis.endlessdrive.ui.l10n.coolantIsLow
import sk.kubis.endlessdrive.ui.l10n.dawnBattery
import sk.kubis.endlessdrive.ui.l10n.dawnBatteryEmptyRad
import sk.kubis.endlessdrive.ui.l10n.doesNotNeedRepair
import sk.kubis.endlessdrive.ui.l10n.drained
import sk.kubis.endlessdrive.ui.l10n.driving
import sk.kubis.endlessdrive.ui.l10n.droppedItem
import sk.kubis.endlessdrive.ui.l10n.emptyItFirst
import sk.kubis.endlessdrive.ui.l10n.endReasonMessage
import sk.kubis.endlessdrive.ui.l10n.endlessModeMsg
import sk.kubis.endlessdrive.ui.l10n.engineAboutToDie
import sk.kubis.endlessdrive.ui.l10n.engineAlreadyRunning
import sk.kubis.endlessdrive.ui.l10n.engineBadlyWorn
import sk.kubis.endlessdrive.ui.l10n.engineBeyondSaving
import sk.kubis.endlessdrive.ui.l10n.engineCutHeat
import sk.kubis.endlessdrive.ui.l10n.engineFinished
import sk.kubis.endlessdrive.ui.l10n.engineIsOff
import sk.kubis.endlessdrive.ui.l10n.engineOffToRespray
import sk.kubis.endlessdrive.ui.l10n.engineOffToService
import sk.kubis.endlessdrive.ui.l10n.engineOffToUpgrade
import sk.kubis.endlessdrive.ui.l10n.engineRunningHold
import sk.kubis.endlessdrive.ui.l10n.engineRunningPressDrive
import sk.kubis.endlessdrive.ui.l10n.engineSwitchedOff
import sk.kubis.endlessdrive.ui.l10n.engineTooWeak
import sk.kubis.endlessdrive.ui.l10n.engineWearing
import sk.kubis.endlessdrive.ui.l10n.engineWouldNotStart
import sk.kubis.endlessdrive.ui.l10n.enterFuelStation
import sk.kubis.endlessdrive.ui.l10n.enterRelayFirst
import sk.kubis.endlessdrive.ui.l10n.findRelayModule
import sk.kubis.endlessdrive.ui.l10n.fitBeforeFluid
import sk.kubis.endlessdrive.ui.l10n.fitFuelTankFirst
import sk.kubis.endlessdrive.ui.l10n.fittedTo
import sk.kubis.endlessdrive.ui.l10n.fixIt
import sk.kubis.endlessdrive.ui.l10n.fluidGradeName
import sk.kubis.endlessdrive.ui.l10n.fluidName
import sk.kubis.endlessdrive.ui.l10n.fluidsDirty
import sk.kubis.endlessdrive.ui.l10n.fuelIsLow
import sk.kubis.endlessdrive.ui.l10n.fuelKindName
import sk.kubis.endlessdrive.ui.l10n.gettingDark
import sk.kubis.endlessdrive.ui.l10n.headlightsOff
import sk.kubis.endlessdrive.ui.l10n.highBeamsOn
import sk.kubis.endlessdrive.ui.l10n.insideBuilding
import sk.kubis.endlessdrive.ui.l10n.itemName
import sk.kubis.endlessdrive.ui.l10n.lowBeamsOn
import sk.kubis.endlessdrive.ui.l10n.missingParts
import sk.kubis.endlessdrive.ui.l10n.movedTo
import sk.kubis.endlessdrive.ui.l10n.msgRoofLights
import sk.kubis.endlessdrive.ui.l10n.mustStayFitted
import sk.kubis.endlessdrive.ui.l10n.needKitOrScrap
import sk.kubis.endlessdrive.ui.l10n.needScrap
import sk.kubis.endlessdrive.ui.l10n.needScrapRepaint
import sk.kubis.endlessdrive.ui.l10n.networkFullyRestored
import sk.kubis.endlessdrive.ui.l10n.noAlternator
import sk.kubis.endlessdrive.ui.l10n.noBuildingNearby
import sk.kubis.endlessdrive.ui.l10n.noDirectUpgrade
import sk.kubis.endlessdrive.ui.l10n.noFlatToPatch
import sk.kubis.endlessdrive.ui.l10n.noFuelInTank
import sk.kubis.endlessdrive.ui.l10n.noHeadlightFitted
import sk.kubis.endlessdrive.ui.l10n.noOilInEngine
import sk.kubis.endlessdrive.ui.l10n.noPumpHere
import sk.kubis.endlessdrive.ui.l10n.noRoomForIt
import sk.kubis.endlessdrive.ui.l10n.noRoomIn
import sk.kubis.endlessdrive.ui.l10n.noTractionAir
import sk.kubis.endlessdrive.ui.l10n.noTractionAssist
import sk.kubis.endlessdrive.ui.l10n.noTyresToSwap
import sk.kubis.endlessdrive.ui.l10n.nothingFitted
import sk.kubis.endlessdrive.ui.l10n.nothingToDrain
import sk.kubis.endlessdrive.ui.l10n.oilIsLow
import sk.kubis.endlessdrive.ui.l10n.onTheRimAssist
import sk.kubis.endlessdrive.ui.l10n.outOfFuel
import sk.kubis.endlessdrive.ui.l10n.outOfFuelDot
import sk.kubis.endlessdrive.ui.l10n.packFull
import sk.kubis.endlessdrive.ui.l10n.paintAfter10km
import sk.kubis.endlessdrive.ui.l10n.paintName
import sk.kubis.endlessdrive.ui.l10n.parkTheCarFirst
import sk.kubis.endlessdrive.ui.l10n.patchedCost
import sk.kubis.endlessdrive.ui.l10n.patchedSlot
import sk.kubis.endlessdrive.ui.l10n.permanentUpgrade
import sk.kubis.endlessdrive.ui.l10n.pickedUp
import sk.kubis.endlessdrive.ui.l10n.prepHint
import sk.kubis.endlessdrive.ui.l10n.radiatorEmpty
import sk.kubis.endlessdrive.ui.l10n.ranDry
import sk.kubis.endlessdrive.ui.l10n.relayAlreadyOnline
import sk.kubis.endlessdrive.ui.l10n.relayOnlineFuel
import sk.kubis.endlessdrive.ui.l10n.relayRestoredMsg
import sk.kubis.endlessdrive.ui.l10n.relayStationHint
import sk.kubis.endlessdrive.ui.l10n.releaseBrake
import sk.kubis.endlessdrive.ui.l10n.removedNoRoom
import sk.kubis.endlessdrive.ui.l10n.removedSlot
import sk.kubis.endlessdrive.ui.l10n.repairedCost
import sk.kubis.endlessdrive.ui.l10n.repairedSlot
import sk.kubis.endlessdrive.ui.l10n.repaintedAd
import sk.kubis.endlessdrive.ui.l10n.repaintedCost
import sk.kubis.endlessdrive.ui.l10n.rescueAd
import sk.kubis.endlessdrive.ui.l10n.restorePreviousRelay
import sk.kubis.endlessdrive.ui.l10n.roadEventMessage
import sk.kubis.endlessdrive.ui.l10n.roughPatch
import sk.kubis.endlessdrive.ui.l10n.runRestored
import sk.kubis.endlessdrive.ui.l10n.safeHavenOffline
import sk.kubis.endlessdrive.ui.l10n.safeHavenReachedMsg
import sk.kubis.endlessdrive.ui.l10n.salvagedScrap
import sk.kubis.endlessdrive.ui.l10n.scrappedItem
import sk.kubis.endlessdrive.ui.l10n.scrappedLeftovers
import sk.kubis.endlessdrive.ui.l10n.searchTheGarage
import sk.kubis.endlessdrive.ui.l10n.searchingBuilding
import sk.kubis.endlessdrive.ui.l10n.shreddedEnd
import sk.kubis.endlessdrive.ui.l10n.sleepBesideBuilding
import sk.kubis.endlessdrive.ui.l10n.sleepNotWreck
import sk.kubis.endlessdrive.ui.l10n.slotName
import sk.kubis.endlessdrive.ui.l10n.slowDownFirst
import sk.kubis.endlessdrive.ui.l10n.stillLightOut
import sk.kubis.endlessdrive.ui.l10n.stopAndSearch
import sk.kubis.endlessdrive.ui.l10n.stopAtBuilding
import sk.kubis.endlessdrive.ui.l10n.stopAtCarFirst
import sk.kubis.endlessdrive.ui.l10n.stopBeforeDismantling
import sk.kubis.endlessdrive.ui.l10n.stopFirst
import sk.kubis.endlessdrive.ui.l10n.stopTheCarFirst
import sk.kubis.endlessdrive.ui.l10n.stopped
import sk.kubis.endlessdrive.ui.l10n.stuckEnd
import sk.kubis.endlessdrive.ui.l10n.swappedTyres
import sk.kubis.endlessdrive.ui.l10n.switchEngineOffFirst
import sk.kubis.endlessdrive.ui.l10n.takeCarToPaint
import sk.kubis.endlessdrive.ui.l10n.tankIsFull
import sk.kubis.endlessdrive.ui.l10n.tapAgainToRemove
import sk.kubis.endlessdrive.ui.l10n.thatCannotBeUsed
import sk.kubis.endlessdrive.ui.l10n.thatIsNotATyre
import sk.kubis.endlessdrive.ui.l10n.thatOneIsFine
import sk.kubis.endlessdrive.ui.l10n.tookEverything
import sk.kubis.endlessdrive.ui.l10n.turnEngineOffDrain
import sk.kubis.endlessdrive.ui.l10n.tyreNotFlat
import sk.kubis.endlessdrive.ui.l10n.tyreShredded
import sk.kubis.endlessdrive.ui.l10n.tyresSpinning
import sk.kubis.endlessdrive.ui.l10n.wearFatal
import sk.kubis.endlessdrive.ui.l10n.wearWarning
import sk.kubis.endlessdrive.ui.l10n.whereBoot
import sk.kubis.endlessdrive.ui.l10n.wherePack
import sk.kubis.endlessdrive.ui.l10n.workshopUnlocksLater
import sk.kubis.endlessdrive.ui.l10n.wrongFuelDrain

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
    private val metaRelayNodes: Int = 0,
    /** Malé permanentné bonusy kúpené za šrot medzi jazdami. */
    private val garageUpgrades: GarageUpgrades = GarageUpgrades()
) {
    var language: AppLanguage = AppLanguage.ENGLISH
    val copy: Copy get() = Copy.of(language)

    private fun missingText(missing: List<ComponentSlot>) =
        copy.missingParts(missing.joinToString(", ") { copy.slotName(it).lowercase() })

    val car = Car()
    var steeringInput = 0f
    internal val propField=sk.kubis.endlessdrive.game.world.WorldProps.Field(this)
    private var collisionCooldown = 0f
    /** Batoh na chrbte – ide s hráčom do budovy. */
    val inventory = Inventory()

    /**
     * Kufor auta. Väčší než batoh, ale dostupný len pri stojacom aute, takže
     * pri prehrabávaní budovy treba rátať s tým, čo unesiem naraz.
     */
    val boot = Inventory(GameConfig.BOOT_SLOTS, GameConfig.BOOT_MAX_WEIGHT)
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
    /** Pri 100 km čakáme na vedomú voľbu: ukončiť príbeh alebo pokračovať. */
    var safeHavenDecisionPending: Boolean = false
        private set
    var endlessMode: Boolean = false
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
        message = copy.msgRoofLights(roofLightsOn)
    }

    private var nightWarned = false
    private var altWarned = false
    private var holdWarned = false

    /** Úsek trate, v ktorom auto práve je. */
    var currentFeature: RoadFeature = RoadFeature.STRAIGHT
        private set

    private var wearWarnCooldown = 0f
    private var lastWearWarnStep = 0
    private var stuckTime = 0f
    /** Čas točenia na mieste pri plyne – oddelené od HUD hlášky. */
    private var spinStuckTime = 0f
    private var spinStuckOriginX = 0f
    /** Hráč sa už pretáčal bez východiska; po zastavení môže volať asist. */
    private var hopelessSpinLatched = false
    private var fluidRescues = 0
    /** Vrak zo záchrannej siete – aby nevznikal nový pri každom pokuse. */
    private var rescueStashId: Long? = null
    /** Starý save údaj – záchrana ide cez helpAdsRemaining, nie voľný pokus. */
    private var batteryRescues = 0
    /**
     * Koľko núdzových rewarded reklám (záchrana / ROAD ASSIST) už táto jazda
     * minula. Lak, upgrade v dielni a dvojnásobný šrot sem nepatria.
     */
    var helpAdsUsed: Int = 0
        private set
    val helpAdsMax: Int get() = GameConfig.HELP_ADS_MAX
    val helpAdsRemaining: Int
        get() = (helpAdsMax - helpAdsUsed).coerceAtLeast(0)
    /** Zostávajúce núdzové reklamy, napr. `5/5`. */
    val helpAdsQuotaLabel: String
        get() = "$helpAdsRemaining/$helpAdsMax"

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

    /** Dynamická fronta počasia za autom; null = nič hráča nenaháňa. */
    var pursuitStorm: ActivePursuitStorm? = null
        private set
    val pursuitStormKind: PursuitStormKind? get() = pursuitStorm?.kind
    val pursuitStormGapM: Float? get() = pursuitStorm?.gapM
    val pursuitStormCaught: Boolean get() = pursuitStorm?.gapM?.let { it <= 0.01f } == true
    /** 0 = front je iba na obzore, 1 = auto je v búrke. */
    val pursuitStormIntensity: Float
        get() = pursuitStorm?.let {
            (1f - it.gapM / PURSUIT_VISIBLE_GAP_M).coerceIn(0f, 1f)
        } ?: 0f

    val daylight: Float get() = DayCycle.daylight(timeOfDay)
    val isNight: Boolean get() = DayCycle.isNight(timeOfDay)
    val clock: String get() = DayCycle.clock(timeOfDay)

    var throttleInput = 0f
    var brakeInput = 0f

    var utilityTrailerAttached: Boolean = false
        private set

    /** Aktívny krátky kontrakt; jazda ani svet sa kvôli nemu neprepínajú. */
    var sideMission: ActiveSideMission? = null
        private set
    var completedSideMission: SideMissionCompletion? = null
        private set
    private var lastSideMissionCompletedAtM = -SIDE_MISSION_COOLDOWN_M
    private var fragileCargoDamageCooldownS = 0f

    val sideMissionTitle: String?
        get() = sideMission?.let { copy.sideMissionTitle(it.kind) }
    val sideMissionProgress: String?
        get() = sideMission?.let { mission ->
            if (mission.kind.isDelivery && !mission.packageLoaded) {
                return@let copy.pickUpMissionPackage
            }
            when (mission.kind) {
                SideMissionKind.DELIVERY -> copy.deliveryMissionProgress(
                    copy.buildingName(mission.targetBuildingType ?: BuildingType.HOUSE),
                    (mission.targetWorldX - car.x).toInt().coerceAtLeast(0),
                    mission.rewardScrap
                )
                SideMissionKind.EXPRESS_DELIVERY -> copy.expressMissionProgress(
                    copy.buildingName(mission.targetBuildingType ?: BuildingType.HOUSE),
                    (mission.targetWorldX - car.x).toInt().coerceAtLeast(0),
                    mission.timeRemainingS.toInt().coerceAtLeast(0),
                    mission.rewardScrap + mission.bonusRewardScrap
                )
                SideMissionKind.FRAGILE_DELIVERY -> copy.fragileMissionProgress(
                    copy.buildingName(mission.targetBuildingType ?: BuildingType.HOUSE),
                    (mission.targetWorldX - car.x).toInt().coerceAtLeast(0),
                    (mission.cargoIntegrity * 100f).toInt().coerceIn(0, 100),
                    mission.rewardScrap
                )
                SideMissionKind.SALVAGE -> copy.salvageMissionProgress(
                    (itemsLooted - mission.startItemsLooted).coerceIn(0, mission.goalCount),
                    mission.goalCount,
                    mission.rewardScrap
                )
            }
        }

    /** Najďalej dosiahnuté X – limituje cúvanie. */
    var maxReachedX: Float = 4f
        private set

    var activeBuilding: WorldBuilding? = null
        private set
    /**
     * Jedna odmena z reklamy na budovu za jazdu – dielňa aj lakovňa.
     * Bez toho by šlo v jednom shope vyupgradeovať všetko.
     */
    private val shopAdUsedBuildingIds = linkedSetOf<Long>()

    /**
     * Prvé ťuknutie na REMOVE v CAR. Druhé ťuknutie na ten istý slot
     * diel skutočne zloží; iný slot potvrdenie zahodí.
     */
    var pendingUnmountSlot: ComponentSlot? = null
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

    private var accumulator = 0f
    private var eventCooldown = 14f
    private var tireHazardAcc = 0f
    /** Čas od posledného kameňa pri protivetru – bez toho by bil každý frame. */
    private var headwindStrikeAcc = 0f
    private var lastPursuitStormAtM = -1f
    private var pursuitStormsStarted = 0
    /**
     * Hotové vetvy nasledujúcej križovatky. Generujú sa po jednej v rôznych
     * fyzikálnych krokoch, aby dve možnosti nevytvorili jeden dlhý frame.
     */
    private val preparedContinuations = linkedMapOf<Int, RoadSegment>()
    /** Najviac jedna plná vetva sa smie vygenerovať v jednom render frame. */
    private var continuationBuiltThisAdvance = false
    /**
     * Predchádzajúci región ostáva rendereru dostupný za autom. Inak sa pri
     * technickej výmene [segment] zmení naraz profil cesty, budovy aj povrchy,
     * ktoré sú ešte stále na ľavej strane obrazovky.
     */
    private var previousVisualSegment: RoadSegment? = null
    /** Obe ramená poslednej križovatky ostanú viditeľné aj po prejazde. */
    private val previousForkContinuations = linkedMapOf<Int, RoadSegment>()
    private var previousForkSelectedChoiceId: Int? = null
    /** Vzhľad regiónu, z ktorého sme práve vošli; pre fade až ZA križovatkou. */
    private var entryVisualBiomeFrom: BiomeType? = null
    private var entryPavingFrom: RoadPaving? = null
    private var entryBackdropSeed: Long = 0L
    internal var entryForkX=Float.NaN
        private set
    internal var entryForkIndex=0
        private set
    internal val previousSegmentForRender: RoadSegment? get() = previousVisualSegment
    internal val previousJunctionSegmentForRender: RoadSegment?
        get() = previousVisualSegment?.takeIf { previousForkContinuations.isNotEmpty() }
    internal val previousJunctionSelectedChoiceId: Int? get() = previousForkSelectedChoiceId
    internal fun previousContinuationForRender(choiceId: Int): RoadSegment? =
        previousForkContinuations[choiceId]
    internal val nextChoiceForRender: BranchChoice? get() = segment.selectedChoice
    internal val nextSegmentForRender: RoadSegment?
        get() = nextChoiceForRender?.let { preparedContinuations[it.id] }
    /** Pripravená geometria každej vetvy pre 3D náhľad križovatky. */
    internal fun continuationForRender(choiceId: Int): RoadSegment? =
        preparedContinuations[choiceId]

    init {
        val startRng = SeededRandom(seed xor 0x57A27L)
        car.installStarterKit(startRng, debugOptions)
        applyGarageUpgrades()
        stockStarterPack(startRng)
        stockGarageKit()

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
        car.setCargoLoad(
            inventory.totalWeight,
            boot.totalWeight + if (utilityTrailerAttached) UTILITY_TRAILER_TARE_KG else 0f
        )
        car.snapToGround(segment.heightAtWorld(car.x))
        maxReachedX = car.x
        stockStarterShed(startRng)
        ensureTrailerOpportunity(segment)
        fuelWasBelowFull = car.fuelRatio < 0.95f
        // Prvé vetvy pripravíme počas načítania jazdy, nie počas rýchlej jazdy.
        prepareAllContinuations()
        message = copy.searchTheGarage
    }

    /** Cruise drží plyn mimo pedálu – spotreba ide cez [Car.cruiseFuelMultiplier]. */
    var cruiseControlActive: Boolean = false

    private fun applyGarageUpgrades() {
        car.setGaragePerformance(
            engineTune = garageUpgrades.engineTune,
            loadSuspension = garageUpgrades.loadSuspension,
            brakePackage = garageUpgrades.brakePackage,
            cruiseEfficiency = garageUpgrades.cruiseEfficiency
        )
        if (garageUpgrades.electrics >= 1) {
            ensureStarterElectrics(includeAlternator = garageUpgrades.electrics >= 2)
            if (garageUpgrades.electrics >= 2) {
                car.batteryCharge = (car.batteryCharge + 0.25f).coerceAtMost(1f)
            }
        }
        if (garageUpgrades.starterTyres >= 1) {
            val floor = if (garageUpgrades.starterTyres >= 2) 0.82f else 0f
            fitStarterTyres(floor)
        }
        if (garageUpgrades.fuelReserve > 0) {
            car.refill(
                FluidType.FUEL,
                garageUpgrades.fuelReserve.coerceIn(0, 3) * 3f,
                purity = 1f,
                fuelKind = car.requiredFuelKind
            )
        }
        if (garageUpgrades.oilReserve > 0) {
            car.refill(
                FluidType.OIL,
                garageUpgrades.oilReserve.coerceIn(0, 2) * 1.2f,
                purity = 0.92f
            )
        }
        val conditionBonus = garageUpgrades.reliability.coerceIn(0, 3) * 0.05f
        if (conditionBonus > 0f) {
            val mechanical = setOf(
                ComponentSlot.ENGINE, ComponentSlot.STARTER, ComponentSlot.ALTERNATOR,
                ComponentSlot.RADIATOR, ComponentSlot.BATTERY, ComponentSlot.FUEL_TANK,
                ComponentSlot.BRAKES, ComponentSlot.DRIVETRAIN, ComponentSlot.SUSPENSION,
                ComponentSlot.TIRE_FRONT, ComponentSlot.TIRE_REAR
            )
            mechanical.forEach { slot ->
                car.parts[slot]?.let { part -> part.health = (part.health + conditionBonus).coerceAtMost(1f) }
            }
        }
        syncCargoCapacity()
    }

    private fun ensureStarterElectrics(includeAlternator: Boolean) {
        fun fitIfMissing(slot: ComponentSlot, def: ItemDef) {
            if (car.hasPart(slot)) return
            car.parts[slot] = MountedPart(
                def.id,
                ComponentCondition.USED,
                0.72f
            )
            if (slot == ComponentSlot.BATTERY && car.batteryCharge < 0.35f) {
                car.batteryCharge = 0.55f
            }
        }
        fitIfMissing(ComponentSlot.BATTERY, ItemCatalog.BATTERY)
        fitIfMissing(ComponentSlot.STARTER, ItemCatalog.STARTER)
        if (includeAlternator) fitIfMissing(ComponentSlot.ALTERNATOR, ItemCatalog.ALTERNATOR)
    }

    private fun fitStarterTyres(healthFloor: Float) {
        listOf(ComponentSlot.TIRE_FRONT, ComponentSlot.TIRE_REAR).forEach { slot ->
            val old = car.parts[slot]
            val health = maxOf(old?.health ?: 0.55f, healthFloor).coerceAtMost(1f)
            val condition = when {
                health >= 0.65f -> ComponentCondition.USED
                health >= 0.35f -> ComponentCondition.DAMAGED
                else -> ComponentCondition.CRITICAL
            }
            car.parts[slot] = MountedPart(
                ItemCatalog.TIRE.id,
                condition,
                health
            )
        }
    }

    private fun stockGarageKit() {
        fun add(def: ItemDef, purity: Float = 1f) {
            inventory.add(
                ItemStack(
                    defId = def.id,
                    condition = ComponentCondition.NEW,
                    health = 1f,
                    count = 1,
                    purity = purity
                )
            )
        }
        if (garageUpgrades.spareKit >= 1) add(ItemCatalog.PUNCTURE_KIT)
        if (garageUpgrades.spareKit >= 2) add(ItemCatalog.COOLANT_BOTTLE, purity = 0.92f)
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
        // Štart má naučiť údržbu auta, nie preskočiť motorový progres.
        // Defektová sada je vždy užitočná a neznehodnotí prvý neskorší
        // nález výkonnejšieho motora.
        shed.loot.add(
            ItemStack(
                ItemCatalog.PUNCTURE_KIT.id,
                ComponentCondition.NEW,
                health = 1f
            )
        )
        // Len jeden ukážkový interiérový diel. Predné sedadlo je permanentne
        // vo vraku, takže v kôlni ukážeme zadné.
        val showcase = ItemCatalog.SEAT_REAR
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
    val inJunctionZone: Boolean
        get() = segment.choices.size > 1 && junctionDistanceM <= GameConfig.JUNCTION_APPROACH

    /** Koľko metrov zostáva k rázcestiu. */
    val junctionDistanceM: Float get() = (segment.endWorldX - car.x).coerceAtLeast(0f)

    /** true, kým sa dá vetva vybrať za jazdy; začína ešte pred fade-om pozadia. */
    val approachingJunction: Boolean
        get() = segment.choices.size > 1 && junctionDistanceM in 0.01f..JUNCTION_SWIPE_LEAD_M

    /**
     * Prostredie pred križovatkou ostáva stabilné bez ohľadu na swipe.
     * Nový bióm sa priestorovo prelína až po vstupe do zvolenej vetvy.
     */
    internal fun visualBiomeBlendAt(worldX: Float): BiomeBlend {
        val current = segment.visualBiome
        if(worldX>segment.endWorldX) nextSegmentForRender?.let { next ->
            return BiomeBlend(current,next.visualBiome,MathX.smoothstep(segment.endWorldX,
                segment.endWorldX+GameConfig.JUNCTION_SCENERY_TRANSITION_M,worldX))
        }
        val incoming = entryVisualBiomeFrom ?: return BiomeBlend(current, current, 0f)
        val start = segment.worldOrigin
        val end = start + GameConfig.JUNCTION_SCENERY_TRANSITION_M
        if (worldX <= start) return BiomeBlend(incoming, incoming, 0f)
        if (worldX >= end || incoming == current) return BiomeBlend(current, current, 0f)
        return BiomeBlend(incoming, current, MathX.smoothstep(start, end, worldX))
    }

    val biomeBlend get() = visualBiomeBlendAt(car.x)
    internal val backdropFromSeedForRender: Long
        get() = if (
            entryVisualBiomeFrom != null &&
            car.x < segment.worldOrigin + GameConfig.JUNCTION_SCENERY_TRANSITION_M
        ) entryBackdropSeed else segment.seed
    internal val backdropToSeedForRender: Long get() = segment.seed
    internal fun entryTransitionAmountAt(worldX: Float): Float {
        if (entryVisualBiomeFrom == null && entryPavingFrom == null) return 0f
        return MathX.smoothstep(
            segment.worldOrigin,
            segment.worldOrigin + GameConfig.JUNCTION_SCENERY_TRANSITION_M,
            worldX
        )
    }
    internal fun entryPavingForRenderAt(worldX: Float): RoadPaving? {
        val previous = entryPavingFrom ?: return null
        val local = worldX - segment.worldOrigin
        return previous.takeIf {
            it != segment.paving && local in 0f..GameConfig.JUNCTION_SCENERY_TRANSITION_M
        }
    }
    val winterAmount: Float
        get() {
            val previous = entryPavingFrom
            val local = car.x - segment.worldOrigin
            if (previous == null || local !in 0f..GameConfig.JUNCTION_SCENERY_TRANSITION_M) {
                return if (segment.paving.winter) 1f else 0f
            }
            val amount = MathX.smoothstep(0f, GameConfig.JUNCTION_SCENERY_TRANSITION_M, local)
            return MathX.lerp(
                if (previous.winter) 1f else 0f,
                if (segment.paving.winter) 1f else 0f,
                amount
            )
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
    private val needsJunctionCall: Boolean
        get() = inJunctionZone && pendingChoiceId == null && segment.choices.size > 1

    fun advance(frameDt: Float) {
        if (timeHeldByMenu) return
        val dt = frameDt.coerceAtMost(GameConfig.MAX_FRAME_TIME)
        accumulator += dt
        continuationBuiltThisAdvance = false
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

    }

    private fun fixedUpdate(dt: Float) {
        collisionCooldown = (collisionCooldown - dt).coerceAtLeast(0f)
        car.setCargoLoad(
            inventory.totalWeight,
            boot.totalWeight + if (utilityTrailerAttached) UTILITY_TRAILER_TARE_KG else 0f
        )
        messageAge += dt
        if (phase != GamePhase.GAME_OVER) {
            sideMission?.takeIf {
                it.kind == SideMissionKind.EXPRESS_DELIVERY &&
                    it.packageLoaded && it.timeRemainingS > 0f
            }?.let { mission ->
                // Toto beží v každom fyzikálnom kroku. Mutácia runtime hodnoty
                // nevytvára 60 nových mission objektov za sekundu.
                mission.timeRemainingS = (mission.timeRemainingS - dt).coerceAtLeast(0f)
            }
            fragileCargoDamageCooldownS =
                (fragileCargoDamageCooldownS - dt).coerceAtLeast(0f)
            timeOfDay = DayCycle.advance(timeOfDay, dt)
            if (tickElectrics(dt)) return
            maybeStartOverduePursuitStorm()
            // Vo vnútri budovy má hráč čas potvrdiť úkryt. Vonku sa front
            // približuje aj pri státí a na križovatke.
            if (phase != GamePhase.EXPLORING || activeBuilding == null) tickPursuitStorm(dt)
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
                car.idleCool(dt)
                syncRide(dt)
            }
            GamePhase.GAME_OVER -> Unit
        }
        if (phase != GamePhase.DRIVING) updateBlockedReason(dt)
        updateTireAssist(dt)
        if (phase != GamePhase.GAME_OVER) {
            car.tickBodyWeather(
                dt,
                winterAmount,
                currentSurface,
                segment.paving,
                hasEvent(RoadEvent.RAIN) || hasEvent(RoadEvent.STORM),
                kotlin.math.abs(car.speed),
                car.grounded,
                car.wheelSlip,
                foggy = hasEvent(RoadEvent.FOG)
            )
        }
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
            message = copy.gettingDark
        }
        if (!isNight) nightWarned = false
        if (car.engineRunning && phase == GamePhase.DRIVING) {
            val hold = car.batteryHoldCapacity
            if (hold in 0.001f..0.18f) {
                if (!holdWarned) {
                    holdWarned = true
                    message = copy.batteryWontHold
                }
            } else {
                holdWarned = false
            }
            if (!car.hasPart(ComponentSlot.ALTERNATOR) && !altWarned) {
                altWarned = true
                message = copy.noAlternator
            }
        }
        return false
    }

    /** Cyklicky: vypnuté → stretávacie → diaľkové → vypnuté. */
    fun toggleHeadlights(): Boolean {
        // Bez svetlometu nie je čo rozsvietiť. Plná batéria ani bežiaci
        // alternátor na tom nič nezmenia – žiarovka je v tom kuse plastu.
        if (!headlightsOn && !car.hasPart(ComponentSlot.HEADLIGHT)) {
            message = copy.noHeadlightFitted
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
                !car.hasPart(ComponentSlot.ALTERNATOR) -> copy.beamsNoAlt(highBeamsOn)
                car.batteryCharge < 0.25f && !car.engineRunning -> copy.beamsWeakBattery(highBeamsOn)
                highBeamsOn -> copy.highBeamsOn
                else -> copy.lowBeamsOn
            }
        } else {
            copy.headlightsOff
        }
        return headlightsOn
    }

    private fun syncRide(dt: Float) {
        car.planar.stop()
        val gy = World3D.height(this, car.x, car.planar.z)
        val slope = World3D.slope(this, car.x, car.planar.z, car.planar.heading)
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
            message = missingText(missing)
        } else if (prepStep != PrepStep.DONE) {
            message = copy.prepHint(prepStep)
        } else {
            car.prepChecklistDone = true
            val worst = minOf(car.fuelPurity, car.oilPurity, car.coolantPurity)
            message = if (worst < 0.8f) {
                copy.fluidsDirty(copy.fluidGradeName(FluidGrade.of(worst)).lowercase())
            } else {
                copy.engineRunningPressDrive
            }
        }
    }

    private fun updateDriving(dt: Float) {
        elapsed += dt
        // Ďalší región vznikne ešte pred začiatkom viditeľného prelínania.
        // Práca s budovami a lootom sa tak nikdy nestretne s technickou hranicou.
        if (!continuationBuiltThisAdvance &&
            preparedContinuations.size < segment.choices.size &&
            car.x >= segment.transitionStartWorldX - CONTINUATION_PRELOAD_LEAD
        ) {
            prepareContinuationStep()
            continuationBuiltThisAdvance = true
        }
        val fuelBefore = car.fuel
        val slopeNow = World3D.slope(this, car.x, car.planar.z, car.planar.heading)
        tickEvents(dt)
        val regionDrain = segment.style.fuelDrainMul
        val cruiseMul = if (cruiseControlActive) car.cruiseFuelMultiplier else 1f
        val cause = car.tickDriving(
            dt, throttleInput, regionDrain * eventDrainMul, slopeNow, cold,
            cruiseFuelMul = cruiseMul
        )
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
        val heading = car.planar.heading
        val hx = kotlin.math.cos(heading)
        val hz = kotlin.math.sin(heading)
        fun axleHeight(x: Float, z: Float): Float {
            val axleX = kotlin.math.cos(car.planar.heading)
            val axleZ = kotlin.math.sin(car.planar.heading)
            return maxOf(
                World3D.height(this,x-axleZ*0.78f,z+axleX*0.78f),
                World3D.height(this,x+axleZ*0.78f,z-axleX*0.78f)
            )
        }
        fun sweptHeight(offset: Float): Float {
            var highest = Float.NEGATIVE_INFINITY
            for (sample in 0..4) {
                val travel = car.speed * dt * sample / 4f + offset
                highest = maxOf(highest, axleHeight(cx + hx*travel, car.planar.z + hz*travel))
            }
            return highest
        }
        val rearGy = sweptHeight(-wb)
        val frontGy = sweptHeight(wb)
        val rearSlope = World3D.slope(this,cx-hx*wb,car.planar.z-hz*wb,heading)
        val frontSlope = World3D.slope(this,cx+hx*wb,car.planar.z+hz*wb,heading)
        // Každá náprava môže byť na inej strane hrebeňa. Jeden spoločný sklon
        // pridával zadnému kolesu falošnú vertikálnu rýchlosť a vystreľoval ho.
        val offRoad = kotlin.math.abs(car.planar.z - World3D.roadCenter(car.x)) > World3D.ROAD_HALF_WIDTH
        val bumpiness = segment.bumpinessAtLocal(localX.coerceIn(0f, segment.length - 0.5f)) + if (offRoad) 0.16f else 0f
        currentSurface = World3D.surface(this, car.x, car.planar.z)
        refreshPatchAhead()
        val oldX = car.x
        val oldZ = car.planar.z
        car.applyDrive(
            dt,
            throttleInput * eventThrottleMul,
            brakeInput,
            rearGy,
            frontGy,
            bumpiness + eventBumpBonus,
            currentSurface,
            if (offRoad) 0.72f else segment.paving.gripMul,
            isWinter,
            rearGroundSlope = rearSlope,
            frontGroundSlope = frontSlope,
            windAcceleration = eventWindAcceleration
        )
        car.planar.advance(
            car.speed, steeringInput,
            currentSurface.gripMul * (if (offRoad) 0.72f else segment.paving.gripMul) *
                (if (isWinter) car.axleSnowGrip(ComponentSlot.TIRE_FRONT) else car.axleGrip(ComponentSlot.TIRE_FRONT)),
            car.grounded, dt
        )
        val destinationX = oldX + car.planar.deltaX
        val destinationZ = car.planar.z
        val steps = (kotlin.math.hypot(destinationX-oldX, destinationZ-oldZ) / 0.3f).toInt().coerceIn(1,16)
        car.x = oldX
        car.planar.z = oldZ
        for (step in 1..steps) {
            val fraction = step.toFloat()/steps
            val nextX = oldX + (destinationX-oldX)*fraction
            val nextZ = oldZ + (destinationZ-oldZ)*fraction
            if (World3D.blocked(this,nextX,nextZ)) {
                if (collisionCooldown <= 0f && kotlin.math.abs(car.speed) > 3f) {
                    val damage = (kotlin.math.abs(car.speed)*0.007f).coerceAtMost(0.24f)
                    car.parts[ComponentSlot.FRONT_BUMPER]?.let { it.health = (it.health-damage).coerceAtLeast(0f) }
                    collisionCooldown = 0.6f
                }
                car.speed = 0f
                car.planar.stop()
                break
            }
            car.x = nextX
            car.planar.z = nextZ
        }
        damageFragileCargo(
            bumpiness = bumpiness + eventBumpBonus
        )
        // applyDrive posunie X až po výpočte pruženia. Preto kontakt ešte raz
        // vyriešime presne pod finálnou polohou auta; pri vysokej rýchlosti
        // nesmie zostať ani jediná snímka medzi predikciou a skutočnou cestou.
        val finalHx = kotlin.math.cos(car.planar.heading)
        val finalHz = kotlin.math.sin(car.planar.heading)
        val finalRearX = car.x - wb * finalHx
        val finalFrontX = car.x + wb * finalHx
        car.resolveRoadPenetration(
            axleHeight(finalRearX, car.planar.z-wb*finalHz),
            axleHeight(finalFrontX, car.planar.z+wb*finalHz),
            World3D.slope(this,finalRearX,car.planar.z-wb*finalHz,car.planar.heading),
            World3D.slope(this,finalFrontX,car.planar.z+wb*finalHz,car.planar.heading),
            bodyGroundAtOffset = { offset -> World3D.height(this,car.x+offset*finalHx,car.planar.z+offset*finalHz) }
        )
        car.tickWear(dt, bumpiness + eventBumpBonus, brakeInput, winterRoad = isWinter)
        tickTireHazards(dt)
        applyFeatureEffects(dt)
        if (phase == GamePhase.GAME_OVER) return
        if (car.x > maxX) {
            car.x = maxX
            if (car.speed > 0f) car.speed = 0f
        }
        // Región sa na hranici len technicky vymení. Vizuál a terén sa menili
        // už kilometre pred ňou, preto tu nesmie byť zastavenie ani teleport.
        if (pendingChoiceId==null && segment.choices.size>1 && car.x>=segment.endWorldX-1.5f) {
            val lateral=car.planar.z-World3D.roadCenter(car.x)
            if(kotlin.math.abs(lateral)>3f) {
                val index=if(lateral<0f) 0 else segment.choices.lastIndex
                selectBranch(segment.choices[index].id)
            }
        }
        if (car.x >= segment.endWorldX && segment.choices.isNotEmpty()) {
            segment.selectedChoice?.let(::enterBranch)
            return
        }
        if (car.x < minX) {
            car.x = minX
            if (car.speed < 0f) car.speed = 0f
        }
        if (car.x > maxReachedX) maxReachedX = car.x

        distanceM = maxReachedX.coerceAtLeast(0f)
        if (distanceM >= Journey.FINAL_DISTANCE_M && !endlessMode) {
            if (relayProgress >= Journey.goals.size) {
                car.speed = 0f
                phase = GamePhase.STOPPED
                safeHavenDecisionPending = true
                message = copy.safeHavenReachedMsg
            } else {
                car.speed = 0f
                phase = GamePhase.STOPPED
                message = copy.safeHavenOffline
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
                    if (near.relayRestored) copy.relayOnlineFuel
                    else copy.relayStationHint
                } else {
                    copy.stopAndSearch(copy.buildingName(near.type))
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
                step >= 3 -> copy.engineAboutToDie((health * 100).toInt(), cause?.let { copy.wearWarning(it) } ?: copy.fixIt)
                step == 2 -> copy.engineBadlyWorn((health * 100).toInt())
                else -> cause?.let { copy.wearWarning(it) } ?: copy.engineWearing((health * 100).toInt())
            }
            return
        }
        if (health > 0.5f) lastWearWarnStep = 0
        // Aj pri zdravom motore hlásime, ak ho niečo aktívne zožiera.
        if (cause != null && car.wearRate > 0.002f && wearWarnCooldown <= 0f) {
            wearWarnCooldown = 12f
            message = copy.wearWarning(cause)
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
        val winterish = isWinter ||
            currentSurface == RoadSurface.ICE ||
            currentSurface == RoadSurface.SLUSH
        val climb = car.maxClimbSlope(bump, car.speed, winterish)
        val absSlope = kotlin.math.abs(slope)
        blockedReason = when {
            missing.isNotEmpty() -> missingText(missing)
            !car.engineRunning -> copy.engineIsOff
            car.fuel <= 0.05f -> copy.outOfFuel
            brakeInput > 0.2f -> copy.releaseBrake
            !car.grounded -> copy.noTractionAir
            slope > 0.12f && slope > climb * 0.95f && isStrandedOnShreddedRim() ->
                copy.onTheRimAssist
            slope > 0.12f && slope > climb * 0.95f ->
                copy.climbTooSteep
            absSlope < 0.08f && car.wheelSlip > 0.55f && car.tireGrip < 0.45f ->
                if (hasUsefulTireRecovery()) {
                    copy.tyresSpinning
                } else {
                    copy.noTractionAssist
                }
            car.powerHp < 22f -> copy.engineTooWeak(car.powerHp.toInt())
            else -> copy.carWontMove
        }
    }

    /**
     * Pretáčanie na mieste (bahno, sneh, ráfik) – nie krátke zastavenie
     * na križovatke, brzda ani ubratie plynu. Až po 6 s bez východiska
     * jazda skončí; ručný ROAD ASSIST v STOPPED ide hneď, ako je auto
     * na ráfiku alebo už pretáčanie zapadlo.
     */
    private fun updateTireAssist(dt: Float) {
        if (phase != GamePhase.DRIVING) {
            spinStuckTime = 0f
            return
        }
        if (kotlin.math.abs(car.speed) > 3.5f) hopelessSpinLatched = false

        val driveHeld = throttleInput >= 0.45f && brakeInput <= 0.15f
        val shredded = hasDrivenShreddedTire()
        // Na ráfiku do kopca sa auto šmýka dozadu – to nie je únik.
        // Pri bahne/snehu berieme |rýchlosť|, aby cúvanie nebolo „státie“.
        val slow = if (shredded) {
            car.speed <= GameConfig.TIRE_RESCUE_SPIN_MAX_SPEED
        } else {
            kotlin.math.abs(car.speed) <= GameConfig.TIRE_RESCUE_SPIN_MAX_SPEED
        }
        val spinning = car.grounded &&
            (car.wheelSlip >= GameConfig.TIRE_RESCUE_SPIN_SLIP || shredded)
        if (!driveHeld || !slow || !spinning) {
            spinStuckTime = 0f
            spinStuckOriginX = car.x
            return
        }
        if (spinStuckTime <= 0f) spinStuckOriginX = car.x
        spinStuckTime += dt
        // Na ráfiku sa auto ešte pomaly posunie; to nie je únik z miesta.
        if (!shredded &&
            kotlin.math.abs(car.x - spinStuckOriginX) > GameConfig.TIRE_RESCUE_SPIN_MAX_MOVE_M
        ) {
            spinStuckTime = 0f
            spinStuckOriginX = car.x
            return
        }
        if (hasUsefulTireRecovery()) return
        if (spinStuckTime >= GameConfig.TIRE_RESCUE_LATCH_S) hopelessSpinLatched = true
        if (spinStuckTime >= GameConfig.TIRE_RESCUE_STUCK_S &&
            car.engineRunning &&
            car.fuel > 0.05f
        ) {
            endRun(
                if (hasDrivenShreddedTire() || isStrandedOnShreddedRim()) {
                    EndReason.SHREDDED_TIRE
                } else {
                    EndReason.STUCK_TIRE
                }
            )
        }
    }

    /** Čo bráni naštartovaniu stojaceho auta (null = dá sa naštartovať). */
    private fun startBlocker(): String? {
        val missing = car.missingEssentials()
        val engineHealth = car.parts[ComponentSlot.ENGINE]?.health ?: 0f
        return when {
            missing.isNotEmpty() -> missingText(missing)
            car.fuel < 0.5f -> copy.noFuelInTank
            car.oil < 0.3f -> copy.noOilInEngine
            car.coolant < 0.3f -> copy.radiatorEmpty
            car.wrongFuelFraction >= 0.70f ->
                copy.wrongFuelDrain(copy.fuelKindName(car.requiredFuelKind).lowercase())
            car.batteryCharge < 0.2f -> copy.batteryIsFlat
            engineHealth <= 0.15f -> copy.engineBeyondSaving
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
    /** Krátke vizuálne zásahy; úmyselne nie sú súčasťou uloženého stavu. */
    val visualDamageBursts = VisualDamageBurstBuffer()

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

    /** Vyvolá front naschvál – pre testy a budúce skriptované úseky. */
    fun forcePursuitStorm(kind: PursuitStormKind) {
        startPursuitStorm(kind)
    }

    private fun startPursuitStorm(kind: PursuitStormKind) {
        pursuitStorm = ActivePursuitStorm(kind, PURSUIT_START_GAP_M)
        lastPursuitStormAtM = distanceM
        pursuitStormsStarted++
        message = copy.stormBehind(kind)
    }

    /** Na hranici regiónu môže vzniknúť front vhodný pre aktuálnu krajinu. */
    private fun maybeStartPursuitStorm() {
        if (pursuitStorm != null || distanceM < PURSUIT_UNLOCK_DISTANCE_M ||
            (lastPursuitStormAtM >= 0f && distanceM - lastPursuitStormAtM < PURSUIT_MIN_GAP_BETWEEN_M)
        ) return
        val roll = MathX.hash01(
            seed.toInt() xor segment.seed.toInt(),
            distanceM.toInt() xor 0x57A1
        )
        if (roll > PURSUIT_SEGMENT_CHANCE) return
        val kind = when (segment.visualBiome) {
            BiomeType.DESERT -> PursuitStormKind.SAND
            BiomeType.DESERT_DUSK, BiomeType.WASTELAND -> PursuitStormKind.DUST
            BiomeType.ALPINE -> PursuitStormKind.BLIZZARD
            else -> PursuitStormKind.THUNDER
        }
        startPursuitStorm(kind)
    }

    /** Náhoda ostáva, no hráč nesmie prejsť desiatky kilometrov bez jediného frontu. */
    private fun maybeStartOverduePursuitStorm() {
        if (phase != GamePhase.DRIVING || pursuitStorm != null) return
        val overdue = if (pursuitStormsStarted == 0) {
            distanceM >= PURSUIT_FIRST_GUARANTEE_M
        } else {
            lastPursuitStormAtM >= 0f && distanceM - lastPursuitStormAtM >= PURSUIT_HARD_GAP_M
        }
        if (!overdue) return
        val kind = when (segment.visualBiome) {
            BiomeType.DESERT -> PursuitStormKind.SAND
            BiomeType.DESERT_DUSK, BiomeType.WASTELAND -> PursuitStormKind.DUST
            BiomeType.ALPINE -> PursuitStormKind.BLIZZARD
            else -> PursuitStormKind.THUNDER
        }
        startPursuitStorm(kind)
    }

    private fun tickPursuitStorm(dt: Float) {
        val storm = pursuitStorm ?: return
        storm.ageS += dt
        storm.gapM = (storm.gapM + (car.speed.coerceAtLeast(0f) - storm.kind.speedMps) * dt)
            .coerceIn(0f, PURSUIT_ESCAPE_GAP_M)

        if (storm.gapM >= PURSUIT_ESCAPE_GAP_M && storm.ageS >= PURSUIT_MIN_ESCAPE_TIME_S) {
            scrap += PURSUIT_ESCAPE_SCRAP_REWARD
            message = copy.stormLost(storm.kind, PURSUIT_ESCAPE_SCRAP_REWARD)
            pursuitStorm = null
            return
        }
        if (storm.gapM > 0f) return

        storm.exposureS += dt
        // Dobehnutie nie je game over. Každý typ pomaly tlačí na inú časť auta.
        when (storm.kind) {
            PursuitStormKind.SAND, PursuitStormKind.DUST -> {
                car.parts[ComponentSlot.SUSPENSION]?.let {
                    it.health = (it.health - PURSUIT_SAND_SUSPENSION_WEAR * dt)
                        .coerceAtLeast(0.05f)
                }
            }
            PursuitStormKind.THUNDER -> {
                car.parts[ComponentSlot.ALTERNATOR]?.let {
                    it.health = (it.health - PURSUIT_THUNDER_ELECTRIC_WEAR * dt)
                        .coerceAtLeast(0.05f)
                }
            }
            PursuitStormKind.BLIZZARD -> {
                car.batteryCharge = (car.batteryCharge - PURSUIT_BLIZZARD_BATTERY_DRAIN * dt)
                    .coerceAtLeast(0f)
            }
        }
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
        if (hasEvent(RoadEvent.HEADWIND)) {
            tickHeadwindDebris(dt)
        } else {
            headwindStrikeAcc = 0f
        }
    }

    /**
     * Protivietor občas priženie kameň do predku. Prvý úder je pri vzniku
     * udalosti; tu len zriedkavé ďalšie, nikdy každý frame.
     */
    private fun tickHeadwindDebris(dt: Float) {
        headwindStrikeAcc += dt
        if (headwindStrikeAcc < GameConfig.EVENT_HEADWIND_STRIKE_GAP) return
        headwindStrikeAcc = 0f
        val rng = SeededRandom(
            seed xor distanceM.toRawBits().toLong() xor
                (elapsed * 1327f).toLong() xor 0x51E0DL
        )
        if (rng.nextFloat() > GameConfig.EVENT_HEADWIND_STRIKE_CHANCE) return
        strikeFrontFromHeadwind(rng)
    }

    /** Škrabnutie predku – nárazník, kapota, svetlo alebo predná guma. */
    private fun strikeFrontFromHeadwind(rng: SeededRandom) {
        val options = HEADWIND_FRONT_SLOTS.filter { car.hasPart(it) }
        if (options.isNotEmpty()) {
            val slot = options[rng.nextInt(options.size)]
            car.parts[slot]?.let {
                it.health = (it.health - rng.nextFloat(
                    GameConfig.EVENT_ROCK_CHIP_MIN,
                    GameConfig.EVENT_ROCK_CHIP_MAX
                )).coerceAtLeast(GameConfig.EVENT_ROCK_CHIP_FLOOR)
            }
        }
        visualDamageBursts.add(
            VisualDamageBurstKind.ROCK_STRIKE,
            elapsed,
            alongM = GameConfig.EVENT_HEADWIND_ROCK_EXTRA_M
        )
        emitSfx(GameSfx.ROCK)
    }

    /** Násobiteľ plynu – misfire a protivietor uberú výkon. */
    private val eventThrottleMul: Float
        get() = (if (hasEvent(RoadEvent.MISFIRE)) 0.55f else 1f) *
            (if (hasEvent(RoadEvent.HEADWIND)) 0.92f else 1f) *
            (if (utilityTrailerAttached) 0.88f else 1f)

    /** Násobiteľ spotreby – vietor mení cenu jazdy, čistá cesta šetrí. */
    private val eventDrainMul: Float
        get() = (if (hasEvent(RoadEvent.HEADWIND)) 1.22f else 1f) *
            (if (hasEvent(RoadEvent.TAILWIND)) 0.72f else 1f) *
            (if (hasEvent(RoadEvent.CLEAR_ROAD)) 0.8f else 1f) *
            (if (utilityTrailerAttached) 1.12f else 1f)

    /** Malá sila pozdĺž cesty; protivietor brzdí, zadný vietor pomáha. */
    private val eventWindAcceleration: Float
        get() = (if (hasEvent(RoadEvent.HEADWIND)) -0.65f else 0f) +
            (if (hasEvent(RoadEvent.TAILWIND)) 0.55f else 0f)

    /** Extra hrboľatosť z počasia a prekážok – zhorší záber aj zrýchlenie. */
    private val eventBumpBonus: Float
        get() = (if (hasEvent(RoadEvent.RAIN) || hasEvent(RoadEvent.STORM)) 0.45f else 0f) +
            (if (hasEvent(RoadEvent.STORM)) 0.25f else 0f) +
            (if (hasEvent(RoadEvent.FOG)) 0.12f else 0f) +
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
        RoadEvent.RAIN -> !hasEvent(RoadEvent.STORM) && !hasEvent(RoadEvent.FOG)
        RoadEvent.FOG -> !hasEvent(RoadEvent.RAIN) && !hasEvent(RoadEvent.STORM) && !inJunctionZone
        RoadEvent.STORM -> !hasEvent(RoadEvent.RAIN) && !hasEvent(RoadEvent.FOG) && !inJunctionZone
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
            RoadEvent.ROCK_STRIKE -> {
                car.parts[ComponentSlot.RADIATOR]?.let {
                    it.health = (it.health - rng.nextFloat(
                        GameConfig.EVENT_ROCK_CHIP_MIN,
                        GameConfig.EVENT_ROCK_CHIP_MAX
                    )).coerceAtLeast(GameConfig.EVENT_ROCK_CHIP_FLOOR)
                }
                visualDamageBursts.add(VisualDamageBurstKind.ROCK_STRIKE, elapsed)
            }
            RoadEvent.HEADWIND -> {
                // Vietor nie je len spomalenie – hneď na začiatku príde kameň
                // do predku. Ďalšie údery sú zriedkavé, kým HEADWIND trvá.
                strikeFrontFromHeadwind(rng)
                headwindStrikeAcc = 0f
            }
            RoadEvent.OIL_SPLASH -> {
                // Špina v oleji – objem ostane, kvalita klesne. Zásah musí byť
                // malý: dolievanie čistotu len mieša, takže veľké skoky sa
                // nasčítali do stavu, z ktorého sa hráč nemal ako dostať.
                car.oilPurity = (car.oilPurity - rng.nextFloat(0.04f, 0.11f)).coerceAtLeast(0.05f)
                visualDamageBursts.add(VisualDamageBurstKind.OIL_SPLASH, elapsed)
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
        if (!e.timed) message = customMessage ?: copy.roadEventMessage(e)
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
        visualDamageBursts.add(VisualDamageBurstKind.TYRE_BLOWOUT, elapsed, slot)
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
        RoadEvent.FOG -> GameSfx.FOG
        RoadEvent.STORM -> GameSfx.STORM
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
            return copy.itemAhead(copy.itemName(def))
        }
        segment.buildings += find
        return copy.wreckAhead
    }

    /**
     * Vyberie vetvu dopredu, za jazdy. Auto nezastavuje – na rázcestí sa
     * jednoducho zaradí tam, kam hráč ukázal.
     */
    fun selectBranch(choiceId: Int): Boolean {
        val choice = segment.choices.find { it.id == choiceId } ?: return false
        pendingChoiceId = choiceId
        segment.selectedChoiceId = choiceId
        message = copy.taking(copy.branchLabel(choice.style) + if (choice.winter) " ❄" else "")
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
        val outgoing = segment
        val origin = outgoing.endWorldX
        entryForkX=if(outgoing.choices.size>1) origin else Float.NaN
        entryForkIndex=outgoing.choices.indexOfFirst { it.id==choice.id }.coerceAtLeast(0)
        entryVisualBiomeFrom = outgoing.visualBiome
        entryPavingFrom = outgoing.paving
        entryBackdropSeed = outgoing.seed
        segmentTripDistance = origin
        previousForkContinuations.clear()
        previousForkContinuations.putAll(preparedContinuations)
        previousForkSelectedChoiceId = choice.id
        val ready = preparedContinuations.remove(choice.id)?.takeIf {
            it.seed == choice.plan.seed && kotlin.math.abs(it.worldOrigin - origin) < 0.01f
        }
        segment = ready ?: WorldGenerator.createSegment(
            plan = choice.plan,
            worldOrigin = origin,
            tripDistance = origin,
            terrain = terrain,
            isTutorial = false
        )
        previousVisualSegment = outgoing
        applyRelayProgress(segment)
        ensureTrailerOpportunity(segment)
        preparedContinuations.clear()
        pendingChoiceId = null
        rescueStashId = null
        currentFeature = segment.featureAtWorld(car.x)
        // Rýchlosť, poloha ani pruženie sa nemenia – žiadny teleport či snap.
        activeBuilding = null
        phase = GamePhase.DRIVING
        heldPatchAhead = null
        maybeStartPursuitStorm()
        // Bez novej hlášky a premerania HUD: hráč už zmenu regiónu videl
        // počas prelínania a technická hranica má byť úplne neviditeľná.
    }

    /** Vytvorí najviac jednu ešte nepripravenú vetvu – práca sa rozloží medzi framy. */
    private fun prepareContinuationStep() {
        val choice = segment.choices.firstOrNull { it.id !in preparedContinuations } ?: return
        val origin = segment.endWorldX
        val prepared = WorldGenerator.createSegment(
            plan = choice.plan,
            worldOrigin = origin,
            tripDistance = origin,
            terrain = terrain,
            isTutorial = false
        )
        applyRelayProgress(prepared)
        preparedContinuations[choice.id] = prepared
    }

    /** Používa sa iba pri štarte/obnove, keď hra ešte nekreslí jazdu. */
    private fun prepareAllContinuations() {
        repeat(segment.choices.size) { prepareContinuationStep() }
    }

    fun requestStop(): Boolean {
        if (phase != GamePhase.DRIVING) return false
        if (car.speed > GameConfig.STOP_SPEED * 3f) {
            message = copy.slowDownFirst
            return false
        }
        car.speed = 0f
        phase = GamePhase.STOPPED
        message = copy.stopped
        return true
    }

    fun resumeDriving(): Boolean {
        if (phase != GamePhase.STOPPED && phase != GamePhase.EXPLORING && phase != GamePhase.PREP) {
            return false
        }
        if (!car.engineRunning) {
            message = copy.startTheEngine
            return false
        }
        activeBuilding = null
        prepStep = PrepStep.DONE
        car.prepChecklistDone = true
        phase = GamePhase.DRIVING
        message = copy.driving
        return true
    }

    /** Svetová pozícia budovy – aj keď ešte visí na susednom vizuálnom segmente. */
    private fun worldXOf(b: WorldBuilding): Float {
        previousVisualSegment?.let { seg ->
            if (seg.buildings.any { it === b }) return seg.worldOrigin + b.localX
        }
        nextSegmentForRender?.let { seg ->
            if (seg.buildings.any { it === b }) return seg.worldOrigin + b.localX
        }
        return segment.worldOrigin + b.localX
    }

    private inline fun forEachVisibleBuilding(action: (WorldBuilding, Float) -> Unit) {
        previousVisualSegment?.let { seg ->
            for (b in seg.buildings) action(b, seg.worldOrigin + b.localX)
        }
        for (b in segment.buildings) action(b, segment.worldOrigin + b.localX)
        nextSegmentForRender?.let { seg ->
            for (b in seg.buildings) action(b, seg.worldOrigin + b.localX)
        }
    }

    fun buildingNear(): WorldBuilding? {
        var best: WorldBuilding? = null
        var bestDist = GameConfig.BUILDING_INTERACT_RANGE
        forEachVisibleBuilding { b, wx ->
            val dz = (kotlin.math.abs(World3D.buildingZ(wx)-car.planar.z)-7f).coerceAtLeast(0f)
            val d = kotlin.math.hypot(wx-car.x, dz)
            if (d <= bestDist) {
                bestDist = d
                best = b
            }
        }
        return best
    }

    private fun hasUtilityTrailerAvailable(): Boolean =
        utilityTrailerAttached ||
            inventory.slots.any { it?.defId == ItemCatalog.UTILITY_TRAILER.id } ||
            boot.slots.any { it?.defId == ItemCatalog.UTILITY_TRAILER.id }

    /**
     * Prvý vhodný objekt po odomknutí dostane vozík. Ak ho hráč obíde, ďalší
     * región ponuku zopakuje, kým si jeden skutočne nezapojí alebo nevezme.
     */
    private fun ensureTrailerOpportunity(targetSegment: RoadSegment) {
        if (hasUtilityTrailerAvailable() || targetSegment.endWorldX < TRAILER_GUARANTEE_FROM_M) return
        if (targetSegment.buildings.any { building ->
                building.loot.any { it.defId == ItemCatalog.UTILITY_TRAILER.id }
            }
        ) return
        val earliestUsefulX = maxOf(TRAILER_GUARANTEE_FROM_M, car.x + 35f)
        if (earliestUsefulX >= targetSegment.endWorldX - 40f) return
        val host = targetSegment.buildings
            .asSequence()
            .filter { !it.landmark && !it.type.isServiceShop }
            .filter { targetSegment.worldOrigin + it.localX >= earliestUsefulX }
            .minByOrNull { it.localX }
            ?: WorldBuilding(
                id = targetSegment.seed xor TRAILER_WRECK_SALT,
                type = BuildingType.WRECK,
                localX = (earliestUsefulX - targetSegment.worldOrigin + 80f)
                    .coerceAtMost(targetSegment.length - 40f)
            ).also { targetSegment.buildings += it }
        host.loot.add(0, ItemStack(ItemCatalog.UTILITY_TRAILER.id, ComponentCondition.NEW, 1f))
    }

    private fun deliveryTargetAhead(sourceBuildingId: Long): Pair<WorldBuilding, Float>? {
        var best: WorldBuilding? = null
        var bestWorldX = Float.POSITIVE_INFINITY
        // Cieľ musí už naozaj existovať v aktuálnom regióne. Hráč tak nikdy
        // nedostane odpočet k imaginárnemu bodu, za ktorým nie je budova.
        for (candidate in segment.buildings) {
            val worldX = segment.worldOrigin + candidate.localX
            if (candidate.id == sourceBuildingId || candidate.landmark ||
                worldX < car.x + SIDE_MISSION_MIN_DELIVERY_M || worldX >= bestWorldX
            ) continue
            best = candidate
            bestWorldX = worldX
        }
        return best?.let { it to bestWorldX }
    }

    fun sideMissionOffer(): SideMissionOffer? {
        val b = activeBuilding ?: return null
        if (phase != GamePhase.EXPLORING || sideMission != null || completedSideMission != null || b.landmark ||
            distanceM < SIDE_MISSION_UNLOCK_M ||
            distanceM - lastSideMissionCompletedAtM < SIDE_MISSION_COOLDOWN_M
        ) return null
        val deliveryTarget = deliveryTargetAhead(b.id)
        val variation = MathX.hash01(seed.toInt(), b.id.toInt())
        val kind = when {
            b.type == BuildingType.GARAGE || b.type == BuildingType.WRECK ||
                b.type == BuildingType.AUTO_SHOP -> SideMissionKind.SALVAGE
            deliveryTarget != null && variation < 0.38f -> SideMissionKind.DELIVERY
            deliveryTarget != null && variation < 0.69f -> SideMissionKind.EXPRESS_DELIVERY
            deliveryTarget != null -> SideMissionKind.FRAGILE_DELIVERY
            else -> SideMissionKind.SALVAGE
        }
        return if (kind.isDelivery) {
            val (target, targetWorldX) = deliveryTarget!!
            val distance = (targetWorldX - car.x).toInt().coerceAtLeast(0)
            val distanceReward = 8 + (distance / 350).coerceAtMost(8)
            val reward = distanceReward + (variation * 4f).toInt() + when (kind) {
                SideMissionKind.FRAGILE_DELIVERY -> 7
                SideMissionKind.EXPRESS_DELIVERY -> 2
                else -> 0
            }
            val timeLimit = if (kind == SideMissionKind.EXPRESS_DELIVERY) {
                (distance / 11f + 32f).coerceIn(75f, 240f)
            } else 0f
            SideMissionOffer(
                kind = kind,
                targetDistanceM = distance,
                goalCount = 0,
                rewardScrap = reward,
                targetWorldX = targetWorldX,
                targetBuildingId = target.id,
                targetBuildingType = target.type,
                bonusRewardScrap = if (kind == SideMissionKind.EXPRESS_DELIVERY) {
                    (reward * 0.75f).toInt().coerceAtLeast(6)
                } else 0,
                timeLimitS = timeLimit
            )
        } else {
            SideMissionOffer(kind, 0, 3, 6 + (variation * 3f).toInt())
        }
    }

    val sideMissionOfferText: String?
        get() = sideMissionOffer()?.let { offer ->
            when (offer.kind) {
                SideMissionKind.DELIVERY -> copy.deliveryMissionOffer(
                    copy.buildingName(offer.targetBuildingType ?: BuildingType.HOUSE),
                    offer.targetDistanceM,
                    offer.rewardScrap
                )
                SideMissionKind.EXPRESS_DELIVERY -> copy.expressMissionOffer(
                    copy.buildingName(offer.targetBuildingType ?: BuildingType.HOUSE),
                    offer.targetDistanceM,
                    offer.timeLimitS.toInt(),
                    offer.rewardScrap,
                    offer.bonusRewardScrap
                )
                SideMissionKind.FRAGILE_DELIVERY -> copy.fragileMissionOffer(
                    copy.buildingName(offer.targetBuildingType ?: BuildingType.HOUSE),
                    offer.targetDistanceM,
                    offer.rewardScrap
                )
                SideMissionKind.SALVAGE -> copy.salvageMissionOffer(offer.goalCount, offer.rewardScrap)
            }
        }

    fun startSideMission(): Boolean {
        val b = activeBuilding ?: return false
        val offer = sideMissionOffer() ?: return false
        sideMission = ActiveSideMission(
            kind = offer.kind,
            sourceBuildingId = b.id,
            startWorldX = car.x,
            targetWorldX = offer.targetWorldX,
            targetBuildingId = offer.targetBuildingId,
            targetBuildingType = offer.targetBuildingType,
            startItemsLooted = itemsLooted,
            goalCount = offer.goalCount,
            rewardScrap = offer.rewardScrap,
            bonusRewardScrap = offer.bonusRewardScrap,
            timeRemainingS = offer.timeLimitS,
            packageLoaded = false,
            cargoIntegrity = 1f
        )
        if (offer.kind.isDelivery) {
            // Kontrakt iba pripraví zásielku. Hráč ju uvidí medzi nálezmi a
            // musí ju fyzicky naložiť; tým je jasné, čo vlastne preváža.
            b.loot.removeAll { it.defId == ItemCatalog.MISSION_PACKAGE.id }
            b.loot.add(0, ItemStack(ItemCatalog.MISSION_PACKAGE.id, ComponentCondition.NEW, 1f))
        }
        message = copy.sideMissionStarted(offer.kind)
        return true
    }

    /** Ukrytie je bezpečná alternatíva k úteku, ale bez odmeny za stratený front. */
    fun shelterFromPursuit(): Boolean {
        val storm = pursuitStorm ?: return false
        val building = activeBuilding ?: return false
        if (phase != GamePhase.EXPLORING) return false
        pursuitStorm = null
        message = copy.shelteredFromStorm(storm.kind, copy.buildingName(building.type))
        return true
    }

    private fun completeSideMission(mission: ActiveSideMission) {
        val timedBonus = if (
            mission.kind == SideMissionKind.EXPRESS_DELIVERY && mission.timeRemainingS > 0f
        ) mission.bonusRewardScrap else 0
        val baseReward = if (mission.kind == SideMissionKind.FRAGILE_DELIVERY) {
            (mission.rewardScrap * (0.30f + 0.70f * mission.cargoIntegrity))
                .toInt().coerceAtLeast(1)
        } else mission.rewardScrap
        val earned = baseReward + timedBonus
        removeMissionPackage()
        scrap += earned
        sideMission = null
        lastSideMissionCompletedAtM = distanceM
        completedSideMission = SideMissionCompletion(
            mission.kind, earned, timedBonus, mission.cargoIntegrity
        )
        message = copy.sideMissionCompleted(earned)
    }

    /** Tvrdý dopad alebo prudké dorazenie pruženia poškodí iba krehkú zásielku. */
    private fun damageFragileCargo(
        bumpiness: Float
    ) {
        val mission = sideMission ?: return
        if (mission.kind != SideMissionKind.FRAGILE_DELIVERY || !mission.packageLoaded ||
            fragileCargoDamageCooldownS > 0f
        ) return

        val landingSeverity = car.landingImpact
        val bottomOutSeverity = MathX.smoothstep(0.72f, 1f, car.suspensionLoad) *
            MathX.smoothstep(7f, 24f, kotlin.math.abs(car.speed)) *
            (0.45f + bumpiness.coerceIn(0f, 1.5f) * 0.35f)
        applyFragileCargoShock(maxOf(landingSeverity, bottomOutSeverity).coerceIn(0f, 1f))
    }

    internal fun applyFragileCargoShock(severity: Float): Boolean {
        val mission = sideMission ?: return false
        if (mission.kind != SideMissionKind.FRAGILE_DELIVERY || !mission.packageLoaded ||
            fragileCargoDamageCooldownS > 0f || severity < 0.12f
        ) return false
        val damage = 0.045f + severity.coerceIn(0f, 1f) * 0.17f
        val integrity = (mission.cargoIntegrity - damage).coerceAtLeast(0f)
        mission.cargoIntegrity = integrity
        fragileCargoDamageCooldownS = FRAGILE_CARGO_DAMAGE_COOLDOWN_S
        message = copy.fragileCargoDamaged((integrity * 100f).toInt())
        return true
    }

    fun claimDoubleSideMissionReward(): Boolean {
        val completion = completedSideMission ?: return false
        scrap += completion.rewardScrap
        completedSideMission = null
        message = copy.sideMissionCompleted(completion.rewardScrap * 2)
        return true
    }

    fun dismissSideMissionReward(): Boolean {
        if (completedSideMission == null) return false
        completedSideMission = null
        return true
    }

    private fun removeMissionPackage() {
        fun removeFrom(source: Inventory): Boolean {
            val index = source.slots.indexOfFirst { it?.defId == ItemCatalog.MISSION_PACKAGE.id }
            if (index < 0) return false
            source.removeAt(index)
            return true
        }
        if (!removeFrom(inventory)) removeFrom(boot)
    }

    private fun maybeCompleteSalvageMission(): Boolean {
        val mission = sideMission ?: return false
        if (mission.kind != SideMissionKind.SALVAGE ||
            itemsLooted - mission.startItemsLooted < mission.goalCount
        ) return false
        completeSideMission(mission)
        return true
    }

    fun enterNearestBuilding(): Boolean {
        // PREP je tiež „stojíme pri aute“ – bez toho by sa hráč nedostal
        // ani do kôlne hneď vedľa, z ktorej má vrak sprevádzkovať.
        if (phase != GamePhase.STOPPED && phase != GamePhase.DRIVING && phase != GamePhase.PREP) {
            return false
        }
        if (phase == GamePhase.DRIVING && car.speed > GameConfig.STOP_SPEED) {
            message = copy.stopAtBuilding
            return false
        }
        car.speed = 0f
        val b = buildingNear() ?: run {
            message = copy.noBuildingNearby
            return false
        }
        activeBuilding = b
        if (b.type.isServiceShop) b.loot.clear()
        phase = GamePhase.EXPLORING
        if (visitedBuildingIds.add(b.id)) buildingsVisited++
        message = if (b.type.isServiceShop) {
            copy.insideBuilding(copy.buildingName(b.type))
        } else {
            copy.searchingBuilding(copy.buildingName(b.type))
        }
        sideMission?.takeIf { mission ->
            mission.kind.isDelivery && mission.packageLoaded &&
                if (mission.targetBuildingId != 0L) {
                    b.id == mission.targetBuildingId
                } else {
                    b.id != mission.sourceBuildingId && car.x >= mission.targetWorldX
                }
        }?.let(::completeSideMission)
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
            message = copy.enterRelayFirst
            return false
        }
        if (relayIndex !in Journey.goals.indices) {
            message = copy.networkFullyRestored
            return false
        }
        if (relayIndex != relayProgress) {
            message = if (relayIndex < relayProgress) {
                copy.relayAlreadyOnline
            } else {
                copy.restorePreviousRelay
            }
            return false
        }
        if (b.relayRestored || (b.relayIndex >= 0 && b.relayIndex < relayIndex)) {
            message = copy.relayAlreadyOnline
            return false
        }
        if (b.relayIndex >= 0 && b.relayIndex != relayIndex) {
            message = copy.restorePreviousRelay
            return false
        }
        val cost = relayRestoreCost(relayIndex)
        if (scrap < cost) {
            message = copy.needScrap(cost, scrap)
            return false
        }
        if (relayModuleCount() <= 0) {
            message = copy.findRelayModule
            return false
        }

        if (!consumeRelayModule()) return false
        scrap -= cost
        b.relayRestored = true
        emitSfx(GameSfx.RADIO)
        message = copy.relayRestoredMsg(relayIndex + 1, Journey.goals.size)
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
        preparedContinuations.values.forEach(::applyRelayProgress)
    }

    private fun applyRelayProgress(target: RoadSegment) {
        target.buildings
            .filter { it.landmark && it.relayIndex in 0 until relayProgress }
            .forEach { it.relayRestored = true }
    }

    fun notePlayer(text: String) {
        message = text
    }

    /**
     * Ručný ROAD ASSIST / batéria v STOPPED/EXPLORING – rovnaká ponuka ako
     * po GAME_OVER, ale hráč nemusí nechať jazdu skončiť.
     */
    val canRequestAssist: Boolean
        get() = (phase == GamePhase.STOPPED || phase == GamePhase.EXPLORING) &&
            rescueOffer != null

    /** Jednorazová núdzová pomoc z rewarded reklamy – podľa toho, čo auto naozaj drží. */
    val rescueOffer: RescueOffer?
        get() {
            if (helpAdsRemaining <= 0) return null
            if (phase == GamePhase.GAME_OVER) {
                return when (endReason) {
                    EndReason.OUT_OF_FUEL -> RescueOffer.FUEL
                    EndReason.BATTERY_DEAD -> batteryRescueOffer()
                    EndReason.OVERHEAT, EndReason.ENGINE_DESTROYED -> diagnoseMechanicalRescue()
                    EndReason.SHREDDED_TIRE, EndReason.STUCK_TIRE -> tireShopOffer()
                    EndReason.ARRIVED, EndReason.MANUAL, null -> null
                }
            }
            if (phase != GamePhase.STOPPED && phase != GamePhase.EXPLORING) return null
            if (isEligibleForTireAssist()) return tireShopOffer()
            if (isStrandedWithDeadBattery()) return batteryRescueOffer()
            return null
        }

    val rescueAdLabel: String?
        get() = rescueOffer?.let { "${copy.rescueAd(it)}  $helpAdsQuotaLabel" }

    private fun radiatorIsBroken(): Boolean {
        val rad = car.parts[ComponentSlot.RADIATOR] ?: return true
        return rad.health < 0.38f || car.radiatorMul < 0.45f
    }

    private fun coolingCanIdleRecover(): Boolean =
        !radiatorIsBroken() && car.coolant >= 0.3f

    private fun hasStoredFluid(fluid: FluidType): Boolean {
        fun matches(stack: ItemStack) =
            stack.def.fluid == fluid &&
                (fluid != FluidType.FUEL || stack.def.fuelKind == car.requiredFuelKind)
        return inventory.slots.any { it != null && matches(it) } ||
            boot.slots.any { it != null && matches(it) } ||
            buildingNear()?.loot?.any(::matches) == true
    }

    /** Čo treba opraviť, aby sa dalo znova ísť – nie len EndReason. */
    private fun diagnoseMechanicalRescue(): RescueOffer {
        val cause = car.wearCause
        if (cause == WearCause.NO_RADIATOR || cause == WearCause.BAD_RADIATOR || radiatorIsBroken()) {
            return RescueOffer.RADIATOR
        }
        if (cause == WearCause.LOW_COOLANT || cause == WearCause.FROZEN_COOLANT || car.coolant < 1f) {
            return RescueOffer.COOLANT
        }
        if (cause == WearCause.LOW_OIL || cause == WearCause.DIRTY_OIL || car.oil < 0.3f) {
            return RescueOffer.OIL
        }
        if (cause == WearCause.WRONG_FUEL || cause == WearCause.DIRTY_FUEL) {
            return RescueOffer.FUEL
        }
        if (endReason == EndReason.OVERHEAT && car.coolant < 0.3f) {
            return RescueOffer.COOLANT
        }
        return RescueOffer.ENGINE
    }

    fun recoverFromRewardedAd(): Boolean {
        if (helpAdsRemaining <= 0) return false
        val parked = phase == GamePhase.STOPPED || phase == GamePhase.EXPLORING
        if (phase != GamePhase.GAME_OVER && !parked) return false
        val offer = rescueOffer ?: return false
        if (parked &&
            offer != RescueOffer.TOW_SHOP &&
            offer != RescueOffer.ROADSIDE_SHOP &&
            offer != RescueOffer.BATTERY &&
            offer != RescueOffer.BATTERY_FIT
        ) {
            return false
        }
        val detail = when (offer) {
            RescueOffer.FUEL -> applyFuelRescue()
            RescueOffer.BATTERY, RescueOffer.BATTERY_FIT -> applyBatteryRescue()
            RescueOffer.RADIATOR -> applyRadiatorRescue()
            RescueOffer.COOLANT -> applyCoolantRescue()
            RescueOffer.OIL -> applyOilRescue()
            RescueOffer.ENGINE -> applyEngineRescue()
            RescueOffer.TOW_SHOP -> applyTowShopRescue()
            RescueOffer.ROADSIDE_SHOP -> applyRoadsideShopRescue()
        }
        hopelessSpinLatched = false
        spinStuckTime = 0f
        endReason = null
        endDetail = ""
        activeBuilding = null
        phase = GamePhase.STOPPED
        prepStep = PrepStep.DONE
        car.prepChecklistDone = true
        car.stopEngine()
        car.speed = 0f
        message = detail
        helpAdsUsed = (helpAdsUsed + 1).coerceAtMost(helpAdsMax)
        return true
    }

    private fun restoreEngineEnoughToStart() {
        val engine = car.parts[ComponentSlot.ENGINE]
        if (engine == null) {
            car.mount(
                ComponentSlot.ENGINE,
                ItemStack(ItemCatalog.ENGINE_A.id, ComponentCondition.DAMAGED, 0.40f)
            )
        } else if (engine.health < 0.35f) {
            car.repair(ComponentSlot.ENGINE, 0.35f - engine.health)
        }
    }

    private fun applyFuelRescue(): String {
        if (!car.hasPart(ComponentSlot.FUEL_TANK)) {
            car.mount(
                ComponentSlot.FUEL_TANK,
                ItemStack(ItemCatalog.FUEL_TANK.id, ComponentCondition.DAMAGED, 0.50f)
            )
        }
        val wrongOrDirty = car.wearCause == WearCause.WRONG_FUEL ||
            car.wearCause == WearCause.DIRTY_FUEL ||
            car.wrongFuelFraction >= 0.70f
        if (wrongOrDirty) car.drain(FluidType.FUEL)
        car.refill(FluidType.FUEL, 12f, 0.88f, car.requiredFuelKind)
        if (wrongOrDirty) restoreEngineEnoughToStart()
        return if (wrongOrDirty) {
            copy.roadsideFuelDrained(copy.fuelKindName(car.requiredFuelKind).lowercase())
        } else {
            copy.roadsideFuel12
        }
    }

    private fun applyBatteryRescue(): String {
        if (!car.hasPart(ComponentSlot.BATTERY)) {
            car.mount(
                ComponentSlot.BATTERY,
                ItemStack(ItemCatalog.BATTERY.id, ComponentCondition.DAMAGED, 0.50f)
            )
        } else {
            car.repair(ComponentSlot.BATTERY, 0.50f)
        }
        car.batteryCharge = minOf(car.batteryHoldCapacity, 0.50f)
        return if (car.batteryHoldCapacity >= 0.50f) {
            copy.roadsideBattery50
        } else {
            copy.roadsideBatteryFit
        }
    }

    private fun applyRadiatorRescue(): String {
        val rad = car.parts[ComponentSlot.RADIATOR]
        if (rad == null) {
            car.mount(
                ComponentSlot.RADIATOR,
                ItemStack(ItemCatalog.RADIATOR.id, ComponentCondition.USED, 0.55f)
            )
        } else if (rad.health < 0.55f) {
            car.repair(ComponentSlot.RADIATOR, 0.55f - rad.health)
        }
        car.refill(FluidType.COOLANT, 3f, 0.75f)
        car.temperature = 55f
        restoreEngineEnoughToStart()
        return copy.roadsideRadiator
    }

    private fun applyCoolantRescue(): String {
        car.refill(FluidType.COOLANT, 3f, 0.75f)
        car.temperature = 55f
        restoreEngineEnoughToStart()
        return copy.roadsideCoolant
    }

    private fun applyOilRescue(): String {
        val dirty = car.wearCause == WearCause.DIRTY_OIL || car.oilPurity < 0.55f
        if (dirty) car.drain(FluidType.OIL)
        car.refill(FluidType.OIL, 2f, 0.85f)
        restoreEngineEnoughToStart()
        return if (dirty) {
            copy.roadsideOilDrained
        } else {
            copy.roadsideOil
        }
    }

    private fun applyEngineRescue(): String {
        if (car.hasPart(ComponentSlot.ENGINE)) {
            car.repair(ComponentSlot.ENGINE, 0.50f)
        } else {
            car.mount(
                ComponentSlot.ENGINE,
                ItemStack(ItemCatalog.ENGINE_A.id, ComponentCondition.DAMAGED, 0.50f)
            )
        }
        car.temperature = minOf(car.temperature, 70f)
        return copy.roadsideEngine50
    }

    /**
     * Hnaná náprava na ráfiku, bez náhradnej gumy v batohu/kufri/budove
     * nablízku a bez možnosti prehodiť zdravú gumu z voľnej nápravy.
     */
    private fun isStrandedOnShreddedRim(): Boolean {
        val blocked = blockingShreddedSlots()
        if (blocked.isEmpty()) return false
        if (storedHasCompatibleTire(blocked)) return false
        if (nearbyHasCompatibleTire(blocked)) return false
        if (canSwapHealthyTyreOntoDrivenAxle()) return false
        return true
    }

    private fun hasDrivenShreddedTire(): Boolean =
        TIRE_SLOTS.any { car.drives(it) && car.tireInjury(it) == TireInjury.SHREDDED }

    /** Ráfik bez náhrady, alebo už pretáčaný úsek bez lepšej gumy v batohu. */
    private fun isEligibleForTireAssist(): Boolean {
        if (hasUsefulTireRecovery()) return false
        return isStrandedOnShreddedRim() || hopelessSpinLatched
    }

    private fun tireShopOffer(): RescueOffer =
        if (nearestTowableShop() != null) RescueOffer.TOW_SHOP else RescueOffer.ROADSIDE_SHOP

    private fun batteryRescueOffer(): RescueOffer =
        if (car.hasPart(ComponentSlot.BATTERY)) RescueOffer.BATTERY else RescueOffer.BATTERY_FIT

    /** Stojí s vybitou batériou a nemá náhradnú v batohu, kufri ani nablízku. */
    private fun isStrandedWithDeadBattery(): Boolean =
        car.batteryCharge < 0.2f && !canRecoverFrom(EndCause.BATTERY_DEAD)

    /**
     * Má hráč čím z preklzu alebo ráfika von – zimná/lepšia guma, reťaze,
     * záplata defektu, náhradná guma alebo prehodenie náprav.
     */
    private fun hasUsefulTireRecovery(): Boolean {
        if (canSwapHealthyTyreOntoDrivenAxle()) return true
        if (car.hasPuncturedTyre && hasPunctureKitAnywhere()) return true
        return anyCarriedOrNearby { spareWouldHelp(it) }
    }

    private fun hasPunctureKitAnywhere(): Boolean = anyCarriedOrNearby { it.def.isPunctureKit }

    private fun anyCarriedOrNearby(predicate: (ItemStack) -> Boolean): Boolean =
        inventory.slots.any { it != null && predicate(it) } ||
            boot.slots.any { it != null && predicate(it) } ||
            buildingNear()?.loot?.any(predicate) == true

    private fun spareWouldHelp(stack: ItemStack): Boolean {
        val winterish = isWinter ||
            currentSurface == RoadSurface.ICE ||
            currentSurface == RoadSurface.SLUSH
        if (stack.def.mountsTo == ComponentSlot.CHAINS) {
            return winterish && !car.hasChains
        }
        if (stack.def.isPunctureKit) return car.hasPuncturedTyre
        if (!stack.def.axleTire) return false
        val slots = TIRE_SLOTS.filter { car.drives(it) }.ifEmpty { TIRE_SLOTS }
        if (slots.none { stack.def.canMountTo(it) }) return false
        if (slots.any { car.tireInjury(it) == TireInjury.SHREDDED }) return true
        if (winterish) {
            return stack.def.snowGrip > car.winterTraction + 0.12f ||
                stack.def.id == ItemCatalog.TIRE_WINTER.id ||
                stack.def.id == ItemCatalog.TIRE_OFFROAD.id
        }
        val currentGrip = slots.minOf { car.axleGrip(it) }
        return stack.def.grip > currentGrip * 1.15f
    }

    private fun blockingShreddedSlots(): List<ComponentSlot> =
        TIRE_SLOTS.filter { car.drives(it) && car.tireInjury(it) == TireInjury.SHREDDED }

    private fun storedHasCompatibleTire(slots: List<ComponentSlot>): Boolean {
        fun matches(stack: ItemStack) = slots.any { stack.def.canMountTo(it) }
        return inventory.slots.any { it != null && matches(it) } ||
            boot.slots.any { it != null && matches(it) }
    }

    private fun nearbyHasCompatibleTire(slots: List<ComponentSlot>): Boolean =
        buildingNear()?.loot?.any { stack -> slots.any { stack.def.canMountTo(it) } } == true

    /** 2WD: zdravá guma na voľnej náprave sa dá prehodiť na hnanú. */
    private fun canSwapHealthyTyreOntoDrivenAxle(): Boolean {
        val healthy = TIRE_SLOTS.filter { car.tireInjury(it) != TireInjury.SHREDDED }
        if (healthy.isEmpty()) return false
        return healthy.any { !car.drives(it) } &&
            TIRE_SLOTS.any { car.drives(it) && car.tireInjury(it) == TireInjury.SHREDDED }
    }

    /**
     * Existujúci servis na aktuálnom segmente, dosť blízko na ťahanie.
     * Prázdny vykradnutý shop sa nepočíta – hráč by tam nemal čo namontovať.
     * Ťahať do bahna / prudkého kopca tiež nie – to nie je záchrana.
     */
    private fun nearestTowableShop(): WorldBuilding? {
        val range = GameConfig.TIRE_RESCUE_TOW_RANGE_M
        val reverseOk = GameConfig.REVERSE_LIMIT
        var best: WorldBuilding? = null
        var bestDist = range
        forEachVisibleBuilding { b, wx ->
            if (!isTowableService(b)) return@forEachVisibleBuilding
            if (!shopGroundIsTowable(b)) return@forEachVisibleBuilding
            val dx = wx - car.x
            val dist = kotlin.math.abs(dx)
            if (dist > range) return@forEachVisibleBuilding
            // Späť len v rámci cúvania – inak by clamp vrátil auto na kopec.
            if (dx < -reverseOk) return@forEachVisibleBuilding
            if (dist < bestDist) {
                bestDist = dist
                best = b
            }
        }
        return best
    }

    private fun isTowableService(b: WorldBuilding): Boolean {
        if (b.type != BuildingType.GARAGE) return false
        val slots = blockingShreddedSlots().ifEmpty { TIRE_SLOTS }
        return b.loot.any { stack -> slots.any { stack.def.canMountTo(it) } }
    }

    private fun shopGroundIsTowable(shop: WorldBuilding): Boolean {
        val wx = worldXOf(shop)
        val around = collectRescueSamples(
            wx - RescueSpotFinder.START_RUN_M,
            wx + RescueSpotFinder.START_RUN_M
        )
        val atShop = sampleRescueGround(wx).copy(occupied = false)
        return RescueSpotFinder.isTowableGround(atShop, around)
    }

    private fun applyTowShopRescue(): String {
        val shop = nearestTowableShop() ?: return applyRoadsideShopRescue()
        placeCarAtWorldX(worldXOf(shop))
        return copy.towedToShop
    }

    private fun applyRoadsideShopRescue(): String {
        placeCarAtWorldX(findRescueWorldX())
        spawnRoadsideShop()
        return copy.roadsideGaragePulledUp
    }

    /**
     * Najbližšia rovina s gripom v dosahu – dopredu, kým to nie je skok
     * cez kopec. Keď nič nesedí, aspoň miesto s najmenším |sklonom|.
     */
    private fun findRescueWorldX(): Float {
        val origin = car.x
        val minX = (origin - GameConfig.TIRE_RESCUE_FLAT_BEHIND_M)
            .coerceAtLeast(segment.worldOrigin + 2f)
        var maxX = origin + GameConfig.TIRE_RESCUE_FLAT_AHEAD_M
        val next = nextSegmentForRender
        if (next != null) {
            maxX = minOf(maxX, next.endWorldX - 4f)
        } else {
            maxX = minOf(maxX, segment.endWorldX - 4f)
        }
        maxX = maxOf(maxX, minX + RescueSpotFinder.SAMPLE_STEP_M)
        return RescueSpotFinder.pickWorldX(origin, collectRescueSamples(minX, maxX))
    }

    private fun collectRescueSamples(fromX: Float, toX: Float): List<RescueSpotFinder.Sample> {
        val samples = ArrayList<RescueSpotFinder.Sample>()
        var x = fromX
        while (x <= toX + 0.01f) {
            samples += sampleRescueGround(x)
            x += RescueSpotFinder.SAMPLE_STEP_M
        }
        return samples
    }

    private fun sampleRescueGround(worldX: Float): RescueSpotFinder.Sample {
        val seg = segmentCovering(worldX)
        val local = (worldX - seg.worldOrigin).coerceIn(0.55f, seg.length - 0.55f)
        return RescueSpotFinder.Sample(
            worldX = worldX,
            slope = seg.slopeAtLocal(local),
            surface = seg.surfaceAtLocal(local),
            paving = seg.paving,
            feature = seg.sectionAtLocal(local)?.feature ?: RoadFeature.STRAIGHT,
            occupied = seg.buildingOccupies(worldX, 5.5f)
        )
    }

    private fun segmentCovering(worldX: Float): RoadSegment {
        if (worldX >= segment.worldOrigin && worldX <= segment.endWorldX) return segment
        nextSegmentForRender?.let {
            if (worldX >= it.worldOrigin && worldX <= it.endWorldX) return it
        }
        previousVisualSegment?.let {
            if (worldX >= it.worldOrigin && worldX <= it.endWorldX) return it
        }
        return segment
    }

    /** Ak vybraný bod leží už v pripravenom pokračovaní, presunieme sa tam. */
    private fun ensureSegmentContains(worldX: Float) {
        val next = nextSegmentForRender ?: return
        if (worldX < next.worldOrigin) return
        val choice = segment.selectedChoice ?: return
        if (next.seed != choice.plan.seed) return
        enterBranch(choice)
    }

    private fun placeCarAtWorldX(worldX: Float) {
        ensureSegmentContains(worldX)
        car.x = worldX.coerceIn(segment.worldOrigin + 1.5f, segment.endWorldX - 2f)
        car.planar.z = World3D.roadCenter(car.x)
        car.planar.heading = kotlin.math.atan(World3D.roadCenter(car.x+0.5f)-World3D.roadCenter(car.x-0.5f))
        car.planar.stop()
        car.speed = 0f
        car.stopEngine()
        val loc = localX.coerceIn(0f, segment.length - 0.5f)
        currentSurface = segment.surfaceAtLocal(loc)
        currentFeature = segment.featureAtWorld(car.x)
        syncRide(0f)
        if (car.x > maxReachedX) maxReachedX = car.x
        distanceM = maxReachedX.coerceAtLeast(0f)
    }

    /**
     * Nová dielňa v dosahu INTERACT – hráč nemusí ísť na ráfiku ku dverám.
     * Nikdy nedoplňa už vykradnutú budovu, pri ktorej auto stojí.
     */
    private fun spawnRoadsideShop(): WorldBuilding {
        val ahead = GameConfig.TIRE_RESCUE_SPAWN_AHEAD_M
        var local = localX + ahead
        val worldAhead = segment.worldOrigin + local
        if (local > segment.length - 4f || segment.buildingOccupies(worldAhead, 4.5f)) {
            local = (localX - ahead).coerceAtLeast(2f)
        }
        local = local.coerceIn(2f, segment.length - 2f)
        val rng = SeededRandom(seed xor ROADSIDE_SHOP_SALT xor distanceM.toRawBits().toLong())
        val shop = WorldBuilding(
            id = seed xor ROADSIDE_SHOP_SALT xor distanceM.toRawBits().toLong(),
            type = BuildingType.GARAGE,
            localX = local,
            loot = mutableListOf(
                ItemStack(
                    ItemCatalog.TIRE_POOR.id,
                    ComponentCondition.USED,
                    rng.nextFloat(0.40f, 0.62f)
                ),
                ItemStack(ItemCatalog.PUNCTURE_KIT.id, ComponentCondition.NEW, 1f)
            )
        )
        segment.buildings += shop
        rescueStashId = shop.id
        return shop
    }

    /** Natankuje vybranú hadicu priamo z pumpy (obe majú vlastnú zásobu). */
    fun refuelFromPump(kind: FuelKind = FuelKind.PETROL): Boolean {
        if (!car.hasPart(ComponentSlot.FUEL_TANK)) {
            message = copy.fitFuelTankFirst
            return false
        }
        val b = activeBuilding ?: run {
            message = copy.enterFuelStation
            return false
        }
        if (b.type != BuildingType.GAS_STATION) {
            message = copy.noPumpHere
            return false
        }
        val available = if (kind == FuelKind.DIESEL) b.pumpDieselL else b.pumpFuelL
        if (available <= 0.05f) {
            message = if (b.type == BuildingType.GAS_STATION) {
                copy.pumpEmpty(copy.fuelKindName(kind).lowercase())
            } else {
                copy.noPumpHere
            }
            return false
        }
        val room = car.fuelCapacity - car.fuel
        if (room <= 0.05f) {
            message = copy.tankIsFull
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
        message = copy.filledUp(
            String.format("%.1f", moved),
            copy.fuelKindName(kind).lowercase(),
            copy.fluidGradeName(FluidGrade.of(b.pumpPurity)).lowercase(),
            String.format("%.0f", remaining)
        )
        return true
    }

    /** Prehodenie gúm predok ↔ zadok – jazda sa dá predĺžiť aj bez nálezu. */
    fun swapTyres(): Boolean {
        if (phase == GamePhase.DRIVING || phase == GamePhase.GAME_OVER) {
            message = copy.stopTheCarFirst
            return false
        }
        if (!car.swapTyres()) {
            message = copy.noTyresToSwap
            return false
        }
        message = copy.swappedTyres
        return true
    }

    fun leaveBuilding() {
        if (phase != GamePhase.EXPLORING) return
        val mission = sideMission
        if (mission?.kind?.isDelivery == true && !mission.packageLoaded &&
            activeBuilding?.id == mission.sourceBuildingId
        ) {
            message = copy.pickUpMissionPackage
            return
        }
        activeBuilding = null
        phase = GamePhase.STOPPED
        message = copy.backAtTheCar
    }

    fun takeLoot(index: Int): Boolean {
        val b = activeBuilding ?: return false
        if (index !in b.loot.indices) return false
        val item = b.loot[index]
        // Vozík sa nebalí do batoha. Pri stojacom aute sa z náleziska rovno
        // zapojí, takže jeho vlastná hmotnosť nemôže zablokovať získanie.
        if (item.defId == ItemCatalog.UTILITY_TRAILER.id && !utilityTrailerAttached) {
            b.loot.removeAt(index)
            utilityTrailerAttached = true
            syncCargoCapacity()
            itemsLooted += 1
            val missionCompleted = maybeCompleteSalvageMission()
            if (!missionCompleted) message = copy.trailerAttached(UTILITY_TRAILER_SLOTS)
            return true
        }
        if (item.defId == ItemCatalog.SCRAP_PILE.id) {
            val gained = item.count.coerceAtLeast(1)
            scrap += gained
            b.loot.removeAt(index)
            itemsLooted += 1
            message = copy.salvagedScrap(gained)
            maybeCompleteSalvageMission()
            return true
        }
        // Nájdené ide do batoha; keď je plný, prepadne rovno do kufra, ak je
        // auto na dosah. Nútiť hráča behať tam a späť po jednej veci by bola
        // len práca navyše, nie rozhodovanie.
        val target = when {
            inventory.canFit(item) -> inventory
            bootReachable && boot.canFit(item) -> boot
            else -> {
                message = copy.packFull
                return false
            }
        }
        target.add(item)
        b.loot.removeAt(index)
        itemsLooted += item.count
        val mission = sideMission
        if (item.defId == ItemCatalog.MISSION_PACKAGE.id &&
            mission?.kind?.isDelivery == true && b.id == mission.sourceBuildingId
        ) {
            mission.packageLoaded = true
            message = copy.packageLoaded
        } else {
            message = copy.pickedUp(copy.itemName(item.def))
        }
        maybeCompleteSalvageMission()
        return true
    }

    /** Hráč môže vyriešiť bežnú zastávku jedným rozhodnutím. */
    fun takeAllLoot(): Boolean {
        if (phase != GamePhase.EXPLORING || activeBuilding == null) return false
        var moved = false
        val missionBefore = sideMission
        while (activeBuilding?.loot?.isNotEmpty() == true) {
            val before = activeBuilding?.loot?.size ?: 0
            if (!takeLoot(0)) break
            moved = moved || before > (activeBuilding?.loot?.size ?: before)
        }
        if (moved) {
            message = if (missionBefore != null && sideMission == null) {
                copy.sideMissionCompleted(missionBefore.rewardScrap)
            } else {
                copy.tookEverything
            }
        }
        return moved
    }

    /** Zoberie všetok zostávajúci loot ako materiál bez riešenia kapacity. */
    fun scrapAllLoot(): Boolean {
        val b = activeBuilding ?: return false
        if (phase != GamePhase.EXPLORING || b.loot.isEmpty()) return false
        val scrapable = b.loot.filterNot { it.defId == ItemCatalog.MISSION_PACKAGE.id }
        if (scrapable.isEmpty()) {
            message = copy.pickUpMissionPackage
            return false
        }
        val gained = scrapable.sumOf { it.scrapValue }
        val pieces = scrapable.sumOf { it.count.coerceAtLeast(1) }
        b.loot.removeAll(scrapable.toSet())
        scrap += gained
        itemsLooted += pieces
        message = copy.scrappedLeftovers(gained)
        maybeCompleteSalvageMission()
        return true
    }

    /** Zahodí rozbehnuté potvrdenie zloženia (iný slot, iná akcia). */
    fun cancelPendingUnmount() {
        pendingUnmountSlot = null
    }

    /**
     * Prvé volanie na slote len potvrdí zámer. Druhé na tom istom slote
     * zavolá [unmountSlot]. Iný slot resetuje potvrdenie.
     */
    fun requestUnmount(slot: ComponentSlot): Boolean {
        if (car.parts[slot] == null) {
            pendingUnmountSlot = null
            message = copy.nothingFitted(copy.slotName(slot))
            return false
        }
        if (!canUnmount(slot)) {
            pendingUnmountSlot = null
            message = copy.mustStayFitted
            return false
        }
        if (car.engineRunning && slot in RUNNING_CRITICAL) {
            pendingUnmountSlot = null
            message = copy.switchEngineOffFirst
            return false
        }
        if (pendingUnmountSlot != slot) {
            pendingUnmountSlot = slot
            message = copy.tapAgainToRemove
            return false
        }
        pendingUnmountSlot = null
        return unmountSlot(slot)
    }

    /**
     * Diely s priamym vplyvom na jazdu sa nedajú zložiť – iba vymeniť
     * za iný kus. Inak ojazdené pruženie ide von a auto ide ako nové.
     */
    fun canUnmount(slot: ComponentSlot): Boolean = slot !in DRIVE_FITTED

    /** Demontuje diel z auta späť do inventára. */
    fun unmountSlot(slot: ComponentSlot): Boolean {
        pendingUnmountSlot = null
        val part = car.parts[slot] ?: run {
            message = copy.nothingFitted(copy.slotName(slot))
            return false
        }
        if (!canUnmount(slot)) {
            message = copy.mustStayFitted
            return false
        }
        if (car.engineRunning && slot in RUNNING_CRITICAL) {
            message = copy.switchEngineOffFirst
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
                message = copy.emptyItFirst
                return false
            }
        }
        // Ťažký diel sa do batoha nezmestí, ale do kufra pri aute áno.
        val landing = when {
            inventory.canFit(stack) -> inventory
            bootReachable && boot.canFit(stack) -> boot
            else -> {
                message = copy.noRoomForIt
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
        message = copy.removedSlot(copy.slotName(slot))
        return true
    }

    fun discardInventoryItem(index: Int): Boolean {
        if (inventory.get(index)?.defId == ItemCatalog.MISSION_PACKAGE.id) {
            message = copy.pickUpMissionPackage
            return false
        }
        val stack = inventory.removeAt(index) ?: return false
        message = copy.droppedItem(copy.itemName(stack.def))
        return true
    }

    fun discardBootItem(index: Int): Boolean {
        if (!bootReachable) {
            message = copy.stopAtCarFirst
            return false
        }
        if (boot.get(index)?.defId == ItemCatalog.MISSION_PACKAGE.id) {
            message = copy.pickUpMissionPackage
            return false
        }
        val stack = boot.removeAt(index) ?: return false
        message = copy.droppedItem(copy.itemName(stack.def))
        return true
    }

    fun scrapInventoryItem(index: Int): Boolean = scrapFrom(inventory, index)

    fun scrapBootItem(index: Int): Boolean {
        if (!bootReachable) {
            message = copy.stopAtCarFirst
            return false
        }
        return scrapFrom(boot, index)
    }

    private fun scrapFrom(source: Inventory, index: Int): Boolean {
        if (phase == GamePhase.DRIVING || kotlin.math.abs(car.speed) > 0.2f) {
            message = copy.stopBeforeDismantling
            return false
        }
        if (source.get(index)?.defId == ItemCatalog.MISSION_PACKAGE.id) {
            message = copy.pickUpMissionPackage
            return false
        }
        val stack = source.removeAt(index) ?: return false
        val gained = stack.scrapValue
        scrap += gained
        message = copy.scrappedItem(copy.itemName(stack.def), gained)
        return true
    }

    /** Prepočíta kapacitu batohu podľa namontovaného úložiska. */
    private fun syncCargoCapacity() {
        // Batoh sa nosí, debna a nosič sú na aute – každé zväčšuje niečo iné.
        val permanentSlots = garageUpgrades.storage.coerceIn(0, 3)
        inventory.applyCapacity(
            car.packBonusSlots + permanentSlots,
            car.packBonusWeight + permanentSlots * 4f
        )
        boot.applyCapacity(
            car.bootBonusSlots + if (utilityTrailerAttached) UTILITY_TRAILER_SLOTS else 0,
            car.bootBonusWeight + if (utilityTrailerAttached) UTILITY_TRAILER_CAPACITY_KG else 0f
        )
    }

    /** Do kufra sa dá siahnuť len pri aute – teda vždy, keď sa práve nejde. */
    val bootReachable: Boolean
        get() = phase != GamePhase.DRIVING && phase != GamePhase.GAME_OVER

    /** Presun batoh → kufor. */
    fun stowInBoot(index: Int): Boolean = transfer(inventory, boot, index, copy.whereBoot)

    /** Presun kufor → batoh. */
    fun takeFromBoot(index: Int): Boolean = transfer(boot, inventory, index, copy.wherePack)

    private fun transfer(from: Inventory, to: Inventory, index: Int, whereTo: String): Boolean {
        if (!bootReachable) {
            message = copy.stopAtCarFirst
            return false
        }
        val stack = from.get(index) ?: return false
        if (!to.canFit(stack)) {
            message = copy.noRoomIn(whereTo)
            return false
        }
        from.removeAt(index)
        if (!to.add(stack)) {
            // Nemalo by nastať, ale radšej vec vrátiť než stratiť.
            from.add(stack)
            message = copy.noRoomIn(whereTo)
            return false
        }
        message = copy.movedTo(whereTo, copy.itemName(stack.def))
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
            message = copy.stopAtCarFirst
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
        if (def.isUtilityTrailer) {
            if (utilityTrailerAttached) {
                message = copy.trailerAlreadyAttached
                return false
            }
            if (phase == GamePhase.DRIVING || kotlin.math.abs(car.speed) > 0.2f) {
                message = copy.parkTheCarFirst
                return false
            }
            inventory.removeAt(index)
            utilityTrailerAttached = true
            syncCargoCapacity()
            message = copy.trailerAttached(UTILITY_TRAILER_SLOTS)
            return true
        }
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
                message = copy.fitBeforeFluid(copy.slotName(requiredPart).lowercase())
                return false
            }
            val room = when (def.fluid) {
                FluidType.FUEL -> car.fuelCapacity - car.fuel
                FluidType.OIL -> car.oilCapacity - car.oil
                FluidType.COOLANT -> car.coolantCapacity - car.coolant
                else -> 0f
            }
            if (room <= 0.05f) {
                message = copy.alreadyFull(copy.fluidName(def.fluid))
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
                message = copy.alreadyFull(copy.fluidName(def.fluid))
                return false
            }
            stack.setFluidLitres(available - used)
            if (stack.fluidLitres <= 0.01f) inventory.removeAt(index)
            val fluidLabel = if (def.fluid == FluidType.FUEL) copy.itemName(def) else copy.fluidName(def.fluid)
            message = if (stack.grade == FluidGrade.PURE) {
                copy.toppedUp(String.format("%.1f", used), fluidLabel)
            } else {
                copy.toppedUpGrade(String.format("%.1f", used), copy.fluidGradeName(stack.grade).lowercase())
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
                    message = copy.removedNoRoom(copy.itemName(old.def))
                }
            }
            syncCargoCapacity()
            val note = when {
                fluidOf == null -> ""
                stack.heldFluidL > 0.01f ->
                    copy.withFluidInIt(String.format("%.1f", stack.heldFluidL))
                else -> copy.dryNeeds(copy.fluidName(fluidOf).lowercase())
            }
            message = copy.fittedTo(copy.itemName(def), copy.slotName(slot).lowercase(), note)
            return true
        }
        message = copy.thatCannotBeUsed
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
            message = copy.stopFirst
            return false
        }
        if (car.engineRunning) {
            message = copy.turnEngineOffDrain
            return false
        }
        val had = when (fluid) {
            FluidType.FUEL -> car.fuel
            FluidType.OIL -> car.oil
            FluidType.COOLANT -> car.coolant
            FluidType.BRAKE_FLUID -> 0f
        }
        if (had <= 0.05f) {
            message = copy.nothingToDrain
            return false
        }
        val requested = litres?.coerceAtLeast(0f) ?: had
        val drained = car.drainAmount(fluid, requested)
        message = copy.drained(
            String.format("%.1f", drained),
            copy.fluidName(fluid).lowercase(),
            if (car.fluidLevel(fluid) > 0.05f) {
                copy.litresRemain(String.format("%.1f", car.fluidLevel(fluid)))
            } else {
                copy.emptySuffix
            }
        )
        return true
    }

    fun repairSlot(slot: ComponentSlot): Boolean {
        val part = car.parts[slot] ?: return false
        if (slot in TIRE_SLOTS && part.injury == TireInjury.SHREDDED) {
            message = copy.tyreShredded
            return false
        }
        if (!part.def.hasDurability) {
            message = copy.permanentUpgrade(copy.itemName(part.def))
            return false
        }
        if (part.health >= 0.98f) {
            message = copy.thatOneIsFine
            return false
        }
        val oilIdx = inventory.slots.indexOfFirst { it?.defId == ItemCatalog.OIL_BOTTLE.id }
        if (oilIdx >= 0) {
            val s = inventory.slots[oilIdx]!!
            s.count--
            if (s.count <= 0) inventory.removeAt(oilIdx)
            car.repair(slot, 0.35f)
            message = copy.repairedSlot(copy.slotName(slot))
        } else {
            car.repair(slot, 0.12f)
            message = copy.roughPatch(copy.slotName(slot))
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
        val base = kotlin.math.ceil(fullStepCost * (step / 0.20f)).toInt().coerceAtLeast(1)
        return kotlin.math.ceil(base * GameConfig.SCRAP_REPAIR_COST_MUL).toInt().coerceAtLeast(1)
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
        val base = kotlin.math.ceil(difference * 0.72f + target.baseValue * 0.12f)
            .toInt()
            .coerceAtLeast(18)
        return kotlin.math.ceil(base * GameConfig.SCRAP_UPGRADE_COST_MUL).toInt()
            .coerceAtLeast(1)
    }

    /** Namontované sloty, ktoré majú v katalógu ďalší stupeň. */
    fun scrapUpgradeSlots(): List<ComponentSlot> =
        ComponentSlot.entries.filter { scrapUpgradeTarget(it) != null }

    fun scrapUpgradeBlockReason(slot: ComponentSlot): String? =
        upgradeWorkshopBlockReason(slot)

    private fun upgradeWorkshopBlockReason(slot: ComponentSlot): String? = when {
        !atAutoShop ->
            copy.takeToWorkshop
        distanceM < when (slot) {
            ComponentSlot.ENGINE -> 8_000f
            ComponentSlot.DRIVETRAIN -> 12_000f
            else -> 6_000f
        } -> copy.workshopUnlocksLater
        else -> null
    }

    /** Dôvod, prečo scrap oprava/upgrade práve nejde; null znamená pripravené. */
    fun scrapWorkshopBlockReason(): String? = when {
        phase == GamePhase.DRIVING || kotlin.math.abs(car.speed) > 0.2f ->
            copy.parkTheCarFirst
        car.engineRunning -> copy.switchEngineOffFirst
        else -> null
    }

    /**
     * Banner v overlayi dielne. Pri bežiacom motore musí byť dôvod viditeľný,
     * nie tiché vypnutie tlačidiel RESPRAY / UPGRADE.
     */
    fun workshopServiceBanner(): String? {
        val blocked = scrapWorkshopBlockReason() ?: return null
        return if (car.engineRunning) {
            when {
                atPaintShop && !atAutoShop -> copy.engineOffToRespray
                atAutoShop && !atPaintShop -> copy.engineOffToUpgrade
                else -> copy.engineOffToService
            }
        } else {
            blocked
        }
    }

    private fun workshopReady(): Boolean {
        val blocked = scrapWorkshopBlockReason() ?: return true
        message = blocked
        return false
    }

    fun repairWithScrap(slot: ComponentSlot): Boolean {
        if (!workshopReady()) return false
        if (slot in TIRE_SLOTS && car.parts[slot]?.injury == TireInjury.SHREDDED) {
            message = copy.tyreShredded
            return false
        }
        val cost = scrapRepairCost(slot) ?: run {
            message = copy.doesNotNeedRepair(copy.slotName(slot))
            return false
        }
        if (scrap < cost) {
            message = copy.needScrap(cost, scrap)
            return false
        }
        scrap -= cost
        // Len dezén / opotrebenie. Defekt ostáva — na to je puncture kit.
        car.repair(slot, 0.20f)
        message = copy.repairedCost(copy.slotName(slot), cost)
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
            message = copy.parkTheCarFirst
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
            message = copy.nothingFitted(copy.slotName(slot))
            return false
        }
        if (slot !in TIRE_SLOTS) {
            message = copy.thatIsNotATyre
            return false
        }
        when (part.injury) {
            TireInjury.INFLATED -> {
                message = copy.tyreNotFlat
                return false
            }
            TireInjury.SHREDDED -> {
                message = copy.tyreShredded
                return false
            }
            TireInjury.PUNCTURED -> Unit
        }
        if (consumePunctureKit()) {
            part.injury = TireInjury.INFLATED
            car.repair(slot, GameConfig.PUNCTURE_KIT_HEAL)
            message = copy.patchedSlot(copy.slotName(slot).lowercase())
            return true
        }
        if (!workshopReady()) return false
        val cost = scrapPatchCost()
        if (scrap < cost) {
            message = copy.needKitOrScrap(cost)
            return false
        }
        scrap -= cost
        part.injury = TireInjury.INFLATED
        car.repair(slot, GameConfig.PUNCTURE_KIT_HEAL)
        message = copy.patchedCost(copy.slotName(slot).lowercase(), cost)
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
            message = copy.noFlatToPatch
            return false
        }
        val part = car.parts[slot] ?: run {
            message = copy.nothingFitted(copy.slotName(slot))
            return false
        }
        when (part.injury) {
            TireInjury.INFLATED -> {
                message = copy.tyreNotFlat
                return false
            }
            TireInjury.SHREDDED -> {
                message = copy.tyreShredded
                return false
            }
            TireInjury.PUNCTURED -> Unit
        }
        val stack = source.get(index) ?: return false
        stack.count--
        if (stack.count <= 0) source.removeAt(index)
        part.injury = TireInjury.INFLATED
        car.repair(slot, GameConfig.PUNCTURE_KIT_HEAL)
        message = copy.patchedSlot(copy.slotName(slot).lowercase())
        return true
    }

    fun upgradeWithScrap(slot: ComponentSlot): Boolean = upgradeInternal(slot, free = false)

    /**
     * Jeden náhodný katalogový upgrade v aktuálnej dielni, ktorý ide kúpiť
     * reklamou namiesto scrapu. Seed je run + id budovy. Po uplatnení
     * táto dielňa ďalšiu odmenu v tejto jazde nedá.
     */
    fun workshopAdUpgradeSlot(): ComponentSlot? {
        val shop = serviceShop(BuildingType.AUTO_SHOP) ?: return null
        if (shop.id in shopAdUsedBuildingIds) return null
        val eligible = scrapUpgradeSlots().filter { slot ->
            upgradeWorkshopBlockReason(slot) == null
        }
        if (eligible.isEmpty()) return null
        return SeededRandom(seed xor shop.id xor WORKSHOP_AD_SALT).pick(eligible)
    }

    /** Odmena z rewarded reklamy: jeden katalogový upgrade bez scrapu. */
    fun upgradeFromRewardedAd(slot: ComponentSlot): Boolean {
        if (workshopAdUpgradeSlot() != slot) {
            message = if (!atAutoShop) {
                copy.takeToWorkshop
            } else if (serviceShop(BuildingType.AUTO_SHOP)?.let { it.id in shopAdUsedBuildingIds } == true) {
                copy.workshopAlreadyUsedAd
            } else {
                copy.upgradeNotAdOffer
            }
            return false
        }
        val shop = serviceShop(BuildingType.AUTO_SHOP) ?: return false
        val ok = upgradeInternal(slot, free = true)
        if (ok) shopAdUsedBuildingIds += shop.id
        return ok
    }

    fun paintAdAvailable(): Boolean {
        val shop = serviceShop(BuildingType.PAINT_SHOP) ?: return false
        return paintShopAvailable && shop.id !in shopAdUsedBuildingIds
    }

    private fun serviceShop(type: BuildingType): WorldBuilding? {
        val shop = (activeBuilding ?: buildingNear()) ?: return null
        return shop.takeIf { it.type == type }
    }

    private fun upgradeInternal(slot: ComponentSlot, free: Boolean): Boolean {
        if (!workshopReady()) return false
        upgradeWorkshopBlockReason(slot)?.let {
            message = it
            return false
        }
        val current = car.parts[slot] ?: return false
        val target = scrapUpgradeTarget(slot) ?: run {
            message = copy.noDirectUpgrade(copy.slotName(slot).lowercase())
            return false
        }
        val cost = scrapUpgradeCost(slot) ?: return false
        if (!free && scrap < cost) {
            message = copy.needScrap(cost, scrap)
            return false
        }
        if (!free) scrap -= cost
        car.parts[slot] = MountedPart(
            defId = target.id,
            condition = current.condition,
            health = (current.health + 0.08f).coerceAtMost(1f),
            paintIndex = current.paintIndex
        )
        message = if (free) {
            copy.upgradedAd(copy.slotName(slot), copy.itemName(target))
        } else {
            copy.upgradedCost(copy.slotName(slot), copy.itemName(target), cost)
        }
        return true
    }

    /** Stojí pri upgrade dielni. */
    val atAutoShop: Boolean
        get() = (activeBuilding ?: buildingNear())?.type == BuildingType.AUTO_SHOP

    val atPaintShop: Boolean
        get() = (activeBuilding ?: buildingNear())?.type == BuildingType.PAINT_SHOP

    val paintShopAvailable: Boolean
        get() = atPaintShop && distanceM >= GameConfig.PAINT_SHOP_UNLOCK_M

    val paintShopCost: Int
        get() = GameConfig.PAINT_SHOP_COST

    fun paintBody(paint: VehiclePaint): Boolean = paintBodyInternal(paint, free = false)

    /** Odmena z rewarded reklamy: jedna zmena laku bez scrapu. */
    fun paintBodyFromRewardedAd(paint: VehiclePaint): Boolean {
        val shop = serviceShop(BuildingType.PAINT_SHOP)
        if (shop == null || shop.id in shopAdUsedBuildingIds) {
            message = if (!atPaintShop) {
                copy.takeCarToPaint
            } else {
                copy.shopAlreadyUsedAd
            }
            return false
        }
        val ok = paintBodyInternal(paint, free = true)
        if (ok) shopAdUsedBuildingIds += shop.id
        return ok
    }

    private fun paintBodyInternal(paint: VehiclePaint, free: Boolean): Boolean {
        if (!paintShopAvailable) {
            message = if (atPaintShop) {
                copy.paintAfter10km
            } else {
                copy.takeCarToPaint
            }
            return false
        }
        if (!workshopReady()) return false
        if (car.bodyPaintIndex == paint.ordinal) {
            message = copy.alreadyHasPaint(copy.paintName(paint).lowercase())
            return false
        }
        val cost = paintShopCost
        if (!free && scrap < cost) {
            message = copy.needScrapRepaint(cost, scrap)
            return false
        }
        if (!free) scrap -= cost
        car.applyBodyPaint(paint)
        message = if (free) {
            copy.repaintedAd(copy.paintName(paint).lowercase())
        } else {
            copy.repaintedCost(copy.paintName(paint).lowercase(), cost)
        }
        return true
    }

    fun tryStartEngine(): Boolean {
        if (car.engineRunning) {
            message = copy.engineAlreadyRunning
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
            message = copy.engineRunningHold
            emitSfx(GameSfx.ENGINE_START)
            true
        } else {
            val missing = car.missingEssentials()
            // Zablokovaný štart nesmie byť slepá ulička – čo chýba, nájde sa nablízku.
            val rescued = offerRescue(missing)
            emitSfx(GameSfx.ENGINE_FAIL_START)
            message = when {
                rescued != null -> rescued
                missing.isNotEmpty() -> missingText(missing)
                car.fuel < 0.5f -> copy.fuelIsLow
                car.oil < 0.3f -> copy.oilIsLow
                car.coolant < 0.3f -> copy.coolantIsLow
                car.wrongFuelFraction >= 0.70f ->
                    copy.wrongFuelDrain(copy.fuelKindName(car.requiredFuelKind).lowercase())
                car.batteryCharge < 0.2f -> copy.batteryFlatFit
                else -> copy.engineWouldNotStart
            }
            if (rescued == null && car.fuel < 0.5f && !canRecoverFrom(EndCause.OUT_OF_FUEL)) {
                endRun(EndCause.OUT_OF_FUEL.toEndReason())
            } else if (rescued == null && car.coolant < 0.3f && !hasStoredFluid(FluidType.COOLANT)) {
                endRun(EndCause.OVERHEAT.toEndReason())
            } else if (rescued == null &&
                car.batteryCharge < 0.2f &&
                !canRecoverFrom(EndCause.BATTERY_DEAD)
            ) {
                endRun(EndCause.BATTERY_DEAD.toEndReason())
            }
            false
        }
    }

    fun stopEngine() {
        car.stopEngine()
        emitSfx(GameSfx.ENGINE_STOP)
        message = copy.engineSwitchedOff
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
                !isNight -> copy.stillLightOut
                phase == GamePhase.DRIVING || kotlin.math.abs(car.speed) >= 0.2f -> copy.stopTheCarFirst
                activeBuilding?.type == BuildingType.WRECK ||
                    (activeBuilding == null && buildingNear()?.type == BuildingType.WRECK) ->
                    copy.sleepNotWreck
                activeBuilding == null && buildingNear() == null -> copy.sleepBesideBuilding
                else -> copy.cannotSleepHere
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
        message = if (car.coolant < 0.3f) {
            copy.dawnBatteryEmptyRad
        } else {
            copy.dawnBattery
        }
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
            // Vybitá / chýbajúca batéria ide do game over a núdzovej reklamy,
            // nie do tichého spawnu v batohu alebo v už vykradnutej budove.
            slot != ComponentSlot.BATTERY &&
                inventory.slots.none { it?.def?.canMountTo(slot) == true }
        }.toMutableList()
        val needFluid = when {
            car.fuel < 0.5f -> FluidType.FUEL
            car.oil < 0.3f -> FluidType.OIL
            car.coolant < 0.3f -> FluidType.COOLANT
            else -> null
        }?.takeIf { fluid ->
            inventory.slots.none {
                it?.def?.fluid == fluid &&
                    (fluid != FluidType.FUEL || it.def.fuelKind == car.requiredFuelKind)
            } && boot.slots.none {
                it?.def?.fluid == fluid &&
                    (fluid != FluidType.FUEL || it.def.fuelKind == car.requiredFuelKind)
            } && fluidRescues < MAX_FLUID_RESCUES
        }
        if (needParts.isEmpty() && needFluid == null) return null

        // Prednostne do batoha, nie novou budovou. Vrak, ktorý sa zjaví štyri
        // metre od stojaceho auta, pôsobí ako chyba, aj keď je to zámerná
        // záchranná sieť. Do prehrabania sa vo vlastnej batožine hráč uverí.
        val stash = (buildingNear()
            ?: rescueStashId?.let { id -> segment.buildings.firstOrNull { it.id == id } }
                ?.takeIf { kotlin.math.abs(worldXOf(it) - car.x) <= GameConfig.BUILDING_INTERACT_RANGE })
            ?.takeUnless { it.type.isServiceShop }
        if (stash == null) {
            // Kvapalina sa do batoha sama nedoliali. Prázdna nádrž na ceste
            // má ísť do game over a núdzovej reklamy, nie do voľného kanistra.
            if (needParts.isEmpty()) return null
            return rescueIntoPack(needParts, needFluid = null)
        }

        val rng = SeededRandom(seed xor stash.id xor distanceM.toRawBits().toLong())
        val added = mutableListOf<String>()
        var rescueInsert = 0
        needParts.forEach { slot ->
            if (stash.loot.any { it.def.canMountTo(slot) }) return@forEach
            val def = when (slot) {
                ComponentSlot.ENGINE -> ItemCatalog.ENGINE_A
                ComponentSlot.FUEL_TANK -> ItemCatalog.FUEL_TANK
                ComponentSlot.TIRE_FRONT, ComponentSlot.TIRE_REAR -> ItemCatalog.TIRE_POOR
                ComponentSlot.STARTER -> ItemCatalog.STARTER
                else -> null
            } ?: return@forEach
            // Nutné veci idú navrch zoznamu, aby ich sedačka či ťažký motor
            // nezablokovali plným batohom skôr, než sa hráč dostane k záchrane.
            stash.loot.add(
                rescueInsert++,
                ItemStack(def.id, ComponentCondition.DAMAGED, rng.nextFloat(0.35f, 0.65f))
            )
            added += copy.itemName(def).lowercase()
        }
        // Kvapalinu do už vykradnutej / navštívenej budovy nedávame.
        // Inak sa po loote znova objaví kanister a vyzerá to ako respawn.
        val mayDropFluid = stash.id !in visitedBuildingIds && !stash.looted
        if (mayDropFluid && needFluid != null && stash.loot.none {
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
            added += copy.itemName(def).lowercase()
        }
        if (added.isEmpty()) return null
        // Hláška musí sedieť s tým, čo sa naozaj stalo. Loot ide prednostne do
        // budovy, ktorá pri ceste už stojí – tvrdiť pritom, že sa za autom
        // zjavil vrak, znie ako chyba aj vtedy, keď je všetko v poriadku.
        val where = when (stash.type) {
            BuildingType.HOUSE -> copy.inTheHouseHere
            BuildingType.GARAGE -> copy.inTheGarageHere
            BuildingType.GAS_STATION -> copy.atThePumpHere
            BuildingType.AUTO_SHOP -> copy.inTheWorkshopHere
            BuildingType.PAINT_SHOP -> copy.inThePaintShopHere
            BuildingType.WRECK -> copy.inTheWreckHere
        }
        return copy.stashFound(where, added.joinToString(", "))
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
                ComponentSlot.STARTER -> ItemCatalog.STARTER
                else -> null
            } ?: return@forEach
            val stack = ItemStack(def.id, ComponentCondition.DAMAGED, rng.nextFloat(0.35f, 0.65f))
            if (inventory.add(stack)) added += copy.itemName(def).lowercase()
        }
        if (added.isEmpty()) return null
        return copy.dugOutOfBoot(added.joinToString(", "))
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
            EndCause.OUT_OF_FUEL -> copy.ranDry
            EndCause.BATTERY_DEAD -> copy.batteryFlatFit
            EndCause.OVERHEAT -> if (radiatorIsBroken()) {
                copy.coolingShot
            } else {
                copy.engineCutHeat
            }
            EndCause.ENGINE_DESTROYED -> copy.engineFinished
        }
        return false
    }

    /** Má hráč ešte reálne čím zhasnuté auto oživiť? */
    private fun canRecoverFrom(cause: EndCause): Boolean {
        val near = buildingNear()
        fun storedHas(predicate: (ItemStack) -> Boolean) =
            inventory.slots.any { it != null && predicate(it) } ||
                boot.slots.any { it != null && predicate(it) }
        fun buildingHas(predicate: (ItemStack) -> Boolean) =
            near?.loot?.any(predicate) == true
        return when (cause) {
            EndCause.OUT_OF_FUEL ->
                storedHas { it.def.fluid == FluidType.FUEL && it.def.fuelKind == car.requiredFuelKind } ||
                    buildingHas { it.def.fluid == FluidType.FUEL && it.def.fuelKind == car.requiredFuelKind } ||
                    (near?.let {
                        if (car.requiredFuelKind == FuelKind.DIESEL) it.pumpDieselL else it.pumpFuelL
                    } ?: 0f) > 0.5f
            EndCause.BATTERY_DEAD ->
                storedHas { it.def.canMountTo(ComponentSlot.BATTERY) } ||
                    buildingHas { it.def.canMountTo(ComponentSlot.BATTERY) }
            EndCause.OVERHEAT -> {
                val engineOk = (car.parts[ComponentSlot.ENGINE]?.health ?: 0f) > 0.06f
                if (!engineOk) false
                else if (radiatorIsBroken()) {
                    storedHas { it.def.canMountTo(ComponentSlot.RADIATOR) } ||
                        buildingHas { it.def.canMountTo(ComponentSlot.RADIATOR) }
                } else if (coolingCanIdleRecover()) true
                else storedHas { it.def.fluid == FluidType.COOLANT } ||
                    buildingHas { it.def.fluid == FluidType.COOLANT }
            }
            EndCause.ENGINE_DESTROYED -> {
                val engineSpare = storedHas { it.def.canMountTo(ComponentSlot.ENGINE) } ||
                    buildingHas { it.def.canMountTo(ComponentSlot.ENGINE) }
                if (radiatorIsBroken()) {
                    val radSpare = storedHas { it.def.canMountTo(ComponentSlot.RADIATOR) } ||
                        buildingHas { it.def.canMountTo(ComponentSlot.RADIATOR) }
                    engineSpare && radSpare
                } else {
                    engineSpare
                }
            }
        }
    }

    private fun compatibleFuelCan(): ItemDef =
        if (car.requiredFuelKind == FuelKind.DIESEL) ItemCatalog.DIESEL_CAN else ItemCatalog.FUEL_CAN

    fun endRun(reason: EndReason = EndReason.MANUAL) {
        safeHavenDecisionPending = false
        phase = GamePhase.GAME_OVER
        endReason = reason
        endDetail = when (reason) {
            EndReason.ENGINE_DESTROYED, EndReason.OVERHEAT ->
                car.wearCause?.let { copy.causeFatal(copy.wearFatal(it)) } ?: ""
            EndReason.OUT_OF_FUEL -> copy.outOfFuelDot
            EndReason.BATTERY_DEAD -> copy.batteryDrained
            EndReason.SHREDDED_TIRE -> copy.shreddedEnd
            EndReason.STUCK_TIRE -> copy.stuckEnd
            EndReason.ARRIVED -> copy.arrivedEnd
            EndReason.MANUAL -> ""
        }
        car.stopEngine()
        car.speed = 0f
        message = reason.let { copy.endReasonMessage(it) }
    }

    fun finishJourney(): Boolean {
        if (!safeHavenDecisionPending) return false
        endRun(EndReason.ARRIVED)
        return true
    }

    fun continueEndless(): Boolean {
        if (!safeHavenDecisionPending) return false
        safeHavenDecisionPending = false
        endlessMode = true
        phase = GamePhase.STOPPED
        message = copy.endlessModeMsg
        return true
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
            bodyPaintIndex = car.bodyPaintIndex,
            snowCover = car.snowCover,
            bodyDirt = car.bodyDirt,
            bodyRust = car.bodyRust,
            lateralZ = car.planar.z,
            heading = car.planar.heading
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
        roofLightsOn = roofLightsOn,
        helpAdsUsed = helpAdsUsed,
        safeHavenDecisionPending = safeHavenDecisionPending,
        endlessMode = endlessMode,
        utilityTrailerAttached = utilityTrailerAttached,
        pursuitStormKind = pursuitStorm?.kind,
        pursuitStormGapM = pursuitStorm?.gapM ?: 0f,
        pursuitStormAgeS = pursuitStorm?.ageS ?: 0f,
        pursuitStormExposureS = pursuitStorm?.exposureS ?: 0f,
        selectedBranchId = segment.selectedChoiceId,
        routeDetourOriginX = entryForkX,
        routeDetourDirection = if(entryForkIndex==0) 1f else -1f,
        entryVisualBiomeFrom = entryVisualBiomeFrom,
        entryPavingFrom = entryPavingFrom,
        entryBackdropSeed = entryBackdropSeed,
        sideMissionKind = sideMission?.kind,
        sideMissionSourceBuildingId = sideMission?.sourceBuildingId ?: 0L,
        sideMissionStartWorldX = sideMission?.startWorldX ?: 0f,
        sideMissionTargetWorldX = sideMission?.targetWorldX ?: 0f,
        sideMissionStartItemsLooted = sideMission?.startItemsLooted ?: 0,
        sideMissionGoalCount = sideMission?.goalCount ?: 0,
        sideMissionRewardScrap = sideMission?.rewardScrap ?: 0,
        lastSideMissionCompletedAtM = lastSideMissionCompletedAtM,
        lastPursuitStormAtM = lastPursuitStormAtM,
        pursuitStormsStarted = pursuitStormsStarted,
        sideMissionTargetBuildingId = sideMission?.targetBuildingId ?: 0L,
        sideMissionTargetBuildingType = sideMission?.targetBuildingType,
        sideMissionBonusRewardScrap = sideMission?.bonusRewardScrap ?: 0,
        sideMissionTimeRemainingS = sideMission?.timeRemainingS ?: 0f,
        sideMissionPackageLoaded = sideMission?.packageLoaded ?: false,
        sideMissionCargoIntegrity = sideMission?.cargoIntegrity ?: 1f,
        completedSideMissionKind = completedSideMission?.kind,
        completedSideMissionRewardScrap = completedSideMission?.rewardScrap ?: 0,
        completedSideMissionBonusScrap = completedSideMission?.bonusScrap ?: 0,
        completedSideMissionCargoIntegrity = completedSideMission?.cargoIntegrity ?: 1f
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
        snap.selectedBranchId?.takeIf { selected ->
            segment.choices.any { it.id == selected }
        }?.let { selected ->
            segment.selectedChoiceId = selected
            pendingChoiceId = selected
        }
        segment.buildings.clear()
        snap.segment.buildings.forEach { b ->
            // Staršie rozbehnuté jazdy si mohli niesť zásobu zo starej,
            // príliš štedrej konfigurácie. Pri načítaní zachováme pomer palív,
            // no bežnú pumpu zrazíme na dnešný spoločný limit.
            val savedPumpTotal = b.pumpFuelL + b.pumpDieselL
            val pumpScale = if (
                b.type == BuildingType.GAS_STATION &&
                savedPumpTotal > GameConfig.PUMP_FUEL_MAX
            ) GameConfig.PUMP_FUEL_MAX / savedPumpTotal else 1f
            val loot = if (b.type.isServiceShop) {
                mutableListOf()
            } else {
                b.loot.map { it.toStack() }.toMutableList()
            }
            segment.buildings += WorldBuilding(
                id = b.id,
                type = b.type,
                localX = b.localX,
                loot = loot,
                pumpFuelL = b.pumpFuelL * pumpScale,
                pumpDieselL = b.pumpDieselL * pumpScale,
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
        car.snowCover = snap.car.snowCover
        car.bodyDirt = snap.car.bodyDirt
        car.bodyRust = snap.car.bodyRust
        car.oilPurity = snap.car.oilPurity
        car.coolantPurity = snap.car.coolantPurity
        car.temperature = snap.car.temperature
        car.batteryCharge = snap.car.batteryCharge
        car.engineRunning = snap.car.engineRunning
        car.x = snap.car.x
        car.y = snap.car.y
        car.speed = 0f
        car.pitch = snap.car.pitch
        car.planar.z = snap.car.lateralZ
        car.planar.heading = snap.car.heading
        car.planar.stop()

        // Batoh a kufor. Kapacitu treba nastaviť skôr, než sa napĺňa – s nosičom
        // má kufor viac slotov a inak by sa presahujúce veci stratili.
        inventory.clear()
        boot.clear()
        utilityTrailerAttached = snap.utilityTrailerAttached
        syncCargoCapacity()
        snap.inventory.forEachIndexed { i, st ->
            if (st != null && i < inventory.slots.size) inventory.slots[i] = st.toStack()
        }
        snap.boot.forEachIndexed { i, st ->
            if (st != null && i < boot.slots.size) boot.slots[i] = st.toStack()
        }
        ensureTrailerOpportunity(segment)

        // Bežiace udalosti pokračujú tam, kde skončili.
        events.clear()
        snap.events.forEach { events += ActiveEvent(it.kind, it.remaining) }
        pursuitStorm = snap.pursuitStormKind?.let { kind ->
            ActivePursuitStorm(
                kind = kind,
                gapM = snap.pursuitStormGapM.coerceIn(0f, PURSUIT_ESCAPE_GAP_M),
                ageS = snap.pursuitStormAgeS.coerceAtLeast(0f),
                exposureS = snap.pursuitStormExposureS.coerceAtLeast(0f)
            )
        }
        lastPursuitStormAtM = snap.lastPursuitStormAtM
        pursuitStormsStarted = snap.pursuitStormsStarted.coerceAtLeast(if (pursuitStorm != null) 1 else 0)
        if (pursuitStorm != null && lastPursuitStormAtM < 0f) lastPursuitStormAtM = snap.distanceM

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
        sideMission = snap.sideMissionKind?.let { kind ->
            ActiveSideMission(
                kind = kind,
                sourceBuildingId = snap.sideMissionSourceBuildingId,
                startWorldX = snap.sideMissionStartWorldX,
                targetWorldX = snap.sideMissionTargetWorldX,
                targetBuildingId = snap.sideMissionTargetBuildingId,
                targetBuildingType = snap.sideMissionTargetBuildingType,
                startItemsLooted = snap.sideMissionStartItemsLooted,
                goalCount = snap.sideMissionGoalCount,
                rewardScrap = snap.sideMissionRewardScrap,
                bonusRewardScrap = snap.sideMissionBonusRewardScrap,
                timeRemainingS = snap.sideMissionTimeRemainingS,
                packageLoaded = snap.sideMissionPackageLoaded,
                cargoIntegrity = snap.sideMissionCargoIntegrity
            )
        }
        completedSideMission = snap.completedSideMissionKind?.let { kind ->
            SideMissionCompletion(
                kind = kind,
                rewardScrap = snap.completedSideMissionRewardScrap,
                bonusScrap = snap.completedSideMissionBonusScrap,
                cargoIntegrity = snap.completedSideMissionCargoIntegrity
            )
        }
        lastSideMissionCompletedAtM = snap.lastSideMissionCompletedAtM
        fluidRescues = snap.fluidRescues
        batteryRescues = snap.batteryRescues
        helpAdsUsed = snap.helpAdsUsed.coerceIn(0, GameConfig.HELP_ADS_MAX)
        safeHavenDecisionPending = snap.safeHavenDecisionPending
        endlessMode = snap.endlessMode
        entryForkX=snap.routeDetourOriginX
        entryForkIndex=if(snap.routeDetourDirection>=0f) 0 else 1
        entryVisualBiomeFrom = snap.entryVisualBiomeFrom
        entryPavingFrom = snap.entryPavingFrom
        entryBackdropSeed = snap.entryBackdropSeed
        // Po obnove nikdy nejdeme rovno v jazde – hráč sa najprv rozhliadne.
        phase = when (snap.phase) {
            GamePhase.DRIVING, GamePhase.EXPLORING, GamePhase.JUNCTION -> GamePhase.STOPPED
            else -> snap.phase
        }
        currentFeature = segment.featureAtWorld(car.x)
        activeBuilding = null
        // Obnova môže skončiť tesne pred hranicou; zaplatíme prípravu teraz,
        // keď je jazda aj tak zastavená, nie v prvom rýchlom prejazde.
        previousVisualSegment = null
        previousForkContinuations.clear()
        previousForkSelectedChoiceId = null
        preparedContinuations.clear()
        prepareAllContinuations()
        message = copy.runRestored(String.format("%.2f", distanceKm))
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
        fun restore(
            snap: RunSnapshot,
            bestDistanceKm: Float,
            metaRelayNodes: Int = 0,
            garageUpgrades: GarageUpgrades = GarageUpgrades()
        ): GameEngine {
            val engine = GameEngine(
                snap.seed,
                bestDistanceKm,
                metaRelayNodes = metaRelayNodes,
                garageUpgrades = garageUpgrades
            )
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
        private const val JUNCTION_SWIPE_LEAD_M = GameConfig.JUNCTION_APPROACH

        private const val UTILITY_TRAILER_TARE_KG = 85f
        private const val UTILITY_TRAILER_SLOTS = 8
        private const val UTILITY_TRAILER_CAPACITY_KG = 180f
        private const val TRAILER_GUARANTEE_FROM_M = 4_500f
        private const val TRAILER_WRECK_SALT = 0x7A11E2L

        private const val SIDE_MISSION_UNLOCK_M = 1_200f
        private const val SIDE_MISSION_COOLDOWN_M = 2_500f
        private const val SIDE_MISSION_MIN_DELIVERY_M = 650f
        private const val FRAGILE_CARGO_DAMAGE_COOLDOWN_S = 0.55f

        private const val PURSUIT_UNLOCK_DISTANCE_M = 3_000f
        private const val PURSUIT_MIN_GAP_BETWEEN_M = 6_000f
        private const val PURSUIT_SEGMENT_CHANCE = 0.34f
        private const val PURSUIT_FIRST_GUARANTEE_M = 8_000f
        private const val PURSUIT_HARD_GAP_M = 10_000f
        private const val PURSUIT_START_GAP_M = 320f
        private const val PURSUIT_VISIBLE_GAP_M = 440f
        private const val PURSUIT_ESCAPE_GAP_M = 850f
        private const val PURSUIT_MIN_ESCAPE_TIME_S = 18f
        private const val PURSUIT_ESCAPE_SCRAP_REWARD = 4
        private const val PURSUIT_SAND_SUSPENSION_WEAR = 0.00020f
        private const val PURSUIT_THUNDER_ELECTRIC_WEAR = 0.00014f
        private const val PURSUIT_BLIZZARD_BATTERY_DRAIN = 0.0009f

        /** Koľkokrát za jazdu môže vrak pri ceste doplniť chýbajúcu kvapalinu. */
        private const val MAX_FLUID_RESCUES = 2
        /** Id núdzovej dielne z reklamy – nesmie sa zameniť so štartovnou kôlňou. */
        private const val ROADSIDE_SHOP_SALT = 0x51ED01EL
        /** Seed reklamného upgrade v dielni – iná budova, iná ponuka. */
        private const val WORKSHOP_AD_SALT = 0xA0FF5A01L

        /** Predok, ktorý vie kameň pri protivetru škrabnúť. */
        private val HEADWIND_FRONT_SLOTS = listOf(
            ComponentSlot.FRONT_BUMPER,
            ComponentSlot.HOOD,
            ComponentSlot.HEADLIGHT,
            ComponentSlot.TIRE_FRONT
        )

        /** Diely, ktoré sa nedajú vybrať za chodu motora. */
        private val RUNNING_CRITICAL = setOf(
            ComponentSlot.ENGINE,
            ComponentSlot.FUEL_TANK,
            ComponentSlot.RADIATOR,
            ComponentSlot.BATTERY,
            ComponentSlot.ALTERNATOR,
            ComponentSlot.STARTER
        )

        /**
         * Jazdné diely ostávajú na aute. Zložiť sa dajú dvere, svetlá, nárazníky
         * a úložisko; motor, pruženie, gumy a podobne len výmenou.
         */
        private val DRIVE_FITTED = setOf(
            ComponentSlot.ENGINE,
            ComponentSlot.ALTERNATOR,
            ComponentSlot.STARTER,
            ComponentSlot.RADIATOR,
            ComponentSlot.BATTERY,
            ComponentSlot.FUEL_TANK,
            ComponentSlot.BRAKES,
            ComponentSlot.TIRE_FRONT,
            ComponentSlot.TIRE_REAR,
            ComponentSlot.DRIVETRAIN,
            ComponentSlot.SUSPENSION
        )
    }

    private fun EndCause.toEndReason(): EndReason = when (this) {
        EndCause.OUT_OF_FUEL -> EndReason.OUT_OF_FUEL
        EndCause.ENGINE_DESTROYED -> EndReason.ENGINE_DESTROYED
        EndCause.OVERHEAT -> EndReason.OVERHEAT
        EndCause.BATTERY_DEAD -> EndReason.BATTERY_DEAD
    }
}
