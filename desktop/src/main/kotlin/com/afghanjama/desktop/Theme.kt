package com.afghanjama.desktop

import com.afghanjama.ui.theme.LightColors
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font

// نام‌های کوتاه برای متن‌های خودِ پنجرهٔ ویندوز (سربرگ، نوارِ کناری).
//
// **مقدارشان از پالتِ مشترک می‌آید، نه از کپی.** تا دیروز همین هفت رنگ
// اینجا دوباره نوشته شده بودند و اتفاقاً درست هم بودند — ولی «اتفاقاً
// درست» تا اولین باری دوام دارد که کسی پالت را عوض کند و این‌جا را
// نبیند.
internal val Brand = LightColors.primary
internal val BrandSoft = LightColors.primaryContainer
internal val Ink = LightColors.onSurface
internal val Muted = LightColors.onSurfaceVariant
internal val Bg = LightColors.background
internal val CardBg = LightColors.surface
internal val Bad = LightColors.error
internal val BadSoft = LightColors.errorContainer
internal val Coral = LightColors.secondary
internal val CoralSoft = LightColors.secondaryContainer
internal val Hairline = LightColors.outlineVariant

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
