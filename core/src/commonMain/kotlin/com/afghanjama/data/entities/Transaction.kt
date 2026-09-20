package com.afghanjama.data.entities

import com.afghanjama.util.nowMillis
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.afghanjama.util.UUID
import com.afghanjama.util.randomUuid
import com.afghanjama.util.uuidOf

@Entity(tableName = "finance_transactions")
data class Transaction(

    @PrimaryKey
    val id: UUID = randomUuid(),

    val orderId: UUID? = null,

    val type: String,      // IN / OUT
    val source: String,    // WALLET / PROFIT
    val amount: Long,
    val note: String = "",

    // دسته هزینه (کرایه، برق و آب، معاش، ...) — خالی یعنی تراکنش عادی
    @ColumnInfo(defaultValue = "")
    val category: String = "",

    val createdAt: Long = nowMillis()
)
