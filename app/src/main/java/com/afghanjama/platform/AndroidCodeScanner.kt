package com.afghanjama.platform

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions

/**
 * اسکنِ QR روی اندروید — همان کدی که تا دیروز داخلِ `CuttingScreen`
 * بود، بی هیچ تغییرِ رفتاری.
 *
 * **اجازهٔ دوربین عمداً از اینجا درخواست نمی‌شود.** `MainActivity` یک
 * `FragmentActivity` است و نسخهٔ fragment ای که `androidx.biometric`
 * می‌آورد، requestCodeهای بزرگ‌تر از ۱۶ بیت را رد می‌کند — در حالی که
 * `ActivityResultRegistry` عمداً کدِ بزرگ می‌سازد. پس هر
 * `launch(RequestPermission())` از این اکتیویتی با
 * «Can only use lower 16 bits for requestCode» می‌ترکد.
 *
 * خودِ `CaptureActivity` کتابخانهٔ اسکنر (که `FragmentActivity` نیست)
 * اجازه را با کدِ کوچکِ خودش می‌گیرد، پس فقط اسکنر باز می‌شود و
 * نتیجه — چه موفق چه ناموفق — به کاربر گفته می‌شود.
 */
object AndroidCodeScanner : CodeScanner {

    @Composable
    override fun rememberStart(
        prompt: String,
        onCode: (String) -> Unit,
        onFailed: (String) -> Unit,
    ): () -> Unit {
        val context = LocalContext.current
        val launcher = rememberLauncherForActivityResult(ScanContract()) { result ->
            // نتیجهٔ خالی یعنی کاربر لغو کرده یا دوربین بالا نیامده —
            // قبلاً بی‌صدا نادیده گرفته می‌شد و اسکنر «کار نمی‌کرد»
            // بدون هیچ پیغامی.
            val code = result.contents
            if (code.isNullOrBlank()) onFailed(cameraMessage(context)) else onCode(code)
        }
        return remember(launcher, prompt) {
            {
                runCatching { launcher.launch(options(prompt)) }
                    .onFailure { onFailed("اسکنر باز نشد؛ کد را دستی وارد کنید.") }
                Unit
            }
        }
    }

    private fun options(prompt: String) = ScanOptions().apply {
        setPrompt(prompt)
        setBeepEnabled(true)
        setOrientationLocked(true)
        setDesiredBarcodeFormats(ScanOptions.QR_CODE)
    }

    private fun cameraMessage(context: Context): String {
        val ok = ContextCompat.checkSelfPermission(
            context, Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        return if (!ok)
            "اجازهٔ دوربین داده نشده است. از تنظیماتِ گوشی اجازهٔ دوربین را بدهید، یا کد را دستی وارد کنید."
        else
            "اسکن انجام نشد. می‌توانید کد را دستی وارد کنید."
    }
}
