package com.afghanjama.data.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * انبار عمومی مواد خام: هر قلم با نام و واحد دلخواهِ کاربر یک ردیف دارد
 * (پارچه، دکمه، زیپ، نخ، لایی، ...). خرید موجودی را زیاد و مصرفِ تولید
 * آن را کم می‌کند. قیمت میانگین وزنی برای بهای تمام‌شده نگه داشته می‌شود.
 */
@Entity(
    tableName = "material_stock",
    indices = [Index(value = ["name", "unit"], unique = true)]
)
data class MaterialStock(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,                 // نام آزاد قلم
    val unit: String,                 // واحد آزاد: متر، عدد، بسته، کیلو...
    val amount: Double,               // موجودی فعلی

    @ColumnInfo(defaultValue = "0")
    val avgPrice: Double = 0.0,       // میانگین قیمت هر واحد

    @ColumnInfo(defaultValue = "0")
    val minLevel: Double = 0.0,       // حد هشدار کمبود

    val updatedAt: Long
)
