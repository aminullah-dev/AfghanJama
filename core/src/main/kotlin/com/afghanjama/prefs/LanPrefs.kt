package com.afghanjama.prefs

/** این دستگاه چه نقشی در شبکهٔ کارگاه دارد. */
enum class DeviceMode { STANDALONE, MAIN, WORKER }

/**
 * تنظیماتِ اشتراکِ کارگاه.
 *
 * STANDALONE پیش‌فرض است: اپ دقیقاً مثلِ قبل، تنها روی همین دستگاه. تا
 * وقتی کارفرما خودش چیزی را روشن نکند، هیچ سوکتی باز نمی‌شود.
 */
object LanPrefs {
    private const val FILE = "lan_prefs"
    private const val KEY_MODE = "mode"
    private const val KEY_CODE = "code"
    private const val KEY_HOST = "host"
    private const val KEY_DEVICE = "device_name"

    fun mode(s: Settings): DeviceMode =
        runCatching { DeviceMode.valueOf(s.getString(FILE, KEY_MODE)) }
            .getOrDefault(DeviceMode.STANDALONE)

    fun setMode(s: Settings, m: DeviceMode) = s.putString(FILE, KEY_MODE, m.name)

    /** رمزِ اتصال — روی دستگاهِ اصلی ساخته و روی دستگاهِ کارگر وارد می‌شود. */
    fun code(s: Settings): String = s.getString(FILE, KEY_CODE)
    fun setCode(s: Settings, c: String) = s.putString(FILE, KEY_CODE, c.trim())

    /** نشانیِ دستگاهِ اصلی — فقط روی دستگاهِ کارگر معنی دارد. */
    fun host(s: Settings): String = s.getString(FILE, KEY_HOST)
    fun setHost(s: Settings, h: String) = s.putString(FILE, KEY_HOST, h.trim())

    /** نامی که این دستگاه خودش را با آن معرفی می‌کند. */
    fun deviceName(s: Settings): String = s.getString(FILE, KEY_DEVICE)
    fun setDeviceName(s: Settings, n: String) = s.putString(FILE, KEY_DEVICE, n.trim())

    /** رمزِ شش‌رقمیِ تازه. عمداً عددی است تا روی کیبوردِ گوشی سریع وارد شود. */
    fun newCode(): String = (100_000..999_999).random().toString()
}
