package sk.kubis.endlessdrive.ui.game

import android.graphics.Path as AndroidPath
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import sk.kubis.endlessdrive.core.GameConfig
import sk.kubis.endlessdrive.core.MathX
import sk.kubis.endlessdrive.domain.model.BiomeType
import sk.kubis.endlessdrive.domain.model.BranchStyle
import sk.kubis.endlessdrive.domain.model.BuildingType
import sk.kubis.endlessdrive.game.DepthProjection
import sk.kubis.endlessdrive.game.GameEngine
import sk.kubis.endlessdrive.game.world.RoadSegment

/**
 * Bočný 2.5D renderer: pozadia, textúry zeme, HillRush kopce, sedan vrstvy.
 */
class GameRenderer(private val assets: GameAssets) {
    private val depth = DepthProjection()
    private val carArtist = CarArtist()
    private val shaders = TerrainShaders(assets.grassTex, assets.dirtTex)
    private val band = Path()
    private val edge = Path()
    private val soil = Path()
    private val roof = Path()
    private val androidBand = AndroidPath()

    private val terrainX = FloatArray(MAX_POINTS)
    private val terrainY = FloatArray(MAX_POINTS)
    private val terrainW = FloatArray(MAX_POINTS)
    private var terrainCount = 0

    fun DrawScope.draw(engine: GameEngine) {
        engine.setScreenHeight(size.height)
        val cam = engine.camera
        val halfW = size.width / 2f
        val halfH = size.height / 2f
        depth.begin(cam.x, cam.y, cam.ppm, halfW, halfH, cam.pitch)

        drawBackground(engine.segment.biome, cam.x)
        collectTerrain(engine, cam.ppm, size.width)
        drawTerrain(engine.segment, cam.x, cam.ppm)
        drawJunctionPreview(engine)
        drawBuildings(engine)
        drawCar(engine)
    }

    private fun DrawScope.drawBackground(biome: BiomeType, camX: Float) {
        val bg = assets.backgroundFor(biome)
        val sky = when (biome) {
            BiomeType.RURAL -> Color(0xFF7EB6C9)
            BiomeType.INDUSTRIAL -> Color(0xFF6A8FA0)
            BiomeType.WASTELAND -> Color(0xFF3A4558)
        }
        drawRect(sky, size = Size(size.width, size.height * 0.65f))

        // Šírka = celá obrazovka (+parallax); výšku orežeme posunom, nie squishom.
        val coverW = size.width * 1.18f
        val scale = coverW / bg.width
        val drawW = coverW
        val drawH = bg.height * scale
        val visibleH = size.height * 0.50f
        val parallax = camX * 0.05f
        val maxShift = (drawW - size.width).coerceAtLeast(0f)
        val x = if (maxShift > 1f) {
            val cycle = maxShift * 2f
            val t = ((parallax % cycle) + cycle) % cycle
            val shift = if (t <= maxShift) t else cycle - t
            -shift
        } else {
            (size.width - drawW) * 0.5f
        }
        // Ak je obrázok vyšší, posunieme hore — vidno spodok (horizont), nie celý gigantický rám.
        val y = if (drawH > visibleH) {
            -(drawH - visibleH) * 0.45f
        } else {
            size.height * 0.02f
        }
        drawImage(
            image = bg,
            dstOffset = androidx.compose.ui.unit.IntOffset(x.toInt(), y.toInt()),
            dstSize = androidx.compose.ui.unit.IntSize(
                drawW.toInt().coerceAtLeast(1),
                drawH.toInt().coerceAtLeast(1)
            )
        )
    }

    private fun collectTerrain(engine: GameEngine, ppm: Float, screenWidth: Float) {
        val halfWorld = screenWidth / (2f * ppm) + 6f
        val from = engine.camera.x - halfWorld
        val to = engine.camera.x + halfWorld
        val step = 1.1f
        var n = 0
        var wx = from
        while (wx <= to && n < MAX_POINTS) {
            val wy = heightFor(engine, wx)
            terrainW[n] = wx
            terrainX[n] = depth.frontX(wx)
            terrainY[n] = depth.frontY(wy)
            n++
            wx += step
        }
        terrainCount = n
    }

    private fun heightFor(engine: GameEngine, worldX: Float): Float {
        val seg = engine.segment
        return when {
            worldX < seg.worldOrigin -> seg.heightAtLocal(0f)
            worldX > seg.endWorldX -> {
                val over = worldX - seg.endWorldX
                val base = seg.heightAtLocal(seg.length)
                base + MathX.approxSin(over * 0.08f) * 0.15f
            }
            else -> seg.heightAtWorld(worldX)
        }
    }

    private fun DrawScope.drawTerrain(segment: RoadSegment, camX: Float, ppm: Float) {
        val n = terrainCount
        if (n < 2) return

        val grass = when (segment.biome) {
            BiomeType.RURAL -> Color(0xFF7A8F5A)
            BiomeType.INDUSTRIAL -> Color(0xFF6A7460)
            BiomeType.WASTELAND -> Color(0xFF8A7A4F)
        }
        val road = when (segment.biome) {
            BiomeType.RURAL -> Color(0xFF52483C)
            BiomeType.INDUSTRIAL -> Color(0xFF454545)
            BiomeType.WASTELAND -> Color(0xFF5E5040)
        }
        val soilColor = Color(0xFF6B5340)

        // 1) Nepriehľadná pôda vpredu – zakryje background.
        soil.reset()
        for (i in 0 until n) {
            if (i == 0) soil.moveTo(terrainX[i], terrainY[i]) else soil.lineTo(terrainX[i], terrainY[i])
        }
        soil.lineTo(terrainX[n - 1], size.height + 4f)
        soil.lineTo(terrainX[0], size.height + 4f)
        soil.close()
        drawPath(soil, soilColor)
        // Bez BitmapShader — textúra sa pri kopcoch „plazila“ a rušila.

        // 2) Tráva (solid).
        buildBand(n, 0f, GameConfig.ROAD_DEPTH + 0.6f, 0f)
        drawPath(band, grass)

        // 3) Cesta — bez wobble, aby sa neklukala pri náklone.
        buildBand(n, GameConfig.VERGE_DEPTH, GameConfig.ROAD_DEPTH - 0.15f, 0f)
        drawPath(band, road)

        buildBand(n, GameConfig.VERGE_DEPTH + 0.25f, GameConfig.ROAD_DEPTH - 0.45f, 0f)
        drawPath(band, Color(road.red * 0.92f, road.green * 0.92f, road.blue * 0.92f))

        drawRut(n, GameConfig.RUT_NEAR_DEPTH, 1.1f, Color(0xFF2A2218))
        drawRut(n, GameConfig.RUT_FAR_DEPTH, 7.3f, Color(0xFF2A2218))

        buildEdge(n, GameConfig.ROAD_DEPTH)
        drawPath(
            edge,
            Color(0xFF6B7A4A).copy(alpha = 0.55f),
            style = Stroke(width = (0.07f * depth.ppm).coerceAtLeast(1.5f))
        )
    }

    private fun buildBand(n: Int, frontDepth: Float, backDepth: Float, wobble: Float) {
        band.reset()
        for (i in 0 until n) {
            val d = frontDepth + wobble * wave(terrainW[i] * 0.22f)
            val x = depth.atX(terrainX[i], d)
            val y = depth.atY(terrainY[i], d)
            if (i == 0) band.moveTo(x, y) else band.lineTo(x, y)
        }
        for (i in n - 1 downTo 0) {
            val d = backDepth - wobble * wave(terrainW[i] * 0.19f + 53f)
            band.lineTo(depth.atX(terrainX[i], d), depth.atY(terrainY[i], d))
        }
        band.close()
    }

    private fun buildEdge(n: Int, d: Float) {
        edge.reset()
        edge.moveTo(depth.atX(terrainX[0], d), depth.atY(terrainY[0], d))
        for (i in 1 until n) {
            edge.lineTo(depth.atX(terrainX[i], d), depth.atY(terrainY[i], d))
        }
    }

    private fun DrawScope.drawRut(n: Int, rutDepth: Float, seed: Float, color: Color) {
        edge.reset()
        for (i in 0 until n) {
            val d = rutDepth + 0.08f * wave(terrainW[i] * 0.28f + seed)
            val x = depth.atX(terrainX[i], d)
            val y = depth.atY(terrainY[i], d)
            if (i == 0) edge.moveTo(x, y) else edge.lineTo(x, y)
        }
        drawPath(edge, color.copy(alpha = 0.5f), style = Stroke(width = (0.25f * depth.ppm).coerceAtLeast(2f), cap = StrokeCap.Round))
    }

    private fun DrawScope.drawJunctionPreview(engine: GameEngine) {
        if (!engine.inJunctionZone && engine.localX < engine.segment.length - 28f) return
        val start = engine.segment.endWorldX - 10f
        val choices = engine.segment.choices
        if (choices.isEmpty()) return

        choices.forEachIndexed { idx, choice ->
            val yOff = when (idx) {
                0 -> 1.6f
                1 -> 0f
                else -> -1.4f
            }
            val col = when (choice.style) {
                BranchStyle.SAFE_RURAL -> Color(0xFF6B8F5A)
                BranchStyle.INDUSTRIAL -> Color(0xFF6A7A8A)
                BranchStyle.SHORTCUT_RISK -> Color(0xFFB85C38)
            }.copy(alpha = 0.55f)

            edge.reset()
            var first = true
            var t = 0f
            while (t <= 18f) {
                val wx = start + t
                val base = engine.segment.heightAtLocal(engine.segment.length)
                val wy = base + yOff * MathX.smoothstep(0f, 10f, t) + MathX.approxSin(t * 0.2f) * 0.1f
                val fx = depth.frontX(wx)
                val fy = depth.frontY(wy)
                val px = depth.atX(fx, PLAY_DEPTH_PROXY)
                val py = depth.atY(fy, PLAY_DEPTH_PROXY)
                if (first) {
                    edge.moveTo(px, py)
                    first = false
                } else edge.lineTo(px, py)
                t += 1.2f
            }
            drawPath(edge, col, style = Stroke(width = (0.45f * depth.ppm).coerceAtLeast(3f), cap = StrokeCap.Round))
        }
    }

    private fun DrawScope.drawBuildings(engine: GameEngine) {
        val seg = engine.segment
        for (b in seg.buildings) {
            val wx = seg.worldOrigin + b.localX
            if (wx < engine.camera.x - 20f || wx > engine.camera.x + 35f) continue
            val ground = seg.heightAtWorld(wx)
            val fx = depth.frontX(wx)
            val fy = depth.frontY(ground)
            val near = GameConfig.CAR_NEAR_DEPTH + 0.8f
            val px = depth.atX(fx, near)
            val py = depth.atY(fy, near)
            val scale = depth.ppm * (1f - depth.perspectiveT(near) * 0.25f)

            val (bw, bh, col) = when (b.type) {
                BuildingType.HOUSE -> Triple(2.4f, 2.1f, Color(0xFF8A6A4A))
                BuildingType.GARAGE -> Triple(3.0f, 1.7f, Color(0xFF6A6A5A))
                BuildingType.GAS_STATION -> Triple(3.4f, 2.0f, Color(0xFF7A5A4A))
                BuildingType.AUTO_SHOP -> Triple(3.6f, 2.2f, Color(0xFF5A6A7A))
            }
            val w = bw * scale
            val h = bh * scale
            val bx = px - w * 0.15f
            drawOval(Color.Black.copy(alpha = 0.25f), topLeft = Offset(bx - w * 0.1f, py - 2f), size = Size(w * 1.1f, 8f))
            drawRect(col, topLeft = Offset(bx, py - h), size = Size(w, h))
            roof.rewind()
            roof.moveTo(bx - 4f, py - h)
            roof.lineTo(bx + w * 0.5f, py - h - 0.55f * scale)
            roof.lineTo(bx + w + 4f, py - h)
            roof.close()
            drawPath(roof, Color(col.red * 0.7f, col.green * 0.7f, col.blue * 0.7f))
            drawRect(Color(0xFF2A2218), topLeft = Offset(bx + w * 0.4f, py - h * 0.55f), size = Size(w * 0.2f, h * 0.55f))
        }
    }

    private fun DrawScope.drawCar(engine: GameEngine) {
        val car = engine.car
        val bodyX = depth.frontX(car.x)
        val bodyY = depth.frontY(car.y)
        val braking = engine.brakeInput > 0.25f && car.speed >= 0f
        with(carArtist) {
            drawSedan(
                car = car,
                bodyX = bodyX,
                bodyY = bodyY,
                angle = car.pitch,
                ppm = depth.ppm,
                proj = depth,
                wheelSpinDeg = car.wheelSpinDeg,
                braking = braking,
                layers = assets.sedan
            )
        }
    }

    private fun wave(x: Float): Float = MathX.approxSin(x) * 0.5f + 0.5f

    companion object {
        private const val MAX_POINTS = 260
        private const val PLAY_DEPTH_PROXY = 1.4f
    }
}
