package sk.kubis.endlessdrive.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.launch
import sk.kubis.endlessdrive.di.AppContainer
import sk.kubis.endlessdrive.domain.model.DebugOptions
import sk.kubis.endlessdrive.domain.repository.PlayerProfile
import sk.kubis.endlessdrive.ui.game.GameScreen
import sk.kubis.endlessdrive.ui.game.GameViewModel
import sk.kubis.endlessdrive.ui.menu.MenuScreen
import sk.kubis.endlessdrive.ui.menu.SettingsScreen

object Routes {
    const val MENU = "menu"
    const val GAME = "game"
    const val SETTINGS = "settings"
}

@Composable
fun EndlessDriveRoot(container: AppContainer) {
    val nav = rememberNavController()
    val profile by container.playerRepository.profile.collectAsState(initial = PlayerProfile())

    // ViewModel visí na aktivite, nie na obrazovke hry – návrat do menu
    // teda jazdu nezahodí a dá sa v nej pokračovať.
    val vm: GameViewModel = viewModel(
        factory = remember { GameViewModel.factory(container.playerRepository, profile.bestDistanceKm) }
    )
    LaunchedEffect(profile.bestDistanceKm) {
        vm.updateBestDistance(profile.bestDistanceKm)
    }

    val debug by container.playerRepository.debugOptions.collectAsState(initial = DebugOptions.OFF)
    // Nová jazda si prepínače prečíta z ViewModelu, takže sa musia doňho
    // dostať skôr, než ju hráč spustí.
    LaunchedEffect(debug) { vm.debugOptions = debug }
    val scope = rememberCoroutineScope()

    NavHost(
        navController = nav,
        startDestination = Routes.MENU
    ) {
        composable(Routes.MENU) {
            MenuScreen(
                profile = profile,
                canContinue = vm.hasActiveRun,
                runDistanceKm = vm.game.distanceKm,
                runClock = vm.game.clock,
                onContinue = {
                    nav.navigate(Routes.GAME) { launchSingleTop = true }
                },
                onNewRun = {
                    vm.retry()
                    nav.navigate(Routes.GAME) { launchSingleTop = true }
                },
                onSettings = { nav.navigate(Routes.SETTINGS) { launchSingleTop = true } },
                debugActive = debug.any
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                options = debug,
                onChange = { scope.launch { container.playerRepository.setDebugOptions(it) } },
                onBack = { nav.popBackStack() }
            )
        }
        composable(Routes.GAME) {
            GameScreen(
                viewModel = vm,
                assets = container.gameAssets,
                onExitToMenu = {
                    nav.popBackStack(Routes.MENU, inclusive = false)
                }
            )
        }
    }
}
