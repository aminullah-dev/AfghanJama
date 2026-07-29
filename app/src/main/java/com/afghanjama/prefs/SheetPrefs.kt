package com.afghanjama.prefs

import android.content.Context

/**
 * کاغذی که کارگاه آخرین بار برای چاپ انتخاب کرده.
 *
 * فاکتور و رسید جدا نگه داشته می‌شوند، چون در عمل دو کاغذِ متفاوت‌اند:
 * فاکتور روی A5 یا A4 چاپ می‌شود و رسید روی رولِ حرارتی. اگر یک انتخابِ
 * مشترک بود، کاربر هر بار باید عوضش می‌کرد.
 */
object SheetPrefs {
    private const val FILE = "sheet"
    private const val KEY_INVOICE = "invoice_paper"
    private const val KEY_RECEIPT = "receipt_paper"

    private fun p(ctx: Context) = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /** برچسبِ کاغذ، یا رشتهٔ خالی یعنی «هنوز چیزی انتخاب نشده». */
    fun paperLabel(ctx: Context, receipt: Boolean): String =
        p(ctx).getString(if (receipt) KEY_RECEIPT else KEY_INVOICE, "").orEmpty()

    fun savePaperLabel(ctx: Context, receipt: Boolean, label: String) {
        p(ctx).edit().putString(if (receipt) KEY_RECEIPT else KEY_INVOICE, label).apply()
    }
}
