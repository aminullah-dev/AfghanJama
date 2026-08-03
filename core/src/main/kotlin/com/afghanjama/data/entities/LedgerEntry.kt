package com.afghanjama.data.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * یک سندِ دفتر کل روی حسابِ یک طرف. قرارداد (از دیدِ دفترِ ما):
 *  - debit  (بدهکار): طرف به ما بدهکارتر می‌شود، یا ما بدهی‌مان به او را می‌پردازیم.
 *  - credit (بستانکار): ما به طرف بدهکارتر می‌شویم، یا او طلبش را از ما می‌گیرد.
 * ماندهٔ هر طرف = مجموع debit − مجموع credit
 *   > مثبت: طرف به ما بدهکار است (مثل مشتری)،
 *   > منفی: ما به طرف بدهکاریم (مثل تأمین‌کننده یا خیاط).
 */
@Entity(
    tableName = "ledger_entries",
    indices = [Index(value = ["partyType", "partyName"])]
)
data class LedgerEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val partyType: String,
    val partyName: String,
    val debit: Long = 0,
    val credit: Long = 0,
    val refType: String,       // WAGE / WAGE_PAID / PURCHASE_CREDIT / SUPPLIER_PAYMENT / CUSTOMER_* / MANUAL ...
    val refId: String = "",    // کد سفارش / کد فاکتور
    val note: String = "",
    val at: Long = System.currentTimeMillis()
)

/** برچسبِ فارسیِ نوعِ سندِ دفتر کل — منبعِ واحد برای UI و PDF. */
fun ledgerRefLabel(r: String): String = when (r) {
    "WAGE" -> "کارمزد دوخت"
    "WAGE_PAID" -> "پرداخت کارمزد"
    "SALARY" -> "حقوق ماهانه"
    "SALARY_PAID" -> "پرداخت حقوق"
    "PURCHASE_CREDIT" -> "خرید نسیه"
    "PURCHASE_RETURN" -> "برگشت از خرید"
    "SUPPLIER_PAYMENT" -> "پرداخت به فروشنده"
    "CUSTOMER_ADVANCE" -> "پیش‌پرداخت مشتری"
    "PREPAY_APPLIED" -> "اعمال بیعانه روی فروش"
    "CUSTOMER_SALE" -> "فروش"
    "CUSTOMER_MANUAL" -> "دریافت دستی"
    "CUSTOMER_RETURN" -> "برگشتی فروش"
    "SALE_BILLING" -> "بدهی بابت سفارش"
    "SALE_ADJUST" -> "اصلاح سفارش"
    "SALE_CANCEL" -> "لغو سفارش"
    "SALE_TO_STOCK" -> "انتقال به انبار محصول"
    "DISCOUNT" -> "تخفیف فروش"
    "MANUAL" -> "سند دستی"
    else -> r
}

/** برچسبِ فارسیِ نوعِ طرف‌حساب. */
fun partyTypeLabel(t: String): String = when (t) {
    "SUPPLIER" -> "تأمین‌کننده"
    "CUSTOMER" -> "مشتری"
    "TAILOR" -> "خیاط"
    "INSPECTOR" -> "ناظر"
    "EMPLOYEE" -> "کارمند"
    else -> t
}
