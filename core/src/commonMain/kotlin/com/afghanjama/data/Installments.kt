package com.afghanjama.data

import com.afghanjama.util.nowMillis
import com.afghanjama.data.entities.CustomerInstallment

/**
 * کدام قسط تسویه شده — مشتق از پرداخت‌های واقعی، نه ثبت‌شده جدا.
 *
 * جدولِ `customer_installments` فقط قول را نگه می‌دارد. اینکه مشتری
 * چقدر داده در دفتر است (`CustomerSummary.totalPaid`). این شیء آن دو را
 * به هم می‌رساند: پولِ پرداخت‌شده را از **قدیمی‌ترین** قسط به بعد پخش
 * می‌کند.
 *
 * **چرا قدیمی‌ترین-اول.** همان کاری است که هر دفترداری با دست می‌کند و
 * مشتری هم همان را انتظار دارد: پولی که امروز می‌دهد اول بدهیِ عقب‌افتاده
 * را می‌بندد، نه قسطِ ماهِ بعد را. اگر برعکس بود، قسطِ گذشته برای همیشه
 * سررسیدگذشته می‌ماند در حالی که مشتری پولش را داده.
 *
 * **چرا پرچمِ «پرداخت‌شده» نداریم.** با پرچم، دو جا دربارهٔ یک بدهی نظر
 * می‌دادند و روزی از هم می‌افتادند — همان دامی که `Margin` را از آن
 * دور نگه داشتیم. با اشتقاق، اختلاف ممکن نیست: قسط چیزی جز جمعِ
 * پرداخت‌ها نمی‌داند.
 */
object Installments {

    /** یک قسط با سهمی که از پرداخت‌های مشتری به آن رسیده. */
    data class Row(val plan: CustomerInstallment, val paid: Long) {

        val remaining: Long get() = plan.amount - paid

        /** روزش گذشته و هنوز کامل پرداخت نشده. */
        fun overdue(now: Long = nowMillis()): Boolean =
            remaining > 0L && plan.dueDate in 1 until now
    }

    /**
     * [totalPaid] را روی [schedule] پخش می‌کند، از قسطی که زودتر سررسید
     * می‌شود به بعد.
     *
     * ترتیبِ ورودی مهم نیست — خودش مرتب می‌کند — تا صفحه و گزارش و
     * هشدار همیشه یک جواب بگیرند. `id` شکنندهٔ تساوی است تا دو قسطِ
     * هم‌تاریخ هم ترتیبِ ثابت داشته باشند.
     *
     * اگر مشتری بیش از جمعِ قسط‌ها داده باشد، مانده‌اش اینجا جایی نمی‌رود:
     * قسط‌بندی دربارهٔ کلِ حساب حرف نمی‌زند، فقط دربارهٔ همین قول‌ها.
     */
    fun allocate(schedule: List<CustomerInstallment>, totalPaid: Long): List<Row> {
        var pool = totalPaid.coerceAtLeast(0L)
        return schedule
            .sortedWith(compareBy({ it.dueDate }, { it.id }))
            .map { plan ->
                val applied = pool.coerceAtMost(plan.amount.coerceAtLeast(0L))
                pool -= applied
                Row(plan, applied)
            }
    }
}
