package com.afghanjama.util

/**
 * پیدا کردنِ نام‌های موجود هنگامِ تایپ — تا یک نفر دو بار ثبت نشود.
 *
 * **چرا لازم شد.** نامِ هر شخص کلیدِ حسابِ اوست: «حاجی نصیر» و «حاجي
 * نصير» (با ی و ک عربیِ کیبوردِ دیگر) یا «حاجی‌نصیر» (با نیم‌فاصله) سه
 * طرفِ حسابِ جدا می‌شوند و بدهیِ یک نفر سه تکه. کاربر از روی چشم فرقشان
 * را نمی‌بیند. پس مقایسه روی شکلِ یکسان‌شده انجام می‌شود و از حرفِ اول
 * نام‌های نزدیک پیشنهاد می‌شوند.
 */
object NameMatch {

    private val SPACES = Regex("\\s+")

    /** شکلِ یکسان‌شده برای مقایسه — برای نمایش نیست. */
    fun normalize(s: String): String {
        val b = StringBuilder(s.length)
        for (c in s) {
            when (c) {
                'ي', 'ى', 'ئ' -> b.append('ی')
                'ك' -> b.append('ک')
                'ة' -> b.append('ه')
                'أ', 'إ', 'آ' -> b.append('ا')
                // نیم‌فاصله و فاصلهٔ بی‌شکست: «حاجی‌نصیر» همان «حاجی نصیر» است
                '‌', ' ' -> b.append(' ')
                // کشیده و اِعراب: در نام تفاوتی نمی‌سازند
                'ـ' -> {}
                in 'ً'..'ْ' -> {}
                else -> b.append(c.lowercaseChar())
            }
        }
        return b.toString().trim().replace(SPACES, " ")
    }

    /** همان نام، با هر شکلِ نوشتن؟ */
    fun same(a: String, b: String): Boolean = normalize(a) == normalize(b)

    /** نامِ موجودی که با [query] یکی است، اگر باشد. */
    fun exact(query: String, names: Collection<String>): String? {
        val q = normalize(query)
        if (q.isEmpty()) return null
        return names.firstOrNull { normalize(it) == q }
    }

    /**
     * نام‌های نزدیک به [query]، نزدیک‌ترین اول: عینِ همان، آغازِ نام،
     * آغازِ یکی از کلمه‌ها، و بعد هر جای نام. از همان حرفِ اول کار
     * می‌کند.
     */
    fun suggest(query: String, names: Collection<String>, limit: Int = 6): List<String> {
        val q = normalize(query)
        if (q.isEmpty()) return emptyList()
        return names
            .asSequence()
            .filter { it.isNotBlank() }
            .distinctBy { normalize(it) }
            .mapNotNull { n ->
                val k = normalize(n)
                val rank = when {
                    k == q -> 0
                    k.startsWith(q) -> 1
                    k.split(' ').any { w -> w.trimStart('[').startsWith(q) } -> 2
                    k.contains(q) -> 3
                    else -> return@mapNotNull null
                }
                rank to n
            }
            .sortedWith(compareBy<Pair<Int, String>>({ it.first }, { it.second.length }, { it.second }))
            .map { it.second }
            .take(limit)
            .toList()
    }
}
