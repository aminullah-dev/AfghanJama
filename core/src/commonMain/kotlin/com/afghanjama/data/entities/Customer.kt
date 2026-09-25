// Customer.kt
package com.afghanjama.data.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "customers",
    indices = [Index(value = ["name"], unique = true)]
)
data class Customer(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,          // نام خریدار
    val phone: String? = null, // اختیاری

    /**
     * نشانیِ مشتری — برای رساندنِ سفارش و پیدا کردنش.
     *
     * `defaultValue` لازم است، نه سلیقه: ستون روی جدولِ پُر با
     * `ALTER TABLE … DEFAULT ''` اضافه می‌شود و Room سرِ باز کردن هر دو
     * را با هم می‌سنجد. اگر یکی جا بماند دفتر باز نمی‌شود.
     */
    @ColumnInfo(defaultValue = "")
    val address: String = ""
)
