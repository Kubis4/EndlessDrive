package sk.kubis.endlessdrive.game.car

import sk.kubis.endlessdrive.domain.model.ItemCatalog
import sk.kubis.endlessdrive.domain.model.ItemDef
import sk.kubis.endlessdrive.domain.model.ItemStack
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
    /** Otáčanie kolies (stupne) – pre CarArtist, každá náprava zvlášť. */
    var wheelSpinDeg = 0f
    /** Uhol predného kolesa – pri RWD sa len valí, netočí sa s preklzom. */
    var wheelSpinFrontDeg = 0f
    /** Uhol zadného kolesa. */
    var wheelSpinRearDeg = 0f
    /** true = aspoň jedno koleso má kontakt. */
    var grounded = true
        private set
    /** 0..1 – ako veľmi kolesá preklzávajú. */
    var wheelSlip = 0f
        private set
    /** true = kolesá sú zablokované brzdou. */
    var wheelsLocked = false
        private set
    /** Aktuálna normálová sila (súčet náprav), na debug/HUD. */
    var normalForce = 0f
        private set
    /** Stlačenie pruženia 0..1. */
    var suspensionLoad = 0f
        private set

    /** Stlačenie zadnej / prednej nápravy (m) – pre vizuál pruženia. */
    val rearCompression: Float get() = rearComp
    val frontCompression: Float get() = frontComp

    private var rearComp = 0f
    private var frontComp = 0f

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

    /**
     * Priľnavosť jednej nápravy. Opotrebenie neuberá grip lineárne – dezén
     * sa zodiera pomaly, ale plešatá guma spadne rýchlo.
     */
    fun axleGrip(slot: ComponentSlot): Float {
        val t = parts[slot] ?: return 0.35f
        val base = if (t.def.grip > 0f) t.def.grip else t.def.reliability
        return base * treadFactor(t.health)
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
     * reťaze z toho spravia normálnu jazdu – ale na suchu prekážajú.
     */
    val winterTraction: Float
        get() {
            val front = parts[ComponentSlot.TIRE_FRONT]?.def?.snowGrip ?: 0.45f
            val rear = parts[ComponentSlot.TIRE_REAR]?.def?.snowGrip ?: 0.45f
            // Rozhoduje hnaná náprava, nie priemer oboch. Pri rovnomernom
            // priemere zimná guma vzadu na RWD takmer nepomohla – letná
            // vpredu, ktorá žiadny ťah neprenáša, jej výhodu zjedla.
            val tyres = when (driveLayout) {
                DriveLayout.RWD -> rear * 0.78f + front * 0.22f
                DriveLayout.FWD -> front * 0.78f + rear * 0.22f
                DriveLayout.AWD -> (front + rear) * 0.5f
            }
            return if (hasChains) tyres * GameConfig.CHAINS_SNOW_BONUS else tyres
        }

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

    /** Aspoň jedna guma je na handry – auto ide na disku. */
    val hasBlownTyre: Boolean
        get() = TIRE_SLOTS.any { (parts[it]?.health ?: 1f) < GameConfig.BLOWN_TYRE_HEALTH }

    /** Ktorá náprava je roztrhaná (null = obe držia). */
    val blownAxle: ComponentSlot?
        get() = TIRE_SLOTS.firstOrNull {
            (parts[it]?.health ?: 1f) < GameConfig.BLOWN_TYRE_HEALTH
        }

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
            val s = parts[ComponentSlot.SUSPENSION] ?: return 0.65f
            return s.def.reliability * s.health.coerceIn(0.25f, 1.3f)
        }

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
     * Efektívne μ na danom povrchu (grip × pruženie × hrboľatosť).
     * Pri malej rýchlosti platí statické trenie – rozjazd do kopca je preto
     * o niečo ľahší než držať sa v kopci vo vyššej rýchlosti.
     */
    fun surfaceMu(bumpMul: Float = 0f, atSpeed: Float = 0f): Float {
        val tire = tireGrip.coerceIn(0.2f, 1.6f)
        val sus = (0.75f + 0.25f * suspensionMul.coerceIn(0.3f, 1.3f))
        val rough = 1f / (1f + bumpMul * 0.55f / tireBumpResist)
        val crawl = 1f + GameConfig.STATIC_GRIP_BONUS *
            (1f - (kotlin.math.abs(atSpeed) / 6f).coerceIn(0f, 1f))
        return GameConfig.TIRE_MU * tire * sus * rough * crawl
    }

    /** Podiel váhy, ktorý ťah vôbec môže využiť – podľa pohonu. */
    private val driveShare: Float
        get() = when (driveLayout) {
            DriveLayout.AWD -> 0.95f
            // Zodpovedá statickému rozloženiu + prenosu váhy pri plnom plyne.
            DriveLayout.FWD ->
                1f - (GameConfig.REAR_BIAS_FWD + GameConfig.WEIGHT_TRANSFER * GameConfig.FWD_TRANSFER_MUL)
            DriveLayout.RWD -> GameConfig.REAR_BIAS_RWD + GameConfig.WEIGHT_TRANSFER
        }

    /**
     * Max sklon (dy/dx), ktorý ešte gumy udržia zo stojky: tanθ < μ.
     * Na rovinke je vždy „dost“ – limit platí len do kopca.
     */
    fun maxClimbSlope(bumpMul: Float = 0f, atSpeed: Float = 0f): Float =
        surfaceMu(bumpMul, atSpeed) * driveShare

    /** Chladenie: bez chladiča kvapalina sama motor neutiahne. */
    val radiatorMul: Float
        get() {
            val r = parts[ComponentSlot.RADIATOR] ?: return 0.22f
            return (r.def.reliability * r.health.coerceIn(0.15f, 1.3f)).coerceIn(0.15f, 1.4f)
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

        // Tieto z auta často niekto vybral – potom sa musia nájsť.
        if (rng.chance(0.75f)) mount(ComponentSlot.BATTERY, ItemCatalog.BATTERY.id, 0.30f, 0.72f)
        if (rng.chance(0.80f)) mount(ComponentSlot.RADIATOR, ItemCatalog.RADIATOR.id, 0.30f, 0.72f)
        if (rng.chance(0.70f)) mount(ComponentSlot.ALTERNATOR, ItemCatalog.ALTERNATOR.id, 0.30f, 0.75f)
        if (rng.chance(0.80f)) mount(ComponentSlot.STARTER, ItemCatalog.STARTER.id, 0.35f, 0.78f)
        // Karoséria (dvere/kapota/okná/nárazníky) chýba vždy – okrem ladenia,
        // to sa dorába nižšie, až keď sú nastavené aj kvapaliny.

        // Kvapaliny: občas úplne suchá nádrž.
        fuel = if (rng.chance(0.20f)) 0f else rng.nextFloat(6f, 22f)
        oil = if (rng.chance(0.20f)) 0f else rng.nextFloat(0.6f, 2.6f)
        coolant = if (rng.chance(0.20f)) 0f else rng.nextFloat(1.0f, 4.2f)
        fuelPurity = rng.nextFloat(0.55f, 0.95f)
        oilPurity = rng.nextFloat(0.50f, 0.95f)
        coolantPurity = rng.nextFloat(0.45f, 0.95f)

        temperature = 40f
        batteryCharge = if (hasPart(ComponentSlot.BATTERY)) rng.nextFloat(0.25f, 0.75f) else 0f
        engineRunning = false
        wheelSpinDeg = 0f
        wheelSpinFrontDeg = 0f
        wheelSpinRearDeg = 0f
        vy = 0f
        wheelSpeed = 0f
        pitchRate = 0f
        grounded = true
        wheelSlip = 0f
        wheelsLocked = false
        normalForce = 0f
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
            parts[slot] = MountedPart(item.id, ComponentCondition.NEW, 1f)
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
            oilPurity = 1f
            coolantPurity = 1f
            batteryCharge = 1f
            temperature = 40f
        }
    }

    /** Priviaže auto na vozovku (príprava, stop, teleport). */
    fun snapToGround(groundY: Float, slope: Float = 0f) {
        pitch = kotlin.math.atan(slope)
        pitchRate = 0f
        val susMul = suspensionMul.coerceIn(0.35f, 1.4f)
        val sag = (GameConfig.CAR_MASS * GameConfig.AIR_GRAVITY) /
            (2f * GameConfig.SUSP_SPRING * susMul).coerceAtLeast(1f)
        // Rovnaká výška ako v rovnováhe pruženia – inak render „zdvihne“ karosériu.
        y = groundY + rideHeight - sag
        vy = 0f
        wheelSpeed = speed
        grounded = true
        wheelSlip = 0f
        wheelsLocked = false
        rearComp = sag
        frontComp = sag
        normalForce = GameConfig.CAR_MASS * GameConfig.AIR_GRAVITY
        suspensionLoad = (sag / 0.2f).coerceIn(0f, 1f)
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

    /**
     * Montáž dielu. Vyliatie kvapaliny pri výmene rieši [GameEngine] – potrebuje
     * ju totiž zachytiť do kanistra, a to auto samo nevie.
     */
    fun mount(slot: ComponentSlot, stack: ItemStack): MountedPart? {
        if (!stack.def.canMountTo(slot)) return null
        val previous = parts[slot]
        parts[slot] = MountedPart(stack.defId, stack.condition, stack.health)
        if (slot == ComponentSlot.BATTERY) {
            batteryCharge = maxOf(batteryCharge, (0.45f + 0.45f * stack.health).coerceAtMost(1f))
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
            FluidType.FUEL -> { fuel = 0f; fuelPurity = 1f }
            FluidType.OIL -> { oil = 0f; oilPurity = 1f }
            FluidType.COOLANT -> { coolant = 0f; coolantPurity = 1f }
            FluidType.BRAKE_FLUID -> Unit
        }
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
     * Bežné opotrebenie z jazdy – nič sa nekazí naraz, ale po desiatkach
     * kilometrov je údržba náplňou hry. Nemá to byť trest, len dôvod
     * zbierať náhradné diely aj keď je auto momentálne v poriadku.
     *
     * @param bumpMul hrboľatosť pod kolesami
     * @param brake koľko sa práve brzdí (0..1)
     */
    fun tickWear(dt: Float, bumpMul: Float, brake: Float) {
        val v = kotlin.math.abs(speed)
        if (v < 0.2f && !engineRunning) return
        val load = (v / GameConfig.MAX_SPEED).coerceIn(0f, 1.2f)

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
        val roll = GameConfig.WEAR_TIRES * load * (1f + bumpMul * 0.8f) * chainStrain / tireBumpResist
        val spin = GameConfig.WEAR_TIRES * wheelSlip * 1.2f / tireBumpResist
        val locked = if (wheelsLocked) spin else 0f
        // Reťaze pri rýchlosti trhajú gumy aj samy seba.
        if (hasChains) {
            val strain = ((v - GameConfig.CHAINS_MAX_SPEED * 0.7f) / 6f).coerceIn(0f, 1.5f)
            wear(ComponentSlot.CHAINS, GameConfig.WEAR_TIRES * (0.6f + strain * 3f))
        }
        wear(ComponentSlot.TIRE_FRONT, roll + locked + if (drives(ComponentSlot.TIRE_FRONT)) spin else 0f)
        wear(ComponentSlot.TIRE_REAR, roll + locked + if (drives(ComponentSlot.TIRE_REAR)) spin else 0f)
        // Brzdy sa zodierajú len keď sa brzdí, o to rýchlejšie z rýchlosti.
        wear(ComponentSlot.BRAKES, GameConfig.WEAR_BRAKES * brake * (0.3f + load))
        // Pruženie: hrbole a dopady.
        wear(
            ComponentSlot.SUSPENSION,
            GameConfig.WEAR_SUSPENSION * (0.25f + load) * (1f + bumpMul * 1.4f)
        )
        if (engineRunning) {
            wear(ComponentSlot.ENGINE, GameConfig.WEAR_ENGINE_IDLE * (0.5f + load))
            wear(ComponentSlot.ALTERNATOR, GameConfig.WEAR_AUX)
            wear(ComponentSlot.STARTER, GameConfig.WEAR_AUX * 0.4f)
            wear(ComponentSlot.RADIATOR, GameConfig.WEAR_AUX * (0.6f + load))
            wear(ComponentSlot.DRIVETRAIN, GameConfig.WEAR_AUX * (0.4f + load * 1.2f))
        }
    }

    /** [charging] = false, keď nabíjanie blokuje udalosť (prasknutý remeň). */
    fun tickElectrics(
        dt: Float,
        headlightsOn: Boolean,
        charging: Boolean = true,
        /** 0 = normálne počasie, 1 = mráz – batéria dáva menej. */
        cold: Float = 0f
    ): EndCause? {
        val alternator = parts[ComponentSlot.ALTERNATOR]?.takeIf { charging }
        // V mraze batéria dáva menej a samovoľne sa vybíja rýchlejšie.
        val coldDrain = 1f + cold * GameConfig.COLD_BATTERY_DRAIN
        if (cold > 0.05f) {
            batteryCharge = (batteryCharge - GameConfig.COLD_BATTERY_DRAIN * 0.004f * cold * dt)
                .coerceAtLeast(0f)
        }
        if (engineRunning) {
            // Batériu nabíja len namontovaný (a živý) alternátor – nie „magicky“.
            if (alternator != null && alternator.health > 0.05f) {
                val charge = GameConfig.ALTERNATOR_CHARGE *
                    (alternator.def.reliability * alternator.health.coerceIn(0.1f, 1.2f))
                batteryCharge = (batteryCharge + charge * dt).coerceAtMost(1f)
            }
            if (headlightsOn) {
                // Bez alternátora svetlá žerú batériu aj pri bežiacom motore.
                val drain = if (alternator != null && alternator.health > 0.05f) {
                    GameConfig.HEADLIGHT_DRAIN_ON
                } else {
                    GameConfig.HEADLIGHT_DRAIN_OFF
                }
                batteryCharge = (batteryCharge - drain * coldDrain * dt).coerceAtLeast(0f)
                if (batteryCharge <= 0.001f) return EndCause.BATTERY_DEAD
            }
            return null
        }
        // Bez bežiaceho motora svetlá žerú batériu – prázdna batéria sama o sebe
        // nie je game over (chýbajúca batéria sa rieši pri štarte).
        if (headlightsOn) {
            batteryCharge = (batteryCharge - GameConfig.HEADLIGHT_DRAIN_OFF * coldDrain * dt)
                .coerceAtLeast(0f)
            if (batteryCharge <= 0.001f) return EndCause.BATTERY_DEAD
        }
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
        cold: Float = 0f
    ): EndCause? {
        if (!engineRunning) return null

        val s = slope.coerceIn(-0.6f, 0.6f)
        val slopeMul = (1f + if (s > 0f) s * GameConfig.FUEL_SLOPE_UP else s * GameConfig.FUEL_SLOPE_DOWN)
            .coerceIn(0.35f, 2.8f)
        val qualityMul = 1f + (1f - fuelPurity.coerceIn(0f, 1f)) * 0.5f
        // Studený motor beží bohato – v mraze sa spotreba dvíha, kým sa nezohreje.
        val warmedUp = MathX.smoothstep(30f, 70f, temperature)
        val coldMul = 1f + cold * GameConfig.COLD_FUEL_PENALTY * (1f - warmedUp)
        val burn = (GameConfig.FUEL_IDLE + GameConfig.FUEL_THROTTLE * throttle * slopeMul) *
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
        val heatGen = 9f * throttle + 8f * climbLoad
        // Ojazdený chladič má hriať, nie zabíjať – štartovací kus má zdravie
        // od 0.30, čo pri koeficiente 16 znamenalo trvalé prehrievanie.
        val radPenalty = if (hasRadiator) (1f - rad) * 9f else 26f
        val coolPenalty = (1f - coolOk) * 18f +
            GameConfig.BAD_COOLANT_HEAT * (1f - coolPure)
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
        if (temperature > GameConfig.OVERHEAT_THRESHOLD) {
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
        winter: Boolean = false
    ) {
        val wb = SedanSpec.wheelOffsetX
        val wheelbase = (wb * 2f).coerceAtLeast(0.5f)
        val slope = (frontGroundY - rearGroundY) / wheelbase
        val sinT = slope / kotlin.math.sqrt(1f + slope * slope)
        val mass = GameConfig.CAR_MASS
        val g = GameConfig.AIR_GRAVITY
        val susMul = suspensionMul.coerceIn(0.35f, 1.4f)
        val springK = GameConfig.SUSP_SPRING * susMul
        val dampC = GameConfig.SUSP_DAMPER * (0.55f + 0.45f * susMul)
        val ride = rideHeight
        val travel = suspTravel.coerceIn(0.2f, 0.75f)

        // --- Pruženie (predná / zadná náprava) ---
        val sinP = kotlin.math.sin(pitch)
        // Nose-up (+pitch): predok vyššie, zadok nižšie.
        val rearBody = y - wb * sinP
        val frontBody = y + wb * sinP
        val rearTarget = rearGroundY + ride
        val frontTarget = frontGroundY + ride
        // Relatívna rýchlosť bodu karosérie k zemi (zem sa hýbe so sklonom·speed).
        val groundVy = slope * speed
        val cosP = kotlin.math.cos(pitch)
        val rearRelVy = vy - wb * pitchRate * cosP - groundVy
        val frontRelVy = vy + wb * pitchRate * cosP - groundVy

        fun axleForce(compRaw: Float, relVy: Float): Pair<Float, Float> {
            val comp = compRaw.coerceIn(0f, travel)
            if (compRaw <= 0f) return 0f to 0f
            var n = springK * comp - dampC * relVy
            // Bottom-out
            if (compRaw > travel) {
                n += springK * 2.5f * (compRaw - travel)
            }
            return n.coerceAtLeast(0f) to comp
        }

        val (rearN, rearC) = axleForce(rearTarget - rearBody, rearRelVy)
        val (frontN, frontC) = axleForce(frontTarget - frontBody, frontRelVy)
        rearComp = rearC
        frontComp = frontC
        val totalN = rearN + frontN
        normalForce = totalN
        suspensionLoad = ((rearC + frontC) * 0.5f / 0.2f).coerceIn(0f, 1f)
        val wasGrounded = grounded
        grounded = totalN > mass * g * 0.04f || rearC > 0.01f || frontC > 0.01f

        // Vertikálna dynamika + náklon
        val fy = totalN - mass * g
        vy += (fy / mass) * dt
        y += vy * dt
        val torque = (frontN - rearN) * wb +
            (throttle - brake) * GameConfig.GROUND_PITCH_TORQUE * (if (grounded) 1f else 0.55f) +
            (if (!grounded) (throttle - brake) * GameConfig.AIR_PITCH_TORQUE else 0f)
        pitchRate += (torque / GameConfig.PITCH_INERTIA) * dt
        if (grounded) {
            // Slabšie lepenie na sklon → viditeľný squat/dive a náklon z pruženia.
            val targetPitch = kotlin.math.atan(slope)
            pitchRate += (targetPitch - pitch) * GameConfig.PITCH_SLOPE_TRACK * dt
            pitchRate *= (1f - (2.0f * dt).coerceIn(0f, 0.7f))
        } else {
            pitchRate *= (1f - (0.35f * dt).coerceIn(0f, 0.5f))
        }
        pitch = (pitch + pitchRate * dt).coerceIn(-1.1f, 1.1f)

        // Dopad
        if (!wasGrounded && grounded && -vy > GameConfig.LANDING_IMPACT) {
            val hit = ((-vy - GameConfig.LANDING_IMPACT) / 12f).coerceIn(0f, 1f)
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
        // Na snehu rozhoduje zimná výbava, na suchu reťaze naopak prekážajú.
        val winterMul = if (icy) winterTraction.coerceIn(0.3f, 1.6f) else tarmacPenalty
        val mu = surfaceMu(bumpMul, speed) * surface.gripMul *
            pavingGrip.coerceIn(0.5f, 1.1f) * winterMul
        // Weight transfer: plyn odľahčí predok / naloží zadek (RWD grip).
        val fwd = driveLayout == DriveLayout.FWD
        val transferMul = if (fwd) GameConfig.FWD_TRANSFER_MUL else 1f
        val transfer = (throttle - brake) * GameConfig.WEIGHT_TRANSFER * transferMul
        // Stúpanie tlačí váhu dozadu – RWD ide do kopca lepšie, FWD horšie,
        // ale nie tak, aby sa s FWD nedalo vyjsť nič.
        val slopeMul = if (fwd) GameConfig.FWD_SLOPE_MUL else 1f
        val slopeShift = (sinT * GameConfig.SLOPE_TRANSFER * slopeMul).coerceIn(-0.20f, 0.20f)
        val staticRear = when (driveLayout) {
            DriveLayout.FWD -> GameConfig.REAR_BIAS_FWD
            DriveLayout.RWD -> GameConfig.REAR_BIAS_RWD
            DriveLayout.AWD -> GameConfig.REAR_BIAS_AWD
        }
        val rearShare = (staticRear + transfer + slopeShift).coerceIn(0.22f, 0.82f)
        val frontShare = 1f - rearShare
        // Koľko normálovej sily môže prenášať ťah podľa pohonu.
        val driveNormal = if (!grounded) {
            0f
        } else {
            when (driveLayout) {
                DriveLayout.AWD -> totalN * 0.95f
                DriveLayout.FWD -> totalN * frontShare
                DriveLayout.RWD -> totalN * rearShare
            }
        }
        val brakeNormal = if (grounded) totalN else 0f
        val maxDriveF = mu * driveNormal
        val maxBrakeF = mu * brakeNormal * brakeMul.coerceIn(0.4f, 1.4f)

        // Strop 1.65 dosiahol už 116 hp, takže V6 a turbodiesel boli na ťah
        // nerozoznateľné. Rozsah motorov je 78–130 hp, nech ho pokryje celý.
        val powerFactor = (powerHp / 70f).coerceIn(0.5f, 1.9f)
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
            val ease = (1f - GameConfig.TRACTION_EASE_OFF * wheelSlip * rolling)
                .coerceAtLeast(GameConfig.TRACTION_EASE_FLOOR)
            driveDemand = GameConfig.ACCEL * powerFactor * throttle * launch * mass * ease
        }
        if (brake > 0.05f && throttle < 0.05f && speed <= GameConfig.STOP_SPEED * 1.2f && grounded) {
            reversing = true
            driveDemand = -GameConfig.REVERSE_ACCEL * brake * brakeMul * mass
        } else if (brake > 0.05f && brake >= throttle) {
            brakeDemand = GameConfig.BRAKE * brake * mass
        }

        // Gravitácia pozdĺž trate + odpor
        val gradeForce = -mass * g * sinT
        val rollDrag = surface.rollDrag *
            (0.35f + 0.65f * (kotlin.math.abs(speed) / GameConfig.MAX_SPEED).coerceIn(0f, 1f))
        val drag = -kotlin.math.sign(speed) * (
            (GameConfig.COAST_DRAG + rollDrag) * mass *
                (if (kotlin.math.abs(speed) > 0.15f) 1f else 0f) +
                GameConfig.AERO_DRAG * (1f + extraDrag) * speed * speed * mass
            )

        var longForce = gradeForce + drag
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
            wheelSpeed = MathX.damp(wheelSpeed, speed, GameConfig.WHEEL_RESP, dt)
            slipTarget = if (maxDriveF < 0.01f) throttle else
                ((kotlin.math.abs(driveDemand) - maxDriveF) / (kotlin.math.abs(driveDemand) + 1f))
                    .coerceIn(0f, 1f)
        } else if (brakeDemand > 0.05f) {
            val brakeF = brakeDemand.coerceAtMost(maxBrakeF)
            if (speed > 0f) longForce -= brakeF
            else if (speed < 0f) longForce += brakeF
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
            // z motora kulisu: nad hranicou gripu bolo 78 hp aj 130 hp rovnaké.
            val traction = if (driveDemand <= maxDriveF) driveDemand
            else maxDriveF + (driveDemand - maxDriveF) * GameConfig.GRIP_OVERDRIVE
            longForce += traction
            // Preklz meriame pomerom „koľko ťahu vs. koľko gumy unesú“.
            // Do TRACTION_SLACK sa auto ešte chytí (vodič dávkuje plyn),
            // nad tým sa začne pretáčať – a na bahne to príde skôr než na asfalte.
            val ratio = if (maxDriveF < 0.01f) 99f else driveDemand / maxDriveF
            slipTarget = ((ratio - GameConfig.TRACTION_SLACK) / 2.2f).coerceIn(0f, 1f)
            val spin = speed + slipTarget * GameConfig.SLIP_SPIN_BONUS
            wheelSpeed = MathX.damp(wheelSpeed, spin, GameConfig.WHEEL_RESP, dt)
        } else {
            wheelSpeed = MathX.damp(wheelSpeed, speed, GameConfig.WHEEL_RESP, dt)
            slipTarget = 0f
        }

        // Slip sa dorovná rýchlo – na vrchole kopca nesmie „visieť“ starý preklz.
        val slipRate = if (slipTarget < wheelSlip) 16f else 10f
        wheelSlip = MathX.damp(wheelSlip, slipTarget.coerceIn(0f, 1f), slipRate, dt)

        speed += (longForce / mass) * dt
        // Guma musí byť na rýchlosti cítiť. Pri pôvodnom vzorci mala zodratá
        // guma strop na 82 % maxima – teda takmer žiadny rozdiel oproti novej.
        // Strop drží guma, ale motor ním smie pohnúť. Bez druhého činiteľa
        // mal 130 hp turbodiesel na rovine presne tú istú maximálku ako
        // základných 78 hp – upgrade nebolo na rýchlomere vôbec vidieť.
        var top = GameConfig.MAX_SPEED *
            (0.34f + 0.66f * tireGrip.coerceIn(0.15f, 1.2f)) *
            (0.80f + 0.20f * (powerHp / 95f).coerceIn(0.6f, 1.7f))
        // Roztrhaná guma znamená jazdu na disku – ďalej sa dá už len doplaziť.
        if (hasBlownTyre) top = top.coerceAtMost(GameConfig.BLOWN_TYRE_MAX_SPEED)
        // S reťazami sa nedá uháňať – buď ich zložíš, alebo ideš pomaly.
        if (hasChains) top = top.coerceAtMost(GameConfig.CHAINS_MAX_SPEED)
        speed = speed.coerceIn(-GameConfig.REVERSE_MAX_SPEED, top)
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
                val r = SedanSpec.wheelRadius * wheelScale(slot)
                if (r < 0.01f) return prev
                val deg = Math.toDegrees((surfaceSpeed * dt / r).toDouble()).toFloat()
                return (prev + deg) % 360f
            }
            wheelSpinFrontDeg = spin(wheelSpinFrontDeg, frontSurface, ComponentSlot.TIRE_FRONT)
            wheelSpinRearDeg = spin(wheelSpinRearDeg, rearSurface, ComponentSlot.TIRE_REAR)
            wheelSpinDeg = wheelSpinRearDeg
        }
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
    NO_RADIATOR("No radiator — coolant alone can't cool the engine", "cooked without a radiator"),
    BAD_RADIATOR("Radiator is failing — engine is overheating", "cooked by a failing radiator"),
    LOW_COOLANT("Coolant is low — engine is overheating", "cooked with no coolant"),
    OVERHEAT("Overheating is destroying the engine", "burnt out by overheating")
}
