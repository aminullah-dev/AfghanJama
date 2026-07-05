package com.afghanjama.data.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * موجودی پارچه انبار: برای هر ترکیب نوع + رنگ + واحد یک ردیف.
 * خرید پارچه موجودی را زیاد و ثبت سفارشِ «از موجودی» آن را کم می‌کند.
 */
@Entity(
    tableName = "fabric_stock",
    indices = [Index(value = ["fabricType", "fabricColor", "fabricUnit"], unique = true)]
)
data class FabricStock(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fabricType: String,
    val fabricColor: String,
    val fabricUnit: String,           // METER / YARD
    val amount: Double,               // موجودی فعلی
    val minLevel: Double,             // حد هشدار کمبود

    // قیمت میانگین خرید هر واحد (برای بهای تمام‌شده سفارش‌های «از موجودی»)
    @ColumnInfo(defaultValue = "0")
    val avgPrice: Double = 0.0,

    val updatedAt: Long
)
