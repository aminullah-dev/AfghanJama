package com.afghanjama.data.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * کارمندِ کارگاه با هر سمتی (آشپز، حسابدار، مدیر، …) — جدا از کاتالوگ
 * خیاط/ناظر. در حضور و غیاب و حسابِ کارکنان (دفتر کل) استفاده می‌شود.
 */
@Entity(
    tableName = "staff",
    indices = [Index(value = ["name"], unique = true)]
)
data class Staff(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val role: String = "",     // سمت: آشپز، حسابدار، مدیر…
    val createdAt: Long = System.currentTimeMillis()
)
