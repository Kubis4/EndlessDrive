package sk.kubis.endlessdrive.ui.game

import androidx.compose.ui.geometry.Rect

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.lerp
import sk.kubis.endlessdrive.core.GameConfig
import sk.kubis.endlessdrive.core.MathX
import sk.kubis.endlessdrive.domain.model.BiomeType
import sk.kubis.endlessdrive.domain.model.BuildingType
import sk.kubis.endlessdrive.domain.model.ComponentSlot
import sk.kubis.endlessdrive.domain.model.RoadFeature
import sk.kubis.endlessdrive.domain.model.RoadPaving
import sk.kubis.endlessdrive.domain.model.RoadSurface
import sk.kubis.endlessdrive.domain.model.SedanSpec
import sk.kubis.endlessdrive.domain.model.TIRE_SLOTS
import sk.kubis.endlessdrive.game.Camera2D
import sk.kubis.endlessdrive.game.DepthProjection
import sk.kubis.endlessdrive.game.GameEngine
import sk.kubis.endlessdrive.game.event.RoadEvent
import sk.kubis.endlessdrive.game.car.TireInjury
import sk.kubis.endlessdrive.game.world.RoadSegment
import sk.kubis.endlessdrive.game.world.BiomeBlend
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Bočný 2.5D renderer: procedurálna obloha s denným cyklom, parallax kopce,
 * kulisy pri ceste, HillRush terén, budovy a sedan s efektmi.
 */
class GameRenderer(private val assets: GameAssets) {
    private val depth = DepthProjection()
    private val carArtist = CarArtist()
    private val sky = SkyPainter()
    private val scenery = SceneryPainter(assets.sedan, assets.wreckSprites)
    private val buildings = BuildingPainter()
    private val band = Path()
    private val edge = Path()
    private val soil = Path()
    private val materials = MaterialPainter()
    private val apronTop = FloatArray(MAX_POINTS)
    private val apronBottom = FloatArray(MAX_POINTS)

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
    /** 1 = náprava na ráfiku, 0 = guma (aj defekt). */
    private val skidRim = ByteArray(SKID_MAX)
    private var skidCount = 0
    private var skidHead = 0
    private var lastFrontSkidX = Float.NaN
    private var lastRearSkidX = Float.NaN
    /** Renderer prežije reštart jazdy – stopy z predošlej sa musia zahodiť. */
    private var lastEngine: GameEngine? = null

    /** Výška mostovky nad terénom pre každú vzorku (0 = žiadny most). */
    private val bridgeClearance = FloatArray(MAX_POINTS)

    /** Nazbieraný posun vrstiev pozadia (far, mid, near) v pixeloch. */
    private val backdropShift = FloatArray(3)
    private val backdropBlendPaint = Paint()
    private val backdropSkyPaint = Paint()
    private var lastBackdropX = Float.NaN
    /** Farba zeme z aktuálnej kresby – tráva, apron aj cesta sa k nej priblížia. */
    private var sceneGround = Color(0xFF6B5340)
    private var sceneMeadow = Color(0xFF6B5340)
    /** Stabilný spodok mid vrstvy – apron naň nadviaže bez pohybu kulís po kopcoch. */
    private var landscapeMidBase = 0f

    private val headlightCone = Path()
    private val headlightPool = Path()
    private val headlightUnion = Path()
    private val rackCone = Path()
    private val headlightMatrix = Matrix()
    private val saveLayerPaint = Paint()
    private val dstInPaint = Paint().apply { blendMode = BlendMode.DstIn }
    private val layerTintFilter = TintFilterSlot(BlendMode.Modulate)
    private val layerHazeFilter = TintFilterSlot(BlendMode.SrcIn)
    private val bridgeWx = FloatArray(BRIDGE_WATER_MAX)
    private val bridgeGround = FloatArray(BRIDGE_WATER_MAX)
    private val slipAxles = arrayOf(ComponentSlot.TIRE_REAR, ComponentSlot.TIRE_FRONT)
    private var headlightLamp = Offset.Zero
    private var rackLamp = Offset.Zero
    private var revealHeadlights = false
    private var revealRoof = false
    private var headlightNoseX = 0f
    private var headlightTailX = 0f

    // Častice majú nemenné semienka; v render loope tak ostáva iba pohyb.
    private val rainSeedX = FloatArray(RAIN_DROPS) { i -> MathX.hash01(i, 17) }
    private val rainSeedY = FloatArray(RAIN_DROPS) { i -> MathX.hash01(i, 41) }
    private val snowSeedX = FloatArray(SNOW_FLAKES) { i -> MathX.hash01(i, 29) }
    private val snowSeedY = FloatArray(SNOW_FLAKES) { i -> MathX.hash01(i, 53) }
    private val dustSeed = FloatArray(DUST_SHEETS) { i -> MathX.hash01(i, 211) }
    private val dustSeedY = FloatArray(DUST_SHEETS) { i -> MathX.hash01(i, 307) }
    private val sandSeedX = FloatArray(SAND_GRAINS) { i -> MathX.hash01(i, 61) }
    private val sandSeedY = FloatArray(SAND_GRAINS) { i -> MathX.hash01(i, 97) }
    private val windSeed = FloatArray(TAILWIND_STREAKS) { i -> MathX.hash01(i, 137) }
    private val windSeedY = FloatArray(TAILWIND_STREAKS) { i -> MathX.hash01(i, 263) }
    private val windSeedAngle = FloatArray(TAILWIND_STREAKS) { i -> MathX.hash01(i, 419) }

    fun DrawScope.draw(engine: GameEngine) {
        if (lastEngine !== engine) {
            lastEngine = engine
            resetTrails()
        }
        engine.setScreenHeight(size.height)
        val cam = engine.camera
        // Kamera sa pri rýchlosti pozerá dopredu. Bez kompenzácie sa jej
        // look-ahead odčíta priamo od pozície auta a na tablete ho vytlačí
        // cez ľavý okraj. Posunieme preto iba projekčný stred, nie auto vo
        // svete; na každom pomere strán tak zostane v bezpečnej časti záberu.
        val lookAheadPx = engine.car.speed.coerceAtLeast(0f) * GameConfig.CAMERA_LOOK_AHEAD * cam.ppm
        val halfW = size.width * GameConfig.CAR_SCREEN_X + lookAheadPx * CAR_LOOK_AHEAD_COMPENSATION
        val halfH = size.height / 2f
        // Otras sa pridá až do projekcie – svet sa nehýbe, hýbe sa kamera.
        depth.begin(cam.x + cam.shakeX, cam.y + cam.shakeY, cam.ppm, halfW, halfH, cam.pitch)

        val day = engine.daylight
        val environment = engine.biomeBlend
        val biome = environment.dominant
        // Horizont je nad vozovkou, nie prilepený na ňu – mesa/les zaberú
        // viac záberu. Mid/near prekryjú švík; farba zeme je poistka.
        // Pozadie má mať viac priestoru nad lokálnym profilom cesty. Samotná
        // cesta sa stále premieta z terénu; nižšie kopce preto nezdvihnú ani
        // nespustia vzdialený horizont a apron medzi nimi zostane prirodzený.
        val horizonY = size.height * BACKDROP_HORIZON

        // Terén zbierame pred pozadím pre apron, ale samotné vzdialené kulisy
        // ostávajú v screen-space. Keď ich spodok sledoval medián kopcov, celý
        // les pri každom stúpaní a klesaní viditeľne poskakoval.
        collectTerrain(engine, cam.ppm, size.width, halfW)
        landscapeMidBase = BackdropLayout.anchorForHorizon(horizonY, size.height)

        // Kreslené pozadie má dnes každý bióm a nesie si vlastnú oblohu aj
        // krajinu – procedurálne vrstvy by sa cezeň len bili.
        updateBackdropScroll(cam, engine.car.x)
        val backdrop = assets.backdropFor(environment.from)
        val nextBackdrop = if (environment.amount > 0.001f && environment.to != environment.from) {
            assets.backdropFor(environment.to)
        } else null
        rememberLandscape(backdrop, nextBackdrop, environment.amount)
        // Fade the assembled scenes once, including all three layers and celestial light.
        // Fading incoming transparent sprites alone leaves the old buildings visible
        // through their gaps until the segment switches.
        drawBackdropCrossfade(
            if (nextBackdrop == null) 0f else environment.amount,
            backdropBlendPaint,
            from = { drawBackdropScene(environment.from, backdrop, cam, horizonY, day, engine.timeOfDay) },
            to = {
                drawBackdropScene(
                    environment.to,
                    nextBackdrop ?: backdrop,
                    cam,
                    horizonY,
                    day,
                    engine.timeOfDay
                )
            }
        )
        val skyHaze = when (environment.from) {
            BiomeType.FOREST, BiomeType.FOREST_ALIVE -> 0.16f
            BiomeType.DESERT, BiomeType.DESERT_DUSK, BiomeType.WASTELAND -> 0.20f
            else -> 0.4f
        }
        with(sky) {
            drawHaze(
                horizonY, day, environment.from, strength = skyHaze,
                nextBiome = environment.to, transition = environment.amount
            )
        }

        // Auto nie je v strede, takže doprava treba dohliadnuť ďalej než doľava.
        val worldW = size.width / cam.ppm
        val screenOrigin = halfW / size.width
        val visibleFrom = cam.x - worldW * screenOrigin - EDGE_MARGIN
        val visibleTo = cam.x + worldW * (1f - screenOrigin) + EDGE_MARGIN
        // Kulisy stoja na teréne, nie na mostovke.
        val heightAt: (Float) -> Float = { wx -> groundFor(engine, wx) }

        // Poradie je dôležité: najprv zem, potom kulisy (stoja na nej), až potom cesta.
        drawLandscapeApron(environment, engine.winterAmount, day)
        drawGround(engine.segment, environment, engine.winterAmount, day)
        // Kulisy nepatria do budovy ani do rokliny pod mostom. Rovnaký filter
        // dostanú stromy, debny, stĺpy, patníky aj drobnosti v popredí.
        val occupiedGround: (Float) -> Boolean = { wx ->
            engine.segment.buildingOccupies(wx, BUILDING_CLEAR_M) ||
                engine.segment.bridgeClearanceAtWorld(wx) > BRIDGE_PROP_CLEARANCE_M
        }
        with(scenery) {
            drawBackProps(
                visibleFrom, visibleTo, engine.segment::biomeBlendAtWorld,
                day, depth, heightAt, occupiedGround,
                sceneMeadow, landFollowAmount(environment, engine.winterAmount)
            )
        }
        drawBridges(engine, day)
        // Voda má rovnakú geometriu ako vozovka: plochu o kúsok nižšie
        // a bočné steny, ktoré siahajú až na dno jamy.
        drawBridgeWaterDeck(engine, day)
        drawRoadSurface(engine.segment, day)
        drawRoadHistory(engine.segment, day)
        drawSurfacePatches(engine.segment, day)
        drawRoadEventDecals(engine, day, visibleFrom, visibleTo)
        recordSkid(engine)
        drawSkidMarks(engine, day)
        drawBuildings(engine, day)
        drawCarShadow(engine)
        drawCar(engine, day)
        drawCarEffects(engine, day)
        with(scenery) {
            drawFrontProps(
                visibleFrom, visibleTo, engine.segment::biomeBlendAtWorld,
                day, depth, heightAt, occupiedGround,
                sceneMeadow, landFollowAmount(environment, engine.winterAmount)
            )
        }
        drawNight(engine, day, horizonY, visibleFrom, visibleTo)
        drawTailwind(engine, day)
        drawWeather(engine, day)
        drawVignette(engine, day)
    }

    /**
     * Počasie cez celú scénu: dážď šikmými šmuhami, sneženie pomalými vločkami.
     * Kreslí sa v obrazovkových súradniciach – lacné a nezávislé od projekcie.
     */
    private fun DrawScope.drawWeather(engine: GameEngine, day: Float) {
        if (engine.segment.biome == BiomeType.SANDSTORM) drawSandstorm(engine, day)
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
                val seedX = rainSeedX[i]
                val seedY = rainSeedY[i]
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
                val seedX = snowSeedX[i]
                val seedY = snowSeedY[i]
                val fall = 60f + seedY * 90f
                // Vločky sa hompáľajú – bez toho vyzerá sneh ako dážď.
                val sway = MathX.approxSin(t * (0.7f + seedX) + i.toFloat()) * w * 0.02f
                val x = ((seedX * w) + sway - (t * SNOW_DRIFT) % w + w) % w
                val y = ((seedY * h) + (t * fall) % h) % h
                drawCircle(col, 1.4f + seedY * 2.4f, Offset(x, y))
            }
        }
    }

    /**
     * Piesočná búrka: teplý závoj cez celú scénu, letiace zrná a vlny prachu,
     * ktoré prechádzajú obrazom. Rýchlosť auta pridá zrnám ťah dozadu.
     */
    private fun DrawScope.drawSandstorm(engine: GameEngine, day: Float) {
        val t = engine.elapsed
        val w = size.width
        val h = size.height
        val tone = shade(Color(0xFFC9A164), day)

        // Závoj: hore hustejší, pri ceste redší, nech je stále vidieť, kam sa ide.
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    tone.copy(alpha = 0.42f),
                    tone.copy(alpha = 0.26f),
                    tone.copy(alpha = 0.10f)
                ),
                startY = 0f,
                endY = h
            ),
            size = Size(w, h)
        )

        // Vlny prachu – široké pruhy, ktoré preletia obrazom a dajú búrke pohyb.
        for (i in 0 until DUST_SHEETS) {
            val seed = dustSeed[i]
            val speed = 260f + seed * 420f
            val x = w - ((t * speed + seed * w * 2.2f) % (w * 1.8f))
            val y = h * (0.10f + dustSeedY[i] * 0.72f)
            val bandH = h * (0.06f + seed * 0.10f)
            drawOval(
                tone.copy(alpha = 0.10f + seed * 0.09f),
                topLeft = Offset(x, y - bandH * 0.5f),
                size = Size(w * (0.55f + seed * 0.6f), bandH)
            )
        }

        // Zrná piesku – čiarky, nie bodky; rýchlosť auta ich natiahne.
        val speedT = (abs(engine.car.speed) / GameConfig.MAX_SPEED).coerceIn(0f, 1f)
        val grain = shade(Color(0xFFE6C88E), day)
        for (i in 0 until SAND_GRAINS) {
            val seedX = sandSeedX[i]
            val seedY = sandSeedY[i]
            val speed = 900f + seedY * 1400f + speedT * 900f
            val x = ((seedX * w) - (t * speed) % (w * 1.2f) + w * 1.2f) % (w * 1.2f)
            val y = ((seedY * h) + MathX.approxSin(t * (0.6f + seedX) + i.toFloat()) * h * 0.02f + h) % h
            val len = w * (0.02f + seedY * 0.05f) * (0.7f + speedT * 0.8f)
            drawLine(
                grain.copy(alpha = 0.20f + seedX * 0.28f),
                Offset(x, y),
                Offset(x + len, y + len * 0.10f),
                strokeWidth = 1.2f + seedY * 1.4f
            )
        }
    }

    /**
     * Stopy po tom, čo sa na ceste dialo pred nami: čierne šmyky pred prudkými
     * stúpaniami a záplaty asfaltu. Oboje sedí na pevnej svetovej mriežke,
     * takže sa pri jazde nehýbe ani nepreblikáva.
     *
     * Detail je zámerne viazaný na sklon – v hre bez riadenia je jediné, čo
     * hráča trápi, kopec. Značky ani zvodidlá by klamali o tom, ako sa hrá.
     */
    private fun DrawScope.drawRoadHistory(segment: RoadSegment, day: Float) {
        val n = terrainCount
        if (n < 2) return
        val ppm = depth.ppm
        val first = MathX.floorDiv(terrainW[0], HISTORY_CELL)
        val last = MathX.floorDiv(terrainW[n - 1], HISTORY_CELL)
        val skid = shade(Color(0xFF1B1714), day)
        val patch = shade(Color(0xFF2E2C2A), day)

        for (cell in first..last) {
            val wx = cell * HISTORY_CELL
            val x0 = depth.atX(depth.frontX(wx), GameConfig.RUT_NEAR_DEPTH)
            if (x0 < -80f || x0 > size.width + 80f) continue
            val gy = segment.heightAtWorld(wx)
            val h = MathX.hash01(cell, 9137)

            // Šmyky: len tam, kde sa cesta naozaj dvíha.
            val slope = (segment.heightAtWorld(wx + 3f) - gy) / 3f
            if (slope > 0.22f && h < 0.34f) {
                for (rut in 0 until 2) {
                    val d = if (rut == 0) GameConfig.RUT_NEAR_DEPTH else GameConfig.RUT_FAR_DEPTH
                    val x = depth.atX(depth.frontX(wx), d)
                    val y = depth.atY(depth.frontY(gy), d)
                    val len = ppm * (0.5f + h * 1.4f)
                    drawLine(
                        skid.copy(alpha = 0.30f + h * 0.25f),
                        Offset(x - len * 0.5f, y),
                        Offset(x + len * 0.5f, y),
                        strokeWidth = (ppm * 0.17f).coerceAtLeast(2f),
                        cap = StrokeCap.Round
                    )
                }
            }

            // Záplaty – len na prasknutom a bežnom asfalte, inde nedávajú zmysel.
            //
            // Kreslia sa ako pás v rovine vozovky, nie ako obdĺžnik na obrazovke.
            // Pri obdĺžniku to vyzeralo ako šedá doska položená na cestu.
            val paved = segment.paving == RoadPaving.CRACKED || segment.paving == RoadPaving.ASPHALT
            if (paved && h > 0.86f) {
                val len = 1.4f + MathX.hash01(cell, 577) * 2.6f
                val near = GameConfig.VERGE_DEPTH + 0.25f + MathX.hash01(cell, 3391) * 0.9f
                val far = (near + 0.5f + MathX.hash01(cell, 1213) * 0.9f)
                    .coerceAtMost(GameConfig.ROAD_DEPTH - 0.2f)
                buildBandWorld(segment, wx, wx + len, near, far)
                drawPath(band, patch.copy(alpha = 0.42f))
                // Okraj zaliatej škáry – tenká tmavá linka po obvode.
                buildEdgeWorld(segment, wx, wx + len, near)
                drawPath(
                    edge,
                    shade(Color(0xFF14120F), day).copy(alpha = 0.40f),
                    style = Stroke(width = (ppm * 0.045f).coerceAtLeast(1f))
                )
            }
        }
    }

    /**
     * Udalosti, ktoré ležia na ceste: bahno a nanesené haluze. Kreslia sa
     * deterministicky z polohy vo svete, takže sa počas jazdy nehýbu – zjavia
     * a zmiznú len so začiatkom a koncom udalosti.
     */
    private fun DrawScope.drawRoadEventDecals(
        engine: GameEngine,
        day: Float,
        fromX: Float,
        toX: Float
    ) {
        val mud = engine.hasEvent(RoadEvent.MUD)
        val debris = engine.hasEvent(RoadEvent.DEBRIS)
        if (!mud && !debris) return

        val seg = engine.segment
        val first = MathX.floorDiv(fromX, DECAL_CELL)
        val last = MathX.floorDiv(toX, DECAL_CELL)
        val mudCol = shade(Color(0xFF4A3524), day)
        val branch = shade(Color(0xFF5C4530), day)

        for (cell in first..last) {
            val wx = cell * DECAL_CELL + MathX.hash01(cell, 5501) * DECAL_CELL
            val h = MathX.hash01(cell, 811)
            val gy = seg.heightAtWorld(wx)
            val d = GameConfig.VERGE_DEPTH + 0.3f + h * (GameConfig.ROAD_DEPTH - GameConfig.VERGE_DEPTH - 0.7f)
            val x = depth.atX(depth.frontX(wx), d)
            val y = depth.atY(depth.frontY(gy), d)
            if (x < -80f || x > size.width + 80f) continue
            val s = depth.ppm

            if (mud && h < 0.75f) {
                // Rozliata mláka – plochá elipsa, nie kruh.
                val w = s * (0.5f + h * 0.9f)
                drawOval(
                    mudCol.copy(alpha = 0.55f),
                    topLeft = Offset(x - w, y - s * 0.09f),
                    size = Size(w * 2f, s * 0.20f)
                )
                drawOval(
                    shade(Color(0xFF33241A), day).copy(alpha = 0.5f),
                    topLeft = Offset(x - w * 0.5f, y - s * 0.06f),
                    size = Size(w, s * 0.12f)
                )
            }
            if (debris && h > 0.30f) {
                // Konár: dve čiary pod uhlom, nie bodka.
                val len = s * (0.30f + h * 0.45f)
                val tilt = (MathX.hash01(cell, 1499) - 0.5f) * 0.5f
                drawLine(
                    branch,
                    Offset(x - len, y + tilt * len),
                    Offset(x + len, y - tilt * len),
                    strokeWidth = (s * 0.055f).coerceAtLeast(1.5f),
                    cap = StrokeCap.Round
                )
                drawLine(
                    branch.copy(alpha = 0.85f),
                    Offset(x, y),
                    Offset(x + len * 0.5f, y - s * 0.16f),
                    strokeWidth = (s * 0.04f).coerceAtLeast(1f),
                    cap = StrokeCap.Round
                )
            }
        }
    }

    /** Vietor dostane smer podľa aktívnej udalosti; šmuhy ukážu aj protivietor. */
    private fun DrawScope.drawTailwind(engine: GameEngine, day: Float) {
        val tailwind = engine.hasEvent(RoadEvent.TAILWIND)
        val headwind = engine.hasEvent(RoadEvent.HEADWIND)
        if (!tailwind && !headwind) return
        val t = engine.elapsed
        val w = size.width
        val h = size.height
        val col = shade(Color(0xFFE8F0E4), day)
        val leaf = shade(Color(0xFFB08A4A), day)
        val direction = when {
            tailwind && !headwind -> 1f
            headwind && !tailwind -> -1f
            else -> 0f
        }

        for (i in 0 until TAILWIND_STREAKS) {
            val seed = windSeed[i]
            val seedY = windSeedY[i]
            val seedA = windSeedAngle[i]
            val speed = 700f + seed * 900f
            val travel = (t * speed) % (w * 1.4f)
            val x = if (direction > 0f) {
                (seedY * w + travel) % (w * 1.4f) - w * 0.2f
            } else {
                (seedY * w - travel) % (w * 1.4f) + w * 0.2f
            }
            // Vietor je turbulentný: každá šmuha má vlastný sklon a v čase sa
            // vlní. Rovnobežné vodorovné čiary vyzerali ako hrebeň, nie vietor.
            val drift = MathX.approxSin(t * (0.8f + seedA) + i * 1.7f)
            val y = h * (0.14f + seedY * 0.70f) + drift * h * 0.035f
            val len = w * (0.04f + seed * 0.11f)
            val tilt = ((seedA - 0.5f) * 0.55f) + drift * 0.18f
            drawLine(
                col.copy(alpha = 0.08f + seed * 0.18f),
                Offset(x, y),
                Offset(x + direction * len, y + len * tilt),
                strokeWidth = 1f + seed * 2f,
                cap = StrokeCap.Round
            )
            // Občas lístok, ktorý sa v prúde prevracia – ukáže smer aj vír.
            if (seed > 0.66f) {
                val spin = MathX.approxSin(t * 3.1f + i.toFloat())
                val lw = w * 0.013f
                drawOval(
                    leaf.copy(alpha = 0.55f),
                    topLeft = Offset(x + direction * len, y + len * tilt),
                    size = Size(lw, (lw * 0.55f * kotlin.math.abs(spin)).coerceAtLeast(1f))
                )
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
                    // V noci sa vignetta nesčítava so závojom do čiernej diery.
                    Color(0xFF060810).copy(alpha = 0.28f + 0.10f * (1f - day) + 0.12f * speedT)
                ),
                center = Offset(size.width * 0.5f, size.height * 0.52f),
                radius = size.width * 0.72f
            ),
            size = Size(size.width, size.height)
        )
    }

    /**
     * Kreslené pozadie v troch vrstvách, každá dlaždica 4:1 ukotvená spodkom
     * na horizont.
     *
     * Úbežník projekcie leží nad obrazovkou a hĺbkové pásy siahajú len po
     * krajnicu, takže všetko ďalej je v tejto hre 2D – kreslený parallax je
     * správny nástroj. Musí sa však hýbať v jednotkách kamery, nie v pevných
     * pixeloch: posun je násobok `ppm`, aby sedel na každom DPI aj pri
     * speed-zoome, a reaguje aj na výšku kamery a naklonenie.
     *
     * Vzdialená krajina má samostatnú mierku; nad ňou sa obloha doplní gradientom,
     * `mid` a `near` sú pásy nad lúkou. Kresba v nich sedí pri spodku PNG
     * (vrch je priehľadný), preto ich kotvíme nad zadný okraj lúky – inak
     * stromy padnú na cestu a medzi kopcami ostane prázdny zelený pás z `far`.
     * Naveľko by z nich boli kmene cez pol obrazovky, teda popredie.
     */
    private fun DrawScope.drawBackdropScene(
        biome: BiomeType,
        backdrop: BiomeBackdrop,
        cam: Camera2D,
        horizonY: Float,
        day: Float,
        timeOfDay: Float
    ) {
        drawParallaxBackdrop(backdrop, horizonY, day, BackdropPass.SKY)
        // Hviezdy ostávajú za diaľkovou krajinou.
        with(sky) { drawStarfield(day, cam.x, horizonY) }
        // Slnko patrí za celú kreslenú krajinu. FAR, HORIZON aj LANDSCAPE ho
        // musia prekryť; inak pri východe presvitá medzi stromami.
        val celestialClipBottom = BackdropLayout.celestialClipBottom(
            horizonY,
            size.height,
            forestArtwork = biome == BiomeType.FOREST ||
                biome == BiomeType.FOREST_ALIVE ||
                biome == BiomeType.RURAL ||
                biome == BiomeType.ALPINE
        )
        clipRect(0f, 0f, size.width, celestialClipBottom) {
            with(sky) { drawCelestialOver(timeOfDay, day, horizonY) }
        }
        drawParallaxBackdrop(backdrop, horizonY, day, BackdropPass.FAR)
        drawParallaxBackdrop(backdrop, horizonY, day, BackdropPass.HORIZON)
        drawParallaxBackdrop(backdrop, horizonY, day, BackdropPass.LANDSCAPE)
    }

    private fun DrawScope.drawParallaxBackdrop(
        backdrop: BiomeBackdrop,
        horizonY: Float,
        day: Float,
        pass: BackdropPass,
        opacity: Float = 1f
    ) {
        // Nočné stmavenie a tón sady sa násobia – oboje ide cez Modulate naraz.
        val night = lerp(Color(0xFF1C2540), Color.White, day.coerceIn(0f, 1f)).let {
            Color(
                it.red * backdrop.tint.red,
                it.green * backdrop.tint.green,
                it.blue * backdrop.tint.blue
            )
        }
        // Vzdušná perspektíva: čím je vrstva ďalej, tým viac splynie s oblohou.
        // Bez toho je najtmavším prvkom záberu strom na obzore, nie auto.
        val haze = lerp(Color(0xFF2A3348), backdrop.hazeDay, day.coerceIn(0f, 1f))

        // Pozadie sa so speed-zoomom nemení veľkosťou.
        //
        // Toto bola tá „vracajúca sa“ vrstva pri brzdení: mierka menila šírku
        // dlaždice a posun sa zobrazuje ako `shift % w`. Keď sa w zmení, fáza
        // vzoru skočí – aj keď posun sám rastie hladko. Vzdialená kulisa sa
        // pri priblížení meniť nemusí, tak ju držíme v konštantnej mierke.

        // Posun sa počíta prírastkovo, nie ako camX * ppm * k.
        //
        // Absolútna poloha × meniace sa ppm bola chyba: pri brzdení sa mení
        // speed-zoom, a tá istá zmena ppm prenásobila celú prejdenú vzdialenosť.
        // Na 10. kilometri to znamenalo skok pozadia o stovky pixelov, hoci auto
        // spomalilo o pár m/s. Prírastok históriu neprepočítava.
        // Posun sa berie z polohy auta, nie kamery. Kamera si k cieľu pripočíta
        // predvídavosť (rýchlosť × look-ahead), takže pri brzdení couvne o pár
        // metrov dozadu – a pozadie, hlavne najbližšia vrstva, cuklo s ňou.
        val lift = backdrop.landscapeLift
        // Horizont je screen-space prvok. Výška auta, otrasy ani pitch kamery
        // ním nehýbu; hĺbku na kopcoch nesú cesta, apron a popredné kulisy.
        val farBase = horizonY + horizonY * BACKDROP_SINK - size.height * lift * 0.55f
        val artScale = backdrop.heightScale.coerceIn(0.85f, 1.35f)
        val farHeight = size.height * BACKDROP_FAR_HEIGHT * artScale
        when (pass) {
            BackdropPass.SKY -> {
                val skyEdge = Color(
                    backdrop.skyEdgeColor.red * night.red,
                    backdrop.skyEdgeColor.green * night.green,
                    backdrop.skyEdgeColor.blue * night.blue
                )
                val skyTop = Color(skyEdge.red * 0.86f, skyEdge.green * 0.90f, skyEdge.blue * 0.96f)
                drawRect(Brush.verticalGradient(listOf(skyTop, skyEdge), endY = farBase))

                // Vzdialená zem až po spodok obrazovky – cesta môže klesnúť hlboko
                // pod horizont a lúka ju prekryje až od svojej hrany.
                drawRect(
                    Color(
                        backdrop.groundColor.red * night.red,
                        backdrop.groundColor.green * night.green,
                        backdrop.groundColor.blue * night.blue,
                        opacity
                    ),
                    topLeft = Offset(0f, farBase - 2f),
                    size = Size(size.width, (size.height - farBase + 2f).coerceAtLeast(0f))
                )
            }
            BackdropPass.FAR -> {
                val top = farBase - farHeight
                val canvas = drawContext.canvas
                canvas.saveLayer(Rect(0f, top, size.width, farBase + 1f), backdropSkyPaint)
                try {
                    drawLayer(
                        backdrop.far, backdropShift[0], baseY = farBase, height = farHeight,
                        tint = night, haze = haze, hazeAmount = 0f,
                        opacity = opacity * backdrop.farSkyOpacity.coerceIn(0f, 1f),
                        widthScale = backdrop.widthScale,
                        bottomInset = backdrop.farBottomInset
                    )
                    // Fade the artwork into a clean sky instead of stretching a scanline.
                    // Namaľované slnko v púšti/búrke zmizne s oblohou – kotúč kreslí DayCycle.
                    val skyWash = if (backdrop.bakedSun) maxOf(0.62f, backdrop.skyWash)
                    else backdrop.skyWash
                    drawRect(
                        Brush.verticalGradient(listOf(Color.Transparent, Color.Black),
                            startY = top, endY = top + farHeight * skyWash),
                        topLeft = Offset(0f, top), size = Size(size.width, farHeight + 1f),
                        blendMode = BlendMode.DstIn
                    )
                } finally { canvas.restore() }
            }
            BackdropPass.HORIZON -> {
                // Spodok oblohy nesie vzdialené kopce. Po slnku ho kreslíme
                // znova, aby východ aj západ zašli za krajinu, nie cez ňu.
                val coverH = farHeight * backdrop.horizonCover
                if (coverH <= 0f) return
                val coverTop = farBase - coverH
                clipRect(0f, coverTop, size.width, farBase + 6f) {
                    val canvas = drawContext.canvas
                    if (backdrop.farSkyOpacity < 0.99f) {
                        canvas.saveLayer(Rect(0f, coverTop, size.width, farBase + 6f), backdropSkyPaint)
                    }
                    try {
                        drawLayer(
                            backdrop.far, backdropShift[0],
                            baseY = farBase,
                            height = farHeight,
                            tint = night, haze = haze, hazeAmount = 0f, opacity = opacity,
                            widthScale = backdrop.widthScale,
                            bottomInset = backdrop.farBottomInset
                        )
                        if (backdrop.farSkyOpacity < 0.99f) {
                            // Pri sadách s namaľovaným slnkom používame iba spodnú
                            // krajinu. Mäkká maska zabráni vodorovnému rezu oblohy.
                            drawRect(
                                Brush.verticalGradient(
                                    listOf(Color.Transparent, Color.Black),
                                    startY = coverTop,
                                    endY = coverTop + farHeight * 0.18f
                                ),
                                topLeft = Offset(0f, coverTop),
                                size = Size(size.width, farBase - coverTop + 6f),
                                blendMode = BlendMode.DstIn
                            )
                        }
                    } finally {
                        if (backdrop.farSkyOpacity < 0.99f) canvas.restore()
                    }
                }
            }
            BackdropPass.LANDSCAPE -> {
                val overlap = size.height * BACKDROP_LAYER_OVERLAP
                // Mid a near sa pripájajú k skutočnej zadnej hrane terénu.
                // Ich pôvodné ručné percentá fungovali iba na jednom pomere
                // strán; pri 21:9 ostal medzi kresbou a cestou obrovský pás.
                // Jemný midRise zachová charakter biómu, no už nemôže otvoriť
                // medzeru väčšiu než pár percent výšky obrazu.
                val midBase = BackdropLayout.midBase(
                    landscapeMidBase,
                    size.height,
                    backdrop.midRise
                )
                val nearBase = BackdropLayout.nearBase(midBase, size.height)
                // Nepriehľadný pás pod lúkou – vlas medzi mid a far nie je obloha.
                val seam = if (backdrop.midHaze > 0.12f &&
                    backdrop.hazeDay.red + backdrop.hazeDay.green + backdrop.hazeDay.blue < 1.6f
                ) {
                    // Industriál: tmavší apron pod mid, nie bledý meadow.
                    lerp(
                        backdrop.tinted(backdrop.meadowColor),
                        Color(0xFF2C2824),
                        0.62f
                    )
                } else {
                    lerp(
                        backdrop.tinted(backdrop.meadowColor),
                        backdrop.tinted(backdrop.groundColor),
                        0.45f
                    )
                }
                val fill = Color(
                    seam.red * night.red,
                    seam.green * night.green,
                    seam.blue * night.blue,
                    opacity
                )
                drawRect(
                    fill,
                    topLeft = Offset(0f, midBase - size.height * 0.04f),
                    size = Size(size.width, (size.height - midBase + size.height * 0.04f).coerceAtLeast(0f))
                )
                drawLayer(
                    backdrop.mid, backdropShift[1],
                    baseY = midBase + overlap,
                    height = size.height * BACKDROP_MID_HEIGHT * artScale + overlap,
                    tint = night, haze = haze, hazeAmount = backdrop.midHaze, opacity = opacity,
                    widthScale = backdrop.widthScale, bottomInset = backdrop.midBottomInset
                )
                drawLayer(
                    backdrop.near, backdropShift[2],
                    baseY = nearBase + overlap,
                    height = size.height * BACKDROP_NEAR_HEIGHT * artScale + overlap,
                    tint = night, haze = haze, hazeAmount = backdrop.nearHaze, opacity = opacity,
                    widthScale = backdrop.widthScale, bottomInset = backdrop.nearBottomInset
                )
            }
        }
    }

    /** Posun parallaxu sa aktualizuje raz za snímku aj počas kreslenia dvoch biomov. */
    private fun updateBackdropScroll(cam: Camera2D, carX: Float) {
        val dx = if (lastBackdropX.isNaN()) 0f else carX - lastBackdropX
        lastBackdropX = carX
        for (i in backdropShift.indices) {
            backdropShift[i] += dx * cam.ppm * BACKDROP_K[i]
        }
    }

    /** [scrolled] je nazbieraný posun vrstvy v pixeloch. */
    private fun DrawScope.drawLayer(
        image: ImageBitmap,
        scrolled: Float,
        baseY: Float,
        height: Float,
        tint: Color,
        haze: Color,
        hazeAmount: Float,
        opacity: Float,
        widthScale: Float = 1f,
        bottomInset: Float = 0f
    ) {
        if (opacity <= 0.001f) return
        // Pomer PNG s explicitnou korekciou širokej generovanej kresby.
        // Celé pixely: dve kópie sa inak stretnú na zlomku pixelu a filter
        // naberie priehľadno za okrajom – na rovnej oblohe vlasová čiara.
        val w = (height * image.width / image.height.toFloat() * widthScale).roundToInt().coerceAtLeast(1)
        if (w < 1 || height < 1f) return
        // Kotvíme posledný viditeľný riadok, nie rám PNG. Staršie 3:1 sady
        // majú pod kresbou veľký priehľadný okraj, ktorý predtým vyzeral ako
        // prázdny pás medzi pozadím a cestou.
        val contentBaseY = baseY + height * bottomInset.coerceIn(0f, 0.45f)
        val top = contentBaseY - height
        val step = w.toFloat()
        val shift = (scrolled % step + step) % step
        // 1 px prekrytie pri rovnakom kroku `w`. Starý ceil(w)+1 menil
        // mierku aj krok naraz a šev ešte zvýraznil.
        val scaleX = (w + 2) / image.width.toFloat()
        val scaleY = height / image.height.toFloat()

        var x = -shift
        while (x < size.width) {
            withTransform({
                translate(left = x, top = top)
                scale(scaleX = scaleX, scaleY = scaleY, pivot = Offset.Zero)
            }) {
                // Nearest: bilinear na okraji dlaždice naberie priehľadno a
                // spraví zvislý „nôž“ oblohy aj pri tesnom PNG spoji.
                drawImage(
                    image = image,
                    alpha = opacity,
                    colorFilter = layerTintFilter.of(tint),
                    filterQuality = FilterQuality.None
                )
                if (hazeAmount > 0.01f) {
                    // Druhý prechod farbí presne tvar siluety (SrcIn), takže vrstva
                    // vybledne do oblohy a pritom si nechá svoje odtiene.
                    drawImage(
                        image = image,
                        alpha = hazeAmount * opacity,
                        colorFilter = layerHazeFilter.of(haze),
                        filterQuality = FilterQuality.None
                    )
                }
            }
            x += step
        }
    }


    private fun collectTerrain(engine: GameEngine, ppm: Float, screenWidth: Float, screenOriginX: Float) {
        // Rovnaký rozsah ako pri kulisách – vpravo je viac sveta než vľavo.
        val worldW = screenWidth / ppm
        val screenOrigin = screenOriginX / screenWidth
        val from = engine.camera.x - worldW * screenOrigin - EDGE_MARGIN
        val to = engine.camera.x + worldW * (1f - screenOrigin) + EDGE_MARGIN
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

    private fun biomeGrassColor(biome: BiomeType) = when (biome) {
            BiomeType.RURAL -> Color(0xFF7A8F5A)
            BiomeType.INDUSTRIAL -> Color(0xFF4A463E)
            BiomeType.WASTELAND -> Color(0xFF8A7A4F)
            // V uschnutom lese je tráva vyblednutá, v živom sýta.
            BiomeType.FOREST -> Color(0xFF6E7A5E)
            BiomeType.FOREST_ALIVE -> Color(0xFF5D7A45)
            BiomeType.DESERT -> Color(0xFFC2A469)
            BiomeType.DESERT_DUSK -> Color(0xFF8A6A6B)
            BiomeType.SANDSTORM -> Color(0xFFB49767)
            BiomeType.DUST_STORM -> Color(0xFFA98A5E)
            BiomeType.ALPINE -> Color(0xFFC5D3D4)
        }

    private fun rememberLandscape(backdrop: BiomeBackdrop, next: BiomeBackdrop?, amount: Float) {
        val t = if (next == null) 0f else amount.coerceIn(0f, 1f)
        val incoming = next ?: backdrop
        sceneGround = lerp(backdrop.tinted(backdrop.groundColor), incoming.tinted(incoming.groundColor), t)
        sceneMeadow = lerp(backdrop.tinted(backdrop.meadowColor), incoming.tinted(incoming.meadowColor), t)
    }

    /** Koľko sa tráva a koruny priblížia k farbe kresby – zima a alpy ostanú čitateľné. */
    private fun landFollowAmount(blend: BiomeBlend, winterAmount: Float): Float {
        val biome = blend.dominant
        val base = when {
            biome.arid || biome == BiomeType.WASTELAND -> 0.52f
            biome == BiomeType.ALPINE -> 0.32f
            biome == BiomeType.INDUSTRIAL -> 0.58f
            else -> 0.48f
        }
        return MathX.lerp(base, 0.22f, winterAmount.coerceIn(0f, 1f))
    }

    private fun grassColor(blend: BiomeBlend, winterAmount: Float, day: Float): Color {
        val region = lerp(biomeGrassColor(blend.from), biomeGrassColor(blend.to), blend.amount)
        val land = lerp(region, sceneMeadow, landFollowAmount(blend, winterAmount))
        return shade(lerp(land, Color(0xFFDCE7EE), winterAmount.coerceIn(0f, 1f)), day)
    }

    /** Rolling ground connects the fixed parallax horizon to the actual sloping road verge. */
    private fun DrawScope.drawLandscapeApron(environment: BiomeBlend, winter: Float, day: Float) {
        val n = terrainCount
        if (n < 2) return
        val back = SCENERY_BACK_DEPTH + 0.5f
        val grass = grassColor(environment, winter, day)
        val land = if (environment.dominant == BiomeType.INDUSTRIAL) {
            lerp(shade(sceneMeadow, day), Color(0xFF2E2A26), 0.38f)
        } else shade(sceneMeadow, day)
        val material = when {
            winter > 0.55f || environment.dominant == BiomeType.ALPINE -> MaterialKind.SNOW
            environment.dominant.arid -> MaterialKind.SAND
            else -> MaterialKind.GRASS
        }
        val midJoin = if (landscapeMidBase > 1f) {
            landscapeMidBase + size.height * BACKDROP_LAYER_OVERLAP * 0.35f
        } else {
            size.height * 0.50f
        }
        for (i in 0 until n) {
            val bottom = depth.atY(groundY[i], back) + 4f
            // Continuous functions of world position, not a random sample at screen pixels.
            val wave = MathX.approxSin(terrainW[i] * 0.042f) * size.height * 0.016f +
                MathX.approxSin(terrainW[i] * 0.097f + 1.7f) * size.height * 0.007f
            apronBottom[i] = bottom
            // Apron spoji lúku s kresbou a o kúsok ju prekryje – žiadny prázdny pás.
            apronTop[i] = minOf(
                bottom - size.height * if (environment.dominant == BiomeType.INDUSTRIAL) 0.085f else 0.022f,
                midJoin + wave
            )
        }
        for (layer in 0..2) {
            val t = layer / 3f
            band.reset()
            for (i in 0 until n) {
                val ripple = MathX.approxSin(terrainW[i] * (0.055f + layer * 0.012f) + layer * 2f) *
                    size.height * 0.006f * layer
                val y = MathX.lerp(apronTop[i], apronBottom[i], t) + ripple
                band.lineToOrMove(i == 0, depth.atX(terrainX[i], back), minOf(y, apronBottom[i]))
            }
            for (i in n - 1 downTo 0) band.lineTo(depth.atX(terrainX[i], back), apronBottom[i])
            band.close()
            drawPath(band, lerp(grass, land, 0.38f - layer * 0.10f))
        }
        for (i in 0 until n - 1) {
            val x0 = depth.atX(terrainX[i], back); val x1 = depth.atX(terrainX[i + 1], back)
            materials.quad(this, material, 0.32f * (0.15f + day * 0.85f),
                x0, apronTop[i], x1, apronTop[i + 1],
                x1, apronBottom[i + 1], x0, apronBottom[i],
                terrainW[i], terrainW[i + 1], 0f, 9f, period = 7f)
        }
        if (environment.dominant == BiomeType.INDUSTRIAL) {
            val hazeTop = midJoin - size.height * 0.10f
            drawRect(
                Brush.verticalGradient(
                    listOf(Color.Transparent, land.copy(alpha = 0.28f * (0.40f + day * 0.60f))),
                    startY = hazeTop,
                    endY = midJoin + size.height * 0.14f
                ),
                topLeft = Offset(0f, hazeTop),
                size = Size(size.width, size.height * 0.24f)
            )
        }
    }

    private fun DrawScope.drawMaterialBand(kind: MaterialKind, front: Float, back: Float,
        ys: FloatArray, day: Float, opacity: Float = 0.85f, period: Float = materialPeriod(kind)) {
        val strength = opacity * (0.12f + 0.88f * day)
        for (i in 0 until terrainCount - 1) {
            materials.quad(this, kind, strength,
                depth.atX(terrainX[i], front), depth.atY(ys[i], front),
                depth.atX(terrainX[i + 1], front), depth.atY(ys[i + 1], front),
                depth.atX(terrainX[i + 1], back), depth.atY(ys[i + 1], back),
                depth.atX(terrainX[i], back), depth.atY(ys[i], back),
                terrainW[i], terrainW[i + 1], front, back, period = period)
        }
    }

    /** Dlhšia perióda = menej viditeľné opakovanie prasklín a zrna na ceste. */
    private fun materialPeriod(kind: MaterialKind): Float = when (kind) {
        MaterialKind.CRACKS -> 16f
        MaterialKind.ASPHALT, MaterialKind.CONCRETE -> 13f
        else -> 11f
    }

    /** Pôda a lúka až za cestu – podklad, na ktorom stoja kulisy. */
    private fun DrawScope.drawGround(
        segment: RoadSegment,
        environment: BiomeBlend,
        winterAmount: Float,
        day: Float
    ) {
        val n = terrainCount
        if (n < 2) return
        val grass = grassColor(environment, winterAmount, day)
        // Pod púšťou nie je hlina, ale piesok – inak by rez terénu vyzeral cudzo.
        val soilColor = shade(
            lerp(
                when {
                    winterAmount >= 0.55f -> Color(0xFF6B5340)
                    environment.dominant == BiomeType.DESERT -> Color(0xFFA9855A)
                    environment.dominant == BiomeType.SANDSTORM -> Color(0xFF9A7B52)
                    environment.dominant == BiomeType.FOREST -> Color(0xFF54402D)
                    environment.dominant == BiomeType.ALPINE -> Color(0xFF657078)
                    else -> Color(0xFF6B5340)
                },
                sceneGround,
                0.28f
            ),
            day
        )

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

        val earthMaterial = when {
            environment.dominant.arid -> MaterialKind.SAND
            environment.dominant == BiomeType.ALPINE -> MaterialKind.STONE
            else -> MaterialKind.DIRT
        }
        val cutDepth = size.height / depth.ppm + 10f
        for (i in 0 until n - 1) {
            materials.quad(this, earthMaterial, 0.78f * (0.12f + 0.88f * day),
                terrainX[i], groundY[i], terrainX[i + 1], groundY[i + 1],
                terrainX[i + 1], groundY[i + 1] + cutDepth * depth.ppm,
                terrainX[i], groundY[i] + cutDepth * depth.ppm,
                terrainW[i], terrainW[i + 1], 0f, cutDepth)
        }

        // 2) Lúka siaha až za kulisy, inak by stromy „leteli“ v prázdne.
        buildBand(n, 0f, SCENERY_BACK_DEPTH + 0.5f, groundY)
        val aridAmount = MathX.lerp(
            if (environment.from.arid) 1f else 0f,
            if (environment.to.arid) 1f else 0f,
            environment.amount
        )
        val distanceHaze = lerp(Color(0xFFAFC6D6), Color(0xFFE0C79A), aridAmount)
        val stormAmount = if (environment.dominant == BiomeType.SANDSTORM) 0.42f else 0.26f
        drawPath(band, lerp(lerp(grass, shade(sceneMeadow, day), 0.40f), shade(distanceHaze, day), stormAmount * 0.65f))
        buildBand(n, 0f, GameConfig.ROAD_DEPTH + 0.7f, groundY)
        drawPath(band, grass)
        val vergeMaterial = when {
            winterAmount > 0.55f || environment.dominant == BiomeType.ALPINE -> MaterialKind.SNOW
            environment.dominant.arid -> MaterialKind.SAND
            else -> MaterialKind.GRASS
        }
        drawMaterialBand(vergeMaterial, 0f, SCENERY_BACK_DEPTH + 0.5f, groundY, day, 0.72f)
        drawGroundTexture(n, segment, winterAmount, day, grass, soilColor)
    }

    /**
     * Materiál zeme: vrstvy v reze pôdy, tieň tesne pod hranou a trsy v tráve.
     *
     * Bez toho boli lúka aj hlina dve ploché farby a celý spodok obrazu pôsobil
     * ako výplň. Ide o jemné odtiene tej istej farby, nie o novú paletu –
     * kreslený štýl scény ostáva.
     */
    private fun DrawScope.drawGroundTexture(
        n: Int,
        segment: RoadSegment,
        winterAmount: Float,
        day: Float,
        grass: Color,
        soil: Color
    ) {
        if (n < 2) return
        val ppm = depth.ppm

        // Tmavý pruh tesne pod hranou terénu – hrana potom nie je len rez.
        edge.reset()
        for (i in 0 until n) {
            val x = depth.atX(terrainX[i], 0f)
            val y = depth.atY(groundY[i], 0f) + ppm * 0.09f
            if (i == 0) edge.moveTo(x, y) else edge.lineTo(x, y)
        }
        drawPath(
            edge,
            lerp(soil, Color.Black, 0.28f).copy(alpha = 0.55f),
            style = Stroke(width = (ppm * 0.16f).coerceAtLeast(2f))
        )

        // Vrstvy podložia – hlbšie tmavšie, s miernym zvlnením podľa terénu.
        for (layer in 1..3) {
            val drop = ppm * (0.55f + layer * 0.75f)
            edge.reset()
            for (i in 0 until n) {
                val wob = MathX.approxSin(terrainW[i] * 0.35f + layer * 2.1f) * ppm * 0.10f
                val x = depth.atX(terrainX[i], 0f)
                val y = depth.atY(groundY[i], 0f) + drop + wob
                if (i == 0) edge.moveTo(x, y) else edge.lineTo(x, y)
            }
            drawPath(
                edge,
                lerp(soil, Color.Black, 0.10f + layer * 0.07f).copy(alpha = 0.40f),
                style = Stroke(width = (ppm * 0.22f).coerceAtLeast(2f))
            )
        }

        // Zrno v hline – riedke svetlé kamienky, deterministicky z polohy.
        val grit = lerp(soil, Color.White, 0.22f)
        var i = 0
        while (i < n) {
            val h = MathX.hash01(terrainW[i].toInt(), 3313)
            if (h < 0.22f) {
                val x = depth.atX(terrainX[i], 0f)
                val y = depth.atY(groundY[i], 0f) + ppm * (0.5f + h * 9f)
                if (y < size.height) {
                    drawCircle(grit.copy(alpha = 0.30f), ppm * (0.02f + h * 0.05f), Offset(x, y))
                }
            }
            i += 2
        }

        // Tráva na hrane – krátke ťahy, ktoré rozbijú rovnú líniu.
        if (winterAmount < 0.55f) {
            val blade = lerp(grass, Color.Black, 0.22f)
            var k = 0
            while (k < n) {
                val h = MathX.hash01(terrainW[k].toInt(), 7717)
                if (h < 0.35f) {
                    val x = depth.atX(terrainX[k], 0f)
                    val y = depth.atY(groundY[k], 0f)
                    drawLine(
                        blade.copy(alpha = 0.55f),
                        Offset(x, y + ppm * 0.02f),
                        Offset(x + (h - 0.17f) * ppm * 0.6f, y - ppm * (0.10f + h * 0.30f)),
                        strokeWidth = (ppm * 0.03f).coerceAtLeast(1f),
                        cap = StrokeCap.Round
                    )
                }
                k += 2
            }
        }
    }


    /** Voda pod mostom – lacný 2.5D rez, len vo viditeľnom okne. */
    private fun DrawScope.drawBridgeWaterDeck(engine: GameEngine, day: Float) {
        val seg = engine.segment
        if (terrainCount < 2) return
        val frontDepth = GameConfig.VERGE_DEPTH
        val backDepth = SCENERY_BACK_DEPTH + 0.5f
        val dropBelowRoad = 1.50f
        val surface = shade(Color(0xFF5A96A4), day)
        val wall = shade(Color(0xFF3F7C88), day)
        val wallDark = shade(Color(0xFF326673), day)
        val visFrom = terrainW[0]
        val visTo = terrainW[terrainCount - 1]

        for (sec in seg.sections) {
            if (sec.feature != RoadFeature.BRIDGE || sec.length < 4f) continue
            val bridgeStart = seg.worldOrigin + sec.start
            val bridgeEnd = seg.worldOrigin + sec.end
            // Len úsek na obrazovke – celý dlhý most nesmie ťahať path-y.
            val startW = maxOf(bridgeStart, visFrom)
            val endW = minOf(bridgeEnd, visTo)
            if (endW - startW < 1.5f) continue

            val steps = (((endW - startW) * 0.4f).toInt() + 1).coerceIn(6, 14)
            var lowestRoad = Float.POSITIVE_INFINITY
            for (k in 0..steps) {
                val wx = MathX.lerp(startW, endW, k / steps.toFloat())
                bridgeWx[k] = wx
                bridgeGround[k] = seg.groundAtWorld(wx)
                lowestRoad = minOf(lowestRoad, seg.heightAtWorld(wx))
            }
            if (!lowestRoad.isFinite()) continue
            val waterLevel = lowestRoad - dropBelowRoad

            var firstWet = -1
            var lastWet = -1
            for (k in 0..steps) {
                if (bridgeGround[k] < waterLevel) {
                    if (firstWet < 0) firstWet = k
                    lastWet = k
                }
            }
            if (firstWet < 0 || lastWet <= firstWet) continue
            val i0 = (firstWet - 1).coerceAtLeast(0)
            val i1 = (lastWet + 1).coerceAtMost(steps)

            fun x(wx: Float, d: Float) = depth.atX(depth.frontX(wx), d)
            val middleDepth = (frontDepth + backDepth) * 0.5f
            val surfaceScreenY = depth.atY(depth.frontY(waterLevel), middleDepth)
            fun bottomY(ground: Float, d: Float): Float = maxOf(
                surfaceScreenY,
                depth.atY(depth.frontY(ground), d)
            )

            // Iba zadný a predný rez – žiadne desiatky depth slices.
            for (pass in 0..1) {
                val d = if (pass == 0) backDepth else frontDepth
                val fill = if (pass == 0) wallDark else wall
                band.reset()
                for (k in i0..i1) {
                    val px = x(bridgeWx[k], d)
                    if (k == i0) band.moveTo(px, surfaceScreenY) else band.lineTo(px, surfaceScreenY)
                }
                for (k in i1 downTo i0) {
                    band.lineTo(x(bridgeWx[k], d), bottomY(bridgeGround[k], d))
                }
                band.close()
                drawPath(band, fill.copy(alpha = 0.96f))
            }

            val surfaceLeft = minOf(x(bridgeWx[i0], frontDepth), x(bridgeWx[i0], backDepth))
            val surfaceRight = maxOf(x(bridgeWx[i1], frontDepth), x(bridgeWx[i1], backDepth))
            val surfaceHeight = (depth.ppm * 0.12f).coerceIn(4f, 9f)
            drawRect(
                surface,
                topLeft = Offset(surfaceLeft, surfaceScreenY),
                size = Size((surfaceRight - surfaceLeft).coerceAtLeast(1f), surfaceHeight)
            )
            drawLine(
                shade(Color(0xFFB8D9DD), day).copy(alpha = 0.55f),
                Offset(surfaceLeft, surfaceScreenY),
                Offset(surfaceRight, surfaceScreenY),
                strokeWidth = (depth.ppm * 0.025f).coerceIn(1f, 2f),
                cap = StrokeCap.Round
            )
        }
    }

    /** Starší objemová voda ponechaná ako pomocná implementácia pre regresiu. */
    private fun DrawScope.drawBridgeWater(engine: GameEngine, day: Float) {
        val seg = engine.segment
        val waterFrontDepth = GameConfig.VERGE_DEPTH
        val waterBackDepth = SCENERY_BACK_DEPTH + 0.5f
        val waterSurfaceDepth = GameConfig.VERGE_DEPTH + 0.44f
        val surface = shade(Color(0xFF66909A), day)
        val deep = shade(Color(0xFF416A75), day)
        val shore = shade(Color(0xFFB2C8C5), day)
        val glint = shade(Color(0xFFD0E0DB), day).copy(alpha = 0.38f)

        for (sec in seg.sections) {
            if (sec.feature != RoadFeature.BRIDGE || sec.length < 4f) continue
            val startW = seg.worldOrigin + sec.start
            val endW = seg.worldOrigin + sec.end
            if (endW < terrainW[0] || startW > terrainW[terrainCount - 1]) continue

            // Voda je rez zaplavenej jamy: horná hrana je vodorovná a spodná
            // presne kopíruje dno rokliny. Nevznikne tak samostatný lichobežnik.
            val samples = 72
            var bottom = Float.POSITIVE_INFINITY
            var bottomIndex = 0
            for (k in 0..samples) {
                val wx = MathX.lerp(startW, endW, k / samples.toFloat())
                val ground = seg.groundAtWorld(wx)
                if (ground < bottom) {
                    bottom = ground
                    bottomIndex = k
                }
            }
            if (!bottom.isFinite()) continue
            // Jama má byť z väčšej časti pod vodou, nie iba s malou mlákou na
            // dne. Hladinu vztiahneme na oba brehy, aby sa zachovala správna
            // výška aj pri úsekoch s miernym pozdĺžnym sklonom.
            val rimLevel = minOf(seg.groundAtWorld(startW), seg.groundAtWorld(endW))
            // Hladina zostáva v skutočnej jame; rozlievanie riešime v hĺbke
            // terénu, nie predlžovaním vody po svetovej osi jazdy.
            val level = MathX.lerp(bottom, rimLevel, 0.82f)

            // Nájdeme oba priesečníky vodnej hladiny so svahom jamy.
            var from = startW
            for (k in bottomIndex downTo 1) {
                val dryW = MathX.lerp(startW, endW, (k - 1) / samples.toFloat())
                val wetW = MathX.lerp(startW, endW, k / samples.toFloat())
                val dryY = seg.groundAtWorld(dryW)
                val wetY = seg.groundAtWorld(wetW)
                if (dryY > level && wetY <= level) {
                    val t = ((dryY - level) / (dryY - wetY)).coerceIn(0f, 1f)
                    from = MathX.lerp(dryW, wetW, t)
                    break
                }
            }
            var to = endW
            for (k in bottomIndex until samples) {
                val wetW = MathX.lerp(startW, endW, k / samples.toFloat())
                val dryW = MathX.lerp(startW, endW, (k + 1) / samples.toFloat())
                val wetY = seg.groundAtWorld(wetW)
                val dryY = seg.groundAtWorld(dryW)
                if (wetY <= level && dryY > level) {
                    val t = ((level - wetY) / (dryY - wetY)).coerceIn(0f, 1f)
                    to = MathX.lerp(wetW, dryW, t)
                    break
                }
            }

            if (to - from < 2.5f) continue

            val waterBottom = bottom

            // Voda vyplní celý rez jamy od kulís po prednú hranu terénu.
            // Jedna pevná hĺbka vytvárala modrú stenu pred jamou.
            val x0 = depth.atX(depth.frontX(from), waterSurfaceDepth)
            val x1 = depth.atX(depth.frontX(to), waterSurfaceDepth)
            val waterY = depth.atY(depth.frontY(level), waterSurfaceDepth)
            val bottomY = depth.atY(depth.frontY(waterBottom), waterSurfaceDepth)
            val contourSteps = (((to - from) * 1.5f).toInt() + 1).coerceIn(12, 72)

            band.reset()
            // Zadná horná hrana hladiny.
            for (k in 0..contourSteps) {
                val wx = MathX.lerp(from, to, k / contourSteps.toFloat())
                val x = depth.atX(depth.frontX(wx), waterBackDepth)
                val y = depth.atY(depth.frontY(level), waterBackDepth)
                if (k == 0) band.moveTo(x, y) else band.lineTo(x, y)
            }
            // Zadné dno, predné dno a predná horná hrana uzavrú celý objem vody.
            for (k in contourSteps downTo 0) {
                val wx = MathX.lerp(from, to, k / contourSteps.toFloat())
                band.lineTo(
                    depth.atX(depth.frontX(wx), waterBackDepth),
                    depth.atY(depth.frontY(seg.groundAtWorld(wx)), waterBackDepth)
                )
            }
            for (k in 0..contourSteps) {
                val wx = MathX.lerp(from, to, k / contourSteps.toFloat())
                band.lineTo(
                    depth.atX(depth.frontX(wx), waterFrontDepth),
                    depth.atY(depth.frontY(seg.groundAtWorld(wx)), waterFrontDepth)
                )
            }
            for (k in contourSteps downTo 0) {
                val wx = MathX.lerp(from, to, k / contourSteps.toFloat())
                band.lineTo(
                    depth.atX(depth.frontX(wx), waterFrontDepth),
                    depth.atY(depth.frontY(level), waterFrontDepth)
                )
            }
            band.close()
            drawPath(
                band,
                Brush.verticalGradient(
                    colors = listOf(surface.copy(alpha = 1f), deep.copy(alpha = 1f)),
                    startY = waterY,
                    endY = bottomY
                )
            )

            // Ostrá vodorovná hladina uzavrie vodu medzi brehmi.
            drawLine(
                shore.copy(alpha = 0.55f),
                Offset(x0, waterY),
                Offset(x1, waterY),
                strokeWidth = (depth.ppm * 0.035f).coerceIn(1f, 2.2f),
                cap = StrokeCap.Round
            )

            val waterHeight = (bottomY - waterY).coerceAtLeast(1f)
            for (k in 1..3) {
                val centre = MathX.lerp(x0, x1, 0.28f + k * 0.15f)
                val half = (x1 - x0) * (0.025f + k * 0.006f)
                drawLine(
                    glint,
                    Offset(centre - half, waterY + waterHeight * (0.18f + k * 0.12f)),
                    Offset(centre + half, waterY + waterHeight * (0.18f + k * 0.12f)),
                    strokeWidth = (depth.ppm * 0.020f).coerceIn(0.8f, 1.5f),
                    cap = StrokeCap.Round
                )
            }
        }
    }

    /**
     * Nepriehľadný vodný rez medzi spodkom mostovky a dnom rokliny.
     * Kreslí sa po ceste, ale jeho horná hrana je pod mostovkou, preto cestu
     * neprekryje a zároveň odstráni zelený pás medzi hĺbkovými vrstvami vody.
     */
    private fun DrawScope.drawBridgeWaterUnderDeck(engine: GameEngine, day: Float) {
        val seg = engine.segment
        val waterFrontDepth = GameConfig.VERGE_DEPTH
        val waterBackDepth = SCENERY_BACK_DEPTH + 0.5f
        val surface = shade(Color(0xFF5D8994), day)
        val deep = shade(Color(0xFF3F6873), day)

        for (sec in seg.sections) {
            if (sec.feature != RoadFeature.BRIDGE || sec.length < 4f) continue
            val startW = seg.worldOrigin + sec.start
            val endW = seg.worldOrigin + sec.end
            if (endW < terrainW[0] || startW > terrainW[terrainCount - 1]) continue

            val steps = (((endW - startW) * 1.5f).toInt() + 1).coerceIn(12, 72)
            val deckUnderside = 0.58f
            fun topY(wx: Float, d: Float) = depth.atY(
                depth.frontY(seg.heightAtWorld(wx) - deckUnderside), d
            )
            fun bottomY(wx: Float, d: Float) = depth.atY(
                depth.frontY(seg.groundAtWorld(wx)), d
            )
            fun screenX(wx: Float, d: Float) = depth.atX(depth.frontX(wx), d)

            band.reset()
            // Zadná horná hrana.
            for (k in 0..steps) {
                val wx = MathX.lerp(startW, endW, k / steps.toFloat())
                val x = screenX(wx, waterBackDepth)
                val y = topY(wx, waterBackDepth)
                if (k == 0) band.moveTo(x, y) else band.lineTo(x, y)
            }
            // Zadné dno, predné dno a predná horná hrana.
            for (k in steps downTo 0) {
                val wx = MathX.lerp(startW, endW, k / steps.toFloat())
                band.lineTo(screenX(wx, waterBackDepth), bottomY(wx, waterBackDepth))
            }
            for (k in 0..steps) {
                val wx = MathX.lerp(startW, endW, k / steps.toFloat())
                band.lineTo(screenX(wx, waterFrontDepth), bottomY(wx, waterFrontDepth))
            }
            for (k in steps downTo 0) {
                val wx = MathX.lerp(startW, endW, k / steps.toFloat())
                band.lineTo(screenX(wx, waterFrontDepth), topY(wx, waterFrontDepth))
            }
            band.close()

            drawPath(
                band,
                Brush.verticalGradient(
                    colors = listOf(surface, deep),
                    startY = topY((startW + endW) * 0.5f, waterFrontDepth),
                    endY = bottomY((startW + endW) * 0.5f, waterFrontDepth)
                )
            )
        }
    }

    /**
     * Most sa kreslí zo svetových hraníc úseku, nie zo vzoriek terénu.
     *
     * Pri hľadaní rozpätia skenovaním vzoriek sa konce mosta lepili na to,
     * kam práve padla vzorka – a keďže sa vzorky s kamerou plynule posúvajú,
     * spodná konštrukcia pri okraji obrazovky preblikávala. Rovnaká chyba
     * ako predtým pri mlákach a textúrach vozovky.
     */
    private fun DrawScope.drawBridges(engine: GameEngine, day: Float) {
        val seg = engine.segment
        if (terrainCount < 2) return
        val fromX = terrainW[0]
        val toX = terrainW[terrainCount - 1]
        val d = GameConfig.ROAD_DEPTH * 0.55f
        val ppm = depth.ppm
        val thickness = 0.52f * ppm
        val deckColor = shade(Color(0xFF5A5044), day)
        val deckShadow = shade(Color(0xFF3B342C), day)
        val pillarColor = shade(Color(0xFF6E6455), day)
        val pillarLit = shade(Color(0xFF837767), day)

        for (sec in seg.sections) {
            if (sec.feature != RoadFeature.BRIDGE) continue
            val startW = seg.worldOrigin + sec.start
            val endW = seg.worldOrigin + sec.end
            if (endW < fromX || startW > toX) continue
            // Len viditeľný pás – celý dlhý most inak kreslí stovky pilierov/stĺpikov.
            val drawFrom = maxOf(startW, fromX - PILLAR_SPACING_M)
            val drawTo = minOf(endW, toX + PILLAR_SPACING_M)
            if (drawTo - drawFrom < 0.5f) continue

            fun deckY(wx: Float) = depth.atY(depth.frontY(seg.heightAtWorld(wx)), d)
            fun groundYAt(wx: Float) = depth.atY(depth.frontY(seg.groundAtWorld(wx)), d)
            fun sx(wx: Float) = depth.atX(depth.frontX(wx), d)

            // Piliere v pevnom rozostupe, zarovnané na svetovú mriežku – tak
            // stoja stále na tom istom mieste, nech je kamera kdekoľvek.
            val firstPillar = MathX.floorDiv(drawFrom, PILLAR_SPACING_M) + 1
            val lastPillar = MathX.floorDiv(drawTo, PILLAR_SPACING_M)
            val pillarXs = ArrayList<Float>(8)
            for (p in firstPillar..lastPillar) {
                val wx = p * PILLAR_SPACING_M
                if (wx <= startW || wx >= endW) continue
                pillarXs += wx
            }

            // Vzpery medzi susednými piliermi.
            for (k in 0 until pillarXs.size - 1) {
                val ax = sx(pillarXs[k])
                val bx = sx(pillarXs[k + 1])
                val aTop = deckY(pillarXs[k]) + thickness
                val bTop = deckY(pillarXs[k + 1]) + thickness
                val aBot = groundYAt(pillarXs[k])
                val bBot = groundYAt(pillarXs[k + 1])
                if (aBot <= aTop || bBot <= bTop) continue
                val w = (0.08f * ppm).coerceAtLeast(1f)
                val brace = pillarColor.copy(alpha = 0.7f)
                drawLine(brace, Offset(ax, aTop), Offset(bx, bBot), strokeWidth = w)
                drawLine(brace, Offset(ax, aBot), Offset(bx, bTop), strokeWidth = w)
                // Vodorovná spojnica v polovici výšky – priehradová konštrukcia.
                val midA = (aTop + aBot) * 0.5f
                val midB = (bTop + bBot) * 0.5f
                drawLine(brace, Offset(ax, midA), Offset(bx, midB), strokeWidth = w * 0.8f)
            }

            pillarXs.forEach { wx ->
                val x = sx(wx)
                val top = deckY(wx) + thickness
                val bottom = groundYAt(wx)
                if (bottom <= top) return@forEach
                val pw = 0.34f * ppm
                drawRect(
                    pillarColor,
                    topLeft = Offset(x - pw * 0.5f, top),
                    size = Size(pw, bottom - top)
                )
                // Osvetlená hrana piliera – bez nej je to len šedý obdĺžnik.
                drawRect(
                    pillarLit.copy(alpha = 0.7f),
                    topLeft = Offset(x - pw * 0.5f, top),
                    size = Size(pw * 0.26f, bottom - top)
                )
                drawRect(
                    shade(Color(0xFF544D41), day),
                    topLeft = Offset(x - pw * 0.95f, bottom - 0.14f * ppm),
                    size = Size(pw * 1.9f, 0.22f * ppm)
                )
            }

            // Doska mostovky.
            buildBandWorldDeck(seg, drawFrom, drawTo, d, thickness)
            drawPath(band, deckColor)
            buildEdgeWorldAt(seg, drawFrom, drawTo, d, thickness)
            drawPath(edge, deckShadow, style = Stroke(width = (0.12f * ppm).coerceAtLeast(1.5f)))

            // Zábradlie na bližšej strane.
            val railD = GameConfig.VERGE_DEPTH * 0.35f
            val railH = 0.95f * ppm
            val railCol = shade(Color(0xFF8A8578), day)
            buildEdgeWorldAt(seg, drawFrom, drawTo, railD, -railH)
            drawPath(edge, railCol, style = Stroke(width = (0.10f * ppm).coerceAtLeast(1.5f)))
            val postFirst = MathX.floorDiv(drawFrom, RAIL_POST_SPACING_M) + 1
            val postLast = MathX.floorDiv(drawTo, RAIL_POST_SPACING_M)
            for (p in postFirst..postLast) {
                val wx = p * RAIL_POST_SPACING_M
                if (wx < startW || wx > endW) continue
                val x = depth.atX(depth.frontX(wx), railD)
                val y = depth.atY(depth.frontY(seg.heightAtWorld(wx)), railD)
                drawLine(
                    railCol,
                    Offset(x, y - railH), Offset(x, y),
                    strokeWidth = (0.09f * ppm).coerceAtLeast(1.5f)
                )
            }
        }
    }

    /** Pás mostovky: horná hrana po vozovke, spodná o [thickness] nižšie. */
    private fun buildBandWorldDeck(
        segment: RoadSegment,
        fromX: Float,
        toX: Float,
        d: Float,
        thickness: Float
    ) {
        val steps = (((toX - fromX) / 1.2f).toInt() + 1).coerceIn(2, 48)
        val step = (toX - fromX) / steps
        band.reset()
        for (k in 0..steps) {
            val wx = fromX + step * k
            val x = depth.atX(depth.frontX(wx), d)
            val y = depth.atY(depth.frontY(segment.heightAtWorld(wx)), d)
            band.lineToOrMove(k == 0, x, y)
        }
        for (k in steps downTo 0) {
            val wx = fromX + step * k
            val x = depth.atX(depth.frontX(wx), d)
            val y = depth.atY(depth.frontY(segment.heightAtWorld(wx)), d) + thickness
            band.lineTo(x, y)
        }
        band.close()
    }

    /** Čiara po vozovke posunutá o [offsetY] – podhľad dosky alebo zábradlie. */
    private fun buildEdgeWorldAt(
        segment: RoadSegment,
        fromX: Float,
        toX: Float,
        d: Float,
        offsetY: Float
    ) {
        val steps = (((toX - fromX) / 1.2f).toInt() + 1).coerceIn(2, 48)
        val step = (toX - fromX) / steps
        edge.reset()
        for (k in 0..steps) {
            val wx = fromX + step * k
            val x = depth.atX(depth.frontX(wx), d)
            val y = depth.atY(depth.frontY(segment.heightAtWorld(wx)), d) + offsetY
            edge.lineToOrMove(k == 0, x, y)
        }
    }

    private fun DrawScope.drawRoadSurface(segment: RoadSegment, day: Float) {
        val n = terrainCount
        if (n < 2) return
        val sampleX = terrainW[n / 2]
        val roadBlend = segment.biomeBlendAtWorld(sampleX)
        val nextWinter = segment.choices.firstOrNull()?.plan?.paving?.winter == true
        val winter = MathX.lerp(
            if (segment.paving.winter) 1f else 0f,
            if (nextWinter) 1f else 0f,
            roadBlend.amount
        )
        val grass = grassColor(roadBlend, winter, day)
        val nextPaving = segment.choices.firstOrNull()?.plan?.paving ?: segment.paving
        val road = shade(
            lerp(pavingColor(segment.paving), pavingColor(nextPaving), roadBlend.amount),
            day
        )

        buildBand(n, 0f, 0.14f, terrainY)
        drawPath(band, lerp(grass, Color.White, 0.10f))

        // 3) Krajnica + cesta.
        buildBand(n, GameConfig.VERGE_DEPTH * 0.6f, GameConfig.ROAD_DEPTH + 0.05f, terrainY)
        drawPath(band, lerp(shade(Color(0xFF7C7060), day), shade(sceneGround, day), 0.35f).copy(alpha = 0.75f))
        buildBand(n, GameConfig.VERGE_DEPTH, GameConfig.ROAD_DEPTH - 0.15f, terrainY)
        drawPath(band, road)
        buildBand(n, GameConfig.VERGE_DEPTH + 0.25f, GameConfig.ROAD_DEPTH - 0.45f, terrainY)
        drawPath(band, Color(road.red * 0.92f, road.green * 0.92f, road.blue * 0.92f))

        drawMaterialBand(MaterialKind.paving(segment.paving), GameConfig.VERGE_DEPTH,
            GameConfig.ROAD_DEPTH - 0.15f, terrainY, day, 0.95f * (1f - roadBlend.amount))
        if (roadBlend.amount > 0.001f) {
            drawMaterialBand(MaterialKind.paving(nextPaving), GameConfig.VERGE_DEPTH,
                GameConfig.ROAD_DEPTH - 0.15f, terrainY, day, 0.95f * roadBlend.amount)
        }

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
            grass.copy(alpha = 0.55f),
            style = Stroke(width = (0.07f * depth.ppm).coerceAtLeast(1.5f))
        )
    }

    private fun pavingColor(paving: RoadPaving): Color {
        val base = when (paving) {
            RoadPaving.ASPHALT -> Color(0xFF3E3E42)
            RoadPaving.CRACKED -> Color(0xFF4A4844)
            RoadPaving.CONCRETE -> Color(0xFF6E6C66)
            RoadPaving.DIRT -> Color(0xFF6A5340)
            RoadPaving.GRAVEL_ROAD -> Color(0xFF6E675C)
            RoadPaving.SAND_TRACK -> Color(0xFFB49A6A)
            RoadPaving.SNOW -> Color(0xFFE6EDF2)
            RoadPaving.PACKED_SNOW -> Color(0xFFCBD7DE)
        }
        val follow = when (paving) {
            RoadPaving.DIRT, RoadPaving.GRAVEL_ROAD, RoadPaving.SAND_TRACK -> 0.40f
            RoadPaving.SNOW, RoadPaving.PACKED_SNOW -> 0.10f
            else -> 0.16f
        }
        return lerp(base, sceneGround, follow)
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
            // Pás sa stavia z presných svetových hraníc naplaveniny, nie z toho,
            // kam práve padli vzorky terénu. Tie sa s kamerou plynule posúvajú,
            // takže začiatok a koniec mláky poskakovali o celý krok vzorkovania.
            val startW = segment.worldOrigin + patch.start
            val endW = segment.worldOrigin + patch.end
            if (endW - startW < 0.2f) continue

            buildBandWorld(
                segment, startW, endW,
                GameConfig.VERGE_DEPTH, GameConfig.ROAD_DEPTH - 0.15f
            )
            val patchAlpha = when (patch.surface) {
                RoadSurface.WATER -> 0.72f
                RoadSurface.MUD -> 1f
                else -> 0.92f
            }
            drawPath(band, shade(base, day).copy(alpha = patchAlpha))
            clipPath(band) {
                drawMaterialBand(MaterialKind.surface(patch.surface), GameConfig.VERGE_DEPTH,
                    GameConfig.ROAD_DEPTH - 0.15f, terrainY, day)
            }

            // Textúra – vlnky na vode, zrno v piesku, kamienky v štrku, hrudy v bahne.
            //
            // Kreslí sa po pevných bunkách sveta, nie po vzorkách terénu.
            // Vzorky sa s kamerou plynule posúvajú, takže hash z ich polohy
            // sa menil každú snímku a naplaveniny preblikávali.
            val col = shade(accent, day)
            val firstCell = MathX.floorDiv(segment.worldOrigin + patch.start, PATCH_CELL)
            val lastCell = MathX.floorDiv(segment.worldOrigin + patch.end, PATCH_CELL)
            for (cell in firstCell..lastCell) {
                val wx = cell * PATCH_CELL
                val h = MathX.hash01(cell, patch.surface.ordinal * 977)
                val d = GameConfig.VERGE_DEPTH + 0.3f + h * (GameConfig.ROAD_DEPTH - GameConfig.VERGE_DEPTH - 0.7f)
                val x = depth.atX(depth.frontX(wx), d)
                if (x < -60f || x > size.width + 60f) continue
                val y = depth.atY(depth.frontY(segment.heightAtWorld(wx)), d)
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
                    // Bahno je súvislá vrstva cez celý pás. Samostatné ovály
                    // vyzerali ako kaluže, hoci fyzika už počítala čisté bahno.
                    RoadSurface.MUD -> Unit
                    else -> drawOval(
                        col.copy(alpha = 0.55f),
                        topLeft = Offset(x - depth.ppm * (0.15f + h * 0.2f), y - depth.ppm * 0.06f),
                        size = Size(depth.ppm * (0.3f + h * 0.4f), depth.ppm * 0.13f)
                    )
                }
            }

            // Okraj naplaveniny – aby splynutie s cestou nebolo ostrý rez.
            buildEdgeWorld(segment, startW, endW, GameConfig.VERGE_DEPTH + 0.1f)
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
        if (n < 2) return
        // Rovnako ako pri naplaveninách: pevné bunky sveta, aby textúra
        // nezávisela od toho, kam práve padli vzorky terénu.
        val first = MathX.floorDiv(terrainW[0], PATCH_CELL)
        val last = MathX.floorDiv(terrainW[n - 1], PATCH_CELL)
        for (cell in first..last) {
            val wx = cell * PATCH_CELL
            val gy = segment.heightAtWorld(wx)
            when (paving) {
                RoadPaving.CONCRETE -> {
                    // Škára každé 4 m naprieč celou vozovkou.
                    if (MathX.floorDiv(wx, 4f) != MathX.floorDiv(wx - PATCH_CELL, 4f)) {
                        val fx = depth.frontX(wx)
                        val fy = depth.frontY(gy)
                        drawLine(
                            col.copy(alpha = 0.55f),
                            Offset(depth.atX(fx, GameConfig.VERGE_DEPTH), depth.atY(fy, GameConfig.VERGE_DEPTH)),
                            Offset(depth.atX(fx, GameConfig.ROAD_DEPTH - 0.2f), depth.atY(fy, GameConfig.ROAD_DEPTH - 0.2f)),
                            strokeWidth = (0.06f * depth.ppm).coerceAtLeast(1f)
                        )
                    }
                }
                else -> {
                    val h = MathX.hash01(cell, paving.ordinal * 613)
                    if (h < 0.30f) {
                        val d = GameConfig.VERGE_DEPTH + 0.25f +
                            h * (GameConfig.ROAD_DEPTH - GameConfig.VERGE_DEPTH - 0.6f) * 3f
                        val x = depth.atX(depth.frontX(wx), d)
                        if (x < -60f || x > size.width + 60f) continue
                        val y = depth.atY(depth.frontY(gy), d)
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
        }
    }

    /**
     * Výtlky kreslíme len tam, kde je cesta naozaj rozbitá – inak z toho boli
     * rušivé bodky po celej trati.
     */
    private fun DrawScope.drawPotholes(n: Int, day: Float, segment: RoadSegment) {
        if (n < 2) return
        val rim = shade(Color(0xFF241D16), day).copy(alpha = 0.76f)
        val water = shade(Color(0xFF2B7084), day).copy(alpha = 0.94f)
        val waterHighlight = shade(Color(0xFF8DCCD5), day).copy(alpha = 0.62f)
        // Výtlk je diera v ceste, nie blikajúca škvrna – drží sa svojej bunky.
        val first = MathX.floorDiv(terrainW[0], POTHOLE_CELL)
        val last = MathX.floorDiv(terrainW[n - 1], POTHOLE_CELL)
        for (cell in first..last) {
            val wx = cell * POTHOLE_CELL
            if (segment.bumpinessAtLocal(wx - segment.worldOrigin) < 0.5f) continue
            val h = MathX.hash01(cell, 6421)
            if (h > 0.30f) continue
            val d = GameConfig.VERGE_DEPTH + 0.4f + MathX.hash01(cell, 77) * 1.6f
            val x = depth.atX(depth.frontX(wx), d)
            if (x < -60f || x > size.width + 60f) continue
            val y = depth.atY(depth.frontY(segment.heightAtWorld(wx)), d)
            val r = depth.ppm * (0.20f + h * 0.55f)
            val waterR = r * 0.83f

            // Tmavý okraj nechá jamu čitateľnú aj za dňa, no jej vnútro je
            // teraz takmer celé vyplnené vodou a hladina sedí vyššie v jame.
            drawOval(
                rim,
                topLeft = Offset(x - r, y - r * 0.35f),
                size = Size(r * 2f, r * 0.7f)
            )
            drawOval(
                water,
                topLeft = Offset(x - waterR, y - waterR * 0.40f),
                size = Size(waterR * 2f, waterR * 0.74f)
            )
            // Krátky odlesk dá vode svetlú hornú hranu namiesto plochej škvrny.
            drawOval(
                waterHighlight,
                topLeft = Offset(x - waterR * 0.62f, y - waterR * 0.27f),
                size = Size(waterR * 1.24f, waterR * 0.12f)
            )
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

    /**
     * Pás medzi dvoma svetovými súradnicami. Nezávisí od toho, kam padli
     * vzorky terénu, takže okraje naplaveniny držia svoje miesto vo svete
     * a pri jazde nepreblikávajú.
     */
    private fun buildBandWorld(
        segment: RoadSegment,
        fromX: Float,
        toX: Float,
        frontDepth: Float,
        backDepth: Float
    ) {
        val steps = (((toX - fromX) / PATCH_CELL).toInt() + 1).coerceIn(2, 96)
        val step = (toX - fromX) / steps
        band.reset()
        for (k in 0..steps) {
            val wx = fromX + step * k
            val fx = depth.frontX(wx)
            val fy = depth.frontY(segment.heightAtWorld(wx))
            band.lineToOrMove(k == 0, depth.atX(fx, frontDepth), depth.atY(fy, frontDepth))
        }
        for (k in steps downTo 0) {
            val wx = fromX + step * k
            val fx = depth.frontX(wx)
            val fy = depth.frontY(segment.heightAtWorld(wx))
            band.lineTo(depth.atX(fx, backDepth), depth.atY(fy, backDepth))
        }
        band.close()
    }

    private fun buildEdgeWorld(segment: RoadSegment, fromX: Float, toX: Float, d: Float) {
        val steps = (((toX - fromX) / PATCH_CELL).toInt() + 1).coerceIn(2, 96)
        val step = (toX - fromX) / steps
        edge.reset()
        for (k in 0..steps) {
            val wx = fromX + step * k
            val fx = depth.frontX(wx)
            val fy = depth.frontY(segment.heightAtWorld(wx))
            edge.lineToOrMove(k == 0, depth.atX(fx, d), depth.atY(fy, d))
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
            val col = Color(choice.style.accentArgb)
                .copy(alpha = if (dimmed) 0.2f else if (picked) 0.9f else 0.5f)

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
            val col = Color(choice.style.accentArgb)
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
        val worldW = size.width / engine.camera.ppm
        val screenOrigin = depth.halfW / size.width
        for (b in seg.buildings) {
            val wx = seg.worldOrigin + b.localX
            // Rovnaký dosah ako kulisy – budova sa nesmie zjaviť v zábere.
            if (wx < engine.camera.x - worldW * screenOrigin - EDGE_MARGIN ||
                wx > engine.camera.x + worldW * (1f - screenOrigin) + EDGE_MARGIN
            ) continue
            val ground = seg.heightAtWorld(wx)
            val d = GameConfig.ROAD_DEPTH + 1.5f
            val px = depth.atX(depth.frontX(wx), d)
            val py = depth.atY(depth.frontY(ground), d)
            val s = depth.ppm * (1f - depth.perspectiveT(d))
            val slopeDeg = roadSlopeDeg(wx, d, seg)
            if (b.type == BuildingType.WRECK) {
                with(scenery) {
                    drawParkedWreck(px, py, s, day, (b.id and 0x7fffffffL).toInt(), slopeDeg)
                }
                if (near === b) with(buildings) { drawSearchMarker(px, py, s, b.looted) }
            } else {
                with(buildings) { drawBuilding(b, px, py, s, day, near === b, slopeDeg,
                    seg.biomeBlendAtWorld(wx).dominant, if (seg.paving.winter) 1f else 0f) }
            }
        }
    }

    /** Sklon vozovky v obrazovkových stupňoch – budovy a efekty sedia na teréne. */
    private fun roadSlopeDeg(wx: Float, d: Float, seg: RoadSegment): Float {
        val dx = 0.9f
        val x0 = depth.atX(depth.frontX(wx - dx), d)
        val y0 = depth.atY(depth.frontY(seg.heightAtWorld(wx - dx)), d)
        val x1 = depth.atX(depth.frontX(wx + dx), d)
        val y1 = depth.atY(depth.frontY(seg.heightAtWorld(wx + dx)), d)
        return Math.toDegrees(
            WheelContactFx.screenSlopeRad(x0, y0, x1, y1).toDouble()
        ).toFloat()
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
        val rearSlopeDeg: Float,
        val frontSlopeDeg: Float,
        val wheelR: Float,
        val rearWheelR: Float,
        val frontWheelR: Float,
        val bodyPitch: Float
    )

    /** Jednotný výpočet pozície auta, kolies a tieňa v obrazovkových súradniciach. */
    private fun carScreenPose(engine: GameEngine): CarScreenPose {
        val car = engine.car
        val visualContact = car.visuallyGrounded
        val d = carDepth()
        val wb = SedanSpec.wheelOffsetX
        val layers = assets.sedan
        // SHREDDED spustí os na ráfik; defekt drží blatník (placku kreslí artist).
        val wellR = carArtist.wheelRadiusPx(layers, depth.ppm)
        val rearScale = car.wheelVisualScale(ComponentSlot.TIRE_REAR)
        val frontScale = car.wheelVisualScale(ComponentSlot.TIRE_FRONT)
        val rearWheelR = wellR * rearScale
        val frontWheelR = wellR * frontScale
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
        fun axleSlopeDeg(wx: Float): Float {
            val dx = 0.35f
            return Math.toDegrees(
                WheelContactFx.screenSlopeRad(
                    sx(wx - dx),
                    sy(engine.segment.heightAtWorld(wx - dx)),
                    sx(wx + dx),
                    sy(engine.segment.heightAtWorld(wx + dx))
                ).toDouble()
            ).toFloat()
        }
        val rearSlopeDeg = axleSlopeDeg(rearWorldX)
        val frontSlopeDeg = axleSlopeDeg(frontWorldX)

        val bodyX = sx(car.x)
        val wheelMidY = ((rearGround.y - rearWheelR) + (frontGround.y - frontWheelR)) * 0.5f
        // Blatníky na kolesách; stlačenie pruženia karosériu vtiahne; ride height mení svetlosť.
        val avgComp = (car.rearCompression + car.frontCompression) * 0.5f
        val sinkPx = (avgComp * depth.ppm * GameConfig.SUSP_VISUAL_GAIN)
            .coerceIn(0f, wellBelowCenter * 0.55f)
        val rideBiasPx = (car.rideHeight - GameConfig.CAR_RIDE_HEIGHT) * depth.ppm
        val bodyLiftPx = GameConfig.BODY_VISUAL_LIFT * depth.ppm
        val roadBodyY = wheelMidY - wellBelowCenter + sinkPx - rideBiasPx - bodyLiftPx
        var bodyY = if (visualContact) {
            roadBodyY
        } else {
            // Aj pri skutočnom skoku nesmie numerická poloha vykresliť stred
            // karosérie nižšie než jej kontaktnú polohu na vozovke.
            minOf(sy(car.y), roadBodyY)
        }

        var layout = carArtist.layoutAtBody(layers, bodyX, bodyY, depth.ppm)
        val bodyPitch = car.visualPitch
        val theta = -bodyPitch
        val c = kotlin.math.cos(theta)
        val s = kotlin.math.sin(theta)
        fun well(wx: Float, wy: Float): Offset {
            val dx = wx - bodyX
            val dy = wy - bodyY
            return Offset(bodyX + dx * c - dy * s, bodyY + dx * s + dy * c)
        }

        var rearWell = well(layout.rearWx, layout.rearWy)
        var frontWell = well(layout.frontWx, layout.frontWy)
        if (visualContact) {
            // Karosériu posadíme podľa priemeru oboch kontaktov. Kolesá potom
            // ostávajú v otočených otvoroch blatníkov, namiesto samostatného
            // poskakovania podľa každej vzorky terénu.
            val targetAxleY = ((rearGround.y - rearWheelR) + (frontGround.y - frontWheelR)) * 0.5f
            val currentAxleY = (rearWell.y + frontWell.y) * 0.5f
            bodyY += targetAxleY - currentAxleY
            layout = carArtist.layoutAtBody(layers, bodyX, bodyY, depth.ppm)
            rearWell = well(layout.rearWx, layout.rearWy)
            frontWell = well(layout.frontWx, layout.frontWy)
        }
        // Koleso sa drží vozovky pod sebou, nie priemeru oboch kontaktov.
        // Výchylku obmedzuje zdvih namontovaného pruženia aj otvor blatníka:
        // inak zadné koleso na hrboli vybehlo cez karosériu. Predok aj zadok
        // majú ten istý oblúk; menší disk (SHREDDED) smie klesnúť o rozdiel
        // polomerov, nie vyliezť hore.
        val travelPx = SuspensionVisual.travelPx(car.suspTravel, depth.ppm)
        fun onRoad(
            well: Offset,
            groundY: Float,
            radius: Float,
            slopeDeg: Float,
            alongSlope: Boolean
        ): Offset {
            val (x, y) = SuspensionVisual.wheelOnRoad(
                wellX = well.x,
                wellY = well.y,
                groundY = groundY,
                radiusPx = radius,
                restRadiusPx = wellR,
                travelPx = travelPx,
                visualContact = visualContact,
                slopeRad = Math.toRadians(slopeDeg.toDouble()).toFloat(),
                alongSlope = alongSlope
            )
            return Offset(x, y)
        }
        // Defekt sadne po normále svahu; nafúknuté aj ráfik ostávajú pod oblúkom.
        val rearWheel = onRoad(
            rearWell, rearGround.y, rearWheelR, rearSlopeDeg,
            car.tireInjury(ComponentSlot.TIRE_REAR) == TireInjury.PUNCTURED
        )
        val frontWheel = onRoad(
            frontWell, frontGround.y, frontWheelR, frontSlopeDeg,
            car.tireInjury(ComponentSlot.TIRE_FRONT) == TireInjury.PUNCTURED
        )

        return CarScreenPose(
            bodyX, bodyY, rearGround, frontGround, rearWheel, frontWheel,
            midGround, slopeDeg, rearSlopeDeg, frontSlopeDeg,
            wheelR, rearWheelR, frontWheelR, bodyPitch
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

    private fun DrawScope.drawCar(engine: GameEngine, day: Float) {
        val car = engine.car
        val pose = carScreenPose(engine)
        val braking = engine.brakeInput > 0.25f && car.speed >= 0f
        with(carArtist) {
            drawSedan(
                car = car,
                bodyX = pose.bodyX,
                bodyY = pose.bodyY,
                angle = pose.bodyPitch,
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
                frontWheelY = pose.frontWheel.y,
                rearRoadSlopeDeg = pose.rearSlopeDeg,
                frontRoadSlopeDeg = pose.frontSlopeDeg
            ) {
                // Iskry ešte pod diskom – koleso ich potom prekryje, nejdú cez výrezy.
                drawRimSparks(engine, day, pose, depth.ppm, engine.elapsed)
            }
        }
    }

    private fun resetTrails() {
        skidCount = 0
        skidHead = 0
        lastFrontSkidX = Float.NaN
        lastRearSkidX = Float.NaN
        backdropShift.fill(0f)
        lastBackdropX = Float.NaN
    }

    /** Vizuálny test: stopy bez fyziky preklzu. Prvé [draw] buffer vymaže. */
    internal fun seedSkidMark(wx: Float, power: Float, stamp: Float, rim: Boolean) {
        skidX[skidHead] = wx
        skidPower[skidHead] = power.coerceIn(0f, 1f)
        skidStamp[skidHead] = stamp
        skidRim[skidHead] = if (rim) 1 else 0
        skidHead = (skidHead + 1) % SKID_MAX
        if (skidCount < SKID_MAX) skidCount++
    }

    /** Zapíše stopu, keď kolesá preklzávajú alebo sú zablokované. */
    private fun recordSkid(engine: GameEngine) {
        val car = engine.car
        val slip = car.wheelSlip
        if (!WheelContactFx.emitsSkidMarks(
                car.grounded, car.visuallyGrounded, car.speed, slip, car.wheelsLocked
            )
        ) {
            if (slip < 0.15f) {
                lastFrontSkidX = Float.NaN
                lastRearSkidX = Float.NaN
            }
            return
        }

        fun stamp(slot: ComponentSlot) {
            val front = slot == ComponentSlot.TIRE_FRONT
            val wx = car.x + if (front) SedanSpec.wheelOffsetX else -SedanSpec.wheelOffsetX
            val last = if (front) lastFrontSkidX else lastRearSkidX
            if (!last.isNaN() && kotlin.math.abs(wx - last) < 0.32f) return
            if (front) lastFrontSkidX = wx else lastRearSkidX = wx
            skidX[skidHead] = wx
            skidPower[skidHead] = slip
            skidStamp[skidHead] = engine.elapsed
            skidRim[skidHead] =
                if (WheelContactFx.metalSkid(car.tireInjury(slot) == TireInjury.SHREDDED)) 1 else 0
            skidHead = (skidHead + 1) % SKID_MAX
            if (skidCount < SKID_MAX) skidCount++
        }

        // Pri wheelspine značkuje iba hnaná náprava; pri zablokovaných brzdách obe.
        if (car.wheelsLocked || car.drives(ComponentSlot.TIRE_REAR)) stamp(ComponentSlot.TIRE_REAR)
        if (car.wheelsLocked || car.drives(ComponentSlot.TIRE_FRONT)) stamp(ComponentSlot.TIRE_FRONT)
    }

    /** Stopy na vozovke – guma ostáva tmavá, ráfik kreslí tenké svetlé ryhy. */
    private fun DrawScope.drawSkidMarks(engine: GameEngine, day: Float) {
        if (skidCount == 0) return
        val seg = engine.segment
        val now = engine.elapsed
        val rubber = shade(Color(0xFF1A1512), day)
        val scratch = shade(Color(0xFFD4C4A0), day)
        val glint = shade(Color(0xFFF0E2B4), day)
        for (i in 0 until skidCount) {
            val age = now - skidStamp[i]
            if (age > SKID_LIFE) continue
            val wx = skidX[i]
            if (wx < engine.camera.x - 22f || wx > engine.camera.x + 22f) continue
            val fade = (1f - age / SKID_LIFE) * skidPower[i]
            val fy = depth.frontY(seg.heightAtWorld(wx))
            val fx = depth.frontX(wx)
            val rim = skidRim[i] != 0.toByte()
            for (rut in 0 until 2) {
                val d = if (rut == 0) GameConfig.RUT_NEAR_DEPTH else GameConfig.RUT_FAR_DEPTH
                val x = depth.atX(fx, d)
                val y = depth.atY(fy, d)
                val slopeRad = Math.toRadians(roadSlopeDeg(wx, d, seg).toDouble()).toFloat()
                val half = WheelContactFx.skidHalfLengthPx(depth.ppm, rim)
                if (rim) {
                    val wobble = (MathX.hash01(i + rut * 17, (wx * 4f).toInt()) - 0.5f) * depth.ppm * 0.04f
                    val (a, b) = WheelContactFx.skidMarkEnds(x, y, slopeRad, half, wobble, -wobble * 0.4f)
                    drawLine(
                        scratch.copy(alpha = 0.42f * fade),
                        Offset(a.first, a.second),
                        Offset(b.first, b.second),
                        strokeWidth = WheelContactFx.metalSkidWidth(depth.ppm),
                        cap = StrokeCap.Round
                    )
                    val (g0, g1) = WheelContactFx.skidMarkEnds(
                        x, y, slopeRad, depth.ppm * 0.17f, 0f, -wobble * 0.2f
                    )
                    drawLine(
                        glint.copy(alpha = 0.28f * fade),
                        Offset(g0.first, g0.second),
                        Offset(g1.first, g1.second),
                        strokeWidth = (WheelContactFx.metalSkidWidth(depth.ppm) * 0.55f).coerceAtLeast(0.8f),
                        cap = StrokeCap.Round
                    )
                } else {
                    val (a, b) = WheelContactFx.skidMarkEnds(x, y, slopeRad, half)
                    drawLine(
                        rubber.copy(alpha = 0.55f * fade),
                        Offset(a.first, a.second),
                        Offset(b.first, b.second),
                        strokeWidth = WheelContactFx.rubberSkidWidth(depth.ppm),
                        cap = StrokeCap.Round
                    )
                }
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
            val theta = -pose.bodyPitch
            val c = kotlin.math.cos(theta)
            val s = kotlin.math.sin(theta)
            val dx = x - px
            val dy = y - py
            return Offset(px + dx * c - dy * s, py + dx * s + dy * c)
        }

        val speedRatio = (abs(car.speed) / GameConfig.MAX_SPEED).coerceIn(0f, 1f)
        val rearSlope = Math.toRadians(pose.rearSlopeDeg.toDouble()).toFloat()
        val rearOnRoad = WheelContactFx.emitsGroundFx(
            car.grounded, car.visuallyGrounded, car.speed,
            GameConfig.MAX_SPEED * WheelContactFx.DUST_MIN_SPEED_RATIO
        ) && WheelContactFx.contactingRoad(
            pose.rearWheel.y, pose.rearGround.y, pose.rearWheelR, ppm * 0.16f, rearSlope
        )
        if (rearOnRoad) {
            val dust = shade(Color(0xFFBFAE8E), day)
            val alongSign = WheelContactFx.trailAlongSign(car.speed)
            val contact = pose.rearGround
            for (i in 0 until 14) {
                val phase = (t * 2.2f + i * 0.29f) % 1f
                val h = MathX.hash01(i, (t * 3f).toInt())
                val along = alongSign * ppm * (0.14f + phase * 2.2f + h * 0.28f)
                val across = -ppm * (0.015f + phase * 0.16f + (h - 0.45f) * 0.04f)
                val (x, y) = WheelContactFx.alongRoad(contact.x, contact.y, rearSlope, along, across)
                val r = ppm * (0.022f + phase * 0.075f + h * 0.018f)
                drawCrispPuff(
                    dust.copy(alpha = (0.34f * speedRatio) * (1f - phase)),
                    Offset(x, y),
                    r
                )
            }
        }
        drawSurfaceSpray(engine, day, pose, ppm, t)
        drawSlipEffects(engine, day, pose, ppm, t)
        if (car.engineRunning) {
            // Dojazdený motor dymí modro a hustejšie – z rúry, nie z nápravy.
            val engineHp = (car.parts[ComponentSlot.ENGINE]?.health ?: 1f).coerceIn(0f, 1f)
            val sick = (1f - engineHp / 0.6f).coerceIn(0f, 1f)
            val smoke = shade(lerp(Color(0xFF9AA0A6), Color(0xFF6E7A93), sick), day)
            val pipe = rotated(layout.exhaustX, layout.exhaustY)
            val axis = CarBodyFx.carAxisRad(pose.bodyPitch)
            val puffs = 6 + (sick * 5f).toInt()
            for (i in 0 until puffs) {
                val phase = (t * (0.85f + sick * 0.45f) + i * 0.21f) % 1f
                val h = MathX.hash01(i, (t * 5f).toInt())
                val along = CarBodyFx.exhaustAlong(phase, car.speed, ppm, h)
                val lift = CarBodyFx.exhaustLift(phase, ppm, h)
                val (x, y) = WheelContactFx.alongRoad(pipe.x, pipe.y, axis, along, lift)
                drawCrispPuff(
                    smoke.copy(alpha = (0.22f + sick * 0.28f) * (1f - phase)),
                    Offset(x, y),
                    CarBodyFx.exhaustRadius(phase, ppm, sick, h)
                )
            }
        }
        drawDamageSigns(engine, day, pose, layout, ppm, t, ::rotated)
        drawEventSigns(engine, day, pose, layout, ppm, t, ::rotated)
    }

    /** Tesnejší prach – tri malé zhluky namiesto jednej obrovskej gule na náboji. */
    private fun DrawScope.drawCrispPuff(color: Color, center: Offset, r: Float) {
        val rr = r.coerceAtLeast(0.6f)
        drawCircle(color, rr * 0.58f, center)
        drawCircle(
            color.copy(alpha = color.alpha * 0.42f),
            rr * 0.38f,
            Offset(center.x + rr * 0.40f, center.y - rr * 0.10f)
        )
        drawCircle(
            color.copy(alpha = color.alpha * 0.32f),
            rr * 0.28f,
            Offset(center.x - rr * 0.30f, center.y + rr * 0.08f)
        )
    }

    /**
     * Jazda na ráfiku – iskry zo styčnej plochy pod diskom, kým sa šúcha o vozovku.
     * Kreslia sa pred kolesom a ostávajú pod stredom náboja, nie cez výrezy v lúčoch.
     * Stopa ide po svahu proti rýchlosti – aj pri cúvaní.
     */
    private fun DrawScope.drawRimSparks(
        engine: GameEngine,
        day: Float,
        pose: CarScreenPose,
        ppm: Float,
        t: Float
    ) {
        val car = engine.car
        if (!WheelContactFx.emitsGroundFx(
                car.grounded, car.visuallyGrounded, car.speed, WheelContactFx.SPARK_MIN_SPEED
            )
        ) {
            return
        }
        val alongSign = WheelContactFx.trailAlongSign(car.speed)
        fun sparks(slot: ComponentSlot, wheel: Offset, ground: Offset, radius: Float, slopeDeg: Float) {
            if (car.tireInjury(slot) != TireInjury.SHREDDED) return
            val slope = Math.toRadians(slopeDeg.toDouble()).toFloat()
            if (!WheelContactFx.contactingRoad(wheel.y, ground.y, radius, ppm * 0.16f, slope)) return
            val hubR = (ground.y - wheel.y).coerceAtLeast(ppm * 0.08f)
            val contact = ground
            val front = slot == ComponentSlot.TIRE_FRONT
            rotate(degrees = slopeDeg, pivot = contact) {
                val originX = WheelContactFx.sparkOriginX(contact.x, car.speed, ppm, front)
                val originY = contact.y - ppm * 0.008f
                val trail = ppm * 1.55f
                val pad = hubR * 0.28f + ppm * 0.06f
                val (clipL, clipR) = WheelContactFx.sparkClipX(
                    contact.x, originX, alongSign, trail, pad
                )
                // Pás na vozovke pod ráfikom, už otočený so svahom.
                clipRect(
                    left = clipL,
                    top = contact.y - ppm * 0.04f,
                    right = clipR,
                    bottom = contact.y + ppm * 0.085f
                ) {
                    for (i in 0 until 14) {
                        val phase = (t * 20f + i * 0.07f) % 1f
                        val h = MathX.hash01(i + slot.ordinal * 13, (t * 24f).toInt())
                        val spark = shade(
                            lerp(Color(0xFFFFF4C2), Color(0xFFFF7A28), h),
                            day
                        )
                        val along = alongSign * ppm * (0.02f + phase * (0.85f + h * 0.4f))
                        val across = ppm * ((h - 0.5f) * 0.03f)
                        val x = originX + along
                        val y = originY + across
                        val alpha = 0.92f * (1f - phase)
                        drawCircle(
                            spark.copy(alpha = alpha),
                            ppm * (0.011f + h * 0.018f),
                            Offset(x, y)
                        )
                        drawLine(
                            spark.copy(alpha = alpha * 0.75f),
                            Offset(originX + alongSign * ppm * phase * 0.08f, originY),
                            Offset(x, y),
                            strokeWidth = ppm * (0.007f + h * 0.010f),
                            cap = StrokeCap.Round
                        )
                    }
                }
            }
        }
        sparks(ComponentSlot.TIRE_REAR, pose.rearWheel, pose.rearGround, pose.rearWheelR, pose.rearSlopeDeg)
        sparks(ComponentSlot.TIRE_FRONT, pose.frontWheel, pose.frontGround, pose.frontWheelR, pose.frontSlopeDeg)
    }

    /**
     * Udalosti viditeľné na aute: kvapkajúce palivo, para z chladiča a čierne
     * fŕkance z výfuku pri vynechávaní. Trvajúca porucha musí byť vidieť na
     * aute, nie len ako štítok v rohu.
     */
    private fun DrawScope.drawEventSigns(
        engine: GameEngine,
        day: Float,
        pose: CarScreenPose,
        layout: CarArtist.SpriteLayout,
        ppm: Float,
        t: Float,
        rotated: (Float, Float) -> Offset
    ) {
        if (engine.hasEvent(RoadEvent.FUEL_LEAK)) {
            // Kvapká spod zadnej časti a na ceste ostáva mokrá stopa.
            // Kvapka padá od podvozku po vozovku – dráhu naťahujeme presne medzi
            // ne, nie na pevnú výšku, inak sa väčšina kvapiek orezala.
            val drip = shade(Color(0xFFC9A24E), day)
            val topY = pose.bodyY + ppm * 0.30f
            val dropY = pose.midGround.y
            // Kvapká za zadným kolesom – tam sedí nádrž, nie v strede podvozku.
            for (i in 0 until 6) {
                val phase = ((t * 1.5f) + i * 0.17f) % 1f
                val x = pose.rearWheel.x - ppm * (0.45f + MathX.hash01(i, 61) * 0.4f)
                val y = topY + (dropY - topY) * phase
                drawCircle(drip.copy(alpha = 0.9f * (1f - phase * 0.5f)), ppm * 0.055f, Offset(x, y))
            }
            // Mokrá stopa leží na vozovke, nie na pevnom odsadení od auta:
            // premieta sa cez terén v mieste úniku, takže kopíruje kopce
            // a nelieta pri každom nadskočení hore-dole.
            val wet = shade(Color(0xFF241C10), day)
            val leakX = engine.car.x - SedanSpec.wheelOffsetX
            val d = GameConfig.RUT_NEAR_DEPTH
            for (i in 0 until 9) {
                val wx = leakX - 0.55f - i * 0.75f
                val fade = 0.45f - i * 0.045f
                if (fade <= 0f) break
                val x = depth.atX(depth.frontX(wx), d)
                if (x < -40f || x > size.width + 40f) continue
                val y = depth.atY(depth.frontY(engine.segment.heightAtWorld(wx)), d)
                drawOval(
                    wet.copy(alpha = fade),
                    topLeft = Offset(x - ppm * 0.24f, y - ppm * 0.05f),
                    size = Size(ppm * 0.48f, ppm * 0.11f)
                )
            }
        }

        if (engine.hasEvent(RoadEvent.COOLANT_LEAK)) {
            // Para z chladiča a kapoty – rovnaký štýl ako prehriatie, hustejšia.
            drawHoodSteam(
                engine, pose, layout, ppm, t, rotated,
                shade(Color(0xFFDCEDE4), day),
                8,
                0.52f
            )
        }

        if (engine.hasEvent(RoadEvent.MISFIRE) && engine.car.engineRunning) {
            val beat = ((t * 3.7f) % 1f)
            if (CarBodyFx.misfirePopping(beat)) {
                val pipe = rotated(layout.exhaustX, layout.exhaustY)
                val axis = CarBodyFx.carAxisRad(pose.bodyPitch)
                val fade = 1f - beat / 0.18f
                val soot = shade(Color(0xFF2A2622), day)
                val flame = shade(Color(0xFFFFB45A), day)
                val core = shade(Color(0xFFFFF3C2), day)
                for (i in 0 until 5) {
                    val h = MathX.hash01(i, (t * 9f).toInt())
                    val along = CarBodyFx.misfireJetAlong(h, ppm)
                    val lift = CarBodyFx.misfireJetLift(h, ppm)
                    val (x, y) = WheelContactFx.alongRoad(pipe.x, pipe.y, axis, along, lift)
                    if (i < 2) {
                        drawCircle(core.copy(alpha = 0.82f * fade), ppm * (0.018f + h * 0.02f), Offset(x, y))
                        drawLine(
                            flame.copy(alpha = 0.75f * fade),
                            pipe,
                            Offset(x, y),
                            strokeWidth = ppm * (0.012f + h * 0.016f),
                            cap = StrokeCap.Round
                        )
                    } else {
                        drawCrispPuff(
                            soot.copy(alpha = 0.48f * fade),
                            Offset(x, y),
                            ppm * (0.020f + h * 0.028f)
                        )
                    }
                }
            }
        }
    }

    private fun DrawScope.drawHoodSteam(
        engine: GameEngine,
        pose: CarScreenPose,
        layout: CarArtist.SpriteLayout,
        ppm: Float,
        t: Float,
        rotated: (Float, Float) -> Offset,
        steam: Color,
        puffs: Int,
        alpha: Float
    ) {
        val axis = CarBodyFx.carAxisRad(pose.bodyPitch)
        val vents = arrayOf(
            rotated(layout.radiatorX, layout.radiatorY),
            rotated(layout.hoodVentX, layout.hoodVentY)
        )
        for (v in vents.indices) {
            val origin = vents[v]
            for (i in 0 until puffs) {
                val phase = ((t * (1.15f + v * 0.12f)) + i * 0.13f) % 1f
                val h = MathX.hash01(i + v * 11, (t * 6f).toInt())
                val along = CarBodyFx.steamAlong(phase, engine.car.speed, ppm, h)
                val lift = CarBodyFx.steamLift(phase, ppm, h)
                val (x, y) = WheelContactFx.alongRoad(origin.x, origin.y, axis, along, lift)
                drawCrispPuff(
                    steam.copy(alpha = alpha * (1f - phase)),
                    Offset(x, y),
                    CarBodyFx.steamRadius(phase, ppm, h)
                )
            }
        }
    }

    /**
     * Poruchy priamo na aute: para z prehriateho chladiča a iskry z rozpadnutého
     * motora. Hráč tak vidí, že je zle, aj keď sa nepozerá na prístrojovku.
     */
    private fun DrawScope.drawDamageSigns(
        engine: GameEngine,
        day: Float,
        pose: CarScreenPose,
        layout: CarArtist.SpriteLayout,
        ppm: Float,
        t: Float,
        rotated: (Float, Float) -> Offset
    ) {
        val car = engine.car
        // Para z chladiča a kapoty – prah je kúsok pod prehriatím, aby varovala včas.
        val heat = ((car.temperature - (GameConfig.OVERHEAT_THRESHOLD - 10f)) / 18f)
            .coerceIn(0f, 1f)
        if (heat > 0.02f) {
            drawHoodSteam(
                engine, pose, layout, ppm, t, rotated,
                shade(Color(0xFFE8EEF2), day),
                6,
                0.38f * heat
            )
        }
        // Motor tesne pred rozpadom – prerušované iskry, nie súvislý efekt.
        val engineHp = car.parts[ComponentSlot.ENGINE]?.health ?: 1f
        if (car.engineRunning && engineHp < 0.22f) {
            val flash = MathX.hash01((t * 9f).toInt(), 4801)
            if (flash < 0.35f) {
                val spark = shade(Color(0xFFFFC46B), day)
                val layout = carArtist.layoutAtBody(assets.sedan, pose.bodyX, pose.bodyY, ppm)
                for (i in 0 until 4) {
                    val h = MathX.hash01(i, (t * 11f).toInt())
                    // Iskry z motorového priestoru, teda z prednej časti sprite.
                    drawCircle(
                        spark.copy(alpha = 0.7f * (1f - h)),
                        ppm * (0.03f + h * 0.05f),
                        Offset(
                            layout.originX + layout.drawW * (0.78f + h * 0.16f),
                            layout.originY + layout.drawH * (0.40f - h * 0.18f)
                        )
                    )
                }
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
        if (!surface.hazard) return
        if (!WheelContactFx.emitsGroundFx(
                engine.car.grounded, engine.car.visuallyGrounded, engine.car.speed, 1.2f
            )
        ) return
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
        val alongSign = WheelContactFx.trailAlongSign(engine.car.speed)
        for (wheel in 0 until 2) {
            val hub = if (wheel == 0) pose.rearWheel else pose.frontWheel
            val contact = if (wheel == 0) pose.rearGround else pose.frontGround
            val radius = if (wheel == 0) pose.rearWheelR else pose.frontWheelR
            val slope = Math.toRadians(
                (if (wheel == 0) pose.rearSlopeDeg else pose.frontSlopeDeg).toDouble()
            ).toFloat()
            if (!WheelContactFx.contactingRoad(hub.y, contact.y, radius, ppm * 0.16f, slope)) continue
            for (i in 0 until 9) {
                val phase = ((t * 2.8f) + i * 0.16f + wheel * 0.11f) % 1f
                val h = MathX.hash01(i + wheel * 13, (t * 8f).toInt())
                // Striekance letia po svahu dozadu a hore, potom padajú.
                val along = alongSign * ppm * (0.08f + phase * 1.8f + h * 0.22f)
                val lift = -ppm * (phase * 0.85f - phase * phase * 1.15f)
                val (x, y) = WheelContactFx.alongRoad(contact.x, contact.y, slope, along, lift)
                val r = ppm * (0.028f + h * 0.032f) * (if (surface == RoadSurface.WATER) 1.15f else 1f)
                drawCircle(col.copy(alpha = 0.72f * v * (1f - phase)), r, Offset(x, y))
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
        if (!WheelContactFx.emitsSkidMarks(
                car.grounded, car.visuallyGrounded, car.speed, slip, car.wheelsLocked, 0.12f
            )
        ) return

        // Dym ide spod hnanej nápravy – pri FWD spredu, pri RWD zozadu.
        val alongSign = WheelContactFx.trailAlongSign(car.speed)
        for (slot in TIRE_SLOTS) {
            if (!car.hasPart(slot) || (!car.wheelsLocked && !car.drives(slot))) continue
            val hub = if (slot == ComponentSlot.TIRE_FRONT) pose.frontWheel else pose.rearWheel
            val contact = if (slot == ComponentSlot.TIRE_FRONT) pose.frontGround else pose.rearGround
            val radius = if (slot == ComponentSlot.TIRE_FRONT) pose.frontWheelR else pose.rearWheelR
            val slope = Math.toRadians(
                (if (slot == ComponentSlot.TIRE_FRONT) pose.frontSlopeDeg else pose.rearSlopeDeg)
                    .toDouble()
            ).toFloat()
            if (!WheelContactFx.contactingRoad(hub.y, contact.y, radius, ppm * 0.16f, slope)) continue
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

            val puffs = 8 + (slip * 6f).toInt()
            for (i in 0 until puffs) {
                val phase = ((t * 2.1f) + i * 0.19f) % 1f
                val jitter = MathX.hash01(i, (t * 6f).toInt()) - 0.5f
                val along = alongSign * ppm * (0.12f + phase * 2.4f) + jitter * ppm * 0.18f
                val across = -ppm * (0.02f + phase * 0.38f + jitter * 0.06f)
                val (x, y) = WheelContactFx.alongRoad(contact.x, contact.y, slope, along, across)
                val r = ppm * (0.032f + phase * 0.11f)
                drawCrispPuff(
                    smoke.copy(alpha = 0.38f * slip * (1f - phase)),
                    Offset(x, y),
                    r
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
                for (i in 0 until 8) {
                    val phase = ((t * 3.4f) + i * 0.14f) % 1f
                    val h = MathX.hash01(i, (t * 9f).toInt())
                    val along = alongSign * ppm * (0.1f + phase * 3.2f)
                    val lift = -ppm * (phase * 1.15f - phase * phase * 1.35f)
                    val (x, y) = WheelContactFx.alongRoad(contact.x, contact.y, slope, along, lift)
                    drawCircle(
                        grit.copy(alpha = 0.78f * slip * (1f - phase)),
                        ppm * (0.018f + h * 0.022f),
                        Offset(x, y)
                    )
                }
            }
        }
    }

    /**
     * Nočný závoj a diera svetlometov. Scéna v kuželi sa prekreslí svetlejším
     * dňom – nie žltým overlayom cez tmu.
     */
    private fun DrawScope.drawNight(
        engine: GameEngine,
        day: Float,
        horizonY: Float,
        visibleFrom: Float,
        visibleTo: Float
    ) {
        val night = (1f - day).coerceIn(0f, 1f)
        val pose = carScreenPose(engine)
        val highBeam = engine.highBeamsOn
        val roofAssist = engine.roofLightsOn && engine.car.hasPart(ComponentSlot.BATTERY)
        val beamStrength = when {
            engine.headlightsOn -> headlightStrength(engine, night)
            roofAssist -> (0.3f + night * 0.7f) *
                (engine.car.batteryCharge * 8f).coerceIn(0.35f, 1f)
            else -> 0f
        }
        val reveal = (engine.headlightsOn || roofAssist) && beamStrength > 0.08f && night > 0.05f
        if (reveal) buildHeadlightMask(engine, pose, highBeam, engine.headlightsOn, roofAssist)

        if (night > 0.02f) {
            val veil = Color(0xFF0B1226).copy(alpha = night * 0.28f)
            if (reveal) {
                withSaveLayer(saveLayerPaint) {
                    drawRect(veil, size = Size(size.width, size.height))
                    paintHeadlightMask(pose, highBeam, beamStrength, BlendMode.DstOut)
                }
            } else {
                drawRect(veil, size = Size(size.width, size.height))
            }
        }
        if (reveal) {
            val litDay = HeadlightFx.revealedDay(day, highBeam, beamStrength)
            withSaveLayer(saveLayerPaint) {
                clipPath(headlightUnion) {
                    drawHeadlightLitWorld(engine, litDay, horizonY, visibleFrom, visibleTo)
                }
                withSaveLayer(dstInPaint) {
                    paintHeadlightMask(pose, highBeam, beamStrength, BlendMode.SrcOver)
                }
            }
            // Litý prechod ide po aute – karoséria musí ostať navrchu stromov v kuželi.
            drawCar(engine, day)
        }
        if (engine.headlightsOn) {
            with(carArtist) {
                drawHeadlightBeam(
                    bodyX = pose.bodyX,
                    bodyY = pose.bodyY,
                    angle = pose.bodyPitch,
                    ppm = depth.ppm,
                    proj = depth,
                    layers = assets.sedan,
                    strength = beamStrength,
                    highBeam = highBeam,
                    braking = engine.brakeInput > 0.25f && engine.car.speed >= 0f,
                    roadY = pose.midGround.y
                )
            }
        }
        if (engine.roofLightsOn && engine.car.hasPart(ComponentSlot.BATTERY)) {
            val power = (engine.car.batteryCharge * 8f).coerceIn(0f, 1f)
            val layout = carArtist.layoutAtBody(assets.sedan, pose.bodyX, pose.bodyY, depth.ppm)
            rotate(-Math.toDegrees(pose.bodyPitch.toDouble()).toFloat(), Offset(pose.bodyX, pose.bodyY)) {
                with(ExpeditionEquipment) { drawLight(layout, power, night) }
            }
        }
        drawRimSparkNightGlow(engine, day, pose, depth.ppm, engine.elapsed)
    }

    private fun headlightStrength(engine: GameEngine, night: Float): Float {
        val headlightHealth = engine.car.parts[ComponentSlot.HEADLIGHT]?.health ?: 0f
        val damageFlicker = if (headlightHealth >= 0.72f) {
            1f
        } else {
            val tick = (engine.elapsed * 17f).toInt()
            val roll = MathX.hash01(tick, 0x1A17)
            val dropChance = ((0.72f - headlightHealth) * 0.95f).coerceIn(0f, 0.62f)
            if (roll < dropChance) 0.06f
            else (0.42f + headlightHealth * 0.75f).coerceIn(0.42f, 0.95f)
        }
        val belt = if (engine.hasEvent(RoadEvent.BELT_SNAPPED)) {
            0.45f + 0.55f * MathX.hash01((engine.elapsed * 11f).toInt(), 733)
        } else 1f
        return (0.3f + night * 0.7f) * damageFlicker * belt
    }

    private fun DrawScope.buildHeadlightMask(
        engine: GameEngine,
        pose: CarScreenPose,
        highBeam: Boolean,
        headlights: Boolean,
        roof: Boolean
    ) {
        val ppm = depth.ppm
        revealHeadlights = headlights
        revealRoof = roof
        headlightLamp = carArtist.lampOnBody(assets.sedan, pose.bodyX, pose.bodyY, ppm)
        rackLamp = if (roof) {
            carArtist.rackLampOnBody(assets.sedan, pose.bodyX, pose.bodyY, ppm)
        } else {
            Offset.Zero
        }
        val deg = -Math.toDegrees(pose.bodyPitch.toDouble()).toFloat()
        fillUnrotatedReveal(headlightUnion, ppm, highBeam)
        headlightMatrix.reset()
        headlightMatrix.translate(pose.bodyX, pose.bodyY)
        headlightMatrix.rotateZ(deg)
        headlightMatrix.translate(-pose.bodyX, -pose.bodyY)
        headlightUnion.transform(headlightMatrix)
    }

    private fun rotatedAroundBody(point: Offset, pose: CarScreenPose): Offset {
        val theta = -pose.bodyPitch
        val c = cos(theta)
        val s = sin(theta)
        val dx = point.x - pose.bodyX
        val dy = point.y - pose.bodyY
        return Offset(pose.bodyX + dx * c - dy * s, pose.bodyY + dx * s + dy * c)
    }

    /**
     * Svetové X, ktoré na vozovke (hĺbka auta) sedí pod danou obrazovkovou X.
     * Sprite lampa je billboard v plnom ppm; projekcia cesty je stlačená.
     */
    private fun worldXAlignedToScreen(screenX: Float, bodyScreenX: Float, carX: Float): Float {
        val pxPerM = depth.ppm * (1f - depth.perspectiveT(carDepth()))
        if (pxPerM < 1e-3f) return carX
        return carX + (screenX - bodyScreenX) / pxPerM
    }

    private fun appendRevealTrapezoid(
        path: Path,
        lamp: Offset,
        ppm: Float,
        reachM: Float,
        nearUpperM: Float,
        nearLowerM: Float,
        farUpperM: Float,
        farLowerM: Float
    ) {
        val len = ppm * reachM
        path.moveTo(lamp.x, lamp.y + ppm * nearUpperM)
        path.lineTo(lamp.x + len, lamp.y + ppm * farUpperM)
        path.lineTo(lamp.x + len, lamp.y + ppm * farLowerM)
        path.lineTo(lamp.x, lamp.y + ppm * nearLowerM)
        path.close()
    }

    /** Strešný reflektor: trojuholník z lampy, nie lichobežníková doska. */
    private fun appendRevealCone(
        path: Path,
        lamp: Offset,
        ppm: Float,
        reachM: Float,
        farUpperM: Float,
        farLowerM: Float
    ) {
        val len = ppm * reachM
        path.moveTo(lamp.x, lamp.y)
        path.lineTo(lamp.x + len, lamp.y + ppm * farUpperM)
        path.lineTo(lamp.x + len, lamp.y + ppm * farLowerM)
        path.close()
    }

    private fun fillUnrotatedReveal(path: Path, ppm: Float, highBeam: Boolean) {
        path.reset()
        path.fillType = PathFillType.NonZero
        if (revealHeadlights) {
            appendRevealTrapezoid(
                path,
                headlightLamp,
                ppm,
                HeadlightFx.reachM(highBeam),
                HeadlightFx.revealNearUpperM(highBeam),
                HeadlightFx.revealNearLowerM(highBeam),
                HeadlightFx.revealUpperM(highBeam),
                HeadlightFx.revealLowerM(highBeam)
            )
        }
        if (revealRoof) {
            rackCone.reset()
            appendRevealCone(
                rackCone,
                rackLamp,
                ppm,
                HeadlightFx.rackReachM(),
                HeadlightFx.rackRevealUpperM(),
                HeadlightFx.rackRevealLowerM()
            )
            path.addPath(rackCone)
        }
    }

    private fun appendRevealPool(
        engine: GameEngine,
        pose: CarScreenPose,
        lampScreenX: Float,
        highBeam: Boolean
    ) {
        val reach = HeadlightFx.reachM(highBeam)
        val nearD = HeadlightFx.revealNearDepth(highBeam)
        val farD = HeadlightFx.revealFarDepth(highBeam)
        val stations = HeadlightFx.revealStations(highBeam)
        val layers = assets.sedan
        val fromLamps = HeadlightFx.beamStartWorldX(
            engine.car.x,
            pose.bodyPitch,
            layers.headlightFx,
            layers.worldWidthM
        )
        val aligned = worldXAlignedToScreen(lampScreenX, pose.bodyX, engine.car.x)
        val startWx = maxOf(fromLamps, aligned)
        headlightPool.reset()
        for (i in 0..stations) {
            val t = i / stations.toFloat()
            val wx = startWx + t * reach
            val gy = engine.segment.heightAtWorld(wx)
            val x = depth.atX(depth.frontX(wx), nearD)
            val y = depth.atY(depth.frontY(gy), nearD)
            if (i == 0) headlightPool.moveTo(x, y) else headlightPool.lineTo(x, y)
        }
        for (i in stations downTo 0) {
            val t = i / stations.toFloat()
            val wx = startWx + t * reach
            val gy = engine.segment.heightAtWorld(wx)
            val x = depth.atX(depth.frontX(wx), farD)
            val y = depth.atY(depth.frontY(gy), farD)
            headlightPool.lineTo(x, y)
        }
        headlightPool.close()
        headlightNoseX = depth.atX(depth.frontX(startWx), GameConfig.RUT_NEAR_DEPTH)
        val farWx = startWx + reach
        headlightTailX = depth.atX(depth.frontX(farWx), GameConfig.RUT_NEAR_DEPTH)
    }

    private fun DrawScope.paintHeadlightMask(
        pose: CarScreenPose,
        highBeam: Boolean,
        strength: Float,
        blend: BlendMode
    ) {
        val ppm = depth.ppm
        val deg = -Math.toDegrees(pose.bodyPitch.toDouble()).toFloat()
        rotate(degrees = deg, pivot = Offset(pose.bodyX, pose.bodyY)) {
            if (revealHeadlights) {
                val len = ppm * HeadlightFx.reachM(highBeam)
                headlightCone.reset()
                appendRevealTrapezoid(
                    headlightCone,
                    headlightLamp,
                    ppm,
                    HeadlightFx.reachM(highBeam),
                    HeadlightFx.revealNearUpperM(highBeam),
                    HeadlightFx.revealNearLowerM(highBeam),
                    HeadlightFx.revealUpperM(highBeam),
                    HeadlightFx.revealLowerM(highBeam)
                )
                drawPath(
                    headlightCone,
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = strength),
                            Color.White.copy(alpha = strength * 0.88f),
                            Color.White.copy(alpha = strength * 0.40f),
                            Color.Transparent
                        ),
                        startX = headlightLamp.x,
                        endX = headlightLamp.x + len
                    ),
                    blendMode = blend
                )
            }
            if (revealRoof) {
                val len = ppm * HeadlightFx.rackReachM()
                rackCone.reset()
                appendRevealCone(
                    rackCone,
                    rackLamp,
                    ppm,
                    HeadlightFx.rackReachM(),
                    HeadlightFx.rackRevealUpperM(),
                    HeadlightFx.rackRevealLowerM()
                )
                val roofStrength = if (revealHeadlights) strength * 0.55f else strength
                drawPath(
                    rackCone,
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = roofStrength),
                            Color.White.copy(alpha = roofStrength * 0.62f),
                            Color.Transparent
                        ),
                        startX = rackLamp.x,
                        endX = rackLamp.x + len
                    ),
                    blendMode = blend
                )
            }
        }
    }

    /**
     * Druhý prechod cesty a kulís so slabším nočným shade – len v kuželi.
     * Obloha ostáva v nočnom nátere (nekreslí sa SKY pass).
     */
    private fun DrawScope.drawHeadlightLitWorld(
        engine: GameEngine,
        litDay: Float,
        horizonY: Float,
        visibleFrom: Float,
        visibleTo: Float
    ) {
        val cam = engine.camera
        val environment = engine.biomeBlend
        val backdrop = assets.backdropFor(environment.from)
        val nextBackdrop = if (environment.amount > 0.001f && environment.to != environment.from) {
            assets.backdropFor(environment.to)
        } else null
        drawParallaxBackdrop(backdrop, horizonY, litDay, BackdropPass.LANDSCAPE)
        if (nextBackdrop != null) {
            drawParallaxBackdrop(
                nextBackdrop, horizonY, litDay, BackdropPass.LANDSCAPE,
                opacity = environment.amount
            )
        }
        val heightAt: (Float) -> Float = { wx -> groundFor(engine, wx) }
        val occupiedGround: (Float) -> Boolean = { wx ->
            engine.segment.buildingOccupies(wx, BUILDING_CLEAR_M) ||
                engine.segment.bridgeClearanceAtWorld(wx) > BRIDGE_PROP_CLEARANCE_M
        }
        drawLandscapeApron(environment, engine.winterAmount, litDay)
        drawGround(engine.segment, environment, engine.winterAmount, litDay)
        with(scenery) {
            drawBackProps(
                visibleFrom, visibleTo, engine.segment::biomeBlendAtWorld,
                litDay, depth, heightAt, occupiedGround,
                sceneMeadow, landFollowAmount(environment, engine.winterAmount)
            )
        }
        drawRoadSurface(engine.segment, litDay)
        drawSurfacePatches(engine.segment, litDay)
        drawBuildings(engine, litDay)
    }

    private inline fun DrawScope.withSaveLayer(paint: Paint, block: DrawScope.() -> Unit) {
        val canvas = drawContext.canvas
        canvas.saveLayer(Rect(Offset.Zero, size), paint)
        try {
            block()
        } finally {
            canvas.restore()
        }
    }

    /** Po nočnom závoji – iskry z ráfika svietia ako zdroj svetla. */
    private fun DrawScope.drawRimSparkNightGlow(
        engine: GameEngine,
        day: Float,
        pose: CarScreenPose,
        ppm: Float,
        t: Float
    ) {
        val night = (1f - day).coerceIn(0f, 1f)
        if (night < 0.12f) return
        val car = engine.car
        if (!WheelContactFx.emitsGroundFx(
                car.grounded, car.visuallyGrounded, car.speed, WheelContactFx.SPARK_MIN_SPEED
            )
        ) {
            return
        }
        val alongSign = WheelContactFx.trailAlongSign(car.speed)
        val glow = WheelContactFx.sparkNightGlow(night)
        val core = WheelContactFx.sparkNightCore(night)
        fun bloom(slot: ComponentSlot, wheel: Offset, ground: Offset, radius: Float, slopeDeg: Float) {
            if (car.tireInjury(slot) != TireInjury.SHREDDED) return
            val slope = Math.toRadians(slopeDeg.toDouble()).toFloat()
            if (!WheelContactFx.contactingRoad(wheel.y, ground.y, radius, ppm * 0.16f, slope)) return
            val front = slot == ComponentSlot.TIRE_FRONT
            rotate(degrees = slopeDeg, pivot = ground) {
                val originX = WheelContactFx.sparkOriginX(ground.x, car.speed, ppm, front)
                val originY = ground.y - ppm * 0.008f
                for (i in 0 until 8) {
                    val phase = (t * 20f + i * 0.09f) % 1f
                    val h = MathX.hash01(i + slot.ordinal * 13, (t * 24f).toInt())
                    val along = alongSign * ppm * (0.02f + phase * (0.55f + h * 0.25f))
                    val x = originX + along
                    val y = originY + ppm * ((h - 0.5f) * 0.03f)
                    val r = ppm * (0.014f + h * 0.022f) * core
                    val hot = lerp(Color(0xFFFFF4C2), Color(0xFFFF7A28), h)
                    drawCircle(
                        hot.copy(alpha = 0.38f * night * (1f - phase) * glow),
                        r * 2.4f,
                        Offset(x, y),
                        blendMode = BlendMode.Plus
                    )
                    drawCircle(
                        hot.copy(alpha = 0.72f * night * (1f - phase)),
                        r,
                        Offset(x, y)
                    )
                }
            }
        }
        bloom(ComponentSlot.TIRE_REAR, pose.rearWheel, pose.rearGround, pose.rearWheelR, pose.rearSlopeDeg)
        bloom(ComponentSlot.TIRE_FRONT, pose.frontWheel, pose.frontGround, pose.frontWheelR, pose.frontSlopeDeg)
    }

    private fun wave(x: Float): Float = MathX.approxSin(x) * 0.5f + 0.5f

    companion object {
        /**
         * Koľko metrov sveta sa vzorkuje za okraj obrazovky.
         *
         * Nesmie to byť pár metrov: hĺbkové pásy sa premietajú smerom k úbežníku,
         * ktorý leží mimo obrazovky, takže vzdialená hrana cesty a lúky sa
         * posunie o stovky pixelov dovnútra záberu. Pri malej rezerve tak bolo
         * pri pravom okraji vidieť, ako trať „končí“ a dostavuje sa.
         */
        private const val EDGE_MARGIN = 24f
        /** Minimálna výška mostovky, pri ktorej vyčistíme roklinu od kulís. */
        private const val BRIDGE_PROP_CLEARANCE_M = 0.45f
        /** Vzorky vodnej hladiny pod mostom. */
        private const val BRIDGE_WATER_MAX = 48

        /**
         * Koľko metrov okolo budovy ostane bez kulís. Najširšia budova
         * (autoservis) má 3.8 m, takže od stredu siaha 1.9 m; zvyšok je na
         * korunu stromu, ktorá je širšia než jeho kmeň.
         */
        private const val BUILDING_CLEAR_M = 4.5f
        private const val MAX_POINTS = 260
        private const val RAIN_DROPS = 90
        private const val SNOW_FLAKES = 70
        /** Vietor v daždi a snežení – konštantný, nezávislý od rýchlosti auta. */
        private const val RAIN_DRIFT = 330f
        private const val RAIN_SLANT = 0.26f
        private const val SNOW_DRIFT = 70f
        /**
         * Rýchlosť vrstiev pozadia v px na meter jazdy. Terén sa hýbe rýchlosťou
         * ppm (~30 px/m), takže aj najbližšia vrstva ostáva zreteľne „ďaleko“.
         */
        private const val BACKDROP_FAR_K = 0.020f
        private const val BACKDROP_MID_K = 0.060f
        private const val BACKDROP_NEAR_K = 0.140f
        /** Rovnaké koeficienty v poradí far, mid, near – pre prírastkový posun. */
        private val BACKDROP_K = floatArrayOf(BACKDROP_FAR_K, BACKDROP_MID_K, BACKDROP_NEAR_K)
        /** O koľko horizontu je spodok oblohy pod horizontom (schová sa za lúku). */
        private const val BACKDROP_SINK = 0.03f
        /** Základná výška vzdialeného horizontu; lokálny profil cesty ju nemení. */
        private const val BACKDROP_HORIZON = 0.52f
        /** Výška pásov nad lúkou ako podiel obrazovky – nie celá scéna. */
        private const val BACKDROP_FAR_HEIGHT = 0.44f
        private const val BACKDROP_MID_HEIGHT = 0.40f
        private const val BACKDROP_NEAR_HEIGHT = 0.38f
        /** Mid/near siahajú pod svoj kotviaci bod, aby prekryli far. */
        private const val BACKDROP_LAYER_OVERLAP = 0.03f
        private enum class BackdropPass { SKY, FAR, HORIZON, LANDSCAPE }
        /**
         * Vyrovná iba časť look-aheadu. Pri 0.90 projekčný stred počas prudkého
         * zrýchlenia predbehol vyhladenú kameru a auto na tablete ušlo doprava.
         */
        private const val CAR_LOOK_AHEAD_COMPENSATION = 0.66f

        /** Piesočná búrka: letiace zrná a široké vlny prachu. */
        private const val SAND_GRAINS = 110
        private const val DUST_SHEETS = 7
        /** Rozostup ozdôb na ceste pri udalostiach (m). */
        private const val DECAL_CELL = 3.2f
        /** Mriežka textúry vozovky a naplavenín (m) – pevná vo svete. */
        private const val PATCH_CELL = 0.85f
        private const val POTHOLE_CELL = 2.6f
        /** Mriežka stôp po jazde a záplat vozovky (m). */
        private const val HISTORY_CELL = 4.4f
        private const val TAILWIND_STREAKS = 26
        private const val PLAY_DEPTH_PROXY = 1.4f

        /** Zlatá HUD farba – zvýraznenie vybranej vetvy na smerovke. */
        private val ACCENT = Color(0xFFD2AE63)
        /** Koľko stôp po preklze si pamätáme a ako dlho vydržia (s). */
        private const val SKID_MAX = 96
        private const val SKID_LIFE = 7f

        /** Od akej výšky nad terénom považujeme úsek za most. */
        private const val MIN_CLEARANCE = 0.35f
        /** Rozostup pilierov v metroch sveta – nezávislý od zoomu a rozlíšenia. */
        private const val PILLAR_SPACING_M = 9f
        /** Rozostup stĺpikov zábradlia (m). */
        private const val RAIL_POST_SPACING_M = 3.2f

        /** Najhlbšia vrstva kulís – lúka musí siahať aspoň sem. */
        const val SCENERY_BACK_DEPTH = GameConfig.ROAD_DEPTH + 1.7f
    }
}
