package com.afghanjama.data.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * خرج‌کارهای به‌کاررفته در یک سفارش (دکمه، لایی چسب، نوار زیبایی، ...).
 * قیمت هر ردیف «فی‌عدد» است؛ خرج کار کل سفارش = مجموع × تعداد.
 * فیلد workCost روی Order به عنوان «خلاصه» (جمع کل) پر می‌شود تا صفحات
 * دیگر بدون تغییر کار کنند.
 */
@Entity(
    tableName = "order_work_items",
    indices = [Index(value = ["orderId"])]
)
data class OrderWorkItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val orderId: String,              // UUID.toString()
    val title: String,
    val price: Long                   // قیمت فی‌عدد
)
