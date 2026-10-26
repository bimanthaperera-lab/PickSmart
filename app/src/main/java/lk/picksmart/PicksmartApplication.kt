package lk.picksmart

import android.app.Application

class PicksmartApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // Starts cloud backup for a signed-in rider; it waits for nothing and blocks nothing.
        container.sync
    }
}
