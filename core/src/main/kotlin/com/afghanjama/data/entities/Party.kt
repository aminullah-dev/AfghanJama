package com.afghanjama.data.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * طرفِ حساب در دفتر کلِ یکپارچه: خیاط، ناظر، کارمند، مشتری یا تأمین‌کننده.
 * هر طرف با ترکیبِ یکتای (نوع + نام) شناخته می‌شود؛ همان کلیدی که سرتاسر
 * اپ برای شناساییِ اشخاص به کار می‌رود. این جدول فقط دفترچهٔ اشخاص است؛
 * گردشِ مالی در ledger_entries نگهداری می‌شود.
 */
@Entity(
    tableName = "parties",
    indices = [Index(value = ["type", "name"], unique = true)]
)
data class Party(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: String,          // SUPPLIER / CUSTOMER / TAILOR / INSPECTOR / EMPLOYEE
    val phone: String = "",
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis()
)
