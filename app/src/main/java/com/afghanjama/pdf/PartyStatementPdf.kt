package com.afghanjama.pdf

import android.content.Context
import android.graphics.pdf.PdfDocument
import com.afghanjama.data.entities.LedgerEntry
import com.afghanjama.data.entities.ledgerRefLabel
import com.afghanjama.data.entities.partyTypeLabel
import com.afghanjama.pdf.PdfKit.BODY_BOTTOM
import com.afghanjama.pdf.PdfKit.PAGE_H
import com.afghanjama.pdf.PdfKit.PAGE_W
import com.afghanjama.ui.format.PersianDate
import com.afghanjama.ui.format.afn
import com.afghanjama.util.ShareUtil
import java.io.File

/**
 * صورت‌حسابِ رسمیِ یک طرفِ دفتر کل (برای دادن به مشتری/خیاط/فروشنده):
 * A4 راست‌به‌چپ، چندصفحه‌ای، با سربرگ و پاصفحهٔ مشترکِ افغان‌جامه.
 */
object PartyStatementPdf {

    fun create(
        context: Context,
        partyType: String,
        partyName: String,
        net: Long,
        entries: List<LedgerEntry>
    ): File {
        val f = PdfKit.fonts(context)
        val title = "صورت‌حساب ${partyTypeLabel(partyType)}"

        val doc = PdfDocument()
        var pageNo = 1
        var page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNo).create())
        var c = page.canvas
        var y = PdfKit.drawHeader(c, context, f, title, partyName)

        fun newPageIfNeeded(needed: Float) {
            if (y + needed <= BODY_BOTTOM) return
            PdfKit.drawFooter(c, context, f, pageNo)
            doc.finishPage(page)
            pageNo++
            page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNo).create())
            c = page.canvas
            y = PdfKit.drawHeader(c, context, f, "$title (ادامه)", partyName)
        }

        // ---------- مانده ----------
        val balanceLabel = when {
            net > 0 -> "بدهکار — به ما بدهکار است"
            net < 0 -> "بستانکار — ما به او بدهکاریم"
            else -> "تسویه‌شده"
        }
        y = PdfKit.totalBox(c, balanceLabel, (if (net < 0) -net else net).afn(), y, f)

        y = PdfKit.section(c, "گردش حساب", y, f)
        val w = listOf(1.6f, 2.4f, 1.6f, 1.6f)
        y = PdfKit.tableHeader(c, listOf("تاریخ", "شرح", "بدهکار", "بستانکار"), w, y, f)

        if (entries.isEmpty()) {
            y = PdfKit.tableRow(c, listOf("—", "سندی ثبت نشده است", "—", "—"), w, y, f)
        }

        entries.forEachIndexed { i, e ->
            newPageIfNeeded(26f)
            val desc = ledgerRefLabel(e.refType) +
                (if (e.refId.isNotBlank()) " (${e.refId})" else "")
            y = PdfKit.tableRow(
                c,
                listOf(
                    PersianDate.short(e.at),
                    desc,
                    if (e.debit > 0) e.debit.afn() else "—",
                    if (e.credit > 0) e.credit.afn() else "—"
                ),
                w, y, f, zebra = i % 2 == 1
            )
            if (e.note.isNotBlank()) {
                newPageIfNeeded(18f)
                y = PdfKit.note(c, "   ${e.note}", y, f)
            }
        }

        // ---------- جمع ----------
        newPageIfNeeded(60f)
        y = PdfKit.rule(c, y + 6f)
        y = PdfKit.kv(c, "جمع بدهکار", entries.sumOf { it.debit }.afn(), y, f)
        y = PdfKit.kv(c, "جمع بستانکار", entries.sumOf { it.credit }.afn(), y, f)
        y = PdfKit.kv(c, "ماندهٔ نهایی", (if (net < 0) -net else net).afn(), y, f, strong = true)

        newPageIfNeeded(70f)
        y = PdfKit.signatures(c, y + 12f, f, "مهر و امضای کارگاه", "تأیید طرف حساب")

        PdfKit.drawFooter(c, context, f, pageNo)
        doc.finishPage(page)

        val safe = partyName.replace(Regex("[/\\\\:*?\"<>|]"), "_")
        val file = File(ShareUtil.sharedDir(context), "صورتحساب-$safe.pdf")
        file.outputStream().use { doc.writeTo(it) }
        doc.close()
        return file
    }
}
