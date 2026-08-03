package com.afghanjama.prefs

import android.content.Context
import com.afghanjama.data.SalePolicy

/**
 * ذخیره‌سازیِ سیاستِ فروش روی اندروید.
 *
 * خودِ **تصمیم** در [SalePolicy] است — یک بولینِ ساده که هیچ ربطی به
 * اندروید ندارد و منطقِ کارگاه از همان‌جا می‌خواندش. اینجا فقط
 * SharedPreferences است؛ روی ویندوز جای همین یک فایلِ ساده می‌نشیند و
 * منطق دست نمی‌خورد.
 */
object SalePrefs {
    private const val FILE = "sale"
    private const val KEY_NEGATIVE = "allow_negative_stock"

    private fun p(ctx: Context) = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun allowNegativeStock(ctx: Context): Boolean {
        val v = p(ctx).getBoolean(KEY_NEGATIVE, SalePolicy.DEFAULT_ALLOW)
        SalePolicy.set(v)
        return v
    }

    fun setAllowNegativeStock(ctx: Context, value: Boolean) {
        p(ctx).edit().putBoolean(KEY_NEGATIVE, value).apply()
        SalePolicy.set(value)
    }
}
