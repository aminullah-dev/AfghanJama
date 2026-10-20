package com.afghanjama.pdf

import com.afghanjama.AppInfo

/**
 * برچسبِ قفسهٔ یک کالای انبارِ محصول، برای چاپ.
 *
 * **چرا لازم شد.** دیالوگِ «برچسبِ قفسه» می‌گفت «این برچسب را چاپ کن و روی
 * قفسه بچسبان» ولی روی هیچ سکویی دکمهٔ چاپ نداشت. حالا همان کد
 * (`StockLabel.encode`) و نامِ کالا روی یک کارتِ اندازهٔ کارتِ بانکی می‌نشیند؛
 * کامپیوتر PDF را باز می‌کند تا چاپ شود، گوشی آن را می‌فرستد.
 *
 * **بها روی برچسب نیست** — همان قاعدهٔ `StockLabel`: مشتری نباید سودِ
 * کارگاه را روی قفسه بخوانَد. فقط کد، که پس از اسکن در خودِ برنامه بها را
 * نشان می‌دهد.
 */
data class StockLabelInfo(
    val name: String,
    /** سایز، اگر کالا سایز دارد؛ خالی یعنی ندارد. */
    val size: String,
    /** همان متنی که QR می‌بَرد؛ زیرش به خط هم نوشته می‌شود. */
    val code: String,
)

/** اندازهٔ کارتِ بانکی (CR80) — همان کارتِ کارمند، تا یک قیچی و یک جا بس باشد. */
private const val LABEL_W = 243f
private const val LABEL_H = 153f
private const val BAND_H = 30f
private const val QR_SIDE = 108f
private const val WHITE = 0xFFFFFFFF.toInt()

/** یک برگهٔ A4 با یک برچسب در گوشهٔ بالای راست؛ لبهٔ کارت خطِ برش است. */
fun stockLabelSheet(label: StockLabelInfo, shopName: String, m: TextMeasurer): SheetDoc {
    val paper = Paper.A4
    val b = SheetBuilder(paper)
    val top = paper.margin + 30f
    b.text(
        "برچسبِ قفسه — $shopName",
        paper.w - paper.margin, paper.margin, 12f,
        SheetColors.INK, Weight.Bold, Align.Start,
    )
    b.text(
        "لبهٔ کارت خطِ برش است",
        paper.margin, paper.margin + 2f, 8f,
        SheetColors.MUTED, Weight.Regular, Align.End,
    )

    val right = paper.w - paper.margin
    val left = right - LABEL_W
    val bottom = top + LABEL_H

    // قاب و نوارِ بالا — همان شکلِ کارتِ کارمند.
    b.rect(left, top, right, bottom, SheetColors.LINE, radius = 8f)
    b.rect(left + 0.8f, top + 0.8f, right - 0.8f, bottom - 0.8f, WHITE, radius = 7.4f)
    b.rect(left, top, right, top + BAND_H, SheetColors.BRAND, radius = 8f)
    b.rect(left, top + BAND_H / 2f, right, top + BAND_H, SheetColors.BRAND)
    b.text(
        fit(shopName, 10f, LABEL_W - 80f, Weight.Bold, m),
        right - 10f, top + 9f, 10f, WHITE, Weight.Bold, Align.Start,
    )
    b.text("برچسبِ کالا", left + 10f, top + 11f, 8f, WHITE, Weight.Regular, Align.End)

    // QR سمتِ چپ؛ نام و سایز سمتِ راست.
    b.qr(label.code, left + 8f, top + BAND_H + 8f, QR_SIDE)

    val textRight = right - 10f
    val textW = LABEL_W - 10f - (8f + QR_SIDE + 10f)
    var y = top + BAND_H + 12f
    val nameLines = m.wrap(label.name, 13f, textW, Weight.Bold).take(2)
    val nameLh = m.lineHeight(13f, Weight.Bold)
    nameLines.forEachIndexed { i, line ->
        val shown = if (i == 1) fit(line, 13f, textW, Weight.Bold, m) else line
        b.text(shown, textRight, y + i * nameLh, 13f, SheetColors.INK, Weight.Bold, Align.Start)
    }
    y += nameLines.size * nameLh + 4f
    if (label.size.isNotBlank()) {
        b.text("سایز ${label.size}", textRight, y, 10f, SheetColors.BRAND_DEEP, Weight.Bold, Align.Start)
    }

    b.text("برای دیدنِ بها در ${AppInfo.NAME} اسکن شود", textRight, bottom - 32f, 7.5f, SheetColors.MUTED)
    b.text(label.code, textRight, bottom - 18f, 8f, SheetColors.INK, Weight.Bold, Align.Start)
    return SheetDoc(listOf(b.build()))
}
