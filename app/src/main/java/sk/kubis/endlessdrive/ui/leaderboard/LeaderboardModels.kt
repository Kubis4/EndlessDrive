package sk.kubis.endlessdrive.ui.leaderboard

import kotlin.math.floor

enum class LeaderboardTab(val label: String) {
    FRIENDS("FRIENDS"),
    COUNTRY("COUNTRY"),
    WORLD("WORLD")
}

data class LeaderboardEntry(
    val rank: Int,
    val nickname: String,
    val countryCode: String,
    val distanceKm: Float,
    val timeSeconds: Float,
    val isCurrentPlayer: Boolean = false
)

fun countryFlag(countryCode: String): String {
    val normalized = countryCode.trim().uppercase()
    if (normalized.length != 2 || normalized.any { it !in 'A'..'Z' }) return "🌐"
    return normalized.map { char ->
        String(Character.toChars(0x1F1E6 + (char.code - 'A'.code)))
    }.joinToString("")
}

fun formatLeaderboardTime(seconds: Float): String {
    if (seconds <= 0f) return "—"
    val whole = floor(seconds).toInt()
    val hours = whole / 3600
    val minutes = (whole % 3600) / 60
    val secs = whole % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, secs)
    } else {
        "%02d:%02d".format(minutes, secs)
    }
}
