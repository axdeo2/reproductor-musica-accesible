package app.mimusica

import android.app.Application
import app.mimusica.di.AppContainer

class MusicApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
