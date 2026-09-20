package com.afghanjama.work

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import com.afghanjama.util.nowMillis

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
        now: Long = nowMillis()
    ): Long {
        val zone = TimeZone.currentSystemDefault()
        val today = Instant.fromEpochMilliseconds(now).toLocalDateTime(zone).date
        val at = LocalDateTime(
            today.year, today.monthNumber, today.dayOfMonth,
            hour.coerceIn(0, 23), minute.coerceIn(0, 59), 0
        ).toInstant(zone).toEpochMilliseconds()
        if (at > now) return at - now
        // نوبتِ امروز گذشته، پس همان ساعت در روزِ بعد. جمعِ ۲۴ ساعت
        // کافی نیست: روزِ تغییرِ ساعتِ تابستانی ۲۳ یا ۲۵ ساعت است و
        // یادآور یک ساعت جابه‌جا می‌شد.
        val tomorrow = today.plus(1, DateTimeUnit.DAY)
        return LocalDateTime(
            tomorrow.year, tomorrow.monthNumber, tomorrow.dayOfMonth,
            hour.coerceIn(0, 23), minute.coerceIn(0, 59), 0
        ).toInstant(zone).toEpochMilliseconds() - now
    }
}
