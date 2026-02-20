package com.afghanjama.data.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "fabric_types",
    indices = [Index(value = ["title"], unique = true)]
)
data class FabricType(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String
)
