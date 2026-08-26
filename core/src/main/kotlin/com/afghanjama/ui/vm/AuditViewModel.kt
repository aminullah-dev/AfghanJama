package com.afghanjama.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.afghanjama.data.entities.AuditLog
import com.afghanjama.data.repo.Repo
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/** رویدادهای حسابرسی: چه کسی، چه کاری، کِی. */
class AuditViewModel(private val repo: Repo) : ViewModel() {

    val records: StateFlow<List<AuditLog>> =
        repo.observeAudit()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}
