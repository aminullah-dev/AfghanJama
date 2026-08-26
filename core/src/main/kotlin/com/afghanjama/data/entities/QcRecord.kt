package com.afghanjama.data.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * رکورد کنترل کیفیت برای یک سفارش در مرحلهٔ نظارت: نتیجه (تأیید/برگشت)،
 * مشکلِ ثبت‌شده، ناظر و تاریخ. تاریخچهٔ کامل کیفیت هر سفارش را نگه می‌دارد.
 */
@Entity(
    tableName = "qc_records",
    indices = [Index(value = ["orderId"])]
)
data class QcRecord(
    @PrimaryKey val id: UUID = UUID.randomUUID(),
    val orderId: String,
    val orderCode: String,
    val inspector: String,
    val result: String,               // APPROVED (تأیید) / REJECTED (برگشت برای اصلاح)
    val problem: String = "",         // شرح مشکل (برای برگشت)
    val note: String = "",

    /**
     * کارِ کدام خیاط برگشت خورد. وقتی سفارش بینِ چند خیاط تقسیم شده،
     * تنها کسی که می‌داند مشکل مالِ کدام است ناظری است که همان لحظه
     * جلوی چشمش کار را دیده — پس همان‌جا از او پرسیده می‌شود.
     * خالی یعنی معلوم نیست (یا رکوردِ قدیمیِ پیش از این ستون است)؛
     * در آن حالت مثل قبل فقط سفارشِ تک‌خیاطه قابلِ انتساب می‌ماند.
     */
    @ColumnInfo(defaultValue = "")
    val tailor: String = "",

    val createdAt: Long = System.currentTimeMillis()
)
