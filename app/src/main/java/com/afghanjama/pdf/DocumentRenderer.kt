package com.afghanjama.pdf

import android.content.Context
import com.afghanjama.data.entities.Document
import com.afghanjama.data.entities.docTypeLabel
import com.afghanjama.data.repo.Repo
import com.afghanjama.prefs.CompanyPrefs
import com.afghanjama.ui.format.fa
import java.io.File

/**
 * تنها راهِ چاپِ یک سند.
 *
 * تا حالا هر سندی — چه فاکتور فروشِ چندردیفه، چه رسیدِ حقوق — یک برگهٔ
 * یکسانِ «مبلغ: فلان» می‌شد. حالا نوعِ سند تعیین می‌کند چه برگه‌ای لازم
 * است و ردیف‌ها از روی `refId` پیدا می‌شوند: در فروش کدِ فاکتور است و در
 * خرید هم همین‌طور.
 *
 * سندی که ردیف ندارد (یا ردیف‌هایش پیدا نشد) همان برگهٔ سادهٔ قبلی را
 * می‌گیرد — هیچ سندی بی‌چاپ نمی‌ماند.
 */
object DocumentRenderer {

    /** نوع‌هایی که ردیف دارند و باید جدول‌دار چاپ شوند. */
    private val ITEMISED = setOf("SALE", "PURCHASE")

    /** نوع‌هایی که رسیدِ پول‌اند نه فاکتور. */
    private val RECEIPTS = setOf(
        "PAYMENT", "RECEIPT", "CUSTOMER_RECEIPT", "SUPPLIER_PAYMENT",
        "WAGE_RECEIPT", "SALARY_RECEIPT"
    )

    /** رسیدِ پول است یا فاکتور؟ کاغذ و چیدمانِ این دو فرق دارد. */
    fun isReceipt(type: String): Boolean = type in RECEIPTS

    /** کاغذِ منطقی برای هر نوع سند، وقتی کاربر چیزی انتخاب نکرده. */
    fun defaultPaper(type: String): Paper =
        if (isReceipt(type)) Paper.ROLL80 else Paper.A5

    suspend fun render(
        context: Context,
        repo: Repo,
        doc: Document,
        paper: Paper = defaultPaper(doc.type)
    ): File {
        val title = docTypeLabel(doc.type)
        return when {
            doc.type in ITEMISED -> renderItemised(context, repo, doc, title, paper)
            doc.type in RECEIPTS -> ReceiptPdf.create(
                context, receiptData(context, repo, doc, title), paper
            )
            else -> DocumentPdf.create(context, doc)
        }
    }

    // ------------------------------------------------------------------
    // فاکتورِ ردیف‌دار
    // ------------------------------------------------------------------
    private suspend fun renderItemised(
        context: Context,
        repo: Repo,
        doc: Document,
        title: String,
        paper: Paper
    ): File {
        val saleRows = if (doc.type == "SALE") repo.saleLinesByCode(doc.refId) else emptyList()
        val discountTotal = saleRows.sumOf { it.discount }

        val lines = when (doc.type) {
            "SALE" -> saleRows.map {
                InvoiceLine(
                    // کدِ اختصاصیِ طرح، همان که در «اطلاعات پایه» وارد
                    // می‌شود (مثل DIP-12). پیش از این شمارهٔ ردیفِ دیتابیس
                    // چاپ می‌شد که بیرون از اپ هیچ معنایی نداشت.
                    // اگر طرح کد ندارد خالی می‌ماند — کدِ ساختگی روی
                    // فاکتوری که دستِ مشتری می‌رود بدتر از نداشتنِ کد است.
                    code = repo.designCodeFor(it.productName),
                    name = listOf(it.productName, it.size).filter { s -> s.isNotBlank() }
                        .joinToString(" • "),
                    qty = it.qty.toDouble(),
                    unit = "عدد",
                    unitPrice = it.unitPrice,
                    total = it.total
                )
            }
            else -> repo.purchaseItemsByCode(doc.refId).map {
                InvoiceLine(
                    code = it.id.fa(),
                    name = it.name,
                    qty = it.qty,
                    unit = it.unit,
                    unitPrice = it.unitPrice,
                    total = it.total
                )
            }
        }

        // ردیفی پیدا نشد — سندِ قدیمی یا فاکتوری که ردیف‌هایش پاک شده.
        // برگهٔ سادهٔ قبلی بهتر از برگهٔ خالی است.
        if (lines.isEmpty()) return DocumentPdf.create(context, doc)

        val isSale = doc.type == "SALE"
        val partyType = if (isSale) "CUSTOMER" else "SUPPLIER"
        val previousDue = repo.partyBalanceBefore(partyType, doc.partyName, doc.refId, doc.at)
        val phone = if (isSale) repo.customerByName(doc.partyName)?.phone.orEmpty() else ""

        val subtotal = lines.sumOf { it.total }
        // «پرداخت» از دفتر کل بیرون کشیده می‌شود نه از حدس: هرچه بعد از
        // این فاکتور از طلب کم شده، پرداخت شده است.
        //
        //   ماندهٔ فعلی = ماندهٔ قبلی + جمعِ فاکتور − پرداخت
        //
        // پس پرداخت همان چیزی است که این معادله را می‌بندد، و «مبلغ قابل
        // پرداخت» دقیقاً ماندهٔ دفتر می‌شود — نه عددی که جدا حساب شده و
        // می‌تواند با دفتر اختلاف پیدا کند.
        val currentDue = repo.partyBalanceUpTo(partyType, doc.partyName, doc.at)
        val paid = previousDue + subtotal - currentDue

        return LineInvoicePdf.create(
            context,
            InvoiceData(
                title = title,
                number = doc.number,
                at = doc.at,
                partyLabel = if (isSale) "خریدار" else "فروشنده",
                partyName = doc.partyName,
                partyPhone = phone,
                lines = lines,
                discount = discountTotal,
                paid = paid,
                previousDue = previousDue,
                note = doc.note
            ),
            paper
        )
    }

    // ------------------------------------------------------------------
    // رسیدِ پول
    // ------------------------------------------------------------------
    /** نوع‌هایی که پول از کارگاه بیرون می‌رود — بقیه پول به کارگاه می‌آید. */
    private val OUTGOING = setOf(
        "PAYMENT", "SUPPLIER_PAYMENT", "WAGE_RECEIPT", "SALARY_RECEIPT"
    )

    private suspend fun receiptData(
        context: Context,
        repo: Repo,
        doc: Document,
        title: String
    ): ReceiptData {
        val workshop = CompanyPrefs.name(context).ifBlank { "افغان‌جامه" }
        val outgoing = doc.type in OUTGOING

        // ماندهٔ حسابِ طرف بعد از این رسید — همان «طلب کلی» که در رسیدهای
        // کارگاه نوشته می‌شود.
        // همان نام‌هایی که `postLedger` واقعاً با آن‌ها ثبت می‌کند —
        // نامِ اشتباه یعنی «طلب کلی» بی‌سروصدا صفر چاپ می‌شود.
        val partyType = when (doc.type) {
            "SUPPLIER_PAYMENT" -> "SUPPLIER"
            "WAGE_RECEIPT" -> "TAILOR"
            "SALARY_RECEIPT" -> "EMPLOYEE"
            else -> "CUSTOMER"
        }
        val remaining = repo.partyBalanceUpTo(partyType, doc.partyName, doc.at)

        return ReceiptData(
            title = title,
            number = doc.number,
            at = doc.at,
            payer = if (outgoing) workshop else doc.partyName,
            payee = if (outgoing) doc.partyName else workshop,
            amount = doc.amount,
            remainingDue = remaining,
            note = doc.note
        )
    }
}
