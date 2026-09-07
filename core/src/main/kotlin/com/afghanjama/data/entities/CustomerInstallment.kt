package com.afghanjama.data.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * یک قسط: قولِ مشتری که فلان مبلغ را تا فلان روز می‌دهد.
 *
 * **اینجا پرچمِ «پرداخت شد» نیست، و این عمدی است.** حسابِ مشتری از
 * `customer_payments` و دفترِ کل می‌آید؛ اگر قسط هم نظرِ خودش را دربارهٔ
 * پرداخت داشت، روزی آن دو از هم می‌افتادند و کارگاه دو عددِ متفاوت برای
 * یک بدهی می‌دید. پس این جدول فقط **قول** را نگه می‌دارد — مبلغ و روز —
 * و اینکه هر قسط تسویه شده یا نه در [com.afghanjama.data.Installments]
 * از همان پرداخت‌های واقعی حساب می‌شود.
 *
 * [customerName] نام است نه شناسه، چون همهٔ حسابِ مشتری در این اپ با نام
 * کار می‌کند (`ledger_entries.partyName`، `customer_payments.customerName`).
 * وصل کردنش به `customers.id` یعنی یک راهِ دومِ شناسایی.
 *
 * [dueDate] مثلِ `Order.dueDate` یک مهرِ زمانی است و صفر یعنی بی‌تاریخ.
 */
@Entity(
    tableName = "customer_installments",
    indices = [Index(value = ["customerName"]), Index(value = ["dueDate"])]
)
data class CustomerInstallment(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val customerName: String,
    val amount: Long,
    val dueDate: Long,
    @ColumnInfo(defaultValue = "") val note: String = "",
    val createdAt: Long = System.currentTimeMillis()
)
