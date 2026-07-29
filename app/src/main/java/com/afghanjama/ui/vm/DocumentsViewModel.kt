package com.afghanjama.ui.vm

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.afghanjama.data.entities.Document
import com.afghanjama.data.repo.Repo
import com.afghanjama.pdf.DocumentRenderer
import com.afghanjama.pdf.Paper
import com.afghanjama.prefs.SheetPrefs
import com.afghanjama.ui.components.SheetAction
import com.afghanjama.util.PrintKit
import com.afghanjama.util.ShareUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

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
    fun paperFor(context: Context, doc: Document): Paper {
        val receipt = DocumentRenderer.isReceipt(doc.type)
        val saved = SheetPrefs.paperLabel(context, receipt)
        return if (saved.isBlank()) DocumentRenderer.defaultPaper(doc.type)
        else Paper.byLabel(saved)
    }

    fun rememberPaper(context: Context, doc: Document, paper: Paper) {
        SheetPrefs.savePaperLabel(context, DocumentRenderer.isReceipt(doc.type), paper.label)
    }

    /**
     * ساختِ برگه و انجامِ کاری که خواسته شده. خواندنِ ردیف‌ها، رسمِ PDF و
     * تبدیل به تصویر هر سه سنگین‌اند و عمداً از نخِ رابط بیرون‌اند تا فهرست
     * هنگامِ چاپ نپرد.
     */
    fun act(
        context: Context,
        doc: Document,
        paper: Paper,
        action: SheetAction
    ) = viewModelScope.launch {
        busy.once {
            val file = runCatching {
                withContext(Dispatchers.IO) { DocumentRenderer.render(context, repo, doc, paper) }
            }.getOrNull()
            if (file == null) {
                _message.value = "ساختِ برگه انجام نشد."
                return@once
            }
            when (action) {
                SheetAction.PDF ->
                    ShareUtil.shareFile(context, file, "application/pdf", "اشتراک‌گذاری فاکتور")

                SheetAction.PRINT ->
                    if (!PrintKit.print(context, file, doc.number)) {
                        _message.value =
                            "چاپ ممکن نشد. اگر پرینتری نصب نیست، «PDF» یا «تصویر» را بفرستید."
                    }

                SheetAction.IMAGE -> {
                    val jpg = File(ShareUtil.sharedDir(context), "${doc.number}.jpg")
                    val ok = withContext(Dispatchers.IO) { PrintKit.toImage(file, jpg) }
                    if (ok) ShareUtil.shareFile(context, jpg, "image/jpeg", "اشتراک‌گذاری تصویر")
                    else _message.value = "تبدیل به تصویر انجام نشد؛ همان PDF را بفرستید."
                }
            }
        }
    }
}
