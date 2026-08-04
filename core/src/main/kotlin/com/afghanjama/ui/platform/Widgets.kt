package com.afghanjama.ui.platform

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * سه ابزارِ Compose که **نامشان یکی است ولی کلاسِ کامپایل‌شده‌شان نیست**.
 *
 * **چرا این مرز لازم شد — و چرا هیچ بررسی‌ای نگرفتش:**
 *
 * فرضِ کلِ فازِ ۴.۵ این بود که `androidx.compose.*` روی گوشی و پی‌سی نامِ
 * یکسان دارد، پس بایت‌کدی که در `:core` ساخته می‌شود هر دو جا می‌نشیند.
 * این فرض برای صدها تابع درست بود — و برای سه‌تا **غلط**:
 *
 *     java.lang.NoClassDefFoundError: Failed resolution of:
 *     Landroidx/compose/material3/SkikoMenu_skikoKt;
 *         at ProductionOrderScreen.kt:161
 *
 * علتش این است که تابعِ سطحِ فایل در جاوا به کلاسی به نامِ همان **فایل**
 * تبدیل می‌شود. `DropdownMenu` روی دسکتاپ در `SkikoMenu.skiko.kt` است و
 * روی اندروید در `AndroidMenu.android.kt`. نامِ کاتلینی یکی است
 * (`androidx.compose.material3.DropdownMenu`) ولی فراخوانی در بایت‌کد به
 * `SkikoMenu_skikoKt.DropdownMenu` بسته می‌شود — کلاسی که در APK وجود
 * ندارد.
 *
 * پس کامپایل سبز بود، APK ساخته شد، و اپ فقط **وقتی آن صفحه باز می‌شد**
 * می‌ترکید. صفحهٔ ورود و داشبورد سالم بودند، برای همین آزمونِ شبیه‌ساز
 * هم چیزی نگرفت.
 *
 * سه نمادِ گرفتار — با شمردنِ مکانیکیِ کلاس‌های `*_skikoKt` در jarهای
 * دسکتاپ، نه با حدس:
 *   • `AlertDialog`      (`AlertDialog_skikoKt`)   ۲۷ جا
 *   • `DropdownMenuItem` (`SkikoMenu_skikoKt`)     ۱۸ جا
 *   • `DropdownMenu`     (`SkikoMenu_skikoKt`)     ۱۵ جا
 *
 * هیچ نمادِ دیگری — نه تابع نه ویژگی — در این دام نبود.
 *
 * **راهِ حل:** همان الگوی شش مرزِ قبلی. `:core` فقط این واسط را می‌بیند؛
 * هر سکو پیاده‌سازیِ خودش را می‌دهد و آنجا فراخوانی به کلاسِ درستِ همان
 * سکو بسته می‌شود.
 *
 * **چرا پوششِ سادهٔ داخلِ `:core` جواب نمی‌داد:** آن پوشش هم در `:core`
 * کامپایل می‌شد و به همان کلاسِ دسکتاپ می‌چسبید. مرز باید جایی باشد که
 * کد **دو بار** کامپایل شود.
 */
interface Widgets {

    /** پنجرهٔ تأیید — روی اندروید Material و روی ویندوز پنجرهٔ Skiko. */
    @Composable
    fun Dialog(
        onDismissRequest: () -> Unit,
        title: (@Composable () -> Unit)?,
        text: @Composable () -> Unit,
        confirmButton: @Composable () -> Unit,
        dismissButton: (@Composable () -> Unit)?
    )

    /** منوی بازشو. */
    @Composable
    fun Menu(
        expanded: Boolean,
        onDismissRequest: () -> Unit,
        content: @Composable ColumnScope.() -> Unit
    )

    /** یک ردیف در منوی بازشو. */
    @Composable
    fun MenuItem(
        text: @Composable () -> Unit,
        onClick: () -> Unit
    )
}

/**
 * مثلِ `LocalSettings` پیش‌فرض ندارد و بلند می‌شکند.
 *
 * پیاده‌سازیِ ساختگی یعنی دکمهٔ «حذف» را بزنی و هیچ پنجرهٔ تأییدی نیاید —
 * و در اپی که کارش پول است، عملی که تأییدش دیده نشود بدترین حالت است.
 */
val LocalWidgets = staticCompositionLocalOf<Widgets> {
    error(
        "LocalWidgets داده نشده. ریشهٔ هر سکو باید با " +
            "CompositionLocalProvider(LocalWidgets provides …) پیچیده شود."
    )
}

/*
 * توابعِ راحت، تا صفحه‌ها `LocalWidgets.current.…` ننویسند.
 *
 * نام‌ها عمداً پیشوندِ `App` دارند: هم‌نام بودن با نسخهٔ اصلیِ متریال
 * باعث می‌شد یک ایمپورتِ جامانده بی‌سروصدا به همان تابعِ خطرناک برگردد و
 * ما دوباره همان‌جا باشیم.
 */

@Composable
fun AppAlertDialog(
    onDismissRequest: () -> Unit,
    text: @Composable () -> Unit,
    confirmButton: @Composable () -> Unit,
    title: (@Composable () -> Unit)? = null,
    dismissButton: (@Composable () -> Unit)? = null
) = LocalWidgets.current.Dialog(
    onDismissRequest = onDismissRequest,
    title = title,
    text = text,
    confirmButton = confirmButton,
    dismissButton = dismissButton
)

@Composable
fun AppDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) = LocalWidgets.current.Menu(expanded, onDismissRequest, content)

@Composable
fun AppDropdownMenuItem(
    text: @Composable () -> Unit,
    onClick: () -> Unit
) = LocalWidgets.current.MenuItem(text, onClick)
