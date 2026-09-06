package sk.kubis.endlessdrive

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import sk.kubis.endlessdrive.core.GameConfig
import sk.kubis.endlessdrive.domain.model.ComponentSlot
import sk.kubis.endlessdrive.domain.model.DebugOptions
import sk.kubis.endlessdrive.domain.model.RoadFeature
import sk.kubis.endlessdrive.domain.model.RoadPaving
import sk.kubis.endlessdrive.domain.model.SedanSpec
import sk.kubis.endlessdrive.game.DepthProjection
import sk.kubis.endlessdrive.game.GameEngine
import sk.kubis.endlessdrive.game.save.RunSnapshot
import sk.kubis.endlessdrive.game.car.TireInjury
import sk.kubis.endlessdrive.ui.game.GameAssets
import sk.kubis.endlessdrive.ui.game.GameRenderer
import sk.kubis.endlessdrive.ui.game.WheelContactFx
import java.io.File
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Scény iskier a stôp do PNG (stylized-review) a kontrola, že ryhy sedia na svahu
 * a predné iskry pri cúvaní naozaj sú.
 */
class WheelContactVisualTest {

    @Test
    fun rimAndRubberMarksFollowSlopeAndReverseFrontSparksExist() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val assets = GameAssets(context)
        val output = File(context.getExternalFilesDir(null), "stylized-review").apply { mkdirs() }
        val base = GameEngine(
            41L, 0f,
            DebugOptions(allComponents = true, fullFluids = true, fullBody = true)
        ).snapshot()

        val rimFwd = renderScene(
            assets, base, speed = 11f, shredded = true, seedRim = true, seedRubber = false
        )
        File(output, "wheel-fx-rim-forward-slope.png").outputStream()
            .use { rimFwd.bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        assertMarksTrackTerrain(rimFwd, brighterOnSlope = true)

        val rimRev = renderScene(
            assets, base, speed = -6.5f, shredded = true, seedRim = true, seedRubber = false
        )
        File(output, "wheel-fx-rim-reverse-slope.png").outputStream()
            .use { rimRev.bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        assertMarksTrackTerrain(rimRev, brighterOnSlope = true)
        assertReverseFrontSparks(rimRev)

        val rubber = renderScene(
            assets, base, speed = 9f, shredded = false, seedRim = false, seedRubber = true
        )
        File(output, "wheel-fx-rubber-forward-slope.png").outputStream()
            .use { rubber.bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        assertMarksTrackTerrain(rubber, brighterOnSlope = false)

        val inflated = renderScene(
            assets, base, speed = -5f, shredded = false, seedRim = false, seedRubber = false
        )
        File(output, "wheel-fx-inflated-reverse-slope.png").outputStream()
            .use { inflated.bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        assertTrue(
            "nafuknuté kolesá pri cúvaní nesmú iskriť",
            sparkCountNearFront(inflated) == 0
        )

        rimFwd.bitmap.recycle()
        rimRev.bitmap.recycle()
        rubber.bitmap.recycle()
        inflated.bitmap.recycle()
    }

    private data class Scene(
        val bitmap: Bitmap,
        val engine: GameEngine,
        val marks: List<Float>
    )

    private fun renderScene(
        assets: GameAssets,
        base: RunSnapshot,
        speed: Float,
        shredded: Boolean,
        seedRim: Boolean,
        seedRubber: Boolean
    ): Scene {
        val snapshot = base.copy(
            timeOfDay = 0.5f,
            car = base.car.copy(x = 220f, speed = speed),
            segment = base.segment.copy(
                paving = RoadPaving.ASPHALT,
                features = listOf(RoadFeature.HILLS)
            )
        )
        val engine = GameEngine.restore(snapshot, 0f)
        engine.setScreenHeight(H.toFloat())
        val wx0 = steepestWx(engine, engine.car.x)
        engine.car.x = wx0
        engine.car.speed = speed
        engine.car.snapToGround(
            engine.segment.heightAtWorld(engine.car.x),
            engine.segment.slopeAtLocal(engine.car.x - engine.segment.worldOrigin)
        )
        engine.camera.snapTo(engine.car.x, engine.car.y)
        if (shredded) {
            engine.car.parts[ComponentSlot.TIRE_FRONT]!!.injury = TireInjury.SHREDDED
            engine.car.parts[ComponentSlot.TIRE_REAR]!!.injury = TireInjury.SHREDDED
        }
        val renderer = GameRenderer(assets)
        val bitmap = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        paint(renderer, engine, bitmap)
        val behind = if (speed >= 0f) -1f else 1f
        val marks = ArrayList<Float>()
        var i = 1
        while (i <= 10) {
            val wx = engine.car.x + behind * i * 0.85f
            marks += wx
            if (seedRim) renderer.seedSkidMark(wx, 0.95f, 0f, true)
            if (seedRubber) renderer.seedSkidMark(wx, 0.9f, 0f, false)
            i++
        }
        paint(renderer, engine, bitmap)
        return Scene(bitmap, engine, marks)
    }

    private fun paint(renderer: GameRenderer, engine: GameEngine, bitmap: Bitmap) {
        CanvasDrawScope().draw(
            Density(1f),
            LayoutDirection.Ltr,
            Canvas(bitmap.asImageBitmap()),
            Size(W.toFloat(), H.toFloat())
        ) {
            with(renderer) { draw(engine) }
        }
    }

    private fun steepestWx(engine: GameEngine, around: Float): Float {
        var best = around
        var bestAbs = 0f
        var x = around - 30f
        while (x < around + 90f) {
            val s = abs(
                engine.segment.heightAtWorld(x + 1.6f) - engine.segment.heightAtWorld(x)
            ) / 1.6f
            if (s > bestAbs) {
                bestAbs = s
                best = x + 0.8f
            }
            x += 1.6f
        }
        assertTrue("scéna potrebuje kopec, sklon $bestAbs", bestAbs > 0.12f)
        return best
    }

    private fun projection(engine: GameEngine): DepthProjection {
        val cam = engine.camera
        val lookAheadPx = engine.car.speed.coerceAtLeast(0f) *
            GameConfig.CAMERA_LOOK_AHEAD * cam.ppm
        val halfW = W * GameConfig.CAR_SCREEN_X + lookAheadPx * 0.66f
        val depth = DepthProjection()
        depth.begin(
            cam.x + cam.shakeX, cam.y + cam.shakeY, cam.ppm,
            halfW, H / 2f, cam.pitch
        )
        return depth
    }

    private fun roadXy(depth: DepthProjection, engine: GameEngine, wx: Float): Pair<Float, Float> {
        val gy = engine.segment.heightAtWorld(wx)
        val d = GameConfig.RUT_NEAR_DEPTH
        return depth.atX(depth.frontX(wx), d) to depth.atY(depth.frontY(gy), d)
    }

    private fun assertMarksTrackTerrain(scene: Scene, brighterOnSlope: Boolean) {
        val depth = projection(scene.engine)
        val ppm = scene.engine.camera.ppm
        val half = WheelContactFx.skidHalfLengthPx(ppm, brighterOnSlope)
        var tilted = 0
        for (wx in scene.marks) {
            val (cx, cy) = roadXy(depth, scene.engine, wx)
            val dx = 0.9f
            val (x0, y0) = roadXy(depth, scene.engine, wx - dx)
            val (x1, y1) = roadXy(depth, scene.engine, wx + dx)
            val roadDy = y1 - y0
            if (abs(roadDy) < 3.5f || abs(x1 - x0) < 4f) continue
            val slopeRad = WheelContactFx.screenSlopeRad(x0, y0, x1, y1)
            val (a, b) = WheelContactFx.skidMarkEnds(cx, cy, slopeRad, half)
            assertTrue(
                "konce ryhy musia meniť Y so svahom (dY=${b.second - a.second}, road=$roadDy)",
                abs(b.second - a.second) > 2.4f &&
                    (b.second - a.second) * roadDy > 0f
            )
            val sloped = sampleLuma(scene.bitmap, a.first, a.second, b.first, b.second)
            val flat = sampleLuma(scene.bitmap, cx - half, cy, cx + half, cy)
            if (brighterOnSlope) {
                assertTrue(
                    "svetlá ryha má sedieť na svahu (slope=$sloped flat=$flat wx=$wx)",
                    sloped > flat + 4f
                )
            } else {
                assertTrue(
                    "guma má sedieť na svahu (slope=$sloped flat=$flat wx=$wx)",
                    sloped < flat - 3f
                )
            }
            tilted++
        }
        assertTrue("aspoň pár stôp musí kopírovať kopec, tilted=$tilted", tilted >= 3)
    }

    private fun sampleLuma(
        bitmap: Bitmap,
        x0: Float,
        y0: Float,
        x1: Float,
        y1: Float
    ): Float {
        var sum = 0
        var n = 0
        for (i in 0..14) {
            val t = i / 14f
            val cx = x0 + (x1 - x0) * t
            val cy = y0 + (y1 - y0) * t
            for (ox in -1..1) for (oy in -1..1) {
                val x = (cx + ox).roundToInt()
                val y = (cy + oy).roundToInt()
                if (x !in 0 until W || y !in 0 until H) continue
                val c = bitmap.getPixel(x, y)
                sum += (Color.red(c) + Color.green(c) + Color.blue(c)) / 3
                n++
            }
        }
        return if (n == 0) 0f else sum.toFloat() / n
    }

    private fun assertReverseFrontSparks(scene: Scene) {
        val count = sparkCountNearFront(scene)
        assertTrue("pri cúvaní musia byť predné iskry, našlo sa $count", count >= 8)
        val depth = projection(scene.engine)
        val ppm = scene.engine.camera.ppm
        val frontWx = scene.engine.car.x + SedanSpec.wheelOffsetX
        val (contactX, contactY) = roadXy(depth, scene.engine, frontWx)
        val originX = WheelContactFx.sparkOriginX(contactX, scene.engine.car.speed, ppm, true)
        assertTrue("origin má byť vzadu v smere cúvania", originX < contactX - 2f)
        val box = sparkCount(
            scene.bitmap,
            originX.roundToInt() - 10,
            contactY.roundToInt() - 14,
            originX.roundToInt() + 18,
            contactY.roundToInt() + 16
        )
        assertTrue("iskry pri nábežnom kontakte predku, count=$box", box >= 4)
    }

    private fun sparkCountNearFront(scene: Scene): Int {
        val depth = projection(scene.engine)
        val frontWx = scene.engine.car.x + SedanSpec.wheelOffsetX
        val (contactX, contactY) = roadXy(depth, scene.engine, frontWx)
        return sparkCount(
            scene.bitmap,
            contactX.roundToInt() - 48,
            contactY.roundToInt() - 22,
            contactX.roundToInt() + 36,
            contactY.roundToInt() + 20
        )
    }

    private fun sparkCount(bitmap: Bitmap, x0: Int, y0: Int, x1: Int, y1: Int): Int {
        var n = 0
        val l = x0.coerceIn(0, W - 1)
        val r = x1.coerceIn(0, W - 1)
        val t = y0.coerceIn(0, H - 1)
        val b = y1.coerceIn(0, H - 1)
        for (x in l..r) for (y in t..b) {
            if (isSparkPixel(bitmap.getPixel(x, y))) n++
        }
        return n
    }

    private fun isSparkPixel(c: Int): Boolean {
        val r = Color.red(c)
        val g = Color.green(c)
        val b = Color.blue(c)
        val orange = r > 200 && g in 70..170 && b < 90 && r - g > 40
        val cream = r > 230 && g > 180 && b in 80..180 && r - b > 40 && g - b > 20
        return orange || cream
    }

    companion object {
        private const val W = 1280
        private const val H = 720
    }
}
