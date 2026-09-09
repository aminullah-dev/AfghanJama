package com.afghanjama.pdf

import com.afghanjama.data.entities.Document
import com.afghanjama.data.entities.docTypeLabel
import com.afghanjama.ui.format.PersianDate
import com.afghanjama.ui.format.afn

/**
 * رسیدِ رسمیِ یک سندِ مالی — پرداخت، دریافت، کارمزد، حقوق.
 *
 * یک برگه، با جای امضا. مبلغ بالاتر از جزئیات می‌آید چون کسی که برگه را
 * می‌گیرد اول همان را نگاه می‌کند.
 *
 * QR اینجا نیست: کدگذارش هنوز در `:app` است. نسخهٔ اندروید داردش.
 */
fun documentSheet(
    d: Document,
    shop: ShopInfo,
    measurer: TextMeasurer,
    paper: Paper = Paper.A4
): Sheet {
    val b = SheetBuilder(paper)
    val c = DocChrome(b, paper, measurer, shop)
    val title = docTypeLabel(d.type)

    var y = c.header(title, d.number, d.at)

    // مبلغ: پررنگ‌ترین چیزِ یک رسید
    y = c.totalBox("مبلغ", d.amount.afn(), y)

    y = c.section("جزئیات سند", y)
    y = c.kv("شمارهٔ سند", d.number, y)
    y = c.kv("نوع سند", title, y)
    y = c.kv("تاریخ", PersianDate.long(d.at), y)
    if (d.partyName.isNotBlank()) y = c.kv("طرف حساب", d.partyName, y)
    if (d.refId.isNotBlank()) y = c.kv("مرجع", d.refId, y)
    if (d.note.isNotBlank()) y = c.kv("توضیح", d.note, y)

    y = c.rule(y + 6f)
    c.signatures(y + 4f, "مهر و امضای کارگاه", "امضای دریافت‌کننده")

    c.footer()
    return b.build()
}
