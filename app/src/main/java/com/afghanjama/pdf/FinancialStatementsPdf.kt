package com.afghanjama.pdf

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import androidx.core.content.res.ResourcesCompat
import com.afghanjama.R
import com.afghanjama.prefs.CompanyPrefs
import com.afghanjama.ui.format.PersianDate
import com.afghanjama.ui.format.afn
import com.afghanjama.ui.format.toPersianDigits
import com.afghanjama.ui.vm.BalanceSheet
import com.afghanjama.ui.vm.IncomeStatement
import com.afghanjama.ui.vm.StatementLine
import com.afghanjama.util.ShareUtil
import java.io.File

/**
 * صورت‌های مالیِ رسمیِ کارگاه در یک PDF (A4 راست‌به‌چپ، چندصفحه‌ای):
 * صورتِ سود و زیانِ دوره + ترازنامهٔ لحظه‌ای، هر دو از ژورنالِ دوطرفه.
 * قابلِ ارائه به شریک، بانک یا حسابدار.
 */
object FinancialStatementsPdf {

    private const val PAGE_W = 595
    private const val PAGE_H = 842
    private const val MARGIN = 40f
    private val CONTENT_W = (PAGE_W - 2 * MARGIN).toInt()

    private const val BRAND = 0xFF1F6E5C.toInt()
    private const val INK = 0xFF1B1C1A.toInt()
    private const val MUTED = 0xFF61605A.toInt()
    private const val LINE = 0xFFE1DFD8.toInt()
    private const val LOSS = 0xFFB3261E.toInt()

    fun create(
        context: Context,
        income: IncomeStatement,
        sheet: BalanceSheet,
        periodLabel: String
    ): File {
        val regular = ResourcesCompat.getFont(context, R.font.vazirmatn_regular) ?: Typeface.DEFAULT
        val bold = ResourcesCompat.getFont(context, R.font.vazirmatn_bold) ?: Typeface.DEFAULT_BOLD

        fun paint(size: Float, color: Int, tf: Typeface) = TextPaint().apply {
            isAntiAlias = true; textSize = size; this.color = color; typeface = tf
        }

        val titlePaint = paint(18f, Color.WHITE, bold)
        val headerSubPaint = paint(11f, Color.WHITE, regular)
        val sectionPaint = paint(13f, BRAND, bold)
        val rowPaint = paint(10f, INK, regular)
        val mutedPaint = paint(9f, MUTED, regular)
        val strongPaint = paint(11f, INK, bold)
        val lossPaint = paint(11f, LOSS, bold)
        val footerPaint = paint(9f, MUTED, regular)

        val doc = PdfDocument()
        var pageNo = 1
        var page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNo).create())
        var c = page.canvas
        var y: Float

        fun drawHeader(canvas: Canvas): Float {
            canvas.drawRect(0f, 0f, PAGE_W.toFloat(), 86f, Paint().apply { color = BRAND })
            val coName = CompanyPrefs.name(context).ifBlank { "AfghanJama — مدیریت کارگاه خیاطی" }
            canvas.drawRtl(coName, MARGIN, 22f, titlePaint, CONTENT_W)
            canvas.drawRtl("صورت‌های مالی — $periodLabel", MARGIN, 54f, headerSubPaint, CONTENT_W)
            return 104f
        }

        fun drawFooter(canvas: Canvas) {
            val footY = PAGE_H - 32f
            canvas.drawLine(
                MARGIN, footY - 10f, PAGE_W - MARGIN, footY - 10f,
                Paint().apply { color = LINE; strokeWidth = 0.8f }
            )
            val phone = CompanyPrefs.phone(context)
            canvas.drawRtl(
                "صفحهٔ $pageNo • ${PersianDate.long(System.currentTimeMillis())}" +
                    (if (phone.isNotBlank()) " • تلفن: $phone" else ""),
                MARGIN, footY, footerPaint, CONTENT_W
            )
        }

        y = drawHeader(c)

        fun ensureSpace(needed: Float) {
            if (y + needed > PAGE_H - 70f) {
                drawFooter(c)
                doc.finishPage(page)
                pageNo++
                page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNo).create())
                c = page.canvas
                y = drawHeader(c)
            }
        }

        fun rule() {
            c.drawLine(
                MARGIN, y, PAGE_W - MARGIN, y,
                Paint().apply { color = LINE; strokeWidth = 0.8f }
            )
            y += 10f
        }

        fun section(title: String) {
            ensureSpace(40f)
            c.drawRtl(title, MARGIN, y, sectionPaint, CONTENT_W)
            y += 22f
            rule()
        }

        fun row(label: String, amount: Long, strong: Boolean = false, negativeIsLoss: Boolean = false) {
            ensureSpace(20f)
            val tp = when {
                negativeIsLoss && amount < 0 -> lossPaint
                strong -> strongPaint
                else -> rowPaint
            }
            c.drawRtl(label, MARGIN, y, tp, (CONTENT_W * 0.62f).toInt())
            c.drawRtlEnd(amount.afn(), MARGIN, y, tp, CONTENT_W)
            y += if (strong) 20f else 16f
        }

        /** عنوانِ فرعی — مثلِ سطرها باید جا رزرو کند وگرنه پایینِ صفحه می‌افتد. */
        fun subHeader(title: String) {
            ensureSpace(24f)
            c.drawRtl(title, MARGIN, y, strongPaint, CONTENT_W)
            y += 18f
        }

        fun lines(list: List<StatementLine>, emptyText: String) {
            if (list.isEmpty()) {
                ensureSpace(18f)
                c.drawRtl(emptyText, MARGIN, y, mutedPaint, CONTENT_W)
                y += 16f
            } else {
                list.forEach { row("${it.label} (${it.code})", it.amount) }
            }
        }

        // ================= صورتِ سود و زیان =================
        section("صورتِ سود و زیان — $periodLabel")

        lines(income.revenues, "درآمدی در این دوره ثبت نشده است.")
        row("جمعِ درآمد", income.totalRevenue, strong = true)
        y += 4f
        row("کسر: بهای تمام‌شدهٔ فروش", income.cogs)
        rule()
        row("سودِ ناخالص", income.grossProfit, strong = true, negativeIsLoss = true)
        y += 8f

        subHeader("هزینه‌های عملیاتی")
        lines(income.expenses, "هزینه‌ای در این دوره ثبت نشده است.")
        row("جمعِ هزینه‌ها", income.totalExpense, strong = true)
        rule()
        row(
            if (income.netProfit >= 0) "سودِ خالصِ دوره" else "زیانِ خالصِ دوره",
            income.netProfit, strong = true, negativeIsLoss = true
        )
        if (income.totalRevenue > 0) {
            ensureSpace(18f)
            c.drawRtl(
                "حاشیهٔ سودِ خالص: ${income.marginPercent}٪".toPersianDigits(),
                MARGIN, y, mutedPaint, CONTENT_W
            )
            y += 18f
        }

        y += 14f

        // ================= ترازنامه =================
        section("ترازنامه — تا ${PersianDate.short(System.currentTimeMillis())}")

        subHeader("دارایی‌ها")
        lines(sheet.assets, "دارایی ثبت نشده است.")
        row("جمعِ دارایی‌ها", sheet.totalAssets, strong = true)
        y += 10f

        subHeader("بدهی‌ها")
        lines(sheet.liabilities, "بدهی ثبت نشده است.")
        row("جمعِ بدهی‌ها", sheet.totalLiabilities, strong = true)
        y += 10f

        subHeader("سرمایه")
        row("سرمایهٔ اولیه", sheet.capital)
        row("سودِ انباشته", sheet.retained, negativeIsLoss = true)
        row("جمعِ سرمایه", sheet.totalEquity, strong = true)
        rule()
        row("جمعِ بدهی‌ها و سرمایه", sheet.totalLiabilities + sheet.totalEquity, strong = true)

        ensureSpace(24f)
        c.drawRtl(
            if (sheet.balanced) "✓ ترازنامه متوازن است (دارایی = بدهی + سرمایه)"
            else "⚠ ترازنامه متوازن نیست — دفترها را بررسی کنید",
            MARGIN, y, if (sheet.balanced) strongPaint else lossPaint, CONTENT_W
        )
        y += 22f

        drawFooter(c)
        doc.finishPage(page)

        val file = File(ShareUtil.sharedDir(context), "صورت‌های-مالی.pdf")
        file.outputStream().use { doc.writeTo(it) }
        doc.close()
        return file
    }

    // ---------- کمکی‌های رسم راست‌به‌چپ ----------

    private fun Canvas.drawRtl(text: String, x: Float, top: Float, tp: TextPaint, width: Int) {
        val layout = StaticLayout.Builder
            .obtain(text, 0, text.length, tp, width)
            .setTextDirection(TextDirectionHeuristics.RTL)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .build()
        save(); translate(x, top); layout.draw(this); restore()
    }

    /** متن در سمتِ مقابل سطر (چپ در چیدمان RTL). */
    private fun Canvas.drawRtlEnd(text: String, x: Float, top: Float, tp: TextPaint, width: Int) {
        val layout = StaticLayout.Builder
            .obtain(text, 0, text.length, TextPaint(tp), width)
            .setTextDirection(TextDirectionHeuristics.RTL)
            .setAlignment(Layout.Alignment.ALIGN_OPPOSITE)
            .build()
        save(); translate(x, top); layout.draw(this); restore()
    }
}
