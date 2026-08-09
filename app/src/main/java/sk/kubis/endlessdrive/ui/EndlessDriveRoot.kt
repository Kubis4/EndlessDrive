package sk.kubis.endlessdrive.ui

import androidx.compose.runtime.Composable
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

    NavHost(
        navController = nav,
        startDestination = Routes.MENU
    ) {
        composable(Routes.MENU) {
            MenuScreen(
                profile = profile,
                onPlay = {
                    nav.navigate(Routes.GAME) {
                        launchSingleTop = true
                    }
                }
            )
        }
        composable(Routes.GAME) {
            val vm: GameViewModel = viewModel(
                factory = remember(profile.bestDistanceKm) {
                    GameViewModel.factory(
                        container.playerRepository,
                        profile.bestDistanceKm
                    )
                }
            )
            GameScreen(
                viewModel = vm,
                onExitToMenu = {
                    nav.popBackStack(Routes.MENU, inclusive = false)
                }
            )
        }
    }
}
