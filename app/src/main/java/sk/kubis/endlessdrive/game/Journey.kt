package sk.kubis.endlessdrive.game

/** Progress is derived from the saved high-water distance, so reversing or loading
 * cannot award the same milestone twice. No additional save format is needed. */
object Journey {
    const val LEG_METERS = 1000f
    fun completedLegs(distanceM: Float): Int = (distanceM.coerceAtLeast(0f) / LEG_METERS).toInt()
    fun rewardBetween(beforeM: Float, afterM: Float): Int =
        (completedLegs(afterM) - completedLegs(beforeM)).coerceAtLeast(0) * 3
}
