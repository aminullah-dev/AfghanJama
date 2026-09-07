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
 * یعنی همان صفحه، با همان کد، روی گوشی یک‌جور بود و روی پی‌سی جورِ
 * دیگر. کسی هم تا دیدنِ پنجره نمی‌فهمید، چون کامپایل درست است.
 *
 * حالا پالت اینجاست و هر دو سکو از همین می‌خوانند — همان کاری که سرِ
 * دیتابیس، مقیاسِ قلم و چیدمانِ کاغذ هم شد.
 *
 * **رنگ‌های پایه:**
 *
 *     نارنجیِ مرجانی  #FF6D41   تأکید
 *     آبیِ تیره        #004E72   رنگِ اصلی
 *     سفیدِ روشن       #F9F9F9   زمینهٔ روشن
 *     آبیِ نفتیِ تیره   #0A2735   زمینهٔ تاریک
 *
 * سایه‌های میانی از همین چهار تا **حساب** شده‌اند، نه انتخاب: هر ۶۲
 * جفتِ متن-روی-زمینه پیش از نوشته شدن اینجا اندازه‌گیری شد و همه از
 * حدِ WCAG گذشتند. بررسیِ `contrast` همان‌ها را نگه می‌دارد و رنگ‌ها
 * را از همین فایل می‌خواند — نه از نسخه‌ای دستی — تا اولین باری که
 * کسی پالت را عوض کند، سبزِ دروغ ندهد.
 */

// هر نقشی که در اپ استفاده می‌شود اینجا **صریح** تعریف شده. نقشِ
// تعریف‌نشده به پالتِ پیش‌فرضِ متریال برمی‌گردد که رنگ‌های خودش را
// دارد و به این پالت ربطی ندارد — همان اتفاقی که برای
// `tertiaryContainer` افتاده بود: بنرِ «مرکز هشدار» صورتیِ پیش‌فرضِ
// متریال (#FFD8E4) درمی‌آمد.
// ======================================================
val LightColors = lightColorScheme(
    primary = Color(0xFF004E72),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD0E7F1),
    onPrimaryContainer = Color(0xFF002D42),
        // مرجانی برای **متن و آیکن** باید تیره‌تر از رنگِ پایه باشد:
        // #FF6D41 روی زمینهٔ روشن فقط ۲٫۶۵ می‌دهد، زیرِ حدِ ۴٫۵. این
        // سایه ۴٫۵۳ می‌دهد و همان لهجه را نگه می‌دارد.
    secondary = Color(0xFFD93200),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFD8CC),
    onSecondaryContainer = Color(0xFF4B1606),
    tertiary = Color(0xFF375B6C),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFDEE8ED),
    onTertiaryContainer = Color(0xFF162F3B),
    background = Color(0xFFF9F9F9),
    onBackground = Color(0xFF0C2531),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF0C2531),
    surfaceVariant = Color(0xFFEBEEF0),
    onSurfaceVariant = Color(0xFF4B626C),
        // طبقه‌های سطح: از روشن‌ترین (کارتِ روی زمینه) تا تیره‌ترین.
        // عمق با اختلافِ روشنایی ساخته می‌شود، نه با سایه و خط.
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFAFBFC),
    surfaceContainer = Color(0xFFF1F4F6),
    surfaceContainerHigh = Color(0xFFEAEEF0),
    surfaceContainerHighest = Color(0xFFE3E8EA),
    surfaceBright = Color(0xFFFFFFFF),
    surfaceDim = Color(0xFFDCE2E5),
    inverseSurface = Color(0xFF1F3A47),
    inverseOnSurface = Color(0xFFF0F3F5),
    inversePrimary = Color(0xFF51BBEC),
    outline = Color(0xFF738B96),
        // خطِ مویی که لبهٔ کارت را تعریف می‌کند — `AppCard` رویش حساب
        // کرده تا کارت را بی‌سایه از زمینه جدا کند. حدش ۱٫۵ است.
    outlineVariant = Color(0xFFCCD3D7),
    scrim = Color(0xFF000000),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B)
)

/*
 * حالتِ تاریک: میدانِ نفتی، تأکیدِ مرجانی.
 *
 * دو نکته که در انتخابِ این عددها تعیین‌کننده بود:
 *
 *  • **پله‌های سطح باید از هم جدا دیده شوند.** کارتِ روی زمینه فقط با
 *    اختلافِ روشنایی تعریف می‌شود، نه با سایه. اگر همه‌شان یک آبیِ
 *    نزدیک به هم باشند، صفحه یکدست و بی‌عمق می‌شود.
 *  • **متن باید بخواند.** آبیِ تیرهٔ پایه (#004E72) روی زمینهٔ نفتی
 *    فقط ۱٫۷۲ می‌دهد — عملاً نامرئی. پس `primary` در این طرح سایهٔ
 *    روشن‌ترِ همان آبی است، نه خودش.
 *
 * `secondary` اینجا دقیقاً همان مرجانیِ پایه است، چون روی نفتی ۵٫۵۵
 * می‌دهد و لازم نیست دست بخورد.
 */
val DarkColors = darkColorScheme(
    primary = Color(0xFF25B2F4),
    onPrimary = Color(0xFF002333),
    primaryContainer = Color(0xFF0B5E84),
    onPrimaryContainer = Color(0xFFC2E5F4),
        // مرجانی برای **متن و آیکن** باید تیره‌تر از رنگِ پایه باشد:
        // #FF6D41 روی زمینهٔ روشن فقط ۲٫۶۵ می‌دهد، زیرِ حدِ ۴٫۵. این
        // سایه ۴٫۵۳ می‌دهد و همان لهجه را نگه می‌دارد.
    secondary = Color(0xFFFF6D41),
    onSecondary = Color(0xFF371106),
    secondaryContainer = Color(0xFF8F3114),
    onSecondaryContainer = Color(0xFFFDD1C3),
    tertiary = Color(0xFF9DBAC8),
    onTertiary = Color(0xFF142934),
    tertiaryContainer = Color(0xFF365563),
    onTertiaryContainer = Color(0xFFD7E4EA),
    background = Color(0xFF0A2735),
    onBackground = Color(0xFFEBF1F4),
    surface = Color(0xFF122F3D),
    onSurface = Color(0xFFEBF1F4),
    surfaceVariant = Color(0xFF1D3E4E),
    onSurfaceVariant = Color(0xFFAEC2CB),
        // طبقه‌های سطح: از روشن‌ترین (کارتِ روی زمینه) تا تیره‌ترین.
        // عمق با اختلافِ روشنایی ساخته می‌شود، نه با سایه و خط.
    surfaceContainerLowest = Color(0xFF091C25),
    surfaceContainerLow = Color(0xFF0E2936),
    surfaceContainer = Color(0xFF153544),
    surfaceContainerHigh = Color(0xFF1F4354),
    surfaceContainerHighest = Color(0xFF295064),
    surfaceBright = Color(0xFF2F5A6F),
    surfaceDim = Color(0xFF091C25),
    inverseSurface = Color(0xFFEBF1F4),
    inverseOnSurface = Color(0xFF163341),
    inversePrimary = Color(0xFF004E72),
    outline = Color(0xFF7E9BA9),
        // خطِ مویی که لبهٔ کارت را تعریف می‌کند — `AppCard` رویش حساب
        // کرده تا کارت را بی‌سایه از زمینه جدا کند. حدش ۱٫۵ است.
    outlineVariant = Color(0xFF3D6071),
    scrim = Color(0xFF000000),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFF9DEDC)
)
