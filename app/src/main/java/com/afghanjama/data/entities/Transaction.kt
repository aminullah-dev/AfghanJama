package com.afghanjama.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "finance_transactions")
data class Transaction(

    @PrimaryKey
    val id: UUID = UUID.randomUUID(),

    val orderId: UUID? = null,

    val type: String,      // IN / OUT
    val source: String,    // WALLET / PROFIT
    val amount: Long,
    val note: String = "",

    val createdAt: Long = System.currentTimeMillis()
)
