package com.afghanjama.ios

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.window.ComposeUIViewController
import com.afghanjama.prefs.IosSettings
import com.afghanjama.prefs.LocalSettings
import com.afghanjama.ui.platform.LocalWidgets
import com.afghanjama.ui.theme.LightColors
import com.afghanjama.ui.theme.appTypography
import com.afghanjama.ui.vm.AuthViewModel
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSBundle
import platform.Foundation.NSData
import platform.Foundation.dataWithContentsOfFile
import platform.UIKit.UIViewController
import platform.posix.memcpy

/*
 * درِ ورودیِ نسخهٔ آیفون.
 *
 * Xcode یک `UIViewController` می‌خواهد و همین را می‌گیرد؛ داخلش همان
 * صفحه‌های `:core` است، نه چیزی بازنویسی‌شده.
 */

/*
 * **`LocalViewModelStoreOwner` اینجا عمداً نیست.**
 *
 * صفحهٔ ورود ViewModel را به‌عنوان پارامتر می‌گیرد و خودش `viewModel { }`
 * صدا نمی‌زند، پس لازمش ندارد. و کتابخانه‌اش هم هنوز نمی‌آید:
 * `androidx.lifecycle:lifecycle-viewmodel-compose` برای iOS منتشر نشده
 * (`No matching variant`)؛ نسخهٔ چندسکویی‌اش زیرِ گروهِ
 * `org.jetbrains.androidx.lifecycle` است. وقتی صفحه‌ای آمد که واقعاً
 * لازمش داشت، همان باید اضافه شود.
 */

@OptIn(ExperimentalForeignApi::class)
private fun bundledFont(name: String): ByteArray? {
    val path = NSBundle.mainBundle.pathForResource(name, "ttf") ?: return null
    val data: NSData = NSData.dataWithContentsOfFile(path) ?: return null
    val length = data.length.toInt()
    if (length == 0) return null
    val out = ByteArray(length)
    out.usePinned { memcpy(it.addressOf(0), data.bytes, data.length) }
    return out
}

/**
 * قلمِ وزیر از داخلِ بستهٔ برنامه.
 *
 * روی iOS راهی مثلِ `res/font`ِ اندروید نیست؛ فایل در خودِ بسته می‌نشیند
 * و بایت‌هایش خوانده می‌شود.
 *
 * مثلِ نسخهٔ رومیزی، نبودنِ فونت برنامه را نمی‌اندازد: فارسیِ بدشکل بهتر
 * از پنجرهٔ بازنشده است — و آن‌وقت دستِ‌کم معلوم می‌شود مشکل از فونت
 * است نه از کدِ صفحه.
 */
private val Vazirmatn: FontFamily = run {
    val faces = listOfNotNull(
        bundledFont("vazirmatn_regular")?.let {
            Font("vazirmatn_regular", it, FontWeight.Normal)
        },
        bundledFont("vazirmatn_semibold")?.let {
            Font("vazirmatn_semibold", it, FontWeight.SemiBold)
        },
        bundledFont("vazirmatn_bold")?.let {
            Font("vazirmatn_bold", it, FontWeight.Bold)
        },
    )
    if (faces.isEmpty()) FontFamily.Default else FontFamily(faces)
}

/**
 * چیزی که سمتِ Swift صدایش می‌زند.
 *
 * **این نقطه عمداً کوچک است.** هر چه اینجا نوشته شود فقط روی iOS اجرا
 * می‌شود و هیچ آزمونی از سکوهای دیگر پوششش نمی‌دهد؛ پس همه‌چیز باید در
 * `:core` باشد و این فقط سیم‌کشی.
 */
fun MainViewController(): UIViewController = ComposeUIViewController {
    val settings = IosSettings()
    MaterialTheme(
        // همان پالت و همان مقیاسِ قلمِ گوشی و پی‌سی، از `:core`.
        colorScheme = LightColors,
        typography = appTypography(Vazirmatn),
    ) {
        CompositionLocalProvider(
            // کلِ برنامه راست‌به‌چپ، مستقلِ از زبانِ آیفون — همان کاری که
            // اندروید و ویندوز می‌کنند.
            LocalLayoutDirection provides LayoutDirection.Rtl,
            LocalSettings provides settings,
            LocalWidgets provides IosWidgets,
        ) {
            val auth = remember { AuthViewModel(settings) }
            IosShell(auth)
        }
    }
}
