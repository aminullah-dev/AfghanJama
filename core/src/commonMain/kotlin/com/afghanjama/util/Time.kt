package com.afghanjama.util

import com.afghanjama.util.nowMillis
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/*
 * ساعتِ دیوار — **بی وابستگی به JVM.**
 *
 * `System.currentTimeMillis()` در ۶۴ فایلِ `:core` بود و هیچ‌کدام
 * `import`ی نداشتند، چون `java.lang.System` روی JVM خودکار وارد
 * می‌شود. یعنی اسکنِ `import java.*` این ۶۴ تا را **اصلاً نشان نداد** و
 * تازه وقتی دنبالِ خودِ نام گشتیم پیدا شدند.
 *
 * این را اینجا می‌نویسم تا اگر روزی سکوی تازه‌ای اضافه شد، فقط یک جا
 * عوض شود — نه ۶۴ جا.
 */
fun nowMillis(): Long = Clock.System.now().toEpochMilliseconds()

/**
 * ساعتِ فعلی به وقتِ محلی (۰ تا ۲۳).
 *
 * صفحهٔ خانه با این تصمیم می‌گیرد «صبح بخیر» بگوید یا «شب بخیر»، پس
 * باید ساعتِ دیوارِ کارگاه باشد نه UTC.
 */
fun currentHour(): Int =
    Instant.fromEpochMilliseconds(nowMillis())
        .toLocalDateTime(TimeZone.currentSystemDefault())
        .hour
