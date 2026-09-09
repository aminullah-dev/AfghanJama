package com.afghanjama.data.entities

import com.afghanjama.util.nowMillis
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * لاگِ حسابرسی: چه کسی (نام + نقش)، چه کاری، با چه جزئیاتی، کِی.
 * روی عملیاتِ حساس (پول، حذف، ویرایش، مراحل تولید) ثبت می‌شود.
 */
@Entity(
    tableName = "audit_log",
    indices = [Index(value = ["at"])]
)
data class AuditLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val user: String,           // نام کاربر (خالی = ثبت‌نشده)
    val role: String,           // نقش هنگام عمل
    val action: String,         // مثلاً «فروش»، «حذف سفارش»
    val detail: String = "",    // جزئیات: کد سند/سفارش، مبلغ…
    val at: Long = nowMillis()
)
