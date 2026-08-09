package sk.kubis.endlessdrive.game.car

import sk.kubis.endlessdrive.domain.model.ItemCatalog
import sk.kubis.endlessdrive.domain.model.ItemDef
import sk.kubis.endlessdrive.domain.model.ItemStack
import sk.kubis.endlessdrive.domain.model.SedanSpec
import sk.kubis.endlessdrive.core.GameConfig
import sk.kubis.endlessdrive.core.MathX
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
    var temperature = 40f
    var batteryCharge = 0.55f
    var engineRunning = false
    var prepChecklistDone = false

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

    val powerHp: Float
        get() {
            val eng = parts[ComponentSlot.ENGINE] ?: return 40f
            return eng.def.powerHp * eng.health.coerceIn(0.2f, 1f)
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

    fun installStarterKit() {
        // Mechanika beží, karoséria je holá – bez dverí/kapoty/okien/nárazníkov.
        parts[ComponentSlot.ENGINE] = MountedPart(ItemCatalog.ENGINE_A.id, ComponentCondition.USED, 0.78f)
        parts[ComponentSlot.FUEL_TANK] = MountedPart(ItemCatalog.FUEL_TANK.id, ComponentCondition.USED, 0.85f)
        parts[ComponentSlot.BATTERY] = MountedPart(ItemCatalog.BATTERY.id, ComponentCondition.USED, 0.8f)
        parts[ComponentSlot.RADIATOR] = MountedPart(ItemCatalog.RADIATOR.id, ComponentCondition.USED, 0.8f)
        parts[ComponentSlot.TIRES] = MountedPart(ItemCatalog.TIRE.id, ComponentCondition.USED, 0.75f)
        parts[ComponentSlot.BRAKES] = MountedPart(ItemCatalog.BRAKES.id, ComponentCondition.USED, 0.75f)
        parts[ComponentSlot.ALTERNATOR] = MountedPart(ItemCatalog.ALTERNATOR.id, ComponentCondition.USED, 0.8f)
        parts[ComponentSlot.STARTER] = MountedPart(ItemCatalog.STARTER.id, ComponentCondition.USED, 0.8f)
        parts[ComponentSlot.SUSPENSION] = MountedPart(ItemCatalog.SUSPENSION.id, ComponentCondition.USED, 0.75f)
        // Žiadne DOORS / HOOD / WINDOWS / bumpers – hráč ich nájde a namontuje.
        // V aute ostali použiteľné zvyšky kvapalín.
        fuel = 28f
        oil = 2.8f
        coolant = 4.0f
        temperature = 40f
        batteryCharge = 0.75f
        engineRunning = false
        wheelSpinDeg = 0f
    }

    fun canStart(): Boolean {
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

    fun refill(fluid: FluidType, amount: Float): Float = when (fluid) {
        FluidType.FUEL -> {
            val add = amount.coerceAtMost(fuelCapacity - fuel)
            fuel += add
            add
        }
        FluidType.OIL -> {
            val add = amount.coerceAtMost(oilCapacity - oil)
            oil += add
            add
        }
        FluidType.COOLANT -> {
            val add = amount.coerceAtMost(coolantCapacity - coolant)
            coolant += add
            add
        }
        FluidType.BRAKE_FLUID -> amount * 0.5f
    }

    fun mount(slot: ComponentSlot, stack: ItemStack): MountedPart? {
        if (stack.def.mountsTo != slot) return null
        val previous = parts[slot]
        parts[slot] = MountedPart(stack.defId, stack.condition, stack.health)
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

    fun tickDriving(dt: Float, throttle: Float, drainMul: Float): EndCause? {
        if (!engineRunning) return null

        val burn = (GameConfig.FUEL_IDLE + GameConfig.FUEL_THROTTLE * throttle) * fuelUseMul * drainMul
        fuel = (fuel - burn * dt).coerceAtLeast(0f)
        if (fuel <= 0f) {
            engineRunning = false
            speed = 0f
            return EndCause.OUT_OF_FUEL
        }

        oil = (oil - GameConfig.OIL_DRAIN * (0.35f + throttle * 0.65f) * dt).coerceAtLeast(0f)
        coolant = (coolant - GameConfig.COOLANT_DRAIN * (0.3f + throttle * 0.7f) * dt).coerceAtLeast(0f)

        // Teplota rastie pomaly; poškodený chladič len mierne zhorší strop, nie okamžitý výbuch.
        val rad = radiatorMul.coerceIn(0.45f, 1.4f)
        val heatTarget = GameConfig.NORMAL_TEMP +
            18f * throttle +
            22f * (1f - coolantRatio) / rad
        temperature = MathX.lerp(temperature, heatTarget.coerceAtMost(135f), dt * GameConfig.TEMP_RESPONSE)

        if (oil < 0.25f) {
            parts[ComponentSlot.ENGINE]?.let {
                it.health = (it.health - GameConfig.ENGINE_WEAR_LOW_OIL * dt).coerceAtLeast(0f)
            }
        }
        if (temperature > GameConfig.OVERHEAT_THRESHOLD) {
            val over = ((temperature - GameConfig.OVERHEAT_THRESHOLD) / 30f).coerceIn(0f, 1f)
            parts[ComponentSlot.ENGINE]?.let {
                it.health = (it.health - GameConfig.ENGINE_WEAR_OVERHEAT * over * dt).coerceAtLeast(0f)
            }
            if (temperature > GameConfig.OVERHEAT_THRESHOLD + 28f && over > 0.85f) {
                engineRunning = false
                return EndCause.OVERHEAT
            }
        }

        batteryCharge = (batteryCharge + 0.015f * dt).coerceAtMost(1f)
        val eng = parts[ComponentSlot.ENGINE]
        if (eng != null && eng.health <= 0.05f) {
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
        val accel = if (engineRunning && fuel > 0f) {
            GameConfig.ACCEL * (powerHp / 70f).coerceIn(0.4f, 1.6f) * throttle /
                (1f + bumpMul * 0.35f + kotlin.math.abs(slope) * 0.8f)
        } else 0f

        when {
            // Ľavý pedál pri nízkej rýchlosti = cúvanie (HillRush).
            brake > 0.05f && throttle < 0.05f && speed <= GameConfig.STOP_SPEED * 1.2f -> {
                val rev = GameConfig.REVERSE_ACCEL * brake * brakeMul
                speed = (speed - rev * dt).coerceAtLeast(-GameConfig.REVERSE_MAX_SPEED)
            }
            brake > 0.05f -> {
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

enum class EndCause {
    OUT_OF_FUEL, ENGINE_DESTROYED, OVERHEAT, BATTERY_DEAD
}
