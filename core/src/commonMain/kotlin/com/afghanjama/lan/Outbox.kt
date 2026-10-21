package com.afghanjama.lan

/**
 * صفِ ارسالِ گوشی — کاری که وقتِ قطع بودن زده شد گم نمی‌شود.
 *
 * **چرا لازم شد.** در قراردادِ ۱ گوشیِ کارگر «ورود» را همان لحظه می‌فرستاد؛
 * اگر وای‌فای نبود، پیامِ «وصل نشد» می‌آمد و کار همان‌جا تمام بود — کارگر
 * باید یادش می‌ماند دوباره بزند، و اگر می‌زد و جوابِ اولی دیر رسیده بود،
 * دو درخواست می‌نشست.
 *
 * حالا هر کار با یک **کلیدِ تکرارنشدنی** و **لحظهٔ زدنِ دکمه** در صف
 * می‌نشیند و با هر همگام‌سازی فرستاده می‌شود تا کامپیوتر جواب بدهد. کامپیوتر
 * با همان کلید تکرار را می‌شناسد ([AckStatus.DUPLICATE])، پس فرستادنِ
 * دوباره هرگز دو ردیف نمی‌سازد.
 *
 * **هیچ کاری بی‌صدا نمی‌رود.** هر کار یکی از این‌هاست و گوشی نشانش می‌دهد:
 *
 * - [OutboxState.PENDING] «در صفِ ارسال» — با تلاشِ دوباره، هر بار دیرتر
 *   ([backoff])، و فوراً وقتی شبکه برگشت یا برنامه جلو آمد ([retryNow]).
 * - [OutboxState.SENT] رسید — کامپیوتر گرفت (منتظرِ تأیید، یا انجام شد).
 * - [OutboxState.REJECTED] «رد شد: …» — با دلیلِ خودِ کامپیوتر.
 * - [OutboxState.EXPIRED] «فرستاده نشد — کهنه شد» — پس از هفت روز.
 * - [OutboxState.ORPHANED] — گوشی دوباره جفت شد و حالا مالِ کسِ دیگری است؛
 *   کارِ نفرِ قبلی به نامِ نفرِ تازه فرستاده نمی‌شود.
 *
 * خالص است (بی شبکه، بی ساعت، بی دیسک) تا آزمونِ JVM همه را بسنجد.
 */
enum class OutboxState { PENDING, SENT, REJECTED, EXPIRED, ORPHANED }

data class OutboxItem(
    val key: String,
    val type: String,
    val payload: Map<String, Any?>,
    /** آنچه گوشی در فهرست نشان می‌دهد: «ورود»، «AJ-12 — ۵ عدد». */
    val summary: String,
    /** لحظهٔ زدنِ دکمه، با ساعتِ خودِ گوشی. */
    val createdAt: Long,
    /** جفتی که این کار زیرِ آن زده شد — و نفرش. */
    val deviceId: String,
    val person: String,
    val state: OutboxState = OutboxState.PENDING,
    val attempts: Int = 0,
    val nextTryAt: Long = createdAt,
    val ack: AckStatus? = null,
    val reason: String = "",
    val doneAt: Long? = null,
) {
    fun toWire(): OutboxWire = OutboxWire(key, createdAt, type, payload)

    fun toJson(): Map<String, Any?> = mapOf(
        "key" to key, "type" to type, "p" to payload, "summary" to summary, "at" to createdAt,
        "device" to deviceId, "person" to person, "state" to state.name, "attempts" to attempts,
        "next" to nextTryAt, "ack" to ack?.name, "reason" to reason, "done" to doneAt,
    )

    companion object {
        fun parse(o: Map<String, Any?>): OutboxItem? {
            return OutboxItem(
                key = o.jStr("key") ?: return null,
                type = o.jStr("type") ?: return null,
                payload = o.jObj("p") ?: emptyMap(),
                summary = o.jStr("summary").orEmpty(),
                createdAt = o.jLong("at") ?: return null,
                deviceId = o.jStr("device").orEmpty(),
                person = o.jStr("person").orEmpty(),
                state = runCatching { OutboxState.valueOf(o.jStr("state").orEmpty()) }.getOrNull() ?: return null,
                attempts = o.jInt("attempts") ?: 0,
                nextTryAt = o.jLong("next") ?: 0L,
                ack = o.jStr("ack")?.let { a -> runCatching { AckStatus.valueOf(a) }.getOrNull() },
                reason = o.jStr("reason").orEmpty(),
                doneAt = o.jLong("done"),
            )
        }
    }
}

object Outbox {

    /** کارِ فرستاده‌نشده پس از این «کهنه» است. */
    const val EXPIRE_MS = 7 * 24 * 3_600_000L

    /** چند کارِ تمام‌شده (رسیده، ردشده…) برای دیدن می‌ماند. */
    const val KEEP_DONE = 50

    /** سقفِ کلِ صف — گوشی‌ای که یک ماه بیرون ماند هم بی‌نهایت جا نمی‌گیرد. */
    const val MAX_PENDING = 500

    private val STEPS = longArrayOf(5_000, 15_000, 30_000, 60_000, 120_000, 300_000)

    /** فاصلهٔ تلاشِ بعدی پس از [attempts] شکست: ۵ ثانیه تا ۵ دقیقه. */
    fun backoff(attempts: Int): Long = STEPS[(attempts - 1).coerceIn(0, STEPS.size - 1)]

    const val EXPIRED_REASON = "فرستاده نشد — کهنه شد (بیش از هفت روز به کامپیوترِ کارگاه نرسید)."
    const val ORPHANED_REASON = "فرستاده نشد — این گوشی دوباره جفت شد و حالا به نامِ کسِ دیگری است."
    const val FULL_REASON = "صفِ ارسال پر است؛ اول به کامپیوترِ کارگاه وصل شوید."

    /**
     * کارِ تازه ته‌ِ صف. اگر صف پر است، کار پذیرفته نمی‌شود و [FULL_REASON]
     * برمی‌گردد — صداکننده همان را به کاربر می‌گوید؛ هیچ کارِ قدیمی‌ای برای
     * جا باز کردن دور ریخته نمی‌شود.
     */
    fun add(items: List<OutboxItem>, item: OutboxItem): Pair<List<OutboxItem>, String?> {
        if (items.any { it.key == item.key }) return items to null
        if (pendingCount(items) >= MAX_PENDING) return items to FULL_REASON
        return trim(items + item) to null
    }

    fun pendingCount(items: List<OutboxItem>): Int = items.count { it.state == OutboxState.PENDING }

    /** آنچه همین حالا باید فرستاده شود: منتظر، وقتش رسیده، قدیمی‌ترین اول. */
    fun due(items: List<OutboxItem>, now: Long, max: Int = HubProtocol.MAX_OUTBOX_PER_SYNC): List<OutboxItem> =
        items.filter { it.state == OutboxState.PENDING && it.nextTryAt <= now }
            .sortedWith(compareBy({ it.createdAt }, { it.key }))
            .take(max)

    /** کارهای منتظری که از [EXPIRE_MS] گذشته‌اند «کهنه» می‌شوند. */
    fun expire(items: List<OutboxItem>, now: Long): List<OutboxItem> = items.map {
        if (it.state == OutboxState.PENDING && now - it.createdAt > EXPIRE_MS) {
            it.copy(state = OutboxState.EXPIRED, reason = EXPIRED_REASON, doneAt = now)
        } else it
    }

    /** فرستادنِ [keys] نرسید (شبکه): تلاشِ بعدی دیرتر. */
    fun failed(items: List<OutboxItem>, keys: Collection<String>, now: Long): List<OutboxItem> = items.map {
        if (it.state == OutboxState.PENDING && it.key in keys) {
            val a = it.attempts + 1
            it.copy(attempts = a, nextTryAt = now + backoff(a))
        } else it
    }

    /**
     * جوابِ کامپیوتر. کاری که جوابی برایش نیامد منتظر می‌ماند (و دوباره
     * فرستاده می‌شود؛ کامپیوتر تکرار را می‌شناسد).
     */
    fun acked(items: List<OutboxItem>, acks: List<Ack>, now: Long): List<OutboxItem> {
        val byKey = acks.associateBy { it.key }
        return trim(items.map { item ->
            val a = byKey[item.key]
            if (a == null || item.state != OutboxState.PENDING) item
            else when (a.status) {
                AckStatus.REJECTED -> item.copy(state = OutboxState.REJECTED, ack = a.status, reason = a.reason, doneAt = now)
                else -> item.copy(state = OutboxState.SENT, ack = a.status, reason = a.reason, doneAt = now)
            }
        })
    }

    /** شبکه برگشت یا برنامه جلو آمد: همهٔ منتظرها همین حالا. */
    fun retryNow(items: List<OutboxItem>, now: Long): List<OutboxItem> = items.map {
        if (it.state == OutboxState.PENDING && it.nextTryAt > now) it.copy(nextTryAt = now) else it
    }

    /**
     * گوشی دوباره جفت شد. کارِ منتظرِ همان نفر با جفتِ تازه می‌رود؛ کارِ نفرِ
     * دیگر [OutboxState.ORPHANED] می‌شود — هرگز به نامِ نفرِ تازه فرستاده
     * نمی‌شود.
     */
    fun rebind(items: List<OutboxItem>, deviceId: String, person: String, now: Long): List<OutboxItem> = items.map {
        when {
            it.state != OutboxState.PENDING -> it
            it.person.trim() == person.trim() -> it.copy(deviceId = deviceId)
            else -> it.copy(state = OutboxState.ORPHANED, reason = ORPHANED_REASON, doneAt = now)
        }
    }

    /** کاربر کارهای تمام‌شده را از فهرست پاک کرد. منتظرها می‌مانند. */
    fun clearDone(items: List<OutboxItem>): List<OutboxItem> = items.filter { it.state == OutboxState.PENDING }

    /** منتظرها همه می‌مانند؛ از تمام‌شده‌ها فقط تازه‌ترین [KEEP_DONE]. */
    fun trim(items: List<OutboxItem>): List<OutboxItem> {
        val done = items.filter { it.state != OutboxState.PENDING }
        if (done.size <= KEEP_DONE) return items
        val keep = done.sortedByDescending { it.doneAt ?: it.createdAt }.take(KEEP_DONE).map { it.key }.toSet()
        return items.filter { it.state == OutboxState.PENDING || it.key in keep }
    }

    fun encode(items: List<OutboxItem>): String = LanJson.write(items.map { it.toJson() })

    /** فایلِ خراب صفِ خالی نمی‌شود بی‌صدا: `null` برمی‌گردد و صداکننده آن را نگه می‌دارد. */
    fun decode(text: String): List<OutboxItem>? {
        if (text.isBlank()) return emptyList()
        val arr = runCatching { LanJson.parse(text) }.getOrNull() as? List<*> ?: return null
        return arr.map { LanJson.asObject(it)?.let { o -> OutboxItem.parse(o) } ?: return null }
    }

    /** وضعِ یک کار به زبانِ کاربر. */
    fun label(item: OutboxItem): String = when (item.state) {
        OutboxState.PENDING -> "در صفِ ارسال"
        OutboxState.SENT -> when (item.ack) {
            AckStatus.APPLIED -> "انجام شد"
            AckStatus.DUPLICATE -> "رسیده بود"
            else -> "رسید — منتظرِ تأییدِ کارفرما"
        }
        OutboxState.REJECTED -> "رد شد: ${item.reason}"
        OutboxState.EXPIRED, OutboxState.ORPHANED -> item.reason
    }
}

/**
 * ساعتِ «ورود» و «خروج»ی که از گوشی می‌رسد.
 *
 * **چرا لازم شد.** حضور باید ساعتِ زدنِ دکمه را بگیرد، نه ساعتِ تأییدِ
 * کارفرما و نه ساعتِ رسیدن (کارِ آفلاین ساعت‌ها بعد می‌رسد). ولی ساعتِ
 * گوشیِ ارزان اغلب غلط است. پس:
 *
 * 1. **تصحیح:** گوشی با هر درخواست ساعتِ خامِ خودش را هم می‌فرستد
 *    ([SyncAsk.phoneNow]). فاصلهٔ آن تا ساعتِ کامپیوتر در لحظهٔ رسیدن همان
 *    خطای ساعتِ گوشی است؛ از لحظهٔ زدنِ دکمه کم می‌شود ([corrected]).
 * 2. **بازه:** ساعتِ تصحیح‌شده فقط اگر در `[رسیدن − ۱۲ ساعت، رسیدن + ۵ دقیقه]`
 *    باشد پذیرفته است ([trusted]). بیرون از آن «ساعتِ گوشی معتبر نیست»
 *    می‌شود و کارفرما خودش یکی از دو ساعت را انتخاب می‌کند — برنامه
 *    حدس نمی‌زند.
 *
 * بازه با **ساعتِ رسیدن** سنجیده می‌شود، نه ساعتِ تأیید؛ وگرنه تأییدِ دیرِ
 * کارفرما درخواستِ درستی را «نامعتبر» می‌کرد.
 */
object AttendanceClock {

    const val PAST_MS = 12 * 3_600_000L
    const val FUTURE_MS = 5 * 60_000L

    /** هر زمانی بیرون از این بازه (۲۰۰۱ تا ۲۱۰۰) بی‌معناست و پذیرفته نمی‌شود. */
    private const val SANE_MIN = 978_307_200_000L
    private const val SANE_MAX = 4_102_444_800_000L

    private fun sane(t: Long) = t in SANE_MIN..SANE_MAX

    /**
     * لحظهٔ زدنِ دکمه به ساعتِ کامپیوتر؛ `null` اگر یکی از سه زمان بی‌معناست
     * (آن‌وقت درخواست «ساعت نامعتبر» است).
     */
    fun corrected(pressedPhone: Long, phoneNow: Long, receivedAt: Long): Long? {
        if (!sane(pressedPhone) || !sane(phoneNow) || !sane(receivedAt)) return null
        return pressedPhone + (receivedAt - phoneNow)
    }

    fun trusted(pressedAt: Long?, receivedAt: Long): Boolean =
        pressedAt != null && pressedAt >= receivedAt - PAST_MS && pressedAt <= receivedAt + FUTURE_MS
}
