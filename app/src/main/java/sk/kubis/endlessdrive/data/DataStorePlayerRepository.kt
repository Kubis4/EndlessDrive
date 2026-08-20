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
import sk.kubis.endlessdrive.domain.repository.PlayerProfile
import sk.kubis.endlessdrive.domain.repository.PlayerRepository

private val Context.dataStore by preferencesDataStore("endless_drive")

class DataStorePlayerRepository(context: Context) : PlayerRepository {
    private val store = context.applicationContext.dataStore

    private object Keys {
        val BEST = floatPreferencesKey("best_km")
        val RUNS = intPreferencesKey("total_runs")
        val TOTAL = floatPreferencesKey("total_km")
        val RUN = stringPreferencesKey("run_snapshot")
        val DBG_ALL = booleanPreferencesKey("debug_all_components")
        val DBG_UPGRADE = booleanPreferencesKey("debug_full_upgrades")
        val DBG_BODY = booleanPreferencesKey("debug_full_body")
        val DBG_FLUIDS = booleanPreferencesKey("debug_full_fluids")
    }

    override val debugOptions: Flow<DebugOptions> = store.data.map { prefs ->
        DebugOptions(
            allComponents = prefs[Keys.DBG_ALL] ?: false,
            fullUpgrades = prefs[Keys.DBG_UPGRADE] ?: false,
            fullBody = prefs[Keys.DBG_BODY] ?: false,
            fullFluids = prefs[Keys.DBG_FLUIDS] ?: false
        )
    }.distinctUntilChanged()

    override suspend fun setDebugOptions(options: DebugOptions) {
        store.edit { prefs ->
            prefs[Keys.DBG_ALL] = options.allComponents
            prefs[Keys.DBG_UPGRADE] = options.fullUpgrades
            prefs[Keys.DBG_BODY] = options.fullBody
            prefs[Keys.DBG_FLUIDS] = options.fullFluids
        }
    }

    override val profile: Flow<PlayerProfile> = store.data.map { prefs ->
        PlayerProfile(
            bestDistanceKm = prefs[Keys.BEST] ?: 0f,
            totalRuns = prefs[Keys.RUNS] ?: 0,
            totalDistanceKm = prefs[Keys.TOTAL] ?: 0f
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

    override suspend fun loadRun(): String? = store.data.first()[Keys.RUN]

    override suspend fun saveRun(data: String) {
        store.edit { it[Keys.RUN] = data }
    }

    override suspend fun clearRun() {
        store.edit { it.remove(Keys.RUN) }
    }
}
