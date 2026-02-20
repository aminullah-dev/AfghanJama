package com.afghanjama.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity
data class PurchasePlan(
    @PrimaryKey val id: UUID = UUID.randomUUID(),
    val fabricType: String = "",
    val fabricColor: String = "",
    val fabricPrice: Long = 0,
    val workCost: Long = 0,
    val paymentSource: String = "WALLET"
)
