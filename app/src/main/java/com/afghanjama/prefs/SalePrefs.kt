package com.afghanjama.prefs

import android.content.Context

/**
 * سیاستِ فروش.
 *
 * «کسری» یعنی اجازهٔ فروشِ بیشتر از موجودیِ ثبت‌شده. در کارگاهِ واقعی جنس
 * گاهی قبل از آنکه کسی ورودش را ثبت کند فروخته می‌شود؛ اگر اپ جلویش را
 * بگیرد، فروشنده مجبور می‌شود یک ورودیِ الکی بزند و همان دروغ در حساب
 * می‌ماند. پس اجازه داده می‌شود، ولی با هشدارِ آشکار.
 *
 * مقدار در حافظه هم نگه داشته می‌شود چون `Repo` به Context دسترسی ندارد.
 * `App.onCreate` پیش از هر استفاده‌ای پُرش می‌کند.
 */
object SalePrefs {
    private const val FILE = "sale"
    private const val KEY_NEGATIVE = "allow_negative_stock"

    @Volatile
    private var cached: Boolean? = null

    private fun p(ctx: Context) = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun allowNegativeStock(ctx: Context): Boolean {
        val v = p(ctx).getBoolean(KEY_NEGATIVE, true)
        cached = v
        return v
    }

    fun setAllowNegativeStock(ctx: Context, value: Boolean) {
        p(ctx).edit().putBoolean(KEY_NEGATIVE, value).apply()
        cached = value
    }

    /**
     * برای جاهایی که Context ندارند. تا وقتی `App.onCreate` مقدار را
     * نخوانده، پیش‌فرضِ «اجازه هست» برمی‌گردد — همان چیزی که تنظیمِ
     * پیش‌فرض هم هست، پس اختلافی پیش نمی‌آید.
     */
    fun allowNegativeStockCached(): Boolean = cached ?: true
}
