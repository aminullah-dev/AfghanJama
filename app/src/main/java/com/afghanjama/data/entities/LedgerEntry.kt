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
