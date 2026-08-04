package com.afghanjama.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/*
 * پالتِ رنگِ خیاط‌یار — **یک تعریف برای هر دو سکو**.
 *
 * تا امروز این‌ها `private` در تمِ اندروید بودند و پنجرهٔ ویندوز چهار
 * رنگ را دستی کپی کرده بود. ولی صفحه‌های مشترک **۱۸ نقشِ رنگ** را صدا
 * می‌زنند (`onSurfaceVariant` ۱۵۸ بار، `outlineVariant` ۷۷، `error`
 * ۵۸ …). آن چهارده نقشِ دیگر روی ویندوز از پالتِ **پیش‌فرضِ بنفشِ
 * متریال** می‌آمدند.
 *
 * یعنی همان صفحه، با همان کد، روی گوشی سبز-برنزی بود و روی پی‌سی
 * بنفش-خاکستری. کسی هم تا دیدنِ پنجره نمی‌فهمید، چون کامپایل درست است.
 *
 * حالا پالت اینجاست و هر دو سکو از همین می‌خوانند — همان کاری که سرِ
 * دیتابیس و چیدمانِ کاغذ هم شد.
 */

// هر نقشی که در اپ استفاده می‌شود اینجا **صریح** تعریف شده. نقشِ
// تعریف‌نشده به پالتِ پیش‌فرضِ متریال برمی‌گردد که رنگ‌های خودش را
// دارد و به این پالت ربطی ندارد — همان اتفاقی که برای
// `tertiaryContainer` افتاده بود: در اپی با پالتِ سبز و برنزی، بنرِ
// «مرکز هشدار» صورتیِ پیش‌فرضِ متریال (#FFD8E4) درمی‌آمد.
//
// طبقه‌های `surfaceContainer*` هم تعریف شده‌اند تا عمقِ صفحه با
// اختلافِ روشناییِ ملایم ساخته شود، نه با سایه و خط.
// ======================================================
val LightColors = lightColorScheme(
    primary = Color(0xFF1F6E5C),           // سبز عمیق (رنگ اصلی)
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD3EDE3),
    onPrimaryContainer = Color(0xFF07352A),

    secondary = Color(0xFF8A6B41),         // برنزی گرم (حس پارچه و کارگاه)
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFF3E6D1),
    onSecondaryContainer = Color(0xFF3C2C13),

    tertiary = Color(0xFF41616D),          // آبیِ خاکستری — برای خبر، نه هشدار
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFDCE8EE),
    onTertiaryContainer = Color(0xFF16323D),

    background = Color(0xFFF7F6F3),        // زمینه گرم و آرام
    onBackground = Color(0xFF1B1C1A),

    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1B1C1A),
    surfaceVariant = Color(0xFFEEECE6),
    onSurfaceVariant = Color(0xFF5F5E58),

    // طبقه‌های سطح: از روشن‌ترین (کارتِ روی زمینه) تا تیره‌ترین
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFBFAF7),
    surfaceContainer = Color(0xFFF2F0EB),
    surfaceContainerHigh = Color(0xFFECEAE4),
    surfaceContainerHighest = Color(0xFFE6E4DD),
    surfaceBright = Color(0xFFFFFFFF),
    surfaceDim = Color(0xFFE0DED7),

    inverseSurface = Color(0xFF30322E),
    inverseOnSurface = Color(0xFFF2F1EC),
    inversePrimary = Color(0xFF87D6BE),

    outline = Color(0xFF87867F),
    outlineVariant = Color(0xFFE2E0D9),
    scrim = Color(0xFF000000),

    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B)
)

val DarkColors = darkColorScheme(
    primary = Color(0xFF87D6BE),
    onPrimary = Color(0xFF00382B),
    primaryContainer = Color(0xFF0F5142),
    onPrimaryContainer = Color(0xFFA5F2DA),

    secondary = Color(0xFFDDC3A0),
    onSecondary = Color(0xFF3E2E14),
    secondaryContainer = Color(0xFF574429),
    onSecondaryContainer = Color(0xFFFBDFBB),

    tertiary = Color(0xFFA9CBD8),
    onTertiary = Color(0xFF11333E),
    tertiaryContainer = Color(0xFF2C4A56),
    onTertiaryContainer = Color(0xFFC6E4F0),

    background = Color(0xFF121412),
    onBackground = Color(0xFFE3E3DE),

    surface = Color(0xFF1A1C1A),
    onSurface = Color(0xFFE3E3DE),
    surfaceVariant = Color(0xFF2A2C29),
    onSurfaceVariant = Color(0xFFC5C4BC),

    surfaceContainerLowest = Color(0xFF0D0F0D),
    surfaceContainerLow = Color(0xFF1A1C1A),
    surfaceContainer = Color(0xFF1E201E),
    surfaceContainerHigh = Color(0xFF282A27),
    surfaceContainerHighest = Color(0xFF333531),
    surfaceBright = Color(0xFF383A37),
    surfaceDim = Color(0xFF121412),

    inverseSurface = Color(0xFFE3E3DE),
    inverseOnSurface = Color(0xFF2F312E),
    inversePrimary = Color(0xFF1F6E5C),

    outline = Color(0xFF8F8E86),
    outlineVariant = Color(0xFF43453F),
    scrim = Color(0xFF000000),

    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFF9DEDC)
)
