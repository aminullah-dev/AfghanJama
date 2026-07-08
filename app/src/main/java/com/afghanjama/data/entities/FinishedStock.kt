package com.afghanjama.data.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * انبار محصول نهایی: لباس‌های آمادهٔ فروش که پس از تکمیل تولید به انبار
 * برمی‌گردند. برای هر ترکیب نام محصول + سایز یک ردیف؛ فروش جزئی موجودی
 * را کم می‌کند. بهای تمام‌شدهٔ هر عدد (میانگین) برای محاسبهٔ سود نگه
 * داشته می‌شود.
 */
@Entity(
    tableName = "finished_stock",
    indices = [Index(value = ["name", "size"], unique = true)]
)
data class FinishedStock(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,                 // نام محصول (طرح)
    val size: String,                 // سایز (خالی مجاز است)
    val qty: Int,                     // موجودی عددی

    @ColumnInfo(defaultValue = "0")
    val avgCost: Long = 0,            // بهای تمام‌شدهٔ هر عدد (میانگین)

    val updatedAt: Long
)
