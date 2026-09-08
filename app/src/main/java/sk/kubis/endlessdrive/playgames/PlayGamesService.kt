package sk.kubis.endlessdrive.playgames

import android.app.Activity
import android.content.Context
import com.google.android.gms.games.PlayGames
import com.google.android.gms.games.PlayGamesSdk
import com.google.android.gms.games.leaderboard.LeaderboardVariant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import sk.kubis.endlessdrive.R
import sk.kubis.endlessdrive.domain.model.EndReason
import sk.kubis.endlessdrive.game.event.RoadEvent
import sk.kubis.endlessdrive.ui.leaderboard.LeaderboardEntry
import sk.kubis.endlessdrive.ui.leaderboard.LeaderboardTab
import kotlin.math.roundToLong

data class PlayGamesState(
    val configured: Boolean = false,
    val signedIn: Boolean = false,
    val playerName: String? = null,
    val error: String? = null
)

/**
 * Play Games v2 adapter. Missing Play Console IDs deliberately degrade to
 * offline mode so local development and sideloaded builds keep working.
 */
class PlayGamesService(context: Context) {
    private val appContext = context.applicationContext
    private val projectId = appContext.getString(R.string.game_services_project_id)
    private val distanceLeaderboardId = appContext.getString(R.string.leaderboard_distance_id)
    private val distanceAchievements = listOf(
        DistanceAchievement(appContext.getString(R.string.achievement_first_steps_id), 1f),
        DistanceAchievement(appContext.getString(R.string.achievement_snowline_id), 10f),
        DistanceAchievement(appContext.getString(R.string.achievement_long_haul_id), 20f),
        DistanceAchievement(appContext.getString(R.string.achievement_halfway_id), 50f),
        DistanceAchievement(appContext.getString(R.string.achievement_safe_haven_id), 100f),
        DistanceAchievement(appContext.getString(R.string.achievement_endless_500_id), 500f)
    )
    private val requestedAchievementIds = mutableSetOf<String>()

    val isConfigured: Boolean = projectId.isUsablePlayGamesValue()
    private val _state = MutableStateFlow(PlayGamesState(configured = isConfigured))
    val state: StateFlow<PlayGamesState> = _state.asStateFlow()

    init {
        if (isConfigured) PlayGamesSdk.initialize(appContext)
    }

    fun connect(activity: Activity) {
        if (!isConfigured || _state.value.signedIn) return
        val signInClient = PlayGames.getGamesSignInClient(activity)
        signInClient.isAuthenticated().addOnCompleteListener { task ->
            val authenticated = task.isSuccessful && task.result?.isAuthenticated == true
            if (authenticated) {
                loadCurrentPlayer(activity)
            } else {
                signInClient.signIn().addOnCompleteListener { signInTask ->
                    if (signInTask.isSuccessful && signInTask.result?.isAuthenticated == true) {
                        loadCurrentPlayer(activity)
                    } else {
                        _state.value = _state.value.copy(error = "Play Games sign-in unavailable")
                    }
                }
            }
        }
    }

    private fun loadCurrentPlayer(activity: Activity) {
        PlayGames.getPlayersClient(activity).getCurrentPlayer()
            .addOnSuccessListener { player ->
                _state.value = PlayGamesState(
                    configured = true,
                    signedIn = true,
                    playerName = player.displayName
                )
            }
            .addOnFailureListener {
                _state.value = _state.value.copy(signedIn = true, error = "Play Games profile unavailable")
            }
    }

    fun submitBestDistance(
        activity: Activity,
        distanceKm: Float,
        timeSeconds: Float,
        countryCode: String
    ) {
        if (!isConfigured || !_state.value.signedIn || !distanceLeaderboardId.isUsablePlayGamesValue()) return
        // Store tenths of a kilometre so Play Console can format the score as km.
        val rawScore = (distanceKm.coerceAtLeast(0f) * 10f).roundToLong()
        val scoreTag = "t=${timeSeconds.coerceAtLeast(0f).roundToLong()};c=${countryCode.take(2)}"
        PlayGames.getLeaderboardsClient(activity)
            .submitScoreImmediate(distanceLeaderboardId, rawScore, scoreTag)
    }

    /** Unlocks every distance milestone reached by the player's best run. */
    fun unlockDistanceAchievements(activity: Activity, distanceKm: Float) {
        if (!isConfigured || !_state.value.signedIn) return
        unlockAchievements(
            activity,
            distanceAchievements
                .filter { distanceKm >= it.distanceKm }
                .map { it.id }
        )
    }

    /**
     * Unlocks distance, expedition, vehicle and event achievements. The
     * conditions are intentionally milestone-based, not one-tap actions.
     */
    fun unlockGameplayAchievements(
        activity: Activity,
        distanceKm: Float,
        relayNodes: Int,
        buildingsVisited: Int,
        fullTankReached: Boolean,
        fullUpgradeReached: Boolean,
        endReason: EndReason?,
        eventKindsSeen: Set<RoadEvent>
    ) {
        if (!isConfigured || !_state.value.signedIn) return
        val reached = buildList {
            distanceAchievements.filter { distanceKm >= it.distanceKm }.forEach { add(it.id) }
            if (relayNodes >= 1) add(appContext.getString(R.string.achievement_relay_runner_id))
            if (relayNodes >= 3) add(appContext.getString(R.string.achievement_relay_keeper_id))
            if (buildingsVisited >= 25) add(appContext.getString(R.string.achievement_building_inspector_id))
            if (fullTankReached) add(appContext.getString(R.string.achievement_topped_off_id))
            if (fullUpgradeReached) add(appContext.getString(R.string.achievement_full_system_id))
            if (endReason == EndReason.OUT_OF_FUEL) add(appContext.getString(R.string.achievement_bone_dry_id))
            if (RoadEvent.HEADWIND in eventKindsSeen) add(appContext.getString(R.string.achievement_against_wind_id))
            if (RoadEvent.TAILWIND in eventKindsSeen) add(appContext.getString(R.string.achievement_tailwind_id))
            if (eventKindsSeen.size >= 8) add(appContext.getString(R.string.achievement_weathered_road_id))
        }
        unlockAchievements(activity, reached)
    }

    private fun unlockAchievements(activity: Activity, ids: List<String>) {
        val achievementsClient = PlayGames.getAchievementsClient(activity)
        ids.asSequence()
            .filter { it.isUsablePlayGamesValue() && requestedAchievementIds.add(it) }
            .forEach { id -> achievementsClient.unlock(id) }
    }

    /** Opens the standard Play Games achievements screen. */
    fun showAchievements(activity: Activity, onFailure: (String) -> Unit = {}) {
        if (!isConfigured || !_state.value.signedIn) {
            onFailure("Play Games is not connected")
            return
        }
        PlayGames.getAchievementsClient(activity)
            .achievementsIntent
            .addOnSuccessListener { intent -> activity.startActivityForResult(intent, ACHIEVEMENTS_REQUEST_CODE) }
            .addOnFailureListener { error -> onFailure(error.message ?: "Unable to open achievements") }
    }

    fun loadScores(
        activity: Activity,
        tab: LeaderboardTab,
        onResult: (entries: List<LeaderboardEntry>, error: String?) -> Unit
    ) {
        if (!isConfigured || !_state.value.signedIn) {
            onResult(emptyList(), "Play Games is not connected")
            return
        }
        if (!distanceLeaderboardId.isUsablePlayGamesValue()) {
            onResult(emptyList(), "Distance leaderboard ID is missing")
            return
        }
        if (tab == LeaderboardTab.COUNTRY) {
            onResult(emptyList(), "Country rankings need a country-aware backend")
            return
        }

        val collection = if (tab == LeaderboardTab.FRIENDS) {
            LeaderboardVariant.COLLECTION_FRIENDS
        } else {
            LeaderboardVariant.COLLECTION_PUBLIC
        }
        PlayGames.getLeaderboardsClient(activity)
            .loadTopScores(
                distanceLeaderboardId,
                LeaderboardVariant.TIME_SPAN_ALL_TIME,
                collection,
                25
            )
            .addOnSuccessListener { annotated ->
                val data = annotated.get()
                if (data == null) {
                    onResult(emptyList(), "Play Games returned no scores")
                    return@addOnSuccessListener
                }
                val scores = data.scores
                val entries = buildList {
                    for (index in 0 until scores.count) {
                        val score = scores[index]
                        val tag = parseScoreTag(score.scoreTag)
                        add(
                            LeaderboardEntry(
                                rank = score.rank.toInt().coerceAtLeast(1),
                                nickname = score.scoreHolderDisplayName,
                                countryCode = tag.countryCode,
                                distanceKm = score.rawScore / 10f,
                                timeSeconds = tag.timeSeconds,
                                isCurrentPlayer = false
                            )
                        )
                    }
                }
                scores.release()
                data.release()
                onResult(entries, null)
            }
            .addOnFailureListener { error ->
                onResult(emptyList(), error.message ?: "Unable to load leaderboard")
            }
    }

    private fun parseScoreTag(tag: String?): ParsedScoreTag {
        if (tag.isNullOrBlank()) return ParsedScoreTag()
        val time = Regex("(?:^|;)t=(\\d+)").find(tag)?.groupValues?.getOrNull(1)?.toFloatOrNull() ?: 0f
        val country = Regex("(?:^|;)c=([A-Za-z]{2})").find(tag)?.groupValues?.getOrNull(1)?.uppercase() ?: ""
        return ParsedScoreTag(time, country)
    }

    private data class ParsedScoreTag(val timeSeconds: Float = 0f, val countryCode: String = "")

    private data class DistanceAchievement(val id: String, val distanceKm: Float)

    private companion object {
        const val ACHIEVEMENTS_REQUEST_CODE = 4101
    }
}

private fun String.isUsablePlayGamesValue(): Boolean =
    isNotBlank() && !startsWith("REPLACE_WITH_")
