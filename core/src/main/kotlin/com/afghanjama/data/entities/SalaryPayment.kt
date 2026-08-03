package com.afghanjama.data.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * پرداختِ حقوقِ ماهانهٔ یک کارمند (آشپز، حسابدار، مدیر، …).
 * جدا از کارمزدِ خیاط است: کارمزد بر اساسِ تعدادِ کار، اما حقوق ماهانه
 * و ثابت است. هر پرداخت یک رسید و یک سندِ هزینه در ژورنال می‌سازد.
 */
@Entity(
    tableName = "salary_payments",
    indices = [Index(value = ["employee"]), Index(value = ["at"])]
)
data class SalaryPayment(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val employee: String,
    val amount: Long,
    /** کلیدِ ماهِ شمسی مثل «1404-06» — برای جلوگیری از پرداختِ دوباره. */
    val periodKey: String = "",
    /** برچسبِ خواناى ماه مثل «سنبله ۱۴۰۴». */
    val periodLabel: String = "",
    /** صندوقِ پرداخت: WALLET / BANK / PROFIT. */
    val source: String = "WALLET",
    val note: String = "",
    val at: Long = System.currentTimeMillis()
)
