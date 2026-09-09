package com.afghanjama.data.entities

import com.afghanjama.util.nowMillis
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.afghanjama.util.UUID
import com.afghanjama.util.randomUuid
import com.afghanjama.util.uuidOf

/**
 * سرِ یک فاکتور خرید مواد خام (یک رویداد خرید با چند قلم).
 * مبلغ کل از منبعِ انتخابی پرداخت می‌شود و همهٔ اقلام وارد انبار می‌شوند.
 */
@Entity(tableName = "purchase_invoices")
data class PurchaseInvoice(
    @PrimaryKey val id: UUID = randomUuid(),
    val code: String,
    val supplier: String = "",        // نام فروشنده/تأمین‌کننده (اختیاری)
    val note: String = "",
    val total: Long,                  // جمع کل خرید
    val paySource: String,            // WALLET / BANK / PROFIT / CUSTOMER
    val createdAt: Long = nowMillis()
)
