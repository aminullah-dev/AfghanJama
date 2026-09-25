package com.afghanjama.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.afghanjama.data.DebtFollowUp
import com.afghanjama.data.Installments
import com.afghanjama.data.repo.Repo
import com.afghanjama.util.nowMillis
import com.afghanjama.util.withTime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * فهرستِ امروزِ پیگیریِ طلب — یک جا ساخته می‌شود، دو جا خوانده.
 *
 * صفحهٔ «پیگیری طلب» و هشدارِ مرکزِ اقدام هر دو از همین می‌خوانند. اگر
 * هر کدام حسابِ خودش را داشت، هشدار می‌گفت «۵ نفر» و صفحه چهار نفر
 * نشان می‌داد — همان دامی که `healthFlow` را هم مشترک کرد.
 */
fun debtPlanFlow(repo: Repo): Flow<DebtFollowUp.Plan> =
    combine(
        repo.observeCustomerAccounts(),
        repo.observeDebtFollowUps(),
        lateInstallmentsFlow(repo),
        phonesFlow(repo),
    ) { accounts, contacts, late, phones ->
        val now = nowMillis()
        val names = accounts.filterValues { it.owed > 0L }.keys + late.keys
        val debtors = names.map { n ->
            val acc = accounts[n]
            val inst = late[n]
            DebtFollowUp.Debtor(
                name = n,
                phone = phones[n].orEmpty(),
                owed = acc?.owed?.coerceAtLeast(0L) ?: 0L,
                oldestUnpaidAt = acc?.oldestUnpaidAt,
                lateInstallment = inst?.first ?: 0L,
                lateInstallmentDue = inst?.second ?: 0L,
                lastContact = contacts[n],
            )
        }
        DebtFollowUp.plan(debtors, now, withTime(now, 0, 0))
    }

/**
 * نامِ مشتری ← (جمعِ ماندهٔ قسط‌های گذشته، سررسیدِ قدیمی‌ترینشان).
 *
 * پرداخت‌ها از `observePaidByCustomer` می‌آیند — همان که پروندهٔ مشتری و
 * هشدارِ قسط می‌خوانند — تا اینجا قسطی «گذشته» نباشد که آنجا «تسویه»
 * است.
 */
private fun lateInstallmentsFlow(repo: Repo): Flow<Map<String, Pair<Long, Long>>> =
    combine(repo.observeInstallments(), repo.observePaidByCustomer()) { all, paidBy ->
        val now = nowMillis()
        all.groupBy { it.customerName.trim() }
            .filterKeys { it.isNotEmpty() }
            .mapNotNull { (name, rows) ->
                val late = Installments.allocate(rows, paidBy[name] ?: 0L).filter { it.overdue(now) }
                if (late.isEmpty()) null
                else name to (late.sumOf { it.remaining } to late.minOf { it.plan.dueDate })
            }
            .toMap()
    }

/**
 * شمارهٔ هر مشتری: اول از پروندهٔ مشتری، اگر نبود از طرفِ حسابِ دفتر.
 * مشتری‌ای که فقط در دفتر است (مثلاً با «حساب قبلی» آمده) هم شماره
 * داشته باشد.
 */
private fun phonesFlow(repo: Repo): Flow<Map<String, String>> =
    combine(repo.observeCustomers(), repo.observeParties()) { customers, parties ->
        val fromParties = parties
            .filter { it.type == "CUSTOMER" && it.phone.isNotBlank() }
            .associate { it.name.trim() to it.phone.trim() }
        val fromCustomers = customers
            .mapNotNull { c -> c.phone?.trim()?.takeIf { it.isNotEmpty() }?.let { c.name.trim() to it } }
            .toMap()
        fromParties + fromCustomers
    }

/**
 * پیگیریِ طلب — چه کسی، چرا، و نتیجهٔ تماس.
 *
 * خودِ زنگ زدن و فرستادنِ پیام کارِ سکو است (`SystemActions`)؛ اینجا
 * فقط **نتیجه** ثبت می‌شود.
 */
class DebtFollowUpViewModel(private val repo: Repo) : ViewModel() {

    val plan: StateFlow<DebtFollowUp.Plan> =
        debtPlanFlow(repo).stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            DebtFollowUp.Plan(emptyList(), emptyList())
        )

    /** نامِ مشتری ← شناسه‌اش، برای باز کردنِ پرونده. */
    val customerIds: StateFlow<Map<String, Long>> =
        repo.observeCustomers()
            .map { list -> list.associate { it.name.trim() to it.id } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message
    fun clearMessage() { _message.value = null }

    /**
     * ثبتِ نتیجه. [days] فقط برای «قول داد» است: روزِ قول چند روز از
     * امروز — ۰ یعنی امروز.
     */
    fun record(row: DebtFollowUp.Row, outcome: DebtFollowUp.Outcome, days: Int = 0) =
        viewModelScope.launch {
            val now = nowMillis()
            val until = if (outcome == DebtFollowUp.Outcome.PROMISED) {
                withTime(now + days.coerceAtLeast(0) * 86_400_000L, 0, 0)
            } else 0L
            runCatching {
                repo.recordDebtFollowUp(row.debtor.name, outcome, until, row.debtor.amount)
            }.onFailure { e -> _message.value = e.message ?: "ثبت نشد" }
        }
}
