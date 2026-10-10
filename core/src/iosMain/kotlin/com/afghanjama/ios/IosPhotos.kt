package com.afghanjama.ios

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.ImageBitmap
import com.afghanjama.platform.FileExport
import com.afghanjama.platform.Photos
import kotlinx.coroutines.launch

/**
 * [Photos] روی آیفون — **هنوز عکسی ذخیره نمی‌کند**.
 *
 * **چرا لازم شد.** `IosApp` این را فراهم نمی‌کرد و `LocalPhotos` بی
 * فراهم‌کننده خطا می‌دهد؛ پس باز کردنِ تبِ فروش (انبارِ محصول)، جزئیاتِ
 * سفارش و بُرش روی آیفون سرِ باز شدن می‌ترکید. کامپایل این را نمی‌بیند.
 *
 * دوربین `null` است (دکمه‌اش ساخته نمی‌شود). انتخابگر فعلاً کاری
 * نمی‌کند؛ نامِ عکس‌های ثبت‌شده در دفتر هم نشان داده نمی‌شود
 * ([rememberThumb] برابرِ `null` است و صفحه جای خالی می‌گذارد).
 */
object IosPhotos : Photos {

    override fun delete(name: String) = Unit

    override fun exists(name: String): Boolean = false

    @Composable
    override fun rememberThumb(name: String, maxSide: Int): ImageBitmap? = null

    @Composable
    override fun rememberCapture(onCaptured: (String) -> Unit): (() -> Unit)? = null

    @Composable
    override fun rememberPicker(onPicked: (String) -> Unit): () -> Unit = remember { {} }
}

/**
 * [FileExport] روی آیفون — متنِ CSV را با برگهٔ اشتراکِ سیستم می‌دهد
 * (ذخیره در Files، پیام، ایمیل…). بی این، صفحهٔ گزارش‌ها سرِ باز شدن
 * می‌ترکید.
 */
object IosFileExport : FileExport {

    @Composable
    override fun rememberTextSaver(mime: String, text: suspend () -> String): (String) -> Unit =
        rememberCoroutineScope().let { scope ->
            { suggested: String ->
                scope.launch { IosSystemActions.shareText(suggested, text()) }
                Unit
            }
        }
}
