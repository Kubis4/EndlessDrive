package sk.kubis.endlessdrive.game

import sk.kubis.endlessdrive.core.GameConfig
import kotlin.math.abs

/** Perspektívna 2.5D projekcia – port z HillRush. */
class DepthProjection {
    var ppm = 0f; private set
    var halfW = 0f; private set
    var halfH = 0f; private set
    var camX = 0f; private set
    var camY = 0f; private set
    var vpX = 0f; private set
    var vpY = 0f; private set
    var focal = GameConfig.DEPTH_FOCAL; private set
    var shearX = 0f; private set
    var shearY = 0f; private set

    fun begin(
        camX: Float,
        camY: Float,
        ppm: Float,
        halfW: Float,
        halfH: Float,
        pitch: Float = 0f
    ) {
        this.camX = camX
        this.camY = camY
        this.ppm = ppm
        this.halfW = halfW
        this.halfH = halfH

        val p = pitch.coerceIn(-GameConfig.DEPTH_PITCH_MAX, GameConfig.DEPTH_PITCH_MAX)
        focal = GameConfig.DEPTH_FOCAL * (1f + abs(p) * GameConfig.DEPTH_PITCH_FOCAL)

        val depthX = (GameConfig.DEPTH_X * (1f + p * GameConfig.DEPTH_PITCH_SHEAR_X)).coerceAtLeast(0.02f)
        val depthY = (GameConfig.DEPTH_Y * (1f + p * GameConfig.DEPTH_PITCH_SHEAR_Y)).coerceAtLeast(0.12f)

        val refDepth = GameConfig.ROAD_DEPTH
        val tRef = perspectiveT(refDepth)
        val invT = if (tRef > 1e-4f) 1f / tRef else 1f

        vpX = halfW + refDepth * depthX * ppm * invT + p * GameConfig.DEPTH_PITCH_VP_X * halfW
        vpY = halfH - refDepth * depthY * ppm * invT - p * GameConfig.DEPTH_PITCH_VP_Y * halfH

        shearX = (vpX - halfW) / focal
        shearY = (vpY - halfH) / focal
    }

    fun frontX(wx: Float): Float = (wx - camX) * ppm + halfW
    fun frontY(wy: Float): Float = halfH - (wy - camY) * ppm

    fun perspectiveT(depth: Float): Float {
        if (depth <= 0f) return 0f
        return 1f - focal / (focal + depth)
    }

    fun atX(frontX: Float, depth: Float): Float {
        val t = perspectiveT(depth)
        return frontX + (vpX - frontX) * t
    }

    fun atY(frontY: Float, depth: Float): Float {
        val t = perspectiveT(depth)
        return frontY + (vpY - frontY) * t
    }

    fun linearX(frontX: Float, depth: Float): Float = frontX + shearX * depth
    fun linearY(frontY: Float, depth: Float): Float = frontY + shearY * depth
}
