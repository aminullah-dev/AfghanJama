package com.afghanjama.util

import android.content.Context
import java.security.MessageDigest

/**
 * قفل اپ با رمز عددی. رمز به‌صورت هش SHA-256 در SharedPreferences ذخیره
 * می‌شود (رمز خام هرگز ذخیره نمی‌شود).
 */
object AppLock {

    private const val PREFS = "app_lock"
    private const val KEY_HASH = "pin_hash"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun hash(pin: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(pin.trim().toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun isPinSet(context: Context): Boolean =
        !prefs(context).getString(KEY_HASH, null).isNullOrBlank()

    fun setPin(context: Context, pin: String) {
        prefs(context).edit().putString(KEY_HASH, hash(pin)).apply()
    }

    fun clearPin(context: Context) {
        prefs(context).edit().remove(KEY_HASH).apply()
    }

    fun check(context: Context, pin: String): Boolean {
        val stored = prefs(context).getString(KEY_HASH, null) ?: return false
        return stored == hash(pin)
    }
}
