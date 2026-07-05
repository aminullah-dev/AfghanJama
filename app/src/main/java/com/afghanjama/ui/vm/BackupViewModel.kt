package com.afghanjama.ui.vm

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.afghanjama.data.repo.Repo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.OutputStream

data class BackupUi(
    val message: String? = null,
    val isError: Boolean = false,
    val restartRequired: Boolean = false
)

/** پشتیبان‌گیری/بازیابی دیتابیس و خروجی CSV (سازگار با اکسل). */
class BackupViewModel(private val repo: Repo) : ViewModel() {

    private val _ui = MutableStateFlow(BackupUi())
    val ui: StateFlow<BackupUi> = _ui

    fun clearMessage() = _ui.update { it.copy(message = null, isError = false) }

    // ------------------------------------------------
    // پشتیبان‌گیری: کپی فایل دیتابیس به محل انتخابی کاربر
    // ------------------------------------------------
    fun backupTo(context: Context, uri: Uri) = viewModelScope.launch(Dispatchers.IO) {
        runCatching {
            repo.checkpoint() // یکپارچه‌سازی WAL تا فایل اصلی کامل باشد
            val dbFile = context.getDatabasePath(DB_NAME)
            context.contentResolver.openOutputStream(uri)?.use { out ->
                dbFile.inputStream().use { it.copyTo(out) }
            } ?: error("openOutputStream returned null")
        }.onSuccess {
            _ui.update { it.copy(message = "✅ پشتیبان‌گیری کامل شد.", isError = false) }
        }.onFailure { e ->
            _ui.update { it.copy(message = "خطا در پشتیبان‌گیری: ${e.message}", isError = true) }
        }
    }

    // ------------------------------------------------
    // بازیابی: جایگزینی فایل دیتابیس + نیاز به راه‌اندازی دوباره اپ
    // ------------------------------------------------
    fun restoreFrom(context: Context, uri: Uri) = viewModelScope.launch(Dispatchers.IO) {
        runCatching {
            repo.checkpoint()
            val dbFile = context.getDatabasePath(DB_NAME)
            context.contentResolver.openInputStream(uri)?.use { input ->
                dbFile.outputStream().use { input.copyTo(it) }
            } ?: error("openInputStream returned null")
            // فایل‌های WAL/SHM قدیمی نباید با دیتابیس بازیابی‌شده قاطی شوند
            java.io.File(dbFile.path + "-wal").delete()
            java.io.File(dbFile.path + "-shm").delete()
        }.onSuccess {
            _ui.update {
                it.copy(
                    message = "✅ بازیابی انجام شد. اپ را ببندید و دوباره باز کنید.",
                    isError = false,
                    restartRequired = true
                )
            }
        }.onFailure { e ->
            _ui.update { it.copy(message = "خطا در بازیابی: ${e.message}", isError = true) }
        }
    }

    // ------------------------------------------------
    // خروجی CSV — با BOM تا اکسل متن فارسی را درست نشان دهد
    // ------------------------------------------------
    fun exportOrdersCsv(context: Context, uri: Uri) = viewModelScope.launch(Dispatchers.IO) {
        runCatching {
            val orders = repo.observeAllOrders().first()
            context.contentResolver.openOutputStream(uri)?.use { out ->
                writeBom(out)
                val w = out.writer(Charsets.UTF_8)
                w.appendLine(
                    listOf(
                        "کد سفارش", "کد کوتاه", "طرح", "تعداد", "نوع پارچه", "رنگ", "سایز",
                        "مقدار پارچه", "واحد", "منبع پارچه", "قیمت پارچه", "خرج کار",
                        "قیمت توافقی", "مشتری", "تلفن", "خیاط", "وضعیت", "تاریخ ایجاد"
                    ).joinToString(",") { csv(it) }
                )
                orders.forEach { o ->
                    w.appendLine(
                        listOf(
                            o.orderCode, o.shortCode, o.designTitle, o.qty.toString(),
                            o.fabricType, o.fabricColor, o.size,
                            o.fabricAmount.toString(), o.fabricUnit, o.fabricSource,
                            o.fabricPrice.toString(), o.workCost.toString(),
                            o.agreedPrice.toString(), o.customerName, o.customerPhone,
                            o.assignedTailor ?: "", o.status, formatDate(o.createdAt)
                        ).joinToString(",") { csv(it) }
                    )
                }
                w.flush()
            } ?: error("openOutputStream returned null")
        }.onSuccess {
            _ui.update { it.copy(message = "✅ خروجی سفارش‌ها ذخیره شد.", isError = false) }
        }.onFailure { e ->
            _ui.update { it.copy(message = "خطا در خروجی: ${e.message}", isError = true) }
        }
    }

    fun exportTransactionsCsv(context: Context, uri: Uri) = viewModelScope.launch(Dispatchers.IO) {
        runCatching {
            val txList = repo.observeTx().first()
            context.contentResolver.openOutputStream(uri)?.use { out ->
                writeBom(out)
                val w = out.writer(Charsets.UTF_8)
                w.appendLine(
                    listOf("نوع", "صندوق", "مبلغ", "دسته", "یادداشت", "تاریخ")
                        .joinToString(",") { csv(it) }
                )
                txList.forEach { t ->
                    w.appendLine(
                        listOf(
                            if (t.type == "IN") "دریافت" else "پرداخت",
                            t.source, t.amount.toString(), t.category, t.note,
                            formatDate(t.createdAt)
                        ).joinToString(",") { csv(it) }
                    )
                }
                w.flush()
            } ?: error("openOutputStream returned null")
        }.onSuccess {
            _ui.update { it.copy(message = "✅ خروجی تراکنش‌ها ذخیره شد.", isError = false) }
        }.onFailure { e ->
            _ui.update { it.copy(message = "خطا در خروجی: ${e.message}", isError = true) }
        }
    }

    private fun writeBom(out: OutputStream) {
        out.write(0xEF)
        out.write(0xBB)
        out.write(0xBF)
    }

    private fun csv(v: String): String = "\"" + v.replace("\"", "\"\"") + "\""

    private fun formatDate(millis: Long): String =
        java.text.SimpleDateFormat("yyyy/MM/dd HH:mm", java.util.Locale.US)
            .format(java.util.Date(millis))

    companion object {
        const val DB_NAME = "afghanjama.db"
    }
}
