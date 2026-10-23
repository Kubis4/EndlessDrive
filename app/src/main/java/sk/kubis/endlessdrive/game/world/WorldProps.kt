package sk.kubis.endlessdrive.game.world

import sk.kubis.endlessdrive.domain.model.BiomeType
import sk.kubis.endlessdrive.game.GameEngine
import kotlin.math.*

/** Stable, shared descriptors: rendering and collision never generate different trees. */
object WorldProps {
    const val CELL = 11f
    enum class Kind { TREE, ROCK, SHRUB }
    data class Prop(val x: Float, val z: Float, val height: Float, val radius: Float,
                    val kind: Kind, val biome: BiomeType, val variant: Float)

    class Field(private val e:GameEngine) {
        private data class Cell(val i:Int,val j:Int,val segmentSeed:Long)
        private val cache=object:LinkedHashMap<Cell,Prop?>(1024,0.75f,true) {
            override fun removeEldestEntry(eldest:MutableMap.MutableEntry<Cell,Prop?>?)=size>1024
        }
        fun at(i:Int,j:Int):Prop? {
            val key=Cell(i,j,World3D.segmentAt(e,i*CELL).seed)
            if(cache.containsKey(key)) return cache[key]
            return generate(e,i,j).also { cache[key]=it }
        }
    }

    fun at(e:GameEngine,i:Int,j:Int):Prop?=e.propField.at(i,j)

    private fun generate(e: GameEngine, i: Int, j: Int): Prop? {
        val x=i*CELL+hash(i,j)*8f; val z=j*CELL+hash(j,i+72)*8f
        val lateral=World3D.roadDistance(e,x,z)
        if(lateral<7f || (abs(z-World3D.roadCenter(x))<24f && World3D.segmentAt(e,x).buildingOccupies(x,13f))) return null
        val blend=e.visualBiomeBlendAt(x)
        val biome=if(hash(i,j+91)<blend.amount) blend.to else blend.from
        val variant=hash(i+e.seed.toInt(),j+12)
        return when {
            (biome.wooded && variant>0.16f) || (!biome.arid && variant>0.86f) ->
                Prop(x,z,3f+hash(i,j+2)*3.5f,0.22f,Kind.TREE,biome,variant)
            variant>0.72f -> {
                val r=0.45f+hash(i,j+4)*1.15f
                Prop(x,z,r*0.65f,r,Kind.ROCK,biome,variant)
            }
            variant>0.25f -> Prop(x,z,0.35f+hash(i,j+3)*0.5f,0f,Kind.SHRUB,biome,variant)
            else -> null
        }
    }

    /** Circle against the oriented car footprint, with vertical clearance for airborne cars. */
    fun blocked(e: GameEngine, x: Float, z: Float): Boolean {
        val ix=floor(x/CELL).toInt(); val iz=floor(z/CELL).toInt()
        val c=cos(e.car.planar.heading); val s=sin(e.car.planar.heading)
        for(i in ix-1..ix+1) for(j in iz-1..iz+1) {
            val p=at(e,i,j) ?: continue
            val dx=p.x-x; val dz=p.z-z
            if(p.radius<=0f || abs(dx)>4.3f || abs(dz)>4.3f) continue
            val ax=(abs(dx*c+dz*s)-2.45f).coerceAtLeast(0f)
            val az=(abs(-dx*s+dz*c)-0.95f).coerceAtLeast(0f)
            if(ax*ax+az*az<p.radius*p.radius &&
                e.car.y-0.45f<=World3D.height(e,p.x,p.z)+p.height) return true
        }
        return false
    }

    fun hash(a:Int,b:Int):Float {
        var n=a*374761393+b*668265263
        n=(n xor (n ushr 13))*1274126177
        return ((n xor (n ushr 16)) and 0xffff)/65535f
    }
}
