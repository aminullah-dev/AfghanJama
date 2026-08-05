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

    // مسِ گرم — هم‌خانوادهٔ `Brand.Copper`، فقط تیره‌تر تا روی زمینهٔ
    // روشن به‌اندازهٔ کافی کنتراست داشته باشد. حالتِ روشن و تاریک باید
    // یک لهجه داشته باشند، وگرنه اپ در دو حالت دو محصولِ متفاوت است.
    secondary = Color(0xFF8F5F31),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFF6E5D2),
    onSecondaryContainer = Color(0xFF3B2413),

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
    // خطِ مویی که لبهٔ کارت را تعریف می‌کند. #E2E0D9 روی سفید فقط
    // نسبتِ ۱٫۳۲ می‌داد — یعنی همان خطی که `AppCard` رویش حساب کرده
    // بود تا کارت را بدونِ سایه از زمینه جدا کند، عملاً دیده نمی‌شد.
    outlineVariant = Color(0xFFD2D0C6),
    scrim = Color(0xFF000000),

    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B)
)

/*
 * حالتِ تاریک: میدانِ زمردی، تأکیدِ مسی.
 *
 * تا دیروز خاکستریِ خنثی بود (`#121412`) با لهجهٔ سبز. حالا خودِ
 * زمینه زمرد است. دو نکته که در انتخابِ این عددها تعیین‌کننده بود:
 *
 *  • **پله‌های سطح باید از هم جدا دیده شوند.** کارتِ روی زمینه فقط با
 *    اختلافِ روشنایی تعریف می‌شود، نه با سایه. اگر همه‌شان یک سبزِ
 *    نزدیک به هم باشند، صفحه یکدست و بی‌عمق می‌شود.
 *  • **متن باید بخواند.** `onSurface` روی `surface` نسبتِ کنتراستِ
 *    بالای ۱۲ دارد و `onSurfaceVariant` بالای ۷ — هر دو از حدِ AA
 *    عبور می‌کنند. سبزِ تیره وسوسه می‌کند که متن را هم سبز کنیم؛
 *    همان‌جاست که خوانایی می‌رود.
 *
 * `primary` عمداً همان نعناییِ روشن ماند و مسی نشد: مس در این طرح
 * نقشِ **تأکید** دارد نه نقشِ رنگِ سیستمی، و از `Brand`/`CopperBrush`
 * می‌آید. اگر `primary` مسی می‌شد، هر چک‌باکس و سوییچ و نوارِ پیشرفت
 * هم مسی می‌شد و تأکید معنایش را از دست می‌داد.
 */
val DarkColors = darkColorScheme(
    primary = Color(0xFF7FD9B9),
    onPrimary = Color(0xFF00382B),
    primaryContainer = Color(0xFF145746),
    onPrimaryContainer = Color(0xFFA5F2DA),

    // مس — همان پله‌های `Brand`, تا تأکیدِ تخت و تأکیدِ گرادیانی از
    // یک خانواده باشند.
    secondary = Color(0xFFE3B489),
    onSecondary = Color(0xFF40270F),
    secondaryContainer = Color(0xFF6B482A),
    onSecondaryContainer = Color(0xFFFBDFBB),

    tertiary = Color(0xFFA9CBD8),
    onTertiary = Color(0xFF11333E),
    tertiaryContainer = Color(0xFF2C4A56),
    onTertiaryContainer = Color(0xFFC6E4F0),

    background = Color(0xFF07271E),
    onBackground = Color(0xFFEAF2EC),

    surface = Color(0xFF0D3327),
    onSurface = Color(0xFFEAF2EC),
    surfaceVariant = Color(0xFF15402F),
    onSurfaceVariant = Color(0xFFAFC9BA),

    surfaceContainerLowest = Color(0xFF051F17),
    surfaceContainerLow = Color(0xFF0A2E23),
    surfaceContainer = Color(0xFF103A2C),
    surfaceContainerHigh = Color(0xFF164634),
    surfaceContainerHighest = Color(0xFF1D523E),
    surfaceBright = Color(0xFF225A44),
    surfaceDim = Color(0xFF051F17),

    inverseSurface = Color(0xFFEAF2EC),
    inverseOnSurface = Color(0xFF12352A),
    inversePrimary = Color(0xFF1F6E5C),

    outline = Color(0xFF6E9484),
    outlineVariant = Color(0xFF2A6049),
    scrim = Color(0xFF000000),

    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFF9DEDC)
)
