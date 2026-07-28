package com.afghanjama.prefs

import android.content.Context

/** این گوشی چه نقشی در شبکهٔ کارگاه دارد. */
enum class DeviceMode { STANDALONE, MAIN, WORKER }

/**
 * تنظیماتِ اشتراکِ کارگاه.
 *
 * STANDALONE پیش‌فرض است: اپ دقیقاً مثلِ قبل، تنها روی همین گوشی. تا
 * وقتی کارفرما خودش چیزی را روشن نکند، هیچ سوکتی باز نمی‌شود.
 */
object LanPrefs {
    private const val FILE = "lan_prefs"
    private const val KEY_MODE = "mode"
    private const val KEY_CODE = "code"
    private const val KEY_HOST = "host"
    private const val KEY_DEVICE = "device_name"

    private fun p(ctx: Context) = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun mode(ctx: Context): DeviceMode =
        runCatching { DeviceMode.valueOf(p(ctx).getString(KEY_MODE, "") ?: "") }
            .getOrDefault(DeviceMode.STANDALONE)

    fun setMode(ctx: Context, m: DeviceMode) =
        p(ctx).edit().putString(KEY_MODE, m.name).apply()

    /** رمزِ اتصال — روی گوشیِ اصلی ساخته و روی گوشیِ کارگر وارد می‌شود. */
    fun code(ctx: Context): String = p(ctx).getString(KEY_CODE, "") ?: ""
    fun setCode(ctx: Context, c: String) = p(ctx).edit().putString(KEY_CODE, c.trim()).apply()

    /** نشانیِ گوشیِ اصلی — فقط روی گوشیِ کارگر معنی دارد. */
    fun host(ctx: Context): String = p(ctx).getString(KEY_HOST, "") ?: ""
    fun setHost(ctx: Context, h: String) = p(ctx).edit().putString(KEY_HOST, h.trim()).apply()

    /** نامی که این گوشی خودش را با آن معرفی می‌کند. */
    fun deviceName(ctx: Context): String =
        p(ctx).getString(KEY_DEVICE, "") ?: ""
    fun setDeviceName(ctx: Context, n: String) =
        p(ctx).edit().putString(KEY_DEVICE, n.trim()).apply()

    /** رمزِ شش‌رقمیِ تازه. عمداً عددی است تا روی کیبوردِ گوشی سریع وارد شود. */
    fun newCode(): String = (100_000..999_999).random().toString()
}
