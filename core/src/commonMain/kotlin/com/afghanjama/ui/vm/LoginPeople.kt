package com.afghanjama.ui.vm

import com.afghanjama.prefs.Settings

/**
 * یک کاشی در صفحهٔ ورود — یک آدمِ کارگاه با نام و نقشش.
 *
 * **چرا کاشی و نه فقط رمز.** تا ۱.۱۱ صفحهٔ ورود یک خانهٔ رمز بود و رمز
 * خودش می‌گفت کیست. برای کارگاهی که چند نفر یک کامپیوتر یا یک گوشی را
 * باز می‌کنند این دو گرفتاری داشت: کسی نمی‌دید روی این دستگاه چه کسانی
 * حساب دارند، و نقش سرِ اولین ورود انتخاب و قفل می‌شد. حالا هر نفر کاشیِ
 * خودش را دارد؛ نامش را می‌زند و رمزِ خودش را.
 *
 * [id] برای صاحبِ دستگاه [OWNER_ID] است و برای بقیه شناسهٔ [AppUser].
 */
data class LoginPerson(
    val id: String,
    val name: String,
    /** برچسبِ قرص: مدیر، خیاط، فروشنده… — مالِ خودِ این نفر. */
    val role: String,
    val isManager: Boolean,
) {
    companion object {
        /** کاشیِ صاحبِ دستگاه — کسی که رمزِ اول را گذاشته. */
        const val OWNER_ID = "owner"
    }
}

/**
 * نامِ نقشِ یک **آدم** — برای قرصِ کاشیِ ورود و پایینِ نوارِ کامپیوتر.
 *
 * [label] نامِ **بخش** است («مدیریتِ دوخت»)؛ این نامِ کسی است که پشتِ
 * دستگاه نشسته («خیاط»). مدیر «مدیر» است، نه «مدیریت».
 */
val UserRole.personLabel: String
    get() = when (this) {
        UserRole.MANAGER -> "مدیر"
        UserRole.PURCHASE -> "خرید و انبار"
        UserRole.SEWING -> "کارگر"
        UserRole.REVIEW -> "ناظر"
        UserRole.SALES -> "فروشنده"
    }

/**
 * نقشِ کسی که مدیر در «کاربران» ساخته — از تیک‌هایش، نه انتخابی جدا.
 *
 * اگر تیک‌ها دقیقاً یکی از پیش‌تنظیم‌ها باشند، همان نام («خیاط»،
 * «فروشنده»). وگرنه نزدیک‌ترین نامِ کلی: کسی که پول و دفتر را می‌بیند
 * «حسابدار»، کسی که می‌فروشد «فروشنده»، کسی که در خطِ تولید است «کارگر».
 * نقش را فقط مدیر عوض می‌کند، چون تیک‌ها را فقط مدیر می‌زند.
 */
val AppUser.roleLabel: String
    get() {
        Feature.PRESETS.firstOrNull { it.second == features }?.let { return it.first }
        val work = setOf(Feature.ORDERS, Feature.CUTTING, Feature.SEWING, Feature.REVIEW, Feature.DELIVERY)
        return when {
            Feature.FINANCE in features || Feature.LEDGER in features || Feature.PAYROLL in features -> "حسابدار"
            Feature.SALES in features || Feature.CUSTOMERS in features -> "فروشنده"
            features.any { it in work } -> "کارگر"
            Feature.WAREHOUSE in features || Feature.PROCUREMENT in features -> "خرید و انبار"
            else -> "کارمند"
        }
    }

/**
 * سقفِ رمزِ اشتباه — پس از چند بار، کاشیِ همان نفر چند لحظه بسته می‌شود.
 *
 * **چرا لازم شد.** رمز چهار رقم است: ده هزار حالت. بی سقف، کسی که پشتِ
 * کامپیوترِ دفتر می‌نشیند می‌تواند رمزِ مدیر را با حوصله پیدا کند. حالا
 * پنج بار اشتباه آزاد است (دستِ لرزان، رمزِ تازه)، بعد ۳۰ ثانیه، و هر
 * بارِ دیگر دو برابر تا ۱۵ دقیقه. ورودِ درست شمار را صفر می‌کند.
 *
 * **روی دیسک، نه در حافظه.** اگر شمار در حافظه بود، بستن و باز کردنِ
 * برنامه قفل را باز می‌کرد. هر نفر شمارِ خودش را دارد، پس قفل شدنِ یک
 * کاشی دیگران را بیرون نمی‌گذارد.
 *
 * **ساعتِ عقب‌کشیده قفل را باز نمی‌کند.** اگر «تا» بیش از سقف دور باشد
 * (ساعتِ دستگاه عقب رفته)، باز هم بسته می‌ماند — تا سقف، از همین حالا.
 */
object PinLockout {
    /** چند اشتباهِ پشتِ‌سرِ‌هم آزاد است. */
    const val FREE_TRIES = 5

    /** اولین قفل. */
    const val FIRST_LOCK_MS = 30_000L

    /** بلندترین قفل. */
    const val MAX_LOCK_MS = 15 * 60_000L

    private const val FILE = "auth_prefs"
    private fun key(id: String) = "fails_$id"

    data class State(val fails: Int, val until: Long)

    fun state(settings: Settings, id: String): State {
        val raw = settings.getString(FILE, key(id))
        val parts = raw.split('|')
        val fails = parts.getOrNull(0)?.toIntOrNull() ?: 0
        val until = parts.getOrNull(1)?.toLongOrNull() ?: 0L
        return State(fails.coerceAtLeast(0), until.coerceAtLeast(0L))
    }

    /** تا کِی بسته است؛ صفر یعنی باز. */
    fun lockedUntil(settings: Settings, id: String, now: Long): Long {
        val s = state(settings, id)
        if (s.until <= now) return 0L
        // ساعتِ دستگاه عقب رفته: بسته بماند، ولی نه بیش از سقف از همین حالا.
        if (s.until - now > MAX_LOCK_MS) {
            val clamped = now + MAX_LOCK_MS
            settings.putString(FILE, key(id), "${s.fails}|$clamped")
            return clamped
        }
        return s.until
    }

    /** یک اشتباهِ دیگر؛ برمی‌گرداند تا کِی بسته شد (صفر یعنی هنوز آزاد). */
    fun fail(settings: Settings, id: String, now: Long): Long {
        val fails = state(settings, id).fails + 1
        val until = if (fails < FREE_TRIES) 0L else now + lockFor(fails)
        settings.putString(FILE, key(id), "$fails|$until")
        return until
    }

    /** ورودِ درست: شمار از نو. */
    fun clear(settings: Settings, id: String) = settings.remove(FILE, key(id))

    /** مدتِ قفل پس از [fails] اشتباه: ۳۰ ثانیه، بعد هر بار دو برابر تا سقف. */
    fun lockFor(fails: Int): Long {
        if (fails < FREE_TRIES) return 0L
        var ms = FIRST_LOCK_MS
        repeat(fails - FREE_TRIES) {
            ms *= 2
            if (ms >= MAX_LOCK_MS) return MAX_LOCK_MS
        }
        return ms.coerceAtMost(MAX_LOCK_MS)
    }
}
