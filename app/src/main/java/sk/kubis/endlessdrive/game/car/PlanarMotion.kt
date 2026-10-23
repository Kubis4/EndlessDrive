package sk.kubis.endlessdrive.game.car

import kotlin.math.*

/** Bicycle steering with a damped lateral tyre velocity. Units: metres, seconds, radians. */
class PlanarMotion {
    var z = 0f
    var heading = 0f
    var yawRate = 0f
    var lateralSpeed = 0f
    var wheelAngle = 0f
        private set
    var roll = 0f
        private set
    var deltaX = 0f
        private set

    fun advance(speed: Float, input: Float, grip: Float, grounded: Boolean, dt: Float) {
        if (dt <= 0f || !dt.isFinite()) return
        val traction = grip.coerceIn(0.12f, 1.4f)
        val amount = input.takeIf(Float::isFinite)?.coerceIn(-1f, 1f) ?: 0f
        val steeringLimit = 0.55f / (1f + abs(speed) * 0.035f)
        wheelAngle += (amount * steeringLimit - wheelAngle) * (1f - exp(-12f * dt))
        val requestedYaw = speed / 3.528f * tan(wheelAngle)
        val limit = (traction * 8.5f / abs(speed).coerceAtLeast(2f)).coerceAtMost(1.45f)
        val targetYaw = if (grounded) requestedYaw.coerceIn(-limit, limit) else 0f
        yawRate += (targetYaw - yawRate) * (1f - exp(-7f * dt))
        if (abs(speed) < 0.08f) yawRate = 0f
        heading = atan2(sin(heading + yawRate * dt), cos(heading + yawRate * dt))
        // Low grip retains side slip. At rest it settles without rotating the car.
        val stiffness = if (grounded) 5f + traction * 13f else 0.5f
        lateralSpeed = (lateralSpeed - speed * yawRate * dt) * exp(-stiffness * dt)
        if (abs(speed) < 0.08f && abs(lateralSpeed) < 0.05f) lateralSpeed = 0f
        deltaX = (speed * cos(heading) - lateralSpeed * sin(heading)) * dt
        z += (speed * sin(heading) + lateralSpeed * cos(heading)) * dt
        val targetRoll = (-speed * yawRate * 0.012f).coerceIn(-0.12f, 0.12f)
        roll += (targetRoll - roll) * (1f - exp(-8f * dt))
    }

    fun stop() {
        lateralSpeed = 0f
        yawRate = 0f
        deltaX = 0f
        roll = 0f
    }
}
