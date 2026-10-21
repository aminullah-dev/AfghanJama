package com.afghanjama.ui.theme

import android.provider.Settings
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
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

/**
 * تمِ اپ روی گوشیِ اندروید — همان [AppTheme]ِ مشترک، با سه چیزی که فقط
 * اینجا معلوم است: قلم از `res/font`، حالتِ تاریکِ سیستم، و «کاهشِ حرکت».
 *
 * تا ۱.۱۱ رنگ و قلم از `:core` می‌آمد ولی گوشه‌ها (`Shapes`) فقط اینجا
 * تعریف شده بودند — ۶، ۱۰، ۱۴ — و آیفون و کامپیوتر پیش‌فرضِ متریال را
 * داشتند. حالا هر سه از `AppShapes` می‌خوانند.
 */
@Composable
fun KhayatYarTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
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

    AppTheme(fontFamily = Vazirmatn, dark = dark, reducedMotion = reducedMotion) {
        // اندروید ۱۵ به بعد صفحه را لبه‌به‌لبه می‌کند و دیگر خودش
        // پنجره را برای کیبورد جمع نمی‌کند؛ پس کیبورد روی کادرهای
        // پایینی می‌افتاد و کاربر جایی را که تایپ می‌کرد نمی‌دید.
        // یک جا برای کلِ اپ درست می‌شود، نه در تک‌تکِ ۲۶ صفحه.
        Box(Modifier.fillMaxSize().imePadding()) { content() }
    }
}
