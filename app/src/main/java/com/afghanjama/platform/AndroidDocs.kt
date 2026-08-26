package com.afghanjama.platform

import android.content.Context
import com.afghanjama.data.entities.CustomerPayment
import com.afghanjama.data.entities.Order
import com.afghanjama.data.entities.OrderFabric
import com.afghanjama.data.entities.OrderWorkItem
import com.afghanjama.pdf.InvoicePdf
import com.afghanjama.data.entities.Document
import com.afghanjama.data.repo.Repo
import com.afghanjama.pdf.DocumentRenderer
import com.afghanjama.pdf.Paper
import com.afghanjama.util.PrintKit
import com.afghanjama.ui.components.SheetAction
import java.io.File
import com.afghanjama.data.entities.LedgerEntry
import com.afghanjama.pdf.AndroidTextMeasurer
import com.afghanjama.pdf.FinancialStatementsPdf
import com.afghanjama.pdf.PartyStatementPdf
import com.afghanjama.pdf.StatementData
import com.afghanjama.pdf.StatementPdf
import com.afghanjama.pdf.PdfKit
import com.afghanjama.pdf.SheetDoc
import com.afghanjama.pdf.SheetPdfAndroid
import com.afghanjama.pdf.TextMeasurer
import com.afghanjama.ui.vm.BalanceSheet
import com.afghanjama.ui.vm.IncomeStatement
import com.afghanjama.util.ShareUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * [Docs] روی اندروید.
 *
 * فونت‌ها یک بار خوانده می‌شوند و می‌مانند: `ResourcesCompat.getFont`
 * ارزان نیست و مترِ متن در مسیرِ چیدمان **برای هر سطر** صدا زده می‌شود.
 */
class AndroidDocs(private val ctx: Context) : Docs {

    private val fonts: PdfKit.Fonts by lazy { PdfKit.fonts(ctx) }

    override val measurer: TextMeasurer by lazy { AndroidTextMeasurer(fonts) }

    /**
     * همان کاری که تا امروز `DocumentsViewModel` مستقیم می‌کرد.
     *
     * کد **کلمه‌به‌کلمه** از آنجا آمد و عمداً دست نخورد: خروجی‌اش
     * کاغذی است که کارگاه هر روز چاپ می‌کند. آنچه عوض شد فقط جای
     * نشستنش است — از ViewModel به این‌سوی مرز، تا صفحهٔ اسناد
     * بتواند به `:core` برود و روی ویندوز هم باز شود.
     */
    /**
     * فاکتورِ سفارش — همان `InvoicePdf`ی که تا امروز صفحهٔ جزئیات
     * مستقیم صدا می‌زد. کاغذ عوض نشد؛ فقط از این‌سوی مرز می‌آید.
     */
    override suspend fun orderInvoice(
        order: Order,
        fabrics: List<OrderFabric>,
        workItems: List<OrderWorkItem>,
        payments: List<CustomerPayment>,
        action: SheetAction
    ): String? {
        val file = runCatching {
            withContext(Dispatchers.IO) {
                InvoicePdf.create(ctx, order, fabrics, workItems, payments)
            }
        }.getOrNull() ?: return "ساخت فاکتور ناموفق بود."

        return when (action) {
            SheetAction.PDF -> {
                ShareUtil.shareFile(ctx, file, "application/pdf", "اشتراک فاکتور")
                null
            }

            SheetAction.PRINT ->
                if (PrintKit.print(ctx, file, order.orderCode)) null
                else "چاپ ممکن نشد؛ PDF یا تصویر را بفرستید."

            SheetAction.IMAGE -> {
                val jpg = File(ShareUtil.sharedDir(ctx), "${order.orderCode}.jpg")
                val ok = withContext(Dispatchers.IO) { PrintKit.toImage(file, jpg) }
                if (ok) {
                    ShareUtil.shareFile(ctx, jpg, "image/jpeg", "اشتراک تصویر فاکتور")
                    null
                } else "تبدیل به تصویر انجام نشد؛ همان PDF را بفرستید."
            }
        }
    }

    override suspend fun documentAction(
        repo: Repo,
        doc: Document,
        paper: Paper,
        action: SheetAction
    ): String? {
        val file = runCatching {
            withContext(Dispatchers.IO) { DocumentRenderer.render(ctx, repo, doc, paper) }
        }.getOrNull() ?: return "ساختِ برگه انجام نشد."

        return when (action) {
            SheetAction.PDF -> {
                ShareUtil.shareFile(ctx, file, "application/pdf", "اشتراک‌گذاری فاکتور")
                null
            }

            SheetAction.PRINT ->
                if (PrintKit.print(ctx, file, doc.number)) null
                else "چاپ ممکن نشد. اگر پرینتری نصب نیست، «PDF» یا «تصویر» را بفرستید."

            SheetAction.IMAGE -> {
                val jpg = File(ShareUtil.sharedDir(ctx), "${doc.number}.jpg")
                val ok = withContext(Dispatchers.IO) { PrintKit.toImage(file, jpg) }
                if (ok) {
                    ShareUtil.shareFile(ctx, jpg, "image/jpeg", "اشتراک‌گذاری تصویر")
                    null
                } else "تبدیل به تصویر انجام نشد؛ همان PDF را بفرستید."
            }
        }
    }

    override suspend fun share(doc: SheetDoc, fileName: String, title: String) {
        // ساختِ PDF دیسک می‌نویسد و روی نخِ اصلی رابط را می‌خشکاند —
        // روی فاکتورِ چندبرگه‌ای دیده می‌شود.
        val file = withContext(Dispatchers.IO) {
            SheetPdfAndroid.write(ctx, doc, fileName)
        }
        ShareUtil.shareFile(ctx, file, "application/pdf", title)
    }

    // این دو عمداً از `PdfKit` می‌آیند نه از چیدمانِ مشترک — تا کاغذی که
    // کارگاه امروز چاپ می‌کند بی‌تغییر بماند. دلیلش در خودِ `Docs` نوشته شده.
    override suspend fun statement(data: StatementData, fileName: String, title: String) {
        val file = withContext(Dispatchers.IO) {
            StatementPdf.create(ctx, data, fileName = fileName)
        }
        ShareUtil.shareFile(ctx, file, "application/pdf", title)
    }

    override suspend fun financials(
        income: IncomeStatement,
        balance: BalanceSheet,
        periodLabel: String,
        title: String
    ) {
        val file = withContext(Dispatchers.IO) {
            FinancialStatementsPdf.create(ctx, income, balance, periodLabel)
        }
        ShareUtil.shareFile(ctx, file, "application/pdf", title)
    }

    override suspend fun partyStatement(
        partyType: String,
        partyName: String,
        net: Long,
        entries: List<LedgerEntry>,
        title: String
    ) {
        val file = withContext(Dispatchers.IO) {
            PartyStatementPdf.create(ctx, partyType, partyName, net, entries)
        }
        ShareUtil.shareFile(ctx, file, "application/pdf", title)
    }
}
