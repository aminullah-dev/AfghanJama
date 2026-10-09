package com.afghanjama.licence

import com.afghanjama.util.sha256
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlinx.datetime.LocalDate

/**
 * پروتکلِ لایسنسِ لینومیک، نسخهٔ ۱ (LNM1).
 *
 * مرجع: `Linumic/licensing/PROTOCOL.md` — همان سندی که `MediFlow` و
 * ابزارِ صدورِ `linumic_license.py` هم از آن می‌خوانند. هر چیزی که اینجا
 * هست باید با آن سند و با آزمون‌های `test-vectors.json` بخوانَد؛ اگر
 * روزی از هم افتادند، سند درست است نه این فایل.
 *
 * شکلِ کلید:
 *
 *     LNM1.<payload>.<signature>
 *
 * - `payload`: base64url بی‌پَد از JSONِ UTF-8، فشرده و با کلیدهای مرتب.
 * - `signature`: base64url بی‌پَد از امضای **ECDSA P-256 / SHA-256** روی
 *   **بایت‌های همان JSON** (نه متنِ base64)، به شکلِ DER.
 * - هر فاصله و خطِ تازه‌ای در کلیدِ چسبانده‌شده نادیده گرفته می‌شود.
 *
 * همهٔ این فایل خالص است — نه ساعت می‌خوانَد، نه فایل، نه شبکه.
 */
object Lnm1 {

    const val PREFIX = "LNM1"
    const val VERSION = 1L

    /** محصولِ این اپ در پروتکل. کلیدِ «mediflow» اینجا پذیرفته نیست. */
    const val PRODUCT = "khayatyar"

    /** «هر دستگاهی» — کلیدی که به دستگاهِ خاصی بسته نیست. */
    const val ANY_MACHINE = "*"

    /** الفبای Crockford؛ بی I، L، O و U تا خواندنش پشتِ تلفن اشتباه نشود. */
    const val CROCKFORD = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"

    // ---------------------------------------------------------------
    // کدِ دستگاه
    // ---------------------------------------------------------------

    /**
     * کدِ دستگاه، مثلِ `K7Q2-9XMB-4T1D-WP3C`.
     *
     *     ۱۶ نویسهٔ اولِ Crockford-base32( SHA-256( "<product>|<platform id>" ) )
     *
     * بیت‌ها از پرارزش‌ترین برداشته می‌شوند، پنج‌تا پنج‌تا. ۱۶ نویسه یعنی
     * ۸۰ بیت از ۲۵۶ بیتِ چکیده — برای اینکه دو دستگاه یک کد نگیرند
     * بیش از کافی است.
     */
    fun machineCode(platformId: String, product: String = PRODUCT): String {
        val raw = crockford(sha256("$product|$platformId".encodeToByteArray()), 16)
        return raw.chunked(4).joinToString("-")
    }

    /** [n] نویسهٔ اولِ Crockford از [data]، بیت‌ها از پرارزش‌ترین. */
    internal fun crockford(data: ByteArray, n: Int): String {
        val sb = StringBuilder(n)
        var buffer = 0
        var bits = 0
        var idx = 0
        while (sb.length < n) {
            if (bits < 5) {
                val next = if (idx < data.size) data[idx++].toInt() and 0xFF else 0
                buffer = (buffer shl 8) or next
                bits += 8
            }
            val v = (buffer shr (bits - 5)) and 0x1F
            bits -= 5
            sb.append(CROCKFORD[v])
        }
        return sb.toString()
    }

    /**
     * کدِ دستگاهی که کاربر تایپ کرده، به شکلِ استاندارد — یا `null`.
     *
     * همان قاعدهٔ `norm_machine` در ابزارِ صدور: حروفِ کوچک بزرگ می‌شوند،
     * خط‌تیره و فاصله برداشته می‌شود، و O/I/L که پشتِ تلفن با ۰ و ۱
     * اشتباه می‌شوند همان ۰ و ۱ خوانده می‌شوند.
     */
    fun normalizeMachineCode(input: String): String? {
        val s = input.uppercase().filter { it.isLetterOrDigit() }
            .map { when (it) { 'O' -> '0'; 'I', 'L' -> '1'; else -> it } }
            .joinToString("")
        if (s.length != 16 || s.any { it !in CROCKFORD }) return null
        return s.chunked(4).joinToString("-")
    }

    // ---------------------------------------------------------------
    // کلید
    // ---------------------------------------------------------------

    /** چرا کلیدی پذیرفته نشد — رمزِ ماشینی، نه متن. متن در [LicenceText]. */
    enum class Problem {
        /** با `LNM1.` شروع نمی‌شود یا سه تکه نیست. */
        NOT_A_KEY,
        /** base64 یا JSON خراب است. */
        MALFORMED,
        /** امضا با کلیدِ عمومیِ این اپ نمی‌خوانَد (دست‌کاری‌شده یا جعلی). */
        BAD_SIGNATURE,
        /** `v` یک نیست. */
        WRONG_VERSION,
        /** کلیدِ محصولِ دیگری است (مثلاً MediFlow). */
        WRONG_PRODUCT,
    }

    sealed interface Result {
        data class Ok(val licence: Licence) : Result
        data class Err(val problem: Problem) : Result
    }

    /** فاصله‌ها و خط‌های تازه را برمی‌دارد — هر جای کلید که باشند. */
    fun compact(text: String): String = text.filterNot { it.isWhitespace() }

    /**
     * کلید را می‌خوانَد و امضایش را با [publicKeyDer] می‌سنجد.
     *
     * ترتیبِ رد کردن همان ترتیبِ سند است: پیشوند، base64، **امضا**، JSON،
     * نسخه، محصول. امضا پیش از خواندنِ JSON سنجیده می‌شود تا هیچ تصمیمی
     * روی متنی گرفته نشود که هنوز ثابت نشده از لینومیک آمده.
     */
    @OptIn(ExperimentalEncodingApi::class)
    fun verify(
        text: String,
        publicKeyDer: ByteArray,
        product: String = PRODUCT,
    ): Result {
        val parts = compact(text).split('.')
        if (parts.size != 3 || parts[0] != PREFIX) return Result.Err(Problem.NOT_A_KEY)
        val body = b64u(parts[1]) ?: return Result.Err(Problem.MALFORMED)
        val sig = b64u(parts[2]) ?: return Result.Err(Problem.MALFORMED)
        if (body.isEmpty() || sig.isEmpty()) return Result.Err(Problem.MALFORMED)

        if (!verifyEcdsaP256Sha256(publicKeyDer, body, sig)) {
            return Result.Err(Problem.BAD_SIGNATURE)
        }

        val json = runCatching { MiniJson.parse(body.decodeToString(throwOnInvalidSequence = true)) }
            .getOrNull() as? Map<*, *> ?: return Result.Err(Problem.MALFORMED)

        if ((json["v"] as? Long) != VERSION) return Result.Err(Problem.WRONG_VERSION)
        if (json["p"] != product) return Result.Err(Problem.WRONG_PRODUCT)

        val licence = runCatching { toLicence(json) }.getOrNull()
            ?: return Result.Err(Problem.MALFORMED)
        return Result.Ok(licence)
    }

    private fun toLicence(j: Map<*, *>): Licence {
        val machine = j["m"] as String
        return Licence(
            id = j["id"] as String,
            customer = j["c"] as String,
            machine = if (machine == ANY_MACHINE) machine else normalizeMachineCode(machine) ?: machine,
            issued = LocalDate.parse(j["i"] as String),
            expires = (j["e"] as String?)?.let { LocalDate.parse(it) },
            edition = (j["ed"] as? String).orEmpty(),
            features = (j["f"] as? List<*>)?.map { it as String } ?: emptyList(),
        )
    }

    /** base64url بی‌پَد (یا با پَد) — `null` اگر خراب باشد. */
    @OptIn(ExperimentalEncodingApi::class)
    internal fun b64u(text: String): ByteArray? = runCatching {
        Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT_OPTIONAL).decode(text)
    }.getOrNull()

    /**
     * بدنهٔ DERِ یک کلیدِ عمومیِ PEM (`-----BEGIN PUBLIC KEY-----`).
     *
     * همان SubjectPublicKeyInfo که جاوا با `X509EncodedKeySpec` و اپل با
     * کمی بریدن می‌پذیرند.
     */
    @OptIn(ExperimentalEncodingApi::class)
    fun pemToDer(pem: String): ByteArray {
        val body = pem.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("-----") }
            .joinToString("")
        return Base64.Default.decode(body)
    }
}

/**
 * لایسنسی که امضایش سنجیده شده.
 *
 * فقط از [Lnm1.verify] ساخته می‌شود؛ هر نمونه‌اش یعنی «لینومیک این را
 * صادر کرده» — ولی هنوز نه اینکه برای **این** دستگاه یا **امروز** معتبر
 * است. آن تصمیم با [LicencePolicy] است.
 */
data class Licence(
    /** شناسه، مثلِ `KY-2026-0001`. */
    val id: String,
    /** نامِ کارگاه، همان‌طور که روی صفحهٔ لایسنس نشان داده می‌شود. */
    val customer: String,
    /** کدِ دستگاه (`XXXX-XXXX-XXXX-XXXX`) یا `*`. */
    val machine: String,
    val issued: LocalDate,
    /** آخرین روزِ معتبر؛ `null` یعنی همیشگی. */
    val expires: LocalDate?,
    /** فقط برای نمایش. کد هرگز رویش تصمیم نمی‌گیرد. */
    val edition: String,
    val features: List<String>,
) {
    val anyMachine: Boolean get() = machine == Lnm1.ANY_MACHINE
}
