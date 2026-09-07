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
 * Používa oficiálny testovací ad unit. Pred publikovaním ho treba nahradiť
 * vlastným ID v [AD_UNIT_ID].
 */
class RewardedAdManager(context: Context) {
    private val appContext = context.applicationContext
    private var rewardedAd: RewardedAd? = null
    private var showing = false

    private val _isReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    init {
        load()
    }

    fun load() {
        if (rewardedAd != null || showing) return
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
        // Google test rewarded ad. Replace before a production release.
        private const val AD_UNIT_ID = "ca-app-pub-3940256099942544/5224354917"
    }
}
