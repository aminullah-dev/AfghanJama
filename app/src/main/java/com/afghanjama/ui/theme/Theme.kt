package com.afghanjama.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import android.provider.Settings
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.afghanjama.R

// ======================================================
// فونت: وزیرمتن (Vazirmatn) — فونت استاندارد و خوانا فارسی
// ======================================================
val Vazirmatn = FontFamily(
    Font(R.font.vazirmatn_regular, FontWeight.Normal),
    Font(R.font.vazirmatn_medium, FontWeight.Medium),
    Font(R.font.vazirmatn_semibold, FontWeight.SemiBold),
    Font(R.font.vazirmatn_bold, FontWeight.Bold)
)

// مقیاس حالا در `:core` است تا ویندوز هم همان را داشته باشد؛ اینجا
// فقط قلم داده می‌شود، چون هر سکو جور دیگری بارش می‌کند.
private val AppTypography = appTypography(Vazirmatn)
)

// ======================================================
// گردیِ گوشه‌ها: یک‌جا تعریف می‌شود تا کارت، دکمه، دیالوگ و فیلدِ
// همهٔ صفحه‌ها یک زبانِ بصری داشته باشند (کمی نرم‌تر از پیش‌فرضِ متریال).
// ======================================================
private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

// ======================================================
// پالت مینیمال کارگاهی: سبز عمیق + خنثی‌های گرم
//

@Composable
fun KhayatYarTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    // چیدمان همیشه راست‌به‌چپ، مستقل از زبان دستگاه
    // کاربری که در تنظیماتِ گوشی انیمیشن را خاموش کرده، همان را از اپ
    // هم می‌خواهد. بعضی‌ها با حرکت سرگیجه می‌گیرند؛ این حقِ اوست، نه
    // سلیقه. `ANIMATOR_DURATION_SCALE` همان کلیدی است که خودِ اندروید
    // برای «Remove animations» می‌نویسد.
    val context = LocalContext.current
    val reducedMotion = remember(context) {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f
        ) == 0f
    }

    CompositionLocalProvider(
        LocalLayoutDirection provides LayoutDirection.Rtl,
        LocalReducedMotion provides reducedMotion
    ) {
        MaterialTheme(
            colorScheme = if (dark) DarkColors else LightColors,
            typography = AppTypography,
            shapes = AppShapes
        ) {
            // اندروید ۱۵ به بعد صفحه را لبه‌به‌لبه می‌کند و دیگر خودش
            // پنجره را برای کیبورد جمع نمی‌کند؛ پس کیبورد روی کادرهای
            // پایینی می‌افتاد و کاربر جایی را که تایپ می‌کرد نمی‌دید.
            // یک جا برای کلِ اپ درست می‌شود، نه در تک‌تکِ ۲۶ صفحه.
            Box(Modifier.fillMaxSize().imePadding()) { content() }
        }
    }
}
