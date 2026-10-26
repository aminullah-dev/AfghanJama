package com.afghanjama.data

/**
 * «سود کنارِ هر طرف» — کدام مشتری و کدام خیاط بیشترین سود را ساخت.
 *
 * **مسئله‌ای که حل می‌کند.** اپ سود را به‌تفکیکِ محصول و سفارش می‌داند
 * (گزارش‌ها)، ولی نمی‌گوید «ارزشمندترین مشتری‌ام کیست؟» و «کدام خیاط
 * سودآورترین است؟». این دو، همان نگاهی است که ERPهای پیشرفته دارند و
 * کارفرما برای تصمیم (چه کسی را نگه دارم، روی چه کسی سرمایه بگذارم) لازم
 * دارد.
 *
 * **یک منبع، بی عددِ تازه.** درآمد و بهای تمام‌شده همان‌هایی‌اند که سفارش
 * دارد: درآمد = `agreedPrice`، بها = `fabricPrice + workCost + sewingCost`
 * (همان فرمولِ مشترکِ اپ). اینجا فقط کنارِ طرف‌ها گذاشته و جمع می‌شود.
 *
 * **سفارشِ بی‌قیمت سودِ قلابی نمی‌سازد** — فقط سفارشِ قیمت‌خورده شمرده
 * می‌شود؛ حاشیهٔ بی‌درآمد `null` است، نه صفر.
 *
 * **سودِ خیاط به نسبتِ کار.** یک سفارش می‌تواند چند خیاط داشته باشد (۲۰
 * دست به احمد، ۶ به بلال). سود و درآمدِ سفارش به نسبتِ تعدادِ دستِ هر خیاط
 * تقسیم می‌شود، نه همه به یک نفر.
 *
 * خالصِ منطق: بی دیتابیس و بی Compose، تا آزمون مستقیم بسنجدش.
 */
object PartyProfit {

    /** یک طرف (مشتری یا خیاط) و جمعِ کارش. */
    data class Row(
        val name: String,
        /** چند سفارش/کار. */
        val count: Int,
        val qty: Int,
        val revenue: Long,
        val cost: Long,
    ) {
        val profit: Long get() = revenue - cost

        /** حاشیهٔ سود به درصد؛ `null` وقتی درآمدی نیست. منفی یعنی ضرر. */
        val marginPercent: Int? get() =
            if (revenue > 0) ((profit * 100) / revenue).toInt() else null
    }

    /** یک قلمِ ورودی — یک سفارش از نگاهِ یک طرف. */
    data class Entry(val name: String, val qty: Int, val revenue: Long, val cost: Long)

    /**
     * جمعِ قلم‌ها کنارِ نام، مرتب از پرسودترین. نامِ خالی با [unknown]
     * جایگزین می‌شود تا در یک سطلِ «بی‌نام» جمع شود، نه گم.
     */
    fun summarize(entries: List<Entry>, unknown: String): List<Row> =
        entries
            .groupBy { it.name.trim().ifBlank { unknown } }
            .map { (name, es) ->
                Row(
                    name = name,
                    count = es.size,
                    qty = es.sumOf { it.qty },
                    revenue = es.sumOf { it.revenue },
                    cost = es.sumOf { it.cost },
                )
            }
            .sortedByDescending { it.profit }

    /** یک سفارش برای تقسیم بینِ خیاط‌ها: درآمد و بها و دستِ هر خیاط. */
    data class SewnOrder(
        val revenue: Long,
        val cost: Long,
        /** (نامِ خیاط، تعدادِ دست) — می‌تواند چند خیاط باشد. */
        val sewers: List<Pair<String, Int>>,
    )

    /**
     * درآمد و بهای هر سفارش را به نسبتِ تعدادِ دستِ هر خیاط می‌شکند و
     * قلم‌های آمادهٔ [summarize] می‌دهد. سفارشِ بی‌خیاط کنار می‌رود (تقسیم
     * بر صفر نمی‌کنیم و به کسی نمی‌چسبانیم).
     */
    fun tailorEntries(orders: List<SewnOrder>): List<Entry> =
        orders.flatMap { o ->
            val total = o.sewers.sumOf { it.second }
            if (total <= 0) emptyList()
            else o.sewers.map { (name, q) ->
                Entry(
                    name = name,
                    qty = q,
                    revenue = o.revenue * q / total,
                    cost = o.cost * q / total,
                )
            }
        }
}
