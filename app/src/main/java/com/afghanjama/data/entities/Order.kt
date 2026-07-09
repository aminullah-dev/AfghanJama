package com.afghanjama.data.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "orders")
data class Order(

    @PrimaryKey
    val id: UUID = UUID.randomUUID(),

    val orderCode: String,
    val shortCode: String,

    val designTitle: String,
    val qty: Int,

    val fabricType: String,
    val fabricColor: String,
    val size: String,

    val fabricUnit: String,
    val fabricAmount: Double,

    // منبع پارچه: NEW = خرید جدید برای همین سفارش، STOCK = مصرف از موجودی انبار
    @ColumnInfo(defaultValue = "NEW")
    val fabricSource: String = "NEW",

    val fabricPrice: Long,
    val workCost: Long,

    // دستمزد دوختِ انباشته از تحویل‌های خیاط (بخشی از بهای تمام‌شده)
    @ColumnInfo(defaultValue = "0")
    val sewingCost: Long = 0,

    // قیمت فروش توافق‌شده با مشتری (۰ = توافق نشده)
    @ColumnInfo(defaultValue = "0")
    val agreedPrice: Long = 0,

    val customerName: String,
    val customerPhone: String,

    val status: String,

    val assignedTailor: String? = null,
    val assignedInspector: String? = null,

    val underSewCount: Int = 0,
    val doneSewCount: Int = 0,

    val reviewed: Boolean = false,

    // زمان ورود به مرحله فعلی (برای هشدار معطلی)
    @ColumnInfo(defaultValue = "0")
    val stageChangedAt: Long = 0,

    val createdAt: Long = System.currentTimeMillis()
)
