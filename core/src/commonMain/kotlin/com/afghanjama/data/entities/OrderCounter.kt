package com.afghanjama.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class OrderCounter(
    @PrimaryKey val id: Int = 1,
    val nextNumber: Int = 1
)
