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

/**
 * سندِ چندبرگه‌ای.
 *
 * فاکتورِ یک سفارشِ بزرگ در یک A4 جا نمی‌شود، پس چیدمان باید بتواند
 * برگهٔ تازه باز کند. تک‌برگه‌ها هم همین‌اند با یک عضو.
 */
data class SheetDoc(val pages: List<Sheet>) {
    init {
        require(pages.isNotEmpty()) { "سندِ بی‌برگه معنا ندارد" }
    }
}

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
     * [y] **بالای** کادرِ متن است نه خطِ کرسی — همان چیزی که
     * `StaticLayout`ِ اندروید هم می‌گیرد. چیدمانِ سندهای این اپ با
     * همین قرارداد نوشته شده و عوض کردنش یعنی جابه‌جا شدنِ هر سطر.
     *
     * [x] در چیدمانِ راست‌به‌چپ: با [Align.Start] لبهٔ **راست** است و با
     * [Align.End] لبهٔ چپ.
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

    // مس — همان پله‌هایی که `ui.theme.Brand` روی صفحه دارد.
    //
    // عمداً دوباره اینجا نوشته شده و از آنجا وارد نمی‌شود: این لایه
    // `Int`ِ ARGB می‌خواهد و آن لایه `Color`ِ Compose است؛ وابسته
    // کردنِ سازندهٔ کاغذ به لایهٔ رابطِ کاربری برای سه عدد، بهایی است
    // که نمی‌ارزد. اگر یکی عوض شد، آن یکی هم باید عوض شود.
    const val COPPER_LIGHT = 0xFFD8B496.toInt()
    const val COPPER = 0xFFC58E62.toInt()
    const val COPPER_DEEP = 0xFFBA7944.toInt()
}

/**
 * رنگِ میانیِ دو رنگ — [t] بینِ ۰ و ۱.
 *
 * در فضای sRGB و بدونِ تصحیحِ گاما. برای گذرِ کوتاهِ بینِ دو پلهٔ نزدیکِ
 * مس تفاوتش با روشِ درست به چشم نمی‌آید، و روشِ درست یعنی توان‌رسانی
 * برای هر تکه از نوار.
 */
fun lerpColor(from: Int, to: Int, t: Float): Int {
    val f = t.coerceIn(0f, 1f)
    fun ch(shift: Int): Int {
        val a = (from shr shift) and 0xFF
        val b = (to shr shift) and 0xFF
        return (a + (b - a) * f).toInt().coerceIn(0, 255)
    }
    return (ch(24) shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
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

    /**
     * نوارِ گرادیانِ افقی — از [stops] پله به پله.
     *
     * **چرا با مستطیل‌های نازک و نه با یک عملیاتِ گرادیان.** افزودنِ
     * `DrawOp.Gradient` یعنی هر دو رسام — `Canvas`ِ اندروید و رسامِ
     * ویندوز — باید پیاده‌اش کنند، و تا وقتی هر دو نکرده‌اند سربرگِ یک
     * سکو با آن یکی فرق دارد. با مستطیل، هر رسامی که همین حالا
     * `Rect` را می‌کشد بدونِ یک خط تغییر همین را هم می‌کشد.
     *
     * [steps] پیش‌فرض ۱۲۸ است، و این عدد اندازه‌گیری شده نه حدسی: با
     * پله‌های مسِ این پروژه، بیشترین جهشِ رنگ بینِ دو تکهٔ کنارِ هم در
     * ۴۸ تکه به ۷ می‌رسد که روی کاغذ پلکان دیده می‌شود، در ۹۶ تکه به
     * ۴، و در ۱۲۸ به ۳ — زیرِ آستانه‌ای که چشم روی سطحِ یکدست
     * تشخیص می‌دهد. ۱۲۸ مستطیلِ نازک برای یک سند هزینه‌ای ندارد.
     */
    fun gradientBand(
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        stops: List<Int>,
        steps: Int = 128
    ) {
        if (stops.isEmpty() || right <= left || bottom <= top) return
        if (stops.size == 1) {
            rect(left, top, right, bottom, stops[0])
            return
        }
        val n = steps.coerceAtLeast(stops.size)
        val width = (right - left) / n
        for (i in 0 until n) {
            // جای این تکه روی کلِ نوار، و اینکه بینِ کدام دو پله افتاده
            val pos = i / (n - 1).toFloat().coerceAtLeast(1f)
            val span = 1f / (stops.size - 1)
            val idx = (pos / span).toInt().coerceIn(0, stops.size - 2)
            val local = (pos - idx * span) / span
            val x = left + i * width
            // نیمْ‌نقطه هم‌پوشانی تا بینِ تکه‌ها خطِ سفید نیفتد
            rect(x, top, x + width + 0.5f, bottom, lerpColor(stops[idx], stops[idx + 1], local))
        }
    }

    fun build() = Sheet(paper, ops.toList())
}

fun sheet(paper: Paper, block: SheetBuilder.() -> Unit): Sheet =
    SheetBuilder(paper).apply(block).build()
