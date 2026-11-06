package com.afghanjama

import com.afghanjama.data.CashPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * قاعدهٔ خروجِ پول از صندوق — صندوق نباید یک افغانی زیرِ صفر برود، و
 * نسیه و حسابِ مشتری به موجودیِ صندوق کار ندارند.
 */
class CashPolicyTest {

    @Test
    fun canSpend_allowsAmountBelowBalance() {
        assertTrue(CashPolicy.canSpend(balance = 1000, amount = 999))
    }

    @Test
    fun canSpend_allowsExactBalance() {
        assertTrue(CashPolicy.canSpend(balance = 1000, amount = 1000))
    }

    @Test
    fun canSpend_rejectsOneAfghaniOverBalance() {
        assertFalse(CashPolicy.canSpend(balance = 1000, amount = 1001))
    }

    @Test
    fun canSpend_rejectsAnyPositiveAmountFromEmptyOrNegativeCash() {
        assertFalse(CashPolicy.canSpend(balance = 0, amount = 1))
        assertFalse(CashPolicy.canSpend(balance = -50, amount = 1))
    }

    @Test
    fun canSpend_zeroOrNegativeAmountIsAlwaysAllowed() {
        assertTrue(CashPolicy.canSpend(balance = 0, amount = 0))
        assertTrue(CashPolicy.canSpend(balance = -50, amount = 0))
        assertTrue(CashPolicy.canSpend(balance = 10, amount = -5))
    }

    @Test
    fun isCashSource_creditAndCustomerDoNotTouchTheCashbox() {
        assertFalse(CashPolicy.isCashSource("CREDIT"))
        assertFalse(CashPolicy.isCashSource("CUSTOMER"))
    }

    @Test
    fun isCashSource_otherSourcesAreCash() {
        assertTrue(CashPolicy.isCashSource("CASH"))
        assertTrue(CashPolicy.isCashSource("PROFIT"))
        assertTrue(CashPolicy.isCashSource(""))
        // حساسِ به حروف: نام‌های ثبت‌شده همه بزرگ‌اند.
        assertTrue(CashPolicy.isCashSource("credit"))
    }

    @Test
    fun isInternalMove_matchesOnlyTheExactCategory() {
        assertTrue(CashPolicy.isInternalMove(CashPolicy.INTERNAL_MOVE))
        assertTrue(CashPolicy.isInternalMove("انتقال داخلی"))
        assertFalse(CashPolicy.isInternalMove("سایر"))
        assertFalse(CashPolicy.isInternalMove(""))
        assertFalse(CashPolicy.isInternalMove(" انتقال داخلی "))
    }

    @Test
    fun outflows_everyPathHasAGuardAndTellsTheUser() {
        assertTrue(CashPolicy.OUTFLOWS.isNotEmpty())
        for ((name, guard, reports) in CashPolicy.OUTFLOWS) {
            assertTrue("path $name has no guard", guard.isNotBlank())
            assertTrue("path $name does not report rejection", reports)
        }
    }

    @Test
    fun outflows_pathNamesAreUnique() {
        val names = CashPolicy.OUTFLOWS.map { it.first }
        assertEquals(names.size, names.toSet().size)
    }
}
