package com.afghanjama.ui.vm

import com.afghanjama.util.nowMillis
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

/** یک دقیقه. */
const val MINUTE_MS = 60_000L

/** یک ساعت. */
const val HOUR_MS = 60 * MINUTE_MS

/**
 * «الان»، که جلو می‌رود — سرِ هر دقیقه (و پس نیمه‌شب هم، چون نیمه‌شب سرِ دقیقه است).
 *
 * **چرا لازم شد.** روی کامپیوتر ViewModelها تا بسته شدنِ برنامه زنده‌اند و
 * پنجره روزها باز می‌ماند. هر جریانی که «الان» را فقط وقتی حساب می‌کرد که
 * دفتر عوض می‌شد، در کارگاهِ آرام کهنه می‌ماند: «معطل ۴ روز» دیروز هم ۴ روز
 * بود، پیگیریِ طلبِ «امروز» تا اولین ثبتِ فردا همان فهرستِ دیروز بود، و
 * پنجرهٔ «۳۰ روزِ» مصرفِ مواد از لحظهٔ باز شدنِ برنامه شمرده می‌شد و کش
 * می‌آمد (بازبینی، بندِ ۱۸). حالا هشدارها، پیش‌بینیِ تحویل، پیگیریِ طلب،
 * وضعِ مالی و داشبورد این را می‌گیرند.
 *
 * مرزِ دقیقه به ساعتِ جهانی است و چون هر منطقهٔ زمانیِ امروزی دقیقهٔ کامل
 * جابه‌جاست، نیمه‌شبِ محلی هم سرِ یکی از همین ضربه‌هاست.
 */
fun clockTicks(periodMs: Long = MINUTE_MS, clock: () -> Long = { nowMillis() }): Flow<Long> = flow {
    while (true) {
        val now = clock()
        emit(now)
        delay(nextTickDelay(now, periodMs))
    }
}

/** تا سرِ دورهٔ بعدی چند میلی‌ثانیه مانده — دست‌کم یک. */
fun nextTickDelay(now: Long, periodMs: Long): Long {
    val left = periodMs - mod(now, periodMs)
    return if (left <= 0L) periodMs else left
}

/** همان ضربه‌ها، ولی فقط وقتی ساعت عوض شد — برای پرسش‌هایی که بازهٔ «از» دارند. */
fun Flow<Long>.hourly(): Flow<Long> = map { it - mod(it, HOUR_MS) }.distinctUntilChanged()

/**
 * همان مقدار، دوباره با هر ضربهٔ [ticks] — تا جریانی که درونش «الان» را
 * می‌خوانَد، بی تغییرِ دفتر هم دوباره حساب شود.
 */
fun <T> Flow<T>.reemitOn(ticks: Flow<Long>): Flow<T> = combine(this, ticks) { v, _ -> v }

/** هر مقدار همراهِ «الانِ» همان لحظه — برای قاعده‌ای که زمان را در حسابش دارد. */
fun <T> Flow<T>.at(ticks: Flow<Long>): Flow<Pair<T, Long>> = combine(this, ticks) { v, n -> v to n }

private fun mod(a: Long, m: Long): Long = ((a % m) + m) % m
