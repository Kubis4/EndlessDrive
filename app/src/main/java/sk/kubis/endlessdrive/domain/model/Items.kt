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
    /** Ak ide o kvapalinu – typ a množstvo v jednej položke. */
    val fluid: FluidType? = null,
    val fluidAmount: Float = 0f,
    /** Parametre komponentu (HP, spotreba…). */
    val powerHp: Float = 0f,
    val fuelUse: Float = 1f,
    val reliability: Float = 1f,
    val capacity: Float = 0f,
    val baseValue: Int = 10
)

object ItemCatalog {
    val FUEL_CAN = ItemDef(
        id = "fuel_can", name = "Benzín", rarity = ItemRarity.COMMON,
        weight = 4f, fluid = FluidType.FUEL, fluidAmount = 10f, baseValue = 15
    )
    val OIL_BOTTLE = ItemDef(
        id = "oil", name = "Motorový olej", rarity = ItemRarity.COMMON,
        weight = 2f, fluid = FluidType.OIL, fluidAmount = 2f, baseValue = 12
    )
    val COOLANT_BOTTLE = ItemDef(
        id = "coolant", name = "Chladiaca kvapalina", rarity = ItemRarity.COMMON,
        weight = 2f, fluid = FluidType.COOLANT, fluidAmount = 3f, baseValue = 12
    )
    val TIRE = ItemDef(
        id = "tire_std", name = "Pneumatiky (štandard)", rarity = ItemRarity.COMMON,
        weight = 8f, mountsTo = ComponentSlot.TIRES, reliability = 0.8f, baseValue = 25
    )
    val TIRE_GOOD = ItemDef(
        id = "tire_good", name = "Pneumatiky (kvalitné)", rarity = ItemRarity.UNCOMMON,
        weight = 9f, mountsTo = ComponentSlot.TIRES, reliability = 1.1f, baseValue = 45
    )
    val BATTERY = ItemDef(
        id = "battery_std", name = "Batéria 45Ah", rarity = ItemRarity.UNCOMMON,
        weight = 12f, mountsTo = ComponentSlot.BATTERY, capacity = 45f, reliability = 0.9f, baseValue = 40
    )
    val BATTERY_GOOD = ItemDef(
        id = "battery_good", name = "Batéria 70Ah", rarity = ItemRarity.RARE,
        weight = 15f, mountsTo = ComponentSlot.BATTERY, capacity = 70f, reliability = 1.15f, baseValue = 70
    )
    val ENGINE_A = ItemDef(
        id = "engine_a", name = "Motor A (60 HP)", rarity = ItemRarity.RARE,
        weight = 120f, mountsTo = ComponentSlot.ENGINE, powerHp = 60f,
        fuelUse = 1.35f, reliability = 0.7f, baseValue = 120
    )
    val ENGINE_B = ItemDef(
        id = "engine_b", name = "Motor B (95 HP)", rarity = ItemRarity.VERY_RARE,
        weight = 145f, mountsTo = ComponentSlot.ENGINE, powerHp = 95f,
        fuelUse = 1.0f, reliability = 1.1f, baseValue = 220
    )
    val RADIATOR = ItemDef(
        id = "radiator", name = "Chladič", rarity = ItemRarity.UNCOMMON,
        weight = 10f, mountsTo = ComponentSlot.RADIATOR, reliability = 1.0f, baseValue = 35
    )
    val RADIATOR_GOOD = ItemDef(
        id = "radiator_good", name = "Chladič XL", rarity = ItemRarity.RARE,
        weight = 14f, mountsTo = ComponentSlot.RADIATOR, reliability = 1.3f, baseValue = 65
    )
    val BRAKES = ItemDef(
        id = "brakes", name = "Brzdy", rarity = ItemRarity.UNCOMMON,
        weight = 6f, mountsTo = ComponentSlot.BRAKES, reliability = 1.0f, baseValue = 30
    )
    val BRAKES_GOOD = ItemDef(
        id = "brakes_good", name = "Športové brzdy", rarity = ItemRarity.RARE,
        weight = 7f, mountsTo = ComponentSlot.BRAKES, reliability = 1.35f, baseValue = 55
    )
    val FUEL_TANK = ItemDef(
        id = "tank_std", name = "Nádrž 45 L", rarity = ItemRarity.UNCOMMON,
        weight = 18f, mountsTo = ComponentSlot.FUEL_TANK, capacity = 45f, baseValue = 40
    )
    val FUEL_TANK_BIG = ItemDef(
        id = "tank_big", name = "Nádrž 70 L", rarity = ItemRarity.RARE,
        weight = 24f, mountsTo = ComponentSlot.FUEL_TANK, capacity = 70f, baseValue = 80
    )
    val ALTERNATOR = ItemDef(
        id = "alternator_basic", name = "Alternátor", rarity = ItemRarity.COMMON,
        weight = 5f, mountsTo = ComponentSlot.ALTERNATOR, reliability = 1f, baseValue = 20
    )
    val STARTER = ItemDef(
        id = "starter_basic", name = "Štartér", rarity = ItemRarity.COMMON,
        weight = 4f, mountsTo = ComponentSlot.STARTER, reliability = 1f, baseValue = 18
    )
    val SUSPENSION = ItemDef(
        id = "suspension_basic", name = "Pruženie", rarity = ItemRarity.COMMON,
        weight = 10f, mountsTo = ComponentSlot.SUSPENSION, reliability = 1f, baseValue = 22
    )
    val DOORS = ItemDef(
        id = "doors", name = "Dvere (pár)", rarity = ItemRarity.UNCOMMON,
        weight = 22f, mountsTo = ComponentSlot.DOORS, reliability = 1f, baseValue = 35
    )
    val HOOD = ItemDef(
        id = "hood", name = "Kapota", rarity = ItemRarity.COMMON,
        weight = 12f, mountsTo = ComponentSlot.HOOD, reliability = 1f, baseValue = 20
    )
    val WINDOWS = ItemDef(
        id = "windows", name = "Okná", rarity = ItemRarity.UNCOMMON,
        weight = 8f, mountsTo = ComponentSlot.WINDOWS, reliability = 1f, baseValue = 28
    )
    val FRONT_BUMPER = ItemDef(
        id = "bumper_f", name = "Predný nárazník", rarity = ItemRarity.COMMON,
        weight = 9f, mountsTo = ComponentSlot.FRONT_BUMPER, reliability = 1f, baseValue = 18
    )
    val REAR_BUMPER = ItemDef(
        id = "bumper_r", name = "Zadný nárazník", rarity = ItemRarity.COMMON,
        weight = 9f, mountsTo = ComponentSlot.REAR_BUMPER, reliability = 1f, baseValue = 18
    )

    val ALL = listOf(
        FUEL_CAN, OIL_BOTTLE, COOLANT_BOTTLE,
        TIRE, TIRE_GOOD, BATTERY, BATTERY_GOOD,
        ENGINE_A, ENGINE_B, RADIATOR, RADIATOR_GOOD,
        BRAKES, BRAKES_GOOD, FUEL_TANK, FUEL_TANK_BIG,
        ALTERNATOR, STARTER, SUSPENSION,
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
