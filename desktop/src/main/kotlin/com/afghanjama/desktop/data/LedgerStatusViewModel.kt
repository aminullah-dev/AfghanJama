package com.afghanjama.desktop.data

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.afghanjama.data.DB_VERSION
import com.afghanjama.data.repo.Repo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class LedgerStatus(
    val opening: Boolean = true,
    /** پیامِ خطا اگر دفتر باز نشد — `null` یعنی باز شد. */
    val error: String? = null,
    val path: String = "",
    val version: Int = DB_VERSION,
    val tables: Int = 0,
    val customers: Int = 0,
    val wallet: Long = 0,
    val bank: Long = 0
)

/**
 * اولین باری که دادهٔ واقعی روی ویندوز خوانده می‌شود.
 *
 * تا دیروز پنجرهٔ ویندوز فقط ریاضیِ پول را می‌سنجید — چیزی که به دفتر
 * کاری ندارد. این یکی واقعاً فایلِ دیتابیس را باز می‌کند، جدول‌ها را
 * می‌شمارد و از همان `Repo`ِ مشترک — ۲٬۹۷۲ خطی که روی گوشی هم اجرا
 * می‌شود — چند عدد می‌خواند.
 *
 * روی دفترِ نو همهٔ عددها صفرند، و **صفر بودنشان خودش خبرِ خوبی است**:
 * یعنی فایل ساخته شد، ۴۰ جدول در آن نشست، و پرس‌وجوها جواب دادند.
 *
 * خطا بلعیده نمی‌شود. اگر دفتر باز نشد، همان‌جا روی صفحه نوشته می‌شود؛
 * برنامه‌ای که می‌گوید حالش خوب است ولی دفترش باز نشده بدترین حالت است.
 */
class LedgerStatusViewModel : ViewModel() {

    private val _ui = MutableStateFlow(LedgerStatus())
    val ui: StateFlow<LedgerStatus> = _ui.asStateFlow()

    init {
        load()
    }

    fun load() = viewModelScope.launch {
        _ui.value = LedgerStatus(opening = true)
        val file = databaseFile()
        try {
            val status = withContext(Dispatchers.IO) {
                val db = openDatabase(file)
                val repo = Repo(db)
                LedgerStatus(
                    opening = false,
                    path = file.absolutePath,
                    tables = db.tableNames().size,
                    customers = repo.observeCustomers().first().size,
                    wallet = repo.observeWalletBalance().first(),
                    bank = repo.observeBankBalance().first()
                )
            }
            _ui.value = status
        } catch (t: Throwable) {
            _ui.value = LedgerStatus(
                opening = false,
                path = file.absolutePath,
                error = t.message ?: t::class.simpleName ?: "خطای ناشناخته"
            )
        }
    }
}
