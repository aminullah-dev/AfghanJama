package com.afghanjama.data.entities

import com.afghanjama.util.nowMillis
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * یک اندازهٔ بدنِ مشتری (قد، دور سینه، دور کمر، شانه، آستین، ...).
 * برچسب و مقدار آزادند تا هر کارگاه اصطلاح خودش را به‌کار ببرد؛ مقدار
 * متن است تا واحد هم بتواند همراهش باشد («۹۸» یا «۹۸ سانت»).
 */
@Entity(
    tableName = "customer_measurements",
    indices = [Index(value = ["customerId"])]
)
data class CustomerMeasurement(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val customerId: Long,
    val label: String,
    val value: String,
    val updatedAt: Long = nowMillis()
)
