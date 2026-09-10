package com.afghanjama.data.entities

import com.afghanjama.util.nowMillis
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * درخواستی که از گوشیِ یک کارگر به گوشیِ اصلی رسیده.
 *
 * قلبِ مدلِ «یک نویسنده»: گوشیِ کارگر **هرگز** در دفتر نمی‌نویسد. هر
 * کاری که پول یا موجودی را تکان می‌دهد اینجا به‌صورت یک درخواستِ
 * منتظر می‌نشیند تا کارفرما روی گوشیِ خودش تأیید کند. با این کار
 * مسئلهٔ تضادِ دو نویسنده اصلاً به‌وجود نمی‌آید.
 */
@Entity(
    tableName = "sync_requests",
    indices = [Index(value = ["status"])]
)
data class SyncRequest(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,

    /** نامی که خودِ گوشیِ کارگر معرفی کرده — برای اینکه معلوم باشد از کجا آمده. */
    val deviceName: String,

    /** برچسبِ کارگر، مثلاً «[T10] احمد». */
    val worker: String,

    /** SEWING_DONE / ATTENDANCE_IN / ATTENDANCE_OUT / NOTE */
    val type: String,

    /** شرحِ خواناىِ درخواست، همان چیزی که کارفرما می‌بیند. */
    val summary: String,

    /** شناسهٔ سطرِ مرتبط (مثلاً id تحویلِ دوخت) — ۰ یعنی ندارد. */
    val refId: Long = 0,

    /** عدد همراهِ درخواست (مثلاً تعدادِ تحویل‌شده). */
    val amount: Int = 0,

    val note: String = "",

    /** PENDING / APPROVED / REJECTED */
    val status: String = "PENDING",

    val createdAt: Long = nowMillis(),
    val decidedAt: Long? = null,
    val decidedNote: String = ""
)

/** برچسبِ فارسیِ نوعِ درخواست. */
fun syncRequestLabel(type: String): String = when (type) {
    "SEWING_DONE" -> "تحویل دوخت"
    "ATTENDANCE_IN" -> "ثبت ورود"
    "ATTENDANCE_OUT" -> "ثبت خروج"
    "NOTE" -> "پیام"
    else -> type
}
