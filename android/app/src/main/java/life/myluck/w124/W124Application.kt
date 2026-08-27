package life.myluck.w124

import android.app.Application
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import life.myluck.w124.data.AppContainer
import life.myluck.w124.notify.DueCheckWorker
import life.myluck.w124.notify.UpdateCheckWorker
import java.util.concurrent.TimeUnit

class W124Application : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        val wm = WorkManager.getInstance(this)
        wm.enqueueUniquePeriodicWork(
            "w124-due-check",
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<DueCheckWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(4, TimeUnit.HOURS)
                .build(),
        )
        wm.enqueueUniquePeriodicWork(
            "w124-update-check",
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<UpdateCheckWorker>(12, TimeUnit.HOURS)
                .setInitialDelay(15, TimeUnit.MINUTES)
                .build(),
        )
    }
}
