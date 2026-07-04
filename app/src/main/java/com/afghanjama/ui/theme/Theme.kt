package com.afghanjama.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
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
    headlineLarge = TextStyle(fontFamily = Vazirmatn, fontWeight = FontWeight.Bold, fontSize = 30.sp),
    headlineMedium = TextStyle(fontFamily = Vazirmatn, fontWeight = FontWeight.SemiBold, fontSize = 26.sp),
    headlineSmall = TextStyle(fontFamily = Vazirmatn, fontWeight = FontWeight.SemiBold, fontSize = 22.sp),
    titleLarge = TextStyle(fontFamily = Vazirmatn, fontWeight = FontWeight.SemiBold, fontSize = 20.sp),
    titleMedium = TextStyle(fontFamily = Vazirmatn, fontWeight = FontWeight.Medium, fontSize = 16.sp),
    titleSmall = TextStyle(fontFamily = Vazirmatn, fontWeight = FontWeight.Medium, fontSize = 14.sp),
    bodyLarge = TextStyle(fontFamily = Vazirmatn, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 26.sp),
    bodyMedium = TextStyle(fontFamily = Vazirmatn, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 22.sp),
    bodySmall = TextStyle(fontFamily = Vazirmatn, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontFamily = Vazirmatn, fontWeight = FontWeight.Medium, fontSize = 14.sp),
    labelMedium = TextStyle(fontFamily = Vazirmatn, fontWeight = FontWeight.Medium, fontSize = 12.sp),
    labelSmall = TextStyle(fontFamily = Vazirmatn, fontWeight = FontWeight.Medium, fontSize = 11.sp)
)

// ======================================================
// پالت مینیمال کارگاهی: سبز عمیق + خنثی‌های گرم
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

    tertiary = Color(0xFF41616D),
    onTertiary = Color(0xFFFFFFFF),

    background = Color(0xFFF8F7F4),        // زمینه گرم و آرام
    onBackground = Color(0xFF1B1C1A),

    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1B1C1A),
    surfaceVariant = Color(0xFFEEECE6),
    onSurfaceVariant = Color(0xFF61605A),

    outline = Color(0xFF8A8983),
    outlineVariant = Color(0xFFE1DFD8),

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

    background = Color(0xFF131513),
    onBackground = Color(0xFFE3E3DE),

    surface = Color(0xFF1B1D1B),
    onSurface = Color(0xFFE3E3DE),
    surfaceVariant = Color(0xFF2A2C29),
    onSurfaceVariant = Color(0xFFC5C4BC),

    outline = Color(0xFF8F8E86),
    outlineVariant = Color(0xFF44463F),

    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFF9DEDC)
)

@Composable
fun AfghanJamaTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    // چیدمان همیشه راست‌به‌چپ، مستقل از زبان دستگاه
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        MaterialTheme(
            colorScheme = if (dark) DarkColors else LightColors,
            typography = AppTypography,
            content = content
        )
    }
}
