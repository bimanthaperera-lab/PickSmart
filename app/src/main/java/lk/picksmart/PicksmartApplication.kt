package lk.picksmart

import android.app.Application

class PicksmartApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // Follows the account, which puts the signed-in rider's records on screen and starts their
        // cloud backup; it waits for nothing and blocks nothing.
        container.start()
    }
}
