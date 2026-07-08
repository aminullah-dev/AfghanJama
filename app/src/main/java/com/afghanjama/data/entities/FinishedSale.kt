package com.afghanjama.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * سابقهٔ یک فروش جزئی از انبار محصول نهایی.
 */
@Entity(tableName = "finished_sales")
data class FinishedSale(
    @PrimaryKey val id: UUID = UUID.randomUUID(),
    val code: String,
    val productName: String,
    val size: String,
    val qty: Int,
    val unitPrice: Long,
    val total: Long,                  // qty × unitPrice
    val cost: Long,                   // qty × بهای تمام‌شده
    val customerName: String = "",
    val createdAt: Long = System.currentTimeMillis()
)
