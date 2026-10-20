package com.afghanjama.lan

import com.afghanjama.ui.format.fa
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
 * **چیزی که این نمی‌بندد:** کسی که روی همان شبکه چند نشانی بگیرد، از هر
 * نشانی همان ده حدس را دارد. بستنِ کامل با جفت کردنِ دستگاه‌ها می‌آید
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
