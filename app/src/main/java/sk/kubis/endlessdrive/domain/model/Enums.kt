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

/**
 * Kvapaliny sa nekazia ako diely – bývajú riedené vodou alebo znečistené.
 * Čistota 0..1 určuje, čo kanister v skutočnosti obsahuje.
 */
enum class FluidGrade(val displayName: String, val minPurity: Float) {
    PURE("Čistá", 0.92f),
    SLIGHTLY_MIXED("Mierne riedená", 0.75f),
    WATERED("Zmiešaná s vodou", 0.55f),
    SLUDGE("Skoro voda / kal", 0f);

    companion object {
        fun of(purity: Float): FluidGrade = when {
            purity >= PURE.minPurity -> PURE
            purity >= SLIGHTLY_MIXED.minPurity -> SLIGHTLY_MIXED
            purity >= WATERED.minPurity -> WATERED
            else -> SLUDGE
        }
    }
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
    // Hint je nálada, nie zoznam – čo je za zákrutou, sa hráč dozvie až tam.
    SAFE_RURAL(
        "Vidiek", "Polia, ploty a ticho",
        BiomeType.RURAL, 0.85f, 0.10f, 1.15f, 0.70f, 0.55f, 1.15f
    ),
    INDUSTRIAL(
        "Priemysel", "Dym na obzore",
        BiomeType.INDUSTRIAL, 1.35f, 0.18f, 1.28f, 0.90f, 0.90f, 1.0f
    ),
    SHORTCUT_RISK(
        "Skratka", "Neoznačená odbočka",
        BiomeType.WASTELAND, 1.65f, 0.28f, 1.45f, 1.15f, 0.50f, 0.78f
    )
}

/**
 * Úsek trate vo vnútri segmentu. Mení terén aj jazdné vlastnosti,
 * takže cesta nie je jedna dlhá jednotvárna rovinka.
 */
enum class RoadFeature(
    val displayName: String,
    val warning: String,
    /** Násobiteľ členitosti terénu v úseku. */
    val hillMul: Float,
    /** Extra hrboľatosť (poškodzuje pneumatiky a spomaľuje). */
    val roughness: Float,
    /** Strop rýchlosti v úseku (m/s), 0 = bez obmedzenia. */
    val speedCap: Float
) {
    STRAIGHT("Rovinka", "", 0.6f, 0f, 0f),
    HILLS("Kopce", "Kopce — sleduj teplotu", 1.35f, 0.12f, 0f),
    SWITCHBACK("Serpentíny", "Serpentíny — pomaly", 1.05f, 0.28f, 15f),
    BROKEN("Rozbitá cesta", "Rozbitá cesta — spomaľ", 0.85f, 1.0f, 11f),
    // Most nemení terén, len naň položí rovnú mostovku.
    BRIDGE("Most", "Most — drž sa stredu", 0.9f, 0.05f, 18f)
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
