package life.myluck.w124.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import life.myluck.w124.MainActivity
import life.myluck.w124.R
import life.myluck.w124.W124Application

class UpdateCheckWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as? W124Application ?: return Result.success()
        app.container.updates.refresh()
        val ui = app.container.updates.ui.value
        val latest = ui.latest ?: return Result.success()
        if (latest.versionCode <= ui.currentCode) return Result.success()
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, "Обновления приложения", NotificationManager.IMPORTANCE_DEFAULT),
        )
        val launch = PendingIntent.getActivity(
            applicationContext,
            1,
            Intent(applicationContext, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle("Бортжурнал ${latest.versionName}")
            .setContentText("Доступно обновление. Можно поставить или откатиться в настройках.")
            .setContentIntent(launch)
            .setAutoCancel(true)
            .build()
        manager.notify(43, notification)
        return Result.success()
    }

    companion object {
        private const val CHANNEL = "w124-updates"
    }
}
