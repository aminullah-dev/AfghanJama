package com.afghanjama.util

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.pdf.PdfRenderer
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

/**
 * چاپ و خروجیِ تصویری از یک برگهٔ PDF که قبلاً ساخته شده.
 *
 * چاپ از خودِ اندروید می‌آید (`PrintManager`)، پس هر پرینتری که روی گوشی
 * نصب باشد — از جمله پرینترِ حرارتیِ ۸ سانتی — بدونِ کتابخانهٔ اضافه کار
 * می‌کند و «ذخیره در PDF» هم مجانی به دست می‌آید.
 */
object PrintKit {

    /**
     * بودجهٔ حافظه برای تبدیل به تصویر: حدودِ ۸ میلیون پیکسل ≈ ۳۲ مگابایت.
     * روی گوشیِ ارزانِ کارگاه بیشتر از این یعنی OutOfMemory وسطِ کار.
     */
    private const val PIXEL_BUDGET = 8_000_000L

    /** بزرگ‌نماییِ دلخواه: PDF ‏۷۲dpi است و ۲٫۵ برابر یعنی ~۱۸۰dpi. */
    private const val WANT_SCALE = 2.5f

    /** ارسالِ برگه به صفِ چاپِ اندروید. */
    fun print(context: Context, file: File, jobName: String): Boolean {
        val activity = context as? Activity ?: return false
        val service = activity.getSystemService(Context.PRINT_SERVICE) as? PrintManager
            ?: return false
        return runCatching {
            service.print(
                jobName,
                FilePrintAdapter(file, jobName),
                PrintAttributes.Builder().build()
            )
            true
        }.getOrDefault(false)
    }

    /**
     * تبدیلِ برگه به یک تصویرِ JPEG.
     *
     * صفحه‌ها زیرِ هم می‌چسبند تا یک فاکتورِ دو صفحه‌ای در واتساپ یک عکس
     * باشد نه دو تا. مقیاس طوری انتخاب می‌شود که مجموعِ پیکسل‌ها از بودجه
     * بیرون نزند — نه اینکه امیدوار باشیم برگه کوچک است.
     */
    fun toImage(file: File, target: File): Boolean = runCatching {
        val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = PdfRenderer(pfd)
        var bmp: Bitmap? = null
        try {
            val count = renderer.pageCount
            if (count <= 0) return false

            // اندازهٔ خامِ صفحه‌ها اول خوانده می‌شود تا مقیاس معلوم شود
            var maxW = 0
            var totalH = 0
            for (i in 0 until count) {
                val page = renderer.openPage(i)
                try {
                    if (page.width > maxW) maxW = page.width
                    totalH += page.height
                } finally {
                    page.close()
                }
            }
            val scale = fitScale(maxW, totalH)
            val outW = (maxW * scale).toInt().coerceAtLeast(1)
            val outH = (totalH * scale).toInt().coerceAtLeast(1)

            val canvasBmp = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
            bmp = canvasBmp
            // زمینهٔ سفید: PDF زمینهٔ شفاف دارد و JPEG شفافیت ندارد، پس
            // بدونِ این، برگه سیاه از آب درمی‌آید.
            Canvas(canvasBmp).drawColor(Color.WHITE)

            // مستقیم داخلِ همین تصویر رسم می‌شود، نه اول در یک تصویرِ
            // جداگانه و بعد کپی — آن راه اوجِ حافظه را دو برابر می‌کرد.
            var top = 0
            for (i in 0 until count) {
                val page = renderer.openPage(i)
                try {
                    val h = (page.height * scale).toInt().coerceAtLeast(1)
                    val w = (page.width * scale).toInt().coerceAtLeast(1)
                    val m = Matrix().apply {
                        setScale(scale, scale)
                        postTranslate(0f, top.toFloat())
                    }
                    page.render(
                        canvasBmp,
                        Rect(0, top, w, top + h),
                        m,
                        PdfRenderer.Page.RENDER_MODE_FOR_PRINT
                    )
                    top += h
                } finally {
                    page.close()
                }
            }

            target.outputStream().use { out ->
                canvasBmp.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }
        } finally {
            bmp?.recycle()
            renderer.close()
            pfd.close()
        }
        true
    }.getOrDefault(false)

    /**
     * بزرگ‌نمایی‌ای که هم برگه را خوانا کند و هم از بودجهٔ حافظه بیرون
     * نزند. هرگز کوچک‌تر از ۱ نمی‌شود، وگرنه برگهٔ بلند آن‌قدر ریز می‌شود
     * که خواندنش ممکن نیست.
     */
    internal fun fitScale(width: Int, height: Int): Float {
        if (width <= 0 || height <= 0) return 1f
        val area = width.toLong() * height.toLong()
        if (area <= 0L) return 1f
        val maxScale = kotlin.math.sqrt(PIXEL_BUDGET.toDouble() / area).toFloat()
        return WANT_SCALE.coerceAtMost(maxScale).coerceAtLeast(1f)
    }
}

/** آداپتورِ چاپ برای فایلی که از قبل رسم شده — فقط بایت‌ها را رد می‌کند. */
private class FilePrintAdapter(
    private val file: File,
    private val jobName: String
) : PrintDocumentAdapter() {

    override fun onLayout(
        oldAttributes: PrintAttributes?,
        newAttributes: PrintAttributes?,
        cancellationSignal: CancellationSignal?,
        callback: LayoutResultCallback,
        extras: Bundle?
    ) {
        if (cancellationSignal?.isCanceled == true) {
            callback.onLayoutCancelled()
            return
        }
        val info = PrintDocumentInfo.Builder(jobName)
            .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
            .setPageCount(PrintDocumentInfo.PAGE_COUNT_UNKNOWN)
            .build()
        // برگه از قبل رسم شده و با تغییرِ تنظیماتِ چاپ عوض نمی‌شود
        callback.onLayoutFinished(info, false)
    }

    override fun onWrite(
        pages: Array<out PageRange>?,
        destination: ParcelFileDescriptor,
        cancellationSignal: CancellationSignal?,
        callback: WriteResultCallback
    ) {
        try {
            FileInputStream(file).use { input ->
                FileOutputStream(destination.fileDescriptor).use { output ->
                    input.copyTo(output)
                }
            }
            if (cancellationSignal?.isCanceled == true) callback.onWriteCancelled()
            else callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
        } catch (e: Exception) {
            callback.onWriteFailed(e.message)
        }
    }
}
