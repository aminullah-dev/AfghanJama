package com.afghanjama.data.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * رکورد واقعیِ مرحلهٔ برش برای یک سفارش: چه کسی برید، چند دست برش خورد،
 * چه‌قدر ضایعات شد و یادداشت. مواد قبلاً هنگام شروع تولید از انبار کسر
 * شده‌اند؛ این رکورد جزئیاتِ عملیاتیِ برش را مستند می‌کند.
 */
@Entity(
    tableName = "cutting_records",
    indices = [Index(value = ["orderId"])]
)
data class CuttingRecord(
    @PrimaryKey val id: UUID = UUID.randomUUID(),
    val orderId: String,
    val orderCode: String,
    val cutter: String,               // مسئول برش
    val pieces: Int,                  // تعداد دستِ برش‌شده
    val waste: String = "",           // ضایعات (متن آزاد، مثلاً «۲ متر»)
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis()
)
