package com.afghanjama.platform

import androidx.compose.runtime.staticCompositionLocalOf
import com.afghanjama.data.entities.CustomerPayment
import com.afghanjama.data.entities.Order
import com.afghanjama.data.entities.OrderFabric
import com.afghanjama.data.entities.OrderWorkItem
import com.afghanjama.data.entities.Document
import com.afghanjama.data.repo.Repo
import com.afghanjama.pdf.Paper
import com.afghanjama.ui.components.SheetAction
import com.afghanjama.data.entities.LedgerEntry
import com.afghanjama.pdf.SheetDoc
import com.afghanjama.pdf.StatementData
import com.afghanjama.pdf.TextMeasurer
import com.afghanjama.ui.vm.BalanceSheet
import com.afghanjama.ui.vm.IncomeStatement

/**
 * ساختن و بیرون‌دادنِ سندِ کاغذی — بی اینکه صفحه بداند چه کسی می‌کشد.
 *
 * مرزِ سومِ فازِ ۴.۵، و متفاوت با دوتای قبلی: [com.afghanjama.prefs.Settings]
 * و [SystemActions] فقط کاری را به سکو می‌سپردند. اینجا **چیدمان** در
 * `:core` می‌ماند (`SheetDoc`) و فقط رسم و تحویل به سکو می‌رود.
 *
 * چرا این تفکیک: چیدمانِ فاکتور — اینکه کدام ستون کجا بنشیند و جمع کجا
 * بیاید — منطقِ کارگاه است نه کارِ سکو. اگر هر سکو خودش می‌چید، دو
 * پیاده‌سازی می‌شد که روزی سرِ یک عدد با هم اختلاف پیدا می‌کنند. همان
 * تله‌ای که سرِ دیتابیس از آن دوری شد.
 */
interface Docs {

    /**
     * مترِ متنِ همین سکو.
     *
     * چیدمان **بدونِ اندازهٔ متن ساخته نمی‌شود**: ارتفاعِ هر سطر تعیین
     * می‌کند سطرِ بعد کجا بنشیند و کجا برگه تمام شود. برای همین مدلِ
     * `Sheet` به‌تنهایی کافی نبود و این هم باید از سکو بیاید.
     */
    val measurer: TextMeasurer

    /**
     * سند را می‌سازد و به کاربر تحویل می‌دهد.
     *
     * روی گوشی یعنی پنجرهٔ اشتراک (واتس‌اپ، ایمیل…)، روی ویندوز یعنی
     * ذخیره و بازکردنش با نمایشگرِ PDFِ سیستم.
     *
     * [fileName] باید پسوندِ `.pdf` داشته باشد و نامِ قابلِ خواندن برای
     * کارگاه باشد — همان چیزی که در واتس‌اپ دیده می‌شود.
     */
    suspend fun share(doc: SheetDoc, fileName: String, title: String)

    /**
     * یک سندِ دفتر را می‌سازد و کارِ خواسته‌شده را رویش انجام می‌دهد.
     *
     * **چرا این یکی `SheetDoc` نمی‌گیرد و خودِ سطرِ سند را می‌گیرد.**
     * بقیهٔ متدهای این مرز چیدمانِ آماده می‌گیرند، چون چیدمانشان
     * مشترک است. اسنادِ دفتر این‌طور نیستند: اندروید فاکتور و رسید را
     * با `PdfKit` می‌کشد و ویندوز با `SheetPdf` — و **ظاهرِ کاغذی که
     * کارگاه امروز چاپ می‌کند نباید عوض شود**. یکی‌کردنشان تصمیمِ
     * کارفرماست، نه اثرِ جانبیِ پرتابل کردنِ یک صفحه (در
     * `docs/WINDOWS.md` نوشته شده).
     *
     * پس قرارداد اینجا **کار** است نه چیدمان: «این سند را چاپ کن /
     * PDF بده / تصویر بده». هر سکو با رندرِ خودش انجامش می‌دهد و
     * کاغذِ هیچ‌کدام تکان نمی‌خورد.
     *
     * [repo] لازم است چون سندِ ردیف‌دار (فاکتورِ فروش و خرید) ردیف‌هایش
     * را از دفتر می‌خواند، نه از خودِ سطرِ سند.
     *
     * برمی‌گرداند: `null` اگر انجام شد، وگرنه پیامی که باید به کاربر
     * نشان داده شود.
     */
    /**
     * فاکتورِ یک سفارش — همان الگوی [documentAction]، برای سندی که
     * سطرِ `documents` نیست بلکه از خودِ سفارش ساخته می‌شود.
     *
     * جدا ماند و در [documentAction] ادغام نشد چون ورودی‌اش فرق
     * دارد: اینجا سفارش و پارچه و کارها و پرداخت‌ها لازم است، و
     * صفحه از قبل هر چهار تا را در دست دارد. گرفتنشان از دفتر یعنی
     * همان پرس‌وجوها دو بار.
     */
    suspend fun orderInvoice(
        order: Order,
        fabrics: List<OrderFabric>,
        workItems: List<OrderWorkItem>,
        payments: List<CustomerPayment>,
        action: SheetAction
    ): String?

    suspend fun documentAction(
        repo: Repo,
        doc: Document,
        paper: Paper,
        action: SheetAction
    ): String?

    // ---- سندهایی که هنوز چیدمانِ مشترک ندارند ----
    //
    // **چرا این دو اینجایند و نه از راهِ `share` بالا:**
    //
    // چیدمانِ مشترک (`DocChrome.header`) یک سربرگِ متنیِ ساده می‌کشد،
    // ولی `PdfKit.drawHeader`ِ اندروید یک نوارِ سبز، نشانِ کارگاه در یک
    // کادرِ گِرد، و لوگوی برداریِ سوزن‌ونخ دارد. اگر امروز اندروید را به
    // چیدمانِ مشترک ببریم، **کاغذی که کارگاه چاپ می‌کند عوض می‌شود** —
    // و قاعدهٔ پروژه می‌گوید هیچ فازی نباید اپِ تحویل‌شده را بشکند.
    //
    // پس فعلاً هر سکو چیدمانِ خودش را نگه می‌دارد و این مرز فقط صفحه را
    // آزاد می‌کند. یکی‌کردنِ سربرگ کارِ جداست و چون ظاهرِ سندِ کارگاه را
    // عوض می‌کند، باید تصمیمِ کارفرما باشد نه اثرِ جانبیِ یک فاز.
    //
    // بدهی‌اش هم پنهان نیست: تا آن روز، هر تغییرِ چیدمان باید در دو جا
    // انجام شود.

    /** کارت حسابِ یک مشتری. */
    suspend fun statement(data: StatementData, fileName: String, title: String)

    /** صورت‌های مالی — سود و زیان و ترازنامه. */
    suspend fun financials(
        income: IncomeStatement,
        balance: BalanceSheet,
        periodLabel: String,
        title: String
    )

    /** صورت‌حسابِ یک طرفِ حساب (فروشنده، خیاط، …). */
    suspend fun partyStatement(
        partyType: String,
        partyName: String,
        net: Long,
        entries: List<LedgerEntry>,
        title: String
    )
}

/**
 * راهِ رسیدنِ صفحه‌ها به ساختِ سند.
 *
 * مثلِ بقیهٔ مرزها پیش‌فرض ندارد: پیاده‌سازیِ ساختگی یعنی کاربر دکمهٔ
 * «PDF» را بزند و هیچ اتفاقی نیفتد.
 */
val LocalDocs = staticCompositionLocalOf<Docs> {
    error(
        "LocalDocs داده نشده. ریشهٔ هر سکو باید با " +
            "CompositionLocalProvider(LocalDocs provides …) پیچیده شود."
    )
}
