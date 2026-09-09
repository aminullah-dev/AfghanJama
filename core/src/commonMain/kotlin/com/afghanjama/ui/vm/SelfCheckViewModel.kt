package com.afghanjama.ui.vm

import com.afghanjama.util.nowMillis
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.afghanjama.selftest.CheckResult
import com.afghanjama.selftest.CheckStatus
import com.afghanjama.selftest.checkCustomerLedger
import com.afghanjama.selftest.checkDiscountMath
import com.afghanjama.selftest.checkInvoiceTotals
import com.afghanjama.selftest.checkMoneySplit
import com.afghanjama.selftest.checkMultiLineInvoice
import com.afghanjama.selftest.checkOrderCycle
import com.afghanjama.selftest.checkSalaryAdvance
import com.afghanjama.selftest.checkShortage
import com.afghanjama.selftest.checkStockValuation
import com.afghanjama.selftest.checkTailorAttribution
import com.afghanjama.selftest.checkWorkSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SelfCheckUi(
    val running: Boolean = false,
    val results: List<CheckResult> = emptyList(),
    val ranAt: Long = 0L
) {
    val passed: Int get() = results.count { it.status == CheckStatus.PASS }
    val failed: Int get() = results.count { it.status == CheckStatus.FAIL }
    val allGood: Boolean get() = results.isNotEmpty() && failed == 0
}

/**
 * بررسی‌هایی که فقط ریاضیِ پول را می‌سنجند — بی هیچ دفتری.
 *
 * فرقش با [SelfTestViewModel]ِ اندروید این است که آن دفترِ واقعیِ کارگاه
 * را هم وارسی می‌کند و برای همین `Repo` می‌خواهد. این یکی هیچ داده‌ای
 * لازم ندارد، پس هر جا که کاتلین اجرا شود کار می‌کند.
 *
 * **دو پیاده‌سازی نیست.** هر دو دقیقاً همان تابع‌های `check*` را از
 * `:core` صدا می‌زنند؛ این فقط زیرمجموعه‌ای است که به دیتابیس دست
 * نمی‌زند. اگر روزی این دو جواب‌های متفاوتی بدهند، یعنی چیزی در
 * `:core` شکسته — نه اینکه دو نسخه از یک قاعده داریم.
 *
 * نسخهٔ ویندوز تا وقتی دیتابیس ندارد از همین استفاده می‌کند؛ با آمدنِ
 * دفتر روی پی‌سی، به همان [SelfTestViewModel]ِ کامل می‌رود.
 */
class SelfCheckViewModel : ViewModel() {

    private val _ui = MutableStateFlow(SelfCheckUi())
    val ui: StateFlow<SelfCheckUi> = _ui.asStateFlow()

    init {
        run()
    }

    fun run() = viewModelScope.launch {
        _ui.value = SelfCheckUi(running = true)
        // روی رشتهٔ پس‌زمینه، تا پنجره در حینِ اجرا یخ نزند.
        val results = withContext(Dispatchers.Default) { pureChecks() }
        _ui.value = SelfCheckUi(
            running = false,
            results = results,
            ranAt = nowMillis()
        )
    }
}

/**
 * همان یازده بررسی، یک‌جا.
 *
 * بیرون از کلاس است تا هر جای دیگری هم (آزمون، ابزارِ خط فرمان) بتواند
 * بی ساختنِ ViewModel صدایش بزند.
 */
fun pureChecks(): List<CheckResult> =
    checkMoneySplit() +
        checkCustomerLedger() +
        checkTailorAttribution() +
        checkMultiLineInvoice() +
        checkStockValuation() +
        checkInvoiceTotals() +
        checkShortage() +
        checkDiscountMath() +
        checkOrderCycle() +
        checkSalaryAdvance() +
        checkWorkSummary()
