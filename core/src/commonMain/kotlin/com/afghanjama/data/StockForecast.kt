package com.afghanjama.data

import kotlin.math.roundToInt

/**
 * «چند روزِ دیگر تمام می‌شود؟» — از رویِ سرعتِ مصرفِ واقعی.
 *
 * انبار از قبل «حدِ هشدار» داشت، ولی آن یک عددِ ثابت است و نمی‌داند
 * پارچه‌ای که ماهی یک بار مصرف می‌شود با پارچه‌ای که هفته‌ای ده متر
 * می‌رود فرق دارد. در خیاطی، تمام شدنِ پارچه وسطِ سفارش یعنی خطِ تولید
 * می‌خوابد و مشتری معطل می‌شود.
 *
 * کاردکس از اول همهٔ ورود و خروج‌ها را ثبت می‌کرده و کسی از آن چیزی
 * نمی‌ساخت. اینجا از همان داده، نرخِ مصرف و روزهای باقی‌مانده درمی‌آید.
 *
 * هیچ چیزِ اندرویدی و هیچ دیتابیسی اینجا نیست تا CI بسنجدش.
 */
object StockForecast {

    /** پنجرهٔ پیش‌فرضِ نگاه به گذشته. */
    const val WINDOW_DAYS = 30

    /** کمتر از این تعداد روز، «کم‌آوردنی» شمرده می‌شود. */
    const val WARN_DAYS = 14

    /** یک خروجِ ثبت‌شده در کاردکس. */
    data class Draw(val name: String, val unit: String, val qty: Double, val atDay: Int)

    /** یک قلمِ انبار با موجودی و حدِ هشدارش. */
    data class Item(val name: String, val unit: String, val amount: Double, val minLevel: Double = 0.0)

    /**
     * پیش‌بینیِ یک قلم.
     *
     * [daysLeft] وقتی null است که مصرفی ثبت نشده باشد — آن‌وقت نمی‌شود
     * حدس زد و حدس زدن بدتر از نگفتن است.
     */
    data class Forecast(
        val name: String,
        val unit: String,
        val amount: Double,
        val perDay: Double,
        val daysLeft: Int?,
        val belowMin: Boolean
    ) {
        /** هم کم‌آوردنی، هم آنکه از حدِ هشدار پایین‌تر رفته. */
        val urgent: Boolean get() = belowMin || (daysLeft != null && daysLeft <= WARN_DAYS)
    }

    /**
     * [windowDays] تعدادِ روزی که به عقب نگاه می‌شود. اگر تاریخچه کوتاه‌تر
     * باشد صداکننده باید همان کوتاه‌تر را بدهد، وگرنه نرخ ساختگی کوچک
     * می‌شود و هشدار دیر می‌رسد.
     */
    fun forecast(
        items: List<Item>,
        draws: List<Draw>,
        windowDays: Int = WINDOW_DAYS
    ): List<Forecast> {
        val days = windowDays.coerceAtLeast(1)
        val usedBy = draws
            .filter { it.qty > 0.0 && it.atDay in 0 until days }
            .groupBy { key(it.name, it.unit) }
            .mapValues { (_, rows) -> rows.sumOf { it.qty } }

        return items.map { item ->
            val used = usedBy[key(item.name, item.unit)] ?: 0.0
            val perDay = used / days
            val left = if (perDay > 0.0 && item.amount > 0.0) {
                (item.amount / perDay).let { if (it > 999.0) 999 else it.roundToInt() }
            } else {
                null
            }
            Forecast(
                name = item.name.trim(),
                unit = item.unit.trim(),
                amount = item.amount,
                perDay = perDay,
                daysLeft = left,
                // حدِ هشدارِ صفر یعنی کاربر حدی نگذاشته، پس معیار نیست
                belowMin = item.minLevel > 0.0 && item.amount <= item.minLevel
            )
        }
    }

    /**
     * فقط آنهایی که باید دیده شوند، فوری‌ترین اول.
     *
     * قلمی که موجودی‌اش تمام شده ولی مصرفی هم ندارد بالا نمی‌آید؛ خبرِ
     * تازه‌ای نیست و فقط فهرست را شلوغ می‌کند.
     */
    fun needsAttention(all: List<Forecast>): List<Forecast> =
        all.filter { it.urgent && (it.daysLeft != null || it.belowMin) }
            .sortedWith(compareBy({ it.daysLeft ?: Int.MAX_VALUE }, { -it.perDay }))

    private fun key(name: String, unit: String) = name.trim() to unit.trim()
}
