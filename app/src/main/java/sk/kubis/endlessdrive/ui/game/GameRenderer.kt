package sk.kubis.endlessdrive.ui.game

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import sk.kubis.endlessdrive.core.GameConfig
import sk.kubis.endlessdrive.core.MathX
import sk.kubis.endlessdrive.domain.model.BiomeType
import sk.kubis.endlessdrive.domain.model.BranchStyle
import sk.kubis.endlessdrive.game.DepthProjection
import sk.kubis.endlessdrive.game.GameEngine
import sk.kubis.endlessdrive.game.world.RoadSegment
import kotlin.math.abs

/**
 * Bočný 2.5D renderer: procedurálna obloha s denným cyklom, parallax kopce,
 * kulisy pri ceste, HillRush terén, budovy a sedan s efektmi.
 */
class GameRenderer(private val assets: GameAssets) {
    private val depth = DepthProjection()
    private val carArtist = CarArtist()
    private val sky = SkyPainter()
    private val scenery = SceneryPainter()
    private val buildings = BuildingPainter()
    private val band = Path()
    private val edge = Path()
    private val soil = Path()

    private val terrainX = FloatArray(MAX_POINTS)
    private val terrainY = FloatArray(MAX_POINTS)
    private val terrainW = FloatArray(MAX_POINTS)
    /** Terén pod cestou – na moste klesá do rokliny. */
    private val groundY = FloatArray(MAX_POINTS)
    private var terrainCount = 0

    fun DrawScope.draw(engine: GameEngine) {
        engine.setScreenHeight(size.height)
        val cam = engine.camera
        val halfW = size.width / 2f
        val halfH = size.height / 2f
        depth.begin(cam.x, cam.y, cam.ppm, halfW, halfH, cam.pitch)

        val day = engine.daylight
        val biome = engine.segment.biome
        val horizonY = size.height * 0.60f

        with(sky) {
            drawSky(engine.timeOfDay, biome, cam.x, horizonY)
            drawDistantHills(cam.x, horizonY, day, biome)
        }
        drawBackdropBand(biome, cam.x, horizonY, day)

        collectTerrain(engine, cam.ppm, size.width)
        val visibleFrom = cam.x - size.width / (2f * cam.ppm) - 8f
        val visibleTo = cam.x + size.width / (2f * cam.ppm) + 12f
        // Kulisy stoja na teréne, nie na mostovke.
        val heightAt: (Float) -> Float = { wx -> groundFor(engine, wx) }

        // Poradie je dôležité: najprv zem, potom kulisy (stoja na nej), až potom cesta.
        drawGround(engine.segment, day)
        with(scenery) { drawBackProps(visibleFrom, visibleTo, biome, day, depth, heightAt) }
        drawBridgeStructure(engine, day)
        drawRoadSurface(engine.segment, day)
        drawBridgeRailing(engine, day)
        drawJunctionPreview(engine)
        drawBuildings(engine, day)
        drawCarShadow(engine)
        drawCar(engine)
        drawCarEffects(engine, day)
        with(scenery) { drawFrontProps(visibleFrom, visibleTo, biome, day, depth, heightAt) }
        drawNight(engine, day)
    }

    /** Vzdialená silueta z bitmapy – len úzky pás nad horizontom. */
    private fun DrawScope.drawBackdropBand(biome: BiomeType, camX: Float, horizonY: Float, day: Float) {
        val bg = assets.backgroundFor(biome)
        val srcTop = (bg.height * 0.50f).toInt()
        val srcH = bg.height - srcTop
        if (srcH <= 1) return
        val bandH = horizonY * 0.42f
        val drawW = (size.width * 1.35f)
        val top = (horizonY - bandH).toInt()
        val tint = lerp(Color(0xFF1B2438), Color(0xFFBFCBD6), day.coerceIn(0f, 1f))
        val shift = ((camX * 3.2f) % drawW + drawW) % drawW

        for (i in -1..1) {
            val x = (-shift + i * drawW)
            if (x > size.width || x + drawW < 0f) continue
            drawImage(
                image = bg,
                srcOffset = IntOffset(0, srcTop),
                srcSize = IntSize(bg.width, srcH),
                dstOffset = IntOffset(x.toInt(), top),
                dstSize = IntSize(drawW.toInt().coerceAtLeast(1), bandH.toInt().coerceAtLeast(1)),
                alpha = 0.55f,
                colorFilter = ColorFilter.tint(tint, androidx.compose.ui.graphics.BlendMode.Modulate)
            )
        }
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
            groundY[n] = depth.frontY(groundFor(engine, wx))
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

    private fun groundFor(engine: GameEngine, worldX: Float): Float {
        val seg = engine.segment
        if (worldX < seg.worldOrigin || worldX > seg.endWorldX) return heightFor(engine, worldX)
        return seg.groundAtWorld(worldX)
    }

    private fun grassColor(segment: RoadSegment, day: Float) = shade(
        when (segment.biome) {
            BiomeType.RURAL -> Color(0xFF7A8F5A)
            BiomeType.INDUSTRIAL -> Color(0xFF6A7460)
            BiomeType.WASTELAND -> Color(0xFF8A7A4F)
        },
        day
    )

    /** Pôda a lúka až za cestu – podklad, na ktorom stoja kulisy. */
    private fun DrawScope.drawGround(segment: RoadSegment, day: Float) {
        val n = terrainCount
        if (n < 2) return
        val grass = grassColor(segment, day)
        val soilColor = shade(Color(0xFF6B5340), day)

        // 1) Nepriehľadná pôda vpredu – zakryje pozadie. Pod mostom klesá do rokliny.
        soil.reset()
        for (i in 0 until n) {
            if (i == 0) soil.moveTo(terrainX[i], groundY[i]) else soil.lineTo(terrainX[i], groundY[i])
        }
        soil.lineTo(terrainX[n - 1], size.height + 4f)
        soil.lineTo(terrainX[0], size.height + 4f)
        soil.close()
        drawPath(soil, soilColor)
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.30f)),
                startY = size.height * 0.72f,
                endY = size.height
            ),
            topLeft = Offset(0f, size.height * 0.72f),
            size = Size(size.width, size.height * 0.28f)
        )

        // 2) Lúka siaha až za kulisy, inak by stromy „leteli“ v prázdne.
        buildBand(n, 0f, SCENERY_BACK_DEPTH + 0.5f, groundY)
        drawPath(band, lerp(grass, shade(Color(0xFFAFC6D6), day), 0.26f))
        buildBand(n, 0f, GameConfig.ROAD_DEPTH + 0.7f, groundY)
        drawPath(band, grass)
    }

    /** Mostovka zospodu a piliere do rokliny – kreslí sa pod vozovku. */
    private fun DrawScope.drawBridgeStructure(engine: GameEngine, day: Float) {
        val n = terrainCount
        if (n < 2) return
        val seg = engine.segment
        val d = GameConfig.ROAD_DEPTH * 0.55f
        val thickness = 0.34f * depth.ppm
        val deckColor = shade(Color(0xFF5A5044), day)
        val pillarColor = shade(Color(0xFF6E6455), day)

        var i = 0
        while (i < n) {
            if (seg.bridgeClearanceAtWorld(terrainW[i]) < MIN_CLEARANCE) {
                i++
                continue
            }
            val start = i
            while (i < n && seg.bridgeClearanceAtWorld(terrainW[i]) >= MIN_CLEARANCE) i++
            val end = i - 1
            if (end - start < 2) continue

            // Piliere.
            var p = start
            while (p <= end) {
                val x = depth.atX(terrainX[p], d)
                val top = depth.atY(terrainY[p], d) + thickness
                val bottom = depth.atY(groundY[p], d)
                if (bottom > top) {
                    drawLine(
                        pillarColor,
                        Offset(x, top),
                        Offset(x, bottom),
                        strokeWidth = (0.28f * depth.ppm).coerceAtLeast(2f)
                    )
                    drawLine(
                        pillarColor.copy(alpha = 0.7f),
                        Offset(x, (top + bottom) * 0.5f),
                        Offset(x, bottom),
                        strokeWidth = (0.10f * depth.ppm).coerceAtLeast(1f)
                    )
                }
                p += 8
            }
            // Doska mostovky.
            band.reset()
            for (k in start..end) {
                val x = depth.atX(terrainX[k], d)
                val y = depth.atY(terrainY[k], d)
                if (k == start) band.moveTo(x, y) else band.lineTo(x, y)
            }
            for (k in end downTo start) {
                band.lineTo(depth.atX(terrainX[k], d), depth.atY(terrainY[k], d) + thickness)
            }
            band.close()
            drawPath(band, deckColor)
        }
    }

    /** Zábradlie na bližšej strane mosta – kreslí sa až nad vozovku. */
    private fun DrawScope.drawBridgeRailing(engine: GameEngine, day: Float) {
        val n = terrainCount
        if (n < 2) return
        val seg = engine.segment
        val d = GameConfig.VERGE_DEPTH * 0.35f
        val railH = 0.95f * depth.ppm
        val col = shade(Color(0xFF8A8578), day)

        edge.reset()
        var drawing = false
        for (i in 0 until n) {
            if (seg.bridgeClearanceAtWorld(terrainW[i]) < MIN_CLEARANCE) {
                drawing = false
                continue
            }
            val x = depth.atX(terrainX[i], d)
            val y = depth.atY(terrainY[i], d) - railH
            if (!drawing) {
                edge.moveTo(x, y)
                drawing = true
            } else {
                edge.lineTo(x, y)
            }
            if (i % 6 == 0) {
                drawLine(
                    col,
                    Offset(x, y),
                    Offset(x, depth.atY(terrainY[i], d)),
                    strokeWidth = (0.09f * depth.ppm).coerceAtLeast(1.5f)
                )
            }
        }
        drawPath(edge, col, style = Stroke(width = (0.10f * depth.ppm).coerceAtLeast(1.5f)))
    }

    private fun DrawScope.drawRoadSurface(segment: RoadSegment, day: Float) {
        val n = terrainCount
        if (n < 2) return
        val grass = grassColor(segment, day)
        val asphalt = segment.biome == BiomeType.INDUSTRIAL
        val road = shade(
            when (segment.biome) {
                BiomeType.RURAL -> Color(0xFF52483C)
                BiomeType.INDUSTRIAL -> Color(0xFF454545)
                BiomeType.WASTELAND -> Color(0xFF5E5040)
            },
            day
        )

        buildBand(n, 0f, 0.14f, terrainY)
        drawPath(band, lerp(grass, Color.White, 0.10f))

        // 3) Krajnica + cesta.
        buildBand(n, GameConfig.VERGE_DEPTH * 0.6f, GameConfig.ROAD_DEPTH + 0.05f, terrainY)
        drawPath(band, shade(Color(0xFF7C7060), day).copy(alpha = 0.75f))
        buildBand(n, GameConfig.VERGE_DEPTH, GameConfig.ROAD_DEPTH - 0.15f, terrainY)
        drawPath(band, road)
        buildBand(n, GameConfig.VERGE_DEPTH + 0.25f, GameConfig.ROAD_DEPTH - 0.45f, terrainY)
        drawPath(band, Color(road.red * 0.92f, road.green * 0.92f, road.blue * 0.92f))

        if (asphalt) {
            drawCenterLine(n, day)
        } else {
            drawRut(n, GameConfig.RUT_NEAR_DEPTH, 1.1f, shade(Color(0xFF2A2218), day))
            drawRut(n, GameConfig.RUT_FAR_DEPTH, 7.3f, shade(Color(0xFF2A2218), day))
        }
        drawPotholes(n, day, segment)

        buildEdge(n, GameConfig.ROAD_DEPTH)
        drawPath(
            edge,
            shade(Color(0xFF6B7A4A), day).copy(alpha = 0.55f),
            style = Stroke(width = (0.07f * depth.ppm).coerceAtLeast(1.5f))
        )
    }

    /** Prerušovaná stredová čiara na asfalte. */
    private fun DrawScope.drawCenterLine(n: Int, day: Float) {
        val d = (GameConfig.VERGE_DEPTH + GameConfig.ROAD_DEPTH) * 0.5f
        val col = shade(Color(0xFFD8D2B4), day).copy(alpha = 0.65f)
        var i = 0
        while (i < n - 1) {
            val onDash = ((terrainW[i] / 3f).toInt() % 2) == 0
            if (onDash) {
                drawLine(
                    col,
                    Offset(depth.atX(terrainX[i], d), depth.atY(terrainY[i], d)),
                    Offset(depth.atX(terrainX[i + 1], d), depth.atY(terrainY[i + 1], d)),
                    strokeWidth = (0.12f * depth.ppm).coerceAtLeast(2f)
                )
            }
            i++
        }
    }

    /** Výtlky – deterministické tmavé škvrny; na rozbitom úseku je ich plno. */
    private fun DrawScope.drawPotholes(n: Int, day: Float, segment: RoadSegment) {
        val col = shade(Color(0xFF241D16), day).copy(alpha = 0.45f)
        for (i in 0 until n step 3) {
            val h = MathX.hash01(terrainW[i].toInt(), 6421)
            val limit = 0.06f + segment.bumpinessAtLocal(terrainW[i] - segment.worldOrigin) * 0.30f
            if (h > limit) continue
            val d = GameConfig.VERGE_DEPTH + 0.4f + MathX.hash01(terrainW[i].toInt(), 77) * 1.6f
            val x = depth.atX(terrainX[i], d)
            val y = depth.atY(terrainY[i], d)
            val r = depth.ppm * (0.12f + h * 2.2f)
            drawOval(col, topLeft = Offset(x - r, y - r * 0.45f), size = Size(r * 2f, r * 0.9f))
        }
    }

    /** Pás medzi dvoma hĺbkami; [ys] určuje, či ide o vozovku alebo terén pod ňou. */
    private fun buildBand(n: Int, frontDepth: Float, backDepth: Float, ys: FloatArray) {
        band.reset()
        for (i in 0 until n) {
            band.lineToOrMove(i == 0, depth.atX(terrainX[i], frontDepth), depth.atY(ys[i], frontDepth))
        }
        for (i in n - 1 downTo 0) {
            band.lineTo(depth.atX(terrainX[i], backDepth), depth.atY(ys[i], backDepth))
        }
        band.close()
    }

    private fun Path.lineToOrMove(move: Boolean, x: Float, y: Float) {
        if (move) moveTo(x, y) else lineTo(x, y)
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
        drawPath(
            edge,
            color.copy(alpha = 0.5f),
            style = Stroke(width = (0.25f * depth.ppm).coerceAtLeast(2f), cap = StrokeCap.Round)
        )
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

    private fun DrawScope.drawBuildings(engine: GameEngine, day: Float) {
        val seg = engine.segment
        val near = engine.buildingNear()
        for (b in seg.buildings) {
            val wx = seg.worldOrigin + b.localX
            if (wx < engine.camera.x - 25f || wx > engine.camera.x + 40f) continue
            val ground = seg.heightAtWorld(wx)
            val d = GameConfig.ROAD_DEPTH + 1.5f
            val px = depth.atX(depth.frontX(wx), d)
            val py = depth.atY(depth.frontY(ground), d)
            val s = depth.ppm * (1f - depth.perspectiveT(d))
            with(buildings) { drawBuilding(b, px, py, s, day, near === b) }
        }
    }

    private fun DrawScope.drawCarShadow(engine: GameEngine) {
        val car = engine.car
        val d = GameConfig.CAR_NEAR_DEPTH + 0.2f
        val x = depth.atX(depth.frontX(car.x), d)
        val groundY = engine.segment.heightAtWorld(car.x)
        val y = depth.atY(depth.frontY(groundY), d)
        val w = 2.9f * depth.ppm
        drawOval(
            Color.Black.copy(alpha = 0.30f),
            topLeft = Offset(x - w * 0.5f, y - depth.ppm * 0.16f),
            size = Size(w, depth.ppm * 0.34f)
        )
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

    /** Prach spod kolies a dym z výfuku – deterministické z času, bez časticového systému. */
    private fun DrawScope.drawCarEffects(engine: GameEngine, day: Float) {
        val car = engine.car
        val ppm = depth.ppm
        val d = GameConfig.CAR_NEAR_DEPTH
        val groundY = engine.segment.heightAtWorld(car.x)
        val baseX = depth.atX(depth.frontX(car.x), d)
        val baseY = depth.atY(depth.frontY(groundY), d)
        val t = engine.elapsed

        val speedRatio = (abs(car.speed) / GameConfig.MAX_SPEED).coerceIn(0f, 1f)
        if (speedRatio > 0.12f) {
            val dust = shade(Color(0xFFBFAE8E), day)
            for (i in 0 until 7) {
                val phase = (t * 1.6f + i * 0.37f) % 1f
                val h = MathX.hash01(i, (t * 2f).toInt())
                val x = baseX - ppm * (1.5f + phase * 3.4f + h * 0.5f)
                val y = baseY - ppm * (0.05f + phase * 0.55f)
                val r = ppm * (0.10f + phase * 0.42f)
                drawCircle(
                    dust.copy(alpha = (0.30f * speedRatio) * (1f - phase)),
                    r,
                    Offset(x, y)
                )
            }
        }
        if (car.engineRunning) {
            val smoke = shade(Color(0xFF9AA0A6), day)
            for (i in 0 until 4) {
                val phase = (t * 0.7f + i * 0.25f) % 1f
                val x = baseX - ppm * (2.5f + phase * 1.9f)
                val y = baseY - ppm * (0.35f + phase * 1.05f)
                val r = ppm * (0.08f + phase * 0.26f)
                drawCircle(smoke.copy(alpha = 0.20f * (1f - phase)), r, Offset(x, y))
            }
        }
    }

    /** Nočné stmavenie scény + kužeľ svetlometov. */
    private fun DrawScope.drawNight(engine: GameEngine, day: Float) {
        val night = (1f - day).coerceIn(0f, 1f)
        if (night > 0.02f) {
            drawRect(
                Color(0xFF0B1226).copy(alpha = night * 0.45f),
                size = Size(size.width, size.height)
            )
        }
        if (!engine.headlightsOn) return

        val car = engine.car
        with(carArtist) {
            drawHeadlightBeam(
                bodyX = depth.frontX(car.x),
                bodyY = depth.frontY(car.y),
                angle = car.pitch,
                ppm = depth.ppm,
                proj = depth,
                layers = assets.sedan,
                strength = 0.3f + night * 0.7f,
                braking = engine.brakeInput > 0.25f && car.speed >= 0f
            )
        }
    }

    private fun wave(x: Float): Float = MathX.approxSin(x) * 0.5f + 0.5f

    companion object {
        private const val MAX_POINTS = 260
        private const val PLAY_DEPTH_PROXY = 1.4f
        /** Od akej výšky nad terénom považujeme úsek za most. */
        private const val MIN_CLEARANCE = 0.35f

        /** Najhlbšia vrstva kulís – lúka musí siahať aspoň sem. */
        const val SCENERY_BACK_DEPTH = GameConfig.ROAD_DEPTH + 1.7f
    }
}
