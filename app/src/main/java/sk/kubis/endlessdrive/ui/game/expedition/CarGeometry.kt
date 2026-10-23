package sk.kubis.endlessdrive.ui.game.expedition

import sk.kubis.endlessdrive.core.GameConfig
import sk.kubis.endlessdrive.domain.model.VehiclePaint
import sk.kubis.endlessdrive.domain.model.ComponentSlot
import sk.kubis.endlessdrive.game.GameEngine
import sk.kubis.endlessdrive.game.car.TireInjury
import sk.kubis.endlessdrive.game.world.World3D
import kotlin.math.*

/** Immutable local meshes stay on the GPU. Only 26 poses and paint colours change per frame. */
internal class CarGeometry(model: List<ModelPart>) {
    class Part(val name:String, val mesh:SceneMesh) {
        val matrix=FloatArray(16)
        var visible=true
        var paint=0xffffff
        var wear=0f
        var snow=0f
        var tireFloor=-1000f
        var tireDamage=0f
    }
    val parts=model.groupBy { it.name }.map { (name,groups) ->
        val mesh=SceneMesh(groups.sumOf { it.positions.size }/3*SceneMesh.STRIDE)
        for(group in groups) {
            mesh.material=when {
                group.material.startsWith("Mat_CarPaint") -> 4f
                group.material=="Mat_Glass_Tinted" -> 5f
                group.material=="Mat_Chrome" || group.material=="Mat_WheelRim" -> 6f
                group.material=="Mat_HeadlightLens" -> 7f
                group.material=="Mat_TaillightRed" -> 8f
                group.material=="Mat_TireRubber" -> 9f
                else -> 0f
            }
            val color=when(group.material) {
                "Mat_CarPaint" -> 0xffffff
                "Mat_CarPaint_Dark" -> 0xb0b0b0
                "Mat_Glass_Tinted" -> 0x344d58
                "Mat_Chrome","Mat_BrakeDisc","Mat_WheelRim" -> 0xb5bdc0
                "Mat_HeadlightLens" -> 0xe8e4ca
                "Mat_TaillightRed" -> 0xb52c24
                "Mat_TaillightWhite" -> 0xc4d0d0
                "Mat_EngineMetal","Mat_Radiator" -> 0x626f74
                "Mat_TireRubber" -> 0x24292b
                else -> 0x343b3e
            }
            val p=group.positions
            for(i in p.indices step 9) mesh.triangle(p[i],p[i+1],p[i+2],p[i+3],p[i+4],p[i+5],p[i+6],p[i+7],p[i+8],color,faceUp=false)
        }
        Part(name,mesh)
    } + equipment()
    val visibleVertexCount get()=parts.sumOf { if(it.visible) it.mesh.vertexCount else 0 }

    fun update(e:GameEngine,originX:Float,originY:Float,originZ:Float) {
        val car=e.car
        val cy=cos(car.planar.heading); val sy=sin(car.planar.heading)
        val cp=cos(car.visualPitch); val sp=sin(car.visualPitch)
        val cr=cos(car.planar.roll); val sr=sin(car.planar.roll)
        val bodyY=car.y-originY+GameConfig.BODY_VISUAL_LIFT
        for(part in parts) {
            val slot=if(part.name.startsWith("Chains_")) {
                if(part.name.startsWith("Chains_F")) ComponentSlot.TIRE_FRONT else ComponentSlot.TIRE_REAR
            } else ExpeditionScene.slotFor(part.name)
            part.visible=slot==null || car.hasPart(slot)
            if(!part.visible) continue
            if(part.name=="Utility_Trailer") part.visible=e.utilityTrailerAttached
            if(part.name=="Roof_Expedition") part.visible=e.hasExpeditionKit
            if(part.name.startsWith("Chains_")) part.visible=car.hasChains
            if(!part.visible) continue
            part.paint=VehiclePaint.at(car.parts[slot]?.paintIndex?.takeIf { it>=0 } ?: car.bodyPaintIndex).argb.toInt()
            part.wear=maxOf(car.bodyDirt*0.55f,car.bodyRust)
            part.snow=car.snowCover
            val wheel=part.name.startsWith("Wheel_") || part.name.startsWith("Chains_")
            val front=part.name.startsWith("Wheel_F") || part.name.startsWith("Chains_F")
            val pivotX=if(front) 1.764f else -1.764f
            val pivotZ=if(part.name.endsWith("L")) 0.78f else -0.78f
            // A shredded tyre exposes the full-size rim; shrinking the whole mesh
            // would shrink the rim twice and leave it floating above the road.
            val scale=if(part.name.startsWith("Chains_")) car.wheelVisualScale(slot!!)
                else if(wheel) car.wheelScale(slot!!) else 1f
            val spin=-(if(front) car.wheelSpinFrontDeg else car.wheelSpinRearDeg)*PI.toFloat()/180f
            val cs=cos(spin); val ss=sin(spin)
            val steer=if(front) car.planar.wheelAngle else 0f
            val ct=cos(steer); val st=sin(steer)
            val wx=car.x+pivotX*cy-pivotZ*sy; val wz=car.planar.z+pivotX*sy+pivotZ*cy
            val centerY=if(wheel && car.visuallyGrounded) World3D.height(e,wx,wz)-originY+
                GameConfig.WHEEL_RADIUS*car.wheelContactScale(slot!!) else bodyY+pivotX*sp
            part.tireDamage=if(!wheel) 0f else when(car.tireInjury(slot!!)) {
                TireInjury.INFLATED -> 0f; TireInjury.PUNCTURED -> 1f; TireInjury.SHREDDED -> 2f
            }
            part.tireFloor=if(wheel && car.visuallyGrounded) centerY-GameConfig.WHEEL_RADIUS*car.wheelContactScale(slot!!) else -1000f
            // Evaluate the affine transform four times, rather than once per model vertex.
            fun transform(x0:Float,y0:Float,z0:Float,out:Int) {
                var x=x0; var y=y0; var z=z0
                if(wheel) {
                    val dx=(x-pivotX)*scale; val dz=z-pivotZ
                    y*=scale
                    val rx=dx*cs-y*ss
                    y=dx*ss+y*cs
                    x=pivotX+rx*ct-dz*st; z=pivotZ+rx*st+dz*ct
                    part.matrix[out]=car.x-originX+x*cy-z*sy
                    part.matrix[out+1]=centerY+y
                    part.matrix[out+2]=car.planar.z-originZ+x*sy+z*cy
                } else {
                    val px=x*cp-y*sp; val py=x*sp+y*cp
                    val ry=py*cr-z*sr; val rz=py*sr+z*cr
                    val trailer=part.name=="Utility_Trailer"
                    val tx=if(trailer) car.x-6.5f*cy else car.x
                    val tz=if(trailer) car.planar.z-6.5f*sy else car.planar.z
                    part.matrix[out]=tx-originX+px*cy-rz*sy
                    part.matrix[out+1]=(if(trailer) World3D.height(e,tx,tz)-originY+0.5f else bodyY)+ry
                    part.matrix[out+2]=tz-originZ+px*sy+rz*cy
                }
            }
            transform(0f,0f,0f,12)
            transform(1f,0f,0f,0); transform(0f,1f,0f,4); transform(0f,0f,1f,8)
            for(i in intArrayOf(0,4,8)) for(j in 0..2) part.matrix[i+j]-=part.matrix[12+j]
            part.matrix[3]=0f; part.matrix[7]=0f; part.matrix[11]=0f; part.matrix[15]=1f
        }
    }

    private fun equipment():List<Part> {
        val trailer=SceneMesh(10_000).apply {
            material=4f
            box(0f,0.1f,0f,3.4f,0.2f,1.9f,0x78877d)
            for(z in floatArrayOf(-0.9f,0.9f)) box(0f,0.3f,z,3.4f,0.65f,0.1f,0x829083)
            box(-1.65f,0.3f,0f,0.1f,0.65f,1.9f,0x829083)
            material=0f
            beam(1.6f,0.1f,0f,3.65f,0.1f,0f,0.12f,0x535b5b)
            box(-0.3f,0.3f,0f,1.2f,0.55f,1.1f,0x9b8054)
            for(z in floatArrayOf(-1f,1f)) rock(-0.4f,-0.18f,z,0.45f,0.6f,0.18f,0x252c2e)
            for(z in floatArrayOf(-0.75f,0.75f)) box(-1.72f,0.25f,z,0.08f,0.2f,0.23f,0xb63129)
        }
        val rack=SceneMesh(8000).apply {
            material=4f
            box(-0.4f,1.66f,0f,0.9f,0.33f,1.2f,0x858b65)
            box(0.55f,1.66f,0f,0.7f,0.28f,1.1f,0xaf8249)
            material=0f
            for(x in floatArrayOf(-0.7f,-0.2f,0.4f,0.8f)) box(x,1.66f,0f,0.06f,0.36f,1.23f,0x303a3b)
            box(1.05f,1.64f,0f,0.16f,0.18f,1.15f,0xddd9c5)
        }
        val result=mutableListOf(Part("Utility_Trailer",trailer),Part("Roof_Expedition",rack))
        for(front in listOf(true,false)) for(left in listOf(true,false)) {
            val x=if(front) 1.764f else -1.764f; val z=if(left) 0.78f else -0.78f
            val mesh=SceneMesh(8000)
            for(i in 0 until 12) {
                val a=i*PI.toFloat()/6f; val b=a+0.18f
                mesh.beam(x+cos(a)*0.49f,sin(a)*0.49f,z-0.16f,
                    x+cos(b)*0.49f,sin(b)*0.49f,z+0.16f,0.03f,0xa7b0ad)
            }
            result+=Part("Chains_"+(if(front) "F" else "R")+(if(left) "L" else "R"),mesh)
        }
        return result
    }
}
