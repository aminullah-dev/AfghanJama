package com.afghanjama.util

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/**
 * تأیید با اثر انگشت (بایومتریک). نکتهٔ مهم: اندروید فقط اثر انگشتِ صاحبِ
 * گوشی را «تأیید/رد» می‌کند و هویتِ کارمند را نمی‌شناسد؛ پس اینجا اثر
 * انگشت به‌عنوان «قفلِ تأیید» برای ثبت ورود/خروج استفاده می‌شود.
 */
object BiometricAuth {

    private val AUTHENTICATORS = BiometricManager.Authenticators.BIOMETRIC_WEAK

    fun isAvailable(activity: FragmentActivity): Boolean =
        BiometricManager.from(activity).canAuthenticate(AUTHENTICATORS) ==
            BiometricManager.BIOMETRIC_SUCCESS

    /**
     * اگر اثر انگشت روی دستگاه فعال باشد، اول تأیید می‌گیرد و بعد [onSuccess]
     * را صدا می‌زند؛ اگر فعال نباشد، مستقیم [onSuccess] اجرا می‌شود تا روی
     * گوشی‌های بدون اثر انگشت هم قابل‌استفاده بماند.
     */
    fun confirm(
        activity: FragmentActivity,
        title: String,
        subtitle: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit = {}
    ) {
        if (!isAvailable(activity)) {
            onSuccess()
            return
        }
        val executor = ContextCompat.getMainExecutor(activity)
        val prompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    // لغو توسط کاربر خطا نیست
                    if (errorCode != BiometricPrompt.ERROR_USER_CANCELED &&
                        errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON
                    ) {
                        onError(errString.toString())
                    }
                }
            }
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setNegativeButtonText("لغو")
            .setAllowedAuthenticators(AUTHENTICATORS)
            .build()
        prompt.authenticate(info)
    }
}
