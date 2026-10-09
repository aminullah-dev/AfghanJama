package com.afghanjama.licence

import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings
import kotlin.concurrent.Volatile

/**
 * سهمِ اندروید — `ANDROID_ID`.
 *
 * از اندروید ۸ به بعد این عدد برای هر «کلیدِ امضای اپ + دستگاه + کاربر»
 * ثابت است و با به‌روزرسانیِ اپ عوض نمی‌شود؛ فقط با ریستِ کارخانه یا
 * امضای دیگر. همان چیزی که برای «یک لایسنس، یک گوشی» لازم است.
 *
 * `Context` را `MainActivity` یک بار با [AndroidMachineId.init] می‌دهد،
 * چون `expect fun` جای پارامترِ مخصوصِ یک سکو ندارد.
 */
object AndroidMachineId {
    @Volatile
    internal var context: Context? = null

    fun init(context: Context) {
        this.context = context.applicationContext
    }
}

@SuppressLint("HardwareIds")
actual fun platformMachineId(): String? {
    val ctx = AndroidMachineId.context ?: return null
    return runCatching {
        Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ANDROID_ID)
    }.getOrNull()?.trim()?.takeIf { it.isNotEmpty() }
}
