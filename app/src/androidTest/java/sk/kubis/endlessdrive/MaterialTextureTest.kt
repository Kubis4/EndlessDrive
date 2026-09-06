package sk.kubis.endlessdrive

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import org.junit.Assert.assertTrue
import org.junit.Test
import sk.kubis.endlessdrive.ui.game.MaterialKind
import sk.kubis.endlessdrive.ui.game.MaterialPainter
import sk.kubis.endlessdrive.ui.game.TiledArtwork
import kotlin.math.abs

class MaterialTextureTest {
    @Test fun backdropLoopPreservesTransparencyAndHasNoColourJump() {
        val source = Bitmap.createBitmap(240, 60, Bitmap.Config.ARGB_8888)
        for (y in 30 until 60) for (x in 0 until 240) {
            source.setPixel(x, y, Color.rgb(x, 100, 240 - x))
        }
        val tile = TiledArtwork.finish(source, MaterialKind.LEAVES, 0f)
        for (x in 0 until tile.width) assertTrue(tile.getPixel(x, 0).ushr(24) == 0)
        val first = tile.getPixel(0, 45); val last = tile.getPixel(tile.width - 1, 45)
        assertTrue("Loop should join neighbouring source pixels, not opposite ends",
            abs(Color.red(first) - Color.red(last)) <= 2 && abs(Color.blue(first) - Color.blue(last)) <= 2)
        for (x in 0 until tile.width) assertTrue(tile.getPixel(x, 45).ushr(24) == 255)
        tile.recycle(); source.recycle()
    }

    /** Zrno sa kreslí na mutovateľnú predlohu – Canvas odmietne nemennú bitmapu. */
    @Test fun grainFinishWritesOntoAMutableTile() {
        val source = Bitmap.createBitmap(240, 60, Bitmap.Config.ARGB_8888)
        for (y in 30 until 60) for (x in 0 until 240) {
            source.setPixel(x, y, Color.rgb(x, 100, 240 - x))
        }
        val tile = TiledArtwork.finish(source, MaterialKind.LEAVES, 0.65f)
        assertTrue("Grain pass needs a mutable bitmap", tile.isMutable)
        assertTrue(tile.width > 0 && tile.height == 60)
        for (x in 0 until tile.width) assertTrue(tile.getPixel(x, 0).ushr(24) == 0)
        tile.recycle(); source.recycle()
    }

    /** Rovná obloha po wrape aj zrne nesmie dostať tmavší stĺpec na spoji. */
    @Test fun opaqueSkyHasNoWrapHairline() {
        val source = Bitmap.createBitmap(480, 80, Bitmap.Config.ARGB_8888)
        val sky = Color.rgb(194, 164, 105)
        for (y in 0 until 80) for (x in 0 until 480) {
            source.setPixel(x, y, if (y < 48) sky else Color.rgb(90 + x % 50, 72, 48))
        }
        val tile = TiledArtwork.finish(source, MaterialKind.PAPER, 0.25f)
        val first = tile.getPixel(0, 10)
        val last = tile.getPixel(tile.width - 1, 10)
        assertTrue(
            "sky edges must stay neighbouring source pixels",
            abs(Color.red(first) - Color.red(last)) <= 12 &&
                abs(Color.green(first) - Color.green(last)) <= 12
        )
        var worst = 0
        for (x in 1 until tile.width - 1) {
            val c = tile.getPixel(x, 8)
            val l = tile.getPixel(x - 1, 8)
            val r = tile.getPixel(x + 1, 8)
            val spike = abs(Color.red(c) - (Color.red(l) + Color.red(r)) / 2)
            if (spike > worst) worst = spike
        }
        assertTrue("wrap must not paint a darker sky column, spike=$worst", worst < 20)
        tile.recycle(); source.recycle()
    }

    @Test fun surfaceTextureStaysAtWorldPositionWhenCameraMovesAcrossRepeat() {
        fun frame(worldStart: Float): Bitmap {
            val image = Bitmap.createBitmap(256, 96, Bitmap.Config.ARGB_8888)
            val painter = MaterialPainter()
            CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(image.asImageBitmap()), Size(256f, 96f)) {
                painter.quad(this, MaterialKind.GRAVEL, 1f,
                    0f, 0f, 256f, 0f, 256f, 96f, 0f, 96f,
                    worldStart, worldStart + 5f, 0f, 3f)
            }
            return image
        }
        // A 1.25 m camera move equals exactly 64 px; it also crosses the tile origin.
        val before = frame(-0.5f); val after = frame(0.75f)
        var error = 0L; var count = 0
        for (y in 4 until 92 step 3) for (x in 68 until 252 step 3) {
            val a = before.getPixel(x, y); val b = after.getPixel(x - 64, y)
            error += abs(Color.alpha(a) - Color.alpha(b))
            error += abs(Color.red(a) - Color.red(b)); count += 2
        }
        assertTrue("Texture must follow the road, average error=${error.toFloat() / count}", error.toFloat() / count < 1.5f)
        before.recycle(); after.recycle()
    }
}
