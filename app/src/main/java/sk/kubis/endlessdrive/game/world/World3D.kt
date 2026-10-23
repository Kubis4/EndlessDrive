package sk.kubis.endlessdrive.game.world

import sk.kubis.endlessdrive.domain.model.RoadSurface
import sk.kubis.endlessdrive.game.GameEngine
import kotlin.math.*

/** One source of geometry for rendering, wheel contacts, off-road surfaces and collisions. */
object World3D {
    const val ROAD_HALF_WIDTH = 4f
    const val BUILDING_OFFSET = -14f

    fun segmentAt(e: GameEngine, x: Float): RoadSegment = when {
        x < e.segment.worldOrigin -> e.previousSegmentForRender ?: e.segment
        x > e.segment.endWorldX -> e.nextSegmentForRender ?: e.segment
        else -> e.segment
    }

    fun roadCenter(x: Float): Float {
        val t = ((x - 80f) / 140f).coerceIn(0f, 1f)
        val blend = t * t * (3f - 2f * t)
        return blend * (sin(x * 0.010f) * 7f + sin(x * 0.027f) * 2.2f)
    }

    fun roadHeight(e: GameEngine, x: Float) = segmentAt(e, x).heightAtWorld(x)

    /** Fork arms are traversable roads, sharing height and surface with their junction. */
    fun forkOffset(x: Float, end: Float, index: Int, count: Int): Float {
        val t=((x-(end-38f))/38f).coerceIn(0f,1f)
        val exit=((end+100f-x)/100f).coerceIn(0f,1f)
        return (index-(count-1)*0.5f)*24f*(t*t*(3f-2f*t))*(exit*exit*(3f-2f*exit))
    }

    fun roadDistance(e: GameEngine, x: Float, z: Float): Float {
        var distance=if(mainRoadVisible(e,x)) abs(z-roadCenter(x)) else Float.POSITIVE_INFINITY
        fun fork(s: RoadSegment?) {
            if(s==null || s.choices.size<2 || x<s.endWorldX-38f || x>s.endWorldX+100f) return
            // Past the junction only the chosen arm continues.
            for(i in s.choices.indices) {
                if(s!==e.segment && x>s.endWorldX && s.choices[i].id!=s.selectedChoice?.id) continue
                distance=minOf(distance,abs(z-roadCenter(x)-forkOffset(x,s.endWorldX,i,s.choices.size)))
            }
        }
        fork(e.segment); fork(e.previousSegmentForRender)
        if(e.previousSegmentForRender==null && e.entryForkX.isFinite() && x>=e.entryForkX && x<e.entryForkX+100f) {
            distance=minOf(distance,abs(z-roadCenter(x)-forkOffset(x,e.entryForkX,e.entryForkIndex,2)))
        }
        return distance
    }

    fun mainRoadVisible(e:GameEngine,x:Float):Boolean {
        fun split(s:RoadSegment?):Boolean = s!=null && s.choices.size>1 &&
            abs(forkOffset(x,s.endWorldX,0,s.choices.size))>3f
        if(split(e.segment) || split(e.previousSegmentForRender)) return false
        if(e.previousSegmentForRender==null && e.entryForkX.isFinite() && x>=e.entryForkX &&
            abs(forkOffset(x,e.entryForkX,0,2))>3f) return false
        return true
    }

    fun height(e: GameEngine, x: Float, z: Float): Float {
        val segment = segmentAt(e, x)
        val lateral = roadDistance(e,x,z)
        val road = segment.heightAtWorld(x)
        val ground = segment.groundAtWorld(x)
        val edge = ((lateral - ROAD_HALF_WIDTH) / 2f).coerceIn(0f, 1f)
        val base = road + (ground - road) * edge
        val shoulder = ((lateral - 19f) / 24f).coerceIn(0f, 1f)
        val dunes = sin(x * 0.043f + z * 0.058f) * 1.8f +
            cos(z * 0.091f - x * 0.021f) * 1.2f + sin(x * 0.016f + z * 0.034f) * 2.2f
        return base + dunes * shoulder
    }

    fun surface(e: GameEngine, x: Float, z: Float): RoadSurface {
        if (roadDistance(e,x,z) <= ROAD_HALF_WIDTH) {
            val segment = segmentAt(e, x)
            return segment.surfaceAtLocal(x - segment.worldOrigin)
        }
        return if (segmentAt(e,x).visualBiome.arid) RoadSurface.SAND else RoadSurface.GRAVEL
    }

    fun slope(e: GameEngine, x: Float, z: Float, heading: Float): Float {
        val dx = cos(heading) * 0.45f
        val dz = sin(heading) * 0.45f
        return (height(e,x+dx,z+dz) - height(e,x-dx,z-dz)) / 0.9f
    }

    fun buildingZ(worldX: Float) = roadCenter(worldX) + BUILDING_OFFSET

    /** Separating-axis test: the oriented car body against rendered rectangular foundations. */
    fun blocked(e: GameEngine, x: Float, z: Float): Boolean {
        fun inSegment(segment: RoadSegment?): Boolean {
            if (segment == null) return false
            for (building in segment.buildings) {
                val wx = segment.worldOrigin + building.localX
                val halfX = if (building.type == sk.kubis.endlessdrive.domain.model.BuildingType.WRECK) 2.3f else 4.4f
                val halfZ = if (building.type == sk.kubis.endlessdrive.domain.model.BuildingType.WRECK) 1f else 3.5f
                val c=cos(e.car.planar.heading); val s=sin(e.car.planar.heading)
                val hx=abs(c); val hz=abs(s)
                val dx=x-wx; val dz=z-buildingZ(wx)
                if(abs(dx)>=halfX+hx*2.45f+hz*0.95f || abs(dz)>=halfZ+hz*2.45f+hx*0.95f) continue
                if(abs(dx*c+dz*s)>=2.45f+halfX*hx+halfZ*hz || abs(-dx*s+dz*c)>=0.95f+halfX*hz+halfZ*hx) continue
                val height=if(building.type==sk.kubis.endlessdrive.domain.model.BuildingType.WRECK) 1.45f else 3.6f
                if(e.car.y-0.45f<=World3D.height(e,wx,buildingZ(wx))+height) return true
            }
            return false
        }
        return inSegment(e.segment) || inSegment(e.previousSegmentForRender) || inSegment(e.nextSegmentForRender) ||
            WorldProps.blocked(e,x,z)
    }
}
