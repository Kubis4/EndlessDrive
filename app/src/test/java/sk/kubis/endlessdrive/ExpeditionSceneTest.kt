package sk.kubis.endlessdrive

import org.junit.Assert.*
import org.junit.Test
import sk.kubis.endlessdrive.domain.model.ComponentSlot
import sk.kubis.endlessdrive.domain.model.DebugOptions
import sk.kubis.endlessdrive.game.GameEngine
import sk.kubis.endlessdrive.game.save.RunCodec
import sk.kubis.endlessdrive.ui.game.expedition.ExpeditionObj
import sk.kubis.endlessdrive.ui.game.expedition.ExpeditionScene
import sk.kubis.endlessdrive.ui.game.expedition.ModelPart
import sk.kubis.endlessdrive.ui.game.expedition.SceneMesh
import java.io.File

class ExpeditionSceneTest {
    private fun model(): List<ModelPart> = File("src/main/assets/expedition/car_modular.obj")
        .reader().use(ExpeditionObj::read)

    @Test fun repositoryModelUsesKnownSlotsAndFiniteTriangles() {
        val parts = model()
        assertEquals(26, parts.map { it.name }.distinct().size)
        parts.forEach {
            assertTrue(it.positions.isNotEmpty())
            assertEquals(0, it.positions.size % 9)
            assertTrue(it.positions.all(Float::isFinite))
            assertTrue(it.name == "Chassis_Shell" || ExpeditionScene.slotFor(it.name) != null)
        }
    }

    @Test fun objTriangulatesQuadsAndSupportsGlobalNegativeIndices() {
        val parts = ExpeditionObj.read("""
            o First
            v 0 0 0
            v 1 0 0
            v 1 1 0
            v 0 1 0
            usemtl metal
            f 1/1/1 2/2/2 3/3/3 4/4/4
            o Second
            f -4 -3 -2
        """.trimIndent().reader())
        assertEquals(2, parts.size)
        assertEquals(18, parts[0].positions.size)
        assertArrayEquals(floatArrayOf(0f,0f,0f,1f,0f,0f,1f,1f,0f),parts[1].positions,0f)
    }

    @Test fun renderingDoesNotMutateSaveAndInstalledPartsControlGeometry() {
        val engine = GameEngine(811L,0f,debugOptions=DebugOptions(allComponents=true,fullBody=true,fullFluids=true))
        val scene = ExpeditionScene(model())
        val before = RunCodec.encode(engine.snapshot())
        assertTrue(scene.update(engine))
        assertEquals(before,RunCodec.encode(engine.snapshot()))
        val complete = scene.carGeometry.visibleVertexCount
        val world = scene.world.buffer().let { buffer -> FloatArray(buffer.remaining()).also(buffer::get) }
        assertFalse(scene.update(engine))
        assertArrayEquals(world,scene.world.buffer().let { buffer -> FloatArray(buffer.remaining()).also(buffer::get) },0f)
        engine.car.parts.remove(ComponentSlot.HOOD)
        scene.update(engine)
        assertTrue(scene.carGeometry.visibleVertexCount < complete)
        assertTrue(scene.world.buffer().let { buffer -> (0 until buffer.limit()).all { buffer[it].isFinite() } })
    }

    @Test fun sceneRebasesCoordinatesAndInvalidatesOnNewRunAtSamePosition() {
        val scene = ExpeditionScene(model())
        val engine = GameEngine(91L,0f)
        scene.update(engine)
        engine.car.x = 1_000_123f
        engine.car.y = engine.segment.heightAtWorld(engine.car.x)+1f
        assertTrue(scene.update(engine))
        assertTrue(scene.carX in -24f..24f)
        assertTrue(scene.carY.isFinite())
        engine.car.planar.z=172f
        assertTrue(scene.update(engine))
        assertTrue(scene.carZ in -24f..24f)
        assertTrue(scene.update(GameEngine(91L,0f)))
    }

    @Test fun meshGrowsDirectBufferWithoutDroppingVertices() {
        val mesh = SceneMesh(18)
        repeat(40) { mesh.box(it.toFloat(),0f,0f,1f,1f,1f,0xffffff) }
        assertEquals(40*30,mesh.vertexCount)
        assertEquals(mesh.size,mesh.buffer().remaining())
        mesh.clear()
        assertEquals(0,mesh.vertexCount)
    }
}
