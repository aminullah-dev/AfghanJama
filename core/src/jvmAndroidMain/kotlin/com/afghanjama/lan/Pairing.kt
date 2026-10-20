package com.afghanjama.lan

/**
 * QRِ اتصال — نشانی و رمزِ دستگاهِ اصلی در یک تصویر.
 *
 * **مسئله‌ای که حل می‌کند.** وصل کردنِ گوشیِ کارگر یعنی تایپِ یک نشانیِ
 * چهاربخشی و یک رمزِ شش‌رقمی روی کیبوردِ گوشی. [HostAddress] از یک
 * گزارشِ واقعی ساخته شد: کاربر `10.0.0101` زده بود — یک نقطه کم — و
 * نیم ساعت دنبالِ ایرادِ وای‌فای گشت. با QR تایپی در کار نیست.
 *
 * قالب: `kylink:<نشانی>:<رمز>` — مثلِ `kylink:192.168.1.7:482913`.
 * درگاه نمی‌آید چون ثابت است ([Lan.PORT]).
 */
object Pairing {

    const val PREFIX = "kylink:"

    fun encode(host: String, code: String): String = PREFIX + host.trim() + ":" + code.trim()

    /** نشانی و رمزِ خوانده‌شده، یا `null` اگر QRِ اتصالِ کارگاه نیست. */
    data class Link(val host: String, val code: String)

    fun parse(raw: String): Link? {
        val s = raw.trim()
        if (!s.startsWith(PREFIX, ignoreCase = true)) return null
        val rest = s.substring(PREFIX.length)
        val host = rest.substringBeforeLast(':', missingDelimiterValue = "")
        val code = rest.substringAfterLast(':', missingDelimiterValue = "")
        val ok = HostAddress.check(host) as? HostAddress.Result.Ok ?: return null
        if (code.length !in 4..6 || code.any { it !in '0'..'9' }) return null
        return Link(ok.host, code)
    }

    /** پیامِ QRی که اصلاً QRِ اتصالِ کارگاه نیست. */
    const val NOT_PAIRING =
        "این QRِ اشتراکِ کارگاه نیست. روی گوشیِ اصلی صفحهٔ «اشتراک کارگاه» را باز کنید."

    /**
     * چرا [parse] این QR را نپذیرفت — برای پیامِ اسکن.
     *
     * QRی که با `kylink:` شروع می‌شود واقعاً QRِ خیاط‌یار است. اگر فقط
     * نشانی‌اش رد شده باشد (مثلاً نشانیِ عمومی که گوشیِ ۱.۹.۱ نمی‌پذیرد)،
     * گفتنِ «این QRِ اشتراکِ کارگاه نیست» کاربر را دنبالِ QRِ دیگری
     * می‌فرستاد؛ پس همان دلیلِ [HostAddress] گفته می‌شود.
     */
    fun refusal(raw: String): String {
        val s = raw.trim()
        if (!s.startsWith(PREFIX, ignoreCase = true)) return NOT_PAIRING
        val host = s.substring(PREFIX.length).substringBeforeLast(':', missingDelimiterValue = "")
        return (HostAddress.check(host) as? HostAddress.Result.Bad)?.reason ?: NOT_PAIRING
    }
}
