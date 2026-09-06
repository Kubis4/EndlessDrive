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
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import sk.kubis.endlessdrive.domain.model.DebugOptions
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
        override val debugOptions: Flow<DebugOptions> = flowOf(DebugOptions.OFF)
        override suspend fun setDebugOptions(options: DebugOptions) = Unit
        override val throttleMode: Flow<sk.kubis.endlessdrive.domain.model.ThrottleMode> =
            flowOf(sk.kubis.endlessdrive.domain.model.ThrottleMode.BINARY)
        override suspend fun setThrottleMode(mode: sk.kubis.endlessdrive.domain.model.ThrottleMode) = Unit
    }

    @Test
    fun gameScreenRendersAndOpensPanels() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val assets = GameAssets(context)
        val vm = GameViewModel(FakeRepository(), bestDistanceKm = 1.5f)
        // A game has a perpetual frame loop, so it never becomes animation-idle.
        compose.mainClock.autoAdvance = false

        compose.setContent {
            EndlessDriveTheme {
                GameScreen(viewModel = vm, assets = assets, onExitToMenu = {})
            }
        }

        compose.mainClock.advanceTimeBy(300)
        // Prístrojovka a spodná lišta musia byť na obrazovke.
        compose.onNodeWithText("FUEL").assertIsDisplayed()
        compose.onNodeWithText("CAR").assertIsDisplayed()

        // Panel auta sa dá otvoriť – mriežka dielov ukazuje sloty.
        compose.onNodeWithText("CAR").performClick()
        compose.mainClock.advanceTimeBy(300)
        compose.onNodeWithText("Engine", substring = true).assertIsDisplayed()
    }

    @Test
    fun preparedCarDrivesAndPauseFreezesSimulation() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val vm = GameViewModel(FakeRepository(), bestDistanceKm = 1.5f)
        val assets = GameAssets(context)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            EndlessDriveTheme {
                GameScreen(vm, assets, onExitToMenu = {})
            }
        }
        compose.runOnUiThread {
            vm.debugOptions = DebugOptions(allComponents = true, fullFluids = true, fullBody = true)
            vm.retry()
            vm.startEngine()
            vm.resume()
            vm.onGasChanged(true)
        }
        compose.mainClock.advanceTimeBy(12_000)
        compose.runOnUiThread {
            assertTrue("Prepared car should advance", vm.game.distanceM > 15f)
            assertTrue("Engine should be running", vm.game.car.engineRunning)
            vm.onGasChanged(false)
            vm.setPaused(true)
        }
        val stoppedAt = vm.game.distanceM
        compose.mainClock.advanceTimeBy(2_000)
        compose.runOnUiThread {
            assertEquals(stoppedAt, vm.game.distanceM, 0.001f)
            vm.setPaused(false)
        }
        compose.mainClock.advanceTimeBy(300)
    }
}
