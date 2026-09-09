package com.afghanjama.util

import kotlin.concurrent.Volatile

/**
 * هویتِ کاربرِ جاریِ دستگاه (نام + نقش) برای لاگِ حسابرسی.
 * هنگام ورود تنظیم و هنگام خروج پاک می‌شود. آفلاین و تک‌دستگاهه؛ در فازِ
 * سینک با حسابِ سروری جایگزین می‌شود.
 */
object CurrentUser {
    @Volatile var name: String = ""
    @Volatile var role: String = ""

    fun set(name: String, role: String) {
        this.name = name.trim()
        this.role = role
    }

    fun clear() {
        name = ""
        role = ""
    }
}
