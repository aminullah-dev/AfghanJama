package com.afghanjama.data

/**
 * پیگیریِ طلب — «امروز به چه کسی زنگ بزنم؟».
 *
 * **چرا هشدارهای قبلی کافی نبودند.** مرکزِ هشدار می‌گفت «طلب از ۱۲
 * مشتری» و «طلبِ ۳ نفر کهنه است». هر دو درست بودند و هیچ‌کدام کاری
 * نمی‌کردند: نه می‌گفتند اول سراغِ کی برو، نه شماره‌ای کنارشان بود، نه
 * جایی بود که بنویسی «زنگ زدم، قول داد پنج‌شنبه». پس پیگیری در حافظهٔ
 * کارفرما می‌ماند و فردا از اول.
 *
 * اینجا هر بدهکار **یک سطر** است با **یک دلیل**، و ترتیب از دلیل
 * می‌آید نه فقط از مبلغ:
 *
 * ۱. قولی که داده و سرش نایستاده — خودش گفته بود کِی؛
 * ۲. قولی که روزش امروز است؛
 * ۳. قسطی که روزش گذشته؛
 * ۴. بدهی‌ای که [FOLLOW_UP_DAYS] روز است مانده.
 *
 * بقیه — بدهیِ تازه، یا کسی که قول داده و روزش نرسیده — در «بعداً»اند:
 * دیده می‌شوند ولی امروز کاری نمی‌خواهند. زنگ زدن به کسی که دیروز قول
 * داده، مشتری را می‌رنجاند.
 *
 * **سنِ بدهی از قدیمی‌ترین بدهیِ پرداخت‌نشده است، نه از آخرین پرداخت.**
 * مشتری‌ای که ۴۵ روز پیش نسیه برده و هفتهٔ پیش دو هزار داده، بدهیِ
 * ۴۵ روزه دارد؛ اگر آخرین پرداخت ملاک بود، یک پرداختِ کوچک بدهیِ کهنه
 * را هر بار «تازه» می‌کرد. پرداخت‌ها از قدیمی‌ترین بدهی به بعد پخش
 * می‌شوند — همان قاعدهٔ [Installments].
 *
 * بی دیتابیس و بی ساعت، تا آزمون مستقیم بسنجدش.
 */
object DebtFollowUp {

    /**
     * از چند روز ماندن، بدهی در فهرستِ امروز می‌آید.
     *
     * ۱۴ و نه ۳۰ که مرکزِ هشدار برای «کهنه» به کار می‌برد: آن عدد زنگِ
     * خطر است، این یکی جلوتر از خطر. دو هفته یعنی مشتری فرصتِ عادی را
     * داشته؛ از اینجا یادآوریِ مؤدبانه کارِ درستی است، و اگر تا ۳۰ صبر
     * شود همان هشدارِ کهنه روشن می‌شود.
     */
    const val FOLLOW_UP_DAYS = 14

    private const val DAY = 86_400_000L

    /** یک حرکت در حسابِ مشتری: بدهکار یا بستانکار. */
    data class Move(val at: Long, val debit: Long, val credit: Long)

    /**
     * حسابِ یک مشتری: [owed] مثبت یعنی به ما بدهکار است، منفی یعنی
     * پولش پیشِ ماست.
     */
    data class Account(val owed: Long, val oldestUnpaidAt: Long?)

    /**
     * زمانِ قدیمی‌ترین بدهی‌ای که هنوز پرداخت نشده؛ `null` یعنی چیزی
     * باز نیست.
     *
     * همهٔ بستانکارها — پیش یا پس از بدهی — یک کیسه‌اند و از قدیمی‌ترین
     * بدهی به بعد خرج می‌شوند. بیعانه‌ای که پیش از فروش رسیده هم همان
     * فروش را می‌بندد.
     */
    fun oldestUnpaidAt(moves: List<Move>): Long? {
        var pool = moves.sumOf { it.credit.coerceAtLeast(0) }
        for (m in moves.filter { it.debit > 0 }.sortedBy { it.at }) {
            if (pool >= m.debit) pool -= m.debit
            else return m.at
        }
        return null
    }

    /** نتیجهٔ یک تماس یا پیام. */
    enum class Outcome(val label: String) {
        PROMISED("قول داد"),
        NO_ANSWER("جواب نداد"),
        TALKED("صحبت شد"),
        MESSAGED("پیام رفت");

        companion object {
            fun of(name: String): Outcome? = entries.firstOrNull { it.name == name }
        }
    }

    /**
     * آخرین پیگیریِ ثبت‌شده.
     *
     * [owedThen] مبلغِ پیگیری ([Debtor.amount]) در لحظهٔ قول است. قول «شکسته» فقط وقتی است که
     * روزش گذشته **و** از آن لحظه چیزی پرداخت نشده؛ کسی که نصفش را داده
     * سرِ قولش ایستاده، فقط بقیه‌اش مانده.
     */
    data class Contact(
        val at: Long,
        val outcome: Outcome,
        val until: Long = 0L,
        val owedThen: Long = 0L,
    )

    /**
     * پیگیری در `payload`ِ رویداد — `نتیجه=…؛تا=…؛مانده=…`.
     *
     * زمانِ خودِ پیگیری در سطرِ رویداد هست ([Contact.at] از آنجا می‌آید)
     * و اینجا تکرار نمی‌شود.
     */
    fun encode(c: Contact): String =
        "نتیجه=${c.outcome.name}؛تا=${c.until}؛مانده=${c.owedThen}"

    /** عکسِ [encode]؛ سطرِ خراب یا نتیجهٔ ناشناخته `null` است، نه خطا. */
    fun decode(payload: String, at: Long): Contact? {
        val kv = payload.split('؛').mapNotNull { part ->
            val i = part.indexOf('=')
            if (i <= 0) null else part.substring(0, i).trim() to part.substring(i + 1).trim()
        }.toMap()
        val outcome = Outcome.of(kv["نتیجه"].orEmpty()) ?: return null
        return Contact(
            at = at,
            outcome = outcome,
            until = kv["تا"]?.toLongOrNull() ?: 0L,
            owedThen = kv["مانده"]?.toLongOrNull() ?: 0L,
        )
    }

    /** چرا این نفر در فهرست است. ترتیبِ enum همان ترتیبِ فهرست است. */
    enum class Reason(val label: String, val today: Boolean) {
        BROKEN_PROMISE("قولش گذشت", true),
        PROMISE_DUE("امروز روزِ قولش است", true),
        LATE_INSTALLMENT("قسطش گذشته", true),
        OLD_DEBT("بدهیِ مانده", true),
        WAITING_PROMISE("قول داده", false),
        RECENT("بدهیِ تازه", false),
    }

    data class Debtor(
        val name: String,
        val phone: String = "",
        /** طلبِ ما از او طبقِ دفتر — همان که ژورنال «طلب» می‌داند. */
        val owed: Long,
        /** زمانِ قدیمی‌ترین بدهیِ پرداخت‌نشده. */
        val oldestUnpaidAt: Long? = null,
        /** جمعِ ماندهٔ قسط‌هایی که روزشان گذشته. */
        val lateInstallment: Long = 0L,
        /** سررسیدِ قدیمی‌ترین قسطِ گذشته. */
        val lateInstallmentDue: Long = 0L,
        val lastContact: Contact? = null,
    ) {
        /** مبلغی که پیگیری می‌شود — هر کدام بزرگ‌تر است. */
        val amount: Long get() = maxOf(owed, lateInstallment)
    }

    data class Row(
        val debtor: Debtor,
        val reason: Reason,
        /** روزهای مربوط به دلیل — چند روز از قول/قسط/بدهی گذشته، یا تا قول مانده. */
        val days: Int,
        /** امروز یک بار تماس گرفته شده — ته‌ِ فهرست، ولی هنوز در آن. */
        val contactedToday: Boolean,
    )

    data class Plan(val today: List<Row>, val later: List<Row>) {
        val todayTotal: Long get() = today.sumOf { it.debtor.amount }
        val laterTotal: Long get() = later.sumOf { it.debtor.amount }
        /** امروز هنوز به چند نفر سر زده نشده. */
        val pending: Int get() = today.count { !it.contactedToday }
    }

    /**
     * @param todayStart نیمه‌شبِ امروز به وقتِ محلی — از بیرون، تا آزمون
     * به منطقهٔ زمانی بسته نباشد.
     */
    fun plan(debtors: List<Debtor>, now: Long, todayStart: Long): Plan {
        val tomorrow = todayStart + DAY
        val rows = debtors
            .filter { it.amount > 0L && it.name.isNotBlank() }
            .map { d -> row(d, now, todayStart, tomorrow) }
        val (today, later) = rows.partition { it.reason.today }
        return Plan(
            today = today.sortedWith(
                compareBy<Row> { it.contactedToday }
                    .thenBy { it.reason.ordinal }
                    .thenByDescending { it.debtor.amount }
                    .thenBy { it.debtor.name }
            ),
            later = later.sortedWith(
                compareBy<Row> { it.reason.ordinal }
                    .thenByDescending { it.debtor.amount }
                    .thenBy { it.debtor.name }
            ),
        )
    }

    private fun row(d: Debtor, now: Long, todayStart: Long, tomorrow: Long): Row {
        val c = d.lastContact
        val contactedToday = c != null && c.at >= todayStart
        fun daysSince(t: Long) = ((now - t) / DAY).toInt().coerceAtLeast(0)

        if (c != null && c.outcome == Outcome.PROMISED && c.until > 0L) {
            when {
                c.until >= tomorrow ->
                    return Row(
                        d, Reason.WAITING_PROMISE,
                        ((c.until - todayStart) / DAY).toInt(),
                        contactedToday
                    )
                c.until >= todayStart ->
                    return Row(d, Reason.PROMISE_DUE, 0, contactedToday)
                // روزِ قول گذشته و از آن لحظه چیزی نرسیده.
                d.amount >= c.owedThen ->
                    return Row(d, Reason.BROKEN_PROMISE, daysSince(c.until), contactedToday)
                // چیزی داده؛ بقیه‌اش مثلِ هر بدهیِ دیگری سنجیده می‌شود.
                else -> Unit
            }
        }
        // «قول داد» همین امروز یعنی سرِ امروز کارِ دیگری با او نیست — در
        // شاخهٔ بالا گرفته شد. اینجا تماس‌های بی‌قول‌اند.
        if (d.lateInstallment > 0L) {
            return Row(d, Reason.LATE_INSTALLMENT, daysSince(d.lateInstallmentDue), contactedToday)
        }
        val age = d.oldestUnpaidAt?.let { daysSince(it) } ?: 0
        return if (d.owed > 0L && age >= FOLLOW_UP_DAYS) {
            Row(d, Reason.OLD_DEBT, age, contactedToday)
        } else {
            Row(d, Reason.RECENT, age, contactedToday)
        }
    }

    /**
     * متنِ پیامِ یادآوری — مؤدب، کوتاه، با نامِ کارگاه و مبلغ.
     *
     * [amountText] و [dateText] از بیرون می‌آیند: قالبِ «۱۲٬۵۰۰ ؋» و
     * تاریخِ شمسی کارِ لایهٔ نمایش است، نه این قاعده.
     */
    fun message(shop: String, row: Row, amountText: String, dateText: String = ""): String {
        val who = row.debtor.name.trim()
        val from = if (shop.isNotBlank()) " از $shop" else ""
        val body = when (row.reason) {
            Reason.BROKEN_PROMISE ->
                "طبقِ صحبتِ قبلی قرار بود${if (dateText.isNotBlank()) " تا $dateText" else ""} " +
                    "حساب را تسویه کنید. ماندهٔ حسابِ شما $amountText است."
            Reason.PROMISE_DUE ->
                "طبقِ قرارمان امروز روزِ تسویه است. ماندهٔ حسابِ شما $amountText است."
            Reason.LATE_INSTALLMENT ->
                "قسطِ شما${if (dateText.isNotBlank()) " از تاریخِ $dateText" else ""} گذشته است. " +
                    "مبلغِ مانده: $amountText."
            else ->
                "ماندهٔ حسابِ شما $amountText است."
        }
        return "سلام $who جان،$from\n$body\nلطفاً در اولین فرصت تسویه کنید. تشکر."
    }
}
