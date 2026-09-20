package com.afghanjama.util

import com.afghanjama.util.nowMillis
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
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

/**
 * ساعت و دقیقهٔ یک زمانِ مشخص، در منطقهٔ زمانیِ دستگاه.
 *
 * **چرا اینجا و نه در صفحه.** `AttendanceScreen` تا دیروز این‌ها را با
 * `java.util.Calendar` می‌ساخت و همان یک کلاس صفحه را به `:app` گره
 * می‌زد. حالا که صفحه مشترک شده، جایگزینش باید مشترک باشد — و یک
 * تعریف، نه یکی در هر صفحه.
 */
fun hourOf(millis: Long): Int =
    Instant.fromEpochMilliseconds(millis)
        .toLocalDateTime(TimeZone.currentSystemDefault())
        .hour

fun minuteOf(millis: Long): Int =
    Instant.fromEpochMilliseconds(millis)
        .toLocalDateTime(TimeZone.currentSystemDefault())
        .minute

/**
 * همان **روزِ** [base] با ساعتِ [h] و دقیقهٔ [m]؛ ثانیه صفر.
 *
 * نگه داشتنِ روز عمدی است: اصلاحِ حضور و غیاب «ساعت را اشتباه زدم»
 * است، نه «روز را» — و بی این، یک بازه بی‌خبر به روزِ دیگری می‌پرید.
 */
fun withTime(base: Long, h: Int, m: Int): Long {
    val tz = TimeZone.currentSystemDefault()
    val d = Instant.fromEpochMilliseconds(base).toLocalDateTime(tz).date
    return LocalDateTime(d.year, d.monthNumber, d.dayOfMonth, h.coerceIn(0, 23), m.coerceIn(0, 59))
        .toInstant(tz)
        .toEpochMilliseconds()
}
