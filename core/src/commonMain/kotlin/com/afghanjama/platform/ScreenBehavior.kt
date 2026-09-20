package com.afghanjama.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * رفتارهایی که **درونِ ترکیب** اتفاق می‌افتند و با یک تابعِ ساده بیان
 * نمی‌شوند.
 *
 * فرقشان با [SystemActions] این است: آن‌ها کاری‌اند که با زدنِ دکمه
 * انجام می‌شود، اینها به عمرِ خودِ صفحه گره خورده‌اند — تا وقتی صفحه
 * باز است برقرارند و با بسته‌شدنش برداشته می‌شوند. برای همین
 * `@Composable`اند و نه تابعِ معمولی.
 *
 * هر دو روی ویندوز **هیچ‌کاری نمی‌کنند و این درست است**، نه یک نقص:
 * پنجرهٔ پی‌سی خودش خاموش نمی‌شود و دکمهٔ برگشتِ سیستمی هم ندارد.
 */
interface ScreenBehavior {

    /**
     * تا وقتی این صفحه باز است، نمایشگر خاموش نشود.
     *
     * تابلوی کارگاه که هر ده ثانیه خاموش شود تابلو نیست — کسی که از آن
     * طرفِ سالن نگاه می‌کند نمی‌تواند مدام برود دکمه بزند.
     */
    @Composable
    fun KeepAwake()

    /**
     * دکمهٔ برگشتِ سیستم را بگیر.
     *
     * صفحه‌هایی که نوارِ بالا ندارند (مثلِ تابلو) تنها راهِ خروجشان همین
     * است؛ بدونش کاربر از صفحه بیرون نمی‌آید.
     */
    @Composable
    fun HandleBack(onBack: () -> Unit)
}

/**
 * پیاده‌سازیِ بی‌اثر — برای سکویی که نه نمایشگرِ خودخاموش‌شونده دارد نه
 * دکمهٔ برگشت.
 *
 * برخلافِ بقیهٔ مرزها اینجا پیش‌فرضِ بی‌اثر **درست** است و نباید خطا
 * بدهد: نبودنِ این دو روی پی‌سی نقص نیست، واقعیتِ آن سکوست.
 */
object NoScreenBehavior : ScreenBehavior {
    @Composable
    override fun KeepAwake() = Unit

    @Composable
    override fun HandleBack(onBack: () -> Unit) = Unit
}

val LocalScreenBehavior = staticCompositionLocalOf<ScreenBehavior> { NoScreenBehavior }
