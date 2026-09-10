package com.afghanjama.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * بیرون‌دادنِ یک فایلِ متنی (فعلاً CSVِ گزارش‌ها).
 *
 * **چرا `@Composable` و نه یک `suspend fun` ساده:** روی اندروید کاربر
 * خودش جای ذخیره را انتخاب می‌کند، و آن انتخابگر
 * (`rememberLauncherForActivityResult`) باید **درونِ ترکیب** ثبت شود؛
 * از یک تابعِ معمولی نمی‌شود بازش کرد.
 *
 * می‌شد به‌جایش فایل را بی‌پرسش در پوشهٔ Downloads نوشت — کوتاه‌تر بود و
 * یک `suspend fun` کافی می‌شد. ولی آن‌وقت رفتارِ اپِ تحویل‌شده عوض
 * می‌شد: کاربری که امروز جای ذخیره را انتخاب می‌کند، فردا فایلش را
 * جای دیگری پیدا می‌کرد. مرز به شکلِ سکو خم شد، نه برعکس.
 */
interface FileExport {

    /**
     * یک «ذخیره‌کننده» می‌سازد و برمی‌گرداند.
     *
     * تابعِ برگشتی با **نامِ پیشنهادیِ فایل** صدا زده می‌شود — روی
     * اندروید همان نامی که در انتخابگر از پیش پر می‌شود.
     *
     * [text] تنبل است و فقط وقتی صدا زده می‌شود که کاربر واقعاً جایی را
     * انتخاب کرده باشد: ساختنِ CSVِ گزارش کار دارد و نباید با هر
     * بازترکیب انجام شود.
     */
    @Composable
    fun rememberTextSaver(mime: String, text: suspend () -> String): (String) -> Unit
}

val LocalFileExport = staticCompositionLocalOf<FileExport> {
    error(
        "LocalFileExport داده نشده. ریشهٔ هر سکو باید با " +
            "CompositionLocalProvider(LocalFileExport provides …) پیچیده شود."
    )
}
