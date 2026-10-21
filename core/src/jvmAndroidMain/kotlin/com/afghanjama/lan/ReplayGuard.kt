package com.afghanjama.lan

import kotlin.math.abs

/**
 * نگهبانِ تکرار برای درخواست‌های قراردادِ ۲ — روی کامپیوتر (یا گوشیِ اصلی).
 *
 * **چرا لازم شد.** پاکتِ رمز می‌گوید درخواست را چه کسی ساخته، نه اینکه
 * تازه است. کسی که روی وای‌فای یک درخواستِ «ورود» را ضبط کند، بی دانستنِ
 * کلید هم می‌تواند همان بایت‌ها را فردا دوباره بفرستد. دو قاعده آن را
 * می‌بندد:
 *
 * 1. **زمان:** درخواستی که سرخطِ زمانش بیش از [windowMs] (ده دقیقه) با
 *    ساعتِ کامپیوتر فاصله دارد رد می‌شود. زمان در دادهٔ همراهِ پاکت است،
 *    پس عوض کردنش پاکت را می‌شکند.
 * 2. **nonce:** هر nonce که یک بار با کلیدِ یک دستگاه پذیرفته شد، تا
 *    [keepMs] (بیست‌وپنج دقیقه) به یاد می‌ماند. بازهٔ زمان بیست دقیقه
 *    پهنا دارد (ده پیش و ده پس)؛ اگر nonce زودتر فراموش می‌شد، همان
 *    درخواست در دقیقه‌های آخرِ بازه دوباره پذیرفته می‌شد.
 *
 * **ترتیب مهم است:** زمان پیش از باز کردنِ پاکت سنجیده می‌شود (ارزان)، و
 * nonce **پس از** باز شدنش ثبت می‌شود ([admit])؛ وگرنه هر کسی با nonceهای
 * ساختگی حافظه را پر می‌کرد و nonceِ گوشیِ واقعی را «تکراری» می‌کرد.
 *
 * حافظهٔ هر دستگاه سقف دارد ([maxPerDevice]). پر شدنش یعنی یک گوشی در
 * بیست‌وپنج دقیقه بیش از چهار هزار درخواست فرستاده — گوشیِ سالم هر ۱۵
 * ثانیه یکی می‌فرستد — و آن‌وقت **رد** می‌شود، نه اینکه nonceهای کهنه
 * دور ریخته شوند و دری برای تکرار باز شود.
 *
 * nonceها روی دیسک نمی‌روند: پس از روشن‌شدنِ دوبارهٔ کامپیوتر، درخواستِ
 * ضبط‌شده‌ای که هنوز در بازهٔ ده دقیقه است ممکن است یک بار دیگر پذیرفته
 * شود. بی‌ضرر است: کارِ صف کلیدِ تکرارنشدنی دارد (دوباره ثبت نمی‌شود) و
 * جوابش با کلیدِ همان گوشی رمز شده است.
 */
class ReplayGuard(
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val windowMs: Long = HubProtocol.TIME_WINDOW_MS,
    private val keepMs: Long = HubProtocol.NONCE_KEEP_MS,
    private val maxPerDevice: Int = MAX_PER_DEVICE,
) {

    init {
        require(keepMs >= 2 * windowMs) { "nonce باید دست‌کم به پهنای بازهٔ زمان به یاد بماند" }
    }

    sealed interface Verdict {
        data object Ok : Verdict

        /** زمانِ درخواست بیرون از بازه است؛ [serverTime] برای تنظیمِ ساعتِ گوشی. */
        data class Stale(val serverTime: Long) : Verdict

        /** همین nonce پیش‌تر با همین دستگاه آمده. */
        data object Replay : Verdict

        /** این دستگاه بیش از حد درخواست فرستاده. */
        data object Full : Verdict
    }

    /** nonce → زمانِ فراموش شدن، به ترتیبِ رسیدن. */
    private val seen = HashMap<String, LinkedHashMap<String, Long>>()

    /** زمانِ درخواست در بازه است؟ پیش از باز کردنِ پاکت. */
    fun timeOk(time: Long): Verdict {
        val now = clock()
        return if (abs(time - now) <= windowMs) Verdict.Ok else Verdict.Stale(now)
    }

    /** پس از باز شدنِ پاکت: این nonce تازه است؟ اگر بله، ثبت می‌شود. */
    @Synchronized
    fun admit(deviceId: String, nonce: ByteArray, time: Long): Verdict {
        val now = clock()
        if (abs(time - now) > windowMs) return Verdict.Stale(now)
        val mine = seen.getOrPut(deviceId) { LinkedHashMap() }
        val it = mine.entries.iterator()
        while (it.hasNext()) {
            if (it.next().value <= now) it.remove() else break
        }
        val k = LanCrypto.b64(nonce)
        if (mine.containsKey(k)) return Verdict.Replay
        if (mine.size >= maxPerDevice) return Verdict.Full
        mine[k] = now + keepMs
        return Verdict.Ok
    }

    /** دستگاهی که دسترسی‌اش برداشته شد — حافظه‌اش هم. */
    @Synchronized
    fun forget(deviceId: String) {
        seen.remove(deviceId)
    }

    companion object {
        const val MAX_PER_DEVICE = 4096
    }
}
