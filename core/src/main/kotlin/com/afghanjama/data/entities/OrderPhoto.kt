package com.afghanjama.data.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * یک عکسِ چسبیده به سفارش: طرح، پارچه، یا نمونه‌ای که مشتری آورده.
 *
 * فقط نامِ فایل نگه داشته می‌شود نه مسیرِ کامل — چون مسیرِ پوشهٔ خصوصیِ
 * اپ می‌تواند بین دستگاه‌ها یا بعد از بازیابیِ پشتیبان فرق کند، ولی
 * نامِ فایل ثابت می‌ماند.
 */
@Entity(
    tableName = "order_photos",
    indices = [Index(value = ["orderId"])]
)
data class OrderPhoto(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val orderId: String,
    val orderCode: String,
    /** نامِ فایل داخلِ پوشهٔ عکس‌های سفارش. */
    val fileName: String,
    @ColumnInfo(defaultValue = "") val note: String = "",
    val createdAt: Long = System.currentTimeMillis()
)
