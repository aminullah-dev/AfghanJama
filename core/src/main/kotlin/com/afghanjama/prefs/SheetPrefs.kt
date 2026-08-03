package com.afghanjama.prefs

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

    /** برچسبِ کاغذ، یا رشتهٔ خالی یعنی «هنوز چیزی انتخاب نشده». */
    fun paperLabel(s: Settings, receipt: Boolean): String =
        s.getString(FILE, if (receipt) KEY_RECEIPT else KEY_INVOICE)

    fun savePaperLabel(s: Settings, receipt: Boolean, label: String) {
        s.putString(FILE, if (receipt) KEY_RECEIPT else KEY_INVOICE, label)
    }
}
