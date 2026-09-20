package com.afghanjama.data.entities

import com.afghanjama.util.nowMillis
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "customer_payments",
    indices = [Index(value = ["orderId"])]
)
data class CustomerPayment(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val orderId: String,              // UUID.toString() — برای دریافتی دستی خالی است
    val customerName: String = "",    // برای دریافتی‌های دستی (بدون سفارش)
    val amount: Long,
    val source: String,               // "ADVANCE" / "SALE" / "MANUAL"
    val note: String = "",
    val createdAt: Long = nowMillis()
)
