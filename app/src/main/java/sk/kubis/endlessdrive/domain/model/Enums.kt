package sk.kubis.endlessdrive.domain.model

enum class ItemRarity { COMMON, UNCOMMON, RARE, VERY_RARE, LEGENDARY }

enum class FluidType(val displayName: String, val unit: String) {
    FUEL("Benzín", "L"),
    OIL("Motorový olej", "L"),
    COOLANT("Chladiaca kvapalina", "L"),
    BRAKE_FLUID("Brzdová kvapalina", "L")
}

enum class ComponentSlot(val displayName: String, val group: String) {
    ENGINE("Motor", "Motor"),
    ALTERNATOR("Alternátor", "Motor"),
    STARTER("Štartér", "Motor"),
    RADIATOR("Chladič", "Motor"),
    BATTERY("Batéria", "Elektrika"),
    FUEL_TANK("Nádrž", "Palivo"),
    BRAKES("Brzdy", "Podvozok"),
    TIRES("Pneumatiky", "Kolesá"),
    SUSPENSION("Pruženie", "Podvozok"),
    DOORS("Dvere", "Karoséria"),
    HOOD("Kapota", "Karoséria"),
    WINDOWS("Okná", "Karoséria"),
    FRONT_BUMPER("Predný nárazník", "Karoséria"),
    REAR_BUMPER("Zadný nárazník", "Karoséria")
}

enum class ComponentCondition(val displayName: String, val maxHealth: Float) {
    NEW("Nový", 1.0f),
    USED("Použitý", 0.75f),
    DAMAGED("Poškodený", 0.45f),
    CRITICAL("Kritický", 0.20f)
}

enum class BuildingType(val displayName: String) {
    HOUSE("Dom"),
    GARAGE("Garáž"),
    GAS_STATION("Benzínová stanica"),
    AUTO_SHOP("Autoservis")
}

enum class BiomeType(val displayName: String) {
    RURAL("Vidiek"),
    INDUSTRIAL("Priemysel"),
    WASTELAND("Opustená krajina")
}

/** Typ vetvy na križovatke – ovplyvňuje terén, loot, budovy a riziko. */
enum class BranchStyle(
    val label: String,
    val hint: String,
    val biome: BiomeType,
    val lootBias: Float,
    val bumpiness: Float,
    val fuelDrainMul: Float,
    /** HillRush-štýl členitosť kopcov. */
    val hillChallenge: Float,
    /** 0 = takmer žiadne budovy, 1 = častejšie. */
    val buildingDensity: Float,
    /** Násobiteľ dĺžky segmentu. */
    val lengthMul: Float
) {
    SAFE_RURAL(
        "Vidiek", "Dlhá cesta · menej budov · mierne kopce",
        BiomeType.RURAL, 0.85f, 0.10f, 1.15f, 0.70f, 0.55f, 1.15f
    ),
    INDUSTRIAL(
        "Priemysel", "Horšia cesta · viac servisov · stredné kopce",
        BiomeType.INDUSTRIAL, 1.35f, 0.18f, 1.28f, 0.90f, 0.90f, 1.0f
    ),
    SHORTCUT_RISK(
        "Skratka", "Kratšia · strmé kopce · vzácny loot",
        BiomeType.WASTELAND, 1.65f, 0.28f, 1.45f, 1.15f, 0.50f, 0.78f
    )
}

enum class GamePhase {
    PREP,
    DRIVING,
    STOPPED,
    EXPLORING,
    /** Pri križovatke – hráč volí vetvu. */
    JUNCTION,
    GAME_OVER
}

enum class EndReason(val message: String) {
    OUT_OF_FUEL("Došlo palivo"),
    ENGINE_DESTROYED("Motor sa zničil"),
    OVERHEAT("Motor sa prehrial"),
    BATTERY_DEAD("Batéria je vybitá"),
    MANUAL("Koniec jazdy")
}
