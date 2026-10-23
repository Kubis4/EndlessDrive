package sk.kubis.endlessdrive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import sk.kubis.endlessdrive.core.GameConfig
import sk.kubis.endlessdrive.ui.GameUiLayout

class ResolutionScaleTest {
    @Test
    fun fhdAndQhdShareCappedSceneBuffer() {
        assertEquals(1080f, GameConfig.sceneBufferHeight(1920f, 1080f), 0.01f)
        assertEquals(1080f, GameConfig.sceneBufferHeight(2560f, 1440f), 0.01f)
        assertEquals(1920f, 1920f * GameConfig.sceneBufferScale(1920f, 1080f), 0.01f)
        assertEquals(1920f, 2560f * GameConfig.sceneBufferScale(2560f, 1440f), 0.01f)
    }

    @Test
    fun hudGrowsGentlyAndHonorsUserScale() {
        val phone = GameConfig.uiDensityScale(640, 360)
        val fhd = GameConfig.uiDensityScale(1280, 720)
        val qhd = GameConfig.uiDensityScale(2560, 1440)
        assertEquals(GameConfig.UI_DENSITY_SCALE_MIN, phone, 0.0001f)
        assertEquals(1f, fhd, 0.0001f)
        assertEquals(GameConfig.UI_DENSITY_SCALE_MAX, qhd, 0.0001f)
        assertTrue(qhd > fhd)
        assertTrue(qhd < 1440f / 720f)

        val boosted = GameConfig.uiDensityScale(1280, 720, 1.2f)
        assertEquals(1.2f, boosted, 0.0001f)
        val clamped = GameConfig.uiDensityScale(1280, 720, 3f)
        assertEquals(GameConfig.UI_USER_SCALE_MAX, clamped, 0.0001f)
    }

    @Test
    fun gameUiUsesCompactVariantsOnlyWhereSpaceNeedsThem() {
        val small169 = GameUiLayout.forViewport(640, 360)
        assertTrue(small169.compactHud)
        assertTrue(small169.compactLoot)
        assertTrue(small169.compactGarage)

        val phone169 = GameUiLayout.forViewport(800, 480)
        assertTrue(!phone169.compactHud)
        assertTrue(phone169.compactLoot)
        assertTrue(phone169.compactGarage)

        val phone209 = GameUiLayout.forViewport(915, 412)
        assertTrue(!phone209.compactHud)
        assertTrue(phone209.compactLoot)
        assertTrue(phone209.compactGarage)

        val tablet = GameUiLayout.forViewport(1280, 800)
        assertTrue(!tablet.compactHud)
        assertTrue(!tablet.compactLoot)
        assertTrue(!tablet.compactGarage)
    }

}
