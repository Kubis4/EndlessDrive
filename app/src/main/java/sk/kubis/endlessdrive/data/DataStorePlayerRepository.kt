package sk.kubis.endlessdrive.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import sk.kubis.endlessdrive.domain.model.DebugOptions
import sk.kubis.endlessdrive.domain.model.ThrottleMode
import sk.kubis.endlessdrive.domain.repository.PlayerProfile
import sk.kubis.endlessdrive.domain.repository.PlayerRepository
import sk.kubis.endlessdrive.game.Journey

private val Context.dataStore by preferencesDataStore("endless_drive")

class DataStorePlayerRepository(context: Context) : PlayerRepository {
    private val store = context.applicationContext.dataStore

    private object Keys {
        val BEST = floatPreferencesKey("best_km")
        val BEST_TIME = floatPreferencesKey("best_time_seconds")
        val RUNS = intPreferencesKey("total_runs")
        val TOTAL = floatPreferencesKey("total_km")
        val BANKED_SCRAP = intPreferencesKey("banked_scrap")
        val RELAY_NODES = intPreferencesKey("relay_nodes")
        val RUN = stringPreferencesKey("run_snapshot")
        val DBG_ALL = booleanPreferencesKey("debug_all_components")
        val DBG_UPGRADE = booleanPreferencesKey("debug_full_upgrades")
        val DBG_BODY = booleanPreferencesKey("debug_full_body")
        val DBG_FLUIDS = booleanPreferencesKey("debug_full_fluids")
        val DBG_TRACK = booleanPreferencesKey("debug_test_track")
        val DBG_REPAIR = booleanPreferencesKey("debug_repair_controls")
        val THROTTLE_MODE = stringPreferencesKey("throttle_mode")
        val NICKNAME = stringPreferencesKey("player_nickname")
        val COUNTRY_CODE = stringPreferencesKey("player_country_code")
    }

    override val debugOptions: Flow<DebugOptions> = store.data.map { prefs ->
        DebugOptions(
            allComponents = prefs[Keys.DBG_ALL] ?: false,
            fullUpgrades = prefs[Keys.DBG_UPGRADE] ?: false,
            fullBody = prefs[Keys.DBG_BODY] ?: false,
            fullFluids = prefs[Keys.DBG_FLUIDS] ?: false,
            testTrack = prefs[Keys.DBG_TRACK] ?: false,
            repairControls = prefs[Keys.DBG_REPAIR] ?: false
        )
    }.distinctUntilChanged()

    override suspend fun setDebugOptions(options: DebugOptions) {
        store.edit { prefs ->
            prefs[Keys.DBG_ALL] = options.allComponents
            prefs[Keys.DBG_UPGRADE] = options.fullUpgrades
            prefs[Keys.DBG_BODY] = options.fullBody
            prefs[Keys.DBG_FLUIDS] = options.fullFluids
            prefs[Keys.DBG_TRACK] = options.testTrack
            prefs[Keys.DBG_REPAIR] = options.repairControls
        }
    }

    override val throttleMode: Flow<ThrottleMode> = store.data.map { prefs ->
        ThrottleMode.fromStored(prefs[Keys.THROTTLE_MODE])
    }.distinctUntilChanged()

    override suspend fun setThrottleMode(mode: ThrottleMode) {
        store.edit { prefs ->
            prefs[Keys.THROTTLE_MODE] = mode.name
        }
    }

    override val profile: Flow<PlayerProfile> = store.data.map { prefs ->
        PlayerProfile(
            nickname = prefs[Keys.NICKNAME] ?: "",
            countryCode = prefs[Keys.COUNTRY_CODE] ?: "SK",
            bestDistanceKm = prefs[Keys.BEST] ?: 0f,
            bestTimeSeconds = prefs[Keys.BEST_TIME] ?: 0f,
            totalRuns = prefs[Keys.RUNS] ?: 0,
            totalDistanceKm = prefs[Keys.TOTAL] ?: 0f,
            bankedScrap = prefs[Keys.BANKED_SCRAP] ?: 0,
            relayNodes = prefs[Keys.RELAY_NODES] ?: 0
        )
    }.distinctUntilChanged()


    override suspend fun current(): PlayerProfile = profile.first()

    override suspend fun recordRun(distanceKm: Float) {
        store.edit { prefs ->
            val best = prefs[Keys.BEST] ?: 0f
            if (distanceKm > best) prefs[Keys.BEST] = distanceKm
            prefs[Keys.RUNS] = (prefs[Keys.RUNS] ?: 0) + 1
            prefs[Keys.TOTAL] = (prefs[Keys.TOTAL] ?: 0f) + distanceKm
        }
    }

    override suspend fun recordRunResult(distanceKm: Float, timeSeconds: Float) {
        store.edit { prefs ->
            val best = prefs[Keys.BEST] ?: 0f
            if (distanceKm > best) {
                prefs[Keys.BEST] = distanceKm
                if (timeSeconds > 0f) prefs[Keys.BEST_TIME] = timeSeconds
            }
            prefs[Keys.RUNS] = (prefs[Keys.RUNS] ?: 0) + 1
            prefs[Keys.TOTAL] = (prefs[Keys.TOTAL] ?: 0f) + distanceKm
        }
    }

    override suspend fun savePlayerIdentity(nickname: String, countryCode: String) {
        store.edit { prefs ->
            prefs[Keys.NICKNAME] = nickname.trim().take(18)
            prefs[Keys.COUNTRY_CODE] = countryCode.trim().uppercase().take(2)
        }
    }

    override suspend fun bankScrap(amount: Int) {
        if (amount <= 0) return
        store.edit { prefs ->
            prefs[Keys.BANKED_SCRAP] = (prefs[Keys.BANKED_SCRAP] ?: 0) + amount
        }
    }

    override suspend fun recordRelayProgress(relayNodes: Int) {
        store.edit { prefs ->
            val current = prefs[Keys.RELAY_NODES] ?: 0
            prefs[Keys.RELAY_NODES] = maxOf(current, relayNodes.coerceIn(0, Journey.goals.size))
        }
    }

    override suspend fun loadRun(): String? = store.data.first()[Keys.RUN]

    override suspend fun saveRun(data: String) {
        store.edit { it[Keys.RUN] = data }
    }

    override suspend fun clearRun() {
        store.edit { it.remove(Keys.RUN) }
    }
}
