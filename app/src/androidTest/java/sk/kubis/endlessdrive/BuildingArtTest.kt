package sk.kubis.endlessdrive

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
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
import java.io.File

class BuildingArtTest {
    private fun render(id: Long, biome: BiomeType): Bitmap {
        val bitmap = Bitmap.createBitmap(280, 240, Bitmap.Config.ARGB_8888)
        val painter = BuildingPainter()
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
        val bitmap = Bitmap.createBitmap(1440, 1200, Bitmap.Config.ARGB_8888)
        val painter = BuildingPainter()
        val biomes = listOf(BiomeType.RURAL, BiomeType.FOREST_ALIVE, BiomeType.DESERT, BiomeType.INDUSTRIAL, BiomeType.ALPINE)
        val types = listOf(BuildingType.HOUSE, BuildingType.HOUSE, BuildingType.GARAGE, BuildingType.GAS_STATION, BuildingType.AUTO_SHOP)
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bitmap.asImageBitmap()), Size(1440f, 1200f)) {
            drawRect(Color(0xFFCFD2C9))
            biomes.forEachIndexed { row, biome ->
                types.forEachIndexed { column, type ->
                    with(painter) { drawBuilding(WorldBuilding((column * 19 + row * 7 + 1).toLong(), type, 0f, pumpFuelL = 10f),
                        column * 288f + 144f, row * 240f + 224f, 40f, 1f, false, environment = biome) }
                }
            }
        }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val output = File(context.getExternalFilesDir(null), "building-review.png")
        output.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
