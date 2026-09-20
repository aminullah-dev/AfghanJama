package com.afghanjama.prefs

/**
 * جایی که تنظیماتِ کوچک می‌نشیند — بی اینکه بداند روی چه سکویی است.
 *
 * این مرزِ فازِ ۴.۵ است. شمارشِ واقعی نشان داد چیزی که ۲۲ صفحه را در
 * `:app` نگه داشته سخت‌افزار نیست، **تنظیمات** است: ۱۷ استفاده از
 * `SharedPreferences`. با همین یک واسط، آن‌ها و سه ViewModel آزاد
 * می‌شوند.
 *
 * چرا کلید-مقدارِ ساده و نه چیزی هوشمندتر: چون همین است که هست. نامِ
 * کارگاه، رمزِ اتصال، برچسبِ کاغذ. هر چیزی که ساختار داشته باشد جایش
 * در دیتابیس است نه اینجا.
 *
 * **همزمان (synchronous) بودنش عمدی است.** مسیرِ ساختِ PDF این‌ها را
 * وسطِ چیدنِ کاغذ می‌خواند و آنجا coroutine نیست. اگر این واسط
 * `suspend` می‌شد، کلِ لایهٔ چاپ باید عوض می‌شد.
 *
 * [file] فضای‌نام است، نه مسیرِ فایل. روی اندروید دقیقاً به همان
 * `SharedPreferences`ِ امروز نگاشت می‌شود — با همان نام‌ها — تا
 * **نصب‌های موجود داده‌شان را از دست ندهند**. روی ویندوز یک فایلِ
 * properties کنارِ دیتابیس است.
 */
interface Settings {
    fun getString(file: String, key: String, def: String = ""): String
    fun putString(file: String, key: String, value: String)

    fun getBoolean(file: String, key: String, def: Boolean): Boolean
    fun putBoolean(file: String, key: String, value: Boolean)

    // زمان‌ها (میلی‌ثانیه از مبدأ) — مثلِ «آخرین پشتیبان کِی گرفته شد».
    // با رشته هم می‌شد ولی آن‌وقت هر خواننده باید خودش تبدیل می‌کرد و
    // یکی‌شان روزی فراموش می‌کرد.
    fun getLong(file: String, key: String, def: Long): Long
    fun putLong(file: String, key: String, value: Long)

    fun remove(file: String, key: String)
}
