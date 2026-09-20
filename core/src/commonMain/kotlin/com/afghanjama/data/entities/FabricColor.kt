// FabricColor.kt
package com.afghanjama.data.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "fabric_colors",
    indices = [Index(value = ["title"], unique = true)]
)
data class FabricColor(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val hex: String? = null
)
