package com.afghanjama.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import com.afghanjama.util.QrGen

/**
 * نشانِ QRِ اندروید — همان چیزی که تا امروز داخلِ صفحهٔ اسناد بود.
 *
 * جدا شد چون آن صفحه به `:core` رفت تا روی ویندوز هم باز شود، و
 * ساختِ تصویر تنها تکه‌ای بود که پرتابل نیست: `QrGen` روی
 * `android.graphics.Bitmap` می‌نشیند.
 *
 * اگر چیزی ساخته نشد چیزی هم کشیده نمی‌شود — رسید بی QR کارش را
 * می‌کند، ولی جای خالیِ ۱۲۰ نقطه‌ای بدتر از نبودنش است.
 */
@Composable
fun QrBadge(content: String, size: androidx.compose.ui.unit.Dp = 120.dp) {
    val bmp = remember(content) { QrGen.bitmap(content) } ?: return
    Image(
        bitmap = bmp.asImageBitmap(),
        contentDescription = "QR",
        modifier = Modifier.size(size)
    )
}
