package sk.kubis.endlessdrive

import android.app.Application
import android.content.Context
import sk.kubis.endlessdrive.di.AppContainer

class EndlessDriveApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(AudioAttribution.wrap(base))
    }

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
