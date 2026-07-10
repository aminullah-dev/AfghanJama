package com.afghanjama.data.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * سندِ مالی با شمارهٔ یکتا: فاکتور خرید/فروش، رسید کارمزد، پرداخت به
 * فروشنده، دریافت از مشتری، برگشت، پیش‌فاکتور. هر رویدادِ مالیِ مهم یک
 * سند تولید می‌کند تا ردِ حسابرسی و قابلیتِ اشتراک‌گذاری داشته باشیم.
 */
@Entity(
    tableName = "documents",
    indices = [Index(value = ["number"], unique = true), Index(value = ["type"])]
)
data class Document(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val number: String,        // شمارهٔ یکتا مثل KH-00001
    val type: String,          // PURCHASE / SALE / SUPPLIER_PAYMENT / WAGE_RECEIPT / CUSTOMER_RECEIPT / RETURN / PROFORMA
    val partyName: String = "",
    val amount: Long,
    val refId: String = "",    // کد سفارش / کد فاکتورِ مرتبط
    val note: String = "",
    val at: Long = System.currentTimeMillis()
)
