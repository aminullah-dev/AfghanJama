package com.afghanjama.lan

/**
 * QRِ «افزودنِ گوشی» در قراردادِ ۲ — و رمزِ دستیِ جایگزینش.
 *
 * قالب: `kylink2:<نشانی>:<درگاه>:<hubId>:<pairingId>:<راز>`، مثلِ
 *
 *     kylink2:192.168.1.7:8797:5f0c…-…:K7QH3M2P4XR5T:NB2HI4DTHIXS6Y3PNVUW…
 *
 * - **راز** ۱۲۸ بیتِ تصادفی است (Base32ِ ۲۶ حرفی) و فقط در همین QR است؛
 *   روی شبکه هرگز نمی‌رود. گوشی‌ای که آن را دارد ثابت می‌کند QR را دیده.
 * - **hubId** شناسهٔ دفترِ کارگاه است؛ گوشی بعدها کامپیوتر را با همین
 *   پیدا می‌کند، حتی اگر نشانی‌اش عوض شود ([HubFinder]).
 * - **pairingId** شناسهٔ همین یک پنجره است؛ QRِ پنجرهٔ قبلی کار نمی‌کند.
 *
 * QRِ قراردادِ ۱ (`kylink:`) سرِ جایش است ([Pairing]) و هر دو خوانده
 * می‌شوند: گوشیِ تازه با کامپیوترِ قدیمی هم وصل می‌شود.
 *
 * **«kylink2» یک scheme است.** دوربینِ خودِ آیفون QRی را که schemeش مالِ
 * برنامه‌ای است «باز کن در …» نشان می‌دهد؛ برنامهٔ آیفون همین را ثبت
 * می‌کند و بی اسکنرِ درونی جفت می‌شود.
 */
object PairingV2 {

    const val PREFIX = "kylink2:"

    /** چند بایت رازِ QR: ۱۲۸ بیت. */
    const val SECRET_BYTES = 16

    data class Link(
        val host: String,
        val port: Int,
        val hubId: String,
        val pairingId: String,
        /** رازِ ۱۶ بایتی. */
        val secret: ByteArray,
    ) {
        // راز هرگز در گزارش و پیامِ خطا نمی‌نشیند.
        override fun toString(): String = "Link(host=$host, port=$port, hubId=$hubId, pairingId=$pairingId, secret=…)"
        override fun equals(other: Any?): Boolean =
            other is Link && host == other.host && port == other.port && hubId == other.hubId &&
                pairingId == other.pairingId && LanCrypto.sameBytes(secret, other.secret)
        override fun hashCode(): Int = (host + port + hubId + pairingId).hashCode()
    }

    fun encode(l: Link): String =
        PREFIX + listOf(l.host, l.port.toString(), l.hubId, l.pairingId, LanCrypto.b32(l.secret)).joinToString(":")

    private val ID = Regex("^[A-Za-z0-9-]{8,64}$")

    /** QRِ خوانده‌شده؛ `null` اگر QRِ قراردادِ ۲ نیست یا جایی از آن نادرست است. */
    fun parse(raw: String): Link? {
        val s = raw.trim()
        if (!s.startsWith(PREFIX, ignoreCase = true)) return null
        val f = s.substring(PREFIX.length).split(':')
        if (f.size != 5) return null
        val host = (HostAddress.check(f[0]) as? HostAddress.Result.Ok)?.host ?: return null
        val port = f[1].toIntOrNull()?.takeIf { it in 1..65535 } ?: return null
        if (!ID.matches(f[2]) || !ID.matches(f[3])) return null
        val secret = LanCrypto.unb32(f[4])?.takeIf { it.size == SECRET_BYTES } ?: return null
        return Link(host, port, f[2], f[3], secret)
    }

    /** آیا این متن QRِ خیاط‌یار است (هر نسخه)؟ برای پیامِ «این QR کهنه یا خراب است». */
    fun looksLikeOurs(raw: String): Boolean {
        val s = raw.trim()
        return s.startsWith(PREFIX, ignoreCase = true) || s.startsWith(Pairing.PREFIX, ignoreCase = true)
    }

    /** چرا [parse] نپذیرفت — به زبانِ کاربر. */
    fun refusal(raw: String): String {
        val s = raw.trim()
        if (!s.startsWith(PREFIX, ignoreCase = true)) return Pairing.refusal(raw)
        val host = s.substring(PREFIX.length).substringBefore(':')
        (HostAddress.check(host) as? HostAddress.Result.Bad)?.let { return it.reason }
        return "این QRِ «افزودنِ گوشی» خوانده نشد؛ روی کامپیوترِ کارگاه دوباره «افزودنِ گوشی» را بزنید."
    }

    // ---------------------------------------------------------------- رمزِ دستی

    /**
     * رمزِ دستی برای گوشی‌ای که نمی‌تواند QR بخوانَد: **۱۲ رقم**، سه تکهٔ
     * چهاررقمی («۴۸۲۹ ۱۳۵۷ ۲۰۴۶»).
     *
     * طرح هشت حرف می‌گفت؛ رقم شد، چون کارگرِ کارگاه رقمِ فارسی را راحت‌تر از
     * حروفِ لاتین می‌خوانَد و کیبوردِ عددیِ گوشی خطای تایپ ندارد. ۱۲ رقم همان
     * ۴۰ بیتِ هشت حرفِ Base32 است. حدسِ آنلاین با [HubProtocol.PAIR_MAX_FAILURES]
     * بسته است (ده حدس، بعد پنجره بسته)، و چون کلید از ECDH می‌آید، شنودِ
     * وای‌فای هم چیزی برای حدسِ بی‌اتصال نمی‌دهد. تنها راهِ حمله دست بردن در
     * همان ده دقیقه وسطِ راه است، و آنجا هم کارفرما گوشی را با نام روی صفحه
     * می‌بیند و «اجازه» می‌دهد. برای همین **گوشیِ صاحبِ کار فقط با QR**
     * جفت می‌شود.
     */
    fun newManualCode(): String {
        val digits = StringBuilder()
        while (digits.length < HubProtocol.MANUAL_CODE_DIGITS) {
            // بایتِ ≥ ۲۵۰ کنار می‌رود تا هر رقم دقیقاً یک‌دهم شانس داشته باشد.
            for (b in LanPrims.random(16)) {
                val v = b.toInt() and 0xFF
                if (v < 250 && digits.length < HubProtocol.MANUAL_CODE_DIGITS) digits.append('0' + v % 10)
            }
        }
        return digits.toString()
    }

    /** آنچه کاربر تایپ کرده → فقط ۱۲ رقمِ لاتین؛ رقمِ فارسی و عربی هم. `null` اگر ۱۲ رقم نشد. */
    fun normalizeManualCode(raw: String): String? {
        val sb = StringBuilder()
        for (c in raw) {
            when (c) {
                in '0'..'9' -> sb.append(c)
                in '۰'..'۹' -> sb.append('0' + (c - '۰'))
                in '٠'..'٩' -> sb.append('0' + (c - '٠'))
                ' ', '-', ' ', '‌' -> Unit
                else -> return null
            }
        }
        return sb.toString().takeIf { it.length == HubProtocol.MANUAL_CODE_DIGITS }
    }

    /** «۴۸۲۹ ۱۳۵۷ ۲۰۴۶» — برای نمایش روی کامپیوتر (رقم را صفحه فارسی می‌کند). */
    fun groupManualCode(code: String): String = code.chunked(4).joinToString(" ")
}
