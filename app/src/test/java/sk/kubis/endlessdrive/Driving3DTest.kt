package sk.kubis.endlessdrive

import org.junit.Assert.*
import org.junit.Test
import sk.kubis.endlessdrive.domain.model.DebugOptions
import sk.kubis.endlessdrive.domain.model.RoadSurface
import sk.kubis.endlessdrive.game.GameEngine
import sk.kubis.endlessdrive.game.car.PlanarMotion
import sk.kubis.endlessdrive.game.save.RunCodec
import sk.kubis.endlessdrive.game.world.World3D
import kotlin.math.*

class Driving3DTest {
    @Test fun leftAndRightSteeringMirrorEachOther() {
        val left=PlanarMotion(); val right=PlanarMotion()
        repeat(90) {
            left.advance(10f,-0.7f,1f,true,1f/60f)
            right.advance(10f,0.7f,1f,true,1f/60f)
        }
        assertTrue(left.z < -1f)
        assertTrue(right.z > 1f)
        assertEquals(left.z,-right.z,0.001f)
        assertEquals(left.heading,-right.heading,0.001f)
        assertEquals(left.deltaX,right.deltaX,0.001f)
    }

    @Test fun steeringAtRestDoesNotRotateOrTranslateAndReverseReversesYaw() {
        val stationary=PlanarMotion()
        repeat(120) { stationary.advance(0f,1f,1f,true,1f/60f) }
        assertEquals(0f,stationary.heading,0f)
        assertEquals(0f,stationary.z,0f)
        val reverse=PlanarMotion()
        repeat(40) { reverse.advance(-5f,1f,1f,true,1f/60f) }
        assertTrue(reverse.heading < -0.1f)
        assertTrue(reverse.deltaX < 0f)
    }

    @Test fun sidewaysHeadingAdvancesInWorldZAndAirborneCarCannotSteer() {
        val car=PlanarMotion().also { it.heading=PI.toFloat()/2 }
        repeat(60) { car.advance(6f,0f,1f,true,1f/60f) }
        assertEquals(6f,car.z,0.01f)
        assertEquals(0f,car.deltaX,0.001f)
        val flying=PlanarMotion()
        repeat(60) { flying.advance(12f,1f,1f,false,1f/60f) }
        assertEquals(0f,flying.heading,0f)
        assertEquals(0f,flying.z,0f)
    }

    @Test fun slipperySurfaceLimitsTurningAndStateRemainsFinite() {
        val dry=PlanarMotion(); val ice=PlanarMotion()
        repeat(120) {
            dry.advance(28f,1f,1f,true,1f/60f)
            ice.advance(28f,1f,0.2f,true,1f/60f)
        }
        assertTrue(abs(ice.yawRate)<abs(dry.yawRate))
        repeat(3000) { dry.advance(25f,if(it%240<120) 1f else -1f,0.7f,true,1f/60f) }
        assertTrue(dry.z.isFinite() && dry.heading.isFinite() && dry.lateralSpeed.isFinite())
        assertTrue(dry.heading in -PI.toFloat()..PI.toFloat())
    }

    private fun engine()=GameEngine(811L,0f,DebugOptions(fullUpgrades=true,fullBody=true,fullFluids=true)).also {
        assertTrue(it.tryStartEngine())
        it.resumeDriving()
        it.throttleInput=1f
    }

    @Test fun engineUsesSteeringForActualLateralTravelAndSaveRestoresIt() {
        val engine=engine()
        engine.steeringInput=0.7f
        repeat(180) { engine.advance(1f/60f) }
        assertTrue("Car moves in Z",engine.car.planar.z>0.5f)
        assertTrue(engine.car.planar.heading>0.05f)
        val snapshot=RunCodec.decode(RunCodec.encode(engine.snapshot()))!!
        val restored=GameEngine.restore(snapshot,0f)
        assertEquals(engine.car.planar.z,restored.car.planar.z,0.0001f)
        assertEquals(engine.car.planar.heading,restored.car.planar.heading,0.0001f)
        assertTrue(restored.car.y.isFinite())
    }

    @Test fun oldSavesLoadWithNeutralLateralPosition() {
        val text=RunCodec.encode(engine().snapshot()).lines().toMutableList()
        text[0]="v8"
        text[2]=text[2].split('|').take(18).joinToString("|")
        val snapshot=RunCodec.decode(text.joinToString("\n"))!!
        assertEquals(0f,snapshot.car.lateralZ,0f)
        assertEquals(0f,snapshot.car.heading,0f)
    }

    @Test fun wheelSurfaceAndSolidBuildingsUseRenderedWorldCoordinates() {
        val e=engine()
        val x=e.car.x
        assertEquals(e.segment.heightAtWorld(x),World3D.height(e,x,World3D.roadCenter(x)),0.001f)
        assertNotEquals(RoadSurface.ASPHALT,World3D.surface(e,x,World3D.roadCenter(x)+12f))
        val building=e.segment.buildings.first()
        val wx=e.segment.worldOrigin+building.localX
        assertTrue(World3D.blocked(e,wx,World3D.buildingZ(wx)))
        assertFalse(World3D.blocked(e,wx,World3D.roadCenter(wx)))
        assertTrue(World3D.height(e,wx,100f).isFinite())
    }
}
