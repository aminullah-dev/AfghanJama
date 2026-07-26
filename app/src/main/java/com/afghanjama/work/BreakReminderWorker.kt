package com.afghanjama.work

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.afghanjama.data.buildAppDatabase
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * یادآورِ وقتِ نان و چای.
 *
 * عمداً از AlarmManagerِ دقیق استفاده نمی‌کند: از اندروید ۱۲ به بعد آن
 * اجازهٔ جداگانه می‌خواهد و روی بعضی گوشی‌ها اصلاً داده نمی‌شود. برای
 * وقتِ چای چند دقیقه این‌طرف و آن‌طرف مهم نیست، پس هر یادآور یک کارِ
 * یک‌بارهٔ زمان‌بندی‌شده است که پس از اجرا **خودش را برای فردا دوباره
 * می‌چیند**. اگر گوشی خاموش بوده باشد، با بازشدنِ اپ همه‌چیز از نو چیده
 * می‌شود.
 *
 * تعدادِ «داخل» از همان حضور و غیاب خوانده می‌شود، چون سؤالِ واقعیِ
 * آشپز این است که چند نفر سرِ سفره‌اند، نه اینکه ساعت چند است.
 */
class BreakReminderWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val id = inputData.getLong(KEY_ID, -1L)
        if (id < 0) return Result.success()

        val db = buildAppDatabase(applicationContext)
        try {
            val row = db.breakTimeDao().getById(id) ?: return Result.success()
            if (!row.enabled) return Result.success()
            val inside = runCatching { db.breakTimeDao().insideCount() }.getOrDefault(0)
            notify(row.id, row.title, row.clock, inside)
            // فردا هم همین ساعت
            schedule(applicationContext, row.id, row.hour, row.minute)
        } finally {
            db.close()
        }
        return Result.success()
    }

    private fun notify(id: Long, title: String, clock: String, inside: Int) {
        if (!NotificationManagerCompat.from(applicationContext).areNotificationsEnabled()) return
        val manager =
            applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "وقت نان و چای",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    enableVibration(true)
                    vibrationPattern = VIBRATE_PATTERN
                    description = "یادآوریِ وقت‌های ثابتِ کارگاه"
                }
            )
        }

        val text = when {
            inside > 0 -> "$clock — $inside نفر داخلِ کارگاه‌اند."
            else -> "$clock — کسی ورود نزده است."
        }
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVibrate(VIBRATE_PATTERN)
            .setAutoCancel(true)
            .build()

        manager.notify(NOTIFICATION_ID_BASE + (id.toInt() and 0xFFFF), notification)
    }

    companion object {
        const val CHANNEL_ID = "break_reminder"
        const val NOTIFICATION_ID_BASE = 3000
        const val KEY_ID = "break_id"

        private val VIBRATE_PATTERN = longArrayOf(0, 400, 200, 400)

        private fun workName(id: Long) = "break_reminder_$id"

        /**
         * فاصله تا نوبتِ بعدیِ [hour]:[minute]. اگر امروز گذشته باشد،
         * فردا. همیشه مثبت است تا WorkManager فوراً شلیک نکند.
         */
        fun delayUntilNext(hour: Int, minute: Int, now: Long = System.currentTimeMillis()): Long {
            val cal = Calendar.getInstance().apply {
                timeInMillis = now
                set(Calendar.HOUR_OF_DAY, hour.coerceIn(0, 23))
                set(Calendar.MINUTE, minute.coerceIn(0, 59))
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            if (cal.timeInMillis <= now) cal.add(Calendar.DAY_OF_YEAR, 1)
            return cal.timeInMillis - now
        }

        /** چیدنِ یادآور برای نوبتِ بعدی (جایگزینِ قبلی می‌شود). */
        fun schedule(context: Context, id: Long, hour: Int, minute: Int) {
            val request = OneTimeWorkRequestBuilder<BreakReminderWorker>()
                .setInitialDelay(delayUntilNext(hour, minute), TimeUnit.MILLISECONDS)
                .setInputData(workDataOf(KEY_ID to id))
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                workName(id), ExistingWorkPolicy.REPLACE, request
            )
        }

        fun cancel(context: Context, id: Long) {
            WorkManager.getInstance(context).cancelUniqueWork(workName(id))
        }

        /**
         * چیدنِ دوبارهٔ همهٔ یادآورهای فعال. با هر بازشدنِ اپ صدا زده
         * می‌شود تا خاموش‌بودنِ گوشی یا ری‌استارت چیزی را از بین نبرد.
         */
        suspend fun rescheduleAll(context: Context) {
            val db = buildAppDatabase(context)
            try {
                db.breakTimeDao().listEnabled().forEach {
                    schedule(context, it.id, it.hour, it.minute)
                }
            } finally {
                db.close()
            }
        }
    }
}
