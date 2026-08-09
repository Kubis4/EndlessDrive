package sk.kubis.endlessdrive.domain.repository

import kotlinx.coroutines.flow.Flow

data class PlayerProfile(
    val bestDistanceKm: Float = 0f,
    val totalRuns: Int = 0,
    val totalDistanceKm: Float = 0f
)

interface PlayerRepository {
    val profile: Flow<PlayerProfile>
    suspend fun current(): PlayerProfile
    suspend fun recordRun(distanceKm: Float)
}
