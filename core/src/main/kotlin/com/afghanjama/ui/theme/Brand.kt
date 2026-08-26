package com.afghanjama.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/*
 * هویتِ بصریِ خیاط‌یار: زمینهٔ زمردی، تأکیدِ مسی.
 *
 * **چرا این فایل جداست و در `:core` است.** پالتِ متریال فقط رنگِ تخت
 * می‌شناسد؛ `colorScheme` جایی برای گرادیان ندارد. اگر هر صفحه
 * گرادیانِ خودش را می‌ساخت، ده‌ها تعریفِ کمی‌متفاوت پیدا می‌شد و
 * «مسیِ» فاکتور با «مسیِ» کارتِ موجودی یکی نبود. حالا یک تعریف است و
 * هر دو سکو — گوشی و ویندوز — از همین می‌خوانند، همان‌طور که پالت،
 * دیتابیس و چیدمانِ کاغذ یکی شدند.
 *
 * **چرا مس و نه طلا.** طلا روی سبز به زردی می‌زند و ارزان به نظر
 * می‌رسد؛ مس گرم است و با برنزیِ پارچه و کارگاه می‌خواند — همان
 * `secondary`ی که پالت از اول داشت، فقط اشباع‌تر.
 */
object Brand {

    // ── مس ────────────────────────────────────────────────────────
    //
    // چهار پله، نه دو. گرادیانِ دوپله تخت به نظر می‌رسد؛ آنچه فلز را
    // فلز نشان می‌دهد یک **باریکهٔ روشن** در میانه است که مثلِ بازتابِ
    // نور روی سطحِ صیقلی عمل می‌کند.

    /** روشن‌ترین پله — لبهٔ بالاییِ گرادیان. */
    val CopperLight = Color(0xFFD8B496)

    /** باریکهٔ بازتاب — همان چیزی که سطح را صیقلی نشان می‌دهد. */
    val CopperSheen = Color(0xFFD0A480)

    /** مسِ پایه. */
    val Copper = Color(0xFFC58E62)

    /** تیره‌ترین پله — لبهٔ پایینی. */
    val CopperDeep = Color(0xFFBA7944)

    /**
     * متنی که روی مس می‌نشیند.
     *
     * قهوه‌ایِ بسیار تیره، نه سیاهِ خالص: روی زمینهٔ گرم، سیاهِ خالص
     * سرد و بریده به نظر می‌رسد.
     *
     * **این عدد اندازه‌گیری شده، نه انتخابِ ذوقی.** با نسخهٔ اولِ این
     * پالت، متن روی [CopperDeep] نسبتِ ۲٫۸۷ می‌گرفت — زیرِ حدِ ۴٫۵ی
     * AA. یعنی روی همان لبهٔ تیرهٔ گرادیان، عددِ موجودیِ نقد سخت خوانده
     * می‌شد. رمپِ مس روشن‌تر شد تا تیره‌ترین پله‌اش هم ۴٫۵ بدهد و این
     * متن تیره‌تر شد. بررسیِ `contrast` همین را نگه می‌دارد.
     */
    val OnCopper = Color(0xFF2E1C0C)

    /**
     * همان متن، کم‌رنگ‌تر — برای زیرنویس روی مس.
     *
     * «کم‌رنگ» اینجا سقف دارد: نسخهٔ اولش ۱٫۵۷ می‌داد روی لبهٔ تیره،
     * یعنی عملاً نامرئی. حالا روی تیره‌ترین پله ۳٫۰ می‌دهد — حدِ متنِ
     * فرعی.
     */
    val OnCopperMuted = Color(0xFF523823)

    // ── زمرد ──────────────────────────────────────────────────────

    /** سبزِ عمیقِ زمینه — تیره‌ترین پله. */
    val EmeraldDeep = Color(0xFF07271E)

    /** زمردِ میانه. */
    val Emerald = Color(0xFF0E3A2C)

    /** زمردِ روشن‌تر — لبهٔ بالاییِ گرادیانِ زمینه. */
    val EmeraldLight = Color(0xFF15503C)

    /** متنی که روی زمرد می‌نشیند. */
    val OnEmerald = Color(0xFFEAF2EC)

    /** همان، کم‌رنگ‌تر. */
    val OnEmeraldMuted = Color(0xFFA9C4B5)
}

/**
 * گرادیانِ مسی — رویهٔ کارت‌های قهرمان و دکمهٔ اصلی.
 *
 * قطری است نه عمودی: نورِ افقی سطح را تخت نشان می‌دهد، ولی قطری همان
 * حسی را می‌دهد که ورقهٔ فلز زیرِ نورِ مایل دارد.
 *
 * `Offset.Infinite` عمداً استفاده نشده. با آن، جهتِ گرادیان به
 * **اندازهٔ همان جعبه** گره می‌خورد: کارتِ پهن و دکمهٔ باریک دو زاویهٔ
 * متفاوت می‌گرفتند و کنارِ هم ناهماهنگ دیده می‌شدند. با نسبتِ ثابت،
 * زاویه در هر اندازه‌ای یکی می‌ماند.
 */
val CopperBrush: Brush = Brush.linearGradient(
    0.00f to Brand.CopperDeep,
    0.28f to Brand.Copper,
    0.46f to Brand.CopperLight,
    0.58f to Brand.CopperSheen,
    1.00f to Brand.CopperDeep,
    start = androidx.compose.ui.geometry.Offset(0f, 0f),
    end = androidx.compose.ui.geometry.Offset(1000f, 420f)
)

/** همان مس، ولی خوابیده — برای نوارهای پهن و کوتاه مثلِ سربرگ. */
val CopperBandBrush: Brush = Brush.linearGradient(
    0.00f to Brand.CopperDeep,
    0.35f to Brand.Copper,
    0.52f to Brand.CopperLight,
    0.68f to Brand.CopperSheen,
    1.00f to Brand.CopperDeep,
    start = androidx.compose.ui.geometry.Offset(0f, 0f),
    end = androidx.compose.ui.geometry.Offset(1400f, 120f)
)

/** زمینهٔ زمردیِ صفحه‌های قهرمان (ورود و قفلِ اپ). */
val EmeraldBrush: Brush = Brush.linearGradient(
    0.00f to Brand.EmeraldLight,
    0.55f to Brand.Emerald,
    1.00f to Brand.EmeraldDeep,
    start = androidx.compose.ui.geometry.Offset(0f, 0f),
    end = androidx.compose.ui.geometry.Offset(600f, 1600f)
)

/**
 * مسِ خاموش — برای دکمهٔ غیرفعال.
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
