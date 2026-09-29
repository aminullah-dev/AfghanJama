package com.afghanjama.data

import com.afghanjama.util.NameMatch

/**
 * پیشنهادِ قیمت — از فروش‌های واقعیِ همین کارگاه، نه از فرمول.
 *
 * **چرا پیشنهاد و نه پرکردنِ خودکار.** فیلدِ قیمت عمداً خالی می‌ماند:
 * قیمت تصمیمِ فروشنده است، و قیمتی که خودش پر شود بی آنکه کسی نگاهش
 * کند، همان است که ماه‌ها بعد زیرِ بها پیدا می‌شود. اینجا فقط چند عددِ
 * **زدنی** کنارِ فیلد می‌نشیند و هر کدام می‌گوید از کجا آمده:
 *
 * - **آخرین فروش** — همین کالا آخرین بار چند رفت.
 * - **معمول** — میانهٔ قیمتِ فروش‌های اخیرِ همین کالا. میانه و نه
 *   میانگین: یک فروشِ حراجی یا یک اشتباهِ تایپی میانگین را می‌کشد، میانه
 *   را نه.
 * - **با سودِ معمولِ شما** — بهای تمام‌شدهٔ **امروز** به‌اضافهٔ درصدی که
 *   کارگاه معمولاً سود می‌برد (میانهٔ درصدِ سودِ فروش‌های اخیر). وقتی
 *   پارچه گران شده و قیمتِ همیشگی دیگر سود ندارد، همین عدد بالاتر از
 *   «معمول» می‌آید و [Advice.usualBelowCost] هشدار می‌دهد.
 *
 * بی تاریخچه، هیچ عددی ساخته نمی‌شود — مثلِ `WorkshopLoad`.
 */
object PriceAdvisor {

    /** فروش‌های چند روزِ اخیر شمرده می‌شوند؛ قیمتِ دو سال پیش قیمتِ امروز نیست. */
    const val WINDOW_DAYS = 180

    /** «سودِ معمول» از کمتر از این تعداد فروش ساخته نمی‌شود. */
    const val MIN_SAMPLES = 3

    /** «معمول» از چند فروشِ آخر حساب می‌شود. */
    private const val RECENT = 10

    private const val DAY = 86_400_000L

    /**
     * یک فروشِ گذشته، هر عدد. [unitCost] صفر یعنی بها ثبت نشده بود.
     *
     * [netUnitPrice] پس از تخفیف است و سود از آن حساب می‌شود؛ [unitPrice]
     * همان قیمتی است که فروشنده زده و پیشنهاد از آن می‌آید.
     */
    data class Sold(
        val key: String,
        val unitPrice: Long,
        val unitCost: Long,
        val at: Long,
        val netUnitPrice: Long = unitPrice,
    )

    /** فروش‌های گذشته و سودِ معمول، یک‌جا — آنچه صفحه نگه می‌دارد. */
    data class Book(val sold: List<Sold> = emptyList(), val margin: Int? = null, val now: Long = 0L) {
        fun advise(key: String, cost: Long): Advice = advise(key, cost, sold, now, margin)
    }

    fun book(sold: List<Sold>, now: Long): Book = Book(sold, usualMargin(sold, now), now)

    /** کلیدِ «همین کالا» — نام و سایز، بی حساسیت به ی/ک و فاصله. */
    fun key(name: String, size: String = ""): String =
        NameMatch.normalize(name) + "|" + NameMatch.normalize(size)

    /** میانه؛ برای تعدادِ زوج، پایینیِ دو وسطی — تا عددی ساخته نشود که کسی نفروخته. */
    fun median(xs: List<Long>): Long? {
        if (xs.isEmpty()) return null
        val s = xs.sorted()
        return s[(s.size - 1) / 2]
    }

    /**
     * درصدِ سودی که کارگاه معمولاً می‌برد — میانهٔ درصدِ سودِ فروش‌های
     * [WINDOW_DAYS] روزِ اخیر که بهایشان معلوم بوده. همان درصدی که
     * [Margin.Line.percent] برای هر فروش نشان می‌دهد.
     */
    fun usualMargin(sold: List<Sold>, now: Long): Int? {
        val since = now - WINDOW_DAYS * DAY
        val pcts = sold
            .filter { it.at in since..now && it.unitCost > 0 && it.netUnitPrice > 0 }
            .mapNotNull { Margin.Line(it.unitCost, it.netUnitPrice, 1).percent?.toLong() }
        if (pcts.size < MIN_SAMPLES) return null
        return median(pcts)?.toInt()
    }

    data class Advice(
        val lastPrice: Long?,
        val lastAt: Long?,
        val usualPrice: Long?,
        /** از چند فروش. */
        val samples: Int,
        val marginPercent: Int?,
        val marginPrice: Long?,
        val cost: Long,
    ) {
        /** قیمتِ همیشگی با بهای امروز دیگر سود ندارد. */
        val usualBelowCost: Boolean get() = usualPrice != null && cost > 0L && usualPrice <= cost

        val isEmpty: Boolean get() = lastPrice == null && marginPrice == null
    }

    /**
     * پیشنهاد برای کالای [key] با بهای تمام‌شدهٔ امروزِ [cost] (هر عدد).
     * [margin] همان [usualMargin] است — یک بار برای کلِ کارگاه حساب
     * می‌شود، نه برای هر ردیف.
     */
    fun advise(key: String, cost: Long, sold: List<Sold>, now: Long, margin: Int?): Advice {
        val since = now - WINDOW_DAYS * DAY
        val mine = sold
            .filter { it.key == key && it.unitPrice > 0 && it.at in since..now }
            .sortedByDescending { it.at }
        val recent = mine.take(RECENT)
        return Advice(
            lastPrice = mine.firstOrNull()?.unitPrice,
            lastAt = mine.firstOrNull()?.at,
            usualPrice = median(recent.map { it.unitPrice }),
            samples = recent.size,
            marginPercent = margin,
            marginPrice = if (cost > 0L && margin != null) Margin.priceFor(cost, margin) else null,
            cost = cost,
        )
    }
}
