package sk.kubis.endlessdrive.core

import android.util.Log
import sk.kubis.endlessdrive.BuildConfig

/**
 * Debug/info logs stay behind [BuildConfig.DEBUG]. Real failures use [e].
 * ART `NativeAlloc concurrent mark compact GC` is not an app log — hide it in
 * Logcat with: `package:mine -NativeAlloc`
 */
internal object AppLog {
    fun d(tag: String, message: String) {
        if (BuildConfig.DEBUG) Log.d(tag, message)
    }

    fun v(tag: String, message: String) {
        if (BuildConfig.DEBUG) Log.v(tag, message)
    }

    fun i(tag: String, message: String) {
        if (BuildConfig.DEBUG) Log.i(tag, message)
    }

    fun e(tag: String, message: String, error: Throwable? = null) {
        if (error != null) Log.e(tag, message, error) else Log.e(tag, message)
    }
}
