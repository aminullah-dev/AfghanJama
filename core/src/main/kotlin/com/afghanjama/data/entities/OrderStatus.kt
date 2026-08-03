// app/src/main/java/com/afghanjama/data/entities/OrderStatus.kt
package com.afghanjama.data.entities

enum class OrderStatus {
    IN_STOCK,
    CUTTING,
    CUT_DONE,
    SEWING,
    REVIEW,
    SALES,
    STORED,     // تکمیل‌شده و در انبار محصول نهایی (آمادهٔ فروش جزئی)
    SENT
}