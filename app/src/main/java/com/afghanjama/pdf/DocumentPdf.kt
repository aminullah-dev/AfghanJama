package com.afghanjama.pdf

import android.content.Context
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import com.afghanjama.data.entities.Document
import com.afghanjama.data.entities.docTypeLabel
import com.afghanjama.pdf.PdfKit.MARGIN
import com.afghanjama.pdf.PdfKit.PAGE_H
import com.afghanjama.pdf.PdfKit.PAGE_W
import com.afghanjama.ui.format.PersianDate
import com.afghanjama.ui.format.afn
import com.afghanjama.util.QrGen
import com.afghanjama.util.ShareUtil
import java.io.File

/**
 * رسیدِ رسمیِ یک سندِ مالی (پرداخت، دریافت، کارمزد، حقوق…) — یک برگه،
 * با سربرگ و پاصفحهٔ مشترکِ افغان‌جامه، جای امضا و QR برای بررسی.
 */
object DocumentPdf {

    fun create(context: Context, d: Document): File {
        val f = PdfKit.fonts(context)
        val title = docTypeLabel(d.type)

        val doc = PdfDocument()
        val page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, 1).create())
        val c = page.canvas

        var y = PdfKit.drawHeader(c, context, f, title, d.number, d.at)

        // ---------- مبلغ: پررنگ‌ترین چیزِ یک رسید ----------
        y = PdfKit.totalBox(c, "مبلغ", d.amount.afn(), y, f)

        // ---------- جزئیات ----------
        y = PdfKit.section(c, "جزئیات سند", y, f)
        y = PdfKit.kv(c, "شمارهٔ سند", d.number, y, f)
        y = PdfKit.kv(c, "نوع سند", title, y, f)
        y = PdfKit.kv(c, "تاریخ", PersianDate.long(d.at), y, f)
        if (d.partyName.isNotBlank()) y = PdfKit.kv(c, "طرف حساب", d.partyName, y, f)
        if (d.refId.isNotBlank()) y = PdfKit.kv(c, "مرجع", d.refId, y, f)
        if (d.note.isNotBlank()) y = PdfKit.kv(c, "توضیح", d.note, y, f)

        y = PdfKit.rule(c, y + 6f)

        // ---------- امضا ----------
        y = PdfKit.signatures(c, y + 4f, f, "مهر و امضای کارگاه", "امضای دریافت‌کننده")

        // ---------- QR برای بررسیِ اصالت ----------
        QrGen.bitmap("AJ|${d.number}|$title|${d.amount}|${d.partyName}")?.let { qr ->
            y += 14f
            val s = 120f
            val left = (PAGE_W - s) / 2f
            c.drawBitmap(qr, null, RectF(left, y, left + s, y + s), null)
            y += s + 6f
            PdfKit.rtlCenter(
                c, "برای بررسیِ اصالت، این کد را اسکن کنید",
                MARGIN, y, PdfKit.paint(8.5f, PdfKit.MUTED, f.regular), PdfKit.CONTENT_W
            )
        }

        PdfKit.drawFooter(c, context, f, 1)
        doc.finishPage(page)

        val file = File(ShareUtil.sharedDir(context), "${d.number}.pdf")
        file.outputStream().use { doc.writeTo(it) }
        doc.close()
        return file
    }
}
