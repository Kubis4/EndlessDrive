package sk.kubis.endlessdrive.ui.game

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Build
import sk.kubis.endlessdrive.AudioAttribution
import sk.kubis.endlessdrive.R
import sk.kubis.endlessdrive.core.MathX
import sk.kubis.endlessdrive.domain.model.GamePhase
import sk.kubis.endlessdrive.domain.model.RoadSurface
import sk.kubis.endlessdrive.game.GameEngine
import sk.kubis.endlessdrive.game.audio.GameSfx
import sk.kubis.endlessdrive.game.audio.SlipSoundEnvelope
import sk.kubis.endlessdrive.domain.model.AudioSettings
import sk.kubis.endlessdrive.game.event.RoadEvent
import kotlin.random.Random

/**
 * SFX cez [SoundPool].
 * Brzda/šmyk = loopy (pri pustení pedálu hneď zhasnú).
 * Povrch cesty = samostatný loop + one-shot pri vjazde.
 */
class GameAudio(context: Context) {
    var settings = AudioSettings()
    private val slipEnvelope = SlipSoundEnvelope()

    private val pool: SoundPool = buildPool(audioContext(context))

    private val samples = mutableMapOf<Int, Int>()
    private var ready = false
    private var pendingLoads = 0

    private var idleStream = 0
    private var revStream = 0
    private var rainStream = 0
    private var windStream = 0
    private var surfaceStream = 0
    private var brakeStream = 0
    private var skidStream = 0
    private var leakStream = 0
    private var surfaceRes = 0
    private var lastSurface: RoadSurface? = null
    private var surfaceEntryCooldown = 0f
    private var lastMudVariant = 0
    private var lastWaterVariant = 0

    private var idleVol = 0f
    private var revVol = 0f
    private var rainVol = 0f
    private var windVol = 0f
    private var surfaceVol = 0f
    private var brakeVol = 0f
    private var skidVol = 0f
    private var leakVol = 0f
    private var mudSplatCooldown = 0f
    private var waterSplatCooldown = 0f
    private var gravelTickCooldown = 0f

    private var misfireCooldown = 0f
    private var muted = false
    private var enginePitch = 0.85f
    private var gear = 1
    private var shiftRemaining = 0f

    private val mudSplatters = intArrayOf(
        R.raw.sfx_mud_1,
        R.raw.sfx_mud_2,
        R.raw.sfx_mud_3,
        R.raw.sfx_mud_4,
        R.raw.sfx_mud_5
    )
    private val waterSplashers = intArrayOf(
        R.raw.sfx_splash_1,
        R.raw.sfx_splash_2
    )

    init {
        val app = audioContext(context)
        val toLoad = buildList {
            addAll(
                listOf(
                    R.raw.sfx_engine_idle,
                    R.raw.sfx_engine_rev,
                    R.raw.sfx_engine_start,
                    R.raw.sfx_engine_stop,
                    R.raw.sfx_stall,
                    R.raw.sfx_brake,
                    R.raw.sfx_skid,
                    R.raw.sfx_blowout,
                    R.raw.sfx_rock,
                    R.raw.sfx_misfire,
                    R.raw.sfx_belt,
                    R.raw.sfx_leak,
                    R.raw.sfx_coolant_hiss,
                    R.raw.sfx_oil_splash,
                    R.raw.sfx_rain,
                    R.raw.sfx_mud,
                    R.raw.sfx_debris,
                    R.raw.sfx_find,
                    R.raw.sfx_wind,
                    R.raw.sfx_surface_water,
                    R.raw.sfx_surface_mud,
                    R.raw.sfx_surface_gravel,
                    R.raw.sfx_surface_sand,
                    R.raw.sfx_surface_ice,
                    R.raw.sfx_surface_road,
                    R.raw.sfx_leak_loop
                )
            )
            mudSplatters.forEach { add(it) }
            waterSplashers.forEach { add(it) }
        }
        pendingLoads = toLoad.size
        // Optimistic: hrajeme čo je načítané. Čakanie na všetky callbacky
        // (alebo zlyhaný OGG) predtým vypínalo celé audio.
        ready = true
        pool.setOnLoadCompleteListener { _, sampleId, status ->
            pendingLoads = (pendingLoads - 1).coerceAtLeast(0)
            if (status != 0) {
                val dead = samples.filterValues { it == sampleId }.keys
                dead.forEach { samples.remove(it) }
            }
        }
        for (res in toLoad) {
            val id = pool.load(app, res, 1)
            if (id == 0) samples.remove(res) else samples[res] = id
        }
    }

    fun setMuted(value: Boolean) {
        muted = value
        if (value) {
            pool.autoPause()
            stopLoops()
        } else pool.autoResume()
    }

    fun release() {
        stopLoops()
        pool.release()
        samples.clear()
        ready = false
    }

    fun update(engine: GameEngine, dt: Float, paused: Boolean) {
        val queued = engine.consumeSfx()
        if (!ready || muted) {
            if (muted || paused) stopLoops()
            return
        }

        if (paused) {
            pool.autoPause()
            stopLoops()
            return
        }

        pool.autoResume()
        queued.forEach { playOneShot(it) }

        if (engine.phase == GamePhase.GAME_OVER) {
            stopLoops()
            return
        }

        misfireCooldown = (misfireCooldown - dt).coerceAtLeast(0f)

        val car = engine.car
        val driving = engine.phase == GamePhase.DRIVING ||
            engine.phase == GamePhase.JUNCTION
        val running = car.engineRunning
        val speedAbs = kotlin.math.abs(car.speedKmh)
        val reversing = car.speed < -0.4f ||
            (engine.brakeInput > 0.05f && car.speed <= 0.35f && engine.throttleInput < 0.05f)

        updateEngineLoops(running, speedAbs, engine.throttleInput, reversing, dt)
        updateSurfaceLoop(engine, speedAbs, driving, dt)
        updateAmbientLoops(engine, dt)
        updateBrakeSkidLoops(engine, reversing, dt)
        updateLeakLoop(engine, dt)
        updateMisfire(engine)
    }

    private fun updateEngineLoops(
        running: Boolean,
        speedKmh: Float,
        throttle: Float,
        reversing: Boolean,
        dt: Float
    ) {
        if (!running) {
            idleVol = MathX.damp(idleVol, 0f, 6f, dt)
            revVol = MathX.damp(revVol, 0f, 8f, dt)
            idleStream = applyLoop(idleStream, R.raw.sfx_engine_idle, idleVol, 1f)
            revStream = applyLoop(revStream, R.raw.sfx_engine_rev, revVol, 1f)
            return
        }

        val speed01 = (speedKmh / 150f).coerceIn(0f, 1f)
        val speedCurve = speed01 * speed01
        val thr = throttle.coerceIn(0f, 1f)
        // Virtual gears give acceleration a rise-and-release rhythm. Hysteresis
        // prevents repeated shifts when speed hovers around a threshold.
        if (!reversing) {
            if (gear < 5 && speedKmh > gear * 27f + 3f) {
                gear++
                shiftRemaining = 0.2f
            } else if (gear > 1 && speedKmh < (gear - 1) * 27f - 6f) {
                gear--
            }
        } else gear = 1
        shiftRemaining = (shiftRemaining - dt).coerceAtLeast(0f)
        val rpm = ((speedKmh - (gear - 1) * 27f) / 33f).coerceIn(0f, 1f)
        val load = if (reversing) {
            (0.15f + thr * 0.25f).coerceIn(0f, 0.4f)
        } else {
            (thr * 0.45f + speedCurve * 0.55f).coerceIn(0f, 1f)
        }

        val targetIdle = if (reversing) 0.38f else MathX.lerp(0.55f, 0.12f, load)
        val targetRev = if (reversing) {
            0.18f + thr * 0.15f
        } else {
            MathX.lerp(0.05f, 0.95f, load)
        }

        val idleRate = if (reversing) {
            MathX.lerp(0.88f, 0.98f, thr)
        } else {
            MathX.lerp(0.82f, 1.18f, speed01 * 0.6f + thr * 0.4f)
        }
        val revRate = if (reversing) {
            MathX.lerp(0.85f, 1.0f, thr)
        } else {
            MathX.lerp(0.78f, 1.55f, speedCurve * 0.7f + thr * 0.3f)
        }

        idleVol = MathX.damp(idleVol, targetIdle.coerceIn(0f, 0.8f), 5f, dt)
        revVol = MathX.damp(revVol,
            (targetRev * if (shiftRemaining > 0f) 0.48f else 0.8f).coerceIn(0f, 1f), 6f, dt)
        enginePitch = MathX.damp(enginePitch,
            if (reversing) revRate else 0.78f + rpm * 0.58f + thr * 0.16f, 9f, dt)

        idleStream = applyLoop(idleStream, R.raw.sfx_engine_idle, idleVol, idleRate)
        revStream = applyLoop(revStream, R.raw.sfx_engine_rev, revVol, enginePitch)
    }

    private fun updateSurfaceLoop(
        engine: GameEngine,
        speedKmh: Float,
        driving: Boolean,
        dt: Float
    ) {
        val patch = engine.currentSurface
        // Event MUD/DEBRIS majú chip v HUD aj bez patchu pod kolesami.
        val effective = when {
            engine.hasEvent(RoadEvent.MUD) -> RoadSurface.MUD
            engine.hasEvent(RoadEvent.DEBRIS) && patch == RoadSurface.ASPHALT ->
                RoadSurface.GRAVEL
            else -> patch
        }
        val res = surfaceResFor(effective)
        val moving = driving && speedKmh > 1.5f
        val speed01 = (speedKmh / 70f).coerceIn(0f, 1f)

        val targetVol = when {
            !moving -> 0f
            effective == RoadSurface.ASPHALT -> 0.04f + speed01 * 0.08f
            effective == RoadSurface.MUD -> 0.10f + speed01 * 0.12f
            effective == RoadSurface.WATER -> 0.10f + speed01 * 0.14f
            effective == RoadSurface.GRAVEL -> 0.08f + speed01 * 0.14f
            effective == RoadSurface.SAND -> 0.06f + speed01 * 0.10f
            effective == RoadSurface.ICE || effective == RoadSurface.SLUSH ->
                0.05f + speed01 * 0.09f
            else -> 0.4f + speed01 * 0.3f
        }

        val prev = lastSurface
        val entered = prev != null && prev != effective
        lastSurface = effective

        surfaceEntryCooldown = (surfaceEntryCooldown - dt).coerceAtLeast(0f)
        // Nearby patch boundaries must not fire several impact sounds in a row.
        if (entered && moving && effective != RoadSurface.ASPHALT && surfaceEntryCooldown <= 0f) {
            surfaceEntryCooldown = 1.5f
            surfaceVol = targetVol
            when (effective) {
                RoadSurface.MUD -> {
                    mudSplatCooldown = 1.8f
                    play(nextMudVariant(), 0.12f + speed01 * 0.10f, 1f)
                }
                RoadSurface.GRAVEL -> {
                    gravelTickCooldown = 2.5f
                    play(R.raw.sfx_debris, 0.08f + speed01 * 0.08f, 1f)
                }
                RoadSurface.WATER -> {
                    waterSplatCooldown = 2f
                    play(nextWaterVariant(), 0.2f, 1f)
                }
                RoadSurface.SAND -> play(R.raw.sfx_debris, 0.12f, 0.95f)
                RoadSurface.ICE, RoadSurface.SLUSH -> Unit
                else -> Unit
            }
        }

        mudSplatCooldown = (mudSplatCooldown - dt).coerceAtLeast(0f)
        waterSplatCooldown = (waterSplatCooldown - dt).coerceAtLeast(0f)
        gravelTickCooldown = (gravelTickCooldown - dt).coerceAtLeast(0f)
        if (moving && effective == RoadSurface.MUD && mudSplatCooldown <= 0f) {
            play(nextMudVariant(), 0.08f + speed01 * 0.08f, 1f)
            // Sparse irregular detail above a quiet continuous surface bed.
            mudSplatCooldown = 1.8f + Random.nextFloat() * 1.4f
        }
        if (moving && effective == RoadSurface.WATER && waterSplatCooldown <= 0f) {
            play(nextWaterVariant(), 0.10f + speed01 * 0.12f, 1f)
            waterSplatCooldown = 1.6f + Random.nextFloat() * 1.6f
        }
        if (moving && effective == RoadSurface.GRAVEL && gravelTickCooldown <= 0f) {
            play(R.raw.sfx_debris, 0.06f + speed01 * 0.06f, 1f)
            gravelTickCooldown = 2.2f + Random.nextFloat() * 1.8f
        }

        // Bahno/voda: stabilný rate – pitch warble robí „zvonkohru“.
        val rate = when (effective) {
            RoadSurface.MUD, RoadSurface.WATER -> MathX.lerp(0.98f, 1.05f, speed01)
            RoadSurface.GRAVEL -> MathX.lerp(0.95f, 1.12f, speed01)
            else -> MathX.lerp(0.9f, 1.3f, speed01)
        }

        if (res != surfaceRes && surfaceStream != 0) {
            pool.stop(surfaceStream)
            surfaceStream = 0
        }
        surfaceRes = res
        surfaceVol = MathX.damp(surfaceVol, targetVol, 6f, dt)
        surfaceStream = applyLoop(surfaceStream, res, surfaceVol, rate)
    }

    private fun surfaceResFor(surface: RoadSurface): Int = when (surface) {
        RoadSurface.WATER -> R.raw.sfx_surface_water
        RoadSurface.MUD -> R.raw.sfx_surface_mud
        RoadSurface.GRAVEL -> R.raw.sfx_surface_gravel
        RoadSurface.SAND -> R.raw.sfx_surface_sand
        RoadSurface.ICE, RoadSurface.SLUSH -> R.raw.sfx_surface_ice
        RoadSurface.ASPHALT -> R.raw.sfx_surface_road
    }

    private fun updateAmbientLoops(engine: GameEngine, dt: Float) {
        // Noc nemá cvrčky / sovy / rádio – žiadne creepy loopy.
        val raining = engine.hasEvent(RoadEvent.RAIN)
        val windy = engine.hasEvent(RoadEvent.TAILWIND) || engine.hasEvent(RoadEvent.HEADWIND)
        val windStrength = if (engine.hasEvent(RoadEvent.HEADWIND)) 0.28f else 0.22f

        rainVol = MathX.damp(rainVol, if (raining) 0.18f else 0f, 2.5f, dt)
        windVol = MathX.damp(windVol, if (windy) windStrength else 0f, 2.5f, dt)

        rainStream = applyLoop(rainStream, R.raw.sfx_rain, rainVol, 1f)
        windStream = applyLoop(windStream, R.raw.sfx_wind, windVol, 1f)
    }

    /** Tyre noise is a short cue on firm ground, never an endless warning loop. */
    private fun updateBrakeSkidLoops(engine: GameEngine, reversing: Boolean, dt: Float) {
        val car = engine.car
        val hardSurface = engine.currentSurface == RoadSurface.ASPHALT && !engine.isWinter &&
            !engine.hasEvent(RoadEvent.MUD) && !engine.hasEvent(RoadEvent.DEBRIS)
        val slipping = car.grounded && hardSurface && car.wheelSlip > 0.5f &&
            (kotlin.math.abs(car.speedKmh) > 8f || kotlin.math.abs(car.wheelSpeed) > 5f)
        val cue = slipEnvelope.update(slipping, dt)
        val target = cue * (0.08f + car.wheelSlip * 0.10f)
        skidVol = MathX.damp(skidVol, target, if (target > skidVol) 12f else 18f, dt)
        skidStream = applyLoop(skidStream, R.raw.sfx_skid, skidVol, if (reversing) 0.85f else 0.9f)
    }
    private fun updateLeakLoop(engine: GameEngine, dt: Float) {
        val leaking = engine.hasEvent(RoadEvent.FUEL_LEAK) ||
            engine.hasEvent(RoadEvent.COOLANT_LEAK)
        val target = if (leaking) 0.12f else 0f
        leakVol = MathX.damp(leakVol, target, 3f, dt)
        leakStream = applyLoop(leakStream, R.raw.sfx_leak_loop, leakVol, 1f)
    }

    private fun updateMisfire(engine: GameEngine) {
        if (engine.hasEvent(RoadEvent.MISFIRE) &&
            engine.car.engineRunning &&
            misfireCooldown <= 0f
        ) {
            play(R.raw.sfx_misfire, 0.65f, 0.92f + Random.nextFloat() * 0.16f)
            misfireCooldown = 0.85f + Random.nextFloat() * 1.0f
        }
    }

    private fun playOneShot(sfx: GameSfx) {
        when (sfx) {
            GameSfx.ENGINE_START -> play(R.raw.sfx_engine_start, 0.85f, 1f)
            GameSfx.ENGINE_STOP -> play(R.raw.sfx_engine_stop, 0.7f, 1f)
            GameSfx.ENGINE_STALL -> play(R.raw.sfx_stall, 0.8f, 1f)
            GameSfx.ENGINE_FAIL_START -> play(R.raw.sfx_stall, 0.45f, 0.85f)
            GameSfx.BRAKE, GameSfx.SKID -> Unit
            GameSfx.BLOWOUT -> play(R.raw.sfx_blowout, 1f, 1f)
            GameSfx.ROCK -> play(R.raw.sfx_rock, 0.95f, 1f)
            GameSfx.MISFIRE -> play(R.raw.sfx_misfire, 0.8f, 1f)
            GameSfx.BELT -> play(R.raw.sfx_belt, 0.95f, 1f)
            GameSfx.FUEL_LEAK -> play(R.raw.sfx_leak, 0.75f, 1f)
            GameSfx.COOLANT_LEAK -> play(R.raw.sfx_coolant_hiss, 0.8f, 1f)
            GameSfx.OIL_SPLASH -> play(R.raw.sfx_oil_splash, 0.85f, 1f)
            GameSfx.RAIN -> Unit
            // Surface loop + splatters riešia bahno; krátky one-shot tu znel ako cuknutie.
            GameSfx.MUD -> Unit
            GameSfx.DEBRIS -> play(R.raw.sfx_debris, 0.75f, 1f)
            GameSfx.TAILWIND, GameSfx.CLEAR_ROAD -> Unit
            GameSfx.FIND -> play(R.raw.sfx_find, 0.55f, 1f)
            GameSfx.RADIO -> play(R.raw.sfx_radio, 0.7f, 1f)
            GameSfx.ANIMAL, GameSfx.TRACKS -> Unit
        }
    }

    private fun gainFor(res: Int): Float {
        val category = when (res) {
            R.raw.sfx_skid, R.raw.sfx_brake -> settings.tyres
            R.raw.sfx_surface_water, R.raw.sfx_surface_mud, R.raw.sfx_surface_gravel,
            R.raw.sfx_surface_sand, R.raw.sfx_surface_ice, R.raw.sfx_surface_road,
            R.raw.sfx_rain, R.raw.sfx_wind, R.raw.sfx_debris -> settings.surfaces
            else -> if (res in mudSplatters || res in waterSplashers) settings.surfaces else 1f
        }
        return settings.master.coerceIn(0f, 1f) * category.coerceIn(0f, 1f)
    }

    private fun nextMudVariant(): Int {
        lastMudVariant = (lastMudVariant + Random.nextInt(1, mudSplatters.size)) % mudSplatters.size
        return mudSplatters[lastMudVariant]
    }

    private fun nextWaterVariant(): Int {
        lastWaterVariant = (lastWaterVariant + 1) % waterSplashers.size
        return waterSplashers[lastWaterVariant]
    }

    private fun play(res: Int, volume: Float, rate: Float) {
        val id = samples[res] ?: return
        val v = (volume * gainFor(res)).coerceIn(0f, 1f)
        pool.play(id, v, v, 1, 0, rate.coerceIn(0.5f, 2f))
    }

    private fun applyLoop(stream: Int, res: Int, volume: Float, rate: Float): Int {
        val id = samples[res] ?: return 0
        val v = (volume * gainFor(res)).coerceIn(0f, 1f)
        if (v < 0.015f) {
            if (stream != 0) pool.stop(stream)
            return 0
        }
        val r = rate.coerceIn(0.5f, 2f)
        return if (stream == 0) {
            pool.play(id, v, v, 0, -1, r)
        } else {
            pool.setVolume(stream, v, v)
            pool.setRate(stream, r)
            stream
        }
    }

    private fun stopLoops() {
        fun stop(id: Int): Int {
            if (id != 0) pool.stop(id)
            return 0
        }
        idleStream = stop(idleStream)
        revStream = stop(revStream)
        rainStream = stop(rainStream)
        windStream = stop(windStream)
        surfaceStream = stop(surfaceStream)
        brakeStream = stop(brakeStream)
        skidStream = stop(skidStream)
        leakStream = stop(leakStream)
        idleVol = 0f
        revVol = 0f
        rainVol = 0f
        windVol = 0f
        surfaceVol = 0f
        brakeVol = 0f
        skidVol = 0f
        leakVol = 0f
        surfaceRes = 0
        lastSurface = null
    }

    companion object {
        private fun audioContext(context: Context): Context {
            return AudioAttribution.wrap(context)
        }

        private fun buildPool(context: Context): SoundPool {
            val builder = SoundPool.Builder()
                .setMaxStreams(14)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                builder.setContext(context)
            }
            return builder.build()
        }
    }
}
