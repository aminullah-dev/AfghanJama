package com.afghanjama.data

import com.afghanjama.data.entities.OrderStatus

/**
 * متنِ آمادهٔ پیام به مشتری دربارهٔ وضعیتِ سفارشش.
 *
 * **چرا یک شیءِ خالص و نه رشته در دلِ صفحه.** پیام به مرحلهٔ سفارش بستگی
 * دارد: «آماده شد، بیایید» با «در مرحلهٔ دوخت است» فرق دارد. اگر این
 * if/else داخلِ یک دکمه در صفحه بنشیند، نه CI می‌سنجدش نه کسی مطمئن است
 * هر هشت حالت جمله‌ای درست می‌سازند. این‌جا منطق از UI جدا می‌مانَد و
 * آزمون هر حالت را می‌بندد؛ صفحه فقط همین خروجی را به واتس‌اپ/پیامک
 * می‌سپارد.
 *
 * به [OrderPipeline] تکیه می‌کند تا نامِ مرحله‌ها در یک جا بماند.
 */
object OrderMessage {

    /**
     * یک پیامِ مؤدبِ فارسی برای مشتری، بر پایهٔ طرح و وضعیتِ سفارش.
     *
     * [workshop] نامِ کارگاه است و اگر خالی نباشد در امضا می‌آید.
     * [designTitle] اگر خالی باشد، «سفارشِ شما» جایش می‌نشیند تا جمله
     * هیچ‌وقت ناقص نشود.
     */
    fun forCustomer(workshop: String, designTitle: String, status: String): String {
        val design = designTitle.trim().ifBlank { "سفارشِ شما" }
        val idx = OrderPipeline.indexOf(status)
        val core = when {
            OrderPipeline.isComplete(status) ->
                "«$design» تحویل شد. از اعتمادتان سپاسگزاریم."
            status == OrderStatus.STORED.name ->
                "«$design» آماده شد؛ لطفاً برای تحویل تشریف بیاورید."
            idx <= 0 ->
                "«$design» ثبت شد و در نوبتِ کار است."
            else ->
                "«$design» در مرحلهٔ ${OrderPipeline.STAGES[idx].label} است."
        }
        val sign = workshop.trim().takeIf { it.isNotEmpty() }?.let { "\n— $it" } ?: ""
        return "سلام، $core$sign"
    }
}
