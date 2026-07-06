package com.afghanjama.data.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * تحویل بخشی از یک سفارش به یک خیاط.
 * سفارشِ ۳۰ عددی می‌تواند بین چند خیاط تقسیم شود:
 * ۲۰ عدد به احمد، ۴ عدد به محمود، ۶ عدد به بلال — هر تحویل یک ردیف.
 * کارمزد هر تحویل = qty × unitWage و هنگام «دوخت تمام شد» ثبت می‌شود.
 */
@Entity(
    tableName = "sewing_assignments",
    indices = [Index(value = ["orderId"])]
)
data class SewingAssignment(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val orderId: String,              // UUID.toString()
    val orderCode: String,
    val tailorLabel: String,
    val qty: Int,
    val unitWage: Long,               // کارمزد هر عدد
    val status: String = "SEWING",    // SEWING / DONE
    val createdAt: Long = System.currentTimeMillis(),
    val doneAt: Long? = null
) {
    val totalWage: Long get() = unitWage * qty
}
