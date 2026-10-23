package sk.kubis.endlessdrive.ui.game.expedition

import java.io.Reader
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.sqrt
import kotlin.math.sin
import kotlin.math.cos
import kotlin.math.PI

/** Unindexed triangles: position, flat normal, RGB. Reused CPU/direct buffers. */
internal class SceneMesh(capacity: Int = 300_000) {
    private var values = FloatArray(capacity)
    private var direct = ByteBuffer.allocateDirect(capacity * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
    var size = 0
        private set
    val vertexCount get() = size / STRIDE
    /** 0 = plain, 1 = sand, 2 = earth, 3 = asphalt, 4 = painted metal. */
    var material = 0f

    fun clear() { size = 0 }

    fun vertex(x: Float, y: Float, z: Float, nx: Float, ny: Float, nz: Float, color: Int) {
        if (size + STRIDE > values.size) {
            values = values.copyOf(values.size * 2)
            direct = ByteBuffer.allocateDirect(values.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        }
        values[size++] = x; values[size++] = y; values[size++] = z
        values[size++] = nx; values[size++] = ny; values[size++] = nz
        values[size++] = ((color shr 16) and 255) / 255f
        values[size++] = ((color shr 8) and 255) / 255f
        values[size++] = (color and 255) / 255f
        values[size++] = material
    }

    fun triangle(ax: Float, ay: Float, az: Float, bx: Float, by: Float, bz: Float,
                 cx: Float, cy: Float, cz: Float, color: Int, faceUp: Boolean = true) {
        val ux = bx - ax; val uy = by - ay; val uz = bz - az
        val vx = cx - ax; val vy = cy - ay; val vz = cz - az
        var nx = uy * vz - uz * vy; var ny = uz * vx - ux * vz; var nz = ux * vy - uy * vx
        val length = sqrt(nx * nx + ny * ny + nz * nz).coerceAtLeast(0.0001f)
        nx /= length; ny /= length; nz /= length
        // Terrain quads may be authored from either side; culling is disabled.
        if (faceUp && ny < 0f) { nx = -nx; ny = -ny; nz = -nz }
        vertex(ax, ay, az, nx, ny, nz, color)
        vertex(bx, by, bz, nx, ny, nz, color)
        vertex(cx, cy, cz, nx, ny, nz, color)
    }

    fun quad(x0: Float, y0: Float, x1: Float, y1: Float, z0: Float, z1: Float, color: Int) {
        triangle(x0, y0, z0, x0, y0, z1, x1, y1, z1, color)
        triangle(x0, y0, z0, x1, y1, z1, x1, y1, z0, color)
    }

    fun box(x: Float, y: Float, z: Float, w: Float, h: Float, d: Float, color: Int) {
        val l = x - w / 2; val r = x + w / 2; val f = z - d / 2; val b = z + d / 2; val t = y + h
        quad(l, t, r, t, f, b, color)
        triangle(l,y,f, r,y,f, r,t,f,color); triangle(l,y,f, r,t,f, l,t,f,color)
        triangle(r,y,b, l,y,b, l,t,b,color); triangle(r,y,b, l,t,b, r,t,b,color)
        triangle(l,y,b, l,y,f, l,t,f,color); triangle(l,y,b, l,t,f, l,t,b,color)
        triangle(r,y,f, r,y,b, r,t,b,color); triangle(r,y,f, r,t,b, r,t,f,color)
    }

    fun buffer(): FloatBuffer {
        direct.clear(); direct.put(values, 0, size); direct.flip()
        return direct
    }

    fun cylinder(x: Float,y: Float,z: Float,radius: Float,height: Float,color: Int,topRadius: Float=radius,sides: Int=8) {
        for (i in 0 until sides) {
            val a=i*2f*PI.toFloat()/sides; val b=(i+1)*2f*PI.toFloat()/sides
            val ax=x+cos(a)*radius; val az=z+sin(a)*radius
            val bx=x+cos(b)*radius; val bz=z+sin(b)*radius
            val tx=x+cos(a)*topRadius; val tz=z+sin(a)*topRadius
            val ux=x+cos(b)*topRadius; val uz=z+sin(b)*topRadius
            triangle(ax,y,az,bx,y,bz,ux,y+height,uz,color)
            triangle(ax,y,az,ux,y+height,uz,tx,y+height,tz,color)
            triangle(x,y+height,z,tx,y+height,tz,ux,y+height,uz,color)
        }
    }

    fun rock(x:Float,y:Float,z:Float,rx:Float,ry:Float,rz:Float,color:Int) {
        val n=8
        for (layer in 0 until 3) for (i in 0 until n) {
            val a=i*2f*PI.toFloat()/n; val b=(i+1)*2f*PI.toFloat()/n
            val low=layer*PI.toFloat()/6f; val high=(layer+1)*PI.toFloat()/6f
            triangle(x+cos(a)*rx*cos(low),y+ry*sin(low),z+sin(a)*rz*cos(low),
                x+cos(b)*rx*cos(low),y+ry*sin(low),z+sin(b)*rz*cos(low),
                x+cos(b)*rx*cos(high),y+ry*sin(high),z+sin(b)*rz*cos(high),color)
            triangle(x+cos(a)*rx*cos(low),y+ry*sin(low),z+sin(a)*rz*cos(low),
                x+cos(b)*rx*cos(high),y+ry*sin(high),z+sin(b)*rz*cos(high),
                x+cos(a)*rx*cos(high),y+ry*sin(high),z+sin(a)*rz*cos(high),color)
        }
    }

    fun beam(ax:Float,ay:Float,az:Float,bx:Float,by:Float,bz:Float,width:Float,color:Int) {
        val dx=bx-ax; val dz=bz-az
        val length=sqrt(dx*dx+dz*dz).coerceAtLeast(0.001f)
        val sx=-dz/length*width/2; val sz=dx/length*width/2
        triangle(ax+sx,ay,az+sz,ax-sx,ay,az-sz,bx-sx,by,bz-sz,color)
        triangle(ax+sx,ay,az+sz,bx-sx,by,bz-sz,bx+sx,by,bz+sz,color)
        triangle(ax,ay-width/2,az,bx,by-width/2,bz,bx,by+width/2,bz,color)
        triangle(ax,ay-width/2,az,bx,by+width/2,bz,ax,ay+width/2,az,color)
    }

    companion object { const val STRIDE = 10 }
}

internal data class ModelPart(val name: String, val material: String, val positions: FloatArray)

/** Reads the repository's existing OBJ. Face indices are global, including across objects. */
internal object ExpeditionObj {
    fun read(reader: Reader): List<ModelPart> {
        val vertices = ArrayList<FloatArray>()
        val groups = linkedMapOf<Pair<String, String>, MutableList<Float>>()
        var name = "Chassis_Shell"
        var material = "Mat_CarPaint"
        reader.buffered().forEachLine { raw ->
            val line = raw.trim()
            if (line.isNotEmpty() && !line.startsWith('#')) {
                val tokens = line.split(Regex("\\s+"))
                when (tokens[0]) {
                    "v" -> vertices.add(floatArrayOf(tokens[1].toFloat(), tokens[2].toFloat(), tokens[3].toFloat()))
                    "o" -> name = tokens[1]
                    "usemtl" -> material = tokens[1]
                    "f" -> {
                        val indices = tokens.drop(1).map {
                            val index = it.substringBefore('/').toInt()
                            if (index > 0) index - 1 else vertices.size + index
                        }
                        val target = groups.getOrPut(name to material) { ArrayList() }
                        for (i in 1 until indices.lastIndex) {
                            for (index in intArrayOf(indices[0], indices[i], indices[i + 1])) {
                                vertices[index].forEach(target::add)
                            }
                        }
                    }
                }
            }
        }
        return groups.map { (key, positions) -> ModelPart(key.first, key.second, positions.toFloatArray()) }
    }
}
