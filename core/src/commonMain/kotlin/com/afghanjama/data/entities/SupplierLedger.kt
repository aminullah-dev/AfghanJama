package com.afghanjama.data.entities

import com.afghanjama.util.nowMillis
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.afghanjama.util.UUID
import com.afghanjama.util.randomUuid
import com.afghanjama.util.uuidOf

/**
 * دفتر حساب فروشنده (نسیه/قرض). خرید نسیه یک ردیف CREDIT (بدهی جدید) و
 * تسویه یک ردیف PAYMENT (کاهش بدهی) ثبت می‌کند. مانده هر فروشنده =
 * مجموع CREDIT منهای مجموع PAYMENT.
 */
@Entity(
    tableName = "supplier_ledger",
    indices = [Index(value = ["supplier"])]
)
data class SupplierLedger(
    @PrimaryKey val id: UUID = randomUuid(),
    val supplier: String,
    val amount: Long,                 // همیشه مثبت
    val type: String,                 // CREDIT (قرض جدید) / PAYMENT (تسویه)
    val note: String = "",
    val createdAt: Long = nowMillis()
)
