package com.afghanjama.prefs

import android.content.Context

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

    private fun p(ctx: Context) = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /** برچسبِ کارگرِ این دستگاه — خالی یعنی هنوز انتخاب نشده. */
    fun myLabel(ctx: Context): String = p(ctx).getString(KEY_LABEL, "") ?: ""

    fun setMyLabel(ctx: Context, label: String) {
        p(ctx).edit().putString(KEY_LABEL, label.trim()).apply()
    }

    fun clear(ctx: Context) {
        p(ctx).edit().remove(KEY_LABEL).apply()
    }
}
