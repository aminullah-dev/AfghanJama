package com.afghanjama.pdf

import com.afghanjama.ui.format.PersianDate
import com.afghanjama.ui.format.afn
import kotlin.math.abs

/**
 * چیدمانِ رسیدِ پول — همان برگهٔ کوچکی که در کارگاه دستِ گیرنده می‌ماند.
 *
 * این اولین سندی است که از `android.graphics` جدا شد. خروجی‌اش یک
 * [Sheet] است: نه روی گوشی می‌کشد نه روی پی‌سی، فقط می‌گوید **چه چیزی
 * کجا برود**. هر سکو همین را با ابزارِ خودش می‌کشد.
 *
 * ترتیب و اندازه‌ها از `ReceiptPdf`ِ اندروید برداشته شده‌اند تا برگه
 * همان چیزی بماند که کارگاه می‌شناسد.
 *
 * **QR هنوز اینجا نیست.** نسخهٔ اندروید یک کدِ QR برای بررسیِ اصالت
 * می‌گذارد؛ ساختنش به یک کدگذارِ مشترک نیاز دارد که هنوز در `:core`
 * نیست. تا آن وقت، نسخهٔ اندروید QR دارد و این یکی نه — و همین در سند
 * علامت خورده تا فراموش نشود.
 */
fun receiptSheet(
    data: ReceiptData,
    shop: ShopInfo,
    measurer: TextMeasurer,
    paper: Paper = Paper.ROLL80
): Sheet {
    val b = SheetBuilder(paper)
    val c = DocChrome(b, paper, measurer, shop)

    var y = c.header(data.title, data.number, data.at)

    // مبلغ پررنگ‌ترین چیزِ یک رسید است — کسی که برگه را می‌گیرد اول
    // همین را نگاه می‌کند.
    y = c.totalBox("مبلغ", data.amount.afn(), y)

    y = c.kv("پرداخت‌کننده", data.payer.ifBlank { "—" }, y)
    y = c.kv("دریافت‌کننده", data.payee.ifBlank { "—" }, y)
    y = c.kv("تاریخ پرداخت", PersianDate.long(data.at), y)

    if (data.remainingDue != 0L) {
        y = c.kv(
            if (data.remainingDue > 0) "طلب کلی" else "پیش‌پرداختِ باقی‌مانده",
            abs(data.remainingDue).afn(),
            y,
            danger = data.remainingDue > 0
        )
    }
    if (data.note.isNotBlank()) {
        y = c.kv("توضیح", data.note, y)
    }

    y = c.rule(y + 6f)
    c.signatures(y + 2f, "مهر و امضای دریافت‌کننده", "مهر و امضای پرداخت‌کننده")

    c.footer()
    return b.build()
}
