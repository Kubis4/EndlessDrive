package sk.kubis.endlessdrive.game

import sk.kubis.endlessdrive.core.GameConfig
import sk.kubis.endlessdrive.core.MathX
import kotlin.math.atan
import kotlin.math.hypot

/** Bočná kamera – silný follow na X, slabý na Y (cesta ide doprava). */
class Camera2D {
    var x = 0f; private set
    var y = 0f; private set
    var ppm = GameConfig.CAMERA_BASE_PPM; private set
    var basePpm = GameConfig.CAMERA_BASE_PPM; private set
    var pitch = 0f; private set

    private var initialized = false

    fun snapTo(wx: Float, wy: Float) {
        x = wx
        y = wy + GameConfig.CAMERA_Y_BIAS
        initialized = true
    }

    fun update(
        targetX: Float,
        targetY: Float,
        velX: Float,
        dt: Float,
        screenHeightPx: Float,
        groundSlope: Float,
        vehicleAngle: Float
    ) {
        val lookAhead = (velX * GameConfig.CAMERA_LOOK_AHEAD).coerceAtLeast(0f)
        val desiredX = targetX + lookAhead + 2.0f
        // Y kamera sleduje auto 1:1 → auto ostane na obrazovke, nechodí hore/dole.
        val desiredY = targetY + GameConfig.CAMERA_Y_BIAS

        val speed = hypot(velX, 0f)
        val dpiScale = (screenHeightPx / 720f).coerceIn(0.75f, 2.2f)
        basePpm = GameConfig.CAMERA_BASE_PPM * dpiScale
        val desiredPpm = (GameConfig.CAMERA_BASE_PPM / (1f + speed * GameConfig.CAMERA_SPEED_ZOOM))
            .coerceAtLeast(GameConfig.CAMERA_MIN_PPM) * dpiScale

        val groundPitch = atan(groundSlope) * 0.08f
        val desiredPitch = MathX.clamp(
            MathX.lerp(groundPitch, vehicleAngle * 0.08f, 0.12f),
            -GameConfig.DEPTH_PITCH_MAX * 0.12f,
            GameConfig.DEPTH_PITCH_MAX * 0.12f
        )

        if (!initialized) {
            x = desiredX
            y = desiredY
            ppm = desiredPpm
            pitch = desiredPitch
            initialized = true
            return
        }

        x = MathX.damp(x, desiredX, GameConfig.CAMERA_SMOOTH, dt)
        y = MathX.damp(y, desiredY, GameConfig.CAMERA_SMOOTH, dt)
        ppm = MathX.damp(ppm, desiredPpm, 2.4f, dt)
        pitch = MathX.damp(pitch, desiredPitch, GameConfig.DEPTH_PITCH_SMOOTH, dt)
    }
}
