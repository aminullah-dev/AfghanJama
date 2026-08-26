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
 * اعلان روزانهٔ کمبود موجودی: اگر قلمی از انبار مواد به حد هشدار رسیده
 * باشد، نوتیفیکیشن می‌دهد.
 */
class LowStockWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val db = buildAppDatabase(applicationContext)
        try {
            val low = db.materialStockDao().lowStock()
            if (low.isNotEmpty()) {
                val names = low.take(3).joinToString("، ") { it.name }
                val extra = if (low.size > 3) " و ${low.size - 3} قلم دیگر" else ""
                showNotification(low.size, "$names$extra")
            }
        } finally {
            db.close()
        }
        return Result.success()
    }

    private fun showNotification(count: Int, names: String) {
        if (!NotificationManagerCompat.from(applicationContext).areNotificationsEnabled()) return

        val manager =
            applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "اعلان کمبود موجودی",
                    NotificationManager.IMPORTANCE_DEFAULT
                )
            )
        }

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setContentTitle("کمبود موجودی انبار")
            .setContentText("$count قلم به حد هشدار رسیده: $names")
            .setStyle(NotificationCompat.BigTextStyle().bigText("$count قلم به حد هشدار رسیده: $names"))
            .setAutoCancel(true)
            .build()

        manager.notify(NOTIFICATION_ID, notification)
    }

    companion object {
        const val CHANNEL_ID = "low_stock"
        const val NOTIFICATION_ID = 1002
        const val WORK_NAME = "daily_low_stock"
    }
}
