package com.afghanjama.data.entities

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

    val fabricPrice: Long,
    val workCost: Long,

    val customerName: String,
    val customerPhone: String,

    val status: String,

    val assignedTailor: String? = null,
    val assignedInspector: String? = null,

    val underSewCount: Int = 0,
    val doneSewCount: Int = 0,

    val reviewed: Boolean = false,

    val createdAt: Long = System.currentTimeMillis()
)
