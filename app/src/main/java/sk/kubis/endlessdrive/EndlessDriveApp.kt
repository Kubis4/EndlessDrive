package sk.kubis.endlessdrive

import android.app.Application
import sk.kubis.endlessdrive.di.AppContainer

class EndlessDriveApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
