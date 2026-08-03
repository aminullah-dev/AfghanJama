package com.afghanjama.data.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * کاردکس/گردشِ انبار مواد: هر تغییر موجودی یک ردیف با «دلیل» ثبت می‌کند
 * تا رد حسابرسی کامل باشد — چه مقدار وارد/مصرف/ضایعات/اصلاح شد و چرا.
 */
@Entity(
    tableName = "stock_movements",
    indices = [Index(value = ["name"])]
)
data class StockMovement(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val unit: String,
    val delta: Double,               // + ورود / − خروج
    val reason: String,              // خرید / مصرف تولید / مصرف سفارش / ضایعات / برگشت / اصلاح
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis()
)
