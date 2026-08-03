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
