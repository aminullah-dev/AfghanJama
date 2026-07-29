package com.afghanjama.work

import java.util.Calendar

/**
 * زمان‌بندیِ یادآورهای نان و چای — ریاضیِ خالص، بدونِ هیچ وابستگیِ اندرویدی.
 *
 * عمداً از `BreakReminderWorker` جدا شد تا آزمونِ JVM بتواند صدایش بزند.
 * آن کلاس WorkManager و Notification را می‌آورد و روی ماشینِ بی‌اندروید
 * اصلاً بار نمی‌شود؛ این تابع همان چیزی است که واقعاً باید سنجیده شود.
 */
object BreakSchedule {

    /**
     * فاصله تا نوبتِ بعدیِ [hour]:[minute]. اگر امروز گذشته باشد، فردا.
     * همیشه مثبت است تا WorkManager فوراً شلیک نکند.
     */
    fun delayUntilNext(
        hour: Int,
        minute: Int,
        now: Long = System.currentTimeMillis()
    ): Long {
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
}
