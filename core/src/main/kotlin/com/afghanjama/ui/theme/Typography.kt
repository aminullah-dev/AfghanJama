package com.afghanjama.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * مقیاسِ قلمِ خیاط‌یار — **یک تعریف برای هر دو سکو**.
 *
 * **چرا آمد اینجا.** مقیاس در تمِ اندروید بود و پنجرهٔ ویندوز
 * `Typography()`ِ پیش‌فرضِ متریال را می‌گرفت و فقط قلمش را عوض می‌کرد.
 * پس همان صفحه با همان کد دو اندازه داشت: `titleLarge` روی گوشی ۲۰ و
 * روی پی‌سی ۲۲، `bodyLarge` با ارتفاعِ خطِ ۲۶ و ۲۴.
 *
 * دقیقاً همان چیزی که سرِ رنگ افتاده بود — چهارده نقشِ رنگ روی ویندوز
 * از پالتِ بنفشِ متریال می‌آمد — و با `Palette` جمع شد. اینجا هم همان
 * درمان.
 *
 * **ارتفاعِ خط برای فارسی سلیقه نیست.** خطِ فارسی زیر-خط دارد (ج، ح،
 * ر، ی) و اعرابِ گاه‌به‌گاه؛ با ارتفاعِ خطِ لاتین، سطرها به هم می‌چسبند.
 * برای همین `bodyLarge` اینجا ۱۶/۲۶ است نه ۱۶/۲۴ی متریال.
 *
 * [family] پارامتر است چون هر سکو قلم را جور دیگری بار می‌کند: اندروید
 * از `res/font`، ویندوز از فایلِ کنارِ برنامه. اندازه‌ها یکی‌اند، راهِ
 * رسیدن به قلم نه.
 */
fun appTypography(family: FontFamily): Typography = Typography(
    displayLarge = TextStyle(fontFamily = family, fontWeight = FontWeight.Bold, fontSize = 48.sp),
    displayMedium = TextStyle(fontFamily = family, fontWeight = FontWeight.Bold, fontSize = 40.sp),
    displaySmall = TextStyle(fontFamily = family, fontWeight = FontWeight.Bold, fontSize = 34.sp),
    headlineLarge = TextStyle(fontFamily = family, fontWeight = FontWeight.Bold, fontSize = 30.sp, lineHeight = 40.sp),
    headlineMedium = TextStyle(fontFamily = family, fontWeight = FontWeight.SemiBold, fontSize = 26.sp, lineHeight = 36.sp),
    headlineSmall = TextStyle(fontFamily = family, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 30.sp),
    titleLarge = TextStyle(fontFamily = family, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontFamily = family, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 24.sp),
    titleSmall = TextStyle(fontFamily = family, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontFamily = family, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 26.sp),
    bodyMedium = TextStyle(fontFamily = family, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 22.sp),
    bodySmall = TextStyle(fontFamily = family, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontFamily = family, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontFamily = family, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
    labelSmall = TextStyle(fontFamily = family, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 16.sp)
)
