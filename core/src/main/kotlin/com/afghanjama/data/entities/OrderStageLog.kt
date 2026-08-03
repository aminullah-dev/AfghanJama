package com.afghanjama.data.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** تاریخچه کامل جابه‌جایی سفارش بین مراحل (برای تایم‌لاین و حسابرسی). */
@Entity(
    tableName = "order_stage_logs",
    indices = [Index(value = ["orderId"])]
)
data class OrderStageLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val orderId: String,          // UUID.toString()
    val orderCode: String,
    val fromStatus: String,       // "NEW" برای ایجاد سفارش
    val toStatus: String,
    val at: Long = System.currentTimeMillis()
)
