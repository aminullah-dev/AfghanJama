package com.afghanjama.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

/**
 * عکس‌های سفارش در پوشهٔ خصوصیِ خودِ اپ می‌مانند، نه در گالریِ گوشی.
 *
 * دو دلیل: اجازهٔ حافظه لازم نمی‌شود، و عکسِ نمونهٔ مشتری قاطیِ عکس‌های
 * شخصیِ کسی نمی‌شود.
 *
 * **هر عکس هنگام ذخیره کوچک می‌شود.** عکسِ ۱۲ مگاپیکسلیِ دوربین باز که
 * شود حدود ۵۰ مگابایت حافظه می‌گیرد؛ چند تای‌شان در یک ردیف، گوشیِ
 * ارزانِ کارگاه را می‌خواباند. با ضلعِ حداکثر [MAX_SIDE] هر فایل چند صد
 * کیلوبایت می‌ماند و پشتیبان هم سنگین نمی‌شود.
 */
object PhotoStore {

    private const val DIR = "order_photos"

    /** بلندترین ضلعِ عکسِ ذخیره‌شده. برای دیدنِ طرح و پارچه کاملاً کافی است. */
    const val MAX_SIDE = 1600

    /** بلندترین ضلعِ بندانگشتی در فهرست. */
    const val THUMB_SIDE = 320

    private const val QUALITY = 85

    fun dir(context: Context): File =
        File(context.filesDir, DIR).apply { mkdirs() }

    fun file(context: Context, fileName: String): File = File(dir(context), fileName)

    fun newFileName(): String = "p_${System.currentTimeMillis()}_${(1000..9999).random()}.jpg"

    /** URI قابلِ نوشتن برای اپِ دوربین. */
    fun uriFor(context: Context, fileName: String): Uri = FileProvider.getUriForFile(
        context,
        context.packageName + ".fileprovider",
        file(context, fileName)
    )

    fun delete(context: Context, fileName: String) {
        runCatching { file(context, fileName).delete() }
    }

    fun exists(context: Context, fileName: String): Boolean =
        runCatching { file(context, fileName).let { it.exists() && it.length() > 0 } }
            .getOrDefault(false)

    /**
     * ضریبِ کوچک‌کردنِ توانِ دو که BitmapFactory می‌خواهد.
     * همیشه ≥ ۱ و همیشه توانِ دو، وگرنه اندروید خودش گرد می‌کند.
     */
    fun sampleSize(width: Int, height: Int, maxSide: Int): Int {
        var sample = 1
        var w = width
        var h = height
        while (maxSide > 0 && (w / 2 >= maxSide || h / 2 >= maxSide)) {
            w /= 2; h /= 2; sample *= 2
        }
        return sample
    }

    private fun decodeBounds(read: () -> java.io.InputStream?): Pair<Int, Int>? {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        read()?.use { BitmapFactory.decodeStream(it, null, opts) } ?: return null
        return if (opts.outWidth > 0 && opts.outHeight > 0) opts.outWidth to opts.outHeight else null
    }

    /** خواندنِ عکس با ابعادِ کوچک‌شده — بدونِ بازکردنِ کلِ فایل در حافظه. */
    fun decode(file: File, maxSide: Int): Bitmap? {
        if (!file.exists() || file.length() == 0L) return null
        val (w, h) = decodeBounds { file.inputStream() } ?: return null
        val opts = BitmapFactory.Options().apply { inSampleSize = sampleSize(w, h, maxSide) }
        return runCatching { file.inputStream().use { BitmapFactory.decodeStream(it, null, opts) } }
            .getOrNull()
    }

    /**
     * کوچک‌کردن و فشرده‌کردنِ عکس روی خودش (بعد از دوربین) یا از یک URI
     * (بعد از انتخاب از گالری). `false` یعنی چیزی خوانده نشد.
     */
    fun shrinkInPlace(context: Context, fileName: String): Boolean {
        val f = file(context, fileName)
        val bmp = decode(f, MAX_SIDE) ?: return false
        return runCatching {
            f.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, QUALITY, it) }
            true
        }.getOrDefault(false).also { bmp.recycle() }
    }

    fun copyFrom(context: Context, uri: Uri, fileName: String): Boolean {
        val dst = file(context, fileName)
        val (w, h) = decodeBounds { context.contentResolver.openInputStream(uri) } ?: return false
        val opts = BitmapFactory.Options().apply { inSampleSize = sampleSize(w, h, MAX_SIDE) }
        val bmp = runCatching {
            context.contentResolver.openInputStream(uri)
                ?.use { BitmapFactory.decodeStream(it, null, opts) }
        }.getOrNull() ?: return false
        return runCatching {
            dst.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, QUALITY, it) }
            dst.length() > 0
        }.getOrDefault(false).also { bmp.recycle() }
    }
}
