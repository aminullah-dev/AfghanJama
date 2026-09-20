package com.afghanjama.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.ImageBitmap

/**
 * عکس‌های کارگاه — **مرزِ نهمِ سکو**.
 *
 * **چه چیزی را باز می‌کند.** سه صفحه فقط به‌خاطرِ عکس در `:app` مانده
 * بودند: انبارِ محصول، جزئیاتِ سفارش، و تنظیمات. هر سه در واقع فقط یک
 * چیز از عکس می‌خواهند (`delete`) به‌علاوهٔ یک ویجتِ انتخاب؛ سنگینی‌اش
 * — دوربین، کوچک‌کردن، ریزعکس — همه در `ui/components/OrderPhotos.kt`
 * جمع بود، نه در خودِ صفحه‌ها.
 *
 * **نام، نه مسیر.** هر عکس با نامِ فایلش شناخته می‌شود و جایِ فیزیکی‌اش
 * را سکو تعیین می‌کند. دیتابیس هم همین را ذخیره می‌کند
 * (`OrderPhoto.fileName`) پس دفتری که از گوشی به پی‌سی می‌رود نامِ
 * عکس‌هایش را نگه می‌دارد — گرچه خودِ فایل‌ها جدا منتقل می‌شوند.
 *
 * **دوربین عمداً می‌تواند نباشد.** [rememberCapture] روی پی‌سی `null`
 * برمی‌گرداند و دکمهٔ دوربین اصلاً نشان داده نمی‌شود. همان تصمیمی که
 * برای «تماس» گرفته شد، ولی برعکسش: آنجا دکمه ماند و کارِ دیگری کرد،
 * چون شماره‌ای بود که می‌شد کپی کرد. اینجا هیچ کارِ جایگزینی نیست —
 * پی‌سیِ کارگاه دوربین ندارد — و دکمه‌ای که هیچ نکند بدتر از نبودنش
 * است.
 *
 * انتخاب از فایل روی هر دو هست: روی گوشی گالری، روی ویندوز پنجرهٔ
 * انتخابِ فایل.
 */
interface Photos {

    /** پاک کردنِ فایلِ عکس. اگر نباشد، بی‌صدا رد می‌شود. */
    fun delete(name: String)

    fun exists(name: String): Boolean

    /**
     * عکس برای نمایش، کوچک‌شده تا [maxSide].
     *
     * `null` یعنی فایل نیست یا خوانده نشد — صفحه باید جای خالی نشان
     * دهد نه اینکه بشکند.
     */
    @Composable
    fun rememberThumb(name: String, maxSide: Int): ImageBitmap?

    /**
     * گرفتن با دوربین.
     *
     * `null` یعنی این سکو دوربین ندارد و دکمه‌اش نباید ساخته شود.
     */
    @Composable
    fun rememberCapture(onCaptured: (String) -> Unit): (() -> Unit)?

    /** انتخاب از گالری (گوشی) یا از فایل (ویندوز). */
    @Composable
    fun rememberPicker(onPicked: (String) -> Unit): () -> Unit

    /** اندازه‌ها یک‌جا، تا ریزعکسِ گوشی و پی‌سی یکی باشد. */
    companion object {
        const val MAX_SIDE = 1600
        const val THUMB_SIDE = 320
    }
}

val LocalPhotos = staticCompositionLocalOf<Photos> {
    error(
        "LocalPhotos داده نشده. ریشهٔ هر سکو باید با " +
            "CompositionLocalProvider(LocalPhotos provides …) پیچیده شود."
    )
}
