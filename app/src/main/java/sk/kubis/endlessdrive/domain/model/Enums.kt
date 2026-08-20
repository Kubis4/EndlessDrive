package sk.kubis.endlessdrive.domain.model

enum class ItemRarity { COMMON, UNCOMMON, RARE, VERY_RARE, LEGENDARY }

enum class FluidType(val displayName: String, val unit: String) {
    FUEL("Petrol", "L"),
    OIL("Engine oil", "L"),
    COOLANT("Coolant", "L"),
    BRAKE_FLUID("Brake fluid", "L")
}

enum class ComponentSlot(val displayName: String, val group: String) {
    ENGINE("Engine", "Engine"),
    ALTERNATOR("Alternator", "Engine"),
    STARTER("Starter", "Engine"),
    RADIATOR("Radiator", "Engine"),
    BATTERY("Battery", "Electrics"),
    FUEL_TANK("Fuel tank", "Fuel"),
    BRAKES("Brakes", "Chassis"),
    TIRE_FRONT("Front tyre", "Wheels"),
    TIRE_REAR("Rear tyre", "Wheels"),
    DRIVETRAIN("Drivetrain", "Chassis"),
    SUSPENSION("Suspension", "Chassis"),
    DOORS("Doors", "Body"),
    HOOD("Hood", "Body"),
    WINDOWS("Windows", "Body"),
    /** Sedadlá – vidno ich cez okná aj cez prázdne dverné otvory. */
    SEAT_FRONT("Front seat", "Interior"),
    SEAT_REAR("Rear seat", "Interior"),
    /** Veko kufra – bez neho je zadok otvorený. */
    TRUNK_LID("Boot lid", "Body"),
    /** Bez svetlometu sa v noci nedá svietiť, nech je batéria akokoľvek plná. */
    HEADLIGHT("Headlight", "Body"),
    TAILLIGHT("Tail light", "Body"),
    FRONT_BUMPER("Front bumper", "Body"),
    REAR_BUMPER("Rear bumper", "Body"),
    /** Reťaze na kolesách – v zime pomáhajú, na suchu prekážajú. */
    CHAINS("Snow chains", "Wheels"),
    /** Batoh alebo debna v kufri – miesto navyše. */
    CARGO("Cargo box", "Storage"),
    /** Strešný nosič – miesto navyše, ale aj odpor vzduchu. Vidno ho na aute. */
    ROOF_RACK("Roof rack", "Storage")
}

/** Pneumatikové sloty – predok a zadok zvlášť. */
val TIRE_SLOTS = listOf(ComponentSlot.TIRE_FRONT, ComponentSlot.TIRE_REAR)

/**
 * Povrch, po ktorom sa práve ide. Asfalt je norma, ostatné sú prekážky,
 * ktoré vidno na ceste dopredu – dá sa spomaliť alebo to vziať s rozbehom.
 *
 * @param gripMul násobiteľ priľnavosti (μ)
 * @param rollDrag valivý odpor navyše (m/s² pri plnej rýchlosti)
 * @param chip štítok do HUD; null = nič sa nehlási
 */
enum class RoadSurface(
    val displayName: String,
    val gripMul: Float,
    val rollDrag: Float,
    val chip: String? = null
) {
    ASPHALT("Tarmac", 1f, 0f),
    ICE("Black ice", 0.34f, 0.10f, "ICE"),
    SLUSH("Slush", 0.58f, 1.40f, "SLUSH"),
    GRAVEL("Gravel", 0.84f, 0.45f, "GRAVEL"),
    SAND("Sand", 0.66f, 1.60f, "SAND"),
    MUD("Mud", 0.55f, 2.10f, "MUD"),
    WATER("Water", 0.72f, 1.70f, "WATER");

    val hazard: Boolean get() = this != ASPHALT
}

/**
 * Z čoho je postavená celá vetva. Každá odbočka vyzerá inak už na prvý pohľad –
 * niekde asfalt, inde len vyjazdená hlina alebo zaviaty piesok.
 *
 * @param gripMul základná priľnavosť povrchu (naplaveniny sa rátajú navyše)
 * @param rutted true = vyjazdené koľaje namiesto súvislej vozovky
 */
enum class RoadPaving(
    val displayName: String,
    val gripMul: Float,
    val rutted: Boolean
) {
    ASPHALT("Asphalt", 1.00f, false),
    CRACKED("Cracked asphalt", 0.97f, false),
    CONCRETE("Concrete slabs", 0.99f, false),
    // Nespevnené povrchy uberajú grip len mierne – celá vetva sa musí dať prejsť
    // aj so štartovným autom, rozdiel má byť cítiť, nie blokovať.
    DIRT("Dirt track", 0.94f, true),
    GRAVEL_ROAD("Gravel road", 0.92f, true),
    SAND_TRACK("Sand track", 0.88f, true),
    /** Zasnežená cesta – prichádza až v neskoršej časti jazdy. */
    SNOW("Snow", 0.40f, true),
    PACKED_SNOW("Packed snow", 0.50f, true);

    /** true = mrzne; auto rieši chlad, gumy zimnú výbavu. */
    val winter: Boolean get() = this == SNOW || this == PACKED_SNOW
}

/** Pohon – koľko náprav prenáša ťah. */
enum class DriveLayout(val displayName: String) {
    RWD("RWD"),
    FWD("FWD"),
    AWD("4×4")
}

/**
 * Kvapaliny sa nekazia ako diely – bývajú riedené vodou alebo znečistené.
 * Čistota 0..1 určuje, čo kanister v skutočnosti obsahuje.
 */
enum class FluidGrade(val displayName: String, val minPurity: Float) {
    PURE("Clean", 0.92f),
    SLIGHTLY_MIXED("Slightly diluted", 0.75f),
    WATERED("Watered down", 0.55f),
    SLUDGE("Mostly water / sludge", 0f);

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
    NEW("New", 1.0f),
    USED("Used", 0.75f),
    DAMAGED("Damaged", 0.45f),
    CRITICAL("Critical", 0.20f)
}

enum class BuildingType(val displayName: String) {
    HOUSE("House"),
    GARAGE("Garage"),
    GAS_STATION("Petrol station"),
    AUTO_SHOP("Repair shop")
}

enum class BiomeType(val displayName: String) {
    RURAL("Countryside"),
    INDUSTRIAL("Industry"),
    WASTELAND("Wasteland"),
    DESERT("Desert"),
    /** Tá istá púšť po západe slnka – stolové hory proti fialovej oblohe. */
    DESERT_DUSK("Mesa"),
    /** Uschnutý les v hmle – kmene bez ihličia, všetko sivé. */
    FOREST("Dead forest"),
    /** Živý les – zeleň a modrá obloha, jediný bióm, kde je vidieť ďaleko. */
    FOREST_ALIVE("Living forest"),
    /** Púšť, do ktorej sa oprel vietor – vidieť je sotva na pár desiatok metrov. */
    SANDSTORM("Sandstorm"),
    /** Prachová stena nad mestom – v hnedej mrákave presvitajú domy. */
    DUST_STORM("Dust storm");

    /** Suché biómy – iná paleta zeme aj iná vzdušná perspektíva. */
    val arid: Boolean
        get() = this == DESERT || this == DESERT_DUSK ||
            this == SANDSTORM || this == DUST_STORM

    /** Les v ktoromkoľvek stave – hustá kulisa kmeňov po oboch stranách. */
    val wooded: Boolean get() = this == FOREST || this == FOREST_ALIVE
}

/**
 * Typ vetvy na križovatke – ovplyvňuje terén, loot, budovy a riziko.
 *
 * @param accentArgb farba vetvy naprieč celou hrou (panel križovatky, smerovka,
 *   náhľad odbočky) – aby sa jedna vetva nikdy nekreslila dvoma farbami
 * @param unlockDistance od akej vzdialenosti sa vetva vôbec ponúka
 * @param pickWeight relatívna šanca, že vetva na križovatke padne
 */
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
    val lengthMul: Float,
    val accentArgb: Long,
    val unlockDistance: Float = 0f,
    val pickWeight: Float = 1f
) {
    // Hint je nálada, nie zoznam – čo je za zákrutou, sa hráč dozvie až tam.
    SAFE_RURAL(
        "Countryside", "Fields, fences and silence",
        BiomeType.RURAL, 0.85f, 0.12f, 1.15f, 0.72f, 0.60f, 1.20f,
        accentArgb = 0xFF6B8F5A, pickWeight = 1.15f
    ),
    FOREST(
        "Dead forest", "Bare trunks and no birdsong",
        BiomeType.FOREST, 0.95f, 0.16f, 1.12f, 0.95f, 0.45f, 1.15f,
        accentArgb = 0xFF6E7A6A, pickWeight = 0.95f
    ),
    // Živý les je tá istá cesta v lepšom stave – viac tieňa, menej sucha.
    FOREST_ALIVE(
        "Living forest", "Cool shade between the trunks",
        BiomeType.FOREST_ALIVE, 0.95f, 0.15f, 1.10f, 0.92f, 0.50f, 1.15f,
        accentArgb = 0xFF3F7A4E, pickWeight = 1.05f
    ),
    INDUSTRIAL(
        "Industry", "Smoke on the horizon",
        BiomeType.INDUSTRIAL, 1.25f, 0.18f, 1.28f, 0.88f, 0.85f, 1.05f,
        accentArgb = 0xFF6A7A8A, pickWeight = 1.0f
    ),
    DESERT(
        "Desert", "Heat over an empty road",
        BiomeType.DESERT, 1.20f, 0.22f, 1.38f, 0.82f, 0.35f, 1.10f,
        accentArgb = 0xFFC9924A, unlockDistance = 1200f, pickWeight = 0.95f
    ),
    // Tá istá púšť, ale po západe – chladnejšie, tmavšie, menej vidieť.
    DESERT_DUSK(
        "Mesa", "Sunset over the plateaus",
        BiomeType.DESERT_DUSK, 1.25f, 0.24f, 1.32f, 0.86f, 0.30f, 1.10f,
        accentArgb = 0xFF9A5F6E, unlockDistance = 1800f, pickWeight = 0.85f
    ),
    // „Shortcut“ mýlilo – hráč nikam neskracuje, ide po horšej ceste.
    SHORTCUT_RISK(
        "Backroad", "Unmarked and unmaintained",
        BiomeType.WASTELAND, 1.50f, 0.28f, 1.45f, 1.05f, 0.55f, 0.85f,
        accentArgb = 0xFFB85C38, pickWeight = 1.0f
    ),
    SANDSTORM(
        "Sandstorm", "The horizon is gone",
        BiomeType.SANDSTORM, 1.70f, 0.30f, 1.60f, 0.95f, 0.40f, 0.80f,
        accentArgb = 0xFFA8763C, unlockDistance = 3000f, pickWeight = 0.70f
    ),
    // Búrka nad mestom – v prachu presvitajú domy, takže je čo prehľadať.
    DUST_STORM(
        "Dust storm", "Somewhere in there was a town",
        BiomeType.DUST_STORM, 1.65f, 0.30f, 1.58f, 0.90f, 0.95f, 0.80f,
        accentArgb = 0xFFB08A4E, unlockDistance = 3600f, pickWeight = 0.60f
    );

    /** Piesok si vyberá daň – vetvy, kde je vidieť horšie a všetko drie. */
    val arid: Boolean get() = biome.arid
}

/**
 * Úsek trate vo vnútri segmentu. Mení terén aj jazdné vlastnosti,
 * takže cesta nie je jedna dlhá jednotvárna rovinka.
 */
enum class RoadFeature(
    val displayName: String,
    /** Násobiteľ členitosti terénu v úseku. */
    val hillMul: Float,
    /** Extra hrboľatosť (poškodzuje pneumatiky a spomaľuje). */
    val roughness: Float,
    /** Strop rýchlosti v úseku (m/s), 0 = bez obmedzenia. */
    val speedCap: Float
) {
    STRAIGHT("Straight", 0.45f, 0f, 0f),
    HILLS("Hills", 1.15f, 0.12f, 0f),
    /** Prudký hrebeň – rozbeh a skok ako v Hill Climb. */
    CREST("Crests", 1.40f, 0.16f, 0f),
    /** Hlboké preliačiny medzi kopcami. */
    RAVINE("Ravines", 1.30f, 0.20f, 14f),
    SWITCHBACK("Switchbacks", 1.05f, 0.28f, 14f),
    BROKEN("Broken road", 0.90f, 1.0f, 11f),
    // Most nemení terén, len naň položí rovnú mostovku. Strop rýchlosti nemá –
    // bola to neviditeľná brzda odôvodnená radou „drž sa v strede“, ktorá
    // v hre bez riadenia nedávala zmysel.
    BRIDGE("Bridge", 0.85f, 0.05f, 0f)
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
    OUT_OF_FUEL("Ran out of fuel"),
    ENGINE_DESTROYED("Engine destroyed"),
    OVERHEAT("Engine overheated"),
    BATTERY_DEAD("The battery is dead"),
    MANUAL("Run over")
}

/** Plechy a interiér – to, čo na aute vidno a čo sa musí nájsť. */
val BODY_SLOTS = listOf(
    ComponentSlot.SEAT_FRONT,
    ComponentSlot.SEAT_REAR,
    ComponentSlot.DOORS,
    ComponentSlot.HOOD,
    ComponentSlot.WINDOWS,
    ComponentSlot.TRUNK_LID,
    ComponentSlot.HEADLIGHT,
    ComponentSlot.TAILLIGHT,
    ComponentSlot.FRONT_BUMPER,
    ComponentSlot.REAR_BUMPER,
    // Nosič je doplnok, ale kreslí sa ako diel – pri ladení polôh nech je vidieť.
    ComponentSlot.ROOF_RACK
)
