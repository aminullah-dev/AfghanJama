package com.afghanjama.work

import com.afghanjama.data.ShiftPolicy
import com.afghanjama.data.entities.AttendanceRecord
import com.afghanjama.data.entities.BreakTime
import com.afghanjama.ui.format.fa
import com.afghanjama.ui.format.toPersianDigits

/**
 * کدام یادآور همین حالا وقتش است — پایانِ شیفت و وقتِ نان و چای.
 *
 * **چرا جدا و خالص.** گوشی این را به `WorkManager` می‌سپارد: هر «ورود» یک
 * کارِ زمان‌بندی‌شده می‌سازد. کامپیوتر چنین چیزی ندارد و برنامه‌اش ممکن است
 * بعد از «ورود» باز شود (ورود از گوشیِ کارگر، یا دیروز). پس کامپیوتر به‌جای
 * زمان‌بندی، هر چند ثانیه از دفتر می‌پرسد «چه کسی داخل است و ساعت چند
 * است؟» و این تابع جواب می‌دهد چه چیزی باید گفته شود. همان قاعده‌های
 * گوشی، بی هیچ ساعتِ سکو — تا آزمونِ واحد بسنجدش.
 *
 * [done] کلیدِ یادآورهایی است که گفته شده‌اند؛ هر یادآور یک بار. کامپیوتر
 * این کلیدها را روی دیسک نگه می‌دارد (`DeskPrefs.shownReminders`)، تا بستن و
 * باز کردنِ برنامه یادآور را تکرار نکند.
 */
object ReminderPlan {

    /**
     * یادآورِ وقتِ استراحت تا چند دقیقه پس از ساعتش هنوز گفته می‌شود — برای
     * برنامه‌ای که چند دقیقه دیر باز شده. بعد از آن کهنه است و گفته نمی‌شود.
     */
    const val BREAK_GRACE_MIN = 10

    data class Due(val key: String, val title: String, val text: String)

    /**
     * شیفت‌های بازی که به ۳۰ دقیقهٔ آخر رسیده‌اند (یا از ۸ ساعت گذشته‌اند و
     * خروجشان ثبت نشده).
     */
    fun shifts(open: List<AttendanceRecord>, now: Long, done: Set<String>): List<Due> =
        open.asSequence()
            .filter { it.checkOut == null && ShiftPolicy.nearShiftEnd(it.checkIn, now) }
            .map { it to "shift|${it.employee}|${it.checkIn}" }
            .filter { (_, key) -> key !in done }
            .map { (r, key) ->
                val hours = ShiftPolicy.SHIFT_MS / 3_600_000L
                val leftMin = (r.checkIn + ShiftPolicy.SHIFT_MS - now) / 60_000L
                Due(
                    key = key,
                    title = "پایانِ شیفت",
                    text = if (leftMin > 0) {
                        "شیفتِ ${hours.fa()} ساعتهٔ «${r.employee}» تا ${leftMin.fa()} دقیقهٔ دیگر " +
                            "تمام می‌شود؛ خروج را ثبت کنید."
                    } else {
                        "شیفتِ ${hours.fa()} ساعتهٔ «${r.employee}» تمام شده و خروجش هنوز ثبت نشده."
                    }
                )
            }
            .toList()

    /**
     * وقت‌های نان و چایی که همین حالا رسیده‌اند.
     *
     * [minuteOfDay] ساعتِ محلی به دقیقه از نیمه‌شب؛ [day] شمارهٔ روز (هر
     * عددی که روز به روز عوض شود) — یک وقت هر روز یک بار.
     */
    fun breaks(
        list: List<BreakTime>,
        minuteOfDay: Int,
        day: Long,
        inside: Int,
        done: Set<String>,
    ): List<Due> =
        list.asSequence()
            .filter { it.enabled }
            .filter {
                val at = it.hour.coerceIn(0, 23) * 60 + it.minute.coerceIn(0, 59)
                minuteOfDay >= at && minuteOfDay < at + BREAK_GRACE_MIN
            }
            .map { it to "break|${it.id}|$day" }
            .filter { (_, key) -> key !in done }
            .map { (b, key) ->
                Due(
                    key = key,
                    title = b.title,
                    // همان متنِ یادآورِ گوشی (`BreakReminderWorker`).
                    text = if (inside > 0) "${b.clock.toPersianDigits()} — ${inside.fa()} نفر داخلِ کارگاه‌اند."
                    else "${b.clock.toPersianDigits()} — کسی ورود نزده است."
                )
            }
            .toList()
}
