package com.afghanjama.licence

import com.afghanjama.prefs.LicencePrefs
import com.afghanjama.prefs.Settings
import com.afghanjama.util.secureRandomBytes

/**
 * شناسهٔ سکو — خامِ چیزی که کدِ دستگاه از آن ساخته می‌شود.
 *
 * | سکو | از کجا |
 * |---|---|
 * | اندروید | `Settings.Secure.ANDROID_ID` (برای هر کلیدِ امضا + دستگاه + کاربر ثابت) |
 * | ویندوز | رجیستری `HKLM\SOFTWARE\Microsoft\Cryptography\MachineGuid` |
 * | مک | `IOPlatformUUID` از `ioreg -rd1 -c IOPlatformExpertDevice` |
 * | آیفون | `identifierForVendor` |
 *
 * `null` یعنی خوانده نشد. **هرگز کش نمی‌شود:** اگر شناسهٔ خوانده‌شده
 * جایی نوشته می‌شد، کپی کردنِ پوشهٔ دفتر به کمپیوترِ دیگر لایسنس را هم
 * با خودش می‌برد.
 */
expect fun platformMachineId(): String?

/** کدِ این دستگاه و اینکه از شناسهٔ واقعیِ سکو آمده یا از جایگزین. */
data class MachineIdentity(val code: String, val fallback: Boolean)

object MachineIds {

    /**
     * کدِ این دستگاه.
     *
     * اگر شناسهٔ سکو خوانده نشد، یک شناسهٔ تصادفی **یک بار** ساخته و در
     * تنظیماتِ همین نصب نگه داشته می‌شود — تا کد روی همین نصب ثابت بماند
     * — و صفحهٔ لایسنس می‌گوید که این کد از جایگزین آمده.
     */
    fun resolve(settings: Settings, platformId: String? = platformMachineId()): MachineIdentity {
        val id = platformId?.trim()?.takeIf { it.isNotEmpty() }
        if (id != null) return MachineIdentity(Lnm1.machineCode(id), fallback = false)
        var fb = LicencePrefs.fallbackId(settings)
        if (fb.isBlank()) {
            fb = "fallback-" + secureRandomBytes(16).joinToString("") {
                (it.toInt() and 0xFF).toString(16).padStart(2, '0')
            }
            LicencePrefs.setFallbackId(settings, fb)
        }
        return MachineIdentity(Lnm1.machineCode(fb), fallback = true)
    }
}
