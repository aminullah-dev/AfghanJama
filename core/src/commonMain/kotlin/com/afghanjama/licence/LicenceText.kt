package com.afghanjama.licence

import com.afghanjama.ui.format.PersianDate
import com.afghanjama.ui.format.fa
import kotlinx.datetime.LocalDate

/**
 * هر جمله‌ای که دربارهٔ لایسنس به کاربر گفته می‌شود — یک‌جا.
 *
 * قاعدهٔ پروتکل: **هر محدودیتی دلیل و راهِ حلش را روی صفحه می‌گوید.**
 * پس هر حالت سه چیز دارد: چه شده، چه هنوز کار می‌کند، و چه باید کرد.
 * بنر، صفحهٔ لایسنس، پیامِ ردِ ثبت و جوابِ شبکه همه از همین‌جا می‌خوانند
 * تا یک وضعیت در دو جا به دو زبان گفته نشود.
 */
object LicenceText {

    /** متنِ نشان روی کاغذ — عیناً همان که پروتکل گفته. */
    const val WATERMARK = "نسخهٔ آزمایشی — بدون لایسنس"

    /** کارهایی که در هر حالتی کار می‌کنند. */
    const val ALWAYS_WORKS =
        "همهٔ داده‌ها سرِ جایشان است: دیدن، جست‌وجو، چاپ، PDF، فرستادن در واتساپ، پشتیبان‌گیری و بازیابی همیشه کار می‌کنند."

    /** راهِ حلِ همیشگی. */
    const val REMEDY =
        "کدِ دستگاه را از تنظیمات ← لایسنس به لینومیک بدهید و کلیدی را که می‌گیرید همان‌جا وارد کنید."

    fun title(s: LicenceStatus): String = when {
        s.coveredByMain && s.state != LicenceState.READ_ONLY -> "زیرِ لایسنسِ دستگاهِ اصلی"
        else -> when (s.state) {
            LicenceState.LICENSED -> "لایسنس فعال است"
            LicenceState.GRACE_MACHINE -> "مهلتِ دستگاهِ تازه"
            LicenceState.GRACE_EXPIRY -> "تاریخِ لایسنس گذشته — مهلت"
            LicenceState.TRIAL -> "دورهٔ آزمایشی"
            LicenceState.READ_ONLY -> "فقط‌خواندنی"
        }
    }

    /** چرا این حالت — یک جمله. */
    fun reason(s: LicenceStatus): String {
        if (s.coveredByMain) {
            return if (s.state == LicenceState.READ_ONLY)
                "دستگاهِ اصلیِ کارگاه فقط‌خواندنی است، پس این گوشی هم چیزی ثبت نمی‌کند."
            else "این گوشی کارگرِ کارگاه است و با لایسنسِ دستگاهِ اصلی کار می‌کند؛ لایسنسِ جدا نمی‌خواهد."
        }
        return when (s.state) {
            LicenceState.LICENSED -> s.daysLeft?.let { "لایسنس تا ${it.fa()} روزِ دیگر معتبر است." }
                ?: "لایسنسِ این کارگاه همیشگی است."
            LicenceState.GRACE_MACHINE ->
                "این لایسنس برای دستگاهِ دیگری صادر شده (شاید سخت‌افزار یا سیستم عوض شده). " +
                    "${days(s.daysLeft)} همه‌چیز کامل کار می‌کند."
            LicenceState.GRACE_EXPIRY ->
                "تاریخِ لایسنس گذشته. ${days(s.daysLeft)} همه‌چیز کامل کار می‌کند."
            LicenceState.TRIAL ->
                "${days(s.daysLeft)} و تا ${(s.ordersLeft ?: 0).fa()} سفارشِ دیگر، همه‌چیز کامل کار می‌کند؛ " +
                    "روی سندهای چاپی نشانِ «$WATERMARK» می‌نشیند."
            LicenceState.READ_ONLY -> when (s.reason) {
                ReadOnlyReason.TRIAL_OVER -> "دورهٔ آزمایشیِ ${LicencePolicy.TRIAL_DAYS.fa()} روزه تمام شده."
                ReadOnlyReason.TRIAL_CAP ->
                    "دورهٔ آزمایشی به سقفِ ${LicencePolicy.TRIAL_ORDER_CAP.fa()} سفارش رسیده."
                ReadOnlyReason.MACHINE_GRACE_OVER ->
                    "مهلتِ ${LicencePolicy.GRACE_DAYS.fa()} روزهٔ دستگاهِ تازه تمام شده."
                ReadOnlyReason.EXPIRY_GRACE_OVER ->
                    "تاریخِ لایسنس و مهلتِ ${LicencePolicy.GRACE_DAYS.fa()} روزهٔ پس از آن گذشته."
                ReadOnlyReason.MAIN_DEVICE, null -> "ثبتِ تازه بسته است."
            }
        }
    }

    /** چه باید کرد — یا `null` اگر کاری لازم نیست. */
    fun remedy(s: LicenceStatus): String? {
        if (s.coveredByMain) {
            return if (s.state == LicenceState.READ_ONLY)
                "روی دستگاهِ اصلیِ کارگاه، از تنظیمات ← لایسنس، کلیدِ لایسنس را وارد کنید."
            else null
        }
        return when (s.state) {
            LicenceState.LICENSED ->
                if ((s.daysLeft ?: Int.MAX_VALUE) <= LicencePolicy.EXPIRY_NOTICE_DAYS)
                    "برای تمدید با لینومیک تماس بگیرید."
                else null
            LicenceState.GRACE_MACHINE ->
                "برای کلیدِ تازه با لینومیک تماس بگیرید و کدِ همین دستگاه را بدهید."
            LicenceState.GRACE_EXPIRY -> "برای تمدید با لینومیک تماس بگیرید و کلیدِ تازه را وارد کنید."
            LicenceState.TRIAL, LicenceState.READ_ONLY -> REMEDY
        }
    }

    /** یک سطر برای بنرِ خانه. */
    fun banner(s: LicenceStatus): String {
        val head = when {
            s.coveredByMain && s.state == LicenceState.READ_ONLY -> "دستگاهِ اصلی فقط‌خواندنی است"
            s.state == LicenceState.TRIAL ->
                "نسخهٔ آزمایشی: ${(s.daysLeft ?: 0).fa()} روز و ${(s.ordersLeft ?: 0).fa()} سفارش مانده"
            s.state == LicenceState.GRACE_MACHINE ->
                "لایسنس برای دستگاهِ دیگری است: ${(s.daysLeft ?: 0).fa()} روز مهلت"
            s.state == LicenceState.GRACE_EXPIRY ->
                "تاریخِ لایسنس گذشته: ${(s.daysLeft ?: 0).fa()} روز مهلت"
            s.state == LicenceState.READ_ONLY -> "فقط‌خواندنی — ${reason(s)}"
            s.daysLeft != null -> "لایسنس ${s.daysLeft.fa()} روزِ دیگر تمام می‌شود"
            else -> title(s)
        }
        val notes = buildList {
            if (s.clockRolledBack) add(CLOCK_NOTICE)
            if (s.storedKeyRejected) add("کلیدِ نصب‌شده پذیرفته نشد.")
        }
        return (listOf(head) + notes).joinToString(" • ")
    }

    const val CLOCK_NOTICE = "ساعتِ دستگاه عقب است؛ تاریخ را درست کنید."

    /** پیامِ ردِ ثبت، وقتی کاری که داده را عوض می‌کند بسته است. */
    fun refusal(s: LicenceStatus): String = buildString {
        append("ثبت انجام نشد: ")
        append(reason(s))
        append("\n\n")
        append(ALWAYS_WORKS)
        remedy(s)?.let { append("\n\n"); append(it) }
    }

    /** جوابِ دستگاهِ اصلی به درخواستِ گوشیِ کارگر، وقتی خودش فقط‌خواندنی است. */
    const val LAN_REFUSAL =
        "دستگاهِ اصلیِ کارگاه فقط‌خواندنی است و درخواستِ تازه نمی‌پذیرد. " +
            "کارفرما باید روی دستگاهِ اصلی از تنظیمات ← لایسنس کلید را وارد کند."

    /** چرا کلیدی که کاربر چسبانده پذیرفته نشد. */
    fun problem(p: Lnm1.Problem): String = when (p) {
        Lnm1.Problem.NOT_A_KEY -> "این متن کلیدِ لایسنس نیست. کلید با «LNM1.» شروع می‌شود؛ همه‌اش را بچسبانید."
        Lnm1.Problem.MALFORMED -> "کلید ناقص یا خراب است. دوباره و کامل بچسبانید، یا فایلِ ‎.lnmlic‎ را باز کنید."
        Lnm1.Problem.BAD_SIGNATURE -> "امضای کلید درست نیست. شاید یک حرفش عوض شده؛ کلید را از خودِ لینومیک دوباره بگیرید."
        Lnm1.Problem.WRONG_VERSION -> "این کلید برای نسخهٔ دیگری از لایسنس است. با لینومیک تماس بگیرید."
        Lnm1.Problem.WRONG_PRODUCT -> "این کلید برای برنامهٔ دیگری است، نه خیاط‌یار."
    }

    /** تاریخ به شمسی، با میلادی کنارش تا با کلیدِ صادرشده یکی خوانده شود. */
    fun date(d: LocalDate): String {
        val j = PersianDate.gregorianToJalali(d.year, d.monthNumber, d.dayOfMonth)
        val sh = "${j[0].fa()}/${j[1].fa()}/${j[2].fa()}"
        return "$sh ($d)"
    }

    private fun days(n: Int?): String = "تا ${(n ?: 0).fa()} روزِ دیگر"
}
