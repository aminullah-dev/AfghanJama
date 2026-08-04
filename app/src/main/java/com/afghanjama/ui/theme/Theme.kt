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
import androidx.compose.runtime.CompositionLocalProvider
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

private val AppTypography = Typography(
    displayLarge = TextStyle(fontFamily = Vazirmatn, fontWeight = FontWeight.Bold, fontSize = 48.sp),
    displayMedium = TextStyle(fontFamily = Vazirmatn, fontWeight = FontWeight.Bold, fontSize = 40.sp),
    displaySmall = TextStyle(fontFamily = Vazirmatn, fontWeight = FontWeight.Bold, fontSize = 34.sp),
    headlineLarge = TextStyle(fontFamily = Vazirmatn, fontWeight = FontWeight.Bold, fontSize = 30.sp, lineHeight = 40.sp),
    headlineMedium = TextStyle(fontFamily = Vazirmatn, fontWeight = FontWeight.SemiBold, fontSize = 26.sp, lineHeight = 36.sp),
    headlineSmall = TextStyle(fontFamily = Vazirmatn, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 30.sp),
    titleLarge = TextStyle(fontFamily = Vazirmatn, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontFamily = Vazirmatn, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 24.sp),
    titleSmall = TextStyle(fontFamily = Vazirmatn, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontFamily = Vazirmatn, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 26.sp),
    bodyMedium = TextStyle(fontFamily = Vazirmatn, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 22.sp),
    bodySmall = TextStyle(fontFamily = Vazirmatn, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontFamily = Vazirmatn, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontFamily = Vazirmatn, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
    labelSmall = TextStyle(fontFamily = Vazirmatn, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 16.sp)
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
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
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
