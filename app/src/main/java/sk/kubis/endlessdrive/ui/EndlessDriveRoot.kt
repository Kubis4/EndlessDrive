package sk.kubis.endlessdrive.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import sk.kubis.endlessdrive.di.AppContainer
import sk.kubis.endlessdrive.domain.repository.PlayerProfile
import sk.kubis.endlessdrive.ui.game.GameScreen
import sk.kubis.endlessdrive.ui.game.GameViewModel
import sk.kubis.endlessdrive.ui.menu.MenuScreen

object Routes {
    const val MENU = "menu"
    const val GAME = "game"
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

    NavHost(
        navController = nav,
        startDestination = Routes.MENU
    ) {
        composable(Routes.MENU) {
            MenuScreen(
                profile = profile,
                canContinue = vm.hasActiveRun,
                onContinue = {
                    nav.navigate(Routes.GAME) { launchSingleTop = true }
                },
                onNewRun = {
                    vm.retry()
                    nav.navigate(Routes.GAME) { launchSingleTop = true }
                }
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
