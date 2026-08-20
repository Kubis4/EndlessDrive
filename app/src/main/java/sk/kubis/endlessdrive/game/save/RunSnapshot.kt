package sk.kubis.endlessdrive.game.save

import sk.kubis.endlessdrive.domain.model.BranchStyle
import sk.kubis.endlessdrive.domain.model.BuildingType
import sk.kubis.endlessdrive.domain.model.ComponentCondition
import sk.kubis.endlessdrive.domain.model.ComponentSlot
import sk.kubis.endlessdrive.domain.model.GamePhase
import sk.kubis.endlessdrive.domain.model.RoadFeature
import sk.kubis.endlessdrive.game.event.RoadEvent

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
    val events: List<EventState> = emptyList()
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
    val pitch: Float
)

data class PartState(
    val slot: ComponentSlot,
    val defId: String,
    val condition: ComponentCondition,
    val health: Float
)

data class StackState(
    val defId: String,
    val condition: ComponentCondition,
    val health: Float,
    val count: Int,
    val purity: Float,
    /** Kvapalina, ktorá ostala vo vymontovanom diele. */
    val heldFluidL: Float = 0f,
    val heldPurity: Float = 1f
)

data class SegmentState(
    val planSeed: Long,
    val style: BranchStyle,
    val length: Float,
    val features: List<RoadFeature>,
    val buildingCount: Int,
    val worldOrigin: Float,
    val tripDistance: Float,
    val buildings: List<BuildingState>
)

data class BuildingState(
    val id: Long,
    val type: BuildingType,
    val localX: Float,
    val pumpFuelL: Float,
    val pumpPurity: Float,
    val landmark: Boolean,
    val loot: List<StackState>
)

/**
 * Textový kodek – žiadna knižnica, žiadna reflexia, dá sa testovať na JVM.
 * Formát je riadkový, polia oddelené `|`, položky v zozname `;`, ich časti `:`.
 */
object RunCodec {
    // 4: kufor auta sa ukladá zvlášť od batoha. Staršie záznamy sa zahodia
    // a hra začne novú jazdu – rozdeliť jeden inventár na dva spätne nemá zmysel.
    private const val VERSION = 4

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
                s.buildingsVisited, s.fluidRescues, s.batteryRescues
            ).joinToString("|")
        )
        val c = s.car
        appendLine(
            listOf(
                c.fuel, c.oil, c.coolant, c.fuelPurity, c.oilPurity, c.coolantPurity,
                c.temperature, c.batteryCharge, c.engineRunning, c.x, c.y, c.speed, c.pitch
            ).joinToString("|")
        )
        appendLine(c.parts.joinToString(";") { "${it.slot.name}:${it.defId}:${it.condition.name}:${it.health}" })
        appendLine(s.inventory.joinToString(";") { it?.let(::encodeStack) ?: "-" })
        appendLine(s.boot.joinToString(";") { it?.let(::encodeStack) ?: "-" })
        val seg = s.segment
        appendLine(
            listOf(
                seg.planSeed, seg.style.name, seg.length, seg.buildingCount,
                seg.worldOrigin, seg.tripDistance,
                seg.features.joinToString(",") { it.name }
            ).joinToString("|")
        )
        appendLine(s.events.joinToString(";") { "${it.kind.name}:${it.remaining}" })
        seg.buildings.forEach { b ->
            appendLine(
                listOf(
                    b.id, b.type.name, b.localX, b.pumpFuelL, b.pumpPurity, b.landmark,
                    b.loot.joinToString(";", transform = ::encodeStack)
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
            when (f[0]) {
                "TIRES" -> {
                    // Starý save: jedna sada → predok aj zadok.
                    parts += PartState(ComponentSlot.TIRE_FRONT, defId, condition, health)
                    parts += PartState(ComponentSlot.TIRE_REAR, defId, condition, health)
                }
                else -> parts += PartState(
                    slot = ComponentSlot.valueOf(f[0]),
                    defId = defId,
                    condition = condition,
                    health = health
                )
            }
        }
        val inventory = lines[4].split(";").map { if (it == "-" || it.isBlank()) null else decodeStack(it) }
        val boot = lines[5].split(";").map { if (it == "-" || it.isBlank()) null else decodeStack(it) }
        val seg = lines[6].split("|")
        val events = lines[7].split(";").filter { it.isNotBlank() }.map { e ->
            val f = e.split(":")
            EventState(RoadEvent.valueOf(f[0]), f[1].toFloat())
        }
        val buildings = lines.drop(8).filter { it.isNotBlank() }.map { line ->
            val f = line.split("|")
            BuildingState(
                id = f[0].toLong(),
                type = BuildingType.valueOf(f[1]),
                localX = f[2].toFloat(),
                pumpFuelL = f[3].toFloat(),
                pumpPurity = f[4].toFloat(),
                landmark = f[5].toBoolean(),
                loot = f.getOrNull(6).orEmpty().split(";").filter { it.isNotBlank() }.map(::decodeStack)
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
                x = c[9].toFloat(), y = c[10].toFloat(), speed = c[11].toFloat(), pitch = c[12].toFloat()
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
                buildings = buildings
            ),
            events = events
        )
    }.getOrNull()

    private fun encodeStack(s: StackState) =
        "${s.defId}:${s.condition.name}:${s.health}:${s.count}:${s.purity}" +
            ":${s.heldFluidL}:${s.heldPurity}"

    private fun decodeStack(text: String): StackState {
        val f = text.split(":")
        return StackState(
            defId = f[0],
            condition = ComponentCondition.valueOf(f[1]),
            health = f[2].toFloat(),
            count = f[3].toInt(),
            purity = f[4].toFloat(),
            // Staršie záznamy tieto polia nemajú – diel je proste suchý.
            heldFluidL = f.getOrNull(5)?.toFloatOrNull() ?: 0f,
            heldPurity = f.getOrNull(6)?.toFloatOrNull() ?: 1f
        )
    }
}
