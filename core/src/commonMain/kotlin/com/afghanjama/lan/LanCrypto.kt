package com.afghanjama.lan

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * پاکتِ رمزِ قراردادِ ۲ — آنچه روی [LanPrims] ساخته می‌شود.
 *
 * **چرا لازم شد.** در قراردادِ ۱ رمزِ شش‌رقمی با هر درخواست، بی رمز، روی
 * وای‌فای می‌رفت و کارمزدِ هر خیاط هم بی رمز برمی‌گشت؛ هر کسی روی همان
 * وای‌فای هر دو را می‌خواند. از قراردادِ ۲ هر چیزی که داده دارد در AES-256-GCM
 * پیچیده است، با کلیدی که فقط همان یک گوشی و کامپیوترِ کارگاه دارند.
 *
 * سه کلید، سه کار، هیچ‌کدام دوبار:
 *
 * - **کلیدِ جفت شدن** ([pairKey]): از ECDHِ گذرای دو سو، که رازِ QR (یا رمزِ
 *   دستی) آن را می‌سنجد. شنودِ وای‌فای بی کلیدِ خصوصیِ یکی از دو سو هیچ
 *   نمی‌فهمد؛ دانستنِ رازِ QR بی ECDH هم هیچ.
 * - **کلیدِ دستگاه**: ۳۲ بایتِ تصادفی که کامپیوتر سرِ «اجازه» می‌سازد و یک
 *   بار در پاکتِ جفت شدن به گوشی می‌دهد.
 * - **دو کلیدِ جهت‌دار** ([directional]): از کلیدِ دستگاه، یکی برای رفت و یکی
 *   برای برگشت. پیامِ کامپیوتر هرگز به‌جای پیامِ گوشی پذیرفته نمی‌شود.
 *
 * هر پاکت **دادهٔ همراه** (AAD) دارد که مسیر، دستگاه، زمان و nonce را به
 * متن می‌چسباند؛ جوابِ کامپیوتر هم به nonceِ همان درخواست بسته است، پس
 * جوابِ کهنه به درخواستِ تازه نمی‌نشیند.
 */
@OptIn(ExperimentalEncodingApi::class)
object LanCrypto {

    const val KEY_BYTES = 32
    const val NONCE_BYTES = 12
    const val TAG_BYTES = 16

    // ---------------------------------------------------------------- HKDF

    /**
     * HKDF-SHA256 (RFC 5869): استخراج با [salt] (خالی یعنی ۳۲ صفر، همان
     * قاعدهٔ سند)، بعد گسترش با [info] تا [length] بایت.
     */
    fun hkdf(ikm: ByteArray, salt: ByteArray, info: ByteArray, length: Int): ByteArray {
        require(length in 1..255 * 32) { "طولِ HKDF بیرون از بازه" }
        val prk = LanPrims.hmacSha256(if (salt.isEmpty()) ByteArray(32) else salt, ikm)
        val out = ByteArray(length)
        var t = ByteArray(0)
        var pos = 0
        var counter = 1
        while (pos < length) {
            t = LanPrims.hmacSha256(prk, t + info + byteArrayOf(counter.toByte()))
            val n = minOf(t.size, length - pos)
            t.copyInto(out, pos, 0, n)
            pos += n
            counter++
        }
        return out
    }

    // ---------------------------------------------------------------- پاکت

    /** متنِ رمز با nonceِ خودش. */
    class Sealed(val nonce: ByteArray, val box: ByteArray)

    /** پاکت با nonceِ تازه و تصادفی — هیچ nonceی دو بار با یک کلید نمی‌آید. */
    fun seal(key: ByteArray, plain: ByteArray, aad: String): Sealed {
        val nonce = LanPrims.random(NONCE_BYTES)
        return Sealed(nonce, LanPrims.gcmSeal(key, nonce, plain, aad.encodeToByteArray()))
    }

    fun open(key: ByteArray, nonce: ByteArray, box: ByteArray, aad: String): ByteArray? =
        LanPrims.gcmOpen(key, nonce, box, aad.encodeToByteArray())

    /** کلیدهای رفت و برگشت از کلیدِ دستگاه. */
    class Directional(val request: ByteArray, val response: ByteArray) {
        override fun toString(): String = "Directional(…)"
    }

    fun directional(deviceKey: ByteArray): Directional {
        require(deviceKey.size == KEY_BYTES) { "کلیدِ دستگاه باید ۳۲ بایت باشد" }
        return Directional(
            hkdf(deviceKey, ByteArray(0), "ky2/sync/request".encodeToByteArray(), KEY_BYTES),
            hkdf(deviceKey, ByteArray(0), "ky2/sync/response".encodeToByteArray(), KEY_BYTES),
        )
    }

    /**
     * کلیدِ جفت شدن. [shared] رازِ ECDH است و [secret] رازِ QR یا رمزِ دستی؛
     * هر دو در ورودی‌اند، پس هیچ‌کدام به‌تنهایی کلید را نمی‌سازد. [transcript]
     * هر آنچه دو سو دربارهٔ این جفت شدن گفته‌اند را در کلید می‌نشاند
     * ([HubProtocol.pairTranscript])؛ اگر کسی وسطِ راه کلیدِ عمومی‌ای را عوض
     * کند، دو سو کلیدِ متفاوت می‌سازند و هیچ پاکتی باز نمی‌شود.
     */
    fun pairKey(shared: ByteArray, secret: ByteArray, transcript: String): ByteArray {
        require(shared.size == 32) { "رازِ ECDH باید ۳۲ بایت باشد" }
        require(secret.isNotEmpty()) { "رازِ جفت شدن خالی است" }
        val ikm = shared + byteArrayOf((secret.size ushr 8).toByte(), secret.size.toByte()) + secret
        return hkdf(ikm, "khayatyar/pair/v2".encodeToByteArray(), transcript.encodeToByteArray(), KEY_BYTES)
    }

    /**
     * دورهای PBKDF2 برای رمزِ دستی ([codeSecret]): ۲^۱۷.
     *
     * **چرا لازم شد.** ECDH جلوی شنود را می‌گیرد، نه جلوی میانجیِ فعال. کسی که
     * روی همان وای‌فای خودش را جای کامپیوتر جا بزند (ARP)، به گوشی کلیدِ عمومیِ
     * خودش را می‌دهد و یک پاکتِ رمزشده با کلیدِ «ECDH + رمز» می‌گیرد؛ چون رازِ
     * ECDHِ خودش را دارد، تنها مجهول همان ۱۲ رقم است و بی‌اتصال حدس زده می‌شود.
     * ۱۰^۱۲ حدس با HKDFِ خام روی یک کارتِ گرافیکِ امروزی چند دقیقه است — درونِ
     * همان پنجرهٔ ده‌دقیقه‌ای؛ بعد میانجی با همان رمز به‌جای گوشی جفت می‌شود و
     * کلیدِ دستگاه را می‌گیرد. با ۲^۱۷ دورِ PBKDF2 هر حدس صد و سی هزار بار
     * گران‌تر است: همان کار برای یک کارت چند ماه می‌شود. گوشی و کامپیوتر فقط
     * یک بار در هر جفت شدن آن را می‌پردازند (کسری از ثانیه).
     */
    const val MANUAL_CODE_ROUNDS = 131_072

    /**
     * رمزِ دستیِ ۱۲ رقمی، کُند شده و به همین پنجره بسته — آنچه به‌جای خودِ رمز در
     * [pairKey] می‌نشیند. نمک [pairingId] است تا هیچ جدولِ ازپیش‌ساخته‌ای برای
     * همهٔ رمزها به کار نیاید.
     */
    fun codeSecret(code: ByteArray, pairingId: String): ByteArray =
        LanPrims.pbkdf2Sha256(code, "ky2/manual-code|$pairingId".encodeToByteArray(), MANUAL_CODE_ROUNDS, KEY_BYTES)

    // ---------------------------------------------------------------- ابزار

    /**
     * برابریِ دو رشته بایت **با زمانِ ثابت** — زمانِ پاسخ نمی‌گوید چند بایتِ
     * اول درست بود. طول راز نیست و زود سنجیده می‌شود.
     */
    fun sameBytes(a: ByteArray, b: ByteArray): Boolean {
        if (a.size != b.size) return false
        var d = 0
        for (i in a.indices) d = d or (a[i].toInt() xor b[i].toInt())
        return d == 0
    }

    fun b64(bytes: ByteArray): String = Base64.encode(bytes)

    /** `null` اگر Base64ِ درست نیست — ورودیِ شبکه هرگز استثنای بی‌پاسخ نمی‌دهد. */
    fun unb64(text: String?): ByteArray? =
        text?.let { runCatching { Base64.decode(it) }.getOrNull() }

    private const val B32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"

    /** Base32ِ RFC 4648، بی `=` — برای QR (فقط حروفِ بزرگ و رقم، QR کوچک‌تر). */
    fun b32(bytes: ByteArray): String {
        val sb = StringBuilder()
        var buffer = 0
        var bits = 0
        for (b in bytes) {
            buffer = (buffer shl 8) or (b.toInt() and 0xFF)
            bits += 8
            while (bits >= 5) {
                sb.append(B32[(buffer shr (bits - 5)) and 31])
                bits -= 5
            }
        }
        if (bits > 0) sb.append(B32[(buffer shl (5 - bits)) and 31])
        return sb.toString()
    }

    /** وارونهٔ [b32]؛ `null` برای هر حرفِ بیرون از الفبا یا بیتِ اضافهٔ غیرِصفر. */
    fun unb32(text: String): ByteArray? {
        val out = ArrayList<Byte>()
        var buffer = 0
        var bits = 0
        for (c in text) {
            val v = B32.indexOf(c.uppercaseChar())
            if (v < 0) return null
            buffer = (buffer shl 5) or v
            bits += 5
            if (bits >= 8) {
                out += ((buffer shr (bits - 8)) and 0xFF).toByte()
                bits -= 8
            }
            buffer = buffer and 0xFF
        }
        // باقی‌ماندهٔ کمتر از یک بایت باید صفر باشد؛ وگرنه رشته از جای دیگری آمده.
        if (bits >= 5 || (buffer and ((1 shl bits) - 1)) != 0) return null
        return out.toByteArray()
    }

    /** شناسهٔ تصادفیِ [bytes] بایتی به Base32 — برای pairingId و requestId. */
    fun randomId(bytes: Int): String = b32(LanPrims.random(bytes))
}
