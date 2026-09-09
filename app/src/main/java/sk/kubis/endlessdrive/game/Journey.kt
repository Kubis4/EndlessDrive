package sk.kubis.endlessdrive.game

data class JourneyGoal(
    val distanceKm: Float,
    val title: String,
    val description: String
)

/**
 * The long-term route. The goals are deliberately uneven: early relays give
 * quick feedback, while the final safe haven remains a real expedition.
 * The route milestones define where the physical relay stations appear. The
 * actual progress is recorded only after the player restores each station.
 */
object Journey {
    const val LEG_METERS = 1000f
    const val FINAL_DISTANCE_M = 100_000f

    val goals = listOf(
        JourneyGoal(5f, "FIRST RELAY", "A signal is back on the air."),
        JourneyGoal(10f, "SECOND RELAY", "The route reaches beyond the first snow line."),
        JourneyGoal(20f, "THIRD RELAY", "Long empty stretches become part of the plan."),
        JourneyGoal(35f, "FOURTH RELAY", "The old network starts to wake up."),
        JourneyGoal(50f, "FIFTH RELAY", "Halfway to the safe haven."),
        JourneyGoal(75f, "SIXTH RELAY", "Only a hardened expedition can keep going."),
        JourneyGoal(100f, "SAFE HAVEN", "Restore the final relay and finish the journey.")
    )

    fun completedLegs(distanceM: Float): Int = (distanceM.coerceAtLeast(0f) / LEG_METERS).toInt()

    fun completedGoals(distanceM: Float): Int = goals.count { distanceM >= it.distanceKm * LEG_METERS }

    fun nextGoal(completed: Int): JourneyGoal? = goals.getOrNull(completed.coerceAtLeast(0))

    /** Cost rises with expedition depth but remains recoverable at each relay. */
    fun relayRestoreCost(relayIndex: Int): Int =
        (6 + relayIndex.coerceAtLeast(0) * 4).coerceAtMost(36)

    /** Kilometre milestoney už samy negenerujú scrap; ten pochádza z lootu. */
    fun rewardBetween(beforeM: Float, afterM: Float): Int = 0
}
