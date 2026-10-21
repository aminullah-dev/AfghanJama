package com.afghanjama.desktop.data

import com.afghanjama.prefs.Settings
import com.afghanjama.ui.nav.DeskGroup
import java.net.URLDecoder
import java.net.URLEncoder

/**
 * چیزهایی که کامپیوتر از یک بار باز شدن تا بارِ بعد به یاد می‌سپارد:
 * اندازه و جای پنجره، گروه‌های جمع‌شدهٔ نوارِ کنار، قفلِ خودکار، پوشهٔ
 * پشتیبانِ خودکار، و یادآورهایی که گفته شده‌اند.
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

    /**
     * پوشهٔ پشتیبانِ خودکار. خالی یعنی پیش‌فرض: `backups/` کنارِ دفتر.
     *
     * کارفرما می‌تواند پوشه‌ای در iCloud Drive یا OneDrive بدهد تا پشتیبان
     * از خودِ کامپیوتر هم بیرون برود.
     */
    fun backupFolder(s: Settings): String = s.getString(FILE, "backup_folder", "")

    fun setBackupFolder(s: Settings, path: String) = s.putString(FILE, "backup_folder", path)

    /**
     * یادآورهایی که گفته شده‌اند — کلیدِ `ReminderPlan.Due` ← زمانِ گفتن.
     *
     * **چرا روی دیسک.** تا ۱.۱۰ این فهرست فقط در حافظه بود؛ پس از هر بستن و
     * باز کردنِ برنامه (یا به‌روزرسانی، یا رفتنِ برق) یادآورِ پایانِ شیفت و
     * نان و چای دوباره گفته می‌شد.
     *
     * **عمرِ محدود ([REMINDER_SHOWN_TTL_MS]).** هیچ کلیدی پس از این مدت دوباره
     * وقتش نمی‌شود: شیفتِ کهنه‌تر از ۲۴ ساعت یادآوری نمی‌شود و کلیدِ وقتِ
     * استراحت روز را در خودش دارد. پس هر کلیدِ کهنه‌تر دور ریخته می‌شود و
     * فایل بزرگ نمی‌شود. ساعتی که عقب رفته هم کلید را نگه می‌دارد تا به
     * عمرش برسد (زمانِ «آینده» کهنه شمرده نمی‌شود).
     */
    fun shownReminders(s: Settings, now: Long): Map<String, Long> =
        s.getString(FILE, "reminders_shown", "")
            .lineSequence()
            .mapNotNull { line ->
                val tab = line.indexOf('\t')
                if (tab <= 0) return@mapNotNull null
                val at = line.substring(0, tab).toLongOrNull() ?: return@mapNotNull null
                val key = runCatching { URLDecoder.decode(line.substring(tab + 1), Charsets.UTF_8) }
                    .getOrNull() ?: return@mapNotNull null
                key to at
            }
            .filter { (_, at) -> now - at < REMINDER_SHOWN_TTL_MS }
            .toMap()

    /** [keys] همین حالا گفته شدند؛ کلیدهای کهنه همین‌جا دور ریخته می‌شوند. */
    fun markRemindersShown(s: Settings, keys: Collection<String>, now: Long) {
        val all = shownReminders(s, now) + keys.associateWith { now }
        val text = all.entries
            .sortedByDescending { it.value }
            .take(REMINDER_SHOWN_MAX)
            .joinToString("\n") { (key, at) -> "$at\t" + URLEncoder.encode(key, Charsets.UTF_8) }
        s.putString(FILE, "reminders_shown", text)
    }

    /** دو روز — بیش از ۲۴ ساعتِ نگاهِ شیفت و یک روزِ کلیدِ استراحت. */
    const val REMINDER_SHOWN_TTL_MS: Long = 48L * 60 * 60 * 1000

    /** سقفِ کلیدها، اگر روزی کارگاهی صدها شیفت در دو روز داشت. */
    private const val REMINDER_SHOWN_MAX = 500
}
