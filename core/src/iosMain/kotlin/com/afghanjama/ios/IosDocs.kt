package com.afghanjama.ios

import com.afghanjama.data.entities.CustomerPayment
import com.afghanjama.data.entities.Document
import com.afghanjama.data.entities.LedgerEntry
import com.afghanjama.data.entities.Order
import com.afghanjama.data.entities.OrderFabric
import com.afghanjama.data.entities.OrderWorkItem
import com.afghanjama.data.repo.Repo
import com.afghanjama.pdf.Paper
import com.afghanjama.pdf.SheetDoc
import com.afghanjama.pdf.StatementData
import com.afghanjama.pdf.TextMeasurer
import com.afghanjama.pdf.Weight
import com.afghanjama.platform.Docs
import com.afghanjama.ui.components.SheetAction
import com.afghanjama.ui.vm.BalanceSheet
import com.afghanjama.ui.vm.IncomeStatement

/**
 * [Docs] روی آیفون — **هنوز PDF نمی‌سازد، و این را صریح می‌گوید.**
 *
 * `LocalDocs` مثلِ `LocalSystemActions` بی فراهم‌کننده خطا می‌دهد، پس
 * صفحه‌های اسناد، جزئیاتِ سفارش و گزارش‌ها روی آیفون سرِ باز شدن
 * می‌ترکیدند. با این، صفحه‌ها باز می‌شوند و فقط خودِ چاپ پیامِ «هنوز
 * نیست» می‌دهد — آنجا که قراردادِ `Docs` پیام برمی‌گرداند، پیام؛ آنجا
 * که ندارد، استثنایی با همان متن تا صداکننده نشانش دهد.
 *
 * ساختنِ PDFِ واقعی روی iOS (Core Graphics + CoreText برای متنِ
 * راست‌به‌چپ) کارِ جداست و این فایل وانمود نمی‌کند انجامش داده.
 */
object IosDocs : Docs {

    private const val NOT_YET = "چاپ و PDF روی نسخهٔ آیفون هنوز نیامده است."

    /**
     * متر تقریبی — فقط برای اینکه چیدمان بتواند بچیند؛ چون چیزی کشیده
     * نمی‌شود، دقیق بودنش اهمیتی ندارد.
     */
    override val measurer: TextMeasurer = object : TextMeasurer {
        override fun width(text: String, size: Float, weight: Weight): Float =
            text.length * size * 0.5f

        override fun wrap(text: String, size: Float, maxWidth: Float, weight: Weight): List<String> {
            if (text.isBlank()) return emptyList()
            val perLine = (maxWidth / (size * 0.5f)).toInt().coerceAtLeast(1)
            return text.chunked(perLine)
        }

        override fun lineHeight(size: Float, weight: Weight): Float = size * 1.6f
    }

    override suspend fun share(doc: SheetDoc, fileName: String, title: String) {
        throw UnsupportedOperationException(NOT_YET)
    }

    override suspend fun orderInvoice(
        order: Order,
        fabrics: List<OrderFabric>,
        workItems: List<OrderWorkItem>,
        payments: List<CustomerPayment>,
        action: SheetAction
    ): String? = NOT_YET

    override suspend fun documentAction(
        repo: Repo,
        doc: Document,
        paper: Paper,
        action: SheetAction
    ): String? = NOT_YET

    override suspend fun statement(data: StatementData, fileName: String, title: String) {
        throw UnsupportedOperationException(NOT_YET)
    }

    override suspend fun financials(
        income: IncomeStatement,
        balance: BalanceSheet,
        periodLabel: String,
        title: String
    ) {
        throw UnsupportedOperationException(NOT_YET)
    }

    override suspend fun partyStatement(
        partyType: String,
        partyName: String,
        net: Long,
        entries: List<LedgerEntry>,
        title: String
    ) {
        throw UnsupportedOperationException(NOT_YET)
    }
}
