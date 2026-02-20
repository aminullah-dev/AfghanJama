// SizeItem.kt
package com.afghanjama.data.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "sizes",
    indices = [Index(value = ["title"], unique = true)]
)
data class SizeItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String
)
