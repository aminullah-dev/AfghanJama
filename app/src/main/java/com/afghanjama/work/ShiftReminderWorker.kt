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
import com.afghanjama.data.ShiftPolicy
import com.afghanjama.data.buildAppDatabase
import java.util.concurrent.TimeUnit

/**
 * یادآورِ پایانِ شیفت: با هر «ورود»، یک یادآورِ یک‌باره برای ۳۰ دقیقه قبل
 * از پایانِ شیفتِ ۸ ساعته (یعنی ۷:۳۰ بعد از ورود) زمان‌بندی می‌شود تا اگر
 * خروجِ کارمند هنوز ثبت نشده باشد، با نوتیفیکیشن و «لرزش گوشی» به مدیریت
 * هشدار دهد که خروج را فراموش نکند. با ثبتِ خروج، یادآور لغو می‌شود.
 */
class ShiftReminderWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val employee = inputData.getString(KEY_EMPLOYEE) ?: return Result.success()
        val db = buildAppDatabase(applicationContext)
        try {
            val open = db.attendanceDao().findOpen(employee) ?: return Result.success()
            // فقط اگر واقعاً نزدیکِ پایان شیفت است (محافظ در برابر ورودِ تازه‌تر)
            val elapsed = System.currentTimeMillis() - open.checkIn
            if (elapsed >= WARN_AFTER_MS - TimeUnit.MINUTES.toMillis(10)) {
                showNotification(employee)
            }
        } finally {
            db.close()
        }
        return Result.success()
    }

    private fun showNotification(employee: String) {
        if (!NotificationManagerCompat.from(applicationContext).areNotificationsEnabled()) return

        val manager =
            applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "یادآور پایان شیفت",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    enableVibration(true)
                    vibrationPattern = VIBRATE_PATTERN
                    description = "۳۰ دقیقه قبل از پایان شیفت ۸ ساعته، اگر خروج ثبت نشده باشد"
                }
            )
        }

        val text = "شیفت ۸ ساعتهٔ «$employee» تا ۳۰ دقیقهٔ دیگر تمام می‌شود — خروج را ثبت کنید."
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("پایان شیفت نزدیک است")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVibrate(VIBRATE_PATTERN)
            .setAutoCancel(true)
            .build()

        // شناسهٔ متفاوت برای هر کارمند تا هشدارها همدیگر را نپوشانند
        manager.notify(NOTIFICATION_ID_BASE + employee.hashCode().and(0xFFFF), notification)
    }

    companion object {
        const val CHANNEL_ID = "shift_reminder"
        const val NOTIFICATION_ID_BASE = 2000
        const val KEY_EMPLOYEE = "employee"

        /**
         * از `ShiftPolicy` می‌آید نه از اینجا: همان عدد را دو صفحه هم
         * می‌خوانند و اگر اینجا تعریف می‌شد، آن‌ها به WorkManager وابسته
         * می‌ماندند.
         */
        val WARN_AFTER_MS: Long = ShiftPolicy.WARN_AFTER_MS

        private val VIBRATE_PATTERN = longArrayOf(0, 600, 250, 600, 250, 900)

        private fun workName(employee: String) = "shift_reminder_$employee"

        /** زمان‌بندی یادآور برای این کارمند (با ورودِ جدید جایگزین می‌شود). */
        fun schedule(context: Context, employee: String) {
            val request = OneTimeWorkRequestBuilder<ShiftReminderWorker>()
                .setInitialDelay(WARN_AFTER_MS, TimeUnit.MILLISECONDS)
                .setInputData(workDataOf(KEY_EMPLOYEE to employee))
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                workName(employee), ExistingWorkPolicy.REPLACE, request
            )
        }

        /** لغو یادآور (وقتی خروج ثبت شد). */
        fun cancel(context: Context, employee: String) {
            WorkManager.getInstance(context).cancelUniqueWork(workName(employee))
        }
    }
}
