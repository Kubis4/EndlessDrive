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
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import sk.kubis.endlessdrive.core.GameConfig
import sk.kubis.endlessdrive.core.MathX
import sk.kubis.endlessdrive.domain.model.BiomeType
import sk.kubis.endlessdrive.domain.model.BranchStyle
import sk.kubis.endlessdrive.domain.model.ComponentSlot
import sk.kubis.endlessdrive.domain.model.RoadPaving
import sk.kubis.endlessdrive.domain.model.RoadSurface
import sk.kubis.endlessdrive.domain.model.SedanSpec
import sk.kubis.endlessdrive.game.DepthProjection
import sk.kubis.endlessdrive.game.GameEngine
import sk.kubis.endlessdrive.game.event.RoadEvent
import sk.kubis.endlessdrive.game.world.RoadSegment
import kotlin.math.abs
import kotlin.math.hypot

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

    // Stopy po preklze: kruhový buffer svetových pozícií, ktoré časom vyblednú.
    private val skidX = FloatArray(SKID_MAX)
    private val skidPower = FloatArray(SKID_MAX)
    private val skidStamp = FloatArray(SKID_MAX)
    private var skidCount = 0
    private var skidHead = 0
    private var lastSkidX = Float.NaN
    /** Renderer prežije reštart jazdy – stopy z predošlej sa musia zahodiť. */
    private var lastEngine: GameEngine? = null

    /** Výška mostovky nad terénom pre každú vzorku (0 = žiadny most). */
    private val bridgeClearance = FloatArray(MAX_POINTS)

    fun DrawScope.draw(engine: GameEngine) {
        if (lastEngine !== engine) {
            lastEngine = engine
            resetTrails()
        }
        engine.setScreenHeight(size.height)
        val cam = engine.camera
        // Auto nesedí v strede – vľavo je menej sveta, vpravo viac cesty dopredu.
        val halfW = size.width * GameConfig.CAR_SCREEN_X
        val halfH = size.height / 2f
        // Otras sa pridá až do projekcie – svet sa nehýbe, hýbe sa kamera.
        depth.begin(cam.x + cam.shakeX, cam.y + cam.shakeY, cam.ppm, halfW, halfH, cam.pitch)

        val day = engine.daylight
        val biome = engine.segment.biome
        val horizonY = size.height * 0.70f

        with(sky) {
            drawSky(engine.timeOfDay, biome, cam.x, horizonY)
            drawDistantHills(cam.x, horizonY, day, biome)
        }
        drawBackdropBand(biome, cam.x, horizonY, day)
        with(sky) {
            drawTreeline(cam.x, horizonY, day, biome)
            drawHaze(horizonY, day, biome)
        }

        collectTerrain(engine, cam.ppm, size.width)
        // Auto nie je v strede, takže doprava treba dohliadnuť ďalej než doľava.
        val worldW = size.width / cam.ppm
        val visibleFrom = cam.x - worldW * GameConfig.CAR_SCREEN_X - 8f
        val visibleTo = cam.x + worldW * (1f - GameConfig.CAR_SCREEN_X) + 12f
        // Kulisy stoja na teréne, nie na mostovke.
        val heightAt: (Float) -> Float = { wx -> groundFor(engine, wx) }

        // Poradie je dôležité: najprv zem, potom kulisy (stoja na nej), až potom cesta.
        drawGround(engine.segment, day)
        with(scenery) { drawBackProps(visibleFrom, visibleTo, biome, day, depth, heightAt) }
        drawBridgeStructure(engine, day)
        drawRoadSurface(engine.segment, day)
        drawSurfacePatches(engine.segment, day)
        drawBridgeRailing(engine, day)
        recordSkid(engine)
        drawSkidMarks(engine, day)
        drawJunctionPreview(engine)
        drawBuildings(engine, day)
        drawCarShadow(engine)
        drawCar(engine)
        drawCarEffects(engine, day)
        with(scenery) { drawFrontProps(visibleFrom, visibleTo, biome, day, depth, heightAt) }
        drawNight(engine, day)
        drawWeather(engine, day)
        drawVignette(engine, day)
    }

    /**
     * Počasie cez celú scénu: dážď šikmými šmuhami, sneženie pomalými vločkami.
     * Kreslí sa v obrazovkových súradniciach – lacné a nezávislé od projekcie.
     */
    private fun DrawScope.drawWeather(engine: GameEngine, day: Float) {
        val raining = engine.activeEvents.any { it.event == RoadEvent.RAIN }
        val snowing = engine.isWinter
        if (!raining && !snowing) return
        val t = engine.elapsed
        val w = size.width
        val h = size.height
        // Počasie je počasie – prší a sneží rovnako, či stojíme alebo ideme.
        // Naviazať to na rýchlosť vyzeralo pri prehrabávaní sa na mieste zle.

        if (raining) {
            // Mokrá scéna: studený závoj cez celý obraz.
            drawRect(
                Color(0xFF2A3A46).copy(alpha = 0.16f),
                size = Size(w, h)
            )
            val col = shade(Color(0xFFBFD4E0), day).copy(alpha = 0.5f)
            for (i in 0 until RAIN_DROPS) {
                val seedX = MathX.hash01(i, 17)
                val seedY = MathX.hash01(i, 41)
                val speed = 900f + seedY * 700f
                val x = ((seedX * w) - (t * RAIN_DRIFT) % w + w) % w
                val y = ((seedY * h) + (t * speed) % h) % h
                val len = h * (0.035f + seedY * 0.03f)
                drawLine(
                    col,
                    Offset(x, y),
                    Offset(x - len * RAIN_SLANT, y + len),
                    strokeWidth = 1.6f
                )
            }
        }

        if (snowing) {
            val col = Color(0xFFEFF6FA).copy(alpha = 0.85f)
            for (i in 0 until SNOW_FLAKES) {
                val seedX = MathX.hash01(i, 29)
                val seedY = MathX.hash01(i, 53)
                val fall = 60f + seedY * 90f
                // Vločky sa hompáľajú – bez toho vyzerá sneh ako dážď.
                val sway = MathX.approxSin(t * (0.7f + seedX) + i.toFloat()) * w * 0.02f
                val x = ((seedX * w) + sway - (t * SNOW_DRIFT) % w + w) % w
                val y = ((seedY * h) + (t * fall) % h) % h
                drawCircle(col, 1.4f + seedY * 2.4f, Offset(x, y))
            }
        }
    }

    /** Jemné stmavenie rohov – scéna pôsobí ako záber, nie ako plocha. */
    private fun DrawScope.drawVignette(engine: GameEngine, day: Float) {
        val speedT = (kotlin.math.abs(engine.car.speed) / GameConfig.MAX_SPEED).coerceIn(0f, 1f)
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color.Transparent,
                    Color.Transparent,
                    Color(0xFF060810).copy(alpha = 0.28f + 0.22f * (1f - day) + 0.12f * speedT)
                ),
                center = Offset(size.width * 0.5f, size.height * 0.52f),
                radius = size.width * 0.72f
            ),
            size = Size(size.width, size.height)
        )
    }

    /** Vzdialená silueta z bitmapy – len úzky pás nad horizontom. */
    private fun DrawScope.drawBackdropBand(biome: BiomeType, camX: Float, horizonY: Float, day: Float) {
        val bg = assets.backgroundFor(biome)
        val srcTop = (bg.height * 0.50f).toInt()
        val srcH = bg.height - srcTop
        if (srcH <= 1) return
        val bandH = horizonY * 0.50f
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
        // Rovnaký rozsah ako pri kulisách – vpravo je viac sveta než vľavo.
        val worldW = screenWidth / ppm
        val from = engine.camera.x - worldW * GameConfig.CAR_SCREEN_X - 6f
        val to = engine.camera.x + worldW * (1f - GameConfig.CAR_SCREEN_X) + 6f
        val step = 1.1f
        var n = 0
        var wx = from
        while (wx <= to && n < MAX_POINTS) {
            val road = heightFor(engine, wx)
            val ground = groundFor(engine, wx)
            terrainW[n] = wx
            terrainX[n] = depth.frontX(wx)
            terrainY[n] = depth.frontY(road)
            groundY[n] = depth.frontY(ground)
            // Výšku mostovky máme zadarmo z už vypočítaných výšok – opakované
            // volania bridgeClearanceAtWorld() by znamenali tisíce vzoriek šumu.
            bridgeClearance[n] = (road - ground).coerceAtLeast(0f)
            n++
            wx += step
        }
        terrainCount = n
    }

    // Profil pokračuje aj za hranicami segmentu – žiadne zarovnanie ani vlnka,
    // inak by bolo vidieť, kde sa trať „začína a končí“.
    private fun heightFor(engine: GameEngine, worldX: Float): Float =
        engine.segment.heightAtWorld(worldX)

    private fun groundFor(engine: GameEngine, worldX: Float): Float =
        engine.segment.groundAtWorld(worldX)

    private fun grassColor(segment: RoadSegment, day: Float) = shade(
        if (segment.paving.winter) Color(0xFFDCE7EE) else when (segment.biome) {
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
            if (bridgeClearance[i] < MIN_CLEARANCE) {
                i++
                continue
            }
            val start = i
            while (i < n && bridgeClearance[i] >= MIN_CLEARANCE) i++
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
            if (bridgeClearance[i] < MIN_CLEARANCE) {
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
        // Vzhľad vozovky určuje vetva, nie biom – každá odbočka je iná.
        val road = shade(
            when (segment.paving) {
                RoadPaving.ASPHALT -> Color(0xFF3E3E42)
                RoadPaving.CRACKED -> Color(0xFF4A4844)
                RoadPaving.CONCRETE -> Color(0xFF6E6C66)
                RoadPaving.DIRT -> Color(0xFF6A5340)
                RoadPaving.GRAVEL_ROAD -> Color(0xFF6E675C)
                RoadPaving.SAND_TRACK -> Color(0xFFB49A6A)
                RoadPaving.SNOW -> Color(0xFFE6EDF2)
                RoadPaving.PACKED_SNOW -> Color(0xFFCBD7DE)
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

        if (segment.paving.rutted) {
            // Vyjazdené koľaje – hlina, štrk aj piesok sa jazdia „po stopách“.
            val rut = shade(
                when (segment.paving) {
                    RoadPaving.SAND_TRACK -> Color(0xFF8E7644)
                    RoadPaving.SNOW, RoadPaving.PACKED_SNOW -> Color(0xFF9FB3BE)
                    RoadPaving.GRAVEL_ROAD -> Color(0xFF4E4941)
                    else -> Color(0xFF2A2218)
                },
                day
            )
            drawRut(n, GameConfig.RUT_NEAR_DEPTH, 1.1f, rut)
            drawRut(n, GameConfig.RUT_FAR_DEPTH, 7.3f, rut)
        }
        drawPavementDetail(n, day, segment)
        drawPotholes(n, day, segment)

        buildEdge(n, GameConfig.ROAD_DEPTH)
        drawPath(
            edge,
            shade(Color(0xFF6B7A4A), day).copy(alpha = 0.55f),
            style = Stroke(width = (0.07f * depth.ppm).coerceAtLeast(1.5f))
        )
    }

    /** Farby naplavenín – každá prekážka musí byť na prvý pohľad iná. */
    private fun surfaceColors(surface: RoadSurface): Pair<Color, Color> = when (surface) {
        RoadSurface.MUD -> Color(0xFF4A3524) to Color(0xFF33241A)
        RoadSurface.SAND -> Color(0xFFC9AE72) to Color(0xFFAD9159)
        RoadSurface.WATER -> Color(0xFF3E6B7A) to Color(0xFF9FD2DE)
        RoadSurface.GRAVEL -> Color(0xFF8A8378) to Color(0xFF625C53)
        RoadSurface.ICE -> Color(0xFFA8C6D6) to Color(0xFFE8F4FA)
        RoadSurface.SLUSH -> Color(0xFF8FA0A8) to Color(0xFFC2CFD6)
        RoadSurface.ASPHALT -> Color(0xFF52483C) to Color(0xFF52483C)
    }

    /**
     * Bahno, piesok, voda a štrk priamo na vozovke. Kreslia sa cez celý pás
     * cesty, takže ich vidno z diaľky a dá sa na ne pripraviť.
     */
    private fun DrawScope.drawSurfacePatches(segment: RoadSegment, day: Float) {
        val n = terrainCount
        if (n < 2 || segment.patches.isEmpty()) return
        val from = terrainW[0] - segment.worldOrigin
        val to = terrainW[n - 1] - segment.worldOrigin

        for (patch in segment.patches) {
            if (patch.end < from || patch.start > to) continue
            val (base, accent) = surfaceColors(patch.surface)
            // Index prvého a posledného vzorku, ktorý do naplaveniny spadá.
            var i0 = -1
            var i1 = -1
            for (i in 0 until n) {
                val local = terrainW[i] - segment.worldOrigin
                if (local >= patch.start && local <= patch.end) {
                    if (i0 < 0) i0 = i
                    i1 = i
                }
            }
            if (i0 < 0 || i1 <= i0) continue

            buildBandRange(i0, i1, GameConfig.VERGE_DEPTH, GameConfig.ROAD_DEPTH - 0.15f)
            drawPath(band, shade(base, day).copy(alpha = if (patch.surface == RoadSurface.WATER) 0.72f else 0.92f))

            // Textúra – vlnky na vode, zrno v piesku, kamienky v štrku, hrudy v bahne.
            val col = shade(accent, day)
            var i = i0
            while (i < i1) {
                val local = terrainW[i] - segment.worldOrigin
                val h = MathX.hash01(terrainW[i].toInt(), patch.surface.ordinal * 977)
                val d = GameConfig.VERGE_DEPTH + 0.3f + h * (GameConfig.ROAD_DEPTH - GameConfig.VERGE_DEPTH - 0.7f)
                val x = depth.atX(terrainX[i], d)
                val y = depth.atY(terrainY[i], d)
                when (patch.surface) {
                    // Ľad je hladký – namiesto textúry mu dáme lesklý odblesk.
                    RoadSurface.ICE -> drawLine(
                        col.copy(alpha = 0.5f + 0.3f * h),
                        Offset(x - depth.ppm * (0.4f + h * 0.5f), y),
                        Offset(x + depth.ppm * (0.4f + h * 0.5f), y),
                        strokeWidth = (0.04f * depth.ppm).coerceAtLeast(1f)
                    )
                    RoadSurface.WATER -> drawLine(
                        col.copy(alpha = 0.35f + 0.25f * h),
                        Offset(x - depth.ppm * (0.25f + h * 0.3f), y),
                        Offset(x + depth.ppm * (0.25f + h * 0.3f), y),
                        strokeWidth = (0.05f * depth.ppm).coerceAtLeast(1f)
                    )
                    RoadSurface.GRAVEL -> drawCircle(
                        col.copy(alpha = 0.7f), depth.ppm * (0.03f + h * 0.04f), Offset(x, y)
                    )
                    RoadSurface.SAND -> drawOval(
                        col.copy(alpha = 0.35f),
                        topLeft = Offset(x - depth.ppm * 0.35f, y - depth.ppm * 0.05f),
                        size = Size(depth.ppm * 0.7f, depth.ppm * 0.1f)
                    )
                    else -> drawOval(
                        col.copy(alpha = 0.55f),
                        topLeft = Offset(x - depth.ppm * (0.15f + h * 0.2f), y - depth.ppm * 0.06f),
                        size = Size(depth.ppm * (0.3f + h * 0.4f), depth.ppm * 0.13f)
                    )
                }
                i += 2
            }

            // Okraj naplaveniny – aby splynutie s cestou nebolo ostrý rez.
            buildEdgeRange(i0, i1, GameConfig.VERGE_DEPTH + 0.1f)
            drawPath(
                edge, shade(accent, day).copy(alpha = 0.45f),
                style = Stroke(width = (0.08f * depth.ppm).coerceAtLeast(1.5f))
            )
        }
    }

    /**
     * Povrch vozovky – žiadne stredové čiary, len materiál: praskliny
     * v asfalte, škáry medzi betónovými platňami, zrno v piesku a štrku.
     */
    private fun DrawScope.drawPavementDetail(n: Int, day: Float, segment: RoadSegment) {
        val paving = segment.paving
        if (paving == RoadPaving.ASPHALT) return
        val col = shade(
            when (paving) {
                RoadPaving.CRACKED -> Color(0xFF2C2A28)
                RoadPaving.CONCRETE -> Color(0xFF4E4C48)
                RoadPaving.DIRT -> Color(0xFF54402F)
                RoadPaving.GRAVEL_ROAD -> Color(0xFF8B857A)
                RoadPaving.SNOW, RoadPaving.PACKED_SNOW -> Color(0xFFFFFFFF)
                else -> Color(0xFFD8C08A)
            },
            day
        )
        var i = 0
        while (i < n - 1) {
            val wx = terrainW[i]
            when (paving) {
                RoadPaving.CONCRETE -> {
                    // Škára každé 4 m naprieč celou vozovkou.
                    if ((wx / 4f).toInt() != ((wx - 1.1f) / 4f).toInt()) {
                        drawLine(
                            col.copy(alpha = 0.55f),
                            Offset(depth.atX(terrainX[i], GameConfig.VERGE_DEPTH), depth.atY(terrainY[i], GameConfig.VERGE_DEPTH)),
                            Offset(depth.atX(terrainX[i], GameConfig.ROAD_DEPTH - 0.2f), depth.atY(terrainY[i], GameConfig.ROAD_DEPTH - 0.2f)),
                            strokeWidth = (0.06f * depth.ppm).coerceAtLeast(1f)
                        )
                    }
                }
                else -> {
                    val h = MathX.hash01(wx.toInt(), paving.ordinal * 613)
                    if (h < 0.30f) {
                        val d = GameConfig.VERGE_DEPTH + 0.25f +
                            h * (GameConfig.ROAD_DEPTH - GameConfig.VERGE_DEPTH - 0.6f) * 3f
                        val x = depth.atX(terrainX[i], d)
                        val y = depth.atY(terrainY[i], d)
                        if (paving == RoadPaving.CRACKED) {
                            drawLine(
                                col.copy(alpha = 0.45f),
                                Offset(x - depth.ppm * 0.3f, y - depth.ppm * 0.04f),
                                Offset(x + depth.ppm * 0.25f, y + depth.ppm * 0.05f),
                                strokeWidth = (0.04f * depth.ppm).coerceAtLeast(1f)
                            )
                        } else {
                            drawCircle(col.copy(alpha = 0.4f), depth.ppm * (0.02f + h * 0.08f), Offset(x, y))
                        }
                    }
                }
            }
            i++
        }
    }

    /**
     * Výtlky kreslíme len tam, kde je cesta naozaj rozbitá – inak z toho boli
     * rušivé bodky po celej trati.
     */
    private fun DrawScope.drawPotholes(n: Int, day: Float, segment: RoadSegment) {
        val col = shade(Color(0xFF241D16), day).copy(alpha = 0.30f)
        for (i in 0 until n step 4) {
            val rough = segment.bumpinessAtLocal(terrainW[i] - segment.worldOrigin)
            if (rough < 0.5f) continue
            val h = MathX.hash01(terrainW[i].toInt(), 6421)
            if (h > 0.10f) continue
            val d = GameConfig.VERGE_DEPTH + 0.4f + MathX.hash01(terrainW[i].toInt(), 77) * 1.6f
            val x = depth.atX(terrainX[i], d)
            val y = depth.atY(terrainY[i], d)
            val r = depth.ppm * (0.20f + h * 1.6f)
            drawOval(col, topLeft = Offset(x - r, y - r * 0.35f), size = Size(r * 2f, r * 0.7f))
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

    /** Ako [buildBand], ale len pre časť trate (naplaveniny). */
    private fun buildBandRange(i0: Int, i1: Int, frontDepth: Float, backDepth: Float) {
        band.reset()
        for (i in i0..i1) {
            band.lineToOrMove(i == i0, depth.atX(terrainX[i], frontDepth), depth.atY(terrainY[i], frontDepth))
        }
        for (i in i1 downTo i0) {
            band.lineTo(depth.atX(terrainX[i], backDepth), depth.atY(terrainY[i], backDepth))
        }
        band.close()
    }

    private fun buildEdgeRange(i0: Int, i1: Int, d: Float) {
        edge.reset()
        for (i in i0..i1) {
            edge.lineToOrMove(i == i0, depth.atX(terrainX[i], d), depth.atY(terrainY[i], d))
        }
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

    /**
     * Rozcestie sa ohlasuje uz z dialky: vetvy sa rozbiehaju od bodu odbocenia,
     * vybrana svieti, ostatne su stlmene. Pri ceste stoji smerovka.
     */
    private fun DrawScope.drawJunctionPreview(engine: GameEngine) {
        val choices = engine.segment.choices
        if (choices.isEmpty()) return
        val fork = engine.segment.endWorldX
        val toFork = fork - engine.camera.x
        if (toFork > GameConfig.JUNCTION_APPROACH || toFork < -12f) return

        // Cim blizsie k odbocke, tym vyraznejsie sa vetvy rozostupuju.
        val nearness = MathX.smoothstep(GameConfig.JUNCTION_APPROACH, 20f, toFork)
        val base = engine.segment.heightAtLocal(engine.segment.length)

        choices.forEachIndexed { idx, choice ->
            val spread = when (idx) {
                0 -> 2.0f
                1 -> 0f
                else -> -1.8f
            }
            val picked = engine.pendingChoiceId == choice.id
            val dimmed = engine.pendingChoiceId != null && !picked
            val col = when (choice.style) {
                BranchStyle.SAFE_RURAL -> Color(0xFF6B8F5A)
                BranchStyle.INDUSTRIAL -> Color(0xFF6A7A8A)
                BranchStyle.SHORTCUT_RISK -> Color(0xFFB85C38)
            }.copy(alpha = if (dimmed) 0.2f else if (picked) 0.9f else 0.5f)

            edge.reset()
            var first = true
            var t = 0f
            while (t <= 26f) {
                val wx = fork + t
                val wy = base + spread * MathX.smoothstep(0f, 16f, t) +
                    MathX.approxSin(t * 0.2f) * 0.1f
                val px = depth.atX(depth.frontX(wx), PLAY_DEPTH_PROXY)
                val py = depth.atY(depth.frontY(wy), PLAY_DEPTH_PROXY)
                if (first) {
                    edge.moveTo(px, py)
                    first = false
                } else edge.lineTo(px, py)
                t += 1.2f
            }
            val w = (0.35f + 0.35f * nearness + if (picked) 0.25f else 0f) * depth.ppm
            drawPath(edge, col, style = Stroke(width = w.coerceAtLeast(3f), cap = StrokeCap.Round))
        }

        drawJunctionSign(engine, fork, base, nearness)
    }

    /** Smerovka pri odbocke – prve, co hrac na obzore uvidi. */
    private fun DrawScope.drawJunctionSign(
        engine: GameEngine,
        fork: Float,
        base: Float,
        nearness: Float
    ) {
        val px = depth.atX(depth.frontX(fork - 4f), PLAY_DEPTH_PROXY + 2f)
        val groundY = depth.atY(depth.frontY(base), PLAY_DEPTH_PROXY + 2f)
        val h = 3.2f * depth.ppm * (1f - depth.perspectiveT(PLAY_DEPTH_PROXY + 2f))
        val alpha = (0.35f + 0.65f * nearness).coerceIn(0f, 1f)
        drawLine(
            Color(0xFF4A4137).copy(alpha = alpha),
            Offset(px, groundY),
            Offset(px, groundY - h),
            strokeWidth = (0.16f * depth.ppm).coerceAtLeast(2f)
        )
        val boardW = h * 0.75f
        val boardH = h * 0.22f
        engine.segment.choices.forEachIndexed { idx, choice ->
            val picked = engine.pendingChoiceId == choice.id
            val col = when (choice.style) {
                BranchStyle.SAFE_RURAL -> Color(0xFF6B8F5A)
                BranchStyle.INDUSTRIAL -> Color(0xFF6A7A8A)
                BranchStyle.SHORTCUT_RISK -> Color(0xFFB85C38)
            }
            val top = groundY - h + idx * (boardH + boardH * 0.35f)
            // Doska ukazuje smerom, ktorym vetva odbocuje.
            val left = if (idx % 2 == 0) px else px - boardW
            drawRect(
                col.copy(alpha = if (picked) alpha else alpha * 0.65f),
                topLeft = Offset(left, top),
                size = Size(boardW, boardH)
            )
            if (picked) {
                drawRect(
                    ACCENT.copy(alpha = alpha),
                    topLeft = Offset(left, top),
                    size = Size(boardW, boardH),
                    style = Stroke(width = 2f)
                )
            }
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

    /** Hĺbka, v ktorej stojí auto aj jeho tieň – stred pásu vozovky. */
    private fun carDepth(): Float = GameConfig.RUT_NEAR_DEPTH

    private data class CarScreenPose(
        val bodyX: Float,
        val bodyY: Float,
        val rearGround: Offset,
        val frontGround: Offset,
        val rearWheel: Offset,
        val frontWheel: Offset,
        val midGround: Offset,
        val slopeDeg: Float,
        val wheelR: Float
    )

    /** Jednotný výpočet pozície auta, kolies a tieňa v obrazovkových súradniciach. */
    private fun carScreenPose(engine: GameEngine): CarScreenPose {
        val car = engine.car
        val d = carDepth()
        val wb = SedanSpec.wheelOffsetX
        val layers = assets.sedan
        val rearScale = car.wheelScale(ComponentSlot.TIRE_REAR)
        val frontScale = car.wheelScale(ComponentSlot.TIRE_FRONT)
        val rearWheelR = carArtist.wheelRadiusPx(layers, depth.ppm) * rearScale
        val frontWheelR = carArtist.wheelRadiusPx(layers, depth.ppm) * frontScale
        val wheelR = (rearWheelR + frontWheelR) * 0.5f
        val scale = (layers.worldWidthM * depth.ppm) / layers.imageWidth
        val drawH = layers.imageHeight * scale
        // O koľko je stred blatníka pod vizuálnym stredom karosérie v sprite.
        val wellBelowCenter = (layers.wheelCenterFy - 0.52f) * drawH

        val rearWorldX = car.x - wb
        val frontWorldX = car.x + wb
        val rearGy = engine.segment.heightAtWorld(rearWorldX)
        val frontGy = engine.segment.heightAtWorld(frontWorldX)

        fun sx(wx: Float) = depth.atX(depth.frontX(wx), d)
        fun sy(wy: Float) = depth.atY(depth.frontY(wy), d)

        val rearGround = Offset(sx(rearWorldX), sy(rearGy))
        val frontGround = Offset(sx(frontWorldX), sy(frontGy))
        val midGround = Offset(
            (rearGround.x + frontGround.x) * 0.5f,
            (rearGround.y + frontGround.y) * 0.5f
        )
        val slopeDeg = Math.toDegrees(
            kotlin.math.atan2(
                (frontGround.y - rearGround.y).toDouble(),
                (frontGround.x - rearGround.x).toDouble()
            )
        ).toFloat()

        val bodyX = sx(car.x)
        val wheelMidY = ((rearGround.y - rearWheelR) + (frontGround.y - frontWheelR)) * 0.5f
        // Blatníky na kolesách; stlačenie pruženia karosériu vtiahne; ride height mení svetlosť.
        val avgComp = (car.rearCompression + car.frontCompression) * 0.5f
        val sinkPx = (avgComp * depth.ppm * GameConfig.SUSP_VISUAL_GAIN)
            .coerceIn(0f, wellBelowCenter * 0.55f)
        val rideBiasPx = (car.rideHeight - GameConfig.CAR_RIDE_HEIGHT) * depth.ppm
        val bodyLiftPx = GameConfig.BODY_VISUAL_LIFT * depth.ppm
        val bodyY = if (car.grounded) {
            wheelMidY - wellBelowCenter + sinkPx - rideBiasPx - bodyLiftPx
        } else {
            sy(car.y)
        }

        val layout = carArtist.layoutAtBody(layers, bodyX, bodyY, depth.ppm)
        val theta = -car.pitch
        val c = kotlin.math.cos(theta)
        val s = kotlin.math.sin(theta)
        fun well(wx: Float, wy: Float): Offset {
            val dx = wx - bodyX
            val dy = wy - bodyY
            return Offset(bodyX + dx * c - dy * s, bodyY + dx * s + dy * c)
        }

        val rearWell = well(layout.rearWx, layout.rearWy)
        val frontWell = well(layout.frontWx, layout.frontWy)
        val rearWheel: Offset
        val frontWheel: Offset
        if (car.grounded) {
            rearWheel = Offset(rearWell.x, rearGround.y - rearWheelR)
            frontWheel = Offset(frontWell.x, frontGround.y - frontWheelR)
        } else {
            rearWheel = rearWell
            frontWheel = frontWell
        }

        return CarScreenPose(
            bodyX, bodyY, rearGround, frontGround, rearWheel, frontWheel,
            midGround, slopeDeg, wheelR
        )
    }

    private fun DrawScope.drawCarShadow(engine: GameEngine) {
        val car = engine.car
        val layers = assets.sedan
        val pose = carScreenPose(engine)
        val groundY = engine.segment.heightAtWorld(car.x)
        val clearance = (car.y - groundY - car.rideHeight).coerceAtLeast(0f)
        val air = (clearance / 2.8f).coerceIn(0f, 1f)
        val span = hypot(
            pose.frontGround.x - pose.rearGround.x,
            pose.frontGround.y - pose.rearGround.y
        ).coerceAtLeast(layers.worldWidthM * depth.ppm * 0.85f)
        val w = span * (1.02f - 0.22f * air)
        val h = depth.ppm * (0.40f - 0.14f * air)
        // Tieň leží na svahu medzi predným a zadným kolesom.
        rotate(degrees = pose.slopeDeg, pivot = pose.midGround) {
            drawOval(
                Color.Black.copy(alpha = 0.36f * (1f - 0.55f * air)),
                topLeft = Offset(pose.midGround.x - w * 0.5f, pose.midGround.y - h * 0.35f),
                size = Size(w, h)
            )
        }
    }

    private fun DrawScope.drawCar(engine: GameEngine) {
        val car = engine.car
        val pose = carScreenPose(engine)
        val braking = engine.brakeInput > 0.25f && car.speed >= 0f
        with(carArtist) {
            drawSedan(
                car = car,
                bodyX = pose.bodyX,
                bodyY = pose.bodyY,
                angle = car.pitch,
                ppm = depth.ppm,
                proj = depth,
                wheelSpinDeg = car.wheelSpinDeg,
                braking = braking,
                layers = assets.sedan,
                roadY = pose.midGround.y,
                rearSpinDeg = car.wheelSpinRearDeg,
                frontSpinDeg = car.wheelSpinFrontDeg,
                cargoFill = engine.inventory.let {
                    it.usedSlots.toFloat() / it.slots.size.coerceAtLeast(1)
                },
                rearWheelX = pose.rearWheel.x,
                rearWheelY = pose.rearWheel.y,
                frontWheelX = pose.frontWheel.x,
                frontWheelY = pose.frontWheel.y
            )
        }
    }

    private fun resetTrails() {
        skidCount = 0
        skidHead = 0
        lastSkidX = Float.NaN
    }

    /** Zapíše stopu, keď kolesá preklzávajú alebo sú zablokované. */
    private fun recordSkid(engine: GameEngine) {
        val car = engine.car
        val slip = car.wheelSlip
        val moving = kotlin.math.abs(car.speed) > 0.35f
        if (slip < 0.22f || (!moving && !car.wheelsLocked)) {
            if (slip < 0.15f) lastSkidX = Float.NaN
            return
        }
        if (!lastSkidX.isNaN() && kotlin.math.abs(car.x - lastSkidX) < 0.4f) return
        lastSkidX = car.x
        skidX[skidHead] = car.x
        skidPower[skidHead] = slip
        skidStamp[skidHead] = engine.elapsed
        skidHead = (skidHead + 1) % SKID_MAX
        if (skidCount < SKID_MAX) skidCount++
    }

    /** Čierne pásy na vozovke – držia sa zeme a pomaly blednú. */
    private fun DrawScope.drawSkidMarks(engine: GameEngine, day: Float) {
        if (skidCount == 0) return
        val seg = engine.segment
        val now = engine.elapsed
        val col = shade(Color(0xFF1A1512), day)
        for (i in 0 until skidCount) {
            val age = now - skidStamp[i]
            if (age > SKID_LIFE) continue
            val wx = skidX[i]
            if (wx < engine.camera.x - 22f || wx > engine.camera.x + 22f) continue
            val fade = (1f - age / SKID_LIFE) * skidPower[i]
            val fy = depth.frontY(seg.heightAtWorld(wx))
            val fx = depth.frontX(wx)
            for (rut in 0 until 2) {
                val d = if (rut == 0) GameConfig.RUT_NEAR_DEPTH else GameConfig.RUT_FAR_DEPTH
                val x = depth.atX(fx, d)
                val y = depth.atY(fy, d)
                drawLine(
                    col.copy(alpha = 0.55f * fade),
                    Offset(x - depth.ppm * 0.22f, y),
                    Offset(x + depth.ppm * 0.22f, y),
                    strokeWidth = (0.20f * depth.ppm).coerceAtLeast(2f),
                    cap = StrokeCap.Round
                )
            }
        }
    }

    /** Prach spod kolies a dym z výfuku – viazané na sprite auta. */
    private fun DrawScope.drawCarEffects(engine: GameEngine, day: Float) {
        val car = engine.car
        val ppm = depth.ppm
        val pose = carScreenPose(engine)
        val layout = carArtist.layoutAtBody(assets.sedan, pose.bodyX, pose.bodyY, ppm)
        val t = engine.elapsed
        val px = pose.bodyX
        val py = pose.bodyY

        fun rotated(x: Float, y: Float): Offset {
            val theta = -car.pitch
            val c = kotlin.math.cos(theta)
            val s = kotlin.math.sin(theta)
            val dx = x - px
            val dy = y - py
            return Offset(px + dx * c - dy * s, py + dx * s + dy * c)
        }

        val speedRatio = (abs(car.speed) / GameConfig.MAX_SPEED).coerceIn(0f, 1f)
        if (speedRatio > 0.12f && car.grounded) {
            val dust = shade(Color(0xFFBFAE8E), day)
            for (i in 0 until 7) {
                val phase = (t * 1.6f + i * 0.37f) % 1f
                val h = MathX.hash01(i, (t * 2f).toInt())
                val x = pose.rearWheel.x - ppm * (0.2f + phase * 3.4f + h * 0.5f)
                val y = pose.rearGround.y - ppm * (0.05f + phase * 0.55f)
                val r = ppm * (0.10f + phase * 0.42f)
                drawCircle(
                    dust.copy(alpha = (0.30f * speedRatio) * (1f - phase)),
                    r,
                    Offset(x, y)
                )
            }
        }
        drawSurfaceSpray(engine, day, pose, ppm, t)
        drawSlipEffects(engine, day, pose, ppm, t)
        if (car.engineRunning) {
            val pipe = rotated(layout.exhaustX, layout.exhaustY)
            val smoke = shade(Color(0xFF9AA0A6), day)
            for (i in 0 until 5) {
                val phase = (t * 0.7f + i * 0.25f) % 1f
                val x = pipe.x - ppm * (0.2f + phase * 2.0f)
                val y = pipe.y - ppm * (0.08f + phase * 0.9f)
                val r = ppm * (0.07f + phase * 0.24f)
                drawCircle(smoke.copy(alpha = 0.26f * (1f - phase)), r, Offset(x, y))
            }
        }
    }

    /**
     * Striekance spod kolies pri prejazde naplaveninou – voda strieka,
     * bahno lieta, piesok sa práši. Ide z oboch kolies, nie len z hnaného.
     */
    private fun DrawScope.drawSurfaceSpray(
        engine: GameEngine,
        day: Float,
        pose: CarScreenPose,
        ppm: Float,
        t: Float
    ) {
        val surface = engine.currentSurface
        if (!surface.hazard || !engine.car.grounded) return
        val v = (abs(engine.car.speed) / 10f).coerceIn(0f, 1f)
        if (v < 0.12f) return
        val col = shade(
            when (surface) {
                RoadSurface.WATER -> Color(0xFFBFE2EC)
                RoadSurface.MUD -> Color(0xFF4E3A28)
                RoadSurface.SAND -> Color(0xFFDCC08A)
                RoadSurface.ICE, RoadSurface.SLUSH -> Color(0xFFDDE9EE)
                else -> Color(0xFF8E877D)
            },
            day
        )
        for (wheel in 0 until 2) {
            val w = if (wheel == 0) pose.rearWheel else pose.frontWheel
            val baseY = w.y + pose.wheelR * 0.4f
            for (i in 0 until 6) {
                val phase = ((t * 2.6f) + i * 0.19f + wheel * 0.11f) % 1f
                val h = MathX.hash01(i + wheel * 13, (t * 8f).toInt())
                // Striekance letia dozadu a hore, potom padajú.
                val x = w.x - ppm * (phase * 2.2f + h * 0.3f)
                val y = baseY - ppm * (phase * 1.4f - phase * phase * 1.9f)
                val r = ppm * (0.05f + h * 0.07f) * (if (surface == RoadSurface.WATER) 1.2f else 1f)
                drawCircle(col.copy(alpha = 0.7f * v * (1f - phase)), r, Offset(x, y))
            }
        }
    }

    private fun DrawScope.drawSlipEffects(
        engine: GameEngine,
        day: Float,
        pose: CarScreenPose,
        ppm: Float,
        t: Float
    ) {
        val car = engine.car
        val slip = car.wheelSlip
        if (slip < 0.12f) return

        // Dym ide spod hnanej nápravy – pri FWD spredu, pri RWD zozadu.
        val driven = if (car.drivenSlot == ComponentSlot.TIRE_FRONT) pose.frontWheel else pose.rearWheel
        val rearX = driven.x
        val rearY = driven.y + pose.wheelR * 0.35f
        val surface = engine.currentSurface
        val smoke = shade(
            when {
                surface == RoadSurface.WATER -> Color(0xFFCFE7EE)
                surface == RoadSurface.MUD -> Color(0xFF6A523A)
                surface == RoadSurface.SAND -> Color(0xFFE0CB99)
                car.wheelsLocked -> Color(0xFFD8D2C8)
                else -> Color(0xFFC9BCA4)
            },
            day
        )

        val puffs = 5 + (slip * 5f).toInt()
        for (i in 0 until puffs) {
            val phase = ((t * 1.9f) + i * 0.23f) % 1f
            val jitter = MathX.hash01(i, (t * 6f).toInt()) - 0.5f
            val x = rearX - ppm * (phase * 3.2f) + jitter * ppm * 0.4f
            val y = rearY - ppm * (0.1f + phase * 1.25f + jitter * 0.15f)
            val r = ppm * (0.18f + phase * 0.75f)
            drawCircle(
                smoke.copy(alpha = 0.34f * slip * (1f - phase)),
                r,
                Offset(x, y)
            )
        }

        if (!car.wheelsLocked && slip > 0.3f) {
            val grit = shade(
                when (surface) {
                    RoadSurface.MUD -> Color(0xFF3A2A1C)
                    RoadSurface.SAND -> Color(0xFFB99A5F)
                    RoadSurface.WATER -> Color(0xFF8FC4D2)
                    RoadSurface.GRAVEL -> Color(0xFF7A736A)
                    // Z ľadu odlietavajú úlomky, z brečky mokrá kaša.
                    RoadSurface.ICE -> Color(0xFFDCEFF7)
                    RoadSurface.SLUSH -> Color(0xFFA9BAC2)
                    RoadSurface.ASPHALT -> Color(0xFF6B5A44)
                },
                day
            )
            for (i in 0 until 6) {
                val phase = ((t * 3.1f) + i * 0.17f) % 1f
                val h = MathX.hash01(i, (t * 9f).toInt())
                val x = rearX - ppm * (phase * 4.5f)
                val y = rearY - ppm * (phase * 1.9f - phase * phase * 2.2f) - ppm * 0.05f
                drawCircle(
                    grit.copy(alpha = 0.75f * slip * (1f - phase)),
                    ppm * (0.035f + h * 0.035f),
                    Offset(x, y)
                )
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
        val pose = carScreenPose(engine)
        with(carArtist) {
            drawHeadlightBeam(
                bodyX = pose.bodyX,
                bodyY = pose.bodyY,
                angle = car.pitch,
                ppm = depth.ppm,
                proj = depth,
                layers = assets.sedan,
                strength = 0.3f + night * 0.7f,
                braking = engine.brakeInput > 0.25f && car.speed >= 0f,
                roadY = pose.midGround.y
            )
        }
    }

    private fun wave(x: Float): Float = MathX.approxSin(x) * 0.5f + 0.5f

    companion object {
        private const val MAX_POINTS = 260
        private const val RAIN_DROPS = 90
        private const val SNOW_FLAKES = 70
        /** Vietor v daždi a snežení – konštantný, nezávislý od rýchlosti auta. */
        private const val RAIN_DRIFT = 330f
        private const val RAIN_SLANT = 0.26f
        private const val SNOW_DRIFT = 70f
        private const val PLAY_DEPTH_PROXY = 1.4f

        /** Zlatá HUD farba – zvýraznenie vybranej vetvy na smerovke. */
        private val ACCENT = Color(0xFFD2AE63)
        /** Koľko stôp po preklze si pamätáme a ako dlho vydržia (s). */
        private const val SKID_MAX = 96
        private const val SKID_LIFE = 7f

        /** Od akej výšky nad terénom považujeme úsek za most. */
        private const val MIN_CLEARANCE = 0.35f

        /** Najhlbšia vrstva kulís – lúka musí siahať aspoň sem. */
        const val SCENERY_BACK_DEPTH = GameConfig.ROAD_DEPTH + 1.7f
    }
}
