package com.afghanjama.data.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * پارچه‌های به‌کاررفته در یک سفارش.
 * یک نوع لباس (مثلاً گند افغانی) می‌تواند از چند پارچه با نوع/رنگ/قیمت
 * متفاوت ساخته شود؛ هر پارچه یک ردیف در این جدول است.
 * فیلدهای تک‌پارچهٔ Order به عنوان «خلاصه» (اولین پارچه + جمع قیمت)
 * پر می‌شوند تا صفحات دیگر بدون تغییر کار کنند.
 */
@Entity(
    tableName = "order_fabrics",
    indices = [Index(value = ["orderId"])]
)
data class OrderFabric(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val orderId: String,              // UUID.toString()
    val fabricType: String,
    val fabricColor: String,
    val fabricUnit: String,           // METER / YARD
    val amount: Double,
    val price: Long,                  // NEW: قیمت خرید | STOCK: بهای تمام‌شده از میانگین
    val source: String                // NEW / STOCK
)
