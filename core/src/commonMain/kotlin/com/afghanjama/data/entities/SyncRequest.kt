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

/** دو نوعِ درخواستِ حضور؛ این دو به ترتیبِ زمانِ رسیدن ثبت می‌شوند. */
val ATTENDANCE_REQUEST_TYPES = setOf("ATTENDANCE_IN", "ATTENDANCE_OUT")

/** بیشترین طولِ برچسبِ کارگر در صندوقِ درخواست‌ها. */
const val REQUEST_WORKER_MAX = 60

/**
 * برچسبِ کارگر همان‌طور که در صندوق می‌نشیند.
 *
 * سرور همین را ذخیره می‌کند و تأیید هم با همین می‌سنجد؛ اگر هر کدام برشِ
 * خودش را داشت، برچسبِ بلندتر از ۶۰ حرف هرگز با صاحبِ کار جور نمی‌شد.
 */
fun requestWorkerLabel(raw: String): String = raw.trim().take(REQUEST_WORKER_MAX)

/**
 * آیا این کارِ دوخت مالِ همین کارگر است؟
 *
 * **چرا لازم شد:** تأییدِ «تحویلِ دوخت» فقط می‌دید که کار هست و تمام
 * نشده — هرگز نمی‌پرسید به نامِ کیست. پس گوشیِ یک خیاط می‌توانست کارِ
 * خیاطِ دیگری را «تمام شد» بزند و کارمزدِ آن به حسابِ صاحبِ کار برود بی
 * آنکه او چیزی دوخته باشد. هم سرور سرِ رسیدن این را می‌پرسد و هم تأیید.
 */
fun ownsAssignment(a: SewingAssignment, worker: String): Boolean =
    requestWorkerLabel(a.tailorLabel).isNotEmpty() &&
        requestWorkerLabel(a.tailorLabel) == requestWorkerLabel(worker)

/**
 * چند دقیقه یک درخواستِ همسان هنوز «همان فشارِ دکمه» شمرده می‌شود.
 *
 * ده دقیقه: بیش از هر تلاشِ دوباره‌ای که کارگر بعد از پیامِ «وصل نشد»
 * می‌کند، و کمتر از فاصلهٔ دو کارِ واقعیِ همسان.
 */
const val REQUEST_REPEAT_WINDOW_MS = 10 * 60 * 1000L

/**
 * آیا درخواستِ تازه همان [last] است که دوباره فرستاده شده؟
 *
 * **چرا لازم شد:** جوابِ سرور اگر به گوشی نرسد (وای‌فای لحظه‌ای قطع شود)،
 * کارگر پیامِ «وصل نشد» می‌بیند و دوباره می‌زند — در حالی که اولی نشسته
 * است. بی این، کارفرما دو ورود یا دو تحویلِ یکسان می‌دید.
 *
 * [last] **آخرین** درخواستِ همین کارگر است، نه هر درخواستی. فقط اگر
 * آخرینِ او همین باشد، هنوز منتظر، و تازه، تکرار است. پس «ورود، خروج،
 * ورود» در ده دقیقه سه درخواستِ جداست، چون وسطی آخرین را عوض کرده.
 *
 * این کار شناسهٔ مشتری نمی‌خواهد و طرحِ دیتابیس را عوض نمی‌کند؛ کلیدِ
 * تکرارنشدنیِ واقعی با دفترِ ۶۷ می‌آید.
 */
fun isRepeatOf(
    last: SyncRequest?,
    worker: String,
    type: String,
    refId: Long,
    amount: Int,
    note: String,
    now: Long,
): Boolean =
    last != null &&
        last.status == "PENDING" &&
        last.worker == worker &&
        last.type == type &&
        last.refId == refId &&
        last.amount == amount &&
        last.note == note &&
        now - last.createdAt in 0L..REQUEST_REPEAT_WINDOW_MS
