package sk.kubis.endlessdrive

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import sk.kubis.endlessdrive.domain.model.BiomeType
import sk.kubis.endlessdrive.domain.model.BranchStyle
import sk.kubis.endlessdrive.domain.model.DebugOptions
import sk.kubis.endlessdrive.domain.model.RoadPaving
import sk.kubis.endlessdrive.domain.model.RoadFeature
import sk.kubis.endlessdrive.domain.model.RoadSurface
import sk.kubis.endlessdrive.game.GameEngine
import sk.kubis.endlessdrive.game.DayCycle
import sk.kubis.endlessdrive.game.world.SurfacePatch
import sk.kubis.endlessdrive.ui.game.BackdropCatalog
import sk.kubis.endlessdrive.ui.game.GameAssets
import sk.kubis.endlessdrive.ui.game.GameRenderer
import java.io.File

/** Decode real PNGs and render actual game scenes for visual review, without audio or timers. */
class BackgroundArtTest {
    @Test fun layeredArtworkDecodesAndRenders() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        for (biome in listOf(BiomeType.RURAL, BiomeType.ALPINE)) {
            val spec = BackdropCatalog.specs.getValue(biome)
            for ((resource, transparent) in listOf(spec.far to false, spec.mid to true, spec.near to true)) {
                val bitmap = BitmapFactory.decodeResource(context.resources, resource)
                assertTrue("$biome must have landscape artwork", bitmap.width > bitmap.height)
                val alpha = bitmap.getPixel(bitmap.width / 2, 0).ushr(24)
                assertEquals("$biome layer must preserve sky/alpha", if (transparent) 0 else 255, alpha)
                bitmap.recycle()
            }
        }
        val assets = GameAssets(context)
        val output = File(context.getExternalFilesDir(null), "stylized-review").apply { mkdirs() }
        val base = GameEngine(41L, 0f, DebugOptions(allComponents = true, fullFluids = true, fullBody = true)).snapshot()
        for (biome in listOf(BiomeType.RURAL, BiomeType.ALPINE, BiomeType.FOREST_ALIVE,
            BiomeType.DESERT, BiomeType.INDUSTRIAL, BiomeType.SANDSTORM, BiomeType.DUST_STORM)) {
            val style = BranchStyle.entries.first { it.biome == biome }
            val times = when (biome) {
                BiomeType.ALPINE -> listOf("day" to 0.5f, "night" to 0.0f)
                BiomeType.FOREST_ALIVE -> listOf(
                    "day" to 0.5f,
                    "sunrise" to DayCycle.at(6, 50)
                )
                else -> listOf("day" to 0.5f)
            }
            for ((timeLabel, time) in times) {
                val engine = GameEngine.restore(base.copy(timeOfDay = time,
                    segment = base.segment.copy(style = style)), 0f)
                val renderer = GameRenderer(assets)
                val bitmap = Bitmap.createBitmap(1280, 720, Bitmap.Config.ARGB_8888)
                CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bitmap.asImageBitmap()), Size(1280f, 720f)) {
                    with(renderer) { draw(engine) }
                }
                File(output, "${biome.name.lowercase()}-$timeLabel.png")
                    .outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                assertTrue("Rendered scene must be opaque", bitmap.getPixel(640, 360).ushr(24) == 255)
                if (biome == BiomeType.DESERT) assertNoVerticalSkySeam(bitmap, "desert")
                bitmap.recycle()
            }
        }
        // All driveable materials and a real sloping terrain section, beyond the flat tutorial.
        for (paving in RoadPaving.entries) {
            val snapshot = base.copy(car = base.car.copy(x = 220f),
                segment = base.segment.copy(paving = paving, features = listOf(RoadFeature.HILLS)))
            val engine = GameEngine.restore(snapshot, 0f)
            engine.setScreenHeight(720f)
            engine.car.snapToGround(engine.segment.heightAtWorld(engine.car.x),
                engine.segment.slopeAtLocal(engine.car.x - engine.segment.worldOrigin))
            engine.camera.snapTo(engine.car.x, engine.car.y)
            val renderer = GameRenderer(assets)
            val bitmap = Bitmap.createBitmap(1280, 720, Bitmap.Config.ARGB_8888)
            CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bitmap.asImageBitmap()), Size(1280f, 720f)) {
                with(renderer) { draw(engine) }
            }
            File(output, "surface-${paving.name.lowercase()}.png").outputStream()
                .use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }

        // Súvislé nánosy na ceste: kontrolujeme ich ako celý pás, nie izolovanú kaluž.
        for (surface in RoadSurface.entries.filter { it != RoadSurface.ASPHALT }) {
            val engine = GameEngine.restore(
                base.copy(
                    car = base.car.copy(x = 220f),
                    segment = base.segment.copy(features = listOf(RoadFeature.HILLS))
                ),
                0f
            )
            engine.segment.patches += SurfacePatch(surface, 185f, 275f)
            engine.setScreenHeight(720f)
            engine.car.snapToGround(
                engine.segment.heightAtWorld(engine.car.x),
                engine.segment.slopeAtLocal(engine.car.x - engine.segment.worldOrigin)
            )
            engine.camera.snapTo(engine.car.x, engine.car.y)
            val renderer = GameRenderer(assets)
            val bitmap = Bitmap.createBitmap(1280, 720, Bitmap.Config.ARGB_8888)
            CanvasDrawScope().draw(
                Density(1f), LayoutDirection.Ltr, Canvas(bitmap.asImageBitmap()), Size(1280f, 720f)
            ) {
                with(renderer) { draw(engine) }
            }
            File(output, "patch-${surface.name.lowercase()}.png").outputStream()
                .use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }

    /** Far vrstva je nepriehľadná obloha – 1 px medzera medzi dlaždicami je v nej vlas. */
    private fun assertNoVerticalSkySeam(bitmap: Bitmap, label: String) {
        val skyBottom = (bitmap.height * 0.28f).toInt().coerceAtLeast(8)
        var worstRows = 0
        var worstX = 0
        for (x in 2 until bitmap.width - 2) {
            var darkRows = 0
            for (y in 1 until skyBottom) {
                val c = bitmap.getPixel(x, y)
                val sides = (
                    Color.red(bitmap.getPixel(x - 1, y)) + Color.green(bitmap.getPixel(x - 1, y)) +
                        Color.blue(bitmap.getPixel(x - 1, y)) +
                        Color.red(bitmap.getPixel(x + 1, y)) + Color.green(bitmap.getPixel(x + 1, y)) +
                        Color.blue(bitmap.getPixel(x + 1, y))
                    ) / 2
                val mid = Color.red(c) + Color.green(c) + Color.blue(c)
                if (sides - mid >= 28) darkRows++
            }
            if (darkRows > worstRows) {
                worstRows = darkRows
                worstX = x
            }
        }
        assertTrue(
            "$label sky seam at x=$worstX covering $worstRows/$skyBottom rows",
            worstRows < skyBottom / 5
        )
    }
}
