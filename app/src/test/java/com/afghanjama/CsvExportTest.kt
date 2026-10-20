package com.afghanjama

import com.afghanjama.data.CsvExport
import com.afghanjama.data.entities.Order
import com.afghanjama.data.entities.Transaction
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.TimeZone

/**
 * خروجیِ اکسل (CSV) — همان متن برای گوشی و کامپیوتر.
 *
 * اکسل فایل را با ویرگول و گیومه می‌بُرد. نامی که ویرگول یا گیومه یا خطِ
 * تازه دارد، اگر درست در گیومه نرود، ستون‌های بعدی را جابه‌جا می‌کند و
 * کارگاه عددِ یک سفارش را زیرِ ستونِ دیگری می‌بیند.
 */
class CsvExportTest {

    private lateinit var savedZone: TimeZone

    @Before
    fun kabul() {
        savedZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Kabul"))
    }

    @After
    fun restore() {
        TimeZone.setDefault(savedZone)
    }

    /** ۱ حمل ۱۴۰۴، ساعتِ ۰۰:۳۰ کابل = ۲۰ مارچ ۲۰۲۵، ۲۰:۰۰ گرینویچ. */
    private val nowruz1404 = 1742500800000L

    private fun order(name: String, phone: String = "0700123456", tailor: String? = "[T1] احمد") = Order(
        orderCode = "AJ-1404-0001",
        shortCode = "0001",
        designTitle = "پیراهن تنبان",
        qty = 2,
        fabricType = "کتان",
        fabricColor = "سفید",
        size = "L",
        fabricUnit = "متر",
        fabricAmount = 4.5,
        fabricPrice = 1200,
        workCost = 800,
        agreedPrice = 3500,
        customerName = name,
        customerPhone = phone,
        status = "SEWING",
        assignedTailor = tailor,
        createdAt = nowruz1404,
    )

    @Test
    fun `a plain cell is quoted`() {
        assertEquals("\"کتان\"", CsvExport.cell("کتان"))
    }

    @Test
    fun `an inner quote is doubled`() {
        assertEquals("\"پیراهنِ \"\"ویژه\"\"\"", CsvExport.cell("پیراهنِ \"ویژه\""))
    }

    @Test
    fun `a comma or a newline stays inside the cell`() {
        assertEquals("\"کابل، کارته ۳, سرک ۵\"", CsvExport.cell("کابل، کارته ۳, سرک ۵"))
        assertEquals("\"سطر اول\nسطر دوم\"", CsvExport.cell("سطر اول\nسطر دوم"))
        assertEquals("\"a\",\"b,c\",\"\"", CsvExport.row(listOf("a", "b,c", "")))
    }

    @Test
    fun `the orders header is the Dari header of 1-9`() {
        val first = CsvExport.orders(emptyList()).lines().first()
        assertEquals(CsvExport.row(CsvExport.ORDER_HEADER), first)
        assertTrue(first.startsWith("\"کد سفارش\",\"کد کوتاه\",\"طرح\""))
        assertTrue(first.endsWith("\"وضعیت\",\"تاریخ ایجاد\""))
        assertEquals(18, CsvExport.ORDER_HEADER.size)
    }

    @Test
    fun `an order row carries Dari text and a Persian date with Latin digits`() {
        val text = CsvExport.orders(listOf(order("محمد، پسرِ \"کریم\"")))
        val lines = text.lines().filter { it.isNotEmpty() }
        assertEquals(2, lines.size)
        val row = lines[1]
        assertTrue(row, row.contains("\"محمد، پسرِ \"\"کریم\"\"\""))
        assertTrue(row, row.contains("\"پیراهن تنبان\""))
        assertTrue(row, row.contains("\"4.5\""))
        // تاریخِ هجری شمسی با رقمِ لاتین، تا اکسل مرتبش کند.
        assertTrue(row, row.endsWith("\"SEWING\",\"1404/01/01 00:30\""))
    }

    @Test
    fun `a missing tailor is an empty cell, not the word null`() {
        val row = CsvExport.orderCells(order("مریم", tailor = null))
        assertEquals("", row[15])
        assertTrue(!CsvExport.orders(listOf(order("مریم", tailor = null))).contains("null"))
    }

    @Test
    fun `every order row has as many cells as the header`() {
        assertEquals(CsvExport.ORDER_HEADER.size, CsvExport.orderCells(order("مریم")).size)
    }

    @Test
    fun `transactions say receive or pay in Dari`() {
        val txIn = Transaction(type = "IN", source = "WALLET", amount = 5000, note = "پیش‌پرداختِ سفارش", createdAt = nowruz1404)
        val txOut = Transaction(type = "OUT", source = "BANK", amount = 250, category = "برق و آب", createdAt = nowruz1404)
        val lines = CsvExport.transactions(listOf(txIn, txOut)).lines().filter { it.isNotEmpty() }
        assertEquals(CsvExport.row(CsvExport.TX_HEADER), lines[0])
        assertEquals(
            "\"دریافت\",\"WALLET\",\"5000\",\"\",\"پیش‌پرداختِ سفارش\",\"1404/01/01 00:30\"",
            lines[1]
        )
        assertEquals(
            "\"پرداخت\",\"BANK\",\"250\",\"برق و آب\",\"\",\"1404/01/01 00:30\"",
            lines[2]
        )
    }
}
