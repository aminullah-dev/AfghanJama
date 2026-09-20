// Inspector.kt
package com.afghanjama.data.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "inspectors",
    indices = [Index(value = ["code"], unique = true)]
)
data class Inspector(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val code: String,
    val name: String,
    val phone: String? = null
)
