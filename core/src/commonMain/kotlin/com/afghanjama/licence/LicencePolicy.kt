package com.afghanjama.licence

import com.afghanjama.prefs.DeviceMode
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

/** پنج حالتِ پروتکل — نه بیشتر. */
enum class LicenceState {
    /** امضا درست، محصول درست، دستگاه درست (یا `*`)، تاریخ نگذشته. همه‌چیز، بی‌نشان. */
    LICENSED,
    /** لایسنسِ درست روی دستگاهِ دیگر (عوض‌شدنِ سخت‌افزار یا نصبِ دوبارهٔ سیستم). ۳۰ روز کامل. */
    GRACE_MACHINE,
    /** تاریخِ لایسنس گذشته، تا ۳۰ روز بعدش. کامل، با شمارشِ معکوس. */
    GRACE_EXPIRY,
    /** بی‌لایسنس، در ۱۴ روزِ اول و زیرِ سقفِ سفارش. کامل، با نشانِ «آزمایشی» روی کاغذ. */
    TRIAL,
    /** دیدن، جست‌وجو، چاپ، خروجی و پشتیبان — بله. ثبت و تغییر — نه. */
    READ_ONLY,
}

/** چرا فقط‌خواندنی — تا صفحه بگوید چرا و چه باید کرد. */
enum class ReadOnlyReason {
    TRIAL_OVER,
    TRIAL_CAP,
    MACHINE_GRACE_OVER,
    EXPIRY_GRACE_OVER,
    /** دستگاهِ کارگر: دستگاهِ اصلیِ کارگاه فقط‌خواندنی است. */
    MAIN_DEVICE,
}

/**
 * آنچه دستگاهِ اصلی دربارهٔ لایسنسِ خودش به دستگاهِ کارگر گفته.
 *
 * کارگر لایسنسِ خودش را ندارد و نمی‌خواهد؛ «یک لایسنس برای کلِ کارگاه».
 * پس هر بار که به دستگاهِ اصلی وصل می‌شود، حالتِ آن را می‌گیرد و پیرو
 * همان است.
 */
data class MainLicence(
    val state: LicenceState,
    val writable: Boolean,
    val seenAtMillis: Long,
)

/**
 * همهٔ آنچه برای تصمیم لازم است — و هیچ چیزِ دیگری.
 *
 * عمداً دادهٔ خالص است: نه تنظیمات می‌خوانَد نه دیتابیس نه ساعت. همین
 * است که هر حالتِ پروتکل را بی دستگاه و بی دیتابیس آزمودنی می‌کند.
 */
data class LicenceFacts(
    /** لایسنسِ نصب‌شده که امضایش سنجیده شده؛ `null` یعنی هیچ یا نامعتبر. */
    val licence: Licence?,
    /** کدِ همین دستگاه. */
    val machineCode: String,
    /** شروعِ دورهٔ آزمایشی (اولین اجرای نسخهٔ لایسنس‌دار)، میلی‌ثانیه. */
    val firstRunMillis: Long,
    /** دیرترین زمانی که اپ تا امروز دیده — برای پیدا کردنِ عقب‌کشیدنِ ساعت. */
    val lastSeenMillis: Long,
    /** از کِی این لایسنس روی دستگاهِ دیگری دیده شده؛ `null` یعنی همین الان. */
    val machineMismatchSinceMillis: Long? = null,
    /** سفارش‌هایی که از شروعِ دورهٔ آزمایشی در دفتر ساخته شده‌اند. */
    val ordersSinceTrialStart: Int = 0,
    val mode: DeviceMode = DeviceMode.STANDALONE,
    /** فقط روی دستگاهِ کارگر: آخرین گزارشِ دستگاهِ اصلی، یا `null` اگر نگفته. */
    val mainReport: MainLicence? = null,
    /** کلیدی نصب شده بود ولی دیگر نمی‌خوانَد — تا صفحه بگوید. */
    val storedKeyRejected: Boolean = false,
)

/** نتیجهٔ تصمیم — آنچه صفحه نشان می‌دهد و `Repo` رویش اجازه می‌دهد یا نه. */
data class LicenceStatus(
    val state: LicenceState,
    val reason: ReadOnlyReason? = null,
    val licence: Licence? = null,
    /**
     * روزهای مانده — بسته به حالت:
     * آزمایشی تا پایانِ دوره، مهلت‌ها تا پایانِ مهلت، و لایسنسِ تاریخ‌دار
     * تا آخرین روزش (همان روز هم شمرده می‌شود). `null` یعنی بی‌پایان.
     */
    val daysLeft: Int? = null,
    /** فقط در آزمایشی: چند سفارشِ دیگر جا دارد. */
    val ordersLeft: Int? = null,
    /** ساعتِ دستگاه بیش از یک روز عقب کشیده شده. */
    val clockRolledBack: Boolean = false,
    /** دستگاهِ کارگر؛ لایسنس از دستگاهِ اصلی است. */
    val coveredByMain: Boolean = false,
    val storedKeyRejected: Boolean = false,
) {
    /** ساختن و تغییر دادنِ سند مجاز است؟ */
    val canWrite: Boolean get() = state != LicenceState.READ_ONLY

    /**
     * نشانِ «نسخهٔ آزمایشی — بدون لایسنس» روی کاغذ؟
     *
     * در آزمایشی و فقط‌خواندنی، بله. در دو مهلت، نه: کارگاهی که لایسنس
     * خریده و فقط سخت‌افزارش عوض شده یا تمدیدش چند روز عقب افتاده، طبقِ
     * پروتکل «کامل» کار می‌کند، و فاکتورِ مشتری‌اش نباید «بدون لایسنس»
     * بخورد.
     */
    val watermark: Boolean
        get() = state == LicenceState.TRIAL || state == LicenceState.READ_ONLY

    /** بنر روی خانه لازم است؟ */
    val needsBanner: Boolean
        get() = state != LicenceState.LICENSED || clockRolledBack || storedKeyRejected ||
            (daysLeft != null && daysLeft <= LicencePolicy.EXPIRY_NOTICE_DAYS && !coveredByMain)
}

/**
 * قاعده‌های پروتکل، به شکلِ یک تابعِ خالص.
 *
 * عددها پیش‌فرضِ `PROTOCOL.md` برای خیاط‌یارند و کارفرما می‌تواند عوضشان
 * کند — فقط همین‌جا.
 */
object LicencePolicy {

    /** طولِ دورهٔ آزمایشی از اولین اجرای نسخهٔ لایسنس‌دار. */
    const val TRIAL_DAYS = 14

    /** سقفِ سفارشِ تازه در دورهٔ آزمایشی. فقط **ساختن** را می‌بندد، نه دیدن را. */
    const val TRIAL_ORDER_CAP = 20

    /** مهلتِ عوض‌شدنِ دستگاه و مهلتِ پس از تاریخِ لایسنس. */
    const val GRACE_DAYS = 30

    /** از چند روز مانده به تاریخِ لایسنس بنر نشان داده شود (۳۰، ۱۴، ۷، ۱). */
    const val EXPIRY_NOTICE_DAYS = 30

    /** عقب‌رفتنِ ساعت تا این اندازه (تنظیمِ ساعت، منطقهٔ زمانی) نادیده گرفته می‌شود. */
    const val ROLLBACK_TOLERANCE_MILLIS = 24L * 60 * 60 * 1000

    const val DAY_MILLIS = 24L * 60 * 60 * 1000

    /**
     * «اکنون»ی که تصمیم رویش گرفته می‌شود.
     *
     * پروتکل: اگر ساعت بیش از یک روز پیش از دیرترین زمانِ دیده‌شده باشد،
     * همان دیرترین زمان «اکنون» است. یعنی عقب کشیدنِ ساعت دورهٔ آزمایشی
     * را دراز نمی‌کند — و هیچ‌وقت هم کسی را بیرون نمی‌اندازد.
     */
    fun effectiveNow(atMillis: Long, lastSeenMillis: Long): Pair<Long, Boolean> =
        if (atMillis < lastSeenMillis - ROLLBACK_TOLERANCE_MILLIS) lastSeenMillis to true
        else atMillis to false

    fun evaluate(
        f: LicenceFacts,
        atMillis: Long,
        tz: TimeZone = TimeZone.currentSystemDefault(),
    ): LicenceStatus {
        val (now, rolledBack) = effectiveNow(atMillis, f.lastSeenMillis)

        // ---- دستگاهِ کارگر: پیروِ دستگاهِ اصلی ----
        if (f.mode == DeviceMode.WORKER) {
            val main = f.mainReport
                // دستگاهِ اصلی هنوز چیزی نگفته (یا نسخهٔ پیش از لایسنس است
                // و اصلاً لایسنس نمی‌شناسد): کارگر را بی‌دلیل نمی‌بندیم.
                ?: return LicenceStatus(LicenceState.LICENSED, coveredByMain = true)
            return if (!main.writable) {
                LicenceStatus(LicenceState.READ_ONLY, ReadOnlyReason.MAIN_DEVICE, coveredByMain = true)
            } else {
                // حالتِ اصلی (مثلاً آزمایشی) آینه می‌شود تا نشانِ کاغذ هم
                // همان باشد؛ فقط‌خواندنی بالا جدا گرفته شد.
                val mirrored = if (main.state == LicenceState.READ_ONLY) LicenceState.LICENSED else main.state
                LicenceStatus(mirrored, coveredByMain = true)
            }
        }

        val lic = f.licence
        if (lic != null) {
            val today = Instant.fromEpochMilliseconds(now).toLocalDateTime(tz).date

            // تاریخ
            val exp = lic.expires
            var expiryGraceLeft: Int? = null
            if (exp != null && today > exp) {
                val graceLast = exp.plus(DatePeriod(days = GRACE_DAYS))
                if (today > graceLast) {
                    return LicenceStatus(
                        LicenceState.READ_ONLY, ReadOnlyReason.EXPIRY_GRACE_OVER, lic,
                        clockRolledBack = rolledBack,
                    )
                }
                expiryGraceLeft = daysInclusive(today, graceLast)
            }

            // دستگاه
            val machineOk = lic.anyMachine || lic.machine == f.machineCode
            if (!machineOk) {
                val since = f.machineMismatchSinceMillis ?: now
                val end = since + GRACE_DAYS * DAY_MILLIS
                if (now >= end) {
                    return LicenceStatus(
                        LicenceState.READ_ONLY, ReadOnlyReason.MACHINE_GRACE_OVER, lic,
                        clockRolledBack = rolledBack,
                    )
                }
                val left = ceilDays(end - now)
                return LicenceStatus(
                    LicenceState.GRACE_MACHINE, null, lic,
                    daysLeft = expiryGraceLeft?.let { minOf(it, left) } ?: left,
                    clockRolledBack = rolledBack,
                )
            }

            if (expiryGraceLeft != null) {
                return LicenceStatus(
                    LicenceState.GRACE_EXPIRY, null, lic, daysLeft = expiryGraceLeft,
                    clockRolledBack = rolledBack,
                )
            }
            return LicenceStatus(
                LicenceState.LICENSED, null, lic,
                daysLeft = exp?.let { daysInclusive(today, it) },
                clockRolledBack = rolledBack,
            )
        }

        // ---- بی‌لایسنس: دورهٔ آزمایشی ----
        val end = f.firstRunMillis + TRIAL_DAYS * DAY_MILLIS
        if (f.ordersSinceTrialStart >= TRIAL_ORDER_CAP) {
            return LicenceStatus(
                LicenceState.READ_ONLY, ReadOnlyReason.TRIAL_CAP,
                clockRolledBack = rolledBack, storedKeyRejected = f.storedKeyRejected,
            )
        }
        if (now >= end) {
            return LicenceStatus(
                LicenceState.READ_ONLY, ReadOnlyReason.TRIAL_OVER,
                clockRolledBack = rolledBack, storedKeyRejected = f.storedKeyRejected,
            )
        }
        return LicenceStatus(
            LicenceState.TRIAL,
            daysLeft = ceilDays(end - now),
            ordersLeft = TRIAL_ORDER_CAP - f.ordersSinceTrialStart,
            clockRolledBack = rolledBack,
            storedKeyRejected = f.storedKeyRejected,
        )
    }

    /** روزهای مانده تا [last]، خودِ امروز و خودِ آن روز هم شمرده می‌شوند. */
    private fun daysInclusive(today: LocalDate, last: LocalDate): Int = today.daysUntil(last) + 1

    /** میلی‌ثانیه به روز، رو به بالا: ۲ ساعت مانده یعنی «۱ روز»، نه «۰». */
    private fun ceilDays(millis: Long): Int =
        ((millis + DAY_MILLIS - 1) / DAY_MILLIS).toInt().coerceAtLeast(0)
}
