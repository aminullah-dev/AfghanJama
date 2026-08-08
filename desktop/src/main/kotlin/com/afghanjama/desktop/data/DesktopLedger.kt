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
    private var cachedDb: Result<DesktopDatabase>? = null

    @Volatile
    private var cached: Result<Repo>? = null

    /**
     * خودِ دیتابیس — برای سرورِ شبکه.
     *
     * **چرا لازم شد:** `DesktopServer` تا دیروز خودش `openDatabase()`
     * را صدا می‌زد، یعنی یک اتصالِ **دوم** به همان فایل. دو ایراد داشت و
     * هر دو در توضیحِ بالای همین شیء از قبل هشدار داده شده بودند:
     *
     * - نوشتنِ گوشی از آن اتصال، `Flow`های پنجره را بی‌اعتبار نمی‌کرد.
     *   یعنی خیاط سفارشی را جلو می‌برد و **صفحهٔ پی‌سی همان‌جا می‌ماند**
     *   تا کسی دستی تازه‌اش کند.
     * - دو اتصالِ نویسنده به یک فایلِ SQLite، روزی سرِ قفل به هم می‌خورند.
     */
    @Synchronized
    fun db(): Result<DesktopDatabase> =
        cachedDb ?: runCatching { openDatabase() }.also { cachedDb = it }

    @Synchronized
    fun repo(): Result<Repo> =
        cached ?: db().mapCatching { Repo(it) }.also { result ->
            cached = result
            // تعمیرِ یک‌بارهٔ طبقه‌بندیِ نقدِ دستی — همان کاری که
            // `App.onCreate` روی گوشی می‌کند.
            //
            // اینجا و نه در `Main.kt`، چون دفتر ممکن است از راهِ
            // دیگری هم باز شود (دودآزمایی‌ها) و تعمیر نباید به یک
            // مسیرِ خاص گره بخورد. خودش در برابرِ اجرای دوباره
            // بی‌خطر است.
            result.getOrNull()?.let { repo ->
                runCatching {
                    kotlinx.coroutines.runBlocking {
                        repo.repairManualCashClassification()
                    }
                }
            }
        }
}
