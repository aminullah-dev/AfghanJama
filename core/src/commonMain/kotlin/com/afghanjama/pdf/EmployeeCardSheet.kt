package com.afghanjama.pdf

import com.afghanjama.ui.format.fa
import com.afghanjama.util.QrCode

/**
 * یک کارتِ کارمند برای چاپ.
 *
 * [code] همان متنی است که QR می‌بَرد (`EmployeeCard.encode`)؛ زیرِ QR هم
 * به خط نوشته می‌شود تا اگر دوربین نخواند، مدیر بتواند تایپش کند.
 */
data class EmployeeCardInfo(
    val name: String,
    val kindLabel: String,
    val detail: String,
    val code: String,
)

/**
 * QR را با مستطیل‌های پرشده می‌کشد — نه با تصویر.
 *
 * [DrawOp.Image] بایتِ PNG می‌خواهد و ساختنِ PNG روی هر سکو کدِ جدا
 * دارد. مستطیل را هر دو رسام (`Canvas`ِ اندروید و PDFBoxِ ویندوز) از
 * قبل می‌کشند، و QRِ برداری روی هر چاپگری تیز می‌ماند.
 *
 * خانه‌های تیرهٔ پشتِ‌سرِ‌همِ هر ردیف یک مستطیل می‌شوند: هم دستورِ کمتر،
 * هم بی درزِ مویی بینِ دو خانه که بعضی نمایشگرهای PDF نشان می‌دهند.
 * همان درز بینِ دو ردیف هم با کمی هم‌پوشانیِ عمودی بسته می‌شود.
 *
 * چهار خانه حاشیهٔ سفید دورش می‌ماند — حدِ استاندارد؛ کمتر از آن
 * دوربینِ گوشی لبهٔ QR را با نقشِ پشتش قاطی می‌کند.
 */
fun SheetBuilder.qr(content: String, left: Float, top: Float, side: Float) {
    val q = QrCode.encodeText(content)
    val quiet = 4
    val cell = side / (q.size + quiet * 2)
    val overlap = cell * 0.06f
    rect(left, top, left + side, top + side, WHITE)
    for (y in 0 until q.size) {
        var x = 0
        while (x < q.size) {
            if (!q.isDark(x, y)) {
                x++
                continue
            }
            val start = x
            while (x < q.size && q.isDark(x, y)) x++
            val rowTop = top + (y + quiet) * cell
            rect(
                left + (start + quiet) * cell,
                rowTop,
                left + (x + quiet) * cell,
                rowTop + cell + if (y < q.size - 1) overlap else 0f,
                SheetColors.INK,
            )
        }
    }
}

private const val WHITE = 0xFFFFFFFF.toInt()

/** اندازهٔ کارتِ بانکی (CR80) — ۸۵٫۶ × ۵۴ میلی‌متر، به نقطه. */
private const val CARD_W = 243f
private const val CARD_H = 153f
private const val BAND_H = 30f
private const val QR_SIDE = 108f

/**
 * برگه‌های کارتِ کارمندان — هشت کارت روی هر A4، آمادهٔ برش.
 *
 * اندازهٔ کارتِ بانکی است تا در جاکارتیِ معمولی و کیف بنشیند. لبهٔ هر
 * کارت خطِ برش است.
 */
fun employeeCardSheets(
    cards: List<EmployeeCardInfo>,
    shopName: String,
    m: TextMeasurer,
): SheetDoc {
    require(cards.isNotEmpty()) { "کارتی برای چاپ نیست" }
    val paper = Paper.A4
    val perRow = 2
    val rows = 4
    val colGap = paper.contentW - perRow * CARD_W
    val rowGap = 12f
    val headerTop = paper.margin
    val gridTop = headerTop + 30f

    val pages = cards.chunked(perRow * rows).mapIndexed { pageIndex, pageCards ->
        val b = SheetBuilder(paper)
        b.text(
            "کارت‌های کارمندان — $shopName",
            paper.w - paper.margin, headerTop, 12f,
            SheetColors.INK, Weight.Bold, Align.Start,
        )
        b.text(
            "برگهٔ ${(pageIndex + 1).fa()} — لبهٔ هر کارت خطِ برش است",
            paper.margin, headerTop + 2f, 8f,
            SheetColors.MUTED, Weight.Regular, Align.End,
        )
        pageCards.forEachIndexed { i, card ->
            val col = i % perRow
            val row = i / perRow
            // راست‌به‌چپ: کارتِ اول در ستونِ راست.
            val right = paper.w - paper.margin - col * (CARD_W + colGap)
            val top = gridTop + row * (CARD_H + rowGap)
            drawCard(b, card, right - CARD_W, top, shopName, m)
        }
        b.build()
    }
    return SheetDoc(pages)
}

private fun drawCard(
    b: SheetBuilder,
    card: EmployeeCardInfo,
    left: Float,
    top: Float,
    shopName: String,
    m: TextMeasurer,
) {
    val right = left + CARD_W
    val bottom = top + CARD_H

    // قاب: مستطیلِ خط‌رنگ و روی آن مستطیلِ سفیدِ کمی کوچک‌تر.
    b.rect(left, top, right, bottom, SheetColors.LINE, radius = 8f)
    b.rect(left + 0.8f, top + 0.8f, right - 0.8f, bottom - 0.8f, WHITE, radius = 7.4f)

    // نوارِ بالا — گوشهٔ بالا گرد، پایینش صاف.
    b.rect(left, top, right, top + BAND_H, SheetColors.BRAND, radius = 8f)
    b.rect(left, top + BAND_H / 2f, right, top + BAND_H, SheetColors.BRAND)
    b.text(
        fit(shopName, 10f, CARD_W - 90f, Weight.Bold, m),
        right - 10f, top + 9f, 10f, WHITE, Weight.Bold, Align.Start,
    )
    b.text("کارتِ کارمند", left + 10f, top + 11f, 8f, WHITE, Weight.Regular, Align.End)

    // QR سمتِ چپ؛ متن سمتِ راست.
    val qrTop = top + BAND_H + 8f
    b.qr(card.code, left + 8f, qrTop, QR_SIDE)

    val textRight = right - 10f
    val textW = CARD_W - 10f - (8f + QR_SIDE + 10f)
    var y = top + BAND_H + 12f

    val nameLines = m.wrap(card.name, 13f, textW, Weight.Bold).take(2)
    val nameLh = m.lineHeight(13f, Weight.Bold)
    nameLines.forEachIndexed { i, line ->
        val shown = if (i == 1 && nameLines.size == 2) fit(line, 13f, textW, Weight.Bold, m) else line
        b.text(shown, textRight, y + i * nameLh, 13f, SheetColors.INK, Weight.Bold, Align.Start)
    }
    y += nameLines.size * nameLh + 4f

    b.text(card.kindLabel, textRight, y, 9f, SheetColors.BRAND_DEEP, Weight.Bold, Align.Start)
    y += m.lineHeight(9f, Weight.Bold)
    if (card.detail.isNotBlank()) {
        b.text(fit(card.detail, 9f, textW, Weight.Regular, m), textRight, y, 9f, SheetColors.MUTED)
    }

    b.text("برای ورود و خروج اسکن شود", textRight, bottom - 32f, 7.5f, SheetColors.MUTED)
    b.text(card.code, textRight, bottom - 18f, 8f, SheetColors.INK, Weight.Bold, Align.Start)
}

/** یک سطر که از [width] بیرون نزند — اگر زد، با «…» کوتاه می‌شود. */
private fun fit(text: String, size: Float, width: Float, weight: Weight, m: TextMeasurer): String {
    if (m.width(text, size, weight) <= width) return text
    var t = text
    while (t.isNotEmpty() && m.width("$t…", size, weight) > width) t = t.dropLast(1)
    return "$t…"
}
