package com.afghanjama.ios

import com.afghanjama.ui.theme.AppTheme
import platform.UIKit.UIAccessibilityIsReduceMotionEnabled
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.runtime.remember
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font
import androidx.compose.ui.window.ComposeUIViewController
import com.afghanjama.platform.LocalDocs
import com.afghanjama.platform.LocalFileExport
import com.afghanjama.platform.LocalPhotos
import com.afghanjama.platform.LocalSystemActions
import com.afghanjama.prefs.IosSettings
import com.afghanjama.prefs.LocalSettings
import com.afghanjama.ui.platform.LocalWidgets
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
 * از پنجرهٔ بازنشده است — و آن‌وقت دست‌کم معلوم می‌شود مشکل از فونت
 * است نه از کدِ صفحه.
 */
private val Vazirmatn: FontFamily = run {
    val faces = listOfNotNull(
        bundledFont("vazirmatn_regular")?.let {
            Font("vazirmatn_regular", it, FontWeight.Normal)
        },
        // مقیاسِ مشترک برای عنوان‌ها و برچسب‌ها `Medium` می‌خواهد. تا ۱.۱۱
        // آیفون این وزن را نداشت و از Regular می‌ساختش — همان کمبودی که
        // کامپیوتر در ۱.۱۰ داشت.
        bundledFont("vazirmatn_medium")?.let {
            Font("vazirmatn_medium", it, FontWeight.Medium)
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
/**
 * QRِ «افزودنِ گوشی» که دوربینِ آیفون خواند (`kylink2:`). فقط نگه داشته می‌شود؛
 * پوسته به «کارگاهِ وصل» می‌رود و آنجا پیش از هر اتصالی از کاربر پرسیده می‌شود.
 */
fun openHubLink(raw: String) = com.afghanjama.ui.vm.HubDeepLink.offer(raw)

fun MainViewController(): UIViewController = ComposeUIViewController {
    val settings = IosSettings()
    // همان تمِ گوشی و کامپیوتر ([AppTheme]): پالت، قلم، گوشه‌ها و راست‌به‌چپ.
    // «کاهشِ حرکت»ِ آیفون هم خوانده می‌شود — تا ۱.۱۱ فقط اندروید می‌خواند.
    AppTheme(
        fontFamily = Vazirmatn,
        reducedMotion = UIAccessibilityIsReduceMotionEnabled(),
    ) {
        CompositionLocalProvider(
            LocalSettings provides settings,
            LocalWidgets provides IosWidgets,
            // بی این، هر صفحه‌ای که `LocalSystemActions` می‌خوانَد سرِ باز
            // شدن می‌ترکید — دلیلش در خودِ `IosSystemActions` نوشته شده.
            LocalSystemActions provides IosSystemActions,
            // صفحه‌های اسناد و گزارش باز می‌شوند؛ خودِ چاپ صریح می‌گوید
            // «هنوز نیست» — `IosDocs`.
            LocalDocs provides IosDocs,
            // انبارِ محصول (تبِ فروش)، جزئیاتِ سفارش و بُرش `LocalPhotos` را
            // سرِ باز شدن می‌خوانند و گزارش‌ها `LocalFileExport` را؛ بی این
            // دو، آیفون روی همان صفحه‌ها می‌ترکید.
            LocalPhotos provides IosPhotos,
            LocalFileExport provides IosFileExport,
        ) {
            val auth = remember { AuthViewModel(settings) }
            // کیبوردِ آیفون روی کادرها و دکمه‌های پایینِ صفحه می‌افتاد
            // (لایسنس، ورود، فرم‌ها). همان کاری که اندروید در
            // `Theme.kt` می‌کند: یک جا برای کل اپ، نه تک‌تک صفحه‌ها.
            // لمسِ بیرونِ کادر هم کیبورد را می‌بندد.
            val focus = LocalFocusManager.current
            Box(
                Modifier
                    .fillMaxSize()
                    .imePadding()
                    .pointerInput(Unit) { detectTapGestures { focus.clearFocus() } }
            ) {
                IosShell(auth)
            }
        }
    }
}
