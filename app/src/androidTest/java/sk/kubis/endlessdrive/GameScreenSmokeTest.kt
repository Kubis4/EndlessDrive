package sk.kubis.endlessdrive

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import sk.kubis.endlessdrive.domain.repository.PlayerProfile
import sk.kubis.endlessdrive.domain.repository.PlayerRepository
import sk.kubis.endlessdrive.ui.game.GameAssets
import sk.kubis.endlessdrive.ui.game.GameScreen
import sk.kubis.endlessdrive.ui.game.GameViewModel
import sk.kubis.endlessdrive.ui.theme.EndlessDriveTheme

/**
 * Smoke test: obrazovka hry sa poskladá, vykreslí a reaguje na tlačidlá.
 * Nekontroluje pixely – ide o to, aby sa Compose strom vôbec postavil
 * (chytí chýbajúce parametre, pády v layoute a rozbité stavy).
 */
@RunWith(AndroidJUnit4::class)
class GameScreenSmokeTest {

    @get:Rule
    val compose = createComposeRule()

    private class FakeRepository : PlayerRepository {
        var saved: String? = null
        override val profile: Flow<PlayerProfile> = flowOf(PlayerProfile(bestDistanceKm = 1.5f))
        override suspend fun current() = PlayerProfile(bestDistanceKm = 1.5f)
        override suspend fun recordRun(distanceKm: Float) = Unit
        override suspend fun loadRun(): String? = saved
        override suspend fun saveRun(data: String) { saved = data }
        override suspend fun clearRun() { saved = null }
    }

    @Test
    fun gameScreenRendersAndOpensPanels() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val assets = GameAssets(context)
        val vm = GameViewModel(FakeRepository(), bestDistanceKm = 1.5f)

        compose.setContent {
            EndlessDriveTheme {
                GameScreen(viewModel = vm, assets = assets, onExitToMenu = {})
            }
        }

        // Prístrojovka a spodná lišta musia byť na obrazovke.
        compose.onNodeWithText("FUEL").assertIsDisplayed()
        compose.onNodeWithText("CAR").assertIsDisplayed()

        // Panel auta sa dá otvoriť – mriežka dielov ukazuje sloty.
        compose.onNodeWithText("CAR").performClick()
        compose.onNodeWithText("Engine", substring = true).assertIsDisplayed()
    }
}
