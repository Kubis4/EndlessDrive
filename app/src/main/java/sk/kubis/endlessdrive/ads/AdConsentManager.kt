package sk.kubis.endlessdrive.ads

import android.app.Activity
import android.content.Context
import com.google.android.gms.ads.MobileAds
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Spravuje GDPR/EEA súhlas pre AdMob. Reklamy sa môžu načítať až po tom,
 * ako UMP dokončí aktuálnu kontrolu súhlasu.
 */
class AdConsentManager(context: Context) {
    private val appContext = context.applicationContext
    private val consentInformation = UserMessagingPlatform.getConsentInformation(appContext)
    private var adsInitialized = false

    private val _privacyOptionsRequired = MutableStateFlow(false)
    val privacyOptionsRequired: StateFlow<Boolean> = _privacyOptionsRequired.asStateFlow()

    fun canRequestAds(): Boolean = consentInformation.canRequestAds()

    /** Volaj pri každom štarte Activity, aby UMP obnovil stav súhlasu. */
    fun gatherConsent(activity: Activity, onComplete: () -> Unit = {}) {
        val parameters = ConsentRequestParameters.Builder().build()
        consentInformation.requestConsentInfoUpdate(
            activity,
            parameters,
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) {
                    finishConsent(onComplete)
                }
            },
            {
                // Pri dočasnej chybe použije UMP posledný platný stav, ak existuje.
                finishConsent(onComplete)
            }
        )
    }

    fun showPrivacyOptions(activity: Activity, onComplete: () -> Unit = {}) {
        UserMessagingPlatform.showPrivacyOptionsForm(activity) {
            updatePrivacyOptionsState()
            if (consentInformation.canRequestAds()) initializeAdsOnce()
            onComplete()
        }
    }

    private fun finishConsent(onComplete: () -> Unit) {
        updatePrivacyOptionsState()
        if (consentInformation.canRequestAds()) initializeAdsOnce()
        onComplete()
    }

    private fun updatePrivacyOptionsState() {
        _privacyOptionsRequired.value =
            consentInformation.privacyOptionsRequirementStatus ==
                ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED
    }

    private fun initializeAdsOnce() {
        if (adsInitialized) return
        adsInitialized = true
        MobileAds.initialize(appContext)
    }
}
