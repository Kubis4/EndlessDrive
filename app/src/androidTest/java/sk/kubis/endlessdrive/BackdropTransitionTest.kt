package sk.kubis.endlessdrive

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import sk.kubis.endlessdrive.domain.model.BiomeType
import sk.kubis.endlessdrive.domain.model.BranchStyle
import sk.kubis.endlessdrive.domain.model.DebugOptions
import sk.kubis.endlessdrive.domain.model.ComponentSlot
import sk.kubis.endlessdrive.domain.model.ComponentCondition
import sk.kubis.endlessdrive.domain.model.ItemCatalog
import sk.kubis.endlessdrive.domain.model.ItemStack
import sk.kubis.endlessdrive.game.DepthProjection
import sk.kubis.endlessdrive.ui.game.CarArtist
import sk.kubis.endlessdrive.ui.game.ExpeditionEquipment
import sk.kubis.endlessdrive.game.GameEngine
import sk.kubis.endlessdrive.ui.game.GameAssets
import sk.kubis.endlessdrive.ui.game.GameRenderer
import sk.kubis.endlessdrive.ui.game.drawBackdropCrossfade
import java.io.File
import kotlin.math.abs
import kotlin.math.roundToInt

class BackdropTransitionTest {
    @Test fun expeditionKitPreviewUsesSameCarGeometry() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val assets=GameAssets(context)
        val engine=GameEngine(41L,0f,DebugOptions(allComponents=true,fullBody=true,fullFluids=true))
        engine.car.parts.values.forEach { it.paintIndex=engine.car.bodyPaintIndex }
        val artist=CarArtist()
        val output=File(context.getExternalFilesDir(null),"backdrop-transition-review").apply { mkdirs() }
        fun carFrame(): Bitmap = render(1280,650) {
            drawRect(Color(0xFF28343D))
            val layout=artist.layoutAtBody(assets.sedan,640f,340f,185f)
            with(artist) {
                drawSedan(engine.car,640f,340f,0f,185f,DepthProjection(),0f,false,assets.sedan,550f,
                    rearWheelX=layout.rearWx,rearWheelY=layout.rearWy,
                    frontWheelX=layout.frontWx,frontWheelY=layout.frontWy)
            }
            if (engine.roofLightsOn) with(ExpeditionEquipment) { drawLight(layout,1f,1f) }
        }
        engine.car.mount(ComponentSlot.ROOF_RACK,ItemStack(ItemCatalog.ROOF_RACK.id,ComponentCondition.USED,1f))
        val standard=carFrame()
        engine.car.mount(ComponentSlot.ROOF_RACK,ItemStack(ItemCatalog.EXPEDITION_RACK.id,ComponentCondition.USED,1f))
        val expedition=carFrame()
        assertTrue("kit must be visible",!standard.sameAs(expedition))
        engine.toggleRoofLights()
        val lit = carFrame()
        assertTrue("roof switch changes light output",!lit.sameAs(expedition))
        engine.toggleRoofLights()
        val switchedOff = carFrame()
        assertTrue("roof switch restores unlit appearance",switchedOff.sameAs(expedition))
        switchedOff.recycle()
        engine.car.mount(ComponentSlot.ROOF_RACK,ItemStack(ItemCatalog.ROOF_RACK.id,ComponentCondition.USED,1f))
        val removed=carFrame()
        assertTrue("standard appearance returns on replacement",standard.sameAs(removed))
        for ((name,bmp) in listOf("car-standard" to standard,"car-expedition" to expedition,"car-expedition-lit" to lit)) {
            File(output,"$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG,100,it) }
            bmp.recycle()
        }
        removed.recycle()
    }

    private fun render(w: Int, h: Int, block: DrawScope.() -> Unit): Bitmap {
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr,
            Canvas(bitmap.asImageBitmap()), Size(w.toFloat(), h.toFloat()), block)
        return bitmap
    }

    @Test fun transparentLayerGapsFadeOutOldObjectsContinuously() {
        val outgoing: DrawScope.() -> Unit = {
            drawRect(Color.Blue) // far
            drawRect(Color.Red, Offset(0f, 10f), Size(20f, 10f)) // mid
            drawRect(Color.Yellow, Offset(0f, 20f), Size(10f, 10f)) // near
        }
        val incoming: DrawScope.() -> Unit = {
            drawRect(Color.Cyan) // opaque far behind the gaps in mid/near
            drawRect(Color.Green, Offset(20f, 10f), Size(20f, 10f))
            drawRect(Color.Magenta, Offset(30f, 20f), Size(10f, 10f))
        }
        val first = render(40, 30, outgoing)
        val last = render(40, 30, incoming)
        for (t in listOf(0f, .25f, .5f, .75f, .999f, 1f)) {
            val frame = render(40, 30) {
                drawBackdropCrossfade(t, Paint(), outgoing, incoming)
            }
            for (y in 0 until 30) for (x in 0 until 40) {
                val a = first.getPixel(x,y); val b = last.getPixel(x,y)
                val actual = frame.getPixel(x,y)
                assertEquals("opaque scene at $t", 255, actual ushr 24)
                for (shift in listOf(0,8,16)) {
                    val expected = (((a ushr shift) and 255)*(1-t)+((b ushr shift) and 255)*t).roundToInt()
                    assertTrue("layer fade at $x,$y t=$t", abs(((actual ushr shift) and 255)-expected)<=2)
                }
            }
            frame.recycle()
        }
        first.recycle(); last.recycle()
    }

    @Test fun actualBiomeScaleAndTransitionFramesRender() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val assets = GameAssets(context)
        val output = File(context.getExternalFilesDir(null), "backdrop-transition-review").apply { mkdirs() }
        val base = GameEngine(41L, 0f, DebugOptions(allComponents=true, fullFluids=true, fullBody=true)).snapshot()
        for (biome in listOf(BiomeType.INDUSTRIAL, BiomeType.SANDSTORM, BiomeType.DUST_STORM,
            BiomeType.DESERT, BiomeType.DESERT_DUSK, BiomeType.FOREST, BiomeType.FOREST_ALIVE)) {
            val style = BranchStyle.entries.first { it.biome == biome }
            val engine = GameEngine.restore(base.copy(timeOfDay=.5f, segment=base.segment.copy(style=style)),0f)
            val renderer = GameRenderer(assets)
            val positions = if (biome==BiomeType.INDUSTRIAL) listOf(0f,.25f,.5f,.75f,1f) else listOf(0f)
            for (fraction in positions) {
                engine.car.x = if (fraction==0f) 0f else
                    engine.segment.transitionStartWorldX + engine.segment.transitionLength*fraction
                engine.setScreenHeight(720f)
                engine.car.snapToGround(engine.segment.heightAtWorld(engine.car.x),
                    engine.segment.slopeAtLocal(engine.car.x-engine.segment.worldOrigin))
                engine.camera.snapTo(engine.car.x,engine.car.y)
                val bitmap = render(1280,720) { with(renderer) { draw(engine) } }
                val label = "${biome.name.lowercase()}-${(fraction*100).toInt()}"
                File(output,"$label.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
                assertEquals("$label sky coverage",255,bitmap.getPixel(640,10) ushr 24)
                bitmap.recycle()
            }
        }
    }
}
