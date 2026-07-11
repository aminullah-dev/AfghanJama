// DesignItem.kt
package com.afghanjama.data.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "design_items",
    indices = [Index(value = ["title"], unique = true)]
)
data class DesignItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,  // نام طرح (مثلاً "لباس کودک - مدل A")
    // کد اختصاصی طرح (مثل D-003) — خودکار از id ساخته می‌شود
    @ColumnInfo(defaultValue = "")
    val code: String = ""
)
