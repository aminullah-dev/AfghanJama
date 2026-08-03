package com.afghanjama.prefs

import android.content.Context

/**
 * [Settings] روی اندروید — همان `SharedPreferences`ِ همیشگی.
 *
 * **مهم‌ترین نکتهٔ این فایل:** `file` مستقیم به نامِ همان
 * `SharedPreferences`ی می‌رود که تا دیروز استفاده می‌شد، و کلیدها هم
 * دست نخورده‌اند. یعنی کارگاهی که اپ را از قبل دارد، نامِ کارگاه و رمزِ
 * اتصال و برچسبِ کاغذش سرِ جایش می‌مانَد.
 *
 * اگر این نگاشت عوض می‌شد، هیچ خطایی نمی‌داد — فقط یک روز صبح فاکتورها
 * بی‌نامِ کارگاه چاپ می‌شدند و گوشی‌های کارگر از دفتر جدا می‌افتادند.
 * برای همین اینجا هیچ «تمیزکاریِ» نام‌ها انجام نشد.
 */
class AndroidSettings(private val ctx: Context) : Settings {

    private fun p(file: String) = ctx.getSharedPreferences(file, Context.MODE_PRIVATE)

    override fun getString(file: String, key: String, def: String): String =
        p(file).getString(key, def) ?: def

    override fun putString(file: String, key: String, value: String) {
        p(file).edit().putString(key, value).apply()
    }

    override fun getBoolean(file: String, key: String, def: Boolean): Boolean =
        p(file).getBoolean(key, def)

    override fun putBoolean(file: String, key: String, value: Boolean) {
        p(file).edit().putBoolean(key, value).apply()
    }

    override fun remove(file: String, key: String) {
        p(file).edit().remove(key).apply()
    }
}

/**
 * میان‌بر برای جاهایی که Context دمِ دست است ولی Compose نیست — مثلِ
 * `PdfKit` و `App`.
 *
 * ساختنِ شیء در هر فراخوانی به‌نظر ولخرجی می‌آید ولی نیست: این فقط یک
 * پوششِ نازک است و خودِ `getSharedPreferences` را اندروید کش می‌کند.
 */
val Context.settings: Settings get() = AndroidSettings(this)
