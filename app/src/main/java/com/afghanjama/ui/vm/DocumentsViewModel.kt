package com.afghanjama.ui.vm

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.afghanjama.data.entities.Document
import com.afghanjama.data.repo.Repo
import com.afghanjama.pdf.DocumentRenderer
import com.afghanjama.pdf.Paper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import java.io.File

/** فهرستِ اسنادِ مالیِ تولیدشده (فاکتور/رسید) با شمارهٔ یکتا. */
class DocumentsViewModel(private val repo: Repo) : ViewModel() {

    val documents: StateFlow<List<Document>> =
        repo.observeDocuments()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * ساختِ برگهٔ چاپیِ یک سند. خواندنِ ردیف‌ها و رسمِ PDF هر دو سنگین‌اند و
     * عمداً از نخِ رابط بیرون‌اند تا فهرست هنگامِ چاپ نپرد.
     */
    suspend fun sheet(context: Context, doc: Document, paper: Paper): File =
        withContext(Dispatchers.IO) {
            DocumentRenderer.render(context, repo, doc, paper)
        }

    /** کاغذِ منطقیِ هر نوع سند، وقتی کاربر خودش چیزی انتخاب نکرده. */
    fun defaultPaper(doc: Document): Paper = DocumentRenderer.defaultPaper(doc.type)
}
