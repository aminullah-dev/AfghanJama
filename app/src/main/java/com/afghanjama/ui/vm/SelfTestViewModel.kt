package com.afghanjama.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.afghanjama.data.entities.Accounts
import com.afghanjama.data.repo.Repo
import com.afghanjama.selftest.CheckResult
import com.afghanjama.selftest.CheckSink
import com.afghanjama.selftest.CheckStatus
import com.afghanjama.selftest.checkCustomerLedger
import com.afghanjama.selftest.checkJalali
import com.afghanjama.selftest.checkMoneySplit
import com.afghanjama.selftest.checkMultiLineInvoice
import com.afghanjama.selftest.checkBreakSchedule
import com.afghanjama.selftest.checkStockValuation
import com.afghanjama.selftest.checkTailorAttribution
import com.afghanjama.ui.format.PersianDate
import com.afghanjama.work.BreakReminderWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SelfTestUi(
    val running: Boolean = false,
    val results: List<CheckResult> = emptyList(),
    val ranAt: Long = 0L
) {
    val passed: Int get() = results.count { it.status == CheckStatus.PASS }
    val failed: Int get() = results.count { it.status == CheckStatus.FAIL }
    val skipped: Int get() = results.count { it.status == CheckStatus.SKIP }
    val allGood: Boolean get() = results.isNotEmpty() && failed == 0
}

/**
 * خودآزماییِ اپ روی همین گوشی و همین داده.
 *
 * دو دسته بررسی انجام می‌شود:
 *  - ریاضیِ خالص (تفکیکِ پول، دفترِ مشتری، انتسابِ برگشت، تقویم) که به
 *    داده کاری ندارد و همیشه یک جواب دارد.
 *  - وارسیِ دادهٔ واقعیِ کارگاه، **فقط خواندنی**. هیچ سفارش، فروش یا سندِ
 *    آزمایشی ساخته نمی‌شود؛ اجرا کردنش روی دفترِ واقعی بی‌خطر است.
 */
class SelfTestViewModel(private val repo: Repo) : ViewModel() {

    private val _ui = MutableStateFlow(SelfTestUi())
    val ui: StateFlow<SelfTestUi> = _ui.asStateFlow()

    fun run() = viewModelScope.launch {
        _ui.value = SelfTestUi(running = true)
        val results = withContext(Dispatchers.IO) {
            buildList {
                addAll(checkMoneySplit())
                addAll(checkCustomerLedger())
                addAll(checkTailorAttribution())
                addAll(checkMultiLineInvoice())
                addAll(checkStockValuation())
                addAll(
                    checkBreakSchedule { h, m, now ->
                        BreakReminderWorker.delayUntilNext(h, m, now)
                    }
                )
                addAll(
                    checkJalali(
                        toJalali = { millis ->
                            val j = PersianDate.todayJalali(millis)
                            Triple(j[0], j[1], j[2])
                        },
                        atMillis = { y, m, d -> PersianDate.startOfJalaliDay(y, m, d) }
                    )
                )
                addAll(auditLiveData())
            }
        }
        _ui.value = SelfTestUi(running = false, results = results, ranAt = System.currentTimeMillis())
    }

    /** وارسیِ دفترِ واقعی — هیچ نوشتنی در کار نیست. */
    private suspend fun auditLiveData(): List<CheckResult> {
        val s = CheckSink("دفترِ واقعیِ کارگاه")

        // ۱) هر سندِ ژورنال باید خودش تراز باشد
        val totals = repo.auditEntryTotals()
        if (totals.isEmpty()) {
            s.skip("هر سند تراز است", "هنوز سندی ثبت نشده")
        } else {
            val broken = totals.filter { it.debit != it.credit }
            s.isTrue(
                "هر سند تراز است",
                broken.isEmpty(),
                "${broken.size} سندِ ناتراز، اولی #${broken.firstOrNull()?.entryId}: " +
                    "بدهکار ${broken.firstOrNull()?.debit} ≠ بستانکار ${broken.firstOrNull()?.credit}",
                "${totals.size} سند"
            )
        }

        // ۲) کلِ دفتر تراز است
        val balances = repo.auditAccountBalances()
        val td = balances.sumOf { it.debit }
        val tc = balances.sumOf { it.credit }
        if (balances.isEmpty()) s.skip("کلِ دفتر تراز است", "هنوز حسابی حرکت نکرده")
        else s.eq("کلِ دفتر تراز است", td, tc, "بدهکار و بستانکار هر دو ${td} ؋")

        // ۳) حساب‌هایی که نباید در سمتِ اشتباه بنشینند
        val byAccount = balances.associateBy { it.account }
        val assets = listOf(
            Accounts.CASH to "صندوق",
            Accounts.RECEIVABLE to "طلب از مشتریان",
            Accounts.MATERIALS to "موجودی مواد",
            Accounts.FINISHED to "موجودی محصول",
            Accounts.WIP to "کار در جریان"
        )
        val wrongSide = assets.filter { (code, _) -> (byAccount[code]?.net ?: 0L) < 0L }
        s.isTrue(
            "هیچ دارایی ماندهٔ منفی ندارد",
            wrongSide.isEmpty(),
            wrongSide.joinToString("، ") { (code, label) -> "$label: ${byAccount[code]?.net} ؋" },
            "${assets.size} حساب بررسی شد"
        )

        // ۴) سطرهای «اعمال بیعانه» باید خنثی باشند (اشکالِ اصلاح‌شده)
        val ledger = repo.auditLedgerEntries()
        val prepayRows = ledger.filter { it.refType == "PREPAY_APPLIED" }
        if (prepayRows.isEmpty()) {
            s.skip("اعمالِ بیعانه ماندهٔ حساب را تکان نمی‌دهد", "هنوز بیعانه‌ای اعمال نشده")
        } else {
            val lopsided = prepayRows.filter { it.debit != it.credit }
            s.isTrue(
                "اعمالِ بیعانه ماندهٔ حساب را تکان نمی‌دهد",
                lopsided.isEmpty(),
                "${lopsided.size} سطر یک‌طرفه مانده — مشتری دو بار بیعانه بدهکار شده. " +
                    "اگر این پیغام را می‌بینید یعنی مهاجرت اجرا نشده؛ خبر بدهید.",
                "${prepayRows.size} سطر"
            )
        }

        // ۵) هیچ سطرِ دفترِ کل نباید هم‌زمان بدهکار و بستانکارِ نابرابر و منفی داشته باشد
        val negative = ledger.filter { it.debit < 0 || it.credit < 0 }
        s.isTrue(
            "هیچ سطرِ دفتر مبلغِ منفی ندارد",
            negative.isEmpty(),
            "${negative.size} سطرِ منفی، اولی: ${negative.firstOrNull()?.refType}",
            "${ledger.size} سطر"
        )

        // ۶) حسابِ «موجودی محصول» باید دقیقاً برابرِ ارزشِ واقعیِ انبار باشد
        val stockValue = repo.auditFinishedStock().sumOf { it.totalValue }
        val finishedAccount = byAccount[Accounts.FINISHED]?.net ?: 0L
        if (finishedAccount == 0L && stockValue == 0L) {
            s.skip("حسابِ موجودی محصول = ارزشِ واقعیِ انبار", "هنوز محصولی وارد انبار نشده")
        } else {
            s.eq(
                "حسابِ موجودی محصول = ارزشِ واقعیِ انبار",
                stockValue, finishedAccount,
                "هر دو ${stockValue} ؋"
            )
        }

        // ۷) موجودیِ انبارها منفی نشده باشد
        val negFinished = repo.auditFinishedStock().filter { it.qty < 0 }
        s.isTrue(
            "موجودیِ محصول منفی نیست",
            negFinished.isEmpty(),
            negFinished.joinToString("، ") { "${it.name}: ${it.qty}" }
        )
        val negMaterial = repo.auditMaterialStock().filter { it.amount < 0.0 }
        s.isTrue(
            "موجودیِ مواد منفی نیست",
            negMaterial.isEmpty(),
            negMaterial.joinToString("، ") { "${it.name}: ${it.amount}" }
        )

        return s.results
    }
}
