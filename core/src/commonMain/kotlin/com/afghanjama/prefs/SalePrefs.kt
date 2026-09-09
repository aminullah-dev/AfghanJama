package com.afghanjama.prefs

import com.afghanjama.data.SalePolicy

/**
 * ذخیره‌سازیِ سیاستِ فروش.
 *
 * خودِ **تصمیم** در [SalePolicy] است — یک بولینِ ساده که هیچ ربطی به
 * سکو ندارد و منطقِ کارگاه از همان‌جا می‌خواندش. اینجا فقط جای
 * نگهداری‌اش است.
 */
object SalePrefs {
    private const val FILE = "sale"
    private const val KEY_NEGATIVE = "allow_negative_stock"

    fun allowNegativeStock(s: Settings): Boolean {
        val v = s.getBoolean(FILE, KEY_NEGATIVE, SalePolicy.DEFAULT_ALLOW)
        SalePolicy.set(v)
        return v
    }

    fun setAllowNegativeStock(s: Settings, value: Boolean) {
        s.putBoolean(FILE, KEY_NEGATIVE, value)
        SalePolicy.set(value)
    }
}
