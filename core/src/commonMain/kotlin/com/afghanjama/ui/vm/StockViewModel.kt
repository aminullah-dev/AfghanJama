package com.afghanjama.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.afghanjama.data.entities.MaterialStock
import com.afghanjama.data.repo.Repo
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/**
 * موجودی انبار مواد برای نمایش «موجودی فعلی» در ثبت سفارش مشتری
 * (وقتی پارچه «از موجودی انبار» انتخاب می‌شود). پارچه دیگر انبار جدا
 * ندارد و بخشی از انبار عمومی مواد است.
 */
class StockViewModel(repo: Repo) : ViewModel() {

    val stocks: StateFlow<List<MaterialStock>> =
        repo.observeMaterialStock()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}
