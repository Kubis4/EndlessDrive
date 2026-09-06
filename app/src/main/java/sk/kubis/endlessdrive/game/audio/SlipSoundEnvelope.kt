package sk.kubis.endlessdrive.game.audio

/** A brief warning at loss of traction, followed by silence until grip returns.
 * Hysteresis prevents alternating slippery patches from retriggering a squeal. */
class SlipSoundEnvelope {
    private var age = 0f
    private var quietTime = 1f
    private var armed = true

    fun update(slipping: Boolean, dt: Float): Float {
        val step = dt.coerceIn(0f, 0.1f)
        if (!slipping) {
            quietTime += step
            if (quietTime >= 0.9f) armed = true
            return 0f
        }
        quietTime = 0f
        if (armed) { age = 0f; armed = false }
        age += step
        val attack = (age / 0.1f).coerceIn(0f, 1f)
        val release = ((0.85f - age) / 0.6f).coerceIn(0f, 1f)
        return attack * release
    }
}
