package com.afghanjama.prefs

/**
 * اطلاعاتِ کارگاه/شرکت که روی رسیدها و فاکتورهای PDF چاپ می‌شود.
 *
 * از [Settings] می‌خواند و همزمان است — مسیرِ ساختِ PDF این‌ها را وسطِ
 * چیدنِ کاغذ می‌خواهد و آنجا coroutine نیست.
 */
object CompanyPrefs {
    private const val FILE = "company"

    fun name(s: Settings): String = s.getString(FILE, "name")
    fun phone(s: Settings): String = s.getString(FILE, "phone")
    fun address(s: Settings): String = s.getString(FILE, "address")

    /**
     * نامی که روی کاغذ و رسید می‌نشیند.
     *
     * تا امروز هرجا نامِ کارگاه لازم بود، نامِ **خودِ اپ** نوشته می‌شد.
     * یعنی هر کارگاهی که این برنامه را می‌گرفت، فاکتور و رسیدش را به
     * نامِ کارگاهِ دیگری صادر می‌کرد. حالا نامِ خودش می‌آید.
     *
     * تا وقتی در تنظیمات چیزی ثبت نشده، یک عنوانِ خنثی برمی‌گردد نه
     * نامِ هیچ کسب‌وکارِ مشخصی.
     */
    fun shopName(s: Settings): String = name(s).ifBlank { DEFAULT_SHOP }

    /** عنوانِ خنثی تا وقتی کارگاه نامش را ثبت نکرده. */
    const val DEFAULT_SHOP = "کارگاه خیاطی"

    /**
     * نامِ فایلِ لوگوی کارگاه در پوشهٔ خصوصیِ اپ — خالی یعنی لوگو ندارد.
     * روی سرصفحهٔ فاکتور و رسیدهای چاپی می‌نشیند.
     */
    fun logo(s: Settings): String = s.getString(FILE, "logo")

    fun saveLogo(s: Settings, fileName: String) {
        s.putString(FILE, "logo", fileName)
    }

    fun save(s: Settings, name: String, phone: String, address: String) {
        s.putString(FILE, "name", name)
        s.putString(FILE, "phone", phone)
        s.putString(FILE, "address", address)
    }
}
