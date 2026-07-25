package com.afghanjama.data.entities

import androidx.room.ColumnInfo
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

    /** چند عدد از این فروش تا حالا مرجوع شده (برگشت از فروش). */
    @ColumnInfo(defaultValue = "0")
    val returnedQty: Int = 0,

    val createdAt: Long = System.currentTimeMillis()
) {
    /** تعدادِ باقی‌مانده که هنوز قابلِ برگشت است. */
    val returnableQty: Int get() = (qty - returnedQty).coerceAtLeast(0)

    /** بهای تمام‌شدهٔ هر عدد در همین فروش (مبنای برگشت به انبار). */
    val unitCost: Long get() = if (qty > 0) cost / qty else 0L

    /** فروشِ خالص پس از کسرِ مرجوعی‌ها. */
    val netTotal: Long get() = total - returnedQty * unitPrice

    /** بهای تمام‌شدهٔ خالص پس از کسرِ مرجوعی‌ها. */
    val netCost: Long get() = cost - returnedQty * unitCost
}
