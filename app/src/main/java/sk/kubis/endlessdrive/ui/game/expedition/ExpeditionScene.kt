package sk.kubis.endlessdrive.ui.game.expedition

import sk.kubis.endlessdrive.core.GameConfig
import sk.kubis.endlessdrive.domain.model.*
import sk.kubis.endlessdrive.game.GameEngine
import sk.kubis.endlessdrive.game.SceneEffects
import sk.kubis.endlessdrive.game.VisualDamageBurstBuffer
import sk.kubis.endlessdrive.game.VisualDamageBurstKind
import sk.kubis.endlessdrive.game.event.RoadEvent
import sk.kubis.endlessdrive.game.event.PursuitStormKind
import sk.kubis.endlessdrive.game.world.RoadSegment
import sk.kubis.endlessdrive.game.world.World3D
import sk.kubis.endlessdrive.game.world.WorldProps
import kotlin.math.*

/** World geometry and dynamic car/tyre effects, extracted under the simulation monitor. */
internal class ExpeditionScene(carModel: List<ModelPart>) {
    val carGeometry=CarGeometry(carModel)
    var worldBuildCount=0; private set
    val world = SceneMesh(700_000)
    val vehicle = SceneMesh(20_000)
    val particles = SceneMesh(12_000)
    var originX = Float.NaN; private set
    var originZ = Float.NaN; private set
    var originY = 0f; private set
    var carX = 0f; private set
    var carY = 0f; private set
    var carZ = 0f; private set
    var heading = 0f; private set
    var speed = 0f; private set
    var daylight = 1f; private set
    var headlights = 0f; private set
    var braking=0f; private set
    var fog = 0f; private set
    var groundTint = 0xd0b683; private set
    var elapsed = 0f; private set
    var raining = false; private set
    var snowing = false; private set
    private var engineRef: GameEngine? = null
    private var segmentRef: RoadSegment? = null
    private var nextRef: RoadSegment? = null
    private var choiceId: Int? = null
    private var buildingsKey=0
    private val trackCorners=FloatArray(128*2*4*3)
    private var trackCount=0
    private var trackHead=0
    private var lastTrackX=Float.NaN
    private var lastTrackZ=0f

    fun update(e: GameEngine,preview:Boolean=false): Boolean {
        val anchorX=if(originX.isFinite() && abs(e.car.x-originX)<24f) originX else round(e.car.x/32f)*32f
        val anchorZ=if(originZ.isFinite() && abs(e.car.planar.z-originZ)<24f) originZ else round(e.car.planar.z/32f)*32f
        var contentKey=1
        for(s in listOfNotNull(e.previousSegmentForRender,e.segment,e.nextSegmentForRender)) for(b in s.buildings)
            contentKey=31*contentKey+b.id.hashCode()+(if(b.looted) 1 else 0)+(if(b.relayRestored) 2 else 0)
        val rebuild=anchorX!=originX || anchorZ!=originZ || engineRef!==e || segmentRef!==e.segment ||
            nextRef!==e.nextSegmentForRender || choiceId!=e.segment.selectedChoiceId || contentKey!=buildingsKey
        if (engineRef!==e) { trackCount=0; trackHead=0; lastTrackX=Float.NaN }
        if (rebuild) {
            originX=anchorX; originZ=anchorZ; originY=World3D.roadHeight(e,anchorX)
            engineRef=e; segmentRef=e.segment; nextRef=e.nextSegmentForRender
            choiceId=e.segment.selectedChoiceId
            buildingsKey=contentKey
            if(!preview) buildWorld(e)
        }
        carX=e.car.x-originX
        carY=e.car.y-originY
        carZ=e.car.planar.z-originZ
        heading=e.car.planar.heading
        speed=e.car.speed
        daylight=e.daylight
        headlights=(if(e.headlightsOn) { if(e.highBeamsOn) 1f else 0.65f } else 0f)+(if(e.roofLightsOn) 0.7f else 0f)
        braking=e.brakeInput
        val fogEvent=e.activeEvents.firstOrNull { it.event==RoadEvent.FOG }
        fog=if(fogEvent!=null) SceneEffects.fogAmount(fogEvent.remaining,RoadEvent.FOG.duration)*0.8f else if(e.hasEvent(RoadEvent.STORM)) 0.38f else 0.06f
        elapsed=e.elapsed
        raining=e.hasEvent(RoadEvent.RAIN) || e.hasEvent(RoadEvent.STORM)
        snowing=e.winterAmount>0.2f
        e.pursuitStorm?.let {
            fog=maxOf(fog,((150f-it.gapM)/150f).coerceIn(0f,1f))
            if(it.gapM<50f) {
                raining=raining || it.kind==PursuitStormKind.THUNDER
                snowing=snowing || it.kind==PursuitStormKind.BLIZZARD
            }
        }
        groundTint=groundColor(e.visualBiomeBlendAt(e.car.x).dominant)
        if(!preview) updateTracks(e)
        carGeometry.update(e,originX,originY,originZ)
        if(!preview) buildVehicle(e)
        return rebuild
    }

    private fun groundColor(biome: BiomeType): Int = when(biome) {
        BiomeType.ALPINE -> 0xd7dfdb
        BiomeType.RURAL -> 0xa5a17b
        BiomeType.FOREST_ALIVE -> 0x88916a
        BiomeType.FOREST -> 0x8c8b76
        BiomeType.INDUSTRIAL -> 0xa19b89
        BiomeType.WASTELAND -> 0xc2b18e
        else -> 0xdec28a
    }

    private fun pavingColor(paving: RoadPaving): Int = when(paving) {
        RoadPaving.ASPHALT, RoadPaving.CRACKED -> 0x77746b
        RoadPaving.CONCRETE -> 0xbdb9a9
        RoadPaving.DIRT -> 0xc1aa83
        RoadPaving.GRAVEL_ROAD -> 0xc0b8a1
        RoadPaving.SAND_TRACK -> 0xe9d19b
        RoadPaving.SNOW, RoadPaving.PACKED_SNOW -> 0xe3e7df
    }

    private fun buildWorld(e: GameEngine) {
        world.clear()
        worldBuildCount++
        // Sample each lattice point once; six triangle vertices share the cached normal.
        val nx=74; val nz=55
        val heights=FloatArray(nx*nz)
        for(i in 0 until nx) for(j in 0 until nz) {
            val x=originX+(i-31)*4f; val z=originZ+(j-27)*4f
            // The coarse ground mesh stays beneath the separately tessellated road.
            // Physics still samples the exact shared height; no raised floating pavement.
            val underlay=((6f-World3D.roadDistance(e,x,z))/2f).coerceIn(0f,1f)*0.25f
            heights[i*nz+j]=World3D.height(e,x,z)-underlay
        }
        fun vertex(i:Int,j:Int) {
            val wx=originX+(i-31)*4f; val wz=originZ+(j-27)*4f
            val dx=heights[(i-1)*nz+j]-heights[(i+1)*nz+j]
            val dz=heights[i*nz+j-1]-heights[i*nz+j+1]
            val length=sqrt(dx*dx+64f+dz*dz)
            val blend=e.visualBiomeBlendAt(wx)
            world.material=if(blend.dominant.arid) 1f else 2f
            world.vertex(wx-originX,heights[i*nz+j]-originY,wz-originZ,dx/length,8f/length,dz/length,
                mix(groundColor(blend.from),groundColor(blend.to),blend.amount))
        }
        for(i in 1 until nx-2) for(j in 1 until nz-2) {
            vertex(i,j); vertex(i,j+1); vertex(i+1,j+1)
            vertex(i,j); vertex(i+1,j+1); vertex(i+1,j)
        }
        world.material=0f
        for (i in -120 until 168) {
            val x=originX+i
            if(!World3D.mainRoadVisible(e,x)) continue
            val segment=World3D.segmentAt(e,x)
            val color=pavingColor(segment.paving)
            roadStrip(e,x,x+1,-4f,4f,color,0.025f)
            roadStrip(e,x,x+1,-4.6f,-4f,tint(groundColor(segment.visualBiome),1.04f),0.018f)
            roadStrip(e,x,x+1,4f,4.6f,tint(groundColor(segment.visualBiome),1.04f),0.018f)
            if (segment.paving.rutted) {
                for (z in floatArrayOf(-0.8f,0.8f)) roadStrip(e,x,x+1,z-0.21f,z+0.21f,tint(color,0.91f),0.045f)
            } else {
                if (floor(x/5).toInt()%2==0) roadStrip(e,x,x+1,-0.045f,0.045f,0xe1d3a5,0.045f)
                roadStrip(e,x,x+1,-3.65f,-3.59f,0xc9c5af,0.045f)
                roadStrip(e,x,x+1,3.59f,3.65f,0xc9c5af,0.045f)
            }
            val surface=segment.surfaceAtLocal(x-segment.worldOrigin)
            if(surface!=RoadSurface.ASPHALT) {
                val patch=when(surface) {
                    RoadSurface.WATER,RoadSurface.ICE -> 0x90aaa8
                    RoadSurface.MUD -> 0x847058
                    else -> tint(color,0.88f)
                }
                roadStrip(e,x,x+1,-3.4f,3.4f,patch,0.055f)
            }
            if(segment.bridgeClearanceAtWorld(x)>0.4f && i%4==0) {
                for(z in floatArrayOf(-4.2f,4.2f)) {
                    val center=World3D.roadCenter(x)
                    val bottom=World3D.height(e,x,center+8f)-originY
                    val top=World3D.roadHeight(e,x)-originY
                    world.box(i.toFloat(),bottom,center+z-originZ,0.45f,(top-bottom).coerceAtLeast(0f)+0.9f,0.45f,0x9a9382)
                    world.beam(i.toFloat(),top+0.7f,center+z-originZ,i+4f,
                        World3D.roadHeight(e,x+4)-originY+0.7f,World3D.roadCenter(x+4)+z-originZ,0.17f,0xb4b2a3)
                }
            }
        }
        buildJunctions(e)
        buildScenery(e)
        val segments=listOfNotNull(e.previousSegmentForRender,e.segment,e.nextSegmentForRender).distinct()
        for(segment in segments) for(building in segment.buildings) {
            val wx=segment.worldOrigin+building.localX
            val wz=World3D.buildingZ(wx)
            if(abs(wx-originX)>130f || abs(wz-originZ)>100f) continue
            building(e,wx,wz,building.type,building.relayIndex>=0,building.relayRestored,building.looted)
        }
    }

    private fun buildJunctions(e:GameEngine) {
        for(s in listOfNotNull(e.previousSegmentForRender,e.segment)) {
            if(s.choices.size<2 || abs(s.endWorldX-originX)>180f) continue
            for(index in s.choices.indices) {
                val choice=s.choices[index]
                world.material=when(choice.plan.paving) { RoadPaving.ASPHALT,RoadPaving.CRACKED -> 3f; else -> 1f }
                val end=if(s===e.segment || choice.id==s.selectedChoice?.id) 100 else 0
                forkArm(e,s.endWorldX,index,s.choices.size,choice.plan.paving,-38,end)
                // Physical signposts also show the branch's surface colour.
                val wx=s.endWorldX-12f; val wz=World3D.roadCenter(wx)+(if(index==0) -1 else 1)*7f
                val y=World3D.height(e,wx,wz)-originY
                world.material=4f
                world.cylinder(wx-originX,y,wz-originZ,0.07f,2.8f,0x787c77)
                world.box(wx-originX,y+2f,wz-originZ,2.4f,0.7f,0.1f,pavingColor(choice.plan.paving))
            }
        }
        if(e.previousSegmentForRender==null && e.entryForkX.isFinite() && abs(e.entryForkX-originX)<180f)
            forkArm(e,e.entryForkX,e.entryForkIndex,2,e.segment.paving,0,100)
        world.material=0f
    }

    private fun forkArm(e:GameEngine,endX:Float,index:Int,count:Int,paving:RoadPaving,start:Int,end:Int) {
        world.material=when(paving) { RoadPaving.ASPHALT,RoadPaving.CRACKED -> 3f; else -> 1f }
        for(step in start until end) {
            val x=endX+step
            val a=World3D.roadCenter(x)+World3D.forkOffset(x,endX,index,count)
            val b=World3D.roadCenter(x+1)+World3D.forkOffset(x+1,endX,index,count)
            val y0=World3D.roadHeight(e,x)-originY+0.035f; val y1=World3D.roadHeight(e,x+1)-originY+0.035f
            val c=pavingColor(paving)
            world.triangle(x-originX,y0,a-4-originZ,x-originX,y0,a+4-originZ,x+1-originX,y1,b+4-originZ,c)
            world.triangle(x-originX,y0,a-4-originZ,x+1-originX,y1,b+4-originZ,x+1-originX,y1,b-4-originZ,c)
        }
    }

    private fun roadStrip(e:GameEngine,x0:Float,x1:Float,z0:Float,z1:Float,color:Int,lift:Float) {
        world.material=when(World3D.segmentAt(e,x0).paving) { RoadPaving.ASPHALT,RoadPaving.CRACKED -> 3f; RoadPaving.SAND_TRACK -> 1f; else -> 2f }
        if(lift>0.03f) world.material=0f
        val a=World3D.roadCenter(x0); val b=World3D.roadCenter(x1)
        val y0=World3D.roadHeight(e,x0)-originY+lift; val y1=World3D.roadHeight(e,x1)-originY+lift
        world.triangle(x0-originX,y0,a+z0-originZ,x0-originX,y0,a+z1-originZ,x1-originX,y1,b+z1-originZ,color)
        world.triangle(x0-originX,y0,a+z0-originZ,x1-originX,y1,b+z1-originZ,x1-originX,y1,b+z0-originZ,color)
    }

    private fun buildScenery(e:GameEngine) {
        val cellX=floor(originX/11f).toInt(); val cellZ=floor(originZ/11f).toInt()
        for(i in cellX-11..cellX+15) for(j in cellZ-9..cellZ+9) {
            val p=WorldProps.at(e,i,j) ?: continue
            val wx=p.x; val wz=p.z; val biome=p.biome
            val color=groundColor(biome)
            val x=wx-originX; val z=wz-originZ; val y=World3D.height(e,wx,wz)-originY
            val distance=hypot(x,z)
            if(distance>145f) continue
            val sides=if(distance>70f) 5 else 8
            world.material=4f
            when(p.kind) {
                WorldProps.Kind.TREE -> {
                    val h=p.height
                    world.cylinder(x,y,z,p.radius,h,0x72664e,0.09f,sides)
                    world.material=2f
                    if(biome!=BiomeType.FOREST) {
                        val foliage=if(biome.snowy) 0xc2d0c7 else 0x526b42
                        if(p.variant>0.58f || biome.snowy) {
                            // Staggered crowns avoid the old two-cone silhouette.
                            for(k in 0..3) {
                                val t=k/3f
                                world.cylinder(x,y+h*(0.25f+t*0.48f),z,1.9f-t*1.15f,h*(0.45f-t*0.18f),tint(foliage,0.9f+t*0.2f),0.06f,sides)
                            }
                        } else {
                            world.rock(x,y+h*0.68f,z,1.9f,h*0.43f,1.65f,foliage)
                            if(distance<70f) world.rock(x-0.8f,y+h*0.59f,z+0.4f,1.2f,h*0.3f,1.3f,tint(foliage,1.07f))
                        }
                    } else {
                        world.beam(x,y+h*0.55f,z,x-0.8f,y+h*0.8f,z+0.5f,0.12f,0x72664e)
                        world.beam(x,y+h*0.7f,z,x+0.7f,y+h*0.9f,z-0.4f,0.1f,0x72664e)
                    }
                    world.material=0f
                    shadow(e,wx,wz,0.6f,h,0.6f,color)
                }
                WorldProps.Kind.ROCK -> {
                    val r=p.radius
                    world.rock(x,y,z,r,p.height,r*0.8f,tint(color,0.79f))
                    world.material=0f
                    shadow(e,wx,wz,r*1.3f,p.height,r,color)
                }
                WorldProps.Kind.SHRUB -> if(distance<85f) {
                    world.cylinder(x,y,z,0.45f,p.height,0x8e8b60,0.12f,5)
                }
            }
            world.material=0f
        }
        val first=floor((originX-120)/30f).toInt()
        for(i in first..first+10) {
            val wx=i*30f; val wz=World3D.roadCenter(wx)-7f
            if(abs(wz-originZ)>100f || World3D.segmentAt(e,wx).bridgeClearanceAtWorld(wx)>0.5f) continue
            val y=World3D.height(e,wx,wz)-originY
            world.cylinder(wx-originX,y,wz-originZ,0.12f,6.4f,0x655843,0.08f)
            world.box(wx-originX,y+5.9f,wz-originZ,0.16f,0.15f,2.2f,0x655843)
            shadow(e,wx,wz,0.2f,6.4f,0.2f,groundColor(e.visualBiomeBlendAt(wx).dominant))
            for(side in floatArrayOf(-0.8f,0.8f)) for(part in 0..5) {
                val ax=wx+part*5f; val bx=ax+5
                fun wireY(x:Float):Float {
                    val t=(x-wx)/30f
                    return World3D.height(e,wx,wz)*(1-t)+World3D.height(e,wx+30,World3D.roadCenter(wx+30)-7)*t+
                        6.1f-sin(t*PI.toFloat())*0.65f-originY
                }
                world.beam(ax-originX,wireY(ax),World3D.roadCenter(ax)-7+side-originZ,
                    bx-originX,wireY(bx),World3D.roadCenter(bx)-7+side-originZ,0.032f,0x5d584c)
            }
        }
    }

    private fun building(e:GameEngine,wx:Float,wz:Float,type:BuildingType,relay:Boolean,restored:Boolean,looted:Boolean) {
        val x=wx-originX; val z=wz-originZ; val y=World3D.height(e,wx,wz)-originY
        val ground=groundColor(e.visualBiomeBlendAt(wx).dominant)
        val station=type==BuildingType.GAS_STATION
        world.quad(x-8,y+0.04f,x+8,y+0.04f,z-6,z+9,tint(ground,0.94f))
        if(type==BuildingType.WRECK) {
            world.box(x,y+0.25f,z,4.6f,0.65f,1.9f,0x8d7960)
            world.box(x-0.3f,y+0.9f,z,2.2f,0.55f,1.65f,0x626962)
            for(dx in floatArrayOf(-1.5f,1.5f)) for(dz in floatArrayOf(-0.9f,0.9f))
                world.rock(x+dx,y,z+dz,0.36f,0.5f,0.18f,0x343530)
            return
        }
        val wall=when(type) {
            BuildingType.GAS_STATION -> 0xd5cbb0
            BuildingType.AUTO_SHOP -> 0xb2b6a6
            BuildingType.PAINT_SHOP -> 0xc3b5a4
            else -> 0xb9aa8f
        }
        world.material=4f
        world.box(x,y,z,8.8f,3.6f,7f,wall)
        if(type==BuildingType.HOUSE) {
            world.triangle(x-4.8f,y+3.6f,z-3.9f,x-4.8f,y+5.2f,z,x+4.8f,y+5.2f,z,0x897765)
            world.triangle(x-4.8f,y+3.6f,z-3.9f,x+4.8f,y+5.2f,z,x+4.8f,y+3.6f,z-3.9f,0x897765)
            world.triangle(x-4.8f,y+5.2f,z,x-4.8f,y+3.6f,z+3.9f,x+4.8f,y+3.6f,z+3.9f,0x95816a)
            world.triangle(x-4.8f,y+5.2f,z,x+4.8f,y+3.6f,z+3.9f,x+4.8f,y+5.2f,z,0x95816a)
            for(dx in floatArrayOf(-4.4f,4.4f)) world.triangle(x+dx,y+3.6f,z-3.5f,x+dx,y+5.2f,z,x+dx,y+3.6f,z+3.5f,wall)
            world.box(x-2.5f,y+4.1f,z-1.5f,0.65f,1.6f,0.65f,0xa08e75)
        } else world.box(x,y+3.6f,z,9.4f,0.24f,7.7f,0xe2dcc6)
        val doorWidth=if(type==BuildingType.GARAGE || type.isServiceShop) 4.3f else 1.4f
        world.box(x,y+0.1f,z+3.52f,doorWidth,2.7f,0.08f,if(looted) 0x777366 else 0x55584f)
        if(doorWidth>2) for(i in 1..7) world.box(x,y+i*0.35f,z+3.58f,doorWidth,0.035f,0.07f,0x888b7d)
        for(dx in floatArrayOf(-3.1f,3.1f)) {
            world.box(x+dx,y+1.3f,z+3.55f,1.1f,1.2f,0.1f,0xe1d8bc)
            world.box(x+dx,y+1.4f,z+3.62f,0.9f,0.95f,0.1f,0x758985)
        }
        world.box(x,y+2.9f,z+3.62f,station.let { if(it) 5f else 2f },0.4f,0.12f,
            if(relay && restored) 0x789c61 else if(station) 0xa85f46 else 0x8c9073)
        world.material=0f
        shadow(e,wx,wz,8.8f,4.5f,7f,ground)
        if(station) {
            world.box(x,y+3.3f,z+7.8f,10f,0.3f,5f,0xdad2b8)
            world.box(x,y+3.1f,z+10.3f,10f,0.3f,0.15f,0xa56147)
            for(dx in floatArrayOf(-3.8f,3.8f)) world.cylinder(x+dx,y,z+8f,0.1f,3.4f,0xb7b6a5)
            for(dx in floatArrayOf(-2f,2f)) {
                world.box(x+dx,y,z+7.3f,0.8f,1.5f,0.7f,0xb36f50)
                world.box(x+dx,y+0.85f,z+7.7f,0.5f,0.35f,0.08f,0x34403c)
            }
        }
        if(relay) {
            world.cylinder(x-5.6f,y,z,0.15f,10f,0x8d8d7c,0.06f)
            world.box(x-5.6f,y+7f,z,0.25f,1.2f,2f,0xc1c6b9)
        }
        // Short roadside fence, kept outside the driveway.
        for(i in 0..4) {
            val fx=x+9+i*2f
            world.cylinder(fx,y,z+4.6f,0.07f,1.3f,0x8b7d62)
            if(i<4) for(h in floatArrayOf(0.55f,1.05f)) world.beam(fx,y+h,z+4.6f,fx+2,y+h,z+4.6f,0.05f,0x80755e)
        }
    }

    private fun shadow(e:GameEngine,x:Float,z:Float,w:Float,h:Float,d:Float,color:Int) {
        val shift=h*1.2f
        fun tri(lift:Float,width:Float,shade:Float) {
            fun v(wx:Float,wz:Float) = floatArrayOf(wx-originX,World3D.height(e,wx,wz)-originY+lift,wz-originZ)
            val a=v(x-width/2,z-d/2); val b=v(x+width/2,z-d/2)
            val c=v(x+width/2-shift,z+d/2+h*0.6f); val f=v(x-width/2-shift,z+d/2+h*0.6f)
            world.triangle(a[0],a[1],a[2],b[0],b[1],b[2],c[0],c[1],c[2],tint(color,shade))
            world.triangle(a[0],a[1],a[2],c[0],c[1],c[2],f[0],f[1],f[2],tint(color,shade))
        }
        tri(0.06f,w+0.3f,0.84f); tri(0.075f,w,0.72f)
    }

    private fun updateTracks(e:GameEngine) {
        val car=e.car
        if(!car.grounded || abs(car.speed)<0.6f) return
        if(lastTrackX.isFinite() && hypot(car.x-lastTrackX,car.planar.z-lastTrackZ)<0.7f) return
        if(e.currentSurface==RoadSurface.ASPHALT && car.wheelSlip<0.2f && abs(car.planar.lateralSpeed)<0.5f) return
        val c=cos(car.planar.heading); val s=sin(car.planar.heading)
        for(side in 0..1) for(corner in 0..3) {
            val forward=if(corner<2) -0.4f else 0.4f
            val lateral=(if(side==0) -0.78f else 0.78f)+(if(corner==0 || corner==3) -0.09f else 0.09f)
            val x=car.x+c*(forward-1.8f)-s*lateral
            val z=car.planar.z+s*(forward-1.8f)+c*lateral
            val k=(trackHead*8+side*4+corner)*3
            trackCorners[k]=x; trackCorners[k+1]=World3D.height(e,x,z)+0.075f; trackCorners[k+2]=z
        }
        trackHead=(trackHead+1)%128; trackCount=minOf(trackCount+1,128)
        lastTrackX=car.x; lastTrackZ=car.planar.z
    }

    private fun buildVehicle(e:GameEngine) {
        vehicle.clear()
        vehicle.material=0f
        particles.clear()
        val car=e.car
        val cy=cos(heading); val sy=sin(heading)
        for(i in 0 until trackCount) for(side in 0..1) {
            val a=(i*8+side*4)*3; val b=a+3; val c=a+6; val d=a+9
            val color=tint(groundTint,0.8f)
            fun triangle(u:Int,v:Int,w:Int) {
                vehicle.triangle(trackCorners[u]-originX,trackCorners[u+1]-originY,trackCorners[u+2]-originZ,
                    trackCorners[v]-originX,trackCorners[v+1]-originY,trackCorners[v+2]-originZ,
                    trackCorners[w]-originX,trackCorners[w+1]-originY,trackCorners[w+2]-originZ,color)
            }
            triangle(a,b,c); triangle(a,c,d)
        }
        // Rotated ground contact shadow.
        fun shadowPoint(x:Float,z:Float):FloatArray {
            val wx=car.x+x*cy-z*sy; val wz=car.planar.z+x*sy+z*cy
            return floatArrayOf(wx-originX,World3D.height(e,wx,wz)-originY+0.07f,wz-originZ)
        }
        val a=shadowPoint(-2.7f,-1f); val b=shadowPoint(-2.7f,1.25f); val c=shadowPoint(2.4f,1.25f); val d=shadowPoint(2.4f,-1f)
        vehicle.triangle(a[0],a[1],a[2],b[0],b[1],b[2],c[0],c[1],c[2],0x68604e)
        vehicle.triangle(a[0],a[1],a[2],c[0],c[1],c[2],d[0],d[1],d[2],0x68604e)
        if(abs(speed)>3f && e.currentSurface!=RoadSurface.ASPHALT && car.grounded) {
            for(i in 1..9) {
                val distance=2.5f+i*0.65f
                val x=carX-cy*distance-sy*sin(i*3f+elapsed)*0.7f
                val z=carZ-sy*distance+cy*sin(i*3f+elapsed)*0.7f
                val radius=0.45f+i*0.1f
                val y=World3D.height(e,x+originX,z+originZ)-originY+radius*0.5f
                fun particle(u:Float,v:Float) {
                    particles.vertex(x+radius*(u*0.84f+v*0.29f),y+radius*v*0.8f,
                        z+radius*(u*0.54f-v*0.45f),u,v,1f-i/12f,tint(groundTint,1.08f))
                }
                particle(-1f,-1f); particle(1f,-1f); particle(1f,1f)
                particle(-1f,-1f); particle(1f,1f); particle(-1f,1f)
            }
        }
        buildBodyEffects(e)
        // Weather occupies world space around the camera target and freezes with simulation time.
        if(raining || snowing) for(i in 0 until 80) {
            val x=carX+(hash(i,11)-0.5f)*60
            val z=carZ+(hash(i,12)-0.5f)*48
            val y=carY+((hash(i,13)*14f-elapsed*(if(snowing) 1.6f else 12f))%14f+14f)%14f
            if(snowing) billboard(x,y,z,0.10f,0xe1e7df,0.9f)
            else vehicle.beam(x,y,z,x+0.18f,y+0.8f,z,0.025f,0xa1b4b4)
        }
    }

    private fun billboard(x:Float,y:Float,z:Float,r:Float,color:Int,opacity:Float) {
        fun v(u:Float,w:Float) {
            particles.vertex(x+r*(u*0.84f+w*0.29f),y+r*w*0.8f,z+r*(u*0.54f-w*0.45f),u,w,opacity,color)
        }
        v(-1f,-1f); v(1f,-1f); v(1f,1f); v(-1f,-1f); v(1f,1f); v(-1f,1f)
    }

    private fun buildBodyEffects(e:GameEngine) {
        val car=e.car; val c=cos(heading); val s=sin(heading)
        val engineHealth=car.parts[ComponentSlot.ENGINE]?.health ?: 0f
        if(car.engineRunning) {
            val sick=((0.65f-engineHealth)*2f).coerceIn(0f,1f)
            for(i in 0..5) {
                val t=(elapsed*0.5f+i/6f)%1f
                val d=2.8f+t*(1.6f+abs(speed)*0.08f)
                billboard(carX-c*d,carY+0.15f+t*0.8f,carZ-s*d,0.15f+t*(0.25f+sick*0.5f),
                    if(sick>0.3f) 0x686a64 else 0xc9d0c9,(1-t)*(0.12f+sick*0.7f))
            }
            if(e.hasEvent(RoadEvent.MISFIRE) && SceneEffects.misfirePopping(SceneEffects.misfireBeat(elapsed)))
                billboard(carX-c*3f,carY+0.15f,carZ-s*3f,0.3f,0xffb34b,1f)
        }
        if(car.temperature>105f || e.hasEvent(RoadEvent.COOLANT_LEAK)) for(i in 0..4) {
            val t=(elapsed*0.45f+i/5f)%1f
            billboard(carX+c*(1.7f-t),carY+0.9f+t*2f,carZ+s*(1.7f-t),0.25f+t*0.65f,0xd4ded8,(1-t)*0.8f)
        }
        if(e.hasEvent(RoadEvent.FUEL_LEAK) || car.oil<0.5f) {
            val t=(elapsed*2f)%1f
            billboard(carX-c*1.7f-s*0.7f,carY+0.2f-t*0.5f,carZ-s*1.7f+c*0.7f,0.11f,0x66512c,0.8f)
        }
        for(i in 0 until e.visualDamageBursts.capacity) {
            val burst=e.visualDamageBursts.entryAt(i) ?: continue
            val age=VisualDamageBurstBuffer.normalizedAge(burst,elapsed) ?: continue
            val front=burst.kind!=VisualDamageBurstKind.TYRE_BLOWOUT
            val along=if(front) 2.5f else -1.76f
            for(k in 0..5) {
                val a=k*PI.toFloat()/3
                billboard(carX+c*along+cos(a)*age*1.8f,carY+0.2f+sin(age*PI.toFloat())*0.9f,
                    carZ+s*along+sin(a)*age*1.8f,0.06f+age*0.05f,
                    if(burst.kind==VisualDamageBurstKind.ROCK_STRIKE) 0xc1ac83 else 0x363d3b,(1-age)*1.6f)
            }
        }
        val storm=e.pursuitStorm
        if(storm!=null && (storm.kind==PursuitStormKind.SAND || storm.kind==PursuitStormKind.DUST) && storm.gapM<120f) {
            val opacity=((120f-storm.gapM)/120f).coerceIn(0f,1f)
            for(i in 0 until 24) {
                val x=carX+(hash(i,52)*45f+elapsed*8f)%45f-22.5f
                val z=carZ+(hash(i,53)-0.5f)*35f
                billboard(x,carY+hash(i,54)*9f,z,1.2f+hash(i,55),0xd6b582,opacity*0.6f)
            }
        }
    }

    companion object {
        fun slotFor(name:String):ComponentSlot?=when(name) {
            "Hood" -> ComponentSlot.HOOD
            "Front_Bumper" -> ComponentSlot.FRONT_BUMPER
            "Rear_Bumper" -> ComponentSlot.REAR_BUMPER
            "Trunk_Lid" -> ComponentSlot.TRUNK_LID
            "Door_Front_L","Door_Front_R" -> ComponentSlot.DOOR_FRONT
            "Door_Rear_L","Door_Rear_R" -> ComponentSlot.DOOR_REAR
            "Headlight_L","Headlight_R" -> ComponentSlot.HEADLIGHT
            "Taillight_L","Taillight_R" -> ComponentSlot.TAILLIGHT
            "Seat_Front_L","Seat_Front_R" -> ComponentSlot.SEAT_FRONT
            "Seat_Rear" -> ComponentSlot.SEAT_REAR
            "Roof_Rack" -> ComponentSlot.ROOF_RACK
            "Engine" -> ComponentSlot.ENGINE
            "Radiator" -> ComponentSlot.RADIATOR
            "Battery" -> ComponentSlot.BATTERY
            "Fuel_Tank" -> ComponentSlot.FUEL_TANK
            "Suspension_Drivetrain" -> ComponentSlot.SUSPENSION
            "Wheel_FL","Wheel_FR" -> ComponentSlot.TIRE_FRONT
            "Wheel_RL","Wheel_RR" -> ComponentSlot.TIRE_REAR
            else -> null
        }
        private fun hash(a:Int,b:Int):Float {
            var n=a*374761393+b*668265263
            n=(n xor (n ushr 13))*1274126177
            return ((n xor (n ushr 16)) and 0xffff)/65535f
        }
        private fun mix(a:Int,b:Int,t:Float):Int {
            fun c(s:Int)=((((a shr s) and 255)*(1-t)+((b shr s) and 255)*t).toInt().coerceIn(0,255))
            return (c(16) shl 16) or (c(8) shl 8) or c(0)
        }
        private fun tint(color:Int,f:Float):Int {
            fun channel(shift:Int)=(((color shr shift) and 255)*f).toInt().coerceIn(0,255)
            return (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
        }
    }
}
