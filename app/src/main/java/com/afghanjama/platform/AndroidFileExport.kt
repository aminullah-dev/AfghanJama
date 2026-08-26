package com.afghanjama.platform

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * [FileExport] روی اندروید — همان انتخابگرِ «کجا ذخیره شود» که
 * `ReportsScreen` تا دیروز خودش می‌ساخت.
 */
object AndroidFileExport : FileExport {

    @Composable
    override fun rememberTextSaver(mime: String, text: suspend () -> String): (String) -> Unit {
        val ctx = LocalContext.current
        val scope = rememberCoroutineScope()
        val launcher = rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument(mime)
        ) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            scope.launch(Dispatchers.IO) {
                // خطای نوشتن نباید اپ را بیندازد: کاربر ممکن است جایی را
                // انتخاب کند که دیگر در دسترس نیست (کارتِ درآمده، فضای پر).
                runCatching {
                    ctx.contentResolver.openOutputStream(uri)?.use { out ->
                        // بدونِ BOM، اکسل روی ویندوز فارسی را جویده نشان
                        // می‌دهد — همان کاری که کدِ قبلی هم می‌کرد.
                        out.write(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()))
                        out.writer(Charsets.UTF_8).use { it.write(text()) }
                    }
                }
            }
        }
        return { suggested -> launcher.launch(suggested) }
    }
}
