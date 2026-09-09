package com.afghanjama.pdf

/**
 * دسته‌بندیِ سندهای دفتر — رسیدِ پول است یا فاکتور؟
 *
 * **چرا از `DocumentRenderer` بیرون آمد.** آن شیء در `:app` است و
 * `android.graphics` می‌کشد، ولی این دو تابع هیچ چیزی نمی‌کشند: فقط
 * می‌گویند نوعِ سند چیست و کاغذِ منطقی‌اش کدام است. ماندنشان آنجا یعنی
 * صفحهٔ اسناد — که هیچ چیزِ اندرویدیِ دیگری ندارد — به `:app` گره
 * می‌خورد و ویندوز اصلاً چنین صفحه‌ای نداشته باشد.
 *
 * حالا هر دو سکو از همین می‌خوانند، پس رسیدی که روی گوشی رول است روی
 * پی‌سی هم رول می‌ماند.
 */
object DocKinds {

    /** سندهایی که ردیف دارند و فاکتورِ ردیف‌دار می‌شوند. */
    val ITEMISED = setOf("SALE", "PURCHASE")

    /** نوع‌هایی که رسیدِ پول‌اند نه فاکتور. */
    val RECEIPTS = setOf(
        "PAYMENT", "RECEIPT", "CUSTOMER_RECEIPT", "SUPPLIER_PAYMENT",
        "WAGE_RECEIPT", "SALARY_RECEIPT"
    )

    /** رسیدِ پول است یا فاکتور؟ کاغذ و چیدمانِ این دو فرق دارد. */
    fun isReceipt(type: String): Boolean = type in RECEIPTS

    /** کاغذِ منطقی برای هر نوع سند، وقتی کاربر چیزی انتخاب نکرده. */
    fun defaultPaper(type: String): Paper =
        if (isReceipt(type)) Paper.ROLL80 else Paper.A5
}
