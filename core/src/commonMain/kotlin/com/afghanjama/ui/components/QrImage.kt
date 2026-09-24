package com.afghanjama.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.afghanjama.util.QrCode
import kotlin.math.floor

/**
 * QR روی صفحه — برای هر سه سکو، بی ZXing و بی `Bitmap`.
 *
 * رنگ‌ها عمداً از تم نمی‌آیند: QR باید تیره روی روشن باشد، حتی در تمِ
 * تیره. QRِ وارونه را خیلی از دوربین‌ها نمی‌خوانند.
 *
 * اندازهٔ هر خانه به پیکسلِ درست گرد می‌شود؛ خانهٔ ۲٫۳ پیکسلی لبه‌های
 * نیم‌رنگ می‌سازد و دوربین مرزِ خانه‌ها را گم می‌کند.
 *
 * اگر متن در QR جا نشود چیزی کشیده نمی‌شود — کارت بی QR هنوز نام و
 * کدِ نوشتاری دارد.
 */
@Composable
fun QrImage(
    content: String,
    size: Dp = 160.dp,
    modifier: Modifier = Modifier,
) {
    val qr = remember(content) { runCatching { QrCode.encodeText(content) }.getOrNull() } ?: return
    Canvas(
        modifier
            .size(size)
            .semantics { contentDescription = "QR" }
    ) {
        drawRect(Color.White)
        val quiet = 4
        val n = qr.size + quiet * 2
        val cell = floor(this.size.minDimension / n).coerceAtLeast(1f)
        // لبه هم روی پیکسلِ درست؛ جابه‌جاییِ نیم‌پیکسلی همان نیم‌رنگ را برمی‌گرداند.
        val origin = floor((this.size.minDimension - cell * n) / 2f) + quiet * cell
        for (y in 0 until qr.size) {
            var x = 0
            while (x < qr.size) {
                if (!qr.isDark(x, y)) {
                    x++
                    continue
                }
                val start = x
                while (x < qr.size && qr.isDark(x, y)) x++
                drawRect(
                    Color.Black,
                    topLeft = Offset(origin + start * cell, origin + y * cell),
                    size = Size((x - start) * cell, cell),
                )
            }
        }
    }
}
