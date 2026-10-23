package sk.kubis.endlessdrive

import org.junit.Assert.*
import org.junit.Test
import sk.kubis.endlessdrive.core.GameConfig
import sk.kubis.endlessdrive.domain.model.*
import sk.kubis.endlessdrive.game.GameEngine
import sk.kubis.endlessdrive.game.car.TireInjury
import sk.kubis.endlessdrive.game.save.RunCodec
import sk.kubis.endlessdrive.game.world.World3D
import sk.kubis.endlessdrive.game.world.WorldProps
import sk.kubis.endlessdrive.ui.game.expedition.ExpeditionObj
import sk.kubis.endlessdrive.ui.game.expedition.ExpeditionScene
import java.io.File
import kotlin.math.*

class World3DIntegrationTest {
    private fun engine()=GameEngine(811L,0f,DebugOptions(fullUpgrades=true,fullBody=true,fullFluids=true))
    private fun tree(e:GameEngine)=(-3..20).asSequence().flatMap { i -> (-9..9).asSequence().mapNotNull { j -> WorldProps.at(e,i,j) } }
        .first { it.kind==WorldProps.Kind.TREE && it.x>10f }

    @Test fun treeTrunksUseRotatedFootprintAndAllowVerticalClearance() {
        val e=engine(); val p=tree(e)
        e.car.y=World3D.height(e,p.x,p.z)+GameConfig.WHEEL_RADIUS
        assertTrue(WorldProps.blocked(e,p.x-2.5f,p.z))
        assertFalse(WorldProps.blocked(e,p.x,p.z+1.5f))
        e.car.planar.heading=PI.toFloat()/2
        assertTrue(WorldProps.blocked(e,p.x,p.z-2.5f))
        assertFalse(WorldProps.blocked(e,p.x-1.5f,p.z))
        e.car.y=World3D.height(e,p.x,p.z)+p.height+2f
        assertFalse(WorldProps.blocked(e,p.x,p.z))
    }

    @Test fun fastCarCannotTunnelThroughTreeAndTrunksSurviveSaveRestore() {
        val e=engine(); val p=tree(e)
        e.car.x=p.x-7f; e.car.planar.z=p.z; e.car.planar.heading=0f
        e.car.y=World3D.height(e,e.car.x,p.z)+GameConfig.WHEEL_RADIUS
        assertTrue(e.tryStartEngine()); e.resumeDriving(); e.throttleInput=1f; e.car.speed=40f
        e.advance(0.25f)
        assertTrue("car stopped before trunk",e.car.x<p.x-2.4f)
        assertTrue("collision slows the vehicle",abs(e.car.speed)<4f)
        val restored=GameEngine.restore(RunCodec.decode(RunCodec.encode(e.snapshot()))!!,0f)
        val cellX=floor(p.x/WorldProps.CELL).toInt(); val cellZ=floor(p.z/WorldProps.CELL).toInt()
        assertEquals(p,WorldProps.at(restored,cellX,cellZ))
    }

    @Test fun steeringIntoEitherArmSelectsItsActualDestinationWithoutLateralTeleport() {
        for(index in 0..1) {
            val e=engine(); val outgoing=e.segment
            assertEquals(2,outgoing.choices.size)
            val target=outgoing.choices[index]
            val x=outgoing.endWorldX-0.5f
            e.car.x=x
            e.car.planar.z=World3D.roadCenter(x)+World3D.forkOffset(x,outgoing.endWorldX,index,2)
            val z=e.car.planar.z
            e.car.y=World3D.height(e,x,z)+GameConfig.WHEEL_RADIUS
            assertEquals(World3D.roadHeight(e,x),World3D.height(e,x,z),0.001f)
            assertEquals(outgoing.surfaceAtLocal(x-outgoing.worldOrigin),World3D.surface(e,x,z))
            assertTrue(e.tryStartEngine()); e.resumeDriving(); e.car.speed=12f; e.throttleInput=1f
            repeat(12) { e.advance(1f/60f) }
            assertEquals(target.style,e.segment.style)
            assertEquals(target.segmentSeed,e.segment.seed)
            assertEquals(z,e.car.planar.z,0.1f)
            val blend=e.visualBiomeBlendAt(e.segment.worldOrigin+GameConfig.JUNCTION_SCENERY_TRANSITION_M+1f)
            assertEquals(target.visualBiome,blend.dominant)
            val restored=GameEngine.restore(RunCodec.decode(RunCodec.encode(e.snapshot()))!!,0f)
            assertEquals(target.segmentSeed,restored.segment.seed)
            assertEquals(z,restored.car.planar.z,0.1f)
            assertEquals(World3D.height(e,e.car.x,e.car.planar.z),World3D.height(restored,e.car.x,e.car.planar.z),0.001f)
            assertEquals(e.entryForkX,restored.entryForkX,0f)
            assertEquals(e.entryForkIndex,restored.entryForkIndex)
        }
    }

    @Test fun carMeshesStayImmutableAndSmallMovementDoesNotRebuildWorld() {
        val model=File("src/main/assets/expedition/car_modular.obj").reader().use(ExpeditionObj::read)
        val scene=ExpeditionScene(model); val e=engine()
        scene.update(e)
        val immutable=scene.carGeometry.parts.first().mesh.buffer().let { b -> FloatArray(b.remaining()).also(b::get) }
        repeat(30) {
            e.car.x=it*0.1f; e.car.planar.z=sin(it.toFloat())*0.5f
            e.car.planar.heading=it*0.01f
            assertFalse(scene.update(e))
        }
        assertEquals(1,scene.worldBuildCount)
        assertArrayEquals(immutable,scene.carGeometry.parts.first().mesh.buffer().let { b -> FloatArray(b.remaining()).also(b::get) },0f)
        assertTrue(scene.vehicle.size*4<10_000)
        assertTrue(scene.carGeometry.parts.all { it.matrix.all(Float::isFinite) })
        e.car.parts.remove(ComponentSlot.HOOD)
        e.car.parts.remove(ComponentSlot.CHAINS)
        scene.update(e)
        assertFalse(scene.carGeometry.parts.first { it.name=="Hood" }.visible)
        assertFalse(scene.carGeometry.parts.first { it.name=="Chains_FL" }.visible)
        assertTrue(scene.carGeometry.parts.first { it.name=="Roof_Expedition" }.visible)
        println("3D static car vertices=${scene.carGeometry.parts.sumOf { it.mesh.vertexCount }}, dynamic frame bytes=${(scene.vehicle.size+scene.particles.size)*4}, world builds=${scene.worldBuildCount}")
    }

    @Test fun damagedTyresKeepFullSizeRimsAndWorkshopDoesNotBuildTheWorld() {
        val model=File("src/main/assets/expedition/car_modular.obj").reader().use(ExpeditionObj::read)
        val e=engine(); val scene=ExpeditionScene(model)
        e.car.parts.getValue(ComponentSlot.TIRE_FRONT).injury=TireInjury.SHREDDED
        e.car.parts.getValue(ComponentSlot.TIRE_REAR).injury=TireInjury.PUNCTURED
        e.car.mount(ComponentSlot.CHAINS,ItemStack(ItemCatalog.SNOW_CHAINS.id,ComponentCondition.USED,1f))
        val before=RunCodec.encode(e.snapshot())
        scene.update(e,preview=true)
        assertEquals(0,scene.worldBuildCount)
        assertEquals(0,scene.world.vertexCount)
        assertEquals(before,RunCodec.encode(e.snapshot()))
        val front=scene.carGeometry.parts.first { it.name=="Wheel_FL" }
        val rear=scene.carGeometry.parts.first { it.name=="Wheel_RL" }
        assertEquals(2f,front.tireDamage,0f)
        assertEquals(1f,rear.tireDamage,0f)
        val m=front.matrix
        assertEquals(e.car.wheelScale(ComponentSlot.TIRE_FRONT),sqrt(m[0]*m[0]+m[1]*m[1]+m[2]*m[2]),0.001f)
        assertTrue(scene.carGeometry.parts.first { it.name=="Chains_FL" }.visible)
        assertTrue(front.tireFloor.isFinite())
    }
}
