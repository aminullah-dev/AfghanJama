package com.afghanjama.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
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
// هر نقشی که در اپ استفاده می‌شود اینجا **صریح** تعریف شده. نقشِ
// تعریف‌نشده به پالتِ پیش‌فرضِ متریال برمی‌گردد که رنگ‌های خودش را
// دارد و به این پالت ربطی ندارد — همان اتفاقی که برای
// `tertiaryContainer` افتاده بود: در اپی با پالتِ سبز و برنزی، بنرِ
// «مرکز هشدار» صورتیِ پیش‌فرضِ متریال (#FFD8E4) درمی‌آمد.
//
// طبقه‌های `surfaceContainer*` هم تعریف شده‌اند تا عمقِ صفحه با
// اختلافِ روشناییِ ملایم ساخته شود، نه با سایه و خط.
// ======================================================
private val LightColors = lightColorScheme(
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

private val DarkColors = darkColorScheme(
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
