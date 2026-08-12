package sk.kubis.endlessdrive.domain.model

/**
 * Definícia predmetu (katalog). Inštancie v inventári / loote majú ešte stav.
 */
data class ItemDef(
    val id: String,
    val name: String,
    val rarity: ItemRarity,
    val weight: Float,
    val slots: Int = 1,
    /** Ak ide o komponent – do ktorého slotu sa montuje. */
    val mountsTo: ComponentSlot? = null,
    /**
     * Pneumatika ide na predok alebo zadok – [mountsTo] ostane null
     * a ciele sú [TIRE_SLOTS].
     */
    val axleTire: Boolean = false,
    /** Ak ide o kvapalinu – typ a množstvo v jednej položke. */
    val fluid: FluidType? = null,
    val fluidAmount: Float = 0f,
    /** Parametre komponentu (HP, spotreba…). */
    val powerHp: Float = 0f,
    val fuelUse: Float = 1f,
    val reliability: Float = 1f,
    /**
     * Priľnavosť (hlavne pneumatiky). 0 = použi [reliability].
     * Rozhoduje, či auto vyjde do kopca bez rozjazdu.
     */
    val grip: Float = 0f,
    /** Odolnosť gúm voči hrboľom (vyššie = menej straty gripu). */
    val bumpResist: Float = 1f,
    /**
     * Ako guma drží na snehu a ľade. 1 = zimná zmes, 0.45 = letná guma,
     * ktorá sa na snehu len točí.
     */
    val snowGrip: Float = 0.45f,
    /** Vizuálny / fyzikálny násobiteľ polomeru kolesa. */
    val wheelScale: Float = 1f,
    /** Svetlá výška stredu (m). 0 = predvolená z GameConfig. */
    val rideHeight: Float = 0f,
    /** Max stlačenie pruženia (m). 0 = predvolené. */
    val suspTravel: Float = 0f,
    /** Pohon (len drivetrain diely). */
    val driveLayout: DriveLayout? = null,
    val capacity: Float = 0f,
    /** Úložisko: koľko slotov a kilogramov pridá do batohu. */
    val extraSlots: Int = 0,
    val extraWeight: Float = 0f,
    /** Aerodynamická prirážka (strešný nosič brzdí). */
    val dragAdd: Float = 0f,
    val baseValue: Int = 10
) {
    /** Sloty, do ktorých sa dá namontovať (1 alebo 2 pre pneumatiky). */
    fun mountTargets(): List<ComponentSlot> = when {
        axleTire -> TIRE_SLOTS
        mountsTo != null -> listOf(mountsTo)
        else -> emptyList()
    }

    fun canMountTo(slot: ComponentSlot): Boolean = slot in mountTargets()
}

object ItemCatalog {
    val FUEL_CAN = ItemDef(
        id = "fuel_can", name = "Petrol", rarity = ItemRarity.COMMON,
        weight = 5f, fluid = FluidType.FUEL, fluidAmount = 15f, baseValue = 15
    )
    val OIL_BOTTLE = ItemDef(
        id = "oil", name = "Engine oil", rarity = ItemRarity.COMMON,
        weight = 2f, fluid = FluidType.OIL, fluidAmount = 2f, baseValue = 12
    )
    /**
     * Voda: núdzové riešenie do chladiča. Doplní objem, ale zriedi čistotu
     * takmer na nulu – motor pôjde horúci.
     */
    val WATER = ItemDef(
        id = "water", name = "Water", rarity = ItemRarity.COMMON,
        weight = 2f, fluid = FluidType.COOLANT, fluidAmount = 2f, baseValue = 2
    )
    val COOLANT_BOTTLE = ItemDef(
        id = "coolant", name = "Coolant", rarity = ItemRarity.COMMON,
        weight = 2f, fluid = FluidType.COOLANT, fluidAmount = 3f, baseValue = 12
    )
    val TIRE_POOR = ItemDef(
        id = "tire_poor", name = "Tyre (poor)", rarity = ItemRarity.COMMON,
        weight = 6f, axleTire = true, reliability = 0.55f, grip = 0.68f,
        bumpResist = 0.75f, wheelScale = 0.96f, baseValue = 8
    )
    val TIRE = ItemDef(
        id = "tire_std", name = "Tyre (standard)", rarity = ItemRarity.COMMON,
        weight = 7f, axleTire = true, reliability = 0.85f, grip = 0.90f,
        bumpResist = 1f, wheelScale = 1f, baseValue = 22
    )
    val TIRE_SPORT = ItemDef(
        id = "tire_sport", name = "Tyre (sport)", rarity = ItemRarity.RARE,
        weight = 7f, axleTire = true, reliability = 1.05f, grip = 1.45f,
        bumpResist = 0.70f, wheelScale = 0.94f, baseValue = 55
    )
    /**
     * Zimná zmes – na snehu drží, na suchu je o kúsok horšia než letná.
     * Nájde sa až v neskoršej časti jazdy, keď začne mrznúť.
     */
    val TIRE_WINTER = ItemDef(
        id = "tire_winter", name = "Tyre (winter)", rarity = ItemRarity.RARE,
        weight = 9f, axleTire = true, reliability = 1.05f, grip = 0.95f,
        bumpResist = 1.15f, wheelScale = 1.02f, snowGrip = 1.0f, baseValue = 65
    )
    /**
     * Reťaze idú cez gumy: na snehu zázrak, na holom asfalte uberajú grip
     * a rýchlosť a rýchlejšie zodierajú gumy aj podvozok.
     */
    val SNOW_CHAINS = ItemDef(
        id = "snow_chains", name = "Snow chains", rarity = ItemRarity.RARE,
        weight = 11f, mountsTo = ComponentSlot.CHAINS, reliability = 1f, baseValue = 55
    )
    val TIRE_OFFROAD = ItemDef(
        id = "tire_offroad", name = "Tyre (off-road)", rarity = ItemRarity.RARE,
        weight = 10f, axleTire = true, reliability = 1.2f, grip = 1.15f,
        bumpResist = 1.65f, wheelScale = 1.14f, snowGrip = 0.70f, baseValue = 60
    )
    val BATTERY = ItemDef(
        id = "battery_std", name = "Battery 45Ah", rarity = ItemRarity.UNCOMMON,
        weight = 12f, mountsTo = ComponentSlot.BATTERY, capacity = 45f, reliability = 0.9f, baseValue = 40
    )
    val BATTERY_GOOD = ItemDef(
        id = "battery_good", name = "Battery 70Ah", rarity = ItemRarity.RARE,
        weight = 15f, mountsTo = ComponentSlot.BATTERY, capacity = 70f, reliability = 1.15f, baseValue = 70
    )
    /**
     * Základný motor. Musí uniesť to, že 2WD prenáša ťah len jednou nápravou –
     * s 60 hp sa štartovné auto len prepaľovalo a zožralo gumy.
     */
    val ENGINE_A = ItemDef(
        id = "engine_a", name = "Engine A (78 hp)", rarity = ItemRarity.RARE,
        weight = 120f, mountsTo = ComponentSlot.ENGINE, powerHp = 78f,
        fuelUse = 1.15f, reliability = 0.7f, baseValue = 120
    )
    val ENGINE_B = ItemDef(
        id = "engine_b", name = "Engine B (95 hp)", rarity = ItemRarity.VERY_RARE,
        weight = 145f, mountsTo = ComponentSlot.ENGINE, powerHp = 95f,
        fuelUse = 0.85f, reliability = 1.1f, baseValue = 220
    )
    /**
     * Tretí stupeň – vydrží až do neskorej jazdy a nájde sa prakticky len
     * v depách. Bez neho by chase skončil, keď je auto raz vybavené.
     */
    val ENGINE_C = ItemDef(
        id = "engine_c", name = "Engine C (130 hp)", rarity = ItemRarity.LEGENDARY,
        weight = 160f, mountsTo = ComponentSlot.ENGINE, powerHp = 130f,
        fuelUse = 0.78f, reliability = 1.35f, baseValue = 420
    )
    val RADIATOR = ItemDef(
        id = "radiator", name = "Radiator", rarity = ItemRarity.UNCOMMON,
        weight = 10f, mountsTo = ComponentSlot.RADIATOR, reliability = 1.0f, baseValue = 35
    )
    val RADIATOR_GOOD = ItemDef(
        id = "radiator_good", name = "Radiator XL", rarity = ItemRarity.RARE,
        weight = 14f, mountsTo = ComponentSlot.RADIATOR, reliability = 1.3f, baseValue = 65
    )
    val RADIATOR_HD = ItemDef(
        id = "radiator_hd", name = "Heavy-duty radiator", rarity = ItemRarity.LEGENDARY,
        weight = 17f, mountsTo = ComponentSlot.RADIATOR, reliability = 1.7f, baseValue = 140
    )
    val BRAKES = ItemDef(
        id = "brakes", name = "Brakes", rarity = ItemRarity.UNCOMMON,
        weight = 6f, mountsTo = ComponentSlot.BRAKES, reliability = 1.0f, baseValue = 30
    )
    val BRAKES_GOOD = ItemDef(
        id = "brakes_good", name = "Sport brakes", rarity = ItemRarity.RARE,
        weight = 7f, mountsTo = ComponentSlot.BRAKES, reliability = 1.35f, baseValue = 55
    )
    val FUEL_TANK = ItemDef(
        id = "tank_std", name = "Fuel tank 45 L", rarity = ItemRarity.UNCOMMON,
        weight = 18f, mountsTo = ComponentSlot.FUEL_TANK, capacity = 45f, baseValue = 40
    )
    val FUEL_TANK_BIG = ItemDef(
        id = "tank_big", name = "Fuel tank 70 L", rarity = ItemRarity.RARE,
        weight = 24f, mountsTo = ComponentSlot.FUEL_TANK, capacity = 70f, baseValue = 80
    )
    val FUEL_TANK_LONG = ItemDef(
        id = "tank_long", name = "Long-range tank 110 L", rarity = ItemRarity.LEGENDARY,
        weight = 34f, mountsTo = ComponentSlot.FUEL_TANK, capacity = 110f, baseValue = 200
    )
    val ALTERNATOR = ItemDef(
        id = "alternator_basic", name = "Alternator", rarity = ItemRarity.COMMON,
        weight = 5f, mountsTo = ComponentSlot.ALTERNATOR, reliability = 1f, baseValue = 20
    )
    val STARTER = ItemDef(
        id = "starter_basic", name = "Starter", rarity = ItemRarity.COMMON,
        weight = 4f, mountsTo = ComponentSlot.STARTER, reliability = 1f, baseValue = 18
    )
    val SUSPENSION = ItemDef(
        id = "suspension_basic", name = "Suspension (stock)", rarity = ItemRarity.COMMON,
        weight = 10f, mountsTo = ComponentSlot.SUSPENSION, reliability = 0.9f,
        rideHeight = 0.42f, suspTravel = 0.45f, baseValue = 22
    )
    val SUSPENSION_LOW = ItemDef(
        id = "suspension_low", name = "Suspension (lowered)", rarity = ItemRarity.UNCOMMON,
        weight = 11f, mountsTo = ComponentSlot.SUSPENSION, reliability = 1.2f,
        rideHeight = 0.28f, suspTravel = 0.32f, baseValue = 40
    )
    val SUSPENSION_GOOD = ItemDef(
        id = "suspension_good", name = "Sport suspension", rarity = ItemRarity.RARE,
        weight = 12f, mountsTo = ComponentSlot.SUSPENSION, reliability = 1.35f,
        rideHeight = 0.36f, suspTravel = 0.38f, baseValue = 55
    )
    val SUSPENSION_LIFT = ItemDef(
        id = "suspension_lift", name = "Suspension (lifted)", rarity = ItemRarity.RARE,
        weight = 16f, mountsTo = ComponentSlot.SUSPENSION, reliability = 1.15f,
        rideHeight = 0.64f, suspTravel = 0.58f, baseValue = 70
    )
    val DRIVE_RWD = ItemDef(
        id = "drive_rwd", name = "Drivetrain RWD", rarity = ItemRarity.COMMON,
        weight = 18f, mountsTo = ComponentSlot.DRIVETRAIN, reliability = 1f,
        driveLayout = DriveLayout.RWD, baseValue = 30
    )
    val DRIVE_FWD = ItemDef(
        id = "drive_fwd", name = "Drivetrain FWD", rarity = ItemRarity.UNCOMMON,
        weight = 18f, mountsTo = ComponentSlot.DRIVETRAIN, reliability = 1f,
        driveLayout = DriveLayout.FWD, baseValue = 35
    )
    val DRIVE_AWD = ItemDef(
        id = "drive_awd", name = "Drivetrain 4×4", rarity = ItemRarity.RARE,
        weight = 28f, mountsTo = ComponentSlot.DRIVETRAIN, reliability = 1.1f,
        driveLayout = DriveLayout.AWD, baseValue = 90
    )
    // --- Úložisko -------------------------------------------------------
    val BACKPACK = ItemDef(
        id = "backpack", name = "Hiking backpack", rarity = ItemRarity.UNCOMMON,
        weight = 3f, mountsTo = ComponentSlot.CARGO,
        extraSlots = 3, extraWeight = 20f, baseValue = 30
    )
    val BOOT_CRATE = ItemDef(
        id = "boot_crate", name = "Boot crate", rarity = ItemRarity.UNCOMMON,
        weight = 8f, mountsTo = ComponentSlot.CARGO,
        extraSlots = 6, extraWeight = 45f, baseValue = 45
    )
    /** Vidno ho na streche – a v rýchlosti je ho aj cítiť. */
    val ROOF_RACK = ItemDef(
        id = "roof_rack", name = "Roof rack", rarity = ItemRarity.RARE,
        weight = 14f, mountsTo = ComponentSlot.ROOF_RACK,
        extraSlots = 5, extraWeight = 40f, dragAdd = 0.35f, baseValue = 60
    )

    val DOORS = ItemDef(
        id = "doors", name = "Doors (pair)", rarity = ItemRarity.UNCOMMON,
        weight = 22f, mountsTo = ComponentSlot.DOORS, reliability = 1f, baseValue = 35
    )
    val HOOD = ItemDef(
        id = "hood", name = "Hood", rarity = ItemRarity.COMMON,
        weight = 12f, mountsTo = ComponentSlot.HOOD, reliability = 1f, baseValue = 20
    )
    val WINDOWS = ItemDef(
        id = "windows", name = "Windows", rarity = ItemRarity.UNCOMMON,
        weight = 8f, mountsTo = ComponentSlot.WINDOWS, reliability = 1f, baseValue = 28
    )
    val FRONT_BUMPER = ItemDef(
        id = "bumper_f", name = "Front bumper", rarity = ItemRarity.COMMON,
        weight = 9f, mountsTo = ComponentSlot.FRONT_BUMPER, reliability = 1f, baseValue = 18
    )
    val REAR_BUMPER = ItemDef(
        id = "bumper_r", name = "Rear bumper", rarity = ItemRarity.COMMON,
        weight = 9f, mountsTo = ComponentSlot.REAR_BUMPER, reliability = 1f, baseValue = 18
    )

    val ALL = listOf(
        FUEL_CAN, OIL_BOTTLE, COOLANT_BOTTLE, WATER,
        TIRE_POOR, TIRE, TIRE_SPORT, TIRE_OFFROAD, TIRE_WINTER, SNOW_CHAINS,
        BACKPACK, BOOT_CRATE, ROOF_RACK,
        BATTERY, BATTERY_GOOD,
        ENGINE_A, ENGINE_B, ENGINE_C,
        RADIATOR, RADIATOR_GOOD, RADIATOR_HD,
        BRAKES, BRAKES_GOOD,
        FUEL_TANK, FUEL_TANK_BIG, FUEL_TANK_LONG,
        ALTERNATOR, STARTER,
        SUSPENSION, SUSPENSION_LOW, SUSPENSION_GOOD, SUSPENSION_LIFT,
        DRIVE_RWD, DRIVE_FWD, DRIVE_AWD,
        DOORS, HOOD, WINDOWS, FRONT_BUMPER, REAR_BUMPER
    )

    fun byId(id: String): ItemDef? = ALL.find { it.id == id }
}

/** Inštancia predmetu v inventári alebo v budove. */
data class ItemStack(
    val defId: String,
    var condition: ComponentCondition = ComponentCondition.USED,
    var health: Float = condition.maxHealth,
    var count: Int = 1,
    /** Len pre kvapaliny: 1 = čistá, menej = riedená vodou / znečistená. */
    var purity: Float = 1f
) {
    val def: ItemDef get() = ItemCatalog.byId(defId) ?: ItemCatalog.FUEL_CAN
    val grade: FluidGrade get() = FluidGrade.of(purity)

    /** Popis stavu do UI – kvapaliny majú čistotu, diely opotrebenie. */
    val stateLabel: String
        get() = if (def.fluid != null) {
            "${grade.displayName} · ${(purity * 100).toInt()} %"
        } else {
            "${condition.displayName} · ${(health * 100).toInt()} %"
        }
}
