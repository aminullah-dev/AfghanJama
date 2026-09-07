package com.afghanjama.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * حرکت — **یک تعریف برای هر دو سکو**، مثلِ پالت و گرادیان.
 *
 * **چرا این فایل ساخته شد.** اپ تا امروز هیچ حرکتی نداشت: نه یک
 * `animateFloatAsState`، نه یک `AnimatedVisibility`، نه یک
 * `animateItem`. در ۳۹ صفحه و ۳۱۳ فایل، صفر. هر چیزی آنی می‌پرید —
 * کارتی که باز می‌شد، عددی که عوض می‌شد، سطری که از فهرست می‌رفت.
 *
 * آنی پریدن فقط «ساده» نیست؛ **اطلاعات را می‌خورد**. وقتی صندوق از
 * ۱۲٬۰۰۰ به ۱۵٬۰۰۰ می‌پرد، چشم نمی‌فهمد که عوض شد یا از اول همین بود.
 * وقتی سفارشی از «برش» به «دوخت» می‌رود و سطر بی‌مقدمه جابه‌جا می‌شود،
 * کارفرما نمی‌داند کدام سطر حرکت کرد. حرکت اینجا تزئین نیست، **جملهٔ
 * خبری** است: «این عوض شد».
 *
 * **چرا اعداد اینجا و نه در صفحه‌ها.** اگر هر صفحه مدتِ خودش را
 * می‌نوشت، ۳۹ ریتمِ کمی‌متفاوت پیدا می‌شد و اپ ناهماهنگ حس می‌شد —
 * همان اتفاقی که سرِ رنگ افتاده بود و با `Palette` جمع شد. یک تعریف،
 * و بررسیِ `motion` نگهش می‌دارد.
 */
object Motion {

    // ── مدت‌ها ───────────────────────────────────────────────────
    //
    // هر سه در بازهٔ ۱۵۰ تا ۳۰۰ میلی‌ثانیه‌اند. زیرِ ۱۵۰ چشم حرکت را
    // نمی‌گیرد و همان پریدنِ آنی حس می‌شود؛ بالای ۳۰۰ کاربر منتظر
    // می‌مانَد و اپ کُند به نظر می‌رسد.

    /** تغییرِ کوچک و موضعی: رنگِ فشرده‌شدن، چرخشِ فلش، پررنگ شدن. */
    const val QUICK_MS = 150

    /** پیش‌فرض: باز و بسته شدن، عددی که عوض می‌شود، جابه‌جاییِ سطر. */
    const val NORMAL_MS = 220

    /** حرکتِ بزرگ‌تر: ورقهٔ پایین‌آمدنی، کارتی که کلِ عرض را می‌گیرد. */
    const val SLOW_MS = 300

    /**
     * نمایشِ محیطی — تنها مدتی که عمداً بیرونِ بازهٔ ۱۵۰..۳۰۰ است.
     *
     * تختهٔ دیواریِ کارگاه خودش صفحه‌ها را می‌چرخاند و کسی منتظرِ
     * واکنشش نیست. قاعدهٔ «۳۰۰ حداکثر» برای پاسخ به لمسِ کاربر است:
     * آنجا هر میلی‌ثانیهٔ اضافه یعنی معطلی. اینجا برعکس — محوِ تند روی
     * تخته‌ای که از آن‌سرِ کارگاه دیده می‌شود، پرش حس می‌شود.
     *
     * این عدد از قبل در `BoardScreen` بود؛ اینجا آمد تا **تصمیم** باشد
     * نه عددی که کسی روزی سرخود نوشته.
     */
    const val AMBIENT_MS = 500

    // ── شتاب ─────────────────────────────────────────────────────

    /**
     * ورود: تند شروع می‌کند و نرم می‌ایستد.
     *
     * چیزی که وارد می‌شود باید **رسیدنش** دیده شود، نه رفتنش. منحنی
     * استانداردِ متریال است، نه سلیقه.
     */
    val EnterEasing: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f)

    /**
     * خروج: نرم شروع می‌کند و تند می‌رود.
     *
     * چیزی که می‌رود نباید معطل کند — کاربر تصمیمش را گرفته.
     */
    val ExitEasing: Easing = CubicBezierEasing(0.3f, 0.0f, 0.8f, 0.15f)

    /** رفت‌وبرگشتِ متقارن — برای چیزی که جابه‌جا می‌شود، نه می‌آید و می‌رود. */
    val StandardEasing: Easing = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)

    // ── نسخه‌های آماده ───────────────────────────────────────────
    //
    // این‌ها [reducedMotion] را می‌خوانند: اگر کاربر در تنظیماتِ سیستم
    // انیمیشن را خاموش کرده باشد، مدت صفر می‌شود و همه‌چیز آنی — ولی
    // **بی‌آنکه کد جای دیگری شرط بگذارد**. کسی که حرکت را آزاردهنده
    // می‌داند یا سرگیجه می‌گیرد، حقِ خاموش کردنش را دارد.

    @Composable
    @ReadOnlyComposable
    fun <T> quick(): FiniteAnimationSpec<T> =
        tween(durationMillis = scale(QUICK_MS), easing = StandardEasing)

    @Composable
    @ReadOnlyComposable
    fun <T> normal(): FiniteAnimationSpec<T> =
        tween(durationMillis = scale(NORMAL_MS), easing = StandardEasing)

    @Composable
    @ReadOnlyComposable
    fun <T> enter(): FiniteAnimationSpec<T> =
        tween(durationMillis = scale(NORMAL_MS), easing = EnterEasing)

    @Composable
    @ReadOnlyComposable
    fun <T> exit(): FiniteAnimationSpec<T> =
        tween(durationMillis = scale(QUICK_MS), easing = ExitEasing)

    /** محوِ نمایشِ محیطی — فقط برای تختهٔ دیواری. */
    @Composable
    @ReadOnlyComposable
    fun <T> ambient(): FiniteAnimationSpec<T> =
        tween(durationMillis = scale(AMBIENT_MS), easing = StandardEasing)

    @Composable
    @ReadOnlyComposable
    private fun scale(ms: Int): Int = if (LocalReducedMotion.current) 0 else ms
}

/**
 * کاربر در تنظیماتِ سیستم حرکت را کم کرده؟
 *
 * روی اندروید از `Settings.Global.ANIMATOR_DURATION_SCALE` خوانده
 * می‌شود؛ روی ویندوز چنین تنظیمی در دسترسِ برنامه نیست و پیش‌فرض
 * `false` می‌مانَد.
 *
 * پیش‌فرضِ `false` عمدی است: اگر سکویی این را وصل نکند، اپ حرکتِ کامل
 * دارد — نه اینکه بی‌صدا بی‌حرکت شود و کسی نفهمد چرا.
 */
val LocalReducedMotion = staticCompositionLocalOf { false }
