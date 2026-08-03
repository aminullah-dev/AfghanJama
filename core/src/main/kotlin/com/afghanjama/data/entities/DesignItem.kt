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
    val code: String = "",

    /**
     * دستهٔ طرح (پیراهن، کت، چادری…) — پوشهٔ انبارِ محصول.
     *
     * خالی مجاز است و یعنی «دسته‌بندی‌نشده»؛ پس طرح‌های موجود دست‌نخورده
     * می‌مانند و انبار بی هیچ کارِ دستی همان‌طور که بود کار می‌کند.
     *
     * محصولی که از نظارت وارد انبار می‌شود نامِ طرحش را با خودش دارد، پس
     * از همین میدان می‌فهمد سرِ کدام پوشه برود — بی اینکه کسی جابه‌جایش کند.
     */
    @ColumnInfo(defaultValue = "")
    val category: String = ""
)
