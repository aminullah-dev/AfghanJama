package com.afghanjama.pdf

import com.afghanjama.data.entities.LedgerEntry
import com.afghanjama.data.entities.ledgerRefLabel
import com.afghanjama.data.entities.partyTypeLabel
import com.afghanjama.ui.format.PersianDate
import com.afghanjama.ui.format.afn

/**
 * صورت‌حسابِ یک طرفِ دفتر کل — مشتری، خیاط یا فروشنده.
 *
 * چندبرگه‌ای است و ماندهٔ نهایی در بالای برگهٔ اول می‌آید نه پایینِ
 * آخری: طرفِ حساب اول می‌خواهد بداند بدهکار است یا بستانکار.
 */
fun partyStatementSheets(
    partyType: String,
    partyName: String,
    net: Long,
    entries: List<LedgerEntry>,
    shop: ShopInfo,
    measurer: TextMeasurer,
    paper: Paper = Paper.A4
): SheetDoc {
    val pages = mutableListOf<Sheet>()
    var b = SheetBuilder(paper)
    var c = DocChrome(b, paper, measurer, shop)
    var pageNo = 1
    val title = "صورت‌حساب ${partyTypeLabel(partyType)}"
    var y = c.header(title, partyName, System.currentTimeMillis())

    fun breakIfNeeded(needed: Float) {
        if (y + needed <= paper.bodyBottom) return
        c.footer(pageNo)
        pages += b.build()
        pageNo++
        b = SheetBuilder(paper)
        c = DocChrome(b, paper, measurer, shop)
        y = c.header("$title (ادامه)", partyName, System.currentTimeMillis())
    }

    // ---------- مانده ----------
    val balanceLabel = when {
        net > 0 -> "بدهکار — به ما بدهکار است"
        net < 0 -> "بستانکار — ما به او بدهکاریم"
        else -> "تسویه‌شده"
    }
    y = c.totalBox(balanceLabel, (if (net < 0) -net else net).afn(), y)

    y = c.section("گردش حساب", y)
    val w = listOf(1.6f, 2.4f, 1.6f, 1.6f)
    y = c.tableHeader(listOf("تاریخ", "شرح", "بدهکار", "بستانکار"), w, y)

    if (entries.isEmpty()) {
        y = c.tableRow(listOf("—", "سندی ثبت نشده است", "—", "—"), w, y)
    }
    entries.forEachIndexed { i, e ->
        breakIfNeeded(26f)
        val desc = ledgerRefLabel(e.refType) +
            (if (e.refId.isNotBlank()) " (${e.refId})" else "")
        y = c.tableRow(
            listOf(
                PersianDate.short(e.at),
                desc,
                if (e.debit > 0) e.debit.afn() else "—",
                if (e.credit > 0) e.credit.afn() else "—"
            ),
            w, y, zebra = i % 2 == 1
        )
        if (e.note.isNotBlank()) {
            breakIfNeeded(18f)
            y = c.note("   ${e.note}", y)
        }
    }

    // ---------- جمع ----------
    breakIfNeeded(60f)
    y = c.rule(y + 6f)
    y = c.kv("جمع بدهکار", entries.sumOf { it.debit }.afn(), y)
    y = c.kv("جمع بستانکار", entries.sumOf { it.credit }.afn(), y)
    y = c.kv("ماندهٔ نهایی", (if (net < 0) -net else net).afn(), y, strong = true)

    breakIfNeeded(70f)
    c.signatures(y + 12f, "مهر و امضای کارگاه", "تأیید طرف حساب")

    c.footer(pageNo)
    pages += b.build()
    return SheetDoc(pages)
}
