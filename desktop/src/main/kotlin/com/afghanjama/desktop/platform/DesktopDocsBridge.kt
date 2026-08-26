package com.afghanjama.desktop.platform

import com.afghanjama.desktop.pdf.AwtTextMeasurer
import com.afghanjama.desktop.pdf.DesktopDocs
import com.afghanjama.desktop.pdf.SheetFonts
import com.afghanjama.desktop.pdf.SheetPdf
import com.afghanjama.data.entities.LedgerEntry
import com.afghanjama.pdf.ShopInfo
import com.afghanjama.pdf.SheetDoc
import com.afghanjama.pdf.StatementData
import com.afghanjama.pdf.partyStatementSheets
import com.afghanjama.pdf.financialSheets
import com.afghanjama.pdf.statementSheets
import com.afghanjama.ui.vm.BalanceSheet
import com.afghanjama.ui.vm.IncomeStatement
import com.afghanjama.prefs.CompanyPrefs
import com.afghanjama.prefs.Settings
import com.afghanjama.pdf.TextMeasurer
import com.afghanjama.data.entities.CustomerPayment
import com.afghanjama.data.entities.Order
import com.afghanjama.data.entities.OrderFabric
import com.afghanjama.data.entities.OrderWorkItem
import com.afghanjama.data.entities.Document
import com.afghanjama.data.repo.Repo
import com.afghanjama.pdf.Paper
import com.afghanjama.ui.components.SheetAction
import com.afghanjama.platform.Docs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.Desktop
import java.io.File

/**
 * [Docs] روی ویندوز.
 *
 * «اشتراک» روی پی‌سی معنیِ اندرویدی ندارد؛ نزدیک‌ترین کارِ مفید این است
 * که فایل کنارِ بقیهٔ اسنادِ کارگاه ذخیره و با نمایشگرِ PDFِ خودِ ویندوز
 * باز شود — از همان‌جا کاربر می‌تواند چاپ یا ارسالش کند.
 *
 * بازکردن `runCatching` دارد چون روی رانرِ CI و سرورِ بی‌میزکار
 * `Desktop` در دسترس نیست. آنجا فایل ساخته می‌شود و همان کافی است؛
 * نبودنِ نمایشگر نباید ساختِ سند را بشکند.
 */
class DesktopDocsBridge(private val settings: Settings) : Docs {

    private val fonts by lazy { SheetFonts.load() }
    private val engine by lazy { SheetPdf(fonts) }

    override val measurer: TextMeasurer by lazy { AwtTextMeasurer(fonts) }

    override suspend fun share(doc: SheetDoc, fileName: String, title: String) {
        withContext(Dispatchers.IO) {
            val target = File(DesktopDocs.outputDir(), safeName(fileName))
            engine.write(doc, target)
            runCatching {
                if (Desktop.isDesktopSupported()) Desktop.getDesktop().open(target)
            }
        }
    }

    override suspend fun statement(data: StatementData, fileName: String, title: String) {
        share(statementSheets(data, shop(), measurer), fileName, title)
    }

    override suspend fun financials(
        income: IncomeStatement,
        balance: BalanceSheet,
        periodLabel: String,
        title: String
    ) {
        share(
            financialSheets(income, balance, periodLabel, shop(), measurer),
            "surat-mali.pdf",
            title
        )
    }

    override suspend fun partyStatement(
        partyType: String,
        partyName: String,
        net: Long,
        entries: List<LedgerEntry>,
        title: String
    ) {
        share(
            partyStatementSheets(partyType, partyName, net, entries, shop(), measurer),
            "$partyName.pdf",
            title
        )
    }

    /** اطلاعاتِ کارگاه از تنظیماتِ خودِ ویندوز. */
    /**
     * سندِ دفتر روی ویندوز.
     *
     * **رندرِ خودش را دارد، نه رندرِ اندروید.** `DesktopDocs` از
     * چیدمان‌های مشترکِ `:core` می‌سازد و اندروید از `PdfKit`. یکی‌کردنشان
     * ظاهرِ کاغذی را که کارگاه امروز چاپ می‌کند عوض می‌کند و تصمیمِ
     * کارفرماست — پس هر سکو کاغذِ خودش را نگه داشت.
     *
     * **آنچه اینجا کمتر از اندروید است، صریح گفته می‌شود.** ویندوز
     * تبدیلِ PDF به تصویر ندارد، و چاپِ مستقیم به این بستگی دارد که
     * سیستم چاپگری معرفی کرده باشد. در هر دو حال PDF ساخته و باز
     * می‌شود، و پیام می‌گوید چه شد — سکوت بدترین حالت است، چون کاربر
     * فکر می‌کند کار انجام شده.
     */
    /** فاکتورِ سفارش با رندرِ ویندوز — چیدمانِ مشترکِ `invoiceSheets`. */
    override suspend fun orderInvoice(
        order: Order,
        fabrics: List<OrderFabric>,
        workItems: List<OrderWorkItem>,
        payments: List<CustomerPayment>,
        action: SheetAction
    ): String? = withContext(Dispatchers.IO) {
        val file = runCatching {
            DesktopDocs.invoice(order, fabrics, workItems, payments, shop())
        }.getOrNull() ?: return@withContext "ساخت فاکتور ناموفق بود."

        fun open() = runCatching {
            if (Desktop.isDesktopSupported()) Desktop.getDesktop().open(file)
        }

        when (action) {
            SheetAction.PDF -> { open(); null }

            SheetAction.PRINT -> {
                val canPrint = Desktop.isDesktopSupported() &&
                    Desktop.getDesktop().isSupported(Desktop.Action.PRINT)
                if (canPrint && runCatching { Desktop.getDesktop().print(file) }.isSuccess) null
                else {
                    open()
                    "چاپگری در دسترس نبود — PDF باز شد تا از همان‌جا چاپ کنید."
                }
            }

            SheetAction.IMAGE -> {
                open()
                "تبدیل به تصویر روی ویندوز هنوز نیست — PDF باز شد."
            }
        }
    }

    override suspend fun documentAction(
        repo: Repo,
        doc: Document,
        paper: Paper,
        action: SheetAction
    ): String? = withContext(Dispatchers.IO) {
        val file = runCatching { DesktopDocs.document(doc, shop()) }.getOrNull()
            ?: return@withContext "ساختِ برگه انجام نشد."

        fun open() = runCatching {
            if (Desktop.isDesktopSupported()) Desktop.getDesktop().open(file)
        }

        when (action) {
            SheetAction.PDF -> {
                open()
                null
            }

            SheetAction.PRINT -> {
                val canPrint = Desktop.isDesktopSupported() &&
                    Desktop.getDesktop().isSupported(Desktop.Action.PRINT)
                if (canPrint && runCatching { Desktop.getDesktop().print(file) }.isSuccess) null
                else {
                    open()
                    "چاپگری در دسترس نبود — PDF باز شد تا از همان‌جا چاپ کنید."
                }
            }

            SheetAction.IMAGE -> {
                open()
                "تبدیل به تصویر روی ویندوز هنوز نیست — PDF باز شد."
            }
        }
    }

    private fun shop() = ShopInfo(
        name = CompanyPrefs.shopName(settings),
        phone = CompanyPrefs.phone(settings),
        address = CompanyPrefs.address(settings)
    )

    /**
     * نامِ فایل از دادهٔ کارگاه می‌آید (نامِ مشتری، شمارهٔ سند) و ویندوز
     * روی `\ / : * ? " < > |` فایل نمی‌سازد. بدونِ این، سندِ مشتری‌ای که
     * نامش ممیز دارد بی‌صدا ساخته نمی‌شود.
     */
    private fun safeName(raw: String): String =
        raw.replace(Regex("""[\\/:*?"<>|]"""), "-").ifBlank { "sanad.pdf" }
}
