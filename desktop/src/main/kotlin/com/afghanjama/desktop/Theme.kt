package com.afghanjama.desktop

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font

// نام‌های کوتاه برای رنگ‌های خودِ پنجرهٔ کامپیوتر (سربرگ، نوارِ کناری).
//
// **مقدارشان از تمِ جاری می‌آید، نه از یک پالتِ ثابت.** تا ۱.۱۳ این‌ها
// `val`ِ ساده بودند و از `LightColors` می‌خواندند. تا وقتی کامپیوتر فقط
// روشن بود درست بود؛ ولی از ۱.۱۳ «ظاهر» در تنظیمات تاریک هم دارد، و این
// یازده نام همان رنگ‌های روشن را برمی‌گرداندند. نتیجه در حالتِ تاریک:
// نوارِ کنار و زمینهٔ پنجره روشن می‌ماند و محتوا تاریک می‌شد، و متنِ
// کاشیِ «کارگاهِ وصل» (جوهرِ تیره روی کارتِ تیره) خوانده نمی‌شد.
//
// حالا هر نام همان نقش را از `MaterialTheme.colorScheme` می‌خواند، پس با
// عوض شدنِ تم همه با هم عوض می‌شوند. جاهای مصرف دست نخورده‌اند.
internal val Brand: Color
    @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.primary
internal val BrandSoft: Color
    @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.primaryContainer
internal val Ink: Color
    @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.onSurface
internal val Muted: Color
    @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.onSurfaceVariant
internal val Bg: Color
    @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.background
internal val CardBg: Color
    @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.surface
internal val Bad: Color
    @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.error
internal val BadSoft: Color
    @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.errorContainer
internal val Coral: Color
    @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.secondary
internal val CoralSoft: Color
    @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.secondaryContainer
internal val Hairline: Color
    @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.outlineVariant

/**
 * فونتِ اپ، از همان فایلی که اندروید برمی‌دارد.
 *
 * بدونِ این، فارسی با فونتِ پیش‌فرضِ سیستم نوشته می‌شود که روی ویندوز
 * اغلب حروف را نمی‌چسباند و اعدادِ فارسی را بد می‌کشد.
 */
internal val Vazirmatn: FontFamily = runCatching {
    FontFamily(
        Font("vazirmatn_regular.ttf", FontWeight.Normal),
        // مقیاسِ مشترک برای عنوان‌ها و برچسب‌ها `Medium` می‌خواهد. تا ۱.۱۰ این
        // وزن اینجا ثبت نبود و Skia نزدیک‌ترین را خودش می‌ساخت — همان
        // Regular، کمی پررنگ‌تر کشیده.
        Font("vazirmatn_medium.ttf", FontWeight.Medium),
        Font("vazirmatn_semibold.ttf", FontWeight.SemiBold),
        Font("vazirmatn_bold.ttf", FontWeight.Bold)
    )
}.getOrElse {
    // نبودنِ فونت نباید برنامه را بیندازد؛ فارسی بدشکل بهتر از پنجرهٔ
    // بازنشده است.
    FontFamily.Default
}
