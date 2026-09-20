package com.afghanjama.pdf

/**
 * هندسهٔ برگهٔ چاپی — عمداً بدونِ هیچ وابستگیِ اندرویدی، تا خودآزمایی
 * بتواند بدونِ گوشی بسنجدش. رسمِ واقعی در `PdfKit` است.
 */

/**
 * اندازهٔ کاغذ در واحدِ استانداردِ PDF (۷۲ نقطه در اینچ).
 *
 * کارگاه دو جور برگه بیرون می‌دهد: فاکتور روی کاغذِ معمولی (A4 یا A5) و
 * رسید روی رولِ حرارتیِ ۸ سانتی.
 */
data class Paper(
    val w: Int,
    val h: Int,
    val margin: Float,
    val label: String
) {
    val contentW: Int get() = (w - 2 * margin).toInt()

    /** پایین‌ترین جای امنِ بدنه — پایین‌تر از این باید صفحهٔ تازه باز شود. */
    val bodyBottom: Float get() = h - (if (narrow) 34f else 74f)

    /** رولِ باریک جای سربرگِ رنگی و جدولِ پرستون را ندارد. */
    val narrow: Boolean get() = w < 300

    /** اندازهٔ متنِ خانه‌های جدول؛ کاغذِ کوچک‌تر متنِ کوچک‌تر می‌خواهد. */
    val cellTextSize: Float
        get() = when {
            narrow -> 7.5f
            w < Paper.A4.w -> 8.5f
            else -> 9.5f
        }

    companion object {
        val A4 = Paper(595, 842, 40f, "A4")
        val A5 = Paper(420, 595, 28f, "A5")

        /**
         * رولِ حرارتیِ ۸ سانتی. عرضِ چاپیِ واقعیِ این پرینترها ۷۲ میلی‌متر
         * است نه ۸۰ — بقیه‌اش حاشیهٔ مکانیکیِ کاغذ است. ۷۲mm ≈ ۲۰۴ نقطه.
         * ارتفاع بلند گرفته شده چون رول ته ندارد و برگه بعد از رسم
         * بریده می‌شود.
         */
        val ROLL80 = Paper(204, 1200, 8f, "۸ سانتی")

        /** کاغذهایی که کاربر می‌تواند انتخاب کند. */
        val ALL = listOf(A4, A5, ROLL80)

        fun byLabel(label: String): Paper = ALL.firstOrNull { it.label == label } ?: A4
    }
}

/** یک چیدمانِ ستونیِ آماده برای جدولِ اقلامِ فاکتور. */
data class InvoiceColumns(val titles: List<String>, val weights: List<Float>)

/**
 * ستون‌های جدولِ فاکتور بر اساسِ جایی که کاغذ دارد.
 *
 * چرا سه حالت و نه یکی: جدولِ هفت‌ستونه روی A5 فقط ۰٫۲ نقطه جا کم می‌آورد
 * و روی رول اصلاً نمی‌شود — سرستون‌ها می‌شکنند و برگه به‌هم می‌ریزد. به‌جای
 * کوچک‌کردنِ متن تا مرزِ ناخوانایی، ستون‌های کم‌ارزش‌تر حذف می‌شوند:
 * «ردیف» و «واحد» روی A5، و روی رول فقط نام و تعداد و مبلغ می‌ماند.
 */
fun invoiceColumns(paper: Paper): InvoiceColumns = when {
    paper.narrow -> InvoiceColumns(
        listOf("شرح", "تعداد", "مبلغ"),
        listOf(3.0f, 1.0f, 1.6f)
    )
    paper.w < Paper.A4.w -> InvoiceColumns(
        listOf("کد", "نام کالا", "تعداد", "فی", "جمع"),
        listOf(0.7f, 3.0f, 0.8f, 1.2f, 1.5f)
    )
    else -> InvoiceColumns(
        listOf("ردیف", "کد", "نام کالا یا خدمات", "تعداد", "واحد", "فی", "جمع ردیف"),
        listOf(0.5f, 0.7f, 2.6f, 0.7f, 0.7f, 1.1f, 1.3f)
    )
}

/**
 * پهنای واقعیِ هر ستون به نقطه — همان حسابی که `PdfKit.drawCells` می‌کند،
 * جدا نگه داشته شده تا بشود بدونِ بوم سنجیدش.
 */
fun columnWidths(paper: Paper, weights: List<Float>): List<Float> {
    val total = weights.sum().takeIf { it > 0f } ?: 1f
    return weights.map { paper.contentW * (it / total) }
}
