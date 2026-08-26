package com.afghanjama.data.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * سندِ ژورنالِ حسابداری دوطرفه: هر رویدادِ مالی یک سند، و هر سند چند
 * سطر (journal_lines) دارد که جمعِ بدهکارش همیشه برابرِ جمعِ بستانکار است.
 */
@Entity(tableName = "journal_entries")
data class JournalEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val memo: String,
    val refType: String = "",   // PURCHASE / SALE / WAGE / EXPENSE / OPENING / ...
    val refId: String = "",     // کد فاکتور/سفارش مرتبط
    val at: Long = System.currentTimeMillis()
)

/** یک سطرِ سند: یک حساب، یا بدهکار یا بستانکار. */
@Entity(
    tableName = "journal_lines",
    indices = [Index(value = ["entryId"]), Index(value = ["account"])]
)
data class JournalLine(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val entryId: Long,
    val account: String,        // کدِ حساب از Accounts
    val debit: Long = 0,
    val credit: Long = 0
)

/**
 * دفترِ حساب‌های کلِ کارگاه (Chart of Accounts).
 * کلاسِ حساب از رقمِ اول کد: 1=دارایی 2=بدهی 3=سرمایه 4=درآمد 5=هزینه
 */
object Accounts {
    const val CASH = "1010"            // صندوق
    const val PROFIT_BOX = "1015"      // صندوق فایده
    const val BANK = "1020"            // بانک
    const val RECEIVABLE = "1030"      // حساب‌های دریافتنی (مشتریان)
    const val STAFF_ADVANCE = "1035"   // پیش‌پرداخت کارکنان (طلبِ ما از آن‌ها)
    const val MATERIALS = "1040"       // موجودی مواد
    const val WIP = "1045"             // کار در جریان تولید
    const val FINISHED = "1050"        // موجودی محصول نهایی
    const val PAYABLE = "2010"         // حساب‌های پرداختنی (تأمین‌کنندگان)
    const val WAGES_PAYABLE = "2020"   // کارمزد پرداختنی (خیاطان)
    const val CUSTOMER_PREPAY = "2030" // پیش‌دریافت مشتری (هنوز تحویل نداده‌ایم)
    const val EQUITY = "3010"          // سرمایه
    const val SALES = "4010"           // فروش
    const val OTHER_INCOME = "4090"    // سایر درآمد
    const val COGS = "5010"            // بهای تمام‌شدهٔ فروش
    const val EXPENSES = "5020"        // هزینه‌های عمومی

    fun label(code: String): String = when (code) {
        CASH -> "صندوق"
        PROFIT_BOX -> "صندوق فایده"
        BANK -> "بانک"
        RECEIVABLE -> "حساب‌های دریافتنی"
        STAFF_ADVANCE -> "پیش‌پرداخت کارکنان"
        MATERIALS -> "موجودی مواد"
        WIP -> "کار در جریان تولید"
        FINISHED -> "موجودی محصول نهایی"
        PAYABLE -> "حساب‌های پرداختنی"
        WAGES_PAYABLE -> "کارمزد پرداختنی"
        CUSTOMER_PREPAY -> "پیش‌دریافت مشتری"
        EQUITY -> "سرمایه"
        SALES -> "فروش"
        OTHER_INCOME -> "سایر درآمد"
        COGS -> "بهای تمام‌شدهٔ فروش"
        EXPENSES -> "هزینه‌های عمومی"
        else -> code
    }

    /** حسابِ متناظرِ هر صندوقِ نقد. */
    fun box(source: String): String = when (source) {
        "BANK" -> BANK
        "PROFIT" -> PROFIT_BOX
        else -> CASH
    }
}
