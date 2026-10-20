package com.afghanjama.lan

import java.io.ByteArrayOutputStream
import java.io.InputStream

/**
 * خواندنِ یک درخواستِ HTTP از سوکت — **بایت به بایت، نه حرف به حرف.**
 *
 * **اشکالی که این فایل را ساخت.** سرور بدنه را با یک `Reader` و در یک
 * `CharArray(contentLength)` می‌خواند، یعنی منتظر می‌ماند تا
 * `Content-Length` **حرف** برسد. ولی `Content-Length` شمارِ **بایت**هاست،
 * و هر حرفِ دری در UTF-8 دو بایت است. پس بدنهٔ دری هرگز «کامل» نمی‌شد:
 * سرور تا سقفِ ده ثانیهٔ سوکت منتظر می‌ماند، گوشی بعد از هشت ثانیه
 * می‌گفت «وصل نشد»، و بعد سرور با رسیدنِ پایانِ جریان همان درخواست را
 * ثبت می‌کرد. کارگر دوباره می‌زد و درخواست دو تا می‌شد.
 *
 * دومی پنهان‌تر بود: `InputStreamReader` بی کدگذاریِ صریح، کدگذاریِ
 * پیش‌فرضِ سیستم را می‌گیرد. روی ویندوزی که JDKِ ۱۷ دارد آن کدگذاریِ
 * ANSIِ ویندوز است — cp1252 روی ویندوزِ انگلیسی، cp1256 روی ویندوزِ
 * فارسی یا دری — و هیچ‌کدام UTF-8 نیست؛ متنِ دری درهم ثبت می‌شد.
 *
 * حالا سرخط‌ها ASCII خوانده می‌شوند تا خطِ خالی، بعد **دقیقاً**
 * `Content-Length` بایت، و همان بایت‌ها با UTF-8 به متن برمی‌گردند. هیچ
 * چیزی اینجا به کدگذاریِ سیستم یا به `org.json` وابسته نیست، پس آزمونِ
 * خالصِ `:app` همین را می‌سنجد.
 */
object LanWire {

    /** سقفِ همهٔ سرخط‌ها، با خطِ درخواست. */
    const val MAX_HEADER_BYTES = 8 * 1024

    /** سقفِ شمارِ سرخط‌ها. */
    const val MAX_HEADER_LINES = 50

    /** سقفِ بدنه. بزرگ‌ترین درخواستِ کارگر چند صد بایت است. */
    const val MAX_BODY_BYTES = 64 * 1024

    const val TOO_LARGE = "درخواست خیلی بزرگ است."
    const val MALFORMED = "درخواست خوانده نشد."
    const val TRUNCATED = "بدنهٔ درخواست ناقص رسید."

    /** درخواستِ خوانده‌شده. نامِ سرخط‌ها کوچک‌حرف نگه داشته می‌شود. */
    class Request(
        val method: String,
        val target: String,
        private val headers: Map<String, String>,
        val body: String,
    ) {
        /** مقدارِ یک سرخط، بی‌توجه به بزرگی و کوچکیِ حرفِ نامش. */
        fun header(name: String): String? = headers[name.lowercase()]
    }

    sealed interface Read {
        data class Ok(val request: Request) : Read

        /** پیش از خطِ درخواست بسته شد — جوابی لازم نیست. */
        data object Closed : Read

        /** درخواستِ خراب یا بزرگ؛ با همین کد و پیام جواب داده می‌شود. */
        data class Bad(val status: Int, val message: String) : Read
    }

    private class Over : Exception()

    /**
     * یک درخواست را از [input] می‌خواند و **یک بایت هم بیشتر** نمی‌خواند.
     *
     * [input] بهتر است بافردار باشد؛ خواندنِ سرخط‌ها بایت‌به‌بایت است.
     */
    fun readRequest(input: InputStream): Read {
        var budget = MAX_HEADER_BYTES

        fun line(): String? {
            val buf = ByteArrayOutputStream()
            while (true) {
                val b = input.read()
                if (b < 0) return if (buf.size() == 0) null else buf.toString(Charsets.ISO_8859_1.name())
                if (--budget < 0) throw Over()
                if (b == '\n'.code) break
                buf.write(b)
            }
            val s = buf.toString(Charsets.ISO_8859_1.name())
            return if (s.endsWith('\r')) s.dropLast(1) else s
        }

        try {
            val requestLine = line() ?: return Read.Closed
            val parts = requestLine.split(' ').filter { it.isNotEmpty() }
            if (parts.size < 2) return Read.Bad(400, MALFORMED)

            val headers = mutableMapOf<String, String>()
            var count = 0
            while (true) {
                // پایانِ جریان پیش از خطِ خالی یعنی سرخط‌ها ناقص‌اند.
                val h = line() ?: return Read.Bad(400, MALFORMED)
                if (h.isEmpty()) break
                if (++count > MAX_HEADER_LINES) return Read.Bad(413, TOO_LARGE)
                val i = h.indexOf(':')
                if (i <= 0) continue
                headers[h.substring(0, i).trim().lowercase()] = h.substring(i + 1).trim()
            }

            val length = headers["content-length"]?.let {
                it.toIntOrNull()?.takeIf { n -> n >= 0 } ?: return Read.Bad(400, MALFORMED)
            } ?: 0
            if (length > MAX_BODY_BYTES) return Read.Bad(413, TOO_LARGE)

            val bytes = ByteArray(length)
            var read = 0
            while (read < length) {
                val n = input.read(bytes, read, length - read)
                if (n < 0) return Read.Bad(400, TRUNCATED)
                read += n
            }
            return Read.Ok(Request(parts[0], parts[1], headers, String(bytes, Charsets.UTF_8)))
        } catch (_: Over) {
            return Read.Bad(413, TOO_LARGE)
        }
    }
}
