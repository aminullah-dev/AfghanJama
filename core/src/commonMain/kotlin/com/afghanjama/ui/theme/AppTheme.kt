package com.afghanjama.ui.theme

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.LayoutDirection

/**
 * تمِ خیاط‌یار — **یک تابع برای اندروید، آیفون و کامپیوتر**.
 *
 * **وضعی که از آن آمدیم (۱.۱۱).** پالت، مقیاسِ قلم و حرکت از قبل در `:core`
 * بودند، ولی هر سکو `MaterialTheme`ِ خودش را می‌ساخت و هر کدام چیزی جا
 * می‌انداخت:
 *
 *  - اندروید `Shapes`ِ خودش را داشت و آیفون و کامپیوتر هیچ (`AppShapes`).
 *  - فقط اندروید «کاهشِ حرکتِ» سیستم را می‌خواند؛ آیفون نه.
 *  - یکی از دو پنجرهٔ آزمونِ کامپیوتر (`DeskSmoke`) مقیاسِ قلم نداشت و
 *    صفحه را با اندازه‌های پیش‌فرضِ متریال می‌کشید.
 *
 * حالا هر سکو فقط چیزی را می‌دهد که واقعاً مالِ خودش است — قلم (هر سکو
 * جورِ دیگری بارش می‌کند)، روشن یا تاریک، و پرچمِ کاهشِ حرکت — و بقیه
 * همین‌جاست: رنگ، قلم، گوشه‌ها، راست‌به‌چپ.
 *
 * کامپیوتر فعلاً فقط روشن است (طرحِ فازِ C، پرسشِ ۱۱)؛ آیفون هم مثلِ امروز
 * روشن می‌ماند تا حالتِ تاریکش جدا آزموده شود.
 */
@Composable
fun AppTheme(
    fontFamily: FontFamily,
    dark: Boolean = false,
    reducedMotion: Boolean = false,
    content: @Composable () -> Unit,
) {
    val typography = remember(fontFamily) { appTypography(fontFamily) }
    CompositionLocalProvider(
        // کلِ برنامه راست‌به‌چپ، مستقل از زبانِ دستگاه.
        LocalLayoutDirection provides LayoutDirection.Rtl,
        LocalReducedMotion provides reducedMotion,
    ) {
        MaterialTheme(
            colorScheme = if (dark) DarkColors else LightColors,
            typography = typography,
            shapes = AppShapes,
        ) {
            /*
             * رنگِ پیش‌فرضِ متن از تم، نه سیاهِ ثابت.
             *
             * `MaterialTheme` خودش `LocalContentColor` را نمی‌گذارد؛ آن را
             * `Surface` و `Scaffold` می‌گذارند. پنجرهٔ کامپیوتر محتوا را روی
             * یک `Box`ِ شیشه‌ای می‌نشاند، نه `Surface` — پس هر متنی که رنگِ
             * صریح نداشت سیاهِ پیش‌فرض می‌گرفت. در حالتِ روشن دیده نمی‌شد
             * (سیاه و جوهرِ تیره نزدیکِ هم‌اند)، ولی در حالتِ تاریک عنوانِ
             * داشبورد و نامِ کارگاه روی زمینهٔ نفتی محو بودند.
             *
             * هر `Surface`ِ پایین‌تر همچنان رنگِ خودش را می‌گذارد.
             */
            CompositionLocalProvider(
                LocalContentColor provides MaterialTheme.colorScheme.onBackground,
                content = content,
            )
        }
    }
}
