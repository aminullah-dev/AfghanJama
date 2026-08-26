package com.afghanjama.pdf

import com.afghanjama.data.entities.CustomerPayment
import com.afghanjama.data.entities.FabricUnit
import com.afghanjama.data.entities.Order
import com.afghanjama.data.entities.OrderFabric
import com.afghanjama.data.entities.OrderWorkItem
import com.afghanjama.ui.format.PersianDate
import com.afghanjama.ui.format.afn
import com.afghanjama.ui.format.fa

/**
 * فاکتورِ رسمیِ یک سفارش — چیدمانِ مشترک.
 *
 * چندبرگه‌ای است: سفارشی با سی قلم پارچه در یک A4 جا نمی‌شود. هر جا
 * بدنه به [Paper.bodyBottom] برسد، برگهٔ تازه با سربرگِ «(ادامه)» باز
 * می‌شود — همان کاری که نسخهٔ اندروید می‌کند.
 *
 * QR اینجا نیست: کدگذارش هنوز مشترک نشده. نسخهٔ اندروید داردش.
 */
fun invoiceSheets(
    order: Order,
    fabrics: List<OrderFabric>,
    workItems: List<OrderWorkItem>,
    payments: List<CustomerPayment>,
    shop: ShopInfo,
    measurer: TextMeasurer,
    now: Long = System.currentTimeMillis(),
    paper: Paper = Paper.A4
): SheetDoc {
    val pages = mutableListOf<Sheet>()
    var b = SheetBuilder(paper)
    var c = DocChrome(b, paper, measurer, shop)
    var pageNo = 1
    var y = c.header("فاکتور سفارش", order.orderCode, now)

    /** اگر [needed] نقطه جا نماند، برگه بسته و برگهٔ تازه باز می‌شود. */
    fun breakIfNeeded(needed: Float) {
        if (y + needed <= paper.bodyBottom) return
        c.footer(pageNo)
        pages += b.build()
        pageNo++
        b = SheetBuilder(paper)
        c = DocChrome(b, paper, measurer, shop)
        y = c.header("فاکتور سفارش (ادامه)", order.orderCode, now)
    }

    // ---------- مشخصات ----------
    y = c.section("مشخصات", y)
    y = c.kv("کد سفارش", order.orderCode, y)
    y = c.kv("تاریخ صدور", PersianDate.long(now), y)
    if (order.customerName.isNotBlank()) {
        val phone = order.customerPhone.takeIf { it.isNotBlank() }?.let { " — $it" } ?: ""
        y = c.kv("مشتری", order.customerName + phone, y)
    }
    y = c.kv("طرح / محصول", order.designTitle.ifBlank { "-" }, y)
    y = c.kv("تعداد", "${order.qty.fa()} عدد", y)
    if (order.size.isNotBlank()) y = c.kv("سایز", order.size, y)
    if (order.dueDate > 0) y = c.kv("مهلت تحویل", PersianDate.long(order.dueDate), y)
    // همان کفِ نسخهٔ اندروید: جای QR باید محفوظ بماند حتی وقتی
    // مشخصات کوتاه است، وگرنه جدول روی آن می‌نشیند.
    y = maxOf(y, 190f) + 6f

    // ---------- اقلام ----------
    if (fabrics.isNotEmpty()) {
        breakIfNeeded(70f)
        y = c.section("پارچه و مواد", y)
        val w = listOf(3.2f, 1.6f, 1.4f, 1.8f)
        y = c.tableHeader(listOf("قلم", "رنگ", "مقدار", "مبلغ"), w, y)
        fabrics.forEachIndexed { i, fab ->
            breakIfNeeded(24f)
            val unitFa = when (fab.fabricUnit.uppercase()) {
                FabricUnit.METER.name -> "متر"
                FabricUnit.YARD.name -> "یارد"
                else -> fab.fabricUnit
            }
            val amount = fab.amount.toString().trimEnd('0').trimEnd('.')
            y = c.tableRow(
                listOf(fab.fabricType, fab.fabricColor, "$amount $unitFa", fab.price.afn()),
                w, y, zebra = i % 2 == 1
            )
        }
        y += 8f
    }

    if (workItems.isNotEmpty()) {
        breakIfNeeded(70f)
        y = c.section("خرج‌کارها (فی‌عدد)", y)
        val w = listOf(5f, 2f)
        y = c.tableHeader(listOf("شرح", "مبلغ"), w, y)
        workItems.forEachIndexed { i, item ->
            breakIfNeeded(24f)
            y = c.tableRow(listOf(item.title, item.price.afn()), w, y, zebra = i % 2 == 1)
        }
        y += 8f
    }

    // ---------- خلاصهٔ مالی ----------
    breakIfNeeded(150f)
    y = c.section("خلاصهٔ مالی", y)
    y = c.kv("جمع پارچه و مواد", order.fabricPrice.afn(), y)
    if (order.workCost > 0) y = c.kv("جمع خرج کار", order.workCost.afn(), y)
    if (order.sewingCost > 0) y = c.kv("دستمزد دوخت", order.sewingCost.afn(), y)

    val gross = if (order.agreedPrice > 0) order.agreedPrice
    else order.fabricPrice + order.workCost + order.sewingCost
    val paid = payments.sumOf { it.amount }
    if (paid != 0L) y = c.kv("دریافتی تا امروز", paid.afn(), y)

    y += 4f
    val due = gross - paid
    y = c.totalBox(
        if (due > 0) "قابل پرداخت" else "تسویه‌شده",
        (if (due > 0) due else gross).afn(),
        y
    )
    if (paid != 0L && due > 0) {
        y = c.kv("جمع کل فاکتور", gross.afn(), y)
    }

    // ---------- امضا و یادداشت ----------
    breakIfNeeded(90f)
    y += 10f
    y = c.signatures(y, "مهر و امضای فروشنده", "امضای مشتری")
    y += 6f
    c.note(
        "کالای فروخته‌شده مطابق مشخصات این فاکتور تحویل می‌گردد. " +
            "برای پیگیری، کد سفارش را همراه داشته باشید.",
        y
    )

    c.footer(pageNo)
    pages += b.build()
    return SheetDoc(pages)
}
