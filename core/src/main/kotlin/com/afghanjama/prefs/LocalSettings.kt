package com.afghanjama.prefs

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * راهِ رسیدنِ صفحه‌ها به تنظیمات، بی اینکه بدانند روی چه سکویی‌اند.
 *
 * تا امروز هر صفحه‌ای که تنظیمات می‌خواست `LocalContext.current` را
 * می‌گرفت — و همان یک خط بود که نگهش می‌داشت در `:app`. حالا به‌جایش
 * این را می‌گیرد و صفحه روی ویندوز هم کار می‌کند.
 *
 * `static` است چون در عمرِ برنامه عوض نمی‌شود؛ با `staticCompositionLocalOf`
 * تغییرش کلِ درخت را بازترکیب می‌کند، ولی چون هرگز تغییر نمی‌کند این
 * هزینه صفر است و در عوض خواندنش ارزان می‌شود.
 *
 * پیش‌فرض ندارد و عمداً خطا می‌دهد: تنظیماتِ خالیِ ساختگی یعنی نامِ
 * کارگاه بی‌صدا خالی برگردد و فاکتور بی‌نام چاپ شود. بهتر است همان
 * لحظهٔ اول بلند بشکند.
 */
val LocalSettings = staticCompositionLocalOf<Settings> {
    error(
        "LocalSettings داده نشده. ریشهٔ هر سکو باید با " +
            "CompositionLocalProvider(LocalSettings provides …) پیچیده شود."
    )
}
