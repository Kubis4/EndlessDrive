package sk.kubis.endlessdrive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import sk.kubis.endlessdrive.core.GameConfig
import sk.kubis.endlessdrive.domain.model.ComponentSlot
import sk.kubis.endlessdrive.domain.model.DebugOptions
import sk.kubis.endlessdrive.domain.model.GamePhase
import sk.kubis.endlessdrive.game.GameEngine
import sk.kubis.endlessdrive.game.VisualDamageBurstKind
import sk.kubis.endlessdrive.game.audio.GameSfx
import sk.kubis.endlessdrive.game.event.RoadEvent

class RoadEventDamageTest {

    private val frontSlots = listOf(
        ComponentSlot.FRONT_BUMPER,
        ComponentSlot.HOOD,
        ComponentSlot.HEADLIGHT,
        ComponentSlot.TIRE_FRONT
    )

    @Test
    fun rockStrikeChipsRadiatorAndSpawnsBurst() {
        val engine = equippedEngine()
        val rad0 = engine.car.parts[ComponentSlot.RADIATOR]!!.health
        val bumper0 = engine.car.parts[ComponentSlot.FRONT_BUMPER]!!.health
        engine.forceEvent(RoadEvent.ROCK_STRIKE)
        val rad1 = engine.car.parts[ComponentSlot.RADIATOR]!!.health
        val lost = rad0 - rad1
        assertTrue(lost in GameConfig.EVENT_ROCK_CHIP_MIN..GameConfig.EVENT_ROCK_CHIP_MAX)
        assertEquals(bumper0, engine.car.parts[ComponentSlot.FRONT_BUMPER]!!.health, 0.0001f)
        val burst = rockBurst(engine)
        assertNotNull(burst)
        assertEquals(0f, burst!!.alongM, 0.0001f)
        assertTrue(engine.consumeSfx().contains(GameSfx.ROCK))
    }

    @Test
    fun headwindChipsAFrontPartAndPlaysForwardRock() {
        val engine = equippedEngine()
        val rad0 = engine.car.parts[ComponentSlot.RADIATOR]!!.health
        val before = frontHealth(engine)
        engine.forceEvent(RoadEvent.HEADWIND)
        assertTrue(engine.hasEvent(RoadEvent.HEADWIND))
        assertEquals(rad0, engine.car.parts[ComponentSlot.RADIATOR]!!.health, 0.0001f)
        val lost = before.map { (slot, hp) -> slot to (hp - engine.car.parts[slot]!!.health) }
        val hits = lost.filter { it.second > 0.05f }
        assertEquals("jeden predný diel, nie vrak", 1, hits.size)
        assertTrue(
            hits.single().second in GameConfig.EVENT_ROCK_CHIP_MIN..GameConfig.EVENT_ROCK_CHIP_MAX
        )
        val burst = rockBurst(engine)!!
        assertEquals(GameConfig.EVENT_HEADWIND_ROCK_EXTRA_M, burst.alongM, 0.0001f)
        assertTrue(engine.consumeSfx().contains(GameSfx.ROCK))
    }

    @Test
    fun headwindDoesNotSpamFrontDamageEveryFrame() {
        val engine = equippedEngine()
        assertTrue(engine.tryStartEngine())
        assertEquals(GamePhase.DRIVING, engine.phase)
        engine.forceEvent(RoadEvent.HEADWIND)
        val afterStart = frontHealth(engine)
        engine.throttleInput = 0f
        repeat(90) { engine.advance(1f / 60f) }
        frontSlots.forEach { slot ->
            val drop = afterStart.getValue(slot) - engine.car.parts[slot]!!.health
            assertTrue("$slot sa nesmie zničiť za 1.5 s", drop < 0.02f)
        }
        assertTrue(engine.hasEvent(RoadEvent.HEADWIND))
    }

    @Test
    fun headwindKeepsCarDriveableAfterTheChip() {
        val engine = equippedEngine()
        frontSlots.forEach { slot ->
            engine.car.parts[slot]!!.health = 0.22f
        }
        engine.forceEvent(RoadEvent.HEADWIND)
        frontSlots.forEach { slot ->
            assertTrue(
                "$slot ostane nad podlahou",
                engine.car.parts[slot]!!.health >= GameConfig.EVENT_ROCK_CHIP_FLOOR
            )
        }
    }

    private fun equippedEngine(): GameEngine =
        GameEngine(
            seed = 77L,
            bestDistanceKm = 0f,
            debugOptions = DebugOptions(allComponents = true, fullFluids = true)
        )

    private fun frontHealth(engine: GameEngine): Map<ComponentSlot, Float> =
        frontSlots.associateWith { engine.car.parts[it]!!.health }

    private fun rockBurst(engine: GameEngine) =
        (0 until engine.visualDamageBursts.capacity)
            .mapNotNull { engine.visualDamageBursts.entryAt(it) }
            .firstOrNull { it.kind == VisualDamageBurstKind.ROCK_STRIKE }
}
