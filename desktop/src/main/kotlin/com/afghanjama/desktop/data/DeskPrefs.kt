package com.afghanjama.desktop.data

import com.afghanjama.prefs.Settings
import com.afghanjama.ui.nav.DeskGroup

/**
 * چیزهایی که کامپیوتر از یک بار باز شدن تا بارِ بعد به یاد می‌سپارد:
 * اندازه و جای پنجره، گروه‌های جمع‌شدهٔ نوارِ کنار، و قفلِ خودکار.
 *
 * کنارِ بقیهٔ تنظیمات (`FileSettings`، در پوشهٔ دفتر) و در فایلِ جدای
 * `desk.properties` — هیچ‌کدام مالِ گوشی نیست.
 */
object DeskPrefs {
    private const val FILE = "desk"

    /** اندازه و جای پنجره، به واحدِ نقطه (dp) — همان واحدِ `WindowState`. */
    data class Bounds(
        val width: Int,
        val height: Int,
        /** `null` یعنی هنوز جایی ذخیره نشده؛ سیستم وسطِ صفحه می‌گذارد. */
        val x: Int?,
        val y: Int?,
        val maximized: Boolean,
    )

    fun bounds(s: Settings): Bounds? {
        val w = s.getLong(FILE, "w", 0L).toInt()
        val h = s.getLong(FILE, "h", 0L).toInt()
        if (w <= 0 || h <= 0) return null
        val hasPos = s.getBoolean(FILE, "has_pos", false)
        return Bounds(
            width = w,
            height = h,
            x = if (hasPos) s.getLong(FILE, "x", 0L).toInt() else null,
            y = if (hasPos) s.getLong(FILE, "y", 0L).toInt() else null,
            maximized = s.getBoolean(FILE, "max", false),
        )
    }

    fun saveBounds(s: Settings, b: Bounds) {
        s.putLong(FILE, "w", b.width.toLong())
        s.putLong(FILE, "h", b.height.toLong())
        s.putBoolean(FILE, "has_pos", b.x != null && b.y != null)
        if (b.x != null && b.y != null) {
            s.putLong(FILE, "x", b.x.toLong())
            s.putLong(FILE, "y", b.y.toLong())
        }
        s.putBoolean(FILE, "max", b.maximized)
    }

    /** گروه‌هایی که کاربر جمع کرده. پیش‌فرض: هیچ — همه باز، تا چیزی پنهان نماند. */
    fun collapsed(s: Settings): Set<DeskGroup> =
        s.getString(FILE, "collapsed", "")
            .split(',')
            .mapNotNull { name -> DeskGroup.entries.firstOrNull { it.name == name.trim() } }
            .toSet()

    fun setCollapsed(s: Settings, groups: Set<DeskGroup>) =
        s.putString(FILE, "collapsed", groups.joinToString(",") { it.name })

    /**
     * قفلِ خودکار پس از چند دقیقه بی‌کاری. صفر یعنی خاموش — پیش‌فرض.
     *
     * کامپیوترِ دفتر گاهی بی‌صاحب روی میز می‌ماند؛ ولی قفلی که خودبه‌خود
     * روشن باشد وسطِ کارِ کسی که فقط چند دقیقه رفته، صفحه را می‌بندد. پس
     * تصمیمش با مدیر است.
     */
    fun idleLockMinutes(s: Settings): Int =
        s.getLong(FILE, "idle_lock_min", 0L).toInt().coerceAtLeast(0)

    fun setIdleLockMinutes(s: Settings, minutes: Int) =
        s.putLong(FILE, "idle_lock_min", minutes.coerceAtLeast(0).toLong())

    /** گزینه‌هایی که «تنظیمات» نشان می‌دهد. */
    val IDLE_LOCK_CHOICES: List<Int> = listOf(0, 5, 10, 15, 30)
}
