package sk.kubis.endlessdrive.ui.game

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import sk.kubis.endlessdrive.core.GameConfig
import sk.kubis.endlessdrive.core.MathX
import sk.kubis.endlessdrive.domain.model.BiomeType
import sk.kubis.endlessdrive.domain.model.ComponentSlot
import sk.kubis.endlessdrive.domain.model.RoadFeature
import sk.kubis.endlessdrive.domain.model.RoadPaving
import sk.kubis.endlessdrive.domain.model.RoadSurface
import sk.kubis.endlessdrive.domain.model.SedanSpec
import sk.kubis.endlessdrive.game.Camera2D
import sk.kubis.endlessdrive.game.DepthProjection
import sk.kubis.endlessdrive.game.GameEngine
import sk.kubis.endlessdrive.game.event.RoadEvent
import sk.kubis.endlessdrive.game.world.RoadSegment
import sk.kubis.endlessdrive.game.world.BiomeBlend
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
    private val scenery = SceneryPainter(assets.sedan, assets.wreckSprites)
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
    private var lastFrontSkidX = Float.NaN
    private var lastRearSkidX = Float.NaN
    /** Renderer prežije reštart jazdy – stopy z predošlej sa musia zahodiť. */
    private var lastEngine: GameEngine? = null

    /** Výška mostovky nad terénom pre každú vzorku (0 = žiadny most). */
    private val bridgeClearance = FloatArray(MAX_POINTS)

    /** Nazbieraný posun vrstiev pozadia (far, mid, near) v pixeloch. */
    private val backdropShift = FloatArray(3)
    private var lastBackdropX = Float.NaN

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
        // Horizont pozadia je vysoko a s cestou sa nehýbe – keď klesal spolu
        // s terénom, zníženie cesty neodhalilo nič. Pás medzi ním a lúkou
        // vypĺňa farba vzdialenej zeme, takže diera vzniknúť nemôže.
        val horizonY = size.height * 0.62f

        // Kreslené pozadie má dnes každý bióm a nesie si vlastnú oblohu aj
        // krajinu – procedurálne vrstvy by sa cezeň len bili.
        updateBackdropScroll(cam, engine.car.x)
        val backdrop = assets.backdropFor(environment.from)
        drawParallaxBackdrop(backdrop, cam, horizonY, day, engine.timeOfDay)
        if (environment.amount > 0.001f && environment.to != environment.from) {
            drawParallaxBackdrop(
                assets.backdropFor(environment.to), cam, horizonY, day, engine.timeOfDay,
                opacity = environment.amount,
                drawCelestial = false
            )
        }
        with(sky) {
            drawStarfield(day, cam.x, horizonY)
            drawHaze(
                horizonY, day, environment.from, strength = 0.4f,
                nextBiome = environment.to, transition = environment.amount
            )
        }

        collectTerrain(engine, cam.ppm, size.width, halfW)
        // Auto nie je v strede, takže doprava treba dohliadnuť ďalej než doľava.
        val worldW = size.width / cam.ppm
        val screenOrigin = halfW / size.width
        val visibleFrom = cam.x - worldW * screenOrigin - EDGE_MARGIN
        val visibleTo = cam.x + worldW * (1f - screenOrigin) + EDGE_MARGIN
        // Kulisy stoja na teréne, nie na mostovke.
        val heightAt: (Float) -> Float = { wx -> groundFor(engine, wx) }

        // Poradie je dôležité: najprv zem, potom kulisy (stoja na nej), až potom cesta.
        drawGround(engine.segment, environment, engine.winterAmount, day)
        // Kde stojí budova, tam kulisa nerastie.
        val builtOn: (Float) -> Boolean = { wx ->
            engine.segment.buildingOccupies(wx, BUILDING_CLEAR_M)
        }
        with(scenery) {
            drawBackProps(
                visibleFrom, visibleTo, engine.segment::biomeBlendAtWorld,
                day, depth, heightAt, builtOn
            )
        }
        drawBridges(engine, day)
        drawRoadSurface(engine.segment, day)
        drawRoadHistory(engine.segment, day)
        drawSurfacePatches(engine.segment, day)
        drawRoadEventDecals(engine, day, visibleFrom, visibleTo)
        recordSkid(engine)
        drawSkidMarks(engine, day)
        drawBuildings(engine, day)
        drawCarShadow(engine)
        drawCar(engine)
        drawCarEffects(engine, day)
        with(scenery) {
            drawFrontProps(
                visibleFrom, visibleTo, engine.segment::biomeBlendAtWorld,
                day, depth, heightAt
            )
        }
        drawNight(engine, day)
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
            val seed = MathX.hash01(i, 211)
            val speed = 260f + seed * 420f
            val x = w - ((t * speed + seed * w * 2.2f) % (w * 1.8f))
            val y = h * (0.10f + MathX.hash01(i, 307) * 0.72f)
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
            val seedX = MathX.hash01(i, 61)
            val seedY = MathX.hash01(i, 97)
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

    /**
     * Zadný vietor: šmuhy a lístie letiace dopredu rýchlejšie než svet.
     * Bez toho bol TAILWIND len štítok v HUD, ktorý sa nedal vidieť.
     */
    private fun DrawScope.drawTailwind(engine: GameEngine, day: Float) {
        if (!engine.hasEvent(RoadEvent.TAILWIND)) return
        val t = engine.elapsed
        val w = size.width
        val h = size.height
        val col = shade(Color(0xFFE8F0E4), day)
        val leaf = shade(Color(0xFFB08A4A), day)

        for (i in 0 until TAILWIND_STREAKS) {
            val seed = MathX.hash01(i, 137)
            val seedY = MathX.hash01(i, 263)
            val seedA = MathX.hash01(i, 419)
            val speed = 700f + seed * 900f
            val x = ((seedY * w) + (t * speed) % (w * 1.4f)) % (w * 1.4f) - w * 0.2f
            // Vietor je turbulentný: každá šmuha má vlastný sklon a v čase sa
            // vlní. Rovnobežné vodorovné čiary vyzerali ako hrebeň, nie vietor.
            val drift = MathX.approxSin(t * (0.8f + seedA) + i * 1.7f)
            val y = h * (0.14f + seedY * 0.70f) + drift * h * 0.035f
            val len = w * (0.04f + seed * 0.11f)
            val tilt = ((seedA - 0.5f) * 0.55f) + drift * 0.18f
            drawLine(
                col.copy(alpha = 0.08f + seed * 0.18f),
                Offset(x, y),
                Offset(x + len, y + len * tilt),
                strokeWidth = 1f + seed * 2f,
                cap = StrokeCap.Round
            )
            // Občas lístok, ktorý sa v prúde prevracia – ukáže smer aj vír.
            if (seed > 0.66f) {
                val spin = MathX.approxSin(t * 3.1f + i.toFloat())
                val lw = w * 0.013f
                drawOval(
                    leaf.copy(alpha = 0.55f),
                    topLeft = Offset(x + len, y + len * tilt),
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
     * Vrstvy nerastú s blízkosťou: `far` je celá obloha a musí prekryť obrazovku,
     * `mid` a `near` sú len pásy nad horizontom. Kreslené naveľko by z nich boli
     * kmene cez pol obrazovky – teda popredie, nie pozadie.
     */
    private fun DrawScope.drawParallaxBackdrop(
        backdrop: BiomeBackdrop,
        cam: Camera2D,
        horizonY: Float,
        day: Float,
        timeOfDay: Float,
        opacity: Float = 1f,
        drawCelestial: Boolean = true
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
        val haze = lerp(Color(0xFF2A3348), backdropHaze(day), day.coerceIn(0f, 1f))

        val ppm = cam.ppm
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
        // Zvislá odozva je zámerne slabá – hráč chce mať rovnaký záber na
        // pozadie po celý čas, nie horizont, ktorý na každom kopci ujde preč.
        val riseM = cam.y + cam.shakeY - BACKDROP_REF_Y
        val pitchPx = -cam.pitch * GameConfig.DEPTH_PITCH_VP_Y * (size.height * 0.5f)

        fun baseFor(depthK: Float, follow: Float, sink: Float): Float {
            val rise = (riseM * ppm * depthK * BACKDROP_RISE_DAMP)
                .coerceIn(-size.height * 0.035f, size.height * 0.035f)
            return horizonY + sink + rise + pitchPx * follow * BACKDROP_RISE_DAMP
        }

        val farBase = baseFor(BACKDROP_FAR_K, 1.0f, horizonY * BACKDROP_SINK)
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
        drawLayer(
            backdrop.far, backdropShift[0],
            baseY = farBase,
            height = horizonY * 1.18f,
            tint = night, haze = haze, hazeAmount = 0f, opacity = opacity
        )
        // Slnko a mesiac idú nad oblohu, ale pod siluety – inak by z hry zmizol
        // denný cyklus. Kde ich má kresba namaľované, druhé nedávame.
        if (drawCelestial && !backdrop.bakedSun) {
            with(sky) { drawCelestialOver(timeOfDay, day, horizonY + pitchPx) }
        }
        drawLayer(
            backdrop.mid, backdropShift[1],
            baseY = baseFor(BACKDROP_MID_K, 0.90f, size.height * 0.015f),
            height = size.height * BACKDROP_MID_HEIGHT,
            tint = night, haze = haze, hazeAmount = 0.42f, opacity = opacity
        )
        drawLayer(
            backdrop.near, backdropShift[2],
            baseY = baseFor(BACKDROP_NEAR_K, 0.80f, size.height * 0.045f),
            height = size.height * BACKDROP_NEAR_HEIGHT,
            tint = night, haze = haze, hazeAmount = 0.20f, opacity = opacity
        )
    }

    /** Posun parallaxu sa aktualizuje raz za snímku aj počas kreslenia dvoch biomov. */
    private fun updateBackdropScroll(cam: Camera2D, carX: Float) {
        val dx = if (lastBackdropX.isNaN()) 0f else carX - lastBackdropX
        lastBackdropX = carX
        for (i in backdropShift.indices) {
            backdropShift[i] += dx * cam.ppm * BACKDROP_K[i]
        }
    }

    /** Farba, do ktorej vrstvy blednú – teplá na púšti, chladná inde. */
    private fun backdropHaze(day: Float): Color =
        lerp(Color(0xFFAEBAC4), Color(0xFFE8EEF2), day.coerceIn(0f, 1f))

    /** [scrolled] je nazbieraný posun vrstvy v pixeloch. */
    private fun DrawScope.drawLayer(
        image: ImageBitmap,
        scrolled: Float,
        baseY: Float,
        height: Float,
        tint: Color,
        haze: Color,
        hazeAmount: Float,
        opacity: Float
    ) {
        // Šírka z pomeru strán – pozadie sa nesmie deformovať.
        val w = height * image.width / image.height.toFloat()
        if (w < 1f || height < 1f) return
        val top = kotlin.math.floor(baseY - height).toInt()
        // Dlaždica o pixel širšia, než vychádza – inak medzi nimi presvitá škára
        // z toho, ako sa float pozícia zaokrúhli na celé pixely.
        val tileW = kotlin.math.ceil(w).toInt() + 1
        val tileH = kotlin.math.ceil(height).toInt().coerceAtLeast(1)
        val firstTile = kotlin.math.floor(scrolled / w).toInt()
        val shift = (scrolled % w + w) % w
        val src = IntSize(image.width, image.height)

        var x = -shift
        // Parita patrí dlaždici vo svete, nie jej poradiu v aktuálnom zábere.
        // Inak sa po každom celom posune prvá dlaždica znovu otočila na začiatok.
        var tile = firstTile
        while (x < size.width) {
            val dst = IntOffset(kotlin.math.floor(x).toInt(), top)
            // Predlohy nie sú maľované ako periodické textúry. Striedanie ich
            // zrkadlených kópií spojí pri okraji rovnaké pixely a odstráni
            // tvrdý šev pri každom ďalšom modeli krajiny.
            scale(scaleX = if (tile % 2 == 0) 1f else -1f, scaleY = 1f, pivot = Offset(x + w * 0.5f, 0f)) {
                drawImage(
                    image = image,
                    srcOffset = IntOffset.Zero,
                    srcSize = src,
                    dstOffset = dst,
                    dstSize = IntSize(tileW, tileH),
                    alpha = opacity,
                    colorFilter = ColorFilter.tint(tint, androidx.compose.ui.graphics.BlendMode.Modulate)
                )
                if (hazeAmount > 0.01f) {
                    // Druhý prechod farbí presne tvar siluety (SrcIn), takže vrstva
                    // vybledne do oblohy a pritom si nechá svoje odtiene.
                    drawImage(
                        image = image,
                        srcOffset = IntOffset.Zero,
                        srcSize = src,
                        dstOffset = dst,
                        dstSize = IntSize(tileW, tileH),
                        alpha = hazeAmount * opacity,
                        colorFilter = ColorFilter.tint(haze, androidx.compose.ui.graphics.BlendMode.SrcIn)
                    )
                }
            }
            x += w
            tile++
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
            BiomeType.INDUSTRIAL -> Color(0xFF6A7460)
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

    private fun grassColor(blend: BiomeBlend, winterAmount: Float, day: Float): Color {
        val region = lerp(biomeGrassColor(blend.from), biomeGrassColor(blend.to), blend.amount)
        return shade(lerp(region, Color(0xFFDCE7EE), winterAmount.coerceIn(0f, 1f)), day)
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
            when {
                winterAmount >= 0.55f -> Color(0xFF6B5340)
                environment.dominant == BiomeType.DESERT -> Color(0xFFA9855A)
                environment.dominant == BiomeType.SANDSTORM -> Color(0xFF9A7B52)
                environment.dominant == BiomeType.FOREST -> Color(0xFF54402D)
                environment.dominant == BiomeType.ALPINE -> Color(0xFF657078)
                else -> Color(0xFF6B5340)
            },
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

        // 2) Lúka siaha až za kulisy, inak by stromy „leteli“ v prázdne.
        buildBand(n, 0f, SCENERY_BACK_DEPTH + 0.5f, groundY)
        val aridAmount = MathX.lerp(
            if (environment.from.arid) 1f else 0f,
            if (environment.to.arid) 1f else 0f,
            environment.amount
        )
        val distanceHaze = lerp(Color(0xFFAFC6D6), Color(0xFFE0C79A), aridAmount)
        val stormAmount = if (environment.dominant == BiomeType.SANDSTORM) 0.42f else 0.26f
        drawPath(band, lerp(grass, shade(distanceHaze, day), stormAmount))
        buildBand(n, 0f, GameConfig.ROAD_DEPTH + 0.7f, groundY)
        drawPath(band, grass)
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

            fun deckY(wx: Float) = depth.atY(depth.frontY(seg.heightAtWorld(wx)), d)
            fun groundYAt(wx: Float) = depth.atY(depth.frontY(seg.groundAtWorld(wx)), d)
            fun sx(wx: Float) = depth.atX(depth.frontX(wx), d)

            // Piliere v pevnom rozostupe, zarovnané na svetovú mriežku – tak
            // stoja stále na tom istom mieste, nech je kamera kdekoľvek.
            val firstPillar = MathX.floorDiv(startW, PILLAR_SPACING_M) + 1
            val lastPillar = MathX.floorDiv(endW, PILLAR_SPACING_M)
            val pillarXs = ArrayList<Float>(8)
            for (p in firstPillar..lastPillar) pillarXs += p * PILLAR_SPACING_M

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
            buildBandWorldDeck(seg, startW, endW, d, thickness)
            drawPath(band, deckColor)
            buildEdgeWorldAt(seg, startW, endW, d, thickness)
            drawPath(edge, deckShadow, style = Stroke(width = (0.12f * ppm).coerceAtLeast(1.5f)))

            // Zábradlie na bližšej strane.
            val railD = GameConfig.VERGE_DEPTH * 0.35f
            val railH = 0.95f * ppm
            val railCol = shade(Color(0xFF8A8578), day)
            buildEdgeWorldAt(seg, startW, endW, railD, -railH)
            drawPath(edge, railCol, style = Stroke(width = (0.10f * ppm).coerceAtLeast(1.5f)))
            val postFirst = MathX.floorDiv(startW, RAIL_POST_SPACING_M) + 1
            val postLast = MathX.floorDiv(endW, RAIL_POST_SPACING_M)
            for (p in postFirst..postLast) {
                val wx = p * RAIL_POST_SPACING_M
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
        val steps = (((toX - fromX) / 1.2f).toInt() + 1).coerceIn(2, 128)
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
        val steps = (((toX - fromX) / 1.2f).toInt() + 1).coerceIn(2, 128)
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

    private fun pavingColor(paving: RoadPaving): Color = when (paving) {
        RoadPaving.ASPHALT -> Color(0xFF3E3E42)
        RoadPaving.CRACKED -> Color(0xFF4A4844)
        RoadPaving.CONCRETE -> Color(0xFF6E6C66)
        RoadPaving.DIRT -> Color(0xFF6A5340)
        RoadPaving.GRAVEL_ROAD -> Color(0xFF6E675C)
        RoadPaving.SAND_TRACK -> Color(0xFFB49A6A)
        RoadPaving.SNOW -> Color(0xFFE6EDF2)
        RoadPaving.PACKED_SNOW -> Color(0xFFCBD7DE)
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
            drawPath(band, shade(base, day).copy(alpha = if (patch.surface == RoadSurface.WATER) 0.72f else 0.92f))

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
        val col = shade(Color(0xFF241D16), day).copy(alpha = 0.30f)
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
        val visualContact = car.visuallyGrounded
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
        val roadBodyY = wheelMidY - wellBelowCenter + sinkPx - rideBiasPx - bodyLiftPx
        var bodyY = if (visualContact) {
            roadBodyY
        } else {
            // Aj pri skutočnom skoku nesmie numerická poloha vykresliť stred
            // karosérie nižšie než jej kontaktnú polohu na vozovke.
            minOf(sy(car.y), roadBodyY)
        }

        var layout = carArtist.layoutAtBody(layers, bodyX, bodyY, depth.ppm)
        val theta = -car.pitch
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
        // Kým sedelo napevno v blatníku, terén sa na ňom neprejavil vôbec –
        // auto kĺzalo po kopcoch ako jeden kus. Teraz hrbol nadvihne to
        // koleso, ktoré naň naozaj vošlo, a karoséria nad ním ostane pokojná.
        //
        // Výchylku obmedzuje zdvih namontovaného pruženia: znížený podvozok
        // sa takmer nehýbe a každú nerovnosť prenesie do karosérie, zvýšený
        // kolesami pekne artikuluje.
        val travelPx = car.suspTravel * depth.ppm * GameConfig.SUSP_VISUAL_GAIN
        fun onRoad(well: Offset, groundY: Float, radius: Float): Offset {
            // Vo vzduchu niet čo sledovať – pruženie sa roztiahne na doraz.
            if (!visualContact) return Offset(well.x, well.y + travelPx * AIR_DROOP)
            val target = groundY - radius
            return Offset(well.x, well.y + (target - well.y).coerceIn(-travelPx, travelPx))
        }
        val rearWheel = onRoad(rearWell, rearGround.y, rearWheelR)
        val frontWheel = onRoad(frontWell, frontGround.y, frontWheelR)

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
        lastFrontSkidX = Float.NaN
        lastRearSkidX = Float.NaN
        backdropShift.fill(0f)
        lastBackdropX = Float.NaN
    }

    /** Zapíše stopu, keď kolesá preklzávajú alebo sú zablokované. */
    private fun recordSkid(engine: GameEngine) {
        val car = engine.car
        val slip = car.wheelSlip
        val moving = kotlin.math.abs(car.speed) > 0.35f
        if (slip < 0.22f || (!moving && !car.wheelsLocked && slip < 0.35f)) {
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
            skidHead = (skidHead + 1) % SKID_MAX
            if (skidCount < SKID_MAX) skidCount++
        }

        // Pri wheelspine značkuje iba hnaná náprava; pri zablokovaných brzdách obe.
        if (car.wheelsLocked || car.drives(ComponentSlot.TIRE_REAR)) stamp(ComponentSlot.TIRE_REAR)
        if (car.wheelsLocked || car.drives(ComponentSlot.TIRE_FRONT)) stamp(ComponentSlot.TIRE_FRONT)
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
            // Dojazdený motor dymí modro a hlavne hustejšie – poškodenie musí byť
            // vidieť na aute, nie len v čísle na paneli.
            val engineHp = (car.parts[ComponentSlot.ENGINE]?.health ?: 1f).coerceIn(0f, 1f)
            val sick = (1f - engineHp / 0.6f).coerceIn(0f, 1f)
            val smoke = shade(lerp(Color(0xFF9AA0A6), Color(0xFF6E7A93), sick), day)
            val pipe = rotated(layout.exhaustX, layout.exhaustY)
            val puffs = 5 + (sick * 5f).toInt()
            for (i in 0 until puffs) {
                val phase = (t * (0.7f + sick * 0.5f) + i * 0.25f) % 1f
                val x = pipe.x - ppm * (0.2f + phase * 2.0f)
                val y = pipe.y - ppm * (0.08f + phase * 0.9f)
                val r = ppm * (0.07f + phase * (0.24f + sick * 0.30f))
                drawCircle(
                    smoke.copy(alpha = (0.26f + sick * 0.34f) * (1f - phase)),
                    r,
                    Offset(x, y)
                )
            }
        }
        drawDamageSigns(engine, day, pose, ppm, t)
        drawEventSigns(engine, day, pose, layout, ppm, t, ::rotated)
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
            // Para spod kapoty – hustejšia než pri prehriatí a s odtieňom do zelena.
            val steam = shade(Color(0xFFDCEDE4), day)
            val hood = rotated(layout.originX + layout.drawW * 0.86f, layout.originY + layout.drawH * 0.45f)
            for (i in 0 until 7) {
                val phase = ((t * 1.1f) + i * 0.14f) % 1f
                val jitter = MathX.hash01(i, (t * 4f).toInt()) - 0.5f
                drawCircle(
                    steam.copy(alpha = 0.5f * (1f - phase)),
                    ppm * (0.12f + phase * 0.7f),
                    Offset(hood.x + jitter * ppm * 0.5f, hood.y - ppm * phase * 2.1f)
                )
            }
        }

        if (engine.hasEvent(RoadEvent.MISFIRE) && engine.car.engineRunning) {
            // Nepravidelné čierne fŕkance – zapaľovanie vynecháva, nie dymí stále.
            val beat = ((t * 3.7f) % 1f)
            if (beat < 0.22f) {
                val pipe = rotated(layout.exhaustX, layout.exhaustY)
                val soot = shade(Color(0xFF2A2622), day)
                for (i in 0 until 4) {
                    val h = MathX.hash01(i, (t * 7f).toInt())
                    drawCircle(
                        soot.copy(alpha = 0.55f * (1f - beat / 0.22f)),
                        ppm * (0.10f + h * 0.22f),
                        Offset(pipe.x - ppm * (0.2f + h * 1.1f), pipe.y - ppm * (h * 0.5f))
                    )
                }
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
        ppm: Float,
        t: Float
    ) {
        val car = engine.car
        // Para spod kapoty – prah je kúsok pod prehriatím, aby varovala včas.
        val heat = ((car.temperature - (GameConfig.OVERHEAT_THRESHOLD - 10f)) / 18f)
            .coerceIn(0f, 1f)
        if (heat > 0.02f) {
            val steam = shade(Color(0xFFE8EEF2), day)
            // Miesto sa berie zo sprite, nie z pevného odsadenia – inak para
            // pri inej veľkosti auta uniká vedľa kapoty.
            val layout = carArtist.layoutAtBody(assets.sedan, pose.bodyX, pose.bodyY, ppm)
            val hood = Offset(
                layout.originX + layout.drawW * 0.84f,
                layout.originY + layout.drawH * 0.44f
            )
            for (i in 0 until 6) {
                val phase = (t * 1.3f + i * 0.17f) % 1f
                val jitter = MathX.hash01(i, (t * 5f).toInt()) - 0.5f
                drawCircle(
                    steam.copy(alpha = 0.42f * heat * (1f - phase)),
                    ppm * (0.10f + phase * 0.55f),
                    Offset(hood.x + jitter * ppm * 0.4f, hood.y - ppm * phase * 1.7f)
                )
            }
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
            // Noc má byť tmavá, nie nehrateľná. Pri 0.45 sa cez závoj plus
            // vignetu nedalo prečítať, kde je cesta a kde priekopa.
            drawRect(
                Color(0xFF0B1226).copy(alpha = night * 0.28f),
                size = Size(size.width, size.height)
            )
        }
        if (!engine.headlightsOn) return

        val car = engine.car
        val headlightHealth = car.parts[ComponentSlot.HEADLIGHT]?.health ?: 0f
        val damageFlicker = if (headlightHealth >= 0.72f) {
            1f
        } else {
            // Poškodený kontakt nebliká pravidelne ako smerovka. V krátkych
            // intervaloch náhodne zoslabne; čím horší diel, tým častejšie.
            val tick = (engine.elapsed * 17f).toInt()
            val roll = MathX.hash01(tick, 0x1A17)
            val dropChance = ((0.72f - headlightHealth) * 0.95f).coerceIn(0f, 0.62f)
            if (roll < dropChance) 0.06f
            else (0.42f + headlightHealth * 0.75f).coerceIn(0.42f, 0.95f)
        }
        val pose = carScreenPose(engine)
        with(carArtist) {
            drawHeadlightBeam(
                bodyX = pose.bodyX,
                bodyY = pose.bodyY,
                angle = car.pitch,
                ppm = depth.ppm,
                proj = depth,
                layers = assets.sedan,
                // Odtrhnutý remeň = nič nedobíja; svetlá to priznajú blikaním.
                strength = (0.3f + night * 0.7f) * damageFlicker * if (engine.hasEvent(RoadEvent.BELT_SNAPPED)) {
                    0.45f + 0.55f * MathX.hash01((engine.elapsed * 11f).toInt(), 733)
                } else 1f,
                highBeam = engine.highBeamsOn,
                braking = engine.brakeInput > 0.25f && car.speed >= 0f,
                roadY = pose.midGround.y
            )
        }
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

        /**
         * Koľko zo zdvihu pruženia sa vo vzduchu roztiahne. Kolesá vtedy
         * visia nadol – auto v skoku pôsobí odľahčene, nie ako doska.
         */
        private const val AIR_DROOP = 0.45f

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
        /** Ako slabo pozadie reaguje na výšku a naklonenie kamery. */
        private const val BACKDROP_RISE_DAMP = 0.25f
        /** Pokojová výška kamery (terén 3.2 m + CAMERA_Y_BIAS) – od nej sa meria stúpanie. */
        private const val BACKDROP_REF_Y = 3.2f + GameConfig.CAMERA_Y_BIAS
        /** O koľko horizontu je spodok oblohy pod horizontom (schová sa za lúku). */
        private const val BACKDROP_SINK = 0.06f
        /** Výška pásov nad horizontom ako podiel obrazovky – nie celá scéna. */
        private const val BACKDROP_MID_HEIGHT = 0.30f
        private const val BACKDROP_NEAR_HEIGHT = 0.34f
        /** Vyrovnáva väčšinu look-aheadu, aby auto na tablete neodišlo mimo záber. */
        private const val CAR_LOOK_AHEAD_COMPENSATION = 0.90f

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
