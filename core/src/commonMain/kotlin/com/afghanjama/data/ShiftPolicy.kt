package com.afghanjama.data

/**
 * قاعدهٔ شیفتِ کاری.
 *
 * این یک **تصمیمِ کارگاه** است، نه تنظیمِ زمان‌بندِ اندروید — ولی تا
 * امروز داخلِ `ShiftReminderWorker` نشسته بود و هر صفحه‌ای که فقط
 * می‌خواست بداند «آیا این کارمند از شیفتش گذشته؟» به WorkManager وابسته
 * می‌شد.
 *
 * حالا قاعده اینجاست و زمان‌بند از همین‌جا می‌خواندش؛ دو مصرف‌کننده،
 * یک عدد.
 */
object ShiftPolicy {

    /** شیفتِ ۸ ساعته. */
    const val SHIFT_MS: Long = 8L * 60 * 60 * 1000

    /** هشدار ۳۰ دقیقه پیش از پایان. */
    const val WARN_BEFORE_END_MS: Long = 30L * 60 * 1000

    /** از ورود تا هشدار — ۷ ساعت و ۳۰ دقیقه. */
    const val WARN_AFTER_MS: Long = SHIFT_MS - WARN_BEFORE_END_MS

    /** آیا این کارمند به پایانِ شیفتش نزدیک شده؟ */
    fun nearShiftEnd(checkIn: Long, now: Long): Boolean =
        now - checkIn >= WARN_AFTER_MS
}
