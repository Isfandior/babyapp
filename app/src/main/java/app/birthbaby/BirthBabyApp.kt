package app.birthbaby

import android.app.Application
import app.birthbaby.alerts.Notifier
import app.birthbaby.data.Trace

class BirthBabyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Trace.init(this)
        Notifier.createChannels(this)
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            Trace.critical("crash", "${e.javaClass.simpleName}: ${e.message}")
            previous?.uncaughtException(t, e)
        }
        Trace.event("app_start", BuildConfig.VERSION_NAME)
    }
}
