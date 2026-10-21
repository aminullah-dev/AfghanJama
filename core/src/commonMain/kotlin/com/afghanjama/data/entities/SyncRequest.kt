package com.afghanjama.data.entities

import com.afghanjama.util.nowMillis
import androidx.room.ColumnInfo
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
    indices = [
        Index(value = ["status"]),
        // **یکتا:** همان کارِ گوشی (قراردادِ ۲) دو بار نمی‌نشیند. ردیف‌های
        // قراردادِ ۱ کلید ندارند (`NULL`) و SQLite چند `NULL` را یکتا می‌شمارد.
        Index(value = ["clientKey"], unique = true),
    ]
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
    val decidedNote: String = "",

    // ---- دفترِ ۶۷: قراردادِ ۲ ----

    /** کلیدِ تکرارنشدنیِ همان کار روی گوشی؛ `null` برای قراردادِ ۱. */
    val clientKey: String? = null,

    /**
     * لحظهٔ زدنِ دکمه، **با ساعتِ کامپیوتر** (ساعتِ گوشی تصحیح‌شده،
     * `AttendanceClock`). `null` برای قراردادِ ۱ یا ساعتِ بی‌معنا.
     */
    val clientAt: Long? = null,

    /** لحظه‌ای که کامپیوتر آن را گرفت؛ بازهٔ ساعتِ حضور با همین سنجیده می‌شود. */
    val receivedAt: Long? = null,

    /** گوشیِ جفت‌شده‌ای که فرستاد (`lan_devices`)؛ `null` برای قراردادِ ۱. */
    val deviceId: String? = null,

    /** ۱ یا ۲ — ردیف‌های پیش از دفترِ ۶۷ همه ۱. */
    @ColumnInfo(defaultValue = "1") val protocol: Int = 1,
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
 *
 * **تحویلِ بخشی هرگز تکرار شمرده نمی‌شود.** گوشی اجازه می‌دهد کارگر
 * بخشی از یک کار را اعلام کند (۵ از ۱۰). اگر پیش از تأیید دوباره ۵ از
 * همان ۱۰ را بزند، شاید واقعاً ۵ تای دیگر را دوخته باشد؛ یکی کردنِ آن
 * دو، به کارگر می‌گفت «پیش‌تر رسیده بود» و کارفرما دومی را هرگز
 * نمی‌دید. ردیفِ دوم در بدترین حالت بی‌ضرر است: پس از تأییدِ اولی آن
 * کار «تحویل‌شده» است و تأییدِ دومی با دلیل رد می‌شود. برای همین
 * [assignmentQty] لازم است — شمارِ کلِ همان کار؛ تحویلِ کامل
 * (`amount` برابرِ آن) مثلِ هر درخواستِ دیگر یکی می‌شود.
 */
fun isRepeatOf(
    last: SyncRequest?,
    worker: String,
    type: String,
    refId: Long,
    amount: Int,
    note: String,
    now: Long,
    /** شمارِ کارِ دوخت برای `SEWING_DONE`؛ برای نوع‌های دیگر بی‌اثر. */
    assignmentQty: Int = 0,
): Boolean =
    !(type == "SEWING_DONE" && amount in 1 until assignmentQty) &&
        last != null &&
        last.status == "PENDING" &&
        last.worker == worker &&
        last.type == type &&
        last.refId == refId &&
        last.amount == amount &&
        last.note == note &&
        now - last.createdAt in 0L..REQUEST_REPEAT_WINDOW_MS

/**
 * کدام ساعت برای «ورود» و «خروج»ی که از گوشی رسیده ثبت شود.
 *
 * - [AUTO]: ساعتِ گوشی اگر معتبر است (`AttendanceClock.trusted`)، وگرنه
 *   تأیید نمی‌شود و کارفرما باید یکی از دو تای دیگر را انتخاب کند.
 * - [PHONE]: لحظهٔ زدنِ دکمه، با ساعتِ گوشیِ تصحیح‌شده.
 * - [ARRIVAL]: لحظه‌ای که کامپیوتر درخواست را گرفت.
 */
enum class RequestTime {
    AUTO, PHONE, ARRIVAL;

    companion object {
        fun of(s: String?): RequestTime = entries.firstOrNull { it.name.equals(s, ignoreCase = true) } ?: AUTO
    }
}

/**
 * ساعتِ گوشی برای این درخواستِ حضور معتبر نیست؟ — کارت باید دو دکمه نشان
 * دهد، نه یکی. فقط قراردادِ ۲ (قراردادِ ۱ ساعتِ گوشی را اصلاً نمی‌فرستد).
 */
fun SyncRequest.clockSuspect(): Boolean =
    protocol >= 2 && type in ATTENDANCE_REQUEST_TYPES &&
        !com.afghanjama.lan.AttendanceClock.trusted(clientAt, receivedAt ?: createdAt)
