package com.afghanjama.lan

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * کدام گوشی‌ها به این دستگاهِ اصلی رسیده‌اند — فقط برای اینکه کارفرما
 * ببیند، نه برای تصمیمِ امنیتی.
 *
 * **چرا لازم شد.** کارفرما روی مک صفحهٔ اشتراک را باز می‌کرد و هیچ نشانه‌ای
 * نمی‌دید که گوشیِ کارگر اصلاً به کامپیوتر رسیده یا نه. «وصل نشد»ِ گوشی
 * می‌توانست دیوارِ آتش باشد، شبکهٔ دیگر، یا رمزِ غلط — و روی کامپیوتر
 * هیچ‌کدام دیده نمی‌شد.
 *
 * **«وصل» در قراردادِ ۱ چه معنایی دارد.** گوشیِ کارگر اتصالِ ماندگار ندارد؛
 * هر بار که دکمه‌ای می‌زند یک درخواست می‌فرستد و تمام. پس اینجا «وصل» یعنی
 * «درخواستی از این نشانی پذیرفته شد»: یک `/ping` (همان کاری که اسکنِ QR
 * می‌کند)، یا هر درخواستی با رمزِ درست. رمزِ نادرست و نشانیِ بسته شمرده
 * نمی‌شوند. نشانی جای گوشی را می‌گیرد، چون قراردادِ ۱ گوشی را جز با نشانی
 * نمی‌شناسد؛ گوشی‌ای که در طولِ روز نشانیِ تازه از مودم بگیرد، دو بار
 * شمرده می‌شود.
 *
 * چیزی روی دیسک نمی‌رود و قرارداد عوض نمی‌شود: فقط سرور یادداشت می‌کند.
 */
class LanPeers(
    private val clock: () -> Long = { System.currentTimeMillis() },
    /** بیش از این نشانی نگه داده نمی‌شود؛ کهنه‌ترین اول می‌رود. */
    private val cap: Int = MAX_PEERS,
) {

    /** آنچه صفحه می‌خوانَد. */
    data class Seen(
        /** نشانی → آخرین باری که درخواستی از آن پذیرفته شد. */
        val lastOk: Map<String, Long> = emptyMap(),
    ) {
        /** چند نشانی از [since] به بعد رسیده‌اند. */
        fun countSince(since: Long): Int = lastOk.values.count { it >= since }

        /** آخرین درخواستِ پذیرفته از هر گوشی؛ `null` اگر هنوز هیچ. */
        val lastAt: Long? get() = lastOk.values.maxOrNull()
    }

    private val _seen = MutableStateFlow(Seen())
    val seen: StateFlow<Seen> = _seen.asStateFlow()

    /** درخواستی از [peer] پذیرفته شد. */
    fun accepted(peer: String) {
        val now = clock()
        synchronized(this) {
            val next = LinkedHashMap(_seen.value.lastOk)
            next.remove(peer)
            next[peer] = now
            // ترتیبِ درج همان ترتیبِ تازگی است؛ اولی کهنه‌ترین است.
            while (next.size > cap) next.remove(next.keys.first())
            _seen.value = Seen(next)
        }
    }

    companion object {
        /** شبکهٔ یک کارگاه چند ده دستگاه دارد، نه صدها. */
        const val MAX_PEERS = 256
    }
}
