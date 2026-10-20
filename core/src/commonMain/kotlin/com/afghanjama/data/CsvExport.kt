package com.afghanjama.data

import com.afghanjama.data.entities.Order
import com.afghanjama.data.entities.Transaction
import com.afghanjama.ui.format.PersianDate

/**
 * متنِ خروجیِ CSV (اکسل) — سفارش‌ها و تراکنش‌ها.
 *
 * **چرا اینجا و خالص.** تا ۱.۱۰ این سطرها فقط در `BackupViewModel`ِ
 * اندروید ساخته می‌شدند، چسبیده به `ContentResolver`. کامپیوتر — همان
 * جایی که اکسل هست — هیچ راهی به این خروجی نداشت. حالا ساختنِ متن یک‌جاست
 * و نوشتنِ فایل کارِ هر سکو: اندروید با پنجرهٔ «ذخیره در…»، کامپیوتر در
 * پوشهٔ اسناد (`FileExport`).
 *
 * **BOM اینجا نیست.** هر دو نویسنده خودشان سه بایتِ `EF BB BF` را پیش از
 * متن می‌نویسند؛ بی آن، اکسلِ ویندوز فارسی را جویده نشان می‌دهد.
 *
 * ستون‌ها و ترتیبشان همان ستون‌های ۱.۹ است، تا فایلی که کارگاه با آن
 * کار می‌کند عوض نشود.
 */
object CsvExport {

    /**
     * یک خانه، همیشه در گیومه.
     *
     * گیومه همیشه، نه فقط وقتی لازم است: نامِ مشتری می‌تواند ویرگول یا
     * گیومه یا خطِ تازه داشته باشد، و خانه‌ای که بی‌گیومه بماند ستون‌ها را
     * جابه‌جا می‌کند. گیومهٔ درونی دوتا می‌شود (RFC 4180).
     *
     * **گیومه جلوی فرمول را نمی‌گیرد** — [neutralise] می‌گیرد.
     */
    fun cell(v: String): String = "\"" + neutralise(v).replace("\"", "\"\"") + "\""

    /**
     * خانه‌ای که با `=`، `+`، `-`، `@`، تب یا CR شروع شود، در اکسل **فرمول**
     * خوانده می‌شود، حتی در گیومه. نامِ مشتری یا یادداشتی مثلِ
     * `=HYPERLINK("http://…","…")` با یک کلیک به جای دیگری می‌برد، و
     * کامپیوتر فایل را پس از ذخیره خودش در اکسل باز می‌کند. پس پیش از چنین
     * متنی یک `'` می‌آید تا اکسل آن را متن بداند (همان راهِ OWASP).
     *
     * عددِ خالص (مثلِ `-250` یا `+93700123456`) دست نمی‌خورد: فرمول نیست و
     * باید عدد بماند.
     */
    fun neutralise(v: String): String =
        if (v.isNotEmpty() && v[0] in FORMULA_START && !PLAIN_NUMBER.matches(v)) "'$v" else v

    private val FORMULA_START = setOf('=', '+', '-', '@', '\t', '\r')
    private val PLAIN_NUMBER = Regex("""[+-]?[0-9]+([.][0-9]+)?""")

    fun row(cells: List<String>): String = cells.joinToString(",") { cell(it) }

    val ORDER_HEADER: List<String> = listOf(
        "کد سفارش", "کد کوتاه", "طرح", "تعداد", "نوع پارچه", "رنگ", "سایز",
        "مقدار پارچه", "واحد", "منبع پارچه", "قیمت پارچه", "خرج کار",
        "قیمت توافقی", "مشتری", "تلفن", "خیاط", "وضعیت", "تاریخ ایجاد"
    )

    val TX_HEADER: List<String> = listOf("نوع", "صندوق", "مبلغ", "دسته", "یادداشت", "تاریخ")

    fun orderCells(o: Order): List<String> = listOf(
        o.orderCode, o.shortCode, o.designTitle, o.qty.toString(),
        o.fabricType, o.fabricColor, o.size,
        o.fabricAmount.toString(), o.fabricUnit, o.fabricSource,
        o.fabricPrice.toString(), o.workCost.toString(),
        o.agreedPrice.toString(), o.customerName, o.customerPhone,
        o.assignedTailor ?: "", o.status, PersianDate.csv(o.createdAt)
    )

    fun txCells(t: Transaction): List<String> = listOf(
        if (t.type == "IN") "دریافت" else "پرداخت",
        t.source, t.amount.toString(), t.category, t.note,
        PersianDate.csv(t.createdAt)
    )

    /** کلِ فایلِ سفارش‌ها: سرستون و یک سطر برای هر سفارش. */
    fun orders(list: List<Order>): String = buildString {
        appendLine(row(ORDER_HEADER))
        list.forEach { appendLine(row(orderCells(it))) }
    }

    /** کلِ فایلِ تراکنش‌ها. */
    fun transactions(list: List<Transaction>): String = buildString {
        appendLine(row(TX_HEADER))
        list.forEach { appendLine(row(txCells(it))) }
    }
}
