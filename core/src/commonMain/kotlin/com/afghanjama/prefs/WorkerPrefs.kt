package com.afghanjama.prefs

/**
 * «من کی هستم؟» — برچسبِ خیاط/ناظری که این دستگاه دستِ اوست،
 * مثلاً «[T10] احمد».
 *
 * عمداً یک انتخابِ صریح است و از رویِ نامِ ورود حدس زده نمی‌شود: نامِ
 * ورود متنِ آزاد است و اگر «احمد» را با «[T10] احمد» تطبیق می‌دادیم،
 * دو خیاطِ هم‌نام کارِ همدیگر را می‌دیدند.
 */
object WorkerPrefs {

    private const val FILE = "worker_prefs"
    private const val KEY_LABEL = "my_label"

    /** برچسبِ کارگرِ این دستگاه — خالی یعنی هنوز انتخاب نشده. */
    fun myLabel(s: Settings): String = s.getString(FILE, KEY_LABEL)

    fun setMyLabel(s: Settings, label: String) {
        s.putString(FILE, KEY_LABEL, label.trim())
    }

    fun clear(s: Settings) {
        s.remove(FILE, KEY_LABEL)
    }
}
