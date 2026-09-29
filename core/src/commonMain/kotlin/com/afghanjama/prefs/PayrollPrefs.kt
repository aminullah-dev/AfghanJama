package com.afghanjama.prefs

import com.afghanjama.data.PayrollSmart

/**
 * تنظیمِ حقوقِ هوشمند — روزهای کاریِ ماه.
 *
 * فقط کارفرما می‌داند ماهِ کارگاهش چند روزِ کاری دارد (جمعه‌ها تعطیل؟
 * رخصتیِ رسمی؟)، پس این عدد را او می‌گذارد و پیش‌فرض
 * [PayrollSmart.DEFAULT_WORKING_DAYS] است. پایهٔ «حقوقِ هر روز» و
 * «دستمزدِ هر ساعت» در پیشنهادهای حقوق همین است.
 */
object PayrollPrefs {

    private const val FILE = "payroll_prefs"
    private const val KEY_WORKING_DAYS = "working_days"

    /** روزهای کاریِ ماه؛ همیشه دستِ‌کم ۱ تا تقسیم بر صفر پیش نیاید. */
    fun workingDays(s: Settings): Int =
        s.getLong(FILE, KEY_WORKING_DAYS, PayrollSmart.DEFAULT_WORKING_DAYS.toLong())
            .toInt().coerceIn(1, 31)

    fun setWorkingDays(s: Settings, days: Int) {
        s.putLong(FILE, KEY_WORKING_DAYS, days.coerceIn(1, 31).toLong())
    }
}
