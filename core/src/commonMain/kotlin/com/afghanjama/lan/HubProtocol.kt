package com.afghanjama.lan

/**
 * قراردادِ ۲ — «کامپیوتر مادرِ کارگاه است».
 *
 * همان درگاهِ ۸۷۹۷ و همان HTTP/1.1 که قراردادِ ۱ داشت، با سه فرق:
 *
 * 1. **هویت از جفت شدن می‌آید، نه از درخواست.** گوشیِ کارگر دیگر نمی‌گوید
 *    «من احمدم»؛ کامپیوتر سرِ «اجازه» گوشی را به یک نفر می‌بندد و از آن پس
 *    هر چه از آن گوشی برسد مالِ همان نفر است.
 * 2. **هر چیزی که داده دارد رمز شده است** ([LanCrypto])، و هر درخواست
 *    زمان و nonce دارد تا تکرارش پذیرفته نشود.
 * 3. **یک رفت‌وبرگشت هر دو سو را می‌برد** ([PATH_SYNC]): گوشی صفِ کارهای
 *    آفلاینش را می‌فرستد و تصویرِ تازهٔ نقشِ خودش را می‌گیرد.
 *
 * مسیرهای قراردادِ ۱ (`/my-work`، `/board`، `/request`) سرِ جایشان‌اند، تا
 * گوشیِ ۱.۹ و ۱.۱۰ کار کند یا پیامِ روشنِ «به‌روز کنید» بگیرد.
 */
object HubProtocol {

    /** پایین‌ترین و بالاترین قراردادی که این برنامه می‌فهمد. */
    const val MIN = 1
    const val MAX = 2

    /** همان درگاهِ قراردادِ ۱ ([Lan.PORT] همین را می‌خوانَد). */
    const val PORT = 8797

    const val PATH_HELLO = "/v2/pair/hello"
    const val PATH_PAIR = "/v2/pair"
    const val PATH_PAIR_STATUS = "/v2/pair/status"
    const val PATH_SYNC = "/v2/sync"

    const val HEADER_DEVICE = "X-KY-Device"
    const val HEADER_TIME = "X-KY-Time"
    const val HEADER_NONCE = "X-KY-Nonce"

    /** درخواستی که زمانش بیش از این با ساعتِ کامپیوتر فرق کند، پذیرفته نمی‌شود. */
    const val TIME_WINDOW_MS = 10 * 60_000L

    /**
     * nonceِ هر درخواست تا این مدت به یاد می‌ماند — **بیش از دو برابرِ
     * [TIME_WINDOW_MS]**: درخواستی که زمانش ده دقیقه جلوتر بود، تا ده دقیقه
     * پس از آن هم در بازه است؛ بیست دقیقه کم‌ترین است، بیست‌وپنج با حاشیه.
     */
    const val NONCE_KEEP_MS = 25 * 60_000L

    /** پنجرهٔ «افزودنِ گوشی». */
    const val PAIR_WINDOW_MS = 10 * 60_000L

    /** رمزِ دستیِ نادرست تا این شمار، بعد پنجره بسته می‌شود. */
    const val PAIR_MAX_FAILURES = 10

    /** سقفِ انتظارِ گوشی برای «اجازه» در یک درخواست. */
    const val PAIR_LONG_POLL_MS = 25_000L

    /** و سقفِ انتظارِ خودِ گوشی — بیش از [PAIR_LONG_POLL_MS]، وگرنه زودتر قطع می‌کرد. */
    const val PAIR_CLIENT_TIMEOUT_MS = 32_000

    /** هر بار گوشی در صفِ ارسال حداکثر این شمار کار می‌فرستد. */
    const val MAX_OUTBOX_PER_SYNC = 50

    /** هر ۱۵ ثانیه، وقتی برنامه جلوی چشم است — همان آهنگِ تابلو. */
    const val POLL_MS = 15_000L

    /** «آنلاین» یعنی همگام‌سازی در این مدتِ اخیر. */
    const val ONLINE_MS = 45_000L

    /** شمارِ ارقامِ رمزِ دستی؛ ۱۲ رقم ≈ ۴۰ بیت. */
    const val MANUAL_CODE_DIGITS = 12

    // -------------------------------------------------------- دادهٔ همراه

    /**
     * آنچه کلیدِ جفت شدن را می‌سازد جز دو راز: شناسهٔ پنجره، راه (QR یا
     * رمزِ دستی) و هر دو کلیدِ عمومی.
     */
    fun pairTranscript(pairingId: String, via: PairVia, hubPub: ByteArray, phonePub: ByteArray): String =
        "ky2|pair|$pairingId|${via.wire}|${LanCrypto.b64(hubPub)}|${LanCrypto.b64(phonePub)}"

    fun aadPairRequest(pairingId: String, via: PairVia): String = "ky2|pair-request|$pairingId|${via.wire}"

    fun aadPairStatus(requestId: String): String = "ky2|pair-status|$requestId"

    /** جوابِ «اجازه» به nonceِ همان پرسش بسته است — جوابِ کهنه به پرسشِ تازه نمی‌خورد. */
    fun aadPairReply(requestId: String, askNonce: ByteArray): String =
        "ky2|pair-reply|$requestId|${LanCrypto.b64(askNonce)}"

    fun aadRequest(path: String, deviceId: String, time: Long, nonce: ByteArray): String =
        "ky2|req|POST $path|$deviceId|$time|${LanCrypto.b64(nonce)}"

    fun aadResponse(path: String, deviceId: String, requestNonce: ByteArray): String =
        "ky2|resp|POST $path|$deviceId|${LanCrypto.b64(requestNonce)}"

    // -------------------------------------------------------- کدِ خطا

    /** کدهای خطای بیرونِ پاکت — هیچ‌کدام چیزی از دفتر نمی‌گوید. */
    const val ERR_AUTH = "AUTH"
    const val ERR_TIME = "TIME"
    const val ERR_BLOCKED = "BLOCKED"
    const val ERR_CLOSED = "CLOSED"
    const val ERR_BUSY = "BUSY"
    const val ERR_ROLE = "ROLE"
    const val ERR_DUPLICATE_HUB = "DUP_HUB"
}

/** گوشی با QR جفت شده یا با رمزِ دستی. */
enum class PairVia(val wire: String) {
    QR("qr"),
    CODE("code");

    companion object {
        fun of(s: String?): PairVia? = entries.firstOrNull { it.wire == s }
    }
}

/**
 * نقشِ یک دستگاهِ جفت‌شده — **کامپیوتر تعیین می‌کند، نه گوشی.**
 *
 * گوشی فقط می‌گوید چه می‌خواهد؛ کارفرما سرِ «اجازه» نقش و (برای کارگر) آدم
 * را انتخاب می‌کند.
 */
enum class HubRole(val wire: String, val label: String) {
    OWNER("owner", "گوشیِ صاحبِ کار"),
    WORKER("worker", "گوشیِ کارگر"),
    WALL("wall", "تابلوی دیوار");

    companion object {
        fun of(s: String?): HubRole? = entries.firstOrNull { it.wire == s }
    }
}

/**
 * پاسخِ کامپیوتر به یک کارِ صفِ ارسال.
 *
 * - [QUEUED]: در صندوقِ درخواست‌ها نشست و منتظرِ تأییدِ کارفرماست.
 * - [APPLIED]: همان لحظه اجرا شد (فرمانِ گوشیِ صاحبِ کار).
 * - [REJECTED]: اجرا نشد؛ دلیل همراهش است و گوشی نشانش می‌دهد.
 * - [DUPLICATE]: همین کار پیش‌تر رسیده بود؛ دوباره ثبت نشد.
 */
enum class AckStatus { QUEUED, APPLIED, REJECTED, DUPLICATE }

/**
 * کارهایی که از گوشی پذیرفته می‌شوند — فهرستِ بسته؛ هر نوعِ دیگر رد.
 * [roles] می‌گوید کدام نقش حقِ فرستادنش را دارد.
 */
enum class HubCommand(val wire: String, val roles: Set<HubRole>) {
    SEWING_DONE("SEWING_DONE", setOf(HubRole.WORKER)),
    ATTENDANCE_IN("ATTENDANCE_IN", setOf(HubRole.WORKER)),
    ATTENDANCE_OUT("ATTENDANCE_OUT", setOf(HubRole.WORKER)),
    NOTE("NOTE", setOf(HubRole.WORKER)),
    APPROVE_REQUEST("APPROVE_REQUEST", setOf(HubRole.OWNER)),
    REJECT_REQUEST("REJECT_REQUEST", setOf(HubRole.OWNER)),
    DEBT_FOLLOW_UP("DEBT_FOLLOW_UP", setOf(HubRole.OWNER));

    /** کارِ کارگر در صندوقِ درخواست‌ها می‌نشیند؛ فرمانِ صاحب‌کار اجرا می‌شود. */
    val queues: Boolean get() = HubRole.WORKER in roles

    companion object {
        fun of(s: String?): HubCommand? = entries.firstOrNull { it.wire == s }
    }
}
