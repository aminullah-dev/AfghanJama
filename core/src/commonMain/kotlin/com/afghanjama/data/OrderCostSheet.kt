package com.afghanjama.data

import com.afghanjama.data.entities.Order

/**
 * «برگهٔ هزینهٔ سفارش» — روی این سفارش چقدر خرج شد و چقدر سود کرد؟
 *
 * **مسئله‌ای که حل می‌کند.** اپ بهای تمام‌شده و قیمتِ توافقی را دارد، ولی
 * کنارِ هم نمی‌گذاردشان. کارفرما نمی‌داند «این کتِ ۲ هزارافغانی، با پارچه و
 * کارمزد و دوخت، چقدر برایم ماند؟» و بدتر: کدام سفارش **زیرِ قیمتِ
 * تمام‌شده** فروخته شد (ضرر، بی‌آنکه کسی بفهمد).
 *
 * **یک منبع.** بهای تمام‌شده همان فرمولی است که انبار، گزارش‌ها و تولید
 * به‌کار می‌برند: `fabricPrice + workCost + sewingCost` (پارچه + خرج‌کار +
 * دستمزدِ دوخت). درآمد = `agreedPrice`. اینجا چیزی تازه حساب نمی‌شود، فقط
 * کنارِ هم گذاشته و سود/حاشیه از آن درمی‌آید.
 *
 * **قیمتِ توافق‌نشده (۰) سودِ قلابی نمی‌سازد.** سفارشی که هنوز قیمت ندارد،
 * سود و حاشیه‌اش `null` است، نه صفر — همان قاعدهٔ «نامعلوم، نه صفر» اپ.
 *
 * خالصِ منطق: بی دیتابیس و بی Compose، تا آزمون مستقیم بسنجدش.
 */
object OrderCostSheet {

    /** برگهٔ هزینهٔ یک سفارش. مبلغ‌ها کلِ سفارش‌اند، نه هر عدد. */
    data class Sheet(
        val qty: Int,
        /** قیمتِ فروشِ توافق‌شده؛ ۰ یعنی هنوز توافق نشده. */
        val revenue: Long,
        val fabric: Long,
        val work: Long,
        val sewing: Long,
    ) {
        /** بهای تمام‌شدهٔ کل — همان فرمولِ مشترکِ اپ. */
        val cost: Long get() = fabric + work + sewing

        /** قیمت توافق شده؟ (وگرنه سود معنا ندارد.) */
        val hasPrice: Boolean get() = revenue > 0

        /** سودِ کلِ سفارش؛ `null` وقتی قیمت توافق نشده. */
        val profit: Long? get() = if (hasPrice) revenue - cost else null

        /** حاشیهٔ سود به درصد؛ `null` وقتی قیمت نیست. منفی یعنی ضرر. */
        val marginPercent: Int? get() =
            if (hasPrice && revenue > 0) (((revenue - cost) * 100) / revenue).toInt() else null

        /** بهای تمام‌شدهٔ هر عدد. */
        val unitCost: Long get() = if (qty > 0) cost / qty else cost

        /** زیرِ قیمتِ تمام‌شده فروخته شد؟ (ضرر — مهم‌ترین هشدارِ این برگه.) */
        val belowCost: Boolean get() = hasPrice && revenue < cost
    }

    fun of(fabricPrice: Long, workCost: Long, sewingCost: Long, agreedPrice: Long, qty: Int): Sheet =
        Sheet(qty = qty, revenue = agreedPrice, fabric = fabricPrice, work = workCost, sewing = sewingCost)

    /** از خودِ سفارش — جای مپ‌کردنِ فیلدها یک‌جا بماند. */
    fun of(order: Order): Sheet =
        of(order.fabricPrice, order.workCost, order.sewingCost, order.agreedPrice, order.qty)

    /**
     * جمعِ چند سفارش. سود و حاشیه فقط از سفارش‌هایی که **قیمت دارند** حساب
     * می‌شود؛ بی‌قیمت‌ها شمرده می‌شوند ولی درآمد/هزینه‌شان در جمع نمی‌آید تا
     * حاشیه کج نشود.
     */
    data class Summary(
        val orders: Int,
        val priced: Int,
        val revenue: Long,
        val cost: Long,
        val belowCostCount: Int,
    ) {
        val profit: Long get() = revenue - cost
        val marginPercent: Int? get() = if (revenue > 0) ((profit * 100) / revenue).toInt() else null
        val hasData: Boolean get() = orders > 0
    }

    fun summarize(sheets: List<Sheet>): Summary {
        val priced = sheets.filter { it.hasPrice }
        return Summary(
            orders = sheets.size,
            priced = priced.size,
            revenue = priced.sumOf { it.revenue },
            cost = priced.sumOf { it.cost },
            belowCostCount = priced.count { it.belowCost },
        )
    }
}
