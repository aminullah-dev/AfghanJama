package com.afghanjama.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.afghanjama.data.dao.PartyBalance
import com.afghanjama.data.entities.LedgerEntry
import com.afghanjama.data.repo.Repo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** دفتر کلِ یکپارچه: ماندهٔ هر طرف حساب + گردشِ همهٔ اسناد. */
class LedgerViewModel(private val repo: Repo) : ViewModel() {

    val balances: StateFlow<List<PartyBalance>> =
        repo.observeLedgerBalances()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val entries: StateFlow<List<LedgerEntry>> =
        repo.observeAllLedgerEntries()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * شمارهٔ تلفنِ هر طرفِ حساب، اگر جایی ثبت شده باشد.
     *
     * **چرا از چهار جدول.** شماره‌ها پراکنده‌اند و این تاریخی است:
     * مشتری، خیاط و ناظر هرکدام جدولِ خودشان را دارند و فروشنده در
     * `parties` است. دفتر کل هر چهار را کنارِ هم نشان می‌دهد، پس
     * جست‌وجو هم باید هر چهار را ببیند — وگرنه دکمهٔ تماس برای نصفِ
     * فهرست بی‌صدا غایب می‌شد و کسی نمی‌فهمید چرا.
     *
     * کلید «نوع|نام» است، همان کلیدی که خودِ دفتر با آن کار می‌کند.
     * نامِ خالی رد می‌شود تا با هم قاتی نشوند.
     */
    val phones: StateFlow<Map<String, String>> =
        combine(
            repo.observeParties(),
            repo.observeCustomers(),
            repo.observeTailors(),
            repo.observeInspectors()
        ) { parties, customers, tailors, inspectors ->
            buildMap {
                fun put(type: String, name: String, phone: String?) {
                    val n = name.trim()
                    val p = phone?.trim().orEmpty()
                    if (n.isNotBlank() && p.isNotBlank()) put("$type|$n", p)
                }
                parties.forEach { put(it.type, it.name, it.phone) }
                customers.forEach { put("CUSTOMER", it.name, it.phone) }
                tailors.forEach { put("TAILOR", it.name, it.phone) }
                inspectors.forEach { put("INSPECTOR", it.name, it.phone) }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message
    fun clearMessage() { _message.value = null }

    /** ثبتِ دستیِ پرداخت (isPayment=true) یا دریافت روی حسابِ یک طرف. */
    /**
     * بدهیِ خرج‌کارِ بی‌صاحبِ باقی‌مانده از پیش از اصلاح.
     *
     * صفر یعنی چیزی برای پاک کردن نیست و کارتِ اصلاح اصلاً دیده نمی‌شود.
     */
    private val _orphanWorkCost = MutableStateFlow(0L)
    val orphanWorkCost: StateFlow<Long> = _orphanWorkCost

    init { refreshOrphan() }

    fun refreshOrphan() = viewModelScope.launch {
        _orphanWorkCost.value = repo.orphanWorkCostPayable()
    }

    /**
     * ثبتِ پرداختِ عقب‌افتادهٔ خرج‌کارِ گذشته.
     *
     * این پول در واقعیت پرداخت شده بوده ولی اپ هرگز از صندوق کمش نکرده،
     * پس صندوقِ اپ از صندوقِ واقعی بیشتر نشان می‌دهد. اینجا همان اختلاف
     * جبران می‌شود.
     */
    fun settleOrphanWorkCost(paySource: String) = viewModelScope.launch {
        val before = _orphanWorkCost.value
        val ok = repo.settleOrphanWorkCost(paySource)
        refreshOrphan()
        _message.value = if (ok) {
            "${before} ؋ بدهیِ خرج‌کارِ پیشین تسویه شد و از صندوق کم گردید."
        } else {
            "تسویه انجام نشد — یا چیزی نمانده یا موجودیِ صندوق کافی نیست."
        }
    }

    fun recordManual(type: String, name: String, amount: Long, isPayment: Boolean, note: String) =
        viewModelScope.launch {
            val ok = repo.recordManualLedger(type, name, amount, isPayment, "WALLET", note)
            _message.value =
                if (ok) null
                else "ثبت نشد — موجودی صندوق برای این پرداخت کافی نیست."
        }
}
