package sk.kubis.endlessdrive.domain.repository

import kotlinx.coroutines.flow.Flow
import sk.kubis.endlessdrive.domain.model.DebugOptions
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
    val relayNodes: Int = 0
)

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
}
