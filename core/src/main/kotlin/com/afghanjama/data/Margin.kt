package com.afghanjama.data

/**
 * سودِ هر فروش، همان لحظه‌ای که قیمت زده می‌شود.
 *
 * اپ بهای تمام‌شدهٔ هر کالا را دقیق می‌داند (پارچه + خرج‌کار + دستمزد
 * خیاط)، ولی موقعِ فروش قیمت کاملاً دستی زده می‌شود و هیچ‌جا نمی‌گوید
 * این فروش چقدر سود دارد. در کارگاهی که چند نفر فروش می‌زنند، فروش زیرِ
 * بهای تمام‌شده اتفاق می‌افتد و ماه‌ها بعد کسی می‌فهمد.
 *
 * درصد نسبت به **بهای تمام‌شده** حساب می‌شود — همان‌طور که در بازار
 * گفته می‌شود: «صد خریدم، صدوبیست فروختم، بیست درصد سود».
 */
object Margin {

    /**
     * [cost] بهای تمام‌شدهٔ هر عدد و [price] قیمتِ فروشِ هر عدد.
     *
     * [percent] وقتی null است که بهای تمام‌شده صفر باشد — تقسیم بر صفر
     * معنا ندارد و عددِ ساختگی بدتر از نگفتن است.
     */
    data class Line(val cost: Long, val price: Long, val qty: Int) {
        val costTotal: Long get() = cost * qty
        val revenue: Long get() = price * qty
        val profit: Long get() = revenue - costTotal

        val percent: Int?
            get() = if (cost > 0L) ((price - cost) * 100.0 / cost).toInt() else null

        /** فروش زیرِ بهای تمام‌شده — باید در نگاهِ اول دیده شود. */
        val losing: Boolean get() = cost > 0L && price in 1 until cost

        /** بهای تمام‌شده ثبت نشده، پس دربارهٔ سود نمی‌شود چیزی گفت. */
        val unknownCost: Boolean get() = cost <= 0L
    }

    /**
     * جمعِ سودِ یک فاکتور. ردیف‌های بی‌بها کنار گذاشته نمی‌شوند — بهای
     * صفرشان صفر حساب می‌شود — ولی [hasUnknownCost] می‌گوید که جمع کامل
     * نیست، تا عددِ نصفه به‌جای عددِ درست فروخته نشود.
     */
    data class Invoice(val lines: List<Line>, val discount: Long = 0L) {
        val costTotal: Long get() = lines.sumOf { it.costTotal }
        val revenue: Long get() = lines.sumOf { it.revenue } - discount
        val profit: Long get() = revenue - costTotal
        val hasUnknownCost: Boolean get() = lines.any { it.unknownCost && it.qty > 0 }
        val losing: Boolean get() = profit < 0L

        val percent: Int?
            get() = if (costTotal > 0L) ((profit * 100.0) / costTotal).toInt() else null
    }
}
