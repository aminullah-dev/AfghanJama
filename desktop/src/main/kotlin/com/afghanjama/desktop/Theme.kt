package com.afghanjama.desktop

import androidx.compose.material3.Typography
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font

// رنگ‌های خودِ اپ — همان‌هایی که در Theme.kt اندروید هستند.
// `internal` است نه `private`، چون حالا بیش از یک فایلِ ویندوز از آن
// استفاده می‌کند.
internal val Brand = Color(0xFF1F6E5C)
internal val BrandSoft = Color(0xFFD3EDE3)
internal val Ink = Color(0xFF1B1C1A)
internal val Muted = Color(0xFF5F5E58)
internal val Bg = Color(0xFFF7F6F3)
internal val CardBg = Color(0xFFFFFFFF)
internal val Bad = Color(0xFFB3261E)

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
 */
internal fun vazirTypography(): Typography {
    val d = Typography()
    fun f(s: androidx.compose.ui.text.TextStyle) = s.copy(fontFamily = Vazirmatn)
    return Typography(
        displayLarge = f(d.displayLarge),
        displayMedium = f(d.displayMedium),
        displaySmall = f(d.displaySmall),
        headlineLarge = f(d.headlineLarge),
        headlineMedium = f(d.headlineMedium),
        headlineSmall = f(d.headlineSmall),
        titleLarge = f(d.titleLarge),
        titleMedium = f(d.titleMedium),
        titleSmall = f(d.titleSmall),
        bodyLarge = f(d.bodyLarge),
        bodyMedium = f(d.bodyMedium),
        bodySmall = f(d.bodySmall),
        labelLarge = f(d.labelLarge),
        labelMedium = f(d.labelMedium),
        labelSmall = f(d.labelSmall)
    )
}
