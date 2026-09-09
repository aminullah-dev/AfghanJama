package com.afghanjama.data.entities

import com.afghanjama.util.nowMillis
import androidx.room.ColumnInfo
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
    val at: Long = nowMillis(),

    /**
     * چه کسی مرحله را جلو برد — نام و نقشِ کاربرِ آن لحظه.
     *
     * سطرهای پیش از نسخهٔ ۶۳ خالی‌اند و صفحه برایشان چیزی نشان
     * نمی‌دهد؛ نبودنِ نام بهتر از نامِ حدسی است.
     */
    @ColumnInfo(defaultValue = "") val user: String = "",
    @ColumnInfo(defaultValue = "") val role: String = ""
)
