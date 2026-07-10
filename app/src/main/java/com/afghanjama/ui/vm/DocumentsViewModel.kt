package com.afghanjama.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.afghanjama.data.entities.Document
import com.afghanjama.data.repo.Repo
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/** فهرستِ اسنادِ مالیِ تولیدشده (فاکتور/رسید) با شمارهٔ یکتا. */
class DocumentsViewModel(private val repo: Repo) : ViewModel() {

    val documents: StateFlow<List<Document>> =
        repo.observeDocuments()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}
