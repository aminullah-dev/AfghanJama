package com.afghanjama.prefs

/**
 * آنچه لایسنس روی همین دستگاه به یاد می‌سپارد.
 *
 * در همان تنظیماتی می‌نشیند که بقیهٔ اپ — روی اندروید
 * `SharedPreferences`، روی ویندوز و مک فایلِ properties کنارِ دفتر، روی
 * آیفون `NSUserDefaults` — پس با به‌روزرسانیِ اپ از بین نمی‌رود.
 *
 * شروعِ دورهٔ آزمایشی **علاوه بر اینجا** در خودِ دفتر هم نوشته می‌شود
 * (`Repo.licenceTrialAnchor`)، تا نصبِ دوباره با بازیابیِ پشتیبان دوره
 * را از صفر شروع نکند.
 */
object LicencePrefs {
    private const val FILE = "licence"
    private const val KEY_KEY = "key"
    private const val KEY_FIRST_RUN = "first_run"
    private const val KEY_LAST_SEEN = "last_seen"
    private const val KEY_MISMATCH_SINCE = "mismatch_since"
    private const val KEY_MISMATCH_FOR = "mismatch_for"
    private const val KEY_FALLBACK_ID = "fallback_id"
    private const val KEY_MAIN_STATE = "main_state"
    private const val KEY_MAIN_WRITABLE = "main_writable"
    private const val KEY_MAIN_SEEN = "main_seen"

    /** متنِ کاملِ کلیدِ نصب‌شده (`LNM1.…`)؛ خالی یعنی هیچ. */
    fun key(s: Settings): String = s.getString(FILE, KEY_KEY)
    fun setKey(s: Settings, key: String) = s.putString(FILE, KEY_KEY, key)

    /** اولین اجرای نسخهٔ لایسنس‌دار؛ صفر یعنی هنوز نه. */
    fun firstRun(s: Settings): Long = s.getLong(FILE, KEY_FIRST_RUN, 0L)
    fun setFirstRun(s: Settings, at: Long) = s.putLong(FILE, KEY_FIRST_RUN, at)

    /** دیرترین زمانی که اپ دیده. */
    fun lastSeen(s: Settings): Long = s.getLong(FILE, KEY_LAST_SEEN, 0L)
    fun setLastSeen(s: Settings, at: Long) = s.putLong(FILE, KEY_LAST_SEEN, at)

    /**
     * از کِی لایسنسِ [licenceId] روی این دستگاه با کدِ دیگری دیده شده.
     *
     * به شناسهٔ لایسنس بسته است: کلیدِ تازه‌ای که برای همین دستگاه صادر
     * شود، مهلتِ کلیدِ قبلی را با خودش نمی‌کشد.
     */
    fun mismatchSince(s: Settings, licenceId: String): Long? =
        if (s.getString(FILE, KEY_MISMATCH_FOR) == licenceId)
            s.getLong(FILE, KEY_MISMATCH_SINCE, 0L).takeIf { it > 0 }
        else null

    fun setMismatchSince(s: Settings, licenceId: String, at: Long) {
        s.putString(FILE, KEY_MISMATCH_FOR, licenceId)
        s.putLong(FILE, KEY_MISMATCH_SINCE, at)
    }

    fun clearMismatch(s: Settings) {
        s.remove(FILE, KEY_MISMATCH_FOR)
        s.remove(FILE, KEY_MISMATCH_SINCE)
    }

    /** شناسهٔ تصادفیِ یک‌باره، وقتی شناسهٔ سکو خوانده نمی‌شود. */
    fun fallbackId(s: Settings): String = s.getString(FILE, KEY_FALLBACK_ID)
    fun setFallbackId(s: Settings, id: String) = s.putString(FILE, KEY_FALLBACK_ID, id)

    // ---- روی دستگاهِ کارگر: آخرین گزارشِ دستگاهِ اصلی ----

    fun mainState(s: Settings): String = s.getString(FILE, KEY_MAIN_STATE)
    fun mainWritable(s: Settings): Boolean = s.getBoolean(FILE, KEY_MAIN_WRITABLE, true)
    fun mainSeen(s: Settings): Long = s.getLong(FILE, KEY_MAIN_SEEN, 0L)

    fun setMain(s: Settings, state: String, writable: Boolean, at: Long) {
        s.putString(FILE, KEY_MAIN_STATE, state)
        s.putBoolean(FILE, KEY_MAIN_WRITABLE, writable)
        s.putLong(FILE, KEY_MAIN_SEEN, at)
    }
}
