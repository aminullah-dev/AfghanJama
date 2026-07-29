package com.afghanjama

import com.afghanjama.pdf.Paper
import com.afghanjama.pdf.columnWidths
import com.afghanjama.pdf.invoiceColumns
import com.afghanjama.selftest.CheckResult
import com.afghanjama.selftest.CheckStatus
import com.afghanjama.selftest.PaperSpec
import com.afghanjama.selftest.checkBreakSchedule
import com.afghanjama.selftest.checkCustomerLedger
import com.afghanjama.selftest.checkDiscountMath
import com.afghanjama.selftest.checkInvoiceTotals
import com.afghanjama.selftest.checkJalali
import com.afghanjama.selftest.checkMoneySplit
import com.afghanjama.selftest.checkMultiLineInvoice
import com.afghanjama.selftest.checkPaperGeometry
import com.afghanjama.selftest.checkShortage
import com.afghanjama.selftest.checkStockValuation
import com.afghanjama.selftest.checkTailorAttribution
import com.afghanjama.ui.format.PersianDate
import com.afghanjama.work.BreakSchedule
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ریاضیِ پول، روی هر push.
 *
 * ادعاها اینجا دوباره نوشته نمی‌شوند: همان توابعی صدا زده می‌شوند که
 * دکمهٔ خودآزماییِ داخلِ اپ هم صدا می‌زند. برای همین این دو هرگز از هم
 * جدا نمی‌افتند — چیزی که یک بار در همین پروژه اتفاق افتاد، وقتی یک
 * ادعای قدیمی دربارهٔ فروشِ بیش از موجودی با کدِ تازه نمی‌خواند.
 *
 * فقط بررسی‌های **خالص** اینجا اجرا می‌شوند. وارسیِ دفترِ واقعی به گوشی
 * و دادهٔ کارگاه نیاز دارد و همان‌جا در اپ می‌ماند.
 */
class SelfTestJvmTest {

    private fun pureChecks(): List<CheckResult> = buildList {
        addAll(checkMoneySplit())
        addAll(checkCustomerLedger())
        addAll(checkTailorAttribution())
        addAll(checkMultiLineInvoice())
        addAll(checkStockValuation())
        addAll(checkInvoiceTotals())
        addAll(checkShortage())
        addAll(checkDiscountMath())
        addAll(
            checkPaperGeometry(
                Paper.ALL.map { p ->
                    val cols = invoiceColumns(p)
                    PaperSpec(
                        label = p.label,
                        contentW = p.contentW,
                        cellTextSize = p.cellTextSize,
                        columnTitles = cols.titles,
                        columnWidths = columnWidths(p, cols.weights)
                    )
                }
            )
        )
        addAll(checkBreakSchedule { h, m, now -> BreakSchedule.delayUntilNext(h, m, now) })
        addAll(
            checkJalali(
                toJalali = { millis ->
                    val j = PersianDate.todayJalali(millis)
                    Triple(j[0], j[1], j[2])
                },
                atMillis = { y, m, d -> PersianDate.startOfJalaliDay(y, m, d) }
            )
        )
    }

    @Test
    fun `no money check fails`() {
        val results = pureChecks()
        val failed = results.filter { it.status == CheckStatus.FAIL }
        assertTrue(
            "\n" + failed.joinToString("\n") { "✗ ${it.group} — ${it.name}: ${it.detail}" },
            failed.isEmpty()
        )
    }

    /**
     * اگر روزی همهٔ بررسی‌ها بی‌سروصدا حذف یا SKIP شوند، تستِ بالا هم سبز
     * می‌ماند و هیچ‌کس نمی‌فهمد. این یکی جلوی آن سکوت را می‌گیرد.
     */
    @Test
    fun `checks actually ran`() {
        val results = pureChecks()
        val passed = results.count { it.status == CheckStatus.PASS }
        assertTrue(
            "انتظار دست‌کم ۴۰ بررسیِ قبول‌شده داشتیم ولی $passed تا اجرا شد " +
                "(جمعِ نتیجه‌ها: ${results.size})",
            passed >= 40
        )
    }
}
