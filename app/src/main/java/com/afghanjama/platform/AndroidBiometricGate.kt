package com.afghanjama.platform

import androidx.fragment.app.FragmentActivity
import com.afghanjama.util.BiometricAuth

/**
 * سنجشِ اثرِ انگشت روی اندروید.
 *
 * `BiometricAuth.confirm` خودش وقتی حسگر نباشد `onSuccess` را مستقیم
 * صدا می‌زند، پس رفتار روی گوشیِ بی‌حسگر همان چیزی است که بود.
 */
class AndroidBiometricGate(private val activity: FragmentActivity) : BiometricGate {
    override fun confirm(
        title: String,
        subtitle: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
    ) = BiometricAuth.confirm(activity, title, subtitle, onSuccess, onError)
}
