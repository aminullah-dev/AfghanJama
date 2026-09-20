package com.afghanjama.platform

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * یادآورهایی که سیستم‌عامل نگه می‌دارد — حتی وقتی اپ بسته است.
 *
 * **چرا واسط شد.** صفحهٔ حضور و غیاب به‌خاطرِ همین دو کار در `:app`
 * مانده بود: یادآورِ «فلانی هنوز خروج نزده» و یادآورِ وقتِ استراحت.
 * هر دو روی اندروید با `WorkManager` انجام می‌شوند و هیچ‌کدام کدِ
 * دامنه نیستند — زمان‌بندی کارِ سکو است.
 *
 * **و چرا `null` جوابِ درستی است.** پی‌سیِ کارگاه اعلانِ زمان‌بندی‌شده
 * ندارد؛ پیاده‌سازیِ ساختگی که چیزی زمان‌بندی نکند یعنی کاربر سوییچ
 * را روشن کند و هیچ یادآوری نیاید — بی آنکه بداند چرا. `null` یعنی
 * «این سکو نمی‌تواند» و صفحه همان را نشان می‌دهد.
 *
 * شناسه‌ها عمداً از جنسِ دامنه‌اند (نامِ کارمند، شناسهٔ سطرِ استراحت)
 * نه از جنسِ سکو، تا این واسط چیزی از `WorkManager` نداند.
 */
interface Reminders {

    /** یادآوری که بگوید [employee] هنوز خروج نزده. */
    fun scheduleShift(employee: String)

    fun cancelShift(employee: String)

    /** یادآورِ وقتِ استراحت، هر روز در [hour]:[minute]. */
    fun scheduleBreak(id: Long, hour: Int, minute: Int)

    fun cancelBreak(id: Long)
}

/** `null` یعنی این سکو یادآورِ زمان‌بندی‌شده ندارد. */
val LocalReminders = staticCompositionLocalOf<Reminders?> { null }
