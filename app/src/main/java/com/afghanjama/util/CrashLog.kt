package com.afghanjama.util

import android.app.Application
import android.content.Context
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * وقتی اپ می‌ترکد، **متنِ خطا را نگه می‌دارد** تا دفعهٔ بعد نشان داده شود.
 *
 * **چرا لازم شد:** اپ روی گوشیِ کارگاه سرِ باز شدن کرش کرد. تنها چیزی که
 * تشخیص را از حدس جدا می‌کند متنِ `FATAL EXCEPTION` است، و رسیدن به آن
 * یعنی کابل USB، «اشکال‌زداییِ USB»، نصبِ platform-tools و کار با
 * `adb logcat` — کاری که از کاربرِ یک اپِ کارگاهی خواستنی نیست. بی آن،
 * هر تغییری که بزنیم حدس است، و حدس روی دفترِ مالیِ کارگاه زدن درست
 * نیست.
 *
 * حالا خودِ اپ خطا را روی دیسک می‌نویسد و دفعهٔ بعد که باز شود، پیش از
 * هر چیز نشانش می‌دهد با یک دکمهٔ «فرستادن». یک عکسِ صفحه کافی است.
 *
 * **دو نکته که عمدی‌اند:**
 *  • رفتارِ اندروید عوض نمی‌شود. هندلرِ قبلی صدا زده می‌شود، پس اپ مثلِ
 *    همیشه بسته می‌شود. اینجا خطا **گرفته نمی‌شود**، فقط **ثبت** می‌شود؛
 *    اپی که بعد از خطای ناشناخته به کارش ادامه دهد می‌تواند داده را خراب
 *    بنویسد.
 *  • همه‌چیز در `runCatching` است. نوشتنِ گزارشِ خرابی نباید خودش خرابیِ
 *    تازه بسازد و متنِ اصلی را از بین ببرد.
 */
object CrashLog {

    private const val FILE = "last_crash.txt"

    /** باید **اولین** کار در `App.onCreate` باشد تا همه‌چیز را پوشش دهد. */
    fun install(app: Application) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { write(app, thread, error) }
            // زنجیره را نمی‌شکنیم: بستنِ اپ کارِ اندروید است، نه ما.
            previous?.uncaughtException(thread, error)
        }
    }

    /** متنِ آخرین خرابی، یا `null` اگر خرابی‌ای در کار نبوده. */
    fun pending(context: Context): String? = runCatching {
        val f = file(context)
        if (f.exists() && f.length() > 0) f.readText() else null
    }.getOrNull()

    /** بعد از دیده شدن پاک می‌شود تا هر بار همان یکی را نشان ندهد. */
    fun clear(context: Context) {
        runCatching { file(context).delete() }
    }

    private fun file(context: Context) = File(context.filesDir, FILE)

    private fun write(app: Application, thread: Thread, error: Throwable) {
        val stack = StringWriter().also { error.printStackTrace(PrintWriter(it)) }.toString()

        val version = runCatching {
            val p = app.packageManager.getPackageInfo(app.packageName, 0)
            "${p.versionName} (${p.longVersionCodeCompat()})"
        }.getOrElse { "?" }

        val when_ = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())

        // ترتیب عمدی است: چیزهایی که برای تشخیص لازم‌اند اول می‌آیند، چون
        // اگر کاربر عکسِ صفحه بفرستد شاید فقط بالای متن در کادر جا شود.
        val text = buildString {
            appendLine("خیاط‌یار — گزارشِ خرابی")
            appendLine("زمان: $when_")
            appendLine("نسخهٔ اپ: $version")
            appendLine("گوشی: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("اندروید: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("رشته: ${thread.name}")
            appendLine()
            append(stack)
        }

        file(app).writeText(text)
    }

    private fun android.content.pm.PackageInfo.longVersionCodeCompat(): Long =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) longVersionCode
        else @Suppress("DEPRECATION") versionCode.toLong()
}
