package com.afghanjama.prefs

import android.content.Context

/**
 * اطلاعاتِ کارگاه/شرکت که روی رسیدها و فاکتورهای PDF چاپ می‌شود.
 * از SharedPreferences استفاده می‌کند تا خواندنِ همزمان (بدون coroutine)
 * در مسیرِ تولید PDF ممکن باشد.
 */
object CompanyPrefs {
    private const val FILE = "company"

    private fun p(ctx: Context) = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun name(ctx: Context): String = p(ctx).getString("name", "") ?: ""
    fun phone(ctx: Context): String = p(ctx).getString("phone", "") ?: ""
    fun address(ctx: Context): String = p(ctx).getString("address", "") ?: ""

    /**
     * نامی که روی کاغذ و رسید می‌نشیند.
     *
     * تا امروز هرجا نامِ کارگاه لازم بود، نامِ **خودِ اپ** نوشته می‌شد.
     * یعنی هر کارگاهی که این برنامه را می‌گرفت، فاکتور و رسیدش را به
     * نامِ کارگاهِ دیگری صادر می‌کرد. حالا نامِ خودش می‌آید.
     *
     * تا وقتی در تنظیمات چیزی ثبت نشده، یک عنوانِ خنثی برمی‌گردد نه
     * نامِ هیچ کسب‌وکارِ مشخصی.
     */
    fun shopName(ctx: Context): String = name(ctx).ifBlank { DEFAULT_SHOP }

    /** عنوانِ خنثی تا وقتی کارگاه نامش را ثبت نکرده. */
    const val DEFAULT_SHOP = "کارگاه خیاطی"

    /**
     * نامِ فایلِ لوگوی کارگاه در پوشهٔ خصوصیِ اپ — خالی یعنی لوگو ندارد.
     * روی سرصفحهٔ فاکتور و رسیدهای چاپی می‌نشیند.
     */
    fun logo(ctx: Context): String = p(ctx).getString("logo", "") ?: ""

    fun saveLogo(ctx: Context, fileName: String) {
        p(ctx).edit().putString("logo", fileName).apply()
    }

    fun save(ctx: Context, name: String, phone: String, address: String) {
        p(ctx).edit()
            .putString("name", name)
            .putString("phone", phone)
            .putString("address", address)
            .apply()
    }
}
