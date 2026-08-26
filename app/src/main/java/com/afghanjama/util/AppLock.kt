package com.afghanjama.util

import android.content.Context

/**
 * قفلِ اپ با رمزِ عددی.
 *
 * **هش از `PinHash` می‌آید، نه از اینجا.** تا دیروز `SHA-256`ِ بی‌نمک
 * بود؛ رمزِ ۴ رقمی فقط ۱۰٬۰۰۰ حالت دارد و جدولِ همه‌شان روی هر لپ‌تاپی
 * کسری از ثانیه ساخته می‌شود، پس عملاً با متنِ خام فرقی نداشت.
 *
 * رمزهای قدیمی همچنان باز می‌کنند و **سرِ اولین ورودِ درست بی‌صدا ارتقا
 * می‌گیرند** — کارفرما نباید پشتِ درِ اپِ خودش بماند.
 */
object AppLock {

    private const val PREFS = "app_lock"
    private const val KEY_HASH = "pin_hash"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isPinSet(context: Context): Boolean =
        !prefs(context).getString(KEY_HASH, null).isNullOrBlank()

    fun setPin(context: Context, pin: String) {
        prefs(context).edit().putString(KEY_HASH, PinHash.hash(pin)).apply()
    }

    fun clearPin(context: Context) {
        prefs(context).edit().remove(KEY_HASH).apply()
    }

    fun check(context: Context, pin: String): Boolean {
        val stored = prefs(context).getString(KEY_HASH, null) ?: return false
        if (!PinHash.verify(pin, stored)) return false
        // تنها لحظه‌ای که ارتقا ممکن است: رمزِ درست دمِ دست است و نمکِ
        // تازه به آن نیاز دارد.
        if (PinHash.needsUpgrade(stored)) setPin(context, pin)
        return true
    }
}
