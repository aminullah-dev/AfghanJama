package com.afghanjama.ui.vm

import com.afghanjama.platform.Docs
import com.afghanjama.prefs.Settings
import com.afghanjama.pdf.DocKinds
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.afghanjama.data.entities.Document
import com.afghanjama.data.repo.Repo
import com.afghanjama.pdf.Paper
import com.afghanjama.prefs.SheetPrefs
import com.afghanjama.ui.components.SheetAction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** فهرستِ اسنادِ مالیِ تولیدشده (فاکتور/رسید) با شمارهٔ یکتا. */
class DocumentsViewModel(private val repo: Repo) : ViewModel() {

    val documents: StateFlow<List<Document>> =
        repo.observeDocuments()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** تا وقتی برگه ساخته می‌شود، دکمه‌ها قفل‌اند. */
    private val busy = Busy()
    val working: StateFlow<Boolean> = busy.state

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    fun clearMessage() {
        _message.value = null
    }

    /**
     * کاغذِ این سند: هرچه کاربر بارِ قبل برای همین دسته انتخاب کرده، وگرنه
     * پیش‌فرضِ منطقیِ نوعِ سند (رول برای رسید، A5 برای فاکتور).
     */
    fun paperFor(settings: Settings, doc: Document): Paper {
        val receipt = DocKinds.isReceipt(doc.type)
        val saved = SheetPrefs.paperLabel(settings, receipt)
        return if (saved.isBlank()) DocKinds.defaultPaper(doc.type)
        else Paper.byLabel(saved)
    }

    fun rememberPaper(settings: Settings, doc: Document, paper: Paper) {
        SheetPrefs.savePaperLabel(settings, DocKinds.isReceipt(doc.type), paper.label)
    }

    /**
     * ساخت و انجامِ کارِ خواسته‌شده — از راهِ مرزِ [Docs].
     *
     * **پیش از این کلِ این کار همین‌جا بود** و `Context` می‌گرفت:
     * رسمِ PDF، چاپ، تبدیل به تصویر و اشتراک. هر چهار تا اندرویدی
     * بودند، پس این ViewModel و صفحه‌اش در `:app` گیر کرده بودند و
     * ویندوز اصلاً صفحهٔ اسناد نداشت.
     *
     * حالا تصمیم اینجاست و اجرا آن‌سوی مرز. کاغذِ اندروید ذره‌ای عوض
     * نشد — همان کد، فقط جای دیگری نشسته.
     */
    fun act(
        docs: Docs,
        doc: Document,
        paper: Paper,
        action: SheetAction
    ) = viewModelScope.launch {
        busy.once {
            _message.value = docs.documentAction(repo, doc, paper, action)
        }
    }
}
