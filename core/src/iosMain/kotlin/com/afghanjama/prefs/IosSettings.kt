package com.afghanjama.prefs

import platform.Foundation.NSUserDefaults

/**
 * تنظیماتِ ماندگار روی iOS — روی `NSUserDefaults`.
 *
 * **کلیدها `file:key` می‌شوند و این یک تصمیم است، نه میان‌بر.**
 * `Settings` از هر دو سکوی دیگر مفهومِ «پرونده» را با خود آورده
 * (اندروید `SharedPreferences` جدا دارد و ویندوز فایلِ جدا). روی iOS
 * چنین تفکیکی در `NSUserDefaults` نیست، پس نامِ پرونده به کلید چسبانده
 * می‌شود.
 *
 * دو کلیدِ متفاوت هرگز به یک رشته نمی‌رسند چون `:` در نامِ پرونده‌ها و
 * کلیدهای این پروژه به کار نرفته — همه‌شان حروف و زیرخط‌اند.
 */
class IosSettings(
    private val defaults: NSUserDefaults = NSUserDefaults.standardUserDefaults,
) : Settings {

    private fun k(file: String, key: String) = "$file:$key"

    override fun getString(file: String, key: String, def: String): String =
        defaults.stringForKey(k(file, key)) ?: def

    override fun putString(file: String, key: String, value: String) =
        defaults.setObject(value, k(file, key))

    /**
     * `objectForKey` پیش از `boolForKey` پرسیده می‌شود چون آن یکی برای
     * کلیدِ نبوده `false` می‌دهد و آن‌وقت مقدارِ پیش‌فرضِ `true` هرگز
     * دیده نمی‌شد — همان تفاوتِ «خاموش است» و «هنوز چیزی ذخیره نشده».
     */
    override fun getBoolean(file: String, key: String, def: Boolean): Boolean {
        val name = k(file, key)
        if (defaults.objectForKey(name) == null) return def
        return defaults.boolForKey(name)
    }

    override fun putBoolean(file: String, key: String, value: Boolean) =
        defaults.setBool(value, k(file, key))

    override fun getLong(file: String, key: String, def: Long): Long {
        val name = k(file, key)
        if (defaults.objectForKey(name) == null) return def
        // `integerForKey` روی ۶۴ بیت `NSInteger` می‌دهد که اینجا همان
        // `Long` است؛ زمانِ میلی‌ثانیه‌ای بی‌کم‌وکاست جا می‌شود.
        return defaults.integerForKey(name)
    }

    override fun putLong(file: String, key: String, value: Long) =
        defaults.setInteger(value, k(file, key))

    override fun remove(file: String, key: String) =
        defaults.removeObjectForKey(k(file, key))
}
