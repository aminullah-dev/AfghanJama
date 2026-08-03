package com.afghanjama.data.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * کارمزد خیاط برای هر سفارش.
 * وقتی دوخت یک سفارش تمام شد (ارسال به بازرسی) یک رکورد ثبت می‌شود
 * و در تسویه هفتگی، همه رکوردهای تسویه‌نشده یک خیاط یکجا تسویه
 * و به بخش مالی عمومی (تراکنش‌ها) منتقل می‌شود.
 */
@Entity(
    tableName = "tailor_wages",
    indices = [Index(value = ["orderId"])]
)
data class TailorWage(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val orderId: String,              // UUID.toString()
    val orderCode: String,
    val tailorLabel: String,          // مثلاً: [T10] احمد
    val amount: Long,
    // تحویل مرتبط (۰ برای کارمزدهای قدیمیِ کل‌سفارش). برای هر تحویل یک کارمزد.
    @androidx.room.ColumnInfo(defaultValue = "0")
    val assignmentId: Long = 0,
    val settled: Boolean = false,
    val settledAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis()
)
