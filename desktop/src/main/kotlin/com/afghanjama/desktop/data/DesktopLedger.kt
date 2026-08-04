package com.afghanjama.desktop.data

import com.afghanjama.data.repo.Repo

/**
 * دفترِ کارگاه روی ویندوز — **یک بار** باز می‌شود و همان می‌ماند.
 *
 * تا دیروز فقط `LedgerStatusViewModel` دیتابیس را باز می‌کرد و برای
 * خودش نگه می‌داشت. حالا که پنجره صفحه‌های واقعی نشان می‌دهد، چند
 * ViewModel به `Repo` نیاز دارند و اگر هرکدام خودش باز می‌کرد، چند
 * اتصالِ جدا به یک فایلِ SQLite می‌ماند — که هم حافظه می‌برد و هم روزی
 * سرِ قفلِ نوشتن به هم می‌خورند.
 *
 * نتیجه در [Result] بسته‌بندی شده نه اینکه استثنا بالا برود: اگر فایل
 * باز نشد، پنجره باید **باز شود و خطا را نشان دهد**. برنامه‌ای که سرِ
 * بالا آمدن می‌ترکد به کارگاه نمی‌گوید چه شده.
 */
object DesktopLedger {

    @Volatile
    private var cached: Result<Repo>? = null

    @Synchronized
    fun repo(): Result<Repo> =
        cached ?: runCatching { Repo(openDatabase()) }.also { cached = it }
}
