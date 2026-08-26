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
import life.myluck.w124.core.NodeStatus
import life.myluck.w124.core.NodeUrgency
import life.myluck.w124.data.LocalGarageStore

class DueCheckWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val state = runCatching { LocalGarageStore(applicationContext).loadOrSeed() }.getOrNull()
            ?: return Result.success()
        val due = NodeStatus.nearest(state).filter {
            it.urgency == NodeUrgency.URGENT || it.urgency == NodeUrgency.OVERDUE || it.urgency == NodeUrgency.SOON
        }
        if (due.isEmpty()) return Result.success()
        val title = "Бортжурнал: ${due.first().node.title}"
        val text = due.joinToString(" · ") { "${NodeStatus.urgencyLabelRu(it.urgency)}: ${it.node.title}" }
        notify(title, text)
        return Result.success()
    }

    private fun notify(title: String, text: String) {
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL,
            "Напоминания ТО",
            NotificationManager.IMPORTANCE_DEFAULT,
        )
        manager.createNotificationChannel(channel)
        val launch = PendingIntent.getActivity(
            applicationContext,
            0,
            Intent(applicationContext, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(launch)
            .setAutoCancel(true)
            .build()
        manager.notify(42, notification)
    }

    companion object {
        private const val CHANNEL = "w124-due"
    }
}
