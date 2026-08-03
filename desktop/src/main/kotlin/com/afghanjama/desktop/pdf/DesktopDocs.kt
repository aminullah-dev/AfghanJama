package com.afghanjama.desktop.pdf

import com.afghanjama.data.entities.CustomerPayment
import com.afghanjama.data.entities.Document
import com.afghanjama.data.entities.LedgerEntry
import com.afghanjama.data.entities.Order
import com.afghanjama.data.entities.OrderFabric
import com.afghanjama.data.entities.OrderWorkItem
import com.afghanjama.pdf.Paper
import com.afghanjama.pdf.ReceiptData
import com.afghanjama.pdf.ShopInfo
import com.afghanjama.pdf.StatementData
import com.afghanjama.pdf.documentSheet
import com.afghanjama.pdf.invoiceSheets
import com.afghanjama.pdf.partyStatementSheets
import com.afghanjama.pdf.receiptSheet
import com.afghanjama.pdf.statementSheets
import java.io.File

/**
 * سندهای چاپیِ ویندوز.
 *
 * هیچ چیدمانی اینجا نیست و نباید بیاید — چیدمان در `:core` است و روی
 * گوشی هم همان اجرا می‌شود. اینجا فقط سه کار انجام می‌شود: قلم‌ها خوانده
 * می‌شوند، متر ساخته می‌شود، و برگه روی کاغذ می‌رود.
 *
 * اگر روزی کسی خواست اینجا «فقط یک خط» به فاکتور اضافه کند، همان لحظه
 * دو تعریف از فاکتور به وجود می‌آید. جایش `:core` است.
 */
object DesktopDocs {

    private val fonts by lazy { SheetFonts.load() }
    private val measurer by lazy { AwtTextMeasurer(fonts) }
    private val engine by lazy { SheetPdf(fonts) }

    /** پوشهٔ سندهای چاپ‌شده، کنارِ دفترِ کارگاه. */
    fun outputDir(): File {
        val base = System.getenv("APPDATA")?.takeIf { it.isNotBlank() }
            ?: System.getProperty("user.home")
        return File(File(base, "KhayatYar"), "اسناد").apply { mkdirs() }
    }

    fun receipt(
        data: ReceiptData,
        shop: ShopInfo,
        paper: Paper = Paper.ROLL80,
        target: File = File(outputDir(), "${data.number}.pdf")
    ): File = engine.write(receiptSheet(data, shop, measurer, paper), target)

    /** فاکتورِ سفارش — چندبرگه‌ای اگر اقلام زیاد باشند. */
    fun invoice(
        order: Order,
        fabrics: List<OrderFabric>,
        workItems: List<OrderWorkItem>,
        payments: List<CustomerPayment>,
        shop: ShopInfo,
        target: File = File(outputDir(), "فاکتور-${order.orderCode}.pdf")
    ): File = engine.write(
        invoiceSheets(order, fabrics, workItems, payments, shop, measurer),
        target
    )

    /** کارتِ حسابِ مشتری. */
    fun statement(
        data: StatementData,
        shop: ShopInfo,
        paper: Paper = Paper.A4,
        target: File = File(outputDir(), "کارت-حساب-${data.customerName}.pdf")
    ): File = engine.write(statementSheets(data, shop, measurer, paper), target)

    /** صورت‌حسابِ یک طرفِ دفتر کل. */
    fun partyStatement(
        partyType: String,
        partyName: String,
        net: Long,
        entries: List<LedgerEntry>,
        shop: ShopInfo,
        target: File = File(outputDir(), "صورتحساب-${safeName(partyName)}.pdf")
    ): File = engine.write(
        partyStatementSheets(partyType, partyName, net, entries, shop, measurer),
        target
    )

    /** رسیدِ یک سندِ مالی. */
    fun document(
        d: Document,
        shop: ShopInfo,
        target: File = File(outputDir(), "${safeName(d.number)}.pdf")
    ): File = engine.write(documentSheet(d, shop, measurer), target)

    /**
     * نامِ فایلِ بی‌خطر.
     *
     * نامِ مشتری مستقیم در نامِ فایل می‌نشیند و ویندوز این نویسه‌ها را
     * نمی‌پذیرد؛ بدونِ این، ذخیره برای مشتری‌ای با «/» در نامش می‌شکند.
     */
    private fun safeName(raw: String): String =
        raw.replace(Regex("[/\\\\:*?\"<>|]"), "_")
}
