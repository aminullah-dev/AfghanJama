package com.afghanjama.pdf

import com.afghanjama.licence.LicenceGate
import com.afghanjama.licence.LicenceText

/**
 * نشانِ «نسخهٔ آزمایشی — بدون لایسنس» روی هر برگه‌ای که چاپ یا فرستاده
 * می‌شود، تا وقتی کارگاه لایسنس ندارد.
 *
 * **یک جا، برای هر سه سکو.** نشان به خودِ برگه ([Sheet]) افزوده می‌شود،
 * نه به رسامِ هر سکو؛ پس PDFِ گوشی، PDFِ ویندوز/مک، تصویرِ واتساپ (که از
 * همان PDF ساخته می‌شود) و آیفون همه یک نشان دارند. سندهای قدیمیِ
 * اندروید که هنوز روی `PdfKit` می‌کشند، همین متن را در پاصفحهٔ خودشان
 * می‌کشند.
 *
 * دو تکه دارد:
 *
 * - متنِ درشت و کم‌رنگ وسطِ برگه، **پیش از** بقیهٔ رسم‌ها — یعنی زیرِ
 *   متن و جدول می‌نشیند و چیزی را نمی‌پوشاند.
 * - یک نوارِ باریک بالای پاصفحه، **پس از** بقیه — که اگر وسطِ برگه زیرِ
 *   جدولِ رنگی رفت، باز هم دیده شود.
 *
 * رنگ‌ها کدر (بی شفافیت)اند چون رسامِ ویندوز آلفا را نمی‌خوانَد.
 */
object Watermark {

    /** رنگِ متنِ درشتِ وسط — آن‌قدر کم‌رنگ که خواندنِ سند را سخت نکند. */
    private const val FAINT = 0xFFE9E5DE.toInt()
    private const val STRIP_BG = 0xFFFFF3E8.toInt()

    /** نشان لازم است؟ از درِ لایسنس، با ساعتِ همین لحظه. */
    fun needed(): Boolean = LicenceGate.watermark()

    fun apply(doc: SheetDoc, on: Boolean = needed()): SheetDoc =
        if (!on) doc else SheetDoc(doc.pages.map { apply(it, true) })

    fun apply(sheet: Sheet, on: Boolean = needed()): Sheet {
        if (!on) return sheet
        val p = sheet.paper
        val big = when {
            p.narrow -> 11f
            p.w < Paper.A4.w -> 22f
            else -> 30f
        }
        val middle = DrawOp.Text(
            LicenceText.WATERMARK, x = p.w / 2f, y = p.h * 0.42f, size = big,
            color = FAINT, weight = Weight.Bold, align = Align.Center,
        )
        val stripTop = p.bodyBottom + 2f
        val small = if (p.narrow) 7f else 8f
        val strip = listOf(
            DrawOp.Rect(p.margin, stripTop, p.w - p.margin, stripTop + small + 6f, STRIP_BG, radius = 2f),
            DrawOp.Text(
                LicenceText.WATERMARK, x = p.w / 2f, y = stripTop + 2f, size = small,
                color = SheetColors.DANGER, weight = Weight.Bold, align = Align.Center,
            ),
        )
        return sheet.copy(ops = listOf(middle) + sheet.ops + strip)
    }
}
