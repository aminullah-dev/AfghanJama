package com.afghanjama.desktop.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import com.afghanjama.desktop.pdf.DesktopDocs
import com.afghanjama.platform.FileExport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.awt.Desktop
import java.io.File

/**
 * [FileExport] روی ویندوز.
 *
 * انتخابگرِ فایل عمداً باز نمی‌شود: فایل کنارِ بقیهٔ اسنادِ کارگاه ذخیره
 * و بعد باز می‌شود. کاربرِ کارگاه همهٔ خروجی‌هایش را یک جا پیدا می‌کند و
 * لازم نیست هر بار مسیر انتخاب کند.
 *
 * `JFileChooser` هم می‌شد ولی یک پنجرهٔ Swing وسطِ برنامهٔ Compose باز
 * می‌کرد که ظاهرش با بقیه یکی نیست.
 */
class DesktopFileExport : FileExport {

    @Composable
    override fun rememberTextSaver(mime: String, text: suspend () -> String): (String) -> Unit {
        val scope = rememberCoroutineScope()
        return { suggested ->
            scope.launch(Dispatchers.IO) {
                runCatching {
                    val target = File(DesktopDocs.outputDir(), safeName(suggested))
                    // BOM، به همان دلیلِ اندروید: اکسلِ ویندوز بدونش
                    // فارسی را جویده نشان می‌دهد.
                    target.outputStream().use { out ->
                        out.write(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()))
                        out.writer(Charsets.UTF_8).use { it.write(text()) }
                    }
                    if (Desktop.isDesktopSupported()) Desktop.getDesktop().open(target)
                }
            }
            Unit
        }
    }

    private fun safeName(raw: String): String =
        raw.replace(Regex("""[\\/:*?"<>|]"""), "-").ifBlank { "gozaresh.csv" }
}
