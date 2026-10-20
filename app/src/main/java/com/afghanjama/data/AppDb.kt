package com.afghanjama.data

import android.content.Context

/**
 * **یک دیتابیس برای کلِ پروسه.**
 *
 * تا ۱.۹.۱ هر جا که دفتر لازم داشت `buildAppDatabase` را خودش صدا می‌زد:
 * `MainActivity` یکی، `AndroidLanHost` (سرورِ اشتراکِ کارگاه) یکی دیگر،
 * و هر کارِ پس‌زمینه یکی دیگر. Room فقط تغییرهای **همان نمونه** را به
 * `Flow`هایش خبر می‌دهد و «اعتبارسنجیِ چندنمونه‌ای» هم روشن نبود؛ پس
 * درخواستی که گوشیِ کارگر از راهِ سرور می‌نشاند در فهرستِ «درخواست‌های
 * منتظر»ِ گوشیِ اصلی دیده نمی‌شد، تا کارفرما صفحه را ترک کند و برگردد.
 * چرخاندنِ گوشی هم هر بار یک نمونهٔ تازه می‌ساخت و قبلی را باز رها می‌کرد.
 *
 * حالا همه از همین‌جا می‌گیرند و هیچ‌کس نمی‌بندد؛ بستن فقط کارِ
 * بازیابیِ پشتیبان است، و پس از آن [forget] نمونهٔ بسته را کنار می‌گذارد
 * تا بازِ بعدیِ برنامه فایلِ بازیابی‌شده را با نمونهٔ تازه باز کند.
 *
 * `tools/checks/onedb.py` نگه می‌دارد که `buildAppDatabase` جز اینجا صدا
 * زده نشود و کسی نمونهٔ مشترک را نبندد.
 */
object AppDb {

    @Volatile
    private var instance: AppDatabase? = null

    fun get(context: Context): AppDatabase =
        instance ?: synchronized(this) {
            instance ?: buildAppDatabase(context.applicationContext).also { instance = it }
        }

    /**
     * پس از بستنِ عمدیِ دفتر (بازیابیِ پشتیبان). نمونهٔ بسته دیگر داده
     * نمی‌شود؛ اگر پروسه زنده بماند و برنامه دوباره باز شود، [get] نمونهٔ
     * تازه‌ای روی فایلِ بازیابی‌شده می‌سازد.
     */
    fun forget() = synchronized(this) { instance = null }
}
