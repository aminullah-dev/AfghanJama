package com.afghanjama.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.afghanjama.data.dao.PartyBalance
import com.afghanjama.data.repo.Repo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** یک طرفِ قابلِ انتخاب برای پرداخت/دریافت، همراه با ماندهٔ فعلی‌اش. */
data class PayeeOption(
    val type: String,
    val name: String,
    /** ماندهٔ دفتر کل: مثبت = به ما بدهکار، منفی = ما به او بدهکاریم، ۰ = تسویه. */
    val net: Long
)

data class MoneyMoveUi(
    val payees: List<PayeeOption> = emptyList(),
    val wallet: Long = 0,
    val bank: Long = 0,
    val message: String? = null,
    val isError: Boolean = false,
    val done: Boolean = false
) {
    fun of(type: String) = payees.filter { it.type == type }.sortedBy { it.name }
}

/** برچسبِ فارسیِ نوعِ طرف — ترتیبِ نمایش در صفحه هم از همین می‌آید. */
val payeeTypes: List<Pair<String, String>> = listOf(
    "EMPLOYEE" to "کارمند",
    "TAILOR" to "خیاط",
    "INSPECTOR" to "ناظر",
    "SUPPLIER" to "تأمین‌کننده",
    "CUSTOMER" to "مشتری"
)

/**
 * پرداخت و دریافتِ سریع از داشبورد — به هر کسی: کارمند، خیاط، ناظر،
 * تأمین‌کنندهٔ مواد یا موادِ غذایی، مشتری، یا هر نامِ دلخواهِ دیگر.
 *
 * پشتِ صحنه همان قیفِ آزموده‌شدهٔ [Repo.recordManualLedger] است، پس
 * صندوق، دفتر کل، ژورنال، رسید و لاگ همه هماهنگ به‌روز می‌شوند و
 * پرداختِ بیشتر از موجودیِ صندوق رد می‌شود.
 */
class MoneyMoveViewModel(private val repo: Repo) : ViewModel() {

    /** جلوی ثبتِ دوباره با دو ضربهٔ سریع را می‌گیرد. */
    val busy = Busy()

    private val _state = MutableStateFlow(MoneyMoveUi())

    private val people = combine(
        repo.observeLedgerBalances(),
        repo.observeTailors(),
        repo.observeInspectors(),
        repo.observeStaff(),
        repo.observeCustomers()
    ) { balances, tailors, inspectors, staff, customers ->
        val byKey = balances.associateBy { it.type to it.name }
        fun net(type: String, name: String): Long =
            byKey[type to name]?.net ?: 0L

        // هر کسی که در دفترچهٔ پایه هست + هر طرفی که قبلاً گردش داشته
        val fromMaster =
            staff.map { PayeeOption("EMPLOYEE", it.name, net("EMPLOYEE", it.name)) } +
                tailors.map {
                    val label = "[${it.code}] ${it.name}"
                    PayeeOption("TAILOR", label, net("TAILOR", label))
                } +
                inspectors.map {
                    val label = "[${it.code}] ${it.name}"
                    PayeeOption("INSPECTOR", label, net("INSPECTOR", label))
                } +
                customers.map { PayeeOption("CUSTOMER", it.name, net("CUSTOMER", it.name)) }

        val fromLedger = balances.map { b: PartyBalance ->
            PayeeOption(b.type, b.name, b.net)
        }

        (fromMaster + fromLedger)
            .filter { it.name.isNotBlank() }
            .distinctBy { it.type to it.name }
    }

    val ui: StateFlow<MoneyMoveUi> = combine(
        people,
        repo.observeWalletBalance(),
        repo.observeBankBalance(),
        _state
    ) { payees, wallet, bank, s ->
        s.copy(payees = payees, wallet = wallet, bank = bank)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MoneyMoveUi())

    fun clearMessage() = _state.update { it.copy(message = null, isError = false, done = false) }

    private inline fun MutableStateFlow<MoneyMoveUi>.update(f: (MoneyMoveUi) -> MoneyMoveUi) {
        value = f(value)
    }

    /**
     * ثبتِ پرداخت یا دریافت.
     * [isPayment] = true یعنی پول از صندوقِ ما بیرون می‌رود.
     */
    fun submit(
        type: String,
        name: String,
        amount: Long,
        isPayment: Boolean,
        source: String,
        note: String
    ) =
        viewModelScope.launch {
            busy.once {
                doSubmit(
                    type = type,
                    name = name,
                    amount = amount,
                    isPayment = isPayment,
                    source = source,
                    note = note
                )
            }
        }

    private suspend fun doSubmit(
        type: String,
        name: String,
        amount: Long,
        isPayment: Boolean,
        source: String,
        note: String
    ) {
        if (name.isBlank()) {
            _state.update { it.copy(message = "نام طرف حساب را انتخاب یا وارد کنید.", isError = true) }
            return
        }
        if (amount <= 0) {
            _state.update { it.copy(message = "مبلغ را درست وارد کنید.", isError = true) }
            return
        }
        val ok = repo.recordManualLedger(
            type = type, name = name, amount = amount,
            isPayment = isPayment, paySource = source, note = note
        )
        _state.update {
            if (ok) it.copy(
                message = (if (isPayment) "✅ پرداخت ثبت و رسیدش صادر شد." else "✅ دریافت ثبت و رسیدش صادر شد."),
                isError = false, done = true
            )
            else it.copy(message = "موجودیِ صندوق کافی نیست.", isError = true, done = false)
        }
    }
}
