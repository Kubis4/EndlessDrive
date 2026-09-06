package sk.kubis.endlessdrive

import android.content.Context
import android.os.Build

/**
 * AppOps na Android 12+ loguje `attributionTag not declared`, keď je tag `""`.
 * Native SoundPool/AudioTrack berie [AttributionSource.myAttributionSource],
 * čiže [Application.getAttributionSource] — nie kontext z [android.media.SoundPool.Builder.setContext].
 */
internal object AudioAttribution {
    const val TAG = "gameAudio"

    fun wrap(base: Context): Context {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return base
        if (TAG == base.attributionTag) return base
        return base.createAttributionContext(TAG)
    }
}
