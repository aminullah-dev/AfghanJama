package com.afghanjama

import com.afghanjama.data.entities.Accounts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * دفترِ حساب‌های کل (Accounts): کدها یکتا، کلاسِ هر حساب از رقمِ اولِ کد،
 * هر کد برچسبِ خودش را دارد و هر صندوقِ نقد به حسابِ درستش می‌رود.
 */
class AccountsTest {

    private val all = listOf(
        Accounts.CASH, Accounts.PROFIT_BOX, Accounts.BANK, Accounts.RECEIVABLE,
        Accounts.STAFF_ADVANCE, Accounts.MATERIALS, Accounts.WIP, Accounts.FINISHED,
        Accounts.PAYABLE, Accounts.WAGES_PAYABLE, Accounts.CUSTOMER_PREPAY,
        Accounts.EQUITY, Accounts.SALES, Accounts.OTHER_INCOME, Accounts.COGS,
        Accounts.EXPENSES,
    )

    @Test
    fun `codes are unique`() {
        assertEquals(all.size, all.toSet().size)
    }

    @Test
    fun `class digit follows the documented scheme`() {
        listOf(Accounts.CASH, Accounts.PROFIT_BOX, Accounts.BANK, Accounts.RECEIVABLE,
            Accounts.STAFF_ADVANCE, Accounts.MATERIALS, Accounts.WIP, Accounts.FINISHED)
            .forEach { assertTrue(it, it.startsWith("1")) }
        listOf(Accounts.PAYABLE, Accounts.WAGES_PAYABLE, Accounts.CUSTOMER_PREPAY)
            .forEach { assertTrue(it, it.startsWith("2")) }
        assertTrue(Accounts.EQUITY.startsWith("3"))
        listOf(Accounts.SALES, Accounts.OTHER_INCOME).forEach { assertTrue(it, it.startsWith("4")) }
        listOf(Accounts.COGS, Accounts.EXPENSES).forEach { assertTrue(it, it.startsWith("5")) }
    }

    @Test
    fun `every known code has its own label, not the raw code`() {
        val labels = all.map { Accounts.label(it) }
        all.forEach { assertNotEquals(it, Accounts.label(it)) }
        assertEquals(all.size, labels.toSet().size)
    }

    @Test
    fun `label of a few well known accounts`() {
        assertEquals("صندوق", Accounts.label(Accounts.CASH))
        assertEquals("بانک", Accounts.label(Accounts.BANK))
        assertEquals("سرمایه", Accounts.label(Accounts.EQUITY))
    }

    @Test
    fun `unknown code is shown as is`() {
        assertEquals("9999", Accounts.label("9999"))
        assertEquals("", Accounts.label(""))
    }

    @Test
    fun `cash box source maps to its account`() {
        assertEquals(Accounts.BANK, Accounts.box("BANK"))
        assertEquals(Accounts.PROFIT_BOX, Accounts.box("PROFIT"))
        assertEquals(Accounts.CASH, Accounts.box("CASH"))
    }

    @Test
    fun `unknown or wrongly cased source falls back to the cash box`() {
        assertEquals(Accounts.CASH, Accounts.box(""))
        assertEquals(Accounts.CASH, Accounts.box("bank"))
        assertEquals(Accounts.CASH, Accounts.box("CREDIT"))
    }
}
