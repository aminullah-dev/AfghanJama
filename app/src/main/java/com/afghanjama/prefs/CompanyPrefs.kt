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

    fun save(ctx: Context, name: String, phone: String, address: String) {
        p(ctx).edit()
            .putString("name", name)
            .putString("phone", phone)
            .putString("address", address)
            .apply()
    }
}
