package com.afghanjama.data

/**
 * پوشه‌بندیِ انبارِ محصول — مثلِ فایل‌منیجر: دسته → طرح → سایز.
 *
 * انبار یک فهرستِ صافِ «نام + سایز» است و با زیاد شدنِ طرح‌ها پیدا کردنِ
 * یک کالا سخت می‌شود. دسته از **طرح** می‌آید، نه از خودِ کالا، پس
 * محصولی که از نظارت وارد انبار می‌شود بی هیچ کارِ دستی سرِ جایش
 * می‌نشیند: نامِ طرحش را با خودش دارد و طرح دسته‌اش را می‌داند.
 *
 * اینجا هیچ چیزِ اندرویدی و هیچ دیتابیسی نیست تا CI بسنجدش.
 */
object StockFolders {

    /** پوشهٔ طرح‌هایی که دسته‌ای برایشان تعیین نشده. */
    const val UNCATEGORISED = "دسته‌بندی‌نشده"

    /**
     * دستهٔ یک کالا از رویِ نامِ طرحش.
     *
     * [categoryOfDesign] نامِ طرح → دستهٔ ثبت‌شده‌اش (خالی یا نبود = بی‌دسته).
     * تطبیق با نامِ **پیرایش‌شده** انجام می‌شود چون نامِ طرح در سفارش و در
     * کاتالوگ می‌تواند فاصلهٔ اضافه داشته باشد.
     */
    fun folderOf(productName: String, categoryOfDesign: Map<String, String>): String {
        val raw = categoryOfDesign[productName.trim()]?.trim().orEmpty()
        return raw.ifEmpty { UNCATEGORISED }
    }

    /**
     * ساختِ نقشهٔ «نامِ طرح → دسته» از کاتالوگِ طرح‌ها.
     *
     * نام‌های تکراری (با فاصلهٔ متفاوت) روی هم می‌افتند؛ آخری برنده است،
     * ولی چون نامِ طرح در دیتابیس یکتاست در عمل پیش نمی‌آید.
     */
    fun categoryMap(designs: List<Pair<String, String>>): Map<String, String> =
        designs.associate { (title, category) -> title.trim() to category.trim() }

    /**
     * فهرستِ پوشه‌ها با تعدادِ کالا و جمعِ موجودیِ هرکدام.
     *
     * ترتیب: «دسته‌بندی‌نشده» **آخر** می‌آید تا پوشه‌های واقعی بالا بمانند،
     * بقیه الفبایی. پوشهٔ خالی ساخته نمی‌شود.
     */
    fun folders(
        items: List<StockRow>,
        categoryOfDesign: Map<String, String>
    ): List<Folder> {
        val grouped = items.groupBy { folderOf(it.name, categoryOfDesign) }
        return grouped
            .map { (folder, rows) ->
                Folder(
                    name = folder,
                    designs = rows.map { it.name.trim() }.distinct().size,
                    totalQty = rows.sumOf { it.qty }
                )
            }
            .sortedWith(
                compareBy<Folder> { it.name == UNCATEGORISED }.thenBy { it.name }
            )
    }

    /** کالاهای یک پوشه، الفبایی بر اساسِ نامِ طرح و بعد سایز. */
    fun itemsOf(
        folder: String,
        items: List<StockRow>,
        categoryOfDesign: Map<String, String>
    ): List<StockRow> =
        items
            .filter { folderOf(it.name, categoryOfDesign) == folder }
            .sortedWith(compareBy({ it.name.trim() }, { it.size.trim() }))

    /** حداقلِ چیزی که پوشه‌بندی از یک ردیفِ انبار لازم دارد. */
    data class StockRow(val name: String, val size: String, val qty: Int)

    /** یک پوشه و خلاصه‌اش. */
    data class Folder(val name: String, val designs: Int, val totalQty: Int)
}
