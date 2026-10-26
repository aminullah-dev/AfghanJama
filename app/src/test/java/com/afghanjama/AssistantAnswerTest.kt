package com.afghanjama

import com.afghanjama.data.BreakEven
import com.afghanjama.data.PartyProfit
import com.afghanjama.ui.vm.AssistantData
import com.afghanjama.ui.vm.answerOffline
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * پاسخِ آفلاینِ دستیار — تطبیقِ کلیدواژه روی دفترِ محلی، بی اینترنت.
 */
class AssistantAnswerTest {

    private fun row(name: String, profit: Long) =
        PartyProfit.Row(name = name, count = 2, qty = 3, revenue = profit + 100, cost = 100)

    private val data = AssistantData(
        customers = listOf(row("بوتیک آرزو", 73_200)),
        tailors = listOf(row("احمد نوری", 50_360)),
        be = BreakEven.assess(BreakEven.Inputs(100_000, 40, 0, 120_000)),  // past break-even
        sales30 = 60_000,
    )

    @Test
    fun `asks about the customer`() {
        assertTrue(answerOffline("پرسودترین مشتری کیست؟", data)!!.contains("بوتیک آرزو"))
    }

    @Test
    fun `asks about the tailor`() {
        assertTrue(answerOffline("بهترین خیاط کدام است؟", data)!!.contains("احمد نوری"))
    }

    @Test
    fun `asks about break-even, past it`() {
        assertTrue(answerOffline("سربه‌سر شده‌ام؟", data)!!.contains("بله"))
    }

    @Test
    fun `arabic ya and kaf still match`() {
        // «مشتري» با ي و «خياط» با ي عربی باید بگیرند.
        assertTrue(answerOffline("مشتري", data)!!.contains("بوتیک آرزو"))
        assertTrue(answerOffline("خياط", data)!!.contains("احمد"))
    }

    @Test
    fun `an unknown question returns null`() {
        assertNull(answerOffline("هوا چطور است؟", data))
    }

    @Test
    fun `profit question sums the order profit`() {
        val ans = answerOffline("این بازه چقدر سود کردم؟", data)!!
        assertTrue(ans.contains("سود"))
    }

    private val rich = data.copy(cash = 586_300, atRiskCount = 2, lowMaterials = 3, readyPieces = 10)

    @Test
    fun `asks about cash`() {
        assertTrue(answerOffline("پولِ نقد چقدر دارم؟", rich)!!.contains("۵۸۶"))
    }

    @Test
    fun `asks about late orders`() {
        assertTrue(answerOffline("کدام سفارش‌ها دیر می‌شود؟", rich)!!.contains("۲"))
        assertTrue(answerOffline("دیرکرد", data)!!.contains("هیچ سفارشی"))
    }

    @Test
    fun `asks about low materials`() {
        assertTrue(answerOffline("کدام مواد رو به اتمام است؟", rich)!!.contains("۳"))
    }

    @Test
    fun `asks about ready stock`() {
        assertTrue(answerOffline("چقدر آمادهٔ فروش داری؟", rich)!!.contains("۱۰"))
    }
}
