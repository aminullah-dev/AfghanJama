package com.afghanjama.lan

import com.afghanjama.ui.format.fa
import java.net.InetAddress
import java.security.MessageDigest

/**
 * نگهبانِ رمزِ اتصال: سقفِ رمزِ نادرست برای هر نشانی، و مقایسهٔ رمز با
 * زمانِ ثابت.
 *
 * **چرا لازم شد.** رمزِ اتصال شش رقم است (`LanPrefs.newCode`) و تا ۱.۹.۱
 * هیچ سقفی برای حدس زدن نداشت: هر دستگاهی روی همان وای‌فای می‌توانست
 * پشتِ سرِ هم رمز بفرستد تا یکی درست درآید، و بعد کارمزدِ هر خیاطی را
 * بخوانَد یا به نامِ او حضور بزند. حالا که گوشی‌ها واقعاً وصل می‌شوند،
 * رمز روی هر درخواست روی وای‌فای می‌رود، و دستگاهِ اصلی (مثلاً لپ‌تاپ)
 * ممکن است روی شبکهٔ دیگری هم روشن باشد.
 *
 * **قاعده:** هر نشانی در هر شصت ثانیه [MAX_FAILURES] رمزِ نادرست دارد.
 * دهمی که برسد، آن نشانی [BLOCK_MS] (پنج دقیقه) بسته می‌شود — حتی با رمزِ
 * درست، وگرنه بستن خودش می‌گفت کدام حدس درست بوده. گوشیِ کارگری که رمزِ
 * کهنه دارد و تابلوی دیواری (هر ۱۵ ثانیه، یعنی ۴ بار در دقیقه) به این
 * سقف نمی‌رسند. رمزِ درست شمارِ نادرست‌ها را پاک می‌کند، و «رمز تازه»
 * ([reset]) همه را: حدس‌های رمزِ قبلی دیگر به چیزی نزدیک نمی‌کنند.
 *
 * **چیزی که این نمی‌بندد:** کسی که روی همان شبکه چند نشانیِ IPv4 بگیرد، از
 * هر نشانی همان ده حدس را دارد. (IPv6، که نشانی‌اش بی‌شمار است، اصلاً
 * پذیرفته نمی‌شود: [peerKey].) بستنِ کامل با جفت کردنِ دستگاه‌ها می‌آید
 * (فازِ B)؛ این فقط حدس زدن را از چند دقیقه به روزها می‌کشاند.
 *
 * بی `org.json` و بی اندروید است تا آزمونِ JVMِ `:app` بسنجدش.
 */
class LanGuard(
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val maxFailures: Int = MAX_FAILURES,
    private val windowMs: Long = WINDOW_MS,
    private val blockMs: Long = BLOCK_MS,
    private val maxPeers: Int = MAX_PEERS,
) {

    private class Peer {
        val failures = ArrayDeque<Long>()
        var blockedUntil = 0L
        var touched = 0L
    }

    private val peers = HashMap<String, Peer>()

    /** حکمِ یک درخواست. */
    sealed interface Verdict {
        /** رمز درست است و نشانی باز. */
        data object Pass : Verdict

        /** رمز نادرست است؛ شمرده شد. */
        data object Wrong : Verdict

        /** نشانی بسته است، رمز هر چه باشد. */
        data class Blocked(val waitMs: Long) : Verdict
    }

    /**
     * حکمِ یک درخواست از [peer] — «بسته است؟» و «یک نادرستِ دیگر» **در یک
     * قفل**.
     *
     * سرور هر اتصال را روی رشتهٔ خودش می‌سنجد. تا وقتی این دو قدم جدا
     * بودند ([blockedFor] و بعد [failed])، چهل حدسِ هم‌زمان از یک نشانی
     * همه از قدمِ اول می‌گذشتند پیش از آنکه دهمی ثبت شود؛ `lanLoopSmoke`
     * یازده جوابِ «رمز نادرست» دید، نه ده. حالا هر حدس پس از حدسِ قبلی
     * داوری می‌شود و سقف دقیقاً ده است.
     *
     * [codeOk] پیش از این صدا با [sameCode] حساب می‌شود، تا زمانِ مقایسه
     * به بسته بودن یا نبودن ربطی نداشته باشد.
     */
    @Synchronized
    fun admit(peer: String, codeOk: Boolean): Verdict {
        val wait = blockedFor(peer)
        if (wait > 0) return Verdict.Blocked(wait)
        if (codeOk) {
            succeeded(peer)
            return Verdict.Pass
        }
        failed(peer)
        return Verdict.Wrong
    }

    /** چند میلی‌ثانیهٔ دیگر این نشانی بسته است؛ صفر یعنی باز. */
    @Synchronized
    fun blockedFor(peer: String): Long {
        val p = peers[peer] ?: return 0
        val left = p.blockedUntil - clock()
        return if (left > 0) left else 0
    }

    /** یک رمزِ نادرست از [peer]. `true` اگر همین یکی آن نشانی را بست. */
    @Synchronized
    fun failed(peer: String): Boolean {
        val now = clock()
        val p = peers[peer] ?: Peer().also { prune(now); peers[peer] = it }
        p.touched = now
        while (p.failures.isNotEmpty() && now - p.failures.first() >= windowMs) p.failures.removeFirst()
        p.failures.addLast(now)
        if (p.failures.size >= maxFailures) {
            p.failures.clear()
            p.blockedUntil = now + blockMs
            return true
        }
        return false
    }

    /** رمزِ درست از [peer]: نادرست‌های پیشینش فراموش می‌شوند. */
    @Synchronized
    fun succeeded(peer: String) {
        val p = peers[peer] ?: return
        if (p.blockedUntil <= clock()) peers.remove(peer)
    }

    /** رمزِ تازه: هر چه با رمزِ قبلی حدس زده شده بود دیگر معنایی ندارد. */
    @Synchronized
    fun reset() = peers.clear()

    /**
     * فهرست بی‌اندازه بزرگ نمی‌شود. اول هر چه نه بسته است و نه نادرستِ
     * تازه دارد برداشته می‌شود؛ اگر باز پر بود، کهنه‌ترینِ نابسته — نشانیِ
     * بسته آخرین چیزی است که فراموش می‌شود.
     */
    private fun prune(now: Long) {
        if (peers.size < maxPeers) return
        peers.entries.removeAll { (_, p) ->
            p.blockedUntil <= now && (p.failures.isEmpty() || now - p.failures.last() >= windowMs)
        }
        if (peers.size < maxPeers) return
        val victim = peers.entries.filter { it.value.blockedUntil <= now }.minByOrNull { it.value.touched }
            ?: peers.entries.minByOrNull { it.value.touched }
        victim?.let { peers.remove(it.key) }
    }

    companion object {
        /** رمزِ نادرستِ مجاز برای هر نشانی در هر [WINDOW_MS]. */
        const val MAX_FAILURES = 10
        const val WINDOW_MS = 60_000L

        /** بستنِ نشانی پس از [MAX_FAILURES] رمزِ نادرست. */
        const val BLOCK_MS = 5 * 60_000L

        /** بیشترین نشانی‌ای که در حافظه دنبال می‌شود. */
        const val MAX_PEERS = 1024

        /**
         * کلیدِ نگهبان برای نشانیِ [address]؛ `null` یعنی «جواب نده».
         *
         * **فقط IPv4.** سرور روی همهٔ کارت‌ها گوش می‌دهد و جاوا با آن
         * همهٔ اتصال‌های IPv6 را هم می‌پذیرد (حتی وقتی صریحاً `0.0.0.0`
         * داده شود). روی IPv6 هر دستگاهی در شبکه می‌تواند برای هر ده حدس
         * نشانیِ تازه‌ای بگیرد (`fe80::…` بی‌شمار است)، پس سقفِ «هر نشانی»
         * آنجا هیچ است. گوشیِ کارگر هم هرگز از IPv6 نمی‌آید: QR و
         * [HostAddress.isPrivate] فقط نشانیِ IPv4 می‌شناسند.
         *
         * نشانیِ IPv4ِ پیچیده در IPv6 (`::ffff:192.168.1.5`) همان IPv4 است؛
         * جاوا و اندروید معمولاً خودشان بازش می‌کنند، این‌جا هم برای اطمینان.
         */
        fun peerKey(address: InetAddress?): String? {
            val b = address?.address ?: return null
            if (b.size == 4) return address.hostAddress
            val mapped = b.size == 16 && (0 until 10).all { b[it] == 0.toByte() } &&
                b[10] == 0xFF.toByte() && b[11] == 0xFF.toByte()
            if (!mapped) return null
            return (12 until 16).joinToString(".") { (b[it].toInt() and 0xFF).toString() }
        }

        /**
         * رمزی که آمده همان رمزِ این دستگاه است؟ — با زمانِ ثابت.
         *
         * `!=` در اولین حرفِ ناجور برمی‌گشت و زمانِ جواب می‌گفت چند رقمِ
         * اولِ رمز درست بوده. `MessageDigest.isEqual` روی JDK با هر طولی
         * زمانِ ثابت دارد، ولی روی اندرویدهای قدیم وقتی طول‌ها فرق کند زود
         * برمی‌گردد؛ پس اول هر دو SHA-256 می‌شوند تا همیشه ۳۲ بایت در برابرِ
         * ۳۲ بایت سنجیده شود. رمزِ خالی هرگز درست نیست.
         */
        fun sameCode(given: String, expected: String): Boolean {
            if (expected.isEmpty()) return false
            return MessageDigest.isEqual(sha256(given), sha256(expected))
        }

        private fun sha256(s: String): ByteArray =
            MessageDigest.getInstance("SHA-256").digest(s.toByteArray(Charsets.UTF_8))

        /** پیامِ گوشیِ کارگری که بسته شده — با دقیقه‌های مانده. */
        fun blockedMessage(waitMs: Long): String {
            val minutes = ((waitMs + 59_999) / 60_000).coerceAtLeast(1)
            return "رمزِ نادرست چند بار پشتِ سرِ هم از این گوشی آمد؛ دستگاهِ اصلی تا " +
                "${minutes.fa()} دقیقهٔ دیگر از آن چیزی نمی‌پذیرد. رمزِ درست را از صفحهٔ " +
                "«اشتراکِ کارگاه» روی دستگاهِ اصلی بخوانید و بعد دوباره امتحان کنید."
        }
    }
}
