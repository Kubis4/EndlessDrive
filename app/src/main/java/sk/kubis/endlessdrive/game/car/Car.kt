package sk.kubis.endlessdrive.game.car

import sk.kubis.endlessdrive.domain.model.ItemCatalog
import sk.kubis.endlessdrive.domain.model.ItemDef
import sk.kubis.endlessdrive.domain.model.ItemStack
import sk.kubis.endlessdrive.domain.model.RoadPaving
import sk.kubis.endlessdrive.domain.model.RoadSurface
import sk.kubis.endlessdrive.domain.model.SedanSpec
import sk.kubis.endlessdrive.domain.model.TIRE_SLOTS
import sk.kubis.endlessdrive.core.GameConfig
import sk.kubis.endlessdrive.core.MathX
import sk.kubis.endlessdrive.core.SeededRandom
import sk.kubis.endlessdrive.domain.model.ComponentCondition
import sk.kubis.endlessdrive.domain.model.BODY_SLOTS
import sk.kubis.endlessdrive.domain.model.ComponentSlot
import sk.kubis.endlessdrive.domain.model.DebugOptions
import sk.kubis.endlessdrive.domain.model.basicFor
import sk.kubis.endlessdrive.domain.model.bestFor
import sk.kubis.endlessdrive.domain.model.DriveLayout
import sk.kubis.endlessdrive.domain.model.FluidType
import sk.kubis.endlessdrive.domain.model.FuelKind
import sk.kubis.endlessdrive.domain.model.VehiclePaint

data class MountedPart(
    val defId: String,
    var condition: ComponentCondition,
    var health: Float,
    var paintIndex: Int = -1,
    var injury: TireInjury = TireInjury.INFLATED
) {
    val def: ItemDef get() = ItemCatalog.byId(defId) ?: ItemCatalog.ENGINE_A
}

/**
 * Vehicle simulation: world X/Y and planar Z/heading, with axle suspension.
 */
class Car {
    val planar = PlanarMotion()
    val parts = linkedMapOf<ComponentSlot, MountedPart>()

    /**
     * Lak škrupiny. Nájdené plechy môžu mať iný odtieň, kým ich hráč
     * nenechá prestriekať v dielni — [applyBodyPaint] zjednotí všetko.
     */
    var bodyPaintIndex: Int = 0
    /** 0..1 sneh na karosérii. V zime pribúda, na suchu topí. */
    var snowCover: Float = 0f
    /** 0..1 blato a prach na spodku. V bahne pribúda, vode zmýva. */
    var bodyDirt: Float = 0f
    /**
     * Trvalejšia hrdza na škrupine. Pribúda zo špíny/cesty, dážď ju
     * nezmyje (na rozdiel od [bodyDirt]) – inak by pri daždi/jazde mizla.
     * Prelakovanie ju vynuluje.
     */
    var bodyRust: Float = 0f

    var fuel = 8f
    var oil = 1.2f
    var coolant = 2.0f

    /** Čistota toho, čo je práve v nádržiach (1 = čisté, menej = riedené vodou). */
    var fuelPurity = 1f
    /** Zloženie nádrže: 0 = čistý benzín, 1 = čistý diesel. */
    var fuelDieselFraction = 0f
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
    /** Vertikálna rýchlosť (m/s) – vo vzduchu / pruženie. */
    var vy = 0f
    var speed = 0f
    /** Lineárna rýchlosť povrchu kolesa (m/s) – môže sa líšiť od speed pri preklze. */
    var wheelSpeed = 0f
        private set
    /** Náklon karosérie (rad). */
    var pitch = 0f
    /** Uhlová rýchlosť náklonu (rad/s). */
    var pitchRate = 0f
        private set
    /** Otáčanie kolies (stupne) – pre 3D model, každá náprava zvlášť. */
    var wheelSpinDeg = 0f
    /** Uhol predného kolesa – pri RWD sa len valí, netočí sa s preklzom. */
    var wheelSpinFrontDeg = 0f
    /** Uhol zadného kolesa. */
    var wheelSpinRearDeg = 0f
    /** true = aspoň jedno koleso má kontakt. */
    var grounded = true
        private set
    /**
     * Krátka vizuálna hysterézia kontaktu. Na ostrom hrane môže silový model
     * na jediný krok zahlásiť vzduch, hoci o snímku neskôr je koleso znova na
     * vozovke. Bez hysterézie render v tom jednom kroku prepol na úplne inú
     * súradnicu karosérie a auto bliklo pod cestou.
     */
    private var airborneFor = 0f
    val visuallyGrounded: Boolean get() = grounded || airborneFor < 0.08f
    /** 0..1 – ako veľmi kolesá preklzávajú. */
    var wheelSlip = 0f
        private set
    /** true = kolesá sú zablokované brzdou. */
    var wheelsLocked = false
        private set
    /** Aktuálna normálová sila (súčet náprav), na debug/HUD. */
    var normalForce = 0f
        private set
    var rearNormalForce = 0f
        private set
    var frontNormalForce = 0f
        private set
    /** Stlačenie pruženia 0..1. */
    var suspensionLoad = 0f
        private set
    /** Jednokrokový impulz tvrdého dopadu 0..1 pre náklad a efekty. */
    var landingImpact = 0f
        private set

    /** Stlačenie zadnej / prednej nápravy (m) – pre vizuál pruženia. */
    val rearCompression: Float get() = rearComp
    val frontCompression: Float get() = frontComp

    private var rearComp = 0f
    private var frontComp = 0f

    /** Náklad, ktorý nie je namontovaným dielom: batoh pri posádke a veci v kufri. */
    private var packLoadKg = 0f
    private var bootLoadKg = 0f

    private var garageEngineTuneLevel = 0
    private var garageLoadSuspensionLevel = 0
    private var garageBrakePackageLevel = 0
    private var garageCruiseEfficiencyLevel = 0
    /** Ako dlho už stojíme na brzde – spätný chod až po krátkej páuzze. */
    private var reverseArmS = 0f

    fun setCargoLoad(packKg: Float, bootKg: Float) {
        packLoadKg = packKg.coerceAtLeast(0f)
        bootLoadKg = bootKg.coerceAtLeast(0f)
    }

    /** Trvalé dielenské bonusy. Sú odvodené z profilu, preto nepatria do save-u jazdy. */
    fun setGaragePerformance(
        engineTune: Int,
        loadSuspension: Int,
        brakePackage: Int,
        cruiseEfficiency: Int = 0
    ) {
        garageEngineTuneLevel = engineTune.coerceIn(0, 3)
        garageLoadSuspensionLevel = loadSuspension.coerceIn(0, 3)
        garageBrakePackageLevel = brakePackage.coerceIn(0, 3)
        garageCruiseEfficiencyLevel = cruiseEfficiency.coerceIn(0, 3)
    }

    val garagePowerMultiplier: Float
        get() = 1f + garageEngineTuneLevel * GameConfig.GARAGE_ENGINE_POWER_PER_LEVEL

    val garageSpringMultiplier: Float
        get() = 1f + garageLoadSuspensionLevel * GameConfig.GARAGE_SPRING_PER_LEVEL

    val garageDamperMultiplier: Float
        get() = 1f + garageLoadSuspensionLevel * GameConfig.GARAGE_DAMPER_PER_LEVEL

    val garageBrakeMultiplier: Float
        get() = 1f + garageBrakePackageLevel * GameConfig.GARAGE_BRAKE_PER_LEVEL

    /** Násobiteľ spotreby plynu pri cruise controli (≥ 1). */
    val cruiseFuelMultiplier: Float
        get() = (GameConfig.CRUISE_FUEL_MULT -
            garageCruiseEfficiencyLevel * GameConfig.GARAGE_CRUISE_FUEL_REDUCTION_PER_LEVEL)
            .coerceAtLeast(1f)

    /** Kde hmotnosť dielu leží: 0 = predná náprava, 1 = zadná náprava. */
    private fun componentRearBias(slot: ComponentSlot, part: MountedPart): Float = when (slot) {
        ComponentSlot.ENGINE -> 0.08f
        ComponentSlot.ALTERNATOR -> 0.10f
        ComponentSlot.STARTER -> 0.12f
        ComponentSlot.RADIATOR -> 0.02f
        ComponentSlot.BATTERY -> 0.18f
        ComponentSlot.FUEL_TANK -> 0.82f
        ComponentSlot.TIRE_FRONT, ComponentSlot.HEADLIGHT,
        ComponentSlot.FRONT_BUMPER -> 0.02f
        ComponentSlot.TIRE_REAR, ComponentSlot.TAILLIGHT,
        ComponentSlot.REAR_BUMPER -> 0.98f
        ComponentSlot.DRIVETRAIN -> when (part.def.driveLayout ?: DriveLayout.RWD) {
            DriveLayout.FWD -> 0.28f
            DriveLayout.RWD -> 0.66f
            DriveLayout.AWD -> 0.50f
        }
        ComponentSlot.SUSPENSION, ComponentSlot.BRAKES,
        ComponentSlot.CHAINS -> 0.50f
        ComponentSlot.DOOR_FRONT, ComponentSlot.SEAT_FRONT -> 0.36f
        ComponentSlot.DOOR_REAR, ComponentSlot.SEAT_REAR -> 0.67f
        ComponentSlot.HOOD -> 0.08f
        ComponentSlot.TRUNK_LID -> 0.86f
        ComponentSlot.CARGO -> if (part.defId == ItemCatalog.BACKPACK.id) 0.38f else 0.88f
        ComponentSlot.ROOF_RACK -> 0.58f
    }

    /** Reálna hmotnosť zostavy vrátane kvapalín a prevážaných predmetov. */
    val totalMassKg: Float
        get() = GameConfig.VEHICLE_BASE_MASS_KG +
            parts.values.sumOf { it.def.weight.toDouble() }.toFloat() +
            fuel * 0.75f + oil * 0.86f + coolant +
            packLoadKg + bootLoadKg

    /** Statický podiel hmotnosti na zadnej náprave, odvodený z polohy každého dielu. */
    val rearWeightBias: Float
        get() {
            var rearKg = GameConfig.VEHICLE_BASE_MASS_KG * GameConfig.VEHICLE_BASE_REAR_BIAS
            parts.forEach { (slot, part) ->
                rearKg += part.def.weight * componentRearBias(slot, part)
            }
            rearKg += fuel * 0.75f * 0.82f
            rearKg += oil * 0.86f * 0.08f
            rearKg += coolant * 0.05f
            rearKg += packLoadKg * GameConfig.PACK_LOAD_REAR_BIAS
            rearKg += bootLoadKg * GameConfig.BOOT_LOAD_REAR_BIAS
            return (rearKg / totalMassKg.coerceAtLeast(1f)).coerceIn(0.28f, 0.72f)
        }

    /** Normovaná hmotnosť používaná pružením a silami. */
    val physicsMass: Float
        get() {
            val realRatio = totalMassKg / GameConfig.VEHICLE_REFERENCE_MASS_KG
            val gameplayRatio = if (realRatio > 1f) {
                1f + (realRatio - 1f) * GameConfig.VEHICLE_WEIGHT_GAMEPLAY_GAIN
            } else {
                realRatio
            }
            return GameConfig.CAR_MASS * gameplayRatio.coerceIn(0.70f, 1.85f)
        }

    /** 0 = ľahké/referenčné auto, 1 = vizuálne plne zaťažený podvozok. */
    val visualLoadFraction: Float
        get() = ((totalMassKg - GameConfig.VEHICLE_REFERENCE_MASS_KG) /
            GameConfig.VEHICLE_VISUAL_LOAD_RANGE_KG).coerceIn(0f, 1f)

    private val excessGameplayMass: Float
        get() = (physicsMass / GameConfig.CAR_MASS - 1f).coerceAtLeast(0f)

    /** Poloha ťažiska od geometrického stredu auta; kladná hodnota je dopredu. */
    val centerOfMassOffsetM: Float
        get() = SedanSpec.wheelOffsetX * (1f - 2f * rearWeightBias)

    fun hasPart(slot: ComponentSlot): Boolean = parts.containsKey(slot)

    val fuelCapacity: Float
        get() = parts[ComponentSlot.FUEL_TANK]?.def?.capacity?.takeIf { it > 0f } ?: 40f
    val oilCapacity: Float get() = 4f
    val coolantCapacity: Float get() = 6f

    val requiredFuelKind: FuelKind
        get() = parts[ComponentSlot.ENGINE]?.def?.fuelKind ?: FuelKind.PETROL

    /** Podiel paliva v nádrži, ktorý patrí do namontovaného motora. */
    val correctFuelFraction: Float
        get() = when (requiredFuelKind) {
            FuelKind.PETROL -> 1f - fuelDieselFraction.coerceIn(0f, 1f)
            FuelKind.DIESEL -> fuelDieselFraction.coerceIn(0f, 1f)
        }

    val wrongFuelFraction: Float get() = 1f - correctFuelFraction

    /** Čistota aj správny druh paliva priamo ovplyvňujú výkon. */
    val fuelQualityMul: Float
        get() {
            val purity = 0.55f + 0.45f * fuelPurity.coerceIn(0f, 1f)
            val compatibility = 1f - 0.70f * wrongFuelFraction
            return purity * compatibility.coerceIn(0.30f, 1f)
        }

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

    /**
     * Priľnavosť jednej nápravy. Opotrebenie neuberá grip lineárne – dezén
     * sa zodiera pomaly, ale plešatá guma spadne rýchlo.
     */
    fun axleGrip(slot: ComponentSlot): Float {
        val t = parts[slot] ?: return GameConfig.RIM_GRIP
        val base = if (t.def.grip > 0f) t.def.grip else t.def.reliability
        return when (t.injury) {
            TireInjury.SHREDDED -> GameConfig.RIM_GRIP
            TireInjury.PUNCTURED -> base * 0.22f
            TireInjury.INFLATED -> base * treadFactor(t.health)
        }
    }

    /**
     * Celková priľnavosť – predok/zadok podľa pohonu (RWD ťaží zadok, FWD predok).
     */
    val tireGrip: Float
        get() {
            val front = axleGrip(ComponentSlot.TIRE_FRONT)
            val rear = axleGrip(ComponentSlot.TIRE_REAR)
            return when (driveLayout) {
                DriveLayout.RWD -> rear * 0.72f + front * 0.28f
                DriveLayout.FWD -> front * 0.72f + rear * 0.28f
                DriveLayout.AWD -> (front + rear) * 0.5f
            }
        }

    /**
     * Prehodí prednú a zadnú gumu. Keď sa hnaná náprava zodrala a druhá je
     * ešte slušná, dá sa tým jazda predĺžiť bez jediného nálezu.
     */
    fun swapTyres(): Boolean {
        val front = parts[ComponentSlot.TIRE_FRONT]
        val rear = parts[ComponentSlot.TIRE_REAR]
        if (front == null && rear == null) return false
        if (rear == null) parts.remove(ComponentSlot.TIRE_FRONT) else parts[ComponentSlot.TIRE_FRONT] = rear
        if (front == null) parts.remove(ComponentSlot.TIRE_REAR) else parts[ComponentSlot.TIRE_REAR] = front
        return true
    }

    /** Horšia z náprav – s ňou má zmysel porovnávať nájdenú gumu. */
    fun worstTyre(): MountedPart? = TIRE_SLOTS
        .mapNotNull { parts[it] }
        .minByOrNull { it.health }

    /** Sloty navyše z namontovaného úložiska. */
    val extraCargoSlots: Int
        get() = (parts[ComponentSlot.CARGO]?.def?.extraSlots ?: 0) +
            (parts[ComponentSlot.ROOF_RACK]?.def?.extraSlots ?: 0)

    /** Nosnosť navyše z úložiska (kg). */
    val extraCargoWeight: Float
        get() = (parts[ComponentSlot.CARGO]?.def?.extraWeight ?: 0f) +
            (parts[ComponentSlot.ROOF_RACK]?.def?.extraWeight ?: 0f)

    /**
     * Batoh sa nosí na chrbte, takže zväčšuje to, čo hráč unesie – nie kufor.
     * Debna a strešný nosič sú naopak na aute.
     */
    private val backpack: MountedPart?
        get() = parts[ComponentSlot.CARGO]?.takeIf { it.defId == ItemCatalog.BACKPACK.id }

    val packBonusSlots: Int get() = backpack?.def?.extraSlots ?: 0
    val packBonusWeight: Float get() = backpack?.def?.extraWeight ?: 0f

    /** Úložisko na aute – všetko okrem batoha. */
    val bootBonusSlots: Int get() = extraCargoSlots - packBonusSlots
    val bootBonusWeight: Float get() = extraCargoWeight - packBonusWeight

    /** Odpor vzduchu navyše – strešný nosič stojí rýchlosť. */
    val extraDrag: Float
        get() = (parts[ComponentSlot.ROOF_RACK]?.def?.dragAdd ?: 0f) +
            (parts[ComponentSlot.CARGO]?.def?.dragAdd ?: 0f)

    /** true = na streche stojí nosič (kreslí sa aj v hre). */
    val hasRoofRack: Boolean get() = hasPart(ComponentSlot.ROOF_RACK)

    /** true = na kolesách sú reťaze. */
    val hasChains: Boolean get() = hasPart(ComponentSlot.CHAINS)

    /**
     * Násobiteľ priľnavosti na snehu a ľade. Letná guma sa točí, zimná drží,
     * reťaze z toho spravia použiteľnú jazdu – ale na suchu prekážajú.
     */
    val winterTraction: Float
        get() {
            val front = parts[ComponentSlot.TIRE_FRONT]?.def?.snowGrip ?: 0.26f
            val rear = parts[ComponentSlot.TIRE_REAR]?.def?.snowGrip ?: 0.26f
            // Rozhoduje hnaná náprava, nie priemer oboch. Pri rovnomernom
            // priemere zimná guma vzadu na RWD takmer nepomohla – letná
            // vpredu, ktorá žiadny ťah neprenáša, jej výhodu zjedla.
            val tyres = when (driveLayout) {
                DriveLayout.RWD -> rear * 0.78f + front * 0.22f
                DriveLayout.FWD -> front * 0.78f + rear * 0.22f
                DriveLayout.AWD -> (front + rear) * 0.5f
            }
            return if (hasChains) {
                maxOf(tyres * GameConfig.CHAINS_SNOW_BONUS, GameConfig.CHAINS_SNOW_FLOOR)
            } else tyres
        }

    /**
     * 0 = letná zmes (výkon ide do preklzu), 1 = zimná guma / reťaze.
     * Škáluje overdrive a rezervu trakcie na snehu.
     */
    private fun snowPowerScale(): Float =
        ((winterTraction - 0.38f) / 1.10f).coerceIn(0f, 1f)

    /** Reťaze na holom asfalte grip uberajú, nie pridávajú. */
    val tarmacPenalty: Float
        get() = if (hasChains) GameConfig.CHAINS_TARMAC_PENALTY else 1f

    /** Náprava, ktorá prenáša ťah – podľa nej ide dym, štrk aj stopy. */
    val drivenSlot: ComponentSlot
        get() = if (driveLayout == DriveLayout.FWD) ComponentSlot.TIRE_FRONT
        else ComponentSlot.TIRE_REAR

    /** true = táto náprava dostáva ťah (pri 4×4 obe). */
    fun drives(slot: ComponentSlot): Boolean = when (driveLayout) {
        DriveLayout.AWD -> slot == ComponentSlot.TIRE_FRONT || slot == ComponentSlot.TIRE_REAR
        DriveLayout.FWD -> slot == ComponentSlot.TIRE_FRONT
        DriveLayout.RWD -> slot == ComponentSlot.TIRE_REAR
    }

    /** 0 = hnané nápravy majú gumu, 1 = idú po ráfiku. */
    fun drivenRimBlend(): Float {
        val driven = TIRE_SLOTS.filter { drives(it) }
        if (driven.isEmpty()) return 0f
        return driven.count { tireInjury(it) == TireInjury.SHREDDED } / driven.size.toFloat()
    }

    /** Aspoň jedna guma je na handry – auto ide na disku. */
    val hasBlownTyre: Boolean
        get() = hasShreddedTyre || hasPuncturedTyre

    val hasShreddedTyre: Boolean
        get() = TIRE_SLOTS.any { tireInjury(it) == TireInjury.SHREDDED }

    val hasPuncturedTyre: Boolean
        get() = TIRE_SLOTS.any { tireInjury(it) == TireInjury.PUNCTURED }

    /** Ktorá náprava je roztrhaná (null = obe držia). */
    val blownAxle: ComponentSlot?
        get() = TIRE_SLOTS.firstOrNull { tireInjury(it) == TireInjury.SHREDDED }

    /** Extra valivý odpor z defektu a jazdy na ráfiku. */
    val tireInjuryDrag: Float
        get() = TIRE_SLOTS.sumOf { slot ->
            when (tireInjury(slot)) {
                TireInjury.SHREDDED -> 0.85
                TireInjury.PUNCTURED -> 0.38
                TireInjury.INFLATED -> 0.0
            }
        }.toFloat()

    /** Zlomok gripu, ktorý zo zodratej gumy ostal (0..1.1). */
    fun treadFactor(health: Float): Float {
        val h = health.coerceIn(0f, 1.2f)
        // Nad 30 % mierny pokles, pod 30 % strmý – vtedy je čas hľadať gumy.
        return if (h >= 0.30f) (0.55f + 0.45f * h).coerceAtMost(1.10f)
        else 0.18f + (0.685f - 0.18f) * (h / 0.30f)
    }

    /** Násobiteľ veľkosti jedného kolesa. */
    fun wheelScale(slot: ComponentSlot): Float =
        parts[slot]?.def?.wheelScale?.takeIf { it > 0.2f } ?: 1f

    /** Stav gumy na náprave; chýbajúca guma sa správa ako roztrhnutá. */
    fun tireInjury(slot: ComponentSlot): TireInjury =
        parts[slot]?.injury ?: TireInjury.SHREDDED

    /**
     * Priemer na vozovke pre fyziku (spin, odpor). Vizuál drží koleso
     * v blatníku a defekt kreslí spľasnutím, nie zmenšením celej nápravy.
     * Roztrhnutá guma (jazda na disku) už ide cez [wheelVisualScale].
     */
    fun wheelContactScale(slot: ComponentSlot): Float {
        val base = wheelScale(slot)
        return when (tireInjury(slot)) {
            TireInjury.SHREDDED -> base * WHEEL_HUB_FRAC
            TireInjury.PUNCTURED -> base * 0.74f
            TireInjury.INFLATED -> base
        }
    }

    /**
     * Vizuálny polomer nápravy. Defekt ostáva v blatníku – placka je pod
     * ráfikom. Až roztrhnutá guma spustí os na disk, inak by auto viselo
     * na neviditeľnej pneumatike.
     */
    fun wheelVisualScale(slot: ComponentSlot): Float {
        val base = wheelScale(slot)
        return when (tireInjury(slot)) {
            TireInjury.SHREDDED -> base * WHEEL_HUB_FRAC
            TireInjury.PUNCTURED, TireInjury.INFLATED -> base
        }
    }

    /**
     * Extra náklon, keď jedna náprava ide na ráfiku. Nose-up je kladný, menší
     * predný polomer teda sklopí nos k zemi.
     */
    fun rimSagPitch(): Float {
        val wb = (SedanSpec.wheelOffsetX * 2f).coerceAtLeast(0.5f)
        val rearR = SedanSpec.wheelRadius * wheelVisualScale(ComponentSlot.TIRE_REAR)
        val frontR = SedanSpec.wheelRadius * wheelVisualScale(ComponentSlot.TIRE_FRONT)
        return kotlin.math.atan((frontR - rearR) / wb)
    }

    /** Fyzikálny pitch plus pokles nápravy pri jazde na disku. */
    val visualPitch: Float get() = pitch + rimSagPitch()

    /** Priemerný násobiteľ veľkosti kolies (off-road väčšie, sport menšie). */
    val tireWheelScale: Float
        get() = (wheelScale(ComponentSlot.TIRE_FRONT) + wheelScale(ComponentSlot.TIRE_REAR)) * 0.5f

    /** Odolnosť gúm voči hrboľom (priemer náprav). */
    val tireBumpResist: Float
        get() {
            fun resist(slot: ComponentSlot) =
                parts[slot]?.def?.bumpResist?.coerceIn(0.4f, 2f) ?: 1f
            return (resist(ComponentSlot.TIRE_FRONT) + resist(ComponentSlot.TIRE_REAR)) * 0.5f
        }

    /**
     * Kam namontovať ďalšiu pneumatiku: prázdna náprava, inak horší grip/stav.
     */
    fun pickTireMountSlot(): ComponentSlot {
        val front = parts[ComponentSlot.TIRE_FRONT]
        val rear = parts[ComponentSlot.TIRE_REAR]
        return when {
            front == null -> ComponentSlot.TIRE_FRONT
            rear == null -> ComponentSlot.TIRE_REAR
            else -> {
                val fScore = axleGrip(ComponentSlot.TIRE_FRONT) * (front.health + 0.15f)
                val rScore = axleGrip(ComponentSlot.TIRE_REAR) * (rear.health + 0.15f)
                if (fScore <= rScore) ComponentSlot.TIRE_FRONT else ComponentSlot.TIRE_REAR
            }
        }
    }

    /** Kvalita pruženia – tvrdosť, tlmenie, držanie kontaktu. */
    val suspensionMul: Float
        get() {
            val s = parts[ComponentSlot.SUSPENSION] ?: return GameConfig.SUSP_MISSING_MUL
            return s.def.reliability * s.health.coerceIn(0.25f, 1.3f)
        }

    /** Pružiny: ojazdené ostane mäkké, ale obe nápravy unesú statickú váhu. */
    private fun suspensionSpringMul(): Float =
        if (!hasPart(ComponentSlot.SUSPENSION)) GameConfig.SUSP_MISSING_SPRING
        else suspensionMul.coerceIn(GameConfig.SUSP_WORN_SPRING_FLOOR, 1.4f) *
            garageSpringMultiplier

    /** Tlmiče smú klesnúť nižšie – húpavé ojazdené, nie zlomený krk. */
    private fun suspensionDampMul(): Float =
        if (!hasPart(ComponentSlot.SUSPENSION)) GameConfig.SUSP_MISSING_DAMP
        else suspensionMul.coerceIn(GameConfig.SUSP_WORN_DAMP_FLOOR, 1.4f) *
            garageDamperMultiplier

    /** Svetlá výška (stred karosérie nad vozovkou pri stlačenom pružení). */
    val rideHeight: Float
        get() {
            val h = parts[ComponentSlot.SUSPENSION]?.def?.rideHeight ?: 0f
            return if (h > 0.05f) h else GameConfig.CAR_RIDE_HEIGHT
        }

    /** Max stlačenie pruženia. */
    val suspTravel: Float
        get() {
            val t = parts[ComponentSlot.SUSPENSION]?.def?.suspTravel ?: 0f
            return if (t > 0.05f) t else GameConfig.SUSP_MAX_TRAVEL
        }

    /** Pohon – predvolene RWD. */
    val driveLayout: DriveLayout
        get() = parts[ComponentSlot.DRIVETRAIN]?.def?.driveLayout ?: DriveLayout.RWD

    /**
     * Zimný grip jednej nápravy. Na snehu platí [ItemDef.snowGrip], nie suchý
     * [ItemDef.grip] – inak športová guma (μ 1.45) preletí závej.
     */
    fun axleSnowGrip(slot: ComponentSlot): Float {
        val t = parts[slot] ?: return GameConfig.RIM_GRIP
        val snow = t.def.snowGrip
        val chained = if (hasChains) {
            maxOf(snow * GameConfig.CHAINS_SNOW_BONUS, GameConfig.CHAINS_SNOW_FLOOR)
        } else snow
        return when (t.injury) {
            TireInjury.SHREDDED -> GameConfig.RIM_GRIP
            TireInjury.PUNCTURED -> chained * 0.22f
            TireInjury.INFLATED -> chained * treadFactor(t.health)
        }
    }

    /**
     * Efektívne μ jednej nápravy (grip × pruženie × hrboľatosť).
     * Guma má podlahu 0.2 – defekt ostane hrateľný. Ráfik ide nižšie,
     * bez statického „prilepenia“ ako pneumatika.
     * Na snehu berie [axleSnowGrip], nie suchý dezén.
     */
    fun axleSurfaceMu(
        slot: ComponentSlot,
        bumpMul: Float = 0f,
        atSpeed: Float = 0f,
        winterSurface: Boolean = false
    ): Float {
        val shredded = tireInjury(slot) == TireInjury.SHREDDED
        val raw = if (winterSurface) axleSnowGrip(slot) else axleGrip(slot)
        val tire = if (shredded) raw.coerceIn(0.04f, 0.25f)
        else raw.coerceIn(if (winterSurface) 0.10f else 0.2f, 1.9f)
        val susMul = if (!hasPart(ComponentSlot.SUSPENSION)) {
            GameConfig.SUSP_MISSING_MUL
        } else {
            suspensionMul.coerceIn(0.3f, 1.3f)
        }
        val sus = 0.75f + 0.25f * susMul
        val rough = 1f / (1f + bumpMul * 0.55f / tireBumpResist)
        val staticMul = if (shredded) GameConfig.RIM_STATIC_GRIP_MUL else 1f
        val crawl = 1f + GameConfig.STATIC_GRIP_BONUS * staticMul *
            (1f - (kotlin.math.abs(atSpeed) / 6f).coerceIn(0f, 1f))
        return GameConfig.TIRE_MU * tire * sus * rough * crawl
    }

    /** Súhrnné μ – predok/zadok podľa pohonu. Ťah a brzda berú nápravy zvlášť. */
    fun surfaceMu(
        bumpMul: Float = 0f,
        atSpeed: Float = 0f,
        winterSurface: Boolean = false
    ): Float {
        val front = axleSurfaceMu(ComponentSlot.TIRE_FRONT, bumpMul, atSpeed, winterSurface)
        val rear = axleSurfaceMu(ComponentSlot.TIRE_REAR, bumpMul, atSpeed, winterSurface)
        return when (driveLayout) {
            DriveLayout.RWD -> rear * 0.72f + front * 0.28f
            DriveLayout.FWD -> front * 0.72f + rear * 0.28f
            DriveLayout.AWD -> (front + rear) * 0.5f
        }
    }

    /** μ hnaných náprav – kopec drží to, čo točí. */
    fun driveAxleMu(
        bumpMul: Float = 0f,
        atSpeed: Float = 0f,
        winterSurface: Boolean = false
    ): Float {
        val front = axleSurfaceMu(ComponentSlot.TIRE_FRONT, bumpMul, atSpeed, winterSurface)
        val rear = axleSurfaceMu(ComponentSlot.TIRE_REAR, bumpMul, atSpeed, winterSurface)
        return when (driveLayout) {
            DriveLayout.RWD -> rear
            DriveLayout.FWD -> front
            DriveLayout.AWD -> (front + rear) * 0.5f
        }
    }

    /** Podiel váhy, ktorý ťah vôbec môže využiť – podľa pohonu. */
    private val driveShare: Float
        get() = when (driveLayout) {
            DriveLayout.AWD -> 0.95f
            DriveLayout.FWD ->
                1f - (rearWeightBias + GameConfig.WEIGHT_TRANSFER * GameConfig.FWD_TRANSFER_MUL)
            DriveLayout.RWD -> rearWeightBias + GameConfig.WEIGHT_TRANSFER
        }

    /**
     * Max sklon (dy/dx), ktorý ešte gumy udržia zo stojky: tanθ < μ.
     * Na rovinke je vždy „dost“ – limit platí len do kopca.
     */
    fun maxClimbSlope(
        bumpMul: Float = 0f,
        atSpeed: Float = 0f,
        winterSurface: Boolean = false
    ): Float = driveAxleMu(bumpMul, atSpeed, winterSurface) * driveShare

    /** Chladenie: bez chladiča kvapalina sama motor neutiahne. */
    val radiatorMul: Float
        get() {
            val r = parts[ComponentSlot.RADIATOR] ?: return 0.22f
            return (r.def.reliability * r.health.coerceIn(0.15f, 1.3f)).coerceIn(0.15f, 1.4f)
        }

    /** Reálny výkon alternátora: stav dielu priamo určuje, koľko vie dodať. */
    val alternatorOutput: Float
        get() = parts[ComponentSlot.ALTERNATOR]
            ?.let { it.def.reliability * it.health }
            ?.coerceIn(0f, 1.2f)
            ?: 0f

    /** Fyzický stav batérie = koľko kapacity ešte drží (karta v CAR). */
    val batteryHoldCapacity: Float
        get() = parts[ComponentSlot.BATTERY]?.health?.coerceIn(0f, 1f) ?: 0f

    /**
     * Maximálne SoC, ktoré vie batéria držať – zdravie dielu, nie výkon
     * alternátora. Alternátor určuje, či a ako rýchlo sa nabíja; aj slabší
     * kus má dostať zdravú batériu k jej stropu, iba pomalšie.
     */
    val batteryChargeCeiling: Float
        get() = batteryHoldCapacity

    val overallHealth: Float
        get() {
            val durable = parts.values.filter { it.def.hasDurability }
            if (durable.isEmpty()) return 0f
            return durable.map { it.health }.average().toFloat()
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
    /**
     * @param debug ladiace prepínače z nastavení. Naplno vybavené auto sa
     *   inak dá dostať len nahraním lootu, čo pri testovaní jednej veci
     *   znamená polhodinu zháňania. Testy nechávajú prepínače vypnuté.
     */
    fun installStarterKit(
        rng: SeededRandom = SeededRandom(0L),
        debug: DebugOptions = DebugOptions.OFF
    ) {
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
        mount(ComponentSlot.ENGINE, ItemCatalog.ENGINE_A.id, 0.38f, 0.70f)
        mount(ComponentSlot.FUEL_TANK, ItemCatalog.FUEL_TANK.id, 0.40f, 0.75f)
        // Štart: ojazdené „poor“ gumy zvlášť predok / zadok.
        mount(ComponentSlot.TIRE_FRONT, ItemCatalog.TIRE_POOR.id, 0.28f, 0.58f)
        mount(ComponentSlot.TIRE_REAR, ItemCatalog.TIRE_POOR.id, 0.28f, 0.58f)
        mount(ComponentSlot.BRAKES, ItemCatalog.BRAKES.id, 0.30f, 0.68f)
        mount(ComponentSlot.SUSPENSION, ItemCatalog.SUSPENSION.id, 0.25f, 0.60f)
        // Štart vždy 2WD – FWD alebo RWD. 4×4 je až upgrade z lootu.
        val driveId = if (rng.chance(0.5f)) ItemCatalog.DRIVE_RWD.id else ItemCatalog.DRIVE_FWD.id
        mount(ComponentSlot.DRIVETRAIN, driveId, 0.40f, 0.80f)
        // Vodič má byť vidieť od prvej jazdy – predné sedadlo ostáva vo vraku.
        mount(ComponentSlot.SEAT_FRONT, ItemCatalog.SEAT_FRONT.id, 0.55f, 0.88f)

        // Tieto z auta často niekto vybral – potom sa musia nájsť.
        if (rng.chance(0.75f)) mount(ComponentSlot.BATTERY, ItemCatalog.BATTERY.id, 0.30f, 0.72f)
        if (rng.chance(0.80f)) mount(ComponentSlot.RADIATOR, ItemCatalog.RADIATOR.id, 0.30f, 0.72f)
        if (rng.chance(0.70f)) mount(ComponentSlot.ALTERNATOR, ItemCatalog.ALTERNATOR.id, 0.30f, 0.75f)
        if (rng.chance(0.80f)) mount(ComponentSlot.STARTER, ItemCatalog.STARTER.id, 0.35f, 0.78f)
        // Karoséria (dvere/kapota/okná/nárazníky) chýba vždy – okrem ladenia,
        // to sa dorába nižšie, až keď sú nastavené aj kvapaliny.

        // Bezpečný štart: prvé kilometre majú byť náročné rozhodovaním,
        // nie náhodným game overom na prázdnej nádrži.
        fuel = rng.nextFloat(GameConfig.START_FUEL_MIN, GameConfig.START_FUEL_MAX)
        oil = rng.nextFloat(GameConfig.START_OIL_MIN, 2.6f)
        coolant = rng.nextFloat(GameConfig.START_COOLANT_MIN, 4.2f)
        fuelPurity = starterFluidPurity(rng, 0.46f, 0.76f)
        fuelDieselFraction = 0f
        bodyPaintIndex = rng.nextInt(VehiclePaint.entries.size)
        oilPurity = starterFluidPurity(rng, 0.42f, 0.74f)
        coolantPurity = starterFluidPurity(rng, 0.38f, 0.72f)

        temperature = 40f
        batteryCharge = seededBatteryCharge(rng)
        engineRunning = false
        wheelSpinDeg = 0f
        wheelSpinFrontDeg = 0f
        wheelSpinRearDeg = 0f
        vy = 0f
        wheelSpeed = 0f
        pitchRate = 0f
        grounded = true
        airborneFor = 0f
        wheelSlip = 0f
        wheelsLocked = false
        normalForce = 0f
        rearNormalForce = 0f
        frontNormalForce = 0f
        suspensionLoad = 0f
        rearComp = 0f
        frontComp = 0f

        applyDebugOptions(debug)
    }

    /**
     * Dorobí auto podľa ladiacich prepínačov. Zámerne až na konci prípravy,
     * aby prepísala aj kvapaliny – a zámerne mimo náhody zo seedu, takže dve
     * jazdy s rovnakým nastavením začnú rovnako a testuje sa vždy to isté.
     */
    private fun applyDebugOptions(debug: DebugOptions) {
        if (!debug.any) return

        fun fit(slot: ComponentSlot, def: ItemDef?) {
            val item = def ?: return
            parts[slot] = MountedPart(
                item.id,
                ComponentCondition.NEW,
                1f,
                paintIndex = if (item.mountsTo?.takesBodyPaint == true) {
                    slot.ordinal.mod(VehiclePaint.entries.size)
                } else -1
            )
            // Nová batéria z debugu nesmie ostať na 0 % SoC z vraku bez batérie.
            if (slot == ComponentSlot.BATTERY) batteryCharge = 1f
        }

        if (debug.allComponents || debug.fullUpgrades) {
            ComponentSlot.entries.forEach { slot ->
                // Upgrade prepíše aj to, čo už v aute je; „všetky komponenty"
                // len doplní chýbajúce, aby ostal pôvodný stav dielov.
                if (debug.fullUpgrades) fit(slot, ItemCatalog.bestFor(slot))
                else if (!hasPart(slot)) fit(slot, ItemCatalog.basicFor(slot))
            }
        } else if (debug.fullBody) {
            // Samotná karoséria – na kontrolu polôh plechov bez toho, aby sa
            // zmenila jazda.
            BODY_SLOTS.forEach { slot -> fit(slot, ItemCatalog.basicFor(slot)) }
        }

        if (debug.fullFluids) {
            fuel = fuelCapacity
            oil = oilCapacity
            coolant = coolantCapacity
            fuelPurity = 1f
            fuelDieselFraction = if (requiredFuelKind == FuelKind.DIESEL) 1f else 0f
            oilPurity = 1f
            coolantPurity = 1f
            batteryCharge = batteryHoldCapacity
            temperature = 40f
        }
    }

    /**
     * SoC na štarte kopíruje zdravie batérie. Nový kus je plný; vrak nie je
     * na nule, kým hráč batériu naozaj nevybije.
     */
    private fun seededBatteryCharge(rng: SeededRandom): Float {
        val health = parts[ComponentSlot.BATTERY]?.health ?: return 0f
        val lo = (health * 0.72f).coerceAtLeast(0.08f)
        return rng.nextFloat(lo, health).coerceIn(0f, health)
    }

    /**
     * Na začiatku má hráč viac náplní, ale čistá kvapalina je vzácna.
     * Približne 80 % štartov dostane riedenú/zanesenú náplň pod 80 % čistoty.
     */
    private fun starterFluidPurity(rng: SeededRandom, dirtyMin: Float, dirtyMax: Float): Float =
        if (rng.chance(0.20f)) rng.nextFloat(0.84f, 0.98f)
        else rng.nextFloat(dirtyMin, dirtyMax)

    /** Priviaže auto na vozovku (príprava, stop, teleport). */
    fun snapToGround(groundY: Float, slope: Float = 0f) {
        val spring = (GameConfig.SUSP_SPRING * suspensionSpringMul()).coerceAtLeast(1f)
        val weight = physicsMass * GameConfig.AIR_GRAVITY
        val travel = suspTravel.coerceIn(0.2f, 0.75f)
        var rearSag = (weight * rearWeightBias / (spring * GameConfig.REAR_SPRING_MUL))
            .coerceAtLeast(0.02f)
        var frontSag = (weight * (1f - rearWeightBias) / (spring * GameConfig.FRONT_SPRING_MUL))
            .coerceAtLeast(0.02f)
        val wb = SedanSpec.wheelOffsetX.coerceAtLeast(0.25f)
        // Predný motor stlačí predné pružiny viac a auto aj v pokoji sedí
        // mierne nosom dolu. Náklad v kufri spraví presný opak.
        // Zdvih je strop náklonu: ojazdené pruženie nesmie z 1 m ponoru
        // vymyslieť 0.16 rad, ktorý vizuálne zdvihne zadok z cesty.
        val rawSagPitch = kotlin.math.asin(
            ((rearSag - frontSag) / (2f * wb)).coerceIn(-0.35f, 0.35f)
        )
        val sagPitch = rawSagPitch.coerceIn(-GameConfig.COAST_DIVE_PITCH_LIMIT, 0.12f)
        if (sagPitch != rawSagPitch) {
            // Pri orezanom náklone necháme obe nápravy v zdvihu, inak by
            // predok na doraze zdvihol zadok.
            rearSag = rearSag.coerceIn(0.02f, travel)
            frontSag = frontSag.coerceIn(0.02f, travel)
        }
        pitch = (kotlin.math.atan(slope) + sagPitch).coerceIn(-1.1f, 1.1f)
        pitchRate = 0f
        y = groundY + rideHeight - (rearSag + frontSag) * 0.5f
        vy = 0f
        wheelSpeed = speed
        grounded = true
        airborneFor = 0f
        wheelSlip = 0f
        wheelsLocked = false
        rearComp = rearSag
        frontComp = frontSag
        normalForce = weight
        rearNormalForce = weight * rearWeightBias
        frontNormalForce = weight * (1f - rearWeightBias)
        suspensionLoad = ((rearSag + frontSag) * 0.5f / 0.2f).coerceIn(0f, 1f)
    }

    /**
     * Posledná nepriestrelná kontrola kontaktu po horizontálnom pohybe auta.
     * [applyDrive] počíta pruženie z predikovaného kontaktu, no X sa zmení až
     * neskôr v tom istom kroku. Pri veľkej rýchlosti preto musí finálna poloha
     * ešte raz použiť terén presne pod novou polohou oboch náprav.
     */
    fun resolveRoadPenetration(
        rearGroundY: Float,
        frontGroundY: Float,
        rearGroundSlope: Float = 0f,
        frontGroundSlope: Float = 0f,
        /** Vozovka pod spodkom karosérie, offset je od stredu auta. */
        bodyGroundAtOffset: ((Float) -> Float)? = null
    ) {
        val wb = SedanSpec.wheelOffsetX
        val sinP = kotlin.math.sin(pitch)
        val rearBody = y - wb * sinP
        val frontBody = y + wb * sinP
        val floorOffset = rideHeight - suspTravel.coerceIn(0.2f, 0.75f) + 0.015f
        val rearPenetration = rearGroundY + floorOffset - rearBody
        val frontPenetration = frontGroundY + floorOffset - frontBody
        // Nápravy samy o sebe nestačia: na úzkom hrebeni môžu byť obe kolesá
        // vedľa kopca, zatiaľ čo cesta prejde priamo cez podlahu auta.
        var bodyPenetration = 0f
        bodyGroundAtOffset?.let { groundAt ->
            // Husté pevné body chránia aj dlhú kapotu pri brzdnom ponore.
            // Číselný cyklus zároveň nealokuje FloatArray v každom physics kroku.
            for (sample in -4..5) {
                val offset = wb * sample * 0.25f
                val bodyPoint = y + offset * sinP
                bodyPenetration = maxOf(
                    bodyPenetration,
                    groundAt(offset) + GameConfig.ROAD_CHASSIS_CLEARANCE - bodyPoint
                )
            }
        }
        val penetration = maxOf(rearPenetration, frontPenetration, bodyPenetration, 0f)
        if (penetration <= 0f) return

        y += penetration
        val supportVy = when {
            bodyPenetration >= rearPenetration && bodyPenetration >= frontPenetration ->
                ((rearGroundSlope + frontGroundSlope) * 0.5f) * speed
            rearPenetration >= frontPenetration -> rearGroundSlope * speed
            else -> frontGroundSlope * speed
        }.coerceAtMost(GameConfig.ROAD_CONTACT_MAX_UP_SPEED)
        if (vy < supportVy) {
            // Geometrické vysunutie karosérie musí byť okamžité, ale rýchlosť
            // nesmie v jednom kroku preskočiť z pádu na prudký let nahor.
            // Práve tento impulz bol viditeľný ako krátke „snapnutie“ auta.
            vy = minOf(supportVy, vy + GameConfig.ROAD_CONTACT_MAX_VY_CORRECTION)
        }
        pitchRate = pitchRate.coerceIn(-6f, 6f)
        // Mechanický doraz je tiež kontakt. Toto zároveň zabráni jednému
        // chybnému prepnutiu renderu do režimu letu.
        grounded = true
        airborneFor = 0f
    }

    /** Diely, bez ktorých auto nenaštartuje ani nepôjde. */
    fun missingEssentials(): List<ComponentSlot> =
        ESSENTIAL_SLOTS.filter { !parts.containsKey(it) }

    fun canStart(): Boolean {
        if (missingEssentials().isNotEmpty()) return false
        if (fuel < 0.5f || oil < 0.3f || coolant < 0.3f || batteryCharge < 0.2f) return false
        val eng = parts[ComponentSlot.ENGINE] ?: return false
        return eng.health > 0.15f && wrongFuelFraction < 0.70f
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
    fun refill(
        fluid: FluidType,
        amount: Float,
        purity: Float = 1f,
        fuelKind: FuelKind = FuelKind.PETROL
    ): Float {
        // Kvapalina patrí konkrétnemu dielu. Bez nádrže/chladiča sa nesmie
        // spotrebovať a pri neskoršej montáži potichu zmiznúť.
        val holder = when (fluid) {
            FluidType.FUEL -> ComponentSlot.FUEL_TANK
            FluidType.OIL -> ComponentSlot.ENGINE
            FluidType.COOLANT -> ComponentSlot.RADIATOR
            FluidType.BRAKE_FLUID -> null
        }
        if (holder != null && !hasPart(holder)) return 0f
        return when (fluid) {
        FluidType.FUEL -> {
            val add = amount.coerceAtMost(fuelCapacity - fuel)
            fuelPurity = blend(fuelPurity, fuel, purity, add)
            val addDiesel = if (fuelKind == FuelKind.DIESEL) 1f else 0f
            fuelDieselFraction = blend(fuelDieselFraction, fuel, addDiesel, add)
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
    }

    private fun blend(currentPurity: Float, currentVol: Float, addPurity: Float, addVol: Float): Float {
        val total = currentVol + addVol
        if (total <= 0.001f) return addPurity
        return ((currentPurity * currentVol + addPurity * addVol) / total).coerceIn(0f, 1f)
    }

    /**
     * Montáž dielu. Vyliatie kvapaliny pri výmene rieši [GameEngine] – potrebuje
     * ju totiž zachytiť do kanistra, a to auto samo nevie.
     */
    fun mount(slot: ComponentSlot, stack: ItemStack): MountedPart? {
        if (!stack.def.canMountTo(slot)) return null
        val previous = parts[slot]
        val paintIndex = if (stack.def.mountsTo?.takesBodyPaint == true) stack.paintIndex else -1
        parts[slot] = if (stack.def.hasDurability) {
            MountedPart(
                stack.defId, stack.condition, stack.health, paintIndex,
                injury = TireInjury.INFLATED
            )
        } else {
            MountedPart(stack.defId, ComponentCondition.NEW, 1f, paintIndex)
        }
        if (slot == ComponentSlot.BATTERY) {
            batteryCharge = stack.chargeOnMount()
        }
        return previous
    }

    /** Koľko a akej kvapaliny je práve v aute. */
    fun fluidLevel(fluid: FluidType): Float = when (fluid) {
        FluidType.FUEL -> fuel
        FluidType.OIL -> oil
        FluidType.COOLANT -> coolant
        FluidType.BRAKE_FLUID -> 0f
    }

    fun fluidPurity(fluid: FluidType): Float = when (fluid) {
        FluidType.FUEL -> fuelPurity
        FluidType.OIL -> oilPurity
        FluidType.COOLANT -> coolantPurity
        FluidType.BRAKE_FLUID -> 1f
    }

    /** Vypustí kvapalinu; čistota sa nastaví až tým, čo hráč naleje ako ďalšie. */
    fun drain(fluid: FluidType) {
        when (fluid) {
            FluidType.FUEL -> { fuel = 0f; fuelPurity = 1f; fuelDieselFraction = 0f }
            FluidType.OIL -> { oil = 0f; oilPurity = 1f }
            FluidType.COOLANT -> { coolant = 0f; coolantPurity = 1f }
            FluidType.BRAKE_FLUID -> Unit
        }
    }

    /** Vypustí najviac [litres] a vráti skutočne odstránený objem. */
    fun drainAmount(fluid: FluidType, litres: Float): Float {
        val before = fluidLevel(fluid)
        val removed = minOf(before, litres.coerceAtLeast(0f))
        val left = (before - removed).coerceAtLeast(0f)
        when (fluid) {
            FluidType.FUEL -> {
                fuel = left
                if (left <= 0.001f) {
                    fuelPurity = 1f
                    fuelDieselFraction = 0f
                }
            }
            FluidType.OIL -> {
                oil = left
                if (left <= 0.001f) oilPurity = 1f
            }
            FluidType.COOLANT -> {
                coolant = left
                if (left <= 0.001f) coolantPurity = 1f
            }
            FluidType.BRAKE_FLUID -> Unit
        }
        return removed
    }

    /**
     * Doplní opotrebenie dielu. Defekt ani ráfik tým nezmiznú —
     * spľasnutú gumu treba záplatou, roztrhnutú výmenou.
     */
    fun repair(slot: ComponentSlot, amount: Float = 0.25f) {
        val part = parts[slot] ?: return
        if (!part.def.hasDurability) return
        part.health = (part.health + amount).coerceAtMost(1f)
        part.condition = when {
            part.health >= 0.9f -> ComponentCondition.NEW
            part.health >= 0.65f -> ComponentCondition.USED
            part.health >= 0.35f -> ComponentCondition.DAMAGED
            else -> ComponentCondition.CRITICAL
        }
    }

    /**
     * Stav škrupiny 0..1 (1 = čistá). Špina aj hrdza ju znižujú;
     * prelakovanie v lakovačke ju vráti.
     */
    val bodyCondition: Float
        get() = (1f - maxOf(bodyDirt, bodyRust).coerceIn(0f, 1f))

    /**
     * Prestrieka škrupinu aj všetky namontované lakované plechy jednou
     * farbou. Svetlá, interiér a nosič ostávajú ako v predlohe.
     * Nový lak zmyje špinu a hrdzu zo škrupiny.
     */
    fun applyBodyPaint(paint: VehiclePaint) {
        val index = paint.ordinal
        bodyPaintIndex = index
        bodyDirt = 0f
        bodyRust = 0f
        parts.forEach { (slot, part) ->
            if (slot.takesBodyPaint) part.paintIndex = index
        }
    }

    /**
     * Bežné opotrebenie z jazdy – nič sa nekazí naraz, ale po desiatkach
     * kilometrov je údržba náplňou hry. Nemá to byť trest, len dôvod
     * zbierať náhradné diely aj keď je auto momentálne v poriadku.
     *
     * @param bumpMul hrboľatosť pod kolesami
     * @param brake koľko sa práve brzdí (0..1)
     */
    fun tickWear(dt: Float, bumpMul: Float, brake: Float, winterRoad: Boolean = false) {
        val v = kotlin.math.abs(speed)
        if (v < 0.2f && !engineRunning) return
        val load = (v / GameConfig.MAX_SPEED).coerceIn(0f, 1.2f)
        val massWear = 1f + excessGameplayMass * GameConfig.VEHICLE_WEIGHT_WEAR_GAIN
        // 160 km/h je možný krátky risk, nie bezplatná cestovná rýchlosť.
        val speedWear = 1f + ((v - GameConfig.HIGH_SPEED_WEAR_START)
            .coerceAtLeast(0f) * GameConfig.HIGH_SPEED_WEAR_GAIN)

        fun wear(slot: ComponentSlot, amount: Float) {
            val part = parts[slot] ?: return
            if (amount <= 0f) return
            part.health = (part.health - amount * dt).coerceAtLeast(0.02f)
            part.condition = when {
                part.health >= 0.9f -> ComponentCondition.NEW
                part.health >= 0.65f -> ComponentCondition.USED
                part.health >= 0.35f -> ComponentCondition.DAMAGED
                else -> ComponentCondition.CRITICAL
            }
        }

        // Valivé opotrebenie berie obe kolesá, preklz len to hnané –
        // pri RWD sa zodiera zadok, pri FWD predok, 4×4 delí záťaž.
        val chainStrain = if (hasChains) 1.35f else 1f
        val roll = GameConfig.WEAR_TIRES * load * speedWear * (1f + bumpMul * 0.8f) *
            chainStrain * massWear / tireBumpResist
        val spin = GameConfig.WEAR_TIRES * wheelSlip * 1.2f * speedWear / tireBumpResist
        val locked = if (wheelsLocked) spin else 0f
        // Reťaze pri rýchlosti trhajú gumy aj samy seba.
        if (hasChains) {
            val strain = ((v - GameConfig.CHAINS_MAX_SPEED * 0.7f) / 6f).coerceIn(0f, 1.5f)
            wear(ComponentSlot.CHAINS, GameConfig.WEAR_TIRES * (0.6f + strain * 3f))
        }
        fun tyreClimateWear(slot: ComponentSlot): Float =
            if (!winterRoad && parts[slot]?.defId == ItemCatalog.TIRE_WINTER.id) {
                GameConfig.WINTER_TIRE_DRY_WEAR_MULTIPLIER
            } else 1f
        wear(
            ComponentSlot.TIRE_FRONT,
            (roll + locked + if (drives(ComponentSlot.TIRE_FRONT)) spin else 0f) *
                tyreClimateWear(ComponentSlot.TIRE_FRONT)
        )
        wear(
            ComponentSlot.TIRE_REAR,
            (roll + locked + if (drives(ComponentSlot.TIRE_REAR)) spin else 0f) *
                tyreClimateWear(ComponentSlot.TIRE_REAR)
        )
        // Brzdy sa zodierajú len keď sa brzdí, o to rýchlejšie z rýchlosti.
        wear(ComponentSlot.BRAKES, GameConfig.WEAR_BRAKES * brake * (0.3f + load) * speedWear * massWear)
        // Pruženie: hrbole a dopady.
        wear(
            ComponentSlot.SUSPENSION,
            GameConfig.WEAR_SUSPENSION * (0.25f + load) * (1f + bumpMul * 1.4f) *
                speedWear * massWear / garageSpringMultiplier
        )
        if (engineRunning) {
            wear(ComponentSlot.ENGINE, GameConfig.WEAR_ENGINE_IDLE * (0.5f + load) * speedWear)
            wear(ComponentSlot.ALTERNATOR, GameConfig.WEAR_AUX * speedWear)
            wear(ComponentSlot.STARTER, GameConfig.WEAR_AUX * 0.4f * speedWear)
            wear(ComponentSlot.RADIATOR, GameConfig.WEAR_AUX * (0.6f + load) * speedWear)
            wear(
                ComponentSlot.DRIVETRAIN,
                GameConfig.WEAR_AUX * (0.4f + load * 1.2f) * speedWear * massWear
            )
        }
    }

    /** [charging] = false, keď nabíjanie blokuje udalosť (prasknutý remeň). */
    fun tickElectrics(
        dt: Float,
        headlightsOn: Boolean,
        headlightDrainMultiplier: Float = 1f,
        charging: Boolean = true,
        /** 0 = normálne počasie, 1 = mráz – batéria dáva menej. */
        cold: Float = 0f
    ): EndCause? {
        val hold = batteryHoldCapacity
        val output = if (charging) alternatorOutput else 0f
        // V mraze batéria dáva menej a samovoľne sa vybíja rýchlejšie.
        val coldDrain = 1f + cold * GameConfig.COLD_BATTERY_DRAIN
        if (hold <= 0.001f) {
            batteryCharge = 0f
            return null
        }
        if (cold > 0.05f) {
            batteryCharge = (batteryCharge - GameConfig.COLD_BATTERY_DRAIN * 0.004f * cold * dt)
                .coerceAtLeast(0f)
        }
        if (engineRunning) {
            // Najprv bežný odber auta. Až výkon, ktorý alternátoru zostane,
            // ide do batérie; 50 % diel teda nemôže pomaly vyrobiť plných 100 %.
            var chargeRate = 0f
            var drainRate = GameConfig.RUNNING_ELECTRICAL_DRAIN * coldDrain
            val ceiling = if (output > 0.005f) batteryChargeCeiling else 0f
            if (batteryCharge < ceiling) {
                val room = ((ceiling - batteryCharge) / 0.18f).coerceIn(0.12f, 1f)
                chargeRate = GameConfig.ALTERNATOR_CHARGE * output * room
            }
            if (headlightsOn) {
                val drain = if (output > 0.005f) {
                    GameConfig.HEADLIGHT_DRAIN_ON
                } else {
                    GameConfig.HEADLIGHT_DRAIN_OFF
                }
                drainRate += drain * coldDrain * headlightDrainMultiplier.coerceAtLeast(0f)
            }
            batteryCharge += (chargeRate - drainRate) * dt
            if (ceiling > 0.005f && batteryCharge > ceiling) {
                batteryCharge = maxOf(ceiling, batteryCharge - GameConfig.ALTERNATOR_CHARGE * dt)
            }
            batteryCharge = batteryCharge.coerceIn(0f, hold)
            if (batteryCharge <= 0.001f) return EndCause.BATTERY_DEAD
            return null
        }
        // Bez bežiaceho motora svetlá žerú batériu. Keď dôjde, ostáva
        // náhrada v batohu alebo núdzová reklama – nie tichý spawn.
        if (headlightsOn) {
            batteryCharge = (
                batteryCharge - GameConfig.HEADLIGHT_DRAIN_OFF * coldDrain *
                    headlightDrainMultiplier.coerceAtLeast(0f) * dt
                )
                .coerceAtLeast(0f)
            if (batteryCharge <= 0.001f) return EndCause.BATTERY_DEAD
        }
        batteryCharge = batteryCharge.coerceIn(0f, hold)
        return null
    }

    /**
     * @param slope sklon trate pod autom (+ = do kopca) – motor pod záťažou žerie viac.
     */
    fun tickDriving(
        dt: Float,
        throttle: Float,
        drainMul: Float,
        slope: Float = 0f,
        /** 0 = normálne počasie, 1 = poriadny mráz. */
        cold: Float = 0f,
        /** Cruise žerie viac než ručný plyn – násobí len termín plynu. */
        cruiseFuelMul: Float = 1f
    ): EndCause? {
        if (!engineRunning) return null

        val s = slope.coerceIn(-0.6f, 0.6f)
        val slopeMul = (1f + if (s > 0f) s * GameConfig.FUEL_SLOPE_UP else s * GameConfig.FUEL_SLOPE_DOWN)
            .coerceIn(0.35f, 2.8f)
        val qualityMul = 1f + (1f - fuelPurity.coerceIn(0f, 1f)) * 0.5f
        // Studený motor beží bohato – v mraze sa spotreba dvíha, kým sa nezohreje.
        val warmedUp = MathX.smoothstep(30f, 70f, temperature)
        val coldMul = 1f + cold * GameConfig.COLD_FUEL_PENALTY * (1f - warmedUp)
        val massFuelMul = 1f + excessGameplayMass * GameConfig.VEHICLE_WEIGHT_FUEL_GAIN
        val cruiseMul = cruiseFuelMul.coerceAtLeast(1f)
        val burn = (GameConfig.FUEL_IDLE +
            GameConfig.FUEL_THROTTLE * throttle * cruiseMul * slopeMul * massFuelMul) *
            fuelUseMul * drainMul * qualityMul * coldMul
        fuel = (fuel - burn * dt).coerceAtLeast(0f)
        if (fuel <= 0f) {
            // Motor zhasne, ale auto sa nezastaví na fleku – dojazd zotrvačnosťou
            // a z kopca je súčasť jazdy. Rýchlosť si prevezme fyzika.
            engineRunning = false
            return EndCause.OUT_OF_FUEL
        }

        oil = (oil - GameConfig.OIL_DRAIN * (0.35f + throttle * 0.65f) * dt).coerceAtLeast(0f)
        coolant = (coolant - GameConfig.COOLANT_DRAIN * (0.3f + throttle * 0.7f) * dt).coerceAtLeast(0f)

        // Teplo: generuje plyn + kopec; odvádza chladič × kvapalina × čistota.
        // Samotná kvapalina bez chladiča motor neuchladí.
        val hasRadiator = hasPart(ComponentSlot.RADIATOR)
        val rad = radiatorMul.coerceIn(0.15f, 1.4f)
        val coolOk = coolantRatio.coerceIn(0f, 1f)
        val coolPure = coolantPurity.coerceIn(0f, 1f)
        val climbLoad = (slopeMul - 1f).coerceAtLeast(0f)
        val heatGen = 7f * throttle + 8f * climbLoad
        // Ojazdený chladič má hriať, nie zabíjať – štartovací kus má zdravie
        // od 0.30, čo pri koeficiente 16 znamenalo trvalé prehrievanie.
        val radPenalty = if (hasRadiator) (1f - rad) * 9f else 26f
        // Chladič bez kvapaliny skoro nič neodvedie. Stará pokuta 18 °C
        // nechala prázdny okruh pod prahom prehriatia aj na plný plyn.
        val fluidFlow = MathX.smoothstep(0.03f, GameConfig.COOLANT_EFFECTIVE_RATIO, coolOk)
        val coolPenalty = (1f - fluidFlow) * GameConfig.EMPTY_COOLANT_HEAT +
            GameConfig.BAD_COOLANT_HEAT * (1f - coolPure) * fluidFlow
        // V mraze sa motor na prevádzkovú teplotu nedostane tak ľahko.
        val heatTarget = GameConfig.NORMAL_TEMP + heatGen + radPenalty + coolPenalty -
            cold * GameConfig.COLD_TEMP_DROP
        temperature = MathX.lerp(temperature, heatTarget.coerceAtMost(145f), dt * GameConfig.TEMP_RESPONSE)

        val engine = parts[ComponentSlot.ENGINE]
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

        if (oil < 0.25f) wear(GameConfig.ENGINE_WEAR_LOW_OIL, WearCause.LOW_OIL)
        // Voda namiesto chladiacej kvapaliny v mraze zamrzne a roztrhne motor.
        if (cold > 0.4f && coolantPurity < 0.55f && coolant > 0.2f) {
            val frozen = ((0.55f - coolantPurity) / 0.55f).coerceIn(0f, 1f) * cold
            wear(GameConfig.ENGINE_WEAR_FROZEN * frozen, WearCause.FROZEN_COOLANT)
        }
        val badOil = ((GameConfig.PURITY_SAFE_OIL - oilPurity) / GameConfig.PURITY_SAFE_OIL)
            .coerceIn(0f, 1f)
        val badFuel = ((GameConfig.PURITY_SAFE_FUEL - fuelPurity) / GameConfig.PURITY_SAFE_FUEL)
            .coerceIn(0f, 1f)
        wear(GameConfig.ENGINE_WEAR_BAD_OIL * badOil, WearCause.DIRTY_OIL)
        wear(
            GameConfig.ENGINE_WEAR_BAD_FUEL * badFuel * (0.3f + throttle * 0.7f),
            WearCause.DIRTY_FUEL
        )
        wear(
            GameConfig.ENGINE_WEAR_WRONG_FUEL * wrongFuelFraction * (0.35f + throttle * 0.65f),
            WearCause.WRONG_FUEL
        )
        val dryLoop = fluidFlow < 0.22f
        if (dryLoop) {
            val overCause = when {
                !hasPart(ComponentSlot.RADIATOR) -> WearCause.NO_RADIATOR
                radiatorMul < 0.45f -> WearCause.BAD_RADIATOR
                else -> WearCause.LOW_COOLANT
            }
            if (temperature > GameConfig.DRY_COOLANT_WEAR_START) {
                val span = (GameConfig.DRY_COOLANT_CUTOUT - GameConfig.DRY_COOLANT_WEAR_START)
                    .coerceAtLeast(1f)
                val cook = ((temperature - GameConfig.DRY_COOLANT_WEAR_START) / span)
                    .coerceIn(0f, 1f)
                wear(GameConfig.ENGINE_WEAR_DRY_COOLANT * (0.40f + 0.60f * cook), overCause)
            }
            if (temperature > GameConfig.DRY_COOLANT_CUTOUT) {
                engineRunning = false
                wearCause = overCause
                return EndCause.OVERHEAT
            }
        } else if (temperature > GameConfig.OVERHEAT_THRESHOLD) {
            val over = ((temperature - GameConfig.OVERHEAT_THRESHOLD) / 30f).coerceIn(0f, 1f)
            val overCause = when {
                !hasPart(ComponentSlot.RADIATOR) -> WearCause.NO_RADIATOR
                radiatorMul < 0.45f -> WearCause.BAD_RADIATOR
                coolantRatio < 0.25f -> WearCause.LOW_COOLANT
                else -> WearCause.OVERHEAT
            }
            wear(GameConfig.ENGINE_WEAR_OVERHEAT * over, overCause)
            if (temperature > GameConfig.OVERHEAT_THRESHOLD + 28f && over > 0.85f) {
                engineRunning = false
                wearCause = overCause
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

    /** Chladenie pri zhasnutom motore – stojace auto s funkčným chladičom vychladne. */
    fun idleCool(dt: Float) {
        if (engineRunning) return
        val ambient = GameConfig.NORMAL_TEMP * 0.55f
        val coolingWorks = hasPart(ComponentSlot.RADIATOR) &&
            radiatorMul >= 0.45f &&
            coolant >= 0.3f
        val rate = if (coolingWorks) 0.18f else 0.035f
        temperature = MathX.lerp(temperature, ambient, dt * rate)
    }

    /**
     * Spätný chod: motor beží, brzda zo stoja. Zotrvačnosť dozadu
     * s vypnutým motorom cúvanie nie je – zadné biele svetlo vtedy nesvieti.
     */
    fun isReverseGear(brake: Float, throttle: Float): Boolean =
        engineRunning && fuel > 0f && brake > 0.05f &&
            throttle < 0.05f &&
            speed <= GameConfig.REVERSE_ENGAGE_SPEED &&
            grounded &&
            reverseArmS >= GameConfig.REVERSE_ARM_DELAY

    /**
     * Silová fyzika: pruženie → normálová sila → μ·N grip, samostatné kolesá,
     * gravitácia po svahu, skoky. Na rovinke (malý sklon) grip neblokuje rozjazd.
     *
     * @param rearGroundY výška trate pod zadným kolesom
     * @param frontGroundY výška trate pod predným kolesom
     */
    fun applyDrive(
        dt: Float,
        throttle: Float,
        brake: Float,
        rearGroundY: Float,
        frontGroundY: Float,
        bumpMul: Float,
        surface: RoadSurface = RoadSurface.ASPHALT,
        /** Priľnavosť samotnej vozovky (asfalt 1.0, hlina/piesok menej). */
        pavingGrip: Float = 1f,
        /** true = mrznúci povrch, rozhoduje zimná výbava. */
        winter: Boolean = false,
        /** Lokálny sklon presne pod nápravou; null zachová kompatibilitu testov. */
        rearGroundSlope: Float? = null,
        frontGroundSlope: Float? = null,
        /** Zrýchlenie vetra pozdĺž cesty; + = zadný vietor, - = protivietor. */
        windAcceleration: Float = 0f
    ) {
        landingImpact = 0f
        val wb = SedanSpec.wheelOffsetX
        val wheelbase = (wb * 2f).coerceAtLeast(0.5f)
        val slope = (frontGroundY - rearGroundY) / wheelbase
        val sinT = slope / kotlin.math.sqrt(1f + slope * slope)
        val mass = physicsMass
        val referenceMass = GameConfig.CAR_MASS
        val g = GameConfig.AIR_GRAVITY
        val springMul = suspensionSpringMul()
        val dampMul = suspensionDampMul()
        val springK = GameConfig.SUSP_SPRING * springMul
        val qualityDamp = 1f + GameConfig.SUSP_QUALITY_DAMP_BONUS *
            (springMul - 0.9f).coerceAtLeast(0f)
        val dampC = GameConfig.SUSP_DAMPER * (0.55f + 0.45f * dampMul) * qualityDamp
        val ride = rideHeight
        val travel = suspTravel.coerceIn(0.2f, 0.75f)
        val highSpeedStability = MathX.smoothstep(24f, 38f, kotlin.math.abs(speed))
        val rearSlopeNow = rearGroundSlope ?: slope
        val frontSlopeNow = frontGroundSlope ?: slope
        // Krátky vypuklý hrbol má zadnú nápravu ešte v stúpaní a prednú už
        // v klesaní. Rozdiel sklonov je veľký; široký skok má naopak malú
        // krivosť. Takto absorbujeme iba ostrú špičku, nie normálny odraz rampy.
        val sharpCrest = MathX.smoothstep(
            GameConfig.SHARP_CREST_SLOPE_DELTA_START,
            GameConfig.SHARP_CREST_SLOPE_DELTA_FULL,
            (rearSlopeNow - frontSlopeNow).coerceAtLeast(0f)
        ) * highSpeedStability
        val crestForceMul = 1f - GameConfig.SHARP_CREST_FORCE_RELIEF * sharpCrest

        // --- Pruženie (predná / zadná náprava) ---
        val sinP = kotlin.math.sin(pitch)
        // Nose-up (+pitch): predok vyššie, zadok nižšie.
        val rearBody = y - wb * sinP
        val frontBody = y + wb * sinP
        val rearTarget = rearGroundY + ride
        val frontTarget = frontGroundY + ride
        // Relatívna rýchlosť bodu karosérie k zemi (zem sa hýbe so sklonom·speed).
        val rearGroundVy = rearSlopeNow * speed
        val frontGroundVy = frontSlopeNow * speed
        val cosP = kotlin.math.cos(pitch)
        val rearRelVy = vy - wb * pitchRate * cosP - rearGroundVy
        val frontRelVy = vy + wb * pitchRate * cosP - frontGroundVy

        fun axleForce(compRaw: Float, relVy: Float, springMul: Float): Pair<Float, Float> {
            val comp = compRaw.coerceIn(0f, travel)
            if (compRaw <= 0f) return 0f to 0f
            // Tlmiče majú high-speed blow-off. Bez neho veľký rozdiel sklonov
            // na hrebeni vytvoril jednorámový impulz, ktorý vystrelil zadok.
            val dampVelocity = relVy.coerceIn(
                -GameConfig.SUSP_DAMPER_VELOCITY_LIMIT,
                GameConfig.SUSP_DAMPER_VELOCITY_LIMIT
            )
            val axleSpring = springK * springMul
            var n = axleSpring * comp - dampC * kotlin.math.sqrt(springMul) * dampVelocity
            // Bottom-out
            if (compRaw > travel) {
                n += axleSpring * 2.5f * (compRaw - travel)
            }
            val maxAxleForce = mass * g * GameConfig.SUSP_AXLE_FORCE_LIMIT
            return n.coerceIn(0f, maxAxleForce) to comp
        }

        val (rearNRaw, rearC) = axleForce(rearTarget - rearBody, rearRelVy, GameConfig.REAR_SPRING_MUL)
        val (frontNRaw, frontC) = axleForce(frontTarget - frontBody, frontRelVy, GameConfig.FRONT_SPRING_MUL)
        val rearN = rearNRaw * crestForceMul
        val frontN = frontNRaw * crestForceMul
        rearComp = rearC
        frontComp = frontC
        val totalN = rearN + frontN
        rearNormalForce = rearN
        frontNormalForce = frontN
        normalForce = totalN
        suspensionLoad = ((rearC + frontC) * 0.5f / 0.2f).coerceIn(0f, 1f)
        val wasGrounded = grounded
        grounded = totalN > mass * g * 0.04f || rearC > 0.01f || frontC > 0.01f
        airborneFor = if (grounded) 0f else airborneFor + dt
        val armingReverse = engineRunning && fuel > 0f && brake > 0.05f &&
            throttle < 0.05f && grounded &&
            // speed <= prah: stojka aj pokračujúce cúvanie (záporná rýchlosť).
            speed <= GameConfig.REVERSE_ENGAGE_SPEED
        reverseArmS = if (armingReverse) {
            (reverseArmS + dt).coerceAtMost(GameConfig.REVERSE_ARM_DELAY + 0.5f)
        } else {
            0f
        }
        val reverseRequested = armingReverse && reverseArmS >= GameConfig.REVERSE_ARM_DELAY
        val rearwardInput = brake * if (reverseRequested) GameConfig.REVERSE_ACCEL / GameConfig.ACCEL else 1f

        // Vertikálna dynamika + náklon. S rýchlosťou rastie iba jemný
        // aerodynamický prítlak, nie umelý limiter: auto môže ďalej zrýchľovať,
        // no na drobných vlnách sa menej odľahčí a zadok nevystrelí.
        val aeroDownforce = (
            speed * speed * GameConfig.HIGH_SPEED_DOWNFORCE_COEFF * highSpeedStability
            ).coerceAtMost(GameConfig.HIGH_SPEED_DOWNFORCE_MAX)
        val fy = totalN - mass * (g + aeroDownforce)
        vy += (fy / mass) * dt
        y += vy * dt
        // Sily pruženia pôsobia okolo stredu karosérie, gravitácia však cez
        // skutočné ťažisko. Predný motor preto zaťaží predok namiesto toho,
        // aby RWD auto dostalo umelú väčšinu váhy dozadu.
        val cgGravityTorque = if (grounded) {
            -centerOfMassOffsetM * mass * g * kotlin.math.cos(pitch)
        } else 0f
        // Jedna náprava vo vzduchu by inak celým rozdielom síl preklopila nos.
        val oneWheel = (rearC > 0.01f) != (frontC > 0.01f)
        // Pri brzde chceme plný vracajúci moment z prednej nápravy – one-wheel
        // rez pri skoku by tu nechal zadok visieť vo vzduchu.
        val axlePitchMul = if (oneWheel && rearwardInput < 0.25f) {
            GameConfig.ONE_WHEEL_PITCH_TORQUE
        } else {
            1f
        }
        val groundPitchTorque = if (!grounded) {
            0f
        } else if (reverseRequested) {
            rearwardInput * GameConfig.GROUND_REVERSE_PITCH_TORQUE
        } else {
            throttle * GameConfig.GROUND_PITCH_TORQUE -
                rearwardInput * GameConfig.GROUND_BRAKE_DIVE_TORQUE
        }
        val bothAxlesDown = rearC > 0.01f && frontC > 0.01f
        val roadPitchNow = kotlin.math.atan(slope)
        // Zadok sadíme aj bez brzdy, ale len keď nos už klesol pod cestu
        // (z kopca / ojazdené). Na hrboli by to pridalo extra pitch.
        val rearPlantTorque = if (grounded && !reverseRequested) {
            val unload = ((0.05f - rearC) / 0.05f).coerceIn(0f, 1f)
            val noseDown = pitch < roadPitchNow - 0.015f
            val allowPlant = rearwardInput > 0.25f ||
                kotlin.math.abs(speed) < 2.5f ||
                (bothAxlesDown && noseDown)
            if (allowPlant && unload > 0f) {
                val plantMul = if (rearwardInput > 0.25f) 0.55f else 0.40f
                unload * mass * g * wb * plantMul
            } else {
                0f
            }
        } else {
            0f
        }
        val slopeRestoreTorque = if (grounded && bothAxlesDown && pitch < roadPitchNow - 0.02f) {
            (roadPitchNow - pitch) * GameConfig.PITCH_SLOPE_RESTORE
        } else {
            0f
        }
        val torque = (frontN - rearN) * wb * axlePitchMul + cgGravityTorque +
            groundPitchTorque + rearPlantTorque + slopeRestoreTorque +
            (if (!grounded) (throttle - brake) * GameConfig.AIR_PITCH_TORQUE else 0f)
        val pitchInertia = GameConfig.PITCH_INERTIA * (mass / GameConfig.CAR_MASS)
        pitchRate += (torque / pitchInertia.coerceAtLeast(0.5f)) * dt
        if (!wasGrounded && grounded) {
            pitchRate *= GameConfig.LANDING_PITCH_BLEED
        }
        if (grounded) {
            val targetPitch = kotlin.math.atan(slope)
            pitchRate += (targetPitch - pitch) * GameConfig.PITCH_SLOPE_TRACK * dt
            val pitchDamping = 2.0f + GameConfig.HIGH_SPEED_PITCH_DAMPING * highSpeedStability
            pitchRate *= (1f - (pitchDamping * dt).coerceIn(0f, 0.7f))
        } else {
            pitchRate *= (1f - (GameConfig.AIR_PITCH_DAMPING * dt).coerceIn(0f, 0.55f))
        }
        pitch = (pitch + pitchRate * dt).coerceIn(-1.1f, 1.1f)
        if (grounded) {
            // Nose-down vždy voči ceste, nielen pri brzde. Inak z kopca
            // voľný jazd ide na 0.16 rad a brzda to „opraví“ tesným clampom.
            val roadPitch = kotlin.math.atan(slope)
            val diveLimit = when {
                reverseRequested -> GameConfig.REVERSE_PITCH_LIMIT
                rearwardInput > 0.25f -> {
                    val keepRearDown = ((rearC + 0.04f) / wb.coerceAtLeast(0.5f))
                        .coerceAtLeast(0.012f)
                    minOf(GameConfig.BRAKE_DIVE_PITCH_LIMIT, keepRearDown)
                }
                // Skok / jedna náprava: voľnejší limit, aby ONE_WHEEL ostal.
                oneWheel -> 0.16f
                else -> GameConfig.COAST_DIVE_PITCH_LIMIT
            }
            val limitedPitch = pitch.coerceIn(
                roadPitch - diveLimit,
                roadPitch + 0.20f
            )
            if (limitedPitch != pitch) pitchRate *= 0.35f
            pitch = limitedPitch
        }

        // Kontinuálna ochrana proti preniknutiu. Pri vysokej rýchlosti sa vie
        // tenký hrbol medzi dvoma krokmi dostať hlbšie než celý zdvih pruženia;
        // samotná sila tlmiča už potom karosériu včas nevytlačí a sprite
        // preskočí pod cestu. Korigujeme iba presah za mechanický doraz.
        resolveRoadPenetration(
            rearGroundY,
            frontGroundY,
            rearGroundSlope ?: slope,
            frontGroundSlope ?: slope
        )

        // Dopad
        if (!wasGrounded && grounded && -vy > GameConfig.LANDING_IMPACT) {
            val hit = ((-vy - GameConfig.LANDING_IMPACT) / 12f).coerceIn(0f, 1f)
            landingImpact = hit
            parts[ComponentSlot.SUSPENSION]?.let {
                it.health = (it.health - GameConfig.LANDING_WEAR * hit).coerceAtLeast(0.05f)
            }
            listOf(ComponentSlot.TIRE_FRONT, ComponentSlot.TIRE_REAR).forEach { slot ->
                parts[slot]?.let {
                    it.health = (it.health - GameConfig.LANDING_WEAR * 0.55f * hit).coerceAtLeast(0.05f)
                }
            }
        }

        // --- Pozdĺžna fyzika ---
        // Bahno / piesok / voda uberajú grip a pridávajú valivý odpor.
        val icy = winter || surface == RoadSurface.ICE || surface == RoadSurface.SLUSH
        // Na snehu platí snowGrip nápravy (nie suchý dezén). WinterMul tu
        // už nie je – predtým sa násobil suchý grip × 0.45 a šport preletel.
        // Na suchu reťaze naopak prekážajú.
        val winterMul = if (icy) 1f else tarmacPenalty
        // Spodok 0.32, aby SNOW (0.38) ostal pod packed (0.48). Staré 0.5
        // dvíhalo sypký sneh na úroveň utlačeného.
        // Ľad × snehová dlažba (0.34×0.38) zomlela aj zimnú+reťaze. Reťaze
        // dvíhajú ľad a podlahu dláždenia; letná ostáva takmer bez šance.
        val pavingClamped = pavingGrip.coerceIn(0.32f, 1.1f)
        val surfaceMul = when {
            surface != RoadSurface.ICE -> surface.gripMul
            hasChains -> GameConfig.ICE_CHAINS_SURFACE_MUL
            snowPowerScale() > 0.4f -> GameConfig.ICE_WINTER_SURFACE_MUL
            else -> surface.gripMul
        }
        val pavingMul = if (surface == RoadSurface.ICE && hasChains) {
            pavingClamped.coerceAtLeast(GameConfig.ICE_CHAINS_PAVING_FLOOR)
        } else pavingClamped
        val envMul = surfaceMul * pavingMul * winterMul
        val frontMu = axleSurfaceMu(
            ComponentSlot.TIRE_FRONT, bumpMul, speed, winterSurface = icy
        ) * envMul
        val rearMu = axleSurfaceMu(
            ComponentSlot.TIRE_REAR, bumpMul, speed, winterSurface = icy
        ) * envMul
        // Weight transfer: plyn odľahčí predok / naloží zadek (RWD grip).
        val fwd = driveLayout == DriveLayout.FWD
        val transferMul = if (fwd) GameConfig.FWD_TRANSFER_MUL else 1f
        // Cúvanie nesmie ísť ako brzdový dive – vyložený zadok na hrbolci
        // RWD auto na mieste nechalo. Statické rozloženie + obe nápravy.
        val transfer = if (reverseRequested) {
            0f
        } else {
            (throttle - rearwardInput) * GameConfig.WEIGHT_TRANSFER * transferMul
        }
        // Stúpanie tlačí váhu dozadu – RWD ide do kopca lepšie, FWD horšie,
        // ale nie tak, aby sa s FWD nedalo vyjsť nič.
        val slopeMul = if (fwd) GameConfig.FWD_SLOPE_MUL else 1f
        val slopeShift = if (reverseRequested) {
            (sinT * GameConfig.SLOPE_TRANSFER * slopeMul * 0.35f).coerceIn(-0.08f, 0.08f)
        } else {
            (sinT * GameConfig.SLOPE_TRANSFER * slopeMul).coerceIn(-0.20f, 0.20f)
        }
        val staticRear = rearWeightBias
        val rearShare = (staticRear + transfer + slopeShift).coerceIn(0.22f, 0.82f)
        val frontShare = 1f - rearShare
        // Koľko normálovej sily môže prenášať ťah podľa pohonu.
        val maxDriveF = if (!grounded) {
            0f
        } else if (reverseRequested) {
            (frontMu * totalN * frontShare + rearMu * totalN * rearShare) *
                GameConfig.REVERSE_DRIVE_GRIP
        } else {
            when (driveLayout) {
                DriveLayout.AWD ->
                    (frontMu * totalN * frontShare + rearMu * totalN * rearShare) * 0.95f
                DriveLayout.FWD -> frontMu * totalN * frontShare
                DriveLayout.RWD -> rearMu * totalN * rearShare
            }
        }
        val maxBrakeF = if (grounded && totalN > 0.05f) {
            // Brzdy nesmú zmiznúť, keď ponor odľahčí zadok. Podiel náprav
            // ostane čitateľný, ale ani jedna nesmie spadnúť na nulu.
            val frontShareN = (frontN / totalN).coerceIn(0.32f, 0.68f)
            val rearShareN = 1f - frontShareN
            val tractionLimit = (frontMu * frontShareN + rearMu * rearShareN) * totalN *
                brakeMul.coerceIn(0.72f, 1.55f)
            // Hydraulika má konečnú silu: s rovnakými brzdami naložené auto
            // brzdí dlhšie. Garážový balík tento hardvérový strop reálne zvýši.
            val hardwareLimit = GameConfig.BRAKE * GameConfig.BRAKE_HARDWARE_MUL * referenceMass *
                brakeMul.coerceIn(0.72f, 1.55f)
            // Garážový balík zvyšuje skutočný brzdný strop (hydraulika + doštičky).
            minOf(tractionLimit, hardwareLimit) * garageBrakeMultiplier
        } else 0f
        val rimBlend = drivenRimBlend()
        val snowScale = if (icy) snowPowerScale() else 1f
        val chainedWinter = icy && hasChains
        val dryOverdrive = when {
            chainedWinter -> GameConfig.CHAINS_SNOW_OVERDRIVE
            icy -> MathX.lerp(
                GameConfig.SNOW_GRIP_OVERDRIVE,
                GameConfig.GRIP_OVERDRIVE * 0.50f,
                snowScale
            )
            else -> GameConfig.GRIP_OVERDRIVE
        }
        val overdrive = MathX.lerp(
            dryOverdrive, GameConfig.RIM_GRIP_OVERDRIVE, rimBlend
        )
        val drySlack = when {
            chainedWinter -> GameConfig.CHAINS_TRACTION_SLACK
            icy -> MathX.lerp(
                GameConfig.SNOW_TRACTION_SLACK,
                GameConfig.TRACTION_SLACK * 0.85f,
                snowScale
            )
            else -> GameConfig.TRACTION_SLACK
        }
        val tractionSlack = MathX.lerp(
            drySlack, GameConfig.RIM_TRACTION_SLACK, rimBlend
        )
        val slipSpin = MathX.lerp(
            GameConfig.SLIP_SPIN_BONUS, GameConfig.RIM_SLIP_SPIN_BONUS, rimBlend
        ) * (if (chainedWinter) GameConfig.CHAINS_SLIP_SPIN_MUL else 1f)

        // Široký rozsah dovolí, aby bol rozdiel medzi základným motorom a
        // najsilnejšou verziou cítiť aj po rozbehu, nielen pri štarte.
        val powerFactor = (powerHp / 80f).coerceIn(0.5f, 3.2f)
        val launch = 1f + 0.55f * (1f - (kotlin.math.abs(speed) / 7f).coerceIn(0f, 1f))
        var driveDemand = 0f
        var brakeDemand = 0f
        var reversing = false

        if (engineRunning && fuel > 0f && throttle > 0.05f) {
            // Keď už kolesá preklzávajú, vodič uberie – ale nikdy nie tak, aby
            // auto stratilo ťah. Bez stropu vznikla špirála: preklz → menej
            // výkonu → ešte pomalšie → do kopca sa nedalo vyjsť vôbec.
            // Pri rozjazde zo stojky sa nešetrí vôbec.
            val rolling = MathX.smoothstep(0.5f, 3.0f, kotlin.math.abs(speed))
            val easeFloor = if (icy) {
                MathX.lerp(
                    GameConfig.SNOW_TRACTION_EASE_FLOOR,
                    GameConfig.TRACTION_EASE_FLOOR,
                    snowScale
                )
            } else GameConfig.TRACTION_EASE_FLOOR
            val ease = (1f - GameConfig.TRACTION_EASE_OFF * wheelSlip * rolling)
                .coerceAtLeast(easeFloor)
            // Motor má danú silu; ťažší kufor teda zrýchlenie zníži. Predtým
            // sa sila násobila aktuálnou hmotnosťou a náklad by bol zadarmo.
            driveDemand = GameConfig.ACCEL * powerFactor * garagePowerMultiplier * throttle *
                launch * referenceMass * ease
        }
        if (reverseRequested) {
            reversing = true
            val reversePower = powerFactor.coerceIn(0.65f, 1.35f)
            val reverseLaunch = 1f + 0.22f * (1f - (kotlin.math.abs(speed) / 6f).coerceIn(0f, 1f))
            val demand = GameConfig.REVERSE_ACCEL * brake * reversePower * garagePowerMultiplier *
                reverseLaunch * referenceMass
            // Meter the reverse gear on firm ground. Poor tyres still limit
            // acceleration, while mud, ice and sand can genuinely spin them.
            val firmRoad = surface == RoadSurface.ASPHALT && pavingGrip >= 0.9f && !winter
            driveDemand = -if (firmRoad) minOf(demand, maxDriveF * 0.96f) else demand
        } else if (brake > 0.05f && brake >= throttle) {
            // Žiadosť vodiča je nad hardvérovým stropom – inak by garážové
            // brzdy pri silných gumách nebolo cítiť (dopyt < strop).
            brakeDemand = GameConfig.BRAKE * 1.35f * brake * referenceMass
        }

        // Gravitácia pozdĺž trate + odpor
        val gradeForce = -mass * g * sinT
        val rollDrag = (surface.rollDrag + tireInjuryDrag) *
            (0.35f + 0.65f * (kotlin.math.abs(speed) / GameConfig.MAX_SPEED).coerceIn(0f, 1f))
        val drag = -kotlin.math.sign(speed) * (
            (GameConfig.COAST_DRAG + rollDrag) * mass *
                (if (kotlin.math.abs(speed) > 0.15f) 1f else 0f) +
                GameConfig.AERO_DRAG * (1f + extraDrag) * speed * speed * referenceMass
            )

        // Vietor mení rýchlosť len vtedy, keď sa auto už hýbe. Neštartuje ani
        // nepretáča stojace auto; pri cúvaní pôsobí opačne proti smeru pohybu.
        val windForce = if (kotlin.math.abs(speed) > 0.2f) {
            windAcceleration.coerceIn(-1.2f, 1.2f) * kotlin.math.sign(speed) * mass
        } else 0f
        var longForce = gradeForce + drag + windForce
        var slipTarget = 0f
        wheelsLocked = false

        if (!grounded) {
            // Vo vzduchu: slabý „air control“ ťah, kolesá voľne točia.
            longForce += driveDemand * 0.12f
            wheelSpeed = MathX.damp(
                wheelSpeed,
                speed * 0.3f + throttle * 8f,
                GameConfig.WHEEL_RESP * 0.45f,
                dt
            )
            slipTarget = (kotlin.math.abs(wheelSpeed - speed) / 12f).coerceIn(0f, 1f)
        } else if (reversing) {
            val f = driveDemand.coerceIn(-maxDriveF, maxDriveF)
            longForce += f
            val ratio = if (maxDriveF < 0.01f) 99f else kotlin.math.abs(driveDemand) / maxDriveF
            slipTarget = ((ratio - tractionSlack) / 2.2f).coerceIn(0f, 1f)
            wheelSpeed = MathX.damp(wheelSpeed,
                speed - slipTarget * slipSpin, GameConfig.WHEEL_RESP, dt)
        } else if (brakeDemand > 0.05f) {
            val brakeF = brakeDemand.coerceAtMost(maxBrakeF)
            if (kotlin.math.abs(speed) <= GameConfig.REVERSE_ENGAGE_SPEED) {
                // Parkovanie na svahu: brzda musí vyrušiť gravitáciu, inak
                // auto pri rýchlosti ~0 dostane len gradeForce a znovu sa rozbehne.
                longForce -= gradeForce.coerceIn(-brakeF, brakeF)
            } else if (speed > 0f) {
                longForce -= brakeF
            } else {
                longForce += brakeF
            }
            // Zamykanie: brzda väčšia než grip.
            if (brakeDemand > maxBrakeF * 1.05f && kotlin.math.abs(speed) > 2.5f) {
                wheelsLocked = true
                wheelSpeed = MathX.damp(wheelSpeed, 0f, 30f, dt)
                slipTarget = ((brakeDemand - maxBrakeF) / (brakeDemand + 1f)).coerceIn(0.35f, 1f)
            } else {
                wheelSpeed = MathX.damp(wheelSpeed, speed, GameConfig.WHEEL_RESP, dt)
                slipTarget = 0f
            }
        } else if (driveDemand > 0.05f) {
            // Kolesá chcú ísť rýchlejšie o ťah / grip. Guma je strop, ale nie
            // úplný – zlomok prebytku sa na cestu dostane. Tvrdý orez robil
            // z motora kulisu: nad hranicou gripu boli slabé aj silné motory rovnaké.
            val traction = if (driveDemand <= maxDriveF) driveDemand
            else maxDriveF + (driveDemand - maxDriveF) * overdrive
            longForce += traction
            // Preklz meriame pomerom „koľko ťahu vs. koľko gumy unesú“.
            // Do TRACTION_SLACK sa auto ešte chytí (vodič dávkuje plyn),
            // nad tým sa začne pretáčať – a na bahne to príde skôr než na asfalte.
            val ratio = if (maxDriveF < 0.01f) 99f else driveDemand / maxDriveF
            slipTarget = ((ratio - tractionSlack) / 2.2f).coerceIn(0f, 1f)
            val spin = speed + slipTarget * slipSpin
            wheelSpeed = MathX.damp(wheelSpeed, spin, GameConfig.WHEEL_RESP, dt)
        } else {
            wheelSpeed = MathX.damp(wheelSpeed, speed, GameConfig.WHEEL_RESP, dt)
            slipTarget = 0f
        }

        // Reťaze na zime zrežú hlásený preklz – kolesá sa chytia, nie točia naprázdno.
        if (chainedWinter) slipTarget *= GameConfig.CHAINS_SLIP_VISUAL

        // Slip sa dorovná rýchlo – na vrchole kopca nesmie „visieť“ starý preklz.
        val slipRate = when {
            chainedWinter && slipTarget < wheelSlip -> 28f
            chainedWinter -> 14f
            slipTarget < wheelSlip -> 16f
            else -> 10f
        }
        wheelSlip = MathX.damp(wheelSlip, slipTarget.coerceIn(0f, 1f), slipRate, dt)

        // Poškodená guma a reťaze kladú rastúci odpor, ale rýchlosť nikdy tvrdo
        // neorežú. Z kopca sa tak dá prirodzene zrýchliť bez viditeľného snapu.
        val equipmentSpeed = when {
            hasShreddedTyre && hasChains ->
                minOf(GameConfig.BLOWN_TYRE_MAX_SPEED, GameConfig.CHAINS_MAX_SPEED)
            hasShreddedTyre -> GameConfig.BLOWN_TYRE_MAX_SPEED
            hasPuncturedTyre && hasChains ->
                minOf(GameConfig.BLOWN_TYRE_MAX_SPEED * 1.65f, GameConfig.CHAINS_MAX_SPEED)
            hasPuncturedTyre -> GameConfig.BLOWN_TYRE_MAX_SPEED * 1.65f
            hasChains -> GameConfig.CHAINS_MAX_SPEED
            else -> Float.POSITIVE_INFINITY
        }
        if (speed > equipmentSpeed) {
            longForce -= (speed - equipmentSpeed) * mass * 0.85f
        }
        speed += (longForce / mass) * dt
        // Dopredu aj dozadu limituje výkon, svah a odpor – bez tvrdého
        // stropu na cúvaní (ten na voľnobehu zbytočne brzdil rozbeh späť).
        // Držíme stojku, keď brzda prekoná svah – inak mikro-zrýchlenie znova
        // rozbehne auto a hráč „brzdí“ celý kopec.
        if (grounded && brakeDemand > 0.05f && !reversing &&
            kotlin.math.abs(speed) < 0.55f &&
            kotlin.math.abs(longForce) < maxBrakeF * 0.08f
        ) {
            speed = 0f
        }
        if (!engineRunning && kotlin.math.abs(speed) < 0.2f && grounded) speed = 0f

        x += speed * dt

        if (SedanSpec.wheelRadius > 0.01f) {
            // Hnaná náprava sa točí podľa wheelSpeed (aj s preklzom), voľná sa
            // len valí po ceste; brzda zablokuje obe. Preto pri RWD vidno
            // pretáčať len zadné koleso.
            val driven = if (wheelsLocked) 0f else wheelSpeed
            val rolling = if (wheelsLocked) 0f else speed
            val frontSurface = when (driveLayout) {
                DriveLayout.FWD, DriveLayout.AWD -> driven
                DriveLayout.RWD -> rolling
            }
            val rearSurface = when (driveLayout) {
                DriveLayout.RWD, DriveLayout.AWD -> driven
                DriveLayout.FWD -> rolling
            }
            fun spin(prev: Float, surfaceSpeed: Float, slot: ComponentSlot): Float {
                val r = SedanSpec.wheelRadius * wheelContactScale(slot)
                if (r < 0.01f) return prev
                val deg = Math.toDegrees((surfaceSpeed * dt / r).toDouble()).toFloat()
                return (prev + deg) % 360f
            }
            wheelSpinFrontDeg = spin(wheelSpinFrontDeg, frontSurface, ComponentSlot.TIRE_FRONT)
            wheelSpinRearDeg = spin(wheelSpinRearDeg, rearSurface, ComponentSlot.TIRE_REAR)
            wheelSpinDeg = wheelSpinRearDeg
        }
    }

    /**
     * Sneh a špina na karosérii. V zime auto hneď opráši a na snehu pribúda
     * čiapka; v bahne a na hline sa zamaže. Dážď a voda to zmyjú.
     */
    fun tickBodyWeather(
        dt: Float,
        winterAmount: Float,
        surface: RoadSurface,
        paving: RoadPaving,
        raining: Boolean,
        speed: Float,
        grounded: Boolean,
        slip: Float,
        foggy: Boolean = false
    ) {
        val moving = grounded && kotlin.math.abs(speed) > 0.45f
        val onSnow = paving.winter ||
            surface == RoadSurface.ICE ||
            surface == RoadSurface.SLUSH
        val ambient = winterAmount * GameConfig.CAR_SNOW_AMBIENT
        if (snowCover < ambient) snowCover = ambient
        val snowIn = when {
            onSnow -> dt * GameConfig.CAR_SNOW_GAIN *
                (0.55f + winterAmount * 0.65f) *
                (if (moving) 1.45f else 0.62f)
            winterAmount > 0.35f -> dt * GameConfig.CAR_SNOW_GAIN * 0.28f * winterAmount
            else -> 0f
        }
        val snowOut = when {
            onSnow || winterAmount > 0.2f -> 0f
            raining -> dt * GameConfig.CAR_SNOW_MELT * 4.2f
            surface == RoadSurface.WATER -> dt * GameConfig.CAR_SNOW_MELT * 2.6f
            else -> dt * GameConfig.CAR_SNOW_MELT *
                (0.55f + kotlin.math.abs(speed) * 0.045f)
        }
        snowCover = (snowCover + snowIn - snowOut).coerceIn(0f, 1f)

        val dirty = surface == RoadSurface.MUD ||
            surface == RoadSurface.SAND ||
            surface == RoadSurface.GRAVEL ||
            paving == RoadPaving.DIRT ||
            paving == RoadPaving.GRAVEL_ROAD ||
            paving == RoadPaving.SAND_TRACK
        val mudMul = when (surface) {
            RoadSurface.MUD -> 1.7f
            RoadSurface.SAND -> 1.15f
            else -> 1f
        }
        val dirtIn = if (dirty && moving) {
            dt * GameConfig.CAR_DIRT_GAIN * mudMul *
                (0.32f + (kotlin.math.abs(speed) / 11f).coerceIn(0.2f, 1f) + slip * 0.85f)
        } else 0f
        // Počasie a čas pomaly vracajú špinu aj na čistej ceste – prelakovanie
        // nie je natrvalo.
        val ambientDirt = dt * GameConfig.CAR_DIRT_AMBIENT *
            (0.55f + winterAmount * 0.35f +
                if (raining) 0.25f else 0f +
                if (foggy) 0.45f else 0f)
        val dirtOut = when {
            // Stojacie auto dážď neopláchne naraz – inak po prieskume budovy
            // zmizne hrdza/špina a potom sa „vráti“ ambientom.
            !moving -> 0f
            raining -> dt * GameConfig.CAR_DIRT_WASH * 1.15f
            surface == RoadSurface.WATER -> dt * GameConfig.CAR_DIRT_WASH
            surface == RoadSurface.ASPHALT &&
                paving == RoadPaving.ASPHALT ->
                dt * GameConfig.CAR_DIRT_WASH * 0.12f
            else -> 0f
        }
        bodyDirt = (bodyDirt + dirtIn + ambientDirt - dirtOut).coerceIn(0f, 1f)
        // Hrdza rastie so špinou/ambientom, ale nikdy sa neumýva – inak
        // pri daždi a jazde mizne a po čase sa „vracia“.
        val rustIn = dirtIn * 0.55f + ambientDirt * 0.85f
        bodyRust = (bodyRust + rustIn).coerceIn(0f, 1f)
    }

    /** Kompatibilita testov: jeden bod + sklon → predok/zadok. */
    fun applyDriveSlope(
        dt: Float,
        throttle: Float,
        brake: Float,
        groundY: Float,
        slope: Float,
        bumpMul: Float
    ) {
        val wb = SedanSpec.wheelOffsetX
        applyDrive(
            dt, throttle, brake,
            rearGroundY = groundY - slope * wb,
            frontGroundY = groundY + slope * wb,
            bumpMul = bumpMul
        )
    }
}

/** Bez týchto dielov auto nenaštartuje ani sa nepohne. */
private val ESSENTIAL_SLOTS = listOf(
    ComponentSlot.ENGINE,
    ComponentSlot.FUEL_TANK,
    ComponentSlot.TIRE_FRONT,
    ComponentSlot.TIRE_REAR,
    ComponentSlot.BATTERY,
    ComponentSlot.STARTER
)

/** Disk voči vonkajšiemu polomeru nafúknutej gumy – kresba aj pokles pri SHREDDED. */
const val WHEEL_HUB_FRAC = 0.61f

enum class EndCause {
    OUT_OF_FUEL, ENGINE_DESTROYED, OVERHEAT, BATTERY_DEAD
}

/** Čo práve najviac ničí motor – ide do hlášok aj do konca jazdy. */
enum class WearCause(val warning: String, val fatal: String) {
    FROZEN_COOLANT(
        "Water in the cooling system is freezing — the engine is cracking",
        "cracked by frozen water in the cooling system"
    ),
    LOW_OIL("Oil is low — the engine is seizing!", "seized without oil"),
    DIRTY_OIL("Dirty oil is grinding the engine", "worn out by dirty oil"),
    DIRTY_FUEL("Watered fuel is killing the engine", "ruined by watered fuel"),
    WRONG_FUEL("Wrong fuel is damaging the engine — drain the tank", "destroyed by the wrong fuel"),
    NO_RADIATOR("No radiator — coolant alone can't cool the engine", "cooked without a radiator"),
    BAD_RADIATOR("Radiator is failing — engine is overheating", "cooked by a failing radiator"),
    LOW_COOLANT("Coolant is low — engine is overheating", "cooked with no coolant"),
    OVERHEAT("Overheating is destroying the engine", "burnt out by overheating")
}
