package com.afghanjama.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/*
 * هویتِ بصریِ خیاط‌یار: زمینهٔ آبیِ نفتی، تأکیدِ مرجانی.
 *
 * **چرا این فایل جداست و در `:core` است.** پالتِ متریال فقط رنگِ تخت
 * می‌شناسد؛ `colorScheme` جایی برای گرادیان ندارد. اگر هر صفحه
 * گرادیانِ خودش را می‌ساخت، ده‌ها تعریفِ کمی‌متفاوت پیدا می‌شد و
 * «مرجانیِ» فاکتور با «مرجانیِ» کارتِ موجودی یکی نبود. حالا یک تعریف
 * است و هر دو سکو — گوشی و ویندوز — از همین می‌خوانند.
 *
 * **پیش‌تر مس روی زمرد بود.** این پالت جایش را گرفت. رمپ‌ها از چهار
 * رنگِ پایه ساخته شده‌اند و هر پله **حساب** شده، نه انتخاب:
 *
 *     نارنجیِ مرجانی  #FF6D41
 *     آبیِ تیره        #004E72
 *     سفیدِ روشن       #F9F9F9
 *     آبیِ نفتیِ تیره   #0A2735
 *
 * **و یک محدودیتِ سختِ همین مرجانی:** متنِ سفید رویش فقط نسبتِ ۲٫۷۹
 * می‌دهد، زیرِ حدِ ۴٫۵ی AA. پس مرجانی نمی‌تواند زمینهٔ متنِ روشن باشد
 * — همان چیزی که مس هم داشت و با متنِ تیره حل شد. [OnCoral] قهوه‌ایِ
 * سوخته است، نه سفید، و روی تیره‌ترین پلهٔ رمپ ۵٫۱۳ می‌دهد.
 */
object Brand {

    // ── مرجانی ────────────────────────────────────────────────────
    //
    // چهار پله، نه دو. گرادیانِ دوپله تخت به نظر می‌رسد؛ آنچه سطح را
    // زنده نشان می‌دهد یک **باریکهٔ روشن** در میانه است که مثلِ بازتابِ
    // نور روی سطحِ صیقلی عمل می‌کند.

    /** روشن‌ترین پله — لبهٔ بالاییِ گرادیان. */
    val CoralLight = Color(0xFFFF9170)

    /** باریکهٔ بازتاب — همان چیزی که سطح را صیقلی نشان می‌دهد. */
    val CoralSheen = Color(0xFFFF825C)

    /** مرجانیِ پایه — همان رنگی که پالت با آن شناخته می‌شود. */
    val Coral = Color(0xFFFF6D41)

    /** تیره‌ترین پله — لبهٔ پایینی. */
    val CoralDeep = Color(0xFFFF4F1A)

    /**
     * متنی که روی مرجانی می‌نشیند.
     *
     * قهوه‌ایِ سوختهٔ بسیار تیره، نه سیاهِ خالص: روی زمینهٔ گرم، سیاهِ
     * خالص سرد و بریده به نظر می‌رسد.
     *
     * **این عدد اندازه‌گیری شده، نه انتخابِ ذوقی.** روی تیره‌ترین پلهٔ
     * رمپ نسبتِ ۵٫۱۳ می‌دهد و روی روشن‌ترین ۷٫۶۶ — هر دو بالای حدِ
     * ۴٫۵ی AA. بررسیِ `contrast` همین را نگه می‌دارد.
     */
    val OnCoral = Color(0xFF371106)

    /** همان متن، کم‌رنگ‌تر — برای زیرنویس روی مرجانی. حدِ آن ۳٫۰ است. */
    val OnCoralMuted = Color(0xFF5F2B1C)

    // ── آبیِ نفتی ──────────────────────────────────────────────────

    /** تیره‌ترین پله — همان `#0A2735`ِ پالت، و زمینهٔ حالتِ تاریک. */
    val PetrolDeep = Color(0xFF0A2735)

    /** نفتیِ میانه. */
    val Petrol = Color(0xFF103F56)

    /** روشن‌ترین پله — لبهٔ بالاییِ گرادیانِ زمینه. */
    val PetrolLight = Color(0xFF175878)

    /** متنی که روی نفتی می‌نشیند. */
    val OnPetrol = Color(0xFFEBF1F4)

    /** همان، کم‌رنگ‌تر. */
    val OnPetrolMuted = Color(0xFFA8BDC7)
}

/**
 * گرادیانِ مرجانی — رویهٔ **دکمهٔ اصلی** و نشانِ اپ.
 *
 * **تا ۱.۱۳ پنج‌پله بود، با یک «باریکهٔ بازتاب» در میانه.** روی دکمهٔ
 * کوچک خوب می‌نشست، ولی همان گرادیان رویهٔ کارت‌های قهرمان هم بود و
 * روی سطحِ پهن مثلِ پلاستیکِ براق دیده می‌شد: نواری روشن که از وسطِ
 * عددِ موجودی رد می‌شد. حالا سه پلهٔ نرم است — نورِ مایل از بالا، بی
 * نوار — و کارتِ قهرمان رویهٔ خودش را دارد ([InkBrush]).
 *
 * نتیجه این است که مرجانی **کم** دیده می‌شود: فقط جایی که باید زده
 * شود. تأکید وقتی کار می‌کند که اقلیت باشد.
 *
 * `Offset.Infinite` عمداً استفاده نشده. با آن، جهتِ گرادیان به
 * **اندازهٔ همان جعبه** گره می‌خورد: دکمهٔ پهن و دکمهٔ باریک دو زاویهٔ
 * متفاوت می‌گرفتند و کنارِ هم ناهماهنگ دیده می‌شدند. با نسبتِ ثابت،
 * زاویه در هر اندازه‌ای یکی می‌ماند.
 */
val CoralBrush: Brush = Brush.linearGradient(
    0.00f to Brand.CoralSheen,
    0.55f to Brand.Coral,
    1.00f to Brand.CoralDeep,
    start = androidx.compose.ui.geometry.Offset(0f, 0f),
    end = androidx.compose.ui.geometry.Offset(520f, 260f)
)

/** همان مرجانی، ولی خوابیده — برای نوارهای پهن و کوتاه مثلِ سربرگ. */
val CoralBandBrush: Brush = Brush.linearGradient(
    0.00f to Brand.CoralSheen,
    0.50f to Brand.Coral,
    1.00f to Brand.CoralDeep,
    start = androidx.compose.ui.geometry.Offset(0f, 0f),
    end = androidx.compose.ui.geometry.Offset(1400f, 120f)
)

/**
 * رویهٔ **کارتِ قهرمان** — نفتیِ عمیق، نه مرجانی.
 *
 * کارتِ قهرمان بزرگ‌ترین سطحِ رنگیِ هر صفحه است (موجودیِ نقد، خلاصهٔ
 * فروش). تا ۱.۱۳ مرجانیِ براق بود و با دکمهٔ اصلی — که آن هم مرجانی
 * است — سرِ توجه رقابت می‌کرد. حالا سطحِ بزرگ آرام و تیره است و عددِ
 * روشن رویش می‌نشیند؛ مرجانی برای کنش می‌ماند.
 *
 * متنِ روی آن [Brand.OnPetrol] و [Brand.OnPetrolMuted] است؛ هر سه پلهٔ
 * نفتی در بررسیِ `contrast` با همین دو سنجیده می‌شوند.
 */
val InkBrush: Brush = Brush.linearGradient(
    0.00f to Brand.PetrolLight,
    0.45f to Brand.Petrol,
    1.00f to Brand.PetrolDeep,
    start = androidx.compose.ui.geometry.Offset(0f, 0f),
    end = androidx.compose.ui.geometry.Offset(900f, 620f)
)

/** زمینهٔ نفتیِ صفحه‌های قهرمان (ورود و قفلِ اپ). */
val PetrolBrush: Brush = Brush.linearGradient(
    0.00f to Brand.PetrolLight,
    0.55f to Brand.Petrol,
    1.00f to Brand.PetrolDeep,
    start = androidx.compose.ui.geometry.Offset(0f, 0f),
    end = androidx.compose.ui.geometry.Offset(600f, 1600f)
)

/**
 * مرجانیِ خاموش — برای دکمهٔ غیرفعال.
 *
 * دکمهٔ غیرفعال نباید براق بماند: کاربر رنگِ زنده را «قابلِ زدن»
 * می‌خواند و دو بار می‌زند. یک سطحِ تختِ کم‌رمق صریح‌تر است.
 */
@Composable
@ReadOnlyComposable
fun disabledSurface(): Color =
    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)

/** متنِ روی سطحِ خاموش. */
@Composable
@ReadOnlyComposable
fun disabledContent(): Color =
    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
