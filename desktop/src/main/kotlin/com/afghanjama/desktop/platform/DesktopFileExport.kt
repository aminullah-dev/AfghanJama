package com.afghanjama.desktop.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import com.afghanjama.desktop.DeskNotices
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
 *
 * **شکست گفته می‌شود.** تا ۱.۱۰ هر خطا (دیسکِ پر، پوشهٔ بی‌اجازه، فایلی که
 * اکسل قفلش کرده) بی‌صدا خورده می‌شد و دکمه انگار کاری نکرده بود.
 */
class DesktopFileExport : FileExport {

    @Composable
    override fun rememberTextSaver(mime: String, text: suspend () -> String): (String) -> Unit {
        val scope = rememberCoroutineScope()
        return { suggested ->
            scope.launch(Dispatchers.IO) {
                val target = runCatching {
                    val body = text()
                    File(DesktopDocs.outputDir(), safeName(suggested)).also { writeText(it, body) }
                }.getOrElse {
                    DeskNotices.show("خروجی ساخته نشد: ${it.message ?: it::class.simpleName}")
                    return@launch
                }
                val opened = runCatching {
                    if (!Desktop.isDesktopSupported()) error("no desktop")
                    Desktop.getDesktop().open(target)
                }
                if (opened.isFailure) {
                    DeskNotices.show("خروجی ساخته شد ولی باز نشد. فایل اینجاست:", ltr = target.absolutePath)
                }
            }
            Unit
        }
    }

    private fun safeName(raw: String): String =
        raw.replace(Regex("""[\\/:*?"<>|]"""), "-").ifBlank { "gozaresh.csv" }

    companion object {
        /** سه بایتِ نشانهٔ UTF-8 (BOM). */
        internal val UTF8_BOM = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())

        /**
         * متن با BOM، به همان دلیلِ اندروید: اکسلِ ویندوز بدونش فارسی را
         * جویده نشان می‌دهد. جدا شده تا `deskSmoke` همین را بسنجد.
         */
        internal fun writeText(target: File, text: String) {
            target.outputStream().use { out ->
                out.write(UTF8_BOM)
                out.writer(Charsets.UTF_8).use { it.write(text) }
            }
        }
    }
}
