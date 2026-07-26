package com.afghanjama.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.afghanjama.data.entities.SalaryPayment
import com.afghanjama.data.repo.Repo
import com.afghanjama.ui.format.PersianDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** یک کارمندِ حقوق‌بگیر و وضعیتِ حقوقِ ماهِ جاری‌اش. */
data class PayrollRow(
    val name: String,
    val role: String,
    val monthlySalary: Long,
    val paidThisMonth: Long,
    val lastPaidLabel: String
) {
    val isPaid: Boolean get() = monthlySalary > 0 && paidThisMonth >= monthlySalary
    val remaining: Long get() = (monthlySalary - paidThisMonth).coerceAtLeast(0)
}

data class PayrollUi(
    val rows: List<PayrollRow> = emptyList(),
    val payments: List<SalaryPayment> = emptyList(),
    val monthLabel: String = "",
    val wallet: Long = 0,
    val bank: Long = 0
) {
    val salaried: List<PayrollRow> get() = rows.filter { it.monthlySalary > 0 }
    val unpaidCount: Int get() = salaried.count { !it.isPaid }
    val monthlyTotal: Long get() = salaried.sumOf { it.monthlySalary }
    val paidTotal: Long get() = salaried.sumOf { it.paidThisMonth }
    val remainingTotal: Long get() = salaried.sumOf { it.remaining }
}

/**
 * حقوقِ ماهانهٔ کارکنان (آشپز، حسابدار، مدیر، …) — جدا از کارمزدِ خیاط
 * که بر اساسِ تعدادِ کار است. وضعیتِ ماهِ جاری از روی پرداخت‌های ثبت‌شده
 * محاسبه می‌شود، پس هیچ‌وقت با واقعیت اختلاف پیدا نمی‌کند.
 */
class PayrollViewModel(private val repo: Repo) : ViewModel() {

    /** جلوی ثبتِ دوباره با دو ضربهٔ سریع را می‌گیرد. */
    val busy = Busy()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message
    fun clearMessage() { _message.value = null }

    /** کلید و برچسبِ ماهِ جاری (بر مبنای همین لحظه). */
    private fun nowKey() = PersianDate.monthKey(System.currentTimeMillis())
    private fun nowLabel() = PersianDate.monthLabel(System.currentTimeMillis())

    val ui: StateFlow<PayrollUi> = combine(
        repo.observeStaff(),
        repo.observeSalaryPayments(),
        repo.observeWalletBalance(),
        repo.observeBankBalance()
    ) { staff, payments, wallet, bank ->
        val key = nowKey()
        val rows = staff.map { s ->
            val mine = payments.filter { it.employee == s.name }
            PayrollRow(
                name = s.name,
                role = s.role,
                monthlySalary = s.monthlySalary,
                paidThisMonth = mine.filter { it.periodKey == key }.sumOf { it.amount },
                lastPaidLabel = mine.maxByOrNull { it.at }?.periodLabel.orEmpty()
            )
        }.sortedWith(
            compareByDescending<PayrollRow> { it.monthlySalary > 0 }
                .thenBy { it.isPaid }
                .thenBy { it.name }
        )
        PayrollUi(
            rows = rows,
            payments = payments,
            monthLabel = nowLabel(),
            wallet = wallet,
            bank = bank
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PayrollUi())

    /** ثبت/به‌روزرسانی حقوقِ ماهانهٔ توافقیِ یک کارمند. */
    fun saveStaff(name: String, role: String, monthlySalary: Long) = viewModelScope.launch {
        if (name.isBlank()) {
            _message.value = "نام کارمند را وارد کنید."
            return@launch
        }
        repo.addStaff(name, role, monthlySalary)
        _message.value = "«${name.trim()}» ذخیره شد."
    }

    /** پرداختِ حقوقِ ماهِ جاری. */
    fun pay(employee: String, amount: Long, source: String) =
        viewModelScope.launch { busy.once { doPay(employee = employee, amount = amount, source = source) } }

    private suspend fun doPay(employee: String, amount: Long, source: String) {
        if (amount <= 0) {
            _message.value = "مبلغ را درست وارد کنید."
            return
        }
        val ok = repo.paySalary(
            employee = employee,
            amount = amount,
            periodKey = nowKey(),
            periodLabel = nowLabel(),
            source = source
        )
        _message.value =
            if (ok) "حقوق $employee برای ${nowLabel()} پرداخت شد."
            else "موجودیِ صندوق کافی نیست."
    }
}
