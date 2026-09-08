package sk.kubis.endlessdrive.ads

import android.app.Activity
import android.content.Context
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.rewarded.RewardItem
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Jedno miesto pre rewarded reklamy. Gameplay pozná iba „odmenu za reklamu“;
 * konkrétny reklamný účet sa dá neskôr vymeniť bez zásahu do herného enginu.
 *
 * Používa produkčný rewarded ad unit vytvorený pre Endless Drive.
 */
class RewardedAdManager(context: Context) {
    private val appContext = context.applicationContext
    private var rewardedAd: RewardedAd? = null
    private var showing = false
    private var adsEnabled = false

    private val _isReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    /** Nastaví, či UMP dovolil žiadať reklamné odpovede. */
    fun setAdRequestAllowed(allowed: Boolean) {
        if (!allowed) {
            adsEnabled = false
            rewardedAd = null
            _isReady.value = false
            return
        }
        if (adsEnabled) return
        adsEnabled = true
        load()
    }

    fun load() {
        if (!adsEnabled || rewardedAd != null || showing) return
        RewardedAd.load(
            appContext,
            AD_UNIT_ID,
            AdRequest.Builder().build(),
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    rewardedAd = ad
                    _isReady.value = true
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    rewardedAd = null
                    _isReady.value = false
                }
            }
        )
    }

    /**
     * Odmenu voláme iba v callbacku, ktorý Google pošle po získaní odmeny.
     * Zatvorenie reklamy bez dopozerania preto nič neudeľuje.
     */
    fun show(
        activity: Activity,
        onReward: () -> Unit,
        onFinished: () -> Unit = {}
    ): Boolean {
        val ad = rewardedAd ?: run {
            load()
            return false
        }
        if (showing) return false
        showing = true
        rewardedAd = null
        _isReady.value = false
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                showing = false
                onFinished()
                load()
            }

            override fun onAdFailedToShowFullScreenContent(adError: com.google.android.gms.ads.AdError) {
                showing = false
                onFinished()
                load()
            }
        }
        ad.show(activity) { _: RewardItem -> onReward() }
        return true
    }

    companion object {
        private const val AD_UNIT_ID = "ca-app-pub-9434007333228721/7126065526"
    }
}
