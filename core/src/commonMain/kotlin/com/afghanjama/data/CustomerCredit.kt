package com.afghanjama.data

/**
 * پولی که از مشتری می‌رسد کدام حساب را می‌بندد: «طلب از مشتری» یا
 * «پیش‌دریافتِ مشتری»؟
 *
 * **باگی که این را لازم کرد.** دریافت از مشتری — چه از دکمهٔ «دریافت» و
 * دفتر کل، چه از صفحهٔ خودِ مشتری — در ژورنال به «پیش‌دریافت» می‌نشست،
 * حتی وقتی مشتری بابتِ فروشِ نسیه بدهکار بود. طلب کم نمی‌شد و یک بدهیِ
 * ساختگی کنارش ساخته می‌شد: فروشِ نسیهٔ ۲۴٬۸۵۰ و دریافتِ ۲۴٬۸۰۰ در
 * «وضعیت مالی» و ترازنامه هم ۲۴٬۸۵۰ طلب نشان می‌داد هم ۲۴٬۸۰۰ بدهی، در
 * حالی که مشتری فقط ۵۰ بدهکار بود. خالصِ دارایی درست می‌ماند و همین
 * پنهانش می‌کرد.
 *
 * صفحهٔ مشتری یک پرسش داشت («بدهکار است؟») ولی آن را **پس از** ثبتِ همین
 * دریافت در دفترِ مشتری می‌پرسید؛ پرداختِ کامل همیشه جوابِ «نه» می‌گرفت.
 *
 * **چرا ماندهٔ خامِ دفترِ مشتری کافی نیست.** سفارش همان لحظهٔ ثبت، مبلغِ
 * توافقی را در دفترِ مشتری بدهکار می‌کند، ولی ژورنال تا فروش هیچ طلبی
 * برایش ندارد. پولی که پیش از تحویل می‌رسد، در ژورنال پیش‌دریافت است؛ اگر
 * طلب را می‌بست، طلب منفی می‌شد. پس سطرهای چرخهٔ سفارش — بدهیِ ثبت، و
 * خنثی‌شدنش هنگامِ رفتن به انبار یا حذف — از این مانده بیرون می‌مانند.
 *
 * بی دیتابیس، تا آزمون مستقیم بسنجدش.
 */
object CustomerCredit {

    /** یک سطرِ دفترِ مشتری — فقط آنچه این قاعده لازم دارد. */
    data class Row(val refType: String, val refId: String, val debit: Long, val credit: Long)

    /** نوع‌های سطری که به چرخهٔ «سفارش ← انبار» تعلق دارند. */
    private val ORDER_LIFECYCLE = setOf("SALE_BILLING", "SALE_TO_STOCK", "SALE_CANCEL")

    /**
     * ماندهٔ مشتری آن‌طور که ژورنال می‌شناسد: مثبت یعنی به ما بدهکار است،
     * منفی یعنی پولِ او پیشِ ماست.
     *
     * [openOrderCodes] کدِ سفارش‌هایی است که هنوز وجود دارند. سفارشِ بسته‌شده
     * (به انبار رفته یا حذف‌شده) از روی سطرِ خنثی‌کننده‌اش شناخته می‌شود،
     * چون سفارشِ حذف‌شده دیگر در جدول نیست.
     */
    fun recognizedNet(rows: List<Row>, openOrderCodes: Set<String>): Long {
        val closed = rows
            .filter { it.refType == "SALE_TO_STOCK" || it.refType == "SALE_CANCEL" }
            .map { it.refId }
            .toSet()
        val orderIds = closed + openOrderCodes
        return rows
            .filterNot { it.refType in ORDER_LIFECYCLE && it.refId in orderIds }
            .sumOf { it.debit - it.credit }
    }

    /** سهمِ هر حساب از یک مبلغ. جمعشان همیشه خودِ مبلغ است. */
    data class Split(val receivable: Long, val prepay: Long)

    /**
     * پولی که از مشتری رسید (یا به حسابش بستانکار شد، مثلِ برگشتِ بی‌نقد):
     * اول آنچه بدهکار است بسته می‌شود، فقط مازاد پیش‌دریافت است.
     *
     * [netBefore] باید **پیش از** ثبتِ همین حرکت در دفترِ مشتری خوانده شود.
     */
    fun credit(netBefore: Long, amount: Long): Split {
        val a = amount.coerceAtLeast(0)
        val toReceivable = minOf(a, netBefore.coerceAtLeast(0))
        return Split(receivable = toReceivable, prepay = a - toReceivable)
    }

    /**
     * پولی که ما به مشتری دادیم: اول پولی که از او پیشِ ما بود پس داده
     * می‌شود، مازادش طلبِ ما از او می‌شود.
     */
    fun debit(netBefore: Long, amount: Long): Split {
        val a = amount.coerceAtLeast(0)
        val fromPrepay = minOf(a, (-netBefore).coerceAtLeast(0))
        return Split(receivable = a - fromPrepay, prepay = fromPrepay)
    }
}
