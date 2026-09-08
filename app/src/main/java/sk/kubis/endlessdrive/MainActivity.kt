package sk.kubis.endlessdrive

import android.content.Context
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import sk.kubis.endlessdrive.core.GameConfig
import sk.kubis.endlessdrive.ui.EndlessDriveRoot
import sk.kubis.endlessdrive.ui.theme.EndlessDriveTheme

class MainActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AudioAttribution.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        requestSixtyHertz()

        val container = (application as EndlessDriveApp).container
        container.adConsent.gatherConsent(this) {
            container.rewardedAds.setAdRequestAllowed(container.adConsent.canRequestAds())
        }
        setContent {
            EndlessDriveTheme {
                EndlessDriveRoot(container = container)
            }
        }
    }

    private fun requestSixtyHertz() {
        val target = GameConfig.TARGET_FPS.toFloat()
        val params = window.attributes
        params.preferredRefreshRate = target
        runCatching {
            @Suppress("DEPRECATION")
            val display = windowManager.defaultDisplay
            val current = display.mode
            display.supportedModes
                .filter {
                    it.physicalWidth == current.physicalWidth &&
                        it.physicalHeight == current.physicalHeight &&
                        it.refreshRate >= target - 1f
                }
                .minByOrNull { it.refreshRate }
                ?.let { params.preferredDisplayModeId = it.modeId }
        }
        window.attributes = params
    }
}
