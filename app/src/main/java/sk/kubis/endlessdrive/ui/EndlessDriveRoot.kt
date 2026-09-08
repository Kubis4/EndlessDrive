package sk.kubis.endlessdrive.ui

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import sk.kubis.endlessdrive.domain.model.AudioSettings
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.launch
import sk.kubis.endlessdrive.di.AppContainer
import sk.kubis.endlessdrive.domain.model.DebugOptions
import sk.kubis.endlessdrive.domain.model.ThrottleMode
import sk.kubis.endlessdrive.domain.repository.PlayerProfile
import sk.kubis.endlessdrive.ui.game.GameScreen
import sk.kubis.endlessdrive.ui.game.GameViewModel
import sk.kubis.endlessdrive.ui.menu.MenuScreen
import sk.kubis.endlessdrive.ui.menu.SettingsScreen
import sk.kubis.endlessdrive.ui.leaderboard.LeaderboardScreen
import sk.kubis.endlessdrive.ui.leaderboard.PlayerProfileScreen

object Routes {
    const val MENU = "menu"
    const val GAME = "game"
    const val SETTINGS = "settings"
    const val PROFILE = "profile"
    const val LEADERBOARD = "leaderboard"
}

@Composable
fun EndlessDriveRoot(container: AppContainer) {
    val context = LocalContext.current
    val activity = context as? Activity
    val audioPrefs = remember(context) { context.getSharedPreferences("audio", android.content.Context.MODE_PRIVATE) }
    var audioSettings by remember {
        mutableStateOf(AudioSettings(
            audioPrefs.getFloat("master", 0.8f),
            audioPrefs.getFloat("surfaces", 0.65f),
            audioPrefs.getFloat("tyres", 0.5f)
        ))
    }
    var showFps by remember(context) { mutableStateOf(context.getSharedPreferences("display", 0).getBoolean("fps", false)) }
    val nav = rememberNavController()
    val profile by container.playerRepository.profile.collectAsState(initial = PlayerProfile())

    // ViewModel visí na aktivite, nie na obrazovke hry – návrat do menu
    // teda jazdu nezahodí a dá sa v nej pokračovať.
    val vm: GameViewModel = viewModel(
        factory = remember { GameViewModel.factory(container.playerRepository, profile) }
    )
    val gameUi by vm.ui.collectAsState()
    LaunchedEffect(profile) {
        vm.updateProfile(profile)
    }

    val debug by container.playerRepository.debugOptions.collectAsState(initial = DebugOptions.OFF)
    val throttleMode by container.playerRepository.throttleMode.collectAsState(initial = ThrottleMode.BINARY)
    val playGamesState by container.playGames.state.collectAsState()
    val privacyOptionsRequired by container.adConsent.privacyOptionsRequired.collectAsState()
    // Nová jazda si prepínače prečíta z ViewModelu, takže sa musia doňho
    // dostať skôr, než ju hráč spustí.
    LaunchedEffect(debug) { vm.debugOptions = debug }
    LaunchedEffect(throttleMode) { vm.throttleMode = throttleMode }
    val scope = rememberCoroutineScope()
    LaunchedEffect(activity, container.playGames) {
        if (activity != null) container.playGames.connect(activity)
    }
    LaunchedEffect(
        profile.bestDistanceKm,
        profile.bestTimeSeconds,
        gameUi.bestDistanceKm,
        gameUi.relayNodes,
        gameUi.buildingsVisited,
        gameUi.fullTankReached,
        gameUi.fullUpgradeReached,
        gameUi.endReason,
        gameUi.eventKindsSeen,
        playGamesState.signedIn
    ) {
        if (activity != null && playGamesState.signedIn && profile.bestDistanceKm > 0f) {
            container.playGames.submitBestDistance(
                activity,
                profile.bestDistanceKm,
                profile.bestTimeSeconds,
                profile.countryCode
            )
            container.playGames.unlockGameplayAchievements(
                activity = activity,
                distanceKm = maxOf(profile.bestDistanceKm, gameUi.bestDistanceKm),
                relayNodes = gameUi.relayNodes,
                buildingsVisited = gameUi.buildingsVisited,
                fullTankReached = gameUi.fullTankReached,
                fullUpgradeReached = gameUi.fullUpgradeReached,
                endReason = gameUi.endReason,
                eventKindsSeen = gameUi.eventKindsSeen
            )
        }
    }

    NavHost(
        navController = nav,
        startDestination = Routes.MENU
    ) {
        composable(Routes.MENU) {
            if (profile.nickname.isBlank()) {
                PlayerProfileScreen(
                    profile = profile,
                    required = true,
                    suggestedNickname = playGamesState.playerName,
                    onSave = { nickname, country ->
                        scope.launch { container.playerRepository.savePlayerIdentity(nickname, country) }
                    }
                )
            } else {
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
                    debugActive = debug.any,
                    onLeaderboard = { nav.navigate(Routes.LEADERBOARD) { launchSingleTop = true } },
                    onAchievements = {
                        if (activity != null) container.playGames.showAchievements(activity)
                    }
                )
            }
        }
        composable(Routes.PROFILE) {
            PlayerProfileScreen(
                profile = profile,
                required = false,
                suggestedNickname = playGamesState.playerName,
                onSave = { nickname, country ->
                    scope.launch {
                        container.playerRepository.savePlayerIdentity(nickname, country)
                        nav.popBackStack()
                    }
                },
                onBack = { nav.popBackStack() }
            )
        }
        composable(Routes.LEADERBOARD) {
            LeaderboardScreen(
                profile = profile,
                playGames = container.playGames,
                activity = activity,
                onEditProfile = { nav.navigate(Routes.PROFILE) { launchSingleTop = true } },
                onBack = { nav.popBackStack() }
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                options = debug,
                audioSettings = audioSettings,
                throttleMode = throttleMode,
                onThrottleModeChange = {
                    scope.launch { container.playerRepository.setThrottleMode(it) }
                },
                onAudioChange = {
                    audioSettings = it
                    audioPrefs.edit().putFloat("master", it.master)
                        .putFloat("surfaces", it.surfaces).putFloat("tyres", it.tyres).apply()
                },
                onChange = { scope.launch { container.playerRepository.setDebugOptions(it) } },
                onBack = { nav.popBackStack() }
                ,showFps = showFps,
                onShowFpsChange = { showFps = it; context.getSharedPreferences("display", 0).edit().putBoolean("fps", it).apply() }
                ,showPrivacyOptions = privacyOptionsRequired
                ,onPrivacyOptions = {
                    if (activity != null) {
                        container.adConsent.showPrivacyOptions(activity) {
                            container.rewardedAds.setAdRequestAllowed(container.adConsent.canRequestAds())
                        }
                    }
                }
            )
        }
        composable(Routes.GAME) {
            GameScreen(
                viewModel = vm,
                assets = container.gameAssets,
                audioSettings = audioSettings,
                throttleMode = throttleMode,
                showFps = showFps,
                rewardedAds = container.rewardedAds,
                onToggleFps = { showFps = !showFps; context.getSharedPreferences("display", 0).edit().putBoolean("fps", showFps).apply() },
                onExitToMenu = {
                    nav.popBackStack(Routes.MENU, inclusive = false)
                }
            )
        }
    }
}
