package sk.kubis.endlessdrive.ui

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import sk.kubis.endlessdrive.core.GameConfig
import sk.kubis.endlessdrive.di.AppContainer
import sk.kubis.endlessdrive.domain.model.AppLanguage
import sk.kubis.endlessdrive.domain.model.DebugOptions
import sk.kubis.endlessdrive.domain.model.GaugeStyle
import sk.kubis.endlessdrive.domain.model.ThrottleMode
import sk.kubis.endlessdrive.domain.repository.PlayerProfile
import sk.kubis.endlessdrive.ui.game.GameScreen
import sk.kubis.endlessdrive.ui.game.GameViewModel
import sk.kubis.endlessdrive.ui.l10n.Copy
import sk.kubis.endlessdrive.ui.l10n.LocalCopy
import sk.kubis.endlessdrive.ui.menu.MenuScreen
import sk.kubis.endlessdrive.ui.menu.SettingsScreen
import sk.kubis.endlessdrive.ui.menu.TutorialScreen
import sk.kubis.endlessdrive.ui.menu.GarageScreen
import sk.kubis.endlessdrive.ui.leaderboard.LeaderboardScreen
import sk.kubis.endlessdrive.ui.leaderboard.PlayerProfileScreen
import sk.kubis.endlessdrive.ui.ProvideResolutionScaledUi
import sk.kubis.endlessdrive.ui.LocalUiScale

object Routes {
    const val MENU = "menu"
    const val GAME = "game"
    const val SETTINGS = "settings"
    const val PROFILE = "profile"
    const val LEADERBOARD = "leaderboard"
    const val TUTORIAL = "tutorial"
    const val GARAGE = "garage"
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
    var lightningFlashesEnabled by remember {
        mutableStateOf(audioPrefs.getBoolean("lightning_flashes", true))
    }
    var tutorialStartsRun by remember { mutableStateOf(false) }
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
    val gaugeStyle by container.playerRepository.gaugeStyle.collectAsState(initial = GaugeStyle.CLASSIC)
    val storedUiScale by container.playerRepository.uiScale.collectAsState(
        initial = GameConfig.UI_USER_SCALE_DEFAULT
    )
    var uiScale by remember { mutableStateOf(storedUiScale) }
    LaunchedEffect(storedUiScale) { uiScale = storedUiScale }
    val language by container.playerRepository.appLanguage.collectAsState(initial = AppLanguage.systemDefault())
    val playGamesState by container.playGames.state.collectAsState()
    val privacyOptionsRequired by container.adConsent.privacyOptionsRequired.collectAsState()
    // Pri štarte len tiché overenie – account picker až na tlačidlo Sign in.
    LaunchedEffect(activity, playGamesState.configured) {
        if (activity != null && playGamesState.configured && !playGamesState.signedIn) {
            container.playGames.refreshAuth(activity)
        }
    }
    // Debug výbava ani FPS sa v hráčskom UI nezobrazujú. Zároveň čistíme
    // staré uložené prepínače, aby neovplyvnili novú jazdu po aktualizácii.
    LaunchedEffect(debug) {
        vm.debugOptions = DebugOptions.OFF
        if (debug.any) container.playerRepository.setDebugOptions(DebugOptions.OFF)
    }
    LaunchedEffect(throttleMode) { vm.throttleMode = throttleMode }
    LaunchedEffect(language) { vm.language = language }
    val scope = rememberCoroutineScope()
    val playDistanceBucket = maxOf(profile.bestDistanceKm, gameUi.bestDistanceKm, gameUi.distanceKm).toInt()
    LaunchedEffect(
        profile.bestDistanceKm,
        profile.bestTimeSeconds,
        playDistanceBucket,
        gameUi.relayNodes,
        gameUi.buildingsVisited,
        gameUi.fullTankReached,
        gameUi.fullUpgradeReached,
        gameUi.endReason,
        gameUi.eventKindsSeen.size,
        playGamesState.signedIn
    ) {
        if (activity == null || !playGamesState.signedIn) return@LaunchedEffect
        val distanceKm = maxOf(profile.bestDistanceKm, gameUi.bestDistanceKm, gameUi.distanceKm)
        if (profile.bestDistanceKm > 0f) {
            container.playGames.submitBestDistance(
                activity,
                profile.bestDistanceKm,
                profile.bestTimeSeconds,
                profile.countryCode
            )
        }
        container.playGames.unlockGameplayAchievements(
            activity = activity,
            distanceKm = distanceKm,
            relayNodes = gameUi.relayNodes,
            buildingsVisited = gameUi.buildingsVisited,
            fullTankReached = gameUi.fullTankReached,
            fullUpgradeReached = gameUi.fullUpgradeReached,
            endReason = gameUi.endReason,
            eventKindsSeen = gameUi.eventKindsSeen
        )
    }

    CompositionLocalProvider(
        LocalCopy provides Copy.of(language),
        LocalUiScale provides uiScale
    ) {
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
                        if (profile.tutorialCompleted) {
                            vm.retry()
                            nav.navigate(Routes.GAME) { launchSingleTop = true }
                        } else {
                            tutorialStartsRun = true
                            nav.navigate(Routes.TUTORIAL) { launchSingleTop = true }
                        }
                    },
                    onSettings = { nav.navigate(Routes.SETTINGS) { launchSingleTop = true } },
                    debugActive = false,
                    onLeaderboard = {
                        nav.navigate(Routes.LEADERBOARD) { launchSingleTop = true }
                    },
                    onTutorial = {
                        tutorialStartsRun = false
                        nav.navigate(Routes.TUTORIAL) { launchSingleTop = true }
                    },
                    onGarage = { nav.navigate(Routes.GARAGE) { launchSingleTop = true } }
                )
        }
        composable(Routes.TUTORIAL) {
            ProvideResolutionScaledUi {
                TutorialScreen(
                    startsRun = tutorialStartsRun,
                    onComplete = {
                        val startAfterSave = tutorialStartsRun
                        tutorialStartsRun = false
                        scope.launch {
                            // Najprv uložiť dokončenie, až potom odísť z obrazovky;
                            // pri rýchlom ukončení aplikácie sa tutorial neukáže znova.
                            container.playerRepository.setTutorialCompleted(true)
                            if (startAfterSave) {
                                vm.retry()
                                nav.navigate(Routes.GAME) { launchSingleTop = true }
                            } else {
                                nav.popBackStack()
                            }
                        }
                    },
                    onBack = { nav.popBackStack() }
                )
            }
        }
        composable(Routes.GARAGE) {
            ProvideResolutionScaledUi {
                GarageScreen(
                    profile = profile,
                    onBuy = { upgrade ->
                        scope.launch { container.playerRepository.buyGarageUpgrade(upgrade) }
                    },
                    onBack = { nav.popBackStack() }
                )
            }
        }
        composable(Routes.PROFILE) {
            ProvideResolutionScaledUi {
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
        }
        composable(Routes.LEADERBOARD) {
            ProvideResolutionScaledUi {
            LeaderboardScreen(
                profile = profile,
                playGames = container.playGames,
                activity = activity,
                onEditProfile = { nav.navigate(Routes.PROFILE) { launchSingleTop = true } },
                onAchievements = {
                    if (activity != null) {
                        val pg = container.playGames
                        if (pg.state.value.signedIn) {
                            pg.showAchievements(activity)
                        } else {
                            pg.signIn(activity) { ok ->
                                if (ok) pg.showAchievements(activity)
                            }
                        }
                    }
                },
                onBack = { nav.popBackStack() }
            )
            }
        }
        composable(Routes.SETTINGS) {
            ProvideResolutionScaledUi {
            SettingsScreen(
                audioSettings = audioSettings,
                lightningFlashesEnabled = lightningFlashesEnabled,
                onLightningFlashesEnabledChange = {
                    lightningFlashesEnabled = it
                    audioPrefs.edit().putBoolean("lightning_flashes", it).apply()
                },
                throttleMode = throttleMode,
                onThrottleModeChange = {
                    scope.launch { container.playerRepository.setThrottleMode(it) }
                },
                gaugeStyle = gaugeStyle,
                onGaugeStyleChange = {
                    scope.launch { container.playerRepository.setGaugeStyle(it) }
                },
                uiScale = uiScale,
                onUiScaleChange = {
                    uiScale = it
                    scope.launch { container.playerRepository.setUiScale(it) }
                },
                language = language,
                onLanguageChange = {
                    scope.launch { container.playerRepository.setAppLanguage(it) }
                },
                onAudioChange = {
                    audioSettings = it
                    audioPrefs.edit().putFloat("master", it.master)
                        .putFloat("surfaces", it.surfaces).putFloat("tyres", it.tyres).apply()
                },
                onBack = { nav.popBackStack() },
                showPrivacyOptions = privacyOptionsRequired,
                onPrivacyOptions = {
                    if (activity != null) {
                        container.adConsent.showPrivacyOptions(activity) {
                            container.rewardedAds.setAdRequestAllowed(container.adConsent.canRequestAds())
                        }
                    }
                }
            )
            }
        }
        composable(Routes.GAME) {
            GameScreen(
                viewModel = vm,
                audioSettings = audioSettings,
                throttleMode = throttleMode,
                gaugeStyle = gaugeStyle,
                lightningFlashesEnabled = lightningFlashesEnabled,
                rewardedAds = container.rewardedAds,
                onExitToMenu = {
                    nav.popBackStack(Routes.MENU, inclusive = false)
                }
            )
        }
    }
    }
}
