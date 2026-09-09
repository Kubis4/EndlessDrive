package sk.kubis.endlessdrive

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import sk.kubis.endlessdrive.domain.model.BiomeType
import sk.kubis.endlessdrive.domain.model.BuildingType
import sk.kubis.endlessdrive.game.world.WorldBuilding
import sk.kubis.endlessdrive.ui.game.BuildingPainter
import sk.kubis.endlessdrive.ui.game.BuildingSprites
import sk.kubis.endlessdrive.ui.game.BuildingSeason
import java.io.File

class BuildingArtTest {
    private val sprites by lazy { BuildingSprites(InstrumentationRegistry.getInstrumentation().targetContext) }

    @Test fun artworkHasTransparentBackgroundAndNoChromaKey() {
        val artwork = BuildingSeason.entries.flatMap { season ->
            listOfNotNull(sprites.building(BuildingType.HOUSE, false, season),
                sprites.building(BuildingType.GARAGE, false, season),
                sprites.building(BuildingType.GAS_STATION, false, season),
                sprites.building(BuildingType.AUTO_SHOP, false, season),
                sprites.building(BuildingType.AUTO_SHOP, true, season), sprites.relay(season))
        }
        artwork.forEach { art ->
            val bitmap = art.asAndroidBitmap()
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            assertTrue("Sprite needs transparent silhouette", pixels.any { (it ushr 24) == 0 })
            assertTrue("Sprite must contain opaque artwork", pixels.any { (it ushr 24) == 255 })
            assertFalse("Magenta backing must not reach the renderer", pixels.any {
                (it ushr 24) > 24 && minOf((it ushr 16) and 255, it and 255) - ((it ushr 8) and 255) > 100
            })
        }
    }
    @Test fun everyBuildingAndMastHasDistinctSeasonalArtwork() {
        for (type in BuildingType.entries.filter { it != BuildingType.WRECK }) {
            for (paint in listOf(false, true)) {
                val normal = sprites.building(type, paint)!!.asAndroidBitmap()
                val autumn = sprites.building(type, paint, BuildingSeason.AUTUMN)!!.asAndroidBitmap()
                val winter = sprites.building(type, paint, BuildingSeason.WINTER)!!.asAndroidBitmap()
                assertFalse(normal.sameAs(autumn))
                assertFalse(normal.sameAs(winter))
                assertFalse(autumn.sameAs(winter))
            }
        }
        assertFalse(sprites.relay(BuildingSeason.AUTUMN).asAndroidBitmap().sameAs(sprites.relay.asAndroidBitmap()))
        assertFalse(sprites.relay(BuildingSeason.WINTER).asAndroidBitmap().sameAs(sprites.relay.asAndroidBitmap()))
    }
    private fun render(id: Long, biome: BiomeType): Bitmap {
        val bitmap = Bitmap.createBitmap(280, 240, Bitmap.Config.ARGB_8888)
        val painter = BuildingPainter(sprites)
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bitmap.asImageBitmap()), Size(280f, 240f)) {
            with(painter) { drawBuilding(WorldBuilding(id, BuildingType.HOUSE, 0f), 140f, 220f, 48f, 1f, false, environment = biome) }
        }
        return bitmap
    }

    @Test fun buildingsRemainStableButDifferAcrossIdsAndEnvironments() {
        val first = render(1, BiomeType.RURAL)
        val same = render(1, BiomeType.RURAL)
        val other = render(20, BiomeType.RURAL)
        val snow = render(1, BiomeType.ALPINE)
        assertTrue("The same building must survive redraw/save reload unchanged", first.sameAs(same))
        assertFalse("Different buildings need different silhouettes / materials", first.sameAs(other))
        assertFalse("Alpine buildings need local materials and snow", first.sameAs(snow))
        listOf(first, same, other, snow).forEach { it.recycle() }
    }

    @Test fun renderBuildingGallery() {
        val bitmap = Bitmap.createBitmap(1800, 720, Bitmap.Config.ARGB_8888)
        val painter = BuildingPainter(sprites)
        val biomes = listOf(BiomeType.RURAL, BiomeType.FOREST_ALIVE, BiomeType.ALPINE)
        val types = listOf(BuildingType.HOUSE, BuildingType.GARAGE, BuildingType.GAS_STATION, BuildingType.AUTO_SHOP, BuildingType.AUTO_SHOP, BuildingType.GAS_STATION)
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bitmap.asImageBitmap()), Size(1800f, 720f)) {
            drawRect(Color(0xFFCFD2C9))
            biomes.forEachIndexed { row, biome ->
                types.forEachIndexed { column, type ->
                    with(painter) { drawBuilding(WorldBuilding((column * 19 + 1).toLong(), type, 0f, pumpFuelL = 10f,
                        landmark = column == 5, relayRestored = row % 2 == 0),
                        column * 300f + 150f, row * 240f + 224f, if (column == 5) 25f else 48f,
                        1f, false, environment = biome, paintService = column == 4) }
                }
            }
        }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val output = File(context.getExternalFilesDir(null), "building-seasons.png")
        output.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
