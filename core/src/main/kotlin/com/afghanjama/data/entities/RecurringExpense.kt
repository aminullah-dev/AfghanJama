package com.afghanjama.data.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * هزینه‌ای که هر ماه تکرار می‌شود — کرایه، برق، اینترنت.
 *
 * **چرا لازم شد.** این هزینه‌ها هر ماه دستی وارد می‌شدند و اگر ماهی
 * فراموش می‌شد، هیچ‌چیز خبر نمی‌داد. نتیجه‌اش این بود که **سودِ آن ماه
 * بیشتر از واقعیت نشان داده می‌شد** — و این بدترین جنسِ خطاست، چون
 * عدد سالم به نظر می‌رسد و کارفرما بر اساسش تصمیم می‌گیرد.
 *
 * **این جدول فقط تعریف است، نه سند.** ثبتِ واقعی همان مسیرِ همیشگی را
 * می‌رود (`Repo.spend` → صندوق، دفتر کل، ژورنال). اینجا فقط نوشته
 * می‌شود «هر ماه چنین هزینه‌ای هست» تا اپ بتواند یادآوری کند و ثبتش
 * یک زدن باشد. اگر جور دیگری بود، دو جا عدد می‌داشتیم که از هم
 * می‌افتادند.
 *
 * @param dayOfMonth روزِ ماهِ شمسی. ۱ تا ۳۱؛ ماهی که آن روز را ندارد،
 *   روزِ آخرش حساب می‌شود.
 * @param lastPostedYm آخرین ماهی که ثبت شد، به شکلِ `سال*۱۰۰+ماه`
 *   شمسی (مثلاً ۱۴۰۵*۱۰۰+۶ = ۱۴۰۵۰۶). صفر یعنی هرگز.
 *
 *   **چرا این و نه یک مهرِ زمان.** سؤالی که پرسیده می‌شود «این ماه ثبت
 *   شده؟» است، نه «کِی ثبت شد». یک عددِ ماه این را مستقیم جواب می‌دهد
 *   و مقایسه‌اش به تقویم و منطقه‌زمانی وابسته نیست.
 */
@Entity(tableName = "recurring_expenses")
data class RecurringExpense(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val amount: Long,
    val dayOfMonth: Int,
    /** از کدام صندوق پرداخت می‌شود — همان نام‌هایی که `PaymentSource` دارد. */
    val source: String,
    /** دستهٔ هزینه در گزارش‌ها. */
    @ColumnInfo(defaultValue = "")
    val category: String = "",
    @ColumnInfo(defaultValue = "1")
    val enabled: Boolean = true,
    @ColumnInfo(defaultValue = "0")
    val lastPostedYm: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
)
