package sk.kubis.endlessdrive.game

import sk.kubis.endlessdrive.core.GameConfig
import sk.kubis.endlessdrive.core.MathX
import kotlin.math.floor

/** Saved simulation time synchronises 3D lightning, exhaust pops and their sounds. */
internal object SceneEffects {
    fun fogAmount(remaining:Float,duration:Float):Float {
        if(duration<=0f || remaining<=0f) return 0f
        return minOf(MathX.smoothstep(0f,5f,(duration-remaining).coerceAtLeast(0f)),MathX.smoothstep(0f,7f,remaining))
    }
    fun stormFlashBeat(t:Float)=floor(t/5.8f).toInt()
    fun stormFlash(t:Float,enabled:Boolean=true):Float {
        if(!enabled) return 0f
        val beat=floor(t/5.8f); val local=t-beat*5.8f
        val delay=1.1f+MathX.hash01(beat.toInt(),4401)*2.8f
        if(local<delay || local>delay+0.22f) return 0f
        val u=(local-delay)/0.22f
        return when { u<0.07f -> 1f; u<0.18f -> 0.18f; u<0.32f -> 0.78f; else -> ((1-u)*0.55f).coerceAtLeast(0f) }
    }
    fun misfireBeat(t:Float)=if(GameConfig.MISFIRE_FX_HZ>0f) (t*GameConfig.MISFIRE_FX_HZ)%1f else 1f
    fun misfireCycleIndex(t:Float)=(t*GameConfig.MISFIRE_FX_HZ).toInt()
    fun misfirePopping(beat:Float)=beat>=0f && beat<GameConfig.MISFIRE_FX_DUTY
}
