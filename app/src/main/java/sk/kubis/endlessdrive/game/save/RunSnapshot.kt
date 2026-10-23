package sk.kubis.endlessdrive.game.save

import sk.kubis.endlessdrive.domain.model.BranchStyle
import sk.kubis.endlessdrive.domain.model.BiomeType
import sk.kubis.endlessdrive.domain.model.BuildingType
import sk.kubis.endlessdrive.domain.model.ComponentCondition
import sk.kubis.endlessdrive.domain.model.ComponentSlot
import sk.kubis.endlessdrive.domain.model.GamePhase
import sk.kubis.endlessdrive.domain.model.FuelKind
import sk.kubis.endlessdrive.domain.model.ItemCatalog
import sk.kubis.endlessdrive.domain.model.RoadFeature
import sk.kubis.endlessdrive.domain.model.RoadPaving
import sk.kubis.endlessdrive.game.event.RoadEvent
import sk.kubis.endlessdrive.game.event.PursuitStormKind
import sk.kubis.endlessdrive.game.car.TireInjury
import sk.kubis.endlessdrive.game.SideMissionKind

/**
 * Odfotená rozohraná jazda. Ukladáme len to, čo sa nedá dopočítať:
 * priebeh, stav auta, batoh a to, čo hráč v budovách nechal.
 *
 * Prechodná fyzika (pruženie, preklz, náklonová rýchlosť) sa neukladá –
 * po obnove sa usadí do snímky. Ak pribudne nový **trvalý** stav auta,
 * treba ho doplniť sem aj do [CarState].
 */
data class RunSnapshot(
    val seed: Long,
    val phase: GamePhase,
    val distanceM: Float,
    val maxReachedX: Float,
    val elapsed: Float,
    val timeOfDay: Float,
    val headlightsOn: Boolean,
    val fuelBurnedL: Float,
    val itemsLooted: Int,
    val buildingsVisited: Int,
    val fluidRescues: Int,
    val batteryRescues: Int,
    val car: CarState,
    val inventory: List<StackState?>,
    /** Obsah kufra – ukladá sa zvlášť od batoha na chrbte. */
    val boot: List<StackState?>,
    val segment: SegmentState,
    /** Bežiace udalosti: názov + zvyšok času. */
    val events: List<EventState> = emptyList(),
    /** Materiál získaný zošrotovaním predmetov v tejto jazde. */
    val scrap: Int = 0,
    /** Režim svetiel; false pri starom save znamená stretávacie. */
    val highBeamsOn: Boolean = false,
    val roofLightsOn: Boolean = false,
    /** Koľko núdzových reklám (záchrana / ROAD ASSIST) už táto jazda minula. */
    val helpAdsUsed: Int = 0,
    val safeHavenDecisionPending: Boolean = false,
    val endlessMode: Boolean = false,
    val utilityTrailerAttached: Boolean = false,
    val pursuitStormKind: PursuitStormKind? = null,
    val pursuitStormGapM: Float = 0f,
    val pursuitStormAgeS: Float = 0f,
    val pursuitStormExposureS: Float = 0f,
    /** Explicitný swipe výber počas dlhého prechodu ku križovatke. */
    val selectedBranchId: Int? = null,
    val routeDetourOriginX: Float = Float.NaN,
    val routeDetourDirection: Float = 0f,
    val entryVisualBiomeFrom: BiomeType? = null,
    val entryPavingFrom: RoadPaving? = null,
    val entryBackdropSeed: Long = 0L,
    val sideMissionKind: SideMissionKind? = null,
    val sideMissionSourceBuildingId: Long = 0L,
    val sideMissionStartWorldX: Float = 0f,
    val sideMissionTargetWorldX: Float = 0f,
    val sideMissionStartItemsLooted: Int = 0,
    val sideMissionGoalCount: Int = 0,
    val sideMissionRewardScrap: Int = 0,
    val lastSideMissionCompletedAtM: Float = -2_500f,
    val lastPursuitStormAtM: Float = -1f,
    val pursuitStormsStarted: Int = 0,
    val sideMissionTargetBuildingId: Long = 0L,
    val sideMissionTargetBuildingType: BuildingType? = null,
    val sideMissionBonusRewardScrap: Int = 0,
    val sideMissionTimeRemainingS: Float = 0f,
    val sideMissionPackageLoaded: Boolean = false,
    val sideMissionCargoIntegrity: Float = 1f,
    val completedSideMissionKind: SideMissionKind? = null,
    val completedSideMissionRewardScrap: Int = 0,
    val completedSideMissionBonusScrap: Int = 0,
    val completedSideMissionCargoIntegrity: Float = 1f
)

data class EventState(val kind: RoadEvent, val remaining: Float)

data class CarState(
    val parts: List<PartState>,
    val fuel: Float,
    val oil: Float,
    val coolant: Float,
    val fuelPurity: Float,
    val oilPurity: Float,
    val coolantPurity: Float,
    val temperature: Float,
    val batteryCharge: Float,
    val engineRunning: Boolean,
    val x: Float,
    val y: Float,
    val speed: Float,
    val pitch: Float,
    /** Zloženie paliva: 0 = benzín, 1 = diesel. */
    val fuelDieselFraction: Float = 0f,
    val bodyPaintIndex: Int = 0,
    val snowCover: Float = 0f,
    val bodyDirt: Float = 0f,
    val bodyRust: Float = 0f,
    val lateralZ: Float = 0f,
    val heading: Float = 0f
)

data class PartState(
    val slot: ComponentSlot,
    val defId: String,
    val condition: ComponentCondition,
    val health: Float,
    val paintIndex: Int = -1,
    val injury: TireInjury = TireInjury.INFLATED
)

data class StackState(
    val defId: String,
    val condition: ComponentCondition,
    val health: Float,
    val count: Int,
    val purity: Float,
    /** Kvapalina, ktorá ostala vo vymontovanom diele. */
    val heldFluidL: Float = 0f,
    val heldPurity: Float = 1f,
    val heldDieselFraction: Float = 0f,
    val paintIndex: Int = -1,
    /** SoC vymontovanej batérie; -1 = loot/nový kus (nabitý na zdravie). */
    val heldCharge: Float = -1f
)

data class SegmentState(
    val planSeed: Long,
    val style: BranchStyle,
    val length: Float,
    val features: List<RoadFeature>,
    val buildingCount: Int,
    val worldOrigin: Float,
    val tripDistance: Float,
    val buildings: List<BuildingState>,
    /** Povrch regiónu; dôležitý aj pre plynulý nástup snehu. */
    val paving: RoadPaving = RoadPaving.ASPHALT
)

data class BuildingState(
    val id: Long,
    val type: BuildingType,
    val localX: Float,
    val pumpFuelL: Float,
    val pumpPurity: Float,
    val landmark: Boolean,
    val loot: List<StackState>,
    val pumpFuelKind: FuelKind = FuelKind.PETROL,
    val pumpDieselL: Float = 0f,
    val relayIndex: Int = -1,
    val relayRestored: Boolean = false
)

/**
 * Textový kodek – žiadna knižnica, žiadna reflexia, dá sa testovať na JVM.
 * Formát je riadkový, polia oddelené `|`, položky v zozname `;`, ich časti `:`.
 */
object RunCodec {
    // 4: kufor auta sa ukladá zvlášť od batoha. Staršie záznamy sa zahodia
    // a hra začne novú jazdu – rozdeliť jeden inventár na dva spätne nemá zmysel.
    private const val VERSION = 9

    /**
     * Najstaršia verzia, ktorú ešte vieme prečítať.
     *
     * Doteraz sa vyžadovala presná zhoda, takže každé zvýšenie VERSION ticho
     * zmazalo rozbehnutú jazdu – aj vtedy, keď sa iba pridalo pole na koniec
     * riadka. Nové polia sa čítajú cez [getOrNull] s náhradnou hodnotou, takže
     * staršie záznamy sú čitateľné; zdvihnúť túto hranicu treba len vtedy, keď
     * sa formát zmení tak, že sa dopočítať nedá (ako pri rozdelení inventára).
     */
    private const val MIN_COMPAT = 4

    fun encode(s: RunSnapshot): String = buildString {
        appendLine("v$VERSION")
        appendLine(
            listOf(
                s.seed, s.phase.name, s.distanceM, s.maxReachedX, s.elapsed,
                s.timeOfDay, s.headlightsOn, s.fuelBurnedL, s.itemsLooted,
                s.buildingsVisited, s.fluidRescues, s.batteryRescues, s.scrap, s.highBeamsOn, s.roofLightsOn,
                s.helpAdsUsed, s.safeHavenDecisionPending, s.endlessMode,
                s.utilityTrailerAttached,
                s.pursuitStormKind?.name ?: "-", s.pursuitStormGapM,
                s.pursuitStormAgeS, s.pursuitStormExposureS,
                s.selectedBranchId ?: -1,
                s.routeDetourOriginX, s.routeDetourDirection,
                s.entryVisualBiomeFrom?.name ?: "-",
                s.entryPavingFrom?.name ?: "-",
                s.entryBackdropSeed,
                s.sideMissionKind?.name ?: "-",
                s.sideMissionSourceBuildingId,
                s.sideMissionStartWorldX,
                s.sideMissionTargetWorldX,
                s.sideMissionStartItemsLooted,
                s.sideMissionGoalCount,
                s.sideMissionRewardScrap,
                s.lastSideMissionCompletedAtM,
                s.lastPursuitStormAtM,
                s.pursuitStormsStarted,
                s.sideMissionTargetBuildingId,
                s.sideMissionTargetBuildingType?.name ?: "-",
                s.sideMissionBonusRewardScrap,
                s.sideMissionTimeRemainingS,
                s.sideMissionPackageLoaded,
                s.sideMissionCargoIntegrity,
                s.completedSideMissionKind?.name ?: "-",
                s.completedSideMissionRewardScrap,
                s.completedSideMissionBonusScrap,
                s.completedSideMissionCargoIntegrity
            ).joinToString("|")
        )
        val c = s.car
        appendLine(
            listOf(
                c.fuel, c.oil, c.coolant, c.fuelPurity, c.oilPurity, c.coolantPurity,
                c.temperature, c.batteryCharge, c.engineRunning, c.x, c.y, c.speed, c.pitch,
                c.fuelDieselFraction, c.bodyPaintIndex, c.snowCover, c.bodyDirt, c.bodyRust,
                c.lateralZ, c.heading
            ).joinToString("|")
        )
        appendLine(c.parts.joinToString(";") {
            "${it.slot.name}:${it.defId}:${it.condition.name}:${it.health}:${it.paintIndex}:${it.injury.name}"
        })
        appendLine(s.inventory.joinToString(";") { it?.let(::encodeStack) ?: "-" })
        appendLine(s.boot.joinToString(";") { it?.let(::encodeStack) ?: "-" })
        val seg = s.segment
        appendLine(
            listOf(
                seg.planSeed, seg.style.name, seg.length, seg.buildingCount,
                seg.worldOrigin, seg.tripDistance,
                seg.features.joinToString(",") { it.name }, seg.paving.name
            ).joinToString("|")
        )
        appendLine(s.events.joinToString(";") { "${it.kind.name}:${it.remaining}" })
        seg.buildings.forEach { b ->
            appendLine(
                listOf(
                    b.id, b.type.name, b.localX, b.pumpFuelL, b.pumpPurity, b.landmark,
                    b.loot.joinToString(";", transform = ::encodeStack), b.pumpFuelKind.name,
                    b.pumpDieselL, b.relayIndex, b.relayRestored
                ).joinToString("|")
            )
        }
    }

    /** Vráti null, keď je záznam z inej verzie alebo poškodený – hra si vytvorí novú jazdu. */
    fun decode(text: String): RunSnapshot? = runCatching {
        val lines = text.trim().lines()
        require(lines.size >= 8) { "krátky záznam" }
        val version = lines[0].removePrefix("v").toIntOrNull()
        require(version != null && version in MIN_COMPAT..VERSION) { "iná verzia" }

        val h = lines[1].split("|")
        val c = lines[2].split("|")
        val parts = mutableListOf<PartState>()
        lines[3].split(";").filter { it.isNotBlank() }.forEach { p ->
            val f = p.split(":")
            val defId = when (f[1]) {
                "tire_bald" -> "tire_poor"
                "tire_good" -> "tire_std"
                else -> f[1]
            }
            val condition = ComponentCondition.valueOf(f[2])
            val health = f[3].toFloat()
            val paintIndex = f.getOrNull(4)?.toIntOrNull() ?: -1
            val injury = f.getOrNull(5)?.let { runCatching { TireInjury.valueOf(it) }.getOrNull() }
                ?: TireInjury.INFLATED
            when (f[0]) {
                "TIRES" -> {
                    // Starý save: jedna sada → predok aj zadok.
                    parts += PartState(ComponentSlot.TIRE_FRONT, defId, condition, health, paintIndex, injury)
                    parts += PartState(ComponentSlot.TIRE_REAR, defId, condition, health, paintIndex, injury)
                }
                // Starý pár dverí sa po migrácii rozdelí na oba nové sloty.
                "DOORS" -> {
                    parts += PartState(ComponentSlot.DOOR_FRONT, "door_front", condition, health, paintIndex)
                    parts += PartState(ComponentSlot.DOOR_REAR, "door_rear", condition, health, paintIndex)
                }
                // Samostatné okná už nie sú predmet; sklo je súčasťou dverí.
                "WINDOWS" -> Unit
                else -> parts += PartState(
                    slot = ComponentSlot.valueOf(f[0]),
                    defId = defId,
                    condition = condition,
                    health = health,
                    paintIndex = paintIndex,
                    injury = injury
                )
            }
        }
        val inventory = lines[4].split(";").map { if (it == "-" || it.isBlank()) null else decodeStack(it) }
        val boot = lines[5].split(";").map { if (it == "-" || it.isBlank()) null else decodeStack(it) }
        val seg = lines[6].split("|")
        val events = lines[7].split(";").filter { it.isNotBlank() }.mapNotNull { e ->
            val f = e.split(":")
            val kind = runCatching { RoadEvent.valueOf(f[0]) }.getOrNull() ?: return@mapNotNull null
            EventState(kind, f[1].toFloat())
        }
        val buildings = lines.drop(8).filter { it.isNotBlank() }.map { line ->
            val f = line.split("|")
            val legacyKind = f.getOrNull(7)?.let { FuelKind.valueOf(it) } ?: FuelKind.PETROL
            val savedDiesel = f.getOrNull(8)?.toFloatOrNull()
            BuildingState(
                id = f[0].toLong(),
                type = BuildingType.valueOf(f[1]),
                localX = f[2].toFloat(),
                // Starý save mal iba jeden stojan. Pri migrácii jeho zásobu
                // ponecháme v správnom palive; nový formát ukladá obe zvlášť.
                pumpFuelL = if (savedDiesel == null && legacyKind == FuelKind.DIESEL) 0f
                    else f[3].toFloat(),
                pumpPurity = f[4].toFloat(),
                landmark = f[5].toBoolean(),
                loot = f.getOrNull(6).orEmpty().split(";").filter { it.isNotBlank() }.map(::decodeStack),
                pumpFuelKind = legacyKind,
                pumpDieselL = savedDiesel
                    ?: if (legacyKind == FuelKind.DIESEL) f[3].toFloat() else 0f,
                relayIndex = f.getOrNull(9)?.toIntOrNull() ?: -1,
                relayRestored = f.getOrNull(10)?.toBoolean() ?: false
            )
        }

        RunSnapshot(
            seed = h[0].toLong(),
            phase = GamePhase.valueOf(h[1]),
            distanceM = h[2].toFloat(),
            maxReachedX = h[3].toFloat(),
            elapsed = h[4].toFloat(),
            timeOfDay = h[5].toFloat(),
            headlightsOn = h[6].toBoolean(),
            fuelBurnedL = h[7].toFloat(),
            itemsLooted = h[8].toInt(),
            buildingsVisited = h[9].toInt(),
            fluidRescues = h[10].toInt(),
            batteryRescues = h[11].toInt(),
            car = CarState(
                parts = parts,
                fuel = c[0].toFloat(), oil = c[1].toFloat(), coolant = c[2].toFloat(),
                fuelPurity = c[3].toFloat(), oilPurity = c[4].toFloat(), coolantPurity = c[5].toFloat(),
                temperature = c[6].toFloat(), batteryCharge = c[7].toFloat(),
                engineRunning = c[8].toBoolean(),
                x = c[9].toFloat(), y = c[10].toFloat(), speed = c[11].toFloat(), pitch = c[12].toFloat(),
                fuelDieselFraction = c.getOrNull(13)?.toFloatOrNull() ?: run {
                    // V4 záznamy pred rozdelením palív mali univerzálne palivo.
                    // Po obnove ho preto priradíme k vtedy namontovanému motoru.
                    val engineId = parts.firstOrNull { it.slot == ComponentSlot.ENGINE }?.defId
                    if (ItemCatalog.byId(engineId.orEmpty())?.fuelKind == FuelKind.DIESEL) 1f else 0f
                },
                bodyPaintIndex = c.getOrNull(14)?.toIntOrNull() ?: 0,
                snowCover = c.getOrNull(15)?.toFloatOrNull()?.coerceIn(0f, 1f) ?: 0f,
                bodyDirt = c.getOrNull(16)?.toFloatOrNull()?.coerceIn(0f, 1f) ?: 0f,
                bodyRust = c.getOrNull(17)?.toFloatOrNull()?.coerceIn(0f, 1f) ?: 0f,
                lateralZ = c.getOrNull(18)?.toFloatOrNull()?.takeIf(Float::isFinite)
                    ?: sk.kubis.endlessdrive.game.world.World3D.roadCenter(c[9].toFloat()),
                heading = c.getOrNull(19)?.toFloatOrNull()?.takeIf(Float::isFinite) ?: kotlin.math.atan(
                    sk.kubis.endlessdrive.game.world.World3D.roadCenter(c[9].toFloat()+0.5f) -
                    sk.kubis.endlessdrive.game.world.World3D.roadCenter(c[9].toFloat()-0.5f)
                )
            ),
            inventory = inventory,
            boot = boot,
            segment = SegmentState(
                planSeed = seg[0].toLong(),
                style = BranchStyle.valueOf(seg[1]),
                length = seg[2].toFloat(),
                buildingCount = seg[3].toInt(),
                worldOrigin = seg[4].toFloat(),
                tripDistance = seg[5].toFloat(),
                features = seg[6].split(",").filter { it.isNotBlank() }.map { RoadFeature.valueOf(it) },
                buildings = buildings,
                paving = seg.getOrNull(7)?.let { RoadPaving.valueOf(it) } ?: RoadPaving.ASPHALT
            ),
            events = events,
            scrap = h.getOrNull(12)?.toIntOrNull() ?: 0,
            highBeamsOn = h.getOrNull(13)?.toBooleanStrictOrNull() ?: false,
            roofLightsOn = h.getOrNull(14)?.toBooleanStrictOrNull() ?: false,
            helpAdsUsed = h.getOrNull(15)?.toIntOrNull() ?: 0,
            safeHavenDecisionPending = h.getOrNull(16)?.toBooleanStrictOrNull() ?: false,
            endlessMode = h.getOrNull(17)?.toBooleanStrictOrNull() ?: false,
            utilityTrailerAttached = h.getOrNull(18)?.toBooleanStrictOrNull() ?: false,
            pursuitStormKind = h.getOrNull(19)?.takeUnless { it == "-" }
                ?.let { runCatching { PursuitStormKind.valueOf(it) }.getOrNull() },
            pursuitStormGapM = h.getOrNull(20)?.toFloatOrNull() ?: 0f,
            pursuitStormAgeS = h.getOrNull(21)?.toFloatOrNull() ?: 0f,
            pursuitStormExposureS = h.getOrNull(22)?.toFloatOrNull() ?: 0f,
            selectedBranchId = h.getOrNull(23)?.toIntOrNull()?.takeIf { it >= 0 },
            routeDetourOriginX = h.getOrNull(24)?.toFloatOrNull() ?: Float.NaN,
            routeDetourDirection = h.getOrNull(25)?.toFloatOrNull()?.coerceIn(-1f, 1f) ?: 0f,
            entryVisualBiomeFrom = h.getOrNull(26)?.takeUnless { it == "-" }
                ?.let { runCatching { BiomeType.valueOf(it) }.getOrNull() },
            entryPavingFrom = h.getOrNull(27)?.takeUnless { it == "-" }
                ?.let { runCatching { RoadPaving.valueOf(it) }.getOrNull() },
            entryBackdropSeed = h.getOrNull(28)?.toLongOrNull() ?: 0L,
            sideMissionKind = h.getOrNull(29)?.takeUnless { it == "-" }
                ?.let { runCatching { SideMissionKind.valueOf(it) }.getOrNull() },
            sideMissionSourceBuildingId = h.getOrNull(30)?.toLongOrNull() ?: 0L,
            sideMissionStartWorldX = h.getOrNull(31)?.toFloatOrNull() ?: 0f,
            sideMissionTargetWorldX = h.getOrNull(32)?.toFloatOrNull() ?: 0f,
            sideMissionStartItemsLooted = h.getOrNull(33)?.toIntOrNull() ?: 0,
            sideMissionGoalCount = h.getOrNull(34)?.toIntOrNull() ?: 0,
            sideMissionRewardScrap = h.getOrNull(35)?.toIntOrNull() ?: 0,
            lastSideMissionCompletedAtM = h.getOrNull(36)?.toFloatOrNull() ?: -2_500f,
            lastPursuitStormAtM = h.getOrNull(37)?.toFloatOrNull() ?: -1f,
            pursuitStormsStarted = h.getOrNull(38)?.toIntOrNull() ?: 0,
            sideMissionTargetBuildingId = h.getOrNull(39)?.toLongOrNull() ?: 0L,
            sideMissionTargetBuildingType = h.getOrNull(40)?.takeUnless { it == "-" }
                ?.let { runCatching { BuildingType.valueOf(it) }.getOrNull() },
            sideMissionBonusRewardScrap = h.getOrNull(41)?.toIntOrNull() ?: 0,
            sideMissionTimeRemainingS = h.getOrNull(42)?.toFloatOrNull() ?: 0f,
            // Staršie savy vznikli v čase, keď sa zásielka „naložila“
            // automaticky a fyzický predmet ešte neexistoval.
            sideMissionPackageLoaded = h.getOrNull(43)?.toBooleanStrictOrNull()
                ?: (h.getOrNull(29)?.let {
                    it != "-" && it != SideMissionKind.SALVAGE.name
                } == true),
            sideMissionCargoIntegrity = h.getOrNull(44)?.toFloatOrNull()?.coerceIn(0f, 1f) ?: 1f,
            completedSideMissionKind = h.getOrNull(45)?.takeUnless { it == "-" }
                ?.let { runCatching { SideMissionKind.valueOf(it) }.getOrNull() },
            completedSideMissionRewardScrap = h.getOrNull(46)?.toIntOrNull() ?: 0,
            completedSideMissionBonusScrap = h.getOrNull(47)?.toIntOrNull() ?: 0,
            completedSideMissionCargoIntegrity = h.getOrNull(48)?.toFloatOrNull()
                ?.coerceIn(0f, 1f) ?: 1f
        )
    }.getOrNull()

    private fun encodeStack(s: StackState) =
        "${s.defId}:${s.condition.name}:${s.health}:${s.count}:${s.purity}" +
            ":${s.heldFluidL}:${s.heldPurity}:${s.heldDieselFraction}:${s.paintIndex}" +
            ":${s.heldCharge}"

    private fun decodeStack(text: String): StackState {
        val f = text.split(":")
        return StackState(
            defId = when (f[0]) {
                "doors" -> "door_front"
                "windows" -> "door_rear"
                else -> f[0]
            },
            condition = ComponentCondition.valueOf(f[1]),
            health = f[2].toFloat(),
            count = f[3].toInt(),
            purity = f[4].toFloat(),
            // Staršie záznamy tieto polia nemajú – diel je proste suchý.
            heldFluidL = f.getOrNull(5)?.toFloatOrNull() ?: 0f,
            heldPurity = f.getOrNull(6)?.toFloatOrNull() ?: 1f,
            heldDieselFraction = f.getOrNull(7)?.toFloatOrNull() ?: 0f,
            paintIndex = f.getOrNull(8)?.toIntOrNull() ?: -1,
            heldCharge = f.getOrNull(9)?.toFloatOrNull() ?: -1f
        )
    }
}
