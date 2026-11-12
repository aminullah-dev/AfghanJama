package com.afghanjama.desktop.platform

import com.afghanjama.desktop.pdf.AwtTextMeasurer
import com.afghanjama.desktop.pdf.DesktopDocs
import com.afghanjama.desktop.pdf.SheetFonts
import com.afghanjama.desktop.pdf.SheetPdf
import com.afghanjama.data.entities.LedgerEntry
import com.afghanjama.pdf.ShopInfo
import com.afghanjama.pdf.DocKinds
import com.afghanjama.pdf.InvoiceData
import com.afghanjama.pdf.InvoiceLine
import com.afghanjama.pdf.ReceiptData
import com.afghanjama.data.entities.docTypeLabel
import com.afghanjama.ui.format.fa
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
                "تبدیل به تصویر روی کامپیوتر هنوز نیست — PDF باز شد."
            }
        }
    }

    override suspend fun documentAction(
        repo: Repo,
        doc: Document,
        paper: Paper,
        action: SheetAction
    ): String? = withContext(Dispatchers.IO) {
        val file = runCatching { renderDocument(repo, doc, paper) }.getOrNull()
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
                "تبدیل به تصویر روی کامپیوتر هنوز نیست — PDF باز شد."
            }
        }
    }

    /**
     * برگهٔ یک سند، به شکلِ نوعِ خودش — همان سه شاخه‌ای که اندروید در
     * `DocumentRenderer` دارد.
     *
     * **تا ۱.۱۳ هر سندی روی کامپیوتر همان برگهٔ سادهٔ «یک مبلغ» بود** و کاغذِ
     * انتخاب‌شده هم نادیده گرفته می‌شد: فاکتورِ فروش بی ردیفِ کالا، بی تخفیف،
     * بی پرداختی و بی ماندهٔ قبلی چاپ می‌شد. `lineInvoice` و `receipt` در
     * `DesktopDocs` بودند و کسی صدایشان نمی‌زد.
     *
     * فاکتوری که ردیفی برایش پیدا نشود (سندِ قدیمی) به همان برگهٔ ساده
     * برمی‌گردد، مثلِ اندروید.
     */
    private suspend fun renderDocument(repo: Repo, doc: Document, paper: Paper): File {
        val title = docTypeLabel(doc.type)
        return when {
            doc.type in DocKinds.ITEMISED -> {
                val isSale = doc.type == "SALE"
                val saleRows = if (isSale) repo.saleLinesByCode(doc.refId) else emptyList()
                val lines =
                    if (isSale) saleRows.map {
                        InvoiceLine(
                            code = repo.designCodeFor(it.productName),
                            name = listOf(it.productName, it.size).filter { s -> s.isNotBlank() }
                                .joinToString(" • "),
                            qty = it.qty.toDouble(),
                            unit = "عدد",
                            unitPrice = it.unitPrice,
                            total = it.total
                        )
                    } else repo.purchaseItemsByCode(doc.refId).map {
                        InvoiceLine(
                            code = it.id.fa(),
                            name = it.name,
                            qty = it.qty,
                            unit = it.unit,
                            unitPrice = it.unitPrice,
                            total = it.total
                        )
                    }
                if (lines.isEmpty()) return DesktopDocs.document(doc, shop())

                val partyType = if (isSale) "CUSTOMER" else "SUPPLIER"
                val previousDue = repo.partyBalanceBefore(partyType, doc.partyName, doc.refId, doc.at)
                val currentDue = repo.partyBalanceUpTo(partyType, doc.partyName, doc.at)
                DesktopDocs.lineInvoice(
                    InvoiceData(
                        title = title,
                        number = doc.number,
                        at = doc.at,
                        partyLabel = if (isSale) "خریدار" else "فروشنده",
                        partyName = doc.partyName,
                        partyPhone = if (isSale) repo.customerByName(doc.partyName)?.phone.orEmpty() else "",
                        lines = lines,
                        discount = saleRows.sumOf { it.discount },
                        paid = previousDue + lines.sumOf { it.total } - currentDue,
                        previousDue = previousDue,
                        note = doc.note
                    ),
                    shop(),
                    paper
                )
            }

            DocKinds.isReceipt(doc.type) -> {
                val workshop = CompanyPrefs.shopName(settings)
                val outgoing = doc.type in OUTGOING
                val partyType = when (doc.type) {
                    "SUPPLIER_PAYMENT" -> "SUPPLIER"
                    "WAGE_RECEIPT" -> "TAILOR"
                    "SALARY_RECEIPT" -> "EMPLOYEE"
                    else -> "CUSTOMER"
                }
                DesktopDocs.receipt(
                    ReceiptData(
                        title = title,
                        number = doc.number,
                        at = doc.at,
                        payer = if (outgoing) workshop else doc.partyName,
                        payee = if (outgoing) doc.partyName else workshop,
                        amount = doc.amount,
                        remainingDue = repo.partyBalanceUpTo(partyType, doc.partyName, doc.at),
                        note = doc.note
                    ),
                    shop(),
                    paper
                )
            }

            else -> DesktopDocs.document(doc, shop())
        }
    }

    /** سندهایی که پول از کارگاه بیرون می‌رود — پرداخت‌کننده خودِ کارگاه است. */
    private val OUTGOING = setOf("PAYMENT", "SUPPLIER_PAYMENT", "WAGE_RECEIPT", "SALARY_RECEIPT")

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
