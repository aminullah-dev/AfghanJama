package com.afghanjama.util

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

/**
 * اشتراک فایل از cache/shared از طریق FileProvider —
 * برای فاکتور PDF و ارسال بکاپ به Drive/واتساپ.
 */
object ShareUtil {

    /** پوشهٔ فایل‌های قابل اشتراک را (تمیزشده) برمی‌گرداند. */
    fun sharedDir(context: Context): File =
        File(context.cacheDir, "shared").apply { mkdirs() }

    /**
     * Activityِ پشتِ یک Context — یا `null` اگر این Context اصلاً از
     * Activity نیامده باشد.
     *
     * `LocalContext.current` در Compose معمولاً خودِ Activity نیست بلکه
     * یک `ContextThemeWrapper` دورِ آن است، پس `context as? Activity`
     * جواب نمی‌دهد و باید زنجیره باز شود.
     */
    private fun Context.activityOrNull(): Activity? {
        var c: Context? = this
        while (c is ContextWrapper) {
            if (c is Activity) return c
            c = c.baseContext
        }
        return null
    }

    /**
     * پنجرهٔ انتخابِ برنامه را باز می‌کند — از هر Contextی، بی‌آنکه
     * اپ بسته شود.
     *
     * **خرابی‌ای که این تابع را ساخت.** «اشتراکِ گزارشِ مالی» اپ را
     * می‌بست با این پیام:
     *
     *     Calling startActivity() from outside of an Activity context
     *     requires the FLAG_ACTIVITY_NEW_TASK flag.
     *
     * چون `AndroidDocs` با `applicationContext` ساخته می‌شود، نه با
     * Activity. `AndroidSystemActions` این را می‌دانست و پرچم را
     * می‌گذاشت — ولی مسیرِ اشتراکِ **فایل** از آنجا رد نمی‌شد و همان
     * درس به آن نرسیده بود. یک درس، دو مسیر، فقط یکی یاد گرفته بود.
     *
     * پرچم **فقط وقتی** اضافه می‌شود که Activityای در کار نباشد:
     * گذاشتنِ همیشگی‌اش پنجره را در یک وظیفهٔ جدا باز می‌کند و دکمهٔ
     * برگشت دیگر به همان صفحه برنمی‌گردد.
     *
     * `runCatching` هم لازم است: گوشی‌ای که هیچ برنامه‌ای برای این
     * نوعِ محتوا ندارد `ActivityNotFoundException` می‌دهد. نبودنِ
     * واتس‌اپ نباید دفترِ کارگاه را ببندد.
     */
    fun launchChooser(context: Context, intent: Intent, chooserTitle: String): Boolean =
        launch(context, Intent.createChooser(intent, chooserTitle))

    /**
     * همان، ولی بدونِ پنجرهٔ انتخاب — برای Intentی که خودش مقصد دارد،
     * مثلِ شماره‌گیر.
     *
     * **تنها جایی در کلِ `:app` که `startActivity` صدا زده می‌شود.**
     * بررسیِ `newtask` این را نگه می‌دارد؛ اگر جای دیگری مستقیم صدا
     * بزند، همان خرابیِ «گزارشِ مالی» دوباره پیش می‌آید.
     */
    fun launch(context: Context, intent: Intent): Boolean {
        if (context.activityOrNull() == null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching { context.startActivity(intent) }.isSuccess
    }

    fun shareFile(context: Context, file: File, mime: String, chooserTitle: String): Boolean {
        val uri = FileProvider.getUriForFile(
            context,
            context.packageName + ".fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return launchChooser(context, intent, chooserTitle)
    }

    /** اشتراکِ متنِ ساده — رسید، گزارش، سند. */
    fun shareText(
        context: Context,
        text: String,
        chooserTitle: String,
        subject: String = ""
    ): Boolean {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            if (subject.isNotBlank()) putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, text)
        }
        return launchChooser(context, intent, chooserTitle)
    }
}
