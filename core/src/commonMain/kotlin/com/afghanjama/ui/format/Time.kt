package com.afghanjama.ui.format

import com.afghanjama.util.nowMillis

/** تعداد روزهایی که سفارش در مرحله فعلی مانده است. */
fun stageDays(stageChangedAt: Long, createdAt: Long): Long {
    val start = if (stageChangedAt > 0) stageChangedAt else createdAt
    return ((nowMillis() - start) / 86_400_000L).coerceAtLeast(0)
}

/** آستانه هشدار معطلی سفارش در یک مرحله (روز). */
const val STAGE_WARN_DAYS = 4L

/** آستانهٔ هشدارِ «نزدیکِ مهلت» (روز). */
const val DUE_SOON_DAYS = 3L

/** مهلت تعیین شده و از آن گذشته‌ایم؟ */
fun isOverdue(dueDate: Long, now: Long = nowMillis()): Boolean =
    dueDate > 0 && now > dueDate

/** روزهای باقی‌مانده تا مهلت (رو به بالا؛ برای نمایشِ «۳ روز مانده»). */
fun dueDaysLeft(dueDate: Long, now: Long = nowMillis()): Long =
    kotlin.math.ceil((dueDate - now) / 86_400_000.0).toLong().coerceAtLeast(0)

/** روزهای گذشته از مهلت (برای نمایشِ «۲ روز تأخیر»). */
fun dueDaysLate(dueDate: Long, now: Long = nowMillis()): Long =
    ((now - dueDate) / 86_400_000L).coerceAtLeast(0)

/** مهلت تعیین شده و تا [DUE_SOON_DAYS] روزِ دیگر سر می‌رسد (و هنوز نگذشته)؟ */
fun isDueSoon(dueDate: Long, now: Long = nowMillis()): Boolean =
    dueDate > 0 && !isOverdue(dueDate, now) && dueDaysLeft(dueDate, now) <= DUE_SOON_DAYS

/** مدتِ سپری‌شده از [fromMillis] تا [now] به شکل «۳:۲۵» (ساعت:دقیقه، ارقام فارسی). */
fun elapsedHm(fromMillis: Long, now: Long = nowMillis()): String {
    val mins = ((now - fromMillis) / 60_000L).coerceAtLeast(0)
    return "${mins / 60}:${(mins % 60).toString().padStart(2, '0')}".toPersianDigits()
}
