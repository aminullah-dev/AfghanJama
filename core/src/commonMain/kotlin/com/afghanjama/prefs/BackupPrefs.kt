package com.afghanjama.prefs

/**
 * «آخرین پشتیبان کِی گرفته شد» — نوشته‌شده توسطِ زمان‌بندِ سکو،
 * خوانده‌شده توسطِ صفحه‌ها.
 *
 * جای این عدد اینجاست نه کنارِ زمان‌بند: **خواندنش کارِ همه است و
 * نوشتنش کارِ یک نفر.** دو صفحه («کارهای امروز» و داشبورد) فقط برای
 * همین یک عدد به `Context` وابسته بودند و در `:app` مانده بودند — در
 * حالی که خودشان هیچ چیزی زمان‌بندی نمی‌کنند.
 *
 * نامِ فایل و کلید همان‌هایی است که `AutoBackupWorker` تا امروز
 * می‌نوشت، پس گوشیِ کارگاه تاریخِ پشتیبانش را از دست نمی‌دهد.
 */
object BackupPrefs {
    const val FILE = "backup_prefs"
    const val KEY_LAST = "last_auto_backup"

    /** صفر یعنی هنوز هیچ پشتیبانِ خودکاری گرفته نشده. */
    fun lastAuto(s: Settings): Long = s.getLong(FILE, KEY_LAST, 0L)

    fun setLastAuto(s: Settings, at: Long) = s.putLong(FILE, KEY_LAST, at)
}
