package com.afghanjama.desktop

import androidx.compose.material3.Typography
import com.afghanjama.ui.theme.appTypography
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

/**
 * فونتِ اپ، از همان فایلی که اندروید برمی‌دارد.
 *
 * بدونِ این، فارسی با فونتِ پیش‌فرضِ سیستم نوشته می‌شود که روی ویندوز
 * اغلب حروف را نمی‌چسباند و اعدادِ فارسی را بد می‌کشد.
 */
internal val Vazirmatn: FontFamily = runCatching {
    FontFamily(
        Font("vazirmatn_regular.ttf", FontWeight.Normal),
        Font("vazirmatn_semibold.ttf", FontWeight.SemiBold),
        Font("vazirmatn_bold.ttf", FontWeight.Bold)
    )
}.getOrElse {
    // نبودنِ فونت نباید برنامه را بیندازد؛ فارسی بدشکل بهتر از پنجرهٔ
    // بازنشده است.
    FontFamily.Default
}

/**
 * همان سبک‌های Material، فقط با قلمِ فارسی.
 *
 * **چرا لازم شد:** صفحه‌های مشترک قلم را صریح نمی‌دهند و از تم
 * می‌گیرند — روی اندروید `KhayatYarTheme` آن را می‌دهد. تا دیروز پنجرهٔ
 * ویندوز فقط متن‌های خودش را داشت و هر کدام دستی `fontFamily` می‌گرفت،
 * پس این کمبود دیده نمی‌شد. با آمدنِ صفحه‌های واقعی، بدونِ این خط کلِ
 * فاکتور و انبار با قلمِ پیش‌فرضِ ویندوز و حروفِ نچسبیده نوشته می‌شد.
 *
 * **و نیمهٔ دومِ همان اشکال، که تا امروز نمانده بود.** آن اصلاح قلم را
 * درست کرد ولی **اندازه** را نه: اینجا `Typography()`ِ پیش‌فرضِ متریال
 * گرفته می‌شد و فقط `fontFamily`اش عوض. یعنی همان صفحه با همان کد دو
 * مقیاس داشت — `titleLarge` روی گوشی ۲۰ و روی پی‌سی ۲۲، `bodyLarge` با
 * ارتفاعِ خطِ ۲۶ و ۲۴. برای خطِ فارسی که زیر-خط و اعراب دارد، همان دو
 * واحد یعنی سطرهایی که به هم می‌چسبند.
 *
 * حالا هر دو از `appTypography` در `:core` می‌خوانند.
 */
internal fun vazirTypography(): Typography = appTypography(Vazirmatn)
