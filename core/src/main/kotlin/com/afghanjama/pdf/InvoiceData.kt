package com.afghanjama.pdf

import com.afghanjama.ui.format.fa
import com.afghanjama.ui.format.toPersianDigits

/** یک قلمِ فاکتور، مستقل از اینکه فروش باشد یا خرید. */
data class InvoiceLine(
    val code: String,
    val name: String,
    /** فروش عددِ صحیح است و خرید می‌تواند اعشاری باشد (۲٫۵ متر پارچه). */
    val qty: Double,
    val unit: String,
    val unitPrice: Long,
    val total: Long
) {
    val qtyText: String get() = qty.qtyFa()
}

/**
 * تعداد را برای چاپ می‌نویسد: عددِ درست بدونِ «٫۰» اضافه، و اعشاری بدونِ
 * صفرهای دنباله‌دار. «۴» نه «۴٫۰»، و «۲٫۵» نه «۲٫۵۰۰۰».
 */
internal fun Double.qtyFa(): String {
    val whole = toLong()
    if (this == whole.toDouble()) return whole.fa()
    return toString().trimEnd('0').trimEnd('.').toPersianDigits()
}

/**
 * دادهٔ یک برگهٔ فاکتور. عمداً از موجودیت‌های دیتابیس جداست تا یک چیدمان
 * هم فاکتور فروش را چاپ کند هم فاکتور خرید — همان یک‌دستی که کارگاه
 * می‌خواهد، بدونِ دو نسخهٔ موازی که با هم فرق می‌کنند.
 */
data class InvoiceData(
    val title: String,
    val number: String,
    val at: Long,
    /** «خریدار» در فاکتور فروش، «فروشنده» در فاکتور خرید. */
    val partyLabel: String,
    val partyName: String,
    val partyPhone: String = "",
    val lines: List<InvoiceLine>,
    /** جمعِ تخفیفِ ردیف‌ها — از جمعِ ردیف‌ها کم شده و فقط نشان داده می‌شود. */
    val discount: Long = 0,
    val paid: Long = 0,
    /** ماندهٔ طرفِ حساب پیش از این فاکتور؛ مثبت یعنی به ما بدهکار بوده. */
    val previousDue: Long = 0,
    val note: String = ""
) {
    val subtotal: Long get() = lines.sumOf { it.total }

    /** آنچه بعد از این فاکتور باید بپردازد. */
    val payable: Long get() = subtotal + previousDue - paid
}

/**
 * فاکتورِ ردیف‌دار — برگه‌ای که مشتری با خودش می‌برد.
 *
 * چرا یک فایل برای فروش و خرید هر دو: تنها فرقشان عنوان و برچسبِ طرفِ
 * حساب است. دو چیدمانِ جدا یعنی روزی یکی درست شود و دیگری عقب بماند.
 */
