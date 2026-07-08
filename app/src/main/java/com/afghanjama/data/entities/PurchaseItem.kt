package com.afghanjama.data.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * یک قلمِ فاکتور خرید. نام و واحد کاملاً آزاد و توسط کاربر وارد می‌شود.
 */
@Entity(
    tableName = "purchase_items",
    indices = [Index(value = ["invoiceId"])]
)
data class PurchaseItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val invoiceId: String,            // UUID فاکتور به‌صورت متن
    val name: String,
    val unit: String,
    val qty: Double,
    val unitPrice: Long,              // قیمت هر واحد
    val total: Long                   // qty × unitPrice
)
