package com.afghanjama.pdf

/**
 * یک برگهٔ آمادهٔ چاپ، مستقل از اینکه چه چیزی آن را می‌کشد.
 *
 * **چرا این لایه:** کدِ چاپِ امروز (۱٬۵۸۱ خط) مستقیم روی
 * `android.graphics` نشسته. یعنی چیدمانِ فاکتور — اینکه کدام ستون کجا
 * بنشیند و جمع کجا بیاید — به موتورِ رسمِ اندروید چسبیده و روی ویندوز
 * تکرار می‌شود. همان تلهٔ «دو پیاده‌سازی» که یک بار سرِ دیتابیس از آن
 * دوری کردیم.
 *
 * راهِ بیرون همان الگوی `Db` است: **چیدمان می‌گوید چه چیزی کجا برود،
 * سکو می‌گوید با چه ابزاری کشیده شود.** یک برگه فهرستی از [DrawOp] است؛
 * اندروید آن را با `Canvas` اجرا می‌کند و ویندوز با PDFBox.
 *
 * مختصات مثلِ خودِ PDF است: مبدأ گوشهٔ **بالا-چپ**، واحد نقطه (۱/۷۲ اینچ)
 * — همان چیزی که `Paper` از قبل با آن کار می‌کند.
 */
data class Sheet(
    val paper: Paper,
    val ops: List<DrawOp>
)

/** ترازِ افقیِ متن. در متنِ راست‌به‌چپ [End] یعنی سمتِ چپ. */
enum class Align { Start, Center, End }

/** وزنِ قلم — هر سکو خودش فایلِ فونتش را می‌شناسد. */
enum class Weight { Regular, Bold }

/**
 * یک دستورِ رسم.
 *
 * عمداً کوچک نگه داشته شده: هرچه اینجا کمتر باشد، پیاده‌سازیِ هر سکو
 * کوچک‌تر و امکانِ اختلافشان کمتر است.
 */
sealed interface DrawOp {

    /**
     * یک تکه متن.
     *
     * [x] لبهٔ شروع است بر اساسِ [align]؛ [y] **خطِ کرسیِ** متن است، نه
     * بالای آن. اگر [maxWidth] داده شود و متن جا نشود، سکو باید کوتاهش
     * کند — بریدنِ متن بهتر از ریختنش روی ستونِ بغلی است.
     */
    data class Text(
        val text: String,
        val x: Float,
        val y: Float,
        val size: Float,
        val color: Int,
        val weight: Weight = Weight.Regular,
        val align: Align = Align.Start,
        val maxWidth: Float = 0f
    ) : DrawOp

    /** خطِ صاف. */
    data class Line(
        val x1: Float, val y1: Float,
        val x2: Float, val y2: Float,
        val color: Int,
        val width: Float = 1f
    ) : DrawOp

    /** مستطیلِ پرشده؛ [radius] صفر یعنی گوشهٔ تیز. */
    data class Rect(
        val left: Float, val top: Float,
        val right: Float, val bottom: Float,
        val color: Int,
        val radius: Float = 0f
    ) : DrawOp

    /**
     * تصویر — لوگوی کارگاه یا کدِ QR.
     *
     * بایت‌های خودِ فایل داده می‌شود نه شیءِ تصویرِ سکو، وگرنه این لایه
     * دوباره به اندروید گره می‌خورد.
     */
    data class Image(
        val bytes: ByteArray,
        val left: Float, val top: Float,
        val width: Float, val height: Float
    ) : DrawOp {
        // ByteArray برابریِ ساختاری ندارد؛ بدونِ اینها دو برگهٔ یکسان
        // نابرابر دیده می‌شوند و آزمون‌ها بی‌دلیل می‌شکنند.
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Image) return false
            return bytes.contentEquals(other.bytes) &&
                left == other.left && top == other.top &&
                width == other.width && height == other.height
        }

        override fun hashCode(): Int =
            bytes.contentHashCode() * 31 + left.hashCode()
    }
}

/** رنگ‌های برگه — همان‌هایی که `PdfKit` روی اندروید دارد. */
object SheetColors {
    const val BRAND = 0xFF1F6E5C.toInt()
    const val BRAND_DEEP = 0xFF17594A.toInt()
    const val INK = 0xFF1B1C1A.toInt()
    const val MUTED = 0xFF61605A.toInt()
    const val LINE = 0xFFE1DFD8.toInt()
    const val SOFT = 0xFFF4F7F5.toInt()
    const val DANGER = 0xFFB3261E.toInt()
}

/** ساختنِ برگه به‌صورتِ گام‌به‌گام. */
class SheetBuilder(private val paper: Paper) {
    private val ops = mutableListOf<DrawOp>()

    fun add(op: DrawOp) {
        ops += op
    }

    fun text(
        text: String,
        x: Float,
        y: Float,
        size: Float,
        color: Int = SheetColors.INK,
        weight: Weight = Weight.Regular,
        align: Align = Align.Start,
        maxWidth: Float = 0f
    ) = add(DrawOp.Text(text, x, y, size, color, weight, align, maxWidth))

    fun line(x1: Float, y1: Float, x2: Float, y2: Float, color: Int = SheetColors.LINE, width: Float = 1f) =
        add(DrawOp.Line(x1, y1, x2, y2, color, width))

    fun rect(left: Float, top: Float, right: Float, bottom: Float, color: Int, radius: Float = 0f) =
        add(DrawOp.Rect(left, top, right, bottom, color, radius))

    fun build() = Sheet(paper, ops.toList())
}

fun sheet(paper: Paper, block: SheetBuilder.() -> Unit): Sheet =
    SheetBuilder(paper).apply(block).build()
