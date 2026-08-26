package com.afghanjama.work

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.afghanjama.data.buildAppDatabase

/**
 * یادآوری هفتگی تسویه کارمزد خیاط‌ها.
 * هر هفته اجرا می‌شود و اگر کارمزد تسویه‌نشده‌ای باشد، نوتیفیکیشن می‌دهد.
 */
class WageReminderWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val db = buildAppDatabase(applicationContext)

        try {
            val pending = db.tailorWageDao().pendingList()
            if (pending.isNotEmpty()) {
                val total = pending.sumOf { it.amount }
                showNotification(pending.size, total)
            }
        } finally {
            db.close()
        }
        return Result.success()
    }

    private fun showNotification(count: Int, total: Long) {
        if (!NotificationManagerCompat.from(applicationContext).areNotificationsEnabled()) return

        val manager =
            applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "یادآوری تسویه کارمزد",
                    NotificationManager.IMPORTANCE_DEFAULT
                )
            )
        }

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("تسویه هفتگی کارمزد خیاط‌ها")
            .setContentText("$count کارمزد باز به مجموع $total ؋ منتظر تسویه است.")
            .setAutoCancel(true)
            .build()

        manager.notify(NOTIFICATION_ID, notification)
    }

    companion object {
        const val CHANNEL_ID = "wage_reminder"
        const val NOTIFICATION_ID = 1001
        const val WORK_NAME = "weekly_wage_reminder"
    }
}
