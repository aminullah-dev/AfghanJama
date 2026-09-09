package com.afghanjama.pdf

import com.afghanjama.AppInfo
import com.afghanjama.ui.format.PersianDate
import com.afghanjama.ui.format.fa

/**
 * هویتِ کارگاه روی برگه.
 *
 * روی اندروید از `CompanyPrefs` می‌آید و روی ویندوز از تنظیماتِ خودش.
 * اینجا فقط داده است، تا چیدمان به هیچ‌کدام گره نخورد.
 */
data class ShopInfo(
    val name: String,
    val phone: String = "",
    val address: String = "",
    /** بایت‌های لوگو — خالی یعنی لوگو ندارد. */
    val logo: ByteArray? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ShopInfo) return false
        return name == other.name && phone == other.phone &&
            address == other.address &&
            (logo?.contentEquals(other.logo ?: ByteArray(0)) ?: (other.logo == null))
    }

    override fun hashCode(): Int = name.hashCode() * 31 + phone.hashCode()
}

/**
 * تکه‌های مشترکِ همهٔ سندها — سربرگ، پاصفحه، ردیفِ «برچسب … مقدار»،
 * کادرِ جمع، و جای امضا.
 *
 * اندازه‌ها **کلمه به کلمه** از `PdfKit`ِ اندروید برداشته شده‌اند، چون
 * برگه‌ای که کارفرما امروز چاپ می‌کند همان است و نباید تکان بخورد.
 *
 * هر تابع مثلِ نسخهٔ اندرویدی‌اش `y`ِ بعدی را برمی‌گرداند؛ چیدمان با
 * همین زنجیره جلو می‌رود.
 */
class DocChrome(
    private val b: SheetBuilder,
    private val paper: Paper,
    private val m: TextMeasurer,
    private val shop: ShopInfo
) {
    private val right = paper.w - paper.margin
    private val left = paper.margin
    private val contentW = paper.contentW.toFloat()

    /** متنِ راست‌چین؛ ارتفاعِ اشغال‌شده را برمی‌گرداند. */
    fun rtl(
        text: String,
        top: Float,
        size: Float,
        color: Int = SheetColors.INK,
        weight: Weight = Weight.Regular,
        width: Float = contentW,
        x: Float = right
    ): Float {
        if (text.isBlank()) return 0f
        val lines = m.wrap(text, size, width, weight)
        val lh = m.lineHeight(size, weight)
        lines.forEachIndexed { i, line ->
            b.text(line, x, top + i * lh, size, color, weight, Align.Start, width)
        }
        return lines.size * lh
    }

    /** متن در سمتِ مقابلِ سطر — چپ، در چیدمانِ راست‌به‌چپ. */
    fun rtlEnd(
        text: String,
        top: Float,
        size: Float,
        color: Int = SheetColors.INK,
        weight: Weight = Weight.Regular,
        width: Float = contentW
    ): Float {
        if (text.isBlank()) return 0f
        val lines = m.wrap(text, size, width, weight)
        val lh = m.lineHeight(size, weight)
        lines.forEachIndexed { i, line ->
            b.text(line, left, top + i * lh, size, color, weight, Align.End, width)
        }
        return lines.size * lh
    }

    /** متنِ وسط‌چین. */
    fun center(text: String, top: Float, size: Float, color: Int = SheetColors.INK): Float {
        if (text.isBlank()) return 0f
        val lh = m.lineHeight(size)
        b.text(text, paper.w / 2f, top, size, color, Weight.Regular, Align.Center, contentW)
        return lh
    }

    fun rule(y: Float): Float {
        b.line(left, y, right, y, SheetColors.LINE, 0.8f)
        return y + 12f
    }

    /** ردیفِ «برچسب … مقدار». */
    fun kv(
        label: String,
        value: String,
        y: Float,
        strong: Boolean = false,
        danger: Boolean = false
    ): Float {
        val size = if (paper.narrow) 8.5f else 10.5f
        val h1 = rtl(label, y, size, SheetColors.MUTED, width = contentW * 0.58f)
        val h2 = rtlEnd(
            value, y,
            if (strong) size + 1.5f else size,
            if (danger) SheetColors.DANGER else SheetColors.INK,
            if (strong) Weight.Bold else Weight.Regular
        )
        return y + maxOf(h1, h2, 15f) + 5f
    }

    /**
     * کادرِ جمع — پررنگ‌ترین چیزِ برگه.
     *
     * روی رول کادرِ خطی می‌شود نه پرشده: زمینهٔ پر روی پرینترِ حرارتی
     * جوهر و حرارتِ زیادی می‌برد.
     */
    fun totalBox(label: String, value: String, y: Float): Float {
        val h = if (paper.narrow) 28f else 34f
        val innerW = contentW - 24f
        if (paper.narrow) {
            b.rect(left, y, right, y + h, SheetColors.SOFT, 4f)
            rtl(label, y + 7f, 9f, SheetColors.INK, width = innerW, x = right - 8f)
            rtlEnd(value, y + 6f, 11f, SheetColors.INK, Weight.Bold, innerW)
            return y + h + 10f
        }
        b.rect(left, y, right, y + h, SheetColors.BRAND, 6f)
        rtl(label, y + 9f, 11f, WHITE, width = innerW, x = right - 12f)
        rtlEnd(value, y + 7f, 14f, WHITE, Weight.Bold, innerW)
        return y + h + 14f
    }

    /** جای امضا — رسیدِ بدونِ امضا در کارگاه اعتبار ندارد. */
    fun signatures(y: Float, rightLabel: String, leftLabel: String): Float {
        val size = if (paper.narrow) 7.5f else 9.5f
        val half = contentW / 2f
        val lineY = y + 26f
        val gap = if (paper.narrow) 6f else 20f
        b.line(right - half + gap, lineY, right, lineY, SheetColors.LINE, 0.8f)
        b.line(left, lineY, left + half - gap, lineY, SheetColors.LINE, 0.8f)
        rtl(rightLabel, lineY + 6f, size, SheetColors.MUTED, width = half)
        rtl(leftLabel, lineY + 6f, size, SheetColors.MUTED, width = half - gap, x = left + half - gap)
        return lineY + 26f
    }

    /**
     * سلول‌های یک سطر.
     *
     * ستون‌ها **از راست** چیده می‌شوند (اولین عنوان راست‌ترین ستون است) و
     * آخرین ستون — که معمولاً مبلغ است — چپ‌چین می‌شود.
     */
    private fun cells(
        values: List<String>,
        weights: List<Float>,
        y: Float,
        size: Float,
        color: Int,
        weight: Weight
    ) {
        val total = weights.sum().takeIf { it > 0f } ?: 1f
        var edge = right
        values.forEachIndexed { i, text ->
            val w = contentW * (weights.getOrElse(i) { 1f } / total)
            val cellLeft = edge - w
            if (i == values.lastIndex) {
                if (text.isNotBlank()) {
                    val lines = m.wrap(text, size, w, weight)
                    val lh = m.lineHeight(size, weight)
                    lines.forEachIndexed { k, line ->
                        b.text(line, cellLeft, y + k * lh, size, color, weight, Align.End, w)
                    }
                }
            } else if (text.isNotBlank()) {
                val lines = m.wrap(text, size, w, weight)
                val lh = m.lineHeight(size, weight)
                lines.forEachIndexed { k, line ->
                    b.text(line, edge, y + k * lh, size, color, weight, Align.Start, w)
                }
            }
            edge = cellLeft
        }
    }

    /** سرستونِ جدول. */
    fun tableHeader(titles: List<String>, weights: List<Float>, y: Float): Float {
        b.rect(left, y - 4f, right, y + 17f, SheetColors.SOFT, 3f)
        cells(
            titles, weights, y, paper.cellTextSize,
            if (paper.narrow) SheetColors.INK else SheetColors.BRAND, Weight.Bold
        )
        return y + 24f
    }

    /** ردیفِ جدول؛ [zebra] زمینهٔ ملایم برای خوانایی سطرهای بلند. */
    fun tableRow(
        values: List<String>,
        weights: List<Float>,
        y: Float,
        zebra: Boolean = false
    ): Float {
        if (zebra) b.rect(left, y - 3f, right, y + 16f, SheetColors.SOFT)
        cells(values, weights, y, paper.cellTextSize, SheetColors.INK, Weight.Regular)
        return y + 21f
    }

    /** عنوانِ بخش با نشانهٔ کوچکِ برند در ابتدایش. */
    fun section(title: String, y: Float): Float {
        val ink = if (paper.narrow) SheetColors.INK else SheetColors.BRAND
        b.rect(right - 3f, y + 2f, right, y + 15f, ink, 1.5f)
        rtl(title, y, 12.5f, ink, Weight.Bold, contentW - 8f)
        return y + 24f
    }

    /**
     * کادرِ متنی با حاشیه — برای پیامِ پایانِ فاکتور.
     *
     * ارتفاعِ کادر از خودِ متن می‌آید، پس اول سطرها نوشته می‌شوند و بعد
     * دورشان کادر کشیده می‌شود.
     */
    fun boxedNote(lines: List<String>, y: Float): Float {
        val size = if (paper.narrow) 7.5f else 9f
        val innerW = contentW - 20f
        var inner = y + 8f
        lines.filter { it.isNotBlank() }.forEach {
            inner += rtl(it, inner, size, SheetColors.INK, width = innerW, x = right - 10f)
        }
        val bottom = inner + 8f
        // کادر پس از متن اضافه می‌شود ولی چون فقط خط است، روی متن
        // نمی‌افتد — برخلافِ مستطیلِ پر که باید اول بیاید.
        b.line(left, y, right, y, SheetColors.LINE, 1f)
        b.line(left, bottom, right, bottom, SheetColors.LINE, 1f)
        b.line(left, y, left, bottom, SheetColors.LINE, 1f)
        b.line(right, y, right, bottom, SheetColors.LINE, 1f)
        return bottom + 10f
    }

    /** یادداشتِ کم‌رنگ — شرایط، توضیح، سلبِ مسئولیت. */
    fun note(text: String, y: Float): Float =
        y + rtl(text, y, 8.5f, SheetColors.MUTED) + 8f

    /** سربرگ: نامِ کارگاه، عنوانِ سند، شماره و تاریخ. */
    fun header(title: String, number: String, at: Long): Float {
        /*
         * نوارِ مسیِ بالای برگه — همان هویتی که روی صفحهٔ گوشی هست.
         *
         * از لبه تا لبه و **بالاتر از حاشیه** کشیده می‌شود، پس هیچ
         * متنی را جابه‌جا نمی‌کند: چیدمانِ بدنه از `paper.margin`
         * شروع می‌شود و آن دست‌نخورده مانده. اگر نوار جا باز می‌کرد،
         * محلِ شکستِ صفحه در همهٔ اسناد عوض می‌شد.
         *
         * روی رولِ باریک نمی‌آید — همان‌جا که `Paper.narrow` می‌گوید
         * جای سربرگِ رنگی نیست. چاپگرِ حرارتی هم رنگ ندارد و فقط
         * نوارِ خاکستری چاپ می‌کند.
         */
        if (!paper.narrow) {
            b.gradientBand(
                left = 0f,
                top = 0f,
                right = paper.w.toFloat(),
                bottom = 5f,
                stops = listOf(
                    SheetColors.COPPER_DEEP,
                    SheetColors.COPPER,
                    SheetColors.COPPER_LIGHT,
                    SheetColors.COPPER_DEEP
                )
            )
        }

        var y = paper.margin
        val nameSize = if (paper.narrow) 11f else 15f
        y += rtl(shop.name, y, nameSize, SheetColors.BRAND, Weight.Bold)

        val sub = listOf(shop.phone, shop.address).filter { it.isNotBlank() }.joinToString(" • ")
        if (sub.isNotBlank()) {
            y += rtl(sub, y + 2f, if (paper.narrow) 7f else 8.5f, SheetColors.MUTED) + 2f
        }

        y += 6f
        y += rtl(title, y, if (paper.narrow) 10f else 13f, SheetColors.INK, Weight.Bold)
        val meta = listOf(number, PersianDate.long(at)).filter { it.isNotBlank() }.joinToString(" • ")
        y += rtl(meta, y + 2f, if (paper.narrow) 7.5f else 9f, SheetColors.MUTED) + 4f

        return rule(y)
    }

    /** پاصفحه — همیشه در پایینِ برگه، مستقل از اینکه بدنه کجا تمام شد. */
    fun footer(pageNo: Int = 1, note: String = "") {
        // همان عددی که `PdfKit.footerTop` روی اندروید می‌دهد.
        val top = paper.h - (if (paper.narrow) 26f else 46f)
        b.line(left, top, right, top, SheetColors.LINE, 0.8f)
        val credit = note.ifBlank { "صادرشده با اپلیکیشن ${AppInfo.NAME}" }

        // روی رول صفحه‌شماری معنا ندارد و جا هم نیست
        if (paper.narrow) {
            center(credit, top + 6f, 7f, SheetColors.MUTED)
            return
        }
        val shopLine = buildString {
            append(shop.name)
            if (shop.phone.isNotBlank()) append(" • ${shop.phone}")
        }
        rtl(shopLine, top + 8f, 8.5f, SheetColors.MUTED)
        rtlEnd("صفحهٔ ${pageNo.fa()}", top + 8f, 8.5f, SheetColors.MUTED)
        rtl(credit, top + 22f, 8f, SheetColors.MUTED)
    }

    private companion object {
        const val WHITE = 0xFFFFFFFF.toInt()
    }
}
