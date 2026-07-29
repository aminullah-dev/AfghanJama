package com.afghanjama.pdf

import android.content.Context
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import com.afghanjama.ui.format.PersianDate
import com.afghanjama.ui.format.afn
import com.afghanjama.util.QrGen
import com.afghanjama.util.ShareUtil
import java.io.File

/** دادهٔ یک رسیدِ پول — پرداخت یا دریافت. */
data class ReceiptData(
    val title: String,
    val number: String,
    val at: Long,
    /** کسی که پول را داده. */
    val payer: String,
    /** کسی که پول را گرفته. */
    val payee: String,
    val amount: Long,
    /** ماندهٔ حساب بعد از این رسید؛ صفر یعنی تسویه. */
    val remainingDue: Long = 0,
    val note: String = ""
)

/**
 * رسیدِ پول — همان برگهٔ کوچکی که در کارگاه دستِ گیرنده می‌ماند.
 *
 * روی رولِ ۸ سانتی طراحی شده چون پرینترِ حرارتی ارزان‌تر و سریع‌تر است و
 * برای رسید کاغذِ A4 حیف است؛ ولی روی A4 و A5 هم درست درمی‌آید.
 */
object ReceiptPdf {

    fun create(
        context: Context,
        data: ReceiptData,
        paper: Paper = Paper.ROLL80,
        fileName: String = "${data.number}.pdf"
    ): File {
        val f = PdfKit.fonts(context)
        val doc = PdfDocument()
        val page = doc.startPage(PdfDocument.PageInfo.Builder(paper.w, paper.h, 1).create())
        val c = page.canvas

        var y = PdfKit.drawHeader(c, context, f, data.title, data.number, data.at, paper)

        // مبلغ پررنگ‌ترین چیزِ یک رسید است — کسی که برگه را می‌گیرد
        // اول همین را نگاه می‌کند
        y = PdfKit.totalBox(c, "مبلغ", data.amount.afn(), y, f, paper)

        y = PdfKit.kv(c, "پرداخت‌کننده", data.payer.ifBlank { "—" }, y, f, paper = paper)
        y = PdfKit.kv(c, "دریافت‌کننده", data.payee.ifBlank { "—" }, y, f, paper = paper)
        y = PdfKit.kv(c, "تاریخ پرداخت", PersianDate.long(data.at), y, f, paper = paper)
        if (data.remainingDue != 0L) {
            y = PdfKit.kv(
                c,
                if (data.remainingDue > 0) "طلب کلی" else "پیش‌پرداختِ باقی‌مانده",
                kotlin.math.abs(data.remainingDue).afn(),
                y, f, danger = data.remainingDue > 0, paper = paper
            )
        }
        if (data.note.isNotBlank()) {
            y = PdfKit.kv(c, "توضیح", data.note, y, f, paper = paper)
        }

        y = PdfKit.rule(c, y + 6f, paper)
        y = PdfKit.signatures(
            c, y + 2f, f, "مهر و امضای دریافت‌کننده", "مهر و امضای پرداخت‌کننده", paper
        )

        // QR برای بررسیِ اصالت — همان چیزی که رسیدهای دیگرِ اپ هم دارند
        QrGen.bitmap("AJ|${data.number}|${data.title}|${data.amount}|${data.payer}")?.let { qr ->
            val s = if (paper.narrow) 78f else 120f
            if (y + s + 24f <= paper.bodyBottom) {
                y += 10f
                val left = (paper.w - s) / 2f
                c.drawBitmap(qr, null, RectF(left, y, left + s, y + s), null)
                y += s + 4f
                PdfKit.rtlCenter(
                    c, "برای بررسیِ اصالت اسکن کنید", paper.margin, y,
                    PdfKit.paint(if (paper.narrow) 6.5f else 8.5f, PdfKit.MUTED, f.regular),
                    paper.contentW
                )
            }
        }

        PdfKit.drawFooter(c, context, f, 1, paper = paper)
        doc.finishPage(page)

        val file = File(ShareUtil.sharedDir(context), fileName)
        file.outputStream().use { doc.writeTo(it) }
        doc.close()
        return file
    }
}
