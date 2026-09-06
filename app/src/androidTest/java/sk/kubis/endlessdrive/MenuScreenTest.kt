package sk.kubis.endlessdrive

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.test.platform.app.InstrumentationRegistry
import android.graphics.Bitmap
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import sk.kubis.endlessdrive.domain.model.AudioSettings
import sk.kubis.endlessdrive.domain.model.DebugOptions
import sk.kubis.endlessdrive.domain.repository.PlayerProfile
import sk.kubis.endlessdrive.ui.menu.MenuScreen
import sk.kubis.endlessdrive.ui.menu.SettingsScreen
import sk.kubis.endlessdrive.ui.theme.EndlessDriveTheme

class MenuScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun endlessRoadMovesAndReturnsToTheSameLoopFrame() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            EndlessDriveTheme {
                Box(Modifier.size(640.dp, 360.dp)) {
                    MenuScreen(PlayerProfile(), false, 0f, "08:30", {}, {}, {})
                }
            }
        }
        compose.mainClock.advanceTimeBy(320)
        val first = compose.onRoot().captureToImage().asAndroidBitmap()
        compose.mainClock.advanceTimeBy(800)
        val moving = compose.onRoot().captureToImage().asAndroidBitmap()
        compose.mainClock.advanceTimeBy(1600)
        val loop = compose.onRoot().captureToImage().asAndroidBitmap()
        var changed = 0; var loopChanged = 0
        for (y in first.height * 2 / 3 until first.height step 2) {
            for (x in 0 until first.width step 2) {
                if (first.getPixel(x, y) != moving.getPixel(x, y)) changed++
                if (first.getPixel(x, y) != loop.getPixel(x, y)) loopChanged++
            }
        }
        assertTrue("Road markings and asphalt must move", changed > 40)
        assertTrue("Animation must wrap smoothly ($loopChanged versus $changed)", loopChanged < changed / 3)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.getExternalFilesDir(null), "stylized-review").apply { mkdirs() }
        for ((name, bitmap) in listOf("menu-a" to first, "menu-b" to moving)) {
            File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    @Test fun compactMenuProtectsExistingRun() {
        var replacements = 0
        compose.mainClock.autoAdvance = false
        compose.setContent {
            EndlessDriveTheme {
                Box(Modifier.size(640.dp, 360.dp)) {
                    MenuScreen(PlayerProfile(), true, 2.5f, "08:30", {},
                        { replacements++ }, {})
                }
            }
        }
        compose.mainClock.advanceTimeBy(300)
        compose.onNodeWithText("CONTINUE").assertIsDisplayed()
        compose.onNodeWithText("SETTINGS").assertIsDisplayed()
        compose.onNodeWithText("NEW RUN").performClick()
        compose.mainClock.advanceTimeBy(100)
        assertEquals(0, replacements)
        compose.onNodeWithText("KEEP RUN").performClick()
        compose.mainClock.advanceTimeBy(100)
        assertEquals(0, replacements)
        compose.onNodeWithText("NEW RUN").performClick()
        compose.mainClock.advanceTimeBy(100)
        compose.onNodeWithText("REPLACE RUN").performClick()
        assertEquals(1, replacements)
    }

    @Test fun tyreVolumeCanBeMutedIndependently() {
        val settings = mutableStateOf(AudioSettings())
        compose.setContent {
            EndlessDriveTheme {
                SettingsScreen(DebugOptions.OFF, {}, {}, settings.value, { settings.value = it })
            }
        }
        compose.onNodeWithContentDescription("Tyre slip").performScrollTo()
            .performSemanticsAction(SemanticsActions.SetProgress) { it(0f) }
        assertEquals(0f, settings.value.tyres, 0f)
        assertEquals(0.8f, settings.value.master, 0f)
        assertEquals(0.65f, settings.value.surfaces, 0f)
    }
}
