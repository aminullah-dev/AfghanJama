package com.afghanjama.prefs

/** ظاهرِ برنامه: پیروِ سیستم، همیشه روشن، یا همیشه تاریک. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/**
 * انتخابِ ظاهر (روشن/تاریک)، ماندگار روی همین دستگاه.
 *
 * پیش‌فرض [ThemeMode.LIGHT] است، نه SYSTEM: تا امروز برنامه همیشه روشن بود
 * و نباید سرِ یک به‌روزرسانی ناگهان تاریک شود چون سیستمِ کاربر تاریک است.
 * کاربر خودش تاریک یا «پیروِ سیستم» را انتخاب می‌کند.
 */
object ThemePrefs {
    private const val FILE = "theme_prefs"
    private const val KEY_MODE = "mode"

    fun mode(s: Settings): ThemeMode =
        runCatching { ThemeMode.valueOf(s.getString(FILE, KEY_MODE)) }
            .getOrDefault(ThemeMode.LIGHT)

    fun setMode(s: Settings, m: ThemeMode) = s.putString(FILE, KEY_MODE, m.name)
}
