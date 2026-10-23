package sk.kubis.endlessdrive.domain.repository

import kotlinx.coroutines.flow.Flow
import sk.kubis.endlessdrive.domain.model.AppLanguage
import sk.kubis.endlessdrive.domain.model.DebugOptions
import sk.kubis.endlessdrive.domain.model.GaugeStyle
import sk.kubis.endlessdrive.domain.model.ThrottleMode

data class PlayerProfile(
    val nickname: String = "",
    val countryCode: String = "SK",
    val bestDistanceKm: Float = 0f,
    val bestTimeSeconds: Float = 0f,
    val totalRuns: Int = 0,
    val totalDistanceKm: Float = 0f,
    /** Šrot prenesený medzi jazdami – používa sa na dlhodobé ciele. */
    val bankedScrap: Int = 0,
    /** Počet obnovených rádiových relé v meta-progrese. */
    val relayNodes: Int = 0,
    /** Tutorial sa po prvom dokončení už nevnucuje, z menu sa dá vždy zopakovať. */
    val tutorialCompleted: Boolean = false,
    val garageUpgrades: GarageUpgrades = GarageUpgrades()
)

data class GarageUpgrades(
    val fuelReserve: Int = 0,
    val reliability: Int = 0,
    val storage: Int = 0,
    val starterTyres: Int = 0,
    val electrics: Int = 0,
    val oilReserve: Int = 0,
    val spareKit: Int = 0,
    val engineTune: Int = 0,
    val loadSuspension: Int = 0,
    val brakePackage: Int = 0,
    val cruiseEfficiency: Int = 0
) {
    fun levelOf(upgrade: GarageUpgrade): Int = when (upgrade) {
        GarageUpgrade.FUEL_RESERVE -> fuelReserve
        GarageUpgrade.RELIABILITY -> reliability
        GarageUpgrade.STORAGE -> storage
        GarageUpgrade.STARTER_TYRES -> starterTyres
        GarageUpgrade.ELECTRICS -> electrics
        GarageUpgrade.OIL_RESERVE -> oilReserve
        GarageUpgrade.SPARE_KIT -> spareKit
        GarageUpgrade.ENGINE_TUNE -> engineTune
        GarageUpgrade.LOAD_SUSPENSION -> loadSuspension
        GarageUpgrade.BRAKE_PACKAGE -> brakePackage
        GarageUpgrade.CRUISE_EFFICIENCY -> cruiseEfficiency
    }
}

enum class GarageUpgrade(
    val title: String,
    val detail: String,
    val maxLevel: Int,
    private val costs: List<Int>
) {
    FUEL_RESERVE(
        "Fuel reserve",
        "Start every new run with 3 more litres of the correct fuel per level.",
        3,
        listOf(20, 45, 80)
    ),
    OIL_RESERVE(
        "Oil cache",
        "Start every new run with 1.2 more litres of oil per level.",
        2,
        listOf(70, 140)
    ),
    RELIABILITY(
        "Workshop care",
        "Starting mechanical parts gain 5% condition per level.",
        3,
        listOf(25, 55, 95)
    ),
    STORAGE(
        "Field pack",
        "Adds one permanent pack slot and 4 kg capacity per level.",
        3,
        listOf(20, 50, 90)
    ),
    STARTER_TYRES(
        "Starter tyres",
        "Leave in standard rubber instead of worn poor tyres. Level 2 starts them healthier.",
        2,
        listOf(90, 175)
    ),
    ELECTRICS(
        "Charged electrics",
        "Guarantees a battery and starter. Level 2 also fits an alternator and extra charge.",
        2,
        listOf(80, 160)
    ),
    SPARE_KIT(
        "Road kit",
        "Pack a puncture kit for every new run. Level 2 also packs a coolant bottle.",
        2,
        listOf(85, 165)
    ),
    ENGINE_TUNE(
        "Load tune",
        "Adds 6% engine pull per level, especially useful with a full boot.",
        3,
        listOf(90, 180, 310)
    ),
    LOAD_SUSPENSION(
        "Load suspension",
        "Stiffens springs by 10% and damping by 8% per level.",
        3,
        listOf(75, 155, 280)
    ),
    BRAKE_PACKAGE(
        "Brake package",
        "Adds 10% braking capacity per level so a loaded car stops more safely.",
        3,
        listOf(70, 145, 260)
    ),
    CRUISE_EFFICIENCY(
        "Cruise tuning",
        "Cruise control burns 1.5× throttle fuel by default. Each level cuts that waste by 0.15×.",
        3,
        listOf(85, 170, 290)
    );

    fun costAt(level: Int): Int? = costs.getOrNull(level)
}

interface PlayerRepository {
    val profile: Flow<PlayerProfile>
    suspend fun current(): PlayerProfile
    suspend fun recordRun(distanceKm: Float)

    /** Zaznamená výsledok jazdy vrátane času použiteľného v rebríčku. */
    suspend fun recordRunResult(distanceKm: Float, timeSeconds: Float) {
        recordRun(distanceKm)
    }

    /** Lokálny profil hráča; cloud/Play Games väzba sa doplní neskôr. */
    suspend fun savePlayerIdentity(nickname: String, countryCode: String) = Unit

    /** Uloží šrot z ukončenej jazdy do dlhodobého skladu. */
    suspend fun bankScrap(amount: Int) = Unit

    /** Meta-progres je high-water mark, nikdy sa neznižuje. */
    suspend fun recordRelayProgress(relayNodes: Int) = Unit

    suspend fun setTutorialCompleted(completed: Boolean) = Unit

    /** Atomicky odčíta banked scrap a zvýši vybraný permanentný upgrade. */
    suspend fun buyGarageUpgrade(upgrade: GarageUpgrade): Boolean = false

    /** Rozohraná jazda ako text z RunCodec-u; null = žiadna. */
    suspend fun loadRun(): String?
    suspend fun saveRun(data: String)
    suspend fun clearRun()

    /** Ladiace prepínače z nastavení – držia sa medzi spusteniami. */
    val debugOptions: Flow<DebugOptions>
    suspend fun setDebugOptions(options: DebugOptions)

    /** Schéma plynu: binárny pedál alebo zvislý slide. */
    val throttleMode: Flow<ThrottleMode>
    suspend fun setThrottleMode(mode: ThrottleMode)

    /** Vzhľad budíkov v HUD. */
    val gaugeStyle: Flow<GaugeStyle>
    suspend fun setGaugeStyle(style: GaugeStyle)

    /** Veľkosť menu a HUD (0.8–1.3). */
    val uiScale: Flow<Float>
    suspend fun setUiScale(scale: Float)

    /** Jazyk menu, HUD a hlášok počas jazdy. */
    val appLanguage: Flow<AppLanguage>
    suspend fun setAppLanguage(language: AppLanguage)
}
