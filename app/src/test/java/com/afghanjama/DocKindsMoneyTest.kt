package com.afghanjama

import com.afghanjama.pdf.DocKinds
import com.afghanjama.pdf.Paper
import com.afghanjama.ui.format.afn
import com.afghanjama.ui.format.afnBare
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * نوعِ سند (رسید یا فاکتور) و قالبِ مبلغِ افغانی.
 *
 * هر دو روی کاغذِ چاپی می‌روند: رسیدِ پول باید روی رول ۸ سانتی برود و
 * فاکتور روی A5، و مبلغ با ارقام فارسی و جداکنندهٔ هزارگانِ فارسی نوشته
 * شود. ارقام با escape نوشته شده‌اند (U+06F0..U+06F9) تا ویرایشگر
 * چیزی را عوض نکند.
 */
class DocKindsMoneyTest {

    // ---------- DocKinds ----------

    @Test
    fun `every money receipt type is a receipt`() {
        for (t in listOf(
            "PAYMENT", "RECEIPT", "CUSTOMER_RECEIPT", "SUPPLIER_PAYMENT",
            "WAGE_RECEIPT", "SALARY_RECEIPT"
        )) {
            assertTrue(t, DocKinds.isReceipt(t))
        }
    }

    @Test
    fun `sale and purchase are invoices not receipts`() {
        assertFalse(DocKinds.isReceipt("SALE"))
        assertFalse(DocKinds.isReceipt("PURCHASE"))
    }

    @Test
    fun `unknown and wrongly cased types are not receipts`() {
        assertFalse(DocKinds.isReceipt(""))
        assertFalse(DocKinds.isReceipt("payment"))
        assertFalse(DocKinds.isReceipt("OTHER"))
    }

    @Test
    fun `itemised and receipt sets do not overlap`() {
        assertTrue(DocKinds.ITEMISED.intersect(DocKinds.RECEIPTS).isEmpty())
    }

    @Test
    fun `receipts default to the 80mm roll and the rest to A5`() {
        assertSame(Paper.ROLL80, DocKinds.defaultPaper("PAYMENT"))
        assertSame(Paper.ROLL80, DocKinds.defaultPaper("SALARY_RECEIPT"))
        assertSame(Paper.A5, DocKinds.defaultPaper("SALE"))
        assertSame(Paper.A5, DocKinds.defaultPaper("PURCHASE"))
        assertSame(Paper.A5, DocKinds.defaultPaper("UNKNOWN"))
    }

    // ---------- Money ----------

    @Test
    fun `afn groups thousands with Persian digits and the afghani sign`() {
        // 12,500 ؋  ->  ۱۲٬۵۰۰ ؋
        assertEquals("۱۲٬۵۰۰ ؋", 12_500L.afn())
    }

    @Test
    fun `afn of small and zero amounts has no separator`() {
        assertEquals("۰ ؋", 0L.afn())
        assertEquals("۹۹۹ ؋", 999L.afn())
    }

    @Test
    fun `afn of a million has two separators`() {
        // 1,000,000 -> ۱٬۰۰۰٬۰۰۰
        assertEquals(
            "۱٬۰۰۰٬۰۰۰ ؋",
            1_000_000L.afn()
        )
    }

    @Test
    fun `afnBare is the same number without the sign`() {
        assertEquals("۱۲٬۵۰۰", 12_500L.afnBare())
        assertEquals(12_500L.afn(), "${12_500L.afnBare()} ؋")
    }

    @Test
    fun `negative amounts keep the minus sign in front`() {
        // withThousands keeps the sign; digits are still converted
        assertEquals("-۱۲٬۵۰۰", (-12_500L).afnBare())
    }
}
