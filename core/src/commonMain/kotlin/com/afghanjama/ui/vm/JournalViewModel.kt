package com.afghanjama.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.afghanjama.data.entities.Accounts
import com.afghanjama.data.entities.JournalEntry
import com.afghanjama.data.entities.JournalLine
import com.afghanjama.data.repo.Repo
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** یک سندِ ژورنال با سطرهایش، آمادهٔ نمایش. */
data class JournalRow(
    val entry: JournalEntry,
    val lines: List<JournalLine>
) {
    val debit: Long get() = lines.sumOf { it.debit }
    val credit: Long get() = lines.sumOf { it.credit }

    /**
     * سندِ نامتراز — نباید هرگز پیش بیاید.
     *
     * `postJournal` سندِ نامتراز را اصلاً نمی‌نویسد، پس این فقط
     * می‌تواند از دادهٔ تاریخی یا یک دستکاریِ بیرونی بیاید. ولی
     * اگر پیش بیاید باید **دیده شود**، نه اینکه در جمعِ کل گم شود.
     */
    val balanced: Boolean get() = debit == credit && debit > 0
}

/**
 * دفتر روزنامه — سندهای حسابداری، همان‌طور که ثبت شده‌اند.
 *
 * **چرا لازم شد.** ژورنال از روزِ اول پشتِ هر عددِ مالیِ این اپ بود و
 * **هیچ صفحه‌ای نداشت**: `observeJournalEntries` سال‌ها در مخزن بود و
 * هیچ‌کس صدایش نمی‌زد. یعنی وقتی عددی در سود و زیان باورنکردنی به
 * نظر می‌رسید، هیچ راهی در خودِ اپ نبود که ببینی از کجا آمده.
 *
 * و یک بار دقیقاً همین افتاد: پولی که در صندوق گذاشته می‌شد به‌جای
 * سرمایه، درآمد ثبت می‌شد و سودِ گزارش‌شده را از منفی به مثبت
 * می‌بُرد. پیدا کردنش یک اسکرین‌شات و یک تحلیل خواست؛ با این صفحه
 * چند ثانیه بود.
 *
 * صفحه فقط می‌خوانَد. هیچ دکمه‌ای چیزی را عوض نمی‌کند — اصلاحِ سند
 * از راهِ خودش انجام می‌شود، نه با دست بردن در دفتر.
 */
class JournalViewModel(repo: Repo) : ViewModel() {

    val rows: StateFlow<List<JournalRow>> =
        combine(
            repo.observeJournalEntries(),
            repo.observeJournalLines()
        ) { entries, lines ->
            val byEntry = lines.groupBy { it.entryId }
            entries.map { JournalRow(it, byEntry[it.id].orEmpty()) }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    companion object {
        /**
         * آیا این سند به جست‌وجو می‌خورد؟
         *
         * نامِ حساب هم گشته می‌شود، نه فقط شرحِ سند: کسی که دنبالِ
         * «سرمایه» می‌گردد، سندهایی را می‌خواهد که به آن حساب خورده‌اند
         * — و شرحِ آن‌ها می‌تواند هر چیزی باشد.
         */
        fun matches(row: JournalRow, query: String): Boolean {
            val q = query.trim()
            if (q.isBlank()) return true
            if (row.entry.memo.contains(q, ignoreCase = true)) return true
            if (row.entry.refType.contains(q, ignoreCase = true)) return true
            if (row.entry.refId.contains(q, ignoreCase = true)) return true
            return row.lines.any {
                it.account.contains(q, ignoreCase = true) ||
                    Accounts.label(it.account).contains(q, ignoreCase = true)
            }
        }
    }
}
