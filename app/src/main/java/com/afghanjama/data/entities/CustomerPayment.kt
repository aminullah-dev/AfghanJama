package com.afghanjama.data.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "customer_payments",
    indices = [Index(value = ["orderId"])]
)
data class CustomerPayment(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val orderId: String,              // UUID.toString()
    val amount: Long,
    val source: String,               // "CASH" / "WALLET" / ...
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis()
)
