package com.afghanjama.ui.format

/** تعداد روزهایی که سفارش در مرحله فعلی مانده است. */
fun stageDays(stageChangedAt: Long, createdAt: Long): Long {
    val start = if (stageChangedAt > 0) stageChangedAt else createdAt
    return ((System.currentTimeMillis() - start) / 86_400_000L).coerceAtLeast(0)
}

/** آستانه هشدار معطلی سفارش در یک مرحله (روز). */
const val STAGE_WARN_DAYS = 4L
