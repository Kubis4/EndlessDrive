package sk.kubis.endlessdrive.game.car

import sk.kubis.endlessdrive.domain.model.ItemCatalog
import sk.kubis.endlessdrive.domain.model.ItemDef
import sk.kubis.endlessdrive.domain.model.ItemStack
import sk.kubis.endlessdrive.domain.model.SedanSpec
import sk.kubis.endlessdrive.core.GameConfig
import sk.kubis.endlessdrive.core.MathX
import sk.kubis.endlessdrive.core.SeededRandom
import sk.kubis.endlessdrive.domain.model.ComponentCondition
import sk.kubis.endlessdrive.domain.model.ComponentSlot
import sk.kubis.endlessdrive.domain.model.FluidType

data class MountedPart(
    val defId: String,
    var condition: ComponentCondition,
    var health: Float
) {
    val def: ItemDef get() = ItemCatalog.byId(defId) ?: ItemCatalog.ENGINE_A
}

/**
 * Auto v bočnom pohľade: X = postup po ceste, Y = výška na profile.
 */
class Car {
    val parts = linkedMapOf<ComponentSlot, MountedPart>()

    var fuel = 8f
    var oil = 1.2f
    var coolant = 2.0f

    /** Čistota toho, čo je práve v nádržiach (1 = čisté, menej = riedené vodou). */
    var fuelPurity = 1f
    var oilPurity = 1f
    var coolantPurity = 1f

    var temperature = 40f
    var batteryCharge = 0.55f
    var engineRunning = false
    var prepChecklistDone = false

    /** Čo motor práve najviac ničí (null = nič). */
    var wearCause: WearCause? = null
        private set
    /** Rýchlosť opotrebenia motora za sekundu. */
    var wearRate: Float = 0f
        private set

    /** Svetová vzdialenosť (skóre). */
    var x = 0f
    /** Výška stredu auta. */
    var y = 0f
    var speed = 0f
    /** Náklon karosérie (rad). */
    var pitch = 0f
    /** Otáčanie kolies (stupne) – pre CarArtist. */
    var wheelSpinDeg = 0f

    fun hasPart(slot: ComponentSlot): Boolean = parts.containsKey(slot)

    val fuelCapacity: Float
        get() = parts[ComponentSlot.FUEL_TANK]?.def?.capacity?.takeIf { it > 0f } ?: 40f
    val oilCapacity: Float get() = 4f
    val coolantCapacity: Float get() = 6f

    /** Riedené palivo = slabší ťah. 1.0 pri čistom, ~0.55 pri „vode“. */
    val fuelQualityMul: Float get() = 0.55f + 0.45f * fuelPurity.coerceIn(0f, 1f)

    val powerHp: Float
        get() {
            val eng = parts[ComponentSlot.ENGINE] ?: return 40f
            return eng.def.powerHp * eng.health.coerceIn(0.2f, 1f) * fuelQualityMul
        }

    val fuelUseMul: Float
        get() = parts[ComponentSlot.ENGINE]?.def?.fuelUse ?: 1.2f

    val brakeMul: Float
        get() {
            val b = parts[ComponentSlot.BRAKES] ?: return 0.7f
            return b.def.reliability * b.health.coerceIn(0.3f, 1.2f)
        }

    val tireGrip: Float
        get() {
            val t = parts[ComponentSlot.TIRES] ?: return 0.7f
            return t.def.reliability * t.health.coerceIn(0.35f, 1.2f)
        }

    val radiatorMul: Float
        get() {
            val r = parts[ComponentSlot.RADIATOR] ?: return 0.8f
            return r.def.reliability * r.health.coerceIn(0.3f, 1.3f)
        }

    val overallHealth: Float
        get() {
            if (parts.isEmpty()) return 0f
            return parts.values.map { it.health }.average().toFloat()
        }

    val speedKmh: Float get() = speed * 3.6f
    val fuelRatio: Float get() = (fuel / fuelCapacity).coerceIn(0f, 1f)
    val oilRatio: Float get() = (oil / oilCapacity).coerceIn(0f, 1f)
    val coolantRatio: Float get() = (coolant / coolantCapacity).coerceIn(0f, 1f)

    /**
     * Vrak na štarte je zakaždým iný: stav dielov, čo v ňom vôbec ostalo
     * aj koľko a akej kvapaliny je v nádržiach. Chýbajúce veci si hráč
     * musí nájsť — o to práve ide.
     */
    fun installStarterKit(rng: SeededRandom = SeededRandom(0L)) {
        parts.clear()

        fun mount(slot: ComponentSlot, defId: String, lo: Float, hi: Float) {
            val health = rng.nextFloat(lo, hi)
            val condition = when {
                health >= 0.65f -> ComponentCondition.USED
                health >= 0.35f -> ComponentCondition.DAMAGED
                else -> ComponentCondition.CRITICAL
            }
            parts[slot] = MountedPart(defId, condition, health)
        }

        // Bez týchto by sa auto nedalo ani rozhýbať – ostávajú vždy, len dojazdené.
        mount(ComponentSlot.ENGINE, ItemCatalog.ENGINE_A.id, 0.30f, 0.62f)
        mount(ComponentSlot.FUEL_TANK, ItemCatalog.FUEL_TANK.id, 0.40f, 0.75f)
        mount(ComponentSlot.TIRES, ItemCatalog.TIRE.id, 0.32f, 0.68f)
        mount(ComponentSlot.BRAKES, ItemCatalog.BRAKES.id, 0.30f, 0.68f)
        mount(ComponentSlot.SUSPENSION, ItemCatalog.SUSPENSION.id, 0.25f, 0.60f)

        // Tieto z auta často niekto vybral – potom sa musia nájsť.
        if (rng.chance(0.75f)) mount(ComponentSlot.BATTERY, ItemCatalog.BATTERY.id, 0.30f, 0.72f)
        if (rng.chance(0.80f)) mount(ComponentSlot.RADIATOR, ItemCatalog.RADIATOR.id, 0.30f, 0.72f)
        if (rng.chance(0.70f)) mount(ComponentSlot.ALTERNATOR, ItemCatalog.ALTERNATOR.id, 0.30f, 0.75f)
        if (rng.chance(0.80f)) mount(ComponentSlot.STARTER, ItemCatalog.STARTER.id, 0.35f, 0.78f)
        // Karoséria (dvere/kapota/okná/nárazníky) chýba vždy.

        // Kvapaliny: občas úplne suchá nádrž.
        fuel = if (rng.chance(0.25f)) 0f else rng.nextFloat(3f, 16f)
        oil = if (rng.chance(0.20f)) 0f else rng.nextFloat(0.6f, 2.6f)
        coolant = if (rng.chance(0.20f)) 0f else rng.nextFloat(1.0f, 4.2f)
        fuelPurity = rng.nextFloat(0.55f, 0.95f)
        oilPurity = rng.nextFloat(0.50f, 0.95f)
        coolantPurity = rng.nextFloat(0.45f, 0.95f)

        temperature = 40f
        batteryCharge = if (hasPart(ComponentSlot.BATTERY)) rng.nextFloat(0.25f, 0.75f) else 0f
        engineRunning = false
        wheelSpinDeg = 0f
    }

    /** Diely, bez ktorých auto nenaštartuje ani nepôjde. */
    fun missingEssentials(): List<ComponentSlot> =
        ESSENTIAL_SLOTS.filter { !parts.containsKey(it) }

    fun canStart(): Boolean {
        if (missingEssentials().isNotEmpty()) return false
        if (fuel < 0.5f || oil < 0.3f || coolant < 0.3f || batteryCharge < 0.2f) return false
        val eng = parts[ComponentSlot.ENGINE] ?: return false
        return eng.health > 0.15f
    }

    fun tryStart(): Boolean {
        if (!canStart()) return false
        engineRunning = true
        batteryCharge = (batteryCharge - 0.05f).coerceAtLeast(0f)
        return true
    }

    fun stopEngine() {
        engineRunning = false
    }

    /**
     * Doleje kvapalinu a zmieša jej čistotu s tým, čo už v nádrži je.
     * Vracia skutočne doliaty objem.
     */
    fun refill(fluid: FluidType, amount: Float, purity: Float = 1f): Float = when (fluid) {
        FluidType.FUEL -> {
            val add = amount.coerceAtMost(fuelCapacity - fuel)
            fuelPurity = blend(fuelPurity, fuel, purity, add)
            fuel += add
            add
        }
        FluidType.OIL -> {
            val add = amount.coerceAtMost(oilCapacity - oil)
            oilPurity = blend(oilPurity, oil, purity, add)
            oil += add
            add
        }
        FluidType.COOLANT -> {
            val add = amount.coerceAtMost(coolantCapacity - coolant)
            coolantPurity = blend(coolantPurity, coolant, purity, add)
            coolant += add
            add
        }
        FluidType.BRAKE_FLUID -> amount * 0.5f
    }

    private fun blend(currentPurity: Float, currentVol: Float, addPurity: Float, addVol: Float): Float {
        val total = currentVol + addVol
        if (total <= 0.001f) return addPurity
        return ((currentPurity * currentVol + addPurity * addVol) / total).coerceIn(0f, 1f)
    }

    fun mount(slot: ComponentSlot, stack: ItemStack): MountedPart? {
        if (stack.def.mountsTo != slot) return null
        val previous = parts[slot]
        parts[slot] = MountedPart(stack.defId, stack.condition, stack.health)
        // Nájdená batéria má vlastnú šťavu – inak by sa vybité auto nedalo oživiť.
        if (slot == ComponentSlot.BATTERY) {
            batteryCharge = maxOf(batteryCharge, (0.45f + 0.45f * stack.health).coerceAtMost(1f))
        }
        return previous
    }

    fun repair(slot: ComponentSlot, amount: Float = 0.25f) {
        val part = parts[slot] ?: return
        part.health = (part.health + amount).coerceAtMost(1f)
        part.condition = when {
            part.health >= 0.9f -> ComponentCondition.NEW
            part.health >= 0.65f -> ComponentCondition.USED
            part.health >= 0.35f -> ComponentCondition.DAMAGED
            else -> ComponentCondition.CRITICAL
        }
    }

    /**
     * Elektrika beží nezávisle od jazdy: alternátor dobíja pri bežiacom motore,
     * svetlá odoberajú aj keď auto stojí. Vybitá batéria pri vypnutom motore = koniec.
     */
    fun tickElectrics(dt: Float, headlightsOn: Boolean): EndCause? {
        val alternator = parts[ComponentSlot.ALTERNATOR]
        if (engineRunning) {
            val charge = GameConfig.ALTERNATOR_CHARGE *
                (alternator?.let { it.def.reliability * it.health } ?: 0.35f)
            batteryCharge = (batteryCharge + charge * dt).coerceAtMost(1f)
            if (headlightsOn) {
                batteryCharge = (batteryCharge - GameConfig.HEADLIGHT_DRAIN_ON * dt).coerceAtLeast(0f)
            }
            return null
        }
        if (headlightsOn) {
            batteryCharge = (batteryCharge - GameConfig.HEADLIGHT_DRAIN_OFF * dt).coerceAtLeast(0f)
        }
        return if (batteryCharge <= 0.001f) EndCause.BATTERY_DEAD else null
    }

    /**
     * @param slope sklon trate pod autom (+ = do kopca) – motor pod záťažou žerie viac.
     */
    fun tickDriving(dt: Float, throttle: Float, drainMul: Float, slope: Float = 0f): EndCause? {
        if (!engineRunning) return null

        // Do kopca výrazne viac, z kopca menej ako na rovine.
        val s = slope.coerceIn(-0.6f, 0.6f)
        val slopeMul = (1f + if (s > 0f) s * GameConfig.FUEL_SLOPE_UP else s * GameConfig.FUEL_SLOPE_DOWN)
            .coerceIn(0.35f, 2.8f)
        // Riedené palivo horí rýchlejšie na ten istý výkon.
        val qualityMul = 1f + (1f - fuelPurity.coerceIn(0f, 1f)) * 0.5f
        val burn = (GameConfig.FUEL_IDLE + GameConfig.FUEL_THROTTLE * throttle * slopeMul) *
            fuelUseMul * drainMul * qualityMul
        fuel = (fuel - burn * dt).coerceAtLeast(0f)
        if (fuel <= 0f) {
            engineRunning = false
            speed = 0f
            return EndCause.OUT_OF_FUEL
        }

        oil = (oil - GameConfig.OIL_DRAIN * (0.35f + throttle * 0.65f) * dt).coerceAtLeast(0f)
        coolant = (coolant - GameConfig.COOLANT_DRAIN * (0.3f + throttle * 0.7f) * dt).coerceAtLeast(0f)

        // Teplota rastie pomaly; poškodený chladič len mierne zhorší strop, nie okamžitý výbuch.
        // Voda namiesto chladiacej kvapaliny = motor ide trvale teplejšie.
        val rad = radiatorMul.coerceIn(0.45f, 1.4f)
        val heatTarget = GameConfig.NORMAL_TEMP +
            18f * throttle +
            10f * (slopeMul - 1f).coerceAtLeast(0f) +
            18f * (1f - coolantRatio) / rad +
            GameConfig.BAD_COOLANT_HEAT * (1f - coolantPurity.coerceIn(0f, 1f))
        temperature = MathX.lerp(temperature, heatTarget.coerceAtMost(140f), dt * GameConfig.TEMP_RESPONSE)

        val engine = parts[ComponentSlot.ENGINE]
        // Zbierame, čo motor práve zožiera – hráč to musí vidieť skôr, než je neskoro.
        var worstWear = 0f
        var worstCause: WearCause? = null
        fun wear(amount: Float, cause: WearCause) {
            if (amount <= 0f) return
            engine?.let { it.health = (it.health - amount * dt).coerceAtLeast(0f) }
            if (amount > worstWear) {
                worstWear = amount
                worstCause = cause
            }
        }

        if (oil < 0.25f) {
            wear(GameConfig.ENGINE_WEAR_LOW_OIL, WearCause.LOW_OIL)
        }
        // Znečistené kvapaliny škodia až od istej hranice – mierne riedenie motor prežije.
        val badOil = ((GameConfig.PURITY_SAFE_OIL - oilPurity) / GameConfig.PURITY_SAFE_OIL)
            .coerceIn(0f, 1f)
        val badFuel = ((GameConfig.PURITY_SAFE_FUEL - fuelPurity) / GameConfig.PURITY_SAFE_FUEL)
            .coerceIn(0f, 1f)
        wear(GameConfig.ENGINE_WEAR_BAD_OIL * badOil, WearCause.DIRTY_OIL)
        wear(
            GameConfig.ENGINE_WEAR_BAD_FUEL * badFuel * (0.3f + throttle * 0.7f),
            WearCause.DIRTY_FUEL
        )
        if (temperature > GameConfig.OVERHEAT_THRESHOLD) {
            val over = ((temperature - GameConfig.OVERHEAT_THRESHOLD) / 30f).coerceIn(0f, 1f)
            wear(GameConfig.ENGINE_WEAR_OVERHEAT * over, WearCause.OVERHEAT)
            if (temperature > GameConfig.OVERHEAT_THRESHOLD + 28f && over > 0.85f) {
                engineRunning = false
                wearCause = WearCause.OVERHEAT
                return EndCause.OVERHEAT
            }
        }
        wearCause = worstCause
        wearRate = worstWear

        if (engine != null && engine.health <= 0.05f) {
            engineRunning = false
            return EndCause.ENGINE_DESTROYED
        }
        return null
    }

    /**
     * Arcade pohyb po X; Y a pitch sledujú profil trate.
     */
    fun applyDrive(dt: Float, throttle: Float, brake: Float, groundY: Float, slope: Float, bumpMul: Float) {
        val brakePower = GameConfig.BRAKE * brakeMul * brake
        // „Prvý prevodový stupeň“: pri rozjazde má motor citeľne viac ťahu,
        // inak by sa slabý vrak na kopci nikdy nepohol.
        val launch = 1f + 0.9f * (1f - (kotlin.math.abs(speed) / 6f).coerceIn(0f, 1f))
        val accel = if (engineRunning && fuel > 0f) {
            GameConfig.ACCEL * (powerHp / 70f).coerceIn(0.5f, 1.6f) * throttle * launch /
                (1f + bumpMul * 0.35f + kotlin.math.abs(slope) * 0.35f)
        } else 0f

        when {
            // Ľavý pedál pri nízkej rýchlosti = cúvanie (HillRush).
            brake > 0.05f && throttle < 0.05f && speed <= GameConfig.STOP_SPEED * 1.2f -> {
                val rev = GameConfig.REVERSE_ACCEL * brake * brakeMul
                speed = (speed - rev * dt).coerceAtLeast(-GameConfig.REVERSE_MAX_SPEED)
            }
            // Brzda vyhráva len keď je stlačená viac ako plyn – inak by palec
            // opretý o brzdu potichu zablokoval rozjazd.
            brake > 0.05f && brake >= throttle -> {
                if (speed > 0f) speed = (speed - brakePower * dt).coerceAtLeast(0f)
                else if (speed < 0f) speed = (speed + brakePower * dt).coerceAtMost(0f)
            }
            throttle > 0.05f -> {
                if (speed < 0f) {
                    speed = (speed + accel * 1.4f * dt).coerceAtMost(0f)
                } else {
                    speed = (speed + accel * dt)
                        .coerceAtMost(GameConfig.MAX_SPEED * tireGrip.coerceIn(0.5f, 1.1f))
                }
            }
            else -> {
                if (speed > 0f) speed = (speed - GameConfig.COAST_DRAG * dt).coerceAtLeast(0f)
                else if (speed < 0f) speed = (speed + GameConfig.COAST_DRAG * dt).coerceAtMost(0f)
            }
        }

        // Gravitácia po svahu: do kopca brzdí, z kopca ťahá.
        // Používame sínus uhla, nie surový sklon – ten rastie do nekonečna
        // a na strmom mieste by auto zatlačil dozadu bez ohľadu na plyn.
        if (kotlin.math.abs(slope) > 0.005f) {
            val pull = slope / kotlin.math.sqrt(1f + slope * slope)
            speed = (speed - pull * GameConfig.SLOPE_GRAVITY * dt)
                .coerceIn(-GameConfig.REVERSE_MAX_SPEED, GameConfig.MAX_SPEED)
        }

        x += speed * dt
        if (SedanSpec.wheelRadius > 0.01f) {
            val deg = Math.toDegrees((speed * dt / SedanSpec.wheelRadius).toDouble()).toFloat()
            wheelSpinDeg = (wheelSpinDeg + deg) % 360f
        }
        val ride = groundY + GameConfig.CAR_RIDE_HEIGHT
        y = MathX.damp(y, ride, 14f, dt)
        val targetPitch = kotlin.math.atan(slope)
        pitch = MathX.damp(pitch, targetPitch, GameConfig.BODY_PITCH_SMOOTH, dt)
    }
}

/** Bez týchto dielov auto nenaštartuje ani sa nepohne. */
private val ESSENTIAL_SLOTS = listOf(
    ComponentSlot.ENGINE,
    ComponentSlot.FUEL_TANK,
    ComponentSlot.TIRES,
    ComponentSlot.BATTERY,
    ComponentSlot.STARTER
)

enum class EndCause {
    OUT_OF_FUEL, ENGINE_DESTROYED, OVERHEAT, BATTERY_DEAD
}

/** Čo práve najviac ničí motor – ide do hlášok aj do konca jazdy. */
enum class WearCause(val warning: String, val fatal: String) {
    LOW_OIL("Málo oleja — motor sa zadiera!", "zadretý bez oleja"),
    DIRTY_OIL("Špinavý olej brúsi motor", "zodratý špinavým olejom"),
    DIRTY_FUEL("Riedené palivo ničí motor", "zničený riedeným palivom"),
    OVERHEAT("Prehriaty motor sa ničí", "spálený prehriatím")
}
